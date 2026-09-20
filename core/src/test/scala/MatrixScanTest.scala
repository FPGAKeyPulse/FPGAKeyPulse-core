package keypulse.core

import keypulse.core.matrix._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import scala.util.Random

class MatrixScanTest extends AnyFunSuite {
  test("reject unsafe dwell and synchronizer parameters") {
    intercept[IllegalArgumentException](MatrixScanConfig(2, 8, 2))
    intercept[IllegalArgumentException](MatrixScanConfig(2, 8, 50, syncStages = 1))
    intercept[IllegalArgumentException](MatrixScanConfig(0, 8))
    intercept[IllegalArgumentException](MatrixScanConfig(2, 0))
  }

  for ((rows, cols, dwell, activeLow) <- Seq((2, 52, 50, true), (2, 3, 3, false), (1, 1, 7, true), (3, 5, 9, false))) {
    test(s"${rows}x${cols} dwell=$dwell activeLow=$activeLow coherent frames and reset") {
      val cfg = MatrixScanConfig(rows, cols, dwell, activeLow, activeLow)
      SimConfig.withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)))
        .workspacePath(s"simWorkspace/matrix-$rows-$cols-$dwell")
        .compile(new MatrixScan(cfg)).doSim { dut =>
          SimTimeout(2000000)
          val rng = new Random(0x1234)
          val mask = (BigInt(1) << cols) - 1
          var pressed = Array.fill(rows)(BigInt(0))
          dut.io.cols #= (if (activeLow) mask else BigInt(0))
          dut.clockDomain.forkStimulus(10)
          dut.clockDomain.waitSampling(5)
          // Drive the physical matrix as soon as the selected row changes.
          fork {
            while (true) {
              val row = dut.io.rowIndex.toInt
              dut.io.cols #= (if (activeLow) mask ^ pressed(row) else pressed(row))
              sleep(1)
            }
          }
          for (epoch <- 0 until 20) {
            dut.clockDomain.assertReset()
            sleep(30)
            sleep(1)
            assert(dut.io.frameKeys.toBigInt == 0)
            assert(!dut.io.frameValid.toBoolean)
            pressed = Array.fill(rows)(BigInt(cols, rng))
            dut.clockDomain.deassertReset()
            var previousFrame = BigInt(0)
            var lastFrameCycle = -1
            var frameCount = 0
            var lastSample = -1
            for (cycle <- 0 until rows * dwell * 5) {
              dut.clockDomain.waitSampling()
              sleep(2)
              val row = dut.io.rowIndex.toInt
              val expectedRows = if (activeLow) ((BigInt(1) << rows) - 1) ^ (BigInt(1) << row) else BigInt(1) << row
              assert(dut.io.rows.toBigInt == expectedRows)
              if (dut.io.sampleValid.toBoolean) {
                if (lastSample >= 0) assert(cycle - lastSample == dwell)
                lastSample = cycle
                val sampled = dut.io.sampledRowIndex.toInt
                assert(((dut.io.keys.toBigInt >> (sampled * cols)) & mask) == pressed(sampled))
              }
              if (dut.io.frameValid.toBoolean) {
                val expected = pressed.zipWithIndex.map { case (bits, r) => bits << (r * cols) }.reduce(_ | _)
                assert(dut.io.frameKeys.toBigInt == expected)
                assert(dut.io.sampleValid.toBoolean)
                assert(dut.io.sampledRowIndex.toInt == rows - 1)
                if (lastFrameCycle >= 0) assert(cycle - lastFrameCycle == rows * dwell)
                lastFrameCycle = cycle
                previousFrame = expected
                frameCount += 1
              } else assert(dut.io.frameKeys.toBigInt == previousFrame)
            }
            assert(frameCount >= 4)
          }
        }
    }
  }
}

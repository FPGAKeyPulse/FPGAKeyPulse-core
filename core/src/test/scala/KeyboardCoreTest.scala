package keypulse.core

import keypulse.core.matrix._
import keypulse.core.hid._
import keypulse.core.keyboard._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._

class KeyboardCoreTest extends AnyFunSuite {
  test("physical matrix to debounced HID snapshot, rollover, backpressure and reset") {
    val mapping = (4 until 12) ++ Seq(0xe0, 0xe1)
    val cfg = KeyboardCoreConfig(MatrixScanConfig.fast2xN(5), BootKeyMap(mapping), 1, 3)
    SimConfig.withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)))
      .workspacePath("simWorkspace/keyboard-core").compile(new KeyboardCore(cfg)).doSim { dut =>
        SimTimeout(1000000)
        var pressed = 0
        dut.io.cols #= 31
        dut.io.send #= false
        dut.io.report.ready #= false
        dut.clockDomain.forkStimulus(10)
        dut.clockDomain.waitSampling(5)
        fork {
          while (true) {
            val selected = if ((dut.io.rows.toInt & 1) == 0) 0 else 1
            dut.io.cols #= (31 ^ ((pressed >> (selected*5)) & 31))
            sleep(1)
          }
        }
        def step(): Unit = { dut.clockDomain.waitSampling(); sleep(1) }
        def packed(bytes: Seq[Int]): BigInt = bytes.zipWithIndex.map {case (b,i) => BigInt(b) << (8*i)}.reduce(_ | _)
        def state(bits: Int, expected: Seq[Int]): Unit = {
          pressed = bits
          // Includes worst row phase, three release frames, and pipeline latency.
          sleep(6000)
          assert(dut.io.stateValid.toBoolean)
          assert(dut.io.currentReport.toBigInt == packed(expected))
        }
        def transfer(expected: Seq[Int], changeDuringTransfer: Boolean = false): Unit = {
          assert(dut.io.canSend.toBoolean)
          dut.io.send #= true
          step()
          dut.io.send #= false
          if (changeDuringTransfer) { pressed = 0; sleep(6000) }
          for (i <- 0 until 8) {
            dut.io.report.ready #= false
            for (_ <- 0 until 3) {
              assert(dut.io.report.valid.toBoolean)
              assert(dut.io.report.fragment.toInt == expected(i))
              assert(dut.io.report.last.toBoolean == (i == 7))
              step()
            }
            dut.io.report.ready #= true
            step()
          }
          dut.io.report.ready #= false
          assert(!dut.io.report.valid.toBoolean)
        }
        val empty = Seq.fill(8)(0)
        state(0, empty)
        transfer(empty)
        val combo = Seq(1, 0, 4, 5, 0, 0, 0, 0)
        state((1 << 8) | 3, combo)
        transfer(combo, changeDuringTransfer = true)
        assert(dut.io.changed.toBoolean)
        transfer(empty)
        assert(!dut.io.changed.toBoolean)
        val rollover = Seq(3, 0) ++ Seq.fill(6)(1)
        state(1023, rollover)
        transfer(rollover)
        val six = Seq(0, 0, 4, 5, 6, 7, 8, 9)
        state(63, six)
        transfer(six)
        // A short clear bounce must not release an accepted key.
        pressed = 0
        sleep(500)
        pressed = 63
        sleep(2000)
        assert(dut.io.currentReport.toBigInt == packed(six))
        dut.io.send #= true
        step()
        dut.io.send #= false
        dut.clockDomain.assertReset()
        sleep(30)
        assert(!dut.io.report.valid.toBoolean)
        assert(!dut.io.stateValid.toBoolean)
        assert(!dut.io.canSend.toBoolean)
        pressed = 0
        dut.clockDomain.deassertReset()
        state(0, empty)
        transfer(empty)
      }
  }
}

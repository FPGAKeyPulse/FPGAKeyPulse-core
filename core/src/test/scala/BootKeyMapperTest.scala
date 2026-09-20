package keypulse.core

import keypulse.core.hid._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import scala.util.Random

class BootKeyMapperTest extends AnyFunSuite {
  test("reject reserved and out-of-descriptor key usages") {
    intercept[IllegalArgumentException](BootKeyMap(Seq.empty))
    for (u <- Seq(-1, 1, 2, 3, 0x66, 0xdf, 0xe8, 256))
      intercept[IllegalArgumentException](BootKeyMap(Seq(u)))
  }
  for (mapping <- Seq(Seq(4), Seq(0, 0xe0, 0xe7), Seq(4, 5, 4, 0, 0xe0, 0xe1, 6, 7, 8, 9, 10, 11, 12),
    (4 until 100) ++ (0xe0 to 0xe7))) {
    test(s"${mapping.length} keys pipeline ordering dedup modifiers and rollover") {
      SimConfig.withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)))
        .workspacePath(s"simWorkspace/keymap-${mapping.length}")
        .compile(new BootKeyMapper(BootKeyMap(mapping))).doSim { dut =>
          SimTimeout(1000000)
          dut.io.samples.valid #= false
          dut.io.samples.payload #= 0
          dut.clockDomain.forkStimulus(10)
          dut.clockDomain.waitSampling(5)
          val rng = new Random(15)
          def model(bits: BigInt): (Int, Seq[Int], Boolean) = {
            val usages = mapping.zipWithIndex.collect { case (u,i) if bits.testBit(i) => u }.distinct
            val mods = usages.filter(u => u >= 0xe0 && u <= 0xe7).foldLeft(0)((a,u) => a | (1 << (u-0xe0)))
            val normal = usages.filter(u => u >= 4 && u <= 0x65).sorted
            (mods, if (normal.size > 6) Seq.fill(6)(1) else normal.padTo(6, 0), normal.size > 6)
          }
          var pending: Option[(Int, Seq[Int], Boolean)] = None
          var held = (0, Seq.fill(6)(0), false)
          def drive(bits: BigInt, valid: Boolean): Unit = {
            dut.io.samples.valid #= valid
            dut.io.samples.payload #= bits
            dut.clockDomain.waitSampling()
            sleep(1)
            assert(dut.io.valid.toBoolean == pending.nonEmpty)
            pending.foreach(expected => held = expected)
            assert(dut.io.modifiers.toInt == held._1)
            assert(dut.io.keyCodes.map(_.toInt).toSeq == held._2)
            assert(dut.io.rollover.toBoolean == held._3)
            pending = if (valid) Some(model(bits)) else None
          }
          for (epoch <- 0 until 5) {
            for (i <- mapping.indices) drive(BigInt(1) << i, true)
            drive((BigInt(1) << mapping.size)-1, true)
            drive(0, true)
            for (_ <- 0 until 500) drive(BigInt(mapping.size, rng), rng.nextInt(4) != 0)
            drive(0, false)
            drive(0, false)
            dut.clockDomain.assertReset()
            sleep(30)
            assert(!dut.io.valid.toBoolean)
            assert(dut.io.modifiers.toInt == 0)
            assert(dut.io.keyCodes.forall(_.toInt == 0))
            pending = None
            held = (0, Seq.fill(6)(0), false)
            dut.io.samples.valid #= false
            dut.clockDomain.deassertReset()
          }
        }
    }
  }
}

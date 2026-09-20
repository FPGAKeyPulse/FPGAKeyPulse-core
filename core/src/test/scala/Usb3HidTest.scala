package keypulse.core

import keypulse.core.hid._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import scala.util.Random

class Usb3HidTest extends AnyFunSuite {
  test("SuperSpeed descriptor lengths, endpoint companion, power and service interval") {
    val cfg = Usb3HidDescriptorConfig(0x1234, 0x5678, 100, 10, 32)
    val d = new Usb3HidDescriptors(cfg)
    def word(s: Seq[Int], i: Int): Int = s(i) | (s(i+1) << 8)
    assert(d.device.length == 18)
    assert(word(d.device, 2) == 0x300)
    assert(d.device(7) == 9)
    assert(word(d.device, 8) == cfg.vendorId)
    assert(word(d.device, 10) == cfg.productId)
    assert(d.device.slice(14, 17) == Seq(0, 0, 0))
    assert(word(d.configuration, 2) == d.configuration.length)
    assert(d.configuration(7) == 0x80)
    assert(d.configuration(8) == 13)
    var offset = 0
    var types = Vector.empty[Int]
    while (offset < d.configuration.length) {
      val n = d.configuration(offset)
      assert(n >= 2 && offset+n <= d.configuration.length)
      types :+= d.configuration(offset+1)
      offset += n
    }
    assert(types == Vector(2, 4, 0x21, 5, 0x30))
    assert(word(d.configuration, 25) == d.report.length)
    assert(d.configuration.slice(27, 34) == Seq(7, 5, 0x81, 3, 8, 0, 1))
    assert(d.configuration.slice(34, 40) == Seq(6, 0x30, 0, 0, 8, 0))
    assert(d.bos.length == word(d.bos, 2))
    assert(d.bos(4) == 2)
    assert(d.bos.slice(5, 12) == Seq(7, 16, 2, 0, 0, 0, 0))
    assert(word(d.bos, 16) == 14)
    assert(cfg.serviceIntervalUs == 125)
    assert(cfg.copy(intervalExponent = 16).serviceIntervalUs == 4096000)
    intercept[IllegalArgumentException](cfg.copy(intervalExponent = 0))
    intercept[IllegalArgumentException](cfg.copy(intervalExponent = 17))
    intercept[IllegalArgumentException](cfg.copy(maxPowerMa = 901))
    intercept[IllegalArgumentException](cfg.copy(vendorId = 0))
    assert((d.device ++ d.configuration ++ d.bos ++ d.report).forall(v => v >= 0 && v <= 255))
  }

  test("HID report snapshot survives arbitrary byte backpressure, input changes and reset") {
    SimConfig.withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)))
      .workspacePath("simWorkspace/hid-backpressure").compile(new HidKeyboard()).doSim { dut =>
        val rng = new Random(7)
        dut.io.send #= false
        dut.io.modifiers #= 0
        dut.io.keyCodes.foreach(_ #= 0)
        dut.io.report.ready #= false
        dut.clockDomain.forkStimulus(10)
        dut.clockDomain.waitSampling(5)
        def step(): Unit = { dut.clockDomain.waitSampling(); sleep(1) }
        for (trial <- 0 until 100) {
          assert(dut.io.canSend.toBoolean)
          val mods = rng.nextInt(256)
          val keys = Seq.fill(6)(rng.nextInt(0x62)+4)
          val expected = Seq(mods, 0) ++ keys
          dut.io.modifiers #= mods
          keys.zipWithIndex.foreach { case (v,i) => dut.io.keyCodes(i) #= v }
          dut.io.send #= true
          step()
          dut.io.send #= false
          assert(!dut.io.changed.toBoolean)
          // Changes after acceptance must not alter the in-flight report.
          dut.io.modifiers #= (mods ^ 255)
          dut.io.keyCodes.foreach(_ #= 0)
          for (index <- 0 until 8) {
            dut.io.report.ready #= false
            for (_ <- 0 until rng.nextInt(8)+1) {
              sleep(1)
              assert(dut.io.report.valid.toBoolean)
              assert(dut.io.report.fragment.toInt == expected(index))
              assert(dut.io.report.last.toBoolean == (index == 7))
              assert(!dut.io.canSend.toBoolean)
              // Busy send pulses are ignored, never restart a transfer.
              dut.io.send #= (index == 3)
              step()
            }
            dut.io.send #= false
            dut.io.report.ready #= true
            step()
          }
          assert(!dut.io.report.valid.toBoolean)
          assert(dut.io.canSend.toBoolean)
          assert(dut.io.changed.toBoolean)
          dut.io.report.ready #= false
        }
        dut.io.send #= true
        step()
        dut.io.send #= false
        dut.clockDomain.assertReset()
        step()
        assert(!dut.io.report.valid.toBoolean)
        assert(dut.io.canSend.toBoolean)
        dut.clockDomain.deassertReset()
      }
  }
}

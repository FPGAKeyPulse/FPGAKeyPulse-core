package keypulse.test

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import spinal.sim._
import scala.util.Random

case class DemoComponent() extends Component {
  val io = new Bundle {
    val aIn = in UInt (8 bits)
    val bIn = in UInt (8 bits)
    val resOut = out UInt (9 bits)
  }

  io.resOut := RegNext(io.aIn +^ io.bIn, io.resOut.getZero)
}

class TesterDemo extends SpinalTester[DemoComponent] {
  def createTop: DemoComponent = DemoComponent()

  def sim: Seq[SimTest[DemoComponent]] = Seq(
    SimTest("fixed") { dut =>
      dut.clockDomain.forkStimulus(250 MHz)
      dut.io.aIn #= 0
      dut.io.bIn #= 0
      dut.clockDomain.waitSampling()

      for ((a, b) <- Seq((0, 0), (1, 2), (7, 9), (42, 85), (255, 1))) {
        dut.io.aIn #= a
        dut.io.bIn #= b
        dut.clockDomain.waitSampling(2)

        dut.io.resOut.toBigInt shouldBe a + b
      }
    },
    SimTest("random") { dut =>
      dut.clockDomain.forkStimulus(250 MHz)
      dut.io.aIn #= 0
      dut.io.bIn #= 0
      dut.clockDomain.waitSampling()

      for ((a, b) <- Seq.fill(1024)((Random.nextInt(256) & 0xff, Random.nextInt(256) & 0xff))) {
        dut.io.aIn #= a
        dut.io.bIn #= b
        dut.clockDomain.waitSampling(2)

        dut.io.resOut.toBigInt shouldBe a + b
      }
    }
  )

}

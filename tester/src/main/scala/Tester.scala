package keypulse.test

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import spinal.core._
import spinal.core.sim._
import spinal.sim._

/** One simulation case for a SpinalHDL component. */
final case class SimTest[T <: Component](
    name: String
)(val body: T => Unit)

/**
 * Generic SpinalHDL test framework.
 *
 * Usage:
 *
 * {{{
 * class MyDutSpec extends SpinalTester[MyDut] {
 *   override def createTop: MyDut = new MyDut
 *
 *   override def sim: Seq[SimTest[MyDut]] = Seq(
 *     SimTest("basic") { dut =>
 *       dut.clockDomain.waitSampling()
 *     }
 *   )
 * }
 * }}}
 *
 * By default it registers:
 *   1. generateVerilog
 *   2. generateVHDL
 *   3. every simulation in `sim`
 */
trait SpinalTester[T <: Component] extends AnyFunSuite with Matchers {
  def createTop: T
  def sim: Seq[SimTest[T]]

  protected def enableVerilog: Boolean = true
  protected def enableVhdl: Boolean = true
  protected def enableSim: Boolean = true

  protected def enableWave: Boolean = true
  protected def simulator: SpinalSimBackendSel = SpinalSimBackendSel.VERILATOR
  protected def waveFormat: WaveFormat = WaveFormat.FST
  protected def clockDomainConfig: ClockDomainConfig = ClockDomainConfig(resetKind = SYNC)

  protected def testName: String =
    getClass.getSimpleName.stripSuffix("$")

  protected def targetDirectory: String =
    s"generated/$testName"

  protected def workspacePath(testCase: String): String =
    s"simWorkspace/$testName-$testCase"

  protected def spinalConfig: SpinalConfig =
    SpinalConfig(targetDirectory = targetDirectory, defaultConfigForClockDomains = clockDomainConfig)

  protected def simConfig(testCase: String): SpinalSimConfig = {
    val base = SimConfig
      .withConfig(spinalConfig)
      .workspacePath(workspacePath(testCase))
    base._backend = simulator
    base._waveFormat = waveFormat
    if (!enableWave)
      base._waveFormat = WaveFormat.NONE

    base

  }

  if (enableVerilog) {
    test("generateVerilog") {
      spinalConfig.generateVerilog(createTop)
    }
  }

  if (enableVhdl) {
    test("generateVHDL") {
      spinalConfig.generateVhdl(createTop)
    }
  }

  if (enableSim) {
    for (simTest <- sim) {
      test(s"sim ${simTest.name}") {
        runSim(simTest.name)(simTest.body)
      }
    }
  }

  protected def runSim(testCase: String)(body: T => Unit): Unit =
    simConfig(testCase).compile(createTop).doSim { dut =>
      body(dut)
    }

}

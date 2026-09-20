package keypulse.core.tools

import spinal.core._
import keypulse.core.matrix._
import keypulse.core.hid._

object GenerateOoc extends App {
  require(args.length == 1, "usage: GenerateOoc matrix|hid")
  val config = SpinalConfig(targetDirectory = "generated/ooc",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC),
    defaultClockDomainFrequency = FixedFrequency(100 MHz))
  args(0) match {
    case "matrix" => config.generateVerilog(new MatrixScan(MatrixScanConfig(2, 52, cyclesPerRow = 50)))
    case "hid" => config.generateVerilog(new HidKeyboard())
    case other => sys.error(s"Unknown OOC target: $other")
  }
}

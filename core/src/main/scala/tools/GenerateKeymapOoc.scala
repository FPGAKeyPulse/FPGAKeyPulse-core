package keypulse.core.tools
import spinal.core._
import keypulse.core.hid._
object GenerateKeymapOoc extends App {
  SpinalConfig(targetDirectory = "generated/ooc",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC),
    defaultClockDomainFrequency = FixedFrequency(100 MHz))
    .generateVerilog(new BootKeyMapper(BootKeyMap((4 until 100) ++ (0xe0 to 0xe7))))
}

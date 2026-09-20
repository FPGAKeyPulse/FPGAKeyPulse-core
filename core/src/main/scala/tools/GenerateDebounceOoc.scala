package keypulse.core.tools
import spinal.core._
import keypulse.core.matrix._
object GenerateDebounceOoc extends App {
  SpinalConfig(targetDirectory = "generated/ooc",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC),
    defaultClockDomainFrequency = FixedFrequency(100 MHz))
    .generateVerilog(new KeyDebounce(KeyDebounceConfig(104)))
}

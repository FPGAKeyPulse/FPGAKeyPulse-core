package keypulse.core.tools
import spinal.core._
import keypulse.core.matrix._
import keypulse.core.hid._
import keypulse.core.keyboard._
object GenerateKeyboardOoc extends App {
  SpinalConfig(targetDirectory = "generated/ooc",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC),
    defaultClockDomainFrequency = FixedFrequency(100 MHz))
    .generateVerilog(new KeyboardCore(KeyboardCoreConfig(MatrixScanConfig.fast2xN(52),
      BootKeyMap((4 until 100) ++ (0xe0 to 0xe7)))))
}

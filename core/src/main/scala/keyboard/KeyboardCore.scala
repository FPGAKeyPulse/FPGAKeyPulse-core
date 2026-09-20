package keypulse.core.keyboard

import spinal.core._
import spinal.lib._
import keypulse.core.matrix._
import keypulse.core.hid._

case class KeyboardCoreConfig(
    scan: MatrixScanConfig,
    keyMap: BootKeyMap,
    pressSamples: Int = 1,
    releaseSamples: Int = 5000
) {
  require(scan.keyCount == keyMap.usages.length, "one mapping is required for every matrix position")
}

/** Matrix-to-HID report core. USB PHY/controller/endpoint ACK and retries are external.
  * A send pulse is accepted only when canSend is true. The accepted report is a
  * snapshot; subsequent key changes never mutate its byte stream under backpressure.
  */
class KeyboardCore(config: KeyboardCoreConfig) extends Component {
  val io = new Bundle {
    val cols = in Bits(config.scan.colCount bits)
    val rows = out Bits(config.scan.rowCount bits)
    val send = in Bool()
    val canSend = out Bool()
    val report = master(Stream(Fragment(Bits(8 bits))))
    val stateValid = out Bool()
    val currentReport = out Bits(64 bits)
    val changed = out Bool()
  }
  val scan = new MatrixScan(config.scan)
  val debounce = new KeyDebounce(KeyDebounceConfig(config.scan.keyCount, config.pressSamples, config.releaseSamples))
  val mapper = new BootKeyMapper(config.keyMap)
  val hid = new HidKeyboard()
  scan.io.cols := io.cols
  io.rows := scan.io.rows
  debounce.io.samples.valid := scan.io.frameValid
  debounce.io.samples.payload := scan.io.frameKeys
  mapper.io.samples << debounce.io.filtered
  hid.io.modifiers := mapper.io.modifiers
  hid.io.keyCodes := mapper.io.keyCodes
  val haveState = Reg(Bool()) init(False)
  when(mapper.io.valid) { haveState := True }
  hid.io.send := io.send && haveState
  io.canSend := haveState && hid.io.canSend
  io.report << hid.io.report
  io.stateValid := haveState
  io.currentReport := hid.io.reportBits
  io.changed := haveState && hid.io.changed
}

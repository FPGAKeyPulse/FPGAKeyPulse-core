package keypulse.core.matrix

import spinal.core._
import spinal.lib._

case class KeyDebounceConfig(keyCount: Int, pressSamples: Int = 1, releaseSamples: Int = 5000) {
  require(keyCount > 0)
  require(pressSamples > 0 && releaseSamples > 0)
}

/** Per-key consecutive-sample filter. Counts input frames, not clock cycles.
  * Default eager press adds one core clock; release needs 5000 consecutive clear
  * frames (5 ms at 1 MHz). Eager press does not reject the first electrical glitch.
  * Use pressSamples > 1 when press-side noise rejection is required.
  */
class KeyDebounce(config: KeyDebounceConfig) extends Component {
  val io = new Bundle {
    val samples = slave(Flow(Bits(config.keyCount bits)))
    val filtered = master(Flow(Bits(config.keyCount bits)))
    val changed = out Bool()
  }
  val state = Reg(Bits(config.keyCount bits)) init(0)
  val nextState = Bits(config.keyCount bits)
  nextState := state
  val width = log2Up(config.pressSamples max config.releaseSamples) max 1
  for (key <- 0 until config.keyCount) {
    val count = Reg(UInt(width bits)) init(0)
    val limit = Mux(io.samples.payload(key),
      U(config.pressSamples - 1, width bits), U(config.releaseSamples - 1, width bits))
    when(io.samples.valid) {
      when(io.samples.payload(key) === state(key)) {
        count := 0
      } otherwise {
        when(count === limit) {
          nextState(key) := io.samples.payload(key)
          count := 0
        } otherwise {
          count := count + 1
        }
      }
    }
  }
  state := nextState
  io.filtered.payload := state
  io.filtered.valid := RegNext(io.samples.valid) init(False)
  io.changed := RegNext(io.samples.valid && nextState =/= state) init(False)
}

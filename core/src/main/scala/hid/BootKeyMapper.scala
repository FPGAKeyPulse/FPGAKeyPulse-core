package keypulse.core.hid

import spinal.core._
import spinal.lib._

case class BootKeyMap(usages: Seq[Int]) {
  require(usages.nonEmpty, "map at least one physical key")
  require(usages.forall(u => u == 0 || (u >= 4 && u <= 0x65) || (u >= 0xe0 && u <= 0xe7)),
    "usage must be unmapped, a supported boot normal key, or a modifier")
}

/** Two-clock bitmap-to-boot-report pipeline. Accepts one frame every clock.
  * Duplicate physical mappings are ORed. Normal usages are sorted ascending;
  * more than six distinct normal keys produces ErrorRollOver in every slot.
  */
class BootKeyMapper(keyMap: BootKeyMap) extends Component {
  val io = new Bundle {
    val samples = slave(Flow(Bits(keyMap.usages.length bits)))
    val valid = out Bool()
    val modifiers = out Bits(8 bits)
    val keyCodes = out Vec(Bits(8 bits), 6)
    val rollover = out Bool()
  }
  def active(usage: Int): Bool = keyMap.usages.zipWithIndex.collect {
    case (u, i) if u == usage => io.samples.payload(i)
  }.foldLeft(False: Bool)(_ || _)
  val normal = keyMap.usages.filter(u => u >= 4 && u <= 0x65).distinct.sorted
  // A constant inactive entry handles modifier-only/unmapped matrices.
  val codes = if (normal.nonEmpty) normal else Seq(0)
  val flags = codes.map(u => if (u == 0) False else active(u))
  val width = log2Up(codes.length + 1) max 1
  // Parallel prefix network: logarithmic combinational depth, no serial priority chain.
  var prefix: Seq[UInt] = flags.map(_.asUInt.resize(width))
  var distance = 1
  while (distance < codes.length) {
    val old = prefix
    val step = distance
    prefix = old.indices.map(i => if (i >= step) (old(i) + old(i-step)).resize(width) else old(i))
    distance *= 2
  }
  val activeReg = Vec.fill(codes.length)(Reg(Bool()) init(False))
  val ranks = Vec.fill(codes.length)(Reg(UInt(width bits)) init(0))
  val mods = Bits(8 bits)
  for (i <- 0 until 8) mods(i) := active(0xe0+i)
  val modsReg = Reg(Bits(8 bits)) init(0)
  val overflow = Reg(Bool()) init(False)
  val stageValid = RegNext(io.samples.valid) init(False)
  when(io.samples.valid) {
    modsReg := mods
    overflow := (if (codes.length > 6) prefix.last > 6 else False)
    for (i <- codes.indices) {
      activeReg(i) := flags(i)
      ranks(i) := (if (i == 0) U(0, width bits) else prefix(i-1))
    }
  }
  val resultMods = Reg(Bits(8 bits)) init(0)
  val resultCodes = Vec.fill(6)(Reg(Bits(8 bits)) init(0))
  val resultOverflow = Reg(Bool()) init(False)
  when(stageValid) {
    resultMods := modsReg
    resultOverflow := overflow
    for (slot <- 0 until 6) {
      val selected = codes.indices.map { i =>
        // Avoid out-of-range rank constants in tiny matrices.
        if (slot >= codes.length) B(0, 8 bits)
        else Mux(activeReg(i) && ranks(i) === slot, B(codes(i), 8 bits), B(0, 8 bits))
      }.reduce(_ | _)
      resultCodes(slot) := Mux(overflow, B(HidUsage.ErrorRollOver, 8 bits), selected)
    }
  }
  io.valid := RegNext(stageValid) init(False)
  io.modifiers := resultMods
  io.keyCodes := resultCodes
  io.rollover := resultOverflow
}

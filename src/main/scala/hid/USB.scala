package keypulse.core.hid

import spinal.core._
import spinal.lib._

case class HidKeyboardConfig(
    keySlots: Int = 6,
    reportBytes: Int = 8
) {
  require(keySlots == 6, "USB HID boot keyboard reports have exactly 6 normal key slots")
  require(reportBytes == 8, "USB HID boot keyboard input reports are 8 bytes")

  val reportBits: Int = reportBytes * 8
}

case class HidKeyboardIo(config: HidKeyboardConfig) extends Bundle {
  /*
   * USB HID 是 USB 协议里的 Human Interface Device 类，键盘、鼠标、游戏手柄
   * 都可以用它描述自己。主机枚举设备时，会先读取 USB 标准描述符，知道这是一个
   * Interface Class = 0x03 的 HID 接口；然后再读取 HID 描述符和 Report
   * Descriptor，知道设备每次通过 interrupt IN endpoint 返回的数据格式。
   *
   * 这里实现的是最常见、BIOS/UEFI 也能识别的 Boot Keyboard 输入报告：
   *
   *   Byte 0: modifier 位图
   *           bit0 Left Ctrl,  bit1 Left Shift, bit2 Left Alt,  bit3 Left GUI
   *           bit4 Right Ctrl, bit5 Right Shift,bit6 Right Alt, bit7 Right GUI
   *   Byte 1: reserved，固定为 0
   *   Byte 2..7: 最多 6 个普通按键的 HID Usage ID
   *
   * 普通按键不是 ASCII，而是 HID Usage Tables 里的 Keyboard/Keypad usage。
   * 例如 A=0x04, B=0x05, 1=0x1E, Enter=0x28, Esc=0x29。大小写不是不同
   * keycode，而是同一个 keycode 加 Shift modifier。
   *
   * Boot Keyboard 的限制是最多同时上报 6 个普通键，也就是常说的 6KRO。
   * 如果需要 NKRO，Report Descriptor 和报告格式都要换成 bitmap 形式；但 Boot
   * 格式兼容性最好，适合作为这个 core 的第一版 USB HID 边界。
   */
  val modifiers = in Bits (8 bits)
  val keyCodes = in Vec (Bits(8 bits), config.keySlots)

  /*
   * send 是“USB IN token 到来 / endpoint 需要一个新包”的抽象脉冲。
   * 真正的 USB 总线层负责处理 reset、setup packet、地址、CRC、ACK/NAK 等；
   * 本模块只负责把当前键盘状态打包成 HID report，并按 USB endpoint 常见的
   * byte stream 形式输出。
   */
  val send = in Bool ()
  val canSend = out Bool ()

  val reportBytes = out Vec (Bits(8 bits), config.reportBytes)
  val reportBits = out Bits (config.reportBits bits)
  val changed = out Bool ()

  val report = master Stream (Fragment(Bits(8 bits)))
}

class HidKeyboard(config: HidKeyboardConfig = HidKeyboardConfig()) extends Component {
  val io = HidKeyboardIo(config)

  val currentBytes = Vec(Bits(8 bits), config.reportBytes)
  currentBytes(0) := io.modifiers
  currentBytes(1) := 0
  for (slot <- 0 until config.keySlots) {
    currentBytes(slot + 2) := io.keyCodes(slot)
  }

  val currentReport = currentBytes.asBits
  val previousReport = Reg(Bits(config.reportBits bits)) init (0)

  io.reportBytes := currentBytes
  io.reportBits := currentReport
  io.changed := currentReport =/= previousReport

  /*
   * HID interrupt IN endpoint 不是设备主动推送，而是主机按 bInterval 轮询。
   * 主机发 IN token 时，endpoint 可以返回一个 report，也可以 NAK 表示暂无数据。
   * 这里用 send 表示“上层 endpoint 决定现在发送一次”；模块会锁存当时的 8 字节
   * report，然后连续输出 8 个 byte，最后一个 byte 的 last 置 1。
   */
  val sending = Reg(Bool()) init (False)
  val byteIndex = Reg(UInt(log2Up(config.reportBytes) bits)) init (0)
  val txReport = Reg(Bits(config.reportBits bits)) init (0)
  val txBytes = Vec(Bits(8 bits), config.reportBytes)

  for (byte <- 0 until config.reportBytes) {
    txBytes(byte) := txReport((byte + 1) * 8 - 1 downto byte * 8)
  }

  io.canSend := !sending

  io.report.valid := sending
  io.report.payload.fragment := txBytes(byteIndex)
  io.report.payload.last := byteIndex === (config.reportBytes - 1)

  when(!sending && io.send) {
    sending := True
    byteIndex := 0
    txReport := currentReport
    previousReport := currentReport
  }

  when(io.report.fire) {
    when(byteIndex === (config.reportBytes - 1)) {
      sending := False
      byteIndex := 0
    } otherwise {
      byteIndex := byteIndex + 1
    }
  }
}

object HidUsage {
  val NoEvent: Int = 0x00
  val ErrorRollOver: Int = 0x01

  val A: Int = 0x04
  val B: Int = 0x05
  val C: Int = 0x06
  val D: Int = 0x07
  val E: Int = 0x08
  val F: Int = 0x09
  val G: Int = 0x0a
  val H: Int = 0x0b
  val I: Int = 0x0c
  val J: Int = 0x0d
  val K: Int = 0x0e
  val L: Int = 0x0f
  val M: Int = 0x10
  val N: Int = 0x11
  val O: Int = 0x12
  val P: Int = 0x13
  val Q: Int = 0x14
  val R: Int = 0x15
  val S: Int = 0x16
  val T: Int = 0x17
  val U: Int = 0x18
  val V: Int = 0x19
  val W: Int = 0x1a
  val X: Int = 0x1b
  val Y: Int = 0x1c
  val Z: Int = 0x1d

  val Num1: Int = 0x1e
  val Num2: Int = 0x1f
  val Num3: Int = 0x20
  val Num4: Int = 0x21
  val Num5: Int = 0x22
  val Num6: Int = 0x23
  val Num7: Int = 0x24
  val Num8: Int = 0x25
  val Num9: Int = 0x26
  val Num0: Int = 0x27

  val Enter: Int = 0x28
  val Escape: Int = 0x29
  val Backspace: Int = 0x2a
  val Tab: Int = 0x2b
  val Space: Int = 0x2c
}

object HidModifier {
  val LeftCtrl: Int = 1 << 0
  val LeftShift: Int = 1 << 1
  val LeftAlt: Int = 1 << 2
  val LeftGui: Int = 1 << 3
  val RightCtrl: Int = 1 << 4
  val RightShift: Int = 1 << 5
  val RightAlt: Int = 1 << 6
  val RightGui: Int = 1 << 7
}

object UsbHidKeyboardDescriptors {
  private def le16(value: Int): Seq[Int] = Seq(value & 0xff, (value >> 8) & 0xff)

  /*
   * Device Descriptor 是 USB 枚举的第一层身份信息。这里把 bDeviceClass 设为 0，
   * 表示 class 信息放在 interface descriptor 里；这也是 HID 键盘常用写法。
   * idVendor/idProduct 只是占位，真正发布硬件时需要替换成你自己的 VID/PID。
   */
  val device: Seq[Int] =
    Seq(
      18,
      0x01
    ) ++ le16(0x0200) ++ Seq(
      0x00,
      0x00,
      0x00,
      64
    ) ++ le16(0x1209) ++ le16(0x0001) ++ le16(0x0100) ++ Seq(
      1,
      2,
      3,
      1
    )

  /*
   * Report Descriptor 是 HID 的核心。它是一段小型“格式语言”，逐项说明 report
   * 的 usage page、usage、位宽、数量、逻辑范围，以及每个字段是 Input 还是
   * Output。主机并不猜键盘报告格式，而是解析这里的描述。
   */
  val report: Seq[Int] = Seq(
    0x05, 0x01, // Usage Page: Generic Desktop
    0x09, 0x06, // Usage: Keyboard
    0xa1, 0x01, // Collection: Application
    0x05, 0x07, // Usage Page: Keyboard/Keypad
    0x19, 0xe0, // Usage Minimum: Left Control
    0x29, 0xe7, // Usage Maximum: Right GUI
    0x15, 0x00, // Logical Minimum: 0
    0x25, 0x01, // Logical Maximum: 1
    0x75, 0x01, // Report Size: 1 bit
    0x95, 0x08, // Report Count: 8 modifier bits
    0x81, 0x02, // Input: Data, Variable, Absolute
    0x95, 0x01, // Report Count: 1 reserved byte
    0x75, 0x08, // Report Size: 8 bits
    0x81, 0x03, // Input: Constant, Variable, Absolute
    0x95, 0x05, // Report Count: 5 keyboard LED bits
    0x75, 0x01, // Report Size: 1 bit
    0x05, 0x08, // Usage Page: LEDs
    0x19, 0x01, // Usage Minimum: Num Lock
    0x29, 0x05, // Usage Maximum: Kana
    0x91, 0x02, // Output: Data, Variable, Absolute
    0x95, 0x01, // Report Count: 1 padding field
    0x75, 0x03, // Report Size: 3 bits
    0x91, 0x03, // Output: Constant, Variable, Absolute
    0x95, 0x06, // Report Count: 6 key slots
    0x75, 0x08, // Report Size: 8 bits
    0x15, 0x00, // Logical Minimum: 0
    0x25, 0x65, // Logical Maximum: 101
    0x05, 0x07, // Usage Page: Keyboard/Keypad
    0x19, 0x00, // Usage Minimum: No event
    0x29, 0x65, // Usage Maximum: Keyboard Application
    0x81, 0x00, // Input: Data, Array, Absolute
    0xc0 // End Collection
  )

  /*
   * Configuration Descriptor 描述“这个配置里有哪些 interface 和 endpoint”。
   * 下面的配置包含：
   *   - 1 个 HID keyboard interface
   *   - 1 个 HID descriptor，指向上面的 report descriptor
   *   - 1 个 interrupt IN endpoint，地址 0x81，max packet 8 byte
   *
   * HID 键盘通常用 interrupt IN endpoint，因为主机周期性轮询它，延迟稳定，
   * 同时不需要 bulk endpoint 那种大吞吐。
   */
  val configuration: Seq[Int] = {
    val totalLength = 9 + 9 + 9 + 7

    Seq(
      9,
      0x02
    ) ++ le16(totalLength) ++ Seq(
      1, 1, 0, 0xa0, 50, 9, 0x04, 0, 0, 1, 0x03, 0x01, 0x01, 0, 9, 0x21
    ) ++ le16(0x0111) ++ Seq(
      0,
      1,
      0x22
    ) ++ le16(report.length) ++ Seq(
      7,
      0x05,
      0x81,
      0x03
    ) ++ le16(8) ++ Seq(
      1
    )
  }
}

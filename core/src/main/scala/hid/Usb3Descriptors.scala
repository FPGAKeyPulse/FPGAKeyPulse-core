package keypulse.core.hid

/** Descriptor data for an external programmable USB 3 device controller.
  * This is not a SuperSpeed PHY, link layer or enumeration engine.
  * IDs, power and exit latency must match the actual controller/board firmware.
  */
case class Usb3HidDescriptorConfig(
    vendorId: Int,
    productId: Int,
    maxPowerMa: Int,
    u1ExitLatencyUs: Int,
    u2ExitLatencyUs: Int,
    intervalExponent: Int = 1
) {
  require(vendorId > 0 && vendorId <= 0xffff)
  require(productId >= 0 && productId <= 0xffff)
  require(maxPowerMa > 0 && maxPowerMa <= 900)
  require(u1ExitLatencyUs >= 0 && u1ExitLatencyUs <= 10)
  require(u2ExitLatencyUs >= 0 && u2ExitLatencyUs <= 2047)
  require(intervalExponent >= 1 && intervalExponent <= 16)
  val serviceIntervalUs: Long = 125L << (intervalExponent - 1)
}

class Usb3HidDescriptors(config: Usb3HidDescriptorConfig) {
  private def le16(v: Int): Seq[Int] = Seq(v & 255, (v >> 8) & 255)
  val report: Seq[Int] = UsbHidKeyboardDescriptors.report
  // SuperSpeed EP0 uses an exponent: 2^9 = 512 bytes. No string descriptors advertised.
  val device: Seq[Int] = Seq(18, 1) ++ le16(0x0300) ++ Seq(0, 0, 0, 9) ++
    le16(config.vendorId) ++ le16(config.productId) ++ le16(0x0100) ++ Seq(0, 0, 0, 1)
  // USB2 extension capability: LPM not advertised. SS capability: FS/HS/SS supported,
  // full speed is the lowest speed with all functionality (bFunctionalitySupport=1).
  // The external controller must also provide appropriate USB2 descriptors/fallback.
  val bos: Seq[Int] = Seq(5, 15) ++ le16(22) ++ Seq(2) ++
    Seq(7, 16, 2, 0, 0, 0, 0) ++
    Seq(10, 16, 3, 0) ++ le16(0x000e) ++
    Seq(1, config.u1ExitLatencyUs) ++ le16(config.u2ExitLatencyUs)
  val configuration: Seq[Int] = Seq(9, 2) ++ le16(40) ++
    Seq(1, 1, 0, 0x80, (config.maxPowerMa + 7) / 8) ++
    Seq(9, 4, 0, 0, 1, 3, 1, 1, 0) ++
    Seq(9, 0x21) ++ le16(0x0111) ++ Seq(0, 1, 0x22) ++ le16(report.length) ++
    Seq(7, 5, 0x81, 3) ++ le16(8) ++ Seq(config.intervalExponent) ++
    // One 8-byte interrupt report per service interval, no bursts.
    Seq(6, 0x30, 0, 0) ++ le16(8)
}

package keypulse.core.hid

import keypulse.test._
import spinal.core._
import spinal.core.sim._
import spinal.sim._

class UsbProtocolTest extends SpinalTester[UsbHidDevice] {
  override def createTop: UsbHidDevice = new UsbHidDevice

  override def sim: Seq[SimTest[UsbHidDevice]] = Seq(
    SimTest("enumeration-and-interrupt-in") { dut =>
      dut.clockDomain.forkStimulus(60 MHz)
      initialize(dut)

      resetBus(dut)
      getDescriptor(dut, descriptorType = 1, descriptorIndex = 0, expected = UsbHidKeyboardDescriptors.device)
      setAddress(dut, 5)
      setConfiguration(dut, address = 5, configuration = 1)

      dut.io.modifiers #= HidModifier.LeftShift
      dut.io.keyCodes(0) #= HidUsage.A
      dut.clockDomain.waitSampling()

      sendPacket(dut, token(UsbPid.IN, address = 5, endpoint = 1))
      val firstReport = readPacket(dut)
      verifyDataPacket(firstReport, UsbPid.DATA0, Seq(HidModifier.LeftShift, 0, HidUsage.A, 0, 0, 0, 0, 0))
      sendPacket(dut, handshake(UsbPid.ACK))

      dut.io.modifiers #= 0
      dut.io.keyCodes(0) #= HidUsage.Enter
      dut.clockDomain.waitSampling()

      sendPacket(dut, token(UsbPid.IN, address = 5, endpoint = 1))
      val secondReport = readPacket(dut)
      verifyDataPacket(secondReport, UsbPid.DATA1, Seq(0, 0, HidUsage.Enter, 0, 0, 0, 0, 0))
      sendPacket(dut, handshake(UsbPid.ACK))
    },
    SimTest("retries-unacknowledged-report") { dut =>
      dut.clockDomain.forkStimulus(60 MHz)
      initialize(dut)
      resetBus(dut)
      setConfiguration(dut, address = 0, configuration = 1)

      dut.io.keyCodes(0) #= HidUsage.B
      sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 1))
      val original = readPacket(dut)

      dut.io.keyCodes(0) #= HidUsage.C
      sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 1))
      val retry = readPacket(dut)
      retry shouldBe original

      sendPacket(dut, handshake(UsbPid.ACK))
      sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 1))
      val next = readPacket(dut)
      verifyDataPacket(next, UsbPid.DATA1, Seq(0, 0, HidUsage.C, 0, 0, 0, 0, 0))
    },
    SimTest("handles-hid-class-output") { dut =>
      dut.clockDomain.forkStimulus(60 MHz)
      initialize(dut)
      resetBus(dut)

      controlSetup(dut, address = 0, Seq(0x21, 0x09, 0, 2, 0, 0, 1, 0))
      sendPacket(dut, token(UsbPid.OUT, address = 0, endpoint = 0))
      sendPacket(dut, data(UsbPid.DATA1, Seq(0x05)))
      readPacket(dut) shouldBe handshake(UsbPid.ACK)
      dut.io.ledState.toBigInt shouldBe 0x05

      sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 0))
      verifyDataPacket(readPacket(dut), UsbPid.DATA1, Seq.empty)
      sendPacket(dut, handshake(UsbPid.ACK))

      controlSetup(dut, address = 0, Seq(0x21, 0x0b, 0, 0, 0, 0, 0, 0))
      sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 0))
      verifyDataPacket(readPacket(dut), UsbPid.DATA1, Seq.empty)
      sendPacket(dut, handshake(UsbPid.ACK))
      dut.io.protocol.toBoolean shouldBe false
    },
    SimTest("rejects-corrupt-setup") { dut =>
      dut.clockDomain.forkStimulus(60 MHz)
      initialize(dut)
      resetBus(dut)

      sendPacket(dut, token(UsbPid.SETUP, address = 0, endpoint = 0))
      val corrupt = data(UsbPid.DATA0, Seq(0x80, 0x06, 0, 1, 0, 0, 18, 0)).updated(9, 0x55)
      sendPacket(dut, corrupt)

      for (_ <- 0 until 12) {
        dut.io.phyTx.valid.toBoolean shouldBe false
        dut.clockDomain.waitSampling()
      }
    }
  )

  private def initialize(dut: UsbHidDevice): Unit = {
    dut.io.busReset #= false
    dut.io.phyRx.data #= 0
    dut.io.phyRx.valid #= false
    dut.io.phyRx.active #= false
    dut.io.phyRx.error #= false
    dut.io.phyTx.ready #= false
    dut.io.modifiers #= 0
    for (slot <- 0 until 6) dut.io.keyCodes(slot) #= 0
    dut.clockDomain.waitSampling(3)
  }

  private def resetBus(dut: UsbHidDevice): Unit = {
    dut.io.busReset #= true
    dut.clockDomain.waitSampling(3)
    dut.io.busReset #= false
    dut.clockDomain.waitSampling(2)
    dut.io.address.toBigInt shouldBe 0
    dut.io.configured.toBoolean shouldBe false
  }

  private def sendPacket(dut: UsbHidDevice, bytes: Seq[Int]): Unit = {
    dut.io.phyRx.active #= true
    dut.io.phyRx.valid #= false
    dut.clockDomain.waitSampling()
    for (byte <- bytes) {
      dut.io.phyRx.data #= byte
      dut.io.phyRx.valid #= true
      dut.clockDomain.waitSampling()
    }
    dut.io.phyRx.valid #= false
    dut.io.phyRx.active #= false
    dut.clockDomain.waitSampling(3)
  }

  private def readPacket(dut: UsbHidDevice): Seq[Int] = {
    var timeout = 0
    while (!dut.io.phyTx.valid.toBoolean && timeout < 100) {
      dut.clockDomain.waitSampling()
      timeout += 1
    }
    withClue("USB device did not start a response packet") {
      dut.io.phyTx.valid.toBoolean shouldBe true
    }

    val bytes = collection.mutable.ArrayBuffer.empty[Int]
    dut.io.phyTx.ready #= true
    sleep(1)
    while (dut.io.phyTx.valid.toBoolean) {
      bytes += dut.io.phyTx.data.toInt
      dut.clockDomain.waitSampling()
      sleep(1)
    }
    dut.io.phyTx.ready #= false
    bytes.toSeq
  }

  private def getDescriptor(
      dut: UsbHidDevice,
      descriptorType: Int,
      descriptorIndex: Int,
      expected: Seq[Int]
  ): Unit = {
    val length = expected.length min 64
    controlSetup(
      dut,
      address = 0,
      Seq(0x80, 0x06, descriptorIndex, descriptorType, 0, 0, length, 0)
    )

    sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 0))
    verifyDataPacket(readPacket(dut), UsbPid.DATA1, expected.take(length))
    sendPacket(dut, handshake(UsbPid.ACK))

    sendPacket(dut, token(UsbPid.OUT, address = 0, endpoint = 0))
    sendPacket(dut, data(UsbPid.DATA1, Seq.empty))
    readPacket(dut) shouldBe handshake(UsbPid.ACK)
  }

  private def setAddress(dut: UsbHidDevice, address: Int): Unit = {
    controlSetup(dut, address = 0, Seq(0x00, 0x05, address, 0, 0, 0, 0, 0))
    sendPacket(dut, token(UsbPid.IN, address = 0, endpoint = 0))
    verifyDataPacket(readPacket(dut), UsbPid.DATA1, Seq.empty)
    dut.io.address.toBigInt shouldBe 0
    sendPacket(dut, handshake(UsbPid.ACK))
    dut.io.address.toBigInt shouldBe address
  }

  private def setConfiguration(dut: UsbHidDevice, address: Int, configuration: Int): Unit = {
    controlSetup(dut, address, Seq(0x00, 0x09, configuration, 0, 0, 0, 0, 0))
    sendPacket(dut, token(UsbPid.IN, address, endpoint = 0))
    verifyDataPacket(readPacket(dut), UsbPid.DATA1, Seq.empty)
    sendPacket(dut, handshake(UsbPid.ACK))
    dut.io.configured.toBoolean shouldBe (configuration == 1)
  }

  private def controlSetup(dut: UsbHidDevice, address: Int, setup: Seq[Int]): Unit = {
    setup.length shouldBe 8
    sendPacket(dut, token(UsbPid.SETUP, address, endpoint = 0))
    sendPacket(dut, data(UsbPid.DATA0, setup))
    readPacket(dut) shouldBe handshake(UsbPid.ACK)
  }

  private def token(pid: Int, address: Int, endpoint: Int): Seq[Int] = {
    val tokenBits = (address & 0x7f) | ((endpoint & 0xf) << 7)
    val crc = UsbCrc.crc5Token(address, endpoint)
    Seq(
      UsbPid.byte(pid),
      tokenBits & 0xff,
      ((tokenBits >> 8) & 0x07) | (crc << 3)
    )
  }

  private def data(pid: Int, payload: Seq[Int]): Seq[Int] = {
    val crc = UsbCrc.crc16(payload)
    Seq(UsbPid.byte(pid)) ++ payload ++ Seq(crc & 0xff, (crc >> 8) & 0xff)
  }

  private def handshake(pid: Int): Seq[Int] = Seq(UsbPid.byte(pid))

  private def verifyDataPacket(packet: Seq[Int], pid: Int, payload: Seq[Int]): Unit = {
    packet.length shouldBe payload.length + 3
    packet.head shouldBe UsbPid.byte(pid)
    packet.slice(1, 1 + payload.length) shouldBe payload
    val crc = UsbCrc.crc16(payload)
    packet.takeRight(2) shouldBe Seq(crc & 0xff, (crc >> 8) & 0xff)
  }
}

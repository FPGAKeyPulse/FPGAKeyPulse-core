package keypulse.core.hid

import spinal.core._
import spinal.lib._

private object UsbControlState extends SpinalEnum {
  val Idle, DataIn, DataOut, StatusIn, StatusOut, Stalled = newElement()
}

private object UsbCommitAction extends SpinalEnum {
  val None, SetAddress, SetConfiguration = newElement()
}

private object UsbTxKind extends SpinalEnum {
  val Handshake, ControlData, ControlStatus, HidData = newElement()
}

case class UsbHidProtocolIo(maxPayloadBytes: Int) extends Bundle {
  val busReset = in Bool()
  val rx = slave(Flow(UsbRxPacket(maxPayloadBytes)))
  val tx = master(Stream(UsbTxPacket(maxPayloadBytes)))

  val modifiers = in Bits (8 bits)
  val keyCodes = in Vec (Bits(8 bits), 6)

  val address = out UInt (7 bits)
  val configured = out Bool()
  val ledState = out Bits (5 bits)
  val protocol = out Bool()
}

/**
  * USB 1.1 control endpoint and HID boot-keyboard interrupt endpoint.
  *
  * Endpoint 0 implements the requests needed by standard USB enumeration plus the HID class
  * requests used by boot-keyboard hosts. Endpoint 1 is an 8-byte interrupt IN endpoint.
  * DATA toggles are committed only after ACK, so an interrupted transaction is retried with
  * the same PID and payload.
  */
class UsbHidProtocol(maxPayloadBytes: Int = 64) extends Component {
  require(maxPayloadBytes >= 64, "USB endpoint zero requires a 64-byte packet buffer")

  val io = UsbHidProtocolIo(maxPayloadBytes)

  private val deviceAddress = Reg(UInt(7 bits)) init (0)
  private val configuration = Reg(UInt(8 bits)) init (0)
  private val ledState = Reg(Bits(5 bits)) init (0)
  private val hidProtocol = Reg(Bool()) init (True) // true = report, false = boot
  private val idleRate = Reg(Bits(8 bits)) init (0)

  private val controlState = Reg(UsbControlState()) init (UsbControlState.Idle)
  private val commitAction = Reg(UsbCommitAction()) init (UsbCommitAction.None)
  private val pendingAddress = Reg(UInt(7 bits)) init (0)
  private val pendingConfiguration = Reg(UInt(8 bits)) init (0)

  private val pendingTokenValid = Reg(Bool()) init (False)
  private val pendingTokenPid = Reg(Bits(4 bits)) init (0)
  private val pendingEndpoint = Reg(UInt(4 bits)) init (0)

  private val responseLength = Reg(UInt(log2Up(maxPayloadBytes + 1) bits)) init (0)
  private val response = Vec(Reg(Bits(8 bits)) init (0), maxPayloadBytes)

  private val endpointOneData1 = Reg(Bool()) init (False)
  private val txPending = Reg(Bool()) init (False)
  private val txPid = Reg(Bits(4 bits)) init (0)
  private val txLength = Reg(UInt(log2Up(maxPayloadBytes + 1) bits)) init (0)
  private val txPayload = Vec(Reg(Bits(8 bits)) init (0), maxPayloadBytes)
  private val txKind = Reg(UsbTxKind()) init (UsbTxKind.Handshake)
  private val awaitingAck = Reg(Bool()) init (False)

  io.address := deviceAddress
  io.configured := configuration === 1
  io.ledState := ledState
  io.protocol := hidProtocol

  io.tx.valid := txPending
  io.tx.payload.pid := txPid
  io.tx.payload.payloadLength := txLength
  for (index <- 0 until maxPayloadBytes) {
    io.tx.payload.payload(index) := txPayload(index)
  }

  private def queueHandshake(pid: Int): Unit = {
    txPending := True
    txPid := pid
    txLength := 0
    txKind := UsbTxKind.Handshake
  }

  private def queueControlData(): Unit = {
    txPending := True
    txPid := UsbPid.DATA1
    txLength := responseLength
    txKind := UsbTxKind.ControlData
    for (index <- 0 until maxPayloadBytes) {
      txPayload(index) := response(index)
    }
  }

  private def queueControlStatus(): Unit = {
    txPending := True
    txPid := UsbPid.DATA1
    txLength := 0
    txKind := UsbTxKind.ControlStatus
  }

  private def queueHidReport(): Unit = {
    txPending := True
    txPid := Mux(endpointOneData1, B(UsbPid.DATA1, 4 bits), B(UsbPid.DATA0, 4 bits))
    txLength := 8
    txKind := UsbTxKind.HidData
    txPayload(0) := io.modifiers
    txPayload(1) := 0
    for (slot <- 0 until 6) {
      txPayload(slot + 2) := io.keyCodes(slot)
    }
  }

  private def setResponse(bytes: Seq[Int], requestedLength: UInt): Unit = {
    for (index <- bytes.indices) {
      response(index) := bytes(index)
    }
    when(requestedLength < bytes.length) {
      responseLength := requestedLength.resized
    } otherwise {
      responseLength := bytes.length
    }
  }

  private val stringLanguage = Seq(4, 0x03, 0x09, 0x04)
  private def usbString(value: String): Seq[Int] =
    Seq(2 + value.length * 2, 0x03) ++ value.flatMap(character => Seq(character.toInt, 0))
  private val stringManufacturer = usbString("FPGAKeyPulse")
  private val stringProduct = usbString("FPGA Keyboard")
  private val stringSerial = usbString("0001")

  when(io.tx.fire) {
    txPending := False
    when(UsbPid.isData(txPid)) {
      awaitingAck := True
    }
  }

  when(io.busReset) {
    deviceAddress := 0
    configuration := 0
    controlState := UsbControlState.Idle
    commitAction := UsbCommitAction.None
    pendingTokenValid := False
    endpointOneData1 := False
    txPending := False
    awaitingAck := False
    hidProtocol := True
    idleRate := 0
  } otherwise {
    when(io.rx.valid && io.rx.payload.pidValid && io.rx.payload.crcValid) {
      val rx = io.rx.payload
      val addressed = rx.address === deviceAddress

      when(rx.pid === UsbPid.ACK && awaitingAck) {
        awaitingAck := False
        switch(txKind) {
          is(UsbTxKind.ControlData) {
            controlState := UsbControlState.StatusOut
          }
          is(UsbTxKind.ControlStatus) {
            controlState := UsbControlState.Idle
            switch(commitAction) {
              is(UsbCommitAction.SetAddress) {
                deviceAddress := pendingAddress
              }
              is(UsbCommitAction.SetConfiguration) {
                configuration := pendingConfiguration
                endpointOneData1 := False
              }
              default {}
            }
            commitAction := UsbCommitAction.None
          }
          is(UsbTxKind.HidData) {
            endpointOneData1 := !endpointOneData1
          }
          default {}
        }
      }

      when(UsbPid.isToken(rx.pid) && rx.pid =/= UsbPid.SOF) {
        when(addressed) {
          when(rx.pid === UsbPid.SETUP || rx.pid === UsbPid.OUT) {
            pendingTokenValid := True
            pendingTokenPid := rx.pid
            pendingEndpoint := rx.endpoint
            when(rx.pid === UsbPid.SETUP && rx.endpoint === 0) {
              // A new SETUP transaction always aborts the previous control transfer.
              controlState := UsbControlState.Idle
              awaitingAck := False
              commitAction := UsbCommitAction.None
            }
          }

          when(rx.pid === UsbPid.IN && !txPending) {
            when(awaitingAck) {
              // No ACK was observed. Requeue the exact packet already held in tx registers.
              txPending := True
            } elsewhen (rx.endpoint === 0) {
              switch(controlState) {
                is(UsbControlState.DataIn) {
                  queueControlData()
                }
                is(UsbControlState.StatusIn) {
                  queueControlStatus()
                }
                is(UsbControlState.Stalled) {
                  queueHandshake(UsbPid.STALL)
                }
                default {
                  queueHandshake(UsbPid.NAK)
                }
              }
            } elsewhen (rx.endpoint === 1) {
              when(configuration === 1) {
                queueHidReport()
              } otherwise {
                queueHandshake(UsbPid.NAK)
              }
            } otherwise {
              queueHandshake(UsbPid.STALL)
            }
          }
        }
      }

      when(UsbPid.isData(rx.pid) && pendingTokenValid) {
        pendingTokenValid := False

        when(pendingEndpoint === 0 && pendingTokenPid === UsbPid.SETUP && rx.pid === UsbPid.DATA0 && rx.payloadLength === 8) {
          val requestType = rx.payload(0)
          val request = rx.payload(1)
          val value = (rx.payload(3) ## rx.payload(2)).asUInt
          val requestedLength = (rx.payload(7) ## rx.payload(6)).asUInt

          for (index <- 0 until maxPayloadBytes) {
            response(index) := 0
          }
          responseLength := 0
          controlState := UsbControlState.Stalled
          commitAction := UsbCommitAction.None
          queueHandshake(UsbPid.ACK)

          // Standard device requests.
          when(requestType === 0x80 && request === 0x06) { // GET_DESCRIPTOR
            switch(value) {
              is(0x0100) {
                setResponse(UsbHidKeyboardDescriptors.device, requestedLength)
                controlState := UsbControlState.DataIn
              }
              is(0x0200) {
                setResponse(UsbHidKeyboardDescriptors.configuration, requestedLength)
                controlState := UsbControlState.DataIn
              }
              is(0x0300) {
                setResponse(stringLanguage, requestedLength)
                controlState := UsbControlState.DataIn
              }
              is(0x0301) {
                setResponse(stringManufacturer, requestedLength)
                controlState := UsbControlState.DataIn
              }
              is(0x0302) {
                setResponse(stringProduct, requestedLength)
                controlState := UsbControlState.DataIn
              }
              is(0x0303) {
                setResponse(stringSerial, requestedLength)
                controlState := UsbControlState.DataIn
              }
              default {}
            }
          }
          when(requestType === 0x81 && request === 0x06 && value === 0x2200) {
            setResponse(UsbHidKeyboardDescriptors.report, requestedLength)
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x81 && request === 0x06 && value === 0x2100) {
            setResponse(UsbHidKeyboardDescriptors.hid, requestedLength)
            controlState := UsbControlState.DataIn
          }
          when((requestType === 0x80 || requestType === 0x81 || requestType === 0x82) && request === 0x00) { // GET_STATUS
            setResponse(Seq(0, 0), requestedLength)
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x80 && request === 0x08) { // GET_CONFIGURATION
            response(0) := configuration.asBits
            responseLength := 1
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x81 && request === 0x0a) { // GET_INTERFACE
            response(0) := 0
            responseLength := 1
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x00 && request === 0x05 && requestedLength === 0) { // SET_ADDRESS
            pendingAddress := value(6 downto 0)
            commitAction := UsbCommitAction.SetAddress
            controlState := UsbControlState.StatusIn
          }
          when(requestType === 0x00 && request === 0x09 && requestedLength === 0) { // SET_CONFIGURATION
            pendingConfiguration := value(7 downto 0)
            commitAction := UsbCommitAction.SetConfiguration
            controlState := UsbControlState.StatusIn
          }
          when(requestType === 0x01 && request === 0x0b && requestedLength === 0) { // SET_INTERFACE
            controlState := UsbControlState.StatusIn
          }

          // HID boot-keyboard class requests.
          when(requestType === 0xa1 && request === 0x03) { // GET_PROTOCOL
            response(0) := hidProtocol.asBits.resized
            responseLength := 1
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x21 && request === 0x0b && requestedLength === 0) { // SET_PROTOCOL
            hidProtocol := value(0)
            controlState := UsbControlState.StatusIn
          }
          when(requestType === 0xa1 && request === 0x02) { // GET_IDLE
            response(0) := idleRate
            responseLength := 1
            controlState := UsbControlState.DataIn
          }
          when(requestType === 0x21 && request === 0x0a && requestedLength === 0) { // SET_IDLE
            idleRate := value(15 downto 8).asBits
            controlState := UsbControlState.StatusIn
          }
          when(requestType === 0x21 && request === 0x09 && requestedLength === 1) { // SET_REPORT (LEDs)
            controlState := UsbControlState.DataOut
          }
        } elsewhen (pendingEndpoint === 0 && pendingTokenPid === UsbPid.OUT) {
          switch(controlState) {
            is(UsbControlState.StatusOut) {
              when(rx.pid === UsbPid.DATA1 && rx.payloadLength === 0) {
                queueHandshake(UsbPid.ACK)
                controlState := UsbControlState.Idle
              } otherwise {
                queueHandshake(UsbPid.STALL)
              }
            }
            is(UsbControlState.DataOut) {
              when(rx.pid === UsbPid.DATA1 && rx.payloadLength === 1) {
                ledState := rx.payload(0)(4 downto 0)
                queueHandshake(UsbPid.ACK)
                controlState := UsbControlState.StatusIn
              } otherwise {
                queueHandshake(UsbPid.STALL)
              }
            }
            is(UsbControlState.Stalled) {
              queueHandshake(UsbPid.STALL)
            }
            default {
              queueHandshake(UsbPid.NAK)
            }
          }
        }
      }
    }
  }
}

case class UsbHidDeviceIo() extends Bundle {
  val busReset = in Bool()
  val phyRx = slave(UsbPhyRxIo())
  val phyTx = master(UsbPhyTxIo())
  val modifiers = in Bits (8 bits)
  val keyCodes = in Vec (Bits(8 bits), 6)
  val address = out UInt (7 bits)
  val configured = out Bool()
  val ledState = out Bits (5 bits)
  val protocol = out Bool()
}

/** Complete byte-level USB HID keyboard core with a UTMI-style PHY boundary. */
class UsbHidDevice extends Component {
  val io = UsbHidDeviceIo()

  private val receiver = new UsbPacketReceiver(64)
  private val protocol = new UsbHidProtocol(64)
  private val transmitter = new UsbPacketTransmitter(64)

  receiver.io.phy.data := io.phyRx.data
  receiver.io.phy.valid := io.phyRx.valid
  receiver.io.phy.active := io.phyRx.active
  receiver.io.phy.error := io.phyRx.error

  protocol.io.busReset := io.busReset
  protocol.io.rx << receiver.io.packet
  protocol.io.modifiers := io.modifiers
  protocol.io.keyCodes := io.keyCodes
  transmitter.io.packet << protocol.io.tx

  io.phyTx.data := transmitter.io.phy.data
  io.phyTx.valid := transmitter.io.phy.valid
  transmitter.io.phy.ready := io.phyTx.ready

  io.address := protocol.io.address
  io.configured := protocol.io.configured
  io.ledState := protocol.io.ledState
  io.protocol := protocol.io.protocol
}

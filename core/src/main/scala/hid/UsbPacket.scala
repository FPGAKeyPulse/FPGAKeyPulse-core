package keypulse.core.hid

import spinal.core._
import spinal.lib._

object UsbPid {
  val OUT: Int = 0x1
  val ACK: Int = 0x2
  val DATA0: Int = 0x3
  val SOF: Int = 0x5
  val IN: Int = 0x9
  val NAK: Int = 0xa
  val DATA1: Int = 0xb
  val STALL: Int = 0xe
  val SETUP: Int = 0xd

  def isToken(pid: Bits): Bool =
    pid === OUT || pid === IN || pid === SOF || pid === SETUP

  def isData(pid: Bits): Bool = pid === DATA0 || pid === DATA1

  def isHandshake(pid: Bits): Bool =
    pid === ACK || pid === NAK || pid === STALL

  def byte(pid: Int): Int = ((~pid & 0xf) << 4) | (pid & 0xf)
}

/** USB CRC helpers. Bits are processed least-significant first, as they are on USB. */
object UsbCrc {
  def crc5(data: Bits): Bits = {
    var crc = B(0x1f, 5 bits)
    for (bit <- 0 until data.getWidth) {
      val feedback = crc(0) ^ data(bit)
      crc = (crc |>> 1).asBits ^ Mux(feedback, B(0x14, 5 bits), B(0, 5 bits))
    }
    ~crc
  }

  def crc16Next(crc: Bits, data: Bits): Bits = {
    var next = crc
    for (bit <- 0 until 8) {
      val feedback = next(0) ^ data(bit)
      next = (next |>> 1).asBits ^ Mux(feedback, B(0xa001, 16 bits), B(0, 16 bits))
    }
    next
  }

  def crc16(bytes: Seq[Int]): Int = {
    var crc = 0xffff
    for (byte <- bytes) {
      for (bit <- 0 until 8) {
        val feedback = ((crc & 1) ^ ((byte >> bit) & 1)) != 0
        crc = crc >>> 1
        if (feedback) crc ^= 0xa001
      }
    }
    (~crc) & 0xffff
  }

  def crc5Token(address: Int, endpoint: Int): Int = {
    val token = (address & 0x7f) | ((endpoint & 0xf) << 7)
    var crc = 0x1f
    for (bit <- 0 until 11) {
      val feedback = ((crc & 1) ^ ((token >> bit) & 1)) != 0
      crc = crc >>> 1
      if (feedback) crc ^= 0x14
    }
    (~crc) & 0x1f
  }
}

case class UsbPhyRxIo() extends Bundle with IMasterSlave {
  val data = Bits(8 bits)
  val valid = Bool()
  val active = Bool()
  val error = Bool()

  override def asMaster(): Unit = {
    out(data, valid, active, error)
  }
}

case class UsbPhyTxIo() extends Bundle with IMasterSlave {
  val data = Bits(8 bits)
  val valid = Bool()
  val ready = Bool()

  override def asMaster(): Unit = {
    out(data, valid)
    in(ready)
  }
}

case class UsbRxPacket(maxPayloadBytes: Int) extends Bundle {
  val pid = Bits(4 bits)
  val pidValid = Bool()
  val crcValid = Bool()
  val address = UInt(7 bits)
  val endpoint = UInt(4 bits)
  val frameNumber = UInt(11 bits)
  val payloadLength = UInt(log2Up(maxPayloadBytes + 1) bits)
  val payload = Vec(Bits(8 bits), maxPayloadBytes)
}

/**
  * Packet decoder for an 8-bit UTMI-style receive interface.
  *
  * The PHY is responsible for line coding, bit stuffing, SYNC and EOP. This block receives
  * bytes from PID through CRC and emits one validated packet event when `active` falls.
  */
class UsbPacketReceiver(maxPayloadBytes: Int = 64) extends Component {
  require(maxPayloadBytes >= 8)

  val io = new Bundle {
    val phy = slave(UsbPhyRxIo())
    val packet = master(Flow(UsbRxPacket(maxPayloadBytes)))
  }

  private val countWidth = log2Up(maxPayloadBytes + 3)
  private val activePrevious = RegNext(io.phy.active) init (False)
  private val byteCount = Reg(UInt(countWidth bits)) init (0)
  private val pid = Reg(Bits(4 bits)) init (0)
  private val pidValid = Reg(Bool()) init (False)
  private val packetError = Reg(Bool()) init (False)
  private val tokenLow = Reg(Bits(8 bits)) init (0)
  private val tokenHigh = Reg(Bits(8 bits)) init (0)
  private val payloadBuffer = Vec(Reg(Bits(8 bits)) init (0), maxPayloadBytes + 2)
  private val dataCrc = Reg(Bits(16 bits)) init (0xffff)

  io.packet.valid := False
  io.packet.payload.pid := pid
  io.packet.payload.pidValid := pidValid
  io.packet.payload.crcValid := False
  io.packet.payload.address := tokenLow(6 downto 0).asUInt
  io.packet.payload.endpoint := (tokenHigh(2 downto 0) ## tokenLow(7)).asUInt
  io.packet.payload.frameNumber := (tokenHigh(2 downto 0) ## tokenLow).asUInt
  io.packet.payload.payloadLength := 0
  for (index <- 0 until maxPayloadBytes) {
    io.packet.payload.payload(index) := payloadBuffer(index)
  }

  when(io.phy.active && !activePrevious) {
    byteCount := 0
    pidValid := False
    packetError := False
    dataCrc := 0xffff
  }

  when(io.phy.active && io.phy.error) {
    packetError := True
  }

  when(io.phy.active && io.phy.valid) {
    when(byteCount === 0) {
      pid := io.phy.data(3 downto 0)
      pidValid := io.phy.data(7 downto 4) === ~io.phy.data(3 downto 0)
    } otherwise {
      when(UsbPid.isToken(pid)) {
        when(byteCount === 1) {
          tokenLow := io.phy.data
        }
        when(byteCount === 2) {
          tokenHigh := io.phy.data
        }
      }

      when(UsbPid.isData(pid)) {
        when(byteCount <= maxPayloadBytes + 2) {
          payloadBuffer((byteCount - 1).resized) := io.phy.data
        } otherwise {
          packetError := True
        }
        dataCrc := UsbCrc.crc16Next(dataCrc, io.phy.data)
      }
    }
    byteCount := byteCount + 1
  }

  when(!io.phy.active && activePrevious) {
    val tokenBits = tokenHigh(2 downto 0) ## tokenLow
    val tokenCrc = tokenHigh(7 downto 3)
    val dataLength = UInt(log2Up(maxPayloadBytes + 1) bits)
    dataLength := 0
    when(byteCount >= 3) {
      dataLength := (byteCount - 3).resized
    }

    io.packet.valid := True
    io.packet.payload.pid := pid
    io.packet.payload.pidValid := pidValid && !packetError
    io.packet.payload.payloadLength := dataLength

    when(UsbPid.isToken(pid)) {
      io.packet.payload.crcValid := byteCount === 3 && UsbCrc.crc5(tokenBits) === tokenCrc
    } elsewhen (UsbPid.isData(pid)) {
      // A valid payload followed by its transmitted complemented CRC leaves this residue.
      io.packet.payload.crcValid := byteCount >= 3 && dataCrc === B(0xb001, 16 bits)
    } otherwise {
      io.packet.payload.crcValid := byteCount === 1
    }
  }
}

case class UsbTxPacket(maxPayloadBytes: Int) extends Bundle {
  val pid = Bits(4 bits)
  val payloadLength = UInt(log2Up(maxPayloadBytes + 1) bits)
  val payload = Vec(Bits(8 bits), maxPayloadBytes)
}

/** Builds handshake or DATA packets for an 8-bit UTMI-style transmit interface. */
class UsbPacketTransmitter(maxPayloadBytes: Int = 64) extends Component {
  val io = new Bundle {
    val packet = slave(Stream(UsbTxPacket(maxPayloadBytes)))
    val phy = master(UsbPhyTxIo())
    val busy = out Bool()
  }

  private val indexWidth = log2Up(maxPayloadBytes)
  private val sending = Reg(Bool()) init (False)
  private val phase = Reg(UInt(2 bits)) init (0)
  private val pid = Reg(Bits(4 bits)) init (0)
  private val payloadLength = Reg(UInt(log2Up(maxPayloadBytes + 1) bits)) init (0)
  private val payload = Vec(Reg(Bits(8 bits)) init (0), maxPayloadBytes)
  private val index = Reg(UInt(indexWidth bits)) init (0)
  private val crc = Reg(Bits(16 bits)) init (0xffff)
  private val finalCrc = Bits(16 bits)
  finalCrc := ~crc

  io.packet.ready := !sending
  io.busy := sending
  io.phy.valid := sending
  io.phy.data := ((~pid).asBits ## pid)

  when(phase === 1) {
    io.phy.data := payload(index)
  }
  when(phase === 2) {
    io.phy.data := finalCrc(7 downto 0)
  }
  when(phase === 3) {
    io.phy.data := finalCrc(15 downto 8)
  }

  when(io.packet.fire) {
    sending := True
    phase := 0
    pid := io.packet.pid
    payloadLength := io.packet.payloadLength
    index := 0
    crc := 0xffff
    for (byte <- 0 until maxPayloadBytes) {
      payload(byte) := io.packet.payload.payload(byte)
    }
  }

  when(io.phy.valid && io.phy.ready) {
    switch(phase) {
      is(0) {
        when(UsbPid.isData(pid)) {
          when(payloadLength === 0) {
            phase := 2
          } otherwise {
            phase := 1
          }
        } otherwise {
          sending := False
        }
      }
      is(1) {
        crc := UsbCrc.crc16Next(crc, payload(index))
        when(index === payloadLength - 1) {
          phase := 2
        } otherwise {
          index := index + 1
        }
      }
      is(2) {
        phase := 3
      }
      is(3) {
        sending := False
        phase := 0
      }
    }
  }
}

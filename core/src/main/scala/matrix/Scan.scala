package keypulse.core.matrix

import spinal.core._
import spinal.lib._

case class MatrixScanConfig(
    rowCount: Int,
    colCount: Int,
    cyclesPerRow: Int = 1024,
    rowActiveLow: Boolean = true,
    colActiveLow: Boolean = true,
    syncStages: Int = 2
) {
  require(rowCount > 0, "rowCount must be greater than 0")
  require(colCount > 0, "colCount must be greater than 0")
  require(cyclesPerRow > 0, "cyclesPerRow must be greater than 0")

  require(syncStages >= 2, "asynchronous columns need at least two synchronizer stages")
  require(cyclesPerRow > syncStages, "row dwell must exceed synchronizer latency")

  val keyCount: Int = rowCount * colCount
}

object MatrixScanConfig {
  /** Each row is driven for 500 ns; both rows complete every 1 us at 100 MHz. */
  def fast2xN(colCount: Int): MatrixScanConfig =
    MatrixScanConfig(rowCount = 2, colCount = colCount, cyclesPerRow = 50)
}

case class MatrixScanIo(config: MatrixScanConfig) extends Bundle {
  val cols = in Bits (config.colCount bits)
  val rows = out Bits (config.rowCount bits)

  val rowIndex = out UInt ((log2Up(config.rowCount) max 1) bits)
  val frameKeys = out Bits (config.keyCount bits)
  val sampledRowIndex = out UInt ((log2Up(config.rowCount) max 1) bits)
  val keys = out Bits (config.keyCount bits)
  val sampleValid = out Bool ()
  val frameValid = out Bool ()
}

class MatrixScan(config: MatrixScanConfig) extends Component {
  val io = MatrixScanIo(config)

  private val rowWidth = log2Up(config.rowCount) max 1
  private val waitWidth = log2Up(config.cyclesPerRow) max 1

  private val rowIndex = Reg(UInt(rowWidth bits)) init (0)
  private val waitCounter = Reg(UInt(waitWidth bits)) init (0)
  private val keys = Reg(Bits(config.keyCount bits)) init (0)

  private val idleColumns = if (config.colActiveLow) B((BigInt(1) << config.colCount) - 1, config.colCount bits)
                            else B(0, config.colCount bits)
  private val columns = BufferCC(io.cols, init = idleColumns, bufferDepth = config.syncStages)
  private val nextKeys = Bits(config.keyCount bits)
  nextKeys := keys
  // Static slices avoid a multiply and a wide variable-index write decoder.
  for (row <- 0 until config.rowCount) {
    when(rowIndex === row) {
      nextKeys((row + 1) * config.colCount - 1 downto row * config.colCount) :=
        (if (config.colActiveLow) ~columns else columns)
    }
  }
  private val frameKeys = Reg(Bits(config.keyCount bits)) init(0)
  private val rowSample = Bool()
  rowSample := waitCounter === (config.cyclesPerRow - 1)

  when(rowSample) {
    waitCounter := 0

    if (config.rowCount > 1) {
      when(rowIndex === (config.rowCount - 1)) {
        rowIndex := 0
      } otherwise {
        rowIndex := rowIndex + 1
      }
    } else {
      rowIndex := 0
    }

    keys := nextKeys
    when(rowIndex === config.rowCount - 1) {
      frameKeys := nextKeys
    }
  } otherwise {
    waitCounter := waitCounter + 1
  }

  val selectedRows = Bits(config.rowCount bits)
  selectedRows := 0
  for (row <- 0 until config.rowCount) {
    selectedRows(row) := rowIndex === row
  }

  io.rows := (if (config.rowActiveLow) ~selectedRows else selectedRows)
  io.rowIndex := rowIndex
  io.keys := keys
  io.frameKeys := frameKeys
  io.sampledRowIndex := RegNextWhen(rowIndex, rowSample) init(0)
  io.sampleValid := RegNext(rowSample) init(False)
  io.frameValid := RegNext(rowSample && rowIndex === (config.rowCount - 1)) init(False)
}

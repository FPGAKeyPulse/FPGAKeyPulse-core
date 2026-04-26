package keypulse.core.matrix

import spinal.core._

case class MatrixScanConfig(
    rowCount: Int,
    colCount: Int,
    cyclesPerRow: Int = 1024,
    rowActiveLow: Boolean = true,
    colActiveLow: Boolean = true
) {
  require(rowCount > 0, "rowCount must be greater than 0")
  require(colCount > 0, "colCount must be greater than 0")
  require(cyclesPerRow > 0, "cyclesPerRow must be greater than 0")

  val keyCount: Int = rowCount * colCount
}

case class MatrixScanIo(config: MatrixScanConfig) extends Bundle {
  val cols = in Bits (config.colCount bits)
  val rows = out Bits (config.rowCount bits)

  val rowIndex = out UInt ((log2Up(config.rowCount) max 1) bits)
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

    for (col <- 0 until config.colCount) {
      val pressed = if (config.colActiveLow) !io.cols(col) else io.cols(col)
      keys((rowIndex * config.colCount + col).resized) := pressed
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
  io.sampleValid := rowSample
  io.frameValid := rowSample && rowIndex === (config.rowCount - 1)
}

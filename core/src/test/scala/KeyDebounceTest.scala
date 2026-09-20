package keypulse.core

import keypulse.core.matrix._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import scala.util.Random

class KeyDebounceTest extends AnyFunSuite {
  test("invalid sample thresholds are rejected") {
    intercept[IllegalArgumentException](KeyDebounceConfig(0))
    intercept[IllegalArgumentException](KeyDebounceConfig(8, 0, 1))
    intercept[IllegalArgumentException](KeyDebounceConfig(8, 1, 0))
  }
  for ((press, release) <- Seq((1, 1), (1, 4), (3, 5), (7, 2))) {
    test(s"per-key bounce and sparse frames press=$press release=$release") {
      SimConfig.withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC)))
        .workspacePath(s"simWorkspace/debounce-$press-$release")
        .compile(new KeyDebounce(KeyDebounceConfig(8, press, release))).doSim { dut =>
          SimTimeout(1000000)
          dut.io.samples.valid #= false
          dut.io.samples.payload #= 0
          dut.clockDomain.forkStimulus(10)
          dut.clockDomain.waitSampling(5)
          val rng = new Random(123)
          var state = 0
          val counts = Array.fill(8)(0)
          def drive(bits: Int, valid: Boolean): Unit = {
            val old = state
            dut.io.samples.valid #= valid
            dut.io.samples.payload #= bits
            if (valid) for (key <- 0 until 8) {
              val input = (bits >> key) & 1
              if (input == ((state >> key) & 1)) counts(key) = 0
              else {
                counts(key) += 1
                val threshold = if (input == 1) press else release
                if (counts(key) == threshold) {
                  state ^= 1 << key
                  counts(key) = 0
                }
              }
            }
            dut.clockDomain.waitSampling()
            sleep(1)
            assert(dut.io.filtered.valid.toBoolean == valid)
            assert(dut.io.filtered.payload.toInt == state)
            assert(dut.io.changed.toBoolean == (state != old))
          }
          for (_ <- 0 until 20) {
            // Long all-pressed/released runs cover exact thresholds and simultaneous keys.
            for (_ <- 0 until press+2) drive(255, true)
            for (_ <- 0 until release+2) drive(0, true)
            for (_ <- 0 until 100) drive(rng.nextInt(256), rng.nextBoolean())
            // Invalid frames must not advance partial debounce counts or state.
            for (_ <- 0 until 20) drive(rng.nextInt(256), false)
            dut.clockDomain.assertReset()
            sleep(30)
            state = 0
            java.util.Arrays.fill(counts, 0)
            assert(dut.io.filtered.payload.toInt == 0)
            assert(!dut.io.filtered.valid.toBoolean)
            assert(!dut.io.changed.toBoolean)
            dut.io.samples.valid #= false
            dut.clockDomain.deassertReset()
            drive(0, false)
          }
        }
    }
  }
}

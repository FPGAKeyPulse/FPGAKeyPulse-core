# Validation gates

Each feature is a draft PR until regression, review and routed OOC timing pass.
Configure repository variables `FPGA_PART` (exact package/speed grade) and
`VIVADO_OOC_ENABLED=true`, plus a disposable `self-hosted,linux,vivado` runner
with Vivado and device support on PATH. No FPGA part is guessed by this project.
The missing configuration job fails explicitly rather than reporting a skipped pass.

Run locally:

```sh
sbt clean test
sbt 'core/runMain keypulse.core.tools.GenerateOoc matrix'
vivado -mode batch -source scripts/ooc.tcl -tclargs "$FPGA_PART" MatrixScan generated/ooc reports
```

Repeat for target `hid` / top `HidKeyboard`. New hardware features must add OOC
targets. Reports contain routed setup/hold timing, CDC, DRC, utilization and checkpoint.
The 2 ns synchronous boundary budget is a core benchmark; it is not a board pin,
USB GPIF or mechanical-matrix timing constraint. Missing clocks and unconstrained/delay coverage checks fail closed, including
unrecognized report format. Review check_timing and unconstrained paths before
accepting the result. Board-level timing/electrical measurements are
required after integration.

The bot reports CI/OOC run conclusions and latest submitted reviews for the current
head SHA. It does not manufacture a code review from green CI. An absent review or
missing OOC run remains pending. Workflow-run and PR-target reporting becomes active after this branch is merged
into the default branch; review events can exercise the reporter on this PR.
This infrastructure PR depends on `feat-matrix-1mhz`, because asynchronous column
ports without synchronizers deliberately fail the OOC constraints.

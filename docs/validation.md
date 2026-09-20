# Validation gates

Each feature is a draft PR until regression, review and routed OOC timing pass.
OOC runs on GitHub-hosted Ubuntu 22.04 with a community image
containing Vivado 2024.1, not Vivado Lab or a dependency-only image. It targets
the user-selected MLK-S02-35T (`xc7a35tfgg484-2`) at 100 MHz. No self-hosted
machine, runner registration token or repository secret is required.
Image: `gusanagy/xilinx-vivado:2024.1-x11` (Docker Hub reports 23.2 GB; the
workflow records the resolved manifest digest in `reports/image.json`).
This is a third-party preinstalled tool distribution, not an AMD-published image
or a reproduced AMD installer build. Record actual version, image digest and
routed results in every artifact; availability and device support are verified
by the real synthesis/implementation job, never inferred from the image name.
After pulling, the Vivado container runs without networking, credentials, root
privileges or a Docker socket, with only RTL/scripts and report mounts.
The image is about 25 GB compressed; disposable runner SDKs are removed to make
space. The workflow records the resolved digest, `vivado -version` and checks the exact part with
`get_parts` before routing. Other Vivado versions require a separately validated
image/digest.

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

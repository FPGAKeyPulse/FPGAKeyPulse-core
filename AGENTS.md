# FPGAKeyPulse development

- Implement every feature on its own branch. Branch names must not contain `/`.
- Preserve the SpinalHDL module structure and use Conventional Commits.
- Every feature requires the complete `sbt clean test` regression, code review,
  and Vivado routed out-of-context timing at 100 MHz on the selected exact part.
- Report commit SHA, tests, failures, review findings, target part, Vivado version,
  setup/hold slack and utilization. Missing or skipped checks are NOT passes.
- Keep PRs draft and do not merge until all required checks pass.
- Separate matrix frame rate, debounced state update rate and USB host service rate.
- Do not claim USB 3.0 support from descriptors/report packing alone. The device
  controller, PHY/bridge firmware and host enumeration must also be verified.
- Do not invent board pins, USB chip models, or timing results.

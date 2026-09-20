# FPGAKeyPulse Core

[![Release](https://github.com/FPGAKeyPulse/FPGAKeyPulse-core/actions/workflows/ci.yml/badge.svg)](https://github.com/FPGAKeyPulse/FPGAKeyPulse-core/actions/workflows/ci.yml)
[![Contributors](https://img.shields.io/github/contributors/FPGAKeyPulse/FPGAKeyPulse-core)](https://github.com/FPGAKeyPulse/FPGAKeyPulse-core/graphs/contributors)

FPGAKeyPulse Core is a SpinalHDL-based hardware core library for building FPGA keyboard firmware.

The repository is split into two publishable modules:

- `fpga-keypulse-core`: reusable hardware components.
- `fpga-keypulse-tester`: a small ScalaTest-based helper layer for SpinalHDL generation and simulation tests.

The root project only aggregates these modules and is not published.

> [!note]
> The current build targets Scala 2.12.

## Usage

Add the core package when building FPGAKeyPulse-based hardware:

```scala
libraryDependencies += "io.github.fpgakeypulse" %% "fpga-keypulse-core" % "<version>"
```

Add the tester package in test scope when writing SpinalHDL tests:

```scala
libraryDependencies += "io.github.fpgakeypulse" %% "fpga-keypulse-tester" % "<version>" % Test
```

In this repository, the `core` module uses the `tester` module only for its test configuration, so the published core artifact does not depend on the tester artifact at runtime.

## Testing

Run all checks:

```bash
sbt test
```

Run only core tests:

```bash
sbt core/test
```

Run only tester tests:

```bash
sbt tester/test
```

The tester module provides a `SpinalTester` trait that can register Verilog generation, VHDL generation, and one or more simulation cases from a single ScalaTest suite.

## Generated Files

SpinalHDL and simulation outputs are written under ignored directories such as `generated/` and `simWorkspace/`.

## License

BSD 3-Clause

## Contributors

<a href="https://github.com/FPGAKeyPulse/FPGAKeyPulse-core/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=FPGAKeyPulse/FPGAKeyPulse-core" />
</a>

## Fast matrix scanning

`MatrixScanConfig.fast2xN(N)` uses two rows, N parallel columns, and 50
100 MHz clocks per row: **1 MHz complete-matrix scan rate**, independent of N.
This is an internal scan rate, not a USB host report rate.
Columns pass through a two-stage synchronizer. Board RC settling must fit inside
row dwell minus synchronizer latency; verify this on hardware. Mechanical contact
bounce is not removed by the scanner. Use per-switch diodes to prevent ghosting.

`keys` updates one row at a time. `frameKeys` is a coherent snapshot, updated
only when the last row is sampled. Registered `frameValid` accompanies that
snapshot; downstream logic must consume `frameKeys`, not a partially updated frame.
`sampleValid` accompanies `sampledRowIndex`; `rowIndex` identifies the row currently
driven. This changes the previous pre-edge valid-pulse semantics.

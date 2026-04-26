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

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

## USB HID keyboard core

`keypulse.core.hid.UsbHidDevice` implements a USB full-speed HID boot keyboard at an
8-bit UTMI-style PHY boundary. The external PHY handles the electrical interface, SYNC,
NRZI/bit stuffing and EOP; the core handles USB packets and device behavior:

- PID, token CRC5 and data CRC16 validation/generation.
- Endpoint 0 enumeration, descriptors, deferred address/configuration updates and stalls.
- HID boot protocol, idle/protocol requests and keyboard LED output reports.
- Endpoint 1 interrupt IN reports with DATA0/DATA1 toggling and ACK-safe retransmission.
- Bus-reset recovery to USB default state.

The receive side presents `data`, `valid`, `active` and `error`. The transmit side presents
`data` and `valid`, with `ready` backpressure from the PHY. The included SpinalHDL simulation
drives real USB token/data/handshake packets through this boundary and verifies enumeration,
CRC rejection, report toggling and retry behavior.

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

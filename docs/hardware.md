# Hardware selection status

The user selected MLK-S02-35T. The [Milianke hardware manual](https://www.cnblogs.com/milianke/p/17683342.html)
identifies XC7A35T, FGG484 package and -2 speed grade (the model table transposes
FFG/FGG; the package text and chip photo show FGG484). Configure the OOC repository
variable explicitly as `FPGA_PART=xc7a35tfgg484-2`; the workflow has no fallback.
The board uses a 25 MHz oscillator; a board top must generate the 100 MHz core clock.
The manual identifies CH340K USB UART, not a USB3 device controller. External USB3
controller selection, board revision and final pin allocation remain pending.

Official candidates inspected:

- [MLK-F9-35T/100T](https://www.uisrc.com/t-6284.html): two FEP expansion
  connectors and a CEP connector that can be used for GPIO. The published text
  does not establish a USB 3.0 device controller or exact usable GPIO count.
- [MLK-S02-35T/100T](https://www.uisrc.com/t-6264.html): FEP, camera and display
  expansion interfaces. Published text likewise does not establish USB 3.0 HID
  capability. Reusing pins requires checking schematics, bank voltage and loads.

For 104 switches in a 2x52 matrix, reserve 54 GPIO just for the matrix. Reserve
additional pins for the USB bridge, reset, clock and debug. Count connector pins
only after removing power, ground, dedicated pins and occupied signals.

A programmable USB 3.0 device controller plus FPGA interface is a possible path,
not yet a verified board choice. FT600/FT601-style fixed bulk FIFO bridges must
not be assumed to enumerate as standard interrupt-endpoint HID keyboards.
The existing `usb-protocol` PR implements a different packet-level boundary;
do not connect that boundary to a USB 3.0 PHY without a matching controller.

Before board integration: match the selected part against the actual board revision,
obtain its schematic and pin map, USB controller model and firmware SDK, remaining GPIO
budget, and measured row/column settling. No board XDC is supplied until verified.

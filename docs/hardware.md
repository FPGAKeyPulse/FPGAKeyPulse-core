# Hardware selection status

The requested target is a Milianke Artix-family board with enough accessible GPIO
and a USB 3.0 **device** interface. Selection remains open; do not buy based only
on a USB-shaped connector or assume JTAG/UART USB can carry HID reports.

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

Before board integration: obtain exact FPGA part/package/speed grade, revisioned
schematic and pin map, USB controller model and firmware SDK, remaining GPIO
budget, and measured row/column settling. No board XDC is supplied until verified.

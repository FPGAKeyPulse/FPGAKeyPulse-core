# USB 3.0 HID integration boundary

`Usb3HidDescriptors` provides SuperSpeed device/configuration/BOS descriptor data
for a **programmable external USB device controller**. Use assigned VID/PID and
actual board current plus controller U1/U2 exit latencies. Tests use synthetic IDs.
No remote wakeup or USB2 LPM support is advertised. USB2 FS/HS fallback is declared
in BOS and must be implemented by the external controller with speed-specific
descriptors (the existing USB2 descriptors are examples, not that integration).

The existing `HidKeyboard` outputs a latched 8-byte boot report with Stream
backpressure. Firmware/controller must implement EP0 requests, enumeration,
SET/GET_PROTOCOL, SET/GET_IDLE, LED output reports, bus reset, endpoint retries,
suspend/resume, and the USB3 link/PHY. This branch alone cannot enumerate on USB.

The shortest advertised SS interrupt service interval is 125 us (8 kHz),
not 1 us. A 1 MHz matrix scan provides fresh state to the endpoint but is not
1 MHz host HID polling. Sending buffered samples in a custom bulk protocol would
be a separate interface and driver, not standard keyboard HID behavior.

Reference: USB 3.x specification chapter 9; Microsoft
[SuperSpeed endpoint companion descriptor](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/usbspec/ns-usbspec-_usb_superspeed_endpoint_companion_descriptor).

Required integration checks: real host enumeration at FS/HS/SS, descriptor capture,
class requests and LED reports, protocol switch, reset during transfer, retry,
suspend/resume, host report interval measurement, and external-controller timing.

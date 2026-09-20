# KeyboardCore

Composes scanner → per-key debounce → boot key mapping → byte-stream HID report.
The included 104-key OOC target uses 2x52, 100 MHz, 1 MHz whole-matrix frames, eager
press and 5000-frame stable release. Its usage map is an elaboration test map,
not a physical keyboard layout; applications must supply their actual layout.

`cols` is the asynchronous column input. `rows` selects matrix rows. Coherent
frames feed the debounce Flow, then the two-stage mapper. There is no serial
104-key loop limiting frame throughput. All blocks share the 100 MHz domain.
A newly sampled final-row change reaches the live report in three more clocks;
the first available state is qualified one clock after mapper output. Debounce
thresholds and row sampling phase contribute additional key-dependent latency.

The external endpoint/controller pulses `send` only while `canSend` is true.
Before the first mapped frame, sends are ignored. Each accepted send captures
one coherent eight-byte report. `report` honors arbitrary backpressure. A new
mapped frame arriving on the same clock as send is visible to the next send;
that transaction uses the preceding coherent report. `currentReport` is the latest
state, not proof of host delivery. `changed` compares against the last accepted
send, not USB ACK. The controller must retain/retry a report until host ACK.

This core does not implement USB PHY/link, device firmware, NKRO, board pins,
or a host driver. Configure a programmable USB3 controller before claiming a
working USB3 keyboard. HID report service rate is separate from matrix scan rate.

End-to-end test covers physical row/column modeling, modifiers, full rollover,
recovery, short release bounce, state changes during stalled transmission and
reset during a report. Full regression and all five OOC targets are required.

# Boot keyboard key mapping

`BootKeyMap` supplies one HID usage per physical key in row-major order. Usage 0
is unmapped; normal usages 0x04–0x65 match the existing report descriptor; modifiers
are 0xE0–0xE7. Unsupported/reserved usages are rejected at elaboration.

`BootKeyMapper` accepts a bitmap Flow every clock, independently of matrix size.
It uses a parallel prefix count followed by registered report selection. A frame
accepted at edge t is available with `valid` after edge t+1 (two register stages).
Output data holds between valid frames. Reset clears the pipeline and report.

Duplicate physical mappings combine with OR. Normal keys use deterministic ascending
usage order. More than six distinct normal keys emits ErrorRollOver in all six slots,
while modifiers remain valid. Releasing back to six keys recovers automatically.
This is 6KRO boot reporting, not NKRO. Feed mapper outputs to HidKeyboard; only
request send when a coherent output is available. OOC target uses 104 physical keys.

# Debounce

Connect coherent matrix `frameKeys`/`frameValid` to `KeyDebounce.io.samples`.
The output is another registered Flow; it is updated one 100 MHz clock after
accepting a matrix frame. Invalid cycles do not advance any per-key counter.

Defaults: eager press (1 frame) and stable release (5000 consecutive frames).
At 1 MHz frame rate, release filtering is about 5 ms. The first press is accepted
immediately; an electrical glitch can therefore trigger a press. Increase
`pressSamples` to require stable press samples. A return to the currently accepted
state resets that key's counter. Keys do not delay one another. Threshold=1 is a
registered bypass for that transition. Reset clears all keys and partial counts.

The frame-rate setting determines debounce time; changing the core clock alone
does not change sample-count thresholds. Measure real switch bounce/RC behavior
before choosing production thresholds. OOC target: KeyDebounce, 104 keys.

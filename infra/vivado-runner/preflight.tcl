set part $::env(FPGA_PART)
if {[llength [get_parts -quiet $part]] != 1} {
  error "Target part unavailable: $part; install the Artix-7 device files"
}
puts "KEYPULSE_PREFLIGHT_OK part=$part vivado=[version -short]"
exit 0

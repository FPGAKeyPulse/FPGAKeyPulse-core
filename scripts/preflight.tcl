set part xc7a35tfgg484-2
if {[llength [get_parts -quiet $part]] != 1} {
  error "Vivado image does not contain target part $part"
}
puts "KEYPULSE_VIVADO_PREFLIGHT_OK part=$part version=[version -short]"
exit 0

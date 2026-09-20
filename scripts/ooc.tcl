# Usage: vivado -mode batch -source scripts/ooc.tcl -tclargs <part> <top> <rtl-dir> <report-dir>
if {$argc != 4} { error "Expected part, top, RTL directory, report directory" }
lassign $argv part top rtl_dir report_dir
file mkdir $report_dir
set files [glob -nocomplain $rtl_dir/*.v]
if {[llength $files] == 0} { error "No generated Verilog" }
read_verilog $files
synth_design -top $top -part $part -mode out_of_context
create_clock -name core_clk -period 10.000 [get_ports clk]
set inputs [get_ports -filter {DIRECTION == IN && NAME != clk}]
set outputs [get_ports -filter {DIRECTION == OUT}]
set_input_delay -clock core_clk -max 2.000 $inputs
set_input_delay -clock core_clk -min 0.000 $inputs
set_output_delay -clock core_clk -max 2.000 $outputs
set_output_delay -clock core_clk -min 0.000 $outputs
# Only asynchronous column input paths into synchronizers are exempted.
# Synchronizer register-to-register paths remain timed.
set cols [get_ports -quiet {io_cols*}]
if {[llength $cols] > 0} {
  set sync_cells [get_cells -hier -filter {ASYNC_REG == TRUE}]
  if {[llength $sync_cells] == 0} { error "Columns exist but no ASYNC_REG synchronizer: refusing timing waiver" }
  set sync_d [get_pins -of_objects $sync_cells -filter {REF_PIN_NAME == D}]
  set_false_path -from $cols -to $sync_d
}
opt_design
place_design
phys_opt_design
route_design
report_timing_summary -delay_type min_max -report_unconstrained -file $report_dir/timing.rpt
report_utilization -file $report_dir/utilization.rpt
report_route_status -file $report_dir/route.rpt
report_drc -file $report_dir/drc.rpt
report_cdc -file $report_dir/cdc.rpt
check_timing -override_defaults {no_clock unconstrained_internal_endpoints no_input_delay no_output_delay partial_input_delay partial_output_delay} -verbose -file $report_dir/check_timing.rpt
set fd [open $report_dir/check_timing.rpt r]
set coverage [read $fd]
close $fd
foreach check {no_clock unconstrained_internal_endpoints no_input_delay no_output_delay partial_input_delay partial_output_delay} {
  set pattern [format {checking %s \(([0-9]+)\)} $check]
  if {![regexp $pattern $coverage ignored count]} { error "Cannot verify timing coverage section: $check" }
  if {$count != 0} { error "Timing coverage failed: $check ($count)" }
}
write_checkpoint -force $report_dir/routed.dcp
set setup [get_timing_paths -delay_type max -max_paths 1]
set hold [get_timing_paths -delay_type min -max_paths 1]
if {[llength $setup] == 0 || [llength $hold] == 0} { error "No timed setup/hold paths" }
set wns [get_property SLACK $setup]
set whs [get_property SLACK $hold]
set fd [open $report_dir/result.txt w]
puts $fd "part=$part\ntop=$top\nvivado=[version -short]\nclock_ns=10.000\nwns_ns=$wns\nwhs_ns=$whs"
close $fd
if {$wns < 0 || $whs < 0} { error "100 MHz timing failed: WNS=$wns WHS=$whs" }
if {[llength [get_drc_violations -quiet -filter {SEVERITY == Error}]] > 0} { error "DRC errors" }
# The reports (including unconstrained endpoints and CDC) must still be reviewed.

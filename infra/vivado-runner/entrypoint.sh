#!/usr/bin/env bash
set -euo pipefail
: "${VIVADO_SETTINGS:?Set VIVADO_SETTINGS to the mounted settings64.sh}"
: "${FPGA_PART:=xc7a35tfgg484-2}"
export FPGA_PART
[[ -r "$VIVADO_SETTINGS" ]] || { echo 'Vivado settings file is unreadable' >&2; exit 1; }
# Vendor scripts are not guaranteed to support nounset.
set +u
# shellcheck disable=SC1090
source "$VIVADO_SETTINGS"
set -u
command -v vivado >/dev/null
preflight_log=$(mktemp)
trap 'rm -f "$preflight_log"' EXIT
vivado -mode batch -nojournal -nolog -source /opt/keypulse/preflight.tcl >"$preflight_log" 2>&1 || {
  cat "$preflight_log"; exit 1;
}
cat "$preflight_log"
grep -q '^KEYPULSE_PREFLIGHT_OK ' "$preflight_log"
if [[ "${1:-}" == '--check' ]]; then exit 0; fi
: "${RUNNER_NAME:?Set a unique runner name}"
: "${REPO_URL:=https://github.com/FPGAKeyPulse/FPGAKeyPulse-core}"
[[ "$REPO_URL" =~ ^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
  echo 'REPO_URL must identify one GitHub repository' >&2; exit 1;
}
# Read without printing credentials; never bake a PAT into this image.
IFS= read -r token || [[ -n "${token:-}" ]]
[[ -n "$token" ]] || { echo 'Empty registration token' >&2; exit 1; }
./config.sh --unattended --ephemeral --url "$REPO_URL" --token "$token" \
  --name "$RUNNER_NAME" --labels vivado --work _work
unset token
# Close the consumed token stream before executing any repository code.
exec </dev/null
# One job per container. Start another fresh container for the next OOC target.
exec ./run.sh

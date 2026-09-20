#!/usr/bin/env bash
set -euo pipefail
: "${VIVADO_ROOT:?Absolute path to the installed Xilinx tree}"
: "${VIVADO_SETTINGS:?Absolute settings64.sh path within VIVADO_ROOT}"
: "${RUNNER_TOKEN_FILE:?Absolute registration-token file path}"
: "${RUNNER_NAME:?Unique runner name}"
: "${RUNNER_IMAGE:=keypulse-vivado-runner:local}"
: "${CONTAINER_ENGINE:=docker}"
: "${FPGA_PART:=xc7a35tfgg484-2}"
: "${REPO_URL:=https://github.com/FPGAKeyPulse/FPGAKeyPulse-core}"
[[ "$VIVADO_ROOT" == /* && -d "$VIVADO_ROOT" && "$VIVADO_ROOT" != / ]] || exit 2
[[ "$VIVADO_SETTINGS" == "$VIVADO_ROOT/"* && -r "$VIVADO_SETTINGS" ]] || exit 2
[[ "$RUNNER_TOKEN_FILE" == /* && -f "$RUNNER_TOKEN_FILE" ]] || exit 2
export VIVADO_SETTINGS FPGA_PART REPO_URL RUNNER_NAME
# Preserve the installation's absolute path: settings64.sh may contain it.
exec "$CONTAINER_ENGINE" run --rm --init --cap-drop ALL --security-opt no-new-privileges \
  --mount "type=bind,src=$VIVADO_ROOT,dst=$VIVADO_ROOT,readonly" \
  --mount "type=bind,src=$RUNNER_TOKEN_FILE,dst=/run/keypulse-token,readonly" \
  --env VIVADO_SETTINGS --env FPGA_PART --env REPO_URL --env RUNNER_NAME \
  --env RUNNER_TOKEN_FILE=/run/keypulse-token "$RUNNER_IMAGE"

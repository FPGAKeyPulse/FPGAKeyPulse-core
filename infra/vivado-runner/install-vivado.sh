#!/usr/bin/env bash
# Run in the image as root, using an official extracted AMD installer.
set -euo pipefail
if [[ $# != 2 || ! -x "$1" || ! -r "$2" ]]; then
  echo 'Usage: install-vivado.sh /installer/xsetup /config/install_config.txt' >&2
  exit 2
fi
: "${AMD_AGREEMENTS:?Set AMD_AGREEMENTS to the agreements accepted for this installer version}"
# ConfigGen must be run with the SAME installer first. No guessed Modules keys.
"$1" --batch Install --agree "$AMD_AGREEMENTS" --config "$2"

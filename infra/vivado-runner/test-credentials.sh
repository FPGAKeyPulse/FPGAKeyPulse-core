#!/usr/bin/env bash
# Regression for credential lifetime; mocks registration, not Vivado timing.
set -euo pipefail
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
chmod 755 "$fixture"
cat > "$fixture/settings64.sh" <<'MOCK'
export PATH=/fixture:$PATH
MOCK
cat > "$fixture/vivado" <<'MOCK'
#!/bin/bash
echo 'KEYPULSE_PREFLIGHT_OK mock-only'
MOCK
cat > "$fixture/config.sh" <<'MOCK'
#!/bin/bash
set -eu
found=0
while [[ $# -gt 0 ]]; do
  if [[ "$1" == --token ]]; then
    shift
    [[ "$1" == fixture-registration-value ]] || exit 1
    found=1
  fi
  shift
done
[[ "$found" == 1 ]]
MOCK
cat > "$fixture/run.sh" <<'MOCK'
#!/bin/bash
set -eu
[[ ! -e /run/keypulse-token ]]
[[ -z "${RUNNER_TOKEN_FILE:-}" && -z "${token:-}" ]]
[[ "$(readlink /proc/self/fd/0)" == /dev/null ]]
echo CREDENTIAL_LIFETIME_TEST_OK
MOCK
chmod 755 "$fixture"/*.sh "$fixture/vivado"
# Include an extra line: even unread stdin content must be unavailable to jobs.
printf 'fixture-registration-value\nunread-fixture-value\n' | docker run --rm -i \
  --mount "type=bind,src=$fixture,dst=/fixture,readonly" \
  --mount "type=bind,src=$fixture/config.sh,dst=/opt/runner/config.sh,readonly" \
  --mount "type=bind,src=$fixture/run.sh,dst=/opt/runner/run.sh,readonly" \
  --env VIVADO_SETTINGS=/fixture/settings64.sh \
  --env RUNNER_NAME=credential-fixture keypulse-vivado-runner:test

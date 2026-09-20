# Vivado runner for MLK-S02-35T

This builds a Linux x64 GitHub Actions runner environment. Vivado is installed
separately from AMD's installer and mounted read-only into job containers.
The image does not contain AMD binaries, credentials, a license or a live runner.
One container accepts one job and exits. Recreate it for each of the five OOC jobs.
No FPGA hardware or USB access is needed for routed OOC timing.

## Host requirements

Use a dedicated Linux x86-64 build machine with Docker, internet access to GitHub,
AMD (during installation), Maven and sbt repositories. Plan for 16 GB RAM and
at least 150 GB free disk when staging a full installer (a planning budget, not
AMD's minimum). A web install selecting only Vivado and Artix-7 can use less;
check the installer's displayed disk requirement. Do not use Vivado Lab Edition:
it does not provide synthesis/place/route. Do not mount a Docker socket or
production directories into this public-repository runner.

## Build

From the repository root:

```bash
docker build -t keypulse-vivado-runner:local infra/vivado-runner
```

The GitHub runner archive is version/hash pinned. Java 17 and sbt are provisioned
by the existing OOC workflow. Rebuild when updating runner/OS dependencies.
The `Runner Image` workflow builds the image and executes Runner.Listener,
but this alone does not verify a Vivado installation.

## Install Vivado, or reuse an existing installation

Download the Linux installer from the [AMD 2024.2 download page](https://www.amd.com/en/support/downloads/adaptive-socs-and-fpgas/development-tools/2024-2.html).
The download link uses an AMD account session. Complete login and download on
the deployment host; never commit account credentials or authenticated URLs.
Verify the download with the checksum AMD publishes. Extract the full installer
into `/srv/amd-installer`, with `xsetup` immediately inside that directory.
For the web installer, use its supported extraction procedure and authentication
flow; a browser download alone does not authenticate subsequent payload downloads.

Generate the install configuration using that exact installer:

```bash
mkdir -p /srv/keypulse-install-config /srv/Xilinx
docker run --rm -it --user 0 --entrypoint /bin/bash \
  --mount type=bind,src=/srv/amd-installer,dst=/installer,readonly \
  --mount type=bind,src=/srv/keypulse-install-config,dst=/root/.Xilinx \
  keypulse-vivado-runner:local \
  -c '/installer/xsetup --batch ConfigGen'
```

Edit `/srv/keypulse-install-config/install_config.txt`: choose Vivado Standard,
Artix-7 device support and destination `/srv/Xilinx`; omit Vitis, other device
families and cable drivers for this OOC-only machine. Keep the generated option
names, since they vary by installer version. Review the installer agreements.
For an installer whose `--help` lists `XilinxEULA,3rdPartyEULA`, installation is:

```bash
docker run --rm --user 0 --entrypoint /bin/bash \
  --mount type=bind,src=/srv/amd-installer,dst=/installer,readonly \
  --mount type=bind,src=/srv/keypulse-install-config,dst=/root/.Xilinx \
  --mount type=bind,src=/srv/Xilinx,dst=/srv/Xilinx \
  --env AMD_AGREEMENTS=XilinxEULA,3rdPartyEULA \
  keypulse-vivado-runner:local /opt/keypulse/install-vivado.sh \
  /installer/xsetup /root/.Xilinx/install_config.txt
```

The install configuration's destination MUST equal the mounted destination.
Apply any required AMD patches for the chosen version before validation.
If Vivado is already installed, skip installation and use its absolute paths below.
The installation must be readable/executable by container UID 1000.

## Verify Vivado and target part

```bash
export VIVADO_ROOT=/srv/Xilinx
export VIVADO_SETTINGS=/srv/Xilinx/Vivado/2024.2/settings64.sh
export FPGA_PART=xc7a35tfgg484-2
docker run --rm \
  --mount "type=bind,src=$VIVADO_ROOT,dst=$VIVADO_ROOT,readonly" \
  --env VIVADO_SETTINGS --env FPGA_PART \
  keypulse-vivado-runner:local --check
```

Expect `KEYPULSE_PREFLIGHT_OK`, the exact part and Vivado version. This verifies
startup/device availability only. OOC still has to synthesize, place and route.
`xc7a35tfgg484-2` follows the [Milianke hardware manual](https://www.cnblogs.com/milianke/p/17683342.html)
(FGG484 package, -2 speed). The board has a 25 MHz oscillator; the OOC clock is
100 MHz. A future board top must generate it; this runner does not supply pins.

## Register and run

In GitHub: repository Settings → Actions → Runners → New self-hosted runner →
Linux x64. Copy only the short-lived registration token into a file on the host.
Do not use a PAT as the runner token. File ownership must allow container UID
1000 to read it; keep it mode 0400 or 0600 and never commit it.

```bash
export RUNNER_TOKEN_FILE=/srv/keypulse-secrets/registration-token
export RUNNER_NAME=keypulse-vivado-01
bash infra/vivado-runner/run.sh
```

The container registers with `self-hosted`, `linux`, `x64`, `vivado` labels and
runs one job. It has no host Docker access or privilege escalation. Use a fresh
unique name/container for subsequent jobs; registration tokens expire after one
hour. Automatic reprovisioning/token issuance is not included: it needs a GitHub
App or host-side administrator credentials, which must stay outside job containers.
An interrupted runner may need removal in repository Settings; normal ephemeral
job completion deregisters it automatically. Do not configure `--restart always`
with an expiring registration token.

After successful preflight and runner registration, set repository Actions
variables `FPGA_PART=xc7a35tfgg484-2` and `VIVADO_OOC_ENABLED=true`, then rerun the
OOC workflow. The provisioned runner must remain online. Five containers/jobs
are needed for the current matrix (sequential operation is fine).

## Validation status

Check the PR's exact-SHA CI and Runner Image runs for regression/image results.
Real installation, registration, routed timing and host USB enumeration must
be reported separately. Missing runtime checks are not successes.

References: [GitHub runner registration](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners),
[runner v2.337.0 and SHA-256](https://github.com/actions/runner/releases/tag/v2.337.0).

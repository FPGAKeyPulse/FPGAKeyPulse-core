FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git build-essential python3 unzip zip xz-utils \
    libicu70 libssl3 libkrb5-3 zlib1g liblttng-ust1 libnuma1 libtinfo5 \
    libncurses5 libx11-6 libxext6 libxrender1 libxtst6 libxi6 libfreetype6 \
    libglib2.0-0 libsm6 libice6 libfontconfig1 locales tini \
    && locale-gen en_US.UTF-8 && rm -rf /var/lib/apt/lists/*
ENV LANG=en_US.UTF-8
# Pinned to the SHA-256 published by actions/runner v2.337.0.
RUN useradd --create-home --uid 1000 runner && mkdir /opt/runner \
    && curl --fail --location --retry 3 --proto '=https' --proto-redir '=https' \
       https://github.com/actions/runner/releases/download/v2.337.0/actions-runner-linux-x64-2.337.0.tar.gz -o /tmp/runner.tgz \
    && echo '70920811a4f8ad4328818682bca5c6469c1c942fab52448868071d0063816613  /tmp/runner.tgz' | sha256sum -c - \
    && tar xzf /tmp/runner.tgz -C /opt/runner && rm /tmp/runner.tgz \
    && chown -R runner:runner /opt/runner
COPY entrypoint.sh preflight.tcl install-vivado.sh /opt/keypulse/
USER runner
WORKDIR /opt/runner
ENTRYPOINT ["/usr/bin/tini", "--", "/bin/bash", "/opt/keypulse/entrypoint.sh"]

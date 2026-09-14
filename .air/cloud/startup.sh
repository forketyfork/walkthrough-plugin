#!/usr/bin/env bash

set -euo pipefail

WARMUP=
if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then
  WARMUP=1
fi

log() {
  printf '[startup] %s\n' "$*"
}

run_in_dev_shell() {
  if command -v nix >/dev/null 2>&1; then
    nix develop --command bash -c "$*"
    return
  fi

  if [ -n "${HTTPS_PROXY:-}" ]; then
    proxy_host=${HTTPS_PROXY#*://}
    proxy_host=${proxy_host%%:*}
    proxy_port=${HTTPS_PROXY##*:}
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=${proxy_port} -Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=${proxy_port} -Dhttp.nonProxyHosts=localhost\|127.*"
  fi
  bash -c "$*"
}

healthcheck() {
  log 'Running Gradle tests and plugin build as the readiness check'
  run_in_dev_shell './gradlew test buildPlugin --no-daemon'
  log 'Healthcheck passed: tests and plugin build completed'
}

if command -v nix >/dev/null 2>&1; then
  log 'Priming Nix development environment'
  nix develop --command true
else
  log 'Nix is unavailable; using the repository Gradle wrapper and installed JDK'
fi

if [ -n "${WARMUP:-}" ]; then
  healthcheck
fi

log 'Startup complete'

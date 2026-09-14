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
  nix develop --command bash -c "$*"
}

healthcheck() {
  log 'Running Gradle tests and plugin build as the readiness check'
  run_in_dev_shell './gradlew test buildPlugin --no-daemon'
  log 'Healthcheck passed: tests and plugin build completed'
}

log 'Priming Nix development environment'
nix develop --command true

if [ -n "${WARMUP:-}" ]; then
  healthcheck
fi

log 'Startup complete'

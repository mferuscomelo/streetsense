#!/usr/bin/env bash
# Wraps the PlatformIO commands for firmware/. See README.md for what each
# environment means (ledglasses = mock sensor / default demo, ledglasses_sen54
# = real SEN54, native = host-side tests, no board required).
#
# Usage:
#   scripts/run-firmware.sh              # build (env:ledglasses) — default
#   scripts/run-firmware.sh test         # host-side tests, no hardware
#   scripts/run-firmware.sh build-sen54  # build env:ledglasses_sen54
#   scripts/run-firmware.sh flash        # upload to whichever env you built (ERASES the board)
#   scripts/run-firmware.sh monitor      # watch packets over serial

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/firmware"

if [[ -x "$HOME/.platformio/penv/bin/pio" ]]; then
  pio="$HOME/.platformio/penv/bin/pio"
elif command -v pio >/dev/null 2>&1; then
  pio="pio"
else
  echo "error: pio not found at ~/.platformio/penv/bin/pio or on PATH" >&2
  exit 1
fi

cmd="${1:-build}"

case "$cmd" in
  test)
    echo "+ $pio test -e native"
    exec "$pio" test -e native
    ;;
  build)
    echo "+ $pio run -e ledglasses"
    exec "$pio" run -e ledglasses
    ;;
  build-sen54)
    echo "+ $pio run -e ledglasses_sen54"
    exec "$pio" run -e ledglasses_sen54
    ;;
  flash)
    echo "WARNING: this erases the board's current firmware."
    echo "+ $pio run -t upload"
    exec "$pio" run -t upload
    ;;
  monitor)
    echo "+ $pio device monitor"
    exec "$pio" device monitor
    ;;
  *)
    echo "usage: $0 [test|build|build-sen54|flash|monitor]" >&2
    exit 1
    ;;
esac

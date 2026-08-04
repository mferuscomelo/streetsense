---
name: run
description: Launch StreetSense's modules (backend, firmware, app) via the scripts in scripts/. Use whenever asked to run, start, build, test, or flash any of firmware/, backend/, or app/.
---

StreetSense has three modules, each with its own runner script in `scripts/`
at the repo root. Prefer these over hand-rolling `./gradlew` or `pio`
invocations — they already carry the required flags and pre-flight checks.

## backend

```sh
scripts/run-backend.sh          # plain run — dashboard at http://localhost:8080
scripts/run-backend.sh --seed   # also seeds synthetic crowd contributors (flagged, never presented as measured)
```

Runs in the foreground (`bootRun`); it doesn't exit on its own. Use a
background shell/process if you need to keep working while it's up, and stop
it with the same mechanism when done. Auto-provisions a JDK 26 toolchain via
Gradle on first run.

## firmware

```sh
scripts/run-firmware.sh test         # host-side tests, no hardware — safe to run any time
scripts/run-firmware.sh              # build env:ledglasses (default, mock sensor)
scripts/run-firmware.sh build-sen54  # build env:ledglasses_sen54 (real SEN54, unproven on hardware)
scripts/run-firmware.sh flash        # upload — ERASES the board's current firmware, confirm with the user first
scripts/run-firmware.sh monitor      # watch serial output
```

`test` is the one safe to run unprompted to verify firmware changes compile
and pass. `flash` and `monitor` need a physically connected board — don't run
them without confirming one is attached.

## app

```sh
scripts/run-app.sh
```

Bootstraps `local.properties`, runs `adb reverse tcp:8080 tcp:8080`, builds
`assembleDebug`, installs, and launches on a connected phone. Requires a
**physical Android phone** connected over USB with debugging enabled — BLE
doesn't work in the emulator, and the script will fail fast with instructions
if no device is found via `adb devices`. Confirm with the user that a phone
is plugged in and unlocked before running this one; it's the only script that
depends on external hardware state you can't check without running it.

If the backend needs to be up for an end-to-end test, start
`scripts/run-backend.sh` first — the app's default `BACKEND_BASE_URL` is
`http://localhost:8080`, which the `adb reverse` step forwards to.

# Quickstart

This guide explains how to build and run each part of StreetSense. Each part
has a one command runner script in [`scripts/`](../scripts/). Each section
below also shows the full command behind the script, as proof of what runs.

## Firmware

The firmware has two build targets. Both targets share the same code. Only
the sensor source file changes.

Run the host side tests:

```sh
scripts/run-firmware.sh test
```

Build the mock sensor target. This target always flags its data as mock:

```sh
scripts/run-firmware.sh
```

Build the real sensor target. This target uses a real SEN54 sensor, a real
microphone, and a real battery gauge:

```sh
scripts/run-firmware.sh build-sen54
```

Upload the firmware to the board. Note that this step erases the board's
current firmware:

```sh
scripts/run-firmware.sh flash
```

Watch the sensor packets over the serial connection:

```sh
scripts/run-firmware.sh monitor
```

The `ledglasses` target is the default target. The `ledglasses_sen54` target
uses real hardware. Both targets have run over Bluetooth against the app and
the backend.

## Backend

Run the backend:

```sh
scripts/run-backend.sh
```

Run the backend with seeded crowd data:

```sh
scripts/run-backend.sh --seed
```

The backend needs a Java 26 build tool. The script installs this tool
automatically.

Open `http://localhost:8080` to view the dashboard. The dashboard updates in
real time.

The `--seed` flag adds fake contributors to the crowd layer. Use this flag to
show the crowd layer with only one real sensor node. The system always marks
seeded contributors as seeded. It never shows seeded data as real data. See
[`docs/honest-caveats.md`](honest-caveats.md) for more detail.

## App

The app needs a real phone. Bluetooth does not work in the Android emulator.

Run the app:

```sh
scripts/run-app.sh
```

This script sets up `local.properties`. It then runs `adb reverse tcp:8080
tcp:8080`. It then builds the app and installs it on your connected phone.

Complete these three steps before you run the app:

1. Turn on USB debugging on your phone. Open Settings, then About, then tap
   the build number seven times. Open Developer Options. Plug in your phone.
   Accept the RSA prompt.
2. Point the app at your backend. Check that `MainActivity.BACKEND_BASE_URL`
   and `res/xml/network_security_config.xml` list the same host. The script
   uses `adb reverse tcp:8080 tcp:8080` with `http://localhost:8080`. This
   setup survives network changes.
3. Turn on Location at the system level. Grant Precise location, not
   Approximate location. A coarse location cannot fit inside a 110 meter
   grid cell.

Open the app. Pick an activity. Tap Continue. Connect to `StreetSense-01`.
Tap Start session. The screen shows live values once per second. A MOCK
badge appears when the connected sensor node sends synthetic data. Tap
Finish to see your session summary.

# Getting started

Each part of StreetSense has a one command runner script in
[`scripts/`](../scripts/), and the full commands behind them are in
[`docs/quickstart.md`](quickstart.md).

```sh
scripts/run-backend.sh    # backend, http://localhost:8080
scripts/run-firmware.sh   # firmware
scripts/run-app.sh        # app, needs a physical Android phone
```

The app needs a real phone rather than the emulator, since Bluetooth Low
Energy does not work in Android's emulator. See
[`docs/quickstart.md`](quickstart.md) for the full setup steps, including
pointing the app at a locally running backend.

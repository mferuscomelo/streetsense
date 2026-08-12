# StreetSense

An exposure tracker for people who move through their city under their own
power, for the Hackster.io / Avnet **"Modern Java in the Wild"** contest
(sponsor: Oracle).

**Category:** Best Health Solution

Runners, cyclists, and dog walkers have no idea what they're breathing or how
loud it is while they're out. A battery-powered BLE sensor node measures air
quality (PM1/2.5/4/10, VOC) and ambient noise. An Android phone carries it
through a **session** — tagged Run, Cycle, or Walk — and uploads each reading
to a Java 26 backend, which decodes the wire packet, weighs it by how hard
you were breathing, and turns the outing into a **dose**: not "the air was
34 µg/m³ PM2.5" but "you inhaled 0.6 mg of it, and the worst 30 seconds was
this junction." Sessions accumulate into your own comparisons — this route
against that one, this hour against another — and every contributor's
readings pool into a shared per-block picture: a street *your* node has
never walked can still have an answer, because someone else walked it.

The phone never uploads a precise location. It snaps to a ~110m grid cell
before sending anything, and the exact route stays on the device — the
backend has nowhere to put a coordinate even if it wanted to.

## Architecture

```
 ┌─────────────────┐   BLE notify    ┌────────────────────┐   HTTPS POST     ┌────────────────────┐
 │   firmware/      │  (26B packet,   │   app/              │  {rawPacket,     │   backend/          │
 │  nRF52840 board  │──  1 Hz)  ─────▶│  Android, Java 17   │──  cell bucket, ─▶│  Spring Boot,       │
 │  (mock or SEN54) │                 │  session + BLE      │   session, hour}  │  Java 26            │
 └─────────────────┘                 └────────────────────┘                  └──────────┬──────────┘
                                                                                          │
                                                                       FFM-decode → structured-concurrency
                                                                       ingest: persist / hour-keyed baseline /
                                                                       explained event, then session dose +
                                                                       crowd merge on read
                                                                                          │
                                                              ┌───────────────────────────┴───────────────────────────┐
                                                              │  backend also serves the dashboard (static + SSE)      │
                                                              │  and the app's session debrief/history (GET JSON)      │
                                                              └─────────────────────────────────────────────────────────┘
```

- **`/firmware`** — PlatformIO project targeting the [Adafruit LED Glasses Driver, nRF52840](https://www.adafruit.com/product/5217). **This is C++, deliberately** — it's a dumb sensor-to-BLE layer with no application logic, not a gap in the Java requirement. See [`docs/java26-jeps.md`](docs/java26-jeps.md) for where Java 26 actually lives.
- **`/app`** — Android BLE central, Java 17 (Android's toolchain caps at 17; see below). Runs the session flow (activity picker → live scan → debrief), snaps GPS to a grid cell before upload, and draws the session map from its own local trace.
- **`/backend`** — Spring Boot on Java 26, the only place the contest's Java 26 requirement is claimed. Decodes the wire format via FFM, ingests via Structured Concurrency + Scoped Values, computes hour-keyed rolling baselines and dose via Stream Gatherers, classifies events over a sealed hierarchy, pools contributions into a crowd layer, and serves both the JSON API and the dashboard.

**Java 26 is backend-only.** Android's build toolchain caps `sourceCompatibility`/`targetCompatibility` at Java 17 — see [developer.android.com/build/jdks](https://developer.android.com/build/jdks). The app is Java 17 on purpose; the Java 26 claim (30 of 120 rubric points) lives entirely in `/backend` on a real JVM. `backend/build.log` is committed as evidence alongside the Gradle toolchain declaration.

## Quickstart

Each module has a one-command runner script in [`scripts/`](scripts/). They're
thin wrappers — the underlying `pio`/`./gradlew`/`adb` commands are shown
below each one for reference (and as the actual evidence of what's running,
for judging purposes).

### firmware

Two board environments share the same code; only the sensor source compiled in differs:

```sh
scripts/run-firmware.sh test         # host-side packet + mock-source tests
scripts/run-firmware.sh              # build env:ledglasses — mock sensor, always FLAG_MOCK_DATA
scripts/run-firmware.sh build-sen54  # build env:ledglasses_sen54 — real SEN54 + PDM mic, compiles, not yet run on hardware
scripts/run-firmware.sh flash        # upload whichever env you built (⚠️ erases the board's current firmware)
scripts/run-firmware.sh monitor      # watch packets tick over serial
```

Equivalent to, respectively: `pio test -e native`, `pio run -e ledglasses`,
`pio run -e ledglasses_sen54`, `pio run -t upload`, `pio device monitor` (run
from `firmware/`, using `~/.platformio/penv/bin/pio` if it's not on `PATH`).

`ledglasses` is the default and what the demo currently runs on. `ledglasses_sen54` is real and builds, but hasn't been verified against a physical SEN54 — see [`docs/future-work.md`](docs/future-work.md).

### backend

```sh
scripts/run-backend.sh          # ./gradlew bootRun — requires a JDK 26 toolchain (auto-provisioned), --enable-preview already wired in
scripts/run-backend.sh --seed   # same, plus -Pseed=true
```

Open **http://localhost:8080** for the dashboard — served by this same process, live over Server-Sent Events. `--seed` shows the crowd layer working with only one physical node: it generates additional contributors walking overlapping stretches of one street, every one flagged as seeded (never presented as measured — see [`docs/honest-caveats.md`](docs/honest-caveats.md)).

### app

Requires a **physical phone** — BLE does not work in the emulator.

```sh
scripts/run-app.sh
```

Bootstraps `local.properties`, runs `adb reverse tcp:8080 tcp:8080`, then
`./gradlew assembleDebug` and installs + launches on the connected phone —
the USB path described below. Before running it end-to-end:

1. **Enable USB debugging** on the phone (Settings → About → tap Build number 7× → Developer options), plug it in, and accept the RSA prompt. The script checks `~/Android/Sdk/platform-tools/adb devices` and tells you if no device is found — use the SDK's `adb`, not a distro-packaged one, or the two will fight over the adb server.
2. **Point the app at your backend.** `MainActivity.BACKEND_BASE_URL` and `res/xml/network_security_config.xml` must agree — cleartext HTTP is blocked unless the host is listed in that config. Two supported paths:
   - **WiFi**: phone and laptop on the same network; set both to the laptop's LAN IP and allow inbound 8080 through its firewall.
   - **USB** (what the script uses — simpler, survives DHCP changes): `adb reverse tcp:8080 tcp:8080` and use `http://localhost:8080`.
3. **Turn Location on** at the system level, and grant **Precise** (not Approximate) when prompted — a ~1km coarse fix can't place a reading in a ~110m grid cell, so the app asks for precise and says so if it doesn't get it.

Tap **Start**, choose Run/Cycle/Walk, and it scans, connects to `StreetSense-01`, and ticks live values at 1 Hz with a **MOCK** badge whenever the connected node's data is synthetic. Tap **Stop** and it opens the session debrief — dose, worst stretch, classified events, and a map of the actual route colored by air quality. **History** lists past sessions.

### Demo video

_(placeholder — 90–120s walkthrough goes here before submission. Suggested beats: start a session on the phone and tag it Run/Walk/Cycle → live values ticking with the MOCK badge → stop and open the debrief, showing dose and the worst-segment map → switch to the dashboard, showing the same reading arrive live over SSE → a cell your node never sampled still returning a verdict, because a seeded contributor walked it → the confidence legend distinguishing that from a corroborated block.)_

## Repo layout

```
firmware/   PlatformIO, nRF52840, C++
app/        Android, Java 17 — session flow, debrief, history
backend/    Spring Boot, Java 26 — ingest, sessions, crowd layer, dashboard
hardware/   Bill of materials, wiring notes
docs/       Protocol spec, Java 26 feature map, future work, honest caveats, attribution
```

## Docs

- [`docs/ble-protocol.md`](docs/ble-protocol.md) — wire format, UUIDs, scaling factors
- [`docs/java26-jeps.md`](docs/java26-jeps.md) — which JEP, where in the code, why it's there
- [`docs/future-work.md`](docs/future-work.md) — what's deliberately out of scope
- [`docs/honest-caveats.md`](docs/honest-caveats.md) — what this project does and doesn't claim
- [`docs/attribution.md`](docs/attribution.md) — third-party components and licences
- [`hardware/BOM.md`](hardware/BOM.md) — bill of materials

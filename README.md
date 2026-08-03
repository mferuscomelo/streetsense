# StreetSense

A civic environmental sensing network for the Hackster.io / Avnet **"Modern Java in the Wild"** contest (sponsor: Oracle).

**Category:** Best Home Solution

A battery-powered BLE sensor node measures air quality (PM1/2.5/4/10, VOC) and ambient noise level. An Android phone scans for the node, GPS-tags each reading, and uploads it to a Java 26 backend. The backend decodes the wire packet, updates a rolling per-location baseline, and flags readings that deviate from *that location's own history* — surfacing anomalies and, eventually, recommending clean/quiet routes.

This is **slice 1**: the full skeleton, wired end-to-end with mocked sensor data, proving the architecture before real SEN54 readings are swapped in.

## Architecture

```
 ┌─────────────────┐   BLE notify    ┌──────────────────┐   HTTPS POST    ┌───────────────────┐
 │   firmware/      │  (20B packet,   │   app/            │  {rawPacket,    │   backend/         │
 │  nRF52840 board  │──  1 Hz)  ─────▶│  Android, Java 17 │──  b64, GPS,  ─▶│  Spring Boot,      │
 │  (mock sensor)   │                 │  BLE central      │   timestamp}    │  Java 26           │
 └─────────────────┘                 └──────────────────┘                 └───────────────────┘
                                                                                     │
                                                                            FFM-decode, structured-
                                                                            concurrency ingest:
                                                                            persist / rolling
                                                                            baseline / verdict
```

- **`/firmware`** — PlatformIO project targeting the [Adafruit LED Glasses Driver, nRF52840](https://www.adafruit.com/product/5217). **This is C++, deliberately** — it's a dumb sensor-to-BLE layer with no application logic, not a gap in the Java requirement. See [`docs/java26-jeps.md`](docs/java26-jeps.md) for where Java 26 actually lives.
- **`/app`** — Android BLE central, Java 17 (Android's toolchain caps at 17; see below). Forwards the raw packet verbatim plus GPS and timestamp.
- **`/backend`** — Spring Boot on Java 26, the only place the contest's Java 26 requirement is claimed. Decodes the wire format via the FFM API, runs ingest via Structured Concurrency + Scoped Values, and computes the rolling baseline via Stream Gatherers.

**Java 26 is backend-only.** Android's build toolchain caps `sourceCompatibility`/`targetCompatibility` at Java 17 — see [developer.android.com/build/jdks](https://developer.android.com/build/jdks). The app is Java 17 on purpose; the Java 26 claim (30 of 120 rubric points) lives entirely in `/backend` on a real JVM. `backend/build.log` is committed as evidence alongside the Gradle toolchain declaration.

## Quickstart

### firmware

```sh
cd firmware
~/.platformio/penv/bin/pio test -e native      # host-side packet encoder tests
~/.platformio/penv/bin/pio run -e ledglasses   # cross-compile for the board
~/.platformio/penv/bin/pio run -t upload       # flash (⚠️ erases whatever is currently on the board)
~/.platformio/penv/bin/pio device monitor      # watch mock packets tick over serial
```

### backend

```sh
cd backend
./gradlew build                                          # requires a JDK 26 toolchain (auto-provisioned)
java --enable-preview -jar build/libs/backend-0.1.0.jar   # --enable-preview is required at runtime too
```

### app

Requires a **physical phone** — BLE does not work in the emulator.

```sh
cd app
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # Android Studio writes this for you on first open
./gradlew assembleDebug                                # or just Run ▶ in Android Studio
```

Before running end-to-end:

1. **Enable USB debugging** on the phone (Settings → About → tap Build number 7× → Developer options), plug it in, and accept the RSA prompt. Verify with `~/Android/Sdk/platform-tools/adb devices` — use the SDK's `adb`, not a distro-packaged one, or the two will fight over the adb server.
2. **Point the app at your backend.** `MainActivity.BACKEND_BASE_URL` and `res/xml/network_security_config.xml` must agree — cleartext HTTP is blocked unless the host is listed in that config. Two supported paths:
   - **WiFi**: phone and laptop on the same network; set both to the laptop's LAN IP and allow inbound 8080 through its firewall.
   - **USB** (simpler, survives DHCP changes): `adb reverse tcp:8080 tcp:8080` and use `http://localhost:8080`.
3. **Turn Location on** at the system level, and grant **Precise** (not Approximate) when prompted — a ~1km coarse fix can't place a reading in a ~1.1km grid cell, so the app asks for precise and says so if it doesn't get it.

Tap **Start** → it scans, connects to `StreetSense-01`, and ticks live values at 1 Hz with a **MOCK** badge. Confirm the full chain with `curl http://<backend>:8080/api/v1/readings/recent`.

### Demo video

_(placeholder — 90–120s walkthrough goes here before submission)_

## Repo layout

```
firmware/   PlatformIO, nRF52840, C++
app/        Android, Java 17
backend/    Spring Boot, Java 26
hardware/   Bill of materials, wiring notes
docs/       Protocol spec, Java 26 feature map, future work, honest caveats
```

## Docs

- [`docs/ble-protocol.md`](docs/ble-protocol.md) — wire format, UUIDs, scaling factors
- [`docs/java26-jeps.md`](docs/java26-jeps.md) — which JEP, where in the code, why it's there
- [`docs/future-work.md`](docs/future-work.md) — what's deliberately out of scope for slice 1
- [`docs/honest-caveats.md`](docs/honest-caveats.md) — what this project does and doesn't claim
- [`hardware/BOM.md`](hardware/BOM.md) — bill of materials

# BLE protocol

StreetSense sensor nodes advertise a custom GATT service with a single notify
characteristic carrying a fixed 26-byte packet — chosen over a Nordic UART +
JSON approach for a concrete reason: JSON parsing on a Cortex-M4 microcontroller
is unnecessary overhead, and a packed binary struct keeps both sides simple.
(v1 was 20 bytes, sized to avoid MTU negotiation entirely — see the wire
format section below for why v2 spent that budget.)

## Identifiers

| | UUID |
|---|---|
| Device name | `StreetSense-01` |
| Service | `ee8ebcc5-07f5-4bce-96c2-ccafc2a91f7c` |
| Characteristic (notify) | `b70d5d1b-481e-4d17-8f6d-6b91b22d6b60` |

Defined once in `firmware/include/ble_uuids.h` and mirrored in
`app/.../ble/BleScanner.java` / `SensorNodeClient.java`. If either side's
values drift, the app will simply never find the node — get them from that
header, don't retype them.

**Adafruit Bluefruit gotcha**: on the firmware side, `BLEService`/
`BLECharacteristic` take 128-bit UUIDs as a byte array in **LSB-first
(reversed)** order — get this backwards and the service is silently
unadvertised, no error raised. `ble_uuids.h` documents both the MSB-first
UUID string (for cross-referencing against the app/backend) and the
already-reversed byte array Bluefruit actually needs. Android's
`UUID.fromString(...)` takes the normal MSB-first form — no reversal there.

## Wire format — exactly 26 bytes (v2)

v1 was 20 bytes, deliberately sized to fit inside the default BLE ATT MTU of
23 bytes (20-byte payload + 3-byte L2CAP/ATT header) so a notification never
needed MTU negotiation. Adding battery telemetry (v2) needed more room than
that budget had left, so v2 spends the negotiation v1 was built to avoid:
firmware calls `Bluefruit.configPrphBandwidth(BANDWIDTH_MAX)` before
`Bluefruit.begin()` (raising the peripheral's MTU ceiling to 247), and the
app requests a 64-byte MTU on connect (`SensorNodeClient.REQUESTED_MTU`)
before discovering services. `version` is what signals the change — the
backend's decoder is version-aware and still accepts v1's 20-byte packets
from older firmware.

All multi-byte fields are little-endian.

| Offset | Field | Type | Scale | Notes |
|---|---|---|---|---|
| 0 | `version` | u8 | — | schema version, currently `2` |
| 1 | `flags` | u8 | — | bit0 = `FLAG_MOCK_DATA`, bit1 = `FLAG_CHARGING`, bit2 = `FLAG_BATTERY_VALID` |
| 2 | `seq` | u16 | — | wraps; detects dropped notifications |
| 4 | `pm1` | u16 | ÷10 | ug/m³ |
| 6 | `pm2_5` | u16 | ÷10 | ug/m³ |
| 8 | `pm4` | u16 | ÷10 | ug/m³ |
| 10 | `pm10` | u16 | ÷10 | ug/m³ |
| 12 | `voc_index` | u16 | ÷10 | unitless VOC index |
| 14 | `temp_c` | i16 (signed) | ÷100 | °C |
| 16 | `humidity` | u16 | ÷100 | %RH |
| 18 | `noise_db` | u16 | ÷10 | dB(A) |
| 20 | `batt_mv` | u16 | — | millivolts; 0 when `FLAG_BATTERY_VALID` is clear |
| 22 | `batt_soc` | u16 | ÷10 | battery percent, 0–100; 0 when invalid |
| 24 | `batt_rate` | i16 (signed) | ÷10 | %/hour; negative while discharging; 0 when invalid |

Total: 26 bytes. See `docs/golden-packet.md` for concrete byte-for-byte
examples (both v1 and v2) independently reproduced by the firmware, Android,
and backend test suites.

### Battery telemetry

Battery data comes from an Adafruit MAX17048 fuel gauge (STEMMA QT breakout,
product 5580) sharing the I2C bus with the SEN54 at a different address
(`0x36` vs `0x69`). Its state of charge is a ModelGauge voltage-curve
estimate, not a coulomb count — see the honesty note in
`firmware/lib/battery_monitor/battery_monitor.h`.

The nRF52840 has no charger-status pin exposed on this driver board, so
`FLAG_CHARGING` is inferred rather than read directly: USB VBUS detected
(`NRF_POWER->USBREGSTATUS`) **and** the gauge's own charge rate is positive
past a small noise floor. The raw signed `batt_rate` also goes out on the
wire, so that inference is auditable rather than hidden.

If the gauge never starts (missing hardware, I2C fault), the node keeps
advertising and notifying — `FLAG_BATTERY_VALID` just stays clear and the
three battery fields go out as zero, the same "present but honest" pattern
the SEN54 path uses for a missing sensor.

## Version authority

The phone app forwards these bytes **verbatim** — base64-encoded, alongside
a grid cell, hour of day, session id, and activity — rather than re-deriving
a decoded JSON object. The backend, via the Foreign Function & Memory API
(`backend/.../wire/PacketLayout.java`), is the sole authority on decoding.
`PacketLayout.decode()` dispatches on packet length (20 or 26 bytes) and the
`version` byte, so a v1 node's packets still decode correctly even after the
backend has moved on to understanding v2 — and the app's own subset decoder
(`app/.../ble/SensorPacket.java`) accepts either length too, treating
anything shorter than the v2 layout as simply having no battery data. That
duplication between the app's decoder and the backend's is real and
intentional, not eliminated, just kept minimal.

Note what is *not* forwarded: raw GPS. The phone snaps its fix to the grid
cell before uploading and keeps the precise trace to itself — see
`docs/honest-caveats.md` for why.

## Honesty mechanism

`FLAG_MOCK_DATA` is set on every packet produced by
`firmware/lib/mock_sensor_source/mock_sensor_source.cpp`. The app surfaces
this as a MOCK badge in the UI; the backend passes a `mock` field through
every API response. Neither is optional — no judge or user should be able to
mistake synthetic data for a real reading. Battery telemetry is real on both
firmware environments regardless of this flag — the physical MAX17048 is
composed onto the reading independently of which environmental sensor
source (mock or SEN54) produced the rest of the packet.

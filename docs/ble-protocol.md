# BLE protocol

StreetSense sensor nodes advertise a custom GATT service with a single notify
characteristic carrying a fixed 20-byte packet — chosen over a Nordic UART +
JSON approach for a concrete reason: JSON parsing on a Cortex-M4 microcontroller
is unnecessary overhead, and a packed binary struct that fits inside the
default ATT MTU removes an entire negotiation step from both sides of the
connection (see below).

## Identifiers

| | UUID |
|---|---|
| Device name | `StreetSense-01` |
| Service | `ee8ebcc5-07f5-4bce-96c2-ccafc2a91f7c` |
| Characteristic (notify) | `b70d5d1b-481e-4d17-8f6d-6b91b22d6b60` |

Defined once in `firmware/src/ble_uuids.h` and mirrored in
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

## Wire format — exactly 20 bytes

Sized to fit inside the default BLE ATT MTU of 23 bytes (20-byte payload +
3-byte L2CAP/ATT header), so a notification never needs MTU negotiation.
That's a deliberate simplification, not an oversight — if the schema ever
needs to grow past 20 bytes, MTU negotiation becomes unavoidable and the
`version` field is what signals the change.

All multi-byte fields are little-endian.

| Offset | Field | Type | Scale | Notes |
|---|---|---|---|---|
| 0 | `version` | u8 | — | schema version, currently `1` |
| 1 | `flags` | u8 | — | bit0 = `FLAG_MOCK_DATA` |
| 2 | `seq` | u16 | — | wraps; detects dropped notifications |
| 4 | `pm1` | u16 | ÷10 | ug/m³ |
| 6 | `pm2_5` | u16 | ÷10 | ug/m³ |
| 8 | `pm4` | u16 | ÷10 | ug/m³ |
| 10 | `pm10` | u16 | ÷10 | ug/m³ |
| 12 | `voc_index` | u16 | ÷10 | unitless VOC index |
| 14 | `temp_c` | i16 (signed) | ÷100 | °C |
| 16 | `humidity` | u16 | ÷100 | %RH |
| 18 | `noise_db` | u16 | ÷10 | dB(A) |

Total: 20 bytes. See `docs/golden-packet.md` for a concrete byte-for-byte
example independently reproduced by the firmware, Android, and backend test
suites.

## Version authority

The phone app forwards these 20 bytes **verbatim** — base64-encoded,
alongside a grid cell, hour of day, session id, and activity — rather than
re-deriving a decoded JSON object. The backend, via the Foreign Function &
Memory API (`backend/.../wire/PacketLayout.java`), is the sole authority on
decoding. This means a firmware/backend upgrade to a wider `version = 2`
packet still relays correctly through phones running an older app build,
since the app never re-encodes what it doesn't understand. The app does
decode a subset of fields for its own live display
(`app/.../ble/SensorPacket.java`) — that duplication is real and
intentional, not eliminated, just kept minimal.

Note what is *not* forwarded: raw GPS. The phone snaps its fix to the grid
cell before uploading and keeps the precise trace to itself — see
`docs/honest-caveats.md` for why.

## Honesty mechanism

`FLAG_MOCK_DATA` is set on every packet produced by
`firmware/src/mock_sensor_source.cpp`. The app surfaces this as a MOCK badge
in the UI; the backend passes a `mock` field through every API response.
Neither is optional — no judge or user should be able to mistake synthetic
data for a real reading.

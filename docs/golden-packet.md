# Golden packet fixtures

Canonical `StreetSensePacket` values, used verbatim by three independent test
suites:

- `firmware/test/test_packet/test_packet.cpp` (Unity)
- `app/src/test/java/io/streetsense/app/SensorPacketTest.java` (JUnit)
- `backend/src/test/java/io/streetsense/backend/wire/PacketLayoutTest.java` (JUnit + FFM)

Each suite hardcodes the same byte arrays independently, since there's no
shared build system across PlatformIO/Gradle-Android/Gradle-backend to import
a common file from. Agreement is proven by all three decoders producing the
same field values from the same bytes: if firmware, Android, and the backend
ever disagree on the wire format, one of these three tests catches it.

Generated via Python's `struct.pack(...)` to avoid any floating-point
rounding ambiguity between languages. The bytes below are exact, not derived
by encoding floats through each language's own scaling logic.

## v2, 26 bytes

`struct.pack('<BBHHHHHHhHHHHh', 2, 0x07, 42, 83, 157, 192, 246, 1345, 2137, 5280, 584, 3897, 875, 123)`

```
0x02, 0x07, 0x2A, 0x00, 0x53, 0x00, 0x9D, 0x00, 0xC0, 0x00,
0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, 0xA0, 0x14, 0x48, 0x02,
0x39, 0x0F, 0x6B, 0x03, 0x7B, 0x00
```

| Field | Raw (u16/i16) | Scale | Decoded value |
|---|---|---|---|
| `version` | 2 | n/a | `2` |
| `flags` | 0x07 | n/a | `FLAG_MOCK_DATA` (synthetic reading) \| `FLAG_CHARGING` \| `FLAG_BATTERY_VALID` all set |
| `seq` | 42 | n/a | `42` |
| `pm1` | 83 | ÷10 | `8.3` ug/m³ |
| `pm2_5` | 157 | ÷10 | `15.7` ug/m³ |
| `pm4` | 192 | ÷10 | `19.2` ug/m³ |
| `pm10` | 246 | ÷10 | `24.6` ug/m³ |
| `voc_index` | 1345 | ÷10 | `134.5` |
| `temp_c` | 2137 (signed) | ÷100 | `21.37` °C |
| `humidity` | 5280 | ÷100 | `52.80` %RH |
| `noise_db` | 584 | ÷10 | `58.4` dB(A) |
| `batt_mv` | 3897 | n/a | `3.897` V |
| `batt_soc` | 875 | ÷10 | `87.5` % |
| `batt_rate` | 123 (signed) | ÷10 | `+12.3` %/hour (charging) |

## v1, 20 bytes (no battery telemetry)

`struct.pack('<BBHHHHHHhHH', 1, 1, 42, 83, 157, 192, 246, 1345, 2137, 5280, 584)`

Same environmental values as the v2 fixture above, byte-identical to the
first 20 bytes of it minus the flags byte (v1 has no `FLAG_CHARGING` /
`FLAG_BATTERY_VALID` bits to set).

```
0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, 0x9D, 0x00, 0xC0, 0x00,
0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, 0xA0, 0x14, 0x48, 0x02
```

Decodes with the same environmental fields as above, `version = 1`,
`flags = FLAG_MOCK_DATA` only (a synthetic reading), and battery telemetry
absent (backend `DecodedPacket.battery() == BatteryStatus.ABSENT`; app
`SensorPacket.batteryValid() == false`). Kept as a fixture to prove the
decoders stay backward-compatible with nodes still running v1 firmware.

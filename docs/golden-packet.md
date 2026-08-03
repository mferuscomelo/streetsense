# Golden packet fixture

One canonical 20-byte `StreetSensePacket` value, used verbatim by three independent test suites:

- `firmware/test/test_packet/test_packet.cpp` (Unity)
- `app/src/test/java/io/streetsense/app/SensorPacketTest.java` (JUnit)
- `backend/src/test/java/io/streetsense/backend/wire/PacketLayoutTest.java` (JUnit + FFM)

Each suite hardcodes the same byte array independently (there's no shared build system across PlatformIO/Gradle-Android/Gradle-backend to import a common file from). Agreement is proven by all three decoders producing the same field values from the same bytes — if firmware, Android, and the backend ever disagree on the wire format, one of these three tests catches it.

Generated via Python's `struct.pack('<BBHHHHHHhHH', ...)` to avoid any floating-point rounding ambiguity between languages — the bytes below are exact, not derived by encoding floats through each language's own scaling logic.

## Bytes (20, little-endian)

```
0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, 0x9D, 0x00, 0xC0, 0x00,
0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, 0xA0, 0x14, 0x48, 0x02
```

## Expected decoded fields

| Field | Raw (u16/i16) | Scale | Decoded value |
|---|---|---|---|
| `version` | 1 | — | `1` |
| `flags` | 1 | — | `FLAG_MOCK_DATA` set |
| `seq` | 42 | — | `42` |
| `pm1` | 83 | ÷10 | `8.3` ug/m³ |
| `pm2_5` | 157 | ÷10 | `15.7` ug/m³ |
| `pm4` | 192 | ÷10 | `19.2` ug/m³ |
| `pm10` | 246 | ÷10 | `24.6` ug/m³ |
| `voc_index` | 1345 | ÷10 | `134.5` |
| `temp_c` | 2137 (signed) | ÷100 | `21.37` °C |
| `humidity` | 5280 | ÷100 | `52.80` %RH |
| `noise_db` | 584 | ÷10 | `58.4` dB(A) |

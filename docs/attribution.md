# Third-party materials

Every third-party component this project depends on, and the licence it
carries. The contest rules are strict about third-party materials, so this is
listed explicitly rather than left to be inferred from lockfiles.

## Firmware

| Component | Licence | Why it's here |
|---|---|---|
| [Sensirion I2C SEN5X](https://github.com/Sensirion/arduino-i2c-sen5x) `0.3.0` | BSD-3-Clause | Sensirion's own driver for the Sensirion SEN54. Hand-rolling the I2C command set and its CRC-8 would be a worse engineering decision and an untestable one — this project has no way to verify a hand-written driver except against the same hardware the official one already supports. |
| Sensirion Core `0.7.3` | BSD-3-Clause | Transitive dependency of the SEN5X driver. |
| [Adafruit MAX1704X](https://github.com/adafruit/Adafruit_MAX1704X) `1.0.3` | BSD | Adafruit's own driver for the MAX17048 fuel gauge on their 5580 breakout — same rationale as the SEN5X driver above. |
| Adafruit BusIO | MIT | Transitive dependency of the MAX1704X driver (shared I2C register helpers). |
| Adafruit nRF52 Arduino core (Bluefruit BLE stack, `PDM` library) | LGPL-2.1 / BSD, per component | Ships bundled with the board's framework package; unavoidable for any Arduino-framework build on this board. Used as a library, unmodified, and not redistributed in source form. |

The SEN5X driver, Sensirion Core, MAX1704X driver, and BusIO are fetched by
PlatformIO at build time from their published packages — see
`firmware/platformio.ini`. They are not vendored into this repository.

## Android app

| Component | Licence | Why it's here |
|---|---|---|
| [osmdroid](https://github.com/osmdroid/osmdroid) | Apache-2.0 | OpenStreetMap tile rendering for the session map. Chosen over the Google Maps SDK specifically to avoid an API key, a billing account, and proprietary tile terms. |
| OpenStreetMap tile data | ODbL 1.0 | Map data © OpenStreetMap contributors. Attribution is rendered on-map by osmdroid and repeated in the app's about screen. |

## Backend

Spring Boot and the JDK itself. No other runtime dependencies.

## What is *not* third-party

The BLE wire protocol, the packet encoder, the FFM decoder, the grid and
baseline model, the event classifier, the session model, and all firmware
outside the SEN5x driver are original to this project.

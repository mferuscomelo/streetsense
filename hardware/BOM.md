# Bill of Materials

Prices are approximate USD list prices at time of writing, not live quotes —
check current pricing before ordering.

| Item | Qty | Approx. price | Notes |
|---|---|---|---|
| [Adafruit LED Glasses Driver, nRF52840](https://www.adafruit.com/product/5217) | 1 | ~$25 | The sensor node's MCU + BLE radio. Onboard LIS3DH accelerometer (not a full IMU) and PDM digital microphone; STEMMA QT port. |
| Sensirion SEN54 (PM + VOC + temp/RH) | 1 | ~$40 | Firmware support is written and builds (`ledglasses_sen54` environment). Connects via STEMMA QT / Qwiic-compatible I2C. |
| [Adafruit SEN5x STEMMA QT adapter breakout](https://www.adafruit.com/product/5964) | 1 | ~$3 | Breaks the SEN54's JST-GH connector out to STEMMA QT so it can share the driver board's I2C bus. |
| [Adafruit MAX17048 fuel gauge](https://www.adafruit.com/product/5580) | 1 | ~$7 | Battery voltage, state of charge, and charge rate — see `docs/ble-protocol.md#battery-telemetry`. Shares the I2C bus with the SEN54 at a different address (0x36 vs 0x69). |
| STEMMA QT / Qwiic cable (JST-SH 4-pin) | 2 | ~$1 each | One for the SEN54 adapter, one for the MAX17048 breakout, both off the driver board's STEMMA QT port (or daisy-chained off each other). |
| LiPo battery, 3.7V (~500–1200 mAh) | 1 | ~$8–12 | The driver board has an onboard JST battery connector and charge circuit; the MAX17048 needs to be wired across the same cell (its own JST-PH is not the same physical connection as the driver board's) to report real charge state. |
| Android phone (physical device, API 31+) | 1 | — | BLE central; BLE does not work in the emulator. User-supplied. |

## Not included in slice 1

- The EyeLights LED glasses matrix panel itself — this project uses the
  driver board only, as a general-purpose nRF52840 BLE sensor node. The
  LED matrix is not driven by this firmware.
- Enclosure / mounting hardware — not sourced yet.

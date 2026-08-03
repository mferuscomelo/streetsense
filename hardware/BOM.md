# Bill of Materials

Prices are approximate USD list prices at time of writing, not live quotes —
check current pricing before ordering.

| Item | Qty | Approx. price | Notes |
|---|---|---|---|
| [Adafruit LED Glasses Driver, nRF52840](https://www.adafruit.com/product/5217) | 1 | ~$25 | The sensor node's MCU + BLE radio. Onboard LIS3DH accelerometer (not a full IMU) and PDM digital microphone; STEMMA QT port. |
| Sensirion SEN54 (PM + VOC + temp/RH) | 1 | ~$40 | On hand, not yet wired into firmware this slice — see `docs/future-work.md`. Connects via STEMMA QT / Qwiic-compatible I2C. |
| STEMMA QT / Qwiic cable (JST-SH 4-pin) | 1 | ~$1 | Connects the SEN54 to the driver board's STEMMA QT port. |
| LiPo battery, 3.7V (~500–1200 mAh) | 1 | ~$8–12 | The driver board has an onboard JST battery connector and charge circuit. |
| Android phone (physical device, API 31+) | 1 | — | BLE central; BLE does not work in the emulator. User-supplied. |

## Not included in slice 1

- The EyeLights LED glasses matrix panel itself — this project uses the
  driver board only, as a general-purpose nRF52840 BLE sensor node. The
  LED matrix is not driven by this firmware.
- Enclosure / mounting hardware — not sourced yet.

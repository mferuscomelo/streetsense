#!/usr/bin/env python3
"""Independent verification of the StreetSense wire format — no phone, no
Android app involved. Scans for StreetSense-01, subscribes to the sensor
characteristic, and decodes notifications the same way the backend's FFM
decoder does. If this matches but the phone doesn't, the bug is in the app,
not the protocol.

Usage: source .venv/bin/activate && python3 ble_sniff.py
"""
import asyncio
import struct

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "ee8ebcc5-07f5-4bce-96c2-ccafc2a91f7c"
CHARACTERISTIC_UUID = "b70d5d1b-481e-4d17-8f6d-6b91b22d6b60"
DEVICE_NAME = "StreetSense-01"

PACKET_STRUCT = "<BBHHHHHHhHHHHh"  # version,flags,seq,pm1,pm2_5,pm4,pm10,voc,temp,humidity,noise,batt_mv,batt_soc,batt_rate


def decode(raw: bytes) -> dict:
    (version, flags, seq, pm1, pm2_5, pm4, pm10, voc, temp, humidity, noise,
     batt_mv, batt_soc, batt_rate) = struct.unpack(PACKET_STRUCT, raw)
    return {
        "version": version,
        "mock": bool(flags & 0x01),
        "charging": bool(flags & 0x02),
        "battery_valid": bool(flags & 0x04),
        "seq": seq,
        "pm1": pm1 / 10, "pm2_5": pm2_5 / 10, "pm4": pm4 / 10, "pm10": pm10 / 10,
        "voc_index": voc / 10,
        "temp_c": temp / 100,
        "humidity": humidity / 100,
        "noise_db": noise / 10,
        "battery_volts": batt_mv / 1000,
        "battery_soc": batt_soc / 10,
        "battery_rate_pct_per_hr": batt_rate / 10,
    }


def on_notify(_handle, data: bytearray):
    if len(data) != 26:
        print(f"unexpected packet length: {len(data)}")
        return
    print(decode(bytes(data)))


async def main():
    print(f"Scanning for {DEVICE_NAME}...")
    device = await BleakScanner.find_device_by_name(DEVICE_NAME, timeout=15.0)
    if device is None:
        print(f"{DEVICE_NAME} not found within 15s.")
        return

    print(f"Found {device}. Connecting...")
    async with BleakClient(device) as client:
        print("Connected. Subscribing to notifications (Ctrl+C to stop)...")
        await client.start_notify(CHARACTERISTIC_UUID, on_notify)
        await asyncio.sleep(10.0)
        await client.stop_notify(CHARACTERISTIC_UUID)


if __name__ == "__main__":
    asyncio.run(main())

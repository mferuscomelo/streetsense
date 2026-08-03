#pragma once
#include <stdint.h>

// StreetSense custom GATT service. These UUIDs are the wire contract shared
// with the Android app (ble/SensorNodeClient.java) and documented in
// docs/ble-protocol.md — do not regenerate them.
//
// Service:        ee8ebcc5-07f5-4bce-96c2-ccafc2a91f7c
// Characteristic: b70d5d1b-481e-4d17-8f6d-6b91b22d6b60
//
// Bluefruit's BLEService/BLECharacteristic constructors take 128-bit UUIDs
// as a byte array in LSB-first (reversed) order — get this backwards and
// the service is silently unadvertised/undiscoverable, no error raised.
static uint8_t STREETSENSE_SERVICE_UUID[16] = {
    0x7C, 0x1F, 0xA9, 0xC2, 0xAF, 0xCC, 0xC2, 0x96,
    0xCE, 0x4B, 0xF5, 0x07, 0xC5, 0xBC, 0x8E, 0xEE
};

static uint8_t STREETSENSE_CHAR_UUID[16] = {
    0x60, 0x6B, 0x2D, 0xB2, 0x91, 0x6B, 0x6D, 0x8F,
    0x17, 0x4D, 0x1E, 0x48, 0x1B, 0x5D, 0x0D, 0xB7
};

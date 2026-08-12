#pragma once
#include <stdint.h>
#include <stddef.h>

// StreetSense sensor packet — exactly 26 bytes, little-endian.
//
// v1 was sized to fit inside the default BLE ATT MTU of 23 bytes (20-byte
// payload + 3-byte L2CAP/ATT header) so a notification never needed MTU
// negotiation. Adding battery telemetry needed more room than that budget
// had left, so v2 spends the MTU negotiation this format was originally
// built to avoid — see docs/ble-protocol.md for the tradeoff and
// firmware/src/main.cpp for where the peripheral requests the wider MTU.
//
// This layout is mirrored field-for-field by the backend's FFM MemoryLayout
// decoder (backend/.../PacketLayout.java) and the app's SensorPacket record
// (app/.../ble/SensorPacket.java). All three are proven to agree via a
// shared golden byte vector used in each language's test suite. The backend
// decoder is version-aware and still accepts 20-byte v1 packets.
#pragma pack(push, 1)
struct StreetSensePacket {
    uint8_t  version;     // schema version; currently 2
    uint8_t  flags;       // bit0 = FLAG_MOCK_DATA, bit1 = FLAG_CHARGING, bit2 = FLAG_BATTERY_VALID
    uint16_t seq;         // wraps; lets the receiver detect dropped notifications
    uint16_t pm1;         // ug/m3 x10
    uint16_t pm2_5;       // ug/m3 x10
    uint16_t pm4;         // ug/m3 x10
    uint16_t pm10;        // ug/m3 x10
    uint16_t voc_index;   // x10
    int16_t  temp_c;      // degrees C x100
    uint16_t humidity;    // %RH x100
    uint16_t noise_db;    // dB(A) x10
    uint16_t batt_mv;     // millivolts
    uint16_t batt_soc;    // percent x10
    int16_t  batt_rate;   // %/hour x10; negative while discharging
};
#pragma pack(pop)

static_assert(sizeof(StreetSensePacket) == 26, "StreetSensePacket must be exactly 26 bytes");

constexpr uint8_t STREETSENSE_PACKET_VERSION = 2;
constexpr uint8_t FLAG_MOCK_DATA = 0x01;
constexpr uint8_t FLAG_CHARGING = 0x02;
constexpr uint8_t FLAG_BATTERY_VALID = 0x04;

// A single environmental + battery reading, in real (unscaled) units. Sensor
// sources (mock or real) produce the environmental fields; main.cpp composes
// in the battery fields from BatteryMonitor before encode_packet() runs.
struct Reading {
    float pm1;
    float pm2_5;
    float pm4;
    float pm10;
    float voc_index;
    float temp_c;
    float humidity;
    float noise_db;
    bool  is_mock;

    // Battery fields are only meaningful when battery_valid is true — the
    // gauge may not have started (missing hardware, still warming up), in
    // which case these are ignored and FLAG_BATTERY_VALID stays clear.
    bool  battery_valid = false;
    bool  charging = false;
    float battery_volts = 0.0f;
    float battery_soc = 0.0f;          // percent, 0-100
    float battery_rate = 0.0f;         // percent per hour, negative = discharging
};

// Encodes `reading` into `out`, stamping `seq` and the mock/charging/battery
// flags. Pure function of its inputs — this is what the native unit tests
// exercise without any BLE stack or hardware.
void encode_packet(const Reading& reading, uint16_t seq, StreetSensePacket& out);

// Serializes `packet` into a 26-byte little-endian buffer, matching the
// on-the-wire representation notified over BLE. `buf` must have room for
// sizeof(StreetSensePacket) == 26 bytes.
void write_packet_bytes(const StreetSensePacket& packet, uint8_t* buf);

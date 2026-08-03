#pragma once
#include <stdint.h>
#include <stddef.h>

// StreetSense sensor packet — exactly 20 bytes, little-endian.
//
// Sized to fit inside the default BLE ATT MTU of 23 bytes (20-byte payload
// + 3-byte L2CAP/ATT header) so a StreetSense-01 notification never needs
// MTU negotiation. That removes an entire connection-setup state from both
// sides. If the schema ever needs to grow past 20 bytes, MTU negotiation
// becomes unavoidable — bump `version` and document the tradeoff there.
//
// This layout is mirrored field-for-field by the backend's FFM MemoryLayout
// decoder (backend/.../PacketLayout.java) and the app's SensorPacket record
// (app/.../ble/SensorPacket.java). All three are proven to agree via a
// shared golden byte vector used in each language's test suite.
#pragma pack(push, 1)
struct StreetSensePacket {
    uint8_t  version;     // schema version; currently 1
    uint8_t  flags;       // bit0 = FLAG_MOCK_DATA
    uint16_t seq;         // wraps; lets the receiver detect dropped notifications
    uint16_t pm1;         // ug/m3 x10
    uint16_t pm2_5;       // ug/m3 x10
    uint16_t pm4;         // ug/m3 x10
    uint16_t pm10;        // ug/m3 x10
    uint16_t voc_index;   // x10
    int16_t  temp_c;      // degrees C x100
    uint16_t humidity;    // %RH x100
    uint16_t noise_db;    // dB(A) x10
};
#pragma pack(pop)

static_assert(sizeof(StreetSensePacket) == 20, "StreetSensePacket must be exactly 20 bytes");

constexpr uint8_t STREETSENSE_PACKET_VERSION = 1;
constexpr uint8_t FLAG_MOCK_DATA = 0x01;

// A single environmental reading, in real (unscaled) units. Sensor sources
// (mock or real) produce this; encode() packs it into the wire format.
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
};

// Encodes `reading` into `out`, stamping `seq` and the mock flag.
// Pure function of its inputs — this is what the native unit tests exercise
// without any BLE stack or hardware.
void encode_packet(const Reading& reading, uint16_t seq, StreetSensePacket& out);

// Serializes `packet` into a 20-byte little-endian buffer, matching the
// on-the-wire representation notified over BLE. `buf` must have room for
// sizeof(StreetSensePacket) == 20 bytes.
void write_packet_bytes(const StreetSensePacket& packet, uint8_t* buf);

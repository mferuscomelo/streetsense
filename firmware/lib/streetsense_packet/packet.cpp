#include "packet.h"

static uint16_t scale_u16(float value, float factor) {
    float scaled = value * factor;
    if (scaled < 0) scaled = 0;
    if (scaled > 65535.0f) scaled = 65535.0f;
    return static_cast<uint16_t>(scaled + 0.5f);
}

static int16_t scale_i16(float value, float factor) {
    float scaled = value * factor;
    if (scaled < -32768.0f) scaled = -32768.0f;
    if (scaled > 32767.0f) scaled = 32767.0f;
    return static_cast<int16_t>(scaled >= 0 ? scaled + 0.5f : scaled - 0.5f);
}

void encode_packet(const Reading& reading, uint16_t seq, StreetSensePacket& out) {
    out.version   = STREETSENSE_PACKET_VERSION;
    out.flags     = reading.is_mock ? FLAG_MOCK_DATA : 0;
    out.seq       = seq;
    out.pm1       = scale_u16(reading.pm1, 10.0f);
    out.pm2_5     = scale_u16(reading.pm2_5, 10.0f);
    out.pm4       = scale_u16(reading.pm4, 10.0f);
    out.pm10      = scale_u16(reading.pm10, 10.0f);
    out.voc_index = scale_u16(reading.voc_index, 10.0f);
    out.temp_c    = scale_i16(reading.temp_c, 100.0f);
    out.humidity  = scale_u16(reading.humidity, 100.0f);
    out.noise_db  = scale_u16(reading.noise_db, 10.0f);
}

void write_packet_bytes(const StreetSensePacket& packet, uint8_t* buf) {
    // StreetSensePacket is #pragma pack(1) and little-endian on every target
    // this firmware runs on (Cortex-M4 and any native test host), so a
    // direct memcpy reproduces the exact wire format.
    const uint8_t* src = reinterpret_cast<const uint8_t*>(&packet);
    for (size_t i = 0; i < sizeof(StreetSensePacket); ++i) {
        buf[i] = src[i];
    }
}

#include <unity.h>
#include <string.h>
#include "packet.h"

void setUp(void) {}
void tearDown(void) {}

void test_packet_is_exactly_20_bytes(void) {
    TEST_ASSERT_EQUAL_UINT32(20, sizeof(StreetSensePacket));
}

void test_encode_scales_fields_correctly(void) {
    Reading reading{};
    reading.pm1 = 8.3f;
    reading.pm2_5 = 15.7f;
    reading.pm4 = 19.2f;
    reading.pm10 = 24.6f;
    reading.voc_index = 134.5f;
    reading.temp_c = 21.37f;
    reading.humidity = 52.80f;
    reading.noise_db = 58.4f;
    reading.is_mock = true;

    StreetSensePacket packet;
    encode_packet(reading, 42, packet);

    TEST_ASSERT_EQUAL_UINT8(STREETSENSE_PACKET_VERSION, packet.version);
    TEST_ASSERT_EQUAL_UINT8(FLAG_MOCK_DATA, packet.flags);
    TEST_ASSERT_EQUAL_UINT16(42, packet.seq);
    TEST_ASSERT_EQUAL_UINT16(83, packet.pm1);
    TEST_ASSERT_EQUAL_UINT16(157, packet.pm2_5);
    TEST_ASSERT_EQUAL_UINT16(192, packet.pm4);
    TEST_ASSERT_EQUAL_UINT16(246, packet.pm10);
    TEST_ASSERT_EQUAL_UINT16(1345, packet.voc_index);
    TEST_ASSERT_EQUAL_INT16(2137, packet.temp_c);
    TEST_ASSERT_EQUAL_UINT16(5280, packet.humidity);
    TEST_ASSERT_EQUAL_UINT16(584, packet.noise_db);
}

void test_non_mock_reading_clears_flag(void) {
    Reading reading{};
    reading.is_mock = false;

    StreetSensePacket packet;
    encode_packet(reading, 0, packet);

    TEST_ASSERT_EQUAL_UINT8(0, packet.flags & FLAG_MOCK_DATA);
}

// Golden byte vector — see docs/golden-packet.md. This exact 20-byte array
// is independently reproduced by the Android SensorPacketTest and the
// backend PacketLayoutTest; agreement here proves the wire format is fixed
// across all three languages.
void test_write_packet_bytes_matches_golden_vector(void) {
    Reading reading{};
    reading.pm1 = 8.3f;
    reading.pm2_5 = 15.7f;
    reading.pm4 = 19.2f;
    reading.pm10 = 24.6f;
    reading.voc_index = 134.5f;
    reading.temp_c = 21.37f;
    reading.humidity = 52.80f;
    reading.noise_db = 58.4f;
    reading.is_mock = true;

    StreetSensePacket packet;
    encode_packet(reading, 42, packet);

    uint8_t buf[20];
    write_packet_bytes(packet, buf);

    const uint8_t expected[20] = {
        0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, 0x9D, 0x00, 0xC0, 0x00,
        0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, 0xA0, 0x14, 0x48, 0x02
    };

    TEST_ASSERT_EQUAL_UINT8_ARRAY(expected, buf, 20);
}

void test_scaling_clamps_out_of_range_values(void) {
    Reading reading{};
    reading.pm1 = -5.0f;    // below zero must clamp to 0
    reading.pm2_5 = 7000.0f; // above u16-after-scale range must clamp to 65535
    reading.temp_c = -400.0f; // below i16-after-scale range must clamp to -32768
    reading.is_mock = true;

    StreetSensePacket packet;
    encode_packet(reading, 0, packet);

    TEST_ASSERT_EQUAL_UINT16(0, packet.pm1);
    TEST_ASSERT_EQUAL_UINT16(65535, packet.pm2_5);
    TEST_ASSERT_EQUAL_INT16(-32768, packet.temp_c);
}

int main(int argc, char** argv) {
    UNITY_BEGIN();
    RUN_TEST(test_packet_is_exactly_20_bytes);
    RUN_TEST(test_encode_scales_fields_correctly);
    RUN_TEST(test_non_mock_reading_clears_flag);
    RUN_TEST(test_write_packet_bytes_matches_golden_vector);
    RUN_TEST(test_scaling_clamps_out_of_range_values);
    return UNITY_END();
}

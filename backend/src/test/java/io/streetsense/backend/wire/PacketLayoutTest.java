package io.streetsense.backend.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Golden byte vectors — see docs/golden-packet.md. These exact arrays are
 * independently reproduced by the firmware Unity test
 * (test_write_packet_bytes_matches_golden_vector) and the Android
 * SensorPacketTest; agreement here proves the wire format is fixed across
 * all three languages.
 */
class PacketLayoutTest {

    // v1, 20 bytes — no battery telemetry.
    private static final byte[] GOLDEN_PACKET_V1 = {
            0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, (byte) 0x9D, 0x00, (byte) 0xC0, 0x00,
            (byte) 0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, (byte) 0xA0, 0x14, 0x48, 0x02
    };

    // v2, 26 bytes — same environmental values as the v1 fixture, plus
    // battery: 3.897V, 87.5% SoC, +12.3%/hr (charging).
    private static final byte[] GOLDEN_PACKET_V2 = {
            0x02, 0x07, 0x2A, 0x00, 0x53, 0x00, (byte) 0x9D, 0x00, (byte) 0xC0, 0x00,
            (byte) 0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, (byte) 0xA0, 0x14, 0x48, 0x02,
            0x39, 0x0F, 0x6B, 0x03, 0x7B, 0x00
    };

    @Test
    void decodesGoldenVectorFields() {
        DecodedPacket packet = PacketLayout.decode(GOLDEN_PACKET_V1);

        assertEquals(1, packet.version());
        assertTrue(packet.mock());
        assertEquals(42, packet.seq());
        assertEquals(8.3, packet.pm1(), 1e-9);
        assertEquals(15.7, packet.pm2_5(), 1e-9);
        assertEquals(19.2, packet.pm4(), 1e-9);
        assertEquals(24.6, packet.pm10(), 1e-9);
        assertEquals(134.5, packet.vocIndex(), 1e-9);
        assertEquals(21.37, packet.tempC(), 1e-9);
        assertEquals(52.80, packet.humidity(), 1e-9);
        assertEquals(58.4, packet.noiseDb(), 1e-9);
        assertFalse(packet.battery().valid());
    }

    @Test
    void decodesV2GoldenVectorFieldsIncludingBattery() {
        DecodedPacket packet = PacketLayout.decode(GOLDEN_PACKET_V2);

        assertEquals(2, packet.version());
        assertTrue(packet.mock());
        assertEquals(42, packet.seq());
        assertEquals(8.3, packet.pm1(), 1e-9);
        assertEquals(58.4, packet.noiseDb(), 1e-9);

        DecodedPacket.BatteryStatus battery = packet.battery();
        assertTrue(battery.valid());
        assertTrue(battery.charging());
        assertEquals(3.897, battery.volts(), 1e-9);
        assertEquals(87.5, battery.socPercent(), 1e-9);
        assertEquals(12.3, battery.ratePercentPerHour(), 1e-9);
    }

    @Test
    void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> PacketLayout.decode(new byte[]{1, 2, 3}));
    }
}

package io.streetsense.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.streetsense.app.ble.SensorPacket;
import org.junit.Test;

/**
 * Golden byte vectors — see docs/golden-packet.md. These exact arrays are
 * independently reproduced by the firmware Unity test
 * (test_write_packet_bytes_matches_golden_vector) and the backend's
 * PacketLayoutTest; agreement here proves the wire format is fixed across
 * all three languages.
 */
public class SensorPacketTest {

    // v1, 20 bytes — no battery telemetry.
    private static final byte[] GOLDEN_PACKET_V1 = {
            (byte) 0x01, (byte) 0x01, (byte) 0x2A, (byte) 0x00, (byte) 0x53,
            (byte) 0x00, (byte) 0x9D, (byte) 0x00, (byte) 0xC0, (byte) 0x00,
            (byte) 0xF6, (byte) 0x00, (byte) 0x41, (byte) 0x05, (byte) 0x59,
            (byte) 0x08, (byte) 0xA0, (byte) 0x14, (byte) 0x48, (byte) 0x02
    };

    // v2, 26 bytes — same environmental values as the v1 fixture, plus
    // battery: 3.897V, 87.5% SoC, +12.3%/hr (charging).
    private static final byte[] GOLDEN_PACKET_V2 = {
            (byte) 0x02, (byte) 0x07, (byte) 0x2A, (byte) 0x00, (byte) 0x53,
            (byte) 0x00, (byte) 0x9D, (byte) 0x00, (byte) 0xC0, (byte) 0x00,
            (byte) 0xF6, (byte) 0x00, (byte) 0x41, (byte) 0x05, (byte) 0x59,
            (byte) 0x08, (byte) 0xA0, (byte) 0x14, (byte) 0x48, (byte) 0x02,
            (byte) 0x39, (byte) 0x0F, (byte) 0x6B, (byte) 0x03, (byte) 0x7B, (byte) 0x00
    };

    @Test
    public void parsesGoldenVectorFields() {
        SensorPacket packet = SensorPacket.parse(GOLDEN_PACKET_V1);

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
        assertFalse(packet.batteryValid());
    }

    @Test
    public void parsesV2GoldenVectorFieldsIncludingBattery() {
        SensorPacket packet = SensorPacket.parse(GOLDEN_PACKET_V2);

        assertEquals(2, packet.version());
        assertTrue(packet.mock());
        assertEquals(42, packet.seq());
        assertEquals(8.3, packet.pm1(), 1e-9);
        assertEquals(15.7, packet.pm2_5(), 1e-9);
        assertEquals(19.2, packet.pm4(), 1e-9);
        assertEquals(24.6, packet.pm10(), 1e-9);
        assertEquals(58.4, packet.noiseDb(), 1e-9);
        assertTrue(packet.batteryValid());
        assertTrue(packet.charging());
        assertEquals(3.897, packet.batteryVolts(), 1e-9);
        assertEquals(87.5, packet.batterySoc(), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongLength() {
        SensorPacket.parse(new byte[]{1, 2, 3});
    }
}

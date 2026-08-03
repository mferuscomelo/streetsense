package io.streetsense.backend.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Golden byte vector — see docs/golden-packet.md. This exact 20-byte array
 * is independently reproduced by the firmware Unity test
 * (test_write_packet_bytes_matches_golden_vector) and the Android
 * SensorPacketTest; agreement here proves the wire format is fixed across
 * all three languages.
 */
class PacketLayoutTest {

    private static final byte[] GOLDEN_PACKET = {
            0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, (byte) 0x9D, 0x00, (byte) 0xC0, 0x00,
            (byte) 0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, (byte) 0xA0, 0x14, 0x48, 0x02
    };

    @Test
    void decodesGoldenVectorFields() {
        DecodedPacket packet = PacketLayout.decode(GOLDEN_PACKET);

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
    }

    @Test
    void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> PacketLayout.decode(new byte[]{1, 2, 3}));
    }
}

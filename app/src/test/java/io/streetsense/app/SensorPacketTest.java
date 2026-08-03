package io.streetsense.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.streetsense.app.ble.SensorPacket;
import org.junit.Test;

/**
 * Golden byte vector — see docs/golden-packet.md. This exact 20-byte array
 * is independently reproduced by the firmware Unity test
 * (test_write_packet_bytes_matches_golden_vector) and the backend's
 * PacketLayoutTest; agreement here proves the wire format is fixed across
 * all three languages.
 */
public class SensorPacketTest {

    private static final byte[] GOLDEN_PACKET = {
            (byte) 0x01, (byte) 0x01, (byte) 0x2A, (byte) 0x00, (byte) 0x53,
            (byte) 0x00, (byte) 0x9D, (byte) 0x00, (byte) 0xC0, (byte) 0x00,
            (byte) 0xF6, (byte) 0x00, (byte) 0x41, (byte) 0x05, (byte) 0x59,
            (byte) 0x08, (byte) 0xA0, (byte) 0x14, (byte) 0x48, (byte) 0x02
    };

    @Test
    public void parsesGoldenVectorFields() {
        SensorPacket packet = SensorPacket.parse(GOLDEN_PACKET);

        assertEquals(1, packet.version());
        assertTrue(packet.mock());
        assertEquals(42, packet.seq());
        assertEquals(15.7, packet.pm2_5(), 1e-9);
        assertEquals(134.5, packet.vocIndex(), 1e-9);
        assertEquals(21.37, packet.tempC(), 1e-9);
        assertEquals(52.80, packet.humidity(), 1e-9);
        assertEquals(58.4, packet.noiseDb(), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongLength() {
        SensorPacket.parse(new byte[]{1, 2, 3});
    }
}

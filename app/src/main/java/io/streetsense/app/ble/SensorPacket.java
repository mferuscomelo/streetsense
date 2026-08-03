package io.streetsense.app.ble;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes the subset of the 20-byte StreetSensePacket wire format the app
 * actually displays. The full record — including PM1/PM4/PM10, which this
 * app never shows — is decoded authoritatively by the backend via FFM; this
 * app forwards the raw bytes verbatim rather than re-deriving the complete
 * record, so a v2 packet with new fields still relays correctly even from
 * an app build that predates it.
 *
 * Layout mirrors firmware/lib/streetsense_packet/packet.h field-for-field.
 * See docs/ble-protocol.md and docs/golden-packet.md.
 */
public record SensorPacket(
        int version,
        boolean mock,
        int seq,
        double pm2_5,
        double vocIndex,
        double tempC,
        double humidity,
        double noiseDb) {

    private static final int PACKET_LENGTH = 20;
    private static final int FLAG_MOCK_DATA = 0x01;

    public static SensorPacket parse(byte[] raw) {
        if (raw.length != PACKET_LENGTH) {
            throw new IllegalArgumentException(
                    "expected " + PACKET_LENGTH + "-byte packet, got " + raw.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        int version = buf.get() & 0xFF;
        int flags = buf.get() & 0xFF;
        int seq = buf.getShort() & 0xFFFF;
        buf.getShort(); // pm1 — not displayed by this app
        int pm2_5Raw = buf.getShort() & 0xFFFF;
        buf.getShort(); // pm4 — not displayed by this app
        buf.getShort(); // pm10 — not displayed by this app
        int vocRaw = buf.getShort() & 0xFFFF;
        int tempRaw = buf.getShort(); // signed
        int humidityRaw = buf.getShort() & 0xFFFF;
        int noiseRaw = buf.getShort() & 0xFFFF;

        return new SensorPacket(
                version,
                (flags & FLAG_MOCK_DATA) != 0,
                seq,
                pm2_5Raw / 10.0,
                vocRaw / 10.0,
                tempRaw / 100.0,
                humidityRaw / 100.0,
                noiseRaw / 10.0);
    }
}

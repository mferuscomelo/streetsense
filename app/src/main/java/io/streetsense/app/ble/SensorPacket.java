package io.streetsense.app.ble;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes the StreetSensePacket wire format. The backend also decodes the
 * full record authoritatively via FFM; this app forwards the raw bytes
 * verbatim rather than re-deriving the complete record, so a packet with
 * fields newer than this parser knows about still relays correctly.
 *
 * Accepts both the 20-byte v1 layout (no battery telemetry, batteryValid
 * always false) and the 26-byte v2 layout. Layout mirrors
 * firmware/lib/streetsense_packet/packet.h field-for-field. See
 * docs/ble-protocol.md and docs/golden-packet.md.
 */
public record SensorPacket(
        int version,
        boolean mock,
        int seq,
        double pm1,
        double pm2_5,
        double pm4,
        double pm10,
        double vocIndex,
        double tempC,
        double humidity,
        double noiseDb,
        boolean batteryValid,
        boolean charging,
        double batteryVolts,
        double batterySoc) {

    private static final int PACKET_LENGTH_V1 = 20;
    private static final int PACKET_LENGTH_V2 = 26;
    private static final int FLAG_MOCK_DATA = 0x01;
    private static final int FLAG_CHARGING = 0x02;
    private static final int FLAG_BATTERY_VALID = 0x04;

    public static SensorPacket parse(byte[] raw) {
        if (raw.length < PACKET_LENGTH_V1) {
            throw new IllegalArgumentException(
                    "expected at least " + PACKET_LENGTH_V1 + "-byte packet, got " + raw.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        int version = buf.get() & 0xFF;
        int flags = buf.get() & 0xFF;
        int seq = buf.getShort() & 0xFFFF;
        int pm1Raw = buf.getShort() & 0xFFFF;
        int pm2_5Raw = buf.getShort() & 0xFFFF;
        int pm4Raw = buf.getShort() & 0xFFFF;
        int pm10Raw = buf.getShort() & 0xFFFF;
        int vocRaw = buf.getShort() & 0xFFFF;
        int tempRaw = buf.getShort(); // signed
        int humidityRaw = buf.getShort() & 0xFFFF;
        int noiseRaw = buf.getShort() & 0xFFFF;

        boolean batteryValid = false;
        boolean charging = false;
        int battMvRaw = 0;
        int battSocRaw = 0;
        if (raw.length >= PACKET_LENGTH_V2 && (flags & FLAG_BATTERY_VALID) != 0) {
            battMvRaw = buf.getShort() & 0xFFFF;
            battSocRaw = buf.getShort() & 0xFFFF;
            // batt_rate isn't displayed by this app.
            batteryValid = true;
            charging = (flags & FLAG_CHARGING) != 0;
        }

        return new SensorPacket(
                version,
                (flags & FLAG_MOCK_DATA) != 0,
                seq,
                pm1Raw / 10.0,
                pm2_5Raw / 10.0,
                pm4Raw / 10.0,
                pm10Raw / 10.0,
                vocRaw / 10.0,
                tempRaw / 100.0,
                humidityRaw / 100.0,
                noiseRaw / 10.0,
                batteryValid,
                charging,
                battMvRaw / 1000.0,
                battSocRaw / 10.0);
    }
}

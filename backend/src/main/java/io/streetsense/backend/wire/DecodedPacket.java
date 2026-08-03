package io.streetsense.backend.wire;

/**
 * The fully decoded 20-byte StreetSensePacket, in real (unscaled) units.
 * Mirrors firmware/lib/streetsense_packet/packet.h field-for-field.
 */
public record DecodedPacket(
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
        double noiseDb) {
}

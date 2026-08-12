package io.streetsense.backend.wire;

/**
 * The fully decoded StreetSensePacket, in real (unscaled) units. Mirrors
 * firmware/lib/streetsense_packet/packet.h field-for-field.
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
        double noiseDb,
        BatteryStatus battery) {

    /**
     * Battery telemetry, absent from v1 (20-byte) packets. {@code valid} and
     * {@code charging} mirror FLAG_BATTERY_VALID/FLAG_CHARGING; the other
     * fields are meaningless when {@code valid} is false.
     */
    public record BatteryStatus(
            boolean valid,
            boolean charging,
            double volts,
            double socPercent,
            double ratePercentPerHour) {

        public static final BatteryStatus ABSENT = new BatteryStatus(false, false, 0.0, 0.0, 0.0);
    }

    // Pre-battery constructor, kept so existing v1 call sites (tests and
    // ContributorSeeder) don't need to learn about battery telemetry they
    // never had — v1 packets simply decode with BatteryStatus.ABSENT.
    public DecodedPacket(
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
        this(version, mock, seq, pm1, pm2_5, pm4, pm10, vocIndex, tempC, humidity, noiseDb,
                BatteryStatus.ABSENT);
    }
}

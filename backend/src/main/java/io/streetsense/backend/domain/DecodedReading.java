package io.streetsense.backend.domain;

import io.streetsense.backend.wire.DecodedPacket;

import java.time.Instant;

/**
 * A decoded packet plus the GPS/time context the phone attached to it.
 */
public record DecodedReading(
        DecodedPacket packet,
        GridCell cell,
        double lat,
        double lon,
        Instant capturedAt) {

    public boolean mock() {
        return packet.mock();
    }

    public double pm1() { return packet.pm1(); }
    public double pm2_5() { return packet.pm2_5(); }
    public double pm4() { return packet.pm4(); }
    public double pm10() { return packet.pm10(); }
    public double vocIndex() { return packet.vocIndex(); }
    public double tempC() { return packet.tempC(); }
    public double humidity() { return packet.humidity(); }
    public double noiseDb() { return packet.noiseDb(); }
}

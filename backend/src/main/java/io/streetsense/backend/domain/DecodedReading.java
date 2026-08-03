package io.streetsense.backend.domain;

import io.streetsense.backend.wire.DecodedPacket;

import java.time.Instant;

/**
 * A decoded packet plus the context the phone attached to it.
 *
 * <p><b>There is deliberately no latitude or longitude here.</b> The phone
 * snaps its GPS fix to a {@link GridCell} before uploading and keeps the
 * precise trace on the device, so the backend never receives — and therefore
 * cannot store, log, or leak — a contributor's path. That is the whole
 * privacy model, and it holds because this type has nowhere to put a
 * coordinate, not because every future call site remembers not to persist
 * one.
 *
 * <p>The session map in the app is drawn from the phone's own local trace,
 * never from anything the server holds.
 */
public record DecodedReading(
        DecodedPacket packet,
        GridCell cell,
        int hourOfDay,
        String sessionId,
        String contributorId,
        Activity activity,
        Instant capturedAt) {

    public CellKey key() {
        return new CellKey(cell, hourOfDay);
    }

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

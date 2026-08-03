package io.streetsense.app.session;

/**
 * One point on the local trace of a session: where you were, and what the air
 * was like there.
 *
 * <p><b>This is the precise coordinate that never leaves the device.</b> The
 * upload path snaps to a grid cell instead (see
 * {@code upload/ReadingUploader}); this class exists so the session map can
 * still draw the actual route you walked. Everything here stays in app-local
 * storage.
 */
public final class TracePoint {

    private final double lat;
    private final double lon;
    private final double pm2_5;
    private final double noiseDb;
    private final long timestampMillis;

    public TracePoint(double lat, double lon, double pm2_5, double noiseDb, long timestampMillis) {
        this.lat = lat;
        this.lon = lon;
        this.pm2_5 = pm2_5;
        this.noiseDb = noiseDb;
        this.timestampMillis = timestampMillis;
    }

    public double lat() { return lat; }
    public double lon() { return lon; }
    public double pm2_5() { return pm2_5; }
    public double noiseDb() { return noiseDb; }
    public long timestampMillis() { return timestampMillis; }
}

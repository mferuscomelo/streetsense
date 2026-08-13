package io.streetsense.app.session;

/**
 * One point on the local trace of a session: where you were, and what the air
 * was like there. Doubles as the sample unit for the readings screens (hero
 * card, sparklines, metric sheet, summary) — one instance per decoded packet
 * that had a GPS fix.
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
    private final double pm1;
    private final double pm2_5;
    private final double pm4;
    private final double pm10;
    private final double noiseDb;
    private final double vocIndex;
    private final double tempC;
    private final double humidity;
    private final long timestampMillis;

    public TracePoint(double lat, double lon, double pm1, double pm2_5, double pm4, double pm10,
                       double noiseDb, double vocIndex, double tempC, double humidity,
                       long timestampMillis) {
        this.lat = lat;
        this.lon = lon;
        this.pm1 = pm1;
        this.pm2_5 = pm2_5;
        this.pm4 = pm4;
        this.pm10 = pm10;
        this.noiseDb = noiseDb;
        this.vocIndex = vocIndex;
        this.tempC = tempC;
        this.humidity = humidity;
        this.timestampMillis = timestampMillis;
    }

    public double lat() { return lat; }
    public double lon() { return lon; }
    public double pm1() { return pm1; }
    public double pm2_5() { return pm2_5; }
    public double pm4() { return pm4; }
    public double pm10() { return pm10; }
    public double noiseDb() { return noiseDb; }
    public double vocIndex() { return vocIndex; }
    public double tempC() { return tempC; }
    public double humidity() { return humidity; }
    public long timestampMillis() { return timestampMillis; }
}

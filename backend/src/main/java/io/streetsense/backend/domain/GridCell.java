package io.streetsense.backend.domain;

/**
 * A fixed-size lat/lon bucket — the unit of everything StreetSense shares.
 *
 * <p>{@code CELL_SIZE_DEGREES} is not a tuning knob. It sets two things at
 * once, pulling in opposite directions:
 *
 * <ul>
 *   <li><b>Route resolution.</b> A cell is the finest thing the app can say
 *       "this bit was bad" about. At the original 0.01° (~1.1km) an entire 5km
 *       run fell into three or four cells, so "which part of my route" had no
 *       answer.</li>
 *   <li><b>Anonymity.</b> A cell is also the only location a contribution ever
 *       carries — the phone snaps to a cell before uploading and the precise
 *       trace never leaves the device. Finer cells mean a contribution points
 *       at a smaller area.</li>
 * </ul>
 *
 * <p>0.001° of latitude is ~111m everywhere on Earth, about a city block:
 * fine enough to distinguish one street from the next, coarse enough that a
 * contribution names a block rather than a building. See
 * {@code docs/honest-caveats.md}. Longitude is different — 0.001° of
 * longitude shrinks toward the poles (it's a fixed fraction of a parallel's
 * circumference, and parallels get shorter with latitude), so bucketing
 * longitude at a flat 0.001° makes cells narrower than they are tall almost
 * everywhere off the equator. The longitude step is widened by
 * {@code 1 / cos(latitude)} — the same secant correction Web Mercator itself
 * uses — so a cell is close to square in real-world metres (and therefore
 * on any conformally-projected map) at whatever latitude it sits at, not
 * just at the equator.
 *
 * <p><b>This formula is mirrored in {@code app/.../location/GridCell.java}
 * and the two must agree</b>, the same way the BLE UUIDs are mirrored between
 * firmware and app. {@code GridCellTest} and the app's {@code GridCellTest}
 * assert the same golden coordinates on both sides; if they drift, readings
 * land in cells the backend never groups together and baselines quietly go
 * thin instead of failing loudly.
 *
 * <p>Real grid sophistication (geohash precision tuned per density,
 * PostGIS-backed spatial binning) lands with the Postgres slice and replaces
 * this without an API change.
 */
public record GridCell(int latBucket, int lonBucket) {

    static final double CELL_SIZE_DEGREES = 0.001;

    /**
     * Snaps a coordinate to its cell. Kept for tests, seeding, and any
     * server-side path that still has a raw coordinate — the ingest path
     * does not, by design: the phone snaps before upload.
     */
    public static GridCell of(double lat, double lon) {
        int latBucket = (int) Math.floor(lat / CELL_SIZE_DEGREES);
        int lonBucket = (int) Math.floor(lon / lonStepDegrees(latBucket));
        return new GridCell(latBucket, lonBucket);
    }

    /** Latitude cells per longitude-correction band — ~11.1km. */
    private static final int LAT_BAND_CELLS = 100;

    /**
     * The longitude width, in degrees, of every cell in {@code latBucket}'s
     * band — widened by {@code 1 / cos(latitude)} so a cell is square in
     * real-world metres rather than a flat 0.001° regardless of how
     * compressed longitude is there.
     *
     * <p>The correction is resolved per <em>band</em> of {@link
     * #LAT_BAND_CELLS} rows (~11.1km), not per individual row. A lonBucket
     * is a global integer counted from the prime meridian — for a
     * mid-latitude city that's already in the thousands — and multiplying
     * that large integer by a step size that changes with every row turns
     * cos(latitude)'s per-row floating-point drift (sub-micro-degree) into a
     * visible, growing offset between rows a few blocks apart, the same way
     * a tiny per-mile compass error puts you miles off course over a long
     * walk. Sharing one step across a whole band keeps every cell within it
     * — the span of a typical single-city deployment — bit-for-bit aligned;
     * the only cost is a barely-perceptible seam at a band boundary, the
     * same trade every real-world planar grid over a round Earth makes (UTM
     * zones, state-plane coordinate zones).
     */
    static double lonStepDegrees(int latBucket) {
        int band = Math.floorDiv(latBucket, LAT_BAND_CELLS);
        double bandCenterLat = (band * LAT_BAND_CELLS + LAT_BAND_CELLS / 2.0) * CELL_SIZE_DEGREES;
        return CELL_SIZE_DEGREES / Math.cos(Math.toRadians(bandCenterLat));
    }
}

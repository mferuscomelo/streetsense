package io.streetsense.app.location;

/**
 * A fixed-size lat/lon bucket — the only location StreetSense ever uploads.
 *
 * <p>This is a deliberate mirror of {@code backend/.../domain/GridCell.java},
 * not a coincidence. The phone snaps here, before upload, so the precise fix
 * stays on the device: the backend has no coordinate to store, log or leak,
 * and the session map is drawn from the local trace instead. That is the
 * whole privacy model, and it only works if this snapping happens on this
 * side of the network.
 *
 * <p><b>The bucketing formula must equal the backend's.</b> Drift between
 * the two doesn't throw — it just puts readings in cells the backend never
 * groups together, and baselines quietly stay thin. {@code GridCellTest} here
 * and {@code GridCellTest} there assert the same golden coordinates so that
 * failure is loud. Same discipline the BLE UUIDs get in
 * {@code docs/ble-protocol.md}.
 *
 * <p>0.001° of latitude is ~111m everywhere on Earth, about a city block:
 * fine enough to tell one street from the next, coarse enough that a
 * contribution names a block rather than a building. Longitude is different
 * — 0.001° of longitude shrinks toward the poles, so bucketing it at a flat
 * 0.001° makes cells narrower than they are tall almost everywhere off the
 * equator. The longitude step is widened by {@code 1 / cos(latitude)} — the
 * same secant correction Web Mercator itself uses — so a cell stays close to
 * square in real-world metres at whatever latitude it sits at.
 */
public final class GridCell {

    private static final double CELL_SIZE_DEGREES = 0.001;

    private final int latBucket;
    private final int lonBucket;

    private GridCell(int latBucket, int lonBucket) {
        this.latBucket = latBucket;
        this.lonBucket = lonBucket;
    }

    public static GridCell of(double lat, double lon) {
        // Math.floor, not a cast: truncation rounds toward zero, which would
        // merge the cells either side of the equator and the prime meridian.
        int latBucket = (int) Math.floor(lat / CELL_SIZE_DEGREES);
        int lonBucket = (int) Math.floor(lon / lonStepDegrees(latBucket));
        return new GridCell(latBucket, lonBucket);
    }

    /** Latitude cells per longitude-correction band — ~11.1km. */
    private static final int LAT_BAND_CELLS = 100;

    /**
     * The longitude width, in degrees, of every cell in {@code latBucket}'s
     * band. See the class doc for why this isn't a flat
     * {@code CELL_SIZE_DEGREES}, and why it's resolved per band of {@link
     * #LAT_BAND_CELLS} rows rather than per individual row — a lonBucket is
     * a large integer counted from the prime meridian, and multiplying it by
     * a step that changes every row would turn cos(latitude)'s per-row
     * floating-point drift into a visible offset between nearby rows. Must
     * match the backend's {@code GridCell.lonStepDegrees} exactly.
     */
    private static double lonStepDegrees(int latBucket) {
        int band = Math.floorDiv(latBucket, LAT_BAND_CELLS);
        double bandCenterLat = (band * LAT_BAND_CELLS + LAT_BAND_CELLS / 2.0) * CELL_SIZE_DEGREES;
        return CELL_SIZE_DEGREES / Math.cos(Math.toRadians(bandCenterLat));
    }

    public int latBucket() {
        return latBucket;
    }

    public int lonBucket() {
        return lonBucket;
    }

    @Override
    public String toString() {
        return "GridCell[" + latBucket + "," + lonBucket + "]";
    }
}

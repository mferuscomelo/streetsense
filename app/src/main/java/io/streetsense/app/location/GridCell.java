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
 * <p><b>{@code CELL_SIZE_DEGREES} must equal the backend's.</b> Drift between
 * the two doesn't throw — it just puts readings in cells the backend never
 * groups together, and baselines quietly stay thin. {@code GridCellTest} here
 * and {@code GridCellTest} there assert the same golden coordinates so that
 * failure is loud. Same discipline the BLE UUIDs get in
 * {@code docs/ble-protocol.md}.
 *
 * <p>0.001° is ~111m of latitude, about a city block: fine enough to tell one
 * street from the next, coarse enough that a contribution names a block
 * rather than a building.
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
        return new GridCell(
                (int) Math.floor(lat / CELL_SIZE_DEGREES),
                (int) Math.floor(lon / CELL_SIZE_DEGREES));
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

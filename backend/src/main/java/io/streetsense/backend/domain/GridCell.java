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
 * <p>0.001° is ~111m of latitude, about a city block: fine enough to
 * distinguish one street from the next, coarse enough that a contribution
 * names a block rather than a building. See {@code docs/honest-caveats.md}.
 *
 * <p><b>This constant is mirrored in {@code app/.../location/GridCell.java}
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
        return new GridCell(
                (int) Math.floor(lat / CELL_SIZE_DEGREES),
                (int) Math.floor(lon / CELL_SIZE_DEGREES));
    }
}

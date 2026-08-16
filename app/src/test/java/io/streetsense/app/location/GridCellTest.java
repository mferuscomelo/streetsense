package io.streetsense.app.location;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The app half of the cross-tier grid agreement.
 *
 * <p>The backend has a {@code GridCellTest} asserting these exact same
 * coordinates and buckets. If the two implementations ever drift — a changed
 * cell size on one side, truncation instead of floor — readings land in cells
 * the backend never groups together, and the only symptom is baselines that
 * quietly stay thin. Nothing throws. This table is what makes that loud.
 *
 * <p>Same discipline the BLE UUIDs get in docs/ble-protocol.md: mirrored
 * deliberately, with drift asserted rather than hoped against.
 */
public class GridCellTest {

    @Test
    public void goldenCoordinatesBucketAsTheBackendDoes() {
        assertBucket(49.0069, 8.4037, 49006, 5507);      // Karlsruhe
        assertBucket(51.5074, -0.1278, 51507, -80);      // London, west of the meridian
        assertBucket(-33.8688, 151.2093, -33869, 125579); // Sydney, southern hemisphere
    }

    @Test
    public void negativeCoordinatesFloorDownwardRatherThanTowardZero() {
        // (int) truncation would put -0.0005 and 0.0005 in the same bucket,
        // silently merging two cells either side of the equator or meridian.
        assertBucket(-0.0005, -0.0005, -1, -1);
        assertBucket(0.0005, 0.0005, 0, 0);
    }

    private static void assertBucket(double lat, double lon, int expectedLat, int expectedLon) {
        GridCell cell = GridCell.of(lat, lon);
        assertEquals("latBucket for " + lat, expectedLat, cell.latBucket());
        assertEquals("lonBucket for " + lon, expectedLon, cell.lonBucket());
    }
}

package io.streetsense.backend.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridCellTest {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;

    // Karlsruhe, well away from the equator, the prime meridian, and any
    // bucket boundary — so none of these tests accidentally depend on
    // sign handling or on a coordinate landing exactly on an edge.
    private static final double LAT = 49.0069;
    private static final double LON = 8.4037;

    @Test
    void aKilometreOfWalkingCrossesRoughlyTenCells() {
        // This is the property the whole "which part of my route was bad"
        // feature rests on. The original ~1.1km grid put an entire 5km run
        // inside three or four cells, which cannot answer that question.
        Set<GridCell> visited = new HashSet<>();
        for (int metres = 0; metres <= 1000; metres += 5) {
            visited.add(GridCell.of(LAT + metres / METRES_PER_DEGREE_LAT, LON));
        }

        assertTrue(visited.size() >= 8 && visited.size() <= 11,
                "expected ~9-10 cells per km, got " + visited.size());
    }

    @Test
    void cellsAreCoarseEnoughToNotIdentifyAnAddress() {
        // The other half of the trade: cells are the unit of contribution to
        // the crowd layer, so a cell must cover a block rather than a house.
        // Anything finer than ~50m would start resolving individual buildings.
        Set<GridCell> visited = new HashSet<>();
        for (int metres = 0; metres <= 50; metres += 1) {
            visited.add(GridCell.of(LAT + metres / METRES_PER_DEGREE_LAT, LON));
        }

        assertTrue(visited.size() <= 2,
                "a 50m stretch should fall in at most two cells, got " + visited.size());
    }

    @Test
    void goldenCoordinatesBucketAsThePhoneDoes() {
        // The backend half of the cross-tier grid agreement. The app has a
        // GridCellTest asserting this exact same table. If the two drift —
        // a changed cell size on one side, truncation instead of floor — the
        // phone's readings land in cells this side never groups together, and
        // the only symptom is baselines quietly staying thin. Nothing throws.
        // Same discipline the BLE UUIDs get in docs/ble-protocol.md.
        assertBucket(49.0069, 8.4037, 49006, 8403);       // Karlsruhe
        assertBucket(51.5074, -0.1278, 51507, -128);      // London, west of the meridian
        assertBucket(-33.8688, 151.2093, -33869, 151209); // Sydney, southern hemisphere
        assertBucket(-0.0005, -0.0005, -1, -1);
        assertBucket(0.0005, 0.0005, 0, 0);
    }

    private static void assertBucket(double lat, double lon, int expectedLat, int expectedLon) {
        GridCell cell = GridCell.of(lat, lon);
        assertEquals(expectedLat, cell.latBucket(), "latBucket for " + lat);
        assertEquals(expectedLon, cell.lonBucket(), "lonBucket for " + lon);
    }

    @Test
    void nearbyPointsShareACell() {
        GridCell a = GridCell.of(49.00690, 8.40370);
        GridCell b = GridCell.of(49.00691, 8.40371);

        assertEquals(a, b);
    }

    @Test
    void southernAndWesternHemispheresBucketDownward() {
        // Math.floor, not truncation: -0.0005 must land in the cell below
        // zero, not share a cell with +0.0005. Truncation toward zero would
        // silently merge two cells straddling the equator or meridian.
        GridCell justSouth = GridCell.of(-0.0005, -0.0005);
        GridCell justNorth = GridCell.of(0.0005, 0.0005);

        assertTrue(justSouth.latBucket() < justNorth.latBucket());
        assertTrue(justSouth.lonBucket() < justNorth.lonBucket());
    }
}

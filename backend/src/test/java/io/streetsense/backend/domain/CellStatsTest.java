package io.streetsense.backend.domain;

import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CellStatsTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);

    private static DecodedReading reading(String contributorId, double pm25) {
        DecodedPacket packet = new DecodedPacket(1, false, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, 8, "session-" + contributorId,
                contributorId, Activity.RUN, Instant.now());
    }

    @Test
    void contributorCountCountsDistinctPeopleNotReadings() {
        // The crowd layer's honesty depends on this distinction. 400 readings
        // from one person walking the same street every morning is not the
        // same evidence as 400 readings from twelve people, and the UI has to
        // be able to tell a viewer which one they're looking at.
        List<DecodedReading> window = List.of(
                reading("alice", 10), reading("alice", 11), reading("alice", 12),
                reading("bob", 13), reading("bob", 14));

        CellStats stats = CellStats.of(window);

        assertEquals(5, stats.sampleCount());
        assertEquals(2, stats.contributorCount());
    }

    @Test
    void anEmptyWindowHasNoContributors() {
        CellStats stats = CellStats.of(List.of());

        assertEquals(0, stats.sampleCount());
        assertEquals(0, stats.contributorCount());
    }
}

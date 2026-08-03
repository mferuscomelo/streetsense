package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.repository.InMemoryReadingRepository;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowdServiceTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);

    private final InMemoryReadingRepository repository = new InMemoryReadingRepository();
    private final CrowdService crowd = new CrowdService(repository);

    private void save(String contributorId, int hourOfDay, double pm25, double noiseDb) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, noiseDb);
        repository.save(new DecodedReading(packet, CELL, hourOfDay, "s-" + contributorId,
                contributorId, Activity.WALK, Instant.now()));
    }

    @Test
    void reportsTheCleanestHourToBeOnThisBlock() {
        // The recommendation the whole hour-keyed model exists to produce.
        // Not "avoid this street" — "go at seven, not at six".
        for (int i = 0; i < 5; i++) {
            save("alice", 7, 8.0, 50.0);
            save("alice", 18, 60.0, 70.0);
            save("alice", 12, 30.0, 60.0);
        }

        CellSummary summary = crowd.summarise(CELL);

        assertEquals(7, summary.cleanestHour());
    }

    @Test
    void reportsTheQuietestHourSeparatelyFromTheCleanest() {
        // They are genuinely different questions and can have different
        // answers — a road can be quiet at the exact hour it is dirtiest.
        for (int i = 0; i < 5; i++) {
            save("alice", 7, 40.0, 45.0);   // quietest, but not cleanest
            save("alice", 14, 5.0, 80.0);   // cleanest, but loudest
        }

        CellSummary summary = crowd.summarise(CELL);

        assertEquals(14, summary.cleanestHour());
        assertEquals(7, summary.quietestHour());
    }

    @Test
    void countsDistinctContributorsAcrossTheWholeCellNotJustOneHour() {
        for (int i = 0; i < 3; i++) {
            save("alice", 7, 10.0, 50.0);
            save("bob", 18, 10.0, 50.0);
            save("carol", 18, 10.0, 50.0);
        }

        CellSummary summary = crowd.summarise(CELL);

        assertEquals(3, summary.contributorCount());
    }

    @Test
    void seededContributorsAreCountedSeparatelyAndNeverPassedOffAsReal() {
        // Same discipline as FLAG_MOCK_DATA. A viewer must always be able to
        // tell how much of the evidence in front of them was measured and how
        // much was generated to demonstrate the merge.
        for (int i = 0; i < 3; i++) {
            save("alice", 7, 10.0, 50.0);
            save(CrowdService.SEEDED_PREFIX + "bob", 7, 10.0, 50.0);
            save(CrowdService.SEEDED_PREFIX + "carol", 7, 10.0, 50.0);
        }

        CellSummary summary = crowd.summarise(CELL);

        assertEquals(3, summary.contributorCount());
        assertEquals(2, summary.seededContributorCount());
        assertTrue(summary.hasSeededData());
    }

    @Test
    void aCellNobodyHasSampledIsHonestlyEmptyRatherThanConfidentlyWrong() {
        CellSummary summary = crowd.summarise(GridCell.of(0.0, 0.0));

        assertEquals(0, summary.sampleCount());
        assertEquals(0, summary.contributorCount());
        assertFalse(summary.hasEnoughEvidence(),
                "an unsampled cell must not present itself as knowing anything");
    }

    @Test
    void aCellSampledOnlyByOnePersonSaysSoRatherThanClaimingConsensus() {
        for (int i = 0; i < 50; i++) {
            save("alice", 7, 10.0, 50.0);
        }

        CellSummary summary = crowd.summarise(CELL);

        assertEquals(1, summary.contributorCount());
        assertEquals(CellSummary.Confidence.SINGLE_CONTRIBUTOR, summary.confidence(),
                "50 readings from one person is not a crowd");
    }

    @Test
    void theCityViewListsEveryCellAnyoneHasContributedTo() {
        save("alice", 7, 10.0, 50.0);
        save("bob", 7, 10.0, 50.0);

        List<CellSummary> city = crowd.cityView();

        assertEquals(1, city.size());
        assertEquals(CELL, city.getFirst().cell());
    }
}

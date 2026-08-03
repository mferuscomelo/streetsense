package io.streetsense.backend.baseline;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.repository.InMemoryReadingRepository;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollingBaselineTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037); // Karlsruhe

    private DecodedReading readingWithPm25(double pm25) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, 49.0069, 8.4037, Instant.now());
    }

    @Test
    void currentBaselineIsEmptyBeforeAnyUpdates() {
        RollingBaseline baseline = new RollingBaseline(new InMemoryReadingRepository());
        CellStats stats = baseline.currentBaseline(CELL);
        assertEquals(0, stats.sampleCount());
    }

    @Test
    void baselineTracksMeanAcrossSteadyReadings() {
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        RollingBaseline baseline = new RollingBaseline(repository);

        CellStats last = null;
        for (int i = 0; i < 8; i++) {
            DecodedReading reading = readingWithPm25(15.0);
            repository.save(reading);
            last = baseline.update(reading);
        }

        assertTrue(last.sampleCount() > 0);
        assertEquals(15.0, last.meanPm2_5(), 0.5);
    }

    @Test
    void baselineShiftsTowardNewValuesGradually() {
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        RollingBaseline baseline = new RollingBaseline(repository);

        for (int i = 0; i < 10; i++) {
            DecodedReading reading = readingWithPm25(15.0);
            repository.save(reading);
            baseline.update(reading);
        }
        double steadyMean = baseline.currentBaseline(CELL).meanPm2_5();

        // A single spike shouldn't fully overwrite the established baseline —
        // that's the point of the EWMA smoothing.
        DecodedReading spike = readingWithPm25(80.0);
        repository.save(spike);
        CellStats afterSpike = baseline.update(spike);

        assertTrue(afterSpike.meanPm2_5() > steadyMean, "baseline should shift up");
        assertTrue(afterSpike.meanPm2_5() < 80.0, "one spike shouldn't fully overwrite the baseline");
    }
}

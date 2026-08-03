package io.streetsense.backend.baseline;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.CellKey;
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
        return new DecodedReading(packet, CELL, 12, "test-session", "contributor-a", Activity.RUN, Instant.now());
    }

    private DecodedReading readingBy(String contributorId, double pm25) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, 12, "session-" + contributorId, contributorId, Activity.RUN, Instant.now());
    }

    private DecodedReading readingAt(int hourOfDay, double pm25) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, hourOfDay, "test-session", "contributor-a", Activity.RUN, Instant.now());
    }

    @Test
    void currentBaselineIsEmptyBeforeAnyUpdates() {
        RollingBaseline baseline = new RollingBaseline(new InMemoryReadingRepository());
        CellStats stats = baseline.currentBaseline(new CellKey(CELL, 12));
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
    void theSameStreetKeepsSeparateBaselinesPerHour() {
        // A street at 07:00 and the same street at 18:00 are different places
        // as far as this system is concerned. Pooling them produces a mean
        // that describes neither: a perfectly ordinary rush-hour reading looks
        // anomalous against an all-day average, and a genuinely quiet morning
        // looks unremarkable. It is also the only way "when is this block
        // quietest" has an answer at all.
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        RollingBaseline baseline = new RollingBaseline(repository);

        for (int i = 0; i < 8; i++) {
            DecodedReading quietMorning = readingAt(7, 8.0);
            repository.save(quietMorning);
            baseline.update(quietMorning);

            DecodedReading busyEvening = readingAt(18, 60.0);
            repository.save(busyEvening);
            baseline.update(busyEvening);
        }

        CellStats morning = baseline.currentBaseline(new CellKey(CELL, 7));
        CellStats evening = baseline.currentBaseline(new CellKey(CELL, 18));

        assertEquals(8.0, morning.meanPm2_5(), 2.0);
        assertEquals(60.0, evening.meanPm2_5(), 5.0);
    }

    @Test
    void contributorCountSpansTheWholeHistoryNotTheLatestWindow() {
        // The sliding window is 5 readings wide. If contributor count came
        // from the window, a cell sampled by ten people over a month would
        // report however many happened to appear in the last five readings —
        // usually one. The number the UI shows has to mean "people who have
        // ever sampled here", or the confidence indicator is a lie.
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        RollingBaseline baseline = new RollingBaseline(repository);

        DecodedReading first = readingBy("alice", 15.0);
        repository.save(first);
        baseline.update(first);

        // Bob then contributes enough readings to fill the window on his own.
        CellStats stats = null;
        for (int i = 0; i < 10; i++) {
            DecodedReading r = readingBy("bob", 15.0);
            repository.save(r);
            stats = baseline.update(r);
        }

        assertEquals(2, stats.contributorCount(),
                "alice contributed to this cell and must still be counted");
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
        double steadyMean = baseline.currentBaseline(new CellKey(CELL, 12)).meanPm2_5();

        // A single spike shouldn't fully overwrite the established baseline —
        // that's the point of the EWMA smoothing.
        DecodedReading spike = readingWithPm25(80.0);
        repository.save(spike);
        CellStats afterSpike = baseline.update(spike);

        assertTrue(afterSpike.meanPm2_5() > steadyMean, "baseline should shift up");
        assertTrue(afterSpike.meanPm2_5() < 80.0, "one spike shouldn't fully overwrite the baseline");
    }
}

package io.streetsense.backend.session;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.StoredReading;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSummariserTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);
    private static final Instant START = Instant.parse("2026-08-03T07:00:00Z");

    private final SessionSummariser summariser = new SessionSummariser();

    /** {@code count} readings, one second apart, all at the same air quality. */
    private List<StoredReading> session(Activity activity, int count, double pm25) {
        return session(activity, count, i -> pm25);
    }

    private List<StoredReading> session(Activity activity, int count, java.util.function.IntToDoubleFunction pm25At) {
        List<StoredReading> readings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DecodedPacket packet = new DecodedPacket(
                    1, true, i, 10, pm25At.applyAsDouble(i), 15, 20, 120, 21.0, 50.0, 55.0);
            DecodedReading reading = new DecodedReading(
                    packet, CELL, 7, "session-1", "contributor-a", activity,
                    START.plus(Duration.ofSeconds(i)));
            readings.add(new StoredReading(
                    i + 1, reading, new Verdict.Normal(CellStats.of(List.of())), START));
        }
        return readings;
    }

    @Test
    void identicalAirCostsARunnerMoreThanAWalker() {
        // The distinctive number in the whole product. Concentration alone
        // says these two outings were the same; they were not, because
        // ventilation rate rises several-fold with exertion. A fitness watch
        // shows neither.
        SessionDebrief run = summariser.summarise(session(Activity.RUN, 60, 100.0));
        SessionDebrief walk = summariser.summarise(session(Activity.WALK, 60, 100.0));

        assertTrue(run.inhaledPm25Micrograms() > walk.inhaledPm25Micrograms(),
                "a run through the same air must cost more than a walk");
        assertEquals(3.0, run.inhaledPm25Micrograms() / walk.inhaledPm25Micrograms(), 0.01,
                "ratio should be the ventilation multipliers, 6x vs 2x");
    }

    @Test
    void doseAccumulatesOverTimeRatherThanAveraging() {
        SessionDebrief oneMinute = summariser.summarise(session(Activity.RUN, 60, 50.0));
        SessionDebrief twoMinutes = summariser.summarise(session(Activity.RUN, 120, 50.0));

        assertEquals(2.0, twoMinutes.inhaledPm25Micrograms() / oneMinute.inhaledPm25Micrograms(), 0.05,
                "twice as long in the same air is twice the dose");
    }

    @Test
    void theWorstStretchIsFoundAndLocatedInTime() {
        // "Your run was 34 ug/m3 on average" is not actionable. "The worst
        // 30 seconds was between 07:01:00 and 07:01:30" is — that's a junction
        // you can go around next time.
        SessionDebrief debrief = summariser.summarise(
                session(Activity.RUN, 120, i -> i >= 60 && i < 90 ? 200.0 : 10.0));

        assertTrue(debrief.worstSegment().meanPm2_5() > 100.0,
                "worst segment should have found the dirty stretch, got "
                        + debrief.worstSegment().meanPm2_5());
        assertTrue(!debrief.worstSegment().startedAt().isBefore(START.plusSeconds(55)),
                "worst segment should start around the 60s mark, got "
                        + debrief.worstSegment().startedAt());
    }

    @Test
    void anEmptySessionSummarisesToZeroRatherThanFailing() {
        // A session stopped before the node ever connected is a real thing
        // that happens, and it must not throw on the debrief screen.
        SessionDebrief debrief = summariser.summarise(List.of());

        assertEquals(0, debrief.readingCount());
        assertEquals(0.0, debrief.inhaledPm25Micrograms(), 0.0001);
    }
}

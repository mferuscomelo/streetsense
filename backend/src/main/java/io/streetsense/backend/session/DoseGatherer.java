package io.streetsense.backend.session;

import io.streetsense.backend.domain.DecodedReading;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Gatherer;

/**
 * Folds a session's readings into a running inhaled mass, in micrograms.
 *
 * <p>A stateful fold where each element's contribution depends on the gap to
 * the element before it is exactly what a {@link Gatherer} is for, and what
 * neither {@code map} nor {@code reduce} can express on its own — the same
 * reason {@code baseline/EwmaGatherer} exists.
 *
 * <p>The arithmetic:
 * <pre>
 *   µg inhaled  =  concentration (µg/m³)
 *                × resting ventilation (m³/h)
 *                × activity multiplier
 *                × time spent (h)
 * </pre>
 *
 * <p>Intervals are measured between consecutive capture timestamps rather than
 * assumed to be 1 Hz, so a session with dropped notifications reports the dose
 * for the time actually covered instead of silently under-counting. The gap is
 * clamped: a phone that slept for ten minutes mid-session should not attribute
 * ten minutes of breathing at the last-seen concentration.
 *
 * <p>These are population-level ventilation figures used to weight one outing
 * against another, not a clinical estimate of any individual's intake. See
 * {@code docs/honest-caveats.md}.
 */
public final class DoseGatherer {

    /** Resting minute ventilation of ~6 L/min, expressed as m³ per hour. */
    static final double RESTING_VENTILATION_M3_PER_HOUR = 0.36;

    /** Longest gap credited to a single reading, so a sleeping phone can't invent dose. */
    static final Duration MAX_CREDITED_GAP = Duration.ofSeconds(10);

    private static final double SECONDS_PER_HOUR = 3600.0;

    private DoseGatherer() {}

    /** Emits the running total after each reading; the last value is the session dose. */
    public static Gatherer<DecodedReading, ?, Double> micrograms() {
        return Gatherer.ofSequential(
                State::new,
                (State state, DecodedReading reading, Gatherer.Downstream<? super Double> downstream) -> {
                    Duration gap = state.previousAt == null
                            ? Duration.ZERO
                            : Duration.between(state.previousAt, reading.capturedAt());

                    // The first reading of a session has no predecessor to
                    // measure against, so it is credited with the nominal
                    // sampling interval rather than nothing — otherwise a
                    // one-reading session would report a dose of zero.
                    if (state.previousAt == null) {
                        gap = Duration.ofSeconds(1);
                    }
                    if (gap.isNegative()) {
                        gap = Duration.ZERO;
                    }
                    if (gap.compareTo(MAX_CREDITED_GAP) > 0) {
                        gap = MAX_CREDITED_GAP;
                    }

                    double hours = gap.toMillis() / 1000.0 / SECONDS_PER_HOUR;
                    state.total += reading.pm2_5()
                            * RESTING_VENTILATION_M3_PER_HOUR
                            * reading.activity().ventilationMultiplier()
                            * hours;
                    state.previousAt = reading.capturedAt();

                    return downstream.push(state.total);
                });
    }

    private static final class State {
        private double total;
        private Instant previousAt;
    }
}

package io.streetsense.backend.domain;

/**
 * What the contributor was doing while a session was recorded.
 *
 * <p>This is not decoration. Exposure is concentration × time × how hard you
 * are breathing, and ventilation rate rises several-fold with exertion — so
 * a run and a dog walk through identical air are genuinely different doses.
 * The multiplier below is what turns a concentration reading into a personal
 * number.
 *
 * <p>The multipliers are ratios of typical minute ventilation for each
 * activity against resting ventilation, rounded to one decimal place. They
 * are population-level figures used to weight one session against another,
 * not a clinical estimate of any individual's intake — see
 * {@code docs/honest-caveats.md}.
 */
public enum Activity {

    WALK(2.0),
    CYCLE(4.0),
    RUN(6.0);

    private final double ventilationMultiplier;

    Activity(double ventilationMultiplier) {
        this.ventilationMultiplier = ventilationMultiplier;
    }

    /** Breathing rate relative to rest, used to weight exposure into dose. */
    public double ventilationMultiplier() {
        return ventilationMultiplier;
    }
}

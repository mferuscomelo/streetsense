package io.streetsense.backend.baseline;

import io.streetsense.backend.domain.CellStats;

import java.util.stream.Gatherer;

/**
 * Folds a stream of windowed {@link CellStats} into a single running
 * exponentially-weighted moving average — this is the core of the
 * project's "AI" claim: each new window nudges the baseline rather than
 * replacing it outright, so a single noisy reading can't swing the
 * baseline used to judge the next one.
 */
public final class EwmaGatherer {

    private EwmaGatherer() {}

    public static Gatherer<CellStats, ?, CellStats> of(double alpha) {
        return Gatherer.ofSequential(
                () -> new CellStats[]{null}, // 1-element box: mutable running state
                (CellStats[] state, CellStats next, Gatherer.Downstream<? super CellStats> downstream) -> {
                    CellStats smoothed = state[0] == null ? next : blend(state[0], next, alpha);
                    state[0] = smoothed;
                    return downstream.push(smoothed);
                });
    }

    private static CellStats blend(CellStats prev, CellStats next, double alpha) {
        return new CellStats(
                next.sampleCount(),
                // Accumulated as a running maximum, never smoothed. Two
                // reasons. A contributor count is a count of people, and "2.4
                // contributors" is not a thing. And each window here is only 5
                // readings wide, so taking the latest window alone would report
                // whoever happens to appear in the last five readings — usually
                // one — for a cell a dozen people have sampled. The maximum
                // across every window is the count over the retained history,
                // which is what the confidence indicator has to mean.
                Math.max(prev.contributorCount(), next.contributorCount()),
                ewma(prev.meanPm2_5(), next.meanPm2_5(), alpha),
                ewma(prev.stdDevPm2_5(), next.stdDevPm2_5(), alpha),
                ewma(prev.meanVoc(), next.meanVoc(), alpha),
                ewma(prev.stdDevVoc(), next.stdDevVoc(), alpha),
                ewma(prev.meanNoiseDb(), next.meanNoiseDb(), alpha),
                ewma(prev.stdDevNoiseDb(), next.stdDevNoiseDb(), alpha));
    }

    private static double ewma(double prev, double next, double alpha) {
        return alpha * next + (1 - alpha) * prev;
    }
}

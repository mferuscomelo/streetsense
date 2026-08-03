package io.streetsense.backend.domain;

import java.util.List;

/**
 * Rolling per-pollutant statistics for one cell at one hour of day, produced
 * by the Gatherers-based windowing pipeline in baseline/RollingBaseline.java.
 *
 * <p>{@code sampleCount} and {@code contributorCount} are both here because
 * they answer different questions and the difference is the crowd layer's
 * whole claim to honesty. Four hundred readings from one person walking the
 * same street every morning is not the same evidence as four hundred readings
 * from twelve people, and a viewer has to be able to tell which they are
 * looking at. Every surface that shows a verdict shows the confidence behind
 * it.
 */
public record CellStats(
        int sampleCount,
        int contributorCount,
        double meanPm2_5, double stdDevPm2_5,
        double meanVoc, double stdDevVoc,
        double meanNoiseDb, double stdDevNoiseDb) {

    public static CellStats of(List<DecodedReading> window) {
        double meanPm = window.stream().mapToDouble(DecodedReading::pm2_5).average().orElse(0);
        double meanVoc = window.stream().mapToDouble(DecodedReading::vocIndex).average().orElse(0);
        double meanNoise = window.stream().mapToDouble(DecodedReading::noiseDb).average().orElse(0);

        double stdPm = stdDev(window.stream().mapToDouble(DecodedReading::pm2_5), meanPm);
        double stdVoc = stdDev(window.stream().mapToDouble(DecodedReading::vocIndex), meanVoc);
        double stdNoise = stdDev(window.stream().mapToDouble(DecodedReading::noiseDb), meanNoise);

        int contributors = (int) window.stream()
                .map(DecodedReading::contributorId)
                .distinct()
                .count();

        return new CellStats(window.size(), contributors,
                meanPm, stdPm, meanVoc, stdVoc, meanNoise, stdNoise);
    }

    private static double stdDev(java.util.stream.DoubleStream values, double mean) {
        double[] arr = values.toArray();
        if (arr.length < 2) return 0;
        double sumSq = 0;
        for (double v : arr) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (arr.length - 1));
    }
}

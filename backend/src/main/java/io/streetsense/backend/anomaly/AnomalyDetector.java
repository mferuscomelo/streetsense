package io.streetsense.backend.anomaly;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Pollutant;
import io.streetsense.backend.domain.Verdict;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Classifies a reading against its cell's baseline via z-score thresholds.
 * Whichever of PM2.5/VOC/noise has the largest |z| is reported as the
 * driving pollutant.
 */
@Component
public class AnomalyDetector {

    private static final double ELEVATED_Z = 2.0;
    private static final double SPIKE_Z = 4.0;
    private static final int MIN_SAMPLES_FOR_VERDICT = 3;
    private static final double MIN_STD_DEV = 0.5; // guards against divide-by-~0 on a thin baseline

    public Verdict check(DecodedReading reading, CellStats baseline) {
        double zPm = zScore(reading.pm2_5(), baseline.meanPm2_5(), baseline.stdDevPm2_5());
        double zVoc = zScore(reading.vocIndex(), baseline.meanVoc(), baseline.stdDevVoc());
        double zNoise = zScore(reading.noiseDb(), baseline.meanNoiseDb(), baseline.stdDevNoiseDb());

        double absPm = Math.abs(zPm), absVoc = Math.abs(zVoc), absNoise = Math.abs(zNoise);
        double maxAbsZ = Math.max(absPm, Math.max(absVoc, absNoise));

        Pollutant driver;
        double signedZ;
        if (maxAbsZ == absPm) {
            driver = Pollutant.PM2_5;
            signedZ = zPm;
        } else if (maxAbsZ == absVoc) {
            driver = Pollutant.VOC;
            signedZ = zVoc;
        } else {
            driver = Pollutant.NOISE;
            signedZ = zNoise;
        }

        if (baseline.sampleCount() < MIN_SAMPLES_FOR_VERDICT || maxAbsZ < ELEVATED_Z) {
            return new Verdict.Normal(baseline);
        }
        if (maxAbsZ < SPIKE_Z) {
            return new Verdict.Elevated(signedZ, driver, baseline);
        }
        return new Verdict.Spike(signedZ, driver, baseline, Instant.now());
    }

    private static double zScore(double value, double mean, double stdDev) {
        double safeStdDev = Math.max(stdDev, MIN_STD_DEV);
        return (value - mean) / safeStdDev;
    }
}

package io.streetsense.backend.anomaly;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Evidence;
import io.streetsense.backend.domain.Severity;
import io.streetsense.backend.domain.Verdict;
import org.springframework.stereotype.Component;

/**
 * Classifies a reading against its cell's baseline for that hour of day.
 *
 * <p>Two things are decided here, and keeping them apart is the point:
 * <b>what happened</b> (from which channels rose together) and <b>how far out
 * of the ordinary it was</b> (from how far they rose). The previous version
 * reported only the latter, having computed all three z-scores and then kept
 * whichever was largest — a magnitude with the cause thrown away.
 *
 * <p>Only <em>rises</em> count. Air cleaner than this block's normal is good
 * news, and flagging it as prominently as a smoke plume — which is what keying
 * off {@code |z|} did — is simply wrong.
 *
 * <p>This is a rule-based classifier over sensor cross-products. It is not
 * machine learning, and {@code docs/honest-caveats.md} says so plainly. The
 * trade is deliberate: every verdict carries the {@link Evidence} it was
 * decided from, so the app can explain itself in a sentence the user can check
 * against the numbers on screen.
 */
@Component
public class AnomalyDetector {

    private static final double ELEVATED_Z = 2.0;
    private static final double SPIKE_Z = 4.0;
    private static final int MIN_SAMPLES_FOR_VERDICT = 3;
    private static final double MIN_STD_DEV = 0.5; // guards against divide-by-~0 on a thin baseline

    public Verdict check(DecodedReading reading, CellStats baseline) {
        if (baseline.sampleCount() < MIN_SAMPLES_FOR_VERDICT) {
            return new Verdict.Normal(baseline);
        }

        double zPm = zScore(reading.pm2_5(), baseline.meanPm2_5(), baseline.stdDevPm2_5());
        double zVoc = zScore(reading.vocIndex(), baseline.meanVoc(), baseline.stdDevVoc());
        double zNoise = zScore(reading.noiseDb(), baseline.meanNoiseDb(), baseline.stdDevNoiseDb());

        boolean pmUp = zPm >= ELEVATED_Z;
        boolean vocUp = zVoc >= ELEVATED_Z;
        boolean noiseUp = zNoise >= ELEVATED_Z;

        if (!pmUp && !vocUp && !noiseUp) {
            return new Verdict.Normal(baseline);
        }

        Evidence evidence = new Evidence(zPm, zVoc, zNoise, severityOf(zPm, zVoc, zNoise));

        // Air first, then noise. Particulates and VOC describe what you are
        // breathing; noise rides along in the evidence either way and only
        // becomes the headline when the air itself is fine.
        if (pmUp && vocUp) {
            return new Verdict.SmokeOrExhaust(evidence, baseline);
        }
        if (pmUp) {
            return new Verdict.TrafficPlume(evidence, baseline);
        }
        if (vocUp) {
            return new Verdict.Solvent(evidence, baseline);
        }
        return new Verdict.LoudButClean(evidence, baseline);
    }

    private static Severity severityOf(double zPm, double zVoc, double zNoise) {
        double peak = Math.max(zPm, Math.max(zVoc, zNoise));
        return peak >= SPIKE_Z ? Severity.SPIKE : Severity.ELEVATED;
    }

    private static double zScore(double value, double mean, double stdDev) {
        double safeStdDev = Math.max(stdDev, MIN_STD_DEV);
        return (value - mean) / safeStdDev;
    }
}

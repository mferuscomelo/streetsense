package io.streetsense.backend.anomaly;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Severity;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AnomalyDetectorTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);
    private final AnomalyDetector detector = new AnomalyDetector();

    /** Calm baseline: PM 15±2, VOC 120±10, noise 55±3. */
    private static final CellStats BASELINE =
            new CellStats(10, 1, 15.0, 2.0, 120.0, 10.0, 55.0, 3.0);

    private DecodedReading reading(double pm25, double voc, double noiseDb) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, voc, 21.0, 50.0, noiseDb);
        return new DecodedReading(packet, CELL, 12, "test-session", "contributor-a", Activity.RUN, Instant.now());
    }

    @Test
    void thinBaselineAlwaysReadsNormal() {
        Verdict verdict = detector.check(reading(200.0, 120.0, 55.0), CellStats.of(List.of()));
        assertInstanceOf(Verdict.Normal.class, verdict);
    }

    @Test
    void closeToMeanIsNormal() {
        Verdict verdict = detector.check(reading(15.5, 122.0, 55.5), BASELINE);
        assertInstanceOf(Verdict.Normal.class, verdict);
    }

    // The four signatures. Which channels moved is the whole diagnosis — the
    // old detector computed all three z-scores and then threw two of them away
    // in favour of whichever was largest, which can only ever report a
    // magnitude, never a cause.

    @Test
    void particulatesAloneReadAsATrafficPlume() {
        // Dust, brake particulates, a diesel going past. No solvent vapour.
        Verdict verdict = detector.check(reading(30.0, 122.0, 55.0), BASELINE);
        assertInstanceOf(Verdict.TrafficPlume.class, verdict);
    }

    @Test
    void particulatesWithVocReadAsSmokeOrExhaust() {
        // Combustion puts out both. This is the pairing that separates a
        // barbecue or an exhaust plume from road dust.
        Verdict verdict = detector.check(reading(30.0, 300.0, 55.0), BASELINE);
        assertInstanceOf(Verdict.SmokeOrExhaust.class, verdict);
    }

    @Test
    void vocAloneReadsAsSolvent() {
        // Fumes with no particulate load: paint, cleaning products, fuel vapour.
        Verdict verdict = detector.check(reading(15.0, 300.0, 55.0), BASELINE);
        assertInstanceOf(Verdict.Solvent.class, verdict);
    }

    @Test
    void noiseAloneReadsAsLoudButClean() {
        // Worth telling a runner: unpleasant, but not something you're breathing.
        Verdict verdict = detector.check(reading(15.0, 122.0, 75.0), BASELINE);
        assertInstanceOf(Verdict.LoudButClean.class, verdict);
    }

    @Test
    void airBetterThanUsualIsNormalNotAnEvent() {
        // A large *negative* deviation is good news. The old detector keyed off
        // |z| and would have flagged unusually clean air as an anomaly with the
        // same prominence as a smoke plume.
        Verdict verdict = detector.check(reading(1.0, 40.0, 30.0), BASELINE);
        assertInstanceOf(Verdict.Normal.class, verdict);
    }

    @Test
    void severityRidesAlongsideTheDiagnosis() {
        // Cause and magnitude are different questions and the UI needs both:
        // "traffic plume, mildly elevated" and "traffic plume, way out of
        // range" call for different reactions.
        Verdict moderate = detector.check(reading(21.0, 122.0, 55.0), BASELINE); // z = 3.0
        Verdict extreme = detector.check(reading(30.0, 122.0, 55.0), BASELINE);  // z = 7.5

        assertEquals(Severity.ELEVATED, ((Verdict.TrafficPlume) moderate).evidence().severity());
        assertEquals(Severity.SPIKE, ((Verdict.TrafficPlume) extreme).evidence().severity());
    }

    @Test
    void everyEventCarriesTheZScoresItWasDecidedFrom() {
        // Explainability is the point: this is a rule-based classifier, not a
        // model, so it can always show its working. The UI says "VOC is 18x its
        // normal spread here, PM is untouched" rather than "anomaly detected".
        Verdict verdict = detector.check(reading(15.0, 300.0, 55.0), BASELINE);
        var evidence = ((Verdict.Solvent) verdict).evidence();

        assertEquals(0.0, evidence.zPm2_5(), 0.01);
        assertEquals(18.0, evidence.zVoc(), 0.01);
        assertEquals(0.0, evidence.zNoise(), 0.01);
    }
}

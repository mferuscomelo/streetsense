package io.streetsense.backend.anomaly;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AnomalyDetectorTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);
    private final AnomalyDetector detector = new AnomalyDetector();

    private DecodedReading reading(double pm25) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, pm25, 15, 20, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, 49.0069, 8.4037, Instant.now());
    }

    @Test
    void thinBaselineAlwaysReadsNormal() {
        CellStats emptyBaseline = CellStats.of(java.util.List.of());
        Verdict verdict = detector.check(reading(200.0), emptyBaseline);
        assertInstanceOf(Verdict.Normal.class, verdict);
    }

    @Test
    void closeToMeanIsNormal() {
        CellStats baseline = new CellStats(10, 15.0, 2.0, 120.0, 10.0, 55.0, 3.0);
        Verdict verdict = detector.check(reading(15.5), baseline);
        assertInstanceOf(Verdict.Normal.class, verdict);
    }

    @Test
    void moderateDeviationIsElevated() {
        CellStats baseline = new CellStats(10, 15.0, 2.0, 120.0, 10.0, 55.0, 3.0);
        Verdict verdict = detector.check(reading(21.0), baseline); // z = 3.0
        assertInstanceOf(Verdict.Elevated.class, verdict);
    }

    @Test
    void largeDeviationIsSpike() {
        CellStats baseline = new CellStats(10, 15.0, 2.0, 120.0, 10.0, 55.0, 3.0);
        Verdict verdict = detector.check(reading(30.0), baseline); // z = 7.5
        assertInstanceOf(Verdict.Spike.class, verdict);
    }
}

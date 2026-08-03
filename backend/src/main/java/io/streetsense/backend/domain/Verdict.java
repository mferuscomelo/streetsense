package io.streetsense.backend.domain;

import java.time.Instant;

/**
 * The anomaly verdict for a single reading against its cell's rolling
 * baseline. Sealed + records + exhaustive switch: adding a fourth verdict
 * later becomes a compile error at every site that must handle it — see
 * web/VerdictView.java for the exhaustive switch that serializes this to
 * the API response, and JEP 530's primitive-pattern switch (web/LenientJson)
 * for a related but distinct use of pattern matching.
 */
public sealed interface Verdict {

    record Normal(CellStats baseline) implements Verdict {}

    record Elevated(double zScore, Pollutant driver, CellStats baseline) implements Verdict {}

    record Spike(double zScore, Pollutant driver, CellStats baseline, Instant since) implements Verdict {}
}

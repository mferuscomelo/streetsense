package io.streetsense.backend.web;

import io.streetsense.backend.domain.Verdict;

/**
 * Exhaustive switch over the sealed {@link Verdict} hierarchy, with no
 * default branch — adding a fourth verdict type becomes a compile error
 * here, forcing this view to be updated rather than silently omitting it.
 */
public record VerdictView(String type, Double zScore, String driver, String since) {

    public static VerdictView of(Verdict verdict) {
        return switch (verdict) {
            case Verdict.Normal n -> new VerdictView("NORMAL", null, null, null);
            case Verdict.Elevated e -> new VerdictView("ELEVATED", e.zScore(), e.driver().name(), null);
            case Verdict.Spike s -> new VerdictView("SPIKE", s.zScore(), s.driver().name(), s.since().toString());
        };
    }
}

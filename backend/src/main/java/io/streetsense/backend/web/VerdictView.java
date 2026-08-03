package io.streetsense.backend.web;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.Evidence;
import io.streetsense.backend.domain.Verdict;

import java.util.Locale;

/**
 * Serializes a {@link Verdict} for the API, and turns it into a sentence a
 * person can read.
 *
 * <p>Exhaustive switches over the sealed hierarchy with no default branch —
 * this is the site that makes sealing worth having. Adding a sixth diagnosis
 * is a compile error right here, which is exactly where you want to be forced
 * to decide how it gets explained to a user, rather than discovering months
 * later that it has been rendering as a blank label.
 *
 * <p>{@code sampleCount} and {@code contributorCount} ride along on every
 * verdict on purpose: no surface shows a conclusion without showing how much
 * evidence stands behind it.
 */
public record VerdictView(
        String type,
        String headline,
        String explanation,
        String severity,
        Double zPm2_5,
        Double zVoc,
        Double zNoise,
        int sampleCount,
        int contributorCount) {

    public static VerdictView of(Verdict verdict) {
        Evidence e = verdict.evidenceOrNull();
        CellStats baseline = switch (verdict) {
            case Verdict.Normal v -> v.baseline();
            case Verdict.TrafficPlume v -> v.baseline();
            case Verdict.SmokeOrExhaust v -> v.baseline();
            case Verdict.Solvent v -> v.baseline();
            case Verdict.LoudButClean v -> v.baseline();
        };

        String type = switch (verdict) {
            case Verdict.Normal ignored -> "NORMAL";
            case Verdict.TrafficPlume ignored -> "TRAFFIC_PLUME";
            case Verdict.SmokeOrExhaust ignored -> "SMOKE_OR_EXHAUST";
            case Verdict.Solvent ignored -> "SOLVENT";
            case Verdict.LoudButClean ignored -> "LOUD_BUT_CLEAN";
        };

        String headline = switch (verdict) {
            case Verdict.Normal ignored -> "Normal for here";
            case Verdict.TrafficPlume ignored -> "Traffic or dust";
            case Verdict.SmokeOrExhaust ignored -> "Smoke or exhaust";
            case Verdict.Solvent ignored -> "Fumes";
            case Verdict.LoudButClean ignored -> "Loud, but clean air";
        };

        return new VerdictView(
                type, headline, explain(verdict, e),
                e == null ? null : e.severity().name(),
                e == null ? null : e.zPm2_5(),
                e == null ? null : e.zVoc(),
                e == null ? null : e.zNoise(),
                baseline.sampleCount(), baseline.contributorCount());
    }

    /**
     * The classifier's working, in one sentence. This is where rule-based
     * classification earns its keep over a model: the app can say exactly why
     * it decided what it decided, against numbers the user can check on screen.
     */
    private static String explain(Verdict verdict, Evidence e) {
        return switch (verdict) {
            case Verdict.Normal ignored ->
                    "Nothing unusual for this block at this hour.";
            case Verdict.TrafficPlume ignored -> fmt(
                    "Particulates are %.1f× the usual spread here, with no rise in fumes — "
                            + "that pattern is road or brake dust rather than combustion.", e.zPm2_5());
            case Verdict.SmokeOrExhaust ignored -> fmt(
                    "Particulates (%.1f×) and fumes (%.1f×) rose together, which is what "
                            + "burning something looks like.", e.zPm2_5(), e.zVoc());
            case Verdict.Solvent ignored -> fmt(
                    "Fumes are %.1f× the usual spread here with particulates untouched — "
                            + "solvent, paint or fuel vapour rather than smoke.", e.zVoc());
            case Verdict.LoudButClean ignored -> fmt(
                    "It is %.1f× louder than usual here, but the air itself is normal.", e.zNoise());
        };
    }

    private static String fmt(String template, Object... args) {
        return String.format(Locale.US, template, args);
    }
}

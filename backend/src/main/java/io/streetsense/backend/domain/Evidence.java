package io.streetsense.backend.domain;

/**
 * The z-scores an event was decided from, carried on the event itself.
 *
 * <p>This exists so the system can always show its working. StreetSense
 * classifies with explicit rules over sensor cross-products, not a trained
 * model — see {@code docs/honest-caveats.md} — and the upside of that choice
 * is that every verdict can be justified in a sentence a user can check:
 * "VOC is 18× its normal spread on this block at this hour, and particulates
 * are untouched." A model that merely emitted "anomaly" would be less useful
 * even if it were more sophisticated.
 *
 * <p>Each z is signed, and only positive deviations produce events — air that
 * is cleaner than usual is good news, not an anomaly.
 */
public record Evidence(
        double zPm2_5,
        double zVoc,
        double zNoise,
        Severity severity) {

    /** The strongest rise behind this event, for ranking one event against another. */
    public double peakZ() {
        return Math.max(zPm2_5, Math.max(zVoc, zNoise));
    }
}

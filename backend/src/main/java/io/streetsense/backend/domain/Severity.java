package io.streetsense.backend.domain;

/**
 * How far out of the ordinary an event is, kept separate from what the event
 * <em>is</em>. "Traffic plume, mildly elevated" and "traffic plume, way out of
 * range" call for different reactions, and collapsing cause and magnitude into
 * one scale — which is what the original {@code Normal | Elevated | Spike}
 * verdict did — loses the more useful half.
 */
public enum Severity {
    ELEVATED,
    SPIKE
}

package io.streetsense.backend.session;

import io.streetsense.backend.domain.Activity;

import java.time.Instant;
import java.util.List;

/**
 * What one outing actually cost you.
 *
 * <p>The headline is {@code inhaledPm25Micrograms} rather than a mean
 * concentration, because concentration is a property of a street and dose is a
 * property of <em>you</em>: the same air costs a runner several times what it
 * costs someone walking a dog through it, and no fitness watch reports that.
 *
 * <p>{@code worstSegment} is the other half. "Your run averaged 34 µg/m³" is
 * not actionable; "the worst 30 seconds was the stretch at 07:01" is a
 * junction you can go around next time.
 */
public record SessionDebrief(
        String sessionId,
        String contributorId,
        Activity activity,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        int readingCount,
        double inhaledPm25Micrograms,
        double meanPm2_5,
        double meanNoiseDb,
        Segment worstSegment,
        List<EventSummary> events) {

    /** The dirtiest stretch of the outing, and when you were in it. */
    public record Segment(Instant startedAt, Instant endedAt, double meanPm2_5) {
        public static Segment none(Instant at) {
            return new Segment(at, at, 0.0);
        }
    }

    /** One classified event along the route, in the words the app will show. */
    public record EventSummary(Instant at, String type, String headline, String explanation) {}

    public static SessionDebrief empty() {
        Instant now = Instant.EPOCH;
        return new SessionDebrief(null, null, null, now, now, 0, 0, 0.0, 0.0, 0.0,
                Segment.none(now), List.of());
    }
}

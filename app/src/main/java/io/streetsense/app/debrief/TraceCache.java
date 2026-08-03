package io.streetsense.app.debrief;

import io.streetsense.app.session.TracePoint;

import java.util.List;

/**
 * Hands a just-finished session's local trace from {@code MainActivity} to
 * {@link SessionDebriefActivity} without putting a potentially large point
 * list through an Intent extra (Binder transactions have a roughly 1MB
 * limit, and a multi-minute 1Hz session can approach it).
 *
 * <p>In-memory and single-slot: same process, read once immediately after
 * being set, cleared on read. This is what makes the debrief map available
 * right after a session ends but not for sessions opened later from
 * history — the backend has never held a coordinate to reconstruct one
 * from, so a past session's map genuinely isn't recoverable. The history
 * screen says so rather than silently showing nothing.
 */
public final class TraceCache {

    private static volatile List<TracePoint> pending;

    private TraceCache() {}

    public static void set(List<TracePoint> trace) {
        pending = trace;
    }

    /** Returns and clears the pending trace, or null if none is waiting. */
    public static List<TracePoint> takeIfAvailable() {
        List<TracePoint> trace = pending;
        pending = null;
        return trace;
    }
}

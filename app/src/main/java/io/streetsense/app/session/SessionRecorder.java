package io.streetsense.app.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Holds the one session currently being recorded.
 *
 * <p>Two distinct things happen per reading, and keeping them separate is the
 * point of this class:
 *
 * <ul>
 *   <li>the <b>cell</b> goes to the backend, to be pooled with everyone
 *       else's contributions;</li>
 *   <li>the <b>precise trace</b> stays here, in memory on the device, purely
 *       so the debrief can draw the route you actually took.</li>
 * </ul>
 *
 * <p>The trace is deliberately not uploaded and deliberately not persisted
 * beyond the app's lifetime yet — a killed process loses the map but never
 * the contribution, which is the right way round.
 */
public final class SessionRecorder {

    private String sessionId;
    private Activity activity;
    private long startedAtMillis;
    private final List<TracePoint> trace = new ArrayList<>();

    /** Begins a new session, discarding any previous trace. Returns its id. */
    public synchronized String start(Activity activity) {
        this.sessionId = UUID.randomUUID().toString();
        this.activity = activity;
        this.startedAtMillis = System.currentTimeMillis();
        this.trace.clear();
        return sessionId;
    }

    public synchronized void stop() {
        sessionId = null;
    }

    public synchronized boolean isRecording() {
        return sessionId != null;
    }

    public synchronized String sessionId() {
        return sessionId;
    }

    public synchronized Activity activity() {
        return activity;
    }

    public synchronized long startedAtMillis() {
        return startedAtMillis;
    }

    public synchronized void record(TracePoint point) {
        if (sessionId != null) {
            trace.add(point);
        }
    }

    /** A snapshot of the route so far, safe to hand to the map. */
    public synchronized List<TracePoint> trace() {
        return Collections.unmodifiableList(new ArrayList<>(trace));
    }
}

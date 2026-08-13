package io.streetsense.app.session;

/**
 * What the contributor is doing during a session.
 *
 * <p>Mirrors {@code backend/.../domain/Activity.java} — the name is sent on
 * the wire and parsed back into that enum, so the constant names must match.
 * The ventilation multipliers that turn a concentration into a dose live on
 * the backend side only; the phone just reports what you told it you were
 * doing.
 */
public enum Activity {
    WALK,
    CYCLE,
    RUN,
    DRIVING
}

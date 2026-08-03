package io.streetsense.app.session;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * A stable identifier for this install, used to tell one contributor's
 * readings from another's in the crowd layer.
 *
 * <p><b>Deliberately a random UUID, not a device identifier.</b> Not
 * ANDROID_ID, not an advertising ID, not anything derived from hardware.
 * The crowd layer needs exactly one thing — the ability to count distinct
 * contributors, so it can say "twelve people sampled this block" rather than
 * "four hundred readings, possibly all from one person". Any identifier that
 * survives a reinstall, or that correlates with an identity elsewhere, would
 * buy nothing extra and would turn a set of anonymous cell contributions back
 * into something attributable.
 *
 * <p>Clearing app data yields a new id, and that is fine: it costs a little
 * continuity in contributor counts and nothing else.
 */
public final class ContributorId {

    private static final String PREFS = "streetsense";
    private static final String KEY = "contributorId";

    private ContributorId() {}

    public static String get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY, null);
        if (existing != null) {
            return existing;
        }
        String fresh = UUID.randomUUID().toString();
        prefs.edit().putString(KEY, fresh).apply();
        return fresh;
    }
}

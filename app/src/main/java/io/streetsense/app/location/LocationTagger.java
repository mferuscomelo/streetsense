package io.streetsense.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Keeps a continuously-refreshed cache of the device's location.
 *
 * Deliberately <em>not</em> a one-shot {@code getCurrentLocation} per reading:
 * packets arrive at 1 Hz, and a fix takes seconds to minutes, so a per-packet
 * request would cancel its own predecessor forever and never produce a
 * location at all. Continuous updates are also simply the right API for
 * continuous sensing.
 *
 * Uses {@link LocationManager#FUSED_PROVIDER} (API 31+, matching our minSdk)
 * rather than raw GPS: fused blends WiFi/cell positioning, so it fixes indoors
 * where {@code GPS_PROVIDER} alone frequently never will.
 */
public final class LocationTagger {

    private static final long MIN_INTERVAL_MS = 10_000L;
    private static final float MIN_DISTANCE_M = 10f;

    private final LocationManager locationManager;
    private volatile Location lastKnown;
    private boolean listening = false;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            lastKnown = location;
        }
    };

    public LocationTagger(Context context) {
        this.locationManager = context.getSystemService(LocationManager.class);
    }

    /** Seeds the cache from the last known fix, then subscribes to updates. */
    @SuppressLint("MissingPermission") // caller verifies ACCESS_FINE_LOCATION before calling
    public void start() {
        if (listening || locationManager == null) return;

        // Seed immediately so the very first packet can upload with a real
        // position instead of waiting out a cold fix.
        Location seed = lastKnownFrom(LocationManager.FUSED_PROVIDER);
        if (seed == null) {
            seed = lastKnownFrom(LocationManager.GPS_PROVIDER);
        }
        if (seed != null) {
            lastKnown = seed;
        }

        locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                MIN_INTERVAL_MS,
                MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper());
        listening = true;
    }

    public void stop() {
        if (!listening || locationManager == null) return;
        locationManager.removeUpdates(listener);
        listening = false;
    }

    /** The most recent fix, or null if none has been obtained yet. */
    @Nullable
    public Location current() {
        return lastKnown;
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private Location lastKnownFrom(String provider) {
        try {
            return locationManager.getLastKnownLocation(provider);
        } catch (IllegalArgumentException e) {
            return null; // provider not present on this device
        }
    }
}

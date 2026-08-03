package io.streetsense.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Fetches a one-shot GPS fix via the platform LocationManager — no Play
 * Services dependency, since this app has no other reason to pull it in.
 */
public final class LocationTagger {

    private final LocationManager locationManager;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private CancellationSignal pending;

    public LocationTagger(Context context) {
        this.locationManager = context.getSystemService(LocationManager.class);
    }

    @SuppressLint("MissingPermission") // caller verifies ACCESS_FINE_LOCATION before calling
    public void requestLocation(Consumer<Location> onLocation) {
        if (pending != null) {
            pending.cancel();
        }
        pending = new CancellationSignal();
        locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                pending,
                executor,
                onLocation::accept);
    }

    public void cancel() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }
}

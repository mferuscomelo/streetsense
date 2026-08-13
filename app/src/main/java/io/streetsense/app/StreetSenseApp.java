package io.streetsense.app;

import android.app.Application;

import io.streetsense.app.session.SessionController;

/**
 * Holds the single {@link SessionController} instance for the app's process
 * lifetime, so the BLE connection and in-progress session survive navigating
 * between the Activity-select / Connect / Session / Summary screens.
 *
 * <p>Point BACKEND_BASE_URL at your backend's LAN IP before testing
 * end-to-end (see root README's Quickstart). Must also be listed in
 * res/xml/network_security_config.xml, or uploads fail with
 * CLEARTEXT_NOT_PERMITTED. For a USB-tethered test, run
 * {@code adb reverse tcp:8080 tcp:8080} and use "http://localhost:8080"
 * instead — also already allowed by that config.
 */
public final class StreetSenseApp extends Application {

    private static final String BACKEND_BASE_URL = "http://localhost:8080";

    private SessionController sessionController;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionController = new SessionController(this, BACKEND_BASE_URL);
    }

    public SessionController sessionController() {
        return sessionController;
    }
}

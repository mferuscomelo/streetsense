package io.streetsense.app.session;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import io.streetsense.app.ble.BleScanner;
import io.streetsense.app.ble.SensorNodeClient;
import io.streetsense.app.ble.SensorPacket;
import io.streetsense.app.location.LocationTagger;
import io.streetsense.app.upload.ReadingUploader;

/**
 * The single observable session state shared by Connect/Session/Summary —
 * see docs/handoff/android-notes.md's SessionState interface. Owns the BLE
 * scan/connect lifecycle, the running session's trace buffer, and the
 * derived connection state screens render from. One instance lives for the
 * app's process lifetime, held by {@link StreetSenseApp}, so the BLE link
 * survives navigating between activities.
 *
 * <p>The three BLE failure reasons the design spec calls for (not found /
 * timeout / sensor) don't exist as distinct signals from the platform BLE
 * stack — Android just tells you "found a device" or "disconnected", with
 * no reason code. The timeouts below are what turn that into the three
 * reasons: no scan result in {@link #SEARCH_TIMEOUT_MS} means not found, no
 * GATT-connected callback in {@link #CONNECT_TIMEOUT_MS} means timeout, and
 * no first packet within {@link #WARMUP_TIMEOUT_MS} of connecting means the
 * sensor isn't reporting.
 */
public final class SessionController {

    public enum ConnectionState { IDLE, SEARCHING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    public enum ErrorReason { NOT_FOUND, TIMEOUT, SENSOR }

    public interface Listener {
        void onSessionStateChanged();
    }

    private static final long SEARCH_TIMEOUT_MS = 15_000L;
    private static final long CONNECT_TIMEOUT_MS = 12_000L;
    private static final long WARMUP_TIMEOUT_MS = 20_000L;
    private static final int TRACE_CAPACITY = 3600; // ~1h at 1 sample/s, see data-model.md

    private final Context appContext;
    private final BleScanner scanner;
    private final SensorNodeClient client;
    private final LocationTagger locationTagger;
    private final ReadingUploader uploader;
    private final String contributorId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private ConnectionState connectionState = ConnectionState.IDLE;
    private ErrorReason errorReason;
    private boolean reconnecting;
    private boolean warmingUp;
    private BluetoothDevice device;
    private boolean connectingGuard;

    private Activity activity;
    private boolean running;
    private boolean paused;
    private String sessionId;
    private long startedAtMillis;
    private long endedAtMillis;
    private long pausedAccumulatedMillis;
    private long pauseStartedAtMillis;
    private final Deque<TracePoint> trace = new ArrayDeque<>();
    private TracePoint latest;

    private boolean batteryValid;
    private boolean charging;
    private double batterySoc;

    private final Runnable searchTimeoutRunnable = () -> fail(ErrorReason.NOT_FOUND);
    private final Runnable connectTimeoutRunnable = () -> fail(ErrorReason.TIMEOUT);
    private final Runnable warmupTimeoutRunnable = () -> fail(ErrorReason.SENSOR);

    public SessionController(Context context, String backendBaseUrl) {
        this.appContext = context.getApplicationContext();
        this.locationTagger = new LocationTagger(appContext);
        this.uploader = new ReadingUploader(backendBaseUrl);
        this.contributorId = ContributorId.get(appContext);
        this.scanner = new BleScanner(appContext, this::onNodeFound);
        this.client = new SensorNodeClient(new SensorNodeClient.Listener() {
            @Override
            public void onStateChanged(SensorNodeClient.State state) {
                onBleStateChanged(state);
            }

            @Override
            public void onPacketReceived(byte[] rawPacket) {
                SessionController.this.onPacketReceived(rawPacket);
            }
        });
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            l.onSessionStateChanged();
        }
    }

    // --- reads ---

    public ConnectionState connectionState() { return connectionState; }
    public ErrorReason errorReason() { return errorReason; }
    public boolean isReconnecting() { return reconnecting; }
    public boolean isWarmingUp() { return warmingUp; }
    public BluetoothDevice device() { return device; }
    public Activity activity() { return activity; }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public TracePoint latest() { return latest; }
    public List<TracePoint> trace() { return List.copyOf(trace); }
    public String sessionId() { return sessionId; }
    public boolean isBatteryValid() { return batteryValid; }
    public boolean isCharging() { return charging; }
    public double batterySoc() { return batterySoc; }

    /**
     * Elapsed session time, paused time excluded. Freezes at whatever it was
     * when {@link #endSession()} was called, rather than continuing to grow
     * while a finished session's Summary screen stays on screen.
     */
    public long elapsedMillis() {
        if (startedAtMillis == 0) return 0;
        long now = running ? System.currentTimeMillis() : endedAtMillis;
        long pausedTotal = pausedAccumulatedMillis + (paused ? now - pauseStartedAtMillis : 0);
        return Math.max(0, now - startedAtMillis - pausedTotal);
    }

    // --- actions — see docs/handoff/android-notes.md's SessionState actions ---

    public void selectActivity(Activity activity) {
        this.activity = activity;
        notifyListeners();
    }

    public void startScan() {
        if (connectionState == ConnectionState.SEARCHING
                || connectionState == ConnectionState.CONNECTING
                || connectionState == ConnectionState.CONNECTED) {
            return;
        }
        errorReason = null;
        connectingGuard = false;
        connectionState = ConnectionState.SEARCHING;
        mainHandler.postDelayed(searchTimeoutRunnable, SEARCH_TIMEOUT_MS);
        scanner.start();
        notifyListeners();
    }

    public void retry() {
        reconnecting = connectionState == ConnectionState.DISCONNECTED && device != null;
        startScan();
    }

    public void disconnect() {
        cancelTimeouts();
        scanner.stop();
        client.disconnect();
        device = null;
        connectingGuard = false;
        warmingUp = false;
        reconnecting = false;
        connectionState = ConnectionState.IDLE;
        notifyListeners();
    }

    private void onNodeFound(BluetoothDevice found) {
        // Arrives on a Binder thread and fires repeatedly for the same node.
        mainHandler.post(() -> {
            if (connectingGuard || connectionState != ConnectionState.SEARCHING) return;
            connectingGuard = true;
            mainHandler.removeCallbacks(searchTimeoutRunnable);
            scanner.stop();
            device = found;
            connectionState = ConnectionState.CONNECTING;
            mainHandler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
            client.connect(appContext, found);
            notifyListeners();
        });
    }

    private void onBleStateChanged(SensorNodeClient.State state) {
        if (state == SensorNodeClient.State.CONNECTED) {
            mainHandler.removeCallbacks(connectTimeoutRunnable);
            connectionState = ConnectionState.CONNECTED;
            warmingUp = true;
            reconnecting = false;
            mainHandler.postDelayed(warmupTimeoutRunnable, WARMUP_TIMEOUT_MS);
            locationTagger.start();
            notifyListeners();
        } else if (state == SensorNodeClient.State.DISCONNECTED) {
            boolean wasConnected = connectionState == ConnectionState.CONNECTED;
            cancelTimeouts();
            connectingGuard = false;
            warmingUp = false;
            if (wasConnected || reconnecting) {
                connectionState = ConnectionState.DISCONNECTED;
            } else {
                connectionState = ConnectionState.ERROR;
                errorReason = ErrorReason.TIMEOUT;
            }
            notifyListeners();
        }
    }

    private void fail(ErrorReason reason) {
        cancelTimeouts();
        scanner.stop();
        client.disconnect();
        connectingGuard = false;
        warmingUp = false;
        connectionState = ConnectionState.ERROR;
        errorReason = reason;
        notifyListeners();
    }

    private void cancelTimeouts() {
        mainHandler.removeCallbacks(searchTimeoutRunnable);
        mainHandler.removeCallbacks(connectTimeoutRunnable);
        mainHandler.removeCallbacks(warmupTimeoutRunnable);
    }

    private void onPacketReceived(byte[] rawPacket) {
        SensorPacket packet = SensorPacket.parse(rawPacket);
        if (warmingUp) {
            mainHandler.removeCallbacks(warmupTimeoutRunnable);
            warmingUp = false;
        }
        batteryValid = packet.batteryValid();
        charging = packet.charging();
        batterySoc = packet.batterySoc();

        Location location = locationTagger.current();
        if (location != null) {
            TracePoint point = new TracePoint(
                    location.getLatitude(), location.getLongitude(),
                    packet.pm1(), packet.pm2_5(), packet.pm4(), packet.pm10(),
                    packet.noiseDb(), packet.vocIndex(), packet.tempC(), packet.humidity(),
                    System.currentTimeMillis());
            latest = point;
            if (running && !paused) {
                trace.addLast(point);
                while (trace.size() > TRACE_CAPACITY) {
                    trace.removeFirst();
                }
            }
        }

        if (running && !paused && sessionId != null) {
            uploader.upload(rawPacket, location, sessionId, contributorId, activity.name());
        }
        notifyListeners();
    }

    public void startSession() {
        sessionId = UUID.randomUUID().toString();
        startedAtMillis = System.currentTimeMillis();
        pausedAccumulatedMillis = 0;
        running = true;
        paused = false;
        trace.clear();
        latest = null;
        notifyListeners();
    }

    public void pause() {
        if (!running || paused) return;
        paused = true;
        pauseStartedAtMillis = System.currentTimeMillis();
        notifyListeners();
    }

    public void resume() {
        if (!running || !paused) return;
        paused = false;
        pausedAccumulatedMillis += System.currentTimeMillis() - pauseStartedAtMillis;
        notifyListeners();
    }

    public void endSession() {
        long now = System.currentTimeMillis();
        if (paused) {
            pausedAccumulatedMillis += now - pauseStartedAtMillis;
        }
        endedAtMillis = now;
        running = false;
        paused = false;
        notifyListeners();
    }

    /** Back to a fresh Activity-select state. Leaves an existing BLE connection intact. */
    public void reset() {
        endSession();
        sessionId = null;
        activity = null;
        trace.clear();
        latest = null;
        notifyListeners();
    }

    public void shutdown() {
        cancelTimeouts();
        locationTagger.stop();
        scanner.stop();
        client.disconnect();
    }
}

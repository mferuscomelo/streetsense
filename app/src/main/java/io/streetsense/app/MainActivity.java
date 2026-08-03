package io.streetsense.app;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Map;

import io.streetsense.app.ble.BleScanner;
import io.streetsense.app.ble.SensorNodeClient;
import io.streetsense.app.ble.SensorPacket;
import io.streetsense.app.location.LocationTagger;
import io.streetsense.app.session.Activity;
import io.streetsense.app.session.ContributorId;
import io.streetsense.app.session.SessionRecorder;
import io.streetsense.app.session.TracePoint;
import io.streetsense.app.upload.ReadingUploader;

/**
 * Single-screen app: Start/Stop, connection state, live decoded values, and
 * a required MOCK badge whenever the connected node's data is synthetic —
 * the same honesty mechanism as the firmware's FLAG_MOCK_DATA bit.
 *
 * Point BACKEND_BASE_URL at your backend's LAN IP before testing end-to-end
 * (see root README's Quickstart).
 */
public final class MainActivity extends AppCompatActivity {

    // Must also be listed in res/xml/network_security_config.xml, or uploads
    // fail with CLEARTEXT_NOT_PERMITTED. Change both together.
    // For a USB-tethered test, run `adb reverse tcp:8080 tcp:8080` and use
    // "http://localhost:8080" instead — also already allowed by that config.
    private static final String BACKEND_BASE_URL = "http://localhost:8080";

    private TextView statusText;
    private TextView mockBadge;
    private TextView readingsText;
    private Button startStopButton;

    private BleScanner scanner;
    private SensorNodeClient client;
    private LocationTagger locationTagger;
    private ReadingUploader uploader;
    private final SessionRecorder session = new SessionRecorder();
    private String contributorId;

    private boolean running = false;
    /** Guards against the repeated scan results that would otherwise open several GATT connections. */
    private boolean connecting = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    (Map<String, Boolean> results) -> {
                        boolean ble = Boolean.TRUE.equals(results.get(Manifest.permission.BLUETOOTH_SCAN))
                                && Boolean.TRUE.equals(results.get(Manifest.permission.BLUETOOTH_CONNECT));
                        // Precise location specifically: "Approximate" grants only
                        // COARSE, which at ~1km accuracy can't place a reading in a
                        // ~1.1km grid cell — so it isn't good enough here.
                        boolean fineLocation =
                                Boolean.TRUE.equals(results.get(Manifest.permission.ACCESS_FINE_LOCATION));

                        if (ble && fineLocation) {
                            startScanning();
                        } else if (ble) {
                            statusText.setText(R.string.status_needs_precise_location);
                        } else {
                            statusText.setText(R.string.status_permissions_denied);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        mockBadge = findViewById(R.id.mockBadge);
        readingsText = findViewById(R.id.readingsText);
        startStopButton = findViewById(R.id.startStopButton);

        locationTagger = new LocationTagger(this);
        uploader = new ReadingUploader(BACKEND_BASE_URL);
        contributorId = ContributorId.get(this);

        client = new SensorNodeClient(new SensorNodeClient.Listener() {
            @Override
            public void onStateChanged(SensorNodeClient.State state) {
                if (state == SensorNodeClient.State.DISCONNECTED) {
                    // Release the guard so a dropped link can be picked up
                    // again by the next scan instead of wedging until Stop.
                    connecting = false;
                    if (running) {
                        scanner.start();
                    }
                }
                statusText.setText(switch (state) {
                    case CONNECTING -> getString(R.string.status_connecting);
                    case CONNECTED -> getString(R.string.status_connected);
                    case DISCONNECTED -> getString(R.string.status_disconnected);
                });
            }

            @Override
            public void onPacketReceived(byte[] rawPacket) {
                handlePacket(rawPacket);
            }
        });

        scanner = new BleScanner(this, this::onNodeFound);

        startStopButton.setOnClickListener(v -> {
            if (running) {
                stop();
            } else {
                start();
            }
        });
    }

    private void start() {
        running = true;
        // Step 6 replaces this with a Run/Cycle/Walk picker. Until then WALK
        // is the honest default: it is the lowest ventilation multiplier, so
        // an unchosen activity under-reports dose rather than inflating it.
        session.start(Activity.WALK);
        startStopButton.setText(R.string.action_stop);
        permissionLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void startScanning() {
        statusText.setText(R.string.status_scanning);
        locationTagger.start();
        scanner.start();
    }

    private void stop() {
        running = false;
        connecting = false;
        startStopButton.setText(R.string.action_start);
        scanner.stop();
        client.disconnect();
        locationTagger.stop();
        session.stop();
        statusText.setText(R.string.status_idle);
    }

    private void onNodeFound(BluetoothDevice device) {
        // Arrives on a Binder thread, and fires repeatedly for the same node —
        // hop to main and let only the first result through, or we open (and
        // leak) a GATT connection per scan result.
        runOnUiThread(() -> {
            if (connecting || !running) return;
            connecting = true;
            scanner.stop();
            client.connect(this, device);
        });
    }

    private void handlePacket(byte[] rawPacket) {
        SensorPacket packet = SensorPacket.parse(rawPacket);
        mockBadge.setVisibility(packet.mock() ? android.view.View.VISIBLE : android.view.View.GONE);

        readingsText.setText(String.format(Locale.US,
                "seq %d%nPM2.5   %.1f ug/m3%nVOC     %.1f%ntemp    %.2f C%nhumidity %.2f %%RH%nnoise   %.1f dB(A)",
                packet.seq(), packet.pm2_5(), packet.vocIndex(), packet.tempC(),
                packet.humidity(), packet.noiseDb()));

        // Upload with the most recent cached fix rather than starting a fresh
        // location request per packet. With no fix yet, say so rather than
        // uploading a bogus 0,0 — a silent no-op here would look identical to
        // a working upload path.
        Location location = locationTagger.current();
        if (location == null) {
            statusText.setText(R.string.status_waiting_for_gps);
            return;
        }
        statusText.setText(R.string.status_connected);

        // The precise fix goes to the local trace, for the session map only.
        // The uploader snaps to a cell and sends that instead — see
        // ReadingUploader for why the split lives on this side of the network.
        session.record(new TracePoint(
                location.getLatitude(), location.getLongitude(),
                packet.pm2_5(), packet.noiseDb(), System.currentTimeMillis()));

        uploader.upload(rawPacket, location,
                session.sessionId(), contributorId, session.activity().name());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationTagger.stop();
        client.disconnect();
        scanner.stop();
    }
}

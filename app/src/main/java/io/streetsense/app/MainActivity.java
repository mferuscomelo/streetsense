package io.streetsense.app;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

import io.streetsense.app.ble.BleScanner;
import io.streetsense.app.ble.SensorNodeClient;
import io.streetsense.app.ble.SensorPacket;
import io.streetsense.app.location.LocationTagger;
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

    private static final String BACKEND_BASE_URL = "http://192.168.1.100:8080";

    private TextView statusText;
    private TextView mockBadge;
    private TextView readingsText;
    private Button startStopButton;

    private BleScanner scanner;
    private SensorNodeClient client;
    private LocationTagger locationTagger;
    private ReadingUploader uploader;

    private boolean running = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    (Map<String, Boolean> results) -> {
                        boolean granted = results.values().stream().allMatch(Boolean::booleanValue);
                        if (granted) {
                            startScanning();
                        } else {
                            statusText.setText("Permissions denied");
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

        client = new SensorNodeClient(new SensorNodeClient.Listener() {
            @Override
            public void onStateChanged(SensorNodeClient.State state) {
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
        startStopButton.setText(R.string.action_stop);
        permissionLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
        });
    }

    private void startScanning() {
        statusText.setText(R.string.status_scanning);
        scanner.start();
    }

    private void stop() {
        running = false;
        startStopButton.setText(R.string.action_start);
        scanner.stop();
        client.disconnect();
        statusText.setText(R.string.status_idle);
    }

    private void onNodeFound(BluetoothDevice device) {
        scanner.stop();
        client.connect(this, device);
    }

    private void handlePacket(byte[] rawPacket) {
        SensorPacket packet = SensorPacket.parse(rawPacket);
        mockBadge.setVisibility(packet.mock() ? android.view.View.VISIBLE : android.view.View.GONE);

        readingsText.setText(String.format(
                "seq %d%nPM2.5   %.1f ug/m3%nVOC     %.1f%ntemp    %.2f C%nhumidity %.2f %%RH%nnoise   %.1f dB(A)",
                packet.seq(), packet.pm2_5(), packet.vocIndex(), packet.tempC(),
                packet.humidity(), packet.noiseDb()));

        locationTagger.requestLocation(location -> uploader.upload(rawPacket, location));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationTagger.cancel();
        client.disconnect();
        scanner.stop();
    }
}

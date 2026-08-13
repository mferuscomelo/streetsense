package io.streetsense.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.Collections;
import java.util.UUID;

/**
 * Scans for StreetSense sensor nodes, filtered on the custom service UUID
 * so we never see unrelated BLE peripherals. See docs/ble-protocol.md for
 * the UUID values — they must match firmware/src/ble_uuids.h exactly.
 */
// ConnectActivity gates every entry point here behind a granted BLUETOOTH_SCAN /
// BLUETOOTH_CONNECT request, so the permission checks lint asks for would be
// unreachable duplicates. (Revoking a permission at runtime restarts the
// process, so there's no live window where these calls run unpermitted.)
@SuppressLint("MissingPermission")
public final class BleScanner {

    public static final UUID SERVICE_UUID =
            UUID.fromString("ee8ebcc5-07f5-4bce-96c2-ccafc2a91f7c");

    /** Callback for a discovered StreetSense node. */
    public interface Listener {
        void onNodeFound(BluetoothDevice device);
    }

    private final BluetoothLeScanner scanner;
    private final Listener listener;
    private boolean scanning = false;

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            listener.onNodeFound(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
        }
    };

    public BleScanner(Context context, Listener listener) {
        BluetoothAdapter adapter =
                context.getSystemService(android.bluetooth.BluetoothManager.class).getAdapter();
        this.scanner = adapter.getBluetoothLeScanner();
        this.listener = listener;
    }

    public void start() {
        if (scanning || scanner == null) return;
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        scanning = true;
    }

    public void stop() {
        if (!scanning || scanner == null) return;
        scanner.stopScan(scanCallback);
        scanning = false;
    }
}

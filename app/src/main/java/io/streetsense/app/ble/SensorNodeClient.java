package io.streetsense.app.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

/**
 * Connects to a single StreetSense sensor node, enables notifications on
 * the sensor characteristic, and hands raw packets back to the caller on
 * the main thread. GATT callbacks arrive on a Binder thread, so every
 * callback here hops to main before touching the listener.
 */
// See BleScanner: all entry points are gated behind granted BLE permissions
// in MainActivity, so lint's requested checks would be unreachable duplicates.
@SuppressLint("MissingPermission")
public final class SensorNodeClient {

    public static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("b70d5d1b-481e-4d17-8f6d-6b91b22d6b60");

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // v2 packets are 26 bytes + 3-byte ATT header = 29; 64 leaves headroom
    // for whatever the wire format grows into next without another
    // renegotiation. See firmware/src/main.cpp's matching BANDWIDTH_MAX.
    private static final int REQUESTED_MTU = 64;

    public enum State { CONNECTING, CONNECTED, DISCONNECTED }

    public interface Listener {
        void onStateChanged(State state);
        void onPacketReceived(byte[] rawPacket);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private BluetoothGatt gatt;

    public SensorNodeClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(Context context, BluetoothDevice device) {
        notifyState(State.CONNECTING);
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    public void disconnect() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
    }

    private void notifyState(State state) {
        mainHandler.post(() -> listener.onStateChanged(state));
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Discovery must not race the MTU exchange, so it's deferred
                // to onMtuChanged rather than fired here directly.
                g.requestMtu(REQUESTED_MTU);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyState(State.DISCONNECTED);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            // Proceed even if the peer rejected the request (status != OK) —
            // the connection still works at the default MTU, just truncating
            // any packet wider than that; better than never discovering the
            // service at all.
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            // Discovery can fail, or report a peer without our service — in
            // either case getService returns null, so this must not be chained
            // blindly or the app crashes instead of reporting a failed connect.
            BluetoothGattService service = g.getService(BleScanner.SERVICE_UUID);
            if (service == null) {
                notifyState(State.DISCONNECTED);
                return;
            }

            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) {
                notifyState(State.DISCONNECTED);
                return;
            }

            g.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor cccd =
                    characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
            if (cccd != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(cccd);
                }
            }
            notifyState(State.CONNECTED);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            byte[] raw = characteristic.getValue();
            mainHandler.post(() -> listener.onPacketReceived(raw));
        }
    };
}

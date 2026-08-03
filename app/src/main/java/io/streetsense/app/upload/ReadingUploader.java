package io.streetsense.app.upload;

import android.location.Location;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads a raw sensor packet verbatim, plus GPS and capture time, to the
 * backend. The backend — not this app — is the sole authority on decoding
 * the packet; see ble/SensorPacket.java for why that split exists.
 *
 * No offline queue: a failed upload is dropped. That's an explicit scope
 * cut for slice 1 — see docs/future-work.md.
 */
public final class ReadingUploader {

    private static final String TAG = "ReadingUploader";

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ReadingUploader(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void upload(byte[] rawPacket, Location location) {
        executor.execute(() -> uploadBlocking(rawPacket, location));
    }

    private void uploadBlocking(byte[] rawPacket, Location location) {
        String body = toJson(rawPacket, location);
        try {
            URL url = new URL(baseUrl + "/api/v1/readings");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (status >= 400) {
                    Log.w(TAG, "upload failed, HTTP " + status);
                }
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            Log.w(TAG, "upload failed", e);
        }
    }

    private static String toJson(byte[] rawPacket, Location location) {
        String rawPacketB64 = Base64.encodeToString(rawPacket, Base64.NO_WRAP);
        double lat = location != null ? location.getLatitude() : 0.0;
        double lon = location != null ? location.getLongitude() : 0.0;
        String capturedAt = Instant.now().toString();

        return "{"
                + "\"rawPacket\":\"" + rawPacketB64 + "\","
                + "\"lat\":" + lat + ","
                + "\"lon\":" + lon + ","
                + "\"capturedAt\":\"" + capturedAt + "\""
                + "}";
    }
}

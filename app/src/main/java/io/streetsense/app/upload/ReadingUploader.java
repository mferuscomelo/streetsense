package io.streetsense.app.upload;

import android.location.Location;
import android.util.Base64;
import android.util.Log;

import io.streetsense.app.location.GridCell;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads a raw sensor packet verbatim, plus the cell it was taken in and the
 * session it belongs to. The backend — not this app — is the sole authority on
 * decoding the packet; see ble/SensorPacket.java for why that split exists.
 *
 * <p><b>The precise GPS fix never leaves this class.</b> It is snapped to a
 * {@link GridCell} here, on the device, and only the bucket pair is sent. The
 * backend therefore has no coordinate to store, log, or leak, and the session
 * map is drawn from the phone's own local trace instead. This method is the
 * one place that boundary is enforced — if a lat/lon ever reappears in the
 * JSON below, the privacy model is broken.
 *
 * <p>{@code hourOfDay} is the phone's <em>local</em> hour, deliberately not
 * derived server-side from the capture instant: the backend groups readings by
 * hour to answer "when is this block quietest", and a UTC hour would smear
 * rush hour across timezones as soon as there is more than one contributor
 * city.
 *
 * <p>No offline queue: a failed upload is dropped. That's an explicit scope
 * cut — see docs/future-work.md.
 */
public final class ReadingUploader {

    private static final String TAG = "ReadingUploader";

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ReadingUploader(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * @param location the raw fix; snapped to a cell here and never sent as-is.
     *                 A null location means no fix yet — the reading is dropped
     *                 rather than uploaded against a fabricated (0,0) cell,
     *                 which would poison a real grid cell off the Gulf of Guinea.
     */
    public void upload(byte[] rawPacket, Location location, String sessionId,
                       String contributorId, String activity) {
        if (location == null) {
            Log.d(TAG, "no fix yet — dropping reading rather than inventing a cell");
            return;
        }
        GridCell cell = GridCell.of(location.getLatitude(), location.getLongitude());
        int hourOfDay = LocalTime.now().getHour();
        executor.execute(() -> uploadBlocking(rawPacket, cell, hourOfDay, sessionId, contributorId, activity));
    }

    private void uploadBlocking(byte[] rawPacket, GridCell cell, int hourOfDay,
                                String sessionId, String contributorId, String activity) {
        String body = toJson(rawPacket, cell, hourOfDay, sessionId, contributorId, activity);
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

    private static String toJson(byte[] rawPacket, GridCell cell, int hourOfDay,
                                 String sessionId, String contributorId, String activity) {
        String rawPacketB64 = Base64.encodeToString(rawPacket, Base64.NO_WRAP);
        String capturedAt = Instant.now().toString();

        return "{"
                + "\"rawPacket\":\"" + rawPacketB64 + "\","
                + "\"latBucket\":" + cell.latBucket() + ","
                + "\"lonBucket\":" + cell.lonBucket() + ","
                + "\"hourOfDay\":" + hourOfDay + ","
                + "\"sessionId\":\"" + sessionId + "\","
                + "\"contributorId\":\"" + contributorId + "\","
                + "\"activity\":\"" + activity + "\","
                + "\"capturedAt\":\"" + capturedAt + "\""
                + "}";
    }
}

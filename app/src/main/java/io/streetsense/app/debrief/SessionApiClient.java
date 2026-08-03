package io.streetsense.app.debrief;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches session debriefs from the backend's {@code /api/v1/sessions}
 * endpoints. Plain {@link HttpURLConnection} and {@code org.json}, matching
 * {@code upload.ReadingUploader} rather than pulling in an HTTP client
 * library for two GET requests.
 *
 * <p>Every call runs on a background thread and delivers its result on the
 * caller's choice of callback — Android forbids network I/O on the main
 * thread, and this app has no other place a background executor already
 * lives for HTTP work.
 */
public final class SessionApiClient {

    private static final String TAG = "SessionApiClient";

    public interface Callback<T> {
        void onResult(T result);
        void onError(Exception e);
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SessionApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void fetchDebrief(String sessionId, Callback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                JSONObject result = getJson("/api/v1/sessions/" + sessionId);
                callback.onResult(result);
            } catch (Exception e) {
                Log.w(TAG, "fetchDebrief failed", e);
                callback.onError(e);
            }
        });
    }

    public void fetchRecentSessions(int limit, Callback<List<JSONObject>> callback) {
        executor.execute(() -> {
            try {
                JSONArray array = new JSONArray(getBody("/api/v1/sessions?limit=" + limit));
                List<JSONObject> result = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    result.add(array.getJSONObject(i));
                }
                callback.onResult(result);
            } catch (Exception e) {
                Log.w(TAG, "fetchRecentSessions failed", e);
                callback.onError(e);
            }
        });
    }

    private JSONObject getJson(String path) throws IOException, JSONException {
        return new JSONObject(getBody(path));
    }

    private String getBody(String path) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                throw new IOException("HTTP " + status + " with no body from " + path);
            }
            String body = readAll(stream);
            if (status >= 400) {
                throw new IOException("HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

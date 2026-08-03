package io.streetsense.app.debrief;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.List;
import java.util.Locale;

import io.streetsense.app.R;
import io.streetsense.app.session.TracePoint;

/**
 * What one outing cost you: dose, the worst stretch, the classified events
 * along the way, and — only for the session that just ended — a map of the
 * actual route, drawn from the phone's own local trace.
 *
 * <p>The backend has never held a coordinate (see
 * {@code backend/.../domain/DecodedReading}), so this screen is the only
 * place that route ever exists. {@link TraceCache} hands it over once, in
 * memory, from {@code MainActivity}; a debrief opened later from history has
 * no trace to show and says so, rather than rendering an empty map that
 * looks like a bug.
 */
public final class SessionDebriefActivity extends AppCompatActivity {

    private static final String EXTRA_SESSION_ID = "sessionId";
    private static final String EXTRA_BACKEND_BASE_URL = "backendBaseUrl";

    /** WHO-adjacent traffic-light bands for a quick visual read, not a certified AQI scale. */
    private static final double PM_BAND_MODERATE = 15.0;
    private static final double PM_BAND_HIGH = 35.0;

    public static Intent intent(Context context, String sessionId, String backendBaseUrl) {
        Intent intent = new Intent(context, SessionDebriefActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_BACKEND_BASE_URL, backendBaseUrl);
        return intent;
    }

    private TextView activityLabel;
    private TextView doseText;
    private TextView summaryText;
    private MapView map;
    private TextView mapUnavailableText;
    private TextView noEventsText;
    private LinearLayout eventsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().load(getApplicationContext(),
                getPreferences(Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_debrief);

        activityLabel = findViewById(R.id.activityLabel);
        doseText = findViewById(R.id.doseText);
        summaryText = findViewById(R.id.summaryText);
        map = findViewById(R.id.map);
        mapUnavailableText = findViewById(R.id.mapUnavailableText);
        noEventsText = findViewById(R.id.noEventsText);
        eventsList = findViewById(R.id.eventsList);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        String backendBaseUrl = getIntent().getStringExtra(EXTRA_BACKEND_BASE_URL);

        renderTraceIfAvailable();

        new SessionApiClient(backendBaseUrl).fetchDebrief(sessionId, new SessionApiClient.Callback<>() {
            @Override
            public void onResult(JSONObject debrief) {
                runOnUiThread(() -> renderDebrief(debrief));
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(SessionDebriefActivity.this,
                        R.string.debrief_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderTraceIfAvailable() {
        List<TracePoint> trace = TraceCache.takeIfAvailable();
        if (trace == null || trace.isEmpty()) {
            map.setVisibility(View.GONE);
            mapUnavailableText.setVisibility(View.VISIBLE);
            return;
        }

        // One short polyline segment per consecutive pair, coloured by that
        // segment's air quality, rather than one polyline for the whole
        // route — osmdroid colours a Polyline as a single unit, and the
        // point of this map is showing *where* it got bad.
        for (int i = 1; i < trace.size(); i++) {
            TracePoint a = trace.get(i - 1);
            TracePoint b = trace.get(i);

            Polyline segment = new Polyline();
            segment.setPoints(List.of(
                    new GeoPoint(a.lat(), a.lon()),
                    new GeoPoint(b.lat(), b.lon())));
            segment.getOutlinePaint().setColor(colorFor(b.pm2_5()));
            segment.getOutlinePaint().setStrokeWidth(10f);
            map.getOverlays().add(segment);
        }

        TracePoint last = trace.get(trace.size() - 1);
        map.getController().setZoom(17.0);
        map.getController().setCenter(new GeoPoint(last.lat(), last.lon()));
    }

    private static int colorFor(double pm25) {
        if (pm25 < PM_BAND_MODERATE) return Color.rgb(46, 160, 67);
        if (pm25 < PM_BAND_HIGH) return Color.rgb(230, 159, 0);
        return Color.rgb(213, 48, 48);
    }

    private void renderDebrief(JSONObject debrief) {
        String activityName = debrief.optString("activity", "SESSION");
        int durationSeconds = debrief.optInt("durationSeconds", 0);
        activityLabel.setText(getString(R.string.debrief_title,
                capitalize(activityName), formatDuration(durationSeconds)));

        double doseMicrograms = debrief.optDouble("inhaledPm25Micrograms", 0);
        doseText.setText(getString(R.string.debrief_dose, doseMicrograms));

        double meanPm = debrief.optDouble("meanPm2_5", 0);
        double meanNoise = debrief.optDouble("meanNoiseDb", 0);
        summaryText.setText(getString(R.string.debrief_summary, meanPm, meanNoise));

        JSONArray events = debrief.optJSONArray("events");
        eventsList.removeAllViews();
        if (events == null || events.length() == 0) {
            noEventsText.setVisibility(View.VISIBLE);
        } else {
            noEventsText.setVisibility(View.GONE);
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) continue;
                eventsList.addView(eventRow(event));
            }
        }
    }

    private View eventRow(JSONObject event) {
        TextView view = new TextView(this);
        view.setPadding(0, 12, 0, 12);
        view.setText(getString(R.string.debrief_event_row,
                event.optString("headline", ""), event.optString("explanation", "")));
        return view;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.US);
    }

    private static String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}

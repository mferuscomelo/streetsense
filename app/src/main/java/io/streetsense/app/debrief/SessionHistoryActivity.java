package io.streetsense.app.debrief;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.streetsense.app.R;

/**
 * Past sessions, newest first. Opening one shows dose and events but never a
 * map — the local trace behind a past session was never persisted past that
 * session's own lifetime, on purpose (see {@link TraceCache}), so there is
 * nothing to draw a route from once you've navigated away.
 */
public final class SessionHistoryActivity extends AppCompatActivity {

    private static final String EXTRA_BACKEND_BASE_URL = "backendBaseUrl";

    public static Intent intent(Context context, String backendBaseUrl) {
        Intent intent = new Intent(context, SessionHistoryActivity.class);
        intent.putExtra(EXTRA_BACKEND_BASE_URL, backendBaseUrl);
        return intent;
    }

    private String backendBaseUrl;
    private final List<JSONObject> sessions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_history);

        backendBaseUrl = getIntent().getStringExtra(EXTRA_BACKEND_BASE_URL);

        TextView emptyText = findViewById(R.id.emptyText);
        ListView listView = findViewById(R.id.sessionList);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String sessionId = sessions.get(position).optString("sessionId", null);
            if (sessionId != null) {
                startActivity(SessionDebriefActivity.intent(this, sessionId, backendBaseUrl));
            }
        });

        new SessionApiClient(backendBaseUrl).fetchRecentSessions(20, new SessionApiClient.Callback<>() {
            @Override
            public void onResult(List<JSONObject> result) {
                runOnUiThread(() -> {
                    sessions.clear();
                    sessions.addAll(result);
                    adapter.clear();
                    for (JSONObject s : result) {
                        adapter.add(rowText(s));
                    }
                    emptyText.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(SessionHistoryActivity.this,
                        R.string.debrief_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private static String rowText(JSONObject session) {
        String activity = session.optString("activity", "SESSION");
        double dose = session.optDouble("inhaledPm25Micrograms", 0);
        int seconds = session.optInt("durationSeconds", 0);
        return String.format(Locale.US, "%s — %d:%02d — %.2f µg inhaled",
                activity, seconds / 60, seconds % 60, dose);
    }
}

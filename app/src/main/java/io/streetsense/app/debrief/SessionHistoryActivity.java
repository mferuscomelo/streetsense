package io.streetsense.app.debrief;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_history);

        backendBaseUrl = getIntent().getStringExtra(EXTRA_BACKEND_BASE_URL);

        TextView emptyText = findViewById(R.id.emptyText);
        RecyclerView recyclerView = findViewById(R.id.sessionList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        SessionRowAdapter adapter = new SessionRowAdapter(session -> {
            String sessionId = session.optString("sessionId", null);
            if (sessionId != null) {
                startActivity(SessionDebriefActivity.intent(this, sessionId, backendBaseUrl));
            }
        });
        recyclerView.setAdapter(adapter);

        new SessionApiClient(backendBaseUrl).fetchRecentSessions(20, new SessionApiClient.Callback<>() {
            @Override
            public void onResult(List<JSONObject> result) {
                runOnUiThread(() -> {
                    adapter.submitList(result);
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
}

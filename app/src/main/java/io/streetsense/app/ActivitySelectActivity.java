package io.streetsense.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import io.streetsense.app.session.Activity;
import io.streetsense.app.session.SessionController;

/**
 * Screen 1 — "How are you travelling today?" Single-select 2x2 activity
 * grid; the choice threads through to {@link SessionController} and drives
 * the backend's ventilation multiplier. See docs/handoff/screens.md #1.
 */
public final class ActivitySelectActivity extends AppCompatActivity {

    private record Tile(MaterialCardView card, TextView label, TextView blurb, Activity activity) {}

    private Tile[] tiles;
    private Activity selected;
    private com.google.android.material.button.MaterialButton continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_select);

        View topBar = findViewById(R.id.topBar);
        ((TextView) topBar.findViewById(R.id.topBarTitle)).setText(R.string.activity_select_title);
        topBar.findViewById(R.id.batteryPill).setVisibility(View.GONE);

        tiles = new Tile[]{
                new Tile(findViewById(R.id.tileWalking), findViewById(R.id.labelWalking),
                        findViewById(R.id.blurbWalking), Activity.WALK),
                new Tile(findViewById(R.id.tileJogging), findViewById(R.id.labelJogging),
                        findViewById(R.id.blurbJogging), Activity.RUN),
                new Tile(findViewById(R.id.tileCycling), findViewById(R.id.labelCycling),
                        findViewById(R.id.blurbCycling), Activity.CYCLE),
                new Tile(findViewById(R.id.tileDriving), findViewById(R.id.labelDriving),
                        findViewById(R.id.blurbDriving), Activity.DRIVING),
        };
        for (Tile tile : tiles) {
            tile.card.setOnClickListener(v -> select(tile.activity));
        }

        continueButton = findViewById(R.id.continueButton);
        continueButton.setOnClickListener(v -> {
            if (selected == null) return;
            controller().selectActivity(selected);
            startActivity(new Intent(this, ConnectActivity.class));
        });

        renderSelection();
    }

    private SessionController controller() {
        return ((StreetSenseApp) getApplication()).sessionController();
    }

    private void select(Activity activity) {
        selected = activity;
        renderSelection();
    }

    private void renderSelection() {
        for (Tile tile : tiles) {
            boolean isSelected = tile.activity == selected;
            if (isSelected) {
                tile.card.setStrokeWidth(0);
                tile.card.setCardBackgroundColor(getColor(R.color.color_primary));
                tile.label.setTextColor(getColor(R.color.color_primary_foreground));
                tile.blurb.setTextColor(getColor(R.color.color_primary_foreground));
                tile.blurb.setAlpha(0.7f);
            } else {
                tile.card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
                tile.card.setCardBackgroundColor(getColor(R.color.color_card));
                tile.label.setTextColor(getColor(R.color.color_foreground));
                tile.blurb.setTextColor(getColor(R.color.color_muted_foreground));
                tile.blurb.setAlpha(1f);
            }
        }
        continueButton.setEnabled(selected != null);
        continueButton.setAlpha(selected != null ? 1f : 0.35f);
    }
}

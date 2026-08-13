package io.streetsense.app;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

import java.util.List;
import java.util.Locale;

import io.streetsense.app.metrics.Band;
import io.streetsense.app.metrics.Metric;
import io.streetsense.app.session.SessionController;
import io.streetsense.app.session.TracePoint;
import io.streetsense.app.ui.RouteMapRenderer;
import io.streetsense.app.ui.TopBar;

/**
 * Screen 4 — "what one outing looked like." All numbers are computed
 * locally from the samples the app already collected during the session
 * (not re-fetched from the backend) — readings were already uploaded live
 * for the shared dataset; this on-device view just summarises what the
 * phone itself saw. See docs/handoff/screens.md #5.
 */
public final class SummaryActivity extends AppCompatActivity {

    private MapView summaryMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().load(getApplicationContext(), getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        io.streetsense.app.ui.EdgeInsets.apply(findViewById(R.id.root),
                findViewById(R.id.scrollContent), findViewById(R.id.bottomBar));

        View topBar = findViewById(R.id.topBar);
        ((TextView) topBar.findViewById(R.id.topBarTitle)).setText(R.string.summary_title);
        TopBar.bindBatteryPill(topBar.findViewById(R.id.batteryPill), topBar.findViewById(R.id.batteryIcon),
                topBar.findViewById(R.id.batteryText), controller());

        summaryMap = findViewById(R.id.summaryMap);
        summaryMap.setTileSource(TileSourceFactory.MAPNIK);
        summaryMap.setMultiTouchControls(false);

        findViewById(R.id.doneButton).setOnClickListener(v -> {
            controller().reset();
            Intent intent = new Intent(this, ActivitySelectActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        render();
    }

    private SessionController controller() {
        return ((StreetSenseApp) getApplication()).sessionController();
    }

    @Override
    protected void onResume() {
        super.onResume();
        summaryMap.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        summaryMap.onPause();
    }

    private void render() {
        List<TracePoint> trace = controller().trace();

        String activityLabel = controller().activity() == null ? "" : controller().activity().name();
        ((TextView) findViewById(R.id.summaryHeading))
                .setText(getString(R.string.summary_session_heading_format, activityLabel));

        long elapsed = controller().elapsedMillis();
        ((TextView) findViewById(R.id.summaryDuration)).setText(formatDuration(elapsed));

        double distanceKm = distanceKm(trace);
        ((TextView) findViewById(R.id.summaryDistanceSamples)).setText(getString(
                R.string.summary_distance_samples_format,
                String.format(Locale.US, "%.2f", distanceKm), trace.size()));

        double avgPm25 = average(trace, Metric.PM25);
        Band overallBand = Metric.PM25.bandOf(avgPm25);
        TextView bandText = findViewById(R.id.summaryBand);
        TextView avgPm25Text = findViewById(R.id.summaryAvgPm25);
        if (overallBand != null) {
            bandText.setText(overallBand.label.toUpperCase(Locale.US));
            bandText.setTextColor(getColor(overallBand.textColorRes));
            avgPm25Text.setTextColor(getColor(overallBand.textColorRes));
        }
        avgPm25Text.setText(Metric.PM25.format(avgPm25));

        RouteMapRenderer.render(summaryMap, trace, Metric.PM25);

        LinearLayout container = findViewById(R.id.metricListContainer);
        container.removeAllViews();
        Metric[] metrics = Metric.SUMMARY_ORDER;
        for (int i = 0; i < metrics.length; i++) {
            container.addView(metricRow(container, metrics[i], trace));
            if (i < metrics.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
                divider.setBackgroundColor(getColor(R.color.color_border));
                container.addView(divider);
            }
        }
    }

    private View metricRow(LinearLayout parent, Metric metric, List<TracePoint> trace) {
        View row = LayoutInflater.from(this).inflate(R.layout.view_summary_metric_row, parent, false);
        ((TextView) row.findViewById(R.id.rowName)).setText(metric.label);

        double avg = average(trace, metric);
        double peak = peak(trace, metric);
        ((TextView) row.findViewById(R.id.rowAvgValue)).setText(metric.format(avg));
        ((TextView) row.findViewById(R.id.rowAvgUnit)).setText(metric.unit);
        ((TextView) row.findViewById(R.id.rowPeak)).setText(getString(R.string.summary_peak_format, metric.format(peak)));

        Band band = metric.bandOf(avg);
        int dotColor = getColor(band != null ? band.graphicColorRes : R.color.color_muted_foreground);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(dotColor);
        row.findViewById(R.id.rowBandDot).setBackground(dot);

        row.setOnClickListener(v ->
                MetricSheetFragment.newInstance(metric).show(getSupportFragmentManager(), "metric_sheet"));
        return row;
    }

    private static double average(List<TracePoint> trace, Metric metric) {
        if (trace.isEmpty()) return 0;
        double sum = 0;
        for (TracePoint p : trace) sum += metric.valueOf(p);
        return sum / trace.size();
    }

    private static double peak(List<TracePoint> trace, Metric metric) {
        double max = 0;
        for (TracePoint p : trace) max = Math.max(max, metric.valueOf(p));
        return max;
    }

    private static double distanceKm(List<TracePoint> trace) {
        if (trace.size() < 2) return 0;
        double totalMeters = 0;
        float[] results = new float[1];
        for (int i = 1; i < trace.size(); i++) {
            TracePoint a = trace.get(i - 1);
            TracePoint b = trace.get(i);
            Location.distanceBetween(a.lat(), a.lon(), b.lat(), b.lon(), results);
            totalMeters += results[0];
        }
        return totalMeters / 1000.0;
    }

    private static String formatDuration(long elapsedMillis) {
        long totalSeconds = elapsedMillis / 1000;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}

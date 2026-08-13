package io.streetsense.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.streetsense.app.metrics.Band;
import io.streetsense.app.metrics.Metric;
import io.streetsense.app.session.SessionController;
import io.streetsense.app.session.TracePoint;
import io.streetsense.app.ui.RouteMapRenderer;
import io.streetsense.app.ui.SparklineView;
import io.streetsense.app.ui.TopBar;

/**
 * Screen 3 — live readings while a session is recording. See
 * docs/handoff/screens.md #3. Route guard: if the session isn't running and
 * the module isn't connected, this screen bounces back to Connect.
 */
public final class SessionActivity extends AppCompatActivity implements SessionController.Listener {

    private static final int SPARKLINE_POINTS = 40;

    private record CompactCard(View root, TextView value, SparklineView sparkline, TextView band, Metric metric) {}

    private TextView timerText;
    private View runStatusDot;
    private TextView runStatusLabel;

    private MaterialCardView bannerCard;
    private ImageView bannerIcon;
    private ProgressBar bannerSpinner;
    private TextView bannerTitle;
    private TextView bannerDetail;
    private TextView bannerAction;

    private LinearLayout readingsColumn;
    private View readingsSkeleton;

    private View heroCard;
    private TextView heroBand;
    private TextView heroValue;
    private TextView heroUnit;
    private SparklineView heroSparkline;

    private View noiseCard;
    private TextView noiseValue;
    private TextView noiseUnit;
    private SparklineView noiseSparkline;
    private TextView noiseBand;

    private View vocCard;
    private TextView vocValue;
    private SparklineView vocSparkline;
    private TextView vocBand;

    private CompactCard[] compactCards;

    private MaterialCardView routeMapCard;
    private MapView routeMap;
    private TextView mapPillPm25;
    private TextView mapPillPm10;
    private TextView mapPillNoise;
    private Metric mapColorBy = Metric.PM25;

    private MaterialButton pauseResumeButton;
    private MaterialButton finishButton;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderTimer();
            tickHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().load(getApplicationContext(), getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);

        io.streetsense.app.ui.EdgeInsets.apply(findViewById(R.id.root),
                findViewById(R.id.scrollContent), findViewById(R.id.bottomBar));

        View topBar = findViewById(R.id.topBar);
        ((TextView) topBar.findViewById(R.id.topBarTitle)).setText(R.string.session_title);

        timerText = findViewById(R.id.timerText);
        runStatusDot = findViewById(R.id.runStatusDot);
        runStatusLabel = findViewById(R.id.runStatusLabel);

        bannerCard = findViewById(R.id.bannerCard);
        bannerIcon = findViewById(R.id.bannerIcon);
        bannerSpinner = findViewById(R.id.bannerSpinner);
        bannerTitle = findViewById(R.id.bannerTitle);
        bannerDetail = findViewById(R.id.bannerDetail);
        bannerAction = findViewById(R.id.bannerAction);
        bannerAction.setOnClickListener(v -> controller().retry());

        readingsColumn = findViewById(R.id.readingsColumn);
        readingsSkeleton = findViewById(R.id.readingsSkeleton);

        heroCard = findViewById(R.id.heroCard);
        heroBand = findViewById(R.id.heroBand);
        heroValue = findViewById(R.id.heroValue);
        heroUnit = findViewById(R.id.heroUnit);
        heroSparkline = findViewById(R.id.heroSparkline);
        heroCard.setOnClickListener(v -> openMetricSheet(Metric.PM25));

        noiseCard = findViewById(R.id.noiseCard);
        noiseValue = findViewById(R.id.noiseValue);
        noiseUnit = findViewById(R.id.noiseUnit);
        noiseSparkline = findViewById(R.id.noiseSparkline);
        noiseBand = findViewById(R.id.noiseBand);
        noiseCard.setOnClickListener(v -> openMetricSheet(Metric.NOISE));

        vocCard = findViewById(R.id.vocCard);
        vocValue = findViewById(R.id.vocValue);
        vocSparkline = findViewById(R.id.vocSparkline);
        vocBand = findViewById(R.id.vocBand);
        vocCard.setOnClickListener(v -> openMetricSheet(Metric.VOC));

        compactCards = new CompactCard[]{
                bindCompactCard(R.id.pm1Card, Metric.PM1),
                bindCompactCard(R.id.pm4Card, Metric.PM4),
                bindCompactCard(R.id.pm10Card, Metric.PM10),
                bindCompactCard(R.id.tempCard, Metric.TEMP),
                bindCompactCard(R.id.humidityCard, Metric.HUMIDITY),
        };

        routeMapCard = findViewById(R.id.routeMapCard);
        routeMap = findViewById(R.id.routeMap);
        routeMap.setTileSource(TileSourceFactory.MAPNIK);
        routeMap.setMultiTouchControls(true);
        routeMapCard.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int width = routeMapCard.getWidth();
            int targetHeight = width * 10 / 16;
            if (width > 0 && routeMapCard.getLayoutParams().height != targetHeight) {
                routeMapCard.getLayoutParams().height = targetHeight;
                routeMapCard.requestLayout();
            }
        });

        mapPillPm25 = findViewById(R.id.mapPillPm25);
        mapPillPm10 = findViewById(R.id.mapPillPm10);
        mapPillNoise = findViewById(R.id.mapPillNoise);
        mapPillPm25.setOnClickListener(v -> setMapColorBy(Metric.PM25));
        mapPillPm10.setOnClickListener(v -> setMapColorBy(Metric.PM10));
        mapPillNoise.setOnClickListener(v -> setMapColorBy(Metric.NOISE));

        pauseResumeButton = findViewById(R.id.pauseResumeButton);
        finishButton = findViewById(R.id.finishButton);
        pauseResumeButton.setOnClickListener(v -> {
            if (controller().isPaused()) controller().resume(); else controller().pause();
            render();
        });
        finishButton.setOnClickListener(v -> {
            controller().endSession();
            startActivity(new Intent(this, SummaryActivity.class));
            finish();
        });
    }

    private CompactCard bindCompactCard(int includeId, Metric metric) {
        View root = findViewById(includeId);
        TextView label = root.findViewById(R.id.compactLabel);
        label.setText(metric.shortLabel.toUpperCase(Locale.US));
        TextView value = root.findViewById(R.id.compactValue);
        SparklineView sparkline = root.findViewById(R.id.compactSparkline);
        TextView band = root.findViewById(R.id.compactBand);
        root.setOnClickListener(v -> openMetricSheet(metric));
        return new CompactCard(root, value, sparkline, band, metric);
    }

    private SessionController controller() {
        return ((StreetSenseApp) getApplication()).sessionController();
    }

    @Override
    protected void onStart() {
        super.onStart();
        routeMap.onResume();
        controller().addListener(this);
        if (!controller().isRunning()
                && controller().connectionState() != SessionController.ConnectionState.CONNECTED) {
            startActivity(new Intent(this, ConnectActivity.class));
            finish();
            return;
        }
        tickHandler.post(tick);
        render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        routeMap.onPause();
        controller().removeListener(this);
        tickHandler.removeCallbacks(tick);
    }

    @Override
    public void onSessionStateChanged() {
        render();
    }

    private void setMapColorBy(Metric metric) {
        mapColorBy = metric;
        render();
    }

    private void openMetricSheet(Metric metric) {
        MetricSheetFragment.newInstance(metric).show(getSupportFragmentManager(), "metric_sheet");
    }

    // --- rendering ---

    private void render() {
        TopBar.bindBatteryPill(
                findViewById(R.id.topBar).findViewById(R.id.batteryPill),
                findViewById(R.id.topBar).findViewById(R.id.batteryIcon),
                findViewById(R.id.topBar).findViewById(R.id.batteryText),
                controller());

        renderTimer();
        renderBanner();

        List<TracePoint> trace = controller().trace();
        boolean hasSamples = !trace.isEmpty();
        readingsSkeleton.setVisibility(hasSamples ? View.GONE : View.VISIBLE);
        readingsColumn.setVisibility(hasSamples ? View.VISIBLE : View.GONE);

        boolean connected = controller().connectionState() == SessionController.ConnectionState.CONNECTED;
        readingsColumn.setAlpha(connected ? 1f : 0.55f);

        if (hasSamples) {
            bindMetric(heroValue, heroBand, heroSparkline, Metric.PM25, trace);
            heroUnit.setText(Metric.PM25.unit);

            bindMetric(noiseValue, noiseBand, noiseSparkline, Metric.NOISE, trace);
            noiseUnit.setText(Metric.NOISE.unit);
            bindMetric(vocValue, vocBand, vocSparkline, Metric.VOC, trace);

            for (CompactCard card : compactCards) {
                bindMetric(card.value, card.band, card.sparkline, card.metric, trace);
            }
        }

        RouteMapRenderer.render(routeMap, trace, mapColorBy);
        renderMapPills();

        boolean running = controller().isRunning();
        boolean paused = controller().isPaused();
        pauseResumeButton.setEnabled(running);
        pauseResumeButton.setAlpha(running ? 1f : 0.35f);
        pauseResumeButton.setText(paused ? R.string.action_resume : R.string.action_pause);
        pauseResumeButton.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
    }

    private void renderTimer() {
        long elapsed = controller().elapsedMillis();
        long totalSeconds = elapsed / 1000;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        timerText.setText(h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s));

        boolean running = controller().isRunning() && !controller().isPaused();
        setDotColor(runStatusDot, running
                ? getColor(R.color.band_good_graphic) : getColor(R.color.color_muted_foreground));
        runStatusLabel.setText(running
                ? (controller().activity() == null ? "" : controller().activity().name())
                : getString(R.string.status_paused));
    }

    private static void setDotColor(View dot, int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(color);
        dot.setBackground(drawable);
    }

    private void renderBanner() {
        SessionController c = controller();
        boolean reconnecting = c.isReconnecting()
                && (c.connectionState() == SessionController.ConnectionState.SEARCHING
                || c.connectionState() == SessionController.ConnectionState.CONNECTING
                || c.connectionState() == SessionController.ConnectionState.DISCONNECTED);

        if (reconnecting) {
            showBanner(R.drawable.ic_bluetooth_searching, R.color.color_muted_foreground, true,
                    R.string.banner_reconnecting_title, R.string.banner_reconnecting_detail, false);
        } else if (c.connectionState() == SessionController.ConnectionState.ERROR) {
            showBanner(R.drawable.ic_triangle_alert, R.color.color_destructive, false,
                    R.string.banner_connection_lost_title, R.string.banner_connection_lost_detail, true);
        } else if (c.connectionState() == SessionController.ConnectionState.DISCONNECTED) {
            showBanner(R.drawable.ic_wifi_off, R.color.band_moderate_graphic, false,
                    R.string.banner_disconnected_title, R.string.banner_disconnected_detail, true);
        } else if (c.isWarmingUp()) {
            showBanner(R.drawable.ic_bluetooth, R.color.color_muted_foreground, true,
                    R.string.banner_warming_up_title, R.string.banner_warming_up_detail, false);
        } else {
            bannerCard.setVisibility(View.GONE);
        }
    }

    private void showBanner(int iconRes, int tintRes, boolean spinning, int titleRes, int detailRes,
                             boolean showAction) {
        bannerCard.setVisibility(View.VISIBLE);
        bannerIcon.setVisibility(spinning ? View.GONE : View.VISIBLE);
        bannerSpinner.setVisibility(spinning ? View.VISIBLE : View.GONE);
        bannerIcon.setImageResource(iconRes);
        bannerIcon.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(tintRes)));
        bannerTitle.setText(titleRes);
        bannerDetail.setText(detailRes);
        bannerAction.setVisibility(showAction ? View.VISIBLE : View.GONE);
    }

    private void bindMetric(TextView valueView, TextView bandView, SparklineView sparklineView,
                             Metric metric, List<TracePoint> trace) {
        TracePoint latest = trace.get(trace.size() - 1);
        valueView.setText(metric.format(metric.valueOf(latest)));

        Band band = metric.bandOf(latest);
        int lineColor;
        if (band != null) {
            bandView.setText(band.label.toUpperCase(Locale.US));
            bandView.setTextColor(getColor(band.textColorRes));
            lineColor = getColor(band.graphicColorRes);
        } else {
            bandView.setText(metric.source);
            bandView.setTextColor(getColor(R.color.color_muted_foreground));
            lineColor = getColor(R.color.color_muted_foreground);
        }

        int from = Math.max(0, trace.size() - SPARKLINE_POINTS);
        List<Float> values = new ArrayList<>();
        for (TracePoint p : trace.subList(from, trace.size())) {
            values.add((float) metric.valueOf(p));
        }
        sparklineView.setData(values, lineColor);
    }

    private void renderMapPills() {
        mapPillPm25.setBackground(mapColorBy == Metric.PM25 ? getDrawable(R.drawable.bg_segment_selected) : null);
        mapPillPm10.setBackground(mapColorBy == Metric.PM10 ? getDrawable(R.drawable.bg_segment_selected) : null);
        mapPillNoise.setBackground(mapColorBy == Metric.NOISE ? getDrawable(R.drawable.bg_segment_selected) : null);
    }
}

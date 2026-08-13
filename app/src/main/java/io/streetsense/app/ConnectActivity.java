package io.streetsense.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;
import java.util.Map;

import io.streetsense.app.session.SessionController;
import io.streetsense.app.ui.TopBar;

/**
 * Screen 2 — search, pair and warm up the module. There is no way past this
 * screen without a connected, warmed-up module. See docs/handoff/screens.md #2.
 *
 * <p>Bluetooth-off and permission-denied are not part of the design mock
 * (flagged there as needing a decision) — per the user's direction they
 * reuse this screen's own error-card pattern rather than a new component.
 */
public final class ConnectActivity extends AppCompatActivity implements SessionController.Listener {

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
    };

    private View statusDiscRing;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDetail;
    private View stepRail;
    private TextView stepScan;
    private TextView stepPair;
    private TextView stepStream;
    private View connector1;
    private View connector2;

    private MaterialCardView skeletonCard;
    private MaterialCardView connectedCard;
    private MaterialCardView errorCard;
    private MaterialCardView droppedCard;
    private TextView deviceName;
    private TextView deviceId;
    private TextView deviceBattery;
    private View warmingUpRow;
    private TextView errorCardTitle;
    private TextView errorCardDetail;
    private LinearLayout errorHints;
    private TextView droppedText;

    private MaterialButton actionButton;
    private TextView captionText;

    private boolean permissionDenied;
    private boolean bluetoothOff;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    (Map<String, Boolean> results) -> checkPreconditionsThenMaybeScan());

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> checkPreconditionsThenMaybeScan());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        io.streetsense.app.ui.EdgeInsets.apply(findViewById(R.id.root),
                findViewById(R.id.scrollContent), findViewById(R.id.bottomBar));

        View topBar = findViewById(R.id.topBar);
        ((TextView) topBar.findViewById(R.id.topBarTitle)).setText(R.string.connect_title);
        View backButton = topBar.findViewById(R.id.backButton);
        backButton.setVisibility(View.VISIBLE);
        backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        statusDiscRing = findViewById(R.id.statusDiscRing);
        statusIcon = findViewById(R.id.statusIcon);
        statusTitle = findViewById(R.id.statusTitle);
        statusDetail = findViewById(R.id.statusDetail);
        stepRail = findViewById(R.id.stepRail);
        stepScan = findViewById(R.id.stepScan);
        stepPair = findViewById(R.id.stepPair);
        stepStream = findViewById(R.id.stepStream);
        connector1 = findViewById(R.id.connector1);
        connector2 = findViewById(R.id.connector2);

        skeletonCard = findViewById(R.id.skeletonCard);
        connectedCard = findViewById(R.id.connectedCard);
        errorCard = findViewById(R.id.errorCard);
        droppedCard = findViewById(R.id.droppedCard);
        deviceName = findViewById(R.id.deviceName);
        deviceId = findViewById(R.id.deviceId);
        deviceBattery = findViewById(R.id.deviceBattery);
        warmingUpRow = findViewById(R.id.warmingUpRow);
        errorCardTitle = findViewById(R.id.errorCardTitle);
        errorCardDetail = findViewById(R.id.errorCardDetail);
        errorHints = findViewById(R.id.errorHints);
        droppedText = findViewById(R.id.droppedText);

        actionButton = findViewById(R.id.connectActionButton);
        captionText = findViewById(R.id.connectCaption);
        actionButton.setOnClickListener(v -> onActionClicked());
    }

    private SessionController controller() {
        return ((StreetSenseApp) getApplication()).sessionController();
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller().addListener(this);
        checkPreconditionsThenMaybeScan();
    }

    @Override
    protected void onStop() {
        super.onStop();
        controller().removeListener(this);
    }

    @Override
    public void onSessionStateChanged() {
        render();
    }

    private boolean hasPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean isBluetoothEnabled() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private void checkPreconditionsThenMaybeScan() {
        if (!hasPermissions()) {
            permissionDenied = true;
            render();
            permissionLauncher.launch(REQUIRED_PERMISSIONS);
            return;
        }
        permissionDenied = false;

        if (!isBluetoothEnabled()) {
            bluetoothOff = true;
            render();
            return;
        }
        bluetoothOff = false;

        if (controller().connectionState() == SessionController.ConnectionState.IDLE) {
            controller().startScan();
        }
        render();
    }

    private void onActionClicked() {
        if (permissionDenied) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
            return;
        }
        if (bluetoothOff) {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        switch (controller().connectionState()) {
            case IDLE -> checkPreconditionsThenMaybeScan();
            case ERROR, DISCONNECTED -> controller().retry();
            case CONNECTED -> {
                if (!controller().isWarmingUp()) {
                    controller().startSession();
                    startActivity(new Intent(this, SessionActivity.class));
                }
            }
            default -> { /* button is disabled while searching/connecting */ }
        }
    }

    // --- rendering ---

    private void render() {
        TopBar.bindBatteryPill(
                findViewById(R.id.topBar).findViewById(R.id.batteryPill),
                findViewById(R.id.topBar).findViewById(R.id.batteryIcon),
                findViewById(R.id.topBar).findViewById(R.id.batteryText),
                controller());

        if (permissionDenied) {
            renderDisc(R.drawable.ic_triangle_alert, R.color.color_destructive, false);
            statusTitle.setText(R.string.error_permission_denied_title);
            statusDetail.setText(R.string.error_permission_denied_detail);
            stepRail.setVisibility(View.GONE);
            showDeviceCard(errorCard);
            errorCardTitle.setText(R.string.error_what_to_check);
            errorCardDetail.setText(R.string.error_permission_denied_detail);
            renderHints(R.string.error_permission_denied_hint_1, R.string.error_permission_denied_hint_2,
                    R.string.error_permission_denied_hint_3);
            setAction(getString(R.string.action_open_settings), true, false);
            captionText.setText(R.string.caption_module_required);
            return;
        }

        if (bluetoothOff) {
            renderDisc(R.drawable.ic_triangle_alert, R.color.color_destructive, false);
            statusTitle.setText(R.string.error_bluetooth_off_title);
            statusDetail.setText(R.string.error_bluetooth_off_detail);
            stepRail.setVisibility(View.GONE);
            showDeviceCard(errorCard);
            errorCardTitle.setText(R.string.error_what_to_check);
            errorCardDetail.setText(R.string.error_bluetooth_off_detail);
            renderHints(R.string.error_bluetooth_off_hint_1, R.string.error_bluetooth_off_hint_2);
            setAction(getString(R.string.action_turn_on_bluetooth), true, false);
            captionText.setText(R.string.caption_module_required);
            return;
        }

        SessionController c = controller();
        SessionController.ConnectionState state = c.connectionState();
        boolean warmingUp = c.isWarmingUp();

        stepRail.setVisibility(state == SessionController.ConnectionState.SEARCHING
                || state == SessionController.ConnectionState.CONNECTING
                || state == SessionController.ConnectionState.CONNECTED ? View.VISIBLE : View.GONE);
        renderStepRail(state);

        switch (state) {
            case IDLE -> {
                renderDisc(R.drawable.ic_bluetooth, R.color.color_muted_foreground, false);
                statusTitle.setText(R.string.connect_state_idle_title);
                statusDetail.setText(R.string.connect_state_idle_detail);
                showDeviceCard(null);
                setAction(getString(R.string.action_search_for_module), true, false);
                captionText.setText(R.string.caption_module_required);
            }
            case SEARCHING -> {
                renderDisc(R.drawable.ic_bluetooth_searching, R.color.color_muted_foreground, true);
                statusTitle.setText(R.string.connect_state_searching_title);
                statusDetail.setText(R.string.connect_state_searching_detail);
                showDeviceCard(skeletonCard);
                setAction(getString(R.string.action_scanning), false, true);
                captionText.setText(R.string.caption_module_required);
            }
            case CONNECTING -> {
                renderDisc(R.drawable.ic_bluetooth_searching, R.color.color_muted_foreground, true);
                if (c.isReconnecting()) {
                    statusTitle.setText(R.string.connect_state_reconnecting_title);
                    statusDetail.setText(R.string.connect_state_reconnecting_detail);
                } else {
                    statusTitle.setText(R.string.connect_state_connecting_title);
                    statusDetail.setText(R.string.connect_state_connecting_detail);
                }
                showDeviceCard(skeletonCard);
                setAction(getString(R.string.action_pairing), false, true);
                captionText.setText(R.string.caption_module_required);
            }
            case CONNECTED -> {
                renderDisc(R.drawable.ic_bluetooth_connected, R.color.band_good_graphic, false);
                statusTitle.setText(R.string.connect_state_connected_title);
                statusDetail.setText(R.string.connect_state_connected_detail);
                showDeviceCard(connectedCard);
                renderConnectedCard();
                if (warmingUp) {
                    setAction(getString(R.string.action_warming_up_sensor), false, true);
                    captionText.setText(R.string.caption_warming_up);
                } else {
                    String activityLabel = c.activity() == null ? "" :
                            c.activity().name().toLowerCase(Locale.US);
                    setAction(getString(R.string.action_start_session_format, activityLabel), true, false);
                    captionText.setText(R.string.caption_module_ready);
                }
            }
            case DISCONNECTED -> {
                renderDisc(R.drawable.ic_wifi_off, R.color.band_moderate_graphic, false);
                statusTitle.setText(R.string.connect_state_disconnected_title);
                statusDetail.setText(R.string.connect_state_disconnected_detail);
                showDeviceCard(droppedCard);
                String name = c.device() != null && androidx.core.content.ContextCompat
                        .checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ? c.device().getName() : getString(R.string.connect_title);
                droppedText.setText(getString(R.string.device_dropped, name == null ? "" : name));
                setAction(getString(R.string.action_try_again), true, false);
                captionText.setText(R.string.caption_module_required);
            }
            case ERROR -> {
                renderDisc(R.drawable.ic_triangle_alert, R.color.color_destructive, false);
                statusTitle.setText(R.string.connect_state_error_title);
                statusDetail.setText(R.string.connect_state_error_detail);
                showDeviceCard(errorCard);
                renderErrorReasonCard(c.errorReason());
                setAction(getString(R.string.action_try_again), true, false);
                captionText.setText(R.string.caption_module_required);
            }
        }
    }

    private void renderStepRail(SessionController.ConnectionState state) {
        int currentIndex = switch (state) {
            case SEARCHING -> 0;
            case CONNECTING -> 1;
            case CONNECTED -> 2;
            default -> -1;
        };
        stepScan.setAlpha(currentIndex >= 0 ? 1f : 0.5f);
        stepPair.setAlpha(currentIndex >= 1 ? 1f : 0.5f);
        stepStream.setAlpha(currentIndex >= 2 ? 1f : 0.5f);
        connector1.setBackgroundColor(currentIndex >= 1 ? getColor(R.color.band_good_graphic) : getColor(R.color.color_border));
        connector2.setBackgroundColor(currentIndex >= 2 ? getColor(R.color.band_good_graphic) : getColor(R.color.color_border));
    }

    private void renderDisc(int iconRes, int tintColorRes, boolean pulsing) {
        statusIcon.setImageResource(iconRes);
        int tint = getColor(tintColorRes);
        statusIcon.setImageTintList(ColorStateList.valueOf(tint));

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke((int) (1 * getResources().getDisplayMetrics().density),
                withAlpha(tint, 0.22f));
        statusDiscRing.setBackground(ring);

        statusIcon.clearAnimation();
        if (pulsing) {
            android.view.animation.Animation breathe = new android.view.animation.AlphaAnimation(0.5f, 1f);
            breathe.setDuration(900);
            breathe.setRepeatMode(android.view.animation.Animation.REVERSE);
            breathe.setRepeatCount(android.view.animation.Animation.INFINITE);
            statusIcon.startAnimation(breathe);
        } else {
            statusIcon.setAlpha(1f);
        }
    }

    private static int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void showDeviceCard(View toShow) {
        skeletonCard.setVisibility(toShow == skeletonCard ? View.VISIBLE : View.GONE);
        connectedCard.setVisibility(toShow == connectedCard ? View.VISIBLE : View.GONE);
        errorCard.setVisibility(toShow == errorCard ? View.VISIBLE : View.GONE);
        droppedCard.setVisibility(toShow == droppedCard ? View.VISIBLE : View.GONE);
    }

    private void renderConnectedCard() {
        SessionController c = controller();
        boolean hasConnectPermission = androidx.core.content.ContextCompat
                .checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        String name = c.device() != null && hasConnectPermission ? c.device().getName() : null;
        deviceName.setText(name != null ? name : getString(R.string.connect_state_connected_title));
        deviceId.setText(c.device() != null ? c.device().getAddress() : "");

        if (c.isBatteryValid()) {
            int pct = (int) Math.round(c.batterySoc());
            deviceBattery.setText(getString(R.string.battery_pill_format, pct));
            deviceBattery.setTextColor(getColor(pct < 20 ? R.color.color_destructive : R.color.color_foreground));
        } else {
            deviceBattery.setText("—");
            deviceBattery.setTextColor(getColor(R.color.color_muted_foreground));
        }

        warmingUpRow.setVisibility(c.isWarmingUp() ? View.VISIBLE : View.GONE);
    }

    private void renderErrorReasonCard(SessionController.ErrorReason reason) {
        if (reason == null) reason = SessionController.ErrorReason.TIMEOUT;
        switch (reason) {
            case NOT_FOUND -> {
                errorCardDetail.setText(getString(R.string.error_not_found_title) + " — "
                        + getString(R.string.error_not_found_detail));
                renderHints(R.string.error_not_found_hint_1, R.string.error_not_found_hint_2,
                        R.string.error_not_found_hint_3);
            }
            case TIMEOUT -> {
                errorCardDetail.setText(getString(R.string.error_timeout_title) + " — "
                        + getString(R.string.error_timeout_detail));
                renderHints(R.string.error_timeout_hint_1, R.string.error_timeout_hint_2,
                        R.string.error_timeout_hint_3);
            }
            case SENSOR -> {
                errorCardDetail.setText(getString(R.string.error_sensor_title) + " — "
                        + getString(R.string.error_sensor_detail));
                renderHints(R.string.error_sensor_hint_1, R.string.error_sensor_hint_2,
                        R.string.error_sensor_hint_3);
            }
        }
    }

    private void renderHints(int... hintRes) {
        errorHints.removeAllViews();
        for (int res : hintRes) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.TOP);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(6);
            row.setLayoutParams(rowParams);

            View dot = new View(this);
            int dotSize = dp(6);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.topMargin = dp(6);
            dotParams.setMarginEnd(dp(10));
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(getColor(R.color.color_destructive));
            dot.setBackground(dotDrawable);
            dot.setLayoutParams(dotParams);

            TextView text = new TextView(this);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            text.setTextAppearance(R.style.TextAppearance_StreetSense_BodySm);
            text.setText(res);

            row.addView(dot);
            row.addView(text);
            errorHints.addView(row);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void setAction(String text, boolean enabled, boolean loading) {
        actionButton.setText(text);
        actionButton.setEnabled(enabled && !loading);
        actionButton.setAlpha(enabled && !loading ? 1f : 0.35f);
        actionButton.setIconResource(!loading && text.equals(getString(R.string.action_try_again))
                ? R.drawable.ic_refresh : 0);
        actionButton.setIconTint(ColorStateList.valueOf(getColor(R.color.color_primary_foreground)));
    }
}

package io.streetsense.app.ui;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import io.streetsense.app.R;
import io.streetsense.app.session.SessionController;

/** Battery pill rendering shared by every screen's top bar — see docs/handoff/screens.md. */
public final class TopBar {

    private TopBar() {}

    public static void bindBatteryPill(View pillContainer, ImageView icon, TextView text,
                                        SessionController controller) {
        boolean deviceKnown = controller.connectionState() == SessionController.ConnectionState.CONNECTED
                || controller.connectionState() == SessionController.ConnectionState.DISCONNECTED;
        if (!deviceKnown || !controller.isBatteryValid()) {
            pillContainer.setVisibility(View.GONE);
            return;
        }

        pillContainer.setVisibility(View.VISIBLE);
        int pct = (int) Math.round(controller.batterySoc());
        boolean charging = controller.isCharging();

        text.setText(pillContainer.getContext().getString(R.string.battery_pill_format, pct));
        icon.setImageResource(charging ? R.drawable.ic_battery_charging : R.drawable.ic_battery);

        int colorRes = pct < 20 ? R.color.color_destructive : R.color.color_muted_foreground;
        int color = pillContainer.getContext().getColor(colorRes);
        text.setTextColor(color);
        icon.setImageTintList(ColorStateList.valueOf(color));

        String suffix = charging
                ? pillContainer.getContext().getString(R.string.battery_pill_charging_suffix)
                : "";
        pillContainer.setContentDescription(
                pillContainer.getContext().getString(R.string.battery_pill_description, pct, suffix));
    }
}

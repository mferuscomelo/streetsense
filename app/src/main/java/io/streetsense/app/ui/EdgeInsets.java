package io.streetsense.app.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Pads scrolling content below the status bar and the bottom action bar
 * above the navigation bar. Every screen targets SDK 36, where edge-to-edge
 * is enforced by the platform, so without this the top bar (clock/battery/
 * signal) would sit flush against our own top bar and the bottom buttons
 * flush against the gesture/nav bar.
 */
public final class EdgeInsets {

    private EdgeInsets() {}

    public static void apply(View root, View scrollingContent, View bottomBar) {
        int scrollBaseTop = scrollingContent.getPaddingTop();
        int scrollBaseBottom = scrollingContent.getPaddingBottom();
        int bottomBaseBottom = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // The bottom bar overlays the tail of the scrolling content, so its
            // padding estimate must grow by the same nav-bar inset or the
            // now-taller bar will cover more content than before.
            scrollingContent.setPadding(scrollingContent.getPaddingLeft(), scrollBaseTop + bars.top,
                    scrollingContent.getPaddingRight(), scrollBaseBottom + bars.bottom);
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                    bottomBar.getPaddingRight(), bottomBaseBottom + bars.bottom);
            return insets;
        });
    }
}

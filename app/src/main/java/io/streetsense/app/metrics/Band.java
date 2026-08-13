package io.streetsense.app.metrics;

import io.streetsense.app.R;

/**
 * Air-quality traffic-light band. Each has a graphic colour (lines, dots,
 * fills — identical in light/dark) and a text-safe colour (labels — differs
 * per theme, since the graphic colour alone doesn't reach 4.5:1 contrast on
 * the card surface). See docs/handoff/design-system.md "Air-quality bands".
 */
public enum Band {
    GOOD(R.color.band_good_graphic, R.color.band_good_text, "Good"),
    FAIR(R.color.band_fair_graphic, R.color.band_fair_text, "Fair"),
    MODERATE(R.color.band_moderate_graphic, R.color.band_moderate_text, "Moderate"),
    POOR(R.color.band_poor_graphic, R.color.band_poor_text, "Poor"),
    SEVERE(R.color.band_severe_graphic, R.color.band_severe_text, "Severe");

    public final int graphicColorRes;
    public final int textColorRes;
    public final String label;

    Band(int graphicColorRes, int textColorRes, String label) {
        this.graphicColorRes = graphicColorRes;
        this.textColorRes = textColorRes;
        this.label = label;
    }
}

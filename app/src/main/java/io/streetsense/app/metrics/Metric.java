package io.streetsense.app.metrics;

import java.util.Locale;

import io.streetsense.app.session.TracePoint;

/**
 * The eight readings shown across Session/MetricSheet/Summary, plus their
 * display metadata and traffic-light band thresholds. See
 * docs/handoff/data-model.md "Metrics" — order here is the summary list
 * order. Temperature and Humidity have no thresholds and are never
 * band-coloured (see {@link #hasBands()}).
 */
public enum Metric {
    PM25("PM2.5", "PM2.5", "µg/m³", 1, "SEN54", new double[]{10, 20, 35, 60}) {
        public double valueOf(TracePoint p) { return p.pm2_5(); }
    },
    PM10("PM10", "PM10", "µg/m³", 1, "SEN54", new double[]{20, 40, 60, 100}) {
        public double valueOf(TracePoint p) { return p.pm10(); }
    },
    PM1("PM1.0", "PM1.0", "µg/m³", 1, "SEN54", new double[]{8, 15, 25, 45}) {
        public double valueOf(TracePoint p) { return p.pm1(); }
    },
    PM4("PM4.0", "PM4.0", "µg/m³", 1, "SEN54", new double[]{15, 30, 50, 80}) {
        public double valueOf(TracePoint p) { return p.pm4(); }
    },
    NOISE("Noise", "Noise", "dB(A)", 0, "Module", new double[]{55, 65, 75, 85}) {
        public double valueOf(TracePoint p) { return p.noiseDb(); }
    },
    VOC("VOC Index", "VOC", "index", 0, "SEN54", new double[]{100, 150, 250, 400}) {
        public double valueOf(TracePoint p) { return p.vocIndex(); }
    },
    TEMP("Temperature", "Temp", "°C", 1, "SEN54", null) {
        public double valueOf(TracePoint p) { return p.tempC(); }
    },
    HUMIDITY("Humidity", "Humidity", "%RH", 0, "SEN54", null) {
        public double valueOf(TracePoint p) { return p.humidity(); }
    };

    /** Order used everywhere a per-metric list is shown (e.g. the Summary screen). */
    public static final Metric[] SUMMARY_ORDER =
            {PM25, PM10, PM1, PM4, NOISE, VOC, TEMP, HUMIDITY};

    public final String label;
    public final String shortLabel;
    public final String unit;
    public final int decimals;
    public final String source;
    private final double[] thresholds;

    Metric(String label, String shortLabel, String unit, int decimals, String source, double[] thresholds) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.unit = unit;
        this.decimals = decimals;
        this.source = source;
        this.thresholds = thresholds;
    }

    public abstract double valueOf(TracePoint p);

    public boolean hasBands() {
        return thresholds != null;
    }

    /** Band function: value <= t0 -> good, <= t1 -> fair, <= t2 -> moderate, <= t3 -> poor, else severe. */
    public Band bandOf(double value) {
        if (thresholds == null) return null;
        if (value <= thresholds[0]) return Band.GOOD;
        if (value <= thresholds[1]) return Band.FAIR;
        if (value <= thresholds[2]) return Band.MODERATE;
        if (value <= thresholds[3]) return Band.POOR;
        return Band.SEVERE;
    }

    public Band bandOf(TracePoint p) {
        return bandOf(valueOf(p));
    }

    /** Fixed decimal count, never trimmed — keeps live values from changing width. */
    public String format(double value) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}

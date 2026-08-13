package io.streetsense.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.streetsense.app.metrics.Band;
import io.streetsense.app.metrics.Metric;
import io.streetsense.app.session.SessionController;
import io.streetsense.app.session.TracePoint;
import io.streetsense.app.ui.AreaChartView;

/**
 * The metric detail bottom sheet — see docs/handoff/screens.md #4. Opens
 * over Session or Summary; reads directly from the shared
 * {@link SessionController}, so it works whether the session is still live
 * (Session) or has just ended (Summary).
 */
public final class MetricSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_METRIC = "metric";
    private enum Range { ONE_MIN, FIVE_MIN, SESSION }

    private Metric metric;
    private Range range = Range.SESSION;

    private TextView range1Min;
    private TextView range5Min;
    private TextView rangeSession;
    private AreaChartView chart;

    public static MetricSheetFragment newInstance(Metric metric) {
        MetricSheetFragment fragment = new MetricSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_METRIC, metric.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        metric = Metric.valueOf(requireArguments().getString(ARG_METRIC));
        return inflater.inflate(R.layout.fragment_metric_sheet, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // The default sheet background fights our own rounded/coloured
        // container — make it transparent so only bg_sheet_top_rounded shows.
        android.app.Dialog dialog = getDialog();
        View sheet = dialog == null ? null
                : dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView source = view.findViewById(R.id.sheetSource);
        TextView title = view.findViewById(R.id.sheetTitle);
        source.setText(metric.source);
        title.setText(metric.label);

        range1Min = view.findViewById(R.id.range1Min);
        range5Min = view.findViewById(R.id.range5Min);
        rangeSession = view.findViewById(R.id.rangeSession);
        range1Min.setOnClickListener(v -> setRange(Range.ONE_MIN));
        range5Min.setOnClickListener(v -> setRange(Range.FIVE_MIN));
        rangeSession.setOnClickListener(v -> setRange(Range.SESSION));

        chart = view.findViewById(R.id.chart);
        ((TextView) view.findViewById(R.id.sheetAbout)).setText(aboutTextRes());

        render();
    }

    private int getColor(int res) {
        return requireContext().getColor(res);
    }

    private SessionController controller() {
        return ((StreetSenseApp) requireActivity().getApplication()).sessionController();
    }

    private void setRange(Range newRange) {
        range = newRange;
        render();
    }

    private List<TracePoint> filteredTrace() {
        List<TracePoint> trace = controller().trace();
        int count = switch (range) {
            case ONE_MIN -> 60;
            case FIVE_MIN -> 300;
            case SESSION -> trace.size();
        };
        int from = Math.max(0, trace.size() - count);
        return trace.subList(from, trace.size());
    }

    private void render() {
        renderRangeSelector();

        List<TracePoint> full = controller().trace();
        View view = getView();
        if (view == null) return;

        TextView value = view.findViewById(R.id.sheetValue);
        TextView unit = view.findViewById(R.id.sheetUnit);
        TextView band = view.findViewById(R.id.sheetBand);
        unit.setText(metric.unit);

        if (!full.isEmpty()) {
            TracePoint latest = full.get(full.size() - 1);
            double v = metric.valueOf(latest);
            value.setText(metric.format(v));
            Band b = metric.bandOf(v);
            if (b != null) {
                band.setVisibility(View.VISIBLE);
                band.setText(b.label.toUpperCase(Locale.US));
                band.setTextColor(getColor(b.textColorRes));
                value.setTextColor(getColor(b.textColorRes));
            } else {
                band.setVisibility(View.GONE);
                value.setTextColor(getColor(R.color.color_foreground));
            }
        }

        List<TracePoint> filtered = filteredTrace();
        List<Float> values = new ArrayList<>();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        for (TracePoint p : filtered) {
            double v = metric.valueOf(p);
            values.add((float) v);
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double avg = filtered.isEmpty() ? 0 : sum / filtered.size();
        if (filtered.isEmpty()) { min = 0; max = 0; }

        int lineColor = getColor(R.color.color_muted_foreground);
        if (!filtered.isEmpty()) {
            Band b = metric.bandOf(metric.valueOf(filtered.get(filtered.size() - 1)));
            if (b != null) lineColor = getColor(b.graphicColorRes);
        }
        chart.setColors(lineColor, getColor(R.color.color_border), getColor(R.color.color_muted_foreground),
                getColor(R.color.color_card), getColor(R.color.color_border));
        chart.setData(values, new AreaChartView.Formatter() {
            @Override
            public String formatValue(float v) {
                return metric.format(v);
            }

            @Override
            public String formatElapsed(int index) {
                return formatElapsedSeconds(index);
            }
        });

        bindStat(view.findViewById(R.id.statMin), R.string.stat_min, min);
        bindStat(view.findViewById(R.id.statAvg), R.string.stat_avg, avg);
        bindStat(view.findViewById(R.id.statMax), R.string.stat_max, max);
    }

    private String formatElapsedSeconds(int index) {
        int seconds = index; // ~1 sample/second, see docs/handoff/data-model.md
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m";
    }

    private void bindStat(View cell, int labelRes, double value) {
        ((TextView) cell.findViewById(R.id.statCellLabel)).setText(labelRes);
        ((TextView) cell.findViewById(R.id.statCellValue)).setText(metric.format(value));
        ((TextView) cell.findViewById(R.id.statCellUnit)).setText(metric.unit);
    }

    private void renderRangeSelector() {
        range1Min.setBackground(range == Range.ONE_MIN ? requireContext().getDrawable(R.drawable.bg_segment_selected) : null);
        range5Min.setBackground(range == Range.FIVE_MIN ? requireContext().getDrawable(R.drawable.bg_segment_selected) : null);
        rangeSession.setBackground(range == Range.SESSION ? requireContext().getDrawable(R.drawable.bg_segment_selected) : null);
    }

    private int aboutTextRes() {
        return switch (metric) {
            case PM25 -> R.string.about_pm25;
            case PM10 -> R.string.about_pm10;
            case PM1 -> R.string.about_pm1;
            case PM4 -> R.string.about_pm4;
            case NOISE -> R.string.about_noise;
            case VOC -> R.string.about_voc;
            case TEMP -> R.string.about_temp;
            case HUMIDITY -> R.string.about_humidity;
        };
    }
}

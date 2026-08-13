package io.streetsense.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The metric sheet's area chart — see docs/handoff/screens.md #4. 2dp band-
 * coloured line, gradient fill, horizontal dashed gridlines only (no axis
 * lines/ticks besides the 3 value labels), and a touch tooltip.
 */
public final class AreaChartView extends View {

    public interface Formatter {
        String formatValue(float value);
        String formatElapsed(int index);
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private List<Float> values = List.of();
    private int color = 0xFF000000;
    private Formatter formatter;
    private Integer touchIndex;
    private final float density;
    private final float axisWidth;

    public AreaChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        axisWidth = 30 * density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1 * density);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{2 * density, 6 * density}, 0));

        axisTextPaint.setTextSize(11 * density);
        axisTextPaint.setTextAlign(Paint.Align.LEFT);

        tooltipBgPaint.setStyle(Paint.Style.FILL);
        tooltipBorderPaint.setStyle(Paint.Style.STROKE);
        tooltipBorderPaint.setStrokeWidth(1 * density);
        tooltipTextPaint.setTextSize(12 * density);
        tooltipTextPaint.setTextAlign(Paint.Align.LEFT);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int lineColor, int gridColor, int axisTextColor, int cardColor, int borderColor) {
        this.color = lineColor;
        linePaint.setColor(lineColor);
        gridPaint.setColor(gridColor);
        axisTextPaint.setColor(axisTextColor);
        tooltipBgPaint.setColor(cardColor);
        tooltipBorderPaint.setColor(borderColor);
        tooltipTextPaint.setColor(axisTextColor);
        dotPaint.setColor(lineColor);
        fillPaint.setShader(null);
        invalidate();
    }

    public void setData(List<Float> values, Formatter formatter) {
        this.values = values;
        this.formatter = formatter;
        touchIndex = null;
        fillPaint.setShader(null);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fillPaint.setShader(null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values.size() < 2) return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                float chartLeft = axisWidth;
                float chartWidth = getWidth() - axisWidth;
                float fraction = (event.getX() - chartLeft) / chartWidth;
                int index = Math.round(fraction * (values.size() - 1));
                touchIndex = Math.max(0, Math.min(values.size() - 1, index));
                invalidate();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchIndex = null;
                invalidate();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float chartLeft = axisWidth;
        float chartWidth = w - axisWidth;
        float chartHeight = h;

        if (values.size() < 2) return;

        float rawMin = Collections.min(values);
        float rawMax = Collections.max(values);
        float min = rawMin - 1f;
        float max = rawMax + 1f;
        if (max == min) { max += 1; min -= 1; }

        // 3 dashed gridlines + value labels
        for (int i = 0; i < 3; i++) {
            float frac = i / 2f;
            float y = chartHeight - frac * chartHeight;
            canvas.drawLine(chartLeft, y, w, y, gridPaint);
            float value = min + frac * (max - min);
            canvas.drawText(formatter != null ? formatter.formatValue(value) : String.format(Locale.US, "%.0f", value),
                    0, y - 4 * density, axisTextPaint);
        }

        linePath.reset();
        fillPath.reset();
        int n = values.size();
        for (int i = 0; i < n; i++) {
            float x = chartLeft + chartWidth * i / (float) (n - 1);
            float y = chartHeight - ((values.get(i) - min) / (max - min)) * chartHeight;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartHeight);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(chartLeft + chartWidth, chartHeight);
        fillPath.close();

        if (fillPaint.getShader() == null) {
            fillPaint.setShader(new LinearGradient(0, 0, 0, chartHeight,
                    withAlpha(color, 0.28f), withAlpha(color, 0f), Shader.TileMode.CLAMP));
        }
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        if (touchIndex != null) {
            int i = touchIndex;
            float x = chartLeft + chartWidth * i / (float) (n - 1);
            float y = chartHeight - ((values.get(i) - min) / (max - min)) * chartHeight;
            canvas.drawLine(x, 0, x, chartHeight, gridPaint);
            canvas.drawCircle(x, y, 4 * density, dotPaint);

            String line1 = formatter != null ? formatter.formatElapsed(i) : "";
            String line2 = formatter != null ? formatter.formatValue(values.get(i)) : "";
            float textW = Math.max(tooltipTextPaint.measureText(line1), tooltipTextPaint.measureText(line2));
            float boxW = textW + 16 * density;
            float boxH = 40 * density;
            float boxX = Math.min(Math.max(x - boxW / 2, chartLeft), w - boxW);
            float boxY = 4 * density;
            RectF rect = new RectF(boxX, boxY, boxX + boxW, boxY + boxH);
            canvas.drawRoundRect(rect, 8 * density, 8 * density, tooltipBgPaint);
            canvas.drawRoundRect(rect, 8 * density, 8 * density, tooltipBorderPaint);
            canvas.drawText(line1, boxX + 8 * density, boxY + 16 * density, tooltipTextPaint);
            canvas.drawText(line2, boxX + 8 * density, boxY + 32 * density, tooltipTextPaint);
        }
    }

    private static int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

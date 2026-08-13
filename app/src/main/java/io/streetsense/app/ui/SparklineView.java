package io.streetsense.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Collections;
import java.util.List;

/**
 * A minimal line + gradient-fill chart — the "sparkline fill" gradient
 * design-system.md explicitly allows. Reused at hero (56dp), duo (22dp) and
 * compact (18dp) heights across Session/Summary; draws the last N values
 * auto-scaled to the view's own min/max.
 */
public final class SparklineView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private List<Float> values = List.of();
    private int color = 0xFF000000;

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Float> values, int color) {
        this.values = values;
        this.color = color;
        linePaint.setColor(color);
        fillPaint.setShader(null);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fillPaint.setShader(null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (values.size() < 2 || w == 0 || h == 0) return;

        float min = Collections.min(values);
        float max = Collections.max(values);
        if (max == min) {
            max += 1f;
            min -= 1f;
        }

        linePath.reset();
        fillPath.reset();
        int n = values.size();
        for (int i = 0; i < n; i++) {
            float x = w * i / (float) (n - 1);
            float y = h - ((values.get(i) - min) / (max - min)) * h;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(w, h);
        fillPath.close();

        if (fillPaint.getShader() == null) {
            fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                    withAlpha(color, 0.28f), withAlpha(color, 0f), Shader.TileMode.CLAMP));
        }
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    private static int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(255 * alphaFraction);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

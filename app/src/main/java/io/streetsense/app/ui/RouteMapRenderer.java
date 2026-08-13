package io.streetsense.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

import androidx.core.content.ContextCompat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.List;

import io.streetsense.app.R;
import io.streetsense.app.metrics.Band;
import io.streetsense.app.metrics.Metric;
import io.streetsense.app.session.TracePoint;

/**
 * Draws the session route as a thick polyline, coloured per segment by the
 * selected metric's band, with an accent dot + halo at the current
 * position — see docs/handoff/screens.md #3 "Route map card". Shared by the
 * live Session map and the static Summary map.
 */
public final class RouteMapRenderer {

    private RouteMapRenderer() {}

    public static void render(MapView map, List<TracePoint> trace, Metric colorBy) {
        map.getOverlays().clear();
        int accent = ContextCompat.getColor(map.getContext(), R.color.color_accent);
        float strokeWidth = 10f * map.getContext().getResources().getDisplayMetrics().density / 2f;

        if (trace.isEmpty()) {
            map.invalidate();
            return;
        }

        for (int i = 1; i < trace.size(); i++) {
            TracePoint a = trace.get(i - 1);
            TracePoint b = trace.get(i);
            Polyline segment = new Polyline();
            segment.setPoints(List.of(
                    new GeoPoint(a.lat(), a.lon()),
                    new GeoPoint(b.lat(), b.lon())));
            Band band = colorBy.bandOf(colorBy.valueOf(b));
            int color = band == null ? accent : ContextCompat.getColor(map.getContext(), band.graphicColorRes);
            segment.getOutlinePaint().setColor(color);
            segment.getOutlinePaint().setStrokeWidth(strokeWidth);
            segment.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
            map.getOverlays().add(segment);
        }

        TracePoint last = trace.get(trace.size() - 1);
        GeoPoint lastPoint = new GeoPoint(last.lat(), last.lon());
        map.getOverlays().add(currentPositionOverlay(lastPoint, accent, map.getContext().getResources().getDisplayMetrics().density));

        map.getController().setZoom(17.0);
        map.getController().setCenter(lastPoint);
        map.invalidate();
    }

    private static Overlay currentPositionOverlay(GeoPoint at, int accent, float density) {
        return new Overlay() {
            @Override
            public void draw(Canvas canvas, MapView mapView, boolean shadow) {
                if (shadow) return;
                Point point = new Point();
                mapView.getProjection().toPixels(at, point);

                Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
                halo.setColor((accent & 0x00FFFFFF) | (0x40 << 24));
                canvas.drawCircle(point.x, point.y, 12f * density, halo);

                Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
                dot.setColor(accent);
                canvas.drawCircle(point.x, point.y, 5f * density, dot);
            }
        };
    }
}

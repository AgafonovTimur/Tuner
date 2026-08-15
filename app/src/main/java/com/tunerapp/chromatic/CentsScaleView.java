package com.tunerapp.chromatic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CentsScaleView extends View {

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int COLOR_YELLOW = Color.parseColor("#E8C400");
    private static final int COLOR_YELLOW_DIM = Color.parseColor("#5A4F00");
    private static final int COLOR_GREEN = Color.parseColor("#9FFF6B");

    private float cents = 0f;

    public CentsScaleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;

        tickPaint.setColor(COLOR_YELLOW_DIM);
        tickPaint.setStrokeWidth(1.5f * density);

        majorTickPaint.setColor(COLOR_YELLOW);
        majorTickPaint.setStrokeWidth(2.5f * density);

        centerLinePaint.setColor(COLOR_YELLOW_DIM);
        centerLinePaint.setStrokeWidth(2f * density);

        labelPaint.setColor(COLOR_YELLOW_DIM);
        labelPaint.setTextSize(11f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        markerPaint.setColor(COLOR_YELLOW);
        markerPaint.setStrokeWidth(4f * density);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setCents(float c) {
        this.cents = Math.max(-50f, Math.min(50f, c));
        invalidate();
    }

    public void setInTune(boolean inTune) {
        markerPaint.setColor(inTune ? COLOR_GREEN : COLOR_YELLOW);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float density = getResources().getDisplayMetrics().density;

        float midY = h * 0.4f;

        // center vertical line
        canvas.drawLine(w / 2f, 0, w / 2f, h, centerLinePaint);

        // ticks every 5 cents, major every 15
        for (int c = -50; c <= 50; c += 5) {
            float x = xForCents(c, w);
            boolean major = (c % 15 == 0);
            float tickHeight = major ? 16f * density : 8f * density;
            canvas.drawLine(x, midY - tickHeight / 2, x, midY + tickHeight / 2, major ? majorTickPaint : tickPaint);
        }
        for (int c = -45; c <= 45; c += 15) {
            float x = xForCents(c, w);
            canvas.drawText(String.valueOf(c), x, midY + 30f * density, labelPaint);
        }

        // marker
        float markerX = xForCents((int) cents, w);
        canvas.drawLine(markerX, midY - 20f * density, markerX, midY + 20f * density, markerPaint);
    }

    private float xForCents(int c, int width) {
        float pct = (c + 50f) / 100f;
        return pct * width;
    }
}

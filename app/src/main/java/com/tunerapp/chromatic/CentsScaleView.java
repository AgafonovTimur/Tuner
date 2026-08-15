package com.tunerapp.chromatic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CentsScaleView extends View {

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int COLOR_YELLOW = Color.parseColor("#E8C400");
    private static final int COLOR_GREEN = Color.parseColor("#4ADE5C");
    private static final int COLOR_TICK = Color.parseColor("#4A4A4A");
    private static final int COLOR_TICK_MAJOR = Color.parseColor("#8A8A8A");
    private static final int COLOR_LABEL = Color.parseColor("#C9C9C9");
    private static final int COLOR_CENTER = Color.parseColor("#E0E0E0");

    private float cents = 0f;
    private final RectF barRect = new RectF();

    public CentsScaleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;

        tickPaint.setColor(COLOR_TICK);
        tickPaint.setStrokeWidth(1.5f * d);

        majorTickPaint.setColor(COLOR_TICK_MAJOR);
        majorTickPaint.setStrokeWidth(2.5f * d);

        centerLinePaint.setColor(COLOR_CENTER);
        centerLinePaint.setStrokeWidth(2.5f * d);

        labelPaint.setColor(COLOR_LABEL);
        labelPaint.setTextSize(22f * d);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        barPaint.setColor(COLOR_YELLOW);
    }

    public void setCents(float c) {
        this.cents = Math.max(-50f, Math.min(50f, c));
        barPaint.setColor(Math.abs(this.cents) < 5f ? COLOR_GREEN : COLOR_YELLOW);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float d = getResources().getDisplayMetrics().density;

        float barCenterY = h * 0.32f;
        float barHalf = 11f * d;      // thick bar
        float centerX = w / 2f;

        // ticks
        float tickY = barCenterY;
        for (int c = -50; c <= 50; c += 5) {
            if (c == 0) continue;
            float x = xForCents(c, w);
            boolean major = (c % 25 == 0);
            float th = major ? 26f * d : 16f * d;
            canvas.drawLine(x, tickY - th / 2, x, tickY + th / 2, major ? majorTickPaint : tickPaint);
        }

        // filled bar from center to current cents
        float x = xForCents(cents, w);
        float left = Math.min(centerX, x);
        float right = Math.max(centerX, x);
        if (right - left < 3f * d) {
            left = centerX - 1.5f * d;
            right = centerX + 1.5f * d;
        }
        barRect.set(left, barCenterY - barHalf, right, barCenterY + barHalf);
        canvas.drawRoundRect(barRect, 4f * d, 4f * d, barPaint);

        // center reference line on top
        canvas.drawLine(centerX, barCenterY - barHalf - 6f * d, centerX, barCenterY + barHalf + 6f * d, centerLinePaint);

        // labels
        float labelY = h * 0.32f + 48f * d;
        for (int c = -50; c <= 50; c += 25) {
            float lx = xForCents(c, w);
            if (c == -50) lx += 16f * d;
            if (c == 50) lx -= 16f * d;
            canvas.drawText(String.valueOf(c), lx, labelY, labelPaint);
        }
    }

    private float xForCents(float c, int width) {
        float pct = (c + 50f) / 100f;
        return pct * width;
    }
}

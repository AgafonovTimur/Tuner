package com.tunerapp.chromatic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Весь экран тюнера рисуется здесь: шкала уровня слева, крупная нота
 * семисегментным шрифтом, частота, отклонение в центах и полоса -50..+50.
 */
public class TunerDisplayView extends View {

    public static final int COLOR_GREEN  = Color.parseColor("#22E64B");
    public static final int COLOR_YELLOW = Color.parseColor("#FDE21A");
    public static final int COLOR_ORANGE = Color.parseColor("#DE821E");

    private static final float IN_TUNE = 5f;   // центов — считается «в ноль»

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint readoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private String note = "";
    private boolean sharp = false;
    private String octave = "";
    private double freq = 0;

    private float cents = 0f, targetCents = 0f;
    private float levelFast = 0f, levelSlow = 0f, targetLevel = 0f;
    private long lastFrame = 0L;
    private boolean animating = false;

    public TunerDisplayView(Context c, AttributeSet a) {
        super(c, a);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        labelPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        glyphPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        readoutPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    public void setNote(String name, boolean isSharp, String oct) {
        note = name; sharp = isSharp; octave = oct; kick();
    }

    public void setFrequency(double f) { freq = f; kick(); }

    public void setCents(float c) {
        targetCents = Math.max(-50f, Math.min(50f, c));
        kick();
    }

    /** уровень сигнала 0..1 */
    public void setLevel(double rms) {
        double db = 20 * Math.log10(Math.max(rms, 1e-6)) + 90;
        targetLevel = (float) Math.max(0, Math.min(60, db));
        kick();
    }

    public int accentColor() {
        if (Math.abs(cents) < IN_TUNE) return COLOR_GREEN;
        return cents > 0 ? COLOR_ORANGE : COLOR_YELLOW;
    }

    private void kick() {
        if (!animating) { animating = true; lastFrame = 0L; postInvalidateOnAnimation(); }
    }

    private void step() {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 1f / 60f : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        if (dt > 0.1f) dt = 0.1f;

        cents     += (targetCents - cents)     * (1 - (float) Math.exp(-dt / 0.12f));
        levelFast += (targetLevel - levelFast) * (1 - (float) Math.exp(-dt / 0.05f));
        levelSlow += (targetLevel - levelSlow) * (1 - (float) Math.exp(-dt / 0.60f));

        boolean settled = Math.abs(targetCents - cents) < 0.05f
                && Math.abs(targetLevel - levelFast) < 0.05f
                && Math.abs(targetLevel - levelSlow) < 0.05f;
        if (settled) { cents = targetCents; levelFast = levelSlow = targetLevel; animating = false; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (animating) step();

        int w = getWidth(), h = getHeight();
        int col = accentColor();
        p.setColor(col);
        textPaint.setColor(col);

        drawLevelScale(canvas, w, h, col);
        drawNote(canvas, w, h, col);
        drawReadouts(canvas, w, h, col);
        drawCentsBar(canvas, w, h, col);

        if (animating) postInvalidateOnAnimation();
    }

    // ---------- шкала уровня слева ----------
    private void drawLevelScale(Canvas canvas, int w, int h, int col) {
        float barX = w * 0.035f;
        float barW = w * 0.028f;
        float top = h * 0.04f, bottom = h * 0.86f;

        float span = 21f;                                   // видимое окно, дБ
        float hi = (float) Math.ceil(Math.max(Math.max(levelFast, levelSlow), 12f) / 3f) * 3f;
        float lo = hi - span;

        // подписи каждые 3 дБ
        textPaint.setTextSize(h * 0.028f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        for (float v = lo + 3; v <= hi; v += 3) {
            float y = yForLevel(v, lo, hi, top, bottom);
            canvas.drawText(String.valueOf((int) v), barX + barW * 2.2f, y + h * 0.011f, textPaint);
        }

        // сама полоса — снизу до текущего уровня
        float yLvl = yForLevel(levelFast, lo, hi, top, bottom);
        p.setStyle(Paint.Style.FILL);
        rect.set(barX, Math.min(yLvl, bottom - barW), barX + barW, bottom);
        canvas.drawRoundRect(rect, barW * 0.4f, barW * 0.4f, p);
        rect.set(barX - barW * 0.55f, bottom, barX + barW * 1.55f, bottom + h * 0.012f);
        canvas.drawRoundRect(rect, barW * 0.2f, barW * 0.2f, p);

        // треугольный указатель усреднённого уровня
        float yAvg = yForLevel(levelSlow, lo, hi, top, bottom);
        float tw = w * 0.035f, th = h * 0.016f;
        float tx = barX + barW * 1.75f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w * 0.004f);
        path.reset();
        path.moveTo(tx, yAvg - th);
        path.lineTo(tx - tw, yAvg);
        path.lineTo(tx, yAvg + th);
        path.close();
        canvas.drawPath(path, p);
        canvas.drawLine(tx - tw - w * 0.05f, yAvg, tx - tw, yAvg, p);
        p.setStyle(Paint.Style.FILL);
    }

    private float yForLevel(float v, float lo, float hi, float top, float bottom) {
        float t = (v - lo) / (hi - lo);
        t = Math.max(0f, Math.min(1f, t));
        return bottom - t * (bottom - top);
    }

    // ---------- крупная нота ----------
    private void drawNote(Canvas canvas, int w, int h, int col) {
        if (note.isEmpty()) return;
        glyphPaint.setColor(col);
        glyphPaint.setTextAlign(Paint.Align.LEFT);

        float noteSize = h * 0.30f;          // на 30% меньше прежнего
        float smallSize = noteSize * 0.42f;
        glyphPaint.setTextSize(noteSize);

        String main = note;
        float mainW = glyphPaint.measureText(main);
        float baseY = h * 0.40f;
        float x = w * 0.34f;
        canvas.drawText(main, x, baseY, glyphPaint);

        float sx = x + mainW + noteSize * 0.06f;
        glyphPaint.setTextSize(smallSize);
        if (sharp) canvas.drawText("#", sx, baseY - noteSize * 0.50f, glyphPaint);
        if (!octave.isEmpty()) canvas.drawText(octave, sx, baseY + smallSize * 0.15f, glyphPaint);
    }

    // ---------- частота и центы ----------
    private void drawReadouts(Canvas canvas, int w, int h, int col) {
        readoutPaint.setColor(col);
        readoutPaint.setTextSize(h * 0.038f);
        float y = h * 0.785f;

        readoutPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(String.format(java.util.Locale.US, "%.1f Hz", freq), w * 0.16f, y, readoutPaint);

        readoutPaint.setTextAlign(Paint.Align.RIGHT);
        String right = String.format(java.util.Locale.US, "%+.1f c", cents);
        canvas.drawText(right, w * 0.85f, y, readoutPaint);
    }

    // ---------- полоса центов ----------
    private void drawCentsBar(Canvas canvas, int w, int h, int col) {
        float left = w * 0.16f, right = w * 0.85f;
        float y = h * 0.895f;
        float cx = (left + right) / 2f;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.0022f);
        canvas.drawLine(left, y, right, y, p);
        float tick = h * 0.018f;
        for (int c = -50; c <= 50; c += 25) {
            float tx = xForCents(c, left, right);
            canvas.drawLine(tx, y - tick, tx, y + tick, p);
        }
        p.setStyle(Paint.Style.FILL);

        float x = xForCents(cents, left, right);
        float half = h * 0.017f;
        float l = Math.min(cx, x), r = Math.max(cx, x);
        if (r - l < half) { l = cx - half * 0.45f; r = cx + half * 0.45f; }
        rect.set(l, y - half, r, y + half);
        canvas.drawRoundRect(rect, half * 0.9f, half * 0.9f, p);

        labelPaint.setColor(col);
        labelPaint.setTextSize(h * 0.031f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        float ly = y + h * 0.055f;
        String[] labels = {"-50", "-20", "0", "+20", "+50"};
        int[] vals = {-50, -20, 0, 20, 50};
        for (int i = 0; i < vals.length; i++) {
            canvas.drawText(labels[i], xForCents(vals[i], left, right), ly, labelPaint);
        }
    }

    private float xForCents(float c, float left, float right) {
        return left + (c + 50f) / 100f * (right - left);
    }

}

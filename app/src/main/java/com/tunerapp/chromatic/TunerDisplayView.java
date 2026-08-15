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
        float glyphH = h * 0.30f;
        float glyphW = glyphH * 0.62f;
        float x = w * 0.30f, y = h * 0.08f;

        drawSegChar(canvas, note.charAt(0), x, y, glyphW, glyphH, col, 0f);

        float smallH = glyphH * 0.36f;
        float sx = x + glyphW * 1.30f;
        if (sharp) drawSharp(canvas, sx, y + glyphH * 0.02f, smallH * 0.62f, smallH, col);
        if (!octave.isEmpty()) {
            drawSegChar(canvas, octave.charAt(0), sx, y + glyphH * 0.72f,
                    smallH * 0.60f, smallH, col, 0f);
        }
    }

    // ---------- частота и центы ----------
    private void drawReadouts(Canvas canvas, int w, int h, int col) {
        float ch = h * 0.042f;
        float y = h * 0.775f;
        String left = String.format("%.1f HZ", freq);
        drawSegString(canvas, left, w * 0.16f, y, ch, col, false);

        String right = String.format("%s%.1f C", cents >= 0 ? "+" : "-", Math.abs(cents));
        drawSegString(canvas, right, w * 0.86f, y, ch, col, true);
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

        textPaint.setTextSize(h * 0.062f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float ly = y + h * 0.085f;
        String[] labels = {"-50", "-20", "0", "+20", "+50"};
        int[] vals = {-50, -20, 0, 20, 50};
        for (int i = 0; i < vals.length; i++) {
            canvas.drawText(labels[i], xForCents(vals[i], left, right), ly, textPaint);
        }
    }

    private float xForCents(float c, float left, float right) {
        return left + (c + 50f) / 100f * (right - left);
    }

    // ---------- семисегментная графика ----------
    private static final boolean[][] SEGS = new boolean[128][];
    static {
        //            a      b      c      d      e      f      g
        put('0', true,  true,  true,  true,  true,  true,  false);
        put('1', false, true,  true,  false, false, false, false);
        put('2', true,  true,  false, true,  true,  false, true);
        put('3', true,  true,  true,  true,  false, false, true);
        put('4', false, true,  true,  false, false, true,  true);
        put('5', true,  false, true,  true,  false, true,  true);
        put('6', true,  false, true,  true,  true,  true,  true);
        put('7', true,  true,  true,  false, false, false, false);
        put('8', true,  true,  true,  true,  true,  true,  true);
        put('9', true,  true,  true,  true,  false, true,  true);
        put('A', true,  true,  true,  false, true,  true,  true);
        put('B', false, false, true,  true,  true,  true,  true);
        put('C', true,  false, false, true,  true,  true,  false);
        put('D', false, true,  true,  true,  true,  false, true);
        put('E', true,  false, false, true,  true,  true,  true);
        put('F', true,  false, false, false, true,  true,  true);
        put('G', true,  false, true,  true,  true,  true,  true);
        put('H', false, true,  true,  false, true,  true,  true);
        put('Z', true,  true,  false, true,  true,  false, true);
        put(' ', false, false, false, false, false, false, false);
        put('-', false, false, false, false, false, false, true);
    }

    private static void put(char c, boolean a, boolean b, boolean cc, boolean d,
                            boolean e, boolean f, boolean g) {
        SEGS[c] = new boolean[]{a, b, cc, d, e, f, g};
    }

    /** строка семисегментом; alignRight — x это правый край */
    private void drawSegString(Canvas canvas, String s, float x, float y,
                               float charH, int col, boolean alignRight) {
        float cw = charH * 0.58f, gap = charH * 0.20f;
        float total = 0;
        for (char c : s.toCharArray()) total += widthOf(c, cw, charH) + gap;
        total -= gap;
        float startX = alignRight ? x - total : x;

        canvas.save();
        canvas.skew(-0.12f, 0f);
        canvas.translate(y * 0.12f, 0);   // компенсация наклона
        float cx = startX;
        for (char c : s.toCharArray()) {
            float ww = widthOf(c, cw, charH);
            if (c == '.') {
                p.setColor(col);
                float t = charH * 0.16f;
                canvas.drawRect(cx, y + charH - t, cx + t, y + charH, p);
            } else if (c == '+') {
                drawPlus(canvas, cx, y, ww, charH, col);
            } else {
                drawSegChar(canvas, c, cx, y, ww, charH, col, 0f);
            }
            cx += ww + gap;
        }
        canvas.restore();
    }

    private float widthOf(char c, float cw, float charH) {
        if (c == '.') return charH * 0.16f;
        if (c == ' ') return cw * 0.5f;
        if (c == '1') return cw * 0.45f;
        return cw;
    }

    private void drawSegChar(Canvas canvas, char c, float x, float y,
                             float w, float h, int col, float skew) {
        boolean[] s = c < 128 ? SEGS[Character.toUpperCase(c)] : null;
        if (s == null) return;
        float t = Math.min(w, h) * 0.20f;
        p.setColor(col);
        p.setStyle(Paint.Style.FILL);

        if (s[0]) seg(canvas, x, y, x + w, y, t, true);
        if (s[1]) seg(canvas, x + w, y, x + w, y + h / 2, t, false);
        if (s[2]) seg(canvas, x + w, y + h / 2, x + w, y + h, t, false);
        if (s[3]) seg(canvas, x, y + h, x + w, y + h, t, true);
        if (s[4]) seg(canvas, x, y + h / 2, x, y + h, t, false);
        if (s[5]) seg(canvas, x, y, x, y + h / 2, t, false);
        if (s[6]) seg(canvas, x, y + h / 2, x + w, y + h / 2, t, true);
    }

    /** один сегмент — вытянутый шестиугольник со скошенными концами */
    private void seg(Canvas canvas, float x1, float y1, float x2, float y2,
                     float t, boolean horizontal) {
        float half = t / 2f, m = t * 0.55f;
        path.reset();
        if (horizontal) {
            path.moveTo(x1 + m * 0.4f, y1);
            path.lineTo(x1 + m, y1 - half);
            path.lineTo(x2 - m, y2 - half);
            path.lineTo(x2 - m * 0.4f, y2);
            path.lineTo(x2 - m, y2 + half);
            path.lineTo(x1 + m, y1 + half);
        } else {
            path.moveTo(x1, y1 + m * 0.4f);
            path.lineTo(x1 - half, y1 + m);
            path.lineTo(x2 - half, y2 - m);
            path.lineTo(x2, y2 - m * 0.4f);
            path.lineTo(x2 + half, y2 - m);
            path.lineTo(x1 + half, y1 + m);
        }
        path.close();
        canvas.drawPath(path, p);
    }

    /** знак диеза наклонными штрихами */
    private void drawSharp(Canvas canvas, float x, float y, float w, float h, int col) {
        p.setColor(col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.17f);
        float dx = w * 0.22f;
        canvas.drawLine(x + w * 0.28f + dx, y, x + w * 0.10f, y + h, p);
        canvas.drawLine(x + w * 0.72f + dx, y, x + w * 0.54f, y + h, p);
        canvas.drawLine(x - w * 0.02f, y + h * 0.34f, x + w * 1.02f, y + h * 0.28f, p);
        canvas.drawLine(x - w * 0.10f, y + h * 0.70f, x + w * 0.94f, y + h * 0.64f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawPlus(Canvas canvas, float x, float y, float w, float h, int col) {
        p.setColor(col);
        float t = Math.min(w, h) * 0.20f;
        float cx = x + w / 2f, cy = y + h * 0.62f;
        float arm = w * 0.45f;
        canvas.drawRect(cx - arm, cy - t / 2, cx + arm, cy + t / 2, p);
        canvas.drawRect(cx - t / 2, cy - arm, cx + t / 2, cy + arm, p);
    }
}

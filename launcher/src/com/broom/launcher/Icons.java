package com.broom.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home-screen icon buttons, drawn in code (no bitmaps, crisp at 720p and 4K):
 *  - the Broom gear (Settings) at the top of the header,
 *  - a slim vertical rail of round icons on the left edge (Theme, Briefing, Panchang, Weather,
 *    Screensaver, Network) that floats over the grid and never moves it,
 *  - a themed tooltip bubble that slides next to whichever icon has D-pad focus.
 * Glyphs are vector paths in a 24 x 24 design box, coloured from the theme (text / accent / focus).
 */
final class Icons {
    static final int GEAR = 0, THEME = 1, BRIEF = 2, PANCHANG = 3, WEATHER = 4, DREAM = 5, NETWORK = 6;
    static final int NET_DOWN = -1, NET_OFF = -2;   // network glyph states besides 0..4 signal bars
    private static final String[] NAMES = {"Settings", "Theme", "Briefing", "Panchang", "Weather", "Screensaver", "Network"};

    private Icons() { }

    // ------------------------------------------------------------------ colours

    static int blend(int a, int b, float k) {
        if (k <= 0) return a;
        if (k >= 1) return b;
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * k) << 24) | ((int) (ar + (br - ar) * k) << 16)
                | ((int) (ag + (bg - ag) * k) << 8) | (int) (ab + (bb - ab) * k);
    }

    static int alpha(int c, float a) {
        return ((int) ((c >>> 24) * Math.max(0f, Math.min(1f, a))) << 24) | (c & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ tooltip text + network state

    /** Live tooltip text for an icon (computed when it gets focus). */
    static String tipText(Context c, int kind) {
        try {
            switch (kind) {
                case THEME: return "Theme: " + Theme.label(c);
                case BRIEF: return "Briefing: news, markets & scores";
                case PANCHANG: {
                    Panchang p = Panchang.now();
                    String tn = (p.tithiIdx == 14 || p.tithiIdx == 29) ? p.tithiName() : (p.shukla() ? "Shukla " : "Krishna ") + p.tithiName();
                    return "Panchang: " + tn + (p.festival != null ? "  ·  " + p.festival : "");
                }
                case WEATHER: {
                    Weather w = Weather.cached(c);
                    if (w == null) return "Weather";
                    return "Weather: " + Weather.deg(w.temp) + " " + Weather.label(w.code) + (w.city == null || w.city.isEmpty() ? "" : "  ·  " + w.city);
                }
                case DREAM: return "Screensaver";
                case NETWORK: return netText(c);
                default: return "Settings";
            }
        } catch (Exception e) { return NAMES[kind]; }
    }

    /** 0..4 signal bars when online, NET_DOWN when the last probe failed, NET_OFF with no network. */
    static int netState(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return NET_OFF;
            boolean wifi = ni.getType() == ConnectivityManager.TYPE_WIFI;
            NetMonitor.Sample s = lastSample(c);
            if (s != null && !s.ok) return NET_DOWN;
            if (!wifi) return 4;
            int rssi = s != null ? s.rssi : 0;
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            } catch (Exception ignored) { }
            return rssi == 0 ? 4 : Math.max(1, NetMonitor.bars(rssi));
        } catch (Exception e) { return 4; }
    }

    private static NetMonitor.Sample lastSample(Context c) {
        List<NetMonitor.Sample> ns = NetMonitor.samples(c);
        if (ns.isEmpty()) return null;
        NetMonitor.Sample s = ns.get(ns.size() - 1);
        return System.currentTimeMillis() - s.t < 65 * 60000L ? s : null;
    }

    private static String netText(Context c) {
        int st = netState(c);
        if (st == NET_OFF) return "Network: offline";
        if (st == NET_DOWN) return "Network: internet down";
        StringBuilder b = new StringBuilder("Network:");
        NetMonitor.Sample s = lastSample(c);
        if (s != null && s.ok) b.append(' ').append(s.lat).append(" ms");
        List<NetMonitor.Speed> sp = NetMonitor.speeds(c);
        if (!sp.isEmpty() && System.currentTimeMillis() - sp.get(sp.size() - 1).t < 24 * 3600000L && sp.get(sp.size() - 1).mbps > 0)
            b.append(b.length() > 8 ? "  ·  " : " ").append(String.format(java.util.Locale.US, "%.0f Mbps", sp.get(sp.size() - 1).mbps));
        if (b.length() == 8) b.append(" connected");
        return b.toString();
    }

    // ------------------------------------------------------------------ glyphs

    /** One vector glyph in a 24 x 24 box. f = focus amount (0..1) blends colours towards onFocus. */
    static final class Glyph extends Drawable {
        final int kind;
        final Theme t;
        float f, spin;
        int net = 4;
        private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG), p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path a = new Path(), b = new Path();
        private final RectF r = new RectF();

        Glyph(int kind, Theme t) {
            this.kind = kind;
            this.t = t;
            s.setStyle(Paint.Style.STROKE);
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.FILL);
            build();
        }

        private void build() {
            switch (kind) {
                case GEAR: {   // 8-tooth gear outline
                    float rb = 7.7f, ro = 10.5f;
                    RectF ob = new RectF(12 - rb, 12 - rb, 12 + rb, 12 + rb);
                    for (int i = 0; i < 8; i++) {
                        float c = i * 45f - 90f;
                        if (i == 0) moveTo(a, rb, c - 15);
                        lineTo(a, ro, c - 9);
                        lineTo(a, ro, c + 9);
                        lineTo(a, rb, c + 15);
                        a.arcTo(ob, c + 15, 15, false);
                    }
                    a.close();
                    // broom bristles (drawn upright, rotated when drawn)
                    b.moveTo(10.3f, 12.9f); b.lineTo(13.7f, 12.9f); b.lineTo(15.0f, 17.3f); b.lineTo(9.0f, 17.3f); b.close();
                    break;
                }
                case THEME: {   // artist's palette with a thumb notch
                    a.moveTo(12, 3);
                    a.arcTo(new RectF(3, 3, 21, 21), -90, -180, false);
                    a.cubicTo(12.83f, 21, 13.5f, 20.33f, 13.5f, 19.5f);
                    a.cubicTo(13.5f, 19.11f, 13.35f, 18.76f, 13.11f, 18.49f);
                    a.cubicTo(12.88f, 18.23f, 12.73f, 17.88f, 12.73f, 17.5f);
                    a.cubicTo(12.73f, 16.67f, 13.4f, 16, 14.23f, 16);
                    a.lineTo(16, 16);
                    a.cubicTo(18.76f, 16, 21, 13.76f, 21, 11);
                    a.cubicTo(21, 6.58f, 16.97f, 3, 12, 3);
                    a.close();
                    break;
                }
                case BRIEF: {   // folded newspaper: front page + back column
                    a.moveTo(5.1f, 4.5f); a.lineTo(15.4f, 4.5f); a.quadTo(17, 4.5f, 17, 6.1f); a.lineTo(17, 17.6f);
                    a.cubicTo(17, 18.7f, 17.9f, 19.5f, 19, 19.5f); a.lineTo(5.1f, 19.5f); a.quadTo(3.5f, 19.5f, 3.5f, 17.9f);
                    a.lineTo(3.5f, 6.1f); a.quadTo(3.5f, 4.5f, 5.1f, 4.5f); a.close();
                    b.moveTo(17, 8.5f); b.lineTo(19.6f, 8.5f); b.quadTo(20.5f, 8.5f, 20.5f, 9.4f); b.lineTo(20.5f, 18);
                    b.quadTo(20.5f, 19.5f, 19, 19.5f);
                    break;
                }
                case PANCHANG: {   // diya: lamp bowl + flame
                    a.moveTo(3.5f, 13.6f); a.lineTo(20.5f, 13.6f);
                    a.cubicTo(19.6f, 17.6f, 16.2f, 19.4f, 12, 19.4f);
                    a.cubicTo(7.8f, 19.4f, 4.4f, 17.6f, 3.5f, 13.6f); a.close();
                    b.moveTo(12, 3.2f);
                    b.cubicTo(14.9f, 6.4f, 15.4f, 8.9f, 14.4f, 10.6f);
                    b.cubicTo(13.8f, 11.6f, 12.9f, 12f, 12, 12f);
                    b.cubicTo(11.1f, 12f, 10.2f, 11.6f, 9.6f, 10.6f);
                    b.cubicTo(8.6f, 8.9f, 9.1f, 6.4f, 12, 3.2f); b.close();
                    break;
                }
                case WEATHER: {   // cloud (union of circles) in front of the sun
                    a.addCircle(8.0f, 15.4f, 3.7f, Path.Direction.CW);
                    union(a, circle(12.4f, 13.2f, 4.4f));
                    union(a, circle(16.9f, 16.0f, 3.3f));
                    Path base = new Path();
                    base.addRoundRect(new RectF(4.3f, 15.4f, 20.2f, 19.6f), 2.1f, 2.1f, Path.Direction.CW);
                    union(a, base);
                    break;
                }
                case DREAM: {   // crescent moon
                    a.addCircle(10.6f, 13.4f, 7.4f, Path.Direction.CW);
                    a.op(circle(15.0f, 9.0f, 6.0f), Path.Op.DIFFERENCE);
                    sparkle(b, 18.2f, 4.7f, 2.6f);
                    sparkle(b, 20.4f, 10.6f, 1.6f);
                    break;
                }
                default: break;   // NETWORK is drawn from arcs directly
            }
        }

        private static Path circle(float x, float y, float rad) {
            Path q = new Path();
            q.addCircle(x, y, rad, Path.Direction.CW);
            return q;
        }

        private static void union(Path into, Path other) { into.op(other, Path.Op.UNION); }

        private static void moveTo(Path q, float rad, float deg) {
            double d = Math.toRadians(deg);
            q.moveTo((float) (12 + rad * Math.cos(d)), (float) (12 + rad * Math.sin(d)));
        }

        private static void lineTo(Path q, float rad, float deg) {
            double d = Math.toRadians(deg);
            q.lineTo((float) (12 + rad * Math.cos(d)), (float) (12 + rad * Math.sin(d)));
        }

        /** Four-point star. */
        private static void sparkle(Path q, float cx, float cy, float k) {
            q.moveTo(cx, cy - k);
            q.quadTo(cx, cy, cx + k, cy);
            q.quadTo(cx, cy, cx, cy + k);
            q.quadTo(cx, cy, cx - k, cy);
            q.quadTo(cx, cy, cx, cy - k);
            q.close();
        }

        @Override public void draw(Canvas cv) {
            Rect bd = getBounds();
            float sc = Math.min(bd.width(), bd.height()) / 24f;
            if (sc <= 0) return;
            int save = cv.save();
            cv.translate(bd.exactCenterX() - 12 * sc, bd.exactCenterY() - 12 * sc);
            cv.scale(sc, sc);
            int ink = blend(t.text, t.onFocus, f), acc = blend(t.accent, t.onFocus, f);
            s.setStrokeWidth(1.7f);
            s.setColor(ink);
            switch (kind) {
                case GEAR: {
                    cv.save();
                    cv.rotate(spin, 12, 12);
                    cv.drawPath(a, s);
                    cv.restore();
                    cv.save();
                    cv.rotate(40, 12, 12);     // the broom, tilted as if sweeping
                    s.setStrokeWidth(1.5f);
                    cv.drawLine(12, 6.7f, 12, 11.1f, s);
                    p.setColor(acc);
                    r.set(10.6f, 11.0f, 13.4f, 12.6f);
                    cv.drawRoundRect(r, 0.6f, 0.6f, p);
                    cv.drawPath(b, p);
                    s.setStrokeWidth(0.5f);
                    s.setColor(blend(acc, 0xFF000000, 0.45f));
                    cv.drawLine(11.3f, 13.4f, 10.4f, 16.9f, s);
                    cv.drawLine(12f, 13.4f, 12f, 16.9f, s);
                    cv.drawLine(12.7f, 13.4f, 13.6f, 16.9f, s);
                    cv.restore();
                    break;
                }
                case THEME: {
                    cv.drawPath(a, s);
                    int[] cols = {t.accent, t.glowA, t.glowB, t.focus};
                    float[][] at = {{6.6f, 11.6f}, {9.4f, 7.4f}, {14.4f, 7.3f}, {17.4f, 11.2f}};
                    s.setStrokeWidth(0.7f);
                    for (int i = 0; i < 4; i++) {
                        p.setColor(cols[i] | 0xFF000000);
                        cv.drawCircle(at[i][0], at[i][1], 1.55f, p);
                        if (f > 0) { s.setColor(alpha(ink, f)); cv.drawCircle(at[i][0], at[i][1], 1.55f, s); }
                    }
                    break;
                }
                case BRIEF: {
                    cv.drawPath(a, s);
                    cv.drawPath(b, s);
                    p.setColor(acc);
                    r.set(6, 7, 14.5f, 10.2f);
                    cv.drawRoundRect(r, 0.6f, 0.6f, p);
                    s.setStrokeWidth(1.3f);
                    cv.drawLine(6.2f, 13.2f, 14.3f, 13.2f, s);
                    cv.drawLine(6.2f, 16.2f, 11.6f, 16.2f, s);
                    break;
                }
                case PANCHANG: {
                    p.setColor(alpha(acc, 0.45f));
                    cv.drawPath(a, p);
                    cv.drawPath(a, s);
                    s.setStrokeWidth(1.5f);
                    cv.drawLine(9.6f, 21.3f, 14.4f, 21.3f, s);
                    p.setColor(blend(0xFFFFB347, t.onFocus, f));
                    cv.drawPath(b, p);
                    p.setColor(0xFFFFF1C1);
                    r.set(11.1f, 8.4f, 12.9f, 11.5f);
                    cv.drawOval(r, p);
                    break;
                }
                case WEATHER: {
                    cv.save();
                    cv.clipPath(a, Region.Op.DIFFERENCE);
                    s.setColor(acc);
                    cv.drawCircle(16.2f, 8.6f, 3.2f, s);
                    s.setStrokeWidth(1.5f);
                    for (int deg = 180; deg <= 360; deg += 45) {
                        double d = Math.toRadians(deg);
                        float cx = (float) Math.cos(d), cy = (float) Math.sin(d);
                        cv.drawLine(16.2f + cx * 5.0f, 8.6f + cy * 5.0f, 16.2f + cx * 6.4f, 8.6f + cy * 6.4f, s);
                    }
                    cv.restore();
                    s.setStrokeWidth(1.7f);
                    s.setColor(ink);
                    cv.drawPath(a, s);
                    break;
                }
                case DREAM: {
                    p.setColor(alpha(acc, 0.35f));
                    cv.drawPath(a, p);
                    cv.drawPath(a, s);
                    p.setColor(acc);
                    cv.drawPath(b, p);
                    break;
                }
                case NETWORK: {
                    float cx = 12, cy = 18.6f;
                    boolean off = net == NET_OFF;
                    for (int k = 1; k <= 3; k++) {
                        float rad = 0.8f + 3.8f * k;
                        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                        boolean lit = !off && net != NET_DOWN ? net >= k + 1 : false;
                        s.setColor(lit ? ink : alpha(ink, 0.3f));
                        cv.drawArc(r, -135, 90, false, s);
                    }
                    p.setColor(off ? alpha(ink, 0.3f) : acc);
                    cv.drawCircle(cx, cy, 1.6f, p);
                    if (off) {
                        s.setColor(0xFFFF6B6B);
                        s.setStrokeWidth(1.9f);
                        cv.drawLine(4.5f, 4.5f, 19.5f, 19.5f, s);
                    } else if (net == NET_DOWN) {
                        p.setColor(0xFFFF5A5A);
                        cv.drawCircle(19.4f, 5.2f, 3.4f, p);
                        s.setColor(0xFFFFFFFF);
                        s.setStrokeWidth(1.3f);
                        cv.drawLine(19.4f, 3.4f, 19.4f, 5.5f, s);
                        p.setColor(0xFFFFFFFF);
                        cv.drawCircle(19.4f, 7.1f, 0.75f, p);
                    }
                    break;
                }
                default: break;
            }
            cv.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ round icon button

    /**
     * A round glass icon. On D-pad focus: the disc fills with the theme's focus colour, a soft halo
     * glows around it, it scales up a little (drawn, not laid out, so nothing around it moves) and
     * the tooltip slides in. The gear also turns a few teeth.
     */
    static final class Btn extends View {
        final Glyph g;
        final Theme t;
        final boolean big;
        private final Tip tip;
        private float f;
        private ValueAnimator fa, sa;
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG), lit = new Paint(Paint.ANTI_ALIAS_FLAG),
                rim = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int size, discR, glyph;

        /** big = the header gear (tooltip below); otherwise a rail icon (tooltip to the right). */
        Btn(Context c, Theme t, int kind, boolean big, Tip tip) {
            super(c);
            this.t = t;
            this.big = big;
            this.tip = tip;
            g = new Glyph(kind, t);
            size = Ui.dp(c, big ? 54 : 44);
            discR = Ui.dp(c, big ? 20 : 16);
            glyph = Ui.dp(c, big ? 28 : 21);
            if (kind == NETWORK) g.net = netState(c);
            setFocusable(true);
            setClickable(true);
            setSoundEffectsEnabled(false);
            setContentDescription(NAMES[kind]);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(size, size); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float cx = w / 2f, cy = h / 2f, outer = Math.min(w, h) / 2f;
            disc.setShader(new LinearGradient(0, cy - discR, 0, cy + discR,
                    new int[]{Ui.lighten(t.glassTop, 0.3f), t.glassTop, t.glassBottom}, null, Shader.TileMode.CLAMP));
            lit.setShader(new LinearGradient(0, cy - discR, 0, cy + discR,
                    new int[]{Ui.lighten(t.focus, 0.25f), t.focus, t.focus}, null, Shader.TileMode.CLAMP));
            int halo = alpha(t.focus | 0xFF000000, 0.7f);
            glow.setShader(new RadialGradient(cx, cy, outer, new int[]{halo, halo, halo & 0x00FFFFFF},
                    new float[]{0f, Math.min(0.99f, discR / outer), 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (f > 0.01f) {
                glow.setAlpha((int) (255 * f));
                c.drawCircle(cx, cy, Math.min(cx, cy), glow);
            }
            c.drawCircle(cx, cy, discR, disc);
            if (f > 0.01f) {
                lit.setAlpha((int) (255 * f));
                c.drawCircle(cx, cy, discR, lit);
            }
            rim.setColor(blend(0x59FFFFFF, 0xCCFFFFFF, f));
            c.drawCircle(cx, cy, discR - rim.getStrokeWidth() / 2f, rim);
            g.f = f;
            int h = glyph / 2;
            g.setBounds((int) cx - h, (int) cy - h, (int) cx - h + glyph, (int) cy - h + glyph);
            g.draw(c);
        }

        @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (fa != null) fa.cancel();
            fa = ValueAnimator.ofFloat(f, gain ? 1f : 0f);
            fa.setDuration(gain ? 150 : 110);
            fa.setInterpolator(new DecelerateInterpolator());
            fa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) { f = (Float) va.getAnimatedValue(); invalidate(); }
            });
            fa.start();
            float s = gain ? 1.12f : 1f;
            animate().scaleX(s).scaleY(s).setDuration(130).start();
            if (gain && g.kind == GEAR) {
                if (sa != null) sa.cancel();
                sa = ValueAnimator.ofFloat(g.spin, g.spin + 45f);
                sa.setDuration(520);
                sa.setInterpolator(new DecelerateInterpolator(1.6f));
                sa.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator va) { g.spin = (Float) va.getAnimatedValue() % 360f; invalidate(); }
                });
                sa.start();
            }
            if (gain) Sounds.tick(getContext());
            if (tip != null) { if (gain) showTip(); else tip.hide(this); }
        }

        @Override public void onWindowFocusChanged(boolean has) {
            super.onWindowFocusChanged(has);
            if (has && g.kind == NETWORK) {
                int n = netState(getContext());
                if (n != g.net) { g.net = n; invalidate(); }
            }
            if (tip == null || !isFocused()) return;
            if (has) showTip(); else tip.hide(this);
        }

        private void showTip() { tip.show(this, tipText(getContext(), g.kind), big ? Tip.BELOW : Tip.RIGHT); }
    }

    // ------------------------------------------------------------------ the side rail

    /**
     * Slim vertical glass rail pinned to the left edge. Reached with LEFT from the start of any row;
     * UP / DOWN move inside it and stop at the ends; RIGHT goes back to where you came from.
     * It is skipped by UP / DOWN searches from the grid, so up from the top row still reaches the gear.
     */
    static final class Rail extends LinearLayout {
        private View origin;
        private final ViewTreeObserver.OnGlobalFocusChangeListener fl = new ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override public void onGlobalFocusChanged(View o, View n) {
                if (n != null && o != null && mine(n) && !mine(o)) origin = o;
            }
        };
        private final View.OnKeyListener keys = new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                int i = indexOfChild(v);
                if (code == KeyEvent.KEYCODE_DPAD_UP) return i == 0;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) return i == getChildCount() - 1;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) return true;
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && origin != null && origin.isAttachedToWindow()
                        && origin.isShown() && origin.isFocusable()) return origin.requestFocus();
                return false;
            }
        };

        Rail(Context c, Theme t) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClipChildren(false);
            setClipToPadding(false);
            int pv = Ui.dp(c, 6), ph = Ui.dp(c, 3);
            setPadding(ph, pv, ph, pv);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{alpha(t.glassTop, 0.8f), alpha(t.glassBottom, 0.8f)});
            bg.setCornerRadius(Ui.dp(c, 26));
            bg.setStroke(Math.max(1, Ui.dp(c, 1)), 0x40FFFFFF);
            setBackground(bg);
        }

        void add(Btn b) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(b.size, b.size);
            if (getChildCount() > 0) lp.topMargin = Ui.dp(getContext(), 4);
            b.setOnKeyListener(keys);
            addView(b, lp);
        }

        private boolean mine(View v) {
            View x = v;
            while (x != null) {
                if (x == this) return true;
                ViewParent p = x.getParent();
                x = p instanceof View ? (View) p : null;
            }
            return false;
        }

        @Override public void addFocusables(ArrayList<View> views, int direction, int mode) {
            if (!hasFocus() && (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN)) return;
            super.addFocusables(views, direction, mode);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnGlobalFocusChangeListener(fl);
        }

        @Override protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnGlobalFocusChangeListener(fl);
            origin = null;
            super.onDetachedFromWindow();
        }
    }

    // ------------------------------------------------------------------ tooltip

    /** One shared label bubble, floating in the root frame (never part of the grid's layout). */
    static final class Tip {
        static final int RIGHT = 0, BELOW = 1;
        private final TextView tv;
        private final Bubble bubble;
        private View anchor;
        private int side, tries;

        Tip(Context c, Theme t) {
            tv = Ui.text(c, "", 13, t.text, t.titleFont);
            tv.setShadowLayer(8, 0, 1, t.textShadow());
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setFocusable(false);
            bubble = new Bubble(c, t);
            tv.setBackground(bubble);
            tv.setAlpha(0f);
            tv.setVisibility(View.INVISIBLE);
        }

        View view() { return tv; }

        void show(View a, String text, int side) {
            anchor = a;
            this.side = side;
            tries = 0;
            tv.setText(text);
            Context c = tv.getContext();
            int h = Ui.dp(c, 12), v = Ui.dp(c, 6), nub = bubble.nub, sh = bubble.shadow;
            if (side == RIGHT) tv.setPadding(h + nub, v, h, v + sh);
            else tv.setPadding(h, v + nub, h, v + sh);
            bubble.side = side;
            tv.removeCallbacks(place);
            tv.post(place);
        }

        void hide(View a) {
            if (anchor != a) return;
            anchor = null;
            tv.removeCallbacks(place);
            tv.animate().cancel();
            tv.animate().alpha(0f).setDuration(90).withEndAction(new Runnable() {
                @Override public void run() { if (anchor == null) tv.setVisibility(View.INVISIBLE); }
            }).start();
        }

        private final Runnable place = new Runnable() {
            @Override public void run() {
                View a = anchor;
                View parent = (View) tv.getParent();
                if (a == null || parent == null) return;
                if ((!a.isLaidOut() || a.getWidth() == 0) && tries++ < 10) { tv.postDelayed(this, 32); return; }
                Context c = tv.getContext();
                int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                tv.measure(unspec, unspec);
                int tw = tv.getMeasuredWidth(), th = tv.getMeasuredHeight();
                int[] al = new int[2], pl = new int[2];
                a.getLocationInWindow(al);
                parent.getLocationInWindow(pl);
                float aw = a.getWidth() * a.getScaleX(), ah = a.getHeight() * a.getScaleY();
                float ax = al[0] - pl[0], ay = al[1] - pl[1];
                float x, y, dx = 0, dy = 0;
                int m = Ui.dp(c, 8);
                if (side == RIGHT) {
                    x = ax + aw + Ui.dp(c, 2);
                    y = ay + ah / 2f - (th - bubble.shadow) / 2f;
                    dx = -Ui.dp(c, 8);
                } else {
                    x = ax + aw / 2f - tw / 2f;
                    x = Math.max(m, Math.min(parent.getWidth() - tw - m, x));
                    y = ay + ah - Ui.dp(c, 2);
                    dy = -Ui.dp(c, 6);
                    bubble.nubAt = ax + aw / 2f - x;
                }
                bubble.invalidateSelf();
                boolean showing = tv.getVisibility() == View.VISIBLE && tv.getAlpha() > 0.3f;
                tv.animate().cancel();
                tv.setVisibility(View.VISIBLE);
                if (showing && Math.abs(tv.getTranslationX() - x) < Ui.dp(c, 4)) {
                    // gliding between neighbouring rail icons
                    tv.animate().translationX(x).translationY(y).alpha(1f).setDuration(120)
                            .setInterpolator(new DecelerateInterpolator()).start();
                } else {
                    tv.setTranslationX(x + dx);
                    tv.setTranslationY(y + dy);
                    tv.setAlpha(0f);
                    tv.animate().translationX(x).translationY(y).alpha(1f).setDuration(150)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        };
    }

    /** Rounded glass bubble with a small pointer towards the icon and a soft drop shadow. */
    static final class Bubble extends Drawable {
        int side;
        float nubAt = -1;
        final int nub, shadow;
        private final float radius;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
                shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), tri = new Path();
        private final RectF body = new RectF();

        Bubble(Context c, Theme t) {
            nub = Ui.dp(c, 6);
            shadow = Ui.dp(c, 2);
            radius = Ui.dp(c, 10);
            int base = Ui.mix(t.glassTop, t.glassBottom, 0.55f);
            fill.setColor((base & 0x00FFFFFF) | 0xEB000000);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1, Ui.dp(c, 1.5f)));
            stroke.setColor(alpha(t.focus | 0xFF000000, 0.9f));
            shade.setColor(0x55000000);
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float sw = stroke.getStrokeWidth() / 2f;
            body.set(b.left + sw, b.top + sw, b.right - sw, b.bottom - shadow - sw);
            path.reset();
            tri.reset();
            if (side == Tip.RIGHT) {
                body.left += nub;
                float cy = body.centerY();
                tri.moveTo(body.left + radius / 2f, cy - nub);
                tri.lineTo(b.left + sw, cy);
                tri.lineTo(body.left + radius / 2f, cy + nub);
            } else {
                body.top += nub;
                float cx = nubAt < 0 ? body.centerX() : Math.max(body.left + radius + nub, Math.min(body.right - radius - nub, b.left + nubAt));
                tri.moveTo(cx - nub, body.top + radius / 2f);
                tri.lineTo(cx, b.top + sw);
                tri.lineTo(cx + nub, body.top + radius / 2f);
            }
            tri.close();
            path.addRoundRect(body, radius, radius, Path.Direction.CW);
            path.op(tri, Path.Op.UNION);
            c.save();
            c.translate(0, shadow);
            c.drawPath(path, shade);
            c.restore();
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

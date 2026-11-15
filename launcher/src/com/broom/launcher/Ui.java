package com.broom.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;

final class Ui {
    private static final HashMap<String, Typeface> FONTS = new HashMap<>();
    private static float density = 0;

    static int dp(Context c, float v) {
        if (density == 0) density = c.getResources().getDisplayMetrics().density;
        return Math.round(v * density);
    }

    static Typeface font(Context c, String name) {
        Typeface t = FONTS.get(name);
        if (t == null) {
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf"); }
            catch (Exception e) { t = Typeface.SANS_SERIF; }
            FONTS.put(name, t);
        }
        return t;
    }

    /** Raised, tinted frosted-glass panel: top sheen -> body gradient, bright rim, darker bottom edge. */
    static final class Glass {
        final android.graphics.drawable.LayerDrawable d;
        final GradientDrawable body;
        final int edge;
        Glass(android.graphics.drawable.LayerDrawable d, GradientDrawable body, int edge) { this.d = d; this.body = body; this.edge = edge; }
        int restWidth, restColor;   // a status ring that stays when not focused (e.g. active HDMI)
        void focus(Context c, boolean f, Theme t) {
            if (f) body.setStroke(dp(c, 3), t.focus);
            else body.setStroke(restWidth, restColor);
        }
        /** Tint the glass towards a status colour and keep a ring in that colour. */
        void status(Context c, int color, Theme t) {
            restWidth = dp(c, 2);
            restColor = color;
            body.setColors(new int[]{mix(lighten(t.glassTop, 0.3f), color, 0.35f), mix(t.glassTop, color, 0.28f), mix(t.glassBottom, color, 0.2f)});
            body.setStroke(restWidth, restColor);
        }
    }

    static Glass glass(Context c, Theme t, float radiusPx) {
        int edge = dp(c, 4);
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(0x8C000000);
        shadow.setCornerRadius(radiusPx);
        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lighten(t.glassTop, 0.3f), t.glassTop, t.glassBottom});
        body.setCornerRadius(radiusPx);
        GradientDrawable rim = new GradientDrawable();
        rim.setColor(0x00000000);
        rim.setCornerRadius(radiusPx);
        rim.setStroke(Math.max(1, dp(c, 1)), 0x73FFFFFF);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{shadow, body, rim});
        ld.setLayerInset(0, 0, edge, 0, 0);
        ld.setLayerInset(1, 0, 0, 0, edge);
        ld.setLayerInset(2, 0, 0, 0, edge);
        return new Glass(ld, body, edge);
    }

    /** Clip/outline matching the raised glass body (excludes the bottom edge). */
    static void glassOutline(View v, final float radiusPx, final int edge) {
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight() - edge, radiusPx);
            }
        });
    }

    static int mix(int a, int b, float k) {
        int aa = a >>> 24;
        int r = (int) (((a >> 16) & 0xFF) * (1 - k) + ((b >> 16) & 0xFF) * k);
        int g = (int) (((a >> 8) & 0xFF) * (1 - k) + ((b >> 8) & 0xFF) * k);
        int bl = (int) ((a & 0xFF) * (1 - k) + (b & 0xFF) * k);
        return (Math.max(aa, 0xC0) << 24) | (r << 16) | (g << 8) | bl;
    }

    static int lighten(int c, float k) {
        int a = c >>> 24, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * k); g += (int) ((255 - g) * k); b += (int) ((255 - b) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static TextView text(Context c, String s, float sp, int color, String font) {
        TextView tv = new TextView(c);
        tv.setShadowLayer(4f, 0f, 2f, 0x8C000000);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(font(c, font));
        tv.setIncludeFontPadding(false);
        return tv;
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** A focusable pill button that lights up in the accent colour on focus. */
    static TextView pill(final Context c, String s, final Theme th, View.OnClickListener click) {
        final TextView b = text(c, s, 13, th.text, th.bodyFont);
        b.setGravity(Gravity.CENTER);
        int h = dp(c, 16), v = dp(c, 8);
        b.setPadding(h, v, h, v);
        b.setFocusable(true);
        b.setClickable(true);
        b.setSoundEffectsEnabled(false);
        final Glass g = glass(c, th, dp(c, 22));
        final GradientDrawable bg = g.body;
        b.setBackground(g.d);
        b.setPadding(h, v, h, v + g.edge);
        final View.OnClickListener cl = click;
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.click(c); cl.onClick(v); }
        });
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean f) {
                if (f) bg.setColors(new int[]{lighten(th.focus, 0.25f), th.focus, th.focus});
                else bg.setColors(new int[]{lighten(th.glassTop, 0.3f), th.glassTop, th.glassBottom});
                b.setTextColor(f ? th.onFocus : th.text);
                if (f) b.setShadowLayer(0, 0, 0, 0); else b.setShadowLayer(4f, 0f, 2f, 0x8C000000);
                if (f) Sounds.tick(c);
                b.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
            }
        });
        return b;
    }

    static String mb(long bytes) {
        double m = bytes / 1048576.0;
        if (m >= 1024) return String.format(java.util.Locale.US, "%.1f GB", m / 1024);
        return String.format(java.util.Locale.US, "%.0f MB", m);
    }
}

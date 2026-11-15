package com.broom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;

import java.util.Calendar;

/** Two-weight clock: bold hours, accent colon, light minutes, small am/pm. */
final class BigClock {
    static final class FontSpan extends MetricAffectingSpan {
        final Typeface tf;
        FontSpan(Typeface tf) { this.tf = tf; }
        @Override public void updateDrawState(TextPaint p) { p.setTypeface(tf); }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(tf); }
    }

    static String[] parts(Context c) {
        Calendar now = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(c);
        String hh = String.valueOf(DateFormat.format(h24 ? "HH" : "h", now));
        String mm = String.valueOf(DateFormat.format("mm", now));
        String ap = h24 ? "" : (now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        return new String[]{hh, mm, ap};
    }

    /** For a TextView on the home screen. */
    static CharSequence spannable(Context c, Theme t) {
        String[] s = parts(c);
        boolean serif = !t.clockFont.startsWith("Poppins");
        String hf = serif ? t.clockFont : "Poppins-SemiBold", mf = serif ? t.clockFont : t.clockFont, cf = serif ? t.clockFont : "Poppins-Light";
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append(s[0]);
        b.setSpan(new FontSpan(Ui.font(c, hf)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int st = b.length();
        b.append(":");
        b.setSpan(new ForegroundColorSpan(t.accent), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new FontSpan(Ui.font(c, cf)), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        st = b.length();
        b.append(s[1]);
        b.setSpan(new FontSpan(Ui.font(c, mf)), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!s[2].isEmpty()) {
            st = b.length();
            b.append(" ").append(s[2]);
            b.setSpan(new RelativeSizeSpan(0.26f), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new FontSpan(Ui.font(c, "Poppins-Medium")), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(t.accent), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    /** Canvas version for screensavers. (cx, baseline) = centre of the clock. Returns its width. */
    static float draw(Context ctx, Canvas c, Theme t, float cx, float base, float size, int alpha) {
        String[] s = parts(ctx);
        Paint hp = paint(ctx, "Poppins-SemiBold", size), cp = paint(ctx, "Poppins-Light", size),
                mp = paint(ctx, "Poppins-Thin", size), ap = paint(ctx, "Poppins-Medium", size * 0.24f);
        float wh = hp.measureText(s[0]), wc = cp.measureText(":"), wm = mp.measureText(s[1]);
        float wa = s[2].isEmpty() ? 0 : ap.measureText(s[2]) + size * 0.08f;
        float total = wh + wc + wm + wa, x = cx - total / 2f;
        Shader g = new LinearGradient(0, base - size * 0.8f, 0, base, 0xFFFFFFFF, mix(t.accent, 0xFFFFFFFF), Shader.TileMode.CLAMP);
        for (Paint p : new Paint[]{hp, mp}) { p.setShader(g); p.setAlpha(alpha); p.setShadowLayer(size * 0.12f, 0, 0, (t.accent & 0xFFFFFF) | 0x66000000); }
        cp.setColor(t.accent); cp.setAlpha(alpha);
        ap.setColor(t.accent); ap.setAlpha(alpha);
        c.drawText(s[0], x, base, hp); x += wh;
        c.drawText(":", x, base - size * 0.04f, cp); x += wc;
        c.drawText(s[1], x, base, mp); x += wm;
        if (wa > 0) c.drawText(s[2], x + size * 0.08f, base - size * 0.5f, ap);
        return total;
    }

    static int mix(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2, g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2, bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static Paint paint(Context c, String font, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Ui.font(c, font));
        p.setTextSize(size);
        p.setColor(0xFFFFFFFF);
        return p;
    }
}

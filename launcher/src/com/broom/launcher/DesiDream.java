package com.broom.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Indian-language screensavers, in the screensaver language (Settings → Language: Gujarati, Hindi, Marathi or English).
 *  - "desi"    Desi Clock: time, weekday + Gregorian date, cached weather and a saying, with native digits and names.
 *  - "sayings" Sayings: one large doha / abhang / kahevat at a time, with its source and an English meaning.
 * Both are still text (redrawn on the minute / when the saying changes) with the screensaver's 10-minute burn-in nudge.
 * DreamView owns the layers; this class holds the drawing and the text.
 */
final class DesiDream {
    /** Text on screensavers changes only every 3 minutes (same name/value as DreamView's). */
    static final long INFO_EVERY = 3 * 60000L;

    private DesiDream() { }

    static boolean owns(String mode) { return "desi".equals(mode) || "sayings".equals(mode); }

    // =================================================================== names

    private static final String[][] WEEKDAYS = {   // Sunday first (Calendar.SUNDAY == 1)
            {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"},
            {"रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"},
            {"રવિવાર", "સોમવાર", "મંગળવાર", "બુધવાર", "ગુરુવાર", "શુક્રવાર", "શનિવાર"},
            {"रविवार", "सोमवार", "मंगळवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"}};
    private static final String[][] MONTHS = {
            {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"},
            {"जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"},
            {"જાન્યુઆરી", "ફેબ્રુઆરી", "માર્ચ", "એપ્રિલ", "મે", "જૂન", "જુલાઈ", "ઑગસ્ટ", "સપ્ટેમ્બર", "ઑક્ટોબર", "નવેમ્બર", "ડિસેમ્બર"},
            {"जानेवारी", "फेब्रुवारी", "मार्च", "एप्रिल", "मे", "जून", "जुलै", "ऑगस्ट", "सप्टेंबर", "ऑक्टोबर", "नोव्हेंबर", "डिसेंबर"}};
    /** Part of the day: morning (4-11), afternoon (12-15), evening (16-19), night. English uses AM / PM instead. */
    private static final String[][] PERIODS = {
            {"AM", "PM", "PM", "PM"},
            {"सुबह", "दोपहर", "शाम", "रात"},
            {"સવાર", "બપોર", "સાંજ", "રાત"},
            {"सकाळ", "दुपार", "संध्याकाळ", "रात्र"}};
    private static final String[] FEELS = {"Feels", "महसूस", "અનુભવ", "जाणवते"};
    private static final String[] HUMIDITY = {"Humidity", "नमी", "ભેજ", "आर्द्रता"};
    /** Weather.label() categories, in Lang.CODES order. */
    private static final String[][] SKY = {
            {"साफ़ आसमान", "ज़्यादातर साफ़", "आंशिक बादल", "घने बादल", "कोहरा / धुंध", "बूंदाबांदी", "बारिश", "जमने वाली बारिश",
                    "बर्फ़बारी", "बौछारें", "बर्फ़ की बौछारें", "गरज के साथ बारिश"},
            {"સ્વચ્છ આકાશ", "મોટે ભાગે સ્વચ્છ", "આંશિક વાદળછાયું", "વાદળછાયું", "ધુમ્મસ", "ઝરમર", "વરસાદ", "થીજતો વરસાદ",
                    "હિમવર્ષા", "ઝાપટાં", "હિમનાં ઝાપટાં", "ગાજવીજ સાથે વરસાદ"},
            {"निरभ्र आकाश", "बहुतांशी निरभ्र", "अंशतः ढगाळ", "ढगाळ", "धुके", "रिमझिम", "पाऊस", "गोठणारा पाऊस",
                    "हिमवर्षाव", "सरी", "हिमसरी", "मेघगर्जनेसह पाऊस"}};

    private static int li(String lang) { return Lang.index(lang); }

    static String weekday(String lang, Calendar cal) { return WEEKDAYS[li(lang)][cal.get(Calendar.DAY_OF_WEEK) - 1]; }

    /** "17 November" / "૧૭ નવેમ્બર" (Gregorian, native digits). */
    static String dayMonth(String lang, Calendar cal) {
        return Lang.digits(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), lang) + " " + MONTHS[li(lang)][cal.get(Calendar.MONTH)];
    }

    /** {"10:30", "સવાર"}; English 24-hour clock has no period. */
    static String[] time(Context c, String lang) {
        String[] p = BigClock.parts(c);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String per;
        if (Lang.EN.equals(lang)) per = p[2];
        else per = PERIODS[li(lang)][h >= 4 && h < 12 ? 0 : h >= 12 && h < 16 ? 1 : h >= 16 && h < 20 ? 2 : 3];
        return new String[]{Lang.digits(p[0] + ":" + p[1], lang), per};
    }

    static String sky(String lang, int code) {
        String en = Weather.label(code);
        int i = li(lang);
        if (i == 0) return en;
        String[] keys = {"Clear", "Mostly clear", "Partly cloudy", "Overcast", "Fog / haze", "Drizzle", "Rain", "Freezing rain",
                "Snow", "Showers", "Snow showers", "Thunderstorm"};
        for (int k = 0; k < keys.length; k++) if (keys[k].equals(en)) return SKY[i - 1][k];
        return en;
    }

    static String weatherLine(Weather wx, String lang) {
        int i = li(lang);
        return Weather.icon(wx.code, wx.isDay) + "  " + Lang.digits(Weather.deg(wx.temp), lang) + "   " + sky(lang, wx.code)
                + "   ·   " + FEELS[i] + " " + Lang.digits(Weather.deg(wx.feels), lang)
                + "   ·   " + HUMIDITY[i] + " " + Lang.digits(wx.humidity + "%", lang);
    }

    // =================================================================== sayings (assets/quotes.json)

    /** {k, t, m, by} for every entry; read once (small file). */
    private static List<String[]> all;

    private static synchronized List<String[]> all(Context c) {
        if (all != null) return all;
        List<String[]> out = new ArrayList<>();
        try (java.io.InputStream in = c.getAssets().open("quotes.json")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) bo.write(b, 0, n);
            org.json.JSONArray a = new org.json.JSONArray(new String(bo.toByteArray(), "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("k"), o.getString("t"), o.optString("m", ""), o.optString("by", "")});
            }
        } catch (Exception ignored) { }
        all = out;
        return out;
    }

    /**
     * Sayings for a language. Sayings style: the language's own (+ Sanskrit for Devanagari); English = every Indian-language
     * saying (shown with its English meaning). Desi Clock in English shows the English quotes instead.
     */
    static List<String[]> pool(Context c, String lang, boolean clock) {
        List<String[]> out = new ArrayList<>();
        for (String[] q : all(c)) {
            String k = q[0];
            boolean on;
            if (Lang.EN.equals(lang)) on = clock ? k.equals("quote") : (k.equals("hi") || k.equals("gu") || k.equals("mr") || k.equals("sa"));
            else on = k.equals(lang) || (k.equals("sa") && !Lang.GU.equals(lang));
            if (on) out.add(q);
        }
        return out;
    }

    /** Facts & Quotes keeps every fact and quote but only a random share of the (now many) regional sayings. */
    static boolean inFacts(String k, Random r) {
        return !(k.equals("hi") || k.equals("gu") || k.equals("mr")) || r.nextFloat() < 0.3f;
    }

    static String kindLabel(String k) {
        switch (k) {
            case "hi": return "हिंदी  ·  HINDI";
            case "gu": return "ગુજરાતી  ·  GUJARATI";
            case "mr": return "मराठी  ·  MARATHI";
            case "sa": return "संस्कृत  ·  SANSKRIT";
            default: return "QUOTE";
        }
    }

    // the Desi Clock's saying: same per-session order, changes every INFO_EVERY
    private static List<String[]> clockPool;
    private static String clockLang;

    private static String[] clockSaying(Context c, String lang) {
        if (clockPool == null || !lang.equals(clockLang)) {
            clockPool = pool(c, lang, true);
            Collections.shuffle(clockPool);
            clockLang = lang;
        }
        if (clockPool.isEmpty()) return null;
        return clockPool.get((int) ((System.currentTimeMillis() / INFO_EVERY) % clockPool.size()));
    }

    // =================================================================== drawing (UI thread only)

    private static final int GOLD = 0xFFFFC56B, CREAM = 0xFFFFF1DC;
    private static final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private static final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path leaf = new Path(), petal = new Path();
    private static Shader glow, vignette;
    private static int shaderW, shaderH;
    private static String shaderMode;

    /** Burn-in nudge used by the whole screensaver: a few pixels, changes only every 10 minutes. */
    static float[] nudge(int h) {
        int slot = (Calendar.getInstance().get(Calendar.MINUTE) / 10) % 4;
        return new float[]{(slot == 1 || slot == 2) ? h * 0.008f : 0, slot >= 2 ? h * 0.008f : 0};
    }

    /** Background for both styles. Returns ms until the next redraw (Desi Clock is static; Sayings turns a faint mandala). */
    static long background(Canvas c, Theme th, String mode, int w, int h, float t) {
        boolean sayings = "sayings".equals(mode);
        if (glow == null || w != shaderW || h != shaderH || !mode.equals(shaderMode)) {
            shaderW = w; shaderH = h; shaderMode = mode;
            glow = sayings
                    ? new RadialGradient(w / 2f, h * 0.55f, h * 0.95f, new int[]{0xFF2A0F2E, 0xFF120716, 0xFF000000}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP)
                    : new RadialGradient(w / 2f, h * 0.42f, h * 0.85f, new int[]{0xFF3B1408, 0xFF170704, 0xFF000000}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
            vignette = new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0x99000000, Shader.TileMode.CLAMP);
        }
        bp.setStyle(Paint.Style.FILL);
        bp.setShader(glow);
        c.drawRect(0, 0, w, h, bp);
        bp.setShader(vignette);
        c.drawRect(0, h * 0.7f, w, h, bp);
        bp.setShader(null);
        float[] n = nudge(h);
        if (sayings) {
            mandala(c, th, w / 2f + n[0], h * 0.55f + n[1], h * 0.62f, t);
            return 66;   // ~15 fps is plenty for a turn of a few degrees a second
        }
        toran(c, w, h, n[0], n[1]);
        return 60000;
    }

    /** A toran of mango leaves and marigolds along the top edge, kept dim. */
    private static void toran(Canvas c, int w, int h, float nx, float ny) {
        int count = 17;
        float step = w / (float) count, y0 = h * 0.028f + ny, sag = h * 0.018f;
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(Math.max(1.5f, h * 0.0025f));
        bp.setColor(0x66E8B04B);
        leaf.reset();
        leaf.moveTo(nx, y0);
        for (int i = 0; i < count; i++) leaf.quadTo(nx + step * (i + 0.5f), y0 + sag * 2, nx + step * (i + 1), y0);
        c.drawPath(leaf, bp);
        bp.setStyle(Paint.Style.FILL);
        float L = h * 0.07f, W = h * 0.026f;
        for (int i = 0; i < count; i++) {
            float x = nx + step * (i + 0.5f), y = y0 + sag;   // lowest point of each swag
            if (i % 2 == 0) {
                leaf.reset();
                leaf.moveTo(x, y);
                leaf.cubicTo(x + W, y + L * 0.3f, x + W * 0.5f, y + L * 0.8f, x, y + L);
                leaf.cubicTo(x - W * 0.5f, y + L * 0.8f, x - W, y + L * 0.3f, x, y);
                bp.setColor(0x8C3F7D3A);
                c.drawPath(leaf, bp);
                bp.setColor(0x5598C46B);
                c.drawRect(x - 0.6f, y, x + 0.6f, y + L * 0.9f, bp);
            } else {
                float r = h * 0.017f;
                bp.setColor(0x99E8902A);
                c.drawCircle(x, y + r * 1.2f, r, bp);
                bp.setColor(0x99F6C14B);
                c.drawCircle(x, y + r * 1.2f, r * 0.55f, bp);
            }
        }
    }

    private static void mandala(Canvas c, Theme th, float ox, float oy, float S, float t) {
        int[] cols = {GOLD, th.accent, 0xFFFF8A5B, 0xFFE8B04B, 0xFFB45CFF};
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(2f);
        for (int ring = 0; ring < 5; ring++) {
            int n = 8 + ring * 4;
            float r = S * (0.12f + ring * 0.16f), pl = S * (0.1f + ring * 0.035f);
            bp.setColor((cols[ring] & 0xFFFFFF) | ((46 - ring * 5) << 24));
            c.save();
            c.translate(ox, oy);
            c.rotate((ring % 2 == 0 ? 1 : -1) * t * (0.8f + ring * 0.3f));
            petal.reset();
            petal.moveTo(0, r);
            petal.quadTo(pl * 0.5f, r + pl * 0.5f, 0, r + pl);
            petal.quadTo(-pl * 0.5f, r + pl * 0.5f, 0, r);
            for (int k = 0; k < n; k++) { c.drawPath(petal, bp); c.rotate(360f / n); }
            c.drawCircle(0, 0, r - 3, bp);
            c.restore();
        }
        bp.setStyle(Paint.Style.FILL);
    }

    private static void font(Context ctx, String name, float size, int color) {
        tp.setShader(null);
        tp.setTypeface(Ui.font(ctx, name));
        tp.setFakeBoldText(false);
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setLetterSpacing(0f);
        tp.setTextAlign(Paint.Align.LEFT);   // callers set CENTER / RIGHT after this
        tp.setShadowLayer(size * 0.2f, 0, size * 0.04f, 0xAA000000);
    }

    /** Text layer for both styles (canvas already nudged by DreamView). */
    static void drawText(Context ctx, Canvas c, Theme th, String mode, int w, int h, Weather wx) {
        String lang = Lang.screensaver(ctx);
        if ("sayings".equals(mode)) corner(ctx, c, th, lang, w, h, wx);
        else clock(ctx, c, th, lang, w, h, wx);
        tp.clearShadowLayer();
    }

    private static void clock(Context ctx, Canvas c, Theme th, String lang, int w, int h, Weather wx) {
        Calendar cal = Calendar.getInstance();
        float cx = w / 2f;
        // time: bold hours, accent colon, light minutes, part of the day small on the right
        String[] tm = time(ctx, lang);
        boolean en = Lang.EN.equals(lang);
        float size = h * 0.2f, base = h * 0.4f;
        int colon = tm[0].indexOf(':');
        String hh = tm[0].substring(0, colon), mm = tm[0].substring(colon + 1);
        Shader g = new LinearGradient(0, base - size * 0.8f, 0, base, 0xFFFFFFFF, BigClock.mix(GOLD, 0xFFFFFFFF), Shader.TileMode.CLAMP);
        font(ctx, "Poppins-SemiBold", size, 0xFFFFFFFF);
        tp.setFakeBoldText(!en);   // native digits come from the system font: thicken the hours a little
        float wh = tp.measureText(hh);
        font(ctx, "Poppins-Light", size, 0xFFFFFFFF);
        float wc = tp.measureText(":"), wm = tp.measureText(mm);
        font(ctx, en ? "Poppins-Medium" : "Poppins-Regular", size * (en ? 0.24f : 0.2f), GOLD);
        float wp = tm[1].isEmpty() ? 0 : tp.measureText(tm[1]) + size * 0.1f;
        float x = cx - (wh + wc + wm + wp) / 2f;
        font(ctx, "Poppins-SemiBold", size, 0xFFFFFFFF);
        tp.setFakeBoldText(!en);
        tp.setShader(g);
        tp.setShadowLayer(size * 0.12f, 0, 0, 0x66E8902A);
        c.drawText(hh, x, base, tp);
        x += wh;
        font(ctx, "Poppins-Light", size, th.accent | 0xFF000000);
        tp.clearShadowLayer();
        c.drawText(":", x, base - size * 0.04f, tp);
        x += wc;
        font(ctx, "Poppins-Light", size, 0xFFFFFFFF);
        tp.setShader(g);
        tp.setShadowLayer(size * 0.12f, 0, 0, 0x66E8902A);
        c.drawText(mm, x, base, tp);
        x += wm;
        if (wp > 0) {
            font(ctx, en ? "Poppins-Medium" : "Poppins-Regular", size * (en ? 0.24f : 0.2f), GOLD);
            c.drawText(tm[1], x + size * 0.1f, base - size * 0.5f, tp);
        }
        // weekday (gold), date (cream)
        float y = base + h * 0.09f;
        String wd = weekday(lang, cal), dm = ",  " + dayMonth(lang, cal);
        font(ctx, "Poppins-Regular", h * 0.048f, GOLD);
        float a = tp.measureText(wd);
        font(ctx, "Poppins-Light", h * 0.048f, CREAM);
        float b = tp.measureText(dm);
        font(ctx, "Poppins-Regular", h * 0.048f, GOLD);
        c.drawText(wd, cx - (a + b) / 2f, y, tp);
        font(ctx, "Poppins-Light", h * 0.048f, CREAM);
        c.drawText(dm, cx - (a + b) / 2f + a, y, tp);
        // weather (cached; DreamView refreshes it)
        y += h * 0.075f;
        if (wx != null) {
            font(ctx, "Poppins-Light", h * 0.032f, 0xE6FFFFFF);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(weatherLine(wx, lang), cx, y, tp);
        }
        // saying
        y += h * 0.07f;
        divider(c, cx, y, w * 0.07f, h);
        String[] q = clockSaying(ctx, lang);
        if (q != null) {
            String text = en ? "“" + q[1] + "”" : q[1];
            float ts = h * (text.length() > 70 ? 0.032f : 0.038f);
            font(ctx, "Poppins-Light", ts, 0xF2FFFFFF);
            StaticLayout l = new StaticLayout(text, tp, (int) (w * 0.8f), Layout.Alignment.ALIGN_CENTER, 1.18f, 0, false);
            if (l.getLineCount() > 3) {
                font(ctx, "Poppins-Light", ts * 0.82f, 0xF2FFFFFF);
                l = new StaticLayout(text, tp, (int) (w * 0.84f), Layout.Alignment.ALIGN_CENTER, 1.12f, 0, false);
            }
            c.save();
            c.translate(cx - l.getWidth() / 2f, y + h * 0.03f);
            l.draw(c);
            c.restore();
            if (!q[3].isEmpty()) {
                font(ctx, "Poppins-Medium", h * 0.025f, (GOLD & 0xFFFFFF) | 0xCC000000);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText("— " + q[3], cx, Math.min(h * 0.965f, y + h * 0.03f + l.getHeight() + h * 0.04f), tp);
            }
        }
        tp.setTextAlign(Paint.Align.LEFT);
    }

    /** Small native clock, date and weather top-right (Sayings). */
    private static void corner(Context ctx, Canvas c, Theme th, String lang, int w, int h, Weather wx) {
        Calendar cal = Calendar.getInstance();
        float x = w * 0.95f;
        String[] tm = time(ctx, lang);
        font(ctx, "Poppins-Regular", h * 0.026f, GOLD);
        tp.setTextAlign(Paint.Align.RIGHT);
        float wp = tm[1].isEmpty() ? 0 : tp.measureText(tm[1]) + h * 0.012f;
        if (wp > 0) c.drawText(tm[1], x, h * 0.1f, tp);
        font(ctx, "Poppins-Light", h * 0.075f, 0xF2FFFFFF);
        tp.setTextAlign(Paint.Align.RIGHT);
        c.drawText(tm[0], x - wp, h * 0.13f, tp);
        font(ctx, "Poppins-Light", h * 0.026f, CREAM);
        tp.setTextAlign(Paint.Align.RIGHT);
        c.drawText(weekday(lang, cal) + ",  " + dayMonth(lang, cal), x, h * 0.18f, tp);
        if (wx != null) {
            font(ctx, "Poppins-Light", h * 0.024f, 0xCCFFFFFF);
            tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Weather.icon(wx.code, wx.isDay) + " " + Lang.digits(Weather.deg(wx.temp), lang) + "  ·  " + sky(lang, wx.code), x, h * 0.22f, tp);
        }
        tp.setTextAlign(Paint.Align.LEFT);
    }

    /** ——— ◆ ——— in soft gold. */
    private static void divider(Canvas c, float cx, float y, float half, int h) {
        bp.setShader(null);
        bp.setStyle(Paint.Style.FILL);
        bp.setColor(0x99E8B04B);
        float d = h * 0.008f, th = Math.max(1f, h * 0.0016f);
        c.drawRect(cx - half, y - th / 2, cx - d * 2.2f, y + th / 2, bp);
        c.drawRect(cx + d * 2.2f, y - th / 2, cx + half, y + th / 2, bp);
        leaf.reset();
        leaf.moveTo(cx, y - d); leaf.lineTo(cx + d, y); leaf.lineTo(cx, y + d); leaf.lineTo(cx - d, y); leaf.close();
        bp.setColor(0xDDFFC56B);
        c.drawPath(leaf, bp);
    }

    // =================================================================== Sayings layer

    /** One saying at a time, large and centred, with source and English meaning; fades only when it changes. */
    static final class SayingsView extends View {
        private final List<String[]> items;
        private int idx;
        private final Runnable next = new Runnable() {
            @Override public void run() {
                animate().alpha(0f).setDuration(900).withEndAction(new Runnable() {
                    @Override public void run() { idx = (idx + 1) % Math.max(1, items.size()); invalidate(); animate().alpha(1f).setDuration(900).start(); }
                }).start();
                postDelayed(this, INFO_EVERY);
            }
        };

        SayingsView(Context c) {
            super(c);
            items = pool(c, Lang.screensaver(c), false);
            Collections.shuffle(items);
            postDelayed(next, INFO_EVERY);
        }

        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(next); }

        @Override protected void onDraw(Canvas c) {
            if (items.isEmpty()) return;
            String[] q = items.get(idx % items.size());
            Context ctx = getContext();
            int w = getWidth(), h = getHeight();
            float[] n = nudge(h);
            float cx = w / 2f + n[0], maxW = w * 0.72f;
            int len = q[1].length();
            float ts = h * (len > 110 ? 0.05f : len > 60 ? 0.06f : 0.074f);
            font(ctx, "Poppins-Regular", ts, 0xFFFFFFFF);
            StaticLayout main = new StaticLayout(q[1], tp, (int) maxW, Layout.Alignment.ALIGN_CENTER, 1.22f, 0, false);
            StaticLayout mean = null;
            if (!q[2].isEmpty()) {
                font(ctx, "Poppins-Light", h * 0.03f, 0xD9FFFFFF);
                mean = new StaticLayout(q[2], tp, (int) (maxW * 0.9f), Layout.Alignment.ALIGN_CENTER, 1.12f, 0, false);
            }
            float block = h * 0.06f + main.getHeight() + h * 0.07f + (q[3].isEmpty() ? 0 : h * 0.05f) + (mean != null ? mean.getHeight() : 0);
            float y = Math.max(h * 0.2f, h * 0.56f - block / 2f) + n[1];
            // kind
            font(ctx, "Poppins-Medium", h * 0.024f, Ui.lighten(GOLD, 0.2f));
            tp.setLetterSpacing(0.15f);
            tp.setTextAlign(Paint.Align.CENTER);
            c.drawText(kindLabel(q[0]), cx, y, tp);
            tp.setLetterSpacing(0f);
            y += h * 0.04f;
            // saying
            font(ctx, "Poppins-Regular", ts, 0xFFFFFFFF);
            c.save();
            c.translate(cx - maxW / 2f, y);
            main.draw(c);
            c.restore();
            y += main.getHeight() + h * 0.04f;
            divider(c, cx, y, w * 0.08f, h);
            y += h * 0.055f;
            if (!q[3].isEmpty()) {
                font(ctx, "Poppins-Medium", h * 0.03f, GOLD);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText("— " + q[3], cx, y, tp);
                y += h * 0.03f;
            }
            if (mean != null) {
                font(ctx, "Poppins-Light", h * 0.03f, 0xD9FFFFFF);
                c.save();
                c.translate(cx - maxW * 0.45f, y);
                mean.draw(c);
                c.restore();
            }
            tp.setTextAlign(Paint.Align.LEFT);
            tp.clearShadowLayer();
        }
    }
}

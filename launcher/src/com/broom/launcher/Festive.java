package com.broom.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Festival-day decorations drawn over the home wallpaper, chosen from today's Panchang. */
final class Festive {
    static String override;   // for testing: "diwali", "navratri", "kite", "holi", "toran"

    /** Decoration kind for today's festival, or null on ordinary days. */
    static String kind(Panchang p) {
        if (override != null) return override.isEmpty() ? null : override;
        String f = p.festival;
        if (f == null || f.equals("Ekadashi") || f.equals("Purnima") || f.equals("Amavasya")) return null;
        String l = f.toLowerCase(java.util.Locale.US);
        if (l.contains("diwali") || l.contains("dhanteras") || l.contains("kali chaudas") || l.contains("bestu") || l.contains("bhai beej")
                || l.contains("labh pancham")) return "diwali";
        if (l.contains("navratri") || l.contains("dussehra") || l.contains("sharad")) return "navratri";
        if (l.contains("uttarayan") || l.contains("sankranti")) return "kite";
        if (l.contains("holi") || l.contains("dhuleti")) return "holi";
        if (l.contains("christmas")) return "christmas";
        if (l.contains("new year")) return "newyear";
        if (l.contains("gudi padwa")) return "gudi";
        if (l.contains("republic") || l.contains("independence")) return "tiranga";
        return "toran";
    }

    /** Greeting for the festival (shown instead of "Good morning"). */
    static String greeting(Panchang p) {
        String k = kind(p);
        if (k == null) return null;
        String f = p.festival == null ? "" : p.festival.replaceAll("^[^A-Za-z]+", "");
        if (f.isEmpty() || f.equals("Ekadashi") || f.equals("Purnima") || f.equals("Amavasya"))
            f = k.equals("diwali") ? "Diwali" : k.equals("navratri") ? "Navratri" : "Festival";
        switch (k) {
            case "diwali": return f.startsWith("Bestu") ? "Saal Mubarak · નૂતન વર્ષાભિનંદન" : "Happy " + f + " · શુભ દીપાવલી";
            case "kite": return "Happy Uttarayan · Kai Po Che!";
            case "holi": return "Happy Holi · હોળીની શુભેચ્છા";
            case "navratri": return "Happy " + f;
            case "christmas": return "Merry Christmas! 🎄";
            case "newyear": return f.contains("Eve") ? "Happy New Year's Eve 🎆" : "Happy New Year! 🎆";
            case "gudi": return "Happy Gudi Padwa 🚩";
            case "tiranga": return "Happy " + f + " · जय हिन्द";
            default: return f.isEmpty() ? null : "Happy " + f;
        }
    }

    static void draw(Canvas c, String kind, int w, int h) {
        if (kind == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(kind.hashCode());
        switch (kind) {
            case "diwali": diwali(c, p, w, h, r); break;
            case "navratri": confetti(c, p, w, h, r); break;
            case "kite": kites(c, p, w, h, r); break;
            case "holi": holi(c, p, w, h, r); break;
            case "christmas": christmas(c, p, w, h, r); break;
            case "newyear": fireworks(c, p, w, h, r); break;
            case "gudi": gudi(c, p, w, h); toran(c, p, w, h); break;
            case "tiranga": tiranga(c, p, w, h); break;
            default: toran(c, p, w, h); break;
        }
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int col, int a) {
        p.setShader(new RadialGradient(x, y, r, (col & 0xFFFFFF) | (a << 24), col & 0xFFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }

    private static void diwali(Canvas c, Paint p, int w, int h, Random r) {
        float y = h * 0.985f, s = h * 0.016f;
        for (float x = w * 0.03f; x < w; x += w * 0.06f) {
            glow(c, p, x, y - s * 2.2f, s * 5, 0xFFFFB347, 0x99);
            p.setColor(0xFFFFE08A);
            c.drawOval(new RectF(x - s * 0.45f, y - s * 3.0f, x + s * 0.45f, y - s * 1.3f), p);
            p.setColor(0xFFB5541C);
            c.drawArc(new RectF(x - s * 1.5f, y - s * 1.6f, x + s * 1.5f, y + s * 0.4f), 0, 180, true, p);
        }
        for (int i = 0; i < 40; i++) {        // sparkles
            float x = r.nextFloat() * w, yy = r.nextFloat() * h * 0.5f, z = 1 + r.nextFloat() * 2.5f;
            glow(c, p, x, yy, z * 4, 0xFFFFE6A8, 0x88);
        }
    }

    private static void confetti(Canvas c, Paint p, int w, int h, Random r) {
        int[] cols = {0xFFFF3D7F, 0xFFFFD23F, 0xFF3BCEAC, 0xFF0EAD69, 0xFF540D6E, 0xFFEE4266, 0xFF1E90FF};
        for (int i = 0; i < 160; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() < 0.5f ? r.nextFloat() * h * 0.12f : h - r.nextFloat() * h * 0.08f;
            p.setColor(cols[r.nextInt(cols.length)] & 0xDDFFFFFF);
            c.save();
            c.rotate(r.nextFloat() * 180, x, y);
            c.drawRect(x, y, x + h * 0.012f, y + h * 0.005f, p);
            c.restore();
        }
        // dandiya pairs in the corners
        p.setStrokeWidth(h * 0.008f);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 2; k++) {
            float bx = k == 0 ? w * 0.94f : w * 0.06f, by = h * 0.9f;
            p.setColor(k == 0 ? 0xFFFFD23F : 0xFFFF3D7F);
            c.drawLine(bx - h * 0.05f, by - h * 0.06f, bx + h * 0.05f, by + h * 0.04f, p);
            p.setColor(k == 0 ? 0xFF3BCEAC : 0xFF1E90FF);
            c.drawLine(bx + h * 0.05f, by - h * 0.06f, bx - h * 0.05f, by + h * 0.04f, p);
        }
    }

    private static void kites(Canvas c, Paint p, int w, int h, Random r) {
        int[] cols = {0xFFE63946, 0xFFFFB703, 0xFF219EBC, 0xFF8338EC, 0xFF06D6A0, 0xFFFB5607};
        for (int i = 0; i < 9; i++) {
            float x = w * (0.36f + r.nextFloat() * 0.3f), y = h * (0.05f + r.nextFloat() * 0.4f), s = h * (0.025f + r.nextFloat() * 0.025f);
            c.save();
            c.rotate(-20 + r.nextFloat() * 40, x, y);
            Path k = new Path();
            k.moveTo(x, y - s); k.lineTo(x + s * 0.8f, y); k.lineTo(x, y + s * 1.1f); k.lineTo(x - s * 0.8f, y); k.close();
            p.setColor(cols[i % cols.length]);
            c.drawPath(k, p);
            p.setColor(0x66FFFFFF);
            p.setStrokeWidth(1.5f);
            c.drawLine(x, y - s, x, y + s * 1.1f, p);
            c.drawLine(x - s * 0.8f, y, x + s * 0.8f, y, p);
            p.setColor(0x88FFFFFF);                      // string
            c.drawLine(x, y + s * 1.1f, x - w * 0.08f, y + h * 0.3f, p);
            c.restore();
        }
    }

    private static void holi(Canvas c, Paint p, int w, int h, Random r) {
        int[] cols = {0xFFFF006E, 0xFFFB5607, 0xFFFFBE0B, 0xFF3A86FF, 0xFF8338EC, 0xFF06D6A0};
        for (int i = 0; i < 14; i++) {
            float x = r.nextBoolean() ? r.nextFloat() * w * 0.25f : w - r.nextFloat() * w * 0.25f, y = h * (0.6f + r.nextFloat() * 0.4f);
            glow(c, p, x, y, h * (0.08f + r.nextFloat() * 0.12f), cols[i % cols.length], 0x77);
        }
        for (int i = 0; i < 6; i++) glow(c, p, w * r.nextFloat(), h * r.nextFloat() * 0.15f, h * 0.08f, cols[i], 0x55);
    }

    private static void christmas(Canvas c, Paint p, int w, int h, Random r) {
        for (int i = 0; i < 180; i++) {                 // snowfall
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = 1 + r.nextFloat() * 3;
            p.setColor(((90 + r.nextInt(140)) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, s, p);
        }
        float tx = w * 0.93f, base = h * 0.98f, th = h * 0.32f;   // tree, bottom-right
        p.setColor(0xFF1B5E20);
        for (int k = 0; k < 4; k++) {
            float top = base - th + k * th * 0.2f, half = th * (0.18f + k * 0.07f);
            Path t = new Path();
            t.moveTo(tx, top); t.lineTo(tx - half, top + th * 0.32f); t.lineTo(tx + half, top + th * 0.32f); t.close();
            c.drawPath(t, p);
        }
        p.setColor(0xFF5D4037);
        c.drawRect(tx - th * 0.03f, base - th * 0.08f, tx + th * 0.03f, base, p);
        int[] bulbs = {0xFFFF5252, 0xFFFFD740, 0xFF40C4FF, 0xFFFF80AB};
        for (int i = 0; i < 16; i++) {
            float bx = tx + (r.nextFloat() - 0.5f) * th * 0.45f, by = base - th * (0.15f + r.nextFloat() * 0.7f);
            glow(c, p, bx, by, h * 0.012f, bulbs[i % 4], 0xEE);
        }
        p.setColor(0xFFFFD740);
        star5(c, p, tx, base - th - h * 0.01f, h * 0.025f);
    }

    private static void star5(Canvas c, Paint p, float x, float y, float s) {
        Path st = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? s : s * 0.45f;
            if (i == 0) st.moveTo(x + (float) Math.cos(a) * rr, y + (float) Math.sin(a) * rr);
            else st.lineTo(x + (float) Math.cos(a) * rr, y + (float) Math.sin(a) * rr);
        }
        st.close();
        c.drawPath(st, p);
    }

    private static void fireworks(Canvas c, Paint p, int w, int h, Random r) {
        int[] cols = {0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB, 0xFFFFAB40};
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int b = 0; b < 6; b++) {
            float cx = w * (0.45f + r.nextFloat() * 0.5f), cy = h * (0.42f + r.nextFloat() * 0.16f), R = h * (0.05f + r.nextFloat() * 0.05f);
            int col = cols[b % cols.length];
            glow(c, p, cx, cy, R * 1.2f, col, 0x40);
            p.setStrokeWidth(2.2f);
            for (int k = 0; k < 18; k++) {
                double a = k * Math.PI * 2 / 18;
                p.setColor(col);
                c.drawLine(cx + (float) Math.cos(a) * R * 0.3f, cy + (float) Math.sin(a) * R * 0.3f,
                        cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, p);
                glow(c, p, cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, 4, 0xFFFFFFFF, 0xCC);
            }
        }
    }

    /** Gudi: a pole with a bright silk cloth, neem leaves, a garland and an upturned kalash on top. */
    private static void gudi(Canvas c, Paint p, int w, int h) {
        float x = w * 0.955f, base = h * 0.99f, top = h * 0.66f;
        p.setColor(0xFF8D6E63);
        c.drawRect(x - h * 0.006f, top, x + h * 0.006f, base, p);
        Path cloth = new Path();
        cloth.moveTo(x - h * 0.03f, top + h * 0.03f);
        cloth.quadTo(x - h * 0.09f, top + h * 0.15f, x - h * 0.05f, top + h * 0.26f);
        cloth.lineTo(x + h * 0.05f, top + h * 0.26f);
        cloth.quadTo(x + h * 0.09f, top + h * 0.15f, x + h * 0.03f, top + h * 0.03f);
        cloth.close();
        p.setColor(0xFFFFB300);
        c.drawPath(cloth, p);
        p.setColor(0xFFD81B60);
        c.drawRect(x - h * 0.05f, top + h * 0.23f, x + h * 0.05f, top + h * 0.26f, p);
        p.setColor(0xFF2E7D32);                          // neem leaves
        for (int i = -2; i <= 2; i++) c.drawOval(new RectF(x + i * h * 0.012f - h * 0.01f, top + h * 0.01f, x + i * h * 0.012f + h * 0.01f, top + h * 0.05f), p);
        p.setColor(0xFFFFA000);                          // marigold garland
        for (int i = -3; i <= 3; i++) c.drawCircle(x + i * h * 0.009f, top + h * 0.035f + Math.abs(i) * h * 0.004f, h * 0.007f, p);
        p.setColor(0xFFB0BEC5);                          // upturned kalash
        c.drawArc(new RectF(x - h * 0.03f, top - h * 0.045f, x + h * 0.03f, top + h * 0.015f), 180, 180, true, p);
        c.drawRect(x - h * 0.012f, top - h * 0.06f, x + h * 0.012f, top - h * 0.04f, p);
    }

    private static void tiranga(Canvas c, Paint p, int w, int h) {
        float y = h * 0.0f, band = h * 0.012f;
        p.setColor(0xFFFF9933); c.drawRect(0, y, w, y + band, p);
        p.setColor(0xFFFFFFFF); c.drawRect(0, y + band, w, y + band * 2, p);
        p.setColor(0xFF138808); c.drawRect(0, y + band * 2, w, y + band * 3, p);
        p.setColor(0xFFFF9933); c.drawRect(0, h - band * 3, w, h - band * 2, p);
        p.setColor(0xFFFFFFFF); c.drawRect(0, h - band * 2, w, h - band, p);
        p.setColor(0xFF138808); c.drawRect(0, h - band, w, h, p);
    }

    private static void toran(Canvas c, Paint p, int w, int h) {
        float y0 = 0, sag = h * 0.05f;
        int n = 26;
        for (int i = 0; i <= n; i++) {                // marigold string with a gentle sag
            float t = i / (float) n, x = w * t, y = y0 + sag * (float) Math.sin(Math.PI * ((t * 4) % 1)) + h * 0.012f;
            p.setColor(i % 2 == 0 ? 0xFFFF9F1C : 0xFFFFD23F);
            c.drawCircle(x, y, h * 0.014f, p);
            if (i % 2 == 0) {                          // mango leaves hanging below
                p.setColor(0xFF2D6A4F);
                Path leaf = new Path();
                leaf.moveTo(x, y + h * 0.012f);
                leaf.quadTo(x + h * 0.012f, y + h * 0.04f, x, y + h * 0.065f);
                leaf.quadTo(x - h * 0.012f, y + h * 0.04f, x, y + h * 0.012f);
                c.drawPath(leaf, p);
            }
        }
    }
}

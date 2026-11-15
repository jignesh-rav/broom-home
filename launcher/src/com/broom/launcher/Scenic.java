package com.broom.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Procedural scenes with depth (atmospheric perspective, bloom, soft volumetric clouds):
 *  Dawn - mountain sunrise with light rays and valley mist
 *  Day  - coastal sky, soft cumulus, sun glitter on the sea
 *  Dusk - city sunset over the bay with a "Queen's Necklace" of lights
 */
final class Scenic {
    static Bitmap render(String id, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        switch (id) {
            case "dawn": dawn(c, p, w, h); break;
            case "day": day(c, p, w, h); break;
            case "dusk": dusk(c, p, w, h); break;
            default: PackScenes.draw(id, c, p, w, h); break;
        }
        vignette(c, p, w, h);
        return b;
    }

    // ================================================================== Dawn
    private static void dawn(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.6f;
        p.setShader(new LinearGradient(0, 0, 0, hz, new int[]{0xFF141A3D, 0xFF3A3570, 0xFF8A5A90, 0xFFE07A80, 0xFFF9B184, 0xFFFFDCA6},
                new float[]{0, 0.22f, 0.45f, 0.68f, 0.86f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        stars(c, p, w, h * 0.3f, 90, new Random(7), 0.6f);
        float sx = w * 0.66f, sy = hz;
        // light rays
        rays(c, p, sx, sy, h * 1.1f, 16, 0xFFFFE6B8, 0x1C, new Random(3));
        bloom(c, p, sx, sy, h, new int[]{0xFFFFD39A, 0xFFFFE9C4, 0xFFFFF8E8}, new float[]{0.9f, 0.3f, 0.07f}, new int[]{0x77, 0xAA, 0xFF});
        // thin glowing cloud streaks near the horizon
        Random r = new Random(11);
        for (int i = 0; i < 9; i++) {
            float cx = w * r.nextFloat(), cy = h * (0.32f + r.nextFloat() * 0.22f);
            streak(c, p, cx, cy, w * (0.12f + r.nextFloat() * 0.18f), h * 0.012f, i % 2 == 0 ? 0xFFFFB8A8 : 0xFFF7C9E0, 0x70);
        }
        // mountain ranges with atmospheric perspective + mist between them
        int[] cols = {0xFFC79BB4, 0xFFA97EA6, 0xFF835F92, 0xFF5D4479, 0xFF3D2C5C, 0xFF22183A};
        float[] base = {0.6f, 0.66f, 0.72f, 0.79f, 0.86f, 0.94f};
        Random m = new Random(5);
        for (int i = 0; i < cols.length; i++) {
            ridge(c, p, w, h, base[i], h * (0.09f - i * 0.009f), 2.5f + i * 0.6f, cols[i], m);
            mist(c, p, w, h * base[i] - h * 0.02f, h * 0.06f, 0x44FFE9D6);
        }
        lake(c, p, w, h);
    }

    private static void lake(Canvas c, Paint p, int w, int h) {
        // faint water sheen at the very bottom catching the sunrise
        p.setShader(new LinearGradient(0, h * 0.95f, 0, h, 0x00FFD8A8, 0x33FFD8A8, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.95f, w, h, p);
        p.setShader(null);
    }

    // ================================================================== Day
    private static void day(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.68f;
        p.setShader(new LinearGradient(0, 0, 0, hz, new int[]{0xFF06386F, 0xFF0F5BA8, 0xFF3A8FD6, 0xFF8CC6F0, 0xFFD6EEFC},
                new float[]{0, 0.25f, 0.55f, 0.85f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        float sx = w * 0.58f, sy = h * 0.4f;
        bloom(c, p, sx, sy, h, new int[]{0xFFFFF4CC, 0xFFFFFFF0, 0xFFFFFFFF}, new float[]{0.7f, 0.2f, 0.045f}, new int[]{0x55, 0x99, 0xFF});
        // lens flare dots along the line from the sun through the centre
        float lx = w / 2f - sx, ly = h / 2f - sy;
        float[] at = {0.35f, 0.6f, 0.85f, 1.25f};
        float[] rs = {0.03f, 0.018f, 0.045f, 0.025f};
        for (int i = 0; i < at.length; i++) glow(c, p, sx + lx * at[i], sy + ly * at[i], h * rs[i], i % 2 == 0 ? 0xFFBFE6FF : 0xFFFFF0C8, 0x30);
        // high wispy cirrus
        Random r = new Random(17);
        for (int i = 0; i < 7; i++)
            streak(c, p, w * r.nextFloat(), h * (0.12f + r.nextFloat() * 0.2f), w * (0.1f + r.nextFloat() * 0.15f), h * 0.008f, 0xFFFFFFFF, 0x38);
        // soft cumulus band above the horizon
        for (int i = 0; i < 6; i++) {
            float cx = w * (0.06f + i * 0.18f + r.nextFloat() * 0.05f), cy = h * (0.5f + r.nextFloat() * 0.1f);
            cumulus(c, p, cx, cy, h * (0.05f + r.nextFloat() * 0.035f), r, 0xFFFFFFFF, 0xFF7FA6C8);
        }
        // sea
        p.setShader(new LinearGradient(0, hz, 0, h, new int[]{0xFF2B7CC4, 0xFF0F4F8E, 0xFF06294F}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        // distant island
        Path is = new Path();
        is.moveTo(w * 0.05f, hz + 1);
        is.quadTo(w * 0.12f, hz - h * 0.035f, w * 0.2f, hz - h * 0.012f);
        is.quadTo(w * 0.26f, hz - h * 0.025f, w * 0.31f, hz + 1);
        is.close();
        p.setColor(0xFF2E5E78);
        c.drawPath(is, p);
        // waves + sun glitter
        Random g = new Random(23);
        for (int i = 0; i < 220; i++) {
            float y = hz + (float) Math.pow(g.nextFloat(), 1.6) * (h - hz);
            float depth = (y - hz) / (h - hz);
            float x = g.nextFloat() * w, len = w * (0.01f + depth * 0.04f);
            float near = 1f - Math.min(1f, Math.abs(x - sx) / (w * 0.25f));
            int a = (int) (25 + near * 160 * (1 - depth * 0.5f));
            p.setColor((a << 24) | (near > 0.3f ? 0xFFF6D8 : 0xBFE3FF));
            c.drawRect(x, y, x + len, y + 1 + depth * 2, p);
        }
    }

    // ================================================================== Dusk
    static void dusk(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.68f;
        p.setShader(new LinearGradient(0, 0, 0, hz, new int[]{0xFF0E0824, 0xFF2E1650, 0xFF702A6A, 0xFFC4435F, 0xFFF0784F, 0xFFFFB46A},
                new float[]{0, 0.22f, 0.45f, 0.68f, 0.86f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        stars(c, p, w, h * 0.28f, 80, new Random(4), 0.8f);
        float sx = w * 0.24f;
        bloom(c, p, sx, hz, h, new int[]{0xFFFF9E5E, 0xFFFFC27E, 0xFFFFE7B8}, new float[]{0.95f, 0.32f, 0.075f}, new int[]{0x88, 0xAA, 0xFF});
        // clouds lit from below: warm undersides, violet tops
        Random r = new Random(31);
        for (int i = 0; i < 10; i++) {
            float cx = w * r.nextFloat(), cy = h * (0.3f + r.nextFloat() * 0.25f), len = w * (0.12f + r.nextFloat() * 0.2f);
            streak(c, p, cx, cy - h * 0.008f, len, h * 0.016f, 0xFF5A2A6E, 0x88);
            streak(c, p, cx, cy + h * 0.006f, len * 0.9f, h * 0.01f, i % 2 == 0 ? 0xFFFF9A7A : 0xFFFFC08A, 0x99);
        }
        // sea
        p.setShader(new LinearGradient(0, hz, 0, h, new int[]{0xFF6A2C55, 0xFF2A1236, 0xFF0C0614}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        // far skyline (hazy) then near skyline (dark) on the right
        skyline(c, p, w, h, hz, w * 0.38f, 0.04f, 0.13f, 0xFF7A3A66, 0x00000000, new Random(8));
        skyline(c, p, w, h, hz, w * 0.45f, 0.06f, 0.22f, 0xFF1C0C24, 0xDDFFD27A, new Random(21));
        // reflections: sun column + city light streaks
        Random g = new Random(41);
        for (int i = 0; i < 26; i++) {
            float y = hz + h * 0.008f + i * h * 0.011f, half = h * (0.1f - i * 0.0033f);
            p.setColor((Math.max(15, 170 - i * 6) << 24) | 0xFFC98A);
            c.drawRect(sx - half + g.nextFloat() * 6, y, sx + half - g.nextFloat() * 6, y + h * 0.003f, p);
        }
        for (int i = 0; i < 60; i++) {
            float x = w * (0.47f + g.nextFloat() * 0.53f), y = hz + h * 0.01f + g.nextFloat() * h * 0.1f;
            p.setColor(((40 + g.nextInt(70)) << 24) | 0xFFD27A);
            c.drawRect(x, y, x + 2, y + h * (0.01f + g.nextFloat() * 0.02f), p);
        }
        // the "Queen's Necklace": a curve of glowing street lights around the bay
        float x0 = w * 0.02f, y0 = h * 0.97f, x1 = w * 0.47f, y1 = hz + h * 0.012f, cx = w * 0.12f, cy = hz + h * 0.06f;
        for (int i = 0; i <= 70; i++) {
            float t = i / 70f, it = 1 - t;
            float x = it * it * x0 + 2 * it * t * cx + t * t * x1, y = it * it * y0 + 2 * it * t * cy + t * t * y1;
            float s = h * (0.006f * (1 - t) + 0.0018f);
            glow(c, p, x, y, s * 4, 0xFFFFD27A, 0x55);
            p.setShader(null);
            p.setColor(0xFFFFF0C8);
            c.drawCircle(x, y, s * 0.7f, p);
        }
    }

    private static void skyline(Canvas c, Paint p, int w, int h, float hz, float startX, float minH, float maxH,
                                int col, int windows, Random b) {
        float x = startX;
        while (x < w) {
            float bw = w * (0.016f + b.nextFloat() * 0.03f), bh = h * (minH + b.nextFloat() * (maxH - minH));
            p.setShader(null);
            p.setColor(col);
            c.drawRect(x, hz - bh, x + bw, hz + 1, p);
            if (b.nextInt(4) == 0) {
                c.drawRect(x + bw * 0.46f, hz - bh - h * 0.03f, x + bw * 0.54f, hz - bh, p);
                if (windows != 0) { p.setColor(0xFFFF4D4D); c.drawCircle(x + bw * 0.5f, hz - bh - h * 0.03f, 2f, p); }
            }
            if (windows != 0) {
                p.setColor(windows);
                for (float wy = hz - bh + h * 0.012f; wy < hz - h * 0.01f; wy += h * 0.017f)
                    for (float wx = x + bw * 0.16f; wx < x + bw * 0.86f; wx += bw * 0.26f)
                        if (b.nextInt(4) == 0) c.drawRect(wx, wy, wx + bw * 0.1f, wy + h * 0.006f, p);
            }
            x += bw + w * 0.0015f;
        }
    }

    // ================================================================== helpers
    static void stars(Canvas c, Paint p, int w, float maxY, int n, Random r, float strength) {
        p.setShader(null);
        for (int i = 0; i < n; i++) {
            float y = r.nextFloat() * maxY, fade = 1 - y / maxY;
            p.setColor(((int) ((40 + r.nextInt(160)) * fade * strength) << 24) | 0xFFFFFF);
            c.drawCircle(r.nextFloat() * w, y, 0.6f + r.nextFloat() * 1.1f, p);
        }
    }

    static void bloom(Canvas c, Paint p, float x, float y, int h, int[] cols, float[] radii, int[] alphas) {
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        for (int i = 0; i < cols.length; i++) glow(c, p, x, y, h * radii[i], cols[i], alphas[i]);
        p.setXfermode(null);
        p.setShader(null);
        p.setColor(cols[cols.length - 1]);
        c.drawCircle(x, y, h * radii[radii.length - 1] * 0.75f, p);
    }

    static void glow(Canvas c, Paint p, float x, float y, float r, int col, int alpha) {
        p.setShader(new RadialGradient(x, y, Math.max(1, r), new int[]{(col & 0xFFFFFF) | (alpha << 24), (col & 0xFFFFFF) | ((alpha / 3) << 24), col & 0xFFFFFF},
                new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
    }

    static void rays(Canvas c, Paint p, float x, float y, float len, int n, int col, int alpha, Random r) {
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        p.setShader(new RadialGradient(x, y, len, (col & 0xFFFFFF) | (alpha << 24), col & 0xFFFFFF, Shader.TileMode.CLAMP));
        for (int i = 0; i < n; i++) {
            double a = Math.PI + (i + 0.5) * Math.PI / n + (r.nextFloat() - 0.5) * 0.08;
            double half = 0.025 + r.nextFloat() * 0.035;
            Path t = new Path();
            t.moveTo(x, y);
            t.lineTo(x + (float) (Math.cos(a - half) * len), y + (float) (Math.sin(a - half) * len));
            t.lineTo(x + (float) (Math.cos(a + half) * len), y + (float) (Math.sin(a + half) * len));
            t.close();
            if (i % 2 == 0) c.drawPath(t, p);
        }
        p.setShader(null);
        p.setXfermode(null);
    }

    /** Long soft horizontal cloud streak. */
    static void streak(Canvas c, Paint p, float x, float y, float len, float thick, int col, int alpha) {
        c.save();
        c.translate(x, y);
        c.scale(len / thick, 1f);
        glow(c, p, 0, 0, thick, col, alpha);
        c.restore();
    }

    /** Volumetric-looking cumulus: many soft puffs, shaded underside, bright top. */
    static void cumulus(Canvas c, Paint p, float cx, float cy, float s, Random r, int lit, int shade) {
        int n = 16;
        float[] px = new float[n], py = new float[n], pr = new float[n];
        for (int i = 0; i < n; i++) {
            float u = (r.nextFloat() - 0.5f) * 2;
            px[i] = cx + u * s * 2.2f;
            py[i] = cy - (1 - Math.abs(u)) * s * (0.4f + r.nextFloat() * 0.8f);
            pr[i] = s * (0.5f + r.nextFloat() * 0.6f) * (1.1f - Math.abs(u) * 0.4f);
        }
        for (int i = 0; i < n; i++) glow(c, p, px[i], py[i] + pr[i] * 0.35f, pr[i] * 1.25f, shade, 0x55);
        for (int i = 0; i < n; i++) glow(c, p, px[i], py[i], pr[i] * 1.15f, lit, 0xE0);
        for (int i = 0; i < n; i += 3) glow(c, p, px[i], py[i] - pr[i] * 0.3f, pr[i] * 0.7f, 0xFFFFFFFF, 0x90);
    }

    /** Smooth fractal mountain ridge. */
    static void ridge(Canvas c, Paint p, int w, int h, float base, float amp, float freq, int col, Random r) {
        Path path = new Path();
        path.moveTo(0, h);
        float[] ph = {r.nextFloat() * 6, r.nextFloat() * 6, r.nextFloat() * 6, r.nextFloat() * 6};
        for (int x = 0; x <= w; x += 4) {
            float u = x / (float) w;
            double n = Math.sin(u * freq * 3 + ph[0]) + 0.5 * Math.sin(u * freq * 7 + ph[1])
                    + 0.25 * Math.sin(u * freq * 15 + ph[2]) + 0.12 * Math.sin(u * freq * 31 + ph[3]);
            path.lineTo(x, (float) (h * base - amp * n));
        }
        path.lineTo(w, h);
        path.close();
        p.setShader(null);
        p.setColor(col);
        c.drawPath(path, p);
    }

    static void mist(Canvas c, Paint p, int w, float y, float thick, int col) {
        p.setShader(new LinearGradient(0, y - thick, 0, y + thick, new int[]{col & 0x00FFFFFF, col, col & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, y - thick, w, y + thick, p);
        p.setShader(null);
    }

    static void vignette(Canvas c, Paint p, int w, int h) {
        p.setShader(new RadialGradient(w / 2f, h * 0.45f, (float) Math.hypot(w, h) * 0.62f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // gentle top-left darkening behind the clock for readability
        p.setShader(new RadialGradient(w * 0.15f, h * 0.12f, h * 0.55f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }
}

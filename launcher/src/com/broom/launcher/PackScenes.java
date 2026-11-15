package com.broom.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Procedural scenes for the theme packs. Everything is drawn once into a cached bitmap. */
final class PackScenes {
    static void draw(String id, Canvas c, Paint p, int w, int h) {
        switch (id) {
            case "kerala": kerala(c, p, w, h); break;
            case "rajasthan": rajasthan(c, p, w, h); break;
            case "himalaya": himalaya(c, p, w, h); break;
            case "temple": temple(c, p, w, h); break;
            case "warli": warli(c, p, w, h); break;
            case "arctic": arctic(c, p, w, h); break;
            case "alpine": alpine(c, p, w, h); break;
            case "canyon": canyon(c, p, w, h); break;
            case "noir": noir(c, p, w, h); break;
            case "neon": neon(c, p, w, h); break;
            case "bollywood": bollywood(c, p, w, h); break;
            default: Scenic.dusk(c, p, w, h);
        }
    }

    private static void sky(Canvas c, Paint p, int w, float bottom, int[] cols, float[] pos) {
        p.setShader(new LinearGradient(0, 0, 0, bottom, cols, pos, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, c.getHeight(), p);   // gradient clamps: fills the whole frame
        p.setShader(null);
    }

    // ================================================================ India
    private static void kerala(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.6f;
        sky(c, p, w, hz, new int[]{0xFF123C4A, 0xFF2F6B6B, 0xFFB9A86A, 0xFFF6D58E}, new float[]{0, 0.4f, 0.8f, 1});
        Scenic.bloom(c, p, w * 0.7f, hz - h * 0.04f, h, new int[]{0xFFFFD27A, 0xFFFFE6A8, 0xFFFFF4D6}, new float[]{0.7f, 0.22f, 0.05f}, new int[]{0x66, 0x99, 0xFF});
        // far tree line
        Scenic.ridge(c, p, w, h, 0.6f, h * 0.012f, 18f, 0xFF173A30, new Random(3));
        // water with gradient + reflections
        p.setShader(new LinearGradient(0, hz, 0, h, new int[]{0xFF6E8F72, 0xFF2C5A55, 0xFF0B2A2A}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        Random r = new Random(9);
        for (int i = 0; i < 120; i++) {
            float y = hz + r.nextFloat() * (h - hz), x = r.nextFloat() * w;
            p.setColor(((20 + r.nextInt(50)) << 24) | 0xFFE6B0);
            c.drawRect(x, y, x + w * (0.01f + r.nextFloat() * 0.03f), y + 1.5f, p);
        }
        // palms (and their reflections)
        float[] px = {0.04f, 0.13f, 0.86f, 0.95f};
        float[] ph = {0.42f, 0.36f, 0.4f, 0.32f};
        for (int i = 0; i < px.length; i++) {
            palm(c, p, w * px[i], hz + h * 0.02f, h * ph[i], (i % 2 == 0 ? 1 : -1) * 0.08f, 0xFF0A1F1A, false);
            palm(c, p, w * px[i], hz + h * 0.02f, h * ph[i] * 0.6f, (i % 2 == 0 ? 1 : -1) * 0.08f, 0x440A1F1A, true);
        }
        // houseboat (kettuvallam)
        float bx = w * 0.42f, by = hz + h * 0.05f, bw = w * 0.16f;
        Path hull = new Path();
        hull.moveTo(bx - bw * 0.55f, by - h * 0.02f);
        hull.quadTo(bx, by + h * 0.035f, bx + bw * 0.55f, by - h * 0.025f);
        hull.lineTo(bx + bw * 0.45f, by);
        hull.lineTo(bx - bw * 0.45f, by);
        hull.close();
        p.setColor(0xFF1A120C);
        c.drawPath(hull, p);
        RectF roof = new RectF(bx - bw * 0.35f, by - h * 0.07f, bx + bw * 0.35f, by - h * 0.01f);
        c.drawRoundRect(roof, h * 0.03f, h * 0.03f, p);
        p.setColor(0xCCFFC46B);
        for (int k = 0; k < 4; k++) c.drawRect(bx - bw * 0.25f + k * bw * 0.14f, by - h * 0.04f, bx - bw * 0.2f + k * bw * 0.14f, by - h * 0.025f, p);
    }

    private static void palm(Canvas c, Paint p, float x, float base, float hgt, float lean, int col, boolean flip) {
        c.save();
        if (flip) { c.translate(0, base); c.scale(1, -1); c.translate(0, -base); }
        p.setShader(null);
        p.setColor(col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(hgt * 0.045f);
        float tx = x + hgt * lean * 4, ty = base - hgt;
        Path trunk = new Path();
        trunk.moveTo(x, base);
        trunk.quadTo(x + hgt * lean * 0.5f, base - hgt * 0.6f, tx, ty);
        c.drawPath(trunk, p);
        p.setStrokeWidth(hgt * 0.022f);
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * (0.05 + i * 0.11) + Math.PI;
            float len = hgt * (0.38f + (i % 3) * 0.06f);
            float ex = tx + (float) Math.cos(a) * len, ey = ty + (float) Math.sin(a) * len * 0.5f + len * 0.35f;
            Path fr = new Path();
            fr.moveTo(tx, ty);
            fr.quadTo((tx + ex) / 2, ty - len * 0.25f, ex, ey);
            c.drawPath(fr, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static void rajasthan(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.6f;
        sky(c, p, w, hz, new int[]{0xFF2A1240, 0xFF6B2A5A, 0xFFD2564A, 0xFFFF9A4A, 0xFFFFC777}, new float[]{0, 0.3f, 0.6f, 0.85f, 1});
        Scenic.stars(c, p, w, h * 0.25f, 60, new Random(2), 0.7f);
        Scenic.bloom(c, p, w * 0.22f, hz - h * 0.02f, h, new int[]{0xFFFF9E5E, 0xFFFFC27E, 0xFFFFE7B8}, new float[]{0.85f, 0.28f, 0.07f}, new int[]{0x77, 0xAA, 0xFF});
        // fort on a hill (right)
        Scenic.ridge(c, p, w, h, 0.6f, h * 0.06f, 1.4f, 0xFF4A1E2A, new Random(6));
        float fx = w * 0.56f, fy = h * 0.5f;
        p.setColor(0xFF2A0F1A);
        c.drawRect(fx, fy, fx + w * 0.36f, hz + h * 0.02f, p);
        for (int i = 0; i < 18; i++) c.drawRect(fx + i * w * 0.02f, fy - h * 0.015f, fx + i * w * 0.02f + w * 0.011f, fy, p); // battlements
        float[] tw = {0.04f, 0.17f, 0.3f};
        for (float t : tw) {
            float cx = fx + w * t;
            c.drawRect(cx - w * 0.025f, fy - h * 0.08f, cx + w * 0.025f, fy, p);
            c.drawArc(new RectF(cx - w * 0.03f, fy - h * 0.13f, cx + w * 0.03f, fy - h * 0.04f), 180, 180, true, p); // chhatri dome
            c.drawRect(cx - w * 0.002f, fy - h * 0.15f, cx + w * 0.002f, fy - h * 0.12f, p);
        }
        p.setColor(0xCCFFC46B);
        Random r = new Random(4);
        for (int i = 0; i < 26; i++) {
            float lx = fx + w * 0.01f + r.nextFloat() * w * 0.33f, ly = fy + h * 0.01f + r.nextFloat() * h * 0.07f;
            c.drawRect(lx, ly, lx + w * 0.004f, ly + h * 0.008f, p);
        }
        // dunes
        int[] dc = {0xFFC2703E, 0xFFA85A32, 0xFF7E3E24, 0xFF4E2216};
        float[] db = {0.66f, 0.74f, 0.84f, 0.95f};
        Random d = new Random(8);
        for (int i = 0; i < dc.length; i++) Scenic.ridge(c, p, w, h, db[i], h * 0.035f, 1.2f + i * 0.4f, dc[i], d);
        // camel caravan silhouettes
        for (int i = 0; i < 3; i++) camel(c, p, w * (0.18f + i * 0.07f), h * 0.73f - i * h * 0.004f, h * 0.05f);
    }

    private static void camel(Canvas c, Paint p, float x, float y, float s) {
        p.setColor(0xFF2A1210);
        c.drawOval(new RectF(x - s * 0.6f, y - s * 0.75f, x + s * 0.6f, y - s * 0.3f), p);       // body + hump
        c.drawOval(new RectF(x - s * 0.15f, y - s * 1.0f, x + s * 0.3f, y - s * 0.55f), p);
        p.setStrokeWidth(s * 0.1f);
        c.drawLine(x + s * 0.55f, y - s * 0.55f, x + s * 0.8f, y - s * 1.0f, p);                  // neck
        c.drawOval(new RectF(x + s * 0.72f, y - s * 1.1f, x + s * 1.0f, y - s * 0.95f), p);       // head
        for (int k = 0; k < 4; k++) c.drawLine(x - s * 0.45f + k * s * 0.3f, y - s * 0.35f, x - s * 0.45f + k * s * 0.3f, y, p);
    }

    private static void himalaya(Canvas c, Paint p, int w, int h) {
        sky(c, p, w, h * 0.7f, new int[]{0xFF061230, 0xFF173A6E, 0xFF5C84B8, 0xFFB9D3EC}, new float[]{0, 0.35f, 0.75f, 1});
        Scenic.stars(c, p, w, h * 0.3f, 120, new Random(5), 0.9f);
        // snowy peaks: lit face + shadow face
        float[][] peaks = {{0.18f, 0.22f, 0.2f}, {0.45f, 0.14f, 0.26f}, {0.75f, 0.2f, 0.22f}, {0.95f, 0.3f, 0.16f}};
        for (float[] pk : peaks) {
            float px = w * pk[0], py = h * pk[1], half = w * pk[2], base = h * 0.66f;
            Path lit = new Path();
            lit.moveTo(px, py); lit.lineTo(px - half, base); lit.lineTo(px + half * 0.1f, base); lit.close();
            p.setShader(new LinearGradient(0, py, 0, base, 0xFFF4F8FF, 0xFF8EA8CC, Shader.TileMode.CLAMP));
            c.drawPath(lit, p);
            Path sh = new Path();
            sh.moveTo(px, py); sh.lineTo(px + half * 0.1f, base); sh.lineTo(px + half, base); sh.close();
            p.setShader(new LinearGradient(0, py, 0, base, 0xFF8AA2C8, 0xFF3C5478, Shader.TileMode.CLAMP));
            c.drawPath(sh, p);
        }
        p.setShader(null);
        Scenic.mist(c, p, w, h * 0.62f, h * 0.06f, 0x55DDEBFF);
        Scenic.ridge(c, p, w, h, 0.74f, h * 0.04f, 2.2f, 0xFF2A3E5E, new Random(7));
        Scenic.ridge(c, p, w, h, 0.86f, h * 0.035f, 3f, 0xFF18263E, new Random(8));
        // prayer flags strung across the foreground
        int[] fc = {0xFF2F6FDE, 0xFFF4F4F4, 0xFFE53935, 0xFF43A047, 0xFFFDD835};
        float x0 = w * 0.0f, y0 = h * 0.8f, x1 = w * 0.42f, y1 = h * 0.7f;
        p.setStrokeWidth(1.5f);
        p.setColor(0xAA222222);
        c.drawLine(x0, y0, x1, y1, p);
        for (int i = 0; i < 22; i++) {
            float t = (i + 0.5f) / 22f, x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t + (float) Math.sin(t * Math.PI) * h * 0.03f;
            p.setColor(fc[i % fc.length] & 0xE6FFFFFF);
            c.drawRect(x - w * 0.007f, y, x + w * 0.007f, y + h * 0.03f, p);
        }
    }

    // ================================================================ Traditional
    private static void temple(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.7f;
        sky(c, p, w, hz, new int[]{0xFF0A0620, 0xFF1E1240, 0xFF4A2350, 0xFF8A3A3A}, new float[]{0, 0.4f, 0.75f, 1});
        Scenic.stars(c, p, w, h * 0.45f, 140, new Random(12), 1f);
        Scenic.glow(c, p, w * 0.82f, h * 0.18f, h * 0.06f, 0xFFFFF4D6, 0xFF); // moon
        // shikhara tower silhouette (right)
        float cx = w * 0.7f, base = hz;
        p.setColor(0xFF140812);
        for (int tier = 0; tier < 7; tier++) {
            float tw = w * (0.13f - tier * 0.015f), y0 = base - h * (0.06f + tier * 0.065f);
            Path t = new Path();
            t.moveTo(cx - tw, base - h * tier * 0.065f);
            t.quadTo(cx - tw * 0.95f, y0, cx - tw * 0.7f, y0 - h * 0.01f);
            t.lineTo(cx + tw * 0.7f, y0 - h * 0.01f);
            t.quadTo(cx + tw * 0.95f, y0, cx + tw, base - h * tier * 0.065f);
            t.close();
            c.drawPath(t, p);
        }
        c.drawOval(new RectF(cx - w * 0.03f, base - h * 0.53f, cx + w * 0.03f, base - h * 0.47f), p); // amalaka
        c.drawRect(cx - 2, base - h * 0.6f, cx + 2, base - h * 0.52f, p);
        // flag
        p.setColor(0xFFFF8C1A);
        Path fl = new Path(); fl.moveTo(cx + 2, base - h * 0.6f); fl.lineTo(cx + w * 0.03f, base - h * 0.585f); fl.lineTo(cx + 2, base - h * 0.57f); fl.close();
        c.drawPath(fl, p);
        // mandapa + steps
        p.setColor(0xFF1A0C16);
        c.drawRect(w * 0.48f, base - h * 0.12f, w * 0.92f, base, p);
        for (int s = 0; s < 4; s++) c.drawRect(w * (0.44f - s * 0.03f), base + s * h * 0.025f, w * (0.96f + s * 0.03f), base + (s + 1) * h * 0.025f, p);
        // ghat ground between the steps and the river
        p.setColor(0xFF1A0C16);
        c.drawRect(0, base, w, base + h * 0.1f + 1, p);
        // river ghat water
        p.setShader(new LinearGradient(0, base + h * 0.1f, 0, h, 0xFF2A1430, 0xFF0A0610, Shader.TileMode.CLAMP));
        c.drawRect(0, base + h * 0.1f, w, h, p);
        p.setShader(null);
        // diya lamps along the steps and floating on water
        Random r = new Random(6);
        for (int s = 0; s < 4; s++)
            for (float x = w * (0.45f - s * 0.03f); x < w * (0.95f + s * 0.03f); x += w * 0.035f) diya(c, p, x, base + s * h * 0.025f, h * 0.008f);
        for (int i = 0; i < 26; i++) {
            float x = r.nextFloat() * w, y = base + h * 0.12f + r.nextFloat() * h * 0.17f;
            diya(c, p, x, y, h * 0.006f);
            p.setColor(0x33FFB347);
            c.drawRect(x - 1, y + 3, x + 1, y + h * 0.03f, p);
        }
    }

    private static void diya(Canvas c, Paint p, float x, float y, float s) {
        Scenic.glow(c, p, x, y - s, s * 6, 0xFFFFB347, 0x66);
        p.setColor(0xFFFFE08A);
        c.drawOval(new RectF(x - s * 0.5f, y - s * 2.2f, x + s * 0.5f, y - s * 0.6f), p);
        p.setColor(0xFF5A2A10);
        c.drawArc(new RectF(x - s * 1.4f, y - s, x + s * 1.4f, y + s), 0, 180, true, p);
    }

    private static void warli(Canvas c, Paint p, int w, int h) {
        p.setShader(new RadialGradient(w * 0.6f, h * 0.55f, w * 0.8f, 0xFF9A4A26, 0xFF5A220E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random r = new Random(14);
        for (int i = 0; i < 2500; i++) {       // earthen wall texture
            p.setColor(((10 + r.nextInt(25)) << 24) | (r.nextBoolean() ? 0x000000 : 0xFFE0C0));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 0.6f + r.nextFloat() * 1.6f, p);
        }
        p.setColor(0xF2FFF4E4);
        p.setStrokeWidth(Math.max(2f, h * 0.0035f));
        p.setStrokeCap(Paint.Cap.ROUND);
        // tarpa dance: concentric rings of dancers (right side, clear of the clock)
        float cx = w * 0.72f, cy = h * 0.62f;
        int[] counts = {10, 16};
        float[] radii = {h * 0.13f, h * 0.24f};
        for (int ring = 0; ring < 2; ring++)
            for (int i = 0; i < counts[ring]; i++) {
                double a = i * 2 * Math.PI / counts[ring];
                figure(c, p, cx + (float) Math.cos(a) * radii[ring] * 1.25f, cy + (float) Math.sin(a) * radii[ring] * 0.55f, h * 0.045f);
            }
        figure(c, p, cx, cy, h * 0.06f);   // tarpa player
        // sun and moon, trees, huts along the bottom-left
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(w * 0.93f, h * 0.12f, h * 0.04f, p);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            c.drawLine(w * 0.93f + (float) Math.cos(a) * h * 0.05f, h * 0.12f + (float) Math.sin(a) * h * 0.05f,
                    w * 0.93f + (float) Math.cos(a) * h * 0.07f, h * 0.12f + (float) Math.sin(a) * h * 0.07f, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) tree(c, p, w * (0.06f + i * 0.09f), h * 0.92f, h * 0.12f);
        for (int i = 0; i < 2; i++) hut(c, p, w * (0.4f + i * 0.08f), h * 0.93f, h * 0.06f);
        // decorative border
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        for (int i = 0; i < w; i += (int) (h * 0.03f)) {
            Path tri = new Path();
            tri.moveTo(i, h - 2); tri.lineTo(i + h * 0.015f, h - h * 0.025f); tri.lineTo(i + h * 0.03f, h - 2);
            c.drawPath(tri, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void figure(Canvas c, Paint p, float x, float y, float s) {
        Path body = new Path();      // two triangles meeting at the waist
        body.moveTo(x - s * 0.3f, y - s * 0.6f); body.lineTo(x + s * 0.3f, y - s * 0.6f); body.lineTo(x, y - s * 0.15f); body.close();
        body.moveTo(x, y - s * 0.15f); body.lineTo(x - s * 0.28f, y + s * 0.3f); body.lineTo(x + s * 0.28f, y + s * 0.3f); body.close();
        c.drawPath(body, p);
        c.drawCircle(x, y - s * 0.78f, s * 0.16f, p);
        c.drawLine(x - s * 0.3f, y - s * 0.6f, x - s * 0.55f, y - s * 0.35f, p);
        c.drawLine(x + s * 0.3f, y - s * 0.6f, x + s * 0.55f, y - s * 0.35f, p);
        c.drawLine(x - s * 0.18f, y + s * 0.3f, x - s * 0.25f, y + s * 0.65f, p);
        c.drawLine(x + s * 0.18f, y + s * 0.3f, x + s * 0.25f, y + s * 0.65f, p);
    }

    private static void tree(Canvas c, Paint p, float x, float base, float s) {
        c.drawLine(x, base, x, base - s, p);
        for (int i = 0; i < 6; i++) {
            float y = base - s * (0.35f + i * 0.12f), l = s * (0.35f - i * 0.04f);
            c.drawLine(x, y, x - l, y - l * 0.6f, p);
            c.drawLine(x, y, x + l, y - l * 0.6f, p);
        }
    }

    private static void hut(Canvas c, Paint p, float x, float base, float s) {
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(x - s * 0.5f, base - s * 0.6f, x + s * 0.5f, base, p);
        Path roof = new Path();
        roof.moveTo(x - s * 0.7f, base - s * 0.6f); roof.lineTo(x, base - s * 1.1f); roof.lineTo(x + s * 0.7f, base - s * 0.6f);
        c.drawPath(roof, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ================================================================ Icy / European / Western
    private static void arctic(Canvas c, Paint p, int w, int h) {
        sky(c, p, w, h * 0.75f, new int[]{0xFF020814, 0xFF06182E, 0xFF0E2A44, 0xFF1A3E58}, new float[]{0, 0.4f, 0.8f, 1});
        Scenic.stars(c, p, w, h * 0.6f, 220, new Random(21), 1f);
        // aurora curtains
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        int[] cols = {0xFF3CFFB0, 0xFF2EE6D6, 0xFF8A6CFF};
        for (int band = 0; band < 3; band++) {
            Random r = new Random(30 + band);
            float base = h * (0.28f + band * 0.08f), ph = r.nextFloat() * 6;
            for (int x = 0; x < w; x += 3) {
                float u = x / (float) w;
                float top = base - h * (0.12f + 0.1f * (float) Math.sin(u * 7 + ph) + 0.05f * (float) Math.sin(u * 19 + ph * 2));
                float y = base + h * 0.05f * (float) Math.sin(u * 4 + ph);
                p.setShader(new LinearGradient(0, top, 0, y, cols[band] & 0x00FFFFFF, (cols[band] & 0x00FFFFFF) | 0xB0000000, Shader.TileMode.CLAMP));
                c.drawRect(x, top, x + 3, y, p);
            }
        }
        p.setXfermode(null);
        p.setShader(null);
        // snowfield
        Scenic.ridge(c, p, w, h, 0.76f, h * 0.03f, 1.5f, 0xFF9EB8D2, new Random(4));
        Scenic.ridge(c, p, w, h, 0.84f, h * 0.025f, 2f, 0xFFC9DAEC, new Random(5));
        p.setShader(new LinearGradient(0, h * 0.88f, 0, h, 0xFFDDE8F4, 0xFFAFC3D8, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.88f, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 14; i++) pine(c, p, w * (0.55f + i * 0.033f), h * (0.84f + (i % 3) * 0.015f), h * (0.08f + (i % 4) * 0.025f), 0xFF0A1622);
        // cabin with warm window
        float cx = w * 0.18f, cy = h * 0.87f, s = h * 0.06f;
        p.setColor(0xFF1A1410);
        c.drawRect(cx - s, cy - s * 0.8f, cx + s, cy, p);
        Path roof = new Path(); roof.moveTo(cx - s * 1.3f, cy - s * 0.8f); roof.lineTo(cx, cy - s * 1.6f); roof.lineTo(cx + s * 1.3f, cy - s * 0.8f); roof.close();
        p.setColor(0xFFE8F0F8);
        c.drawPath(roof, p);
        Scenic.glow(c, p, cx, cy - s * 0.4f, s * 1.5f, 0xFFFFB347, 0x66);
        p.setColor(0xFFFFC46B);
        c.drawRect(cx - s * 0.3f, cy - s * 0.6f, cx + s * 0.3f, cy - s * 0.2f, p);
    }

    static void pine(Canvas c, Paint p, float x, float base, float s, int col) {
        p.setShader(null);
        p.setColor(col);
        for (int i = 0; i < 4; i++) {
            float top = base - s * (0.35f + i * 0.22f), half = s * (0.32f - i * 0.06f);
            Path t = new Path();
            t.moveTo(x, top - s * 0.25f); t.lineTo(x - half, top + s * 0.15f); t.lineTo(x + half, top + s * 0.15f); t.close();
            c.drawPath(t, p);
        }
        c.drawRect(x - s * 0.03f, base - s * 0.25f, x + s * 0.03f, base, p);
    }

    private static void alpine(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.6f;
        sky(c, p, w, hz, new int[]{0xFF1E4E86, 0xFF5B8FC4, 0xFFC9D9E8, 0xFFF4E6C8}, new float[]{0, 0.45f, 0.85f, 1});
        Scenic.bloom(c, p, w * 0.3f, h * 0.18f, h, new int[]{0xFFFFF0C8, 0xFFFFF8E0, 0xFFFFFFFF}, new float[]{0.6f, 0.18f, 0.04f}, new int[]{0x55, 0x88, 0xFF});
        // snow-capped range drawn twice: real + mirrored in the lake
        for (int pass = 0; pass < 2; pass++) {
            c.save();
            if (pass == 1) { c.translate(0, hz * 2); c.scale(1, -1); }
            int a = pass == 0 ? 0xFF : 0x66;
            mountainsWithSnow(c, p, w, h, hz, a);
            c.restore();
        }
        // lake
        p.setShader(new LinearGradient(0, hz, 0, h, 0x332A5E8A, 0xEE0C2A40, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        p.setColor(0x22FFFFFF);
        Random r = new Random(3);
        for (int i = 0; i < 60; i++) { float y = hz + r.nextFloat() * (h - hz); c.drawRect(r.nextFloat() * w, y, r.nextFloat() * w, y + 1, p); }
        // forested shore + chalet
        for (int i = 0; i < 22; i++) pine(c, p, w * (0.6f + i * 0.019f), hz + h * 0.01f, h * (0.06f + (i % 3) * 0.02f), 0xFF0E2A1C);
        float cx = w * 0.5f, cy = hz + h * 0.005f, s = h * 0.035f;
        p.setColor(0xFF6A3A22);
        c.drawRect(cx - s, cy - s * 0.8f, cx + s, cy, p);
        p.setColor(0xFFB03A2E);
        Path roof = new Path(); roof.moveTo(cx - s * 1.3f, cy - s * 0.8f); roof.lineTo(cx, cy - s * 1.5f); roof.lineTo(cx + s * 1.3f, cy - s * 0.8f); roof.close();
        c.drawPath(roof, p);
    }

    private static void mountainsWithSnow(Canvas c, Paint p, int w, int h, float hz, int alpha) {
        float[][] pk = {{0.12f, 0.24f, 0.22f}, {0.38f, 0.16f, 0.25f}, {0.66f, 0.2f, 0.24f}, {0.9f, 0.27f, 0.2f}};
        for (float[] k : pk) {
            float px = w * k[0], py = h * k[1], half = w * k[2];
            Path m = new Path();
            m.moveTo(px, py); m.lineTo(px - half, hz); m.lineTo(px + half, hz); m.close();
            p.setShader(new LinearGradient(px - half, 0, px + half, 0, (alpha << 24) | 0x6A7E96, (alpha << 24) | 0x34465C, Shader.TileMode.CLAMP));
            c.drawPath(m, p);
            Path snow = new Path();
            snow.moveTo(px, py); snow.lineTo(px - half * 0.3f, py + (hz - py) * 0.3f);
            snow.lineTo(px - half * 0.12f, py + (hz - py) * 0.24f); snow.lineTo(px, py + (hz - py) * 0.33f);
            snow.lineTo(px + half * 0.15f, py + (hz - py) * 0.25f); snow.lineTo(px + half * 0.3f, py + (hz - py) * 0.3f); snow.close();
            p.setShader(null);
            p.setColor((alpha << 24) | 0xF4F8FF);
            c.drawPath(snow, p);
        }
        Scenic.ridge(c, p, w, h, hz / h, h * 0.03f, 4f, (alpha << 24) | 0x1E3A2A, new Random(9));
    }

    private static void canyon(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.62f;
        sky(c, p, w, hz, new int[]{0xFF241038, 0xFF6A2A5A, 0xFFE0564A, 0xFFFF9A4A, 0xFFFFD08A}, new float[]{0, 0.3f, 0.6f, 0.85f, 1});
        Scenic.bloom(c, p, w * 0.5f, hz, h, new int[]{0xFFFF9E5E, 0xFFFFC27E, 0xFFFFE7B8}, new float[]{0.9f, 0.3f, 0.07f}, new int[]{0x88, 0xAA, 0xFF});
        // mesas / buttes
        float[][] m = {{0.05f, 0.18f, 0.48f}, {0.3f, 0.12f, 0.42f}, {0.62f, 0.16f, 0.46f}, {0.82f, 0.1f, 0.5f}};
        int[] mc = {0xFF8A3A2A, 0xFF73301F, 0xFF92402C, 0xFF6A2A1A};
        for (int i = 0; i < m.length; i++) {
            float x = w * m[i][0], bw = w * m[i][1], top = h * m[i][2];
            Path b = new Path();
            b.moveTo(x - bw * 0.25f, hz + 1); b.lineTo(x, top + h * 0.03f); b.lineTo(x + h * 0.01f, top);
            b.lineTo(x + bw - h * 0.01f, top); b.lineTo(x + bw, top + h * 0.03f); b.lineTo(x + bw * 1.25f, hz + 1); b.close();
            p.setShader(new LinearGradient(x, 0, x + bw, 0, mc[i], 0xFF3A160E, Shader.TileMode.CLAMP));
            c.drawPath(b, p);
            p.setShader(null);
            p.setColor(0x22000000);
            for (int s = 1; s < 5; s++) c.drawRect(x, top + (hz - top) * s / 5f, x + bw, top + (hz - top) * s / 5f + 2, p); // strata
        }
        p.setShader(new LinearGradient(0, hz, 0, h, 0xFFB0532E, 0xFF3A160A, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        // saguaro cacti in the foreground
        cactus(c, p, w * 0.1f, h * 0.98f, h * 0.3f);
        cactus(c, p, w * 0.9f, h * 0.97f, h * 0.22f);
        cactus(c, p, w * 0.78f, h * 0.95f, h * 0.12f);
    }

    private static void cactus(Canvas c, Paint p, float x, float base, float s) {
        p.setColor(0xFF1A0C08);
        float tw = s * 0.12f;
        c.drawRoundRect(new RectF(x - tw / 2, base - s, x + tw / 2, base), tw / 2, tw / 2, p);
        c.drawRoundRect(new RectF(x - tw * 2.2f, base - s * 0.75f, x - tw * 1.3f, base - s * 0.35f), tw / 2, tw / 2, p);
        c.drawRect(x - tw * 2.2f, base - s * 0.42f, x, base - s * 0.34f, p);
        c.drawRoundRect(new RectF(x + tw * 1.3f, base - s * 0.85f, x + tw * 2.2f, base - s * 0.5f), tw / 2, tw / 2, p);
        c.drawRect(x, base - s * 0.57f, x + tw * 2.2f, base - s * 0.49f, p);
    }

    // ================================================================ Movie moods
    private static void noir(Canvas c, Paint p, int w, int h) {
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF101010, 0xFF2A2A2A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // skyline with lit windows
        Random b = new Random(5);
        float x = 0;
        while (x < w) {
            float bw = w * (0.05f + b.nextFloat() * 0.07f), bh = h * (0.3f + b.nextFloat() * 0.45f);
            p.setColor(0xFF0A0A0A);
            c.drawRect(x, h - bh, x + bw, h, p);
            p.setColor(0x66D8D8D8);
            for (float wy = h - bh + h * 0.02f; wy < h; wy += h * 0.035f)
                for (float wx = x + bw * 0.12f; wx < x + bw * 0.85f; wx += bw * 0.22f)
                    if (b.nextInt(6) == 0) c.drawRect(wx, wy, wx + bw * 0.1f, wy + h * 0.015f, p);
            x += bw + 2;
        }
        // venetian-blind light shafts
        p.setShader(new LinearGradient(w * 0.2f, 0, w * 0.8f, h, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        for (int i = 0; i < 9; i++) {
            Path s = new Path();
            float y = h * (0.05f + i * 0.07f);
            s.moveTo(w * 0.35f, y); s.lineTo(w * 1.1f, y + h * 0.25f); s.lineTo(w * 1.1f, y + h * 0.28f); s.lineTo(w * 0.35f, y + h * 0.03f); s.close();
            c.drawPath(s, p);
        }
        p.setShader(null);
        // street lamp cone + rain
        Scenic.glow(c, p, w * 0.12f, h * 0.35f, h * 0.06f, 0xFFFFFFFF, 0xCC);
        Path cone = new Path(); cone.moveTo(w * 0.12f, h * 0.35f); cone.lineTo(w * 0.0f, h); cone.lineTo(w * 0.26f, h); cone.close();
        p.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x40FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(cone, p);
        p.setShader(null);
        p.setColor(0xFF050505);
        c.drawRect(w * 0.118f, h * 0.36f, w * 0.124f, h, p);
        Random r = new Random(77);
        p.setStrokeWidth(1.2f);
        for (int i = 0; i < 450; i++) {
            float rx = r.nextFloat() * w, ry = r.nextFloat() * h, l = h * (0.02f + r.nextFloat() * 0.03f);
            p.setColor(((25 + r.nextInt(50)) << 24) | 0xFFFFFF);
            c.drawLine(rx, ry, rx - l * 0.2f, ry + l, p);
        }
    }

    private static void neon(Canvas c, Paint p, int w, int h) {
        float hz = h * 0.6f;
        sky(c, p, w, hz, new int[]{0xFF0B0320, 0xFF2A0A4A, 0xFF7A1A6A, 0xFFFF4D8A}, new float[]{0, 0.45f, 0.82f, 1});
        Scenic.stars(c, p, w, h * 0.35f, 120, new Random(8), 1f);
        // striped synth sun
        float sx = w * 0.5f, sr = h * 0.2f;
        p.setShader(new LinearGradient(0, hz - sr * 2, 0, hz, 0xFFFFE066, 0xFFFF2BD6, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(0, 0, w, hz);
        c.drawCircle(sx, hz - sr * 0.25f, sr, p);
        p.setShader(null);
        p.setColor(0xFF2A0A4A);
        for (int i = 0; i < 6; i++) { float y = hz - sr * 0.9f + i * sr * 0.22f; c.drawRect(sx - sr, y, sx + sr, y + sr * (0.03f + i * 0.015f), p); }
        c.restore();
        // skyline with neon edges
        Random b = new Random(13);
        float x = 0;
        p.setStrokeWidth(2f);
        while (x < w) {
            float bw = w * (0.03f + b.nextFloat() * 0.05f), bh = h * (0.06f + b.nextFloat() * 0.2f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF12041E);
            c.drawRect(x, hz - bh, x + bw, hz, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(b.nextBoolean() ? 0xCC00F0FF : 0xCCFF2BD6);
            c.drawRect(x, hz - bh, x + bw, hz, p);
            x += bw + 3;
        }
        p.setStyle(Paint.Style.FILL);
        // perspective neon grid floor
        p.setColor(0xFF0A0218);
        c.drawRect(0, hz, w, h, p);
        p.setStrokeWidth(1.6f);
        for (int i = -18; i <= 18; i++) {
            p.setColor(0x99FF2BD6);
            c.drawLine(sx + i * w * 0.012f, hz, sx + i * w * 0.12f, h, p);
        }
        for (int j = 1; j < 14; j++) {
            float y = hz + (h - hz) * (float) Math.pow(j / 14f, 2);
            p.setColor(((int) (60 + j * 12) << 24) | 0x00F0FF);
            c.drawLine(0, y, w, y, p);
        }
        Scenic.glow(c, p, sx, hz, h * 0.5f, 0xFFFF2BD6, 0x40);
    }

    private static void bollywood(Canvas c, Paint p, int w, int h) {
        // sunburst rays
        float cx = w * 0.62f, cy = h * 0.6f, R = (float) Math.hypot(w, h);
        p.setColor(0xFFB0213A);
        c.drawRect(0, 0, w, h, p);
        int n = 28;
        for (int i = 0; i < n; i++) {
            if (i % 2 == 1) continue;
            double a0 = i * 2 * Math.PI / n, a1 = (i + 1) * 2 * Math.PI / n;
            Path t = new Path();
            t.moveTo(cx, cy);
            t.lineTo(cx + (float) Math.cos(a0) * R, cy + (float) Math.sin(a0) * R);
            t.lineTo(cx + (float) Math.cos(a1) * R, cy + (float) Math.sin(a1) * R);
            t.close();
            p.setColor(0xFFE0512E);
            c.drawPath(t, p);
        }
        p.setShader(new RadialGradient(cx, cy, h * 0.9f, 0xCCFFC93C, 0x00FFC93C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Scenic.glow(c, p, cx, cy, h * 0.16f, 0xFFFFF0B0, 0xFF);
        // halftone dots fading in from the bottom-left
        for (float y = h * 0.45f; y < h; y += h * 0.025f)
            for (float x = 0; x < w * 0.5f; x += h * 0.025f) {
                float k = (1 - x / (w * 0.5f)) * ((y - h * 0.45f) / (h * 0.55f));
                p.setColor(0x55300818);
                c.drawCircle(x, y, h * 0.011f * k, p);
            }
        // film strips top and bottom
        float sh = h * 0.055f;
        p.setColor(0xFF140608);
        c.drawRect(0, 0, w, sh, p);
        c.drawRect(0, h - sh, w, h, p);
        p.setColor(0xFFFFE6B0);
        for (float x = h * 0.02f; x < w; x += h * 0.06f) {
            c.drawRoundRect(new RectF(x, sh * 0.3f, x + h * 0.03f, sh * 0.7f), 3, 3, p);
            c.drawRoundRect(new RectF(x, h - sh * 0.7f, x + h * 0.03f, h - sh * 0.3f), 3, 3, p);
        }
        // a few sparkle stars
        Random r = new Random(4);
        for (int i = 0; i < 14; i++) star(c, p, r.nextFloat() * w, sh + r.nextFloat() * h * 0.4f, h * (0.008f + r.nextFloat() * 0.012f));
    }

    private static void star(Canvas c, Paint p, float x, float y, float s) {
        p.setColor(0xEEFFF4C8);
        Path st = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? s : s * 0.4f;
            if (i == 0) st.moveTo(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
            else st.lineTo(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
        }
        st.close();
        c.drawPath(st, p);
    }
}

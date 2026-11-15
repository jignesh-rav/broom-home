package com.broom.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Procedural galaxy / nebula wallpaper. Rendered once per theme+size into a cached bitmap,
 * so the home screen costs no CPU while idle.
 */
final class Nebula {
    private static Bitmap cache;
    private static String cacheKey;

    /** base top, base bottom, then nebula cloud colours */
    static int[] palette(String id) {
        switch (id) {
            case "dawn":  return new int[]{0xFF120A24, 0xFF2C0F2C, 0xFFFF6F91, 0xFFFFC371, 0xFF845EC2, 0xFFFF9671, 0xFF4B7BEC};
            case "day":   return new int[]{0xFF041530, 0xFF0B2C52, 0xFF00C9FF, 0xFF7CF5C4, 0xFF4D8BFF, 0xFFB8F3FF, 0xFFA77BFF};
            case "dusk":  return new int[]{0xFF14061F, 0xFF2A0A28, 0xFFFF3CAC, 0xFF784BA0, 0xFFFF8C42, 0xFF2B86C5, 0xFFFFD166};
            default:      return new int[]{0xFF02030B, 0xFF080C22, 0xFF5B2A9E, 0xFF4E54C8, 0xFF1FA2FF, 0xFFD76D77, 0xFF2EC4B6};
        }
    }

    /** Wallpaper for the theme: galaxy for Night, a procedural scene otherwise. One bitmap kept at a time. */
    static synchronized Bitmap get(Theme t, int w, int h) {
        String key = "w:" + t.id + w + "x" + h;
        if (cache != null && key.equals(cacheKey)) return cache;
        release();
        cache = t.wallpaper == ThemeBackground.STYLE_STARS ? render(t.id, w, h) : Scenic.render(t.id, w, h);
        cacheKey = key;
        return cache;
    }

    /** Galaxy in the theme's colours (Nebula Drift screensaver). Shares the single cache slot. */
    static synchronized Bitmap galaxy(Theme t, int w, int h) {
        if (t.wallpaper == ThemeBackground.STYLE_STARS) return get(t, w, h);
        String key = "g:" + t.id + w + "x" + h;
        if (cache != null && key.equals(cacheKey)) return cache;
        release();
        cache = render(t.id, w, h);
        cacheKey = key;
        return cache;
    }

    /** Small preview for the theme gallery (not cached). */
    static Bitmap preview(Theme t, int w, int h) {
        return t.wallpaper == ThemeBackground.STYLE_STARS ? render(t.id, w, h) : Scenic.render(t.id, w, h);
    }

    static synchronized void release() {
        if (cache != null) cache.recycle();
        cache = null;
        cacheKey = null;
    }

    static Bitmap render(String id, int w, int h) {
        int[] pal = palette(id);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Random r = new Random(id.hashCode() * 31L + 7);

        p.setShader(new LinearGradient(0, 0, w, h, pal[0], pal[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // galactic band: clouds scattered along a diagonal
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        float ang = -18 - r.nextFloat() * 14;
        for (int i = 0; i < 18; i++) {
            float u = r.nextFloat();
            float x = w * (-0.1f + u * 1.2f);
            float y = h * (0.62f - u * 0.42f) + (r.nextFloat() - 0.5f) * h * 0.35f;
            float rad = h * (0.12f + r.nextFloat() * 0.32f);
            int col = pal[2 + r.nextInt(pal.length - 2)];
            int a = 0x38 + r.nextInt(0x50);
            cloud(c, p, x, y, rad, 1.4f + r.nextFloat() * 1.4f, ang + (r.nextFloat() - 0.5f) * 30, col, a);
        }
        // bright core glows
        for (int i = 0; i < 3; i++) {
            float x = w * (0.2f + r.nextFloat() * 0.6f), y = h * (0.25f + r.nextFloat() * 0.4f);
            cloud(c, p, x, y, h * (0.06f + r.nextFloat() * 0.06f), 1.6f, ang, 0xFFFFF4E6, 0x60);
        }
        p.setXfermode(null);

        // dark dust lanes for structure
        for (int i = 0; i < 7; i++) {
            float u = r.nextFloat();
            cloud(c, p, w * u, h * (0.6f - u * 0.4f) + (r.nextFloat() - 0.5f) * h * 0.15f,
                    h * (0.05f + r.nextFloat() * 0.09f), 3f, ang, 0xFF000000, 0x55);
        }

        // star field: dust, medium, bright with spikes
        p.setShader(null);
        for (int i = 0; i < 650; i++) {
            p.setColor(((40 + r.nextInt(170)) << 24) | starTint(r));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 0.5f + r.nextFloat() * 0.9f, p);
        }
        for (int i = 0; i < 45; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, s = 1.2f + r.nextFloat() * 1.4f;
            p.setShader(new RadialGradient(x, y, s * 4, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, s * 4, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, s * 0.7f, p);
        }
        p.setStrokeWidth(1.2f);
        for (int i = 0; i < 9; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.85f, L = h * (0.025f + r.nextFloat() * 0.03f);
            int tint = 0xFF000000 | starTint(r);
            p.setShader(new RadialGradient(x, y, L, tint, tint & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawLine(x - L, y, x + L, y, p);
            c.drawLine(x, y - L, x, y + L, p);
            c.drawCircle(x, y, L * 0.25f, p);
            p.setShader(null);
        }

        // vignette + bottom scrim so tiles stay readable
        p.setShader(new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) * 0.6f, 0x00000000, 0x88000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0x55000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    private static int starTint(Random r) {
        switch (r.nextInt(5)) {
            case 0: return 0xCFE3FF;
            case 1: return 0xFFE9C9;
            case 2: return 0xFFD1E8;
            default: return 0xFFFFFF;
        }
    }

    private static void cloud(Canvas c, Paint p, float x, float y, float rad, float stretch, float rot, int col, int alpha) {
        c.save();
        c.translate(x, y);
        c.rotate(rot);
        c.scale(stretch, 1f);
        int core = (col & 0x00FFFFFF) | (alpha << 24);
        int mid = (col & 0x00FFFFFF) | ((alpha / 3) << 24);
        p.setShader(new RadialGradient(0, 0, rad, new int[]{core, mid, col & 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, rad, p);
        c.restore();
        p.setShader(null);
    }
}

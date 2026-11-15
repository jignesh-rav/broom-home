package com.broom.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Soft UI sounds, synthesised once into tiny WAV files and played through a low-latency SoundPool:
 *  tick()  - very soft, short tick when the selection moves
 *  click() - slightly fuller click when something is selected
 */
final class Sounds {
    private static SoundPool pool;
    private static int tickId, clickId;
    private static long quietUntil, lastTick;

    static synchronized void init(Context ctx) {
        if (pool != null) return;
        Context c = ctx.getApplicationContext();
        try {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            tickId = pool.load(write(c, "tick_v1.wav", synth(0.012f, 2600f, 1800f, 380f, 0.32f)), 1);
            clickId = pool.load(write(c, "click_v1.wav", synth(0.03f, 1500f, 700f, 160f, 0.55f)), 1);
        } catch (Exception e) { pool = null; }
    }

    static boolean enabled(Context c) { return Theme.prefs(c).getBoolean("sounds", true); }

    /** Suppress sounds briefly (e.g. while a screen sets its initial focus). */
    static void quiet(long ms) { quietUntil = System.currentTimeMillis() + ms; }

    static void tick(Context c) {
        long now = System.currentTimeMillis();
        if (pool == null || now < quietUntil || now - lastTick < 35 || !enabled(c)) return;
        lastTick = now;
        pool.play(tickId, 0.35f, 0.35f, 1, 0, 1f);
    }

    static void click(Context c) {
        if (pool == null || !enabled(c)) return;
        pool.play(clickId, 0.6f, 0.6f, 2, 0, 1f);
    }

    /** Decaying sine sweep from f0 to f1 with a little filtered noise for texture, 16-bit mono 44.1 kHz. */
    private static short[] synth(float seconds, float f0, float f1, float decay, float amp) {
        int sr = 44100, n = (int) (sr * seconds);
        short[] s = new short[n];
        double phase = 0, noise = 0;
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < n; i++) {
            double t = i / (double) sr, k = i / (double) n;
            double f = f0 + (f1 - f0) * k;
            phase += 2 * Math.PI * f / sr;
            noise = noise * 0.7 + (r.nextDouble() - 0.5) * 0.3;
            double env = Math.exp(-t * decay) * Math.min(1, i / 40.0);   // 1 ms attack, fast decay
            double v = (Math.sin(phase) * 0.85 + noise * 0.25) * env * amp;
            s[i] = (short) (Math.max(-1, Math.min(1, v)) * 32767);
        }
        return s;
    }

    private static String write(Context c, String name, short[] pcm) throws Exception {
        File f = new File(c.getCacheDir(), name);
        if (f.exists() && f.length() > 44) return f.getAbsolutePath();
        int sr = 44100, bytes = pcm.length * 2;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + bytes).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(sr).putInt(sr * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(bytes);
        for (short v : pcm) b.putShort(v);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(b.array()); }
        return f.getAbsolutePath();
    }
}

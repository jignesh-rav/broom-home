package com.broom.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;

import java.util.Calendar;
import java.util.Random;

/**
 * Screensaver = two layers.
 *  - Background layer: artwork animated in sync with the display (smooth, time-based motion).
 *  - Text layer: clock, date and weather. Completely still; redrawn only when the minute changes.
 *    For burn-in safety the whole block shifts by a few pixels once every 10 minutes.
 */
public class DreamView extends FrameLayout {
    public static final String[] MODES = {"nebula", "aurora", "warp", "mandala", "clock", "quotes",
            "desi", "sayings",
            "briefing", "panchang",
            "photo:monuments", "photo:nature", "photo:animals", "photo:baby", "photo:space", "photo:usb", "shuffle"};
    public static final String[] MODE_NAMES = {"Nebula Drift", "Aurora", "Warp Speed", "Rangoli Mandala", "Minimal Clock",
            "Facts & Quotes",
            "Desi Clock", "Sayings",
            "Briefing", "Panchang", "Monuments", "Nature", "Animals", "Baby animals", "Space", "My photos (USB)", "Shuffle"};
    public static final String[] MODE_DESC = {
            "A colourful galaxy that drifts very slowly, with twinkling stars.",
            "Northern-lights curtains over a starry sky.",
            "Fly gently through a field of stars.",
            "A rangoli-inspired mandala turning slowly in place.",
            "Large clock, date and weather on deep black. Lowest power.",
            "Interesting facts, quotes, and Hindi, Sanskrit, Gujarati & Marathi sayings with meanings.",
            "Time, date, weather and a saying in Gujarati, Hindi, Marathi or English, with native digits (Settings → Language).",
            "Dohe, abhangs and kahevat in large type, with the poet and an English meaning — a new one every 2 minutes, rotating through your screensaver languages.",
            "Markets, live cricket, football, tech and space headlines — a new page every 2 minutes.",
            "Today's Panchang: tithi, nakshatra, Choghadiya now, Rahu Kaal, sunrise/sunset and coming festivals.",
            "Forts, palaces, temples and landmarks — featured photos from Wikimedia Commons.",
            "Mountains, waterfalls, lakes and coasts (Wikimedia Commons).",
            "Tigers, elephants, birds and more (Wikimedia Commons).",
            "Cubs, calves, kittens, ducklings… (Wikimedia Commons).",
            "Nebulae, galaxies, the Moon and auroras (Wikimedia Commons).",
            "Photos from a USB drive plugged into the TV.",
            "A new style every 10 minutes: animations, quotes, Indian-language clock & sayings and all photo packs, each shown once before any repeats."};
    private static final int ANIMATED = 4; // first N modes are the animated ones

    /** Styles Shuffle rotates through: animations, quotes and every online photo pack. */
    private static final String[] SHUFFLE_POOL = {"nebula", "aurora", "warp", "mandala", "quotes",
            "desi", "sayings",
            "photo:monuments", "photo:nature", "photo:animals", "photo:baby", "photo:space"};

    /**
     * Shuffle bag kept in prefs across screensaver sessions: every style is shown once (in random order)
     * before any repeats, and the next round never starts with the style just shown.
     */
    static synchronized String nextShuffle(Context c, String last) {
        android.content.SharedPreferences sp = Theme.prefs(c);
        java.util.List<String> bag = new java.util.ArrayList<>();
        for (String x : sp.getString("shuffle_bag", "").split(","))
            if (java.util.Arrays.asList(SHUFFLE_POOL).contains(x)) bag.add(x);
        if (bag.isEmpty()) {
            bag.addAll(java.util.Arrays.asList(SHUFFLE_POOL));
            java.util.Collections.shuffle(bag);
            String prev = last != null ? last : sp.getString("shuffle_last", "");
            if (bag.size() > 1 && bag.get(0).equals(prev)) java.util.Collections.swap(bag, 0, bag.size() - 1);
        }
        String next = bag.remove(0);
        sp.edit().putString("shuffle_bag", android.text.TextUtils.join(",", bag)).putString("shuffle_last", next).apply();
        return next;
    }

    private final String mode;
    private String active;
    private final Theme theme;
    private final Random rnd = new Random();
    private long switched = System.currentTimeMillis();
    private Weather wx;
    private long wxChecked;
    private final Bg bg;
    private final Txt txt;
    // photo modes
    private volatile Bitmap photo, prevPhoto;
    private volatile Photos.Item photoItem;
    private volatile long photoSince;
    private volatile boolean running = true;
    private Thread loader;
    // quotes mode
    private View quote;   // quotes or sayings layer
    // Briefing interleaved into every style: 2 min style, 2 min Briefing page (a different page each time), repeat
    static final long BRIEF_EVERY = 2 * 60000L;
    /** A photo stays 3 minutes; info (Briefing pages, quotes, sayings and their language) changes every 2 minutes. */
    static final long PHOTO_EVERY = 3 * 60000L, INFO_EVERY = DesiDream.INFO_EVERY;
    private FrameLayout briefBox;
    private View veil;
    private volatile boolean briefOn;
    private final Runnable briefToggle = new Runnable() {
        @Override public void run() {
            briefOn = !briefOn;
            veil.animate().alpha(briefOn ? 1f : 0f).setDuration(900).start();
            briefBox.animate().alpha(briefOn ? 1f : 0f).setDuration(900).start();
            if (quote != null) quote.animate().alpha(briefOn ? 0f : 1f).setDuration(900).start();
            txt.invalidate();
            postDelayed(this, BRIEF_EVERY);
        }
    };

    static boolean briefEverywhere(Context c) { return Theme.prefs(c).getBoolean("brief_in_screensaver", true); }

    /** Night power saving: 00:00-06:00 (on by default). */
    static boolean night(Context c) {
        if (!Theme.prefs(c).getBoolean("night_saver", true)) return false;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 6;
    }

    public DreamView(Context c, String mode) {
        super(c);
        this.mode = mode;
        this.theme = Theme.current(c);
        String a = mode.equals("shuffle") ? nextShuffle(c, null) : mode;
        // a switched-off feature falls back to the clock
        if ((a.equals("panchang") && !Features.on(c, Features.PANCHANG)) || (a.equals("briefing") && !Features.anyBriefing(c))) a = "clock";
        this.active = a;
        this.wx = Weather.cached(c);
        bg = new Bg(c);
        txt = new Txt(c);
        addView(bg, new LayoutParams(-1, -1));
        if (active.equals("quotes")) {
            quote = new QuoteView(c);
            addView(quote, new LayoutParams(-1, -1));
        }
        if (active.equals("sayings")) {
            quote = new DesiDream.SayingsView(c);
            addView(quote, new LayoutParams(-1, -1));
        }
        if (active.equals("briefing")) addView(new BriefView(c), new LayoutParams(-1, -1));
        else if (active.equals("panchang")) addView(new BriefView(c, 6), new LayoutParams(-1, -1));
        else if (briefEverywhere(c) && Features.anyBriefing(c) && !night(c) && !active.equals("panchang")) {
            veil = new View(c);
            veil.setBackgroundColor(0xA6000000);
            veil.setAlpha(0f);
            addView(veil, new LayoutParams(-1, -1));
            briefBox = new FrameLayout(c);
            briefBox.setAlpha(0f);
            briefBox.addView(new BriefView(c), new LayoutParams(-1, -1));
            addView(briefBox, new LayoutParams(-1, -1));
            postDelayed(briefToggle, BRIEF_EVERY);
        }
        addView(txt, new LayoutParams(-1, -1));
        if (active.startsWith("photo:")) startPhotos(c, active.substring(6));
    }

    /** Shuffle: change style in place (quotes layer and photo loader follow the new style). */
    private void switchTo(String next) {
        if (!running) return;
        photoGen++;
        if (loader != null) { loader.interrupt(); loader = null; }
        photo = null; photoItem = null;   // bitmaps left to GC: the old loader may still be drawing with them
        prevPhoto = null;
        if (quote != null) { removeView(quote); quote = null; }
        active = next;
        if (next.equals("quotes")) {
            quote = new QuoteView(getContext());
            if (briefOn) quote.setAlpha(0f);
            addView(quote, 1, new LayoutParams(-1, -1));
        }
        if (next.equals("sayings")) {
            quote = new DesiDream.SayingsView(getContext());
            if (briefOn) quote.setAlpha(0f);
            addView(quote, 1, new LayoutParams(-1, -1));
        }
        if (next.startsWith("photo:")) startPhotos(getContext(), next.substring(6));
        txt.invalidate();
    }

    private volatile int photoGen;

    private void startPhotos(final Context c, final String pack) {
        final int gen = photoGen;
        loader = new Thread(new Runnable() {
            private boolean live() { return running && gen == photoGen; }
            @Override public void run() {
                java.util.List<Photos.Item> items = Photos.list(c, pack);
                // no list yet (offline / source busy): keep trying every minute instead of showing "Loading" forever
                while (live() && items.isEmpty() && !pack.equals("usb")) {
                    try { Thread.sleep(60000); } catch (InterruptedException e) { return; }
                    if (live()) items = Photos.list(c, pack);
                }
                // Rotation: the photos already on the TV (instant), in shuffled order, with each newly fetched photo
                // shown next. While a photo is up, the next new one downloads in the background (within the budget,
                // only while this pack has fewer than Photos.TARGET fresh ones), so a change never waits on the network.
                java.util.List<Photos.Item> pool = new java.util.ArrayList<>();
                for (Photos.Item x : items) if (Photos.cached(c, x)) pool.add(x);
                java.util.Collections.shuffle(pool);
                java.util.List<Photos.Item> todo = new java.util.ArrayList<>();
                for (Photos.Item x : items) if (!Photos.cached(c, x)) todo.add(x);
                java.util.Collections.shuffle(todo);
                int pi = 0, fails = 0;
                Photos.Item ready = null, last = null;
                while (live()) {
                    Photos.Item it;
                    if (ready != null) { it = ready; ready = null; }
                    else if (!pool.isEmpty()) { it = pool.get(pi++ % pool.size()); if (it == last && pool.size() > 1) it = pool.get(pi++ % pool.size()); }
                    else if (!todo.isEmpty() && Photos.fetch(c, todo.get(0))) { it = todo.remove(0); pool.add(it); }   // first run: nothing cached yet
                    else {
                        fails++;
                        try { Thread.sleep(Math.min(5 * 60000L, 15000L * fails)); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    if (!Photos.cached(c, it)) { pool.remove(it); continue; }   // replaced by a newer photo: never re-download it
                    int w = Math.max(1280, getWidth()), h = Math.max(720, getHeight());
                    Bitmap b = Photos.load(c, it, w, h);
                    if (b == null) {
                        pool.remove(it);   // the file went missing or is broken: drop it from this run
                        fails++;
                        try { Thread.sleep(Math.min(5 * 60000L, 15000L * fails)); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    fails = 0;
                    if (!live()) { b.recycle(); return; }
                    Bitmap old = prevPhoto;
                    prevPhoto = photo;
                    photo = b;
                    photoItem = it;
                    last = it;
                    photoSince = System.currentTimeMillis();
                    post(new Runnable() { @Override public void run() { txt.invalidate(); } });
                    long until = photoSince + PHOTO_EVERY;
                    if (old != null) { try { Thread.sleep(2500); } catch (InterruptedException e) { return; } old.recycle(); }
                    // fetch the next new photo now, while this one is on screen
                    if (!todo.isEmpty() && Photos.mayDownload(c)) {
                        int fresh = 0;
                        for (Photos.Item x : pool) if (Photos.fresh(c, x)) fresh++;
                        if (fresh < Photos.TARGET) {
                            Photos.Item cand = todo.remove(0);
                            if (Photos.fetch(c, cand)) { ready = cand; pool.add(cand); }
                        }
                    }
                    long wait = until - System.currentTimeMillis();
                    if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                }
                if (photo == null) post(new Runnable() { @Override public void run() { txt.invalidate(); } });
            }
        });
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        if (loader != null) loader.interrupt();
        removeCallbacks(briefToggle);
    }

    public static String saved(Context c) { return Theme.prefs(c).getString("dream_mode", "nebula"); }
    public static void save(Context c, String m) { Theme.prefs(c).edit().putString("dream_mode", m).apply(); }

    // =================================================================== background layer

    private final class Bg extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix m = new Matrix();
        private final long start = System.nanoTime();
        private long last = start;
        private float[] stars, warp;
        private RadialGradient[] blobs;
        private Shader skyGrad, warpGlow, mandalaBg;
        private final Path petal = new Path();

        Bg(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            long nowNs = System.nanoTime();
            float t = (nowNs - start) / 1e9f, dt = Math.min(0.1f, (nowNs - last) / 1e9f);
            last = nowNs;
            long now = System.currentTimeMillis();
            if (mode.equals("shuffle") && now - switched > 10 * 60 * 1000) {
                switched = now;
                final String next = nextShuffle(getContext(), active);
                DreamView.this.post(new Runnable() { @Override public void run() { switchTo(next); } });
            }
            int w = getWidth(), h = getHeight();
            switch (active) {
                case "aurora": aurora(c, w, h, t); break;
                case "warp": warp(c, w, h, dt); break;
                case "mandala": mandala(c, w, h, t); break;
                case "clock": minimalBg(c, w, h); postInvalidateDelayed(60000); return; // static
                case "quotes": case "briefing": case "panchang": wallpaper(c, w, h, t); break;
                case "desi": case "sayings": postInvalidateDelayed(DesiDream.background(c, theme, active, w, h, t)); return;
                default:
                    if (active.startsWith("photo:")) { if (!photos(c, w, h)) nebula(c, w, h, t); break; }
                    nebula(c, w, h, t); break;
            }
            postInvalidateOnAnimation();
        }

        private void nebula(Canvas c, int w, int h, float t) {
            Bitmap b = Nebula.galaxy(theme, w, h);
            // very slow Ken Burns drift: ~1 full cycle every 5 minutes
            float s = 1.12f + 0.04f * (float) Math.sin(t * 0.021);
            float dx = (float) Math.sin(t * 0.017) * w * 0.035f, dy = (float) Math.cos(t * 0.013) * h * 0.03f;
            m.reset();
            m.postScale(s, s, w / 2f, h / 2f);
            m.postTranslate(dx, dy);
            c.drawColor(0xFF000000);
            c.drawBitmap(b, m, p);
            twinkle(c, w, h, t, 70);
        }

        private void twinkle(Canvas c, int w, int h, float t, int n) {
            if (stars == null) {
                Random r = new Random(11);
                stars = new float[n * 4];
                for (int i = 0; i < stars.length; i += 4) {
                    stars[i] = r.nextFloat() * w; stars[i + 1] = r.nextFloat() * h;
                    stars[i + 2] = r.nextFloat() * 6.28f; stars[i + 3] = 1f + r.nextFloat() * 2f;
                }
            }
            p.setShader(null);
            for (int i = 0; i < stars.length; i += 4) {
                float a = 0.5f + 0.5f * (float) Math.sin(t * (0.4f + stars[i + 3] * 0.25f) + stars[i + 2]);
                p.setColor(((int) (a * 220) << 24) | 0xFFFFFF);
                c.drawCircle(stars[i], stars[i + 1], stars[i + 3] * (0.6f + a * 0.5f), p);
            }
        }

        private void aurora(Canvas c, int w, int h, float t) {
            if (skyGrad == null) skyGrad = new LinearGradient(0, 0, 0, h, 0xFF02040D, 0xFF06142A, Shader.TileMode.CLAMP);
            p.setShader(skyGrad);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            twinkle(c, w, h, t, 90);
            if (blobs == null) {
                int[] cols = {0xFF22F2A0, 0xFF1FA2FF, 0xFFB45CFF, 0xFF4BFFD1, theme.accent};
                blobs = new RadialGradient[cols.length];
                for (int i = 0; i < blobs.length; i++)
                    blobs[i] = new RadialGradient(0, 0, h * 0.5f, new int[]{(cols[i] & 0xFFFFFF) | 0x99000000,
                            (cols[i] & 0xFFFFFF) | 0x33000000, cols[i] & 0xFFFFFF}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
            }
            for (int i = 0; i < blobs.length; i++) {
                for (int k = 0; k < 3; k++) {
                    float ph = i * 1.3f + k * 2.1f;
                    float bx = w * (0.1f + 0.8f * (0.5f + 0.5f * (float) Math.sin(t * 0.02f * (1 + i * 0.2f) + ph)));
                    float by = h * (0.32f + 0.06f * (float) Math.sin(t * 0.035f + ph));
                    c.save();
                    c.translate(bx, by);
                    c.rotate(10 * (float) Math.sin(t * 0.03f + ph));
                    c.scale(0.45f, 1.25f);
                    p.setShader(blobs[i]);
                    c.drawCircle(0, 0, h * 0.5f, p);
                    c.restore();
                }
            }
            p.setShader(null);
        }

        private void warp(Canvas c, int w, int h, float dt) {
            int n = 220;
            if (warp == null) {
                warp = new float[n * 3];
                for (int i = 0; i < warp.length; i += 3) resetStar(i, true);
                warpGlow = new RadialGradient(w / 2f, h / 2f, h * 0.6f, (theme.glowB & 0xFFFFFF) | 0x40000000, 0x00000000, Shader.TileMode.CLAMP);
            }
            c.drawColor(0xFF000000);
            p.setShader(warpGlow);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            float cx = w / 2f, cy = h / 2f, f = h * 0.6f, speed = 0.16f * dt; // gentle, frame-rate independent
            for (int i = 0; i < warp.length; i += 3) {
                float z0 = warp[i + 2];
                warp[i + 2] -= speed;
                if (warp[i + 2] <= 0.03f) { resetStar(i, false); continue; }
                float x0 = cx + warp[i] / z0 * f, y0 = cy + warp[i + 1] / z0 * f;
                float x1 = cx + warp[i] / warp[i + 2] * f, y1 = cy + warp[i + 1] / warp[i + 2] * f;
                if (x1 < 0 || x1 > w || y1 < 0 || y1 > h) { resetStar(i, false); continue; }
                float bright = Math.min(1f, (1.2f - warp[i + 2]));
                p.setColor(((int) (bright * 255) << 24) | (i % 7 == 0 ? (theme.accent & 0xFFFFFF) : 0xFFFFFF));
                p.setStrokeWidth(0.8f + bright * 2f);
                c.drawLine(x0, y0, x1, y1, p);
            }
        }

        private void resetStar(int i, boolean anyDepth) {
            warp[i] = (rnd.nextFloat() - 0.5f) * 2.4f;
            warp[i + 1] = (rnd.nextFloat() - 0.5f) * 1.4f;
            warp[i + 2] = anyDepth ? 0.1f + rnd.nextFloat() * 1.1f : 1.2f;
        }

        private void mandala(Canvas c, int w, int h, float t) {
            float S = Math.min(w, h) * 0.44f, ox = w * 0.42f, oy = h / 2f; // fixed centre, left of the clock
            if (mandalaBg == null) mandalaBg = new RadialGradient(ox, oy, h, darken(theme.bottom), 0xFF000000, Shader.TileMode.CLAMP);
            p.setShader(mandalaBg);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            int[] cols = {theme.accent, 0xFFFF6F91, 0xFF7CF5C4, 0xFFFFC371, 0xFFB45CFF};
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f);
            for (int ring = 0; ring < 5; ring++) {
                int n = 8 + ring * 4;
                float r = S * (0.14f + ring * 0.15f), pl = S * (0.1f + ring * 0.035f);
                p.setColor((cols[ring] & 0xFFFFFF) | ((235 - ring * 22) << 24));
                c.save();
                c.translate(ox, oy);
                c.rotate((ring % 2 == 0 ? 1 : -1) * t * (1.5f + ring * 0.6f));
                petal.reset();
                petal.moveTo(0, r);
                petal.quadTo(pl * 0.5f, r + pl * 0.5f, 0, r + pl);
                petal.quadTo(-pl * 0.5f, r + pl * 0.5f, 0, r);
                for (int k = 0; k < n; k++) {
                    c.drawPath(petal, p);
                    c.rotate(360f / n);
                }
                c.drawCircle(0, 0, r - 3, p);
                c.restore();
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(ox, oy, S * 0.1f, 0xFFFFFFFF, (theme.accent & 0xFFFFFF), Shader.TileMode.CLAMP));
            c.drawCircle(ox, oy, S * 0.1f, p);
            p.setShader(null);
        }

        /** Theme wallpaper with a slow drift and a dark veil for text. */
        private void wallpaper(Canvas c, int w, int h, float t) {
            Bitmap b = Nebula.get(theme, w, h);
            float s = 1.08f + 0.03f * (float) Math.sin(t * 0.02);
            m.reset();
            m.postScale(s, s, w / 2f, h / 2f);
            m.postTranslate((float) Math.sin(t * 0.015) * w * 0.02f, 0);
            c.drawColor(0xFF000000);
            c.drawBitmap(b, m, p);
            c.drawColor(0x8C000000);
        }

        /** Current photo with a slow Ken Burns zoom, cross-fading from the previous one. */
        private boolean photos(Canvas c, int w, int h) {
            Bitmap cur = photo, prev = prevPhoto;
            if (cur == null || cur.isRecycled()) return false;
            c.drawColor(0xFF000000);
            float age = (System.currentTimeMillis() - photoSince) / 1000f;
            float fade = Math.min(1f, age / 2f);
            if (prev != null && !prev.isRecycled() && fade < 1f) drawPhoto(c, prev, w, h, 32f + age, 255);
            drawPhoto(c, cur, w, h, age, (int) (fade * 255));
            // soft bottom gradient for the caption + clock
            p.setShader(new LinearGradient(0, h * 0.62f, 0, h, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.62f, w, h, p);
            p.setShader(null);
            return true;
        }

        private void drawPhoto(Canvas c, Bitmap b, int w, int h, float age, int alpha) {
            float base = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
            float s = base * (1.02f + Math.min(age, 34f) * 0.0025f);
            m.reset();
            m.postScale(s, s);
            m.postTranslate((w - b.getWidth() * s) / 2f, (h - b.getHeight() * s) / 2f);
            p.setAlpha(alpha);
            c.drawBitmap(b, m, p);
            p.setAlpha(255);
        }

        private void minimalBg(Canvas c, int w, int h) {
            c.drawColor(0xFF000000);
            float cx = w / 2f, cy = h * 0.42f;
            p.setShader(new RadialGradient(cx, cy, h * 0.5f, (theme.accent & 0xFFFFFF) | 0x1E000000, 0, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, h * 0.5f, p);
            p.setShader(null);
        }
    }

    // =================================================================== text layer (static)

    private final class Txt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

        Txt(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            if (now - wxChecked > 10 * 60 * 1000) {
                wxChecked = now;
                Weather.refresh(getContext(), false, new Weather.Listener() {
                    @Override public void onWeather(Weather nw) { wx = nw; invalidate(); }
                });
                Weather c2 = Weather.cached(getContext());
                if (c2 != null) wx = c2;
            }
            int w = getWidth(), h = getHeight();
            // burn-in nudge: a few pixels, changes only every 10 minutes
            int slot = (Calendar.getInstance().get(Calendar.MINUTE) / 10) % 4;
            float nx = (slot == 1 || slot == 2) ? h * 0.008f : 0, ny = slot >= 2 ? h * 0.008f : 0;
            c.save();
            c.translate(nx, ny);
            boolean briefLayout = briefOn || active.equals("briefing") || active.equals("panchang");
            if (!briefLayout && DesiDream.owns(active)) {
                DesiDream.drawText(getContext(), c, theme, active, w, h, wx);   // Desi Clock / Sayings, in the screensaver language
            } else if (active.startsWith("photo:") || active.equals("quotes") || briefLayout) {
                // clock + weather bottom-right, caption bottom-left (photos) — all still
                float x = w * 0.84f, y = h * (active.startsWith("photo:") && !briefLayout ? 0.82f : 0.2f);
                BigClock.draw(getContext(), c, theme, x, y, h * 0.11f, 235);
                date(c, x, y + h * 0.055f, h * 0.024f);
                weather(c, x, y + h * 0.105f, h * 0.026f, false);
                Photos.Item it = photoItem;
                if (active.startsWith("photo:") && !briefLayout) {
                    p.setShader(null);
                    p.setTextAlign(Paint.Align.LEFT);
                    p.setShadowLayer(6, 0, 2, 0xCC000000);
                    if (it != null) {
                        p.setTypeface(Ui.font(getContext(), "Poppins-Medium"));
                        p.setTextSize(h * 0.034f);
                        p.setColor(0xF2FFFFFF);
                        c.drawText(ellipsize(it.title, p, w * 0.55f), w * 0.05f, h * 0.88f, p);
                        p.setTypeface(Ui.font(getContext(), "Poppins-Light"));
                        p.setTextSize(h * 0.022f);
                        p.setColor(0xCCFFFFFF);
                        c.drawText(ellipsize("Photo: " + it.credit, p, w * 0.55f), w * 0.05f, h * 0.925f, p);
                    } else if (photo == null) {
                        p.setTypeface(Ui.font(getContext(), "Poppins-Light"));
                        p.setTextSize(h * 0.026f);
                        p.setColor(0xCCFFFFFF);
                        c.drawText(active.equals("photo:usb") ? "Plug in a USB drive with photos" : "Loading photos… (needs internet the first time)", w * 0.05f, h * 0.9f, p);
                    }
                    p.clearShadowLayer();
                }
            } else if (active.equals("mandala")) {
                float x = w * 0.82f, y = h * 0.36f;
                BigClock.draw(getContext(), c, theme, x, y, h * 0.13f, 235);
                date(c, x, y + h * 0.065f, h * 0.026f);
                weather(c, x, y + h * 0.13f, h * 0.03f, false);
            } else {
                float cx = w / 2f, cy = h * 0.42f, size = h * (active.equals("clock") ? 0.24f : 0.2f);
                BigClock.draw(getContext(), c, theme, cx, cy, size, 240);
                date(c, cx, cy + size * 0.42f, size * 0.17f);
                weather(c, cx, cy + size * 0.8f, size * 0.19f, true);
            }
            c.restore();
            // next redraw exactly when the minute changes
            Calendar cal = Calendar.getInstance();
            long ms = (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND) + 30;
            postInvalidateDelayed(ms);
        }

        private String ellipsize(String s, Paint p, float max) {
            if (p.measureText(s) <= max) return s;
            while (s.length() > 3 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        private void date(Canvas c, float cx, float y, float size) {
            p.setShader(null);
            p.setTypeface(Ui.font(getContext(), "Poppins-Light"));
            p.setTextSize(size);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xDDFFFFFF);
            p.setLetterSpacing(0.08f);
            c.drawText(String.valueOf(DateFormat.format("EEEE  ·  d MMMM", Calendar.getInstance())).toUpperCase(), cx, y, p);
            p.setLetterSpacing(0f);
        }

        private void weather(Canvas c, float cx, float y, float size, boolean days) {
            if (wx == null) return;
            p.setShader(null);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Ui.font(getContext(), "Poppins-Light"));
            p.setTextSize(size);
            p.setColor(0xF0FFFFFF);
            p.setShadowLayer(size * 0.3f, 0, 0, 0x99000000);
            if (days) {
                c.drawText(Weather.icon(wx.code, wx.isDay) + "  " + Weather.deg(wx.temp) + "   " + Weather.label(wx.code)
                        + "   ·   Feels " + Weather.deg(wx.feels) + "   ·   Humidity " + wx.humidity + "%", cx, y, p);
            } else {
                c.drawText(Weather.icon(wx.code, wx.isDay) + " " + Weather.deg(wx.temp) + "  ·  " + Weather.label(wx.code), cx, y, p);
                p.setTextSize(size * 0.8f);
                p.setColor(0xCCFFFFFF);
                c.drawText("Feels " + Weather.deg(wx.feels) + "  ·  Humidity " + wx.humidity + "%", cx, y + size * 1.4f, p);
            }
            if (days) {
                for (Weather.Alert a : wx.alerts()) if (a.level > 0) {
                    p.setTextSize(size * 0.7f);
                    p.setColor(a.level == 2 ? 0xFFFF9A8A : 0xFFFFD08A);
                    c.drawText(a.icon + "  " + a.title, cx, y + size * 3.1f, p);
                    break;
                }
            }
            if (days && wx.days.size() >= 5) {
                float step = size * 4.2f, x0 = cx - step * 2;
                p.setTextSize(size * 0.62f);
                for (int i = 0; i < 5; i++) {
                    Weather.Day d = wx.days.get(i);
                    float x = x0 + step * i;
                    p.setColor(0xB3FFFFFF);
                    c.drawText(d.name, x, y + size * 1.3f, p);
                    p.setColor(0xF0FFFFFF);
                    c.drawText(Weather.icon(d.code, true) + " " + Weather.deg(d.max) + " / " + Weather.deg(d.min), x, y + size * 2.15f, p);
                }
            }
            p.clearShadowLayer();
        }
    }

    // =================================================================== briefing layer

    /** Pages of markets / scores / headlines; still text, fades only when the page changes. */
    private final class BriefView extends View {
        private int page;
        private final android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable next = new Runnable() {
            @Override public void run() {
                animate().alpha(0f).setDuration(800).withEndAction(new Runnable() {
                    @Override public void run() { page = nextPage(page); invalidate(); animate().alpha(1f).setDuration(800).start(); }
                }).start();
                postDelayed(this, INFO_EVERY);
            }
        };
        private final Runnable reload = new Runnable() {
            @Override public void run() {
                Briefing.refresh(getContext(), true, new Briefing.Done() { @Override public void done() { invalidate(); } });
                postDelayed(this, 10 * 60000);
            }
        };

        private final boolean fixed;

        /** Pages: 0-2 markets, 3 cricket, 4 football · AI, 5 space · market news, 6 Panchang. Skips switched-off ones. */
        private boolean pageOn(int p) {
            Context c = getContext();
            switch (p) {
                // market pages only while an Indian or US market is trading; otherwise just scores and news
                case 0: case 1: case 2: return Features.on(c, Features.MARKETS) && Briefing.marketsOpen();
                case 3: return Features.on(c, Features.CRICKET) || Features.news(c, "cricket");
                case 4: return Features.news(c, "football") || Features.news(c, "ai");
                case 5: return Features.news(c, "space") || Features.news(c, "markets");
                default: return Features.on(c, Features.PANCHANG);
            }
        }

        private int nextPage(int p) {
            for (int i = 1; i <= 7; i++) if (pageOn((p + i) % 7)) return (p + i) % 7;
            return p;
        }

        BriefView(Context c) {
            super(c);
            fixed = false;
            if (!pageOn(0)) page = nextPage(0);
            post(reload);
            postDelayed(next, INFO_EVERY);
        }

        /** Stays on one page (e.g. 6 = Panchang) and redraws every minute. */
        BriefView(Context c, int page) {
            super(c);
            fixed = true;
            this.page = page;
            postDelayed(minute, 60000);
        }

        private final Runnable minute = new Runnable() { @Override public void run() { invalidate(); postDelayed(this, 60000); } };

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(next);
            removeCallbacks(reload);
        }

        private void text(Canvas c, String s, float x, float y, float size, int color, String font) {
            tp.setTypeface(Ui.font(getContext(), font));
            tp.setTextSize(size);
            tp.setColor(color);
            tp.setShadowLayer(6, 0, 2, 0xCC000000);
            c.drawText(s, x, y, tp);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float x = w * 0.06f, y = h * 0.12f;
            String[] titles = {"MARKETS", "INDIA STOCKS", "US TECH & AI STOCKS", "CRICKET", "FOOTBALL  ·  AI", "SPACE  ·  MARKET NEWS", "PANCHANG"};
            Context cx0 = getContext();
            if (page == 4) titles[4] = join(Features.news(cx0, "football") ? "FOOTBALL" : null, Features.news(cx0, "ai") ? "AI" : null);
            if (page == 5) titles[5] = join(Features.news(cx0, "space") ? "SPACE" : null, Features.news(cx0, "markets") ? "MARKET NEWS" : null);
            if (page == 6) titles[6] = PanchangLang.pick(Lang.panchang(cx0), "PANCHANG", "पंचांग", "પંચાંગ", "पंचांग");
            text(c, titles[page], x, y, h * 0.03f, Ui.lighten(theme.accent | 0xFF000000, 0.45f), "Poppins-SemiBold");
            y += h * 0.07f;
            if (page == 1 || page == 2) {
                java.util.List<Briefing.Quote> qs = Briefing.group(getContext(), page == 1 ? Briefing.MAIN + 2 : Briefing.MAIN + 3);
                if (qs.isEmpty()) { text(c, "Loading…", x, y, h * 0.035f, 0xCCFFFFFF, "Poppins-Light"); return; }
                if (qs.size() > 20) {   // only 20 fit: show the biggest movers, so nothing important is cut off by list order
                    qs = new java.util.ArrayList<>(qs);
                    java.util.Collections.sort(qs, new java.util.Comparator<Briefing.Quote>() {
                        @Override public int compare(Briefing.Quote a, Briefing.Quote b) { return Double.compare(Math.abs(b.pct), Math.abs(a.pct)); }
                    });
                    qs = qs.subList(0, 20);
                    text(c, "TOP 20 MOVERS", x + w * 0.5f, y - h * 0.07f, h * 0.02f, 0x99FFFFFF, "Poppins-Light");
                }
                float colW = w * 0.17f;
                for (int i = 0; i < Math.min(20, qs.size()); i++) {
                    Briefing.Quote q = qs.get(i);
                    float cx = x + (i % 4) * colW, cy = y + (i / 4) * h * 0.15f;
                    text(c, q.name, cx, cy, h * 0.022f, 0xCCFFFFFF, "Poppins-Regular");
                    text(c, Briefing.price(q), cx, cy + h * 0.042f, h * 0.034f, 0xFFFFFFFF, "Poppins-SemiBold");
                    text(c, Briefing.change(q), cx, cy + h * 0.075f, h * 0.022f, q.up() ? 0xFF4CD964 : 0xFFFF6B6B, "Poppins-SemiBold");
                }
                text(c, "Delayed ~15 min · Yahoo Finance", x, h * 0.95f, h * 0.02f, 0x99FFFFFF, "Poppins-Light");
                return;
            }
            if (page == 0) {
                java.util.List<Briefing.Quote> qs = new java.util.ArrayList<>();
                for (int gi = 0; gi < Briefing.MAIN; gi++) qs.addAll(Briefing.group(getContext(), gi));
                if (qs.isEmpty()) { text(c, "Loading markets…", x, y, h * 0.035f, 0xCCFFFFFF, "Poppins-Light"); return; }
                float colW = w * 0.22f;
                for (int i = 0; i < qs.size(); i++) {
                    Briefing.Quote q = qs.get(i);
                    float cx = x + (i % 3) * colW, cy = y + (i / 3) * h * 0.13f;
                    text(c, q.name, cx, cy, h * 0.024f, 0xCCFFFFFF, "Poppins-Regular");
                    text(c, Briefing.price(q), cx, cy + h * 0.045f, h * 0.04f, 0xFFFFFFFF, "Poppins-SemiBold");
                    text(c, Briefing.change(q), cx, cy + h * 0.08f, h * 0.024f, q.up() ? 0xFF4CD964 : 0xFFFF6B6B, "Poppins-SemiBold");
                }
                text(c, "Delayed ~15 min · Yahoo Finance", x, h * 0.95f, h * 0.02f, 0x99FFFFFF, "Poppins-Light");
            } else if (page == 3) {
                java.util.List<Briefing.Score> sc = Briefing.scores(getContext());
                if (sc.isEmpty()) { text(c, "No international or IPL matches right now", x, y, h * 0.03f, 0xCCFFFFFF, "Poppins-Light"); y += h * 0.07f; }
                for (int i = 0; i < Math.min(4, sc.size()); i++) {
                    Briefing.Score s = sc.get(i);
                    text(c, (s.live ? "● " : "") + s.text, x, y, h * 0.036f, s.live ? 0xFFFFFFFF : 0xDDFFFFFF, s.live ? "Poppins-SemiBold" : "Poppins-Regular");
                    y += h * 0.065f;
                }
                headlines(c, Briefing.stories(getContext(), "cricket"), x, y + h * 0.03f, w * 0.68f, 4);
            } else if (page == 4) {
                y = headlines(c, Briefing.stories(getContext(), "football"), x, y, w * 0.68f, 3);
                headlines(c, Briefing.stories(getContext(), "ai"), x, y + h * 0.04f, w * 0.68f, 3);
            } else if (page == 6) {
                long now = System.currentTimeMillis();
                Vedic.Day vd = Vedic.today(getContext());
                Panchang p = vd.p;
                String lg = Lang.panchang(getContext());   // Panchang language (English keeps the old wording)
                boolean en = Lang.EN.equals(lg);
                text(c, PanchangLang.date(lg, p) + "   ·   " + (en ? Vedic.VAAR_GU[vd.weekday] : PanchangLang.vaar(lg, vd.weekday)), x, y, h * 0.05f, 0xFFFFD166, "Poppins-SemiBold");
                y += h * 0.07f;
                text(c, (en ? PanchangLang.tithi(lg, p) : PanchangLang.paksha(lg, p))
                        + "  ·  " + PanchangLang.nakshatra(lg, p.nakshatra) + "  ·  " + PanchangLang.pick(lg, "Vikram Samvat ", "विक्रम संवत ", "વિક્રમ સંવત ", "विक्रम संवत ")
                        + PanchangLang.num(lg, p.samvat), x, y, h * 0.032f, 0xFFFFFFFF, "Poppins-Regular");
                y += h * 0.09f;
                Vedic.Slot cs = Vedic.current(vd, now);
                if (cs != null) {
                    text(c, PanchangLang.pick(lg, "Choghadiya now", "अभी का चौघड़िया", "હાલનું ચોઘડિયું", "आताचा चौघडिया"), x, y, h * 0.024f, 0xAAFFFFFF, "Poppins-Light");
                    text(c, PanchangLang.chog(lg, cs.name) + "   " + PanchangLang.hm(lg, cs.from) + " – " + PanchangLang.hm(lg, cs.to), x, y + h * 0.05f, h * 0.045f,
                            cs.quality > 0 ? 0xFF4CD964 : cs.quality == 0 ? 0xFFFFD166 : 0xFFFF6B6B, "Poppins-SemiBold");
                    y += h * 0.12f;
                }
                text(c, PanchangLang.slot(lg, "Rahu Kaal") + "  " + PanchangLang.hm(lg, vd.rahu.from) + " – " + PanchangLang.hm(lg, vd.rahu.to) + "      "
                        + (en ? "Abhijit" : PanchangLang.slot(lg, "Abhijit Muhurat")) + "  " + PanchangLang.hm(lg, vd.abhijit.from)
                        + " – " + PanchangLang.hm(lg, vd.abhijit.to), x, y, h * 0.03f, 0xEEFFFFFF, "Poppins-Regular");
                y += h * 0.06f;
                text(c, PanchangLang.pick(lg, "Sunrise ", "सूर्योदय ", "સૂર્યોદય ", "सूर्योदय ") + PanchangLang.hm(lg, vd.sunrise) + "   ·   "
                        + PanchangLang.pick(lg, "Sunset ", "सूर्यास्त ", "સૂર્યાસ્ત ", "सूर्यास्त ") + PanchangLang.hm(lg, vd.sunset), x, y, h * 0.03f, 0xCCFFFFFF, "Poppins-Light");
                y += h * 0.09f;
                java.util.List<Vedic.Fest> fs = Vedic.upcoming(getContext(), 45);
                if (!fs.isEmpty()) {
                    text(c, PanchangLang.pick(lg, "Coming up", "आने वाले त्योहार", "આવનારા તહેવારો", "आगामी सण"), x, y, h * 0.024f, 0xAAFFFFFF, "Poppins-Light");
                    for (int i = 0; i < Math.min(3, fs.size()); i++) {
                        y += h * 0.05f;
                        text(c, PanchangLang.greg(lg, "EEE d MMM", fs.get(i).day) + "   " + fs.get(i).name, x, y, h * 0.032f, 0xFFFFFFFF, "Poppins-Regular");
                    }
                }
            } else {
                y = headlines(c, Briefing.stories(getContext(), "space"), x, y, w * 0.68f, 3);
                headlines(c, Briefing.stories(getContext(), "markets"), x, y + h * 0.04f, w * 0.68f, 3);
            }
        }

        private String join(String a, String b) { return a == null ? (b == null ? "" : b) : b == null ? a : a + "  ·  " + b; }

        private float headlines(Canvas c, java.util.List<Briefing.Story> st, float x, float y, float maxW, int n) {
            int h = getHeight();
            for (int i = 0; i < Math.min(n, st.size()); i++) {
                Briefing.Story s = st.get(i);
                tp.setTypeface(Ui.font(getContext(), "Poppins-Regular"));
                tp.setTextSize(h * 0.034f);
                tp.setColor(0xFFFFFFFF);
                tp.setShadowLayer(6, 0, 2, 0xCC000000);
                android.text.StaticLayout l = new android.text.StaticLayout(s.title, tp, (int) maxW, android.text.Layout.Alignment.ALIGN_NORMAL, 1.05f, 0, false);
                c.save();
                c.translate(x, y - h * 0.03f);
                l.draw(c);
                c.restore();
                y += l.getHeight() - h * 0.005f;
                text(c, s.source + (s.time > 0 ? "  ·  " + Briefing.ago(s.time) : ""), x, y, h * 0.022f, 0xAAFFFFFF, "Poppins-Light");
                y += h * 0.055f;
            }
            return y;
        }
    }

    // =================================================================== quotes layer

    /** One fact / quote at a time, centred; fades only when it changes (every 2 min). */
    private final class QuoteView extends View {
        private final java.util.List<String[]> items = new java.util.ArrayList<>();
        private int idx;
        private final android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable next = new Runnable() {
            @Override public void run() {
                animate().alpha(0f).setDuration(900).withEndAction(new Runnable() {
                    @Override public void run() { idx = (idx + 1) % Math.max(1, items.size()); invalidate(); animate().alpha(1f).setDuration(900).start(); }
                }).start();
                postDelayed(this, INFO_EVERY);
            }
        };

        QuoteView(Context c) {
            super(c);
            try (java.io.InputStream in = c.getAssets().open("quotes.json")) {
                byte[] b = new byte[in.available()];
                int n = in.read(b);
                org.json.JSONArray a = new org.json.JSONArray(new String(b, 0, n, "UTF-8"));
                for (int i = 0; i < a.length(); i++) {
                    org.json.JSONObject o = a.getJSONObject(i);
                    if (DesiDream.inFacts(o.getString("k"), rnd))
                        items.add(new String[]{o.getString("k"), o.getString("t"), o.optString("m", ""), o.optString("by", "")});
                }
            } catch (Exception ignored) { }
            java.util.Collections.shuffle(items);
            postDelayed(next, INFO_EVERY);
        }

        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(next); }

        @Override protected void onDraw(Canvas c) {
            if (items.isEmpty()) return;
            String[] q = items.get(idx);
            int w = getWidth(), h = getHeight();
            float maxW = w * 0.66f, x = w * 0.08f;
            String kind = q[0].equals("fact") ? "DID YOU KNOW?" : q[0].equals("quote") ? "QUOTE" : q[0].equals("hi") ? "हिंदी  ·  HINDI"
                    : q[0].equals("gu") ? "ગુજરાતી  ·  GUJARATI" : q[0].equals("mr") ? "मराठी  ·  MARATHI" : "संस्कृत  ·  SANSKRIT";
            tp.setTypeface(Ui.font(getContext(), "Poppins-Medium"));
            tp.setTextSize(h * 0.026f);
            tp.setColor(Ui.lighten(theme.accent | 0xFF000000, 0.45f));
            tp.setLetterSpacing(0.15f);
            tp.setShadowLayer(6, 0, 2, 0xCC000000);
            float y = h * 0.34f;
            c.drawText(kind, x, y, tp);
            tp.setLetterSpacing(0f);
            boolean fact = q[0].equals("fact");
            tp.setTypeface(Ui.font(getContext(), fact || q[0].equals("quote") ? "Poppins-Light" : "Poppins-Regular"));
            tp.setTextSize(h * (q[1].length() > 90 ? 0.048f : 0.06f));
            tp.setColor(0xFFFFFFFF);
            android.text.StaticLayout main = new android.text.StaticLayout((fact ? "" : "“") + q[1] + (fact ? "" : "”"), tp, (int) maxW,
                    android.text.Layout.Alignment.ALIGN_NORMAL, 1.15f, 0, false);
            c.save();
            c.translate(x, y + h * 0.03f);
            main.draw(c);
            c.restore();
            y += h * 0.03f + main.getHeight() + h * 0.03f;
            if (!q[2].isEmpty()) {
                tp.setTypeface(Ui.font(getContext(), "Poppins-Light"));
                tp.setTextSize(h * 0.03f);
                tp.setColor(0xE6FFFFFF);
                android.text.StaticLayout mean = new android.text.StaticLayout(q[2], tp, (int) maxW, android.text.Layout.Alignment.ALIGN_NORMAL, 1.1f, 0, false);
                c.save();
                c.translate(x, y);
                mean.draw(c);
                c.restore();
                y += mean.getHeight() + h * 0.02f;
            }
            if (!q[3].isEmpty()) {
                tp.setTypeface(Ui.font(getContext(), "Poppins-Medium"));
                tp.setTextSize(h * 0.028f);
                tp.setColor(Ui.lighten(theme.accent | 0xFF000000, 0.45f));
                c.drawText("— " + q[3], x, y + h * 0.03f, tp);
            }
        }
    }

    private static int darken(int c) {
        int r = ((c >> 16) & 0xFF) * 5 / 10, g = ((c >> 8) & 0xFF) * 5 / 10, b = (c & 0xFF) * 5 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

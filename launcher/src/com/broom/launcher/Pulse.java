package com.broom.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pop-up updates on a rhythm, shown with the same bottom-right card as the live-score pop-ups, over any
 * app or HDMI input (never on Broom's own home screen, which shows all of this already):
 * market pulse, live cricket score, weather heads-ups, a new headline, Panchang moments and a
 * "time for a break" nudge. Each has its own interval (0 = off); nothing shows during quiet hours.
 * Weather, headlines and Panchang only pop up when there is something new.
 */
final class Pulse {
    static final String MARKET = "market", CRICKET = "cricket", WEATHER = "weather", NEWS = "news",
            PANCHANG = "panchang", WELLNESS = "wellness";
    static final String[] KINDS = {MARKET, CRICKET, WEATHER, NEWS, PANCHANG, WELLNESS};
    static final String[] LABELS = {"Market pulse", "Live cricket score", "Weather heads-ups", "Top headline",
            "Panchang moments", "Break reminder"};
    /** Default minutes; 0 = off. Panchang is on/off only (checked every minute). */
    private static final int[] DEFAULTS = {30, 15, 30, 30, 0, 0};

    static Pulse instance;   // set while the button helper runs (same process as the launcher)

    private final android.accessibilityservice.AccessibilityService svc;
    private final ScorePopup popup;
    private final Handler h = new Handler();
    private final long[] last = new long[KINDS.length];
    private long watchingSince;
    private boolean running;
    String foreground;

    Pulse(android.accessibilityservice.AccessibilityService s, ScorePopup p) { svc = s; popup = p; }

    // ------------------------------------------------------------------ settings
    static SharedPreferences prefs(Context c) { return Theme.prefs(c); }

    static int minutes(Context c, String kind) {
        int i = idx(kind);
        return prefs(c).getInt("pulse_" + kind, DEFAULTS[i]);
    }

    static void setMinutes(Context c, String kind, int m) { prefs(c).edit().putInt("pulse_" + kind, m).apply(); }

    static boolean quietHours(Context c) { return prefs(c).getBoolean("pulse_quiet", true); }

    static void setQuietHours(Context c, boolean on) { prefs(c).edit().putBoolean("pulse_quiet", on).apply(); }

    static String describe(Context c, String kind) {
        int m = minutes(c, kind);
        if (m == 0) return "Off";
        if (kind.equals(PANCHANG)) return "On";
        if (kind.equals(WELLNESS)) return "after " + (m % 60 == 0 ? (m / 60) + " h" : m + " min") + " of watching";
        if (kind.equals(WEATHER) || kind.equals(NEWS)) return "check every " + m + " min, only when new";
        if (kind.equals(CRICKET)) return "every " + m + " min during India / IPL matches";
        return "every " + m + " min while markets are open";
    }

    private static int idx(String kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return i;
        return 0;
    }

    // ------------------------------------------------------------------ loop
    void start() {
        if (running) return;
        running = true;
        long now = System.currentTimeMillis();
        for (int i = 0; i < last.length; i++) last[i] = now;   // first updates come one interval after start
        watchingSince = now;
        instance = this;
        h.postDelayed(tick, 60000);
    }

    void stop() { running = false; h.removeCallbacks(tick); if (instance == this) instance = null; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            h.postDelayed(this, 60000);
            android.os.PowerManager pm = (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
            long now = System.currentTimeMillis();
            if (pm != null && !pm.isInteractive()) { watchingSince = now; return; }
            if (quiet(svc)) return;
            if (svc.getPackageName().equals(foreground)) return;      // Broom home already shows it all
            new Thread(new Runnable() { @Override public void run() { check(false); } }).start();
        }
    };

    private static boolean quiet(Context c) {
        if (!quietHours(c)) return false;
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hr >= 23 || hr < 7;
    }

    /** Background thread: works out at most one card per minute (the first that is due and has something to say). */
    private void check(boolean test) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < KINDS.length; i++) {
            int m = minutes(svc, KINDS[i]);
            if (m == 0 && !test) continue;
            boolean due = KINDS[i].equals(PANCHANG) || KINDS[i].equals(WELLNESS) || now - last[i] >= m * 60000L;
            if (!due && !test) continue;
            String[] card = null;
            try { card = build(KINDS[i], test); } catch (Exception ignored) { }
            if (!KINDS[i].equals(PANCHANG) && !KINDS[i].equals(WELLNESS)) last[i] = now;
            if (card != null) { post(card); if (!test) return; sleep(11000); }
        }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }

    private void post(final String[] c) {
        h.post(new Runnable() { @Override public void run() { popup.card(c[0], c[1], (int) Long.parseLong(c[2], 16)); } });
    }

    /** "Test all": every type once, with sample text where there is nothing live right now. */
    static boolean testAll() {
        final Pulse p = instance;
        if (p == null) return false;
        new Thread(new Runnable() { @Override public void run() { p.check(true); } }).start();
        return true;
    }

    // ------------------------------------------------------------------ cards
    private String[] build(String kind, boolean test) {
        switch (kind) {
            case MARKET: return market(test);
            case CRICKET: return cricket(test);
            case WEATHER: return weather(test);
            case NEWS: return news(test);
            case PANCHANG: return panchang(test);
            default: return wellness(test);
        }
    }

    private String[] market(boolean test) {
        if (!Features.on(svc, Features.MARKETS)) return null;
        Calendar k = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        int dow = k.get(Calendar.DAY_OF_WEEK), mins = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        boolean weekday = dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
        boolean india = weekday && mins >= 9 * 60 + 15 && mins <= 15 * 60 + 30;
        boolean us = (weekday && mins >= 19 * 60) || (dow >= Calendar.TUESDAY && dow <= Calendar.SATURDAY && mins <= 90);
        if (!india && !us && !test) return null;
        if (!india && !us) india = true;
        Briefing.fetchMarketsIfStale(svc);
        List<Briefing.Quote> idx = Briefing.group(svc, india ? 0 : 1);
        if (idx.size() < 2) return null;
        String head = india ? "📈  MARKET PULSE · INDIA" : "📈  MARKET PULSE · US";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            Briefing.Quote q = idx.get(i);
            if (b.length() > 0) b.append("   ·   ");
            b.append(q.name).append(' ').append(Briefing.price(q)).append(' ').append(Briefing.change(q));
        }
        Briefing.Quote top = null;
        for (Briefing.Quote q : Briefing.group(svc, india ? Briefing.WL_INDIA : Briefing.WL_US))
            if (top == null || Math.abs(q.pct) > Math.abs(top.pct)) top = q;
        if (top != null) b.append("\nTop mover: ").append(top.name).append(' ').append(Briefing.change(top));
        boolean up = idx.get(0).pct >= 0;
        return new String[]{head, b.toString(), up ? "FF4CD964" : "FFFF6B6B"};
    }

    private String[] cricket(boolean test) {
        if (!Features.on(svc, Features.CRICKET)) return null;
        try { if (Briefing.stale(svc, "scores", 3 * 60000)) Briefing.fetchScoresNow(svc); } catch (Exception ignored) { }
        for (Briefing.Score s : Briefing.scores(svc))
            if (s.india && s.live) return new String[]{"🏏  LIVE SCORE", s.text, "FFFFD166"};
        if (test) {
            List<Briefing.Score> all = Briefing.scores(svc);
            return new String[]{"🏏  LIVE SCORE (test)", all.isEmpty() ? "Updates every 15 min during India / IPL matches" : all.get(0).text, "FFFFD166"};
        }
        return null;
    }

    private String[] weather(boolean test) {
        Weather.refresh(svc, false, null);
        Weather w = Weather.cached(svc);
        if (w == null) return test ? new String[]{"🌦️  WEATHER (test)", "Heads-ups appear for rain, bad air and heat", "FF5AC8FA"} : null;
        Calendar cal = Calendar.getInstance();
        String day = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        // rain in the next 2 hours when it isn't raining now
        if (w.allHours.size() > 2 && w.allHours.get(0).rain < 40) {
            for (int i = 1; i <= 2; i++) {
                Weather.Hour x = w.allHours.get(i);
                if (x.rain >= 60 && once("rain:" + day + ":" + x.hour))
                    return new String[]{"☔  RAIN LIKELY", "Around " + x.time + " (" + x.rain + "% chance) in " + w.city + ". Carry an umbrella.", "FF5AC8FA"};
            }
        }
        if (w.aqi >= 150) {
            int band = w.aqi >= 300 ? 300 : w.aqi >= 200 ? 200 : 150;
            String what = band == 300 ? "hazardous" : band == 200 ? "very unhealthy" : "unhealthy";
            if (once("aqi:" + day + ":" + band))
                return new String[]{"😷  AIR QUALITY", "AQI " + w.aqi + " (" + what + "). Keep windows shut and exercise indoors.", "FFFF9F0A"};
        }
        if (w.feels >= 40 && once("heat:" + day))
            return new String[]{"🥵  HEAT", "Feels like " + Math.round(w.feels) + "°. Drink water and stay out of the sun.", "FFFF6B6B"};
        if (test) return new String[]{"🌦️  WEATHER (test)", Math.round(w.temp) + "° · feels " + Math.round(w.feels) + "° · AQI " + w.aqi + " in " + w.city, "FF5AC8FA"};
        return null;
    }

    private String[] news(boolean test) {
        if (!Features.anyNews(svc)) return null;
        try { if (Briefing.stale(svc, "news", 30 * 60000)) Briefing.refresh(svc, true, null); } catch (Exception ignored) { }
        String[] sections = {"cricket", "football", "tech", "ai", "space", "markets"};
        String[] names = {"Cricket", "Football", "Tech", "AI", "Space", "Market news"};
        Briefing.Story best = null; String bestSec = null;
        for (int i = 0; i < sections.length; i++)
            for (Briefing.Story s : Briefing.stories(svc, sections[i])) {
                if (seen("news:" + s.title.hashCode())) continue;
                if (best == null || s.time > best.time) { best = s; bestSec = names[i]; }
            }
        if (best == null) return null;
        if (!test) once("news:" + best.title.hashCode());
        return new String[]{"📰  " + bestSec.toUpperCase(Locale.US) + (best.source.isEmpty() ? "" : " · " + best.source), best.title, "FFBF5AF2"};
    }

    private String[] panchang(boolean test) {
        if (!Features.on(svc, Features.PANCHANG)) return null;
        long now = System.currentTimeMillis();
        Vedic.Day d = Vedic.today(svc);
        if (d.rahu != null && d.rahu.from - now > 0 && d.rahu.from - now <= 10 * 60000L && once("rahu:" + d.rahu.from))
            return new String[]{"🕉️  RAHU KAAL SOON", "Starts at " + Vedic.hm(d.rahu.from) + ", till " + Vedic.hm(d.rahu.to) + ".", "FFFF9F0A"};
        Vedic.Slot s = Vedic.current(d, now);
        if (s != null && Vedic.quality(s.name) > 0 && now - s.from < 5 * 60000L && once("chog:" + s.from))
            return new String[]{"🕉️  " + s.name.toUpperCase(Locale.US) + " CHOGHADIYA", "Auspicious time now, till " + Vedic.hm(s.to) + ".", "FF4CD964"};
        if (test && s != null) return new String[]{"🕉️  PANCHANG (test)", s.name + " choghadiya now, till " + Vedic.hm(s.to), "FFFFD166"};
        return null;
    }

    private String[] wellness(boolean test) {
        int m = minutes(svc, WELLNESS);
        long now = System.currentTimeMillis();
        if (test) return new String[]{"🧘  BREAK REMINDER (test)", "After a long stretch of watching: stand up, stretch, drink some water.", "FF64D2FF"};
        if (m == 0 || now - watchingSince < m * 60000L) return null;
        long hrs = (now - watchingSince) / 3600000L, mins = (now - watchingSince) / 60000L % 60;
        watchingSince = now;
        return new String[]{"🧘  TIME FOR A BREAK", "Watching for " + (hrs > 0 ? hrs + " h " : "") + mins + " min. Stand up, stretch, drink some water.", "FF64D2FF"};
    }

    // ------------------------------------------------------------------ "already shown" memory (kept 2 days)
    private Set<String> shown() {
        Set<String> s = new HashSet<>(prefs(svc).getStringSet("pulse_shown", new HashSet<String>()));
        String today = String.valueOf(System.currentTimeMillis() / 86400000L);
        if (!today.equals(prefs(svc).getString("pulse_shown_day", ""))) {
            Set<String> keep = new HashSet<>(prefs(svc).getStringSet("pulse_shown_prev", new HashSet<String>()));
            prefs(svc).edit().putStringSet("pulse_shown_prev", s).putStringSet("pulse_shown", new HashSet<String>())
                    .putString("pulse_shown_day", today).apply();
            keep.addAll(s);
            return keep;
        }
        s.addAll(prefs(svc).getStringSet("pulse_shown_prev", new HashSet<String>()));
        return s;
    }

    private boolean seen(String key) { return shown().contains(key); }

    /** True the first time a key is seen (and remembers it). */
    private synchronized boolean once(String key) {
        if (seen(key)) return false;
        Set<String> s = new HashSet<>(prefs(svc).getStringSet("pulse_shown", new HashSet<String>()));
        s.add(key);
        prefs(svc).edit().putStringSet("pulse_shown", s).apply();
        return true;
    }
}

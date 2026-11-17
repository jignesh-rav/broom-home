package com.broom.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "Still watching?" standby. If no remote key is pressed for the chosen time (default 3 h) while
 * an app is playing, a large card asks "Still watching?" with a visible countdown (default 180 s).
 * Any key dismisses it (and that key is swallowed); at 0 the TV goes to standby exactly like the
 * sleep timer in {@link KeyRemapService}.
 *
 * Runs inside KeyRemapService (accessibility overlay + key filter). Safety rules:
 *  - nothing about the countdown is ever persisted; only the settings are in prefs;
 *  - the idle clock restarts on service start, screen-on, boot and every settings change;
 *  - a clock jump, or an idle time over 2x the threshold, restarts the clock instead of firing;
 *  - the card removes itself after countdown + 30 s no matter what, and standby is never
 *    triggered unless the card was really on screen.
 * Skipped: HDMI / Live TV in front (unless enabled), Broom's own screens, the screensaver,
 * screen off, and the first 10 minutes after boot or power-on.
 */
final class StillWatching {
    private StillWatching() { }

    private static final String K_ON = "sw_on", K_HOURS = "sw_hours", K_COUNT = "sw_count", K_HDMI = "sw_hdmi";
    private static final int[] HOURS = {1, 2, 3, 4, 6};
    private static final int[] COUNTS = {60, 120, 180, 300};
    private static final int TEST_SECONDS = 15;
    private static final long TICK = 60000L;
    private static final long GRACE = 10 * 60000L;          // after boot / power-on
    private static final long JUMP = 2 * 60000L;            // wall clock vs elapsed drift that counts as a time jump

    /** Live TV / HDMI viewers (Mi TV droidlogic, AOSP, TCL / Realtek); anything with tvinput / livetv also counts. */
    private static final String[] HDMI_PKGS = {"com.android.tv", "com.droidlogic.tvinput", "com.droidlogic.droidlivetv",
            "com.droidlogic.tvsource", "com.mitv.tvplayer", "com.tcl.tv", "com.tcl.tvinput", "com.tcl.livetv",
            "com.tcl.inputsource", "com.realtek.tv", "com.realtek.tvinput", "com.google.android.tv"};

    // ---------------------------------------------------------------- settings

    static boolean on(Context c) { return Theme.prefs(c).getBoolean(K_ON, true); }
    static int hours(Context c) { return pick(Theme.prefs(c).getInt(K_HOURS, 3), HOURS, 3); }
    static int countdown(Context c) { return pick(Theme.prefs(c).getInt(K_COUNT, 180), COUNTS, 180); }
    static boolean onHdmi(Context c) { return Theme.prefs(c).getBoolean(K_HDMI, false); }

    private static int pick(int v, int[] allowed, int def) {
        for (int a : allowed) if (a == v) return v;
        return def;
    }

    private static String hoursText(int h) { return h == 1 ? "1 hour" : h + " hours"; }

    private static String secsText(int s) {
        return s % 60 == 0 ? (s / 60 == 1 ? "1 minute" : s / 60 + " minutes") + " (" + s + " s)" : s + " s";
    }

    static String label(Context c) {
        return on(c) ? "Still watching?: after " + hours(c) + " h idle (change)" : "Still watching?: Off (turn on)";
    }

    static void dialog(final Activity a) {
        final SharedPreferences p = Theme.prefs(a);
        final boolean on = on(a), hdmi = onHdmi(a);
        String[] labels = {
                "Still watching?: " + (on ? "On  (turn off)" : "Off  (turn on)"),
                "Ask after: " + hoursText(hours(a)) + " without a remote key press",
                "Countdown before standby: " + secsText(countdown(a)),
                "Also on HDMI inputs: " + (hdmi ? "On  (turn off)" : "Off  (turn on)"),
                "Test now (" + TEST_SECONDS + "-second countdown, TV stays on)"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Still watching? (standby when nobody is watching)")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int i) {
                        switch (i) {
                            case 0:
                                p.edit().putBoolean(K_ON, !on).apply();
                                reset();
                                Toast.makeText(a, on ? "Still watching? off" : "Still watching? on: asks after "
                                        + hoursText(hours(a)) + " without a key press", Toast.LENGTH_LONG).show();
                                dialog(a);
                                return;
                            case 1: {
                                String[] names = new String[HOURS.length];
                                for (int k = 0; k < HOURS.length; k++) names[k] = hoursText(HOURS[k]) + (HOURS[k] == 3 ? " (default)" : "");
                                choose(a, "Ask after no key press for…", names, new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w) {
                                        p.edit().putInt(K_HOURS, HOURS[w]).putBoolean(K_ON, true).apply();
                                        reset();
                                        dialog(a);
                                    }
                                });
                                return;
                            }
                            case 2: {
                                String[] names = new String[COUNTS.length];
                                for (int k = 0; k < COUNTS.length; k++) names[k] = secsText(COUNTS[k]) + (COUNTS[k] == 180 ? " (default)" : "");
                                choose(a, "Countdown before standby", names, new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d2, int w) {
                                        p.edit().putInt(K_COUNT, COUNTS[w]).apply();
                                        reset();
                                        dialog(a);
                                    }
                                });
                                return;
                            }
                            case 3:
                                p.edit().putBoolean(K_HDMI, !hdmi).apply();
                                reset();
                                Toast.makeText(a, hdmi ? "Not asked on HDMI inputs (the Fire TV remote's presses never reach the TV)"
                                        : "Also asks on HDMI inputs. Fire TV remote presses don't count: answer with the TV remote", Toast.LENGTH_LONG).show();
                                dialog(a);
                                return;
                            default:
                                if (!test()) Toast.makeText(a, "Needs the Broom button helper: re-run the installer", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private static void choose(Activity a, String title, String[] names, DialogInterface.OnClickListener l) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setItems(names, l).show();
    }

    // ---------------------------------------------------------------- state (main thread only, never persisted)

    private static AccessibilityService svc;
    private static Handler h;
    private static long lastKeyWall, lastKeyElapsed;      // idle clock: wall clock, cross-checked with elapsedRealtime
    private static long powerOnElapsed;                    // last screen-on / service start
    private static boolean dreaming;
    private static int resumed;                            // Broom activities currently resumed (home, briefing…)
    private static String foreground;                      // last app in front (not Broom, not system UI)
    private static BroadcastReceiver rx;
    private static Application.ActivityLifecycleCallbacks life;

    private static View card;
    private static TextView countText;
    private static View bar;
    private static boolean testCard, finished;
    private static long deadline, cardShownAt, cardSeconds;   // elapsedRealtime based
    private static int swallowCode = -1;
    private static long swallowAt;

    /** Called from KeyRemapService.onServiceConnected. */
    static void start(AccessibilityService s) {
        stop();
        svc = s;
        h = new Handler();
        reset();
        powerOnElapsed = SystemClock.elapsedRealtime();
        dreaming = false;
        resumed = 0;
        foreground = null;
        rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(a) || Intent.ACTION_BOOT_COMPLETED.equals(a)) {
                    powerOnElapsed = SystemClock.elapsedRealtime();
                    dreaming = false;
                    hide();
                } else if (Intent.ACTION_SCREEN_OFF.equals(a)) hide();
                else if (Intent.ACTION_DREAMING_STARTED.equals(a)) { dreaming = true; hide(); }
                else if (Intent.ACTION_DREAMING_STOPPED.equals(a)) dreaming = false;
                reset();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        f.addAction(Intent.ACTION_BOOT_COMPLETED);
        try { s.registerReceiver(rx, f); } catch (Exception e) { rx = null; }
        life = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { resumed++; }
            @Override public void onActivityPaused(Activity a) { if (resumed > 0) resumed--; }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        };
        try { s.getApplication().registerActivityLifecycleCallbacks(life); } catch (Exception e) { life = null; }
        h.postDelayed(tick, TICK);
    }

    /** Called from KeyRemapService.onDestroy. */
    static void stop() {
        if (h != null) { h.removeCallbacks(tick); h.removeCallbacks(count); h.removeCallbacks(failsafe); }
        removeCard();
        if (svc != null) {
            if (rx != null) try { svc.unregisterReceiver(rx); } catch (Exception ignored) { }
            if (life != null) try { svc.getApplication().unregisterActivityLifecycleCallbacks(life); } catch (Exception ignored) { }
        }
        rx = null;
        life = null;
        svc = null;
        swallowCode = -1;
    }

    private static void reset() {
        lastKeyWall = System.currentTimeMillis();
        lastKeyElapsed = SystemClock.elapsedRealtime();
    }

    /** From KeyRemapService.onAccessibilityEvent (TYPE_WINDOW_STATE_CHANGED). */
    static void window(String pkg) {
        if (pkg == null || dreaming) return;
        if (pkg.equals("android") || pkg.equals("com.android.systemui") || pkg.equals("null")) return;
        if (pkg.equals("com.broom.launcher")) return;          // Broom screens: tracked by `resumed`; the dream: by `dreaming`
        if (pkg.equals("com.mitv.tvhome.atv")) return;         // PatchWall input-source popup / password over what's playing
        if (!pkg.equals(foreground)) android.util.Log.i("Broom", "still watching: in front " + pkg + (isHdmi(pkg) ? " (HDMI, skipped)" : ""));
        foreground = pkg;
    }

    static boolean isHdmi(String pkg) {
        if (pkg == null) return false;
        for (String p : HDMI_PKGS) if (p.equals(pkg)) return true;
        String l = pkg.toLowerCase(java.util.Locale.US);
        return l.contains("tvinput") || l.contains("livetv");
    }

    /**
     * From KeyRemapService.onKeyEvent, for every key. Restarts the idle clock; returns true if the
     * key dismissed the card (or is the rest of that key press) and must be consumed.
     */
    static boolean onKey(KeyEvent e) {
        if (svc == null) return false;
        long now = SystemClock.elapsedRealtime();
        if (e.getAction() == KeyEvent.ACTION_DOWN) reset();
        if (swallowCode != -1) {
            if (e.getKeyCode() == swallowCode && now - swallowAt < 5000) {
                if (e.getAction() == KeyEvent.ACTION_UP) swallowCode = -1;
                return true;                                     // repeats / release of the dismissing key
            }
            swallowCode = -1;
        }
        if (card != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean stuck = now - cardShownAt > (cardSeconds + 30) * 1000L;
            android.util.Log.i("Broom", "still watching: key " + e.getKeyCode() + ", staying on");
            hide();
            if (stuck) return false;
            swallowCode = e.getKeyCode();
            swallowAt = now;
            return true;
        }
        return false;
    }

    private static boolean screenOn() {
        try {
            PowerManager pm = (PowerManager) svc.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception e) { return false; }
    }

    /** Null if the card may be shown now, else why not. */
    private static String skip() {
        long el = SystemClock.elapsedRealtime();
        if (!screenOn()) return "screen off";
        if (dreaming) return "screensaver";
        if (resumed > 0) return "Broom on screen";
        if (el < GRACE || el - powerOnElapsed < GRACE) return "just switched on";
        if (foreground == null) return "app in front not known yet";
        if (isHdmi(foreground) && !onHdmi(svc)) return "HDMI input";
        return null;
    }

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (svc == null) return;
            try { check(); } catch (Exception e) { android.util.Log.w("Broom", "still watching: check failed", e); reset(); }
            if (svc != null) h.postDelayed(this, TICK);
        }
    };

    private static void check() {
        if (card != null) return;
        if (!on(svc) || skip() != null) { reset(); return; }   // idle time only counts while it could ask
        long threshold = hours(svc) * 3600000L;
        long idle = System.currentTimeMillis() - lastKeyWall;
        long idleEl = SystemClock.elapsedRealtime() - lastKeyElapsed;
        if (idle < 0 || Math.abs(idle - idleEl) > JUMP || idle > 2 * threshold) {
            android.util.Log.i("Broom", "still watching: clock jump or stale idle (" + idle / 1000 + " s), restarting");
            reset();
            return;
        }
        if (idle >= threshold) {
            android.util.Log.i("Broom", "still watching: idle " + idle / 60000 + " min in " + foreground + ", asking");
            show(false, countdown(svc));
        }
    }

    /** Settings → Test now: the card with a 15 s countdown; never goes to standby. */
    static boolean test() {
        if (svc == null || h == null) return false;
        h.postDelayed(new Runnable() { @Override public void run() { if (svc != null) show(true, TEST_SECONDS); } }, 400);
        return true;
    }

    // ---------------------------------------------------------------- card

    private static void show(boolean test, int seconds) {
        removeCard();
        Context c = svc;
        Theme th = Theme.current(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.Glass g = Ui.glass(c, th, Ui.dp(c, 26));
        g.status(c, th.accent, th);
        box.setBackground(g.d);
        int px = Ui.dp(c, 44), py = Ui.dp(c, 28);
        box.setPadding(px, py, px, py + g.edge);
        box.addView(Ui.text(c, "Still watching?", 40, 0xFFFFFFFF, th.titleFont));
        TextView sub = Ui.text(c, "Press any key to keep watching", 22, 0xFFFFFFFF, "Poppins-SemiBold");
        sub.setPadding(0, Ui.dp(c, 12), 0, 0);
        box.addView(sub);
        countText = Ui.text(c, "", 20, 0xFFFFD166, "Poppins-SemiBold");
        countText.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 12));
        box.addView(countText);
        FrameLayout track = new FrameLayout(c);
        track.setBackground(Ui.round(0x40FFFFFF, Ui.dp(c, 3)));
        bar = new View(c);
        bar.setBackground(Ui.round(0xFFFFD166, Ui.dp(c, 3)));
        bar.setPivotX(0);
        track.addView(bar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 6)));
        box.addView(track, new LinearLayout.LayoutParams(Ui.dp(c, 420), Ui.dp(c, 6)));
        if (test) {
            TextView t = Ui.text(c, "Test: the TV will stay on", 14, 0xCCFFFFFF, "Poppins-SemiBold");
            t.setPadding(0, Ui.dp(c, 12), 0, 0);
            box.addView(t);
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        testCard = test;
        finished = false;
        cardSeconds = seconds;
        long now = SystemClock.elapsedRealtime();
        deadline = now + seconds * 1000L;
        cardShownAt = now;
        render(seconds * 1000L);
        try {
            ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).addView(box, lp);
            card = box;
        } catch (Exception e) {
            android.util.Log.w("Broom", "still watching: overlay failed", e);
            card = null;
            countText = null;
            bar = null;
            reset();
            return;                                              // never go to standby without having asked
        }
        try { Sounds.init(c); Sounds.click(c); } catch (Exception ignored) { }
        h.removeCallbacks(count);
        h.removeCallbacks(failsafe);
        h.postDelayed(count, 500);
        h.postDelayed(failsafe, (seconds + 30) * 1000L);
    }

    private static void render(long left) {
        if (countText == null) return;
        long s = Math.max(0, (left + 999) / 1000);
        countText.setText((testCard ? "Test: " : "") + "TV goes to standby in " + s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60);
        if (bar != null && cardSeconds > 0) bar.setScaleX(Math.max(0f, Math.min(1f, left / (cardSeconds * 1000f))));
    }

    private static final Runnable count = new Runnable() {
        @Override public void run() {
            if (card == null || svc == null || finished) return;
            long left = deadline - SystemClock.elapsedRealtime();
            if (left < -10000L) { hide(); reset(); return; }    // stale (handler stalled, e.g. deep sleep): never act on it
            if (!testCard && (!screenOn() || dreaming)) { hide(); reset(); return; }
            if (left > 0) { render(left); h.postDelayed(this, Math.min(1000L, (left - 1) % 1000 + 1)); return; }
            if (testCard) {
                finished = true;
                render(0);
                if (countText != null) countText.setText("Test: would go to standby now");
                final View mine = card;
                h.postDelayed(new Runnable() { @Override public void run() { if (card == mine) hide(); } }, 4000);
                return;
            }
            String why = skip();
            boolean attached = card.isAttachedToWindow();
            hide();
            reset();
            if (why != null || !attached || !on(svc)) {
                android.util.Log.i("Broom", "still watching: countdown ended, not going to standby (" + (attached ? why : "card not on screen") + ")");
                return;
            }
            standby(svc);
        }
    };

    /** Hard limit: the card can never stay on screen longer than its countdown + 30 s. */
    private static final Runnable failsafe = new Runnable() {
        @Override public void run() { if (card != null) { android.util.Log.w("Broom", "still watching: failsafe removed the card"); hide(); reset(); } }
    };

    /** Same as KeyRemapService's sleep timer. */
    private static void standby(AccessibilityService s) {
        android.util.Log.i("Broom", "still watching: nobody answered, turning TV off");
        if (!s.performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN: puts the TV to sleep */))
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
    }

    private static void hide() {
        if (h != null) { h.removeCallbacks(count); h.removeCallbacks(failsafe); }
        final View v = card;
        card = null;
        countText = null;
        bar = null;
        finished = false;
        if (v == null || svc == null) return;
        final WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        try {
            v.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                @Override public void run() { try { wm.removeView(v); } catch (Exception ignored) { } }
            }).start();
        } catch (Exception e) { try { wm.removeView(v); } catch (Exception ignored) { } }
        // belt and braces: if the fade never finishes, remove the card anyway
        if (h != null) h.postDelayed(new Runnable() {
            @Override public void run() { try { if (v.isAttachedToWindow()) wm.removeView(v); } catch (Exception ignored) { } }
        }, 600);
    }

    /** Immediate removal (service stopping, or a new card replacing the old one). */
    private static void removeCard() {
        View v = card;
        card = null;
        countText = null;
        bar = null;
        finished = false;
        if (v != null && svc != null) try { ((WindowManager) svc.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) { }
    }
}

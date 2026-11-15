package com.broom.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes the remote's extra home buttons (PatchWall / Mi button) open Broom Home, and bounces
 * any PatchWall home screen that Xiaomi's services still try to start. Input-source popup,
 * parental-lock password and other PatchWall screens are left alone.
 */
public class KeyRemapService extends AccessibilityService {
    private static final Set<String> PATCHWALL_HOME = new HashSet<>();
    static {
        for (String s : new String[]{"PatchWallActivity", "BannerLogoA", "BannerLogoB", "BannerLogoC", "BannerLogoD",
                "TvPlusTrampolineActivity", "HomeIntentActivity", "MainActivity"})
            PATCHWALL_HOME.add(s);
    }

    static Set<Integer> keys(android.content.Context c) {
        Set<Integer> out = new HashSet<>();
        String s = Theme.prefs(c).getString("remap_keys", "");
        for (String k : s.split(",")) try { if (!k.isEmpty()) out.add(Integer.parseInt(k.trim())); } catch (Exception ignored) { }
        return out;
    }

    private final Handler h = new Handler();
    private String lastPkg;
    private ScorePopup popup;
    private MarketAlerts alerts;
    private NetMonitor net;
    private boolean sleepWarned;
    private final Runnable sleepCheck = new Runnable() {
        @Override public void run() {
            long at = Theme.prefs(KeyRemapService.this).getLong("sleep_at", 0);
            if (at > 0) {
                long left = at - System.currentTimeMillis();
                if (left < -90000L) {
                    // stale timer (e.g. after a restart or standby): never act on it, just clear it
                    Theme.prefs(KeyRemapService.this).edit().remove("sleep_at").commit();
                    sleepWarned = false;
                } else if (left <= 0) {
                    Theme.prefs(KeyRemapService.this).edit().remove("sleep_at").commit();   // written before the TV goes off
                    sleepWarned = false;
                    android.util.Log.i("Broom", "sleep timer: turning TV off");
                    if (!performGlobalAction(8 /* GLOBAL_ACTION_LOCK_SCREEN: puts the TV to sleep */))
                        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                } else if (left <= 65000 && !sleepWarned) {
                    sleepWarned = true;
                    if (popup != null) popup.card("💤  SLEEP TIMER", "TV turns off in 1 minute — press any key to cancel", 0xFFFFD166);
                }
            }
            h.postDelayed(this, 15000);
        }
    };
    private final BroadcastReceiver demo = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if ("com.broom.launcher.MARKET_DEMO".equals(i.getAction())) { if (alerts != null) alerts.demo(); }
            else if ("com.broom.launcher.SLEEP_TEST".equals(i.getAction()))
                Theme.prefs(c).edit().putLong("sleep_at", System.currentTimeMillis() + 70000L).apply();
            else if (popup != null) popup.demo();
        }
    };
    private final Runnable forceHome = new Runnable() {
        @Override public void run() {
            if (!powerOnHome(KeyRemapService.this)) return;
            android.util.Log.i("Broom", "power-on: opening Broom Home (last=" + lastPkg + ")");
            if (morningBriefing()) return;
            goHome();
        }
    };
    /** Live TV sometimes starts a few seconds late; bounce it once more if it took over. */
    private final Runnable recheck = new Runnable() {
        @Override public void run() {
            if (powerOnHome(KeyRemapService.this) && "com.android.tv".equals(lastPkg)) {
                android.util.Log.i("Broom", "power-on: Live TV took over, reopening Broom");
                goHome();
            }
        }
    };
    private void powerOn() {
        h.removeCallbacks(forceHome); h.removeCallbacks(recheck);
        h.postDelayed(forceHome, 2500);
        h.postDelayed(recheck, 7000);
        h.postDelayed(recheck, 12000);
    }
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { powerOn(); }
    };

    /** First switch-on between 6 and 10 AM each day opens the Briefing screen (if enabled). */
    private boolean morningBriefing() {
        android.content.SharedPreferences p = Theme.prefs(this);
        if (!p.getBoolean("morning_brief", true)) return false;
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hr = now.get(java.util.Calendar.HOUR_OF_DAY);
        String today = now.get(java.util.Calendar.YEAR) + "-" + now.get(java.util.Calendar.DAY_OF_YEAR);
        if (hr < 6 || hr >= 10 || today.equals(p.getString("brief_day", ""))) return false;
        p.edit().putString("brief_day", today).apply();
        Intent i = new Intent(this, BriefingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return true;
    }

    static boolean powerOnHome(Context c) { return Theme.prefs(c).getBoolean("power_on_home", true); }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        android.util.Log.i("Broom", "button helper connected");
        popup = new ScorePopup(this);
        popup.start();
        alerts = new MarketAlerts(this, popup);
        alerts.start();
        net = new NetMonitor(this);
        net.start();
        // a timer left over from before this start (crash, update, standby) is discarded
        long at = Theme.prefs(this).getLong("sleep_at", 0);
        if (at > 0 && at < System.currentTimeMillis() + 60000L) Theme.prefs(this).edit().remove("sleep_at").commit();
        h.postDelayed(sleepCheck, 15000);
        IntentFilter df = new IntentFilter("com.broom.launcher.SCORE_DEMO");
        df.addAction("com.broom.launcher.MARKET_DEMO");
        df.addAction("com.broom.launcher.SLEEP_TEST");
        registerReceiver(demo, df, android.Manifest.permission.DUMP, null);
        if (android.os.SystemClock.elapsedRealtime() < 5 * 60 * 1000) powerOn(); // fresh boot
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        try { unregisterReceiver(demo); } catch (Exception ignored) { }
        if (popup != null) popup.stop();
        if (alerts != null) alerts.stop();
        if (net != null) net.stop();
        h.removeCallbacks(sleepCheck);
        super.onDestroy();
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (sleepWarned && e.getAction() == KeyEvent.ACTION_DOWN) {        // any key cancels a pending sleep
            Theme.prefs(this).edit().remove("sleep_at").commit();
            sleepWarned = false;
            if (popup != null) popup.card("💤  SLEEP TIMER", "Cancelled", 0xFF4CD964);
        }
        if (!keys(this).contains(e.getKeyCode())) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) goHome();
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = String.valueOf(e.getPackageName());
        if (!pkg.equals("android") && !pkg.equals("com.android.systemui")) { lastPkg = pkg; if (popup != null) popup.foreground = pkg; if (net != null) net.foreground = pkg; }
        if (!"com.mitv.tvhome.atv".equals(pkg)) return;
        String cls = String.valueOf(e.getClassName());
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        if (PATCHWALL_HOME.contains(simple)) goHome();
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override public void onInterrupt() { }
}

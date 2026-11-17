package com.broom.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * Backs up / restores Broom's settings (favourites, theme, screensaver, weather area, toggles) as JSON in
 * /sdcard/Android/data/com.broom.launcher/files/broom-settings.json, which adb can pull/push.
 *   adb shell am broadcast -a com.broom.launcher.BACKUP  -n com.broom.launcher/.BackupReceiver
 *   adb shell am broadcast -a com.broom.launcher.RESTORE -n com.broom.launcher/.BackupReceiver
 * For Broom Console (laptop, over ADB):
 *   STATUS  writes broom-status.json next to it: version, DNS, network summary, Still watching?, memory, crashes
 *   RELOAD  re-reads watchlists, marks Briefing data stale and redraws home (only if home is on screen)
 * The DUMP permission on the receiver means only the ADB shell can send these.
 */
public class BackupReceiver extends BroadcastReceiver {
    private static final String[] SKIP = {"wx_json", "wx_aqi"};   // big caches, re-downloaded anyway

    @Override public void onReceive(Context c, Intent i) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) { setResultData("no storage"); return; }
        File f = new File(dir, "broom-settings.json");
        SharedPreferences p = Theme.prefs(c);
        try {
            if ("com.broom.launcher.STATUS".equals(i.getAction())) {
                File sf = new File(dir, "broom-status.json");
                try (FileOutputStream out = new FileOutputStream(sf)) { out.write(status(c).toString(1).getBytes("UTF-8")); }
                setResultData("saved " + sf.getAbsolutePath());
                return;
            }
            if ("com.broom.launcher.RELOAD".equals(i.getAction())) {
                Briefing.loadLists(c);
                for (String n : new String[]{"markets", "news"}) new File(c.getFilesDir(), "brief_" + n + ".json").setLastModified(0);
                MainActivity.requestReload();
                setResultData("reloaded");
                return;
            }
            if ("com.broom.launcher.BACKUP".equals(i.getAction())) {
                JSONObject o = new JSONObject();
                outer:
                for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
                    for (String s : SKIP) if (s.equals(e.getKey())) continue outer;
                    Object v = e.getValue();
                    JSONObject x = new JSONObject();
                    x.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s");
                    x.put("v", String.valueOf(v));
                    o.put(e.getKey(), x);
                }
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(o.toString(1).getBytes("UTF-8")); }
                setResultData("saved " + f.getAbsolutePath());
            } else if ("com.broom.launcher.RESTORE".equals(i.getAction())) {
                byte[] b = new byte[(int) f.length()];
                try (FileInputStream in = new FileInputStream(f)) { int n = in.read(b); }
                JSONObject o = new JSONObject(new String(b, "UTF-8"));
                SharedPreferences.Editor ed = p.edit();
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject x = o.getJSONObject(k);
                    String t = x.getString("t"), v = x.getString("v");
                    if (t.equals("b")) ed.putBoolean(k, Boolean.parseBoolean(v));
                    else if (t.equals("i")) ed.putInt(k, Integer.parseInt(v));
                    else if (t.equals("l")) ed.putLong(k, Long.parseLong(v));
                    else if (t.equals("f")) ed.putFloat(k, Float.parseFloat(v));
                    else ed.putString(k, v);
                }
                ed.remove("wx_time").apply();
                setResultData("restored " + o.length() + " settings");
            } else setResultData("unknown action");   // never fall through to RESTORE
        } catch (Exception e) { setResultData("error: " + e); }
    }

    private static JSONObject status(Context c) throws Exception {
        JSONObject o = new JSONObject();
        android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        o.put("version", pi.versionName).put("code", pi.versionCode).put("time", System.currentTimeMillis());
        android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
        o.put("screen_on", pm != null && pm.isInteractive());
        android.content.ContentResolver cr = c.getContentResolver();
        o.put("dns", new JSONObject()
                .put("mode", String.valueOf(android.provider.Settings.Global.getString(cr, "private_dns_mode")))
                .put("host", String.valueOf(android.provider.Settings.Global.getString(cr, "private_dns_specifier")))
                .put("on_backup", Theme.prefs(c).getBoolean("dns_on_backup", false))
                .put("main_host", Theme.prefs(c).getString("dns_main_host", ""))
                .put("personal_until", Theme.prefs(c).getLong("dns_main_until", 0)));
        java.util.List<NetMonitor.Sample> ss = NetMonitor.samples(c);
        int up = 0; long lat = 0;
        for (NetMonitor.Sample s : ss) if (s.ok) { up++; lat += s.lat; }
        JSONObject net = new JSONObject().put("checks", ss.size()).put("up", up).put("avg_ms", up == 0 ? 0 : lat / up);
        if (!ss.isEmpty()) {
            NetMonitor.Sample l = ss.get(ss.size() - 1);
            net.put("last_t", l.t).put("last_ok", l.ok).put("last_ms", l.lat).put("rssi", l.rssi).put("link_mbps", l.link);
        }
        java.util.List<NetMonitor.Speed> sp = NetMonitor.speeds(c);
        double best = 0;
        for (NetMonitor.Speed s : sp) best = Math.max(best, s.mbps);
        if (!sp.isEmpty()) net.put("speed_mbps", sp.get(sp.size() - 1).mbps).put("speed_t", sp.get(sp.size() - 1).t);
        net.put("speed_best", best);
        o.put("net", net);
        o.put("still_watching", new JSONObject().put("on", StillWatching.on(c)).put("hours", StillWatching.hours(c))
                .put("countdown_s", StillWatching.countdown(c)).put("hdmi", StillWatching.onHdmi(c)));
        android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        o.put("mem", new JSONObject().put("avail_mb", mi.availMem >> 20).put("total_mb", mi.totalMem >> 20).put("low", mi.lowMemory));
        o.put("crash_n", Theme.prefs(c).getInt("crash_n", 0)).put("crash_at", Theme.prefs(c).getLong("crash_at", 0));
        // apps a favourite can point at (label only, no icons), the favourites in order, and the watchlists in use
        android.content.pm.PackageManager pkm = c.getPackageManager();
        java.util.TreeMap<String, String> apps = new java.util.TreeMap<>();
        for (String cat : new String[]{"android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER})
            for (android.content.pm.ResolveInfo ri : pkm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pk = ri.activityInfo.packageName;
                if (!pk.equals(c.getPackageName()) && !apps.containsKey(pk)) apps.put(pk, String.valueOf(ri.loadLabel(pkm)));
            }
        org.json.JSONArray ja = new org.json.JSONArray();
        for (Map.Entry<String, String> e : apps.entrySet()) ja.put(new org.json.JSONArray().put(e.getKey()).put(e.getValue()));
        o.put("apps", ja);
        String favs = Theme.prefs(c).getString("favs", null);
        org.json.JSONArray fa = new org.json.JSONArray();
        for (String pk : favs == null ? AppCatalog.DEFAULT_FAVS : favs.split(",")) if (apps.containsKey(pk)) fa.put(pk);
        o.put("favs", fa).put("favs_default", favs == null);
        Briefing.loadLists(c);
        JSONObject wl = new JSONObject();
        int[] idx = {Briefing.WL_INDIA, Briefing.WL_US};
        String[] names = {"india", "us"};
        for (int k = 0; k < 2; k++) {
            String[] g = Briefing.GROUPS[idx[k]];
            org.json.JSONArray a = new org.json.JSONArray();
            for (int j = 1; j + 1 < g.length; j += 2) a.put(new org.json.JSONArray().put(g[j]).put(g[j + 1]));
            wl.put(names[k], a);
        }
        o.put("watchlists", wl);
        return o;
    }
}

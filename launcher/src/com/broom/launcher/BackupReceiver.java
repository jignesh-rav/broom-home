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
 */
public class BackupReceiver extends BroadcastReceiver {
    private static final String[] SKIP = {"wx_json", "wx_aqi"};   // big caches, re-downloaded anyway

    @Override public void onReceive(Context c, Intent i) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) { setResultData("no storage"); return; }
        File f = new File(dir, "broom-settings.json");
        SharedPreferences p = Theme.prefs(c);
        try {
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
            } else {
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
            }
        } catch (Exception e) { setResultData("error: " + e); }
    }
}

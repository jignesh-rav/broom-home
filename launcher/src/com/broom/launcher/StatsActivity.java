package com.broom.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Dashboard: live RAM / storage / app counts, plus before-vs-after numbers measured over ADB. */
public class StatsActivity extends Activity {
    private Theme th;
    private LinearLayout col;

    @Override protected void onResume() {
        super.onResume();
        Sounds.init(this);
        Sounds.quiet(600);
        th = Theme.current(this);
        FrameLayout root = new FrameLayout(this);
        ThemeBackground bg = new ThemeBackground(this);
        bg.setTheme(th);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setFocusable(true);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 44);
        col.setPadding(pad, Ui.dp(this, 28), pad, pad);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        sv.requestFocus();

        JSONObject base = readBaseline();
        JSONObject before = base == null ? null : base.optJSONObject("before");
        JSONObject after = base == null ? null : base.optJSONObject("after");

        col.addView(Ui.text(this, "TV health & stats", 32, th.text, th.clockFont));
        TextView sub = Ui.text(this, (base != null ? base.optString("device") + "   ·   " : "")
                + "Use ▲▼ to scroll · Back to return", 12, th.dim, th.bodyFont);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        col.addView(sub);

        // ---- live numbers
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long dataFree = fs.getAvailableBytes(), dataTotal = fs.getTotalBytes();

        PackageManager pm = getPackageManager();
        List<PackageInfo> pk = pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS);
        int total = pk.size(), disabled = 0, sys = 0, user = 0;
        for (PackageInfo p : pk) {
            ApplicationInfo ai = p.applicationInfo;
            if (!ai.enabled) disabled++;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) sys++; else user++;
        }

        // ---- before / after cards
        section("Before vs after modernisation");
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        String b = before == null ? "–" : before.optInt("ram_avail_mb") + " MB";
        String a = after != null ? after.optInt("ram_avail_mb") + " MB" : Ui.mb(mi.availMem);
        cards.addView(card("RAM free", b, a, after == null ? "live" : "measured", true));
        b = before == null ? "–" : before.optInt("data_free_mb") + " MB";
        a = after != null ? after.optInt("data_free_mb") + " MB" : Ui.mb(dataFree);
        cards.addView(card("Storage free", b, a, after == null ? "live" : "measured", true));
        b = before == null ? "–" : String.valueOf(before.optInt("pkgs_active"));
        a = after != null ? String.valueOf(after.optInt("pkgs_active")) : String.valueOf(total - disabled);
        cards.addView(card("Active packages", b, a, "lower is leaner", true));
        b = before == null ? "–" : before.optString("load");
        a = after != null ? after.optString("load") : "–";
        cards.addView(card("CPU load (1 min)", b, a, "4 cores · lower is better", false));
        col.addView(cards);

        if (before != null && after != null) {
            int dRam = after.optInt("ram_avail_mb") - before.optInt("ram_avail_mb");
            int dDisk = after.optInt("data_free_mb") - before.optInt("data_free_mb");
            TextView s = Ui.text(this, String.format("Saved:  %+d MB RAM   ·   %+d MB storage   ·   %d packages disabled, %d removed",
                    dRam, dDisk, base.optInt("disabled"), base.optInt("uninstalled")), 14, th.accent, th.titleFont);
            s.setPadding(0, Ui.dp(this, 12), 0, 0);
            col.addView(s);
        }

        // ---- live bars
        section("Memory now");
        long used = mi.totalMem - mi.availMem;
        bar("Used " + Ui.mb(used) + " of " + Ui.mb(mi.totalMem), used, mi.totalMem, th.accent);
        section("Storage now");
        bar("Used " + Ui.mb(dataTotal - dataFree) + " of " + Ui.mb(dataTotal), dataTotal - dataFree, dataTotal, th.glowA);

        // ---- apps by category
        section("Apps by category (on the home screen)");
        AppCatalog cat = new AppCatalog();
        cat.load(this);
        int max = 1;
        Map<String, List<AppCatalog.App>> by = cat.byCategory();
        for (List<AppCatalog.App> l : by.values()) max = Math.max(max, l.size());
        for (Map.Entry<String, List<AppCatalog.App>> e : by.entrySet())
            bar(e.getKey() + "  ·  " + e.getValue().size(), e.getValue().size(), max, th.glowB | 0xFF000000);

        section("All packages");
        bar("System packages  ·  " + sys, sys, total, th.accent);
        bar("Your apps  ·  " + user, user, total, th.glowA);
        bar("Disabled (bloat / telemetry)  ·  " + disabled, disabled, total, 0xFFFF6B6B);

        // ---- top RAM users
        JSONArray top = after != null ? after.optJSONArray("top") : before != null ? before.optJSONArray("top") : null;
        JSONArray topBefore = before != null ? before.optJSONArray("top") : null;
        if (topBefore != null) {
            section("Biggest memory users — before");
            list(topBefore);
        }
        if (top != null && after != null) {
            section("Biggest memory users — after");
            list(top);
        }
        if (base != null) {
            TextView m = Ui.text(this, "Before/after measured over ADB: " + base.optString("measured"), 11, th.dim, th.bodyFont);
            m.setPadding(0, Ui.dp(this, 18), 0, 0);
            col.addView(m);
        }
    }

    private JSONObject readBaseline() {
        try (InputStream in = getAssets().open("baseline.json")) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            return new JSONObject(new String(buf, 0, n, "UTF-8"));
        } catch (Exception e) { return null; }
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 16, th.text, th.titleFont);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        col.addView(t);
    }

    private View card(String title, String before, String after, String note, boolean bigger) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(this, th, Ui.dp(this, th.radiusDp));
        c.setBackground(g.d);
        int p = Ui.dp(this, 14);
        c.setPadding(p, p, p, p);
        c.addView(Ui.text(this, title, 12, th.dim, th.bodyFont));
        TextView v = Ui.text(this, after, 24, th.text, th.titleFont);
        v.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        c.addView(v);
        c.addView(Ui.text(this, "before " + before, 12, th.dim, th.bodyFont));
        TextView n = Ui.text(this, note, 10, th.accent, th.bodyFont);
        n.setPadding(0, Ui.dp(this, 4), 0, 0);
        c.addView(n);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.rightMargin = Ui.dp(this, 12);
        c.setLayoutParams(lp);
        return c;
    }

    private void bar(String label, long value, long max, int color) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        TextView l = Ui.text(this, label, 12, th.text, th.bodyFont);
        r.addView(l, new LinearLayout.LayoutParams(Ui.dp(this, 300), -2));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.round(th.card, Ui.dp(this, 6)));
        View fill = new View(this);
        fill.setBackground(Ui.round(color, Ui.dp(this, 6)));
        float f = max <= 0 ? 0 : Math.max(0.01f, Math.min(1f, value / (float) max));
        track.setWeightSum(1f);
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, f));
        r.addView(track, new LinearLayout.LayoutParams(0, Ui.dp(this, 12), 1));
        col.addView(r);
    }

    private void list(JSONArray arr) {
        int max = 1;
        for (int i = 0; i < arr.length(); i++) max = Math.max(max, arr.optJSONArray(i).optInt(1));
        for (int i = 0; i < Math.min(8, arr.length()); i++) {
            JSONArray e = arr.optJSONArray(i);
            bar(e.optString(0) + "  ·  " + e.optInt(1) + " MB", e.optInt(1), max, th.accent);
        }
    }
}

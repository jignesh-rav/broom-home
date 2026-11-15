package com.broom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Edit the India and US/global stock watchlists with the remote; set market alert thresholds. */
public class WatchlistActivity extends Activity {
    private Theme th;
    private LinearLayout col;
    private String focusTag;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Sounds.init(this);
        th = Theme.current(this);
        Briefing.ensureLists(this);
        build();
    }

    private void build() {
        Sounds.quiet(500);
        FrameLayout root = new FrameLayout(this);
        ThemeBackground bg = new ThemeBackground(this);
        bg.setTheme(th);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 20), pad, pad);
        col.addView(Ui.text(this, "Watchlist & alerts", 30, th.text, "Poppins-SemiBold"));
        TextView sub = Ui.text(this, "Select a stock to move or remove it. Changes show in Briefing, the screensaver and alerts.", 13, th.dim, th.bodyFont);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        col.addView(sub);

        LinearLayout two = new LinearLayout(this);
        two.setOrientation(LinearLayout.HORIZONTAL);
        two.setClipChildren(false);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1);
        l1.rightMargin = Ui.dp(this, 16);
        two.addView(listColumn(true), l1);
        two.addView(listColumn(false), new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(two);

        // ---------- alerts
        section("Market alerts (pop-up over any app, during market hours)");
        chips("Nifty / Sensex / S&P / Nasdaq move", "alert_index", new float[]{0, 1, 1.5f, 2}, "Off", "1%", "1.5%", "2%");
        chips("Any watchlist stock moves", "alert_stock", new float[]{0, 3, 5, 10}, "Off", "3%", "5%", "10%");
        TextView n = Ui.text(this, "Each level alerts once per day, e.g. at 1%, then 2%, then 3% for an index.", 11, th.dim, th.bodyFont);
        n.setPadding(0, Ui.dp(this, 6), 0, 0);
        col.addView(n);

        TextView reset = Ui.pill(this, "Reset watchlists to defaults", th, new View.OnClickListener() {
            @Override public void onClick(View v) { Briefing.resetLists(WatchlistActivity.this); focusTag = null; build(); }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.topMargin = Ui.dp(this, 18);
        col.addView(reset, rl);

        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        View f = focusTag == null ? null : col.findViewWithTag(focusTag);
        if (f == null) f = col.findViewWithTag("add:true");
        final View ff = f;
        if (ff != null) ff.post(new Runnable() { @Override public void run() { ff.requestFocus(); } });
    }

    private static float alertLevel(android.content.Context c, String key, float def) { return Theme.prefs(c).getFloat(key, def); }
    static float indexAlert(android.content.Context c) { return alertLevel(c, "alert_index", 1f); }
    static float stockAlert(android.content.Context c) { return alertLevel(c, "alert_stock", 5f); }

    private void chips(String label, final String key, float[] values, String... names) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setPadding(0, Ui.dp(this, 6), 0, 0);
        r.addView(Ui.text(this, label, 13, th.text, "Poppins-Medium"), new LinearLayout.LayoutParams(Ui.dp(this, 290), -2));
        float cur = key.equals("alert_index") ? indexAlert(this) : stockAlert(this);
        for (int i = 0; i < values.length; i++) {
            final float v = values[i];
            TextView b = Ui.pill(this, (Math.abs(v - cur) < 0.01 ? "● " : "") + names[i], th, new View.OnClickListener() {
                @Override public void onClick(View x) {
                    Theme.prefs(WatchlistActivity.this).edit().putFloat(key, v).apply();
                    focusTag = key + v;
                    build();
                }
            });
            b.setTag(key + v);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 8);
            r.addView(b, lp);
        }
        col.addView(r);
    }

    private View listColumn(final boolean india) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setClipChildren(false);
        TextView h = Ui.text(this, india ? "India stocks" : "US & global stocks", 16, th.text, th.titleFont);
        h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        c.addView(h);
        final List<String[]> items = items(india);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            final String[] it = items.get(i);
            TextView row = Ui.pill(this, it[1] + "   ·   " + it[0], th, new View.OnClickListener() {
                @Override public void onClick(View v) { rowMenu(india, items, idx); }
            });
            row.setTag((india ? "in:" : "us:") + it[0]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 6);
            c.addView(row, lp);
        }
        TextView add = Ui.pill(this, "+  Add stock", th, new View.OnClickListener() {
            @Override public void onClick(View v) { addDialog(india); }
        });
        add.setTag("add:" + india);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.dp(this, 10);
        c.addView(add, lp);
        return c;
    }

    private List<String[]> items(boolean india) {
        String[] g = Briefing.GROUPS[india ? Briefing.WL_INDIA : Briefing.WL_US];
        List<String[]> out = new ArrayList<>();
        for (int i = 1; i + 1 < g.length; i += 2) out.add(new String[]{g[i], g[i + 1]});
        return out;
    }

    private void rowMenu(final boolean india, final List<String[]> items, final int idx) {
        String[] opts = {"Move up", "Move down", "Remove", "Rename…"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(items.get(idx)[1])
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        List<String[]> l = new ArrayList<>(items);
                        String[] it = l.get(idx);
                        if (w == 0 && idx > 0) { l.remove(idx); l.add(idx - 1, it); }
                        else if (w == 1 && idx < l.size() - 1) { l.remove(idx); l.add(idx + 1, it); }
                        else if (w == 2) l.remove(idx);
                        else if (w == 3) { rename(india, l, idx); return; }
                        focusTag = (india ? "in:" : "us:") + it[0];
                        Briefing.saveList(WatchlistActivity.this, india, l);
                        build();
                    }
                }).show();
    }

    private void rename(final boolean india, final List<String[]> l, final int idx) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setText(l.get(idx)[1]);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Display name").setView(in)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        l.set(idx, new String[]{l.get(idx)[0], in.getText().toString().trim()});
                        Briefing.saveList(WatchlistActivity.this, india, l);
                        build();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void addDialog(final boolean india) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint(india ? "Company or NSE symbol, e.g. Titan, TATASTEEL" : "Company or symbol, e.g. Netflix, QCOM");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Add stock").setView(in)
                .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { search(india, in.getText().toString().trim()); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void search(final boolean india, final String q) {
        if (q.isEmpty()) return;
        Toast.makeText(this, "Searching…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String[]> res = Briefing.search(q);
                // India list: prefer NSE listings first
                if (india) java.util.Collections.sort(res, new java.util.Comparator<String[]>() {
                    @Override public int compare(String[] a, String[] b) {
                        return Boolean.compare(!a[0].endsWith(".NS"), !b[0].endsWith(".NS"));
                    }
                });
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { pick(india, res); } });
            }
        }).start();
    }

    private void pick(final boolean india, final List<String[]> res) {
        if (res.isEmpty()) { Toast.makeText(this, "No match — try the company's name or ticker", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[res.size()];
        for (int i = 0; i < res.size(); i++) labels[i] = res.get(i)[1] + "   ·   " + res.get(i)[0] + "   ·   " + res.get(i)[2];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pick a listing")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        List<String[]> l = items(india);
                        String[] r = res.get(w);
                        for (String[] x : l) if (x[0].equals(r[0])) { Toast.makeText(WatchlistActivity.this, "Already in the list", Toast.LENGTH_SHORT).show(); return; }
                        String name = r[1].length() > 18 ? r[1].substring(0, 18).trim() : r[1];
                        l.add(new String[]{r[0], titleCase(name)});
                        focusTag = (india ? "in:" : "us:") + r[0];
                        Briefing.saveList(WatchlistActivity.this, india, l);
                        Toast.makeText(WatchlistActivity.this, "Added " + r[0], Toast.LENGTH_SHORT).show();
                        build();
                    }
                }).show();
    }

    private static String titleCase(String s) {
        if (!s.equals(s.toUpperCase())) return s;
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase().split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.length() <= 3 && !w.matches("[a-z]+") ? w.toUpperCase() : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 16, th.text, th.titleFont);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 4));
        col.addView(t);
    }
}

package com.broom.launcher;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Network monitor: live status, 24 h uptime / latency / outages, speed tests. */
public class NetworkActivity extends Activity {
    private Theme th;
    private LinearLayout col;
    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() { @Override public void run() { render(); h.postDelayed(this, 30000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Sounds.init(this);
        Sounds.quiet(600);
        th = Theme.current(this);
        FrameLayout root = new FrameLayout(this);
        ThemeBackground bg = new ThemeBackground(this);
        bg.setTheme(th);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(this);
        veil.setBackgroundColor(0x59000000);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 20), pad, pad);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        // fresh probe when the screen opens
        new Thread(new Runnable() {
            @Override public void run() {
                NetMonitor.probe(NetworkActivity.this);
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { render(); } });
            }
        }).start();
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private void render() {
        View focused = getCurrentFocus();
        Object ft = focused == null ? null : focused.getTag();
        col.removeAllViews();
        List<NetMonitor.Sample> ss = NetMonitor.samples(this);
        NetMonitor.Sample now = ss.isEmpty() ? null : ss.get(ss.size() - 1);

        LinearLayout head = row();
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(Ui.text(this, "Network", 30, th.text, "Poppins-SemiBold"));
        tl.addView(Ui.text(this, "Checked every 30 minutes · speed test every hour when the TV is idle", 12, th.dim, th.bodyFont));
        head.addView(tl, new LinearLayout.LayoutParams(0, -2, 1));
        TextView run = Ui.pill(this, "Run speed test now", th, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(NetworkActivity.this, "Testing download speed (5 MB)…", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override public void run() {
                        final double m = NetMonitor.speedTest(NetworkActivity.this);
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(NetworkActivity.this, m < 0 ? "Speed test failed" : String.format(java.util.Locale.US, "Download: %.1f Mbps", m), Toast.LENGTH_LONG).show();
                                render();
                            }
                        });
                    }
                }).start();
            }
        });
        run.setTag("run");
        head.addView(run);
        col.addView(head);

        // ---------- now
        LinearLayout cards = row();
        cards.setPadding(0, Ui.dp(this, 14), 0, 0);
        String ssid = "";
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            ssid = wm.getConnectionInfo().getSSID().replace("\"", "");
            if (ssid.startsWith("<")) ssid = "";
        } catch (Exception ignored) { }
        boolean ok = now != null && now.ok;
        cards.addView(stat("Internet", now == null ? "…" : ok ? "Online" : "Offline", ok ? 0xFF4CD964 : 0xFFFF6B6B, ssid.isEmpty() ? "Wi-Fi" : ssid), lp());
        cards.addView(stat("Wi-Fi signal", now == null ? "…" : NetMonitor.barsText(now.rssi) + "  " + now.rssi + " dBm", th.text,
                now == null ? "" : quality(now.rssi)), lp());
        cards.addView(stat("Link speed", now == null || now.link <= 0 ? "…" : now.link + " Mbps", th.text, "TV ↔ router"), lp());
        cards.addView(stat("Latency now", now == null || !now.ok ? "—" : now.lat + " ms", th.text, "to Google · " + dnsName()), lp());
        col.addView(cards);

        // ---------- 24 h
        int total = ss.size(), up = 0;
        List<Integer> lat = new ArrayList<>();
        for (NetMonitor.Sample s : ss) if (s.ok) { up++; lat.add(s.lat); }
        Collections.sort(lat);
        int avg = 0;
        for (int l : lat) avg += l;
        avg = lat.isEmpty() ? 0 : avg / lat.size();
        int p95 = lat.isEmpty() ? 0 : lat.get(Math.min(lat.size() - 1, (int) (lat.size() * 0.95)));
        LinearLayout day = row();
        day.setPadding(0, Ui.dp(this, 12), 0, 0);
        double upPct = total == 0 ? 0 : up * 100.0 / total;
        day.addView(stat("Uptime (24 h)", total == 0 ? "…" : String.format(java.util.Locale.US, "%.1f%%", upPct),
                upPct >= 99.5 ? 0xFF4CD964 : upPct >= 97 ? 0xFFFFD166 : 0xFFFF6B6B, total + " checks"), lp());
        day.addView(stat("Avg latency", avg + " ms", th.text, "worst 5%: " + p95 + " ms"), lp());
        List<NetMonitor.Speed> sp = NetMonitor.speeds(this);
        NetMonitor.Speed last = sp.isEmpty() ? null : sp.get(sp.size() - 1);
        day.addView(stat("Last speed test", last == null ? "—" : String.format(java.util.Locale.US, "%.1f Mbps", last.mbps), th.text,
                last == null ? "runs when idle" : Briefing.ago(last.t)), lp());
        double best = 0;
        for (NetMonitor.Speed s : sp) best = Math.max(best, s.mbps);
        day.addView(stat("Best (recent)", best == 0 ? "—" : String.format(java.util.Locale.US, "%.1f Mbps", best), th.text, sp.size() + " tests"), lp());
        col.addView(day);

        // ---------- latency chart
        LinearLayout ch = card("Latency, last 24 h (ms) — gaps are outages");
        float[] d = new float[ss.size()];
        for (int i = 0; i < ss.size(); i++) d[i] = ss.get(i).ok ? ss.get(i).lat : 0;
        BriefingActivity.Spark spark = new BriefingActivity.Spark(this, d, 0xFF7FD1FF);
        ch.addView(spark, new LinearLayout.LayoutParams(-1, Ui.dp(this, 70)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = Ui.dp(this, 14);
        col.addView(ch, cl);

        // ---------- outages + speed history
        LinearLayout two = row();
        two.setPadding(0, Ui.dp(this, 14), 0, 0);
        LinearLayout out = card("Outages (24 h)");
        List<long[]> outs = outages(ss);
        if (outs.isEmpty()) out.addView(Ui.text(this, "None — connection has been steady.", 13, 0xFF4CD964, th.bodyFont));
        for (int i = outs.size() - 1; i >= 0 && i >= outs.size() - 8; i--) {
            long[] o = outs.get(i);
            out.addView(Ui.text(this, DateFormat.format("EEE h:mm a", o[0]) + "   ·   about " + Math.max(NetMonitor.PROBE_MS / 60000, (o[1] - o[0] + NetMonitor.PROBE_MS) / 60000) + " min",
                    13, th.text, "Poppins-Regular"));
        }
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1);
        l1.rightMargin = Ui.dp(this, 14);
        two.addView(out, l1);
        LinearLayout sh = card("Speed tests");
        if (sp.isEmpty()) sh.addView(Ui.text(this, "No tests yet — press \"Run speed test now\".", 13, th.dim, th.bodyFont));
        for (int i = sp.size() - 1; i >= 0 && i >= sp.size() - 8; i--)
            sh.addView(Ui.text(this, DateFormat.format("EEE h:mm a", sp.get(i).t) + "   ·   " + String.format(java.util.Locale.US, "%.1f Mbps", sp.get(i).mbps),
                    13, th.text, "Poppins-Regular"));
        two.addView(sh, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(two);

        View f = ft == null ? null : col.findViewWithTag(ft);
        if (f == null) f = run;
        final View ff = f;
        ff.post(new Runnable() { @Override public void run() { ff.requestFocus(); } });
    }

    private static String quality(int rssi) {
        int b = NetMonitor.bars(rssi);
        return b >= 4 ? "Excellent" : b == 3 ? "Good" : b == 2 ? "Fair — may buffer in 4K" : "Weak — move router closer";
    }

    /** Runs of failed checks -> {start, end}. */
    private static List<long[]> outages(List<NetMonitor.Sample> ss) {
        List<long[]> out = new ArrayList<>();
        long start = 0, last = 0;
        for (NetMonitor.Sample s : ss) {
            if (!s.ok) { if (start == 0) start = s.t; last = s.t; }
            else if (start != 0) { out.add(new long[]{start, last}); start = 0; }
        }
        if (start != 0) out.add(new long[]{start, last});
        return out;
    }

    /** Which Private DNS is in use; Cloudflare is the automatic backup when AdGuard is down (see NetMonitor). */
    private String dnsName() {
        String mode = android.provider.Settings.Global.getString(getContentResolver(), "private_dns_mode");
        String host = android.provider.Settings.Global.getString(getContentResolver(), "private_dns_specifier");
        if (!"hostname".equals(mode) || host == null) return "router DNS";
        if (NetMonitor.DNS_MAIN.equals(host)) return "AdGuard DNS";
        if (NetMonitor.isPersonal(host)) {
            long until = Theme.prefs(this).getLong("dns_main_until", 0);
            return "AdGuard (personal" + (until > 0 ? ", until " + new java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(new java.util.Date(until)) : "") + ")";
        }
        if (NetMonitor.DNS_BACKUP.equals(host)) return Theme.prefs(this).getBoolean("dns_on_backup", false) ? "Cloudflare (backup)" : "Cloudflare DNS";
        return host;
    }

    private View stat(String label, String value, int color, String note) {
        LinearLayout c = card(null);
        c.addView(Ui.text(this, label, 12, th.dim, th.bodyFont));
        c.addView(Ui.text(this, value, 20, color, "Poppins-SemiBold"));
        c.addView(Ui.text(this, note, 11, th.dim, th.bodyFont));
        return c;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1);
        l.rightMargin = Ui.dp(this, 12);
        return l;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(this, th, Ui.dp(this, th.radiusDp));
        c.setBackground(g.d);
        int p = Ui.dp(this, 14);
        c.setPadding(p, p, p, p + g.edge);
        if (title != null) {
            TextView t = Ui.text(this, title, 15, th.text, th.titleFont);
            t.setPadding(0, 0, 0, Ui.dp(this, 6));
            c.addView(t);
        }
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }
}

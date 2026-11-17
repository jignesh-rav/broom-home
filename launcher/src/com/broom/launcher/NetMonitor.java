package com.broom.launcher;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Network monitor (runs in the always-on helper service):
 *  - every 30 min: reachability + latency probe (HTTP 204, ~0 bytes), Wi-Fi signal and link speed
 *  - every 1 h, only while the TV is idle: 5 MB download speed test (up to ~120 MB/day); also on demand
 * Keeps 24 h of samples on disk for the Network screen and the home status line.
 */
final class NetMonitor {
    static final long PROBE_MS = 30 * 60000L, SPEED_MS = 3600000L;
    static final int KEEP = 200;   // 24 h of 30-minute samples plus on-demand checks (older rows are dropped by age)

    static final class Sample { long t; boolean ok; int lat, rssi, link; }
    static final class Speed { long t; double mbps; }

    private final Context c;
    private final Handler h = new Handler();
    private boolean running;
    String foreground;

    NetMonitor(Context c) { this.c = c.getApplicationContext(); }

    void start() { if (!running) { running = true; h.postDelayed(loop, 30000); } }

    void stop() { running = false; h.removeCallbacks(loop); }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            new Thread(new Runnable() {
                @Override public void run() {
                    probe(c);
                    long last = lastSpeedTime(c);
                    boolean idle = foreground == null || foreground.equals(c.getPackageName()) || foreground.contains("dream");
                    if (idle && System.currentTimeMillis() - last > SPEED_MS - 2 * 60000L) speedTest(c);   // small tolerance: tests run on a probe tick
                }
            }).start();
            h.postDelayed(this, PROBE_MS);
        }
    };

    // ------------------------------------------------------------------ measurements
    static Sample probe(Context c) {
        Sample s = new Sample();
        s.t = System.currentTimeMillis();
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            if (wi != null) { s.rssi = wi.getRssi(); s.link = wi.getLinkSpeed(); }
        } catch (Exception ignored) { }
        // first request warms DNS + TLS (keep-alive), the second one measures the real round trip
        for (int k = 0; k < 2; k++) {
            long t0 = System.nanoTime();
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL("https://connectivitycheck.gstatic.com/generate_204").openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setUseCaches(false);
                s.ok = con.getResponseCode() == 204;
                con.getInputStream().close();
                s.lat = (int) ((System.nanoTime() - t0) / 1000000);
            } catch (Exception e) { s.ok = false; break; }
        }
        append(c, "net_samples.csv", s.t + "," + (s.ok ? 1 : 0) + "," + s.lat + "," + s.rssi + "," + s.link, KEEP);
        return s;
    }

    /** Downloads ~5 MB and returns Mbps (or -1). */
    static double speedTest(Context c) {
        long t0 = System.nanoTime(), bytes = 0;
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=5000000").openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(20000);
            con.setRequestProperty("User-Agent", "BroomHome/10");
            try (InputStream in = con.getInputStream()) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) bytes += n;
            }
        } catch (Exception e) { return -1; }
        finally { if (con != null) con.disconnect(); }
        double secs = (System.nanoTime() - t0) / 1e9;
        double mbps = bytes * 8 / 1e6 / Math.max(0.05, secs);
        append(c, "net_speed.csv", System.currentTimeMillis() + "," + String.format(java.util.Locale.US, "%.1f", mbps), 60);
        return mbps;
    }

    // ------------------------------------------------------------------ history
    static List<Sample> samples(Context c) {
        List<Sample> out = new ArrayList<>();
        for (String l : lines(c, "net_samples.csv")) {
            String[] p = l.split(",");
            if (p.length < 5) continue;
            try {
                Sample s = new Sample();
                s.t = Long.parseLong(p[0]); s.ok = p[1].equals("1"); s.lat = Integer.parseInt(p[2]);
                s.rssi = Integer.parseInt(p[3]); s.link = Integer.parseInt(p[4]);
                if (System.currentTimeMillis() - s.t <= 24 * 3600000L) out.add(s);
            } catch (Exception ignored) { }
        }
        return out;
    }

    static List<Speed> speeds(Context c) {
        List<Speed> out = new ArrayList<>();
        for (String l : lines(c, "net_speed.csv")) {
            String[] p = l.split(",");
            try { Speed s = new Speed(); s.t = Long.parseLong(p[0]); s.mbps = Double.parseDouble(p[1]); out.add(s); } catch (Exception ignored) { }
        }
        return out;
    }

    static long lastSpeedTime(Context c) {
        List<Speed> s = speeds(c);
        return s.isEmpty() ? 0 : s.get(s.size() - 1).t;
    }

    /** Signal bars 0..4 from RSSI. */
    static int bars(int rssi) {
        if (rssi == 0) return 0;
        return rssi >= -55 ? 4 : rssi >= -65 ? 3 : rssi >= -75 ? 2 : rssi >= -85 ? 1 : 0;
    }

    static String barsText(int rssi) { return new String[]{"▁", "▂", "▂▄", "▂▄▆", "▂▄▆█"}[bars(rssi)]; }

    private static synchronized void append(Context c, String name, String line, int keep) {
        File f = new File(c.getFilesDir(), name);
        List<String> l = lines(c, name);
        l.add(line);
        if (l.size() > keep) l = l.subList(l.size() - keep, l.size());
        try (FileWriter w = new FileWriter(f, false)) { for (String x : l) { w.write(x); w.write('\n'); } } catch (Exception ignored) { }
    }

    private static List<String> lines(Context c, String name) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) if (!l.isEmpty()) out.add(l);
        } catch (Exception ignored) { }
        return out;
    }
}

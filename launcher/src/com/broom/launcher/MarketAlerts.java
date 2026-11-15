package com.broom.launcher;

import android.os.Handler;

import java.util.HashMap;
import java.util.Map;

/**
 * Market move alerts: every 10 minutes while a market is open, checks the headline indices and the
 * watchlist stocks. Each threshold level (e.g. 1%, 2%, 3%) pops up once per day per symbol and direction.
 */
final class MarketAlerts {
    private static final String[] INDICES = {"^NSEI", "^BSESN", "^GSPC", "^IXIC"};
    private final android.accessibilityservice.AccessibilityService svc;
    private final ScorePopup popup;
    private final Handler h = new Handler();
    private final Map<String, Integer> level = new HashMap<>();
    private String day = "";
    private boolean running;

    MarketAlerts(android.accessibilityservice.AccessibilityService s, ScorePopup p) { svc = s; popup = p; }

    void start() { if (!running) { running = true; h.postDelayed(loop, 60000); } }

    void stop() { running = false; h.removeCallbacks(loop); }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (Briefing.marketsOpen() && (WatchlistActivity.indexAlert(svc) > 0 || WatchlistActivity.stockAlert(svc) > 0)) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { Briefing.fetchMarketsIfStale(svc); check(); } catch (Exception ignored) { }
                    }
                }).start();
            }
            h.postDelayed(this, 10 * 60000L);
        }
    };

    private void check() {
        String today = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString();
        if (!today.equals(day)) { day = today; level.clear(); }
        float idx = WatchlistActivity.indexAlert(svc), stk = WatchlistActivity.stockAlert(svc);
        if (idx > 0) for (Briefing.Quote q : Briefing.quotes(svc, INDICES)) consider(q, idx, true);
        if (stk > 0)
            for (int gi : new int[]{Briefing.WL_INDIA, Briefing.WL_US})
                for (Briefing.Quote q : Briefing.group(svc, gi)) consider(q, stk, false);
    }

    private void consider(final Briefing.Quote q, float thr, final boolean index) {
        if (Briefing.closed(q) || Double.isNaN(q.pct)) return;
        int lv = (int) (Math.abs(q.pct) / thr) * (q.pct >= 0 ? 1 : -1);
        Integer prev = level.get(q.sym);
        if (lv == 0) return;
        if (prev != null && (prev == lv || (Integer.signum(prev) == Integer.signum(lv) && Math.abs(prev) >= Math.abs(lv)))) return;
        level.put(q.sym, lv);
        final boolean up = q.pct >= 0;
        final String head = (up ? "📈  " : "📉  ") + (index ? "MARKET MOVE" : "STOCK ALERT");
        final String line = String.format(java.util.Locale.US, "%s %s %.1f%%  ·  %s", q.name, up ? "up" : "down", Math.abs(q.pct), Briefing.price(q));
        h.post(new Runnable() { @Override public void run() { popup.card(head, line, up ? 0xFF4CD964 : 0xFFFF6B6B); } });
    }

    /** Test hook: show what an alert looks like. */
    void demo() { popup.card("📈  MARKET MOVE", "Nifty 50 up 1.2%  ·  25,450", 0xFF4CD964); }
}

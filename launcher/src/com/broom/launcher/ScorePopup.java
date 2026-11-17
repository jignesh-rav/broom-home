package com.broom.launcher;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Match-day pop-ups: while an India / IPL match is live, a small card appears bottom-right over
 * whatever is playing (apps or HDMI) when the match goes live, on each wicket and every 50 runs.
 * Runs inside the accessibility service so it can use an accessibility overlay (no extra permission).
 */
final class ScorePopup {
    private static final Pattern RUNS = Pattern.compile("(\\d+)(?:/(\\d+))?\\*");
    private final android.accessibilityservice.AccessibilityService svc;
    private final Handler h = new Handler();
    private final Map<String, int[]> last = new HashMap<>();   // match key -> {runs, wickets, live}
    private View shown;
    private boolean running;
    String foreground;

    ScorePopup(android.accessibilityservice.AccessibilityService s) { svc = s; }

    static boolean enabled(Context c) { return Theme.prefs(c).getBoolean("score_popups", true); }

    void start() { if (!running) { running = true; h.postDelayed(loop, 20000); } }

    void stop() { running = false; h.removeCallbacks(loop); hide(); }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            new Thread(new Runnable() {
                @Override public void run() {
                    boolean anyLive = false;
                    try {
                        Briefing.fetchFootballNow(svc);
                        for (Briefing.Match m : Briefing.matches(svc)) {
                            if (!"in".equals(m.state) && !"post".equals(m.state)) continue;
                            if ("in".equals(m.state)) anyLive = true;
                            final String goal = goal(m);
                            if (goal != null && m.big) {
                                final String text = Briefing.line(m);
                                h.post(new Runnable() { @Override public void run() { football(goal, text); } });
                            }
                        }
                    } catch (Exception ignored) { }
                    try {
                        Briefing.fetchScoresNow(svc);
                        for (Briefing.Score s : Briefing.scores(svc)) {
                            if (!s.india) continue;
                            if (s.live) anyLive = true;
                            final String event = check(s);
                            if (event != null) {
                                final String text = s.text;
                                h.post(new Runnable() { @Override public void run() { show(event, text); } });
                                break;
                            }
                        }
                    } catch (Exception ignored) { }
                    if (running) h.postDelayed(loop, (anyLive ? 3 : 15) * 60000L);
                }
            }).start();
        }
    };

    private final java.util.Map<String, int[]> goals = new java.util.HashMap<>();

    /** "GOAL!" / "KICK-OFF" / "FULL TIME" when a followed match changes. */
    private String goal(Briefing.Match m) {
        String key = m.home + "|" + m.away;
        int[] prev = goals.get(key);
        int st = "in".equals(m.state) ? 1 : 2;
        goals.put(key, new int[]{m.hs + m.as, st});
        if (prev == null) return st == 1 ? "KICK-OFF / LIVE" : null;
        if (prev[1] == 1 && st == 2) return "FULL TIME";
        if (m.hs + m.as > prev[0]) return "GOAL!";
        return null;
    }

    private void football(String event, String text) {
        if (!enabled(svc) || svc.getPackageName().equals(foreground)) return;
        card("⚽  " + event, text, event.startsWith("GOAL") ? 0xFF4CD964 : 0xFFFFD166);
    }

    /** Returns a headline if this score changed in a way worth showing. */
    private String check(Briefing.Score s) {
        String key = s.text.replaceAll("[0-9/*().]", "").replaceAll("\\s+", " ").trim();
        int runs = -1, wk = -1;
        Matcher m = RUNS.matcher(s.text);
        if (m.find()) { runs = Integer.parseInt(m.group(1)); wk = m.group(2) == null ? 0 : Integer.parseInt(m.group(2)); }
        int[] prev = last.get(key);
        last.put(key, new int[]{runs, wk, s.live ? 1 : 0});
        if (prev == null) return s.live ? "LIVE NOW" : null;          // first time we see this match
        if (prev[2] == 1 && !s.live) return "INNINGS / MATCH UPDATE";
        if (!s.live || runs < 0) return null;
        if (prev[0] >= 0 && runs < prev[0]) return "NEW INNINGS";
        if (wk > prev[1] && prev[1] >= 0) return "WICKET!";
        if (prev[0] >= 0 && runs / 50 > prev[0] / 50) return (runs / 50 * 50) + " UP";
        return null;
    }

    private void show(String event, String score) {
        if (!enabled(svc)) return;
        if (svc.getPackageName().equals(foreground)) return;      // Broom home already shows the score strip
        card("🏏  " + event, score, event.startsWith("WICKET") ? 0xFFFF6B60 : 0xFFFFD166);
    }

    static final long SHOW_MS = 90000L;

    boolean showing() { return shown != null; }

    /** A pop-up card bottom-right for 90 s (used by cricket, football, market alerts and the pop-up updates). */
    void card(String head, String line, int headColor) {
        hide();
        Theme th = Theme.current(svc);
        final LinearLayout card = new LinearLayout(svc);
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(svc, th, Ui.dp(svc, 16));
        card.setBackground(g.d);
        int p = Ui.dp(svc, 14);
        card.setPadding(p, Ui.dp(svc, 10), p, Ui.dp(svc, 10) + g.edge);
        TextView ev = Ui.text(svc, head, 13, headColor, "Poppins-SemiBold");
        card.addView(ev);
        TextView sc = Ui.text(svc, line, 16, 0xFFFFFFFF, "Poppins-SemiBold");
        sc.setPadding(0, Ui.dp(svc, 3), 0, 0);
        card.addView(sc);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = Ui.dp(svc, 32);
        lp.y = Ui.dp(svc, 32);
        try {
            WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
            card.setAlpha(0f);
            wm.addView(card, lp);
            shown = card;
            card.animate().alpha(1f).setDuration(400).start();
            Sounds.init(svc);
            Sounds.click(svc);
            h.postDelayed(new Runnable() { @Override public void run() { if (shown == card) hide(); } }, SHOW_MS);
        } catch (Exception ignored) { shown = null; }
    }

    /** For testing: show the current followed score immediately. */
    void demo() {
        List<Briefing.Score> sc = Briefing.scores(svc);
        for (Briefing.Score s : sc) if (s.india) { show("LIVE NOW", s.text); return; }
        show("NO LIVE MATCH", "Pop-ups appear during India / IPL matches");
    }

    private void hide() {
        if (shown == null) return;
        final View v = shown;
        shown = null;
        try {
            v.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                @Override public void run() {
                    try { ((WindowManager) svc.getSystemService(Context.WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) { }
                }
            }).start();
        } catch (Exception ignored) { }
    }
}

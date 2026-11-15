package com.broom.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

/**
 * Show / hide whole features. Everything is on by default; a switched-off feature is not fetched
 * and disappears from home, Briefing, the screensaver, pop-ups and alerts.
 */
final class Features {
    static final String MARKETS = "markets", CRICKET = "cricket", FOOTBALL = "football", PANCHANG = "panchang";

    private static final String[] KEYS = {"f_" + MARKETS, "f_" + CRICKET, "f_" + FOOTBALL, "f_" + PANCHANG,
            "n_cricket", "n_football", "n_tech", "n_ai", "n_space", "n_markets"};
    private static final String[] LABELS = {"Markets: indices, stocks, home ticker, alerts", "Cricket live scores",
            "Football scores", "Panchang: home line, button, screensaver",
            "News: Cricket", "News: Football", "News: Tech", "News: AI", "News: Space", "News: Market news"};

    private Features() { }

    static boolean on(Context c, String feature) { return Theme.prefs(c).getBoolean("f_" + feature, true); }

    /** News section (cricket, football, tech, ai, space, markets) shown and fetched? */
    static boolean news(Context c, String section) { return Theme.prefs(c).getBoolean("n_" + section, true); }

    static boolean anyNews(Context c) {
        for (String s : Briefing.SECTIONS) if (news(c, s)) return true;
        return false;
    }

    /** Anything at all left for the Briefing page? */
    static boolean anyBriefing(Context c) { return on(c, MARKETS) || on(c, CRICKET) || on(c, FOOTBALL) || anyNews(c); }

    /** Tick-box list; calls done after Save so the caller can redraw. */
    static void dialog(final Context c, final Runnable done) {
        final boolean[] v = new boolean[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) v[i] = Theme.prefs(c).getBoolean(KEYS[i], true);
        new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Features: tick what to show")
                .setMultiChoiceItems(LABELS, v, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int i, boolean checked) { v[i] = checked; }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        android.content.SharedPreferences.Editor e = Theme.prefs(c).edit();
                        for (int i = 0; i < KEYS.length; i++) e.putBoolean(KEYS[i], v[i]);
                        e.apply();
                        // refetch news so newly enabled sections fill in
                        new java.io.File(c.getFilesDir(), "brief_news.json").setLastModified(0);
                        Toast.makeText(c, "Saved", Toast.LENGTH_SHORT).show();
                        if (done != null) done.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

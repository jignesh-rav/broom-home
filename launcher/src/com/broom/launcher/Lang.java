package com.broom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * Display languages: English, Hindi, Gujarati, Marathi.
 *  - Panchang language: one of the four (Panchang screen, home Panchang line, Panchang screensaver)
 *  - Screensaver languages: any of the four, all by default (Desi Clock and Sayings). The screensaver rotates
 *    among the chosen ones in a random order, a new one every 2 minutes, never the same twice in a row.
 *  - News languages: any subset (adds Hindi / Gujarati / Marathi headline sections to the Briefing)
 * Hindi and Marathi share the Devanagari script; Gujarati has its own script and digits.
 */
final class Lang {
    static final String EN = "en", HI = "hi", GU = "gu", MR = "mr";
    static final String[] CODES = {EN, HI, GU, MR};
    static final String[] LABELS = {"English", "हिन्दी  (Hindi)", "ગુજરાતી  (Gujarati)", "मराठी  (Marathi)"};

    private Lang() { }

    static String panchang(Context c) { return Theme.prefs(c).getString("lang_panchang", EN); }

    /** Is this language one of the screensaver's (pref lang_dream_xx, all on by default)? */
    static boolean dream(Context c, String code) { return Theme.prefs(c).getBoolean("lang_dream_" + code, true); }

    /** The chosen screensaver languages; all four if none is ticked. */
    static java.util.List<String> dreamLangs(Context c) {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String code : CODES) if (dream(c, code)) l.add(code);
        if (l.isEmpty()) l.addAll(java.util.Arrays.asList(CODES));
        return l;
    }

    /**
     * The screensaver language for the current 2-minute slot. Each round shows every chosen language once in a
     * random order and never starts with the language the round before ended on, so none shows twice in a row.
     * Two languages simply alternate.
     */
    static String screensaver(Context c) {
        java.util.List<String> l = dreamLangs(c);
        int n = l.size();
        if (n == 1) return l.get(0);
        long slot = System.currentTimeMillis() / DesiDream.INFO_EVERY;
        if (n == 2) return l.get((int) (slot % 2));
        return l.get(round(slot / n, n).get((int) (slot % n)));
    }

    private static int lastOf(long round, int n) { return new java.util.Random(round * 7919L + n).nextInt(n); }

    /** Round r: a fixed random last, a first that is neither that nor the previous round's last, the rest shuffled. */
    private static java.util.List<Integer> round(long r, int n) {
        int last = lastOf(r, n), prevLast = lastOf(r - 1, n);
        java.util.Random rnd = new java.util.Random(r * 104729L + 17);
        java.util.List<Integer> firsts = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) if (i != last && i != prevLast) firsts.add(i);
        int first = firsts.get(rnd.nextInt(firsts.size()));
        java.util.List<Integer> mid = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) if (i != first && i != last) mid.add(i);
        java.util.Collections.shuffle(mid, rnd);
        java.util.List<Integer> out = new java.util.ArrayList<>();
        out.add(first);
        out.addAll(mid);
        out.add(last);
        return out;
    }

    static String dreamSummary(Context c) {
        java.util.List<String> l = dreamLangs(c);
        if (l.size() == CODES.length) return "all 4, rotating";
        StringBuilder b = new StringBuilder();
        for (String code : l) b.append(b.length() == 0 ? "" : ", ").append(LABELS[index(code)].split(" ")[0]);
        return l.size() > 1 ? b + ", rotating" : b.toString();
    }

    /** Is the news section in this language shown and fetched? English is always on via the existing sections. */
    static boolean news(Context c, String code) {
        return EN.equals(code) || Theme.prefs(c).getBoolean("lang_news_" + code, true);
    }

    static String label(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return LABELS[i];
        return LABELS[0];
    }

    static int index(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return 0;
    }

    /** ASCII digits -> native digits (Devanagari for hi/mr, Gujarati for gu); English unchanged. */
    static String digits(String s, String code) {
        if (s == null) return null;
        char zero;
        if (HI.equals(code) || MR.equals(code)) zero = '०';
        else if (GU.equals(code)) zero = '૦';
        else return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch >= '0' && ch <= '9' ? (char) (zero + (ch - '0')) : ch);
        }
        return b.toString();
    }

    static String summary(Context c) {
        StringBuilder n = new StringBuilder();
        for (String code : new String[]{HI, GU, MR}) if (news(c, code)) n.append(n.length() == 0 ? "" : ", ").append(LABELS[index(code)].split(" ")[0]);
        return "Panchang " + LABELS[index(panchang(c))].split(" ")[0] + " · Screensaver " + dreamSummary(c)
                + (n.length() > 0 ? " · News " + n : "");
    }

    /** Settings → Language. onChange runs after any change (e.g. to rebuild home). */
    static void dialog(final Activity a, final Runnable onChange) {
        final String[] items = {"Panchang language: " + label(panchang(a)),
                "Screensaver languages: " + dreamSummary(a) + " (a new one every 2 min)",
                "News languages (Hindi, Gujarati, Marathi)"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Language")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) pick(a, "Panchang language", "lang_panchang", panchang(a), onChange);
                        else if (w == 1) dreamDialog(a, onChange);
                        else newsDialog(a, onChange);
                    }
                }).show();
    }

    private static void pick(final Activity a, String title, final String key, String cur, final Runnable onChange) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(LABELS, index(cur), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Theme.prefs(a).edit().putString(key, CODES[w]).apply();
                        d.dismiss();
                        if (onChange != null) onChange.run();
                        dialog(a, onChange);
                    }
                }).show();
    }

    /** Tick any of the four; the screensaver rotates among the ticked ones. At least one must stay ticked. */
    private static void dreamDialog(final Activity a, final Runnable onChange) {
        final boolean[] on = new boolean[CODES.length];
        for (int i = 0; i < CODES.length; i++) on[i] = dream(a, CODES[i]);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Screensaver languages (rotate every 2 min)")
                .setMultiChoiceItems(LABELS, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int w, boolean v) { on[w] = v; }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        boolean any = false;
                        for (boolean b : on) any |= b;
                        if (!any) {
                            android.widget.Toast.makeText(a, "Keep at least one language ticked", android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        android.content.SharedPreferences.Editor e = Theme.prefs(a).edit();
                        for (int i = 0; i < CODES.length; i++) e.putBoolean("lang_dream_" + CODES[i], on[i]);
                        e.apply();
                        if (onChange != null) onChange.run();
                        dialog(a, onChange);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void newsDialog(final Activity a, final Runnable onChange) {
        final String[] codes = {HI, GU, MR};
        final String[] labels = {LABELS[1], LABELS[2], LABELS[3]};
        final boolean[] on = new boolean[codes.length];
        for (int i = 0; i < codes.length; i++) on[i] = news(a, codes[i]);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("News languages")
                .setMultiChoiceItems(labels, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int w, boolean v) { on[w] = v; }
                })
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        android.content.SharedPreferences.Editor e = Theme.prefs(a).edit();
                        for (int i = 0; i < codes.length; i++) e.putBoolean("lang_news_" + codes[i], on[i]);
                        e.apply();
                        if (onChange != null) onChange.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

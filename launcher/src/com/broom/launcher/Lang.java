package com.broom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * Display languages: English, Hindi, Gujarati, Marathi.
 *  - Panchang language: one of the four (Panchang screen, home Panchang line, Panchang screensaver)
 *  - Screensaver language: one of the four (Indian-language clock / date / weather / sayings screensaver)
 *  - News languages: any subset (adds Hindi / Gujarati / Marathi headline sections to the Briefing)
 * Hindi and Marathi share the Devanagari script; Gujarati has its own script and digits.
 */
final class Lang {
    static final String EN = "en", HI = "hi", GU = "gu", MR = "mr";
    static final String[] CODES = {EN, HI, GU, MR};
    static final String[] LABELS = {"English", "हिन्दी  (Hindi)", "ગુજરાતી  (Gujarati)", "मराठी  (Marathi)"};

    private Lang() { }

    static String panchang(Context c) { return Theme.prefs(c).getString("lang_panchang", EN); }

    static String screensaver(Context c) { return Theme.prefs(c).getString("lang_dream", GU); }

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
        return "Panchang " + LABELS[index(panchang(c))].split(" ")[0] + " · Screensaver " + LABELS[index(screensaver(c))].split(" ")[0]
                + (n.length() > 0 ? " · News " + n : "");
    }

    /** Settings → Language. onChange runs after any change (e.g. to rebuild home). */
    static void dialog(final Activity a, final Runnable onChange) {
        final String[] items = {"Panchang language: " + label(panchang(a)),
                "Screensaver language: " + label(screensaver(a)),
                "News languages (Hindi, Gujarati, Marathi)"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Language")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) pick(a, "Panchang language", "lang_panchang", panchang(a), onChange);
                        else if (w == 1) pick(a, "Screensaver language", "lang_dream", screensaver(a), onChange);
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

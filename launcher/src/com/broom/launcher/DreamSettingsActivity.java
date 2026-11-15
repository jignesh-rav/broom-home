package com.broom.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Screensaver style + timers. Timers write the same system settings the TV's own menu uses
 * (screen_off_timeout = when the screensaver starts, sleep_timeout = when the TV turns off).
 */
public class DreamSettingsActivity extends Activity {
    private static final int[] START_MIN = {1, 3, 5, 10, 15, 30, 60, 120};
    private static final int[] OFF_MIN = {-1, 30, 60, 120, 240, 480};
    private Theme th;
    private LinearLayout col;
    private String focusTag;

    @Override protected void onResume() { super.onResume();
        Sounds.init(this);
        Sounds.quiet(600); build(); }

    private void build() {
        th = Theme.current(this);
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
        int pad = Ui.dp(this, 44);
        col.setPadding(pad, Ui.dp(this, 26), pad, pad);
        col.addView(Ui.text(this, "Screensaver", 34, th.text, "Poppins-SemiBold"));

        section("Style");
        String cur = DreamView.saved(this);
        for (int i = 0; i < DreamView.MODES.length; i++) {
            final String m = DreamView.MODES[i];
            if (m.equals("panchang") && !Features.on(this, Features.PANCHANG)) continue;
            LinearLayout row = row();
            TextView pick = Ui.pill(this, (m.equals(cur) ? "●  " : "○  ") + DreamView.MODE_NAMES[i], th, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    DreamView.save(DreamSettingsActivity.this, m);
                    ensureOurDream();
                    focusTag = "style:" + m;
                    build();
                }
            });
            pick.setTag("style:" + m);
            pick.setMinWidth(Ui.dp(this, 210));
            pick.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(pick, margin());
            TextView prev = Ui.pill(this, "Preview", th, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    startActivity(new Intent(DreamSettingsActivity.this, DreamPreviewActivity.class).putExtra("mode", m));
                }
            });
            row.addView(prev, margin());
            TextView d = Ui.text(this, DreamView.MODE_DESC[i], 12, th.dim, th.bodyFont);
            d.setPadding(Ui.dp(this, 16), 0, 0, 0);
            row.addView(d, new LinearLayout.LayoutParams(0, -2, 1));
            col.addView(row);
        }

        final ContentResolver cr = getContentResolver();
        int startMs = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 600000);
        section("Start screensaver after (no remote activity)");
        LinearLayout r1 = chips();
        for (final int mins : START_MIN) {
            boolean on = startMs == mins * 60000;
            TextView b = Ui.pill(this, (on ? "● " : "") + label(mins), th, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    focusTag = "start:" + mins;
                    try {
                        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, mins * 60000);
                        Settings.Secure.putInt(cr, "screensaver_enabled", 1);
                        ensureOurDream();
                        Toast.makeText(DreamSettingsActivity.this, "Screensaver starts after " + label(mins), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { denied(); }
                    build();
                }
            });
            b.setTag("start:" + mins);
            r1.addView(b, margin());
        }
        col.addView(r1);

        int offMs = Settings.Secure.getInt(cr, "sleep_timeout", -1);
        section("Turn TV off after (while on screensaver / idle)");
        LinearLayout r2 = chips();
        for (final int mins : OFF_MIN) {
            boolean on = mins < 0 ? offMs < 0 : offMs == mins * 60000;
            TextView b = Ui.pill(this, (on ? "● " : "") + (mins < 0 ? "Never" : label(mins)), th, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    focusTag = "off:" + mins;
                    try {
                        Settings.Secure.putInt(cr, "sleep_timeout", mins < 0 ? -1 : mins * 60000);
                    } catch (Exception e) { denied(); }
                    build();
                }
            });
            b.setTag("off:" + mins);
            r2.addView(b, margin());
        }
        col.addView(r2);

        section("Briefing inside every screensaver");
        LinearLayout r3 = chips();
        final boolean bOn = DreamView.briefEverywhere(this);
        for (final boolean opt : new boolean[]{true, false}) {
            TextView b = Ui.pill(this, (opt == bOn ? "● " : "") + (opt ? "On — every 3 min, show markets, scores & news" : "Off"), th,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            Theme.prefs(DreamSettingsActivity.this).edit().putBoolean("brief_in_screensaver", opt).apply();
                            focusTag = "brief:" + opt;
                            build();
                        }
                    });
            b.setTag("brief:" + opt);
            r3.addView(b, margin());
        }
        col.addView(r3);

        section("Night power saving (midnight – 6 AM: Minimal Clock, no Briefing)");
        LinearLayout r4 = chips();
        final boolean nOn = Theme.prefs(this).getBoolean("night_saver", true);
        for (final boolean opt : new boolean[]{true, false}) {
            TextView b = Ui.pill(this, (opt == nOn ? "● " : "") + (opt ? "On" : "Off"), th, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Theme.prefs(DreamSettingsActivity.this).edit().putBoolean("night_saver", opt).apply();
                    focusTag = "night:" + opt;
                    build();
                }
            });
            b.setTag("night:" + opt);
            r4.addView(b, margin());
        }
        col.addView(r4);

        TextView note = Ui.text(this, "Tip: the screensaver uses no extra memory while you watch — it only runs when the TV is idle.", 11, th.dim, th.bodyFont);
        note.setPadding(0, Ui.dp(this, 18), 0, 0);
        col.addView(note);

        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        View f = focusTag != null ? col.findViewWithTag(focusTag) : col.findViewWithTag("style:" + cur);
        final View target = f;
        if (target != null) target.post(new Runnable() { @Override public void run() { target.requestFocus(); } });
    }

    private void ensureOurDream() {
        try {
            Settings.Secure.putString(getContentResolver(), "screensaver_components", getPackageName() + "/.BroomDream");
            Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
        } catch (Exception ignored) { }
    }

    private void denied() {
        Toast.makeText(this, "Broom needs the settings permission (granted over ADB during setup)", Toast.LENGTH_LONG).show();
    }

    private static String label(int mins) {
        if (mins >= 60) return (mins / 60) + (mins == 60 ? " hour" : " hours");
        return mins + " min";
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 15, th.text, th.titleFont);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        col.addView(t);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 5), 0, Ui.dp(this, 5));
        return row;
    }

    private LinearLayout chips() { return row(); }

    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(this, 10);
        return lp;
    }
}

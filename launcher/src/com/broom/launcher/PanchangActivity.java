package com.broom.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Hindu calendar for today: Panchang details, muhurat timings, Choghadiya and upcoming festivals. */
public class PanchangActivity extends Activity {
    static final int GOOD = 0xFF4CD964, NEUTRAL = 0xFFFFD166, BAD = 0xFFFF6B6B;
    private Theme th;
    private LinearLayout col;
    private final Handler h = new Handler();
    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); h.postDelayed(this, 60000); }
    };

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
        sv.setFocusable(true);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 20), pad, pad);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        sv.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private void render() {
        col.removeAllViews();
        long now = System.currentTimeMillis();
        Vedic.Day d = Vedic.today(this);
        Panchang p = d.p;

        // ---------- header
        col.addView(Ui.text(this, "Panchang", 30, th.text, "Poppins-SemiBold"));
        TextView sub = Ui.text(this, p.gujarati() + "   ·   " + Vedic.VAAR_GU[d.weekday] + "   ·   " + DateFormat.format("EEEE, d MMMM yyyy", now)
                + (p.festival != null ? "   ·   " + p.festival : ""), 14, 0xFFFFD166, "Poppins-Medium");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(sub);

        // ---------- today + timings
        LinearLayout top = row();
        top.setPadding(0, Ui.dp(this, 14), 0, 0);
        LinearLayout today = card("Today");
        String tithi = (p.tithiIdx == 14 || p.tithiIdx == 29) ? p.tithiName() : (p.shukla() ? "Shukla " : "Krishna ") + p.tithiName();
        kv(today, "Vaar", Vedic.VAAR[d.weekday]);
        kv(today, "Tithi", tithi + "  ·  till " + whenTxt(d.p, d.tithiEnds, now));
        kv(today, "Paksha", p.shukla() ? "Shukla (Sud)" : "Krishna (Vad)");
        kv(today, "Nakshatra", Panchang.NAKSHATRA[p.nakshatra] + "  ·  till " + whenTxt(d.p, d.nakshatraEnds, now));
        kv(today, "Yoga", Vedic.YOGA[d.yoga]);
        kv(today, "Karana", d.karana);
        kv(today, "Moon rashi", Vedic.RASHI[d.moonRashi] + "  (" + Vedic.RASHI_GU[d.moonRashi] + ")");
        kv(today, "Sun rashi", Vedic.RASHI[d.sunRashi] + "  (" + Vedic.RASHI_GU[d.sunRashi] + ")");
        kv(today, "Month", Panchang.MONTH[p.monthIdx] + "  (" + Panchang.MONTH_GU[p.monthIdx] + ", amanta)");
        kv(today, "Samvat", "Vikram " + p.samvat + "  ·  Shaka " + d.shaka);
        kv(today, "Ritu", d.ritu);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1.15f);
        l1.rightMargin = Ui.dp(this, 14);
        top.addView(today, l1);

        LinearLayout times = card("Timings");
        kv(times, "Sunrise", Vedic.hm(d.sunrise));
        kv(times, "Sunset", Vedic.hm(d.sunset));
        slot(times, d.abhijit, now);
        slot(times, d.rahu, now);
        slot(times, d.yama, now);
        slot(times, d.gulika, now);
        TextView note = Ui.text(this, "Computed offline for " + placeName() + ". Times can differ by a few minutes from printed panchangs.",
                10, th.dim, th.bodyFont);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        times.addView(note);
        top.addView(times, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);

        // ---------- choghadiya
        LinearLayout ch = row();
        ch.setPadding(0, Ui.dp(this, 14), 0, 0);
        LinearLayout day = card("Day Choghadiya");
        for (Vedic.Slot s : d.dayChog) chog(day, s, now);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1);
        l2.rightMargin = Ui.dp(this, 14);
        ch.addView(day, l2);
        LinearLayout night = card("Night Choghadiya");
        for (Vedic.Slot s : d.nightChog) chog(night, s, now);
        ch.addView(night, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(ch);
        TextView legend = Ui.text(this, "●  Amrit · Shubh · Labh = good      ●  Chal = neutral      ●  Udveg · Kaal · Rog = avoid", 11, th.dim, th.bodyFont);
        legend.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(legend);

        // ---------- festivals
        LinearLayout fe = card("Upcoming festivals & fasts (next 90 days)");
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(-1, -2);
        l3.topMargin = Ui.dp(this, 14);
        List<Vedic.Fest> fs = Vedic.upcoming(this, 90);
        if (fs.isEmpty()) fe.addView(Ui.text(this, "None found", 13, th.dim, th.bodyFont));
        for (Vedic.Fest f : fs) {
            LinearLayout r = row();
            r.setPadding(0, Ui.dp(this, 5), 0, 0);
            r.addView(Ui.text(this, String.valueOf(DateFormat.format("EEE, d MMM", f.day)), 13, th.text, "Poppins-Medium"),
                    new LinearLayout.LayoutParams(Ui.dp(this, 120), -2));
            r.addView(Ui.text(this, f.guDate, 13, 0xFFFFD166, "Poppins-Regular"), new LinearLayout.LayoutParams(Ui.dp(this, 150), -2));
            r.addView(Ui.text(this, f.name, 13, th.text, "Poppins-Regular"), new LinearLayout.LayoutParams(0, -2, 1));
            fe.addView(r);
        }
        TextView fn = Ui.text(this, "Festival days follow the tithi at sunrise (Gujarati amanta calendar). Adhik maas years can shift a festival — check your family panchang for vrat dates.",
                10, th.dim, th.bodyFont);
        fn.setPadding(0, Ui.dp(this, 10), 0, 0);
        fe.addView(fn);
        col.addView(fe, l3);
    }

    private String placeName() {
        String c = Theme.prefs(this).getString("wx_city", "");
        return c.isEmpty() ? "Mumbai" : c;
    }

    private String whenTxt(Panchang p, long t, long now) {
        String s = Vedic.hm(t);
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        b.setTimeInMillis(t);
        if (a.get(java.util.Calendar.DAY_OF_YEAR) != b.get(java.util.Calendar.DAY_OF_YEAR)) s += " (" + DateFormat.format("EEE", t) + ")";
        return s;
    }

    private void kv(LinearLayout c, String k, String v) {
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(this, 4), 0, 0);
        r.addView(Ui.text(this, k, 12, th.dim, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 96), -2));
        r.addView(Ui.text(this, v, 13, th.text, "Poppins-Medium"), new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(r);
    }

    private void slot(LinearLayout c, Vedic.Slot s, long now) {
        boolean on = now >= s.from && now < s.to;
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(this, 4), 0, 0);
        r.addView(Ui.text(this, s.name, 12, s.quality > 0 ? GOOD : BAD, "Poppins-Medium"), new LinearLayout.LayoutParams(Ui.dp(this, 130), -2));
        r.addView(Ui.text(this, Vedic.hm(s.from) + " – " + Vedic.hm(s.to) + (on ? "   ◀ now" : ""), 13, th.text, on ? "Poppins-SemiBold" : "Poppins-Regular"),
                new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(r);
    }

    private void chog(LinearLayout c, Vedic.Slot s, long now) {
        boolean on = now >= s.from && now < s.to;
        int col = s.quality > 0 ? GOOD : s.quality == 0 ? NEUTRAL : BAD;
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        if (on) r.setBackground(Ui.round((col & 0xFFFFFF) | 0x40000000, Ui.dp(this, 8)));
        View dot = new View(this);
        dot.setBackground(Ui.round(col, Ui.dp(this, 5)));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9));
        dl.rightMargin = Ui.dp(this, 10);
        r.addView(dot, dl);
        r.addView(Ui.text(this, s.name, 14, th.text, on ? "Poppins-SemiBold" : "Poppins-Regular"), new LinearLayout.LayoutParams(Ui.dp(this, 90), -2));
        r.addView(Ui.text(this, Vedic.hm(s.from) + " – " + Vedic.hm(s.to) + (on ? "   ◀ now" : ""), 13, on ? th.text : th.dim, on ? "Poppins-SemiBold" : "Poppins-Regular"),
                new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(r);
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(this, th, Ui.dp(this, th.radiusDp));
        c.setBackground(g.d);
        int p = Ui.dp(this, 14);
        c.setPadding(p, p, p, p + g.edge);
        TextView t = Ui.text(this, title, 15, th.text, th.titleFont);
        t.setPadding(0, 0, 0, Ui.dp(this, 4));
        c.addView(t);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }
}

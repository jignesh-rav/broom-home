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
    private String lg = Lang.EN;   // Panchang language
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
        lg = Lang.panchang(this);
        final boolean en = Lang.EN.equals(lg);

        // ---------- header
        col.addView(Ui.text(this, PanchangLang.pick(lg, "Panchang", "पंचांग", "પંચાંગ", "पंचांग"), 30, th.text, "Poppins-SemiBold"));
        TextView sub = Ui.text(this, PanchangLang.date(lg, p) + "   ·   " + (en ? Vedic.VAAR_GU[d.weekday] : PanchangLang.vaar(lg, d.weekday))
                + "   ·   " + PanchangLang.greg(lg, en ? "EEEE, d MMMM yyyy" : "d MMMM yyyy", now)
                + (p.festival != null ? "   ·   " + p.festival : ""), 14, 0xFFFFD166, "Poppins-Medium");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        if (!en) sub.setIncludeFontPadding(true);
        col.addView(sub);

        // ---------- today + timings
        LinearLayout top = row();
        top.setPadding(0, Ui.dp(this, 14), 0, 0);
        LinearLayout today = card(PanchangLang.pick(lg, "Today", "आज", "આજે", "आज"));
        kv(today, PanchangLang.pick(lg, "Vaar", "वार", "વાર", "वार"), PanchangLang.vaar(lg, d.weekday));
        kv(today, PanchangLang.pick(lg, "Tithi", "तिथि", "તિથિ", "तिथी"),
                PanchangLang.tithi(lg, p) + "  ·  " + PanchangLang.till(lg, whenTxt(d.p, d.tithiEnds, now)));
        kv(today, PanchangLang.pick(lg, "Paksha", "पक्ष", "પક્ષ", "पक्ष"), PanchangLang.paksha(lg, p));
        kv(today, PanchangLang.pick(lg, "Nakshatra", "नक्षत्र", "નક્ષત્ર", "नक्षत्र"),
                PanchangLang.nakshatra(lg, p.nakshatra) + "  ·  " + PanchangLang.till(lg, whenTxt(d.p, d.nakshatraEnds, now)));
        kv(today, PanchangLang.pick(lg, "Yoga", "योग", "યોગ", "योग"), PanchangLang.yoga(lg, d.yoga));
        kv(today, PanchangLang.pick(lg, "Karana", "करण", "કરણ", "करण"), PanchangLang.karana(lg, d.karana));
        if (en) {
            kv(today, "Moon rashi", Vedic.RASHI[d.moonRashi] + "  (" + Vedic.RASHI_GU[d.moonRashi] + ")");
            kv(today, "Sun rashi", Vedic.RASHI[d.sunRashi] + "  (" + Vedic.RASHI_GU[d.sunRashi] + ")");
            kv(today, "Month", Panchang.MONTH[p.monthIdx] + "  (" + Panchang.MONTH_GU[p.monthIdx] + ", amanta)");
        } else {
            kv(today, PanchangLang.pick(lg, "", "चंद्र राशि", "ચંદ્ર રાશિ", "चंद्र रास"), PanchangLang.rashi(lg, d.moonRashi));
            kv(today, PanchangLang.pick(lg, "", "सूर्य राशि", "સૂર્ય રાશિ", "सूर्य रास"), PanchangLang.rashi(lg, d.sunRashi));
            kv(today, PanchangLang.pick(lg, "", "मास", "માસ", "मास"), PanchangLang.monthRow(lg, p));
        }
        kv(today, PanchangLang.pick(lg, "Samvat", "संवत", "સંવત", "संवत"), PanchangLang.samvat(lg, p, d.shaka));
        kv(today, PanchangLang.pick(lg, "Ritu", "ऋतु", "ઋતુ", "ऋतू"), PanchangLang.ritu(lg, d.ritu));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1.15f);
        l1.rightMargin = Ui.dp(this, 14);
        top.addView(today, l1);

        LinearLayout times = card(PanchangLang.pick(lg, "Timings", "मुहूर्त और समय", "મુહૂર્ત અને સમય", "मुहूर्त व वेळा"));
        kv(times, PanchangLang.pick(lg, "Sunrise", "सूर्योदय", "સૂર્યોદય", "सूर्योदय"), PanchangLang.hm(lg, d.sunrise));
        kv(times, PanchangLang.pick(lg, "Sunset", "सूर्यास्त", "સૂર્યાસ્ત", "सूर्यास्त"), PanchangLang.hm(lg, d.sunset));
        slot(times, d.abhijit, now);
        slot(times, d.rahu, now);
        slot(times, d.yama, now);
        slot(times, d.gulika, now);
        TextView note = Ui.text(this, PanchangLang.pick(lg, "Computed offline for " + placeName() + ". Times can differ by a few minutes from printed panchangs.",
                placeName() + " के लिए ऑफ़लाइन गणना। समय छपे हुए पंचांग से कुछ मिनट अलग हो सकता है।",
                placeName() + " માટે ઑફલાઇન ગણતરી. સમય છાપેલા પંચાંગથી થોડી મિનિટ જુદો હોઈ શકે.",
                placeName() + " साठी ऑफलाइन गणना. वेळा छापील पंचांगापेक्षा काही मिनिटांनी वेगळ्या असू शकतात."),
                10, th.dim, th.bodyFont);
        note.setPadding(0, Ui.dp(this, 10), 0, 0);
        times.addView(note);
        top.addView(times, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);

        // ---------- choghadiya
        LinearLayout ch = row();
        ch.setPadding(0, Ui.dp(this, 14), 0, 0);
        LinearLayout day = card(PanchangLang.pick(lg, "Day Choghadiya", "दिन का चौघड़िया", "દિવસના ચોઘડિયા", "दिवसाचे चौघडिया"));
        for (Vedic.Slot s : d.dayChog) chog(day, s, now);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1);
        l2.rightMargin = Ui.dp(this, 14);
        ch.addView(day, l2);
        LinearLayout night = card(PanchangLang.pick(lg, "Night Choghadiya", "रात का चौघड़िया", "રાત્રિના ચોઘડિયા", "रात्रीचे चौघडिया"));
        for (Vedic.Slot s : d.nightChog) chog(night, s, now);
        ch.addView(night, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(ch);
        TextView legend = Ui.text(this, en ? "●  Amrit · Shubh · Labh = good      ●  Chal = neutral      ●  Udveg · Kaal · Rog = avoid"
                : "●  " + PanchangLang.chog(lg, "Amrit") + " · " + PanchangLang.chog(lg, "Shubh") + " · " + PanchangLang.chog(lg, "Labh")
                + PanchangLang.pick(lg, "", " = शुभ", " = શુભ", " = शुभ") + "      ●  " + PanchangLang.chog(lg, "Chal")
                + PanchangLang.pick(lg, "", " = सामान्य", " = સામાન્ય", " = सामान्य") + "      ●  " + PanchangLang.chog(lg, "Udveg")
                + " · " + PanchangLang.chog(lg, "Kaal") + " · " + PanchangLang.chog(lg, "Rog") + PanchangLang.pick(lg, "", " = अशुभ", " = અશુભ", " = अशुभ"),
                11, th.dim, th.bodyFont);
        legend.setPadding(0, Ui.dp(this, 8), 0, 0);
        col.addView(legend);

        // ---------- festivals
        LinearLayout fe = card(PanchangLang.pick(lg, "Upcoming festivals & fasts (next 90 days)", "आने वाले त्योहार और व्रत (अगले ९० दिन)",
                "આવનારા તહેવારો અને વ્રત (આગામી ૯૦ દિવસ)", "आगामी सण व व्रते (पुढील ९० दिवस)"));
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(-1, -2);
        l3.topMargin = Ui.dp(this, 14);
        List<Vedic.Fest> fs = Vedic.upcoming(this, 90);
        if (fs.isEmpty()) fe.addView(Ui.text(this, PanchangLang.pick(lg, "None found", "कोई नहीं", "કોઈ નથી", "काही नाही"), 13, th.dim, th.bodyFont));
        for (Vedic.Fest f : fs) {
            LinearLayout r = row();
            r.setPadding(0, Ui.dp(this, 5), 0, 0);
            r.addView(Ui.text(this, PanchangLang.greg(lg, "EEE, d MMM", f.day), 13, th.text, "Poppins-Medium"),
                    new LinearLayout.LayoutParams(Ui.dp(this, 120), -2));
            r.addView(Ui.text(this, en ? f.guDate : PanchangLang.date(lg, Panchang.at(f.day)), 13, 0xFFFFD166, "Poppins-Regular"),
                    new LinearLayout.LayoutParams(Ui.dp(this, en || Lang.GU.equals(lg) ? 150 : 220), -2));
            r.addView(Ui.text(this, f.name, 13, th.text, "Poppins-Regular"), new LinearLayout.LayoutParams(0, -2, 1));
            fe.addView(r);
        }
        TextView fn = Ui.text(this, PanchangLang.pick(lg, "Festival days follow the tithi at sunrise (Gujarati amanta calendar). Adhik maas years can shift a festival — check your family panchang for vrat dates.",
                "त्योहार सूर्योदय की तिथि के अनुसार (अमांत पंचांग)। अधिक मास वाले वर्ष में त्योहार खिसक सकता है — व्रत की तिथि अपने पारिवारिक पंचांग से मिला लें।",
                "તહેવાર સૂર્યોદય સમયની તિથિ મુજબ (અમાંત પંચાંગ). અધિક માસવાળા વર્ષે તહેવાર ખસી શકે — વ્રતની તારીખ તમારા કુટુંબના પંચાંગમાં જોઈ લો.",
                "सण सूर्योदयाच्या तिथीनुसार (अमांत पंचांग). अधिक मास असलेल्या वर्षी सण पुढे-मागे होऊ शकतो — व्रताच्या तारखा आपल्या घरच्या पंचांगात पाहा."),
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
        String s = PanchangLang.hm(lg, t);
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        b.setTimeInMillis(t);
        if (a.get(java.util.Calendar.DAY_OF_YEAR) != b.get(java.util.Calendar.DAY_OF_YEAR)) s += " (" + PanchangLang.greg(lg, "EEE", t) + ")";
        return s;
    }

    private void kv(LinearLayout c, String k, String v) {
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(this, 4), 0, 0);
        TextView kt = Ui.text(this, k, 12, th.dim, th.bodyFont), vt = Ui.text(this, v, 13, th.text, "Poppins-Medium");
        if (!Lang.EN.equals(lg)) { kt.setIncludeFontPadding(true); vt.setIncludeFontPadding(true); }   // room for Indic vowel signs
        r.addView(kt, new LinearLayout.LayoutParams(Ui.dp(this, 96), -2));
        r.addView(vt, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(r);
    }

    private void slot(LinearLayout c, Vedic.Slot s, long now) {
        boolean on = now >= s.from && now < s.to;
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(this, 4), 0, 0);
        r.addView(Ui.text(this, PanchangLang.slot(lg, s.name), 12, s.quality > 0 ? GOOD : BAD, "Poppins-Medium"), new LinearLayout.LayoutParams(Ui.dp(this, 130), -2));
        r.addView(Ui.text(this, PanchangLang.hm(lg, s.from) + " – " + PanchangLang.hm(lg, s.to) + (on ? nowTag() : ""), 13, th.text, on ? "Poppins-SemiBold" : "Poppins-Regular"),
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
        r.addView(Ui.text(this, PanchangLang.chog(lg, s.name), 14, th.text, on ? "Poppins-SemiBold" : "Poppins-Regular"), new LinearLayout.LayoutParams(Ui.dp(this, 90), -2));
        r.addView(Ui.text(this, PanchangLang.hm(lg, s.from) + " – " + PanchangLang.hm(lg, s.to) + (on ? nowTag() : ""), 13, on ? th.text : th.dim, on ? "Poppins-SemiBold" : "Poppins-Regular"),
                new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(r);
    }

    private String nowTag() { return "   ◀ " + PanchangLang.pick(lg, "now", "अभी", "હમણાં", "आता"); }

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

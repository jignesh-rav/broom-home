package com.broom.launcher;

import java.util.Calendar;

/**
 * Offline Panchang from Sun/Moon longitudes (Meeus low-precision series, Lahiri ayanamsa).
 * Tithi/nakshatra are for the current moment; month is amanta (Gujarati calendar), Vikram Samvat
 * starts at Kartak sud 1. Accuracy: within about an hour near tithi boundaries.
 */
final class Panchang {
    static final String[] TITHI = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami",
            "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};
    static final String[] MONTH = {"Chaitra", "Vaishakh", "Jeth", "Ashadh", "Shravan", "Bhadarvo", "Aaso",
            "Kartak", "Magshar", "Posh", "Maha", "Fagan"};
    static final String[] MONTH_GU = {"ચૈત્ર", "વૈશાખ", "જેઠ", "અષાઢ", "શ્રાવણ", "ભાદરવો", "આસો",
            "કારતક", "માગશર", "પોષ", "મહા", "ફાગણ"};
    static final String[] NAKSHATRA = {"Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra", "Punarvasu",
            "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha",
            "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha",
            "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"};

    int monthIdx, tithiIdx, nakshatra, samvat;   // tithiIdx 0..29 (0-14 sud, 15-29 vad)
    String festival;

    boolean shukla() { return tithiIdx < 15; }
    int day() { return tithiIdx % 15 + 1; }          // 1..15 (15 = Purnima / Amas)

    String tithiName() {
        if (tithiIdx == 14) return "Purnima";
        if (tithiIdx == 29) return "Amavasya";
        return TITHI[tithiIdx % 15];
    }

    /** "કારતક સુદ ૭" */
    String gujarati() {
        String num = tithiIdx == 14 ? "પૂનમ" : tithiIdx == 29 ? "અમાસ" : guDigits(day());
        return MONTH_GU[monthIdx] + " " + (shukla() ? "સુદ" : "વદ") + " " + num;
    }

    /** "Kartak Shukla Saptami · VS 2083 · Shravana nakshatra" */
    String english() {
        String t = (tithiIdx == 14 || tithiIdx == 29) ? tithiName() : (shukla() ? "Shukla " : "Krishna ") + tithiName();
        return t + "  ·  VS " + samvat + "  ·  " + NAKSHATRA[nakshatra];
    }

    static Panchang now() { return at(System.currentTimeMillis()); }

    static Panchang at(long ms) {
        Panchang p = new Panchang();
        double jd = ms / 86400000.0 + 2440587.5;
        double elong = norm(moon(jd) - sun(jd));
        p.tithiIdx = (int) (elong / 12.0);
        double ayan = ayanamsa(jd);
        p.nakshatra = (int) (norm(moon(jd) - ayan) / (360.0 / 27));
        // previous new moon: step back by the elongation, refine
        double nm = jd - elong / 12.190749;
        for (int i = 0; i < 3; i++) {
            double e = norm(moon(nm) - sun(nm));
            if (e > 180) e -= 360;
            nm -= e / 12.190749;
        }
        int sign = (int) (norm(sun(nm) - ayanamsa(nm)) / 30.0);
        p.monthIdx = (sign + 1) % 12;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        int year = c.get(Calendar.YEAR), gm = c.get(Calendar.MONTH);
        boolean newYearDone = (p.monthIdx >= 7 && p.monthIdx <= 9) && gm >= Calendar.SEPTEMBER;
        p.samvat = year + (newYearDone ? 57 : 56);
        p.festival = festival(p, c);
        return p;
    }

    static String festival(Panchang p, Calendar c) {
        int m = p.monthIdx, d = p.day();
        boolean s = p.shukla();
        int gm = c.get(Calendar.MONTH), gd = c.get(Calendar.DAY_OF_MONTH);
        if (gm == Calendar.DECEMBER && gd == 25) return "🎄 Christmas";
        if (gm == Calendar.DECEMBER && gd == 31) return "🎆 New Year's Eve";
        if (gm == Calendar.JANUARY && gd == 1) return "🎆 New Year";
        if (gm == Calendar.JANUARY && gd == 26) return "🇮🇳 Republic Day";
        if (gm == Calendar.AUGUST && gd == 15) return "🇮🇳 Independence Day";
        if (gm == Calendar.OCTOBER && gd == 2) return "Gandhi Jayanti";
        if (gm == Calendar.JANUARY && gd == 14) return "☀️ Uttarayan · Makar Sankranti";
        if (gm == Calendar.JANUARY && gd == 15) return "☀️ Vasi Uttarayan";
        if (m == 7 && s && d == 1) return "🎉 Bestu Varas · Gujarati New Year";
        if (m == 7 && s && d == 2) return "Bhai Beej";
        if (m == 7 && s && d == 5) return "Labh Pancham";
        if (m == 7 && s && d == 11) return "Dev Uthi Ekadashi";
        if (m == 7 && s && d == 15) return "✨ Dev Diwali · Kartik Purnima";
        if (m == 6 && !s && d == 13) return "✨ Dhanteras";
        if (m == 6 && !s && d == 14) return "✨ Kali Chaudas";
        if (m == 6 && !s && d == 15) return "✨ Diwali";
        if (m == 6 && s && d <= 9) return "✨ Navratri · day " + d;
        if (m == 6 && s && d == 10) return "Dussehra";
        if (m == 6 && s && d == 15) return "Sharad Purnima";
        if (m == 5 && s && d == 4) return "🐘 Ganesh Chaturthi";
        if (m == 4 && !s && d == 8) return "Janmashtami";
        if (m == 4 && s && d == 15) return "Raksha Bandhan";
        if (m == 3 && s && d == 11) return "Devshayani Ekadashi";
        if (m == 3 && s && d == 15) return "Guru Purnima";
        if (m == 11 && s && d == 15) return "🔥 Holika Dahan";
        if (m == 11 && !s && d == 1) return "🎨 Dhuleti · Holi";
        if (m == 0 && s && d == 1) return "🚩 Gudi Padwa · Chaitra Navratri begins";
        if (m == 0 && s && d == 9) return "Ram Navami";
        if (m == 0 && s && d == 15) return "Hanuman Jayanti";
        if (m == 10 && s && d == 5) return "Vasant Panchami";
        if (m == 10 && !s && d == 14) return "Maha Shivaratri";
        if (d == 11) return "Ekadashi";
        if (p.tithiIdx == 14) return "Purnima";
        if (p.tithiIdx == 29) return "Amavasya";
        return null;
    }

    // ------------------------------------------------------------------ astronomy
    static double sun(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m) + (0.019993 - 0.000101 * t) * Math.sin(2 * m)
                + 0.000289 * Math.sin(3 * m);
        return norm(l0 + c);
    }

    static double moon(double jd) {
        double t = (jd - 2451545.0) / 36525;
        double lp = 218.3164477 + 481267.88123421 * t;
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t);
        double m = Math.toRadians(357.5291092 + 35999.0502909 * t);
        double mp = Math.toRadians(134.9633964 + 477198.8675055 * t);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t);
        double e = 1 - 0.002516 * t;
        double s = 6288774 * Math.sin(mp) + 1274027 * Math.sin(2 * d - mp) + 658314 * Math.sin(2 * d)
                + 213618 * Math.sin(2 * mp) - 185116 * e * Math.sin(m) - 114332 * Math.sin(2 * f)
                + 58793 * Math.sin(2 * d - 2 * mp) + 57066 * e * Math.sin(2 * d - m - mp) + 53322 * Math.sin(2 * d + mp)
                + 45758 * e * Math.sin(2 * d - m) - 40923 * e * Math.sin(m - mp) - 34720 * Math.sin(d)
                - 30383 * e * Math.sin(m + mp) + 15327 * Math.sin(2 * d - 2 * f) - 12528 * Math.sin(mp + 2 * f)
                + 10980 * Math.sin(mp - 2 * f) + 10675 * Math.sin(4 * d - mp) + 10034 * Math.sin(3 * mp)
                + 8548 * Math.sin(4 * d - 2 * mp) - 7888 * e * Math.sin(2 * d + m - mp) - 6766 * e * Math.sin(2 * d + m)
                - 5163 * Math.sin(d - mp) + 4987 * e * Math.sin(d + m) + 4036 * e * Math.sin(2 * d - m + mp);
        return norm(lp + s / 1e6);
    }

    /** Lahiri ayanamsa (approx.): 23.853° at J2000, +50.29"/year. */
    static double ayanamsa(double jd) { return 23.853 + (jd - 2451545.0) / 365.25 * 0.0139694; }

    static double norm(double a) { a %= 360; return a < 0 ? a + 360 : a; }

    static String guDigits(int n) {
        String d = "૦૧૨૩૪૫૬૭૮૯", s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(d.charAt(ch - '0'));
        return b.toString();
    }
}

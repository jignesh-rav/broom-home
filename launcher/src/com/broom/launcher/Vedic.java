package com.broom.launcher;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Full day Panchang for a place: sunrise/sunset (NOAA), vaar, tithi/nakshatra with end times, yoga, karana,
 * Sun/Moon rashi, ritu, Vikram & Shaka Samvat, Rahu Kaal, Yamaganda, Gulika, Abhijit, day/night Choghadiya,
 * and upcoming festivals. Pure maths, offline; place = weather location (default: central India).
 */
final class Vedic {
    static final String[] VAAR = {"Ravivar", "Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar"};
    static final String[] VAAR_GU = {"રવિવાર", "સોમવાર", "મંગળવાર", "બુધવાર", "ગુરુવાર", "શુક્રવાર", "શનિવાર"};
    static final String[] YOGA = {"Vishkambha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda", "Sukarma", "Dhriti",
            "Shula", "Ganda", "Vriddhi", "Dhruva", "Vyaghata", "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyana", "Parigha",
            "Shiva", "Siddha", "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti"};
    static final String[] KARANA = {"Bava", "Balava", "Kaulava", "Taitila", "Garaja", "Vanija", "Vishti"};
    static final String[] RASHI = {"Mesha", "Vrishabha", "Mithuna", "Karka", "Simha", "Kanya", "Tula", "Vrishchika", "Dhanu",
            "Makara", "Kumbha", "Meena"};
    static final String[] RASHI_GU = {"મેષ", "વૃષભ", "મિથુન", "કર્ક", "સિંહ", "કન્યા", "તુલા", "વૃશ્ચિક", "ધન", "મકર", "કુંભ", "મીન"};
    static final String[] RITU = {"Vasant", "Grishma", "Varsha", "Sharad", "Hemant", "Shishir"};
    // Choghadiya: cycle order and the first slot for each weekday (Sun..Sat), for day and night
    static final String[] CHOG = {"Udveg", "Chal", "Labh", "Amrit", "Kaal", "Shubh", "Rog"};
    static final int[] DAY_START = {0, 3, 6, 2, 5, 1, 4};      // Sun Udveg, Mon Amrit, Tue Rog, Wed Labh, Thu Shubh, Fri Chal, Sat Kaal
    static final String[] NIGHT_CYCLE = {"Shubh", "Amrit", "Chal", "Rog", "Kaal", "Labh", "Udveg"};
    static final int[] NIGHT_START = {0, 2, 4, 6, 1, 3, 5};    // Sun Shubh, Mon Chal, Tue Kaal, Wed Udveg, Thu Amrit, Fri Rog, Sat Labh
    // eighth-of-day segment (1-based) by weekday Sun..Sat
    static final int[] RAHU = {8, 2, 7, 5, 6, 4, 3}, YAMA = {5, 4, 3, 2, 1, 7, 6}, GULIKA = {7, 6, 5, 4, 3, 2, 1};

    static final class Slot { String name; long from, to; int quality; }   // quality: 1 good, 0 neutral, -1 avoid

    static final class Day {
        Panchang p;
        int weekday;                 // 0 = Sunday
        long sunrise, sunset, nextSunrise, tithiEnds, nakshatraEnds;
        int yoga, sunRashi, moonRashi, shaka;
        String karana, ritu;
        Slot rahu, yama, gulika, abhijit;
        final List<Slot> dayChog = new ArrayList<>(), nightChog = new ArrayList<>();
    }

    static double[] place(Context c) {
        android.content.SharedPreferences p = Theme.prefs(c);
        if (p.contains("wx_lat"))
            return new double[]{Double.longBitsToDouble(p.getLong("wx_lat", 0)), Double.longBitsToDouble(p.getLong("wx_lon", 0))};
        return new double[]{21.15, 79.09};   // Nagpur, near the centre of India, until an area is set
    }

    static Day today(Context c) { return of(c, System.currentTimeMillis()); }

    static Day of(Context c, long ms) {
        double[] pl = place(c);
        Day d = new Day();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        d.weekday = cal.get(Calendar.DAY_OF_WEEK) - 1;
        d.sunrise = sunEvent(cal, pl[0], pl[1], true);
        d.sunset = sunEvent(cal, pl[0], pl[1], false);
        Calendar next = (Calendar) cal.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        d.nextSunrise = sunEvent(next, pl[0], pl[1], true);
        // before sunrise, the Hindu day is still yesterday's: use yesterday's sunrise/sunset for segments
        if (ms < d.sunrise) {
            Calendar prev = (Calendar) cal.clone();
            prev.add(Calendar.DAY_OF_MONTH, -1);
            d.nextSunrise = d.sunrise;
            d.sunrise = sunEvent(prev, pl[0], pl[1], true);
            d.sunset = sunEvent(prev, pl[0], pl[1], false);
            d.weekday = (d.weekday + 6) % 7;
        }
        d.p = Panchang.at(ms);
        double jd = ms / 86400000.0 + 2440587.5, ay = Panchang.ayanamsa(jd);
        double sidSun = Panchang.norm(Panchang.sun(jd) - ay), sidMoon = Panchang.norm(Panchang.moon(jd) - ay);
        d.yoga = (int) (Panchang.norm(sidSun + sidMoon) / (360.0 / 27));
        d.sunRashi = (int) (sidSun / 30);
        d.moonRashi = (int) (sidMoon / 30);
        int k = (int) (Panchang.norm(Panchang.moon(jd) - Panchang.sun(jd)) / 6.0);
        d.karana = k == 0 ? "Kimstughna" : k >= 57 ? new String[]{"Shakuni", "Chatushpada", "Naga"}[k - 57] : KARANA[(k - 1) % 7];
        d.ritu = RITU[(d.p.monthIdx / 2) % 6];
        int gy = cal.get(Calendar.YEAR), gm = cal.get(Calendar.MONTH);
        d.shaka = gy - ((gm >= Calendar.APRIL || (gm == Calendar.MARCH && d.p.monthIdx <= 1)) ? 78 : 79);
        d.tithiEnds = nextBoundary(ms, true);
        d.nakshatraEnds = nextBoundary(ms, false);

        long dayLen = d.sunset - d.sunrise, nightLen = d.nextSunrise - d.sunset;
        d.rahu = seg("Rahu Kaal", d.sunrise, dayLen, RAHU[d.weekday], -1);
        d.yama = seg("Yamaganda", d.sunrise, dayLen, YAMA[d.weekday], -1);
        d.gulika = seg("Gulika", d.sunrise, dayLen, GULIKA[d.weekday], -1);
        long mid = d.sunrise + dayLen / 2, ab = dayLen / 15;
        d.abhijit = new Slot();
        d.abhijit.name = "Abhijit Muhurat"; d.abhijit.from = mid - ab / 2; d.abhijit.to = mid + ab / 2; d.abhijit.quality = 1;
        for (int i = 0; i < 8; i++) {
            Slot s = new Slot();
            s.name = CHOG[(DAY_START[d.weekday] + i) % 7];
            s.from = d.sunrise + dayLen * i / 8; s.to = d.sunrise + dayLen * (i + 1) / 8; s.quality = quality(s.name);
            d.dayChog.add(s);
            Slot n = new Slot();
            n.name = NIGHT_CYCLE[(NIGHT_START[d.weekday] + i) % 7];
            n.from = d.sunset + nightLen * i / 8; n.to = d.sunset + nightLen * (i + 1) / 8; n.quality = quality(n.name);
            d.nightChog.add(n);
        }
        return d;
    }

    static int quality(String chog) {
        if (chog.equals("Amrit") || chog.equals("Shubh") || chog.equals("Labh")) return 1;
        if (chog.equals("Chal")) return 0;
        return -1;
    }

    /** The choghadiya slot covering 'ms', or null. */
    static Slot current(Day d, long ms) {
        for (Slot s : d.dayChog) if (ms >= s.from && ms < s.to) return s;
        for (Slot s : d.nightChog) if (ms >= s.from && ms < s.to) return s;
        return null;
    }

    private static Slot seg(String name, long sunrise, long dayLen, int eighth, int q) {
        Slot s = new Slot();
        s.name = name; s.from = sunrise + dayLen * (eighth - 1) / 8; s.to = sunrise + dayLen * eighth / 8; s.quality = q;
        return s;
    }

    /** Time when the current tithi (or nakshatra) ends: Newton steps on the angle. */
    private static long nextBoundary(long ms, boolean tithi) {
        double span = tithi ? 12.0 : 360.0 / 27;
        double jd = ms / 86400000.0 + 2440587.5;
        double a0 = angle(jd, tithi), target = (Math.floor(a0 / span) + 1) * span;
        double t = jd;
        for (int i = 0; i < 6; i++) {
            double a = angle(t, tithi);
            double diff = target - a;
            if (diff < -180) diff += 360;
            if (diff > 180) diff -= 360;
            double rate = (Panchang.norm(angle(t + 0.01, tithi) - a)) / 0.01;   // deg/day
            if (rate <= 0) rate = tithi ? 12.19 : 13.18;
            t += diff / rate;
        }
        return (long) ((t - 2440587.5) * 86400000.0);
    }

    private static double angle(double jd, boolean tithi) {
        if (tithi) return Panchang.norm(Panchang.moon(jd) - Panchang.sun(jd));
        return Panchang.norm(Panchang.moon(jd) - Panchang.ayanamsa(jd));
    }

    /** NOAA sunrise/sunset for the calendar day of 'c' (local timezone), in epoch ms. */
    static long sunEvent(Calendar c, double lat, double lon, boolean rise) {
        int n = c.get(Calendar.DAY_OF_YEAR);
        double lngHour = lon / 15.0;
        double t = n + ((rise ? 6 : 18) - lngHour) / 24.0;
        double M = 0.9856 * t - 3.289;
        double L = M + 1.916 * Math.sin(Math.toRadians(M)) + 0.020 * Math.sin(Math.toRadians(2 * M)) + 282.634;
        L = Panchang.norm(L);
        double RA = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(L))));
        RA = Panchang.norm(RA);
        RA += (Math.floor(L / 90) * 90 - Math.floor(RA / 90) * 90);
        RA /= 15;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(L)), cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * Math.sin(Math.toRadians(lat))) / (cosDec * Math.cos(Math.toRadians(lat)));
        cosH = Math.max(-1, Math.min(1, cosH));
        double H = rise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        H /= 15;
        double T = H + RA - 0.06571 * t - 6.622;
        double UT = ((T - lngHour) % 24 + 24) % 24;
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        long ms = u.getTimeInMillis() + (long) (UT * 3600000);
        // keep the event on the same local date
        long tz = c.getTimeZone().getOffset(ms);
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(ms);
        if (local.get(Calendar.DAY_OF_YEAR) != n) ms += (local.get(Calendar.DAY_OF_YEAR) < n || (n == 1 && local.get(Calendar.DAY_OF_YEAR) > 300)) ? 86400000L : -86400000L;
        return ms;
    }

    static final class Fest { long day; String name; String guDate; }

    /** Festivals (by sunrise tithi) over the next 'days' days; also the next Ekadashi / Purnima / Amavasya. */
    static List<Fest> upcoming(Context c, int days) {
        List<Fest> out = new ArrayList<>();
        double[] pl = place(c);
        Calendar cal = Calendar.getInstance();
        String last = "";
        boolean eka = false, pur = false, ama = false;
        for (int i = 0; i < days; i++) {
            long sr = sunEvent(cal, pl[0], pl[1], true) + 30 * 60000L;   // udaya tithi, just after sunrise
            Panchang p = Panchang.at(sr);
            String f = p.festival;
            if (f != null) {
                boolean minor = f.equals("Ekadashi") || f.equals("Purnima") || f.equals("Amavasya");
                boolean keep = !minor || (f.equals("Ekadashi") && !eka) || (f.equals("Purnima") && !pur) || (f.equals("Amavasya") && !ama);
                if (f.equals("Ekadashi")) eka = true;
                if (f.equals("Purnima")) pur = true;
                if (f.equals("Amavasya")) ama = true;
                if (keep && !f.equals(last)) {
                    Fest x = new Fest();
                    x.day = sr; x.name = f; x.guDate = p.gujarati();
                    out.add(x);
                }
            }
            last = f == null ? "" : f;
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    static String hm(long ms) { return String.valueOf(android.text.format.DateFormat.format("h:mm a", ms)); }
}

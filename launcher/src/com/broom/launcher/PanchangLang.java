package com.broom.launcher;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.Calendar;
import java.util.Locale;

/**
 * Panchang names and labels in the chosen Panchang language (Lang.panchang): English, Hindi, Gujarati, Marathi.
 * English keeps the existing wording exactly. Hindi uses शुक्ल/कृष्ण and purnimanta month names (North Indian
 * convention); Gujarati uses સુદ/વદ with amanta months; Marathi uses शुद्ध/वद्य with amanta months.
 * Numbers use native digits (Lang.digits). Tables are indexed en, hi, gu, mr (Lang.index).
 */
final class PanchangLang {
    private PanchangLang() { }

    private static final String[][] VAAR = {Vedic.VAAR,
            {"रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"},
            Vedic.VAAR_GU,
            {"रविवार", "सोमवार", "मंगळवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"}};

    /** Tithi 1..14, then Purnima, Amavasya. */
    private static final String[][] TITHI = {
            {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
                    "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima", "Amavasya"},
            {"प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
                    "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा", "अमावस्या"},
            {"પડવો", "બીજ", "ત્રીજ", "ચોથ", "પાંચમ", "છઠ", "સાતમ", "આઠમ", "નોમ", "દશમ",
                    "અગિયારસ", "બારસ", "તેરસ", "ચૌદસ", "પૂનમ", "અમાસ"},
            {"प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
                    "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पौर्णिमा", "अमावस्या"}};

    /** {shukla, krishna} as used before a tithi name. */
    private static final String[][] PAKSHA = {{"Shukla", "Krishna"}, {"शुक्ल", "कृष्ण"}, {"સુદ", "વદ"}, {"शुद्ध", "वद्य"}};

    /** Paksha row on the Panchang screen. */
    private static final String[][] PAKSHA_LONG = {{"Shukla (Sud)", "Krishna (Vad)"}, {"शुक्ल पक्ष", "कृष्ण पक्ष"},
            {"શુક્લ પક્ષ (સુદ)", "કૃષ્ણ પક્ષ (વદ)"}, {"शुक्ल पक्ष (शुद्ध)", "कृष्ण पक्ष (वद्य)"}};

    private static final String[][] MONTH = {Panchang.MONTH,
            {"चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद", "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"},
            Panchang.MONTH_GU,
            {"चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ", "श्रावण", "भाद्रपद", "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"}};

    private static final String[][] NAKSHATRA = {Panchang.NAKSHATRA,
            {"अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशिरा", "आर्द्रा", "पुनर्वसु", "पुष्य", "आश्लेषा", "मघा",
                    "पूर्वा फाल्गुनी", "उत्तरा फाल्गुनी", "हस्त", "चित्रा", "स्वाति", "विशाखा", "अनुराधा", "ज्येष्ठा", "मूल",
                    "पूर्वाषाढ़ा", "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा", "शतभिषा", "पूर्वा भाद्रपद", "उत्तरा भाद्रपद", "रेवती"},
            {"અશ્વિની", "ભરણી", "કૃત્તિકા", "રોહિણી", "મૃગશીર્ષ", "આર્દ્રા", "પુનર્વસુ", "પુષ્ય", "આશ્લેષા", "મઘા",
                    "પૂર્વા ફાલ્ગુની", "ઉત્તરા ફાલ્ગુની", "હસ્ત", "ચિત્રા", "સ્વાતિ", "વિશાખા", "અનુરાધા", "જ્યેષ્ઠા", "મૂળ",
                    "પૂર્વાષાઢા", "ઉત્તરાષાઢા", "શ્રવણ", "ધનિષ્ઠા", "શતભિષા", "પૂર્વા ભાદ્રપદ", "ઉત્તરા ભાદ્રપદ", "રેવતી"},
            {"अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशीर्ष", "आर्द्रा", "पुनर्वसू", "पुष्य", "आश्लेषा", "मघा",
                    "पूर्वा फाल्गुनी", "उत्तरा फाल्गुनी", "हस्त", "चित्रा", "स्वाती", "विशाखा", "अनुराधा", "ज्येष्ठा", "मूळ",
                    "पूर्वाषाढा", "उत्तराषाढा", "श्रवण", "धनिष्ठा", "शततारका", "पूर्वा भाद्रपदा", "उत्तरा भाद्रपदा", "रेवती"}};

    private static final String[][] YOGA = {Vedic.YOGA,
            {"विष्कम्भ", "प्रीति", "आयुष्मान", "सौभाग्य", "शोभन", "अतिगण्ड", "सुकर्मा", "धृति", "शूल", "गण्ड", "वृद्धि",
                    "ध्रुव", "व्याघात", "हर्षण", "वज्र", "सिद्धि", "व्यतीपात", "वरीयान", "परिघ", "शिव", "सिद्ध", "साध्य",
                    "शुभ", "शुक्ल", "ब्रह्म", "इन्द्र", "वैधृति"},
            {"વિષ્કંભ", "પ્રીતિ", "આયુષ્માન", "સૌભાગ્ય", "શોભન", "અતિગંડ", "સુકર્મા", "ધૃતિ", "શૂલ", "ગંડ", "વૃદ્ધિ",
                    "ધ્રુવ", "વ્યાઘાત", "હર્ષણ", "વજ્ર", "સિદ્ધિ", "વ્યતિપાત", "વરીયાન", "પરિઘ", "શિવ", "સિદ્ધ", "સાધ્ય",
                    "શુભ", "શુક્લ", "બ્રહ્મ", "ઇન્દ્ર", "વૈધૃતિ"},
            {"विष्कंभ", "प्रीती", "आयुष्मान", "सौभाग्य", "शोभन", "अतिगंड", "सुकर्मा", "धृती", "शूल", "गंड", "वृद्धी",
                    "ध्रुव", "व्याघात", "हर्षण", "वज्र", "सिद्धी", "व्यतीपात", "वरीयान", "परिघ", "शिव", "सिद्ध", "साध्य",
                    "शुभ", "शुक्ल", "ब्रह्म", "इंद्र", "वैधृती"}};

    /** Vedic.KARANA order, then Kimstughna, Shakuni, Chatushpada, Naga. */
    private static final String[][] KARANA = {
            {"Bava", "Balava", "Kaulava", "Taitila", "Garaja", "Vanija", "Vishti", "Kimstughna", "Shakuni", "Chatushpada", "Naga"},
            {"बव", "बालव", "कौलव", "तैतिल", "गर", "वणिज", "विष्टि (भद्रा)", "किंस्तुघ्न", "शकुनि", "चतुष्पद", "नाग"},
            {"બવ", "બાલવ", "કૌલવ", "તૈતિલ", "ગર", "વણિજ", "વિષ્ટિ (ભદ્રા)", "કિંસ્તુઘ્ન", "શકુનિ", "ચતુષ્પાદ", "નાગ"},
            {"बव", "बालव", "कौलव", "तैतिल", "गर", "वणिज", "विष्टी (भद्रा)", "किंस्तुघ्न", "शकुनी", "चतुष्पाद", "नाग"}};

    private static final String[][] RASHI = {Vedic.RASHI,
            {"मेष", "वृषभ", "मिथुन", "कर्क", "सिंह", "कन्या", "तुला", "वृश्चिक", "धनु", "मकर", "कुंभ", "मीन"},
            Vedic.RASHI_GU,
            {"मेष", "वृषभ", "मिथुन", "कर्क", "सिंह", "कन्या", "तूळ", "वृश्चिक", "धनु", "मकर", "कुंभ", "मीन"}};

    private static final String[][] RITU = {Vedic.RITU,
            {"वसंत", "ग्रीष्म", "वर्षा", "शरद", "हेमंत", "शिशिर"},
            {"વસંત", "ગ્રીષ્મ", "વર્ષા", "શરદ", "હેમંત", "શિશિર"},
            {"वसंत", "ग्रीष्म", "वर्षा", "शरद", "हेमंत", "शिशिर"}};

    /** Choghadiya, in Vedic.CHOG order (Udveg, Chal, Labh, Amrit, Kaal, Shubh, Rog). */
    private static final String[][] CHOG = {Vedic.CHOG,
            {"उद्वेग", "चल", "लाभ", "अमृत", "काल", "शुभ", "रोग"},
            {"ઉદ્વેગ", "ચલ", "લાભ", "અમૃત", "કાળ", "શુભ", "રોગ"},
            {"उद्वेग", "चल", "लाभ", "अमृत", "काळ", "शुभ", "रोग"}};

    /** Muhurat slots as named in Vedic.of. */
    private static final String[][] SLOT = {
            {"Rahu Kaal", "Yamaganda", "Gulika", "Abhijit Muhurat"},
            {"राहुकाल", "यमगण्ड", "गुलिक काल", "अभिजित मुहूर्त"},
            {"રાહુકાળ", "યમગંડ", "ગુલિક કાળ", "અભિજિત મુહૂર્ત"},
            {"राहुकाळ", "यमगंड", "गुलिक काळ", "अभिजित मुहूर्त"}};

    /** Day parts used before a 12-hour time: morning (4-11), afternoon (12-15), evening (16-19), night. */
    private static final String[][] DAYPART = {{"", "", "", ""}, {"सुबह", "दोपहर", "शाम", "रात"},
            {"સવારે", "બપોરે", "સાંજે", "રાત્રે"}, {"सकाळी", "दुपारी", "सायंकाळी", "रात्री"}};

    private static int li(String code) { return Lang.index(code); }

    private static boolean en(String code) { return li(code) == 0; }

    /** One of four strings by language (en, hi, gu, mr). */
    static String pick(String code, String en, String hi, String gu, String mr) {
        switch (li(code)) {
            case 1: return hi;
            case 2: return gu;
            case 3: return mr;
            default: return en;
        }
    }

    private static String byName(String[][] t, String code, String english) {
        for (int i = 0; i < t[0].length; i++) if (t[0][i].equals(english)) return t[li(code)][i];
        return english;
    }

    static String num(String code, Object n) { return Lang.digits(String.valueOf(n), code); }

    static String vaar(String code, int weekday) { return VAAR[li(code)][weekday]; }

    static String nakshatra(String code, int i) { return NAKSHATRA[li(code)][i]; }

    static String yoga(String code, int i) { return YOGA[li(code)][i]; }

    static String karana(String code, String english) { return byName(KARANA, code, english); }

    static String rashi(String code, int i) { return RASHI[li(code)][i]; }

    static String ritu(String code, String english) { return byName(RITU, code, english); }

    /** Choghadiya name (Amrit, Shubh, Labh, Chal, Udveg, Kaal, Rog). */
    static String chog(String code, String english) { return byName(CHOG, code, english); }

    /** Rahu Kaal, Yamaganda, Gulika, Abhijit Muhurat. */
    static String slot(String code, String english) { return byName(SLOT, code, english); }

    static String paksha(String code, Panchang p) { return PAKSHA_LONG[li(code)][p.shukla() ? 0 : 1]; }

    /** Hindi follows the purnimanta month (krishna paksha belongs to the next month); the others are amanta. */
    static int monthIdx(String code, Panchang p) {
        return Lang.HI.equals(code) && !p.shukla() ? (p.monthIdx + 1) % 12 : p.monthIdx;
    }

    static String month(String code, Panchang p) { return MONTH[li(code)][monthIdx(code, p)]; }

    /** Month row of the Panchang screen: "कार्तिक (अमांत)"; Hindi adds the amanta month when it differs. */
    static String monthRow(String code, Panchang p) {
        String s = month(code, p) + pick(code, "", "  (पूर्णिमांत)", "  (અમાંત)", "  (अमांत)");
        if (monthIdx(code, p) != p.monthIdx) s += "  ·  अमांत: " + MONTH[li(code)][p.monthIdx];
        return s;
    }

    /** Tithi with paksha: "Shukla Saptami" · "शुक्ल सप्तमी" · "સુદ સાતમ" · "शुद्ध सप्तमी"; Purnima / Amavasya alone. */
    static String tithi(String code, Panchang p) {
        int l = li(code);
        if (p.tithiIdx == 14) return TITHI[l][14];
        if (p.tithiIdx == 29) return TITHI[l][15];
        return PAKSHA[l][p.shukla() ? 0 : 1] + " " + TITHI[l][p.tithiIdx % 15];
    }

    /**
     * The lunar date: English keeps the Gujarati "કારતક સુદ ૭"; Gujarati the same; Hindi "कार्तिक शुक्ल सप्तमी";
     * Marathi "कार्तिक शुद्ध सप्तमी".
     */
    static String date(String code, Panchang p) {
        if (en(code) || Lang.GU.equals(code)) return p.gujarati();
        return month(code, p) + " " + tithi(code, p);
    }

    /** Clock time: "6:52 AM" in English, "सुबह ६:५२" / "સવારે ૬:૫૨" / "सकाळी ६:५२" otherwise. */
    static String hm(String code, long ms) {
        if (en(code)) return Vedic.hm(ms);
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(ms);
        int h = k.get(Calendar.HOUR_OF_DAY);
        int part = h >= 4 && h < 12 ? 0 : h >= 12 && h < 16 ? 1 : h >= 16 && h < 20 ? 2 : 3;
        return DAYPART[li(code)][part] + " " + Lang.digits(String.valueOf(android.text.format.DateFormat.format("h:mm", ms)), code);
    }

    /** "till 7:45 PM" / "शाम ७:४५ तक" / "સાંજે ૭:૪૫ સુધી" / "सायंकाळी ७:४५ पर्यंत". */
    static String till(String code, String time) {
        return pick(code, "till " + time, time + " तक", time + " સુધી", time + " पर्यंत");
    }

    /** Calendar date in the language's own month / weekday names and digits. English keeps the device format. */
    static String greg(String code, String pattern, long ms) {
        if (en(code)) return String.valueOf(android.text.format.DateFormat.format(pattern, ms));
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(pattern, new Locale(code, "IN"));
            return Lang.digits(f.format(new java.util.Date(ms)), code);
        } catch (Exception e) {
            return Lang.digits(String.valueOf(android.text.format.DateFormat.format(pattern, ms)), code);
        }
    }

    /** "Vikram 2083 · Shaka 1948" in the chosen language. */
    static String samvat(String code, Panchang p, int shaka) {
        return pick(code, "Vikram " + p.samvat + "  ·  Shaka " + shaka,
                "विक्रम संवत " + num(code, p.samvat) + "  ·  शक संवत " + num(code, shaka),
                "વિક્રમ સંવત " + num(code, p.samvat) + "  ·  શક સંવત " + num(code, shaka),
                "विक्रम संवत " + num(code, p.samvat) + "  ·  शके " + num(code, shaka));
    }

    /** Home-screen Panchang line in the chosen language (English is unchanged from before). */
    static CharSequence homeLine(Context c) {
        String code = Lang.panchang(c);
        Panchang p = Panchang.now();
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append(date(code, p));
        b.setSpan(new ForegroundColorSpan(0xFFFFD166), 0, b.length(), 0);
        if (en(code)) b.append("  ·  ").append(tithi(code, p));
        else b.append("  ·  ").append(nakshatra(code, p.nakshatra)).append(pick(code, "", " नक्षत्र", " નક્ષત્ર", " नक्षत्र"));
        if (p.festival != null) {
            b.append("  ·  ");
            int st = b.length();
            b.append(p.festival);
            b.setSpan(new ForegroundColorSpan(0xFFFFB0C8), st, b.length(), 0);
        }
        // current Choghadiya (and Rahu Kaal when it is on)
        long now = System.currentTimeMillis();
        Vedic.Day vd = Vedic.today(c);
        Vedic.Slot cs = Vedic.current(vd, now);
        if (cs != null) {
            b.append("  ·  ");
            int st = b.length();
            b.append(chog(code, cs.name)).append(" ").append(till(code, hm(code, cs.to)));
            b.setSpan(new ForegroundColorSpan(cs.quality > 0 ? 0xFF4CD964 : cs.quality == 0 ? 0xFFFFD166 : 0xFFFF6B6B), st, b.length(), 0);
        }
        if (now >= vd.rahu.from && now < vd.rahu.to) {
            b.append("  ·  ");
            int st = b.length();
            b.append(slot(code, "Rahu Kaal")).append(" ").append(till(code, hm(code, vd.rahu.to)));
            b.setSpan(new ForegroundColorSpan(0xFFFF6B6B), st, b.length(), 0);
        }
        return b;
    }
}

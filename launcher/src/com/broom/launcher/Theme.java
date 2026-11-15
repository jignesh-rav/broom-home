package com.broom.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * A theme = scene (wallpaper) + tinted glass panels + typography + focus colour.
 * All themes use white text on tinted frosted glass. Packs: Time of day, India, Traditional,
 * Icy, European, Western, Movie moods. "Auto" follows the time of day.
 */
public final class Theme {
    public final String id, name, pack;
    public final int top, bottom, glowA, glowB, accent;
    public final int text, dim;
    public final int card;                   // mid glass colour (bars, simple fills)
    public final int glassTop, glassBottom;  // translucent tinted glass gradient for panels
    public final int focus, onFocus;
    public final int radiusDp;
    public final boolean light;
    public final String clockFont, titleFont, bodyFont;
    public final String greet, greetLocal;   // legacy; greeting now follows the clock (see greeting())
    public final int wallpaper;

    Theme(String id, String name, String pack, int top, int bottom, int glowA, int glowB, int accent,
          int text, int dim, int glassTop, int glassBottom, int focus, int onFocus, int radiusDp, boolean light,
          String clockFont, String titleFont, String bodyFont, String greet, String greetLocal, int wallpaper) {
        this.id = id; this.name = name; this.pack = pack;
        this.top = top; this.bottom = bottom; this.glowA = glowA; this.glowB = glowB; this.accent = accent;
        this.text = text; this.dim = dim; this.glassTop = glassTop; this.glassBottom = glassBottom;
        this.card = glassBottom; this.focus = focus; this.onFocus = onFocus;
        this.radiusDp = radiusDp; this.light = light;
        this.clockFont = clockFont; this.titleFont = titleFont; this.bodyFont = bodyFont;
        this.greet = greet; this.greetLocal = greetLocal; this.wallpaper = wallpaper;
    }

    /** Compact constructor for pack themes (white text, dark scenes). */
    private static Theme pack(String id, String name, String pack, int top, int bottom, int glowA, int glowB, int accent,
                              int glassTop, int glassBottom, int focus, int onFocus, int radius, String clockFont) {
        return new Theme(id, name, pack, top, bottom, glowA, glowB, accent, 0xFFFFFFFF, 0xE6EEEEEE,
                glassTop, glassBottom, focus, onFocus, radius, false, clockFont, "Poppins-SemiBold", "Poppins-Regular",
                "", "", ThemeBackground.STYLE_SUNSET);
    }

    public static final String PACK_TIME = "Time of day", PACK_INDIA = "India", PACK_TRAD = "Traditional",
            PACK_ICY = "Icy", PACK_EU = "European", PACK_WEST = "Western", PACK_MOVIE = "Movie moods";

    public static final Theme DAWN = new Theme("dawn", "Dawn", PACK_TIME,
            0xFF8FA3D8, 0xFFFFD8B0, 0xFFFFB88C, 0xFF8E6CFF, 0xFFD9466F,
            0xFFFFFFFF, 0xE6F7E4EE, 0x8C4A2C66, 0xB02A173F, 0xFFFFC36B, 0xFF2A1B3D, 22, false,
            "DMSerifDisplay-Regular", "Poppins-SemiBold", "Poppins-Regular", "", "", ThemeBackground.STYLE_SUNRISE);
    public static final Theme DAY = new Theme("day", "Day", PACK_TIME,
            0xFF3E8EDB, 0xFFDDF1FF, 0xFFFFF3C4, 0xFFA8EEFF, 0xFF0A6CD6,
            0xFFFFFFFF, 0xE6E3F2FF, 0x7A1A5A9C, 0xA60A2F5E, 0xFFFFB020, 0xFF0A2540, 20, false,
            "Poppins-Light", "Poppins-SemiBold", "Poppins-Regular", "", "", ThemeBackground.STYLE_SKY);
    public static final Theme DUSK = new Theme("dusk", "Dusk", PACK_TIME,
            0xFF1B1035, 0xFFFFB36B, 0xFFFF7E5F, 0xFF6A3093, 0xFFFF9E6B,
            0xFFFFFFFF, 0xE6FFE0D6, 0x8C4A1838, 0xB0220A20, 0xFFFFFFFF, 0xFF2A0E26, 18, false,
            "DMSerifDisplay-Regular", "Poppins-Medium", "Poppins-Regular", "", "", ThemeBackground.STYLE_SUNSET);
    public static final Theme NIGHT = new Theme("night", "Night", PACK_TIME,
            0xFF04060F, 0xFF101C3A, 0xFF2C5AA0, 0xFF5B3F8C, 0xFF7FD1FF,
            0xFFF4F7FF, 0xE6C9D6F2, 0x80233866, 0xA60A1330, 0xFFFFD54F, 0xFF0F1730, 20, false,
            "Poppins-Thin", "Poppins-Medium", "Poppins-Regular", "", "", ThemeBackground.STYLE_STARS);

    // ---- India
    public static final Theme KERALA = pack("kerala", "Kerala Backwaters", PACK_INDIA, 0xFF0B3B3A, 0xFFF6D58E, 0xFFFFD27A, 0xFF2EC4B6, 0xFF7EE0C3,
            0x80114A44, 0xA6062A26, 0xFFFFD166, 0xFF06302C, 22, "YatraOne-Regular");
    public static final Theme RAJASTHAN = pack("rajasthan", "Desert Fort", PACK_INDIA, 0xFF3A1640, 0xFFFFB347, 0xFFFF8C42, 0xFF8E3B6E, 0xFFFFB347,
            0x8C5A2A10, 0xB0331505, 0xFFFFE08A, 0xFF3A1A06, 20, "YatraOne-Regular");
    public static final Theme HIMALAYA = pack("himalaya", "Himalayan Peaks", PACK_INDIA, 0xFF0A1A3A, 0xFF9FC4E8, 0xFFBFE3FF, 0xFF5B7FBF, 0xFF9FD8FF,
            0x80203A5C, 0xA60E1E36, 0xFFFFFFFF, 0xFF0E1E36, 20, "Poppins-Light");
    // ---- Traditional
    public static final Theme TEMPLE = pack("temple", "Temple Lamps", PACK_TRAD, 0xFF120A2A, 0xFF5A2A1A, 0xFFFFB347, 0xFF7A3A1A, 0xFFFFA62B,
            0x8C4A1A0A, 0xB02A0C04, 0xFFFFC14D, 0xFF2A0C04, 16, "Cinzel");
    public static final Theme WARLI = pack("warli", "Warli Art", PACK_TRAD, 0xFF8A3B1E, 0xFF5E2410, 0xFFFFE0B8, 0xFFB0552E, 0xFFFFE0B8,
            0x8C6B2E1A, 0xB03D170A, 0xFFFFF1DC, 0xFF5A2410, 18, "YatraOne-Regular");
    // ---- Icy / European / Western
    public static final Theme ARCTIC = pack("arctic", "Arctic Aurora", PACK_ICY, 0xFF030B1A, 0xFFBFD9EE, 0xFF7CFFCB, 0xFF6A5CFF, 0xFF7CFFCB,
            0x80163A4A, 0xA60A1E2A, 0xFF7CFFCB, 0xFF06202A, 22, "Poppins-Thin");
    public static final Theme ALPINE = pack("alpine", "Alpine Lake", PACK_EU, 0xFF2D5C8A, 0xFFE8D8B8, 0xFFFFE6B0, 0xFF6FA6C8, 0xFFBFE3C0,
            0x80244A3A, 0xA60F2A1E, 0xFFFFD580, 0xFF12301F, 20, "Cinzel");
    public static final Theme CANYON = pack("canyon", "Canyon Sunset", PACK_WEST, 0xFF2A1238, 0xFFFF9A4A, 0xFFFF8C42, 0xFFB5462A, 0xFFFF8C42,
            0x8C5A2314, 0xB0300F08, 0xFFFFC66B, 0xFF300F08, 14, "BebasNeue-Regular");
    // ---- Movie moods (styles, not specific films)
    public static final Theme NOIR = pack("noir", "Film Noir", PACK_MOVIE, 0xFF050505, 0xFF3A3A3A, 0xFFE0E0E0, 0xFF707070, 0xFFE0E0E0,
            0x99242424, 0xBF0A0A0A, 0xFFFFFFFF, 0xFF111111, 12, "BebasNeue-Regular");
    public static final Theme NEON = pack("neon", "Neon City", PACK_MOVIE, 0xFF0B0320, 0xFF3A0A4A, 0xFFFF2BD6, 0xFF00F0FF, 0xFFFF2BD6,
            0x8C2A0A40, 0xB0120420, 0xFF00F0FF, 0xFF120420, 16, "Orbitron");
    public static final Theme BOLLYWOOD = pack("bollywood", "Retro Bollywood", PACK_MOVIE, 0xFF5A0F2A, 0xFFFFB627, 0xFFFFD23F, 0xFFE63946, 0xFFFF6B35,
            0x8C5A1030, 0xB0300818, 0xFFFFD23F, 0xFF300818, 20, "YatraOne-Regular");

    public static final Theme[] TIME = {DAWN, DAY, DUSK, NIGHT};
    public static final Theme[] ALL = {DAWN, DAY, DUSK, NIGHT, KERALA, RAJASTHAN, HIMALAYA, TEMPLE, WARLI,
            ARCTIC, ALPINE, CANYON, NOIR, NEON, BOLLYWOOD};

    public static Theme forHour(int h) {
        if (h >= 5 && h < 11) return DAWN;
        if (h >= 11 && h < 16) return DAY;
        if (h >= 16 && h < 20) return DUSK;
        return NIGHT;
    }

    /** {English, Hindi, Gujarati} greeting for the actual time of day. */
    public static String[] greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return new String[]{"Good morning", "सुप्रभात", "સુપ્રભાત"};
        if (h >= 12 && h < 17) return new String[]{"Good afternoon", "नमस्ते", "નમસ્તે"};
        if (h >= 17 && h < 21) return new String[]{"Good evening", "शुभ संध्या", "શુભ સંધ્યા"};
        return new String[]{"Good night", "शुभ रात्रि", "શુભ રાત્રિ"};
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("broom", Context.MODE_PRIVATE); }

    public static String choice(Context c) { return prefs(c).getString("theme", "auto"); }

    public static void setChoice(Context c, String v) { prefs(c).edit().putString("theme", v).apply(); }

    public static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return null;
    }

    public static Theme current(Context c) {
        Theme t = byId(choice(c));
        return t != null ? t : forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    public static String label(Context c) {
        return choice(c).equals("auto") ? "Auto · " + current(c).name : current(c).name;
    }

    int textShadow() { return 0xAA000000; }
}

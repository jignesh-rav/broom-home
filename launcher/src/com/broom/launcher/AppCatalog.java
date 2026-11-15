package com.broom.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Launchable apps, grouped into categories, plus the user's ordered favourites. */
final class AppCatalog {
    static final String STREAMING = "Streaming", MUSIC = "Music & Audio", MEDIA = "Media & Files",
            GAMES = "Games", TOOLS = "Tools & System", OTHER = "Other apps";
    static final String[] ORDER = {STREAMING, MUSIC, MEDIA, GAMES, TOOLS, OTHER};

    /** Default favourites, in remote-friendly order (most watched first). Only installed ones are used. */
    static final String[] DEFAULT_FAVS = {
            "com.google.android.youtube.tv", "in.startv.hotstar", "com.sonyliv", "com.netflix.ninja",
            "org.smarttube.stable", "com.amazon.amazonvideo.livingroom", "com.jio.media.stb.ondemand", "com.graymatrix.did",
            "com.mxtech.videoplayer.television", "org.videolan.vlc"};
    static final int FAVS_VERSION = 3;

    private static final Map<String, String> KNOWN = new HashMap<>();
    static {
        for (String p : new String[]{"com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                "in.startv.hotstar", "com.jio.media.stb.ondemand", "com.graymatrix.did", "com.sonyliv",
                "com.google.android.youtube.tvkids", "com.google.android.videos", "com.viacom18.tv.voot",
                "com.yupptv.scope.tvapp", "com.jio.jioplay.tv", "com.mxplayer", "org.smarttube.stable"}) KNOWN.put(p, STREAMING);
        for (String p : new String[]{"com.google.android.youtube.tvmusic", "com.amazon.music.tv", "com.spotify.tv.android",
                "com.xiaomi.mimusic2", "com.jio.media.jiobeats", "com.gaana"}) KNOWN.put(p, MUSIC);
        for (String p : new String[]{"org.videolan.vlc", "com.mxtech.videoplayer.television", "com.xiaomi.mitv.mediaexplorer",
                "com.mitv.videoplayer", "com.mitv.gallery", "com.google.android.apps.photos"}) KNOWN.put(p, MEDIA);
        for (String p : new String[]{"com.google.android.play.games", "com.airconsole.androidtv"}) KNOWN.put(p, GAMES);
        for (String p : new String[]{"com.android.tv.settings", "com.android.vending", "com.android.tv",
                "com.droidlogic.droidlivetv", "com.xiaomi.mitv.tvmanager", "com.accuweather.android",
                "com.google.android.katniss", "com.mitv.tvhome.atv", "com.google.android.tvlauncher"}) KNOWN.put(p, TOOLS);
    }

    static final class App {
        final String pkg, label; final ComponentName comp; final Drawable banner, icon; final String category;
        App(String pkg, String label, ComponentName comp, Drawable banner, Drawable icon, String category) {
            this.pkg = pkg; this.label = label; this.comp = comp; this.banner = banner; this.icon = icon; this.category = category;
        }
    }

    final LinkedHashMap<String, App> apps = new LinkedHashMap<>();

    void load(Context c) {
        apps.clear();
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER"), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo.packageName;
            if (apps.containsKey(pkg) || pkg.equals(c.getPackageName())) continue;
            Drawable banner = null;
            try { banner = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
            if (banner == null) try { banner = ri.activityInfo.applicationInfo.loadBanner(pm); } catch (Exception ignored) { }
            Drawable icon = ri.loadIcon(pm);
            // Keep only small copies: tiles show banners at ~170x96 px and icons at ~40 px
            int bw = Ui.dp(c, 160), bh = Ui.dp(c, 90), is = Ui.dp(c, 36);   // 1.25x for the focus zoom
            banner = shrink(c, banner, bw, bh);
            icon = shrink(c, icon, is, is);
            String label = String.valueOf(ri.loadLabel(pm));
            apps.put(pkg, new App(pkg, label, new ComponentName(pkg, ri.activityInfo.name), banner, icon,
                    categorize(ri.activityInfo.applicationInfo)));
        }
    }

    /** Renders a drawable into a bitmap of the size it is shown at (saves memory vs full-size banners). */
    static Drawable shrink(Context c, Drawable d, int w, int h) {
        if (d == null) return null;
        try {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) return d;
            float s = Math.min(1f, Math.max(w / (float) iw, h / (float) ih));
            int tw = Math.max(1, Math.round(iw * s)), th = Math.max(1, Math.round(ih * s));
            if (tw >= iw && th >= ih && !(d instanceof android.graphics.drawable.BitmapDrawable)) return d;
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(tw, th, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            d.setBounds(0, 0, tw, th);
            d.draw(cv);
            return new android.graphics.drawable.BitmapDrawable(c.getResources(), b);
        } catch (Exception e) { return d; }
    }

    static String categorize(ApplicationInfo ai) {
        String k = KNOWN.get(ai.packageName);
        if (k != null) return k;
        if (Build.VERSION.SDK_INT >= 26) {
            switch (ai.category) {
                case ApplicationInfo.CATEGORY_GAME: return GAMES;
                case ApplicationInfo.CATEGORY_AUDIO: return MUSIC;
                case ApplicationInfo.CATEGORY_VIDEO: return STREAMING;
                case ApplicationInfo.CATEGORY_IMAGE: return MEDIA;
                case ApplicationInfo.CATEGORY_PRODUCTIVITY: return TOOLS;
            }
        }
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return GAMES;
        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return TOOLS;
        return OTHER;
    }

    LinkedHashMap<String, List<App>> byCategory() {
        LinkedHashMap<String, List<App>> m = new LinkedHashMap<>();
        for (String s : ORDER) m.put(s, new ArrayList<App>());
        for (App a : apps.values()) m.get(a.category).add(a);
        for (List<App> l : m.values())
            Collections.sort(l, new Comparator<App>() {
                @Override public int compare(App a, App b) { return a.label.compareToIgnoreCase(b.label); }
            });
        return m;
    }

    // ---- favourites ----
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("broom", Context.MODE_PRIVATE); }

    List<String> favourites(Context c) {
        int v = prefs(c).getInt("favs_v", 1);
        if (v < 2) prefs(c).edit().remove("favs").apply();
        if (v < 3) { // v3: add SmartTube after the user's top four, keeping any custom order
            String cur = prefs(c).getString("favs", null);
            if (cur != null && !cur.contains("org.smarttube.stable")) {
                List<String> l = new ArrayList<>(Arrays.asList(cur.split(",")));
                l.add(Math.min(4, l.size()), "org.smarttube.stable");
                saveFavourites(c, l);
            }
        }
        if (v < FAVS_VERSION) prefs(c).edit().putInt("favs_v", FAVS_VERSION).apply();
        String s = prefs(c).getString("favs", null);
        List<String> out = new ArrayList<>();
        List<String> src = s == null ? Arrays.asList(DEFAULT_FAVS) : Arrays.asList(s.split(","));
        for (String p : src) if (apps.containsKey(p) && !out.contains(p)) out.add(p);
        return out;
    }

    static void saveFavourites(Context c, List<String> favs) {
        StringBuilder b = new StringBuilder();
        for (String p : favs) { if (b.length() > 0) b.append(','); b.append(p); }
        prefs(c).edit().putString("favs", b.toString()).apply();
    }

    Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        if (Build.VERSION.SDK_INT >= 21) i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }
}

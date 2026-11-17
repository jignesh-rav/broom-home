package com.broom.launcher;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Photo screensaver source. Wikimedia Commons featured pictures (free licences, credit shown),
 * or the user's own photos on USB. Lists are cached for a week; images are downloaded one at a time
 * into a cache capped at 50 MB. Everything here runs on background threads.
 */
final class Photos {
    static final long CACHE_LIMIT = 50L * 1024 * 1024;
    static final String[] PACKS = {"monuments", "nature", "animals", "baby", "space", "usb"};

    /** Title searches inside Commons' featured pictures, per pack. */
    private static String[] queries(String pack) {
        switch (pack) {
            case "monuments": return new String[]{"fort", "palace", "Taj Mahal", "stepwell", "temple India", "castle", "cathedral", "mosque", "monument"};
            case "nature": return new String[]{"waterfall", "Himalaya", "lake", "valley", "beach sunset", "forest", "mountain"};
            case "animals": return new String[]{"tiger", "elephant", "lion", "leopard", "peacock", "kingfisher", "owl", "deer", "giraffe"};
            case "baby": return new String[]{"calf", "juvenile", "kitten", "duckling", "cub", "foal", "lamb", "chick", "baby", "puppy"};
            default: return new String[]{"nebula", "galaxy", "Milky Way", "Moon", "aurora"};
        }
    }

    /** Never show images whose titles contain these (family TV). */
    private static final String[] BLOCK = {"nude", "naked", "erotic", "zoophilia", "sex", "topless", "breast", "mithuna", "khajuraho",
            "bikini", "nudity", "corpse", "carcass", "slaughter",
            // title matches that aren't the animal/place we searched for
            "cuba", "certified proof", "banknote", "coin", "stamp", "butterfly", "jellyfish", "underside",
            "danaus", "tirumala", "phalanta", "anartia", "aglais", "papilio"};
    /** Whole words (optionally plural) that don't belong in a relaxing slideshow. */
    private static final java.util.regex.Pattern BLOCK_WORDS = java.util.regex.Pattern.compile(
            "\\b(dead|blood|hunting|hunt|kill|killed|prey|skull|war|weapon|gun|beetle|spider|insect|moth|wasp|bee|fly|ant|snake|"
            + "cobra|viper|python|shark|worm|tick|mosquito|scorpion|cockroach|leech|caterpillar|larva|bug|grasshopper|mantis|dragonfly|"
            + "crab|lizard|frog|toad|rat|bat|vulture|fish|fungus|cemetery|grave|tomb|prison|ruins)(e?s)?\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    static boolean blocked(String title) {
        String low = title.toLowerCase(Locale.US).replace('_', ' ');
        for (String b : BLOCK) if (low.contains(b)) return true;
        return BLOCK_WORDS.matcher(low).find();
    }

    static final class Item {
        String url, title, credit, file;
        Uri uri;   // USB photos
    }

    static String label(String pack) {
        switch (pack) {
            case "monuments": return "Monuments";
            case "nature": return "Nature";
            case "animals": return "Animals";
            case "baby": return "Baby animals";
            case "space": return "Space";
            default: return "My photos (USB)";
        }
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "photos");
        d.mkdirs();
        return d;
    }

    /** Returns the (possibly cached) list. Network/disk work: call off the main thread. */
    static List<Item> list(Context c, String pack) {
        if (pack.equals("usb")) return usb(c);
        File lf = new File(dir(c), pack + "_v3.json");
        List<Item> out = new ArrayList<>();
        try {
            if (!lf.exists() || System.currentTimeMillis() - lf.lastModified() > 7L * 86400000L) fetchList(pack, lf);
        } catch (Exception ignored) { }
        try {
            JSONArray a = new JSONArray(read(lf));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.url = o.getString("u"); it.title = o.getString("t"); it.credit = o.getString("c");
                if (blocked(it.title)) continue;
                it.file = Integer.toHexString(it.url.hashCode()) + ".jpg";
                out.add(it);
            }
        } catch (Exception ignored) { }
        Collections.shuffle(out);
        return out;
    }

    /**
     * Commons' own featured pictures first. Some networks block commons.wikimedia.org, so if it can't be
     * reached we use English Wikipedia's featured pictures instead (also Commons files, served by upload/thumb.wikimedia.org).
     */
    private static void fetchList(String pack, File out) throws Exception {
        JSONArray res = fetchFrom("commons.wikimedia.org", "Featured_pictures_on_Wikimedia_Commons", pack);
        if (res.length() < 8) {
            JSONArray more = fetchFrom("en.wikipedia.org", "Featured_pictures", pack);
            for (int i = 0; i < more.length(); i++) res.put(more.get(i));
        }
        if (res.length() > 0) try (FileOutputStream f = new FileOutputStream(out)) { f.write(res.toString().getBytes("UTF-8")); }
    }

    private static JSONArray fetchFrom(String host, String category, String pack) throws Exception {
        JSONArray res = new JSONArray();
        List<String> seen = new ArrayList<>();
        int failures = 0;
        for (String q : queries(pack)) {
            String api = "https://" + host + "/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=25"
                    + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1280"
                    + "&iiextmetadatafilter=Artist%7CLicenseShortName%7CObjectName"
                    + "&gsrsearch=" + URLEncoder.encode("incategory:" + category + " intitle:" + q, "UTF-8");
            try {
                String body;
                try { body = get(api); }
                catch (java.io.IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("429")) { Thread.sleep(6000); body = get(api); }  // rate limited: wait, try once more
                    else throw e;
                }
                JSONObject query = new JSONObject(body).optJSONObject("query");
                if (query == null) { Thread.sleep(1500); continue; }   // no results for this word
                JSONObject pages = query.getJSONObject("pages");
                JSONArray names = pages.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    JSONObject pg = pages.getJSONObject(names.getString(i));
                    String title = pg.getString("title");
                    JSONObject ii = pg.getJSONArray("imageinfo").getJSONObject(0);
                    int w = ii.optInt("width"), h = ii.optInt("height");
                    if (w < h * 1.3 || !ii.optString("mime").equals("image/jpeg")) continue;   // landscape JPEGs only
                    if (blocked(title) || seen.contains(title)) continue;
                    seen.add(title);
                    JSONObject md = ii.optJSONObject("extmetadata");
                    String artist = md == null ? "" : strip(md.optJSONObject("Artist") == null ? "" : md.getJSONObject("Artist").optString("value"));
                    String lic = md == null || md.optJSONObject("LicenseShortName") == null ? "" : md.getJSONObject("LicenseShortName").optString("value");
                    String obj = md == null || md.optJSONObject("ObjectName") == null ? "" : strip(md.getJSONObject("ObjectName").optString("value"));
                    JSONObject o = new JSONObject();
                    o.put("u", ii.getString("thumburl"));
                    o.put("t", (obj.isEmpty() ? niceTitle(title) : obj).replaceFirst("^\\d{1,4}\\s+", ""));   // drop "178 " photo-series numbers
                    o.put("c", (artist.isEmpty() ? "Wikimedia Commons" : artist) + (lic.isEmpty() ? "" : " · " + lic) + " · Wikimedia Commons");
                    res.put(o);
                }
            } catch (Exception e) {
                // host unreachable (e.g. blocked): give up on it quickly instead of trying every word
                if (e instanceof java.io.IOException && ++failures >= 2 && res.length() == 0) break;
            }
            Thread.sleep(1500);   // be polite to the API
        }
        return res;
    }

    /** Downloads (if needed) and decodes an item at screen size. Call off the main thread. */
    static Bitmap load(Context c, Item it, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;   // half the memory; photos don't need alpha
            if (it.uri != null) {
                BitmapFactory.Options b = new BitmapFactory.Options();
                b.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(it.uri)) { BitmapFactory.decodeStream(in, null, b); }
                o.inSampleSize = Math.max(1, Math.min(b.outWidth / w, b.outHeight / h));
                try (InputStream in = c.getContentResolver().openInputStream(it.uri)) { return BitmapFactory.decodeStream(in, null, o); }
            }
            File f = new File(dir(c), it.file);
            if (!f.exists()) {
                if (!mayDownload(c)) return null;
                countDownload(c);
                HttpURLConnection con = open(it.url);
                File tmp = new File(dir(c), it.file + ".part");
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally { con.disconnect(); }
                tmp.renameTo(f);
                trim(c);
            }
            f.setLastModified(System.currentTimeMillis());
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) { return null; }
    }

    /** True if the photo is on USB or already downloaded (showing it costs no network). */
    static boolean cached(Context c, Item it) {
        return it.uri != null || (it.file != null && new File(dir(c), it.file).exists());
    }

    /** Polite download budget: at most DL_MAX new photos per 6 hours across all packs; then cached ones repeat. */
    private static final int DL_MAX = 24;
    private static final long DL_WINDOW = 6 * 3600000L;

    static synchronized boolean mayDownload(Context c) {
        android.content.SharedPreferences p = Theme.prefs(c);
        long start = p.getLong("photo_dl_start", 0), now = System.currentTimeMillis();
        if (now - start > DL_WINDOW || now < start) {
            p.edit().putLong("photo_dl_start", now).putInt("photo_dl_n", 0).apply();
            return true;
        }
        return p.getInt("photo_dl_n", 0) < DL_MAX;
    }

    private static synchronized void countDownload(Context c) {
        android.content.SharedPreferences p = Theme.prefs(c);
        p.edit().putInt("photo_dl_n", p.getInt("photo_dl_n", 0) + 1).apply();
    }

    /** Keep the image cache under the limit (oldest first). */
    private static void trim(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) if (f.getName().endsWith(".jpg")) total += f.length();
        if (total <= CACHE_LIMIT) return;
        List<File> imgs = new ArrayList<>();
        for (File f : fs) if (f.getName().endsWith(".jpg")) imgs.add(f);
        Collections.sort(imgs, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (File f : imgs) { if (total <= CACHE_LIMIT * 0.8) break; total -= f.length(); f.delete(); }
    }

    private static List<Item> usb(Context c) {
        List<Item> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT},
                null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC")) {
            while (cur != null && cur.moveToNext() && out.size() < 500) {
                Item it = new Item();
                it.uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(cur.getLong(0)));
                it.title = cur.getString(1);
                it.credit = "Your photos";
                out.add(it);
            }
        } catch (Exception ignored) { }
        Collections.shuffle(out);
        return out;
    }

    private static String niceTitle(String t) {
        t = t.replaceFirst("^File:", "").replaceAll("\\.(jpe?g|JPE?G)$", "").replace('_', ' ');
        t = t.replaceAll("(?i)\\b(edit(ed)?\\d*|crop(ped)?|dsc\\w*|img\\w*|\\d{6,})\\b", "");
        t = t.replaceAll("^[0-9 \\-_.]+", "").replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
        return t.length() > 70 ? t.substring(0, 70) + "…" : t;
    }

    private static String strip(String html) {
        String s = html.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&").replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", "BroomHome/11 (personal Android TV launcher)");
        return con;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = open(u);
        int code = con.getResponseCode();
        if (code != 200) { con.disconnect(); throw new java.io.IOException("HTTP " + code); }
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    private static String read(File f) throws Exception {
        byte[] b = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int n = in.read(b); }
        return new String(b, "UTF-8");
    }

    static List<String> packList() { return Arrays.asList(PACKS); }
}

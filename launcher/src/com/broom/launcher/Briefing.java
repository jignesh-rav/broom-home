package com.broom.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Data for the Briefing screen, home ticker and Briefing screensaver.
 * Markets: Yahoo Finance public quote feed (delayed, display only). Live cricket: Cricinfo live-score RSS.
 * Headlines: Cricinfo, BBC Sport/Tech, NASA, Ars Technica, Google News. Headlines + source + time only.
 * Hindi / Gujarati / Marathi headlines (sections "hi", "gu", "mr", on/off in Settings → Language): BBC and local papers + Google News.
 * Everything is cached on disk; refreshes happen on a background thread only when something is visible.
 */
final class Briefing {
    // ------------------------------------------------------------------ markets
    static String[][] GROUPS = {
            {"India", "^NSEI", "Nifty 50", "^BSESN", "Sensex", "^NSEBANK", "Bank Nifty", "^CNXIT", "Nifty IT"},
            {"US", "^GSPC", "S&P 500", "^IXIC", "Nasdaq", "^DJI", "Dow Jones"},
            {"Global", "^N225", "Nikkei 225", "^HSI", "Hang Seng", "^FTSE", "FTSE 100", "^GDAXI", "DAX"},
            {"Currency & commodities", "USDINR=X", "USD / INR", "GC=F", "Gold ($/oz)", "CL=F", "Crude oil ($)", "BTC-USD", "Bitcoin ($)"},
            // ---- tracker groups (compact cards); index 4+
            {"India indices · by size", "^NSMIDCP", "Nifty Next 50", "^CNX100", "Nifty 100", "NIFTY_MIDCAP_100.NS", "Midcap 100",
                    "^CNXSC", "Smallcap 100", "^CRSLDX", "Nifty 500"},
            {"India indices · by sector", "NIFTY_FIN_SERVICE.NS", "Fin Services", "^CNXPHARMA", "Pharma", "^CNXAUTO", "Auto",
                    "^CNXFMCG", "FMCG", "^CNXMETAL", "Metal", "^CNXENERGY", "Energy", "^CNXREALTY", "Realty", "^CNXPSUBANK", "PSU Bank",
                    "^CNXINFRA", "Infra", "^CNXMEDIA", "Media"},
            {"India stocks", "RELIANCE.NS", "Reliance", "HDFCBANK.NS", "HDFC Bank", "HINDUNILVR.NS", "HUL", "HAL.NS", "HAL",
                    "NESTLEIND.NS", "Nestlé India", "TCS.NS", "TCS", "INFY.NS", "Infosys", "ICICIBANK.NS", "ICICI Bank",
                    "BHARTIARTL.NS", "Airtel", "ITC.NS", "ITC", "LT.NS", "L&T", "SBIN.NS", "SBI", "BAJFINANCE.NS", "Bajaj Finance",
                    "MARUTI.NS", "Maruti", "SUNPHARMA.NS", "Sun Pharma", "BEL.NS", "BEL", "TMPV.NS", "Tata Motors PV",
                    "ADANIENT.NS", "Adani Ent", "ASIANPAINT.NS", "Asian Paints",
                    // most-followed mid / small caps and new-age stocks
                    "ETERNAL.NS", "Eternal", "TRENT.NS", "Trent", "SWIGGY.NS", "Swiggy", "PAYTM.NS", "Paytm",
                    "POLICYBZR.NS", "PB Fintech", "JIOFIN.NS", "Jio Financial", "DIXON.NS", "Dixon", "POLYCAB.NS", "Polycab",
                    "PERSISTENT.NS", "Persistent", "KAYNES.NS", "Kaynes", "BSE.NS", "BSE", "CDSL.NS", "CDSL",
                    "MAZDOCK.NS", "Mazagon Dock", "SUZLON.NS", "Suzlon", "IRFC.NS", "IRFC", "INDIGO.NS", "IndiGo"},
            {"US tech & AI stocks", "NVDA", "Nvidia", "AAPL", "Apple", "GOOGL", "Alphabet", "MSFT", "Microsoft", "AMZN", "Amazon",
                    "META", "Meta", "TSLA", "Tesla", "AVGO", "Broadcom", "AMD", "AMD", "TSM", "TSMC", "MU", "Micron",
                    "SNDK", "SanDisk", "CRWV", "CoreWeave", "000660.KS", "SK Hynix", "005930.KS", "Samsung", "ORCL", "Oracle",
                    "PLTR", "Palantir", "ARM", "Arm", "ASML", "ASML", "INTC", "Intel"}};
    /** GROUPS[0..MAIN-1] are the headline index groups; the rest are trackers. */
    static final int MAIN = 4;
    static final int WL_INDIA = MAIN + 2, WL_US = MAIN + 3;
    static final String[] DEFAULT_INDIA = GROUPS[WL_INDIA].clone(), DEFAULT_US = GROUPS[WL_US].clone();
    private static boolean listsLoaded;

    /** Apply the user's watchlists (saved as JSON [[sym,name],...]) over the defaults. */
    static synchronized void loadLists(Context c) {
        addNewDefaults(c, "wl_india", DEFAULT_INDIA, "ETERNAL.NS");
        addNewDefaults(c, "wl_us", DEFAULT_US, null);   // only makes sure Alphabet is there
        GROUPS[WL_INDIA] = list(c, "wl_india", DEFAULT_INDIA);
        GROUPS[WL_US] = list(c, "wl_us", DEFAULT_US);
        listsLoaded = true;
    }

    /**
     * A saved (edited) watchlist doesn't pick up new default stocks by itself. Once per release, add the
     * defaults from {@code firstNew} onwards that the saved list doesn't have yet (anything removed later stays removed).
     */
    private static void addNewDefaults(Context c, String key, String[] def, String firstNew) {
        String flag = key + "_merged_v16";
        android.content.SharedPreferences p = Theme.prefs(c);
        String js = p.getString(key, null);
        if (js == null || p.getBoolean(flag, false)) return;
        try {
            JSONArray a = new JSONArray(js);
            java.util.Set<String> have = new java.util.HashSet<>();
            for (int i = 0; i < a.length(); i++) have.add(a.getJSONArray(i).getString(0));
            boolean from = false;
            for (int i = 1; i < def.length; i += 2) {
                if (def[i].equals(firstNew)) from = true;
                boolean always = def[i].equals("GOOGL") || def[i].equals("HAL.NS");
                if ((from || always) && !have.contains(def[i])) { JSONArray x = new JSONArray(); x.put(def[i]); x.put(def[i + 1]); a.put(x); }
            }
            p.edit().putString(key, a.toString()).putBoolean(flag, true).apply();
        } catch (Exception ignored) { }
    }

    private static String[] list(Context c, String key, String[] def) {
        String js = Theme.prefs(c).getString(key, null);
        if (js == null) return def;
        try {
            JSONArray a = new JSONArray(js);
            String[] out = new String[1 + a.length() * 2];
            out[0] = def[0];
            for (int i = 0; i < a.length(); i++) { out[1 + i * 2] = a.getJSONArray(i).getString(0); out[2 + i * 2] = a.getJSONArray(i).getString(1); }
            return out;
        } catch (Exception e) { return def; }
    }

    static void saveList(Context c, boolean india, List<String[]> items) {
        JSONArray a = new JSONArray();
        for (String[] it : items) { JSONArray p = new JSONArray(); p.put(it[0]); p.put(it[1]); a.put(p); }
        Theme.prefs(c).edit().putString(india ? "wl_india" : "wl_us", a.toString()).apply();
        loadLists(c);
        file(c, "markets").setLastModified(0);   // refetch with the new symbols
    }

    static void resetLists(Context c) {
        Theme.prefs(c).edit().remove("wl_india").remove("wl_us").apply();
        loadLists(c);
        file(c, "markets").setLastModified(0);
    }

    static void ensureLists(Context c) { if (!listsLoaded) loadLists(c.getApplicationContext()); }

    /** True while a market we track is in its trading session (times in IST, weekdays). */
    static boolean marketsOpen() {
        Calendar k = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
        int dow = k.get(Calendar.DAY_OF_WEEK), mins = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        boolean weekday = dow >= Calendar.MONDAY && dow <= Calendar.FRIDAY;
        boolean asia = weekday && mins >= 5 * 60 + 30 && mins <= 12 * 60 + 30;             // Japan / Korea / HK
        boolean india = weekday && mins >= 9 * 60 + 10 && mins <= 15 * 60 + 40;
        boolean europe = weekday && mins >= 12 * 60 + 30 && mins <= 22 * 60 + 15;
        boolean usEvening = weekday && mins >= 18 * 60 + 55;                                // US open (19:00 / 20:00 IST)
        boolean usNight = dow >= Calendar.TUESDAY && dow <= Calendar.SATURDAY && mins <= 2 * 60 + 40;  // US close after midnight IST
        return asia || india || europe || usEvening || usNight;
    }

    /** How old market data may get: 5 min while something is trading, 1 h otherwise. */
    static long marketsMaxAge() { return marketsOpen() ? 5 * 60000L : 60 * 60000L; }
    static final String[] TICKER = {"^NSEI", "^BSESN", "^NSEBANK", "^GSPC", "^IXIC", "USDINR=X", "GC=F"};

    static final class Quote {
        String sym, name, cur = ""; double price, prev, pct; long time; float[] spark = new float[0];
        boolean up() { return price >= prev; }
    }

    static final class Story { String title, source, link; long time; }

    static final class Score { String text; boolean india, live; }

    // ------------------------------------------------------------------ football (ESPN public scoreboard)
    static final String[][] LEAGUES = {{"eng.1", "Premier League"}, {"uefa.champions", "Champions League"}, {"esp.1", "La Liga"}};
    static final String[] BIG_CLUBS = {"arsenal", "chelsea", "liverpool", "man city", "manchester city", "man united", "manchester united",
            "tottenham", "real madrid", "barcelona", "atl", "bayern", "psg", "paris", "inter", "milan", "juventus", "dortmund"};

    static final class Match { String league, home, away, detail; int hs, as; String state; long start; boolean big; }

    static boolean footballLive(Context c) {
        for (Match m : matches(c)) if ("in".equals(m.state)) return true;
        return false;
    }

    /** Fetch now (off the main thread). */
    static void fetchFootballNow(Context c) {
        if (!Features.on(c, Features.FOOTBALL)) return;
        JSONArray out = new JSONArray();
        for (String[] lg : LEAGUES) {
            try {
                JSONObject d = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + lg[0] + "/scoreboard"));
                JSONArray ev = d.getJSONArray("events");
                for (int i = 0; i < ev.length(); i++) {
                    JSONObject e = ev.getJSONObject(i), comp = e.getJSONArray("competitions").getJSONObject(0);
                    JSONArray t = comp.getJSONArray("competitors");
                    JSONObject h = null, a = null;
                    for (int k = 0; k < t.length(); k++) if ("home".equals(t.getJSONObject(k).optString("homeAway"))) h = t.getJSONObject(k); else a = t.getJSONObject(k);
                    if (h == null || a == null) continue;
                    JSONObject st = e.getJSONObject("status").getJSONObject("type");
                    JSONObject o = new JSONObject();
                    o.put("lg", lg[1]);
                    o.put("h", h.getJSONObject("team").optString("shortDisplayName"));
                    o.put("a", a.getJSONObject("team").optString("shortDisplayName"));
                    o.put("hs", h.optInt("score", 0));
                    o.put("as", a.optInt("score", 0));
                    o.put("st", st.optString("state"));
                    o.put("d", st.optString("shortDetail"));
                    o.put("t", iso(e.optString("date")));
                    out.put(o);
                }
            } catch (Exception ignored) { }
        }
        try { if (out.length() > 0) write(c, "football", out.toString()); } catch (Exception ignored) { }
    }

    private static long iso(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat(s.length() > 17 ? "yyyy-MM-dd'T'HH:mm:ss'Z'" : "yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception e) { return 0; }
    }

    /** Live first, then upcoming by kick-off, then finished. */
    static List<Match> matches(Context c) {
        List<Match> out = new ArrayList<>();
        if (!Features.on(c, Features.FOOTBALL)) return out;
        try {
            JSONArray a = new JSONArray(read(c, "football"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Match m = new Match();
                m.league = o.getString("lg"); m.home = o.getString("h"); m.away = o.getString("a");
                m.hs = o.optInt("hs"); m.as = o.optInt("as"); m.state = o.optString("st"); m.detail = o.optString("d"); m.start = o.optLong("t");
                String both = (m.home + " " + m.away).toLowerCase(Locale.US);
                for (String b : BIG_CLUBS) if (both.contains(b)) { m.big = true; break; }
                out.add(m);
            }
            Collections.sort(out, new Comparator<Match>() {
                @Override public int compare(Match x, Match y) {
                    int rx = rank(x), ry = rank(y);
                    if (rx != ry) return rx - ry;
                    return Long.compare(x.start, y.start);
                }
            });
        } catch (Exception ignored) { }
        return out;
    }

    private static int rank(Match m) { return "in".equals(m.state) ? 0 : "pre".equals(m.state) ? 1 : 2; }

    /** "Arsenal 1–0 Dortmund  67'"  /  "Arsenal v Dortmund  ·  Wed 9:00 PM" */
    static String line(Match m) {
        if ("pre".equals(m.state))
            return m.home + " v " + m.away + "  ·  " + android.text.format.DateFormat.format("EEE d MMM, h:mm a", m.start);
        return m.home + " " + m.hs + "–" + m.as + " " + m.away + "  ·  " + m.detail;
    }

    // ------------------------------------------------------------------ headline feeds
    static final String[][] FEEDS = {
            {"cricket", "https://www.espncricinfo.com/rss/content/story/feeds/0.xml", "ESPNcricinfo"},
            {"cricket", "https://feeds.bbci.co.uk/sport/cricket/rss.xml", "BBC Sport"},
            {"football", "https://feeds.bbci.co.uk/sport/football/rss.xml", "BBC Sport"},
            {"space", "https://www.nasa.gov/news-release/feed/", "NASA"},
            {"space", "https://news.google.com/rss/search?q=ISRO+OR+%22space+mission%22&hl=en-IN&gl=IN&ceid=IN:en", ""},
            {"tech", "https://feeds.arstechnica.com/arstechnica/index", "Ars Technica"},
            {"tech", "https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Tech"},
            {"ai", "https://techcrunch.com/category/artificial-intelligence/feed/", "TechCrunch"},
            {"ai", "https://www.technologyreview.com/topic/artificial-intelligence/feed", "MIT Tech Review"},
            {"ai", "https://arstechnica.com/ai/feed/", "Ars Technica"},
            {"ai", "https://news.google.com/rss/search?q=%22artificial+intelligence%22+OR+%22generative+AI%22+OR+OpenAI+OR+Anthropic+OR+%22AI+model%22+-bitcoin+-crypto&hl=en-IN&gl=IN&ceid=IN:en", ""},
            {"markets", "https://news.google.com/rss/search?q=Sensex+OR+Nifty+OR+%22stock+market%22&hl=en-IN&gl=IN&ceid=IN:en", ""},
            // Indian languages (a few top stories from each; shown only when switched on in Settings → Language)
            {"hi", "https://feeds.bbci.co.uk/hindi/rss.xml", "BBC हिन्दी"},
            {"hi", "https://www.bhaskar.com/rss-v1--category-1061.xml", "दैनिक भास्कर"},
            {"hi", "https://www.amarujala.com/rss/breaking-news.xml", "अमर उजाला"},
            {"hi", "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi", ""},
            {"gu", "https://feeds.bbci.co.uk/gujarati/rss.xml", "BBC ગુજરાતી"},
            {"gu", "https://www.divyabhaskar.co.in/rss-v1--category-1037.xml", "દિવ્ય ભાસ્કર"},
            {"gu", "https://gujarati.abplive.com/home/feed", "ABP અસ્મિતા"},
            {"gu", "https://news.google.com/rss?hl=gu&gl=IN&ceid=IN:gu", ""},
            {"mr", "https://feeds.bbci.co.uk/marathi/rss.xml", "BBC मराठी"},
            {"mr", "https://marathi.abplive.com/home/feed", "ABP माझा"},
            {"mr", "https://www.tv9marathi.com/feed", "TV9 मराठी"},
            {"mr", "https://news.google.com/rss?hl=mr&gl=IN&ceid=IN:mr", ""}};
    static final String[] SECTIONS = {"cricket", "football", "tech", "ai", "space", "markets", Lang.HI, Lang.GU, Lang.MR};
    static final String[] SECTION_TITLES = {"Cricket", "Football", "Tech", "AI", "Space", "Market news",
            "हिन्दी समाचार", "ગુજરાતી સમાચાર", "मराठी बातम्या"};

    /** Hindi / Gujarati / Marathi headline section (switched on or off in Settings → Language)? */
    static boolean isLang(String section) { return Lang.HI.equals(section) || Lang.GU.equals(section) || Lang.MR.equals(section); }

    /** Language sections that are on, e.g. "hi,gu,"; news is refetched when this differs from the cached set. */
    private static String newsLangs(Context c) {
        StringBuilder b = new StringBuilder();
        for (String s : SECTIONS) if (isLang(s) && Features.news(c, s)) b.append(s).append(',');
        return b.toString();
    }

    private static final String[] INTL = {"india", "australia", "england", "south africa", "new zealand", "pakistan", "sri lanka",
            "bangladesh", "west indies", "afghanistan", "zimbabwe", "ireland", "netherlands", "scotland", "nepal", "uae", "usa", "oman"};
    private static final String[] IPL = {"mumbai indians", "chennai super kings", "royal challengers", "kolkata knight riders",
            "sunrisers hyderabad", "rajasthan royals", "delhi capitals", "punjab kings", "lucknow super giants", "gujarat titans"};

    interface Done { void done(); }

    private static File file(Context c, String name) { return new File(c.getFilesDir(), "brief_" + name + ".json"); }

    static boolean stale(Context c, String name, long maxAgeMs) {
        File f = file(c, name);
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMs;
    }

    /** Refreshes whatever is stale (markets 5 min, scores 3 min, news 30 min) and calls back on the main thread. */
    static void refresh(final Context ctx, final boolean news, final Done cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                ensureLists(c);
                if (Features.on(c, Features.MARKETS)) try { if (stale(c, "markets", marketsMaxAge())) fetchMarkets(c); } catch (Exception ignored) { failed(c, "markets"); }
                if (Features.on(c, Features.CRICKET)) try { if (stale(c, "scores", 3 * 60000)) fetchScores(c); } catch (Exception ignored) { }
                if (Features.on(c, Features.FOOTBALL)) try { if (stale(c, "football", footballLive(c) ? 3 * 60000 : 30 * 60000)) fetchFootballNow(c); } catch (Exception ignored) { }
                if (news && Features.anyNews(c)) try {
                    if (stale(c, "news", 30 * 60000) || !newsLangs(c).equals(Theme.prefs(c).getString("news_langs", ""))) fetchNews(c);
                } catch (Exception ignored) { }
                if (cb != null) new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { cb.done(); } });
            }
        }).start();
    }

    // ------------------------------------------------------------------ fetchers
    /** Refresh market data now if it is older than the market-hours limit (call off the main thread). */
    static void fetchMarketsIfStale(Context c) {
        ensureLists(c);
        if (Features.on(c, Features.MARKETS) && stale(c, "markets", marketsMaxAge())) try { fetchMarkets(c.getApplicationContext()); } catch (Exception e) { failed(c, "markets"); }
    }

    private static void fetchMarkets(Context c) throws Exception {
        List<String> all = new ArrayList<>();
        for (String[] g : GROUPS) for (int i = 1; i < g.length; i += 2) all.add(g[i]);
        JSONObject out = new JSONObject();
        for (int from = 0; from < all.size(); from += 20) {       // Yahoo accepts ~20 symbols per request
            StringBuilder syms = new StringBuilder();
            for (int k = from; k < Math.min(all.size(), from + 20); k++) { if (syms.length() > 0) syms.append(','); syms.append(enc(all.get(k))); }
            try { fetchChunk("https://query1.finance.yahoo.com/v7/finance/spark?symbols=" + syms, out); } catch (Exception ignored) { }
        }
        if (out.length() > 0) { write(c, "markets", out.toString()); file(c, "markets_err").delete(); }
        else failed(c, "markets");
    }

    private static void fetchChunk(String base, JSONObject out) throws Exception {
        JSONObject today = new JSONObject(get(base + "&range=1d&interval=15m"));
        JSONObject week = null;
        try { week = new JSONObject(get(base + "&range=5d&interval=1h")); } catch (Exception ignored) { }
        JSONArray r = today.getJSONObject("spark").getJSONArray("result");
        for (int i = 0; i < r.length(); i++) {
            JSONObject m = r.getJSONObject(i).getJSONArray("response").getJSONObject(0).getJSONObject("meta");
            JSONObject q = new JSONObject();
            q.put("p", m.optDouble("regularMarketPrice"));
            q.put("pv", m.has("previousClose") ? m.optDouble("previousClose") : m.optDouble("chartPreviousClose"));
            q.put("t", m.optLong("regularMarketTime") * 1000L);
            q.put("cur", m.optString("currency", ""));
            out.put(r.getJSONObject(i).getString("symbol"), q);
        }
        if (week != null) {
            JSONArray w = week.getJSONObject("spark").getJSONArray("result");
            for (int i = 0; i < w.length(); i++) {
                String s = w.getJSONObject(i).getString("symbol");
                JSONObject q = out.optJSONObject(s);
                if (q == null) continue;
                JSONArray cl = w.getJSONObject(i).getJSONArray("response").getJSONObject(0).getJSONObject("indicators")
                        .getJSONArray("quote").getJSONObject(0).optJSONArray("close");
                JSONArray sp = new JSONArray();
                for (int k = 0; cl != null && k < cl.length(); k++) if (!cl.isNull(k)) sp.put(cl.getDouble(k));
                q.put("sp", sp);
            }
        }
    }

    /** Fetch live scores now (call off the main thread). */
    static void fetchScoresNow(Context c) throws Exception { if (Features.on(c, Features.CRICKET)) fetchScores(c.getApplicationContext()); }

    private static void fetchScores(Context c) throws Exception {
        List<String[]> items = rss("https://static.cricinfo.com/rss/livescores.xml", 60);
        JSONArray out = new JSONArray();
        for (String[] it : items) {
            String t = it[0].trim();
            if (t.isEmpty() || t.startsWith("Cricinfo")) continue;
            String low = t.toLowerCase(Locale.US);
            boolean india = low.contains("india");
            boolean ipl = false;
            for (String s : IPL) if (low.contains(s)) ipl = true;
            String[] sides = low.split(" v ");
            boolean intl = sides.length == 2 && isIntl(sides[0]) && isIntl(sides[1]);
            if (!intl && !ipl) continue;
            JSONObject o = new JSONObject();
            o.put("t", t.replace(" *", "*"));
            o.put("in", india || ipl);
            o.put("live", t.contains("*"));
            out.put(o);
        }
        write(c, "scores", out.toString());
    }

    /** "India 245/3 *" -> true for a national (men's or women's) team, false for A sides, U19, clubs. */
    private static boolean isIntl(String side) {
        String s = side.replaceAll("[0-9/*&().]", " ").replace("women", "").replaceAll("\\s+", " ").trim();
        if (s.matches(".*\\b(a|u19|u-19|xi|club|cc|emerging)\\b.*")) return false;
        for (String n : INTL) if (s.equals(n)) return true;
        return false;
    }

    private static void fetchNews(Context c) throws Exception {
        List<Story> all = new ArrayList<>();
        JSONObject out = new JSONObject();
        for (String sec : SECTIONS) out.put(sec, new JSONArray());
        Set<String> seen = new HashSet<>();
        String langs = newsLangs(c);
        for (String[] f : FEEDS) {
            if (!Features.news(c, f[0])) continue;
            try {
                for (String[] it : rss(f[1], isLang(f[0]) ? 3 : 12)) {     // language sections: each outlet's top 3
                    String title = it[0].trim(), source = f[2];
                    if (source.isEmpty()) {          // Google News: "Headline - Source"
                        int k = title.lastIndexOf(" - ");
                        if (k > 0) { source = title.substring(k + 3); title = title.substring(0, k); }
                        else source = "Google News";
                    }
                    String key = title.toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{M}\\p{N}]", "");   // keeps Devanagari / Gujarati
                    if (title.length() < 12 || !seen.add(key)) continue;
                    JSONObject o = new JSONObject();
                    o.put("t", title);
                    o.put("s", source);
                    o.put("l", it[1]);
                    o.put("d", parseDate(it[2]));
                    out.getJSONArray(f[0]).put(o);
                }
            } catch (Exception ignored) { }
        }
        // newest first, 8 per section
        for (String sec : SECTIONS) {
            JSONArray a = out.getJSONArray(sec);
            List<JSONObject> l = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) l.add(a.getJSONObject(i));
            Collections.sort(l, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject x, JSONObject y) { return Long.compare(y.optLong("d"), x.optLong("d")); }
            });
            JSONArray s = new JSONArray();
            for (int i = 0; i < Math.min(8, l.size()); i++) s.put(l.get(i));
            out.put(sec, s);
        }
        write(c, "news", out.toString());
        Theme.prefs(c).edit().putString("news_langs", langs).apply();
    }

    // ------------------------------------------------------------------ readers
    static List<Quote> quotes(Context c, String[] syms) {
        ensureLists(c);
        List<Quote> out = new ArrayList<>();
        if (!Features.on(c, Features.MARKETS)) return out;
        try {
            JSONObject o = new JSONObject(read(c, "markets"));
            for (String[] g : GROUPS)
                for (int i = 1; i < g.length; i += 2) {
                    if (syms != null && !contains(syms, g[i])) continue;
                    JSONObject q = o.optJSONObject(g[i]);
                    if (q == null) continue;
                    Quote x = new Quote();
                    x.sym = g[i]; x.name = g[i + 1];
                    x.price = q.optDouble("p"); x.prev = q.optDouble("pv"); x.time = q.optLong("t"); x.cur = q.optString("cur", "");
                    x.pct = x.prev > 0 ? (x.price - x.prev) / x.prev * 100 : 0;
                    JSONArray sp = q.optJSONArray("sp");
                    if (sp != null) { x.spark = new float[sp.length()]; for (int k = 0; k < sp.length(); k++) x.spark[k] = (float) sp.getDouble(k); }
                    out.add(x);
                }
        } catch (Exception ignored) { }
        return out;
    }

    /** Quotes of one group, in the group's order. */
    static List<Quote> group(Context c, int gi) {
        ensureLists(c);
        String[] g = GROUPS[gi];
        String[] syms = new String[(g.length - 1) / 2];
        for (int i = 1, k = 0; i < g.length; i += 2, k++) syms[k] = g[i];
        List<Quote> all = quotes(c, syms), out = new ArrayList<>();
        for (String s : syms) for (Quote q : all) if (q.sym.equals(s)) out.add(q);
        return out;
    }

    static List<Score> scores(Context c) {
        List<Score> out = new ArrayList<>();
        if (!Features.on(c, Features.CRICKET)) return out;
        try {
            JSONArray a = new JSONArray(read(c, "scores"));
            for (int i = 0; i < a.length(); i++) {
                Score s = new Score();
                s.text = a.getJSONObject(i).getString("t");
                s.india = a.getJSONObject(i).optBoolean("in");
                s.live = a.getJSONObject(i).optBoolean("live");
                out.add(s);
            }
            Collections.sort(out, new Comparator<Score>() {
                @Override public int compare(Score x, Score y) { return Boolean.compare(y.india, x.india); }
            });
        } catch (Exception ignored) { }
        return out;
    }

    static List<Story> stories(Context c, String section) {
        List<Story> out = new ArrayList<>();
        if (!Features.news(c, section)) return out;
        try {
            JSONArray a = new JSONObject(read(c, "news")).getJSONArray(section);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Story s = new Story();
                s.title = o.getString("t"); s.source = o.optString("s"); s.link = o.optString("l"); s.time = o.optLong("d");
                out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static long updated(Context c, String name) { return file(c, name).lastModified(); }

    /** Remember a failed refresh so screens can say "showing last data". */
    static void failed(Context c, String name) {
        try { write(c, name + "_err", String.valueOf(System.currentTimeMillis())); } catch (Exception ignored) { }
    }

    static boolean lastFailed(Context c, String name) { return file(c, name + "_err").exists(); }

    /** Quote older than 30 min = its market is closed (show "as of"). */
    static boolean closed(Quote q) { return q.time > 0 && System.currentTimeMillis() - q.time > 30 * 60000L; }

    static String asOf(Quote q) {
        if (q.time <= 0) return "";
        return "as of " + android.text.format.DateFormat.format("d MMM, h:mm a", q.time);
    }

    // ------------------------------------------------------------------ formatting
    static String price(Quote q) {
        if (q.sym.equals("USDINR=X")) return String.format(Locale.US, "₹%.2f", q.price);
        boolean stock = !q.sym.startsWith("^") && !q.sym.contains("NIFTY_") && !q.sym.contains("=");
        if (stock && q.cur.equals("INR")) return "₹" + String.format(new Locale("en", "IN"), q.price >= 1000 ? "%,.0f" : "%,.2f", q.price);
        if (stock && q.cur.equals("USD")) return "$" + String.format(Locale.US, "%,.2f", q.price);
        if (q.cur.equals("KRW")) return "₩" + String.format(Locale.US, "%,.0f", q.price);
        if (q.price >= 1000) return String.format(new Locale("en", "IN"), "%,.0f", q.price);
        return String.format(new Locale("en", "IN"), "%,.2f", q.price);
    }

    static String change(Quote q) { return String.format(Locale.US, "%s%.2f%%", q.pct >= 0 ? "▲ " : "▼ ", Math.abs(q.pct)); }

    static String ago(long t) {
        if (t <= 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 48 * 60) return (m / 60) + " h ago";
        return (m / 1440) + " d ago";
    }

    /** Yahoo symbol search: returns {symbol, name, exchange} for equities/ETFs. Call off the main thread. */
    static List<String[]> search(String q) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(get("https://query1.finance.yahoo.com/v1/finance/search?quotesCount=10&newsCount=0&q="
                    + java.net.URLEncoder.encode(q, "UTF-8")));
            JSONArray a = o.getJSONArray("quotes");
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.getJSONObject(i);
                String type = x.optString("quoteType");
                if (!type.equals("EQUITY") && !type.equals("ETF") && !type.equals("INDEX")) continue;
                String name = x.optString("shortname", x.optString("longname", x.getString("symbol")));
                out.add(new String[]{x.getString("symbol"), name, x.optString("exchDisp", x.optString("exchange"))});
            }
        } catch (Exception ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ io helpers
    /** Minimal RSS reader: returns {title, link, pubDate} for the first n items. */
    private static List<String[]> rss(String url, int n) throws Exception {
        List<String[]> out = new ArrayList<>();
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream()) {
            XmlPullParser x = Xml.newPullParser();
            x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            x.setInput(in, null);
            String[] cur = null;
            String tag = null;
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT && out.size() < n; e = x.next()) {
                if (e == XmlPullParser.START_TAG) {
                    tag = x.getName();
                    if ("item".equals(tag)) cur = new String[]{"", "", ""};
                } else if (e == XmlPullParser.TEXT && cur != null && tag != null) {
                    String t = x.getText();
                    if (tag.equals("title")) cur[0] += t;
                    else if (tag.equals("link")) cur[1] += t.trim();
                    else if (tag.equals("pubDate")) cur[2] += t.trim();
                } else if (e == XmlPullParser.END_TAG) {
                    if ("item".equals(x.getName()) && cur != null) { out.add(cur); cur = null; }
                    tag = null;
                }
            }
        } finally { con.disconnect(); }
        return out;
    }

    private static long parseDate(String s) {
        String[] fmts = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z"};
        for (String f : fmts) try { Date d = new SimpleDateFormat(f, Locale.US).parse(s.trim()); if (d != null) return d.getTime(); } catch (Exception ignored) { }
        return 0;
    }

    private static HttpURLConnection open(String u) throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; BroomHome TV)");
            int code = con.getResponseCode();
            if (code / 100 == 3 && con.getHeaderField("Location") != null) {
                u = new URL(new URL(u), con.getHeaderField("Location")).toString();
                con.disconnect();
                continue;
            }
            return con;
        }
        throw new Exception("too many redirects");
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = open(u);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    private static void write(Context c, String name, String s) throws Exception {
        File tmp = new File(c.getFilesDir(), "brief_" + name + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(s.getBytes("UTF-8")); }
        tmp.renameTo(file(c, name));
    }

    private static String read(Context c, String name) throws Exception {
        File f = file(c, name);
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) { int n = in.read(b); }
        return new String(b, "UTF-8");
    }

    private static String enc(String s) { try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    private static boolean contains(String[] a, String s) { for (String x : a) if (x.equals(s)) return true; return false; }
}

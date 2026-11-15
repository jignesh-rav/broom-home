package com.broom.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weather + air quality from Open-Meteo (free, no key, no ads). Fetched at most every 30 min on a
 * background thread and cached in prefs, so screens render instantly and offline.
 */
final class Weather {
    static final long MAX_AGE = 30 * 60 * 1000L;

    static final class Hour {
        String time; int hour, dayIndex, code, rain, hum, aqi = -1; double temp, feels, wind, gust, uv; boolean day;
    }
    static final class Day {
        String name, date, sunrise, sunset; int code, rain; double max, min, precip, windMax, gustMax, uvMax;
    }

    String city;
    double temp, feels, wind, gust, uv;
    int humidity, code, aqi = -1;
    double pm25 = -1;
    boolean isDay = true;
    String sunrise, sunset;
    final List<Hour> hours = new ArrayList<>();     // next 12 h (home / screensaver)
    final List<Hour> allHours = new ArrayList<>();  // next 48 h (weather screen + analysis)
    final List<Day> days = new ArrayList<>();       // up to 14 days
    long fetched;

    interface Listener { void onWeather(Weather w); }
    interface CityResult { void done(String name); }

    private static SharedPreferences prefs(Context c) { return Theme.prefs(c); }
    static boolean hasCity(Context c) { return prefs(c).contains("wx_lat"); }
    static boolean auto(Context c) { return prefs(c).getBoolean("wx_auto", true); }

    static Weather cached(Context c) {
        String js = prefs(c).getString("wx_json", null);
        if (js == null) return null;
        try {
            String aq = prefs(c).getString("wx_aqi", null);
            return parse(prefs(c).getString("wx_city", ""), new JSONObject(js), aq == null ? null : new JSONObject(aq),
                    prefs(c).getLong("wx_time", 0));
        } catch (Exception e) { return null; }
    }

    static void refresh(final Context ctx, boolean force, final Listener l) {
        final Context c = ctx.getApplicationContext();
        if (auto(c) && (!hasCity(c) || System.currentTimeMillis() - prefs(c).getLong("wx_loc_time", 0) > 24 * 3600 * 1000L)) {
            prefs(c).edit().putLong("wx_loc_time", System.currentTimeMillis()).apply();
            useDeviceLocation(c, new CityResult() { @Override public void done(String n) { if (n != null) refresh(c, true, l); } });
            return;
        }
        if (!hasCity(c)) return;
        if (!force && cached(c) != null && System.currentTimeMillis() - prefs(c).getLong("wx_time", 0) < MAX_AGE) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    double lat = Double.longBitsToDouble(prefs(c).getLong("wx_lat", 0)), lon = Double.longBitsToDouble(prefs(c).getLong("wx_lon", 0));
                    String body = get(String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m,uv_index,is_day"
                                    + "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,precipitation_probability,wind_speed_10m,wind_gusts_10m,uv_index,is_day"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunrise,sunset"
                                    + "&timezone=auto&forecast_days=14", lat, lon));
                    new JSONObject(body);
                    String aq = null;
                    try {
                        aq = get(String.format(Locale.US, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f"
                                + "&current=us_aqi,pm2_5&hourly=us_aqi&timezone=auto&forecast_days=3", lat, lon));
                        new JSONObject(aq);
                    } catch (Exception ignored) { aq = null; }
                    SharedPreferences.Editor e = prefs(c).edit().putString("wx_json", body).putLong("wx_time", System.currentTimeMillis());
                    if (aq != null) e.putString("wx_aqi", aq);
                    e.apply();
                    final Weather w = cached(c);
                    if (l != null && w != null) new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() { l.onWeather(w); }
                    });
                } catch (Exception ignored) { }
            }
        }).start();
    }

    /** A place the person can pick when changing the weather area. */
    static final class Place {
        final String name; final double lat, lon;
        Place(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }
    interface PlacesResult { void done(List<Place> places); }

    /**
     * Finds places matching what was typed. Accepts "lat, lon" too. Uses the TV's own geocoder first
     * (Google: knows neighbourhoods and localities), then Open-Meteo's place search, and returns both,
     * without duplicates, so the person can pick the right one.
     */
    static void search(final Context ctx, final String query, final PlacesResult r) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Place> out = new ArrayList<>();
                String q = query == null ? "" : query.trim();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$").matcher(q);
                if (m.matches()) {
                    double lat = Double.parseDouble(m.group(1)), lon = Double.parseDouble(m.group(2));
                    String n = areaName(c, lat, lon);
                    out.add(new Place(n != null ? n : String.format(Locale.US, "%.3f, %.3f", lat, lon), lat, lon));
                } else if (!q.isEmpty()) {
                    try {
                        if (android.location.Geocoder.isPresent()) {
                            List<android.location.Address> l = new android.location.Geocoder(c, Locale.ENGLISH).getFromLocationName(q, 6);
                            if (l != null) for (android.location.Address a : l) {
                                if (!a.hasLatitude()) continue;
                                String n = label(a);
                                if (n != null) addPlace(out, new Place(n, a.getLatitude(), a.getLongitude()));
                            }
                        }
                    } catch (Exception ignored) { }
                    try {
                        JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&name="
                                + URLEncoder.encode(q, "UTF-8")));
                        org.json.JSONArray a = o.optJSONArray("results");
                        if (a != null) for (int i = 0; i < a.length(); i++) {
                            JSONObject g = a.getJSONObject(i);
                            StringBuilder n = new StringBuilder(g.getString("name"));
                            for (String k : new String[]{"admin2", "admin1", "country"}) {
                                String v = g.optString(k, "");
                                if (!v.isEmpty() && n.indexOf(v) < 0) n.append(", ").append(v);
                            }
                            addPlace(out, new Place(n.toString(), g.getDouble("latitude"), g.getDouble("longitude")));
                        }
                    } catch (Exception ignored) { }
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { r.done(out); } });
            }
        }).start();
    }

    private static void addPlace(List<Place> out, Place p) {
        for (Place x : out) if (Math.abs(x.lat - p.lat) < 0.01 && Math.abs(x.lon - p.lon) < 0.01) return;
        out.add(p);
    }

    private static String label(android.location.Address a) {
        StringBuilder b = new StringBuilder();
        for (String v : new String[]{a.getFeatureName(), a.getSubLocality(), a.getLocality(), a.getAdminArea(), a.getCountryName()}) {
            if (v == null || v.isEmpty() || v.matches("^[0-9 ,+-]+$") || b.indexOf(v) >= 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(v);
        }
        return b.length() == 0 ? null : b.toString();
    }

    /** Saves a picked place as the (manual) weather area. Short name: the first two parts of the label. */
    static void setPlace(Context ctx, Place p) {
        String[] parts = p.name.split(",\\s*");
        String shortName = parts.length > 1 ? parts[0] + ", " + parts[1] : p.name;
        prefs(ctx).edit().putLong("wx_lat", Double.doubleToLongBits(p.lat)).putLong("wx_lon", Double.doubleToLongBits(p.lon))
                .putString("wx_city", shortName).putBoolean("wx_auto", false).remove("wx_time").apply();
    }

    static void setCity(final Context ctx, final String query, final CityResult r) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                String name = null;
                try {
                    JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name="
                            + URLEncoder.encode(query.trim(), "UTF-8")));
                    JSONObject g = o.getJSONArray("results").getJSONObject(0);
                    name = g.getString("name");
                    String admin = g.optString("admin2", g.optString("admin1", ""));
                    if (!admin.isEmpty() && !admin.equals(name)) name = name + ", " + admin;
                    prefs(c).edit().putLong("wx_lat", Double.doubleToLongBits(g.getDouble("latitude")))
                            .putLong("wx_lon", Double.doubleToLongBits(g.getDouble("longitude")))
                            .putString("wx_city", name).putBoolean("wx_auto", false).remove("wx_time").apply();
                } catch (Exception ignored) { }
                final String res = name;
                new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { r.done(res); } });
            }
        }).start();
    }

    /** Uses the TV's Wi-Fi based location (Google network location). Rounded to ~1 km before use. */
    @SuppressWarnings("MissingPermission")
    static void useDeviceLocation(final Context ctx, final CityResult r) {
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                String name = null;
                try {
                    android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
                    android.location.Location best = null;
                    for (String p : new String[]{"fused", android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER}) {
                        try {
                            android.location.Location l = lm.getLastKnownLocation(p);
                            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                        } catch (Exception ignored) { }
                    }
                    if (best == null) best = freshLocation(lm);
                    if (best != null) {
                        double lat = Math.round(best.getLatitude() * 100) / 100.0, lon = Math.round(best.getLongitude() * 100) / 100.0;
                        name = areaName(c, best.getLatitude(), best.getLongitude());
                        if (name == null) name = String.format(Locale.US, "%.2f, %.2f", lat, lon);
                        prefs(c).edit().putLong("wx_lat", Double.doubleToLongBits(lat)).putLong("wx_lon", Double.doubleToLongBits(lon))
                                .putString("wx_city", name).putBoolean("wx_auto", true)
                                .putLong("wx_loc_time", System.currentTimeMillis()).remove("wx_time").apply();
                    }
                } catch (Exception ignored) { }
                final String res = name;
                if (r != null) new Handler(Looper.getMainLooper()).post(new Runnable() { @Override public void run() { r.done(res); } });
            }
        }).start();
    }

    /**
     * Nothing cached (no other app has asked for location since boot, common on debloated TVs):
     * ask the network / fused provider for one fix and wait up to 30 s. Runs on a background thread.
     */
    @SuppressWarnings("MissingPermission")
    private static android.location.Location freshLocation(android.location.LocationManager lm) {
        final android.location.Location[] got = new android.location.Location[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        android.os.HandlerThread ht = new android.os.HandlerThread("broom-loc");
        ht.start();
        final android.location.LocationListener ll = new android.location.LocationListener() {
            @Override public void onLocationChanged(android.location.Location l) {
                synchronized (got) { if (got[0] == null) got[0] = l; }
                latch.countDown();
            }
            @Override public void onStatusChanged(String p, int s, android.os.Bundle b) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        };
        int asked = 0;
        for (String p : new String[]{android.location.LocationManager.NETWORK_PROVIDER, "fused"}) {
            try {
                if (lm.getAllProviders().contains(p)) { lm.requestSingleUpdate(p, ll, ht.getLooper()); asked++; }
            } catch (Exception ignored) { }
        }
        try { if (asked > 0) latch.await(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        try { lm.removeUpdates(ll); } catch (Exception ignored) { }
        ht.quitSafely();
        synchronized (got) { return got[0]; }
    }

    static String areaName(Context c, double lat, double lon) {
        try {
            if (!android.location.Geocoder.isPresent()) return null;
            List<android.location.Address> l = new android.location.Geocoder(c, Locale.ENGLISH).getFromLocation(lat, lon, 1);
            if (l == null || l.isEmpty()) return null;
            android.location.Address a = l.get(0);
            String area = a.getSubLocality(), city = a.getLocality();
            if (area != null && city != null && !area.equals(city)) return area + ", " + city;
            return city != null ? city : area;
        } catch (Exception e) { return null; }
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "BroomHome/3");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { con.disconnect(); }
    }

    private static Weather parse(String city, JSONObject o, JSONObject aq, long time) throws Exception {
        Weather w = new Weather();
        w.city = city;
        w.fetched = time;
        JSONObject cur = o.getJSONObject("current");
        w.temp = cur.getDouble("temperature_2m");
        w.feels = cur.getDouble("apparent_temperature");
        w.humidity = cur.getInt("relative_humidity_2m");
        w.code = cur.getInt("weather_code");
        w.wind = cur.optDouble("wind_speed_10m", 0);
        w.gust = cur.optDouble("wind_gusts_10m", 0);
        w.uv = cur.optDouble("uv_index", 0);
        w.isDay = cur.optInt("is_day", 1) == 1;

        Map<String, Integer> aqiByTime = new HashMap<>();
        if (aq != null) {
            JSONObject ac = aq.optJSONObject("current");
            if (ac != null) { w.aqi = ac.optInt("us_aqi", -1); w.pm25 = ac.optDouble("pm2_5", -1); }
            JSONObject ah = aq.optJSONObject("hourly");
            if (ah != null) {
                JSONArray t = ah.getJSONArray("time"), v = ah.getJSONArray("us_aqi");
                for (int i = 0; i < t.length(); i++) if (!v.isNull(i)) aqiByTime.put(t.getString(i), v.getInt(i));
            }
        }

        JSONObject d = o.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time");
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        Map<String, Integer> dayIdx = new HashMap<>();
        for (int i = 0; i < dt.length(); i++) {
            Day x = new Day();
            String[] ymd = dt.getString(i).split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]));
            x.name = i == 0 ? "Today" : names[cal.get(Calendar.DAY_OF_WEEK) - 1];
            x.date = Integer.parseInt(ymd[2]) + " " + months[Integer.parseInt(ymd[1]) - 1];
            x.code = d.getJSONArray("weather_code").getInt(i);
            x.max = d.getJSONArray("temperature_2m_max").getDouble(i);
            x.min = d.getJSONArray("temperature_2m_min").getDouble(i);
            x.rain = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            x.precip = d.getJSONArray("precipitation_sum").optDouble(i, 0);
            x.windMax = d.getJSONArray("wind_speed_10m_max").optDouble(i, 0);
            x.gustMax = d.getJSONArray("wind_gusts_10m_max").optDouble(i, 0);
            x.uvMax = d.getJSONArray("uv_index_max").optDouble(i, 0);
            x.sunrise = d.getJSONArray("sunrise").getString(i).substring(11);
            x.sunset = d.getJSONArray("sunset").getString(i).substring(11);
            w.days.add(x);
            dayIdx.put(dt.getString(i), i);
        }
        if (!w.days.isEmpty()) { w.sunrise = w.days.get(0).sunrise; w.sunset = w.days.get(0).sunset; }

        JSONObject h = o.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time");
        Calendar now = Calendar.getInstance();
        String nowKey = String.format(Locale.US, "%04d-%02d-%02dT%02d:00", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY));
        int start = 0;
        for (int i = 0; i < ht.length(); i++) if (ht.getString(i).compareTo(nowKey) >= 0) { start = i; break; }
        for (int i = start; i < Math.min(ht.length(), start + 80); i++) {
            Hour x = new Hour();
            String ts = ht.getString(i);
            x.hour = Integer.parseInt(ts.substring(11, 13));
            x.time = x.hour == 0 ? "12a" : x.hour < 12 ? x.hour + "a" : x.hour == 12 ? "12p" : (x.hour - 12) + "p";
            Integer di = dayIdx.get(ts.substring(0, 10));
            x.dayIndex = di == null ? 0 : di;
            x.temp = h.getJSONArray("temperature_2m").getDouble(i);
            x.feels = h.getJSONArray("apparent_temperature").getDouble(i);
            x.hum = h.getJSONArray("relative_humidity_2m").optInt(i, 0);
            x.code = h.getJSONArray("weather_code").getInt(i);
            x.rain = h.getJSONArray("precipitation_probability").optInt(i, 0);
            x.wind = h.getJSONArray("wind_speed_10m").optDouble(i, 0);
            x.gust = h.getJSONArray("wind_gusts_10m").optDouble(i, 0);
            x.uv = h.getJSONArray("uv_index").optDouble(i, 0);
            x.day = h.getJSONArray("is_day").optInt(i, 1) == 1;
            Integer a = aqiByTime.get(ts);
            if (a != null) x.aqi = a;
            w.allHours.add(x);
            if (i > start && w.hours.size() < 12) w.hours.add(x);
        }
        return w;
    }

    // =================================================================== insights

    static final class Alert {
        final String icon, title, detail; final int level; // 2 = warning, 1 = advisory, 0 = info/good
        Alert(String icon, String title, String detail, int level) { this.icon = icon; this.title = title; this.detail = detail; this.level = level; }
    }

    static String hourLabel(int h) { return h == 0 ? "12 AM" : h < 12 ? h + " AM" : h == 12 ? "12 PM" : (h - 12) + " PM"; }

    private String when(Hour h) { return days.get(Math.min(h.dayIndex, days.size() - 1)).name + " " + hourLabel(h.hour); }

    List<Alert> alerts() {
        List<Alert> out = new ArrayList<>();
        for (Hour h : allHours) if (h.code >= 95) {
            out.add(new Alert("⛈️", "Thunderstorms likely", "From " + when(h) + ". Unplug sensitive devices, stay indoors.", 2)); break;
        }
        for (int i = 0; i < Math.min(3, days.size()); i++) {
            Day d = days.get(i);
            if (d.precip >= 30 || (d.rain >= 80 && d.precip >= 10)) {
                out.add(new Alert("🌧️", "Heavy rain " + d.name, Math.round(d.precip) + " mm expected, " + d.rain + "% chance. Expect waterlogging.", 2)); break;
            } else if (d.rain >= 60) {
                out.add(new Alert("☔", "Rain likely " + d.name, d.rain + "% chance, about " + Math.round(d.precip) + " mm. Carry an umbrella.", 1)); break;
            }
        }
        for (int i = 0; i < Math.min(3, days.size()); i++) {
            Day d = days.get(i);
            if (d.gustMax >= 50) { out.add(new Alert("💨", "Strong winds " + d.name, "Gusts up to " + Math.round(d.gustMax) + " km/h. Secure loose items on balconies.", 2)); break; }
            if (d.gustMax >= 35) { out.add(new Alert("🌬️", "Windy " + d.name, "Gusts up to " + Math.round(d.gustMax) + " km/h.", 1)); break; }
        }
        double maxFeels = -99; Hour hotH = null;
        for (Hour h : allHours) if (h.feels > maxFeels) { maxFeels = h.feels; hotH = h; }
        double maxT = -99; Day hotD = null;
        for (int i = 0; i < Math.min(3, days.size()); i++) if (days.get(i).max > maxT) { maxT = days.get(i).max; hotD = days.get(i); }
        if (hotH != null && (maxFeels >= 41 || maxT >= 38))
            out.add(new Alert("🔥", "Extreme heat", "Feels like " + Math.round(maxFeels) + "° around " + when(hotH) + ". Stay hydrated, avoid midday sun.", 2));
        else if (hotH != null && (maxFeels >= 37 || maxT >= 35))
            out.add(new Alert("🌡️", "Hot afternoons", (hotD != null ? hotD.name + " up to " + Math.round(maxT) + "°, " : "") + "feels " + Math.round(maxFeels) + "° around " + when(hotH) + ".", 1));
        int muggy = 0; Hour mug = null;
        for (Hour h : allHours) if (h.hum >= 75 && h.temp >= 27) { muggy++; if (mug == null) mug = h; }
        if (muggy >= 4) out.add(new Alert("💧", "Humid & sticky", "Humidity " + mug.hum + "% at " + Math.round(mug.temp) + "° (feels " + Math.round(mug.feels) + "°) from " + when(mug) + ".", 1));
        for (int i = 0; i < Math.min(2, days.size()); i++) {
            Day d = days.get(i);
            if (d.uvMax >= 8) { out.add(new Alert("🕶️", "Very high UV " + d.name, "UV index " + Math.round(d.uvMax) + ". Avoid direct sun 11 AM - 3 PM.", 1)); break; }
        }
        int maxAqi = aqi;
        for (int i = 0; i < Math.min(24, allHours.size()); i++) maxAqi = Math.max(maxAqi, allHours.get(i).aqi);
        if (maxAqi >= 151) out.add(new Alert("😷", "Unhealthy air (AQI " + maxAqi + ")", "Limit outdoor exercise; a mask helps. Keep windows closed at peak hours.", 2));
        else if (maxAqi >= 101) out.add(new Alert("🌫️", "Air poor for sensitive groups (AQI " + maxAqi + ")", "Children, elders and asthma: keep outdoor time short.", 1));
        if (days.size() >= 7) {
            double a = 0, b = 0, an = 0, bn = 0;
            for (int i = 0; i < 3; i++) { a += days.get(i).max; an += days.get(i).min; }
            for (int i = 3; i < 7; i++) { b += days.get(i).max; bn += days.get(i).min; }
            a /= 3; an /= 3; b /= 4; bn /= 4;
            if (a - b >= 2.0) out.add(new Alert("❄️", "Cooling down ahead", "Highs drop from about " + Math.round(a) + "° to " + Math.round(b) + "° from " + days.get(3).name + ".", 0));
            else if (b - a >= 2.0) out.add(new Alert("📈", "Warming up ahead", "Highs rise from about " + Math.round(a) + "° to " + Math.round(b) + "° from " + days.get(3).name + ".", 1));
            else out.add(new Alert("➖", "Steady temperatures", "Highs stay around " + Math.round((a + b) / 2) + "° through next week.", 0));
            if (an - bn >= 2.0) out.add(new Alert("🌙", "Cooler nights coming", "Lows fall from about " + Math.round(an) + "° to " + Math.round(bn) + "°.", 0));
        }
        boolean warn = false;
        for (Alert x : out) if (x.level > 0) warn = true;
        if (!warn) out.add(0, new Alert("✅", "No weather warnings", "Nothing unusual expected in the next 3 days.", 0));
        return out;
    }

    static final class Window { String day, from, to, why; int score, startHour; double feels; int aqi, rain; }

    /** Comfort score 0-100 for being active outdoors in this hour. */
    static int comfort(Hour h) {
        double s = 100 - Math.abs(h.feels - 22) * 4.5 - h.rain * 0.7 - Math.max(0, h.wind - 20) * 1.5
                - Math.max(0, h.uv - 5) * 7 - (h.aqi > 50 ? (h.aqi - 50) * 0.35 : 0) - (h.code >= 95 ? 60 : 0);
        // bad air caps the rating, however pleasant the temperature: unhealthy (151+) is never better than Poor,
        // poor-for-sensitive-groups (101-150) never better than Fair
        if (h.aqi > 200) s = Math.min(s, 15);
        else if (h.aqi > 150) s = Math.min(s, 30);
        else if (h.aqi > 100) s = Math.min(s, 50);
        return (int) Math.max(0, Math.min(100, s));
    }

    /** Best (or worst daytime) 2-hour window between 5 AM and 9 PM for the given day index, or null. */
    Window best(int dayIndex, boolean worst) {
        Window out = null;
        int startHour = dayIndex == 0 ? Calendar.getInstance().get(Calendar.HOUR_OF_DAY) : 0;
        for (int i = 0; i + 1 < allHours.size(); i++) {
            Hour a = allHours.get(i), b = allHours.get(i + 1);
            if (a.dayIndex != dayIndex || b.dayIndex != dayIndex) continue;
            if (a.hour < 5 || a.hour > 19 || a.hour < startHour) continue;
            if (worst && (a.hour < 9 || a.hour > 17)) continue;
            int sc = (comfort(a) + comfort(b)) / 2;
            if (out == null || (worst ? sc < out.score : sc > out.score)) {
                out = new Window();
                out.day = days.get(dayIndex).name;
                out.from = hourLabel(a.hour);
                out.to = hourLabel((a.hour + 2) % 24);
                out.score = sc;
                out.feels = (a.feels + b.feels) / 2;
                out.aqi = Math.max(a.aqi, b.aqi);
                out.rain = Math.max(a.rain, b.rain);
                StringBuilder why = new StringBuilder("Feels " + Math.round(out.feels) + "°");
                if (out.aqi >= 0) why.append("  ·  AQI ").append(out.aqi);
                why.append("  ·  Rain ").append(out.rain).append("%");
                double uv = Math.max(a.uv, b.uv);
                if (uv >= 3) why.append("  ·  UV ").append(Math.round(uv));
                out.why = why.toString();
            }
        }
        return out;
    }

    /** For one day: [best, a different okay-ish backup slot, worst] 2-hour windows between 5 AM and 9 PM. */
    List<Window> plan(int dayIndex) {
        List<Window> all = new ArrayList<>();
        int startHour = dayIndex == 0 ? Calendar.getInstance().get(Calendar.HOUR_OF_DAY) : 0;
        for (int i = 0; i + 1 < allHours.size(); i++) {
            Hour a = allHours.get(i), b = allHours.get(i + 1);
            if (a.dayIndex != dayIndex || b.dayIndex != dayIndex) continue;
            if (a.hour < 5 || a.hour > 19 || a.hour < startHour) continue;
            Window x = new Window();
            x.day = days.get(dayIndex).name;
            x.from = hourLabel(a.hour);
            x.to = hourLabel((a.hour + 2) % 24);
            x.startHour = a.hour;
            x.score = (comfort(a) + comfort(b)) / 2;
            x.feels = (a.feels + b.feels) / 2;
            x.aqi = Math.max(a.aqi, b.aqi);
            x.rain = Math.max(a.rain, b.rain);
            double uv = Math.max(a.uv, b.uv);
            StringBuilder why = new StringBuilder("Feels " + Math.round(x.feels) + "°");
            if (x.aqi >= 0) why.append(" · AQI ").append(x.aqi);
            if (x.rain >= 10) why.append(" · Rain ").append(x.rain).append("%");
            if (uv >= 3) why.append(" · UV ").append(Math.round(uv));
            x.why = why.toString();
            all.add(x);
        }
        List<Window> out = new ArrayList<>();
        if (all.isEmpty()) return out;
        Window best = all.get(0), worst = all.get(0);
        for (Window x : all) { if (x.score > best.score) best = x; if (x.score < worst.score) worst = x; }
        Window alt = null;
        for (Window x : all) {
            if (Math.abs(x.startHour - best.startHour) < 3 || Math.abs(x.startHour - worst.startHour) < 2) continue;
            if (alt == null || x.score > alt.score) alt = x;
        }
        out.add(best);
        out.add(alt);
        out.add(worst == best ? null : worst);
        return out;
    }

    static String rating(int score) { return score >= 75 ? "Great" : score >= 55 ? "Good" : score >= 35 ? "Fair" : "Poor"; }

    static int ratingColor(int score) { return score >= 75 ? 0xFF4CD964 : score >= 55 ? 0xFFA3E635 : score >= 35 ? 0xFFFFD60A : 0xFFFF6B6B; }

    static String aqiLabel(int a) {
        if (a < 0) return "—";
        if (a <= 50) return "Good";
        if (a <= 100) return "Moderate";
        if (a <= 150) return "Poor (sensitive)";
        if (a <= 200) return "Unhealthy";
        if (a <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    static int aqiColor(int a) {
        if (a < 0) return 0xFF9AA0A6;
        if (a <= 50) return 0xFF4CD964;
        if (a <= 100) return 0xFFFFD60A;
        if (a <= 150) return 0xFFFF9F0A;
        if (a <= 200) return 0xFFFF453A;
        return 0xFFBF5AF2;
    }

    static String deg(double t) { return Math.round(t) + "°"; }

    /** WMO weather code -> emoji (rendered in colour by the system emoji font). */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1) return day ? "🌤️" : "🌙";
        if (code == 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌥️";
        if (code >= 51 && code <= 57) return "🌦️";
        if (code >= 61 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "🌨️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 85 && code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌡️";
    }

    static String label(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog / haze";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }
}

package com.broom.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Full weather screen: now, warnings & trends, best time outdoors, 24 h and 14-day forecast. */
public class WeatherActivity extends Activity {
    private Theme th;
    private LinearLayout col;
    private ScrollView sv;
    private final Weather.Listener listener = new Weather.Listener() {
        @Override public void onWeather(Weather w) { render(w); }
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
        sv = new ScrollView(this);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 22), pad, pad);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render(Weather.cached(this));
        Weather.refresh(this, true, listener);
    }

    private void render(Weather w) {
        col.removeAllViews();
        // ---------- title row + location buttons
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(Ui.text(this, "Weather", 30, th.text, "Poppins-SemiBold"));
        String loc = w == null ? "Finding your location..." : "📍 " + w.city + (Weather.auto(this) ? "  (TV location)" : "");
        tl.addView(Ui.text(this, loc, 13, th.dim, th.bodyFont));
        title.addView(tl, new LinearLayout.LayoutParams(0, -2, 1));
        View tvLoc = Ui.pill(this, "Use TV location", th, new View.OnClickListener() {
            @Override public void onClick(View v) { autoLocate(); }
        });
        title.addView(tvLoc, gap());
        View area = Ui.pill(this, "Change area", th, new View.OnClickListener() {
            @Override public void onClick(View v) { askArea(); }
        });
        title.addView(area, gap());
        View refresh = Ui.pill(this, "Refresh", th, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(WeatherActivity.this, "Updating…", Toast.LENGTH_SHORT).show();
                Weather.refresh(WeatherActivity.this, true, listener);
            }
        });
        title.addView(refresh, gap());
        col.addView(title);
        refresh.post(new Runnable() { @Override public void run() { if (getCurrentFocus() == null) sv.requestFocus(); } });
        if (w == null) return;

        // ---------- now + warnings side by side
        LinearLayout top = row();
        top.setGravity(Gravity.TOP);
        top.setPadding(0, Ui.dp(this, 14), 0, 0);

        LinearLayout now = card();
        LinearLayout big = row();
        big.setGravity(Gravity.CENTER_VERTICAL);
        big.addView(Ui.text(this, Weather.icon(w.code, w.isDay), 40, th.text, th.bodyFont));
        big.addView(Ui.text(this, " " + Weather.deg(w.temp), 52, th.text, "Poppins-Light"));
        LinearLayout bl = new LinearLayout(this);
        bl.setOrientation(LinearLayout.VERTICAL);
        bl.setPadding(Ui.dp(this, 12), 0, 0, 0);
        bl.addView(Ui.text(this, Weather.label(w.code), 16, th.text, th.titleFont));
        bl.addView(Ui.text(this, "Feels like " + Weather.deg(w.feels), 13, th.dim, th.bodyFont));
        big.addView(bl);
        now.addView(big);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, Ui.dp(this, 10), 0, 0);
        grid.addView(stat2("💧 Humidity", w.humidity + "%", "💨 Wind", Math.round(w.wind) + " km/h (gusts " + Math.round(w.gust) + ")"));
        grid.addView(stat2("🕶️ UV index", Math.round(w.uv) + uvWord(w.uv), "🍃 Air quality", w.aqi < 0 ? "—" : w.aqi + " · " + Weather.aqiLabel(w.aqi)));
        if (w.days.size() > 0) {
            Weather.Day d0 = w.days.get(0);
            grid.addView(stat2("🌅 Sunrise", d0.sunrise, "🌇 Sunset", d0.sunset));
            grid.addView(stat2("⬆ Today high", Weather.deg(d0.max), "⬇ Tonight low", Weather.deg(d0.min)));
        }
        now.addView(grid);
        top.addView(now, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout warn = card();
        warn.addView(head("Warnings & trends"));
        List<Weather.Alert> alerts = w.alerts();
        for (int i = 0; i < Math.min(6, alerts.size()); i++) warn.addView(alert(alerts.get(i)));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1.25f);
        wl.leftMargin = Ui.dp(this, 14);
        top.addView(warn, wl);
        col.addView(top);

        // ---------- best time outdoors
        LinearLayout out = card();
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
        ol.topMargin = Ui.dp(this, 14);
        out.addView(head("Walk / jog planner — next 3 days"));
        LinearLayout orow = row();
        int shown = 0;
        for (int di = 0; di < w.days.size() && shown < 3; di++) {
            List<Weather.Window> pl = w.plan(di);
            if (pl.isEmpty() || pl.get(0) == null) continue;
            shown++;
            LinearLayout dayCol = new LinearLayout(this);
            dayCol.setOrientation(LinearLayout.VERTICAL);
            Weather.Day d = w.days.get(di);
            dayCol.addView(Ui.text(this, (di == 0 ? "Today" : d.name) + "  ·  " + d.date, 15, th.text, th.titleFont));
            // a "best" slot that is still Poor isn't a recommendation: say the day is bad and label slots honestly
            boolean badDay = pl.get(0).score < 35;
            if (badDay) {
                TextView v = Ui.text(this, "Poor all day: better to exercise indoors", 12, 0xFFFF8A80, "Poppins-SemiBold");
                v.setPadding(0, Ui.dp(this, 4), 0, 0);
                dayCol.addView(v);
            }
            Weather.Window bk = pl.get(1);
            String[] tags = {badDay ? "Least bad" : "Best",
                    bk != null && bk.score < 35 ? "Also poor" : "Backup", "Avoid"};
            for (int k = 0; k < 3; k++) {
                Weather.Window x = pl.get(k);
                if (x == null) continue;
                int col = k == 2 ? 0xFFFF8A80 : Weather.ratingColor(x.score);
                LinearLayout line = row();
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, Ui.dp(this, 6), 0, 0);
                View dot = new View(this);
                dot.setBackground(Ui.round(col, Ui.dp(this, 5)));
                LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
                dlp2.rightMargin = Ui.dp(this, 8);
                line.addView(dot, dlp2);
                line.addView(Ui.text(this, tags[k] + "   " + x.from + " – " + x.to, 13, th.text, "Poppins-Medium"));
                TextView rt = Ui.text(this, "   " + Weather.rating(x.score), 12, col, "Poppins-SemiBold");
                line.addView(rt);
                dayCol.addView(line);
                TextView why = Ui.text(this, x.why, 11, th.dim, th.bodyFont);
                why.setPadding(Ui.dp(this, 16), Ui.dp(this, 1), 0, 0);
                dayCol.addView(why);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            cp.rightMargin = Ui.dp(this, 14);
            orow.addView(dayCol, cp);
        }
        out.addView(orow);
        col.addView(out, ol);

        // ---------- next 24 hours (every 2 h)
        LinearLayout hc = card();
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(this, 14);
        hc.addView(head("Next 24 hours"));
        LinearLayout hr = row();
        for (int i = 1; i < Math.min(25, w.allHours.size()); i += 2) {
            Weather.Hour h = w.allHours.get(i);
            LinearLayout c = cellCol();
            c.addView(small(h.time, th.dim));
            c.addView(center(Ui.text(this, Weather.icon(h.code, h.day), 17, th.text, th.bodyFont)));
            c.addView(center(Ui.text(this, Weather.deg(h.temp), 13, th.text, "Poppins-Medium")));
            c.addView(small("feels " + Weather.deg(h.feels), th.dim));
            c.addView(small(h.rain + "% · " + Math.round(h.wind), h.rain >= 50 ? 0xFF8FD3FF : th.dim));
            View dot = new View(this);
            dot.setBackground(Ui.round(Weather.ratingColor(Weather.comfort(h)), Ui.dp(this, 3)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 4));
            dl.topMargin = Ui.dp(this, 4);
            dl.gravity = Gravity.CENTER_HORIZONTAL;
            c.addView(dot, dl);
            hr.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        }
        hc.addView(hr);
        hc.addView(small("rain % · wind km/h      bar = outdoor comfort (green great → red poor)", th.dim));
        col.addView(hc, hl);

        // ---------- 14 days
        LinearLayout dc = card();
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = Ui.dp(this, 14);
        dc.addView(head(w.days.size() + "-day forecast"));
        double lo = 99, hi = -99;
        for (Weather.Day d : w.days) { lo = Math.min(lo, d.min); hi = Math.max(hi, d.max); }
        for (Weather.Day d : w.days) dc.addView(dayRow(d, lo, hi));
        col.addView(dc, dlp);

        TextView src = small("Forecast & air quality: Open-Meteo.com · updated " +
                android.text.format.DateFormat.format("h:mm a", w.fetched), th.dim);
        src.setGravity(Gravity.START);
        src.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(src);
    }

    // ---------------------------------------------------------------- pieces

    private View dayRow(Weather.Day d, double lo, double hi) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.addView(Ui.text(this, d.name, 13, th.text, th.titleFont));
        name.addView(Ui.text(this, d.date, 10, th.dim, th.bodyFont));
        r.addView(name, new LinearLayout.LayoutParams(Ui.dp(this, 80), -2));
        r.addView(Ui.text(this, Weather.icon(d.code, true), 17, th.text, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 34), -2));
        r.addView(Ui.text(this, Weather.label(d.code), 12, th.dim, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 110), -2));
        TextView mn = Ui.text(this, Weather.deg(d.min), 13, th.dim, "Poppins-Medium");
        mn.setGravity(Gravity.END);
        r.addView(mn, new LinearLayout.LayoutParams(Ui.dp(this, 36), -2));
        // range bar: position of min..max within the fortnight's range, coloured cool -> hot
        LinearLayout track = new LinearLayout(this);
        track.setBackground(Ui.round(0x22FFFFFF, Ui.dp(this, 4)));
        float span = (float) Math.max(1, hi - lo);
        float a = (float) ((d.min - lo) / span), b = (float) ((d.max - lo) / span);
        View pre = new View(this), fill = new View(this), post = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF5AC8FA, 0xFFFFD60A, 0xFFFF6B3D});
        g.setCornerRadius(Ui.dp(this, 4));
        fill.setBackground(g);
        track.setWeightSum(1f);
        track.addView(pre, new LinearLayout.LayoutParams(0, -1, a));
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, Math.max(0.04f, b - a)));
        track.addView(post, new LinearLayout.LayoutParams(0, -1, Math.max(0, 1 - b)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.dp(this, 7), 1);
        tl.leftMargin = Ui.dp(this, 10); tl.rightMargin = Ui.dp(this, 10);
        r.addView(track, tl);
        r.addView(Ui.text(this, Weather.deg(d.max), 13, th.text, "Poppins-Medium"), new LinearLayout.LayoutParams(Ui.dp(this, 36), -2));
        r.addView(Ui.text(this, "☔ " + d.rain + "%" + (d.precip >= 1 ? " · " + Math.round(d.precip) + "mm" : ""), 11,
                d.rain >= 60 ? 0xFF8FD3FF : th.dim, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 110), -2));
        r.addView(Ui.text(this, "💨 " + Math.round(d.gustMax), 11, d.gustMax >= 40 ? 0xFFFFB347 : th.dim, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 60), -2));
        r.addView(Ui.text(this, "UV " + Math.round(d.uvMax), 11, d.uvMax >= 8 ? 0xFFFF9F0A : th.dim, th.bodyFont), new LinearLayout.LayoutParams(Ui.dp(this, 48), -2));
        return r;
    }

    private View alert(Weather.Alert a) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        View bar = new View(this);
        int col = a.level == 2 ? 0xFFFF453A : a.level == 1 ? 0xFFFFB020 : 0xFF4CD964;
        bar.setBackground(Ui.round(col, Ui.dp(this, 2)));
        r.addView(bar, new LinearLayout.LayoutParams(Ui.dp(this, 4), Ui.dp(this, 30)));
        TextView ic = Ui.text(this, a.icon, 17, th.text, th.bodyFont);
        ic.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        r.addView(ic);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, a.title, 13, a.level == 2 ? 0xFFFF8A80 : th.text, th.titleFont));
        TextView d = Ui.text(this, a.detail, 11, th.dim, th.bodyFont);
        d.setPadding(0, Ui.dp(this, 2), 0, 0);
        t.addView(d);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return r;
    }

    private View window(String title, Weather.Window w, boolean avoid) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 12), 0);
        c.addView(Ui.text(this, title, 14, th.text, th.titleFont));
        TextView r = Ui.text(this, avoid ? "Least comfortable" : Weather.rating(w.score) + " conditions", 12,
                avoid ? 0xFFFF8A80 : Weather.ratingColor(w.score), "Poppins-Medium");
        r.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 2));
        c.addView(r);
        c.addView(Ui.text(this, w.why, 11, th.dim, th.bodyFont));
        return c;
    }

    private View stat2(String l1, String v1, String l2, String v2) {
        LinearLayout r = row();
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        r.addView(stat(l1, v1), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(stat(l2, v2), new LinearLayout.LayoutParams(0, -2, 1));
        return r;
    }

    private View stat(String label, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(Ui.text(this, label, 11, th.dim, th.bodyFont));
        c.addView(Ui.text(this, value, 14, th.text, "Poppins-Medium"));
        return c;
    }

    private static String uvWord(double uv) {
        return uv >= 11 ? " · Extreme" : uv >= 8 ? " · Very high" : uv >= 6 ? " · High" : uv >= 3 ? " · Moderate" : " · Low";
    }

    private TextView head(String s) {
        TextView t = Ui.text(this, s, 15, th.text, th.titleFont);
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        return t;
    }

    private TextView small(String s, int color) {
        TextView t = Ui.text(this, s, 10, color, th.bodyFont);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView center(TextView t) { t.setGravity(Gravity.CENTER); return t; }

    private LinearLayout cellCol() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        return c;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(this, th, Ui.dp(this, th.radiusDp));
        c.setBackground(g.d);
        int p = Ui.dp(this, 14);
        c.setPadding(p, p, p, p + g.edge);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        return r;
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(this, 10);
        return lp;
    }

    // ---------------------------------------------------------------- location

    private void autoLocate() {
        Weather.useDeviceLocation(this, new Weather.CityResult() {
            @Override public void done(String name) {
                if (name == null) { Toast.makeText(WeatherActivity.this, "TV location not available - use Change area", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(WeatherActivity.this, "Weather for " + name, Toast.LENGTH_SHORT).show();
                Weather.refresh(WeatherActivity.this, true, listener);
            }
        });
    }

    private void askArea() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint("Area, city, or lat, lon (e.g. Powai Mumbai or 19.12, 72.91)");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Weather area")
                .setView(in)
                .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final String q = in.getText().toString();
                        Toast.makeText(WeatherActivity.this, "Searching...", Toast.LENGTH_SHORT).show();
                        Weather.search(WeatherActivity.this, q, new Weather.PlacesResult() {
                            @Override public void done(java.util.List<Weather.Place> places) { pickPlace(q, places); }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shows every match so the right one can be picked (typed names are often ambiguous). */
    private void pickPlace(String q, final java.util.List<Weather.Place> places) {
        if (isFinishing()) return;
        if (places.isEmpty()) {
            Toast.makeText(this, "No match for \"" + q + "\" - try adding the city, e.g. \"Powai Mumbai\"", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[places.size()];
        for (int i = 0; i < names.length; i++) names[i] = places.get(i).name;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pick your area")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Weather.setPlace(WeatherActivity.this, places.get(which));
                        Toast.makeText(WeatherActivity.this, "Weather for " + places.get(which).name, Toast.LENGTH_SHORT).show();
                        Weather.refresh(WeatherActivity.this, true, listener);
                    }
                })
                .setNeutralButton("Search again", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { askArea(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

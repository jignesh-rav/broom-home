package com.broom.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private final AppCatalog catalog = new AppCatalog();
    private Theme theme;
    private ThemeBackground bg;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView clock, date, greet, status, themeBtn;
    private View firstFocus;
    private String lastFocusedPkg;
    private boolean dirty = true;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!Theme.current(MainActivity.this).id.equals(theme.id)) rebuild(); else updateClock();
            Weather.refresh(MainActivity.this, false, wxListener);
            refreshTicker();
        }
    };
    private final BroadcastReceiver pkgs = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dirty = true; if (hasWindowFocus()) rebuild(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        bg = new ThemeBackground(this);
        bg.festive = true;
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(false);
        scroll.setClipChildren(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        int pad = Ui.dp(this, 40);
        content.setPadding(pad, Ui.dp(this, 18), pad, Ui.dp(this, 40));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgs, pf);
        cityFromIntent(getIntent());
        Sounds.init(this);
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        watchInputs(true);
        if (dirty || theme == null || !Theme.current(this).id.equals(theme.id)) rebuild();
        else { updateClock(); updateStatus(); showWeather(Weather.cached(this)); Weather.refresh(this, false, wxListener); }
        refreshTicker();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(tick); } catch (Exception ignored) { }
        watchInputs(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgs); } catch (Exception ignored) { }
    }

    /** Home pressed while already home: jump back to the top row. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        cityFromIntent(i);
        if (firstFocus != null) { scroll.scrollTo(0, 0); firstFocus.requestFocus(); }
    }

    @Override public void onBackPressed() {
        if (firstFocus != null) { scroll.scrollTo(0, 0); firstFocus.requestFocus(); }
    }

    // ------------------------------------------------------------------ build

    private void rebuild() {
        try {
            if (BroomApp.unstable(this)) throw new IllegalStateException("recent crashes");
            fullRebuild();
        } catch (Throwable t) {
            android.util.Log.e("Broom", "home failed, safe mode", t);
            safeHome();
        }
    }

    /** Minimal home that always works: plain background, every app as a button. */
    private void safeHome() {
        content.removeAllViews();
        focusTiles.clear();
        TextView t = new TextView(this);
        t.setText("Broom Home · safe mode  (something failed to load — your apps are below)");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(18);
        t.setPadding(0, 0, 0, 20);
        content.addView(t);
        try { catalog.load(this); } catch (Throwable ignored) { }
        LinearLayout row = null;
        int i = 0;
        for (final AppCatalog.App a : catalog.apps.values()) {
            if (i++ % 6 == 0) { row = new LinearLayout(this); content.addView(row); }
            android.widget.Button b = new android.widget.Button(this);
            b.setText(a.label);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { launch(a.pkg); } });
            row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        android.widget.Button s = new android.widget.Button(this);
        s.setText("TV Settings");
        s.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { safeStart(new Intent(Settings.ACTION_SETTINGS)); } });
        content.addView(s);
        if (content.getChildCount() > 1) content.getChildAt(1).requestFocus();
    }

    private void fullRebuild() {
        dirty = false;
        Sounds.quiet(600);
        theme = Theme.current(this);
        bg.setTheme(theme);
        catalog.load(this);
        content.removeAllViews();
        focusTiles.clear();
        firstFocus = null;
        View restore = null;

        content.addView(header());
        if (Features.on(this, Features.MARKETS) || Features.on(this, Features.CRICKET) || Features.on(this, Features.FOOTBALL))
            content.addView(tickerStrip());
        else { ticker = null; scoreLine = null; }

        List<String> favs = catalog.favourites(this);
        List<View> favCards = new ArrayList<>();
        for (int i = 0; i < favs.size(); i++) {
            AppCatalog.App a = catalog.apps.get(favs.get(i));
            smallTiles = true;
            View card = appCard(a, true, i, favs);
            smallTiles = false;
            favCards.add(card);
        }
        if (!favCards.isEmpty()) addGrid("Favourites", favCards, 9);
        List<View> recent = recentCards(favs);
        if (!recent.isEmpty()) addGrid("Recently used", recent, 9);
        wxCard = new LinearLayout(this);
        wxCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = Ui.dp(this, 6);
        content.addView(wxCard, wl);
        showWeather(Weather.cached(this));

        List<View> inputs = inputCards();
        if (!inputs.isEmpty()) addRow("Inputs", inputs);

        LinkedHashMap<String, List<AppCatalog.App>> cats = catalog.byCategory();
        for (Map.Entry<String, List<AppCatalog.App>> e : cats.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<View> cards = new ArrayList<>();
            for (AppCatalog.App a : e.getValue()) cards.add(appCard(a, false, -1, favs));
            addRow(e.getKey() + "  ·  " + e.getValue().size(), cards);
        }

        Weather.refresh(this, false, wxListener);
        if (lastFocusedPkg != null) restore = content.findViewWithTag(lastFocusedPkg);
        final View target = restore != null ? restore : firstFocus;
        if (target != null) target.post(new Runnable() { @Override public void run() { target.requestFocus(); } });
        updateClock();
        updateStatus();
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);

        h.setGravity(Gravity.CENTER_VERTICAL);
        clock = Ui.text(this, "", 92, theme.text, "Poppins-SemiBold");
        clock.setShadowLayer(10, 0, 5, 0xA6000000);
        h.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        View bar = new View(this);
        bar.setBackground(Ui.round(theme.accent, Ui.dp(this, 2)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 3), Ui.dp(this, 70));
        bl.leftMargin = Ui.dp(this, 22); bl.rightMargin = Ui.dp(this, 18);
        h.addView(bar, bl);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greet = Ui.text(this, "", 22, theme.text, theme.titleFont);
        greet.setShadowLayer(8, 0, 1, theme.textShadow());
        greet.setSingleLine(true);
        date = Ui.text(this, "", 15, theme.dim, theme.bodyFont);
        date.setShadowLayer(8, 0, 1, theme.textShadow());
        date.setPadding(0, Ui.dp(this, 8), 0, 0);
        left.addView(greet);
        left.addView(date);
        panchang = Ui.text(this, "", 12, theme.text, "Poppins-Medium");
        panchang.setMaxLines(2);                      // festival days may need a second line
        panchang.setEllipsize(android.text.TextUtils.TruncateAt.END);
        panchang.setIncludeFontPadding(true);      // room for tall Gujarati / Devanagari letters
        panchang.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 2));
        panchang.setShadowLayer(8, 0, 1, theme.textShadow());
        if (Features.on(this, Features.PANCHANG)) left.addView(panchang, new LinearLayout.LayoutParams(-1, -2));
        else panchang = null;
        h.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        wxNow = new LinearLayout(this);
        wxNow.setOrientation(LinearLayout.VERTICAL);
        wxNow.setGravity(Gravity.END);
        right.addView(wxNow);
        status = Ui.text(this, "", 11, theme.dim, theme.bodyFont);
        status.setShadowLayer(8, 0, 1, theme.textShadow());
        status.setGravity(Gravity.END);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        right.addView(status);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);
        btns.setClipChildren(false);
        themeBtn = Ui.pill(this, "Theme: " + Theme.label(this), theme, new View.OnClickListener() {
            @Override public void onClick(View v) {
                lastFocusedPkg = "btn:theme";
                dirty = true;
                safeStart(new Intent(MainActivity.this, ThemeGalleryActivity.class));
            }
        });
        themeBtn.setTag("btn:theme");
        btns.addView(themeBtn);
        if (Features.anyBriefing(this)) addBtn(btns, "Briefing", "btn:brief", new Intent(this, BriefingActivity.class));
        if (Features.on(this, Features.PANCHANG)) addBtn(btns, "Panchang", "btn:panchang", new Intent(this, PanchangActivity.class));
        TextView cityBtn = Ui.pill(this, "Weather", theme, new View.OnClickListener() {
            @Override public void onClick(View v) { lastFocusedPkg = "btn:weather"; safeStart(new Intent(MainActivity.this, WeatherActivity.class)); }
        });
        cityBtn.setTag("btn:weather");
        LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-2, -2);
        cl2.leftMargin = Ui.dp(this, 10);
        btns.addView(cityBtn, cl2);
        addBtn(btns, "Screensaver", "btn:dream", new Intent(this, DreamSettingsActivity.class));
        TextView qs = Ui.pill(this, "Settings", theme, new View.OnClickListener() {
            @Override public void onClick(View v) { lastFocusedPkg = "btn:settings"; quickSettings(); }
        });
        qs.setTag("btn:settings");
        LinearLayout.LayoutParams qsl = new LinearLayout.LayoutParams(-2, -2);
        qsl.leftMargin = Ui.dp(this, 10);
        btns.addView(qs, qsl);
        pendingButtons = btns;
        h.addView(right, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 6);
        h.setLayoutParams(lp);
        h.setClipChildren(false);
        return h;
    }

    private String powerLabel() {
        return KeyRemapService.powerOnHome(this) ? "Power-on: Broom (or HDMI device that's on)" : "Power-on: Last input";
    }

    private void addBtn(LinearLayout parent, String label, String tag, final Intent i) {
        TextView b = Ui.pill(this, label, theme, new View.OnClickListener() {
            @Override public void onClick(View v) { lastFocusedPkg = String.valueOf(v.getTag()); safeStart(i); }
        });
        b.setTag(tag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(this, 10);
        parent.addView(b, lp);
    }

    private void addRow(String title, List<View> cards) {
        TextView t = Ui.text(this, title, 15, theme.text, theme.titleFont);
        t.setShadowLayer(8, 0, 1, theme.textShadow());
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, Ui.dp(this, 4));
        content.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setSmoothScrollingEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setFocusable(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 40), 0);
        for (View c : cards) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 12);
            row.addView(c, lp);
            if (firstFocus == null) firstFocus = focusTiles.get(c);
        }
        hs.addView(row);
        content.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Wrapping grid (cols per line) for the favourites. D-pad moves naturally between lines. */
    private void addGrid(String title, List<View> cards, int cols) {
        TextView t = Ui.text(this, title, 15, theme.text, theme.titleFont);
        t.setShadowLayer(8, 0, 1, theme.textShadow());
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, Ui.dp(this, 2));
        if (pendingButtons != null) {
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.BOTTOM);
            tr.setClipChildren(false);
            tr.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            pendingButtons.setPadding(0, 0, 0, Ui.dp(this, 4));
            tr.addView(pendingButtons, new LinearLayout.LayoutParams(-2, -2));
            content.addView(tr, new LinearLayout.LayoutParams(-1, -2));
            pendingButtons = null;
        } else content.addView(t);
        LinearLayout line = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % cols == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setClipChildren(false);
                line.setClipToPadding(false);
                line.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), 0, 0);
                content.addView(line, new LinearLayout.LayoutParams(-1, -2));
            }
            View c = cards.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 12);
            line.addView(c, lp);
            if (firstFocus == null) firstFocus = focusTiles.get(c);
        }
    }

    /** card = vertical [tile, label]. The tile is the focusable part and carries tag pkg. */
    private boolean smallTiles = false;
    private LinearLayout pendingButtons;

    /** Set before calling tile() to give the next tile a status line (e.g. HDMI device name). */
    private String nextSub;
    private int nextStatus;   // 0 = none

    private View tile(String key, String label, Drawable banner, Drawable icon, final Runnable onClick,
                      final Runnable onMenu) {
        final String sub = nextSub;
        final int status = nextStatus;
        nextSub = null;
        nextStatus = 0;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setClipChildren(false);

        final FrameLayout t = new FrameLayout(this);
        int w = Ui.dp(this, smallTiles ? 84 : 128), h = Ui.dp(this, smallTiles ? 47 : 72);
        final Ui.Glass glass = Ui.glass(this, theme, Ui.dp(this, theme.radiusDp));
        t.setBackground(glass.d);
        Ui.glassOutline(t, Ui.dp(this, theme.radiusDp), glass.edge);
        t.setClipToOutline(true);
        t.setAlpha(0.86f);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSoundEffectsEnabled(false);
        t.setTag(key);
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            if (icon != null && sub == null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                int s = Ui.dp(this, smallTiles ? 22 : 30);
                inner.addView(iv, new LinearLayout.LayoutParams(s, s));
            }
            TextView l = Ui.text(this, label, sub != null ? 10 : 10, sub != null ? theme.dim : theme.text, theme.bodyFont);
            l.setSingleLine(true);
            l.setGravity(Gravity.CENTER);
            l.setPadding(Ui.dp(this, 6), Ui.dp(this, sub != null ? 3 : 6), Ui.dp(this, 6), 0);
            inner.addView(l);
            if (sub != null) {
                String[] lines = sub.split("\n");
                for (int k = 0; k < lines.length; k++) {
                    TextView s2 = Ui.text(this, k == 1 ? "●  " + lines[k] : lines[k], k == 0 ? 11 : 10,
                            k == 0 ? theme.text : (status != 0 ? Ui.lighten(status, 0.3f) : theme.dim), k == 0 ? "Poppins-Medium" : "Poppins-SemiBold");
                    s2.setSingleLine(true);
                    s2.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    s2.setGravity(Gravity.CENTER);
                    s2.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), 0);
                    inner.addView(s2);
                }
            }
            t.addView(inner, new FrameLayout.LayoutParams(-1, -1));
            if (status != 0) {
                glass.status(this, status, theme);
                t.setAlpha(1f);
                View dot = new View(this);
                dot.setBackground(Ui.round(status, Ui.dp(this, 5)));
                FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.dp(this, 9), Ui.dp(this, 9), Gravity.TOP | Gravity.END);
                dl.setMargins(0, Ui.dp(this, 7), Ui.dp(this, 9), 0);
                t.addView(dot, dl);
            }
        }
        wrap.addView(t, new LinearLayout.LayoutParams(w, h));

        final TextView cap = Ui.text(this, label, 12, theme.text, "Poppins-Medium");
        cap.setShadowLayer(8, 0, 1, theme.textShadow());
        cap.setSingleLine(true);
        cap.setAlpha(0f);
        cap.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, 0);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(w, -2);
        wrap.addView(cap, cl);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                glass.focus(MainActivity.this, f, theme);
                if (android.os.Build.VERSION.SDK_INT >= 28) { t.setOutlineSpotShadowColor(theme.focus); t.setOutlineAmbientShadowColor(theme.focus); }
                t.animate().scaleX(f ? 1.17f : 1f).scaleY(f ? 1.17f : 1f).translationZ(f ? 24 : 0).alpha(f ? 1f : 0.86f).setDuration(110).start();
                if (f) Sounds.tick(MainActivity.this);
                if (t.getChildCount() > 0) t.getChildAt(0).animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f).setDuration(160).start();
                cap.animate().alpha(f ? 1f : 0f).setDuration(90).start();
                if (f) lastFocusedPkg = String.valueOf(v.getTag());
            }
        });
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Sounds.click(MainActivity.this); onClick.run(); } });
        if (onMenu != null) {
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { onMenu.run(); return true; }
            });
            t.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View v, int code, KeyEvent e) {
                    if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { onMenu.run(); return true; }
                    return false;
                }
            });
        }
        t.setContentDescription(label);
        focusTiles.put(wrap, t);
        return wrap;
    }

    /** wrapper -> its focusable tile */
    private final java.util.HashMap<View, View> focusTiles = new java.util.HashMap<>();

    private View appCard(final AppCatalog.App a, final boolean fav, final int favIndex, final List<String> favs) {
        // Same app can appear in Favourites and its category row; only the favourite copy keeps the plain tag
        String key = fav ? a.pkg : "cat:" + a.pkg;
        return tile(key, a.label, a.banner, a.icon,
                new Runnable() { @Override public void run() { launch(a.pkg); } },
                new Runnable() { @Override public void run() { appMenu(a, fav, favIndex, favs); } });
    }

    private void launch(String pkg) {
        Intent i = catalog.launchIntent(this, pkg);
        if (i == null) { Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show(); return; }
        safeStart(i);
    }

    private void safeStart(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show(); }
    }

    private void appMenu(final AppCatalog.App a, final boolean fav, final int idx, final List<String> favs) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        labels.add("Open"); acts.add(new Runnable() { public void run() { launch(a.pkg); } });
        if (fav) {
            if (idx > 0) { labels.add("Move left"); acts.add(new Runnable() { public void run() { move(favs, idx, -1); } }); }
            if (idx < favs.size() - 1) { labels.add("Move right"); acts.add(new Runnable() { public void run() { move(favs, idx, 1); } }); }
            labels.add("Remove from Favourites"); acts.add(new Runnable() { public void run() {
                List<String> l = new ArrayList<>(favs); l.remove(a.pkg); AppCatalog.saveFavourites(MainActivity.this, l); rebuild(); } });
        } else if (!favs.contains(a.pkg)) {
            labels.add("Add to Favourites"); acts.add(new Runnable() { public void run() {
                List<String> l = new ArrayList<>(favs); l.add(a.pkg); AppCatalog.saveFavourites(MainActivity.this, l); rebuild(); } });
        }
        labels.add("App info"); acts.add(new Runnable() { public void run() {
            safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); } });
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { acts.get(w).run(); }
                }).show();
    }

    private void move(List<String> favs, int idx, int dir) {
        List<String> l = new ArrayList<>(favs);
        String p = l.remove(idx);
        l.add(idx + dir, p);
        AppCatalog.saveFavourites(this, l);
        lastFocusedPkg = p;
        rebuild();
    }

    // ------------------------------------------------------------------ weather

    private LinearLayout wxNow, wxCard;
    private final Weather.Listener wxListener = new Weather.Listener() {
        @Override public void onWeather(Weather w) { showWeather(w); }
    };

    private void autoLocate() {
        Weather.useDeviceLocation(this, new Weather.CityResult() {
            @Override public void done(String name) {
                if (name == null) { Toast.makeText(MainActivity.this, "TV location not available - type your area instead", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(MainActivity.this, "Weather for " + name, Toast.LENGTH_SHORT).show();
                Weather.refresh(MainActivity.this, true, wxListener);
            }
        });
    }

    private void cityFromIntent(Intent i) {
        if (i != null && i.hasExtra("fest")) {
            String f = i.getStringExtra("fest");
            Festive.override = (f == null || f.equals("auto")) ? null : f;
            i.removeExtra("fest");
            if (content != null && theme != null) rebuild();
        }
        if (i != null && i.getStringExtra("theme") != null) {
            Theme.setChoice(this, i.getStringExtra("theme"));
            i.removeExtra("theme");
            if (content != null && theme != null) rebuild();
        }
        if (i != null && i.getBooleanExtra("autoloc", false)) { i.removeExtra("autoloc"); autoLocate(); return; }
        final String city = i == null ? null : i.getStringExtra("city");
        if (city == null || city.trim().isEmpty()) return;
        i.removeExtra("city");
        applyCity(city);
    }

    private void applyCity(String city) {
        Weather.setCity(this, city, new Weather.CityResult() {
            @Override public void done(String name) {
                if (name == null) { Toast.makeText(MainActivity.this, "City not found - try a nearby bigger city", Toast.LENGTH_LONG).show(); return; }
                Toast.makeText(MainActivity.this, "Weather for " + name, Toast.LENGTH_SHORT).show();
                Weather.refresh(MainActivity.this, true, wxListener);
            }
        });
    }

    private void showWeather(Weather w) {
        if (wxNow == null || wxCard == null) return;
        wxNow.removeAllViews();
        wxCard.removeAllViews();
        if (w == null) {
            TextView t = Ui.text(this, Weather.hasCity(this) ? "Loading weather..." : "Finding your location for weather...", 12, theme.dim, theme.bodyFont);
            t.setGravity(Gravity.END);
            wxNow.addView(t);
            return;
        }
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        top.addView(Ui.text(this, Weather.icon(w.code, w.isDay), 30, theme.text, theme.bodyFont));
        top.addView(Ui.text(this, " " + Weather.deg(w.temp), 40, theme.text, "Poppins-Light"));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Ui.dp(this, 10), 0, 0, 0);
        side.addView(Ui.text(this, Weather.label(w.code), 14, theme.text, theme.titleFont));
        side.addView(Ui.text(this, w.city, 11, theme.dim, theme.bodyFont));
        top.addView(side);
        wxNow.addView(top);
        TextView det = Ui.text(this, "Feels " + Weather.deg(w.feels) + "   \u00b7   Humidity " + w.humidity + "%   \u00b7   Wind "
                + Math.round(w.wind) + " km/h", 12, theme.dim, theme.bodyFont);
        det.setGravity(Gravity.END);
        det.setPadding(0, Ui.dp(this, 4), 0, 0);
        wxNow.addView(det);
        String hl = null; int hc = 0xFF4CD964;
        for (Weather.Alert a : w.alerts()) if (a.level > 0) { hl = a.icon + "  " + a.title; hc = a.level == 2 ? (theme.light ? 0xFFC62828 : 0xFFFF8A80) : (theme.light ? 0xFFB26A00 : 0xFFFFC46B); break; }
        if (hl == null) {
            Weather.Window bw = w.best(0, false);
            if (bw == null && w.days.size() > 1) bw = w.best(1, false);
            if (bw != null) {
                hl = bw.score < 35 ? "🏃  Poor day for a walk" : "🏃  Best walk: " + bw.day + " " + bw.from + " – " + bw.to;
                hc = Weather.ratingColor(bw.score);
            }
        }
        if (hl != null) {
            TextView h = Ui.text(this, hl, 12, hc, "Poppins-Medium");
            h.setGravity(Gravity.END);
            h.setPadding(0, Ui.dp(this, 3), 0, 0);
            wxNow.addView(h);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(Ui.glass(this, theme, Ui.dp(this, theme.radiusDp)).d);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout hourly = new LinearLayout(this);
        hourly.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Math.min(8, w.hours.size()); i++) {
            Weather.Hour h = w.hours.get(i);
            hourly.addView(cell(h.time, Weather.icon(h.code, h.day), Weather.deg(h.temp), h.rain >= 20 ? h.rain + "%" : " "),
                    new LinearLayout.LayoutParams(Ui.dp(this, 44), -2));
        }
        card.addView(hourly);
        View div = new View(this);
        div.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 1), -1);
        dl.leftMargin = Ui.dp(this, 10); dl.rightMargin = Ui.dp(this, 10);
        card.addView(div, dl);
        LinearLayout daily = new LinearLayout(this);
        daily.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Math.min(7, w.days.size()); i++) {
            Weather.Day d = w.days.get(i);
            daily.addView(cell(d.name, Weather.icon(d.code, true), Weather.deg(d.max) + " / " + Weather.deg(d.min),
                    d.rain >= 20 ? d.rain + "%" : " "), new LinearLayout.LayoutParams(Ui.dp(this, 60), -2));
        }
        card.addView(daily);
        final Ui.Glass wg = Ui.glass(this, theme, Ui.dp(this, theme.radiusDp));
        card.setBackground(wg.d);
        card.setFocusable(true);
        card.setClickable(true);
        card.setSoundEffectsEnabled(false);
        card.setTag("wxcard");
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.click(MainActivity.this); lastFocusedPkg = "wxcard"; safeStart(new Intent(MainActivity.this, WeatherActivity.class)); }
        });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { wg.focus(MainActivity.this, f, theme); if (f) Sounds.tick(MainActivity.this); }
        });
        wxCard.addView(card, new LinearLayout.LayoutParams(-2, -2));
    }

    private View cell(String top, String icon, String temp, String rain) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView a = Ui.text(this, top, 10, theme.dim, theme.bodyFont);
        TextView b = Ui.text(this, icon, 16, theme.text, theme.bodyFont);
        b.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        TextView t = Ui.text(this, temp, 11, theme.text, "Poppins-Medium");
        TextView r = Ui.text(this, rain, 9, 0xFF8FD3FF, theme.bodyFont);
        for (TextView v : new TextView[]{a, b, t, r}) { v.setGravity(Gravity.CENTER); c.addView(v); }
        return c;
    }

    // ------------------------------------------------------------------ recently used + quick settings

    /** Last-used apps from Android's usage history (needs usage access, granted over ADB). */
    private List<View> recentCards(List<String> favs) {
        List<View> out = new ArrayList<>();
        try {
            android.app.usage.UsageStatsManager um = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<android.app.usage.UsageStats> st = um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, now - 14L * 86400000L, now);
            if (st == null) return out;
            java.util.Collections.sort(st, new java.util.Comparator<android.app.usage.UsageStats>() {
                @Override public int compare(android.app.usage.UsageStats a, android.app.usage.UsageStats b) {
                    return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
                }
            });
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (android.app.usage.UsageStats u : st) {
                String p = u.getPackageName();
                if (seen.contains(p) || u.getTotalTimeInForeground() < 60000) continue;
                seen.add(p);
                AppCatalog.App a = catalog.apps.get(p);
                if (a == null || p.equals("com.android.tv.settings") || p.equals("com.android.vending")
                        || p.equals("com.mitv.tvhome.atv") || p.equals("com.google.android.tvlauncher")) continue;
                smallTiles = true;
                View v = tile("recent:" + p, a.label, a.banner, a.icon, launchRunnable(p), null);
                smallTiles = false;
                out.add(v);
                if (out.size() >= 6) break;
            }
        } catch (Exception ignored) { }
        return out;
    }

    private Runnable launchRunnable(final String pkg) {
        return new Runnable() { @Override public void run() { launch(pkg); } };
    }

    private TextView ticker;

    private void refreshTicker() {
        if (Briefing.stale(this, "markets", Briefing.marketsMaxAge()) || Briefing.stale(this, "scores", 5 * 60000))
            Briefing.refresh(this, false, new Briefing.Done() { @Override public void done() { showTicker(); } });
    }

    private TextView panchang;
    private int panchangMin = -1;

    /** "કારતક સુદ ૬ · Kartak Shukla Shashthi · VS 2083 · Shravana  ·  🪔 festival" (recomputed every 10 min). */
    private void showPanchang() {
        if (panchang == null) return;
        int m = Calendar.getInstance().get(Calendar.MINUTE);
        if (m == panchangMin && panchang.getText().length() > 0) return;
        panchangMin = m;
        try {
            Panchang p = Panchang.now();
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
            b.append(p.gujarati());
            b.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFD166), 0, b.length(), 0);
            String tn = (p.tithiIdx == 14 || p.tithiIdx == 29) ? p.tithiName() : (p.shukla() ? "Shukla " : "Krishna ") + p.tithiName();
            b.append("  ·  ").append(tn);
            if (p.festival != null) {
                b.append("  ·  ");
                int st = b.length();
                b.append(p.festival);
                b.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFB0C8), st, b.length(), 0);
            }
            // current Choghadiya (and Rahu Kaal when it is on)
            long now = System.currentTimeMillis();
            Vedic.Day vd = Vedic.today(this);
            Vedic.Slot cs = Vedic.current(vd, now);
            if (cs != null) {
                b.append("  ·  ");
                int st = b.length();
                b.append(cs.name).append(" till ").append(Vedic.hm(cs.to));
                b.setSpan(new android.text.style.ForegroundColorSpan(cs.quality > 0 ? 0xFF4CD964 : cs.quality == 0 ? 0xFFFFD166 : 0xFFFF6B6B), st, b.length(), 0);
            }
            if (now >= vd.rahu.from && now < vd.rahu.to) {
                b.append("  ·  ");
                int st = b.length();
                b.append("Rahu Kaal till ").append(Vedic.hm(vd.rahu.to));
                b.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF6B6B), st, b.length(), 0);
            }
            panchang.setText(b);
        } catch (Exception e) { panchang.setText(""); }
    }

    /** Full-width glass strip: live India/IPL score + markets. Select it to open Briefing. */
    private TextView scoreLine;

    private View tickerStrip() {
        final LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        final Ui.Glass g = Ui.glass(this, theme, Ui.dp(this, 14));
        strip.setBackground(g.d);
        strip.setFocusable(true);
        strip.setClickable(true);
        strip.setSoundEffectsEnabled(false);
        strip.setTag("ticker");
        strip.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6) + g.edge);
        // line 1 (only during a live India / IPL match): the score, highlighted
        scoreLine = Ui.text(this, "", 15, 0xFFFFFFFF, "Poppins-SemiBold");
        scoreLine.setSingleLine(true);
        scoreLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scoreLine.setVisibility(View.GONE);
        strip.addView(scoreLine, new LinearLayout.LayoutParams(-1, -2));
        // line 2: markets, auto-shrinking to fit
        ticker = Ui.text(this, "Loading markets…", 13, theme.text, "Poppins-Medium");
        ticker.setMaxLines(1);
        if (android.os.Build.VERSION.SDK_INT >= 26)
            ticker.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        strip.addView(ticker, new LinearLayout.LayoutParams(-1, Ui.dp(this, 20)));
        strip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { g.focus(MainActivity.this, f, theme); if (f) Sounds.tick(MainActivity.this); }
        });
        strip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sounds.click(MainActivity.this); lastFocusedPkg = "ticker"; safeStart(new Intent(MainActivity.this, BriefingActivity.class)); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 4);
        strip.setLayoutParams(lp);
        showTicker();
        return strip;
    }

    private static String shortName(String n) {
        switch (n) {
            case "Nifty 50": return "Nifty";
            case "S&P 500": return "S&P";
            case "USD / INR": return "$/₹";
            case "Gold ($/oz)": return "Gold";
            case "Nasdaq": return "Nasdaq";
            default: return n;
        }
    }

    /** "● IND 245/3*  ·  Nifty 24,350 ▲0.4%  ·  Sensex …" with green/red changes. */
    private void showTicker() {
        if (ticker == null) return;
        // live India / IPL score on its own highlighted line
        String live = null;
        for (Briefing.Score sc : Briefing.scores(this)) if (sc.india && sc.live) { live = sc.text; break; }
        if (live == null) for (Briefing.Match m : Briefing.matches(this)) if ("in".equals(m.state) && m.big) { live = "⚽ " + Briefing.line(m); break; }
        if (scoreLine != null) {
            if (live != null) {
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("● LIVE   ");
                sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF4D4D), 0, 6, 0);
                sb.append(live);
                scoreLine.setText(sb);
                scoreLine.setVisibility(View.VISIBLE);
            } else scoreLine.setVisibility(View.GONE);
        }
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        for (Briefing.Quote q : Briefing.quotes(this, Briefing.TICKER)) {
            if (b.length() > 0) b.append("   ·   ");
            b.append(shortName(q.name)).append(" ").append(Briefing.price(q)).append(" ");
            int st = b.length();
            b.append(String.format(java.util.Locale.US, "%s%.1f%%", q.pct >= 0 ? "▲" : "▼", Math.abs(q.pct)));
            b.setSpan(new android.text.style.ForegroundColorSpan(q.pct >= 0 ? 0xFF4CD964 : 0xFFFF6B6B), st, b.length(), 0);
        }
        if (b.length() == 0) b.append(Features.on(this, Features.MARKETS) ? "Markets & live scores — select to open Briefing"
                : "Live scores & headlines — select to open Briefing");
        ticker.setText(b);
    }

    private String sleepLabel() {
        long at = Theme.prefs(this).getLong("sleep_at", 0);
        long left = at - System.currentTimeMillis();
        return left > 0 ? "Sleep timer: off in " + (left / 60000 + 1) + " min  (change)" : "Sleep timer";
    }

    private void sleepDialog() {
        final int[] mins = {0, 15, 30, 45, 60, 90, 120};
        String[] labels = {"Off", "15 minutes", "30 minutes", "45 minutes", "1 hour", "1½ hours", "2 hours"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Turn the TV off in…")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (mins[w] == 0) Theme.prefs(MainActivity.this).edit().remove("sleep_at").apply();
                        else Theme.prefs(MainActivity.this).edit().putLong("sleep_at", System.currentTimeMillis() + mins[w] * 60000L).apply();
                        Toast.makeText(MainActivity.this, mins[w] == 0 ? "Sleep timer off" : "TV will turn off in " + mins[w] + " min (1-minute warning first)", Toast.LENGTH_LONG).show();
                        updateStatus();
                    }
                }).show();
    }

    /** Quick settings → Pop-up updates: interval per type, quiet hours and a test run. */
    private void pulseDialog() {
        final int n = Pulse.KINDS.length;
        String[] labels = new String[n + 2];
        for (int i = 0; i < n; i++) labels[i] = Pulse.LABELS[i] + ": " + Pulse.describe(this, Pulse.KINDS[i]);
        labels[n] = "Quiet hours 11 PM – 7 AM: " + (Pulse.quietHours(this) ? "On  (turn off)" : "Off  (turn on)");
        labels[n + 1] = "Test all now (shows each type once)";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pop-up updates (over any app or HDMI)")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int i) {
                        if (i < n) { pulseInterval(Pulse.KINDS[i], Pulse.LABELS[i]); return; }
                        if (i == n) {
                            Pulse.setQuietHours(MainActivity.this, !Pulse.quietHours(MainActivity.this));
                            pulseDialog();
                            return;
                        }
                        Toast.makeText(MainActivity.this, Pulse.testAll()
                                ? "Showing each pop-up in turn (bottom-right, about 10 s apart)"
                                : "Pop-ups need the Broom button helper: re-run the installer", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void pulseInterval(final String kind, String label) {
        final int[] mins;
        String[] names;
        if (kind.equals(Pulse.PANCHANG)) { mins = new int[]{0, 1}; names = new String[]{"Off", "On (Rahu Kaal soon, good Choghadiya starts)"}; }
        else if (kind.equals(Pulse.WELLNESS)) { mins = new int[]{0, 60, 90, 120}; names = new String[]{"Off", "After 1 hour", "After 1½ hours", "After 2 hours"}; }
        else { mins = new int[]{0, 15, 30, 60}; names = new String[]{"Off", "Every 15 min", "Every 30 min", "Every hour"}; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Pulse.setMinutes(MainActivity.this, kind, mins[w]);
                        pulseDialog();
                    }
                }).show();
    }

    private void quickSettings() {
        final boolean snd = Sounds.enabled(this);
        final String[] labels = {"Picture & TV options", "Sound", "Display", "Wi-Fi & network", "Apps",
                "Themes", "Weather", "Screensaver", "Broom sounds: " + (snd ? "On  (turn off)" : "Off  (turn on)"), "All settings",
                "TV stats & savings", powerLabel() + "  (switch)", "Briefing",
                "Live score pop-ups: " + (ScorePopup.enabled(this) ? "On  (turn off)" : "Off  (turn on)"), "Watchlist & market alerts",
                "Network monitor", sleepLabel(), "Features: choose what to show", "Pop-up updates (market, cricket, weather, news…)"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Quick settings")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int i) {
                        Intent in = null;
                        switch (i) {
                            case 0: in = new Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.MainSettings"); break;
                            case 1: in = new Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.SoundActivity"); break;
                            case 2: in = new Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.display.DisplayActivity"); break;
                            case 3: in = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
                            case 4: in = new Intent(Settings.ACTION_APPLICATION_SETTINGS); break;
                            case 5: dirty = true; in = new Intent(MainActivity.this, ThemeGalleryActivity.class); break;
                            case 6: in = new Intent(MainActivity.this, WeatherActivity.class); break;
                            case 7: in = new Intent(MainActivity.this, DreamSettingsActivity.class); break;
                            case 8:
                                Theme.prefs(MainActivity.this).edit().putBoolean("sounds", !snd).apply();
                                Toast.makeText(MainActivity.this, "Broom sounds " + (snd ? "off" : "on"), Toast.LENGTH_SHORT).show();
                                Sounds.click(MainActivity.this);
                                return;
                            case 10: in = new Intent(MainActivity.this, StatsActivity.class); break;
                            case 11: {
                                boolean now = !KeyRemapService.powerOnHome(MainActivity.this);
                                Theme.prefs(MainActivity.this).edit().putBoolean("power_on_home", now).apply();
                                Toast.makeText(MainActivity.this, now ? "TV will open Broom Home when switched on, or the HDMI device (e.g. Fire TV) if it is on"
                                        : "TV will open the last input (e.g. Fire TV on HDMI) when switched on", Toast.LENGTH_LONG).show();
                                return;
                            }
                            case 12: in = new Intent(MainActivity.this, BriefingActivity.class); break;
                            case 14: in = new Intent(MainActivity.this, WatchlistActivity.class); break;
                            case 15: in = new Intent(MainActivity.this, NetworkActivity.class); break;
                            case 16: sleepDialog(); return;
                            case 17:
                                Features.dialog(MainActivity.this, new Runnable() {
                                    @Override public void run() { lastFocusedPkg = "btn:settings"; rebuild(); refreshTicker(); }
                                });
                                return;
                            case 18: pulseDialog(); return;
                            case 13: {
                                boolean on = !ScorePopup.enabled(MainActivity.this);
                                Theme.prefs(MainActivity.this).edit().putBoolean("score_popups", on).apply();
                                Toast.makeText(MainActivity.this, on ? "Live score pop-ups on (India & IPL matches)" : "Live score pop-ups off", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            default: in = new Intent(Settings.ACTION_SETTINGS);
                        }
                        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        safeStart(in);
                    }
                }).show();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Home hidden (an app is playing): free the wallpaper bitmap; it is redrawn on return.
        if (level >= TRIM_MEMORY_UI_HIDDEN) Nebula.release();
    }

    // ------------------------------------------------------------------ inputs

    private final java.util.HashSet<String> activeInputs = new java.util.HashSet<>();
    private TvInputManager.TvInputCallback inputCb;

    /** Rebuild when an HDMI device is plugged in / switched on or off. */
    private void watchInputs(boolean on) {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return;
        if (on && inputCb == null) {
            inputCb = new TvInputManager.TvInputCallback() {
                @Override public void onInputStateChanged(String id, int state) { inputsChanged(); }
                @Override public void onInputAdded(String id) { inputsChanged(); }
                @Override public void onInputRemoved(String id) { inputsChanged(); }
            };
            tim.registerCallback(inputCb, new android.os.Handler());
        } else if (!on && inputCb != null) {
            tim.unregisterCallback(inputCb);
            inputCb = null;
        }
    }

    private final Runnable rebuildInputs = new Runnable() { @Override public void run() { rebuild(); } };

    private void inputsChanged() {
        if (content == null) return;
        content.removeCallbacks(rebuildInputs);
        content.postDelayed(rebuildInputs, 800);
    }

    private List<View> inputCards() {
        activeInputs.clear();
        List<View> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            List<TvInputInfo> list = new ArrayList<>(tim.getTvInputList());
            final Context ctx = this;
            java.util.Collections.sort(list, new java.util.Comparator<TvInputInfo>() {
                @Override public int compare(TvInputInfo a, TvInputInfo b) {
                    int ka = a.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1, kb = b.getType() == TvInputInfo.TYPE_HDMI ? 0 : 1;
                    if (ka != kb) return ka - kb;
                    return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
                }
            });
            // CEC devices appear as child inputs of their HDMI port (e.g. "Amazon FireTV" under HDMI 3)
            java.util.HashMap<String, TvInputInfo> child = new java.util.HashMap<>();
            for (TvInputInfo in : list) if (in.getParentId() != null) child.put(in.getParentId(), in);
            for (final TvInputInfo in : list) {
                int type = in.getType();
                if (type == TvInputInfo.TYPE_TUNER || in.getParentId() != null) continue;
                // status: droidlogic reports 0 (connected) when a cable/device is present on the port
                int st = 2;
                try { st = tim.getInputState(in.getId()); } catch (Exception ignored) { }
                TvInputInfo dev = child.get(in.getId());
                if (st == TvInputManager.INPUT_STATE_CONNECTED || dev != null) {
                    String name = dev != null ? String.valueOf(dev.loadLabel(this)) : "Device connected";
                    int dst = st;
                    if (dev != null) try { dst = tim.getInputState(dev.getId()); } catch (Exception ignored) { }
                    boolean on = dev == null ? st == TvInputManager.INPUT_STATE_CONNECTED : dst == TvInputManager.INPUT_STATE_CONNECTED;
                    nextSub = name + "\n" + (dev != null ? (on ? "On" : "Standby") : "Connected");
                    nextStatus = on ? 0xFF34C759 : 0xFFFFB020;
                    activeInputs.add(in.getId());
                }
                if (!in.isPassthroughInput()) continue;
                if (in.isHidden(this)) continue;
                CharSequence cl = in.loadCustomLabel(this);
                final String label = String.valueOf(cl != null ? cl : in.loadLabel(this));
                Drawable icon = null;
                try { icon = in.loadIcon(this); } catch (Exception ignored) { }
                if (icon != null) { icon = icon.mutate(); icon.setTint(theme.text); }
                out.add(tile("input:" + in.getId(), label, null, icon, new Runnable() {
                    @Override public void run() {
                        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        safeStart(i);
                    }
                }, null));
            }
        } catch (Exception ignored) { }
        if (catalog.apps.containsKey("com.android.tv")) {
            out.add(tile("input:livetv", "Live TV", null, catalog.apps.get("com.android.tv").icon,
                    new Runnable() { @Override public void run() { launch("com.android.tv"); } }, null));
        }
        return out;
    }

    // ------------------------------------------------------------------ status

    private void updateClock() {
        if (clock == null) return;
        Calendar now = Calendar.getInstance();
        clock.setText(BigClock.spannable(this, theme));
        clock.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, clock.getTextSize(),
                theme.text, BigClock.mix(theme.accent, theme.text), android.graphics.Shader.TileMode.CLAMP));
        String[] g = Theme.greeting();
        // English + Hindi / Gujarati, alternating each minute
        String fg = null;
        try { fg = Festive.greeting(Panchang.now()); } catch (Exception ignored) { }
        greet.setText(fg != null ? fg : g[0] + "  ·  " + (Calendar.getInstance().get(Calendar.MINUTE) % 2 == 0 ? g[1] : g[2]));
        date.setText(DateFormat.format("EEEE, d MMMM", now));
        showPanchang();
    }

    private void updateStatus() {
        if (status == null) return;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        String net = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) net = ni.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : ni.getTypeName();
            java.util.List<NetMonitor.Sample> ns = NetMonitor.samples(this);
            if (!ns.isEmpty()) {
                NetMonitor.Sample s = ns.get(ns.size() - 1);
                if (System.currentTimeMillis() - s.t < 10 * 60000L)
                    net = s.ok ? net + " " + NetMonitor.barsText(s.rssi) + " " + s.lat + " ms" : "⚠ Internet down";
            }
        } catch (Exception ignored) { }
        long sl = Theme.prefs(this).getLong("sleep_at", 0) - System.currentTimeMillis();
        if (sl > 0) net = net + "   ·   💤 " + (sl / 60000 + 1) + " min";
        status.setText("RAM free " + Ui.mb(mi.availMem) + "   ·   Storage free " + Ui.mb(fs.getAvailableBytes())
                + "   ·   " + net);
        if (themeBtn != null) themeBtn.setText("Theme: " + Theme.label(this));
    }
}

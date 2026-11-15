package com.broom.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pick a theme: "Auto (time of day)" or any pack theme. Previews render in the background. */
public class ThemeGalleryActivity extends Activity {
    private Theme th;
    private final List<Bitmap> previews = new ArrayList<>();
    private volatile boolean alive = true;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Sounds.init(this);
        Sounds.quiet(600);
        th = Theme.current(this);
        FrameLayout root = new FrameLayout(this);
        ThemeBackground bg = new ThemeBackground(this);
        bg.setTheme(th);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        int pad = Ui.dp(this, 36);
        col.setPadding(pad, Ui.dp(this, 24), pad, pad);
        col.addView(Ui.text(this, "Themes", 30, th.text, "Poppins-SemiBold"));
        TextView sub = Ui.text(this, "Choose a look. Auto changes with the time of day (Dawn, Day, Dusk, Night).", 13, th.dim, th.bodyFont);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        col.addView(sub);

        Map<String, List<Theme>> packs = new LinkedHashMap<>();
        for (Theme t : Theme.ALL) {
            if (!packs.containsKey(t.pack)) packs.put(t.pack, new ArrayList<Theme>());
            packs.get(t.pack).add(t);
        }
        String cur = Theme.choice(this);
        View first = null;
        final List<Object[]> jobs = new ArrayList<>();   // {Theme, ImageView}
        boolean firstPack = true;
        for (Map.Entry<String, List<Theme>> e : packs.entrySet()) {
            TextView h = Ui.text(this, e.getKey(), 16, th.text, th.titleFont);
            h.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
            col.addView(h);
            // cards wrap 4 per line, with room around them for the focus zoom
            List<View> cards = new ArrayList<>();
            if (firstPack) {
                View auto = card(null, "Auto · time of day", cur.equals("auto"), jobs);
                cards.add(auto);
                if (cur.equals("auto")) first = auto;
                firstPack = false;
            }
            for (Theme t : e.getValue()) {
                View c = card(t, t.name, cur.equals(t.id), jobs);
                cards.add(c);
                if (cur.equals(t.id)) first = c;
            }
            LinearLayout row = null;
            for (int i = 0; i < cards.size(); i++) {
                if (i % 4 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setClipChildren(false);
                    row.setClipToPadding(false);
                    row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), 0, Ui.dp(this, 6));
                    col.addView(row);
                }
                row.addView(cards.get(i), lp());
            }
        }
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        final View f = first;
        if (f != null) f.post(new Runnable() { @Override public void run() { f.requestFocus(); } });

        // render previews off the main thread, one at a time
        final Handler main = new Handler(Looper.getMainLooper());
        final int pw = Ui.dp(this, 192), ph = Ui.dp(this, 108);
        new Thread(new Runnable() {
            @Override public void run() {
                for (Object[] j : jobs) {
                    if (!alive) return;
                    final Theme t = (Theme) j[0];
                    final ImageView iv = (ImageView) j[1];
                    final Bitmap bm = Nebula.preview(t, pw, ph);
                    synchronized (previews) { previews.add(bm); }
                    main.post(new Runnable() { @Override public void run() { if (alive) iv.setImageBitmap(bm); } });
                }
            }
        }).start();
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.rightMargin = Ui.dp(this, 18);
        return l;
    }

    private View card(final Theme t, String name, boolean selected, List<Object[]> jobs) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setClipChildren(false);
        final FrameLayout tile = new FrameLayout(this);
        final Ui.Glass g = Ui.glass(this, th, Ui.dp(this, 14));
        tile.setBackground(g.d);
        Ui.glassOutline(tile, Ui.dp(this, 14), g.edge);
        tile.setClipToOutline(true);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setSoundEffectsEnabled(false);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        if (t != null) jobs.add(new Object[]{t, iv});
        else {
            // Auto: show the four time-of-day themes as stripes
            LinearLayout stripes = new LinearLayout(this);
            for (Theme x : Theme.TIME) {
                ImageView s = new ImageView(this);
                s.setScaleType(ImageView.ScaleType.CENTER_CROP);
                stripes.addView(s, new LinearLayout.LayoutParams(0, -1, 1));
                jobs.add(new Object[]{x, s});
            }
            tile.addView(stripes, new FrameLayout.LayoutParams(-1, -1));
        }
        if (selected) {
            TextView tick = Ui.text(this, "✓", 16, 0xFFFFFFFF, "Poppins-SemiBold");
            tick.setGravity(Gravity.CENTER);
            tick.setBackground(Ui.round(th.focus, Ui.dp(this, 14)));
            tick.setTextColor(th.onFocus);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28), Gravity.TOP | Gravity.END);
            tl.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
            tile.addView(tick, tl);
        }
        wrap.addView(tile, new LinearLayout.LayoutParams(Ui.dp(this, 192), Ui.dp(this, 112)));
        TextView label = Ui.text(this, name, 13, th.text, "Poppins-Medium");
        label.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        wrap.addView(label);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                g.focus(ThemeGalleryActivity.this, f, th);
                if (f) Sounds.tick(ThemeGalleryActivity.this);
                tile.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).translationZ(f ? 20 : 0).setDuration(110).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Sounds.click(ThemeGalleryActivity.this);
                Theme.setChoice(ThemeGalleryActivity.this, t == null ? "auto" : t.id);
                finish();
            }
        });
        return wrap;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        alive = false;
        synchronized (previews) { previews.clear(); }
    }
}

package com.broom.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Markets, live cricket and headlines (cricket, football, tech, space, market news) at a glance. */
public class BriefingActivity extends Activity {
    static final int UP = 0xFF34C759, DOWN = 0xFFFF5A5F;
    private Theme th;
    private LinearLayout col;
    private ScrollView sv;
    private final Handler h = new Handler();
    // auto-rotating tabs: every 2 min, paused for 2 min after any remote key
    static final long ROTATE = 2 * 60000L;
    private final java.util.LinkedHashMap<String, View> anchors = new java.util.LinkedHashMap<>();
    private final java.util.HashMap<String, TextView> tabPills = new java.util.HashMap<>();
    private String[] tabs = {};
    private int tabIdx;
    private long lastKey;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (System.currentTimeMillis() - lastKey >= ROTATE && tabs.length > 0) showTab((tabIdx + 1) % tabs.length);
            h.postDelayed(this, ROTATE);
        }
    };

    @Override public void onUserInteraction() { super.onUserInteraction(); lastKey = System.currentTimeMillis(); }

    private void showTab(int i) {
        if (tabs.length == 0) return;
        tabIdx = i;
        View target = anchors.get(tabs[i]);
        if (target != null) sv.scrollTo(0, Math.max(0, target.getTop() - Ui.dp(this, 8)));
        for (java.util.Map.Entry<String, TextView> e : tabPills.entrySet())
            e.getValue().setText((e.getKey().equals(tabs[i]) ? "▶  " : "") + e.getKey());
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Briefing.refresh(BriefingActivity.this, true, new Briefing.Done() {
                @Override public void done() { if (stamp() != rendered) render(); }
            });
            h.postDelayed(this, 5 * 60000);
        }
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
        View veil = new View(this);
        veil.setBackgroundColor(0x59000000);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        sv = new ScrollView(this);
        sv.setSmoothScrollingEnabled(false);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        int pad = Ui.dp(this, 40);
        col.setPadding(pad, Ui.dp(this, 20), pad, pad);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        h.post(tick);
        lastKey = 0;
        h.removeCallbacks(rotate);
        h.postDelayed(rotate, ROTATE);
    }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); h.removeCallbacks(rotate); }

    private long rendered = -1;

    private long stamp() {
        return Briefing.updated(this, "markets") + 31 * Briefing.updated(this, "scores") + 17 * Briefing.updated(this, "news");
    }

    private void render() {
        rendered = stamp();
        View focused = getCurrentFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        col.removeAllViews();

        // ---------- header
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(Ui.text(this, "Briefing", 30, th.text, "Poppins-SemiBold"));
        long up = Briefing.updated(this, "markets");
        boolean fail = Briefing.lastFailed(this, "markets");
        String upd = up > 0 ? "  ·  updated " + Briefing.ago(up) : "  ·  loading…";
        if (fail && up > 0) upd = "  ·  ⚠ couldn't refresh — showing data from " + Briefing.ago(up);
        final boolean mk = Features.on(this, Features.MARKETS), ck = Features.on(this, Features.CRICKET), fb = Features.on(this, Features.FOOTBALL);
        if (mk) tl.addView(Ui.text(this, "Markets delayed ~15 min · display only" + upd + (Briefing.marketsOpen() ? "" : "  ·  markets closed, refreshing hourly"),
                12, fail ? 0xFFFFB0A0 : th.dim, th.bodyFont));
        else tl.addView(Ui.text(this, "Scores and headlines  ·  select Customise to choose what shows", 12, th.dim, th.bodyFont));
        head.addView(tl, new LinearLayout.LayoutParams(0, -2, 1));
        final boolean morning = Theme.prefs(this).getBoolean("morning_brief", true);
        TextView mb = Ui.pill(this, "Morning briefing: " + (morning ? "On" : "Off"), th, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Theme.prefs(BriefingActivity.this).edit().putBoolean("morning_brief", !morning).apply();
                Toast.makeText(BriefingActivity.this, !morning ? "Briefing opens on the first switch-on between 6 and 10 AM"
                        : "Morning briefing off", Toast.LENGTH_LONG).show();
                render();
            }
        });
        mb.setTag("mb");
        head.addView(mb, gap());
        TextView wl = Ui.pill(this, "Watchlist", th, new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new android.content.Intent(BriefingActivity.this, WatchlistActivity.class)); }
        });
        wl.setTag("wl");
        if (mk) head.addView(wl, gap());
        TextView cu = Ui.pill(this, "Customise", th, new View.OnClickListener() {
            @Override public void onClick(View v) {
                Features.dialog(BriefingActivity.this, new Runnable() {
                    @Override public void run() {
                        tabIdx = 0;
                        render();
                        Briefing.refresh(BriefingActivity.this, true, new Briefing.Done() { @Override public void done() { render(); } });
                    }
                });
            }
        });
        cu.setTag("cu");
        head.addView(cu, gap());
        TextView rf = Ui.pill(this, "Refresh", th, new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String n : new String[]{"markets", "scores", "news"}) new java.io.File(getFilesDir(), "brief_" + n + ".json").setLastModified(0);
                Toast.makeText(BriefingActivity.this, "Updating…", Toast.LENGTH_SHORT).show();
                Briefing.refresh(BriefingActivity.this, true, new Briefing.Done() { @Override public void done() { render(); } });
            }
        });
        rf.setTag("rf");
        head.addView(rf, gap());
        col.addView(head);

        // ---------- jump buttons (the remote can't scroll long pages quickly)
        anchors.clear();
        tabPills.clear();
        LinearLayout jumps = row();
        jumps.setPadding(0, Ui.dp(this, 10), 0, 0);
        jumps.setClipChildren(false);
        col.addView(jumps);
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("Summary");
        if (mk) java.util.Collections.addAll(names, "Indices", "India sectors", "India stocks", "US tech & AI");
        if (fb) names.add("Football");
        if (ck) names.add("Cricket");
        java.util.List<Integer> secs = new java.util.ArrayList<>();
        for (int k = 0; k < Briefing.SECTIONS.length; k++) if (Features.news(this, Briefing.SECTIONS[k])) secs.add(k);
        if (!secs.isEmpty()) names.add("News");
        final String[] jumpNames = names.toArray(new String[0]);
        tabs = jumpNames;
        if (tabIdx >= tabs.length) tabIdx = 0;
        if (!Features.anyBriefing(this)) {
            TextView none = Ui.text(this, "Everything on this page is switched off. Select Customise to turn markets, scores or news back on.",
                    15, th.text, th.bodyFont);
            none.setPadding(0, Ui.dp(this, 30), 0, 0);
            col.addView(none);
        } else anchors.put("Summary", summary(mk, ck));

        // ---------- headline indices (big cards)
        for (int gi = 0; mk && gi < Briefing.MAIN; gi++) {
            View sec = section(Briefing.GROUPS[gi][0]);
            if (gi == 0) anchors.put("Indices", sec);
            LinearLayout r = row();
            for (Briefing.Quote q : Briefing.group(this, gi)) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.rightMargin = Ui.dp(this, 12);
                r.addView(quoteCard(q), lp);
            }
            if (r.getChildCount() == 0) r.addView(Ui.text(this, "Loading…", 13, th.dim, th.bodyFont));
            col.addView(r);
        }

        // ---------- trackers (compact cards, 5 per row) with top gainer / loser
        String[] anchorFor = {"India sectors", "India sectors", "India stocks", "US tech & AI"};
        for (int gi = Briefing.MAIN; mk && gi < Briefing.GROUPS.length; gi++) {
            List<Briefing.Quote> qs = Briefing.group(this, gi);
            View sec = section(Briefing.GROUPS[gi][0] + movers(qs));
            String an = anchorFor[gi - Briefing.MAIN];
            if (!anchors.containsKey(an)) anchors.put(an, sec);
            if (qs.isEmpty()) { col.addView(Ui.text(this, "Loading…", 13, th.dim, th.bodyFont)); continue; }
            LinearLayout r = null;
            for (int i = 0; i < qs.size(); i++) {
                if (i % 5 == 0) {
                    r = row();
                    r.setPadding(0, i == 0 ? 0 : Ui.dp(this, 10), 0, 0);
                    col.addView(r);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.rightMargin = Ui.dp(this, 10);
                r.addView(miniCard(qs.get(i)), lp);
            }
            for (int pad = qs.size() % 5; pad != 0 && pad < 5; pad++) {   // keep the last row's cards the same width
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
                lp.rightMargin = Ui.dp(this, 10);
                r.addView(new View(this), lp);
            }
        }

        // ---------- football scores
        List<Briefing.Match> ms = Briefing.matches(this);
        if (fb) anchors.put("Football", section("Football  ·  Premier League · Champions League · La Liga"));
        if (fb && ms.isEmpty()) col.addView(Ui.text(this, "Loading fixtures…", 13, th.dim, th.bodyFont));
        LinearLayout fr = null;
        for (int i = 0; i < Math.min(8, ms.size()); i++) {
            if (i % 4 == 0) { fr = row(); fr.setPadding(0, i == 0 ? 0 : Ui.dp(this, 10), 0, 0); col.addView(fr); }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = Ui.dp(this, 12);
            fr.addView(matchCard(ms.get(i)), lp);
        }
        for (int pad = Math.min(8, ms.size()) % 4; pad != 0 && pad < 4; pad++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.rightMargin = Ui.dp(this, 12);
            fr.addView(new View(this), lp);
        }

        // ---------- live cricket
        List<Briefing.Score> sc = Briefing.scores(this);
        if (ck) anchors.put("Cricket", section("Cricket scores"));
        LinearLayout sr = row();
        if (ck && sc.isEmpty()) sr.addView(Ui.text(this, "No international or IPL matches right now.", 13, th.dim, th.bodyFont));
        for (int i = 0; i < Math.min(4, sc.size()); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = Ui.dp(this, 12);
            sr.addView(scoreCard(sc.get(i)), lp);
        }
        col.addView(sr);

        // ---------- headlines: Cricket | Football, Tech | AI, Space | Market news
        for (int i = 0; i < secs.size(); i += 2) {
            LinearLayout r = row();
            r.setPadding(0, Ui.dp(this, 14), 0, 0);
            if (i == 0) anchors.put("News", r);
            for (int j = i; j < i + 2; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                if (j == i) lp.rightMargin = Ui.dp(this, 14);
                if (j >= secs.size()) { r.addView(new View(this), lp); continue; }   // keep a lone card half width
                int k = secs.get(j);
                r.addView(newsCard(Briefing.SECTION_TITLES[k], Briefing.stories(this, Briefing.SECTIONS[k]), 5), lp);
            }
            col.addView(r);
        }

        for (final String jn : jumpNames) {
            final View target = anchors.get(jn);
            if (target == null) continue;
            final int idx = java.util.Arrays.asList(jumpNames).indexOf(jn);
            TextView p = Ui.pill(this, jn, th, new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(idx); }
            });
            tabPills.put(jn, p);
            p.setTag("jump:" + jn);
            LinearLayout.LayoutParams jl = new LinearLayout.LayoutParams(-2, -2);
            jl.rightMargin = Ui.dp(this, 10);
            jumps.addView(p, jl);
        }
        TextView src = Ui.text(this, "Sources: Yahoo Finance (quotes, delayed, display only), ESPNcricinfo, BBC, NASA, Ars Technica, TechCrunch, MIT Technology Review, Google News. Headlines only.",
                11, th.dim, th.bodyFont);
        src.setPadding(0, Ui.dp(this, 14), 0, 0);
        col.addView(src);

        sv.post(new Runnable() { @Override public void run() { showTab(tabIdx); } });
        View f = focusTag == null ? null : col.findViewWithTag(focusTag);
        if (f == null) f = mb;
        final View ff = f;
        ff.post(new Runnable() { @Override public void run() { ff.requestFocus(); } });
    }

    // ---------------------------------------------------------------- cards
    private View quoteCard(Briefing.Quote q) {
        LinearLayout c = card();
        c.addView(Ui.text(this, q.name, 12, th.dim, "Poppins-Medium"));
        TextView p = Ui.text(this, Briefing.price(q), 20, th.text, "Poppins-SemiBold");
        p.setPadding(0, Ui.dp(this, 2), 0, 0);
        c.addView(p);
        c.addView(Ui.text(this, Briefing.change(q), 12, q.up() ? UP : DOWN, "Poppins-SemiBold"));
        if (Briefing.closed(q)) c.addView(Ui.text(this, "Closed · " + Briefing.asOf(q), 10, th.dim, th.bodyFont));
        Spark s = new Spark(this, q.spark, q.up() ? UP : DOWN);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 26));
        sl.topMargin = Ui.dp(this, 6);
        c.addView(s, sl);
        return c;
    }

    private View matchCard(Briefing.Match m) {
        LinearLayout c = card();
        boolean live = "in".equals(m.state), pre = "pre".equals(m.state);
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (live) {
            View dot = new View(this);
            dot.setBackground(Ui.round(0xFFFF3B30, Ui.dp(this, 4)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            dl.rightMargin = Ui.dp(this, 6);
            top.addView(dot, dl);
        }
        top.addView(Ui.text(this, (live ? "LIVE " + m.detail : pre ? String.valueOf(android.text.format.DateFormat.format("EEE d MMM · h:mm a", m.start)) : m.detail)
                + "   ·   " + m.league, 10, live ? 0xFFFF6B60 : th.dim, "Poppins-SemiBold"));
        c.addView(top);
        for (int k = 0; k < 2; k++) {
            LinearLayout r = row();
            r.setPadding(0, Ui.dp(this, 3), 0, 0);
            TextView n = Ui.text(this, k == 0 ? m.home : m.away, 13, th.text, "Poppins-Medium");
            n.setSingleLine(true);
            r.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
            if (!pre) r.addView(Ui.text(this, String.valueOf(k == 0 ? m.hs : m.as), 15, th.text, "Poppins-SemiBold"));
            c.addView(r);
        }
        return c;
    }

    private View scoreCard(Briefing.Score s) {
        LinearLayout c = card();
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (s.live) {
            View dot = new View(this);
            dot.setBackground(Ui.round(0xFFFF3B30, Ui.dp(this, 4)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            dl.rightMargin = Ui.dp(this, 6);
            top.addView(dot, dl);
        }
        top.addView(Ui.text(this, s.live ? "LIVE" : "Latest", 10, s.live ? 0xFFFF6B60 : th.dim, "Poppins-SemiBold"));
        c.addView(top);
        String[] sides = s.text.split(" v ");
        for (String side : sides) {
            TextView t = Ui.text(this, side.trim(), 13, th.text, side.contains("*") ? "Poppins-SemiBold" : "Poppins-Regular");
            t.setPadding(0, Ui.dp(this, 3), 0, 0);
            c.addView(t);
        }
        return c;
    }

    private View newsCard(String title, List<Briefing.Story> stories, int n) {
        LinearLayout c = card();
        TextView h = Ui.text(this, title, 15, th.text, th.titleFont);
        h.setPadding(0, 0, 0, Ui.dp(this, 6));
        c.addView(h);
        if (stories.isEmpty()) c.addView(Ui.text(this, "Loading headlines…", 12, th.dim, th.bodyFont));
        for (int i = 0; i < Math.min(n, stories.size()); i++) {
            Briefing.Story s = stories.get(i);
            TextView t = Ui.text(this, s.title, 13, th.text, "Poppins-Regular");
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(0, Ui.dp(this, 6), 0, 0);
            c.addView(t);
            String meta = s.source + (s.time > 0 ? "  ·  " + Briefing.ago(s.time) : "");
            c.addView(Ui.text(this, meta, 10, th.dim, th.bodyFont));
        }
        return c;
    }

    private View section(String s) {
        TextView t = Ui.text(this, s, 15, th.text, th.titleFont);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        col.addView(t);
        return t;
    }

    /** Summary tab: key indices, today's best/worst movers, live cricket, top story per section. */
    private View summary(boolean mk, boolean ck) {
        View sec = section("Summary");
        LinearLayout keys = row();
        if (mk) {
        String[] keySyms = {"^NSEI", "^BSESN", "^GSPC", "^IXIC", "USDINR=X"};
        List<Briefing.Quote> kq = Briefing.quotes(this, keySyms);
        for (String ks : keySyms)
            for (Briefing.Quote q : kq)
                if (q.sym.equals(ks)) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                    lp.rightMargin = Ui.dp(this, 10);
                    keys.addView(miniCard(q), lp);
                }
        if (keys.getChildCount() == 0) keys.addView(Ui.text(this, "Loading markets…", 13, th.dim, th.bodyFont));
        col.addView(keys);
        }

        LinearLayout two = row();
        two.setPadding(0, Ui.dp(this, 12), 0, 0);
        LinearLayout mv = card();
        mv.addView(Ui.text(this, mk ? "Today's movers" : "Cricket", 15, th.text, th.titleFont));
        for (int gi = Briefing.MAIN; gi < Briefing.GROUPS.length; gi++) {
            List<Briefing.Quote> qs = Briefing.group(this, gi);
            if (qs.size() < 2) continue;
            Briefing.Quote up = qs.get(0), dn = qs.get(0);
            for (Briefing.Quote q : qs) { if (q.pct > up.pct) up = q; if (q.pct < dn.pct) dn = q; }
            TextView g = Ui.text(this, Briefing.GROUPS[gi][0], 11, th.dim, th.bodyFont);
            g.setPadding(0, Ui.dp(this, 8), 0, 0);
            mv.addView(g);
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            sb.append(up.name).append(" ");
            int st = sb.length();
            sb.append(String.format(java.util.Locale.US, "%+.1f%%", up.pct));
            sb.setSpan(new android.text.style.ForegroundColorSpan(up.pct >= 0 ? UP : DOWN), st, sb.length(), 0);
            sb.append("     ").append(dn.name).append(" ");
            st = sb.length();
            sb.append(String.format(java.util.Locale.US, "%+.1f%%", dn.pct));
            sb.setSpan(new android.text.style.ForegroundColorSpan(dn.pct >= 0 ? UP : DOWN), st, sb.length(), 0);
            TextView t = Ui.text(this, "", 13, th.text, "Poppins-Medium");
            t.setText(sb);
            mv.addView(t);
        }
        List<Briefing.Score> sc = Briefing.scores(this);
        if (!mk && sc.isEmpty()) mv.addView(Ui.text(this, "No international or IPL matches right now.", 13, th.dim, th.bodyFont));
        if (!sc.isEmpty()) {
            if (mk) {
                TextView g = Ui.text(this, "Cricket", 11, th.dim, th.bodyFont);
                g.setPadding(0, Ui.dp(this, 10), 0, 0);
                mv.addView(g);
            }
            for (int i = 0; i < Math.min(2, sc.size()); i++)
                mv.addView(Ui.text(this, (sc.get(i).live ? "● " : "") + sc.get(i).text, 13, sc.get(i).live ? 0xFFFFFFFF : th.dim, "Poppins-Medium"));
        }
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1);
        l1.rightMargin = Ui.dp(this, 14);
        boolean news = Features.anyNews(this);
        if (mk || ck) two.addView(mv, news ? l1 : new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout top = card();
        top.addView(Ui.text(this, "Top stories", 15, th.text, th.titleFont));
        for (int k = 0; k < Briefing.SECTIONS.length; k++) {
            List<Briefing.Story> st = Briefing.stories(this, Briefing.SECTIONS[k]);
            if (st.isEmpty()) continue;
            TextView g = Ui.text(this, Briefing.SECTION_TITLES[k] + "  ·  " + st.get(0).source, 11, th.dim, th.bodyFont);
            g.setPadding(0, Ui.dp(this, 8), 0, 0);
            top.addView(g);
            TextView t = Ui.text(this, st.get(0).title, 13, th.text, "Poppins-Regular");
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(t);
        }
        if (news) two.addView(top, new LinearLayout.LayoutParams(0, -2, 1.3f));
        col.addView(two);
        return sec;
    }

    /** "   ·   ▲ HAL +2.1%   ▼ ITC −1.3%" */
    private String movers(List<Briefing.Quote> qs) {
        if (qs.size() < 2) return "";
        Briefing.Quote up = qs.get(0), dn = qs.get(0);
        for (Briefing.Quote q : qs) { if (q.pct > up.pct) up = q; if (q.pct < dn.pct) dn = q; }
        return String.format(java.util.Locale.US, "     Best: %s %+.1f%%     Worst: %s %+.1f%%", up.name, up.pct, dn.name, dn.pct);
    }

    private View miniCard(Briefing.Quote q) {
        LinearLayout c = card();
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 12));
        TextView n = Ui.text(this, q.name, 11, th.dim, "Poppins-Medium");
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(n);
        c.addView(Ui.text(this, Briefing.price(q), 15, th.text, "Poppins-SemiBold"));
        android.text.SpannableStringBuilder ch = new android.text.SpannableStringBuilder(Briefing.change(q));
        ch.setSpan(new android.text.style.ForegroundColorSpan(q.up() ? UP : DOWN), 0, ch.length(), 0);
        if (Briefing.closed(q)) {
            int st = ch.length();
            ch.append("   closed");
            ch.setSpan(new android.text.style.ForegroundColorSpan(th.dim), st, ch.length(), 0);
        }
        TextView cv = Ui.text(this, "", 11, th.text, "Poppins-SemiBold");
        cv.setText(ch);
        c.addView(cv);
        Spark s = new Spark(this, q.spark, q.up() ? UP : DOWN);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 16));
        sl.topMargin = Ui.dp(this, 4);
        c.addView(s, sl);
        return c;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        Ui.Glass g = Ui.glass(this, th, Ui.dp(this, th.radiusDp));
        c.setBackground(g.d);
        int p = Ui.dp(this, 12);
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

    /** Tiny line chart of recent closes. */
    static final class Spark extends View {
        private final float[] d;
        private final int col;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Spark(Context c, float[] d, int col) { super(c); this.d = d; this.col = col; }

        @Override protected void onDraw(Canvas c) {
            if (d == null || d.length < 2) return;
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (float v : d) { min = Math.min(min, v); max = Math.max(max, v); }
            float w = getWidth(), h = getHeight(), span = Math.max(1e-6f, max - min);
            Path line = new Path(), fill = new Path();
            for (int i = 0; i < d.length; i++) {
                float x = w * i / (d.length - 1), y = h - 2 - (h - 4) * (d[i] - min) / span;
                if (i == 0) { line.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y); } else { line.lineTo(x, y); fill.lineTo(x, y); }
            }
            fill.lineTo(w, h);
            fill.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, (col & 0xFFFFFF) | 0x55000000, col & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawPath(fill, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f);
            p.setColor(col);
            c.drawPath(line, p);
        }
    }
}

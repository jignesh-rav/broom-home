package com.broom.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/** Galaxy / nebula wallpaper for the current theme. Static bitmap: zero idle CPU. */
public class ThemeBackground extends View {
    public static final int STYLE_SUNRISE = 0, STYLE_SKY = 1, STYLE_SUNSET = 2, STYLE_STARS = 3;

    private Theme theme;

    /** Draw festival decorations (home screen only). */
    public boolean festive;

    public ThemeBackground(Context c) { super(c); }

    public void setTheme(Theme t) { theme = t; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        if (theme == null || getWidth() == 0) return;
        Bitmap b = Nebula.get(theme, getWidth(), getHeight());
        c.drawBitmap(b, 0, 0, null);
        if (festive) try { Festive.draw(c, Festive.kind(Panchang.now()), getWidth(), getHeight()); } catch (Exception ignored) { }
    }
}

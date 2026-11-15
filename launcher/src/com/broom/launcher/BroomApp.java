package com.broom.launcher;

import android.app.Application;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/** Saves crash logs (files/crash.txt) and counts recent crashes so Home can fall back to safe mode. */
public class BroomApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    Theme.prefs(BroomApp.this).edit().putLong("crash_at", System.currentTimeMillis())
                            .putInt("crash_n", Theme.prefs(BroomApp.this).getInt("crash_n", 0) + 1).commit();
                    File dir = getExternalFilesDir(null);
                    if (dir != null) try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "crash.txt"), true))) {
                        w.println("---- " + new java.util.Date());
                        e.printStackTrace(w);
                    }
                } catch (Throwable ignored) { }
                if (def != null) def.uncaughtException(t, e);
            }
        });
    }

    /** True if Broom crashed twice within the last 3 minutes. */
    static boolean unstable(android.content.Context c) {
        android.content.SharedPreferences p = Theme.prefs(c);
        boolean recent = System.currentTimeMillis() - p.getLong("crash_at", 0) < 3 * 60000L;
        if (!recent && p.getInt("crash_n", 0) != 0) p.edit().putInt("crash_n", 0).apply();
        return recent && p.getInt("crash_n", 0) >= 2;
    }
}

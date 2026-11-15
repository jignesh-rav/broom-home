package com.broom.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Full-screen preview of a screensaver style. Any remote key exits. */
public class DreamPreviewActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String m = getIntent().getStringExtra("mode");
        setContentView(new DreamView(this, m == null ? DreamView.saved(this) : m));
    }
    @Override public boolean onKeyDown(int code, KeyEvent e) { finish(); return true; }
}

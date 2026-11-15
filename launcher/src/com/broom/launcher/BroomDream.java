package com.broom.launcher;

import android.service.dreams.DreamService;

public class BroomDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        // midnight-6 AM: low-power Minimal Clock, no Briefing rotation
        String mode = DreamView.night(this) ? "clock" : DreamView.saved(this);
        setContentView(new DreamView(this, mode));
    }
}

package com.broom.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

/** Opens Broom Home after the TV finishes booting (Xiaomi otherwise boots into Live TV). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context c, Intent intent) {
        if (!KeyRemapService.powerOnHome(c)) return;
        final PendingResult pr = goAsync();
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                Intent i = new Intent(c, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                try { c.startActivity(i); } catch (Exception ignored) { }
                pr.finish();
            }
        }, 2500);
    }
}

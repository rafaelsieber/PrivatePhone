package com.rafael.privatephone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** No boot: confere o filtro e garante a checagem diária. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Guard.check(context);
        Guard.scheduleDaily(context);
    }
}

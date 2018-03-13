package com.maxdean.blinkyapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Autostart extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent arg1) {
        Log.i("Autostart", "BOOT_COMPLETED broadcast received. Executing following code:");

        Intent intent = new Intent(context, BlinkyService.class);
        context.startService(intent);
    }
}

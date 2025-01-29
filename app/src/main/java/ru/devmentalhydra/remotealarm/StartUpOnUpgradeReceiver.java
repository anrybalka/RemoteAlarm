package ru.devmentalhydra.remotealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartUpOnUpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {

            Intent activityIntent = new Intent(context, MainActivity.class);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            //activityIntent.putExtra("upgrade", true);
            context.startActivity(activityIntent);
        }

    }
}
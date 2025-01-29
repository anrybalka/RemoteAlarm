    package ru.devmentalhydra.remotealarm;

    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;
    import android.os.Build;

    public class StartUpOnBootUpReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                Intent activityIntent = new Intent(context, MainActivity.class);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activityIntent.putExtra("autorun", true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                }
                context.startActivity(activityIntent);
            }
        }
    }
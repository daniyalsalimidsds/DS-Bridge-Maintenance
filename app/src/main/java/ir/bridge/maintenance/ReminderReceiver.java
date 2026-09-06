package ir.bridge.maintenance;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="bridge_maintenance_reminders";
    @Override public void onReceive(Context context, Intent intent){
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,context.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,100,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title=intent.getStringExtra("title"),body=intent.getStringExtra("body");if(title==null||title.trim().isEmpty())title=context.getString(R.string.app_name);if(body==null)body="موعد پیگیری فرا رسیده است.";
        try{nm.notify(intent.getIntExtra("nid",1),new Notification.Builder(context,CHANNEL_ID).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build());}catch(SecurityException ignored){}
    }
}

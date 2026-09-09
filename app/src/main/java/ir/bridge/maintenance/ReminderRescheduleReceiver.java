package ir.bridge.maintenance;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Recreates persisted reminder alarms after reboot, app replacement, or clock/timezone changes. */
public final class ReminderRescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try (AppDb db = new AppDb(context.getApplicationContext())) {
                JSONArray rows = new JSONArray(db.list("reminders"));
                long now = System.currentTimeMillis();
                for (int i=0;i<rows.length();i++) {
                    JSONObject r = rows.optJSONObject(i); if (r==null || !r.optBoolean("active", true)) continue;
                    long when = r.optLong("timestamp",0); if (when<=now) continue;
                    schedule(context,r.optString("id","reminder-"+i),when,r.optString("title",""),r.optString("body",""));
                }
            } catch (Exception ignored) { } finally { pending.finish(); }
        }, "bridge-reminder-reschedule").start();
    }

    private static void schedule(Context context,String id,long when,String title,String body) {
        Intent i=new Intent(context,ReminderReceiver.class); int nid=Math.abs(id.hashCode());
        i.putExtra("nid",nid);i.putExtra("title",bounded(title,140));i.putExtra("body",bounded(body,500));
        PendingIntent pi=PendingIntent.getBroadcast(context,nid,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
        try {
            if(android.os.Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        } catch(SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi); }
    }
    private static String bounded(String s,int max){if(s==null)return"";return s.length()>max?s.substring(0,max):s;}
}

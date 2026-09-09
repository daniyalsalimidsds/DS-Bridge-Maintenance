package ir.bridge.maintenance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Persistent, silent and self-refreshing Jalali date notification. */
public final class DateStatusNotifier extends BroadcastReceiver {
    private static final String CHANNEL = "bridge_persian_date";
    private static final String ACTION_REFRESH = "ir.bridge.maintenance.action.REFRESH_JALALI_DATE";
    private static final int ID = 1100;
    private static final int ALARM_ID = 1101;
    private static final char RLI = '\u2067', PDI = '\u2069';

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION_REFRESH.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_DATE_CHANGED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        show(context);
    }

    public static void show(Context context) {
        Context app = context.getApplicationContext();
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "تاریخ شمسی", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("نمایش روز و تاریخ هجری شمسی بدون صدا");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
        }

        Calendar now = Calendar.getInstance();
        int[] jalali = jalali(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        String[] weekdays = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"};
        String[] months = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        // Keep one readable Persian date in the notification. Repeating the
        // same date in content text and BigText made the notification panel
        // look duplicated on Samsung/One UI.
        String fullDate = digits(String.format(java.util.Locale.US, "%d", jalali[2])) + " " + months[jalali[1] - 1] + " " + digits(String.format(java.util.Locale.US, "%04d", jalali[0]));
        String rtlFull = rtl(weekdays[now.get(Calendar.DAY_OF_WEEK) - 1] + " " + fullDate);

        Intent open = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(app, ID, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(app, CHANNEL) : new Notification.Builder(app);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(rtlFull)
                .setContentText(rtl("بازرسی پل"))
                .setStyle(new Notification.BigTextStyle().bigText(rtlFull))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setUsesChronometer(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        try { manager.notify(ID, notification); } catch (SecurityException ignored) { }
        scheduleNextMidnight(app, now);
    }

    private static void scheduleNextMidnight(Context context, Calendar now) {
        Calendar next = (Calendar) now.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        next.set(Calendar.HOUR_OF_DAY, 0); next.set(Calendar.MINUTE, 0); next.set(Calendar.SECOND, 2); next.set(Calendar.MILLISECOND, 0);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent refresh = new Intent(context, DateStatusNotifier.class).setAction(ACTION_REFRESH);
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_ID, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC, next.getTimeInMillis(), pending);
        else alarms.set(AlarmManager.RTC, next.getTimeInMillis(), pending);
    }

    private static String rtl(String value) { return String.valueOf(RLI) + value + PDI; }
    private static String digits(String value) {
        return value.replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴')
                .replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');
    }
    private static int[] jalali(int gy, int gm, int gd) {
        int[] gdm={0,31,59,90,120,151,181,212,243,273,304,334};int gy2=gm>2?gy+1:gy;
        int days=355666+365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400+gd+gdm[gm-1],jy=-1595+33*(days/12053);
        days%=12053;jy+=4*(days/1461);days%=1461;if(days>365){jy+=(days-1)/365;days=(days-1)%365;}
        int jm=days<186?1+days/31:7+(days-186)/30,jd=1+(days<186?days%31:(days-186)%30);return new int[]{jy,jm,jd};
    }
}

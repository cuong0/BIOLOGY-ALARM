package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.example.R
import com.example.alarm.AlarmConfig
import com.example.alarm.AlarmReceiver
import com.example.alarm.AlarmStorage
import java.text.SimpleDateFormat
import java.util.*

class SleepCycleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val isThreeCycles = intent.action == "com.example.widget.ACTION_SET_CYCLES_3"
        val isFourCycles = intent.action == "com.example.widget.ACTION_SET_CYCLES_4"
        
        if (isThreeCycles || isFourCycles) {
            val cycles = if (isThreeCycles) 3 else 4
            val sleepMinutes = (cycles * 90) + 10 // default 10 minutes to fall asleep
            val triggerTimeMs = System.currentTimeMillis() + (sleepMinutes * 60 * 1000)
            
            // Format wake-up time
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val alarmTimeString = sdf.format(Date(triggerTimeMs))

            // Save to AlarmStorage
            val storage = AlarmStorage(context)
            val newAlarm = AlarmConfig(
                id = 1,
                triggerTimeMs = triggerTimeMs,
                cycles = cycles,
                fallAsleepMinutes = 10,
                isEnabled = true,
                ringtoneName = "Mặc định hệ thống"
            )
            storage.saveAlarm(newAlarm)

            // Register with AlarmManager
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } catch (e: SecurityException) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Show confirmation toast
            Toast.makeText(context, "Đã đặt báo thức: $alarmTimeString ($cycles chu kỳ)", Toast.LENGTH_LONG).show()

            // Update all widgets
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SleepCycleWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.sleep_cycle_widget_layout)
        val storage = AlarmStorage(context)
        val alarm = storage.getAlarm()

        // Set status
        if (alarm.isEnabled && alarm.triggerTimeMs > System.currentTimeMillis()) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeStr = sdf.format(Date(alarm.triggerTimeMs))
            views.setTextViewText(R.id.widget_status, "Báo thức lúc: $timeStr")
            views.setTextViewText(R.id.widget_details, "Đã cài ${alarm.cycles} chu kỳ ngủ")
        } else {
            views.setTextViewText(R.id.widget_status, "Chưa đặt báo thức")
            views.setTextViewText(R.id.widget_details, "Chạm ở dưới để đặt nhanh")
        }

        // Set button clicks
        views.setOnClickPendingIntent(R.id.btn_cycles_3, getPendingSelfIntent(context, "com.example.widget.ACTION_SET_CYCLES_3"))
        views.setOnClickPendingIntent(R.id.btn_cycles_4, getPendingSelfIntent(context, "com.example.widget.ACTION_SET_CYCLES_4"))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, SleepCycleWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

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

class Widget1x4Provider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action ?: return
        var cycles = 0
        var isBio = false

        when (action) {
            "com.example.widget.ACTION_1X4_SET_CYCLES_2" -> cycles = 2
            "com.example.widget.ACTION_1X4_SET_CYCLES_3" -> cycles = 3
            "com.example.widget.ACTION_1X4_SET_CYCLES_4" -> cycles = 4
            "com.example.widget.ACTION_1X4_SET_BIO" -> {
                cycles = 5 // Recommended biological optimal cycles (7.5h)
                isBio = true
            }
            else -> return
        }

        val sleepMinutes = (cycles * 90) + 10 // 10 minutes to fall asleep
        val triggerTimeMs = System.currentTimeMillis() + (sleepMinutes * 60 * 1000)
        
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
            ringtoneName = "Mặc định hệ thống",
            alarmType = "single"
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

        val msg = if (isBio) {
            "Đã đặt báo thức sinh học: $alarmTimeString ($cycles chu kỳ)"
        } else {
            "Đã đặt báo thức: $alarmTimeString ($cycles chu kỳ)"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

        // Update all 1x4 and 2x2 widgets
        val manager = AppWidgetManager.getInstance(context)
        
        val ids1x4 = manager.getAppWidgetIds(ComponentName(context, Widget1x4Provider::class.java))
        for (id in ids1x4) {
            updateWidget(context, manager, id)
        }

        try {
            val ids2x2 = manager.getAppWidgetIds(ComponentName(context, Widget2x2Provider::class.java))
            for (id in ids2x2) {
                Widget2x2Provider.updateWidget(context, manager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_1x4_layout)

            // Setup button clicks
            views.setOnClickPendingIntent(R.id.btn_cycles_2, getPendingIntent(context, "com.example.widget.ACTION_1X4_SET_CYCLES_2"))
            views.setOnClickPendingIntent(R.id.btn_cycles_3_1x4, getPendingIntent(context, "com.example.widget.ACTION_1X4_SET_CYCLES_3"))
            views.setOnClickPendingIntent(R.id.btn_cycles_4_1x4, getPendingIntent(context, "com.example.widget.ACTION_1X4_SET_CYCLES_4"))
            views.setOnClickPendingIntent(R.id.btn_set_bio_1x4, getPendingIntent(context, "com.example.widget.ACTION_1X4_SET_BIO"))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, Widget1x4Provider::class.java).apply {
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
}

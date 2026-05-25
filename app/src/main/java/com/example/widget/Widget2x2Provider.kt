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

class Widget2x2Provider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action ?: return
        
        val storage = AlarmStorage(context)
        val currentAlarm = storage.getAlarm()
        val isAlarmActive = currentAlarm.isEnabled && currentAlarm.triggerTimeMs > System.currentTimeMillis()
        val prefs = context.getSharedPreferences("sleep_cycle_alarm_prefs", Context.MODE_PRIVATE)
        val activeSource = prefs.getString("widget_2x2_active_source", "") ?: ""

        val effectiveActiveSource = if (activeSource.isNotEmpty()) {
            activeSource
        } else if (isAlarmActive) {
            when (currentAlarm.cycles) {
                3 -> "cycles_3"
                4 -> "cycles_4"
                5 -> "bio"
                else -> "bio"
            }
        } else {
            ""
        }

        var shouldCancel = false
        var targetSource = ""
        var targetCycles = 0
        var isBio = false

        when (action) {
            "com.example.widget.ACTION_2X2_SET_CYCLES_3" -> {
                targetSource = "cycles_3"
                targetCycles = 3
                if (isAlarmActive && effectiveActiveSource == "cycles_3") {
                    shouldCancel = true
                }
            }
            "com.example.widget.ACTION_2X2_SET_CYCLES_4" -> {
                targetSource = "cycles_4"
                targetCycles = 4
                if (isAlarmActive && effectiveActiveSource == "cycles_4") {
                    shouldCancel = true
                }
            }
            "com.example.widget.ACTION_2X2_SET_CYCLES_5" -> {
                targetSource = "cycles_5"
                targetCycles = 5
                if (isAlarmActive && effectiveActiveSource == "cycles_5") {
                    shouldCancel = true
                }
            }
            "com.example.widget.ACTION_SET_BIO_2X2" -> {
                targetSource = "bio"
                targetCycles = 5
                isBio = true
                if (isAlarmActive && effectiveActiveSource == "bio") {
                    shouldCancel = true
                }
            }
            else -> return
        }
        
        if (shouldCancel) {
            // Cancel active alarm
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            storage.clearAlarm()
            prefs.edit().remove("widget_2x2_active_source").apply()
            
            Toast.makeText(context, "Đã tắt báo thức", Toast.LENGTH_SHORT).show()
        } else {
            val sleepMinutes = (targetCycles * 90) + 10 // 10 minutes to fall asleep
            val triggerTimeMs = System.currentTimeMillis() + (sleepMinutes * 60 * 1000)
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val alarmTimeString = sdf.format(Date(triggerTimeMs))

            // Save to AlarmStorage
            val newAlarm = AlarmConfig(
                id = 1,
                triggerTimeMs = triggerTimeMs,
                cycles = targetCycles,
                fallAsleepMinutes = 10,
                isEnabled = true,
                ringtoneName = "Mặc định hệ thống",
                alarmType = "single"
            )
            storage.saveAlarm(newAlarm)
            prefs.edit().putString("widget_2x2_active_source", targetSource).apply()

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
                "Đã đặt báo thức sinh học: $alarmTimeString ($targetCycles chu kỳ)"
            } else {
                "Đã đặt báo thức: $alarmTimeString ($targetCycles chu kỳ)"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }

        // Update all 2x2 widgets
        val manager = AppWidgetManager.getInstance(context)
        val ids2x2 = manager.getAppWidgetIds(ComponentName(context, Widget2x2Provider::class.java))
        for (id in ids2x2) {
            updateWidget(context, manager, id)
        }

        // Also update 1x4 and SleepCycle widgets to keep them in sync if registered
        try {
            val ids1x4 = manager.getAppWidgetIds(ComponentName(context, Widget1x4Provider::class.java))
            for (id in ids1x4) {
                Widget1x4Provider.updateWidget(context, manager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val idsSleep = manager.getAppWidgetIds(ComponentName(context, SleepCycleWidgetProvider::class.java))
            if (idsSleep != null && idsSleep.isNotEmpty()) {
                val intentSleep = Intent(context, SleepCycleWidgetProvider::class.java).apply {
                    setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, idsSleep)
                }
                context.sendBroadcast(intentSleep)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_2x2_layout)
            val storage = AlarmStorage(context)
            val alarm = storage.getAlarm()

            val isAlarmActive = alarm.isEnabled && alarm.triggerTimeMs > System.currentTimeMillis()
            val prefs = context.getSharedPreferences("sleep_cycle_alarm_prefs", Context.MODE_PRIVATE)
            val activeSource = prefs.getString("widget_2x2_active_source", "") ?: ""

            val effectiveActiveSource = if (activeSource.isNotEmpty()) {
                activeSource
            } else if (isAlarmActive) {
                when (alarm.cycles) {
                    3 -> "cycles_3"
                    4 -> "cycles_4"
                    5 -> "bio"
                    else -> "bio"
                }
            } else {
                ""
            }

            if (isAlarmActive) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeStr = sdf.format(Date(alarm.triggerTimeMs))
                views.setTextViewText(R.id.widget_2x2_status, "$timeStr\n(${alarm.cycles} chu kỳ)")
            } else {
                views.setTextViewText(R.id.widget_2x2_status, "Chưa đặt\nbáo thức")
            }

            // Defaults (normal states)
            views.setTextViewText(R.id.btn_cycles_3_2x2, "3 CK")
            views.setInt(R.id.btn_cycles_3_2x2, "setBackgroundResource", R.drawable.bg_btn_cycles_3)
            views.setTextColor(R.id.btn_cycles_3_2x2, android.graphics.Color.WHITE)

            views.setTextViewText(R.id.btn_cycles_4_2x2, "4 CK")
            views.setInt(R.id.btn_cycles_4_2x2, "setBackgroundResource", R.drawable.bg_btn_cycles_4)
            views.setTextColor(R.id.btn_cycles_4_2x2, android.graphics.Color.WHITE)

            views.setTextViewText(R.id.btn_cycles_5_2x2, "5 CK")
            views.setInt(R.id.btn_cycles_5_2x2, "setBackgroundResource", R.drawable.bg_btn_cycles_5)
            views.setTextColor(R.id.btn_cycles_5_2x2, android.graphics.Color.WHITE)

            views.setTextViewText(R.id.btn_set_bio_2x2, "Đặt Báo Thức Sinh Học")
            views.setInt(R.id.btn_set_bio_2x2, "setBackgroundResource", R.drawable.bg_btn_set_bio)
            views.setTextColor(R.id.btn_set_bio_2x2, android.graphics.Color.WHITE)

            // Highlight the active button if there is a running alarm
            if (isAlarmActive) {
                val darkRose = android.graphics.Color.parseColor("#9D174D") // Beautiful dark pink text color
                when (effectiveActiveSource) {
                    "cycles_3" -> {
                        views.setInt(R.id.btn_cycles_3_2x2, "setBackgroundResource", R.drawable.bg_btn_active_pink_6dp)
                        views.setTextColor(R.id.btn_cycles_3_2x2, darkRose)
                    }
                    "cycles_4" -> {
                        views.setInt(R.id.btn_cycles_4_2x2, "setBackgroundResource", R.drawable.bg_btn_active_pink_6dp)
                        views.setTextColor(R.id.btn_cycles_4_2x2, darkRose)
                    }
                    "cycles_5" -> {
                        views.setInt(R.id.btn_cycles_5_2x2, "setBackgroundResource", R.drawable.bg_btn_active_pink_6dp)
                        views.setTextColor(R.id.btn_cycles_5_2x2, darkRose)
                    }
                    "bio" -> {
                        views.setTextViewText(R.id.btn_set_bio_2x2, "Tắt Báo Thức")
                        views.setInt(R.id.btn_set_bio_2x2, "setBackgroundResource", R.drawable.bg_btn_active_pink_8dp)
                        views.setTextColor(R.id.btn_set_bio_2x2, darkRose)
                    }
                }
            }

            // Click pending intents configuration
            views.setOnClickPendingIntent(R.id.btn_cycles_3_2x2, getPendingIntent(context, "com.example.widget.ACTION_2X2_SET_CYCLES_3"))
            views.setOnClickPendingIntent(R.id.btn_cycles_4_2x2, getPendingIntent(context, "com.example.widget.ACTION_2X2_SET_CYCLES_4"))
            views.setOnClickPendingIntent(R.id.btn_cycles_5_2x2, getPendingIntent(context, "com.example.widget.ACTION_2X2_SET_CYCLES_5"))
            views.setOnClickPendingIntent(R.id.btn_set_bio_2x2, getPendingIntent(context, "com.example.widget.ACTION_SET_BIO_2X2"))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, Widget2x2Provider::class.java).apply {
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

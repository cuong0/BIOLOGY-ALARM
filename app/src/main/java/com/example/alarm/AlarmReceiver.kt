package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val storage = AlarmStorage(context)
        val alarm = storage.getAlarm()
        
        // Ensure alarm is active
        if (!alarm.isEnabled) return
        
        // Handle Repeat logic: "lặp lại 5 lần, mỗi lần cách nhau 5 phút"
        if (alarm.alarmType == "repeat") {
            val currentRepeat = storage.getRepeatCount()
            if (currentRepeat < 4) { // 1st ring is 0, so 0, 1, 2, 3, 4 represent the 5 rings
                val nextTriggerTimeMs = System.currentTimeMillis() + (5 * 60 * 1000)
                val nextAlarm = alarm.copy(
                    triggerTimeMs = nextTriggerTimeMs,
                    isEnabled = true
                )
                storage.saveAlarm(nextAlarm)
                storage.setRepeatCount(currentRepeat + 1)
                
                // Schedule the next repeat
                scheduleNextAlarm(context, nextTriggerTimeMs)
            } else {
                // Completed all 5 rings, deactivate the alarm
                storage.saveAlarm(alarm.copy(isEnabled = false))
                storage.setRepeatCount(0)
            }
        } else {
            // Deactivate the single alarm trigger state
            storage.saveAlarm(alarm.copy(isEnabled = false))
        }
        
        // Start Alarm Ringing Service
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("RINGTONE_URI", alarm.ringtoneUri)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun scheduleNextAlarm(context: Context, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            intent,
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

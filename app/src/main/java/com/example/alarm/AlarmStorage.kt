package com.example.alarm

import android.content.Context
import android.net.Uri

data class AlarmConfig(
    val id: Int = 1,
    val triggerTimeMs: Long = 0,
    val cycles: Int = 3,
    val fallAsleepMinutes: Int = 0,
    val isEnabled: Boolean = false,
    val ringtoneName: String = "Điệu chuông mặc định",
    val ringtoneUri: String = "",
    val alarmType: String = "single", // "single", "repeat", "custom"
    val daysOfWeek: String = ""       // comma-separated e.g., "1,2,3" for Mon, Tue, Wed (1=Mon, 7=Sun)
) {
    val totalSleepMinutes: Int
        get() = (cycles * 90) + fallAsleepMinutes
}

class AlarmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("sleep_cycle_alarm_prefs", Context.MODE_PRIVATE)

    fun saveAlarm(config: AlarmConfig) {
        prefs.edit().apply {
            putLong("trigger_time_ms", config.triggerTimeMs)
            putInt("cycles", config.cycles)
            putInt("fall_asleep_minutes", config.fallAsleepMinutes)
            putBoolean("is_enabled", config.isEnabled)
            putString("ringtone_name", config.ringtoneName)
            putString("ringtone_uri", config.ringtoneUri)
            putString("alarm_type", config.alarmType)
            putString("days_of_week", config.daysOfWeek)
            apply()
        }
    }

    fun getAlarm(): AlarmConfig {
        val triggerTimeMs = prefs.getLong("trigger_time_ms", 0)
        val cycles = prefs.getInt("cycles", 3)
        val fallAsleepMinutes = prefs.getInt("fall_asleep_minutes", 0)
        val isEnabled = prefs.getBoolean("is_enabled", false)
        val ringtoneName = prefs.getString("ringtone_name", "Điệu chuông mặc định") ?: "Điệu chuông mặc định"
        val ringtoneUri = prefs.getString("ringtone_uri", "") ?: ""
        val alarmType = prefs.getString("alarm_type", "single") ?: "single"
        val daysOfWeek = prefs.getString("days_of_week", "") ?: ""
        return AlarmConfig(1, triggerTimeMs, cycles, fallAsleepMinutes, isEnabled, ringtoneName, ringtoneUri, alarmType, daysOfWeek)
    }

    fun clearAlarm() {
        prefs.edit().apply {
            putBoolean("is_enabled", false)
            putLong("trigger_time_ms", 0)
            putInt("repeat_count", 0)
            apply()
        }
    }

    fun getRepeatCount(): Int {
        return prefs.getInt("repeat_count", 0)
    }

    fun setRepeatCount(count: Int) {
        prefs.edit().putInt("repeat_count", count).apply()
    }
}

package com.example.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateString: String,           // Format: "dd/MM/yyyy"
    val cycles: Int,                  // Number of sleep cycles
    val totalSleepMinutes: Int,       // Total sleep minutes (cycles * 90 + fallAsleepMinutes)
    val timestamp: Long = System.currentTimeMillis()
)

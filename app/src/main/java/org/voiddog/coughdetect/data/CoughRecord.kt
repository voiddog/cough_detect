package org.voiddog.coughdetect.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

// 音频事件类型枚举
enum class AudioEventType(val displayName: String, val color: Long) {
    COUGH("咳嗽", 0xFFFF5722),
    SNORING("打鼾", 0xFF2196F3),
    UNKNOWN("未知", 0xFF9E9E9E)
}

@Entity(tableName = "cough_records")
data class CoughRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val audioFilePath: String,
    val duration: Long, // 音频时长（毫秒）
    val confidence: Float = 0.0f, // 检测置信度
    val amplitude: Float = 0.0f, // 音频振幅
    val eventType: String = AudioEventType.COUGH.name, // 音频事件类型
    val createdAt: Date = Date()
) {
    fun getFormattedTimestamp(): String {
        val date = Date(timestamp)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    fun getDurationInSeconds(): Float {
        return duration / 1000.0f
    }
    
    fun getAudioEventType(): AudioEventType {
        return try {
            AudioEventType.valueOf(eventType)
        } catch (e: IllegalArgumentException) {
            AudioEventType.UNKNOWN
        }
    }
    
    fun getEventDisplayName(): String {
        return getAudioEventType().displayName
    }
    
    fun getEventColor(): Long {
        return getAudioEventType().color
    }
}
package org.voiddog.coughdetect.data

data class Settings(
    val maxAudioCacheSizeMB: Long = 1024, // 默认1GB
    val gaodeApiKey: String = "" // 高德API Key
)
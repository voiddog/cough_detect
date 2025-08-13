package org.voiddog.coughdetect.utils

/**
 * Application-wide constants
 */
object Constants {
    
    // Audio Configuration
    object Audio {
        const val SAMPLE_RATE = 16000 // 16kHz sample rate
        const val CHANNEL_COUNT = 1 // Mono
        const val BITS_PER_SAMPLE = 16 // 16-bit PCM
        const val BUFFER_SIZE_FACTOR = 2
        const val CHUNK_DURATION_MS = 1000L // 1 second chunks
        const val MIN_AMPLITUDE_THRESHOLD = 0.01f
        const val MAX_RECORDING_DURATION_MS = 300000L // 5 minutes max
    }
    
    // Cough Detection
    object Detection {
        const val MIN_COUGH_DURATION_MS = 200L
        const val MAX_COUGH_DURATION_MS = 3000L
        const val COUGH_CONFIDENCE_THRESHOLD = 0.5f
        const val RULE_BASED_CONFIDENCE_THRESHOLD = 0.6f
        const val FEATURE_SIZE = 40 // MFCC features
        const val TIME_FRAMES = 98 // Number of time frames for MFCC
        const val PRE_EMPHASIS_COEFFICIENT = 0.97f
    }
    
    // TensorFlow Lite
    object TensorFlow {
        const val MODEL_FILENAME = "cough_detection_model.tflite"
        const val INPUT_SIZE = 16000 // 1 second at 16kHz
        const val OUTPUT_SIZE = 2 // [not_cough, cough] probabilities
        const val NUM_THREADS = 4
        const val USE_NNAPI = true
        const val USE_GPU = false
    }
    
    // Database
    object Database {
        const val DATABASE_NAME = "cough_detect_database"
        const val DATABASE_VERSION = 1
        const val TABLE_COUGH_RECORDS = "cough_records"
    }
    
    // File Storage
    object Storage {
        const val AUDIO_DIRECTORY = "cough_audio"
        const val AUDIO_FILE_EXTENSION = ".wav"
        const val DATE_FORMAT_FILENAME = "yyyyMMdd_HHmmss_SSS"
        const val DATE_FORMAT_DISPLAY = "yyyy-MM-dd HH:mm:ss"
        const val MAX_STORAGE_SIZE_MB = 500 // 500MB max storage
    }
    
    // UI
    object UI {
        const val ANIMATION_DURATION_MS = 300
        const val SNACKBAR_DURATION_MS = 3000L
        const val AUDIO_LEVEL_UPDATE_INTERVAL_MS = 50L
        const val PULSE_ANIMATION_SPEED = 0.01f
        const val MAX_RECORDS_DISPLAY = 100
    }
    
    // Audio Processing
    object AudioProcessing {
        const val WINDOW_SIZE = 512
        const val HOP_SIZE = 160
        const val N_MFCC = 13
        const val N_MEL = 40
        const val FMIN = 0f
        const val FMAX = 8000f // Nyquist frequency
    }
    
    // Signal Processing
    object SignalProcessing {
        const val ZCR_THRESHOLD_MIN = 0.05f
        const val ZCR_THRESHOLD_MAX = 0.3f
        const val SPECTRAL_CENTROID_THRESHOLD = 1000f
        const val SPECTRAL_ROLLOFF_THRESHOLD = 4000f
        const val AMPLITUDE_THRESHOLD = 0.1f
        const val ENERGY_THRESHOLD = 1e-8f
    }
    
    // Error Messages
    object ErrorMessages {
        const val AUDIO_RECORD_INIT_FAILED = "AudioRecord初始化失败"
        const val PERMISSION_DENIED = "需要录音权限才能进行咳嗽检测"
        const val STORAGE_PERMISSION_DENIED = "需要存储权限才能保存音频文件"
        const val MODEL_LOAD_FAILED = "TensorFlow模型加载失败"
        const val AUDIO_SAVE_FAILED = "音频文件保存失败"
        const val DATABASE_ERROR = "数据库操作失败"
        const val RECORDING_START_FAILED = "录音启动失败"
        const val RECORDING_ERROR = "录音过程中出错"
        const val INSUFFICIENT_STORAGE = "存储空间不足"
        const val FILE_NOT_FOUND = "文件未找到"
        const val INVALID_AUDIO_FORMAT = "不支持的音频格式"
    }
    
    // Success Messages
    object SuccessMessages {
        const val DETECTION_STARTED = "开始咳嗽检测"
        const val DETECTION_PAUSED = "检测已暂停"
        const val DETECTION_RESUMED = "检测已恢复"
        const val DETECTION_STOPPED = "检测已停止"
        const val RECORDS_CLEARED = "所有记录已清空"
        const val RECORD_DELETED = "记录已删除"
        const val AUDIO_SAVED = "音频已保存"
        const val COUGH_DETECTED = "检测到咳嗽"
    }
    
    // Preferences Keys
    object PreferenceKeys {
        const val DETECTION_SENSITIVITY = "detection_sensitivity"
        const val AUTO_SAVE_ENABLED = "auto_save_enabled"
        const val VIBRATE_ON_DETECTION = "vibrate_on_detection"
        const val SOUND_ON_DETECTION = "sound_on_detection"
        const val MAX_STORAGE_SIZE = "max_storage_size"
        const val DELETE_OLD_RECORDS = "delete_old_records"
        const val CONFIDENCE_THRESHOLD = "confidence_threshold"
        const val FIRST_TIME_SETUP = "first_time_setup"
    }
    
    // Network
    object Network {
        const val CONNECTION_TIMEOUT_MS = 30000L
        const val READ_TIMEOUT_MS = 30000L
        const val WRITE_TIMEOUT_MS = 30000L
        const val MAX_RETRY_ATTEMPTS = 3
    }
    
    // Logging
    object Logging {
        const val MAX_LOG_FILES = 5
        const val MAX_LOG_SIZE_MB = 10
        const val LOG_FILE_EXTENSION = ".log"
        const val LOG_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
    }
    
    // Permissions
    object Permissions {
        const val RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
        const val WRITE_EXTERNAL_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        const val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE
        const val VIBRATE = android.Manifest.permission.VIBRATE
    }
    
    // Intent Actions
    object IntentActions {
        const val ACTION_START_DETECTION = "com.example.coughdetect.START_DETECTION"
        const val ACTION_STOP_DETECTION = "com.example.coughdetect.STOP_DETECTION"
        const val ACTION_EXPORT_DATA = "com.example.coughdetect.EXPORT_DATA"
    }
    
    // Notification
    object Notification {
        const val CHANNEL_ID = "cough_detection_channel"
        const val CHANNEL_NAME = "咳嗽检测通知"
        const val NOTIFICATION_ID = 1001
        const val ONGOING_NOTIFICATION_ID = 1002
    }
    
    // Export/Import
    object Export {
        const val CSV_FILENAME = "cough_records.csv"
        const val JSON_FILENAME = "cough_records.json"
        const val BACKUP_FILENAME = "cough_backup.zip"
        const val EXPORT_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss"
    }
    
    // Statistics
    object Statistics {
        const val STATS_CACHE_DURATION_MS = 60000L // 1 minute
        const val MAX_CHART_POINTS = 50
        const val DEFAULT_TIME_RANGE_DAYS = 7
    }
    
    // Validation
    object Validation {
        const val MIN_AUDIO_LENGTH_MS = 100L
        const val MAX_AUDIO_LENGTH_MS = 10000L
        const val MIN_CONFIDENCE = 0.0f
        const val MAX_CONFIDENCE = 1.0f
        const val MIN_AMPLITUDE = -1.0f
        const val MAX_AMPLITUDE = 1.0f
    }
}
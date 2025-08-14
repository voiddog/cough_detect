package org.voiddog.coughdetect.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioPlayer"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPlayingFile = MutableStateFlow<String?>(null)
    val currentPlayingFile: StateFlow<String?> = _currentPlayingFile.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun playAudioFile(filePath: String): Boolean {
        return try {
            // 停止当前播放
            stop()
            
            val file = File(filePath)
            Log.d(TAG, "检查音频文件是否存在: $filePath, 文件存在: ${file.exists()}, 文件大小: ${if (file.exists()) file.length() else 0}")
            
            if (!file.exists()) {
                Log.w(TAG, "音频文件不存在: $filePath")
                _error.value = "音频文件不存在"
                return false
            }
            
            if (file.length() == 0L) {
                Log.w(TAG, "音频文件为空: $filePath")
                _error.value = "音频文件为空"
                return false
            }
            
            Log.i(TAG, "开始播放音频: $filePath")
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                
                setOnPreparedListener { mp ->
                    Log.d(TAG, "音频准备完成，开始播放")
                    mp.start()
                    _isPlaying.value = true
                    _currentPlayingFile.value = filePath
                }
                
                setOnCompletionListener { mp ->
                    Log.d(TAG, "音频播放完成")
                    _isPlaying.value = false
                    _currentPlayingFile.value = null
                    mp.release()
                    mediaPlayer = null
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "音频播放错误: what=$what, extra=$extra")
                    _error.value = "音频播放失败: what=$what, extra=$extra"
                    _isPlaying.value = false
                    _currentPlayingFile.value = null
                    mp.release()
                    mediaPlayer = null
                    true
                }
                
                prepareAsync()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "播放音频时发生异常", e)
            _error.value = "播放失败: ${e.message}"
            false
        }
    }
    
    fun stop() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
                mediaPlayer = null
                _isPlaying.value = false
                _currentPlayingFile.value = null
                Log.d(TAG, "音频播放已停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止播放时发生异常", e)
        }
    }
    
    fun pause() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    _isPlaying.value = false
                    Log.d(TAG, "音频播放已暂停")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停播放时发生异常", e)
        }
    }
    
    fun resume() {
        try {
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) {
                    mp.start()
                    _isPlaying.value = true
                    Log.d(TAG, "音频播放已恢复")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复播放时发生异常", e)
        }
    }
    
    fun isCurrentlyPlaying(filePath: String): Boolean {
        return _currentPlayingFile.value == filePath && _isPlaying.value
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun release() {
        stop()
        Log.d(TAG, "AudioPlayer资源已释放")
    }
}



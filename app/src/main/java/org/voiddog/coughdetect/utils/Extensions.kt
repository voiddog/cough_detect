package org.voiddog.coughdetect.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Environment
import android.os.Vibrator
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Extension functions for various utility operations
 */

// Context Extensions
fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.vibrate(duration: Long = 100L) {
    try {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(duration)
    } catch (e: Exception) {
        // Ignore vibration errors
    }
}

fun Context.getAudioDirectory(): File {
    val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), Constants.Storage.AUDIO_DIRECTORY)
    if (!audioDir.exists()) {
        audioDir.mkdirs()
    }
    return audioDir
}

fun Context.getAvailableStorageSpace(): Long {
    return try {
        val audioDir = getAudioDirectory()
        val statFs = android.os.StatFs(audioDir.absolutePath)
        statFs.availableBlocksLong * statFs.blockSizeLong
    } catch (e: Exception) {
        0L
    }
}

fun Context.isStorageSpaceAvailable(requiredBytes: Long): Boolean {
    return getAvailableStorageSpace() > requiredBytes
}

// String Extensions
fun String.toFormattedTimestamp(): String {
    return try {
        val timestamp = this.toLong()
        val date = Date(timestamp)
        val formatter = SimpleDateFormat(Constants.Storage.DATE_FORMAT_DISPLAY, Locale.getDefault())
        formatter.format(date)
    } catch (e: Exception) {
        this
    }
}

fun String.isValidAudioPath(): Boolean {
    return try {
        val file = File(this)
        file.exists() && file.canRead() && file.length() > 0
    } catch (e: Exception) {
        false
    }
}

fun String.getFileSize(): Long {
    return try {
        File(this).length()
    } catch (e: Exception) {
        0L
    }
}

// Float Extensions
fun Float.toDecibels(): Float {
    return if (this > 0) 20 * log10(this) else -100f
}

fun Float.normalizeAudioLevel(): Float {
    return ((this + 40) / 40).coerceIn(0f, 1f)
}

fun Float.toPercentage(): String {
    return "${(this * 100).toInt()}%"
}

fun Float.toFormattedConfidence(): String {
    return "${"%.1f".format(this * 100)}%"
}

fun Float.clampAmplitude(): Float {
    return this.coerceIn(Constants.Validation.MIN_AMPLITUDE, Constants.Validation.MAX_AMPLITUDE)
}

// Long Extensions
fun Long.toFormattedDuration(): String {
    val seconds = this / 1000.0
    return when {
        seconds < 1.0 -> "${this}ms"
        seconds < 60.0 -> "${"%.1f".format(seconds)}s"
        else -> {
            val minutes = (seconds / 60).toInt()
            val remainingSeconds = (seconds % 60).toInt()
            "${minutes}m ${remainingSeconds}s"
        }
    }
}

fun Long.isRecentTimestamp(thresholdMs: Long = 5000L): Boolean {
    return System.currentTimeMillis() - this < thresholdMs
}

// Date Extensions
fun Date.formatForFilename(): String {
    val formatter = SimpleDateFormat(Constants.Storage.DATE_FORMAT_FILENAME, Locale.getDefault())
    return formatter.format(this)
}

fun Date.formatForDisplay(): String {
    val formatter = SimpleDateFormat(Constants.Storage.DATE_FORMAT_DISPLAY, Locale.getDefault())
    return formatter.format(this)
}

fun Date.isToday(): Boolean {
    val today = Calendar.getInstance()
    val dateCalendar = Calendar.getInstance().apply { time = this@isToday }
    
    return today.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
           today.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR)
}

// Array Extensions
fun FloatArray.calculateRMS(): Float {
    if (isEmpty()) return 0f
    var sum = 0.0
    forEach { sample ->
        sum += sample * sample
    }
    return sqrt(sum / size).toFloat()
}

fun FloatArray.calculateAmplitude(): Float {
    if (isEmpty()) return 0f
    return map { abs(it) }.average().toFloat()
}

fun FloatArray.calculateZeroCrossingRate(): Float {
    if (size < 2) return 0f
    var crossings = 0
    for (i in 1 until size) {
        if ((this[i] >= 0) != (this[i - 1] >= 0)) {
            crossings++
        }
    }
    return crossings.toFloat() / size
}

fun FloatArray.normalize(): FloatArray {
    if (isEmpty()) return this
    val max = maxOrNull() ?: 1f
    val min = minOrNull() ?: -1f
    val range = max - min
    
    return if (range > 0) {
        map { (it - min) / range * 2f - 1f }.toFloatArray()
    } else {
        this
    }
}

fun FloatArray.applyHammingWindow(): FloatArray {
    return FloatArray(size) { i ->
        val window = 0.54f - 0.46f * cos(2 * PI * i / (size - 1)).toFloat()
        this[i] * window
    }
}

fun FloatArray.toShortArray(): ShortArray {
    return ShortArray(size) { i ->
        (this[i] * 32767).toInt().coerceIn(-32768, 32767).toShort()
    }
}

fun ShortArray.toFloatArray(): FloatArray {
    return FloatArray(size) { i ->
        this[i] / 32768.0f
    }
}

// List Extensions
fun <T> List<T>.takeLast(n: Int): List<T> {
    return if (size <= n) this else drop(size - n)
}

fun List<Float>.average(): Float {
    return if (isEmpty()) 0f else sum() / size
}

fun List<Float>.median(): Float {
    if (isEmpty()) return 0f
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
        sorted[middle]
    }
}

// Flow Extensions
fun <T> Flow<T>.catchAndLog(tag: String): Flow<T> {
    return catch { exception ->
        android.util.Log.e(tag, "Flow error: ${exception.message}", exception)
        emit(null as T) // This might need adjustment based on use case
    }
}

fun Flow<List<*>>.mapToSize(): Flow<Int> {
    return map { it.size }
}

// File Extensions
fun File.ensureDirectoryExists(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

fun File.deleteRecursively(): Boolean {
    return try {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteRecursively() }
        }
        delete()
    } catch (e: Exception) {
        false
    }
}

fun File.getSizeInMB(): Double {
    return length() / (1024.0 * 1024.0)
}

fun File.isAudioFile(): Boolean {
    return name.lowercase().endsWith(Constants.Storage.AUDIO_FILE_EXTENSION)
}

// MediaPlayer Extensions
fun MediaPlayer.playAudioFile(filePath: String, onCompletion: (() -> Unit)? = null) {
    try {
        reset()
        setDataSource(filePath)
        prepareAsync()
        setOnPreparedListener { start() }
        setOnCompletionListener { 
            onCompletion?.invoke()
        }
    } catch (e: Exception) {
        android.util.Log.e("MediaPlayer", "Error playing audio file: ${e.message}", e)
        onCompletion?.invoke()
    }
}

// Math Extensions
fun Double.toDB(): Double {
    return if (this > 0) 20 * log10(this) else -100.0
}

fun Int.coerceInRange(min: Int, max: Int): Int {
    return coerceIn(min, max)
}

// Validation Extensions
fun Float.isValidConfidence(): Boolean {
    return this in Constants.Validation.MIN_CONFIDENCE..Constants.Validation.MAX_CONFIDENCE
}

fun Float.isValidAmplitude(): Boolean {
    return this in Constants.Validation.MIN_AMPLITUDE..Constants.Validation.MAX_AMPLITUDE
}

fun Long.isValidDuration(): Boolean {
    return this in Constants.Validation.MIN_AUDIO_LENGTH_MS..Constants.Validation.MAX_AUDIO_LENGTH_MS
}

// Audio Processing Extensions
fun FloatArray.extractSpectralCentroid(sampleRate: Int): Float {
    if (isEmpty()) return 0f
    
    // Simple approximation using weighted frequency bins
    val fft = performSimpleFFT()
    var weightedSum = 0.0
    var magnitudeSum = 0.0
    
    for (i in fft.indices) {
        val magnitude = sqrt(fft[i].real * fft[i].real + fft[i].imag * fft[i].imag)
        val frequency = i * sampleRate.toDouble() / fft.size
        weightedSum += frequency * magnitude
        magnitudeSum += magnitude
    }
    
    return if (magnitudeSum > 0) (weightedSum / magnitudeSum).toFloat() else 0f
}

fun FloatArray.extractSpectralRolloff(sampleRate: Int, rolloffPercent: Float = 0.85f): Float {
    if (isEmpty()) return 0f
    
    val fft = performSimpleFFT()
    val magnitudes = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }
    val totalEnergy = magnitudes.sum()
    val threshold = totalEnergy * rolloffPercent
    
    var cumulativeEnergy = 0.0
    for (i in magnitudes.indices) {
        cumulativeEnergy += magnitudes[i]
        if (cumulativeEnergy >= threshold) {
            return i * sampleRate.toFloat() / magnitudes.size
        }
    }
    return sampleRate.toFloat() / 2 // Nyquist frequency
}

// Simple FFT implementation for basic spectral analysis
private data class Complex(val real: Double, val imag: Double)

private fun FloatArray.performSimpleFFT(): Array<Complex> {
    val n = size
    val result = Array(n) { Complex(0.0, 0.0) }
    
    for (k in 0 until n) {
        var real = 0.0
        var imag = 0.0
        
        for (t in 0 until n) {
            val angle = -2.0 * PI * k * t / n
            real += this[t] * cos(angle)
            imag += this[t] * sin(angle)
        }
        
        result[k] = Complex(real, imag)
    }
    
    return result
}

// Utility Functions
fun generateUniqueFilename(prefix: String = "cough", extension: String = Constants.Storage.AUDIO_FILE_EXTENSION): String {
    val timestamp = Date().formatForFilename()
    return "${prefix}_${timestamp}${extension}"
}

fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1.0 -> "${"%.2f".format(gb)} GB"
        mb >= 1.0 -> "${"%.2f".format(mb)} MB"
        kb >= 1.0 -> "${"%.2f".format(kb)} KB"
        else -> "$bytes B"
    }
}

fun isValidSampleRate(sampleRate: Int): Boolean {
    val validRates = listOf(8000, 11025, 16000, 22050, 44100, 48000)
    return sampleRate in validRates
}

fun calculateBufferSize(sampleRate: Int, durationMs: Long): Int {
    return (sampleRate * durationMs / 1000).toInt()
}
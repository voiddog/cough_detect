package org.voiddog.coughdetect.plugin

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import org.voiddog.coughdetect.data.CoughRecord
import java.util.concurrent.ConcurrentHashMap

/**
 * GPS 插件，用于记录事件发生时的地理位置信息
 */
class GpsPlugin : AudioEventRecordPlugin {
    companion object {
        private const val TAG = "GpsPlugin"
        private const val LOCATION_CACHE_DURATION_MS = 5 * 60 * 1000L // 5分钟缓存
    }
    
    override val name: String = "gps"
    
    private lateinit var context: Context
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0
    
    // 缓存最近的位置信息
    private val locationCache = ConcurrentHashMap<String, Any>()
    
    override fun initialize(context: Context) {
        this.context = context.applicationContext
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Log.d(TAG, "GPS 插件已初始化")
    }
    
    override fun processRecord(record: CoughRecord): String {
        try {
            // 获取当前位置
            val location = getCurrentLocation()
            
            // 如果没有位置信息，返回空的 JSON 对象
            if (location == null) {
                Log.w(TAG, "无法获取当前位置信息")
                return "{}"
            }
            
            // 构建位置信息 JSON
            val locationJson = StringBuilder()
            locationJson.append("{")
            locationJson.append("\"latitude\":${location.latitude},")
            locationJson.append("\"longitude\":${location.longitude},")
            locationJson.append("\"accuracy\":${location.accuracy},")
            locationJson.append("\"time\":${location.time}")
            locationJson.append("}")
            
            Log.d(TAG, "GPS 插件处理记录: 纬度=${location.latitude}, 经度=${location.longitude}")
            return locationJson.toString()
        } catch (e: Exception) {
            Log.e(TAG, "GPS 插件处理记录时出错", e)
            return "{}"
        }
    }
    
    /**
     * 获取当前位置
     * @return 当前位置信息，如果无法获取则返回 null
     */
    private fun getCurrentLocation(): Location? {
        val currentTime = System.currentTimeMillis()
        
        // 检查缓存是否有效
        if (lastLocation != null && (currentTime - lastLocationTime) < LOCATION_CACHE_DURATION_MS) {
            Log.d(TAG, "使用缓存的位置信息")
            return lastLocation
        }
        
        // 尝试获取最新的位置
        val location = getBestLastKnownLocation()
        if (location != null) {
            lastLocation = location
            lastLocationTime = currentTime
            Log.d(TAG, "获取到新的位置信息并更新缓存")
            return location
        }
        
        // 如果没有位置信息，返回缓存的位置（即使过期了）
        Log.w(TAG, "无法获取新的位置信息，使用缓存的位置信息")
        return lastLocation
    }
    
    /**
     * 获取最佳的最后已知位置
     * @return 最佳位置信息，如果无法获取则返回 null
     */
    private fun getBestLastKnownLocation(): Location? {
        try {
            locationManager?.let { lm ->
                // 尝试获取 GPS 位置
                var location: Location? = null
                try {
                    location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (e: SecurityException) {
                    Log.w(TAG, "没有 GPS 权限", e)
                }
                
                // 如果没有 GPS 位置，尝试获取网络位置
                if (location == null) {
                    try {
                        location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "没有网络位置权限", e)
                    }
                }
                
                return location
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取最后已知位置时出错", e)
        }
        
        return null
    }
}
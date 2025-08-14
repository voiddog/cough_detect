package org.voiddog.coughdetect.plugin

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import org.voiddog.coughdetect.data.CoughRecord
import org.voiddog.coughdetect.data.SettingsManager
import java.net.HttpURLConnection
import java.net.URL

/**
 * GPS 插件，用于记录事件发生时的地理位置信息
 */
class GpsPlugin : AudioEventRecordPlugin {
    companion object {
        private const val TAG = "GpsPlugin"
        private const val LOCATION_CACHE_DURATION_MS = 5 * 60 * 1000L // 5分钟缓存
        private const val GAODE_REVERSE_GEOCODING_URL = "https://restapi.amap.com/v3/geocode/regeo"
    }
    
    override val name: String = "gps"
    
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var lastAddress: String = ""
    private var lastAddressTime: Long = 0
    
    override fun initialize(context: Context) {
        this.context = context.applicationContext
        this.settingsManager = SettingsManager.getInstance(context)
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
            
            // 获取地址信息
            val address = getCurrentAddress(location)
            
            // 构建位置信息 JSON
            val locationJson = StringBuilder()
            locationJson.append("{")
            locationJson.append("\"latitude\":${location.latitude},")
            locationJson.append("\"longitude\":${location.longitude},")
            locationJson.append("\"accuracy\":${location.accuracy}")
            
            // 如果有地址信息，添加到 JSON 中
            if (address.isNotEmpty()) {
                locationJson.append(",\"address\":\"${address}\"")
            }
            
            locationJson.append("}")
            
            Log.d(TAG, "GPS 插件处理记录: 纬度=${location.latitude}, 经度=${location.longitude}, 地址=$address")
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
    
    /**
     * 获取当前位置的地址信息
     * @param location 当前位置
     * @return 地址信息，如果无法获取则返回空字符串
     */
    private fun getCurrentAddress(location: Location): String {
        val currentTime = System.currentTimeMillis()
        
        // 检查缓存是否有效 (10分钟)
        if (lastAddress.isNotEmpty() && (currentTime - lastAddressTime) < 10 * 60 * 1000L) {
            Log.d(TAG, "使用缓存的地址信息")
            return lastAddress
        }
        
        // 获取高德API Key
        val settings = settingsManager.getSettings()
        val apiKey = settings.gaodeApiKey
        
        // 如果没有API Key，不获取地址信息
        if (apiKey.isEmpty()) {
            Log.d(TAG, "没有配置高德API Key，不获取地址信息")
            return ""
        }
        
        // 尝试获取新的地址信息
        val address = fetchAddressFromGaode(location.latitude, location.longitude, apiKey)
        if (address.isNotEmpty()) {
            lastAddress = address
            lastAddressTime = currentTime
            Log.d(TAG, "获取到新的地址信息并更新缓存: $address")
            return address
        }
        
        // 如果无法获取新的地址信息，返回缓存的地址（即使过期了）
        Log.w(TAG, "无法获取新的地址信息，使用缓存的地址信息")
        return lastAddress
    }
    
    /**
     * 从高德API获取地址信息
     * @param latitude 纬度
     * @param longitude 经度
     * @param apiKey 高德API Key
     * @return 地址信息，如果无法获取则返回空字符串
     */
    private fun fetchAddressFromGaode(latitude: Double, longitude: Double, apiKey: String): String {
        return try {
            // 构建API URL
            val url = "$GAODE_REVERSE_GEOCODING_URL?location=$longitude,$latitude&key=$apiKey"
            
            // 创建 HTTP 连接
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // 5 秒连接超时
            connection.readTimeout = 5000 // 5 秒读取超时
            
            // 检查响应码
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // 解析JSON响应，提取formatted_address
                val address = parseGaodeResponse(response)
                Log.d(TAG, "从高德API获取到地址信息: $address")
                address
            } else {
                Log.e(TAG, "获取地址信息失败，响应码: ${connection.responseCode}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "从高德API获取地址信息时出错", e)
            ""
        }
    }
    
    /**
     * 解析高德API的响应，提取formatted_address
     * @param response API响应
     * @return formatted_address，如果无法解析则返回空字符串
     */
    private fun parseGaodeResponse(response: String): String {
        return try {
            // 简单的字符串解析，实际应用中应该使用JSON解析库
            val statusIndex = response.indexOf("\"status\":\"")
            if (statusIndex != -1) {
                val statusStart = statusIndex + "\"status\":\"".length
                val statusEnd = response.indexOf("\"", statusStart)
                val status = response.substring(statusStart, statusEnd)
                
                // 检查状态码
                if (status == "1") {
                    val addressIndex = response.indexOf("\"formatted_address\":\"")
                    if (addressIndex != -1) {
                        val addressStart = addressIndex + "\"formatted_address\":\"".length
                        val addressEnd = response.indexOf("\"", addressStart)
                        val address = response.substring(addressStart, addressEnd)
                        Log.d(TAG, "解析到地址: $address")
                        return address
                    }
                } else {
                    Log.e(TAG, "高德API返回错误状态: $status")
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "解析高德API响应时出错", e)
            ""
        }
    }
}
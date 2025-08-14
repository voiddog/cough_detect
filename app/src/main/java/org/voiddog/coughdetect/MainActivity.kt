package org.voiddog.coughdetect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import android.util.Log
import android.app.AlertDialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.voiddog.coughdetect.BuildConfig
import org.voiddog.coughdetect.plugin.GpsPlugin
import org.voiddog.coughdetect.plugin.WeatherPlugin
import org.voiddog.coughdetect.ui.CoughDetectionScreen
import org.voiddog.coughdetect.ui.SettingsScreen
import org.voiddog.coughdetect.ui.theme.CoughDetectTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import org.voiddog.coughdetect.viewmodel.CoughDetectionViewModel
import org.voiddog.coughdetect.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: CoughDetectionViewModel by viewModels()
    private var hasCheckedPermissions = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.i(TAG, "权限请求结果: $permissions")

        hasCheckedPermissions = true

        // 检查所有必需的权限是否都被授予
        val allPermissionsGranted = permissions.values.all { it }
        
        if (allPermissionsGranted) {
            Log.i(TAG, "✅ 所有必需权限已授予")
            lifecycleScope.launch {
                // Reinitialize if needed
            }
        } else {
            Log.w(TAG, "⚠️ 部分权限被拒绝，显示权限对话框")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "🚀 MainActivity启动中...")
        Log.d(TAG, "Android版本: ${android.os.Build.VERSION.SDK_INT}, 应用版本: ${BuildConfig.VERSION_NAME}")

        checkAndRequestPermissions()

        // 注册插件
        registerPlugins()

        setContent {
            CoughDetectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 只有在有权限时才显示主界面
                    if (hasCheckedPermissions && arePermissionsGranted()) {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = "main"
                        ) {
                            composable("main") {
                                CoughDetectionScreen(
                                    viewModel = viewModel,
                                    onSettingsClick = { navController.navigate("settings") }
                                )
                            }
                            composable("settings") {
                                val settingsViewModel: SettingsViewModel = viewModel()
                                SettingsScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onGaodeApiKeySave = { apiKey ->
                                        settingsViewModel.updateGaodeApiKey(apiKey)
                                        settingsViewModel.saveSettings()
                                    },
                                    viewModel = settingsViewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        Log.i(TAG, "✅ MainActivity创建完成")
    }

    private fun checkAndRequestPermissions() {
        // 只有在没有检查过权限或者权限未授予时才检查
        if (hasCheckedPermissions) {
            Log.d(TAG, "⏭️ 权限已检查过，跳过重复检查")
            return
        }

        Log.i(TAG, "🔐 检查应用权限...")

        val permissionsToRequest = mutableListOf<String>()
        
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // 检查精确位置权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // 检查粗略位置权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        Log.d(TAG, "需要请求的权限: $permissionsToRequest")

        if (permissionsToRequest.isNotEmpty()) {
            Log.i(TAG, "需要请求权限")
            hasCheckedPermissions = true
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.i(TAG, "✅ 所有必需权限已授予")
            hasCheckedPermissions = true
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionDeniedDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(org.voiddog.coughdetect.R.string.permission_required))
        builder.setMessage(getString(org.voiddog.coughdetect.R.string.permission_rationale))
        
        builder.setPositiveButton(getString(org.voiddog.coughdetect.R.string.grant_permission)) { _, _ ->
            // 重新请求权限
            hasCheckedPermissions = false
            checkAndRequestPermissions()
        }
        
        builder.setNegativeButton(getString(org.voiddog.coughdetect.R.string.exit_app)) { _, _ ->
            // 退出应用
            finish()
        }
        
        builder.setCancelable(false) // 禁止通过返回键取消对话框
        val dialog = builder.create()
        dialog.show()
    }

    override fun onDestroy() {
        Log.i(TAG, "🔚 MainActivity正在销毁...")
        super.onDestroy()
        Log.i(TAG, "✅ MainActivity已销毁")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 MainActivity恢复前台")
        // 重置权限检查标志，以便在必要时重新检查权限
        checkAndRequestPermissions()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "📱 MainActivity进入后台")
    }
    
    private fun registerPlugins() {
        // 注册 GPS 插件
        viewModel.repository.registerPlugin(GpsPlugin())
        
        // 注册天气插件
        viewModel.repository.registerPlugin(WeatherPlugin())
        
        Log.d(TAG, "插件注册完成")
    }
}

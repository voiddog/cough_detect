package org.voiddog.coughdetect.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.voiddog.coughdetect.R
import org.voiddog.coughdetect.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 自动清除消息
    uiState.message?.let { message ->
        LaunchedEffect(message) {
            delay(2000)
            viewModel.clearMessage()
        }
    }
    
    // 自动清除错误
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            delay(3000)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                SettingsContent(
                    uiState = uiState,
                    onMaxAudioCacheSizeChange = { viewModel.updateMaxAudioCacheSize(it) },
                    onSaveClick = { viewModel.saveSettings() }
                )
            }
            
            // 显示错误消息
            uiState.error?.let { errorMessage ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // 显示成功消息
            uiState.message?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsContent(
    uiState: org.voiddog.coughdetect.viewmodel.SettingsUiState,
    onMaxAudioCacheSizeChange: (Long) -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "存储设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // 音频缓存大小设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "音频缓存大小",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "设置应用保存音频文件的最大磁盘空间。当超过此限制时，系统会自动删除最旧的音频文件以释放空间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 显示当前使用的磁盘空间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前已使用:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (uiState.isCalculatingCacheSize) {
                        // 显示加载符号
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("计算中...")
                        }
                    } else {
                        // 显示实际大小
                        Text(
                            text = "${uiState.currentAudioCacheSizeMB} MB / ${uiState.maxAudioCacheSizeMB} MB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.currentAudioCacheSizeMB > uiState.maxAudioCacheSizeMB * 0.8) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
                
                // 进度条
                if (uiState.isCalculatingCacheSize) {
                    // 显示不确定的进度条
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 显示确定的进度条
                    LinearProgressIndicator(
                        progress = (uiState.currentAudioCacheSizeMB.toFloat() / uiState.maxAudioCacheSizeMB.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (uiState.currentAudioCacheSizeMB > uiState.maxAudioCacheSizeMB * 0.8) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                
                // 滑块
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = uiState.maxAudioCacheSizeMB.toFloat(),
                        onValueChange = { 
                            // 限制在100MB到10GB之间
                            val clampedValue = it.coerceIn(100f, 10240f)
                            onMaxAudioCacheSizeChange(clampedValue.toLong())
                        },
                        valueRange = 100f..10240f, // 100MB to 10GB
                        steps = 100,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(
                        text = "${uiState.maxAudioCacheSizeMB} MB",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                
                // 预设选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(512L, 1024L, 2048L, 5120L).forEach { size ->
                        Button(
                            onClick = { onMaxAudioCacheSizeChange(size) },
                            enabled = uiState.maxAudioCacheSizeMB != size,
                            modifier = Modifier.weight(1f),
                            colors = if (uiState.maxAudioCacheSizeMB == size) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        ) {
                            Text("${size}MB")
                        }
                        if (size != 5120L) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
        
        // 高德API Key设置
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "高德API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "用于获取地理位置的详细地址信息。如果留空，则不记录地址信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                var gaodeApiKey by remember { mutableStateOf(uiState.gaodeApiKey) }
                
                OutlinedTextField(
                    value = gaodeApiKey,
                    onValueChange = { gaodeApiKey = it },
                    label = { Text("高德API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Button(
                    onClick = { 
                        // 更新API Key并保存设置
                        // 这里我们只是更新状态，实际的保存操作会在其他地方进行
                        // 或者我们可以考虑使用一个回调函数来处理保存操作
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = gaodeApiKey != uiState.gaodeApiKey
                ) {
                    Text("保存API Key")
                }
            }
        }
        
        // 保存按钮
        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("保存设置")
        }
    }
}
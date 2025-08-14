package org.voiddog.coughdetect.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.voiddog.coughdetect.R
import org.voiddog.coughdetect.data.CoughRecord
import org.voiddog.coughdetect.repository.CoughDetectionRepository
import org.voiddog.coughdetect.viewmodel.CoughDetectionViewModel
import kotlinx.coroutines.delay
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoughDetectionScreen(
    viewModel: CoughDetectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val detectionState by viewModel.detectionState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val lastDetectionResult by viewModel.lastDetectionResult.collectAsState()
    val coughRecords by viewModel.coughRecords.collectAsState()
    val error by viewModel.error.collectAsState()

    // 权限处理已移至MainActivity，此处不再显示权限对话框

    // Show clear confirmation dialog
    if (uiState.showClearConfirmDialog) {
        ClearConfirmDialog(
            onConfirm = { viewModel.clearAllRecords() },
            onDismiss = { viewModel.hideClearConfirmDialog() }
        )
    }

    // Show error snackbar
    error?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            // Auto-dismiss error after 3 seconds
            delay(3000)
            viewModel.clearError()
        }
    }

    // Show message snackbar
    uiState.message?.let { message ->
        LaunchedEffect(message) {
            delay(2000)
            viewModel.clearMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Text(
                text = stringResource(R.string.cough_detection_app),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            // Detection Status Card
            DetectionStatusCard(
                detectionState = detectionState,
                audioLevel = audioLevel,
                lastDetectionResult = lastDetectionResult,
                onMainButtonClick = { viewModel.onMainButtonClick() },
                onStopClick = { viewModel.stopDetection() },
                mainButtonText = viewModel.getMainButtonText(),
                statusText = viewModel.getDetectionStatusText(),
                isLoading = uiState.isLoading
            )
        }

        item {
            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.showClearConfirmDialog() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = coughRecords.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_records))
                }
            }
        }

        item {
            // Statistics Card
            StatisticsCard(
                recordCount = coughRecords.size,
                viewModel = viewModel
            )
        }

        item {
            // Audio Events List
            AudioEventsList(
                records = coughRecords,
                onDeleteRecord = { viewModel.deleteRecord(it) },
                onPlayRecord = { viewModel.playRecord(it) },
                viewModel = viewModel
            )
        }
    }

    // Error display overlay
    error?.let { errorMessage ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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

    // Message display overlay
    uiState.message?.let { message ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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

@Composable
fun DetectionStatusCard(
    detectionState: CoughDetectionRepository.DetectionState,
    audioLevel: Float,
    lastDetectionResult: CoughDetectionRepository.CoughDetectionResult?,
    onMainButtonClick: () -> Unit,
    onStopClick: () -> Unit,
    mainButtonText: String,
    statusText: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Text
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when (detectionState) {
                    CoughDetectionRepository.DetectionState.RECORDING -> Color(0xFF4CAF50)
                    CoughDetectionRepository.DetectionState.PAUSED -> Color(0xFFFF9800)
                    CoughDetectionRepository.DetectionState.PROCESSING -> Color(0xFF2196F3)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Level Visualizer
            AudioLevelVisualizer(
                level = audioLevel,
                isRecording = detectionState == CoughDetectionRepository.DetectionState.RECORDING,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Last Detection Result
            lastDetectionResult?.let { result ->
                AnimatedVisibility(
                    visible = result.isCough,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5722).copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5722)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "咳嗽检测 (${(result.confidence * 100).toInt()}%)",
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Control Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onMainButtonClick,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && detectionState != CoughDetectionRepository.DetectionState.PROCESSING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (detectionState) {
                            CoughDetectionRepository.DetectionState.RECORDING -> Color(0xFFFF9800)
                            CoughDetectionRepository.DetectionState.PAUSED -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            when (detectionState) {
                                CoughDetectionRepository.DetectionState.RECORDING -> Icons.Default.PlayArrow
                                CoughDetectionRepository.DetectionState.PAUSED -> Icons.Default.PlayArrow
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mainButtonText)
                    }
                }

                if (detectionState != CoughDetectionRepository.DetectionState.IDLE) {
                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
}

@Composable
fun AudioLevelVisualizer(
    level: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    var animatedLevel by remember { mutableStateOf(0f) }
    
    LaunchedEffect(level) {
        animatedLevel = level
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAudioLevelCircle(animatedLevel, isRecording, this)
        }
        
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.primary
        )
    }
}

fun drawAudioLevelCircle(level: Float, isRecording: Boolean, drawScope: DrawScope) {
    val center = Offset(drawScope.size.width / 2, drawScope.size.height / 2)
    val baseRadius = drawScope.size.minDimension / 4
    val maxRadius = drawScope.size.minDimension / 2

    // Background circle
    drawScope.drawCircle(
        color = if (isRecording) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
        radius = maxRadius,
        center = center
    )

    // Level circle
    val levelRadius = baseRadius + (maxRadius - baseRadius) * level
    drawScope.drawCircle(
        color = if (isRecording) Color(0xFF4CAF50) else Color.Gray,
        radius = levelRadius,
        center = center
    )

    // Pulsing effect when recording
    if (isRecording && level > 0.1f) {
        val pulseRadius = levelRadius + sin(System.currentTimeMillis() * 0.01).toFloat() * 10
        drawScope.drawCircle(
            color = Color(0xFF4CAF50).copy(alpha = 0.5f),
            radius = pulseRadius,
            center = center
        )
    }
}

@Composable
fun StatisticsCard(
    recordCount: Int,
    viewModel: CoughDetectionViewModel
) {
    val detectionState by viewModel.detectionState.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val lastDetectionResult by viewModel.lastDetectionResult.collectAsState()
    val coughRecords by viewModel.coughRecords.collectAsState()
    
    var averageConfidence by remember { mutableStateOf<Float?>(null) }
    
    // 使用 coughRecords 的变化来触发统计信息更新
    LaunchedEffect(coughRecords.size) {
        averageConfidence = viewModel.getAverageConfidence()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "实时统计信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 第一行：检测状态和音频电平
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "检测状态",
                    value = when (detectionState) {
                        CoughDetectionRepository.DetectionState.IDLE -> "空闲"
                        CoughDetectionRepository.DetectionState.RECORDING -> "录制中"
                        CoughDetectionRepository.DetectionState.PAUSED -> "暂停"
                        CoughDetectionRepository.DetectionState.PROCESSING -> "处理中"
                    },
                    icon = Icons.Default.Info,
                    valueColor = when (detectionState) {
                        CoughDetectionRepository.DetectionState.RECORDING -> Color(0xFF4CAF50)
                        CoughDetectionRepository.DetectionState.PAUSED -> Color(0xFFFF9800)
                        CoughDetectionRepository.DetectionState.PROCESSING -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                StatItem(
                    label = "音频电平",
                    value = "${(audioLevel * 100).toInt()}%",
                    icon = Icons.Default.Info,
                    valueColor = if (audioLevel > 0.3f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 第二行：检测次数和置信度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "检测次数",
                    value = coughRecords.size.toString(),
                    icon = Icons.Default.List
                )
                
                StatItem(
                    label = "最近置信度",
                    value = lastDetectionResult?.let { 
                        if (it.isCough) "${(it.confidence * 100).toInt()}%" else "--"
                    } ?: "--",
                    icon = Icons.Default.Info,
                    valueColor = lastDetectionResult?.let {
                        if (it.isCough && it.confidence > 0.7f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                    } ?: MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 第三行：平均置信度和最后检测时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "平均置信度",
                    value = averageConfidence?.let { "${(it * 100).toInt()}%" } ?: "--",
                    icon = Icons.Default.Info
                )
                
                StatItem(
                    label = "最后检测",
                    value = lastDetectionResult?.let {
                        val now = System.currentTimeMillis()
                        val diff = now - it.timestamp
                        when {
                            diff < 60000 -> "${diff / 1000}秒前"
                            diff < 3600000 -> "${diff / 60000}分钟前"
                            else -> "${diff / 3600000}小时前"
                        }
                    } ?: "--",
                    icon = Icons.Default.Info
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AudioEventsList(
    records: List<CoughRecord>,
    onDeleteRecord: (CoughRecord) -> Unit,
    onPlayRecord: (CoughRecord) -> Unit,
    viewModel: CoughDetectionViewModel
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPlayingFile by viewModel.currentPlayingFile.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "音频事件记录 (${records.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (records.isEmpty()) {
                Text(
                    text = "暂无音频事件记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // 不使用嵌套的LazyColumn，直接展示所有记录
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    records.forEach { record ->
                        AudioEventItem(
                            record = record,
                            onDelete = { onDeleteRecord(record) },
                            onPlay = { onPlayRecord(record) },
                            isPlaying = currentPlayingFile == record.audioFilePath && isPlaying,
                            onStop = { viewModel.stopPlayback() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEventItem(
    record: CoughRecord,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    isPlaying: Boolean = false,
    onStop: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/停止按钮
            IconButton(
                onClick = if (isPlaying) onStop else onPlay,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止播放" else "播放音频",
                    tint = if (isPlaying) Color(0xFF4CAF50) else Color(0xFFFF5722),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 事件类型图标
            Icon(
                when (record.getAudioEventType()) {
                    org.voiddog.coughdetect.data.AudioEventType.COUGH -> Icons.Default.Warning
                    org.voiddog.coughdetect.data.AudioEventType.SNORING -> Icons.Default.Settings
                    org.voiddog.coughdetect.data.AudioEventType.UNKNOWN -> Icons.Default.Info
                },
                contentDescription = "${record.getEventDisplayName()}事件",
                tint = Color(record.getEventColor()),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.getEventDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(record.getEventColor())
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.getFormattedTimestamp(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "时长: ${"%.1f".format(record.getDurationInSeconds())}秒 | " +
                            "置信度: ${(record.confidence * 100).toInt()}% | " +
                            "振幅: ${"%.2f".format(record.amplitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "需要录音权限")
        },
        text = {
            Text(text = stringResource(R.string.permission_denied))
        },
        confirmButton = {
            TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ClearConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "确认清空")
        },
        text = {
            Text(text = stringResource(R.string.confirm_clear_all))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
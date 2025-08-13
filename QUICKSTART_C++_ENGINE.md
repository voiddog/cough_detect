# 咳嗽检测器 C++ 引擎快速开始指南

## 概述

本项目已重构为使用高性能的 C++ 引擎来实现咳嗽检测功能。新的架构提供了更好的性能和更低的延迟。

## 快速开始

### 1. 环境要求

- Android Studio Arctic Fox 或更高版本
- Android NDK 25.2.9519653 或更高版本
- CMake 3.22.1 或更高版本
- Android SDK API 24 或更高版本

### 2. 项目设置

#### 2.1 克隆项目
```bash
git clone <repository-url>
cd cough_detect
```

#### 2.2 设置环境变量
```bash
# 设置 Android SDK 路径
export ANDROID_HOME=/path/to/android/sdk

# 设置 Android NDK 路径
export ANDROID_NDK_HOME=/path/to/android/ndk
```

#### 2.3 构建 C++ 库
```bash
# 使用提供的构建脚本
./build_native.sh

# 或者在 Android Studio 中直接构建
# Build -> Make Project
```

### 3. 运行应用

#### 3.1 在 Android Studio 中
1. 打开项目
2. 同步 Gradle 文件
3. 构建项目
4. 在设备上运行应用

#### 3.2 命令行构建
```bash
# 构建 APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 使用新的 C++ 引擎

### 1. 基本使用

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var engine: CoughDetectEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建引擎实例
        engine = CoughDetectEngine(this)
        
        // 初始化引擎
        lifecycleScope.launch {
            val success = engine.initialize()
            if (success) {
                Log.d("MainActivity", "引擎初始化成功")
            } else {
                Log.e("MainActivity", "引擎初始化失败")
            }
        }
    }
}
```

### 2. 监听事件

```kotlin
// 监听引擎状态
lifecycleScope.launch {
    engine.engineState.collect { state ->
        when (state) {
            CoughDetectEngine.EngineState.IDLE -> {
                // 空闲状态
                updateUI("准备就绪")
            }
            CoughDetectEngine.EngineState.RECORDING -> {
                // 正在录制
                updateUI("正在检测...")
            }
            CoughDetectEngine.EngineState.PAUSED -> {
                // 暂停状态
                updateUI("检测已暂停")
            }
            CoughDetectEngine.EngineState.PROCESSING -> {
                // 处理中
                updateUI("处理中...")
            }
        }
    }
}

// 监听音频级别
lifecycleScope.launch {
    engine.audioLevel.collect { level ->
        // 更新音频可视化
        updateAudioVisualization(level)
    }
}

// 监听咳嗽检测事件
lifecycleScope.launch {
    engine.lastAudioEvent.collect { event ->
        event?.let {
            when (it.type) {
                CoughDetectEngine.AudioEventType.COUGH_DETECTED -> {
                    // 检测到咳嗽
                    showCoughNotification(it.confidence)
                    saveCoughRecord(it)
                }
                CoughDetectEngine.AudioEventType.ERROR_OCCURRED -> {
                    // 处理错误
                    showError("检测出错")
                }
                else -> {
                    // 其他事件
                }
            }
        }
    }
}
```

### 3. 控制检测

```kotlin
// 开始检测
fun startDetection() {
    lifecycleScope.launch {
        val success = engine.start()
        if (success) {
            Log.d("MainActivity", "检测已开始")
        } else {
            Log.e("MainActivity", "启动检测失败")
        }
    }
}

// 暂停检测
fun pauseDetection() {
    engine.pause()
}

// 恢复检测
fun resumeDetection() {
    engine.resume()
}

// 停止检测
fun stopDetection() {
    engine.stop()
}
```

### 4. 清理资源

```kotlin
override fun onDestroy() {
    super.onDestroy()
    engine.release()
}
```

## 性能优化

### 1. 音频缓冲区优化

在 `audio_recorder.cpp` 中调整缓冲区大小：

```cpp
// 调整帧数以获得最佳性能
framesPerCallback_ = sampleRate_ / 10; // 100ms 块
```

### 2. 检测算法优化

在 `tensorflow_wrapper.cpp` 中调整检测参数：

```cpp
// 调整咳嗽检测阈值
float coughThreshold_ = 0.5f;

// 调整音频特征提取参数
bool hasHighAmplitude = amplitude > 0.1f;
bool hasModerateZCR = zeroCrossingRate > 0.05f && zeroCrossingRate < 0.3f;
```

### 3. 内存管理

确保正确释放资源：

```kotlin
// 在适当的生命周期中释放资源
override fun onPause() {
    super.onPause()
    engine.pause()
}

override fun onDestroy() {
    super.onDestroy()
    engine.release()
}
```

## 故障排除

### 1. 编译错误

**问题**: CMake 配置错误
```
解决方案:
- 检查 NDK 版本是否为 25.2.9519653 或更高
- 确保 CMake 版本为 3.22.1 或更高
- 验证 ANDROID_HOME 和 ANDROID_NDK_HOME 环境变量

**问题**: 找不到 Oboe 或 TensorFlow 库
```
解决方案:
- 确保在 build.gradle.kts 中正确配置了依赖
- 检查 CMakeLists.txt 中的库链接配置
- 尝试清理并重新构建项目
```

### 2. 运行时错误

**问题**: 应用崩溃
```
解决方案:
- 检查 logcat 输出查看详细错误信息
- 确保正确初始化了引擎
- 验证权限设置（录音权限）
```

**问题**: 音频录制不工作
```
解决方案:
- 检查录音权限是否已授予
- 验证音频设备是否可用
- 检查 Oboe 库是否正确链接
```

### 3. 性能问题

**问题**: 检测延迟高
```
解决方案:
- 调整音频缓冲区大小
- 优化检测算法参数
- 使用更高效的音频处理算法
```

**问题**: 内存使用过高
```
解决方案:
- 及时释放音频数据
- 优化缓冲区管理
- 使用内存池减少分配开销
```

## 调试技巧

### 1. 启用详细日志

在 C++ 代码中添加更多日志：

```cpp
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
```

### 2. 使用 Android Studio 调试

1. 在 C++ 代码中设置断点
2. 使用 Android Studio 的 Native Debugger
3. 查看 Variables 窗口检查变量值

### 3. 性能分析

使用 Android Studio 的 Profiler：
1. CPU Profiler 分析 C++ 函数调用
2. Memory Profiler 检查内存使用
3. Energy Profiler 监控电池消耗

## 下一步

1. **集成真实的 TensorFlow 模型**: 替换规则基础检测为真实的 ML 模型
2. **添加更多音频特征**: 实现更复杂的音频分析算法
3. **优化性能**: 使用 GPU 加速和并行处理
4. **添加云端功能**: 集成云端分析和存储

## 支持

如果遇到问题，请：
1. 查看 `C++_ENGINE_README.md` 获取详细文档
2. 检查 logcat 输出获取错误信息
3. 运行测试用例验证功能
4. 提交 issue 描述问题 
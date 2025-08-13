# 咳嗽检测器 C++ 引擎架构

本项目已经重构为使用高性能的 C++ 引擎来实现咳嗽检测功能。

## 架构概述

### 1. C++ 层 (Native Layer)
- **CoughDetectEngine**: 主要的检测引擎，协调音频录制和咳嗽检测
- **AudioRecorder**: 使用 Oboe 实现高性能音频录制
- **TensorFlowWrapper**: 封装 TensorFlow Lite 模型推理
- **JNI Interface**: Java 和 C++ 之间的接口

### 2. Java/Kotlin 层
- **CoughDetectEngine.kt**: C++ 引擎的 Java 包装器
- **CoughDetectionRepository**: 使用新的 C++ 引擎替代原来的实现
- **ViewModel**: 保持不变，通过 Repository 与引擎交互

## 主要特性

### 1. 高性能音频录制
- 使用 Oboe 库实现低延迟音频录制
- 支持实时音频处理
- 自动音频级别监控

### 2. C++ 层咳嗽检测
- 所有音频处理都在 C++ 层完成
- 支持 TensorFlow Lite 模型推理
- 规则基础的备用检测算法
- 实时咳嗽事件回调

### 3. 事件驱动架构
- 音频事件回调机制
- 状态管理
- 错误处理

## 文件结构

```
app/src/main/
├── cpp/
│   ├── include/
│   │   ├── cough_detect_engine.h      # 主引擎头文件
│   │   ├── audio_recorder.h           # 音频录制器头文件
│   │   ├── tensorflow_wrapper.h       # TensorFlow 包装器头文件
│   │   └── jni_interface.h            # JNI 接口头文件
│   ├── cough_detect_engine.cpp        # 主引擎实现
│   ├── audio_recorder.cpp             # 音频录制器实现
│   ├── tensorflow_wrapper.cpp         # TensorFlow 包装器实现
│   ├── jni_interface.cpp              # JNI 接口实现
│   └── CMakeLists.txt                 # CMake 构建配置
└── java/com/example/coughdetect/
    ├── engine/
    │   └── CoughDetectEngine.kt       # Java 引擎包装器
    ├── repository/
    │   └── CoughDetectionRepository.kt # 更新的 Repository
    └── viewmodel/
        └── CoughDetectionViewModel.kt  # ViewModel (保持不变)
```

## 使用方法

### 1. 初始化引擎

```kotlin
val engine = CoughDetectEngine(context)
val success = engine.initialize()
```

### 2. 开始检测

```kotlin
engine.start()
```

### 3. 监听事件

```kotlin
// 监听引擎状态
engine.engineState.collect { state ->
    when (state) {
        CoughDetectEngine.EngineState.IDLE -> // 空闲状态
        CoughDetectEngine.EngineState.RECORDING -> // 正在录制
        CoughDetectEngine.EngineState.PAUSED -> // 暂停状态
        CoughDetectEngine.EngineState.PROCESSING -> // 处理中
    }
}

// 监听音频级别
engine.audioLevel.collect { level ->
    // 更新 UI 显示音频级别
}

// 监听音频事件
engine.lastAudioEvent.collect { event ->
    when (event?.type) {
        CoughDetectEngine.AudioEventType.COUGH_DETECTED -> {
            // 处理咳嗽检测事件
            println("检测到咳嗽，置信度: ${event.confidence}")
        }
        CoughDetectEngine.AudioEventType.AUDIO_LEVEL_CHANGED -> {
            // 处理音频级别变化
        }
        CoughDetectEngine.AudioEventType.ERROR_OCCURRED -> {
            // 处理错误
        }
    }
}
```

### 4. 控制检测

```kotlin
engine.pause()   // 暂停检测
engine.resume()  // 恢复检测
engine.stop()    // 停止检测
```

### 5. 释放资源

```kotlin
engine.release()
```

## 构建配置

### 1. build.gradle.kts 配置

```kotlin
android {
    // NDK 配置
    ndk {
        abiFilters("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
    
    externalNativeBuild {
        cmake {
            cppFlags("-std=c++17")
            arguments("-DANDROID_STL=c++_shared")
        }
    }
    
    // CMake 配置
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "25.2.9519653"
}

dependencies {
    // Oboe 依赖
    implementation("com.google.oboe:oboe:1.8.0")
    
    // TensorFlow Lite 依赖
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
}
```

### 2. CMakeLists.txt 配置

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("coughdetect")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找库
find_library(log-lib log)
find_library(android-lib android)
find_library(tensorflow-lite-lib tensorflowlite_jni)
find_library(oboe-lib oboe)

# 源文件
set(SOURCES
    cough_detect_engine.cpp
    audio_recorder.cpp
    tensorflow_wrapper.cpp
    jni_interface.cpp
)

# 创建共享库
add_library(coughdetect SHARED ${SOURCES})

# 链接库
target_link_libraries(coughdetect
    ${log-lib}
    ${android-lib}
)

# 条件链接
if(tensorflow-lite-lib)
    target_link_libraries(coughdetect ${tensorflow-lite-lib})
endif()

if(oboe-lib)
    target_link_libraries(coughdetect ${oboe-lib})
endif()
```

## 性能优势

1. **低延迟音频处理**: 使用 Oboe 实现高性能音频录制
2. **C++ 层处理**: 所有音频处理都在 C++ 层完成，减少 JNI 调用开销
3. **实时检测**: 支持实时咳嗽检测和事件回调
4. **内存效率**: C++ 层直接处理音频数据，减少内存拷贝

## 扩展性

1. **模块化设计**: 各个组件独立，易于扩展和替换
2. **插件化架构**: 可以轻松添加新的音频处理算法
3. **跨平台支持**: C++ 层可以轻松移植到其他平台

## 故障排除

### 1. 编译错误
- 确保 NDK 版本正确
- 检查 CMakeLists.txt 配置
- 验证依赖库路径

### 2. 运行时错误
- 检查权限设置
- 验证模型文件路径
- 查看日志输出

### 3. 性能问题
- 调整音频缓冲区大小
- 优化检测算法参数
- 监控内存使用情况

## 未来改进

1. **GPU 加速**: 集成 GPU 加速的 TensorFlow Lite
2. **多模型支持**: 支持多种咳嗽检测模型
3. **实时训练**: 支持在线模型更新
4. **云端集成**: 与云端服务集成进行更复杂的分析 
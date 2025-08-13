# TensorFlow Lite 集成指南

## 概述

本项目已经成功集成了TensorFlow Lite C++ API，用于高性能的咳嗽检测。集成保持了使用Oboe进行音频采集的高性能特性，并提供了GPU加速支持。

## 🚀 主要特性

- ✅ **TensorFlow Lite C++ API**: 使用原生C++ API而非JNI包装器
- ✅ **GPU加速支持**: 可选的GPU委托以提升推理性能
- ✅ **高性能音频**: 继续使用Oboe进行低延迟音频采集
- ✅ **智能回退**: 如果模型加载失败，自动使用基于规则的检测
- ✅ **多模型支持**: 支持YAMNet和自定义二分类模型
- ✅ **内存优化**: 高效的内存管理和资源释放

## 📁 文件结构

```
app/src/main/cpp/
├── include/
│   └── tensorflow_wrapper.h      # TensorFlow Lite C++ API头文件
├── tensorflow_wrapper.cpp        # TensorFlow Lite C++ API实现
├── CMakeLists.txt                # 更新的构建配置
└── ...

app/src/main/assets/
├── cough_detection_model.tflite  # 主要模型文件（需要用户提供）
├── yamnet_class_map.csv          # YAMNet类别映射
└── model_info.txt               # 模型信息说明
```

## 🔧 构建配置

### CMakeLists.txt 更改

```cmake
# TensorFlow Lite配置
set(TENSORFLOW_LITE_AVAILABLE ON)
add_definitions(-DTENSORFLOW_LITE_AVAILABLE)

# 使用prefab包管理器查找TensorFlow Lite
find_package(tensorflow-lite REQUIRED CONFIG)

# 链接TensorFlow Lite库
target_link_libraries(coughdetect
    tensorflow-lite::tensorflowlite
    # ... 其他库
)
```

### build.gradle.kts 更改

```kotlin
buildFeatures {
    prefab = true  // 启用prefab支持
}

dependencies {
    // 更新的TensorFlow Lite依赖
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
}
```

## 🧠 模型支持

### 1. YAMNet 模型 (推荐)

- **输入**: 16000个浮点数 (1秒16kHz音频)
- **输出**: 521个类别的概率分布
- **优势**: 预训练模型，识别多种音频事件
- **获取**: [TensorFlow Hub](https://tfhub.dev/google/yamnet/1)

### 2. 自定义二分类模型

- **输入**: 16000个浮点数 (1秒16kHz音频)
- **输出**: 2个浮点数 [非咳嗽概率, 咳嗽概率]
- **优势**: 专门针对咳嗽检测优化

### 3. 通用模型

- **输入**: 任意大小的浮点数组
- **输出**: 任意数量的类别概率
- **处理**: 自动检测模型格式并适配

## 💻 API 使用

### C++ 接口

```cpp
#include "include/tensorflow_wrapper.h"

// 创建TensorFlow包装器
coughdetect::TensorFlowWrapper tfWrapper;

// 初始化模型
std::string modelPath = "/path/to/model.tflite";
bool success = tfWrapper.initialize(modelPath);

// 检测咳嗽
std::vector<float> audioData = getAudioSamples();
coughdetect::DetectionResult result = tfWrapper.detectCough(audioData);

if (result.isCough) {
    LOGI("检测到咳嗽! 置信度: %.3f", result.confidence);
}

// 释放资源
tfWrapper.release();
```

### 检测结果结构

```cpp
struct DetectionResult {
    bool isCough;                          // 是否检测到咳嗽
    float confidence;                      // 置信度 [0.0, 1.0]
    std::vector<float> features;           // 提取的特征（保留用于扩展）
    std::vector<float> classProbabilities; // 类别概率分布
};
```

## ⚡ 性能优化

### GPU 加速

```cpp
// GPU委托会自动尝试初始化
// 如果GPU不可用，会自动回退到CPU
bool gpuAvailable = tfWrapper.isGpuDelegateEnabled();
```

### 线程优化

```cpp
// 自动设置为4线程以平衡性能和资源使用
interpreter_->SetNumThreads(4);
```

### 内存管理

- 使用智能指针自动管理TensorFlow Lite对象
- 在析构函数中自动释放GPU委托和解释器
- 支持显式释放资源

## 🔍 调试和日志

### 启用详细日志

所有日志都使用Android Log系统，标签为`TensorFlowWrapper`:

```bash
adb logcat -s TensorFlowWrapper
```

### 模型信息输出

```
Model has 1 input(s):
  Input 0: serving_default_waveform:0
    Type: FLOAT32
    Shape: [1, 16000]

Model has 1 output(s):
  Output 0: StatefulPartitionedCall:0
    Type: FLOAT32
    Shape: [1, 521]
```

### 性能监控

```
TensorFlow inference completed in 45.23ms - Cough: YES, Confidence: 0.847
```

## 🧪 测试集成

运行集成测试脚本：

```bash
./test_tflite_integration.sh
```

该脚本会检查：
- 所有必要文件是否存在
- 配置是否正确
- 尝试构建项目
- 验证生成的库文件

## 📦 部署模型

### 1. 下载或训练模型

```bash
# 下载YAMNet模型 (示例)
wget https://tfhub.dev/google/yamnet/1?tf-hub-format=compressed -O yamnet.tar.gz
tar -xzf yamnet.tar.gz
```

### 2. 转换为TensorFlow Lite

```python
import tensorflow as tf

# 加载模型
model = tf.saved_model.load('yamnet_model')

# 转换为TensorFlow Lite
converter = tf.lite.TFLiteConverter.from_saved_model('yamnet_model')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# 保存模型
with open('cough_detection_model.tflite', 'wb') as f:
    f.write(tflite_model)
```

### 3. 部署到应用

```bash
# 复制模型到assets目录
cp cough_detection_model.tflite app/src/main/assets/
```

## 🚨 故障排除

### 常见问题

1. **模型加载失败**
   - 检查模型文件路径和权限
   - 验证模型文件格式是否正确
   - 查看logcat输出获取详细错误信息

2. **GPU委托初始化失败**
   - 这是正常的，会自动回退到CPU
   - 某些设备不支持GPU加速

3. **构建错误**
   - 确保启用了prefab支持
   - 检查TensorFlow Lite依赖版本
   - 更新NDK到最新版本

4. **性能问题**
   - 考虑使用模型量化
   - 调整输入数据大小
   - 监控内存使用情况

### 日志分析

```bash
# 查看TensorFlow相关日志
adb logcat -s TensorFlowWrapper

# 查看所有相关日志
adb logcat -s CoughDetectEngine -s TensorFlowWrapper -s AudioRecorder
```

## 🔄 回退机制

如果TensorFlow Lite模型不可用，系统会自动使用基于规则的检测：

```cpp
DetectionResult detectCoughRuleBased(const std::vector<float>& audioData) {
    // 基于音频特征的检测算法
    // - 幅度分析
    // - 零交叉率
    // - 频谱质心
    // - 频谱滚降
    // - MFCC特征
}
```

## 📈 未来改进

- [ ] 支持流式推理
- [ ] 模型热更新
- [ ] 更多GPU优化
- [ ] 边缘TPU支持
- [ ] 模型压缩和量化

---

**注意**: 确保您有合适的.tflite模型文件。如果没有模型文件，应用将使用基于规则的检测算法，这仍然能够提供合理的咳嗽检测性能。

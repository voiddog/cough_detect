# YAMNet 模型集成指南

## 概述

本指南说明如何将真正的YAMNet TensorFlow Lite模型集成到咳嗽检测应用中。

## 当前状态

目前应用使用增强的基于规则的咳嗽检测算法，该算法参考了YAMNet的特征提取方法：

- 频谱质心 (Spectral Centroid)
- 过零率 (Zero Crossing Rate)  
- 频谱滚降 (Spectral Rolloff)
- MFCC特征
- 音频幅度

## 集成真正的YAMNet模型

### 1. 获取YAMNet模型

从以下位置下载YAMNet TensorFlow Lite模型：

```bash
# 从TensorFlow Hub下载
wget https://tfhub.dev/google/yamnet/1?tf-hub-format=compressed

# 或从Kaggle下载
# https://www.kaggle.com/models/google/yamnet/tfLite/classification-tflite
```

### 2. 更新CMakeLists.txt

确保正确配置TensorFlow Lite：

```cmake
# 在CMakeLists.txt中
find_package(tensorflow-lite REQUIRED CONFIG)
target_link_libraries(coughdetect tensorflow-lite::tensorflowlite)
```

### 3. 更新tensorflow_wrapper.h

移除条件编译，直接包含TensorFlow Lite头文件：

```cpp
#include "tensorflow/lite/c/c_api.h"
#include "tensorflow/lite/c/common.h"
```

### 4. 实现YAMNet推理

在`tensorflow_wrapper.cpp`中实现以下方法：

```cpp
bool initialize(const std::string& modelPath) {
    // 加载YAMNet模型
    model_ = TfLiteModelCreate(modelBuffer_.data(), modelBuffer_.size());
    interpreter_ = TfLiteInterpreterCreate(model_, options);
    // 分配张量
    TfLiteInterpreterAllocateTensors(interpreter_);
}

DetectionResult detectCoughWithModel(const std::vector<float>& audioData) {
    // 预处理音频数据
    std::vector<float> processedData = preprocessAudio(audioData);
    
    // 运行推理
    TfLiteInterpreterInvoke(interpreter_);
    
    // 后处理结果
    return postprocessResults(outputData);
}
```

### 5. YAMNet预处理

YAMNet需要特定的音频预处理：

```cpp
std::vector<float> preprocessAudio(const std::vector<float>& audioData) {
    // 1. 重采样到16kHz (如果需要)
    // 2. 归一化
    // 3. 填充或截断到固定长度
    // 4. 应用预加重滤波器
    return processedData;
}
```

### 6. YAMNet后处理

处理YAMNet的521类输出：

```cpp
DetectionResult postprocessResults(const std::vector<float>& outputData) {
    // 咳嗽相关类别索引
    std::vector<int> coughClasses = {
        0,   // Cough
        1,   // Throat clearing  
        2,   // Sneeze
        3,   // Sniff
        4,   // Burp
        5    // Belch
    };
    
    // 计算咳嗽概率
    float coughProbability = 0.0f;
    for (int classIdx : coughClasses) {
        coughProbability = std::max(coughProbability, outputData[classIdx]);
    }
    
    bool isCough = coughProbability > 0.5f;
    return DetectionResult{isCough, coughProbability, {}, outputData};
}
```

## 性能优化

### 1. 模型量化

使用TensorFlow Lite量化工具优化模型：

```bash
tflite_convert \
  --output_file=yamnet_quantized.tflite \
  --output_format=TFLITE \
  --inference_type=QUANTIZED_UINT8 \
  --input_arrays=input_1 \
  --output_arrays=Identity
```

### 2. 多线程推理

```cpp
TfLiteInterpreterOptionsSetNumThreads(options, 4);
```

### 3. GPU加速

```cpp
// 启用GPU代理
TfLiteGpuDelegateV2* gpuDelegate = TfLiteGpuDelegateV2Create(nullptr);
TfLiteInterpreterOptionsAddDelegate(options, gpuDelegate);
```

## 测试和验证

### 1. 单元测试

```cpp
TEST_F(CoughDetectorTest, YAMNetCoughDetection) {
    // 加载测试音频
    std::vector<float> coughAudio = loadTestAudio("cough_sample.wav");
    
    // 运行检测
    DetectionResult result = wrapper.detectCough(coughAudio);
    
    // 验证结果
    EXPECT_TRUE(result.isCough);
    EXPECT_GT(result.confidence, 0.7f);
}
```

### 2. 性能基准测试

```cpp
TEST_F(CoughDetectorTest, YAMNetPerformance) {
    auto start = std::chrono::high_resolution_clock::now();
    
    for (int i = 0; i < 100; ++i) {
        wrapper.detectCough(testAudio);
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    EXPECT_LT(duration.count(), 1000); // 100次推理应在1秒内完成
}
```

## 故障排除

### 常见问题

1. **模型加载失败**
   - 检查模型文件路径
   - 验证模型文件完整性
   - 确认TensorFlow Lite版本兼容性

2. **推理速度慢**
   - 启用模型量化
   - 使用GPU代理
   - 优化预处理步骤

3. **内存使用过高**
   - 使用模型量化
   - 减少批处理大小
   - 优化音频缓冲区管理

### 调试技巧

```cpp
// 启用详细日志
#define TENSORFLOW_LITE_DEBUG 1

// 检查模型输入输出
TfLiteTensor* inputTensor = TfLiteInterpreterGetInputTensor(interpreter_, 0);
TfLiteTensor* outputTensor = TfLiteInterpreterGetOutputTensor(interpreter_, 0);

LOG_INFO("Input shape: %d x %d x %d x %d", 
         inputTensor->dims->data[0],
         inputTensor->dims->data[1], 
         inputTensor->dims->data[2],
         inputTensor->dims->data[3]);
```

## 下一步

1. 下载并集成真正的YAMNet模型
2. 实现完整的预处理和后处理流程
3. 添加模型性能监控
4. 优化推理性能
5. 添加更多音频类别支持

## 参考资源

- [YAMNet论文](https://arxiv.org/abs/1609.09430)
- [TensorFlow Lite Android指南](https://www.tensorflow.org/lite/android/development)
- [YAMNet Kaggle模型](https://www.kaggle.com/models/google/yamnet/tfLite/classification-tflite) 
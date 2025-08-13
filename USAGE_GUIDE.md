# TensorFlow Lite 咳嗽检测使用指南

## 🎉 集成完成！

您的Android咳嗽检测项目已经成功集成了TensorFlow Lite C++ API！现在您可以使用高性能的机器学习模型进行咳嗽检测。

## ✨ 主要改进

### 1. 高性能C++实现
- 使用TensorFlow Lite C++ API替代了原有的wrapper
- 保持了Oboe高性能音频采集
- 支持GPU加速（可选）
- 智能回退到基于规则的检测

### 2. 多模型支持
- **YAMNet模型**: 预训练的521类音频分类模型
- **自定义二分类模型**: 专门针对咳嗽检测的模型
- **通用模型**: 支持任意输入输出格式的模型

### 3. 智能检测算法
- 如果TensorFlow Lite模型可用，优先使用ML推理
- 如果模型不可用，自动使用基于规则的特征检测
- 包含音频特征提取：幅度、零交叉率、频谱质心、频谱滚降、MFCC

## 🚀 快速开始

### 1. 添加模型文件

将您的TensorFlow Lite模型文件放在以下位置：

```
app/src/main/assets/cough_detection_model.tflite
```

### 2. 支持的模型格式

#### YAMNet模型（推荐）
- **输入**: [1, 16000] - 1秒16kHz音频
- **输出**: [1, 521] - 521个音频类别的概率
- **下载**: [TensorFlow Hub](https://tfhub.dev/google/yamnet/1)

#### 自定义二分类模型
- **输入**: [1, 16000] - 1秒16kHz音频  
- **输出**: [1, 2] - [非咳嗽概率, 咳嗽概率]

#### 其他格式
系统会自动适配不同的输入输出格式

### 3. 构建和运行

```bash
# 构建项目
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🔧 配置选项

### 检测阈值调整

在`tensorflow_wrapper.cpp`中可以调整检测参数：

```cpp
// YAMNet模型阈值
bool isCough = maxCoughProbability > 0.3f; // 降低阈值提高敏感度

// 基于规则的检测阈值
bool isCough = confidence > 0.6f; // 调整综合置信度阈值
```

### GPU加速

GPU委托会自动尝试初始化，如果不支持会回退到CPU：

```cpp
// GPU委托在initializeGpuDelegate()中配置
// 支持的设备会自动启用GPU加速
```

## 📊 性能监控

### 日志查看

```bash
# 查看TensorFlow相关日志
adb logcat -s TensorFlowWrapper

# 查看完整咳嗽检测日志
adb logcat -s CoughDetectEngine -s TensorFlowWrapper -s AudioRecorder
```

### 性能指标

应用会输出以下性能指标：
- 推理时间（毫秒）
- 检测置信度
- 特征提取时间
- GPU使用状态

## 🎯 检测流程

### 1. 音频采集
- 使用Oboe进行低延迟音频采集
- 采样率：16kHz
- 缓冲区大小：动态调整

### 2. 预处理
- 音频归一化到[-1, 1]范围
- 自动填充或截断到模型所需长度
- 支持不同输入格式的自动适配

### 3. 推理
- 优先使用TensorFlow Lite模型
- GPU加速（如果支持）
- 失败时回退到基于规则的检测

### 4. 后处理
- 多模型格式的统一处理
- 咳嗽相关类别的概率聚合
- 置信度计算和阈值判断

## 🔍 故障排除

### 常见问题

1. **模型加载失败**
   - 检查模型文件路径和权限
   - 确保模型文件格式正确
   - 查看logcat获取详细错误信息

2. **检测精度不理想**
   - 调整检测阈值
   - 尝试不同的模型
   - 检查音频质量和环境噪声

3. **性能问题**
   - 启用GPU加速
   - 使用模型量化
   - 调整音频缓冲区大小

### 调试模式

启用详细日志：

```cpp
// 在tensorflow_wrapper.cpp中
#define ENABLE_DEBUG_LOGS 1
```

## 📈 进一步优化

### 模型优化
- 使用TensorFlow Lite模型量化
- 尝试不同的模型架构
- 收集更多训练数据

### 性能优化
- 启用NNAPI委托
- 使用多线程推理
- 实现模型缓存

### 功能扩展
- 支持实时流式检测
- 添加更多音频特征
- 集成其他音频事件检测

## 📞 技术支持

如果您遇到任何问题：

1. 查看详细的集成文档：`TENSORFLOW_LITE_INTEGRATION.md`
2. 运行测试脚本：`./test_tflite_integration.sh`
3. 检查构建日志和logcat输出
4. 参考项目中的示例代码和注释

---

**恭喜！** 您的咳嗽检测应用现在拥有了强大的机器学习能力！🎉

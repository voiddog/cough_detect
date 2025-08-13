# 咳嗽检测 Android 应用

一个基于 Android Compose 和 TensorFlow Lite 的实时咳嗽检测应用。

## 功能特性

- 🎤 **实时音频录制**: 使用麦克风实时录制环境声音
- 🤖 **智能咳嗽检测**: 集成 TensorFlow Lite 进行准确的咳嗽识别
- 📊 **音频可视化**: 实时显示音频电平和检测状态
- 💾 **本地存储**: 使用 Room 数据库存储咳嗽记录
- 🎵 **音频保存**: 自动保存检测到的咳嗽片段为 WAV 文件
- 📱 **现代 UI**: 基于 Material Design 3 和 Jetpack Compose
- 🔒 **权限管理**: 智能处理录音和存储权限

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM + Repository Pattern
- **数据库**: Room
- **机器学习**: TensorFlow Lite
- **音频处理**: Android AudioRecord API
- **并发**: Kotlin Coroutines + Flow
- **依赖注入**: 手动依赖注入

## 项目结构

```
app/src/main/java/com/example/coughdetect/
├── audio/                  # 音频录制和处理
│   └── AudioRecorder.kt
├── data/                   # 数据层
│   ├── CoughRecord.kt      # 数据实体
│   ├── CoughRecordDao.kt   # 数据访问对象
│   ├── Converters.kt       # 类型转换器
│   └── CoughDetectDatabase.kt
├── ml/                     # 机器学习
│   └── CoughDetector.kt    # TensorFlow Lite 检测器
├── repository/             # 仓库层
│   └── CoughDetectionRepository.kt
├── ui/                     # UI 层
│   ├── theme/              # 主题配置
│   └── CoughDetectionScreen.kt
├── viewmodel/              # 视图模型
│   └── CoughDetectionViewModel.kt
└── MainActivity.kt         # 主活动
```

## 安装要求

- Android API 24+ (Android 7.0)
- 麦克风权限
- 存储权限
- 至少 100MB 可用存储空间

## 构建说明

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd cough_detect
   ```

2. **在 Android Studio 中打开项目**

3. **同步 Gradle 依赖**

4. **运行应用**
   ```bash
   ./gradlew assembleDebug
   ```

## 使用说明

### 基本操作

1. **开始检测**: 点击"开始检测"按钮开始录音和咳嗽检测
2. **暂停/继续**: 可以暂停和恢复检测过程
3. **停止检测**: 点击"停止"按钮结束检测
4. **查看记录**: 在界面下方查看所有检测到的咳嗽记录
5. **清空记录**: 点击"清空记录"按钮删除所有历史记录

### 检测算法

应用使用增强的基于规则的咳嗽检测算法，该算法参考了YAMNet模型的特征提取方法：

#### 特征提取
- **音频幅度 (Amplitude)**: 检测音频的强度变化
- **过零率 (Zero Crossing Rate)**: 检测音频信号的复杂度
- **频谱质心 (Spectral Centroid)**: 检测音频的频率分布中心
- **频谱滚降 (Spectral Rolloff)**: 检测高频成分的分布
- **MFCC特征**: 梅尔频率倒谱系数，模拟人耳听觉特性

#### 检测逻辑
算法使用加权评分系统，综合考虑多个音频特征来判断是否为咳嗽声音：
- 高幅度 (> 0.08): 25% 权重
- 适中过零率 (0.03-0.25): 20% 权重  
- 高频内容 (800-3000Hz): 25% 权重
- 适当滚降 (2000-6000Hz): 15% 权重
- MFCC特征 (> 0.1): 15% 权重

当置信度超过0.6时判定为咳嗽。

#### 未来计划
计划集成真正的YAMNet TensorFlow Lite模型以获得更高的检测准确性。详细集成指南请参考 [YAMNET_INTEGRATION.md](YAMNET_INTEGRATION.md)。

## 配置选项

### 音频设置
- 采样率: 16kHz
- 声道: 单声道
- 编码: 16-bit PCM
- 缓冲区大小: 自动计算

### 检测参数
- 最小咳嗽时长: 200ms
- 最大咳嗽时长: 3000ms
- 检测阈值: 可在代码中调整
- 音频块大小: 1秒

## 权限说明

应用需要以下权限：

- `RECORD_AUDIO`: 录制音频进行咳嗽检测
- `WRITE_EXTERNAL_STORAGE`: 保存咳嗽音频文件
- `READ_EXTERNAL_STORAGE`: 读取保存的音频文件

## 数据存储

### 数据库结构
咳嗽记录包含以下字段：
- `id`: 唯一标识符
- `timestamp`: 检测时间戳
- `audioFilePath`: 音频文件路径
- `duration`: 音频时长（毫秒）
- `confidence`: 检测置信度
- `amplitude`: 音频振幅
- `createdAt`: 创建时间

### 文件存储
- 音频文件存储在应用的外部文件目录
- 格式: WAV (16-bit PCM, 16kHz, 单声道)
- 命名格式: `cough_YYYYMMDD_HHMMSS_SSS.wav`

## 性能优化

- 使用协程进行异步音频处理
- 实时音频处理，避免内存积累
- 数据库操作在后台线程执行
- UI 状态响应式更新
- 音频缓冲区大小自动优化

## 故障排除

### 常见问题

1. **录音权限被拒绝**
   - 在设置中手动授予录音权限
   - 重新启动应用

2. **检测不准确**
   - 确保环境相对安静
   - 调整检测阈值参数
   - 考虑训练自定义 TensorFlow 模型

3. **音频文件保存失败**
   - 检查存储权限
   - 确保有足够的存储空间
   - 检查文件路径权限

4. **应用崩溃**
   - 查看 Logcat 输出
   - 检查设备兼容性
   - 确保 Android API 版本符合要求

## 开发说明

### 添加新功能

1. **扩展检测算法**
   - 在 `CoughDetector.kt` 中添加新的特征提取方法
   - 更新检测逻辑

2. **改进 UI**
   - 在 `CoughDetectionScreen.kt` 中添加新的 Compose 组件
   - 更新主题和样式

3. **数据库迁移**
   - 更新 `CoughRecord.kt` 实体
   - 在 `CoughDetectDatabase.kt` 中增加版本号
   - 添加迁移脚本

### 测试

```bash
# 运行单元测试
./gradlew testDebugUnitTest

# 运行集成测试
./gradlew connectedDebugAndroidTest
```

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交更改
4. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 作者

开发者：[您的姓名]
邮箱：[您的邮箱]

## 致谢

- TensorFlow Lite 团队
- Android Jetpack Compose 团队
- 开源社区的贡献者们
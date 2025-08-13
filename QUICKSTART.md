# 咳嗽检测应用 - 快速开始指南

## 🚀 快速启动

### 1. 环境要求

- **Android Studio**: Arctic Fox 或更新版本
- **Android SDK**: API 24+ (Android 7.0)
- **JDK**: 11 或更高版本
- **Gradle**: 8.0+

### 2. 项目导入

```bash
# 克隆或下载项目
cd cough_detect

# 在 Android Studio 中打开项目
# File > Open > 选择 cough_detect 文件夹
```

### 3. 构建项目

```bash
# 同步 Gradle 依赖
./gradlew build

# 或者在 Android Studio 中点击 "Sync Now"
```

### 4. 运行应用

1. 连接 Android 设备或启动模拟器
2. 点击 Android Studio 中的 "Run" 按钮
3. 或使用命令行：
   ```bash
   ./gradlew installDebug
   ```

## 📱 首次使用

### 权限授予
应用启动后会自动请求以下权限：
- 🎤 **录音权限**: 用于录制音频进行咳嗽检测
- 💾 **存储权限**: 用于保存咳嗽音频文件

### 基本操作
1. **开始检测**: 点击绿色的"开始检测"按钮
2. **查看状态**: 观察音频电平可视化和检测状态
3. **暂停/继续**: 可以随时暂停和恢复检测
4. **查看记录**: 在下方列表中查看检测到的咳嗽记录
5. **清空记录**: 点击红色的"清空记录"按钮

## 🔧 自定义配置

### 1. 添加 TensorFlow 模型

**如果你有训练好的咳嗽检测模型：**

1. 将 `.tflite` 模型文件重命名为 `cough_detection_model.tflite`
2. 复制到 `app/src/main/assets/` 目录
3. 重新构建应用

**模型要求：**
- 输入: 16000个浮点数 (1秒16kHz音频)
- 输出: 2个浮点数 [非咳嗽概率, 咳嗽概率]

### 2. 调整检测参数

在 `Constants.kt` 中可以调整：

```kotlin
object Detection {
    const val COUGH_CONFIDENCE_THRESHOLD = 0.5f  // 检测阈值
    const val MIN_COUGH_DURATION_MS = 200L       // 最小咳嗽时长
    const val MAX_COUGH_DURATION_MS = 3000L      // 最大咳嗽时长
}
```

### 3. 音频设置

```kotlin
object Audio {
    const val SAMPLE_RATE = 16000        // 采样率
    const val CHUNK_DURATION_MS = 1000L  // 音频块大小
}
```

## 🧪 测试

### 运行单元测试
```bash
./gradlew testDebugUnitTest
```

### 运行UI测试
```bash
./gradlew connectedDebugAndroidTest
```

## 🐛 故障排除

### 常见问题

**Q: 应用崩溃或无法启动**
- 检查 Android SDK 版本是否符合要求
- 确保已安装所有必需的 SDK 组件
- 清理并重新构建项目：`./gradlew clean build`

**Q: 录音权限被拒绝**
- 在设备设置中手动授予录音权限
- 重新启动应用

**Q: 检测不准确**
- 确保环境相对安静
- 考虑调整检测阈值参数
- 如果有自定义模型，确保模型文件正确放置

**Q: 音频文件保存失败**
- 检查存储权限
- 确保设备有足够存储空间
- 检查文件路径权限

### 调试技巧

1. **查看日志**:
   ```bash
   adb logcat | grep "CoughDetect"
   ```

2. **检查文件权限**:
   - 确保 `gradlew` 有执行权限：`chmod +x gradlew`

3. **清理项目**:
   ```bash
   ./gradlew clean
   rm -rf .gradle
   ```

## 📊 性能优化

### 电池优化
- 应用在后台时会自动暂停检测
- 使用协程进行异步处理，避免阻塞主线程

### 内存优化
- 音频数据实时处理，不会积累在内存中
- 数据库操作在后台线程执行

### 存储优化
- 音频文件采用压缩的WAV格式
- 提供清理旧记录的功能

## 🔄 更新日志

### v1.0.0
- ✅ 基础咳嗽检测功能
- ✅ TensorFlow Lite 集成
- ✅ 音频可视化
- ✅ 本地数据存储
- ✅ 基于规则的后备检测

## 📞 技术支持

如果遇到问题：

1. 查看 [README.md](README.md) 了解详细信息
2. 检查 GitHub Issues
3. 查看项目代码注释和文档

## 🎯 下一步

- 📈 添加统计图表功能
- 🔔 添加通知提醒
- 📤 添加数据导出功能
- 🌐 添加云端同步功能
- 🎛️ 添加更多自定义设置

---

**快速开始完成！🎉**

现在你可以开始使用咳嗽检测应用了。如果需要更详细的信息，请查看完整的 [README.md](README.md) 文档。
# YAMNet 咳嗽检测模型

## 模型说明

本项目使用YAMNet (Yet Another Audio Mobile Network) 模型进行咳嗽检测。YAMNet是一个预训练的深度神经网络，可以识别521种不同的音频事件。

## 模型文件

- `yamnet_model.tflite`: YAMNet TensorFlow Lite模型文件
- `yamnet_class_map.csv`: 类别映射文件，包含521个音频类别的名称

## 咳嗽相关类别

YAMNet模型中的咳嗽相关类别包括：

- Cough (咳嗽)
- Throat clearing (清嗓子)
- Sneeze (打喷嚏)
- Sniff (吸鼻子)
- Burp (打嗝)
- Belch (打嗝)

## 使用方法

1. 将YAMNet模型文件放置在assets目录中
2. 在应用启动时加载模型
3. 实时处理音频数据，提取特征
4. 使用模型进行推理，获得类别概率
5. 根据咳嗽相关类别的概率判断是否为咳嗽

## 技术细节

- 输入: 16kHz采样率的音频数据，长度约1秒
- 输出: 521个类别的概率分布
- 预处理: 音频归一化、填充/截断到固定长度
- 后处理: 提取咳嗽相关类别的概率，计算置信度

## 获取模型

您可以从以下位置获取YAMNet模型：

1. TensorFlow Hub: https://tfhub.dev/google/yamnet/1
2. Kaggle: https://www.kaggle.com/models/google/yamnet/tfLite/classification-tflite

## 注意事项

- 模型文件较大，建议在应用启动时异步加载
- 推理过程需要一定的计算资源，建议在后台线程中执行
- 可以根据具体需求调整咳嗽检测的阈值

# MiMo Chat Android App

原生 Android 聊天应用，集成 MiMo API。

## 功能

- 💬 **聊天模式** - 支持文本和图片输入
- 🎭 **角色系统** - 4个预设角色（小助手、程序员、作家、老师）
- 🎤 **语音输入** - 长按录音，松开自动识别转文字（ASR）
- 🔊 **语音输出** - AI 回复自动朗读（TTS），可开关
- 📷 **图片发送** - 选择图片发送给 AI 分析
- 📞 **语音通话** - 实时 WebSocket 语音对话
- 📹 **视频通话** - CameraX 实时预览 + WebSocket 音视频流

## 构建要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.5

## 快速开始

1. 用 Android Studio 打开 `android-app` 目录
2. 同步 Gradle 项目
3. 运行应用

## 配置

首次使用需要配置 MiMo API：

1. 打开应用
2. 点击设置图标
3. 输入 API 地址和 API Key
4. 保存配置（API Key 使用 AES-256 加密存储）

## 技术栈

- **语言**: Kotlin
- **网络**: OkHttp (HTTP + WebSocket)
- **序列化**: Gson
- **异步**: Kotlin Coroutines
- **相机**: CameraX
- **加密**: AndroidX Security Crypto
- **UI**: Material Design Components

## License

MIT

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tracediary_logo_transparent.png" width="128" alt="TraceDiary 标志">
</p>
<h1 align="center">TraceDiary Android</h1>
<p align="center">
  记录日常，安心保存，随时回看。
  <br>
  <a href="README.md">English</a>
</p>
<p align="center">
  <a href="https://github.com/Lane0218/TraceDiary-Android/releases"><img src="https://img.shields.io/badge/下载-GitHub%20Releases-181717?style=flat-square&logo=github&logoColor=white" alt="从 GitHub Releases 下载"></a>
  <a href="#环境要求"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="支持 Android 8.0 及以上"></a>
  <a href="#环境要求"><img src="https://img.shields.io/badge/API-26%2B-4CAF50?style=flat-square&logo=android&logoColor=white" alt="最低 API 26"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/许可证-MIT-356859?style=flat-square" alt="MIT 许可证"></a>
</p>

TraceDiary 是一款原生 Android 日记应用，帮助你记录每天的生活并重新发现过往。名称中的 “Trace” 指时间留下的痕迹；核心功能 **往年今日** 会带你回看历年同一天写下的日记。

日记默认保存在本地；你也可以自行配置加密云同步和基于记录证据的 AI 搜索服务。

## 功能

- **记录日常：** 编辑今天或其他日期的纯文本日记。设置每天的切换时间，使用周历或月历浏览日期，并通过自动保存和可选提醒持续记录。
- **往年今日：** 回看历年同一天写下的记录，这是 TraceDiary 回顾过往的核心体验。你也可以按关键词、年份或有记录的日期搜索，并撰写年度总结。
- **AI 搜索：** 使用端侧语义索引查找日记，并向自行配置的只读 AI 服务提问。回答附带日期和原文片段，便于核对。
- **记录统计：** 查看连续记录天数、月度字数趋势和年度记录热力图。
- **保护和迁移数据：** 使用应用密码加密本地数据库，将加密后的日记内容同步到已配置的对象存储服务，并导入或导出 JSON、TXT ZIP、Markdown ZIP 备份。
- **个性化设置：** 选择每周起始日和主题强调色，并设置日记提醒。

## Quick Start

TraceDiary 支持 Android 8.0（API 26）及以上的手机。发布 APK 后，你可以直接下载并安装：

1. 打开 [GitHub Releases](https://github.com/Lane0218/TraceDiary-Android/releases)，下载最新版本的 TraceDiary APK。
2. 在手机上打开下载的 APK。如果 Android 提示授权，请允许当前浏览器或文件管理器安装应用，然后点击“安装”。
3. 启动 TraceDiary 并设置应用密码。

首个 APK 版本尚未发布，后续可在 Releases 页面获取。

## 隐私

日记保存在 SQLCipher 加密的本地数据库中。应用密码用于派生数据库和云端内容的加密密钥。开启云同步后，日记内容会先加密，再上传到你配置的对象存储服务。

AI 搜索为可选功能。语义检索在设备本地运行。你提交问题后，应用会将相关日记片段及回答问题所需的数据发送给你配置的云端 AI 服务。连接前请查看服务提供方的隐私政策。关键词搜索无需 AI，可独立使用。

导出的备份文件为明文 JSON、TXT ZIP 或 Markdown ZIP，请妥善保管。

## 技术栈

- Kotlin 2.2.10、Jetpack Compose 和 Material 3
- Room 与 SQLCipher：本地数据存储
- DataStore：用户偏好设置
- WorkManager 与 OkHttp：后台任务和网络请求
- ONNX Runtime Android：端侧 `BAAI/bge-small-zh-v1.5` 向量模型
- 单 Android App 模块，按 UI、ViewModel、Domain、Data 分层

## 构建与测试

### 环境要求

- Android Studio 及其捆绑的 Java 运行时
- Android SDK API 36
- Android 8.0（API 26）或更高版本用于安装应用

在 Android Studio 中打开项目并运行 `app` 配置，也可以通过 Gradle Wrapper 在终端构建。

```shell
# Windows
gradlew.bat test assembleDebug --no-daemon

# macOS / Linux
./gradlew test assembleDebug --no-daemon
```

在已启动的模拟器或已连接的 Android 设备上运行 Compose 仪器测试：

```shell
gradlew.bat connectedDebugAndroidTest --no-daemon
```

调试 APK 输出到 `app/build/outputs/apk/debug/`。

## 许可证

TraceDiary 使用 [MIT 许可证](LICENSE)。

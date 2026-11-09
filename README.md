# TraceDiary Android

| # | 内容 |
| --- | --- |
| 01 | TraceDiary 是一款原生 Android 日记应用，面向快速记录、可靠保存和长期回顾。应用以本地日记为核心，提供日历编辑、历史搜索、年度总结、统计、加密存储、备份与可选云同步。最低支持 Android 8.0（API 26）。 |
| 02 | **今天：** 按可配置的业务日切换时间记录日记，支持周历/月历、补写历史日期、自动保存、往年今日和日记提醒。**回顾：** 按关键词、年份和有记录日期搜索；查看往年记录；撰写年度总结；或使用带日期与原文证据的 AI 检索历史日记。**统计：** 查看记录概览、连续记录、月度字数趋势和年度热力图。**设置：** 管理应用密码、云同步、AI 搜索、数据导入导出、提醒、周起始日和主题颜色。 |
| 03 | **隐私与数据：** 日记保存在设备上的 SQLCipher 加密数据库中；应用密码用于本地数据库加密和云端内容加密。启用云同步后，日记正文以应用密码派生密钥加密后同步到用户配置的对象存储服务。手动导入/导出备份为明文 JSON、TXT ZIP 或 Markdown ZIP，请妥善保管。AI 搜索使用本地语义索引检索相关内容，再调用用户配置的云端只读 Agent；查询所需的日记片段或统计数据会随请求发送到该 Agent 服务。关键词搜索不依赖 AI。请仅配置你信任的 AI 服务，并查看其数据处理政策。 |
| 04 | **技术栈：** Kotlin 2.2.10、Jetpack Compose、Material 3、Room、SQLCipher、DataStore、WorkManager、OkHttp、kotlinx.serialization、ONNX Runtime。端侧语义索引使用 `BAAI/bge-small-zh-v1.5` INT8 ONNX 模型；AI 问答通过 OpenAI Chat Completions 兼容接口连接用户配置的 Agent。项目采用单 Android App 模块及 UI、ViewModel、Domain、Data 分层。 |
| 05 | **开发环境：** Android Studio（使用其捆绑的 JBR）和 Android SDK。项目使用 Gradle Wrapper；Android Studio 可直接打开仓库并运行 `app` 配置。应用配置为 `minSdk 26`、`targetSdk 36`、`compileSdk 36.1`。 |
| 06 | **构建与测试：** 在 Windows 命令行运行 `gradlew.bat test assembleDebug --no-daemon` 执行 JVM 单元测试并构建调试包；仅运行 JVM 测试可执行 `gradlew.bat test --no-daemon`。调试 APK 输出在 `app/build/outputs/apk/debug/`。Compose 仪器测试运行 `gradlew.bat connectedDebugAndroidTest --no-daemon`，需要已启动的 Android 模拟器或连接设备。 |
| 07 | **许可证：** 当前仓库未包含 `LICENSE` 文件，项目使用、修改和再分发授权尚未声明。 |
| 08 | **README 参考项目：** 2026-11-09 通过 GitHub API 查询 Stars，并阅读各项目根目录 README。 [侠客日记](https://github.com/Yu-Core/SwashbucklerDiary)（1,630 Stars）：本地优先、隐私、导出和同步说明；[Presently](https://github.com/alisonthemonster/Presently)（421 Stars）：把隐私理念、离线数据和技术栈写清楚；[Jotsy](https://github.com/CikeSeven/jotsy)（151 Stars）：先说明产品定位，再分组介绍写作、回顾、备份；[Memos](https://github.com/usememos/memos)（64,351 Stars）：突出时间线记录和数据自主权；[Markor](https://github.com/gsantner/markor)（6,401 Stars）：简洁介绍 Android 写作能力、离线使用和文本兼容性。Stars 会变化，以上数字仅为查询时快照。 |

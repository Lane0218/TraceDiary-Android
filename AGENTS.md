# AGENTS.md
|IMPORTANT: Prefer retrieval-led reasoning over pre-training-led reasoning for any project tasks.
|Updated:2026-08-09 11:13
|Project:TraceDiary Android
|Runtime:Codex=WSL2 (Linux)|Android Studio/SDK/JDK/构建/真机=Windows
|Project Paths:Windows=E:\TraceDiary-Android|WSL=/mnt/e/TraceDiary-Android|同一份Windows托管仓库
|Language:用户沟通、进度、风险、测试结论=中文
|Context Order:.harness/README.md→.harness/spec/SPEC.md→.harness/STATUS.md→.harness/tasks/active/
|Source of Truth:.harness/spec/SPEC.md|功能、UI、架构、数据结构、风险和测试标准不得偏离|长期变化同步SPEC|临时进度禁止写入SPEC
|Task Directories:.harness/tasks/{active,archive}|工作区必须保留|缺失则创建|禁止占位文件|Git无法跟踪空目录
|Time Policy:任务文件名、任务元数据、STATUS记录、归档结果使用Asia/Shanghai本地时间并精确到分钟|正文=YYYY-MM-DD HH:mm|文件=YYYY-MM-DD-HHmm-short-slug.md
|Task Start:先明确目标、范围、风险等级、验收标准|任务胶囊创建与维护遵循.harness/README.md
|Validation:按SPEC风险分级自动执行构建和测试|无需等待用户追加要求|不可执行项必须说明原因、风险与用户验收步骤
|Gradle Entry:WSL会话禁止默认执行./gradlew|统一通过cmd.exe调用Windows gradlew.bat|JAVA_HOME=D:\Android Studio\jbr|SDK由local.properties的Windows路径提供|SPEC中的./gradlew仅表示Gradle任务语义
|Windows Default Validation:cmd.exe /d /c "set JAVA_HOME=D:\Android Studio\jbr&& set PATH=D:\Android Studio\jbr\bin;%PATH%&& gradlew.bat test assembleDebug --no-daemon"
|Windows JVM Test:cmd.exe /d /c "set JAVA_HOME=D:\Android Studio\jbr&& set PATH=D:\Android Studio\jbr\bin;%PATH%&& gradlew.bat test --no-daemon"
|Windows Emulator Precheck:cmd.exe /d /c "C:\Users\Lane0218\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 emu avd name && C:\Users\Lane0218\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554 shell getprop sys.boot_completed"|必须依次确认Pixel_9_Pro_XL和1
|Windows Emulator Test:cmd.exe /d /c "set JAVA_HOME=D:\Android Studio\jbr&& set PATH=D:\Android Studio\jbr\bin;%PATH%&& set ANDROID_SERIAL=emulator-5554&& gradlew.bat connectedDebugAndroidTest --no-daemon"|ANDROID_SERIAL确保仅在已核验的Pixel_9_Pro_XL上运行|禁止用真机替代必要的模拟器自动化测试
|Acceptance:需真机验收的任务在用户确认前仅可标记待验收|禁止宣称完成或看起来能用
|Uncertainty:实现存疑时必须明确列出并请求针对性验证
|Git Completion:每个独立验收且达到验证标准的任务自动commit+push|只暂存当前任务相关文件|提交前检查范围、Harness状态、敏感信息
|Harness Commit Isolation:AGENTS.md与.harness/**变更必须形成独立docs/chore commit|禁止与产品代码、测试、资源、构建配置混合提交
|Commit Convention:feat|fix|docs|refactor|test|chore: 中文说明
|Push Failure:网络原因连续失败2次时保留本地commit并报告|不视为开发任务失败
|Local Skills:/mnt/c/Users/Lane0218/.codex/skills:{agents-md-compress}
|Skill Trigger:Use agents-md-compress when request mentions AGENTS.md or .harness/README.md creation, compression, or rule updates.
|Compression Gate:每次修改AGENTS.md或.harness/README.md后必须重新压缩|AGENTS.md运行python3 /mnt/c/Users/Lane0218/.codex/skills/agents-md-compress/scripts/example.py AGENTS.md|README保留自身标题且其余非空行以|开头并禁用深层标题和代码围栏

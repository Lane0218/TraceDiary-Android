# 整理测试并在 Pixel 9 Pro XL 模拟器验收
状态=待验收
更新时间=2026-08-09 10:44

## 目标与背景

- 清理已确认无业务价值、重复或仅保护死代码的测试。
- 修正 JVM 测试中的错误断言与 Android 运行时分层问题。
- 让需要 Android Framework、Room、SQLCipher、Compose 或 WorkManager 的测试在 Device Manager 的 `Pixel_9_Pro_XL` AVD 上可重复运行。

## 范围

- 测试代码、AndroidX Test 依赖与为测试隔离所需的小范围生产代码重构。
- 未改变用户数据格式、同步协议或可见产品设计。
- 风险等级：高；全量设备测试暴露 SQLCipher `changePassword` 连接池空指针后，生产 rekey 改用已有单测覆盖的 `PRAGMA rekey` 路径，需真机确认改密后的数据库可重开。

## 验收标准

- Windows JBR 环境下 `gradlew.bat test` 全部通过。
- 启动并通过 ADB 确认目标 AVD 为 `Pixel_9_Pro_XL`。
- 目标模拟器上 `gradlew.bat connectedAndroidTest` 全部通过。
- OPPO / ColorOS 真机完成应用密码修改与数据库重开验收。
- 仅提交当前任务相关文件；Harness 变更与产品/测试代码分开提交并推送。

## 实施步骤

1. 删除模板、死代码和重复测试，修正弱断言与错误月历期望。
2. 将依赖 Android XML 解析器的 OSS 测试移动到 `androidTest`。
3. 隔离整应用导航测试的密码持久化状态，补充应用密码与 WorkManager Android 运行时测试。
4. 升级 AndroidX Test 以兼容 Android 17，并稳定 Compose 与 SQLCipher rekey 测试。
5. 运行 JVM、Debug 构建和 Pixel 9 Pro XL 全量仪器测试。
6. 用户在 OPPO 真机完成应用密码修改、进程重启和新旧密码验证后归档。

## 验证结果

- `gradlew.bat test assembleDebug --no-daemon`：通过；151 个 JVM 测试全部通过，Debug APK 构建通过。
- ADB 目标确认：`emulator-5554`，AVD=`Pixel_9_Pro_XL`，model=`sdk_gphone16k_x86_64`，Android=17，`sys.boot_completed=1`。
- `SqlCipherRekeyInstrumentedTest` 单独回归：1/1 通过。
- `gradlew.bat connectedDebugAndroidTest --no-daemon`：36/36 通过，0 跳过、0 失败。
- 首次快照启动卡在 ADB offline；终止该模拟器进程后使用 `-no-snapshot-load -no-snapshot-save` 冷启动成功，未擦除 AVD 数据。
- 待用户真机验收：在 OPPO 上创建一篇测试日记，修改应用密码，强制结束并重启应用；确认新密码可解锁且原日记可读，旧密码无法解锁。
- 产品与测试提交：`b5620ee test: 整理测试分层并补齐模拟器回归`，已推送至 `origin/main`。

# 准备首个 Android Release 并更新双语 README
状态=待验收
更新时间=2026-11-09 11:34

## 目标与背景
用户要求发布 Android APK，并完善 README 中的安装入口、次日切换时间和端侧 AI 搜索说明。

## 范围
- 修改 README.md 与 README.zh-CN.md。
- 构建、验证可安装的 Android APK，并发布 GitHub Release。
- 评估 APK 版本、签名、安装验证与用户数据隐私。
- 不在公开文档中留下内部任务、调研或提示内容。

## 验收标准
- 移除 API 26 顶部徽标；中文 Quick Start 标题为“快速开始”；删除尚未发布提示。
- 解释次日切换时间的用途，并说明 AI 搜索需下载端侧模型、建立加密索引并配置 DeepSeek 等 LLM，提供有证据的回顾用途。
- `test`、`assembleDebug`、Release 构建和可行的设备验证符合项目风险要求。
- GitHub Release 资产为可安装且非 debuggable 的 APK，并明确版本与签名身份。
- 发布后核验 Release、资产链接及双语文档；Harness 与产品文件分开提交。

## 实施步骤
- 更新双语 README 并核对功能表述。
- 检查仓库、GH Actions 和本机的正式签名配置。
- 运行测试与构建，验证 APK 签名、包信息和可安装性。
- 满足安全签名条件后创建 Release 并上传 APK。
- 归档任务并按项目规则提交和推送。

## 验证结果
- `test assembleDebug assembleRelease --no-daemon` 构建成功；单元测试通过，Release APK 生成。
- Pixel_9_Pro_XL / Android 17 仪器测试 55 项、0 失败、2 跳过。
- Release APK 元数据为 `com.tracediary.app`、versionName `1.0`、versionCode `1`、minSdk `26`、targetSdk `36`；Release manifest 未声明 debuggable。
- 用户授权后在仓库外生成长期发布密钥与独立密码文件。APK 通过 zipalign 和 Android v2/v3 签名验证；证书 SHA-256 为 `a8a4713fa2d4ec37cb1a25e518f0da18aee2c26bd7bdc67dbd372e33c1364676`。
- 签名 APK 在已核验的 Pixel_9_Pro_XL 上安装和启动成功，MainActivity 处于前台。
- GitHub `v1.0` Release 已发布 `TraceDiary-v1.0.apk` 与 `SHA256SUMS.txt`；远端 APK SHA-256 与本地一致：`5c574ef03fc00a2651977e647cdfeb2ad62f7caec025a3a3a4657dd930b0daf6`。
- 尚需用户在 Android 真机上下载安装、首次设置密码、记录和往年今日入口进行验收。签名密钥和密码必须分别安全备份，后续更新继续使用同一密钥。

# 准备首个 Android Release 并更新双语 README
状态=阻塞
更新时间=2026-11-09 10:16

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
- Release APK 元数据为 `com.tracediary.app`、versionName `1.0`、versionCode `1`、minSdk `26`、targetSdk `36`；Release manifest 未声明 debuggable。当前产物未签名。
- 仓库无已有 GitHub Release、Actions workflow 或签名 secrets；本机仅有 Android Debug keystore，不能用于正式发布。
- 等待用户选择提供现有正式签名 keystore，或授权生成并长期备份新的发布密钥；收到选择后继续签名、验证、发布和归档。

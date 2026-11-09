# 补充 README 安装入口、徽标和名称说明
状态=已完成
更新时间=2026-11-09 09:44

## 目标与背景
完善双语 README：添加面向 Android 手机用户的 GitHub Releases 安装 Quick Start，选择有用的项目徽标，并说明 TraceDiary 名称与“往年今日”功能的关系。

## 范围
- 更新 README.md 与 README.zh-CN.md。
- 保留语言切换、Logo、MIT 许可证和准确的隐私说明。
- 不修改应用代码、版本发布或其他 Git 历史。

## 验收标准
- README 开头包含相关平台、最低系统版本、下载入口和 MIT 徽标。
- Quick Start 指引用户在 GitHub 发布 APK 后可下载并安装到 Android 手机，并明确首个版本尚未发布。
- 中英文说明一致，突出“往年今日”为 TraceDiary 核心回顾功能。
- 文档链接、Logo、徽标和格式检查通过；不包含内部调研名录或提示。
- 归档任务并按项目规则分别提交 Harness 与产品文档。

## 实施步骤
- 核对 TraceDiary 现有同日往年记录入口与 GitHub Releases 地址。
- 更新双语 README 的徽标、Quick Start 和命名说明。
- 检查文案、链接和工作区范围后归档、提交并推送。

## 验证结果
- 双语 README 均包含 Android 8.0 / API 26 兼容说明、GitHub Releases 下载链接和 MIT 徽标。
- 用户安装步骤已说明 APK 发布后的下载、手机安装授权提示和首次应用密码设置；明确首版尚未发布。
- 名称来源与“往年今日”核心功能说明一致；今天页实现按月日加载过去年份记录。
- README 修改通过 `git diff --check` 和内容/路径检查；纯文档变更未运行 Android 构建或测试。

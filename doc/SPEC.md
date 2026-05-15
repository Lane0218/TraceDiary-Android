# TraceDiary Android 原生版规格

## 1. 软件功能

TraceDiary 是面向个人的轻量日记应用，核心目标是快速记录、稳定保存、方便回看。

- 今天：打开即写当天记录；支持周日历切换日期、补写历史日期、纯文本编辑、自动保存、云同步状态提示；当天页下方展示该日期的往年记录入口。
- 回顾：按日历、年度总结、搜索三个子页组织历史内容。
  - 日历：按月查看有记录日期，选择日期后查看当天记录与该日期的往年记录。
  - 年度总结：查看和撰写每年的年度总结正文，不承载统计图表。
  - 搜索：按关键词、年份、有记录日期等条件查找历史记录。
- 统计：展示记录数据概览，包括本月记录、连续记录、全年记录、月度趋势、年度热力图。
- 设置：提供云同步、数据导入、导出备份、编辑体验、提醒设置、关于应用等入口。

非目标：不提供 AI 分析入口；不暴露 Gitee、Token、仓库、分支、pull/push 等底层同步术语；不规划深色模式作为首版目标。

## 2. UI 设计风格

整体参考 OPPO / ColorOS 系统工具类应用：浅色背景、灰白分层、柔和阴影、克制圆角、深墨绿色作为主强调色。界面应保持安静、实用、低装饰感，避免营销页、纸质手账、强隐私保险箱或 AI 工作台气质。

参考图路径：

- 今日页：`reference/today.png`
- 回顾-日历：`reference/review.png`、`reference/review-calendar.png`
- 回顾-年度总结：`reference/review-year-summary.png`
- 回顾-搜索：`reference/review-search.png`
- 统计页：`reference/stats.png`
- 设置页：`reference/settings.png`

导航结构：

- 底部一级导航固定为：今天、回顾、统计、设置。
- 回顾页二级分段固定为：日历、年度总结、搜索。
- “往年今日”不作为独立页面，只作为日历选中日期下的局部内容。

## 3. 技术栈

当前项目基础：

- Android Gradle Plugin 9.2.1
- Kotlin 2.2.10
- Jetpack Compose + Material 3
- minSdk 26，targetSdk 36，compileSdk 36.1
- 单模块结构：`:app`

规划选型：

- UI：Jetpack Compose、Material 3、自定义主题与组件封装。
- 本地存储：Room + SQLCipher 或等价加密数据库方案。
- 偏好设置：DataStore。
- 后台任务：WorkManager，用于自动同步与备份。
- 网络：Ktor 或 OkHttp，用于对象存储同步。
- 序列化：kotlinx.serialization。

## 4. 架构设计

采用单 App 模块内的分层架构，首版不拆多模块，保持实现简单。

- UI 层：Compose 页面、导航、状态展示；只处理交互和渲染。
- ViewModel 层：维护页面状态、处理用户动作、触发用例。
- Domain 层：定义日记、年度总结、同步、导入导出的业务用例。
- Data 层：封装本地数据库、设置、文件导入导出、云同步客户端。

建议包结构：

```text
com.tracediary.app
  ui/
    today/
    review/
    stats/
    settings/
    components/
    theme/
  domain/
    model/
    repository/
    usecase/
  data/
    local/
    sync/
    backup/
    settings/
```

同步原则：

- 本地数据库是主数据源。
- 云同步只展示产品化状态，例如“已保存 · 云同步完成”。
- 同步配置放在设置页，不在主界面暴露对象存储实现细节。

## 5. 数据结构

核心实体：

```kotlin
data class DiaryEntry(
    val date: LocalDate,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState
)

data class AnnualSummary(
    val year: Int,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState
)

data class SyncMetadata(
    val objectKey: String,
    val localUpdatedAt: Instant,
    val remoteUpdatedAt: Instant?,
    val lastSyncedAt: Instant?,
    val checksum: String?
)
```

约束：

- `DiaryEntry.date` 全局唯一，一天一篇正文。
- `AnnualSummary.year` 全局唯一，一年一篇年度总结。
- 正文以纯文本保存；历史 Markdown 内容可在阅读时兼容渲染，编辑时保留原文。
- 统计数据不单独持久化，优先由日记记录按需聚合生成。
- 导入、导出、同步均以本地加密数据为准，避免明文长期落盘。

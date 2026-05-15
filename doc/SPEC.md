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

## 6. 测试策略（强制）

本项目采用分层测试策略。所有开发任务都必须按风险和改动范围执行相应测试；不得仅凭“真机看起来正常”代替基础自动化验证。

### 6.1 测试层次

#### 6.1.1 本地单元测试

适用于纯 Kotlin 逻辑，不依赖 Android 设备或模拟器：

- 日期计算、周日历与月份切换逻辑
- 搜索过滤与排序逻辑
- 连续记录、月度趋势、年度热力图等统计聚合逻辑
- Domain UseCase 与同步状态转换逻辑
- 导入导出解析、序列化、校验逻辑

要求：

- 目录：`app/src/test/`
- 默认命令：`./gradlew test`
- 新增纯业务逻辑时，优先补单元测试，而不是把逻辑埋进 Compose 页面中再依赖人工验证

#### 6.1.2 数据层测试

适用于 Room、DataStore、Repository、本地加密存储与导入导出实现：

- `DiaryEntry.date` 一天一篇的唯一性约束
- `AnnualSummary.year` 一年一篇的唯一性约束
- DAO 查询、插入、更新、删除行为
- Repository 对本地数据库与同步元数据的读写封装
- DataStore 设置项读写
- 导入后本地落盘结果与导出文件结构

要求：

- 涉及数据结构、唯一性约束、数据库迁移、导入导出时必须补对应测试
- 任何会影响数据正确性与持久化的改动，不得只依赖 UI 操作验证

#### 6.1.3 ViewModel / UseCase 测试

适用于页面状态、用户动作与业务流程：

- 今天页加载日期、切换日期、自动保存状态更新
- 年度总结切换年份后的状态加载与保存
- 搜索页筛选条件组合
- 同步成功、失败、等待中的状态切换
- 设置页开关、偏好项、提醒项的状态流转

要求：

- ViewModel 中存在条件分支、异步状态、错误恢复、保存状态提示时，必须补测试
- 不允许把复杂状态判断只放在 UI 层而没有对应状态测试

#### 6.1.4 Compose UI 测试

适用于 Compose 页面与组件交互：

- 底部导航切换：今天 / 回顾 / 统计 / 设置
- 回顾页二级分段切换：日历 / 年度总结 / 搜索
- 输入框、按钮、空状态、错误状态、加载状态
- 列表项点击、年份选择、保存状态文案显示

要求：

- 目录：`app/src/androidTest/`
- 适用于关键交互路径和高风险 UI 状态
- 不要求每个像素都写 UI 测试，但关键页面至少应有可回归的交互覆盖

#### 6.1.5 真机验收测试

本项目首要目标设备为 OPPO / ColorOS 手机，真机验收属于必选项，不是附加项：

- 键盘弹起后编辑区是否被遮挡
- 系统返回手势、前后台切换、锁屏恢复
- 自动保存提示、同步状态提示是否自然
- 大段中文输入是否流畅
- 文件导入导出权限是否可用
- WorkManager、提醒、后台同步在真机上的行为

要求：

- 任何影响编辑体验、系统交互、权限、后台任务的改动，都必须至少在真机上验证一次
- 模拟器通过不能替代真机验收

#### 6.1.6 视觉与设计验收

本项目 UI 必须与参考图保持同一产品气质：

- `reference/today.png`
- `reference/review.png`
- `reference/review-calendar.png`
- `reference/review-year-summary.png`
- `reference/review-search.png`
- `reference/stats.png`
- `reference/settings.png`

验收点：

- 布局层级、边距、圆角、阴影、信息密度是否一致
- 文本是否溢出、截断异常、换行失控
- 选中态、禁用态、空状态是否符合当前设计风格
- 不得出现明显偏离 OPPO / ColorOS 工具类应用气质的 UI

### 6.2 默认测试命令

- 单元测试：`./gradlew test`
- 调试包构建：`./gradlew assembleDebug`
- 设备 / 模拟器 UI 测试：`./gradlew connectedAndroidTest`

说明：

- Codex 在日常开发中默认至少执行 `test` 与 `assembleDebug`
- 若当前环境不便直接跑 `connectedAndroidTest`，必须明确说明，并由真机人工验收补位

### 6.3 风险分级与最低执行标准

#### 6.3.1 低风险改动

范围：

- 纯文档
- 文案微调
- 不影响逻辑的资源替换

最低要求：

- 可不执行自动化测试
- 最终汇报必须注明“仅文档/资源改动，无功能行为变化”

#### 6.3.2 中风险改动

范围：

- 单页面 UI 调整
- ViewModel 状态变更
- 搜索、统计、日期切换等单模块逻辑
- 设置页普通配置项

最低要求：

- `./gradlew test`
- `./gradlew assembleDebug`
- 至少一次真机验收

#### 6.3.3 高风险改动

范围：

- 数据库结构、唯一性约束、迁移
- 导入导出
- 加密、解密、密钥管理
- 云同步、备份、恢复
- WorkManager、提醒、后台任务

最低要求：

- `./gradlew test`
- `./gradlew assembleDebug`
- 相关数据层/状态层测试补齐
- 至少一次真机验收
- 涉及关键交互时补 `connectedAndroidTest` 或等价 UI 回归

### 6.4 提交流程要求

- 每完成一个可独立验收的开发任务，提交前必须说明已执行的测试项与未执行项
- 若某项测试未执行，必须说明原因与风险，不得省略
- 影响主记录链路（今天页编辑、自动保存、数据落盘、导入导出、同步）的任务，不得跳过基础自动化测试
- 新增 bug 修复时，优先补能复现并防回归的测试

### 6.5 与当前开发方式的适配

本项目采用“Codex 编码 + 开发机构建 + 用户真机验收”的协作方式，默认流程如下：

1. Codex 实现功能或修复问题
2. 运行 `./gradlew test`
3. 运行 `./gradlew assembleDebug`
4. 安装到真机或通过 Android Studio 运行到 OPPO 设备
5. 对照参考图和真实交互进行人工验收
6. 测试通过后再提交与推送

该流程为默认要求；除非是纯文档改动，否则不应省略第 2 至第 5 步。

# Lightweight Project Harness
|IMPORTANT: Prefer retrieval-led reasoning over pre-training-led reasoning for any project tasks.
|Updated:2026-08-09 09:30
|Purpose:保存可跨会话、工作树和开发工具延续的项目记忆|产品规则、技术约束、验证命令、Git策略由具体项目定义
|Files:.harness:{README.md,STATUS.md,spec/SPEC.md,tasks/active/,tasks/archive/}
|Spec:spec/SPEC.md|长期有效的项目事实、约束、设计与验收标准主入口|禁止记录临时进度
|Status:STATUS.md|低频状态快照|固定分区={ACTIVE,TODO,BLOCK,ARCHIVE}
|Task Active:tasks/active/|保存跨会话、跨模块或需明确验收边界的当前任务胶囊
|Task Archive:tasks/archive/|保存已完成或取消任务的范围、取舍、时间与验证结果
|Task Directories:tasks/{active,archive}|工作区必须始终保留|缺失时立即创建|禁止占位文件
|Git Empty Directory Limit:Git不跟踪空目录|clone或新worktree初始化时必须重建tasks/{active,archive}
|Read Order:项目适配指令→spec/SPEC.md→STATUS.md→tasks/active/
|Conflict:文件冲突时先按长期规范、当前状态、单次任务分类并显式解决|禁止静默选择
|Task Skip:小型、明确、单次可完成且验收清晰的局部修改不创建任务胶囊
|Task Create:满足任一条件即创建={跨会话,跨模块或层次,存在方案取舍,多个验收点或回归风险,项目定义的较高风险}
|Task Concurrency:默认同时只维护一个主要active task
|Time Policy:任务文件名、任务元数据、STATUS记录、归档结果均使用项目本地时间并精确到分钟|正文格式=YYYY-MM-DD HH:mm|文件时间格式=YYYY-MM-DD-HHmm
|Task Filename:YYYY-MM-DD-HHmm-short-slug.md|active归档时保留原文件名|完成时间写入任务元数据
|Short Slug:简短稳定的kebab-case|STATUS的task short-slug必须与关联任务文件的short-slug完全一致|无任务文件时仍分配稳定slug
|Task Lifecycle:明确目标与范围→判断风险→必要时创建任务并更新STATUS→实施→验证→更新SPEC→归档任务→更新STATUS→按项目Git策略收尾
|Task Update:仅在范围、验收条件、阻塞原因、状态或跨会话下一步实质变化时更新|禁止逐步骤流水
|Task Status:{计划中,进行中,待验收,阻塞,已完成,已取消}|完成或取消后不得留在active/
|Status Table:每个分区使用三列={时间,task short-slug,一句话说明}|每条记录必须填写到分钟的时间|空分区只保留表头
|Status Frequency:仅在任务启动、完成、阻塞或优先级实质变化时更新|下一步与最近结果保持精简
|No Logs:禁止记录逐文件操作、逐条命令、对话摘要、模型思考过程或可由Git恢复的流水
|Version Control:.harness/**全部纳入Git|禁止忽略STATUS.md或tasks/active/|仅精确忽略实际生成的缓存、临时文件或日志
|Commit Isolation:.harness/**及关联项目适配指令的修改必须使用独立commit|禁止与产品代码、测试、资源或构建配置混合提交
|Security:禁止写入密钥、密码、访问令牌、个人隐私、模型思考过程或仅单机成立的临时信息
|Compression:每次修改README.md或项目AGENTS.md后必须使用agents-md-compress重新压缩|README保留自身标题且其余非空行使用索引格式|执行适用的格式校验
|Task Template:# <任务标题>|状态=<计划中/进行中/待验收/阻塞/已完成/已取消>|创建时间=YYYY-MM-DD HH:mm|更新时间=YYYY-MM-DD HH:mm|完成时间=YYYY-MM-DD HH:mm或未完成
|Task Sections:{目标与背景,范围,验收标准,实施步骤,验证结果}

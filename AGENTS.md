# AGENTS.md
|IMPORTANT: Prefer retrieval-led reasoning over pre-training-led reasoning for any project tasks.
|项目:TraceDiary Android
|运行环境:Codex=WSL2 (Linux)|Android 项目=E:\TraceDiary-Android|WSL=/mnt/e/TraceDiary-Android
|项目根目录:/mnt/e/TraceDiary-Android
|规格来源:doc/SPEC.md|全部开发、测试、验收必须遵循|功能、UI、架构、数据结构、测试标准不得偏离
|沟通语言:与用户对话|进度更新|风险提示|测试结论均使用中文
|任务启动:先明确目标|影响文件|验收标准|再开始修改
|执行策略:除非用户明确要求，否则不主动执行构建|运行|安装|测试
|验收声明:不得将“看起来能用”视为实测验收
|风险提示:实现存疑时必须明确说明|由用户手动验证
|任务提交:每完成一个可独立验收的任务执行 commit + push
|Push 失败:因网络波动连续 2 次失败可仅保留 commit|不视为任务失败
|Commit Convention:feat|fix|docs|refactor|test|chore: 中文说明
|提交前检查:改动范围正确|无敏感信息

# AI 编程 IDE 执行门禁验收目录

> 状态：`READY`
> 决策：`DEC-009`
> Requirement：`REQ-AI-IDE-001`

| 场景 ID | Kind | Given / When | 必须观察到 | 证据 |
| --- | --- | --- | --- | --- |
| `AC-AI-IDE-001` | NORMAL | 正式 Task 为 READY、已授权、base Commit/阶段/REQ/AC/DEC/allowedPaths 全部合法 | spec 与 task 两个 validator 均退出 0；IDE 才可进入实施 | E1 |
| `AC-AI-IDE-002` | UNAUTHORIZED | Task 缺失、为 DRAFT 或 implementationAuthorized=false | task validator 非零退出；协议禁止产品编辑 | E1 |
| `AC-AI-IDE-003` | FAILURE | Task 引用 OPEN Requirement、SUPERSEDED/未接受 Decision 或未知 AC | validator 逐项报告并非零退出 | E1 |
| `AC-AI-IDE-004` | FAILURE | base Commit 与 HEAD 不同或 stageGatePassed=false | validator 阻断，不能用历史批准继续修改 | E1 |
| `AC-AI-IDE-005` | UNAUTHORIZED | allowedPaths 为空、为仓库根/整个 backend/frontend/docs、含通配或路径逃逸 | validator 阻断；IDE 不自行扩大范围 | E1 |
| `AC-AI-IDE-006` | FAILURE | IDE 声称完成但 Evidence 缺失、脏工作树、Commit 不符、FAIL/SKIP/低等级/notProven/未评审 | `-CheckCompletion` 非零退出；任务只能标为 IMPLEMENTED_UNVERIFIED | E1 |
| `AC-AI-IDE-007` | FAILURE | Task 遗漏 AC→证据计划、证据等级不足、缺任务专用验证命令或横切检查留空 | task validator 逐项报告并非零退出 | E1 |
| `AC-AI-IDE-008` | UNAUTHORIZED | PR 绕过 IDE 本地规则，或提交未批准范围/破坏契约/伪完成 | 受保护 CI 必须运行 spec/task/scope/evidence/breaking 门禁并阻断合并 | E1 |

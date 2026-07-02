# AI 编程 IDE 通用执行协议

> 状态：`ACTIVE`
> 决策：`DEC-009`
> 适用：Trae、Codex、Claude Code、Cursor、Qwen Code 等。

## 1. 会话输入

每个实现会话必须得到以下输入：

正式输入来自 Task Card 的 `harnessdg.task/v1` YAML Front Matter，不允许 IDE 用聊天内容或私有
Plan 替代。至少包含 taskId/status/implementationAuthorized/phase/baseCommit/stageGatePassed/
stageGateEvidenceRefs/requirements/acceptanceScenarios/decisions/scenarioEvidencePlan/allowedPaths/
requiredEvidenceLevel/requiredValidationCommands/approvedBy。

缺少 Task Card、存在相关 `OPEN` Decision、需求不是 `READY` 时，只允许做只读调查或补规格，不允许修改
产品代码。

当前 `DEC-007` 进一步冻结所有产品代码、契约、Migration、部署和测试修改，直到规格包整体评审通过。

AI 可以在文档任务中起草 Requirement、AC、Task Card 和 Evidence 计划，但不能在同一会话中把自己
起草的 Task 改为 READY、填写批准人并授权自身实施。授权来自产品/架构责任人或受控流程。

## 2. 强制机器预检

任何产品编辑前必须在仓库根执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-....md
```

两条命令都必须退出 0。Task 校验至少拒绝：

- Task 不是 READY 或 `implementationAuthorized`/`stageGatePassed` 不是 true；
- base Commit 不是当前 HEAD；
- Requirement 不是 READY、Decision 不是 ACCEPTED、AC 不存在；
- allowed paths 为空、包含整个仓库或使用通配逃逸；
- Task Card 不在 `docs/ai-spec/tasks/`；
- evidence 等级、批准人、批准时间或必需关联缺失。
- Acceptance 未逐条映射 Evidence、横切项留空或缺少任务专用验证命令；
- `preExistingDirtyPaths` 没有批准时 SHA-256，或内容在批准后发生变化。

校验失败时，本会话只能继续只读调查或文档修订，不得编辑产品代码、机器契约、Migration、部署或测试。

任务结束前再执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <task-card> -CheckChangedPaths
```

该检查以 base Commit 为基线，把 tracked/untracked 实际变更与 allowedPaths、forbiddenPaths 和批准时记录的
preExistingDirtyPaths 对比。出现越界路径时不得交接完成。

实现提交后，`baseCommit` 必须仍是当前 HEAD 的祖先；不得把 Task 的批准基线改写成实施结果 Commit。
需要声明“完成”时再执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath <task-card> -CheckCompletion
```

完成检查要求 Evidence 绑定当前干净 Commit、逐条覆盖 AC、达到最低等级、PASS 且无 SKIP/notProven，
完成 Secret 脱敏检查并由验证责任方接受。未通过时只能交接为 `IMPLEMENTED_UNVERIFIED`。

Task validator 还按允许路径强制最低命令组合：backend→Maven wrapper verify，frontend→
pnpm lint/typecheck/test/build，OpenAPI→lint + blocking diff，Compose→config + verify。缺少测试
基础设施本身是阻断项，不能删掉 `test` 来通过预检。

## 3. 启动顺序

1. 检查工作树，保留用户已有改动；
2. 读取根 `AGENTS.md`；
3. 读取 `docs/ai-spec/manifest.yaml` 和本协议；
4. 读取 Task Card 引用的需求、ADR 和机器契约；
5. 运行强制机器预检；
6. 校验 base Commit、当前阶段、批准人和允许路径；
7. 检查需求是否 `READY`、Decision 是否 `ACCEPTED`；
8. 建立每条 AC → 契约/数据/实现/测试/运行证据映射；
9. 发现冲突则登记并停止受影响实现。

## 4. 实施顺序

对于行为变化，严格按：

```text
需求/场景
→ OpenAPI/MCP/Event 示例与错误
→ Flyway（如有）
→ 生成类型
→ Application/Domain
→ REST/MCP/Worker Adapter
→ Frontend
→ Unit/Integration/Contract/Auth/Audit/Failure Tests
→ Compose Fixture/Verify
→ Runbook/Traceability/Evidence
```

AI 不得先实现 Controller 或页面，再反推契约。

所有文件修改必须位于 `allowedPaths`。发现任务必须触碰未授权路径时，先停止并要求更新 Task Card；
不得以“构建需要”为由自行扩大范围。

## 5. 任务内持续检查

每完成一个行为切片，更新临时执行表：

| AC ID | 契约 | 实现 | 测试 | 运行证据 | 状态 |
| --- | --- | --- | --- | --- | --- |

只有实际产物存在且检查通过才能更新状态。不得以代码行数、文件存在或“看起来正确”填 PASS。

## 6. 验证策略

1. 从最窄的 E1/E2 开始快速反馈；
2. 运行需求规定的 E3；
3. 达到 E4/E5 要求时必须实际启动相应环境；
4. 每次运行生成 Evidence Manifest；
5. 命令失败时先解释失败与需求的关系，再修复；
6. Docker/外部依赖不可用导致的 SKIP 必须保留为未验证；
7. 不运行与变更无关的破坏性清理或升级。

## 7. AI 的停止条件

以下任一条件出现时，停止受影响部分并向用户说明：

- 任务、ADR、机器契约和设计冲突；
- 产品行为需要未记录的选择；
- 需要扩大任务允许路径或阶段范围；
- 需要新权限、状态、事实源或技术路线；
- 需要修改已应用 Migration；
- 需要降低安全、数据约束或测试；
- 需要真实外部授权、Secret 或人工审批；
- 无法达到任务声明的证据等级。
- Task Card 校验在工作期间失效，或实际 HEAD/允许路径/阶段与批准内容发生漂移。

停止不意味着丢弃已完成的只读调查、规格或无争议实现。

## 8. 禁止的“完成捷径”

- 用 Mock Controller 测试声称全栈完成；
- 用 401/403 声称授权能力完整；
- 用 `docker compose config` 声称 E2E 完成；
- 用页面可构建声称 UI 可用；
- 用表或 Service 存在声称幂等/任务已接入；
- 用 Noop Adapter 让生产 Profile 启动；
- 把 breaking check 设置为不阻断后仍声称契约门禁通过；
- 删除/忽略失败测试或放宽约束；
- 在报告中把 SKIP 合并进 PASS 数；
- 未经用户确认把建议默认值升级为产品需求。
- AI 自己批准 Task、伪造 stageGatePassed/approvedBy 或把私有 Plan 当正式 Task Card。
- 只更新 Trae/Cursor 私有规则而不更新通用 AI Spec。

## 9. 完成与交接

完成时必须：

1. 逐条列出已满足的 REQ/AC；
2. 写入实际 Commit/环境/命令/结果；
3. 区分 E1-E5；
4. 更新追踪矩阵；
5. 报告所有未运行、SKIP 和限制；
6. 保留失败证据或链接；
7. 使用 Task Card 规定的完成报告格式。
8. 运行 `-CheckCompletion`；非零退出时禁止使用“完成/已验证”。

任务可在 `IMPLEMENTED_UNVERIFIED` 状态交接，但不得描述为“完成”。只有验证责任方确认最低证据等级后，
需求才能进入 `VERIFIED`。

## 10. IDE 适配层边界

IDE 专用文件只能描述：

- 该 IDE 如何发现上下文；
- Task/Plan/Rule 文件放在哪里；
- 如何运行命令和收集产物；
- 如何表达暂停和审批。

IDE 适配层不得复制或改写产品需求、权限规则和验收标准。通用规格始终是唯一产品语义来源。

## 11. 服务端门禁边界

本地协议和脚本能让遵循规则的 IDE正确实施，但不能阻止故意忽略规则的客户端。受保护分支/PR 必须由
`TASK-GOV-008` 接入 spec、Task、范围、Evidence 和 breaking-change required checks。在该 Task
实际交付前，门禁属于“本地可执行、服务端未强制”，不得描述为全闭环。

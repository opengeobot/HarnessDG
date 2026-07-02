# AI 编程 IDE 通用执行协议

> 状态：`DRAFT`
> 适用：Trae、Codex、Claude Code、Cursor、Qwen Code 等。

## 1. 会话输入

每个实现会话必须得到以下输入：

```yaml
taskCard: docs/ai-spec/tasks/TASK-....md
requirements: [REQ-...]
acceptanceScenarios: [AC-...]
acceptedDecisions: [DEC-...]
baseCommit: <sha>
allowedPaths: [...]
requiredEvidenceLevel: E...
```

缺少 Task Card、存在相关 `OPEN` Decision、需求不是 `READY` 时，只允许做只读调查或补规格，不允许修改
产品代码。

当前 `DEC-007` 进一步冻结所有产品代码、契约、Migration、部署和测试修改，直到规格包整体评审通过。

## 2. 启动顺序

1. 检查工作树，保留用户已有改动；
2. 读取根 `AGENTS.md`；
3. 读取 `docs/ai-spec/manifest.yaml` 和本协议；
4. 读取 Task Card 引用的需求、ADR 和机器契约；
5. 校验 base Commit、当前阶段和允许路径；
6. 检查需求是否 `READY`、Decision 是否 `ACCEPTED`；
7. 建立场景到预计证据的映射；
8. 发现冲突则登记并停止受影响实现。

## 3. 实施顺序

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

## 4. 任务内持续检查

每完成一个行为切片，更新临时执行表：

| AC ID | 契约 | 实现 | 测试 | 运行证据 | 状态 |
| --- | --- | --- | --- | --- | --- |

只有实际产物存在且检查通过才能更新状态。不得以代码行数、文件存在或“看起来正确”填 PASS。

## 5. 验证策略

1. 从最窄的 E1/E2 开始快速反馈；
2. 运行需求规定的 E3；
3. 达到 E4/E5 要求时必须实际启动相应环境；
4. 每次运行生成 Evidence Manifest；
5. 命令失败时先解释失败与需求的关系，再修复；
6. Docker/外部依赖不可用导致的 SKIP 必须保留为未验证；
7. 不运行与变更无关的破坏性清理或升级。

## 6. AI 的停止条件

以下任一条件出现时，停止受影响部分并向用户说明：

- 任务、ADR、机器契约和设计冲突；
- 产品行为需要未记录的选择；
- 需要扩大任务允许路径或阶段范围；
- 需要新权限、状态、事实源或技术路线；
- 需要修改已应用 Migration；
- 需要降低安全、数据约束或测试；
- 需要真实外部授权、Secret 或人工审批；
- 无法达到任务声明的证据等级。

停止不意味着丢弃已完成的只读调查、规格或无争议实现。

## 7. 禁止的“完成捷径”

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

## 8. 完成与交接

完成时必须：

1. 逐条列出已满足的 REQ/AC；
2. 写入实际 Commit/环境/命令/结果；
3. 区分 E1-E5；
4. 更新追踪矩阵；
5. 报告所有未运行、SKIP 和限制；
6. 保留失败证据或链接；
7. 使用 Task Card 规定的完成报告格式。

任务可在 `IMPLEMENTED_UNVERIFIED` 状态交接，但不得描述为“完成”。只有验证责任方确认最低证据等级后，
需求才能进入 `VERIFIED`。

## 9. IDE 适配层边界

IDE 专用文件只能描述：

- 该 IDE 如何发现上下文；
- Task/Plan/Rule 文件放在哪里；
- 如何运行命令和收集产物；
- 如何表达暂停和审批。

IDE 适配层不得复制或改写产品需求、权限规则和验收标准。通用规格始终是唯一产品语义来源。

# 可执行需求结构

> 状态：`READY`

## 1. 原则

一条需求必须让没有参与过此前对话的 AI 能回答：

- 谁在什么条件下发起什么动作？
- 输入、规则和状态变化是什么？
- 谁有权做，谁必须被拒绝？
- 重试或重复调用会发生什么？
- 失败如何映射、记录、通知和恢复？
- REST、MCP、Worker 和页面是否共享同一用例？
- 哪些自动化证据足以判定完成？

若任一答案依赖猜测，该需求不得进入 `READY`。

## 2. 标准字段

```yaml
id: REQ-<domain>-<number>
title: ...
status: OPEN | PROPOSED | READY | IN_PROGRESS | IMPLEMENTED_UNVERIFIED | VERIFIED
priority: MUST | SHOULD | COULD
phase: P0-B | P1 | P2 | P3 | P4 | P5

sources:
  designSections: [...]
  adrs: [...]
  decisions: [...]
  contracts: [...]

intent:
  actor: ...
  goal: ...
  businessValue: ...
  nonGoals: [...]

behavior:
  trigger: ...
  preconditions: [...]
  inputs: [...]
  invariants: [...]
  outputs: [...]
  stateChanges: [...]

security:
  authentication: ...
  permission: ...
  scope: ...
  resourcePolicy: ...
  antiEnumeration: ...

reliability:
  idempotency: REQUIRED | NOT_REQUIRED
  idempotencyScope: ...
  concurrency: ...
  transactionBoundary: ...
  externalConsistency: ...
  retryAndCompensation: ...

crossCutting:
  errorCodes: [...]
  auditEvents: [...]
  notifications: [...]
  metrics: [...]
  traces: [...]
  alerts: [...]
  dictionaries: [...]
  tags: [...]
  i18nKeys: [...]
  configurations: [...]

interfaces:
  restOperations: [...]
  mcpTools: [...]
  workerHandlers: [...]
  pages: [...]
  events: [...]
  tables: [...]

acceptance:
  minimumEvidenceLevel: E4
  scenarios:
    - id: AC-<domain>-<number>-01
      kind: NORMAL | BOUNDARY | FAILURE | UNAUTHORIZED | RECOVERY | CONCURRENCY
      given: ...
      when: ...
      then: [...]
  requiredCommands: [...]
  evidenceArtifacts: [...]

dependencies: [...]
openQuestions: [...]
```

## 3. 场景要求

每条 `MUST` 需求至少包含：

1. 一个正常场景；
2. 一个边界场景；
3. 一个依赖或业务失败场景；
4. 一个未认证或越权场景；
5. 涉及异步/外部系统时的恢复场景；
6. 涉及重复提交或并发时的幂等/并发场景。

场景必须描述可观察结果，禁止只写实现动作：

```text
错误：THEN 创建 JwtTokenService 类。
正确：THEN 返回 200、access JWT 可验证、refresh Cookie 满足安全属性，
      audit_log 追加 AUTH_LOGIN_SUCCEEDED，响应和日志均不含密码。
```

## 4. 需求进入 READY 的门槛

- 所有影响行为的 `openQuestions` 已转成 Accepted Decision；
- actor、权限、作用域和防枚举语义明确；
- 状态前置条件和状态变化明确；
- 幂等、事务、外部一致性和恢复语义明确；
- 错误码、审计、通知和观测影响明确；
- OpenAPI/MCP/Event/Flyway 变更范围明确；
- UI 页面和字段级行为明确；
- 验收场景及最低证据等级明确；
- 非目标明确，防止 AI 顺手扩展范围。

## 5. 禁止的需求写法

- “实现用户管理”“完善权限”“支持国际化”等不可观察的大标题；
- 只写正常路径；
- 只写“与设计一致”而不引用具体章节和规则；
- 使用“合理处理”“适当提示”“支持常见格式”等模糊词；
- 把类名、框架调用或表存在当成业务验收；
- 在一个任务中同时交付多个不相关用户旅程；
- 将手工检查作为唯一验收方式；
- 允许 AI 在缺少决策时自行选择产品语义。

## REQ-AI-IDE-001 AI 编程 IDE 实施门禁

```yaml
status: READY
priority: MUST
phase: P0-B
decisions: [DEC-009]
minimumEvidenceLevel: E1
acceptance:
  - AC-AI-IDE-001
  - AC-AI-IDE-002
  - AC-AI-IDE-003
  - AC-AI-IDE-004
  - AC-AI-IDE-005
  - AC-AI-IDE-006
  - AC-AI-IDE-007
  - AC-AI-IDE-008
```

### 行为

- 所有 AI 编程 IDE 共用 `06-ide/generic-execution-protocol.md`，IDE 私有规则只能做薄适配；
- 修改产品代码、OpenAPI/MCP/Event、Migration、部署或测试前，必须有
  `docs/ai-spec/tasks/TASK-*.md` 正式 Task Card；
- Task Card 的 YAML Front Matter 是机器权威，至少包含 READY、显式实施授权、真实 base Commit、
  上游阶段证据、READY Requirement、ACCEPTED Decision、Acceptance、窄 allowedPaths 和证据等级；
- 每条 Acceptance 必须在 `scenarioEvidencePlan` 中唯一绑定最低证据等级和预期产物，且
  `requiredValidationCommands` 同时包含治理校验和任务专用测试命令；
- `validate-spec.ps1` 与 `validate-task-card.ps1` 任一失败时，AI 只能继续只读调查或规格修订；
- AI 可起草 DRAFT Task，但不能在同一实施会话中批准或授权自己；
- AI 只能修改 allowedPaths，范围不足时停止并请求重新批准；
- 每条 AC 必须映射预期契约、数据、实现、测试和 Evidence，不能用私有 Plan 或聊天承诺替代；
- 完成声明逐条引用 Evidence Manifest；低等级证据、FAIL、SKIP 或未运行不能被包装成完成；
- `-CheckCompletion` 只有在干净 Commit、完整 PASS 证据、Secret 检查和独立验收均满足时才通过；
- CI/受保护合并流程必须运行规格门禁并阻止未经批准的范围、契约破坏或伪完成；本地协议不能替代
  服务端门禁。

### 目标接口与证据

- 入口：根 `AGENTS.md`、AI Spec manifest、通用协议和正式 Task Card；
- 校验：`tools/validate-spec.ps1`、`tools/validate-task-card.ps1`；
- Trae：`.trae/specs` 只能链接 Task/REQ/AC/DEC，不得重定义产品行为；
- 审计证据：校验命令输出、Task base Commit、allowedPaths 差异、Evidence Manifest；
- 本需求不授权任何 P1+ 产品实现，也不替代 ADR-0002 阶段门禁。


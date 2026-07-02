# 可执行需求结构

> 状态：`DRAFT`

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


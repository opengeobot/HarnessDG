# 已确认决策日志

> 状态：`DRAFT`
> 当前 Accepted Decision：7

## 1. 规则

- 只有产品/架构责任人的明确回答可以创建 `ACCEPTED`；
- AI 的建议默认值仍是 `PROPOSED`；
- 决策记录追加，不静默改写历史；
- 被替代决策改为 `SUPERSEDED` 并链接新 Decision；
- 改变技术路线、事实源、安全模型、模块边界或发布语义的决定必须通过 ADR，不只记在本文件；
- 每条决策列出受影响 Requirement、Contract、Migration、Page、Acceptance 和文档；
- 关闭 Open Question 后同步 `open-questions.md` 与 `manifest.yaml`。

## 2. Decision 模板

```yaml
decisionId: DEC-000
questionIds: [Q-000]
status: ACCEPTED
decision: ...
rationale: ...
alternativesRejected:
  - option: ...
    reason: ...
decidedBy: ...
decidedAt: ...
effectiveFrom: ...
affected:
  requirements: []
  invariants: []
  journeys: []
  pages: []
  acceptanceScenarios: []
  contracts: []
  migrations: []
  documents: []
requiresAdr: false
adr: null
supersedes: null
```

## 3. 决策记录

### DEC-001：当前先完成并重验 P0

```yaml
decisionId: DEC-001
questionIds: [Q-001]
status: ACCEPTED
decision: 当前交付焦点是尚未完全实现的 P0；规格包保留 P1-P5 后续框架，但不授权跳过 P0 开发下游功能。
rationale: 用户明确判断 P0 尚未完全实现，需要先把基础阶段做实。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  requirements: [REQ-IAM-001, REQ-AUTH-001, REQ-COM-001, REQ-UI-001]
  journeys: [JRN-P0B-001, JRN-P0B-002, JRN-P0B-003, JRN-P0B-004, JRN-P0B-005, JRN-P0B-006, JRN-P0B-007, JRN-P0B-008, JRN-P0B-009, JRN-P0B-010]
  documents: [01-requirements/product-scope.md, 02-delivery/work-breakdown.md, 05-acceptance/p0b-exit-catalog.md]
requiresAdr: false
```

P0 的精确出口边界仍由 `Q-007` 确认；在此之前沿用 ADR-0002，不把 P1-P3 改名为 P0。

### DEC-002：保留现有代码并证据驱动整改

```yaml
decisionId: DEC-002
questionIds: [Q-002]
status: ACCEPTED
decision: 采用方案 A，保留现有实现；逐条审计，只有证据证明局部不可用时才替换。
rationale: 避免无依据推倒重写和丢失已有迁移/兼容历史。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  requirements: []
  documents: [00-governance/current-state-audit-2026-07-02.md, 02-delivery/work-breakdown.md]
requiresAdr: false
```

### DEC-003：P0-B 未完成，必须重新验收

```yaml
decisionId: DEC-003
questionIds: [Q-003]
status: ACCEPTED
decision: 不接受 2026-07-02 状态快照作为 P0-B 已完成证明；P0-B 状态为 IMPLEMENTED_UNVERIFIED/PARTIAL，按出口目录逐项重验。
rationale: 现有契约、任务勾选、Runbook、代码和 Verify 覆盖互相矛盾。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  requirements: [REQ-IAM-001, REQ-AUTH-001, REQ-TAX-001, REQ-JOB-001, REQ-AUD-001, REQ-OBS-001, REQ-UI-001]
  acceptanceScenarios: [AC-P0B-ENG-001]
  documents: [00-governance/current-state-audit-2026-07-02.md, 02-delivery/traceability-matrix.md, 05-acceptance/p0b-exit-catalog.md]
requiresAdr: false
```

`AC-P0B-ENG-001` 在此仅作为出口目录入口引用，不代表其他 69 个场景被覆盖。

### DEC-004：单公司租户、多组织、多项目

```yaml
decisionId: DEC-004
questionIds: [Q-101]
status: ACCEPTED
decision: MVP 只有一个公司租户；公司内存在多个 Organization，每个 Organization 下存在多个 Project。
rationale: 用户明确组织隔离模型。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  requirements: [REQ-ORG-001, REQ-AUTH-001, REQ-AST-001]
  invariants: [INV-ORG-001, INV-ORG-002, INV-ORG-003, INV-ORG-004, INV-ORG-005, INV-ORG-006]
  pages: [PAGE-ADM-003, PAGE-ADM-005]
  documents: [01-requirements/personas-and-scope.md, 01-requirements/p0b-identity-authorization.md]
requiresAdr: false
```

### DEC-005：首批业务旅程不包含 Agent/MCP

```yaml
decisionId: DEC-005
questionIds: [Q-006]
status: ACCEPTED
decision: 首批目标包含管理员初始化/用户治理、用户登录并创建资产、组织管理员治理字典和标签、普通用户仅检索有权资产、上传/审批/发布/下载闭环；不包含 Agent 获取只读 JWT 并调用 MCP。
rationale: 用户明确选择除 Agent 只读 MCP 外的全部主要旅程。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  journeys: [JRN-P0B-001, JRN-P0B-002, JRN-P0B-005, JRN-P1-001, JRN-P1-002, JRN-P2-002, JRN-P2-003, JRN-P3-001]
  documents: [03-use-cases/user-journey-catalog.md, 02-delivery/work-breakdown.md]
requiresAdr: false
```

P4 Agent 规格保留为后续范围，但不进入首批实施/验收顺序。

### DEC-006：Trae 为主要 AI 编程 IDE

```yaml
decisionId: DEC-006
questionIds: [Q-004]
status: ACCEPTED
decision: 主要面向 Trae 交付；通用规格核心保持 IDE 中立，Trae 使用薄适配层。
rationale: 用户当前主要使用 Trae，仍需避免把产品语义锁死在 IDE 私有格式中。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  documents: [06-ide/generic-execution-protocol.md, 06-ide/trae-adapter.md]
requiresAdr: false
```

### DEC-007：规格确认前只修改文档

```yaml
decisionId: DEC-007
questionIds: [Q-005]
status: ACCEPTED
decision: 在规格包评审通过前，只允许修改规格/设计文档和执行只读证据审计，不继续修改产品代码、契约、Migration、部署或测试。
rationale: 先稳定验收尺度，避免继续扩大实现偏差。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  documents: [manifest.yaml, 06-ide/generic-execution-protocol.md, templates/task-card.md]
requiresAdr: false
```

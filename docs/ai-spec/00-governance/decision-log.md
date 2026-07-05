# 已确认决策日志

> 状态：`DRAFT`
> 当前生效的 Accepted Decision：12（`DEC-005` 已被 `DEC-008` 替代）

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
status: SUPERSEDED
decision: 首批目标包含管理员初始化/用户治理、用户登录并创建资产、组织管理员治理字典和标签、普通用户仅检索有权资产、上传/审批/发布/下载闭环；不包含 Agent 获取只读 JWT 并调用 MCP。
rationale: 用户明确选择除 Agent 只读 MCP 外的全部主要旅程。
decidedBy: User
decidedAt: "2026-07-02"
supersededBy: DEC-008
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

### DEC-008：数据集体验与 AI 数据工作流是必须交付的产品目标

```yaml
decisionId: DEC-008
questionIds: [Q-006]
status: ACCEPTED
decision: >
  最终可交付产品必须提供类似 ModelScope 的受治理数据集发现与详情体验，包括分类/受控标签、
  Dataset Card、安全预览、精确版本文件和资产内交流反馈；同时必须让获授权 AI 通过标准搜索工具
  找到数据集，经 REST 或最小平台 CLI 下载精确版本到本地，并能通过受限写工具或同源 Agent API
  创建草稿和上传数据。正式发布、删除、扩权和配置仍由人工闸门控制。
rationale: 用户明确把 AI 搜索、下载、创建与上传作为重点，并确认补齐 ModelScope 类数据集体验。
decidedBy: User
decidedAt: "2026-07-02"
supersedes: DEC-005
affected:
  requirements:
    - REQ-DST-TAX-001
    - REQ-DST-DETAIL-001
    - REQ-DST-DISC-001
    - REQ-DST-CLI-001
    - REQ-DST-AI-001
    - REQ-DST-AIW-001
    - REQ-PRE-001
    - REQ-MCP-003
    - REQ-MCP-004
    - REQ-MCP-005
  journeys: [JRN-P1-002, JRN-P1-005, JRN-P2-003, JRN-P2-004, JRN-P4-002, JRN-P4-005]
  pages: [PAGE-AST-001, PAGE-AST-003, PAGE-DST-001, PAGE-DST-002, PAGE-VER-003]
  acceptanceScenarios:
    - AC-DST-TAX-001
    - AC-DST-DETAIL-001
    - AC-DST-DISC-001
    - AC-DST-PRE-001
    - AC-DST-CLI-001
    - AC-DST-AI-001
    - AC-DST-AIW-001
  contracts:
    - contracts/openapi/aihub-v1.yaml
    - contracts/mcp/tools.yaml
    - contracts/events/events-v1.yaml
  documents:
    - 01-requirements/dataset-experience.md
    - 01-requirements/p1-asset-catalog.md
    - 01-requirements/p2-version-transfer.md
    - 01-requirements/p4-agent-integration.md
    - 01-requirements/p5-quality-operations.md
    - 03-use-cases/user-journey-catalog.md
    - 04-ui/information-architecture.md
    - 04-ui/page-catalog.md
    - 05-acceptance/dataset-agent-exit-catalog.md
requiresAdr: false
```

本决策改变产品优先级和阶段出口，不改变 ADR-0001/0002 的技术路线、事实源或 P0-B 准入门。
实施顺序仍为 P0-B → P1 → P2 → P3 → P4；规格可以提前完成，产品代码不得跨阶段抢跑。

### DEC-009：AI 编程 IDE 必须执行统一规格门禁

```yaml
decisionId: DEC-009
questionIds: []
status: ACCEPTED
decision: >
  Trae、Codex、Claude Code、Cursor、Qwen Code 等 AI 编程 IDE 在修改产品代码前，必须读取根
  AGENTS.md、AI Spec manifest、统一执行协议和正式 Task Card；只有 Task、Requirement 和
  Decision 状态、base Commit、allowed paths、阶段准入及预期 Evidence 全部通过机器校验后才能
  实施。行为变更必须契约优先，完成声明必须逐条绑定 AC 与 Evidence Manifest。
rationale: 用户确认设计文档不仅描述目标，还必须约束 AI 编程 IDE 按设计实施并留下可审计证据。
decidedBy: User
decidedAt: "2026-07-02"
affected:
  documents:
    - AGENTS.md
    - 06-ide/generic-execution-protocol.md
    - 06-ide/trae-adapter.md
    - templates/task-card.md
    - templates/evidence-manifest.yaml
    - tools/validate-spec.ps1
    - tools/validate-task-card.ps1
requiresAdr: false
```

### DEC-010：P0 边界采用 ADR-0002，全阶段连续实施

```yaml
decisionId: DEC-010
questionIds: [Q-007]
status: ACCEPTED
decision: >
  P0 边界严格采用 ADR-0002 的 P0-A/P0-B 定义；P0-B 真实退出后，按 P1→P2→P3→P4→P5 顺序连续实施，
  不拆分独立交付阶段。资产登记/搜索/上传/发布仍按 P1-P3 实施，但不再等待阶段门禁人工审批。
rationale: 用户明确要求不分阶段直接进行完整开发实施。
decidedBy: User
decidedAt: "2026-07-04"
affected:
  requirements: [REQ-AST-001, REQ-VER-001, REQ-PUB-001]
  documents: [01-requirements/product-scope.md, 02-delivery/work-breakdown.md]
requiresAdr: false
```

### DEC-011：用户跨组织、Team 与 Owner 模型

```yaml
decisionId: DEC-011
questionIds: [Q-102, Q-103, Q-104, Q-105, Q-106]
status: ACCEPTED
decision: >
  用户可同时属于多个组织和项目；Team 是正式领域资源（Organization 内）；资产 Owner 至少一个 Team，
  可附个人 Maintainer；平台管理员建组织，组织管理员建项目/Team，项目成员按角色建资产；
  PUBLIC 仅对已认证主体公开，匿名不可访问。
rationale: 与设计文档第 5.4/5.5 节一致，支持灵活的多组织协作和严格的责任归属。
decidedBy: User
decidedAt: "2026-07-04"
affected:
  requirements: [REQ-ORG-001, REQ-ORG-002, REQ-AST-001, REQ-AST-006, REQ-AUTH-001, REQ-AUTH-003]
  invariants: [INV-ORG-001, INV-ORG-002, INV-ORG-003, INV-ORG-004, INV-ORG-005, INV-ORG-006, INV-TEAM-001, INV-TEAM-002, INV-TEAM-003, INV-TEAM-004, INV-TEAM-005, INV-TEAM-006, INV-TEAM-007, INV-AST-004, INV-AST-005]
  pages: [PAGE-ADM-003, PAGE-ADM-004, PAGE-ADM-005, PAGE-AST-005]
  contracts: [contracts/openapi/aihub-v1.yaml]
  migrations: [V14__asset_remediation.sql]
requiresAdr: false
```

### DEC-012：版本、发布与保留策略

```yaml
decisionId: DEC-012
questionIds: [Q-201, Q-202, Q-203, Q-204, Q-205, Q-206]
status: ACCEPTED
decision: >
  首个可交付版本同时支持 MODEL 和 DATASET；资产名称组织内 Namespace 唯一，重命名保留永久别名；
  提交人与审批人必须分离，平台管理员仅紧急越权并审计；所有发布均审批，高敏感/高风险追加安全审核；
  模型强制 SemVer，数据集允许受控日期版本；元数据 90 天可恢复，正式对象需引用检查后回收。
rationale: 与设计文档第 8/9/10 章一致，确保版本不可变和发布治理闭环。
decidedBy: User
decidedAt: "2026-07-04"
affected:
  requirements: [REQ-VER-001, REQ-VAL-001, REQ-REV-001, REQ-PUB-001, REQ-IMM-001, REQ-DEP-001, REQ-AST-007]
  invariants: [INV-VER-001, INV-VER-002, INV-VER-003, INV-VER-004, INV-VER-005, INV-VER-006, INV-VER-007, INV-VER-008, INV-VER-009]
  pages: [PAGE-VER-001, PAGE-VER-002, PAGE-REV-001, PAGE-REV-002]
  contracts: [contracts/openapi/aihub-v1.yaml]
  migrations: [V16__version_transfer.sql, V17__release_governance.sql]
requiresAdr: false
```

### DEC-013：UI 与非功能基线

```yaml
decisionId: DEC-013
questionIds: [Q-301, Q-302, Q-303, Q-304, Q-305]
status: ACCEPTED
decision: >
  必须同时支持 zh-CN/en-US 运行时切换；以 Ant Design 企业后台为 UI 基线；
  沿用 PRD 规模目标（10 万资产、Web 20GiB 上限）；Compose 为功能验收环境；
  浏览器 E2E 采用 Playwright + 关键页面截图。
rationale: 与设计文档第 13/14/16 章一致。
decidedBy: User
decidedAt: "2026-07-04"
affected:
  requirements: [REQ-UI-001, REQ-OBS-001, REQ-PERF-001]
  pages: [PAGE-AUTH-001, PAGE-COM-001, PAGE-COM-002, PAGE-COM-003]
  documents: [01-requirements/non-functional-requirements.md, 04-ui/information-architecture.md]
requiresAdr: false
```


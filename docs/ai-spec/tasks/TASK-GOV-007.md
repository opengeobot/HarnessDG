---
schemaVersion: harnessdg.task/v1
taskId: TASK-GOV-007
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: e490857bf889bf04976f5a012585dc8ecbd2600a
stageGatePassed: true
stageGateEvidence: DEC-007/DEC-008/DEC-009 authorize specification-only optimization
stageGateEvidenceRefs:
  - DEC-007
  - DEC-008
  - DEC-009
requirements:
  - REQ-AI-IDE-001
acceptanceScenarios:
  - AC-AI-IDE-001
  - AC-AI-IDE-002
  - AC-AI-IDE-003
  - AC-AI-IDE-004
  - AC-AI-IDE-005
  - AC-AI-IDE-006
  - AC-AI-IDE-007
decisions:
  - DEC-007
  - DEC-008
  - DEC-009
scenarioEvidencePlan:
  - AC-AI-IDE-001|E1|READY Task validator positive result
  - AC-AI-IDE-002|E1|DRAFT and unauthorized Task negative result
  - AC-AI-IDE-003|E1|OPEN requirement and unaccepted decision negative result
  - AC-AI-IDE-004|E1|base Commit and stage gate negative result
  - AC-AI-IDE-005|E1|broad path and changed path negative result
  - AC-AI-IDE-006|E1|completion Evidence negative result
  - AC-AI-IDE-007|E1|incomplete evidence plan and command negative result
crossCuttingPlan:
  - AUTHN|N/A - specification and static validator only
  - AUTHZ|Validate explicit Task authorization metadata only
  - DB_FILTER|N/A - no database or query behavior
  - STATE|Require READY Task and READY/ACCEPTED upstream state
  - IDEMPOTENCY|N/A - read-only local validation has no persistent side effect
  - CONSISTENCY|N/A - no business transaction or external system
  - ERRORS|Use nonzero process exit and enumerate validation failures
  - AUDIT|N/A - local E1 evidence is not a business audit event
  - NOTIFICATION|N/A - no business notification
  - TAXONOMY_I18N|N/A - no governed value or UI behavior
  - CONFIG|Task Front Matter is validation input; no deployment config
  - OBSERVABILITY|Command output is E1 evidence; no runtime service
  - SECRETS|Validator does not read secrets; Evidence requires redaction check
allowedPaths:
  - AGENTS.md
  - prd/DEEP_RESEARCH_内部AI资产管理平台设计.md
  - docs/architecture/data-ownership.md
  - docs/architecture/module-map.md
  - docs/ai-spec/00-governance/decision-log.md
  - docs/ai-spec/00-governance/current-state-audit-2026-07-02.md
  - docs/ai-spec/00-governance/open-questions.md
  - docs/ai-spec/00-governance/source-and-status-policy.md
  - docs/ai-spec/01-requirements
  - docs/ai-spec/02-domain
  - docs/ai-spec/02-delivery
  - docs/ai-spec/03-use-cases/user-journey-catalog.md
  - docs/ai-spec/04-ui
  - docs/ai-spec/05-acceptance
  - docs/ai-spec/06-ide
  - docs/ai-spec/templates
  - docs/ai-spec/tasks
  - docs/ai-spec/tools
  - docs/ai-spec/README.md
  - docs/ai-spec/manifest.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend
  - frontend
  - contracts
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E1
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-GOV007-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-GOV-007.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-GOV-007.md -CheckChangedPaths
  - git diff --check
approvedBy: User goal confirmation
approvedAt: 2026-07-02T21:36:39+08:00
---

# TASK-GOV-007：数据集目标与 AI IDE 实施门禁规格化

## 1. 目标

把用户确认的数据集/AI 工作流固化为可追踪 Requirement/AC，并使所有 AI 编程 IDE 在实施前被正式
Task Card、阶段、状态、范围和证据机器门禁约束。

## 2. 关联规格

- Requirement：`REQ-AI-IDE-001`
- Acceptance：`AC-AI-IDE-001..006`
- Decision：`DEC-007`、`DEC-008`、`DEC-009`
- 产品目标规格：`01-requirements/dataset-experience.md`

## 3. 范围

只修改 Front Matter `allowedPaths` 中的设计/规格/治理文件。产品代码、机器契约、Migration、部署和
测试均禁止修改。

## 4. 行为切片

| 场景 | 本任务结果 | 证据 |
| --- | --- | --- |
| `AC-AI-IDE-001` | 合法正式 Task 可通过校验 | validator 正例 |
| `AC-AI-IDE-002` | DRAFT 模板被阻断 | validator 反例 |
| `AC-AI-IDE-003` | 非 READY/ACCEPTED/未知引用被阻断 | validator 逻辑与静态检查 |
| `AC-AI-IDE-004` | base Commit/阶段漂移被阻断 | validator 正反例 |
| `AC-AI-IDE-005` | 过宽/逃逸 allowedPaths 被阻断 | validator 逻辑与反例 |
| `AC-AI-IDE-006` | 不完整或未评审 Evidence 不能通过完成门禁 | completion validator 反例 |
| `AC-AI-IDE-007` | AC 证据计划、任务专用命令和横切项不可留空 | validator 正反例 |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：仅修改规格与本地静态校验器，不处理运行时身份 |
| Permission/Scope/ACL | N/A：仅校验 Task 授权元数据，不改变业务权限 |
| 数据库权限下推 | N/A：无数据库或查询代码变更 |
| 状态前置条件 | Task 必须 READY、显式授权且引用 READY/ACCEPTED 上游 |
| 幂等与并发 | N/A：校验器只读仓库，无持久化副作用 |
| 事务与外部一致性 | N/A：不访问业务数据库或外部系统 |
| 错误码 | 校验失败使用非零进程退出码并逐项输出原因 |
| 审计事件 | N/A：E1 本地治理校验不产生业务审计事件 |
| 通知 | N/A：不触发业务通知 |
| 字典/标签/i18n | N/A：不改变受控值或用户界面文案 |
| 配置 | Task Front Matter 是校验输入，不新增部署配置 |
| 指标/Trace/告警 | N/A：E1 命令输出作为 Evidence，不是运行时服务 |
| Secret/日志脱敏 | Evidence 必须声明 redactionChecked，校验器不读取或输出 Secret |

## 6. 契约与数据先行

不修改 OpenAPI、MCP、Event、Flyway 或生成类型。仅增加 `harnessdg.task/v1` 的附加机器字段和
完成门禁；该变更不授权产品行为。

## 7. 验证命令

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-GOV-007.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-GOV-007.md -CheckChangedPaths
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-GOV-007.md -CheckCompletion
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/templates/task-card.md
git diff --check
```

前三条预期退出 0；`-CheckCompletion` 在工作树未提交且 Evidence 尚未独立接受时预期非零，
证明不能伪报完成。DRAFT 模板同样预期非零；`git diff --check` 预期退出 0。

## 8. 完成边界

本 Task 只证明规格结构与机器门禁可以约束实施入口，不证明 P0-B、数据集业务或 Agent 业务已经实现。
`AC-AI-IDE-008` 的 CI 强制门禁由 `TASK-GOV-008` 处理，不在当前批准路径内。

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P3-006
status: READY
implementationAuthorized: true
phase: P3
baseCommit: 10ffb20d7c1850a7e5a87893a7d2274cb491e737
stageGatePassed: true
stageGateEvidence: P2 wiring committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-REV-002
acceptanceScenarios:
  - AC-P3-UI-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P3-UI-001|E4|Review and version detail pages show frozen commit/digest and validation summary
crossCuttingPlan:
  - AUTHN|JWT PrincipalContext required for submit/review/publish actions
  - AUTHZ|asset:submit/review/publish/deprecate permissions enforced fail-closed
  - DB_FILTER|Version and publish_request queries scoped to authorized assets
  - STATE|VersionStatus and publish_request status machines per state-machines.md
  - IDEMPOTENCY|Submit, approve and publish saga idempotent replay
  - CONSISTENCY|P1-P7 publish saga with Gitea tag before PG commit when enabled
  - ERRORS|VERSION_STATE_NOT_ALLOWED, GITEA_DEPENDENCY_UNAVAILABLE, tag conflict catalog
  - AUDIT|VERSION_REVIEW_REQUESTED/APPROVED/REJECTED and VERSION_PUBLISHED events
  - NOTIFICATION|Outbox events for review and publish lifecycle
  - TAXONOMY_I18N|Structured validation findings use i18nKey codes
  - CONFIG|aihub.gitea.enabled controls real tag creation vs PG-only mode
  - OBSERVABILITY|Saga step logging and reconciliation metrics
  - SECRETS|Gitea token never logged; validation reports redact internal paths
allowedPaths:
  - frontend/src/features/version
  - frontend/src/shared/i18n/en.json
  - frontend/src/shared/i18n/zh.json
  - docs/ai-spec/tasks/evidence/EVD-P3006-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P3006-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-006.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-006.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P3-006：审批中心、版本详情与 Diff/Review UI

> 状态：`READY`

## 1. 目标

审批中心与版本详情展示冻结 revision、校验报告与 Diff 审阅能力。

## 2. 关联规格

```yaml
requirements: ['REQ-REV-002']
acceptanceScenarios: ['AC-P3-UI-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

见 Front Matter `allowedPaths`。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P3-UI-001` | Actor | Given preconditions | Execute action | PASS criteria | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V17+ 为既有 release governance 下限

## 7. 实施步骤

- [ ] 按 allowedPaths 实现目标行为
- [ ] 补充单元/集成测试
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-006.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-006.md -CheckChangedPaths
cd frontend && pnpm lint
cd frontend && pnpm typecheck
cd frontend && pnpm test
  - cd frontend && pnpm build
```

## 9. 停止条件

- validate-task-card 或 task-specific 命令失败
- AC 证据未达到 requiredEvidenceLevel

## 10. 完成报告

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```

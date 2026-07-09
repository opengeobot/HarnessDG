---
schemaVersion: harnessdg.task/v1
taskId: TASK-P5-008
status: READY
implementationAuthorized: true
phase: P5
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P4 agent integration gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
  - DEC-016
requirements:
  - REQ-OBS-001
acceptanceScenarios:
  - AC-P5-OBS-001
decisions:
  - DEC-010
  - DEC-016
scenarioEvidencePlan:
  - AC-P5-OBS-001|E5|Download heat stats from audit_log aggregation on model dataset cards per DEC-016
crossCuttingPlan:
  - AUTHN|Platform JWT and job principal for reconciliation and ops tasks
  - AUTHZ|Reconciler and ops actions require job:manage or dedicated permissions
  - DB_FILTER|Reconciler scans respect organization/project scope predicates
  - STATE|Job/Inbox/Outbox/Preview lifecycle states per state-machines.md
  - IDEMPOTENCY|Webhook Delivery ID and reconciler repair actions are idempotent
  - CONSISTENCY|Saga/Inbox/Outbox for webhook projection and repair workflows
  - ERRORS|Stable error codes for inbox parse failure and reconciler incidents
  - AUDIT|Reconciler repairs, backup drills and security findings audited
  - NOTIFICATION|MANUAL_REVIEW and SECURITY_INCIDENT create operator notifications
  - TAXONOMY_I18N|N/A - ops metrics use stable metric names
  - CONFIG|RPO/RTO thresholds and performance gates from configuration service
  - OBSERVABILITY|Dashboard SLI, alerts and runbook-linked metrics
  - SECRETS|Backup/restore and scan reports redact credentials and presigned URLs
allowedPaths:
  - backend/src/main/java/com/aihub/observability
  - backend/src/test/java/com/aihub/observability
  - frontend/src/features/assets/DownloadStats.tsx
  - docs/ai-spec/tasks/evidence/EVD-P5008-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E5
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P5008-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-008.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-008.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P5-008：下载量热度统计面板（DEC-016）

> 状态：`READY`

## 1. 目标

实现 DEC-016 推迟项：基于 audit_log 下载授权事件聚合，在模型/数据集卡片展示下载量。

## 2. 关联规格

```yaml
requirements: ['REQ-OBS-001']
acceptanceScenarios: ['AC-P5-OBS-001']
decisions: ['DEC-010', 'DEC-016']
```

## 3. 范围

### 允许修改

- 见 Front Matter `allowedPaths`。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P5-OBS-001` | Operator | Download heat stats from audit_log aggregation on model dataset cards per DEC-016 | Execute | PASS | `E5` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] audit_log 聚合查询 API；DownloadStats 组件；AC-P5-OBS-001 E5 证据。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-008.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-008.md -CheckChangedPaths
cd backend && ./mvnw -o verify
cd frontend && pnpm lint
cd frontend && pnpm typecheck
cd frontend && pnpm test
cd frontend && pnpm build
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

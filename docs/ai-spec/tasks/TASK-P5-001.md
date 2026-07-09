---
schemaVersion: harnessdg.task/v1
taskId: TASK-P5-001
status: READY
implementationAuthorized: true
phase: P5
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P4 agent integration gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-INBOX-001
  - REQ-REC-001
acceptanceScenarios:
  - AC-P5-INBOX-001
  - AC-P5-REC-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P5-INBOX-001|E4|Gitea webhook inbox signature verification idempotent delivery and async parse
  - AC-P5-REC-001|E4|Full reconciler scan repair dry-run across PG Gitea MinIO Job Outbox projections
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
  - backend/src/main/java/com/aihub/integration/gitea
  - backend/src/main/java/com/aihub/reconciliation
  - backend/src/main/resources/db/migration/V28__reconciliation_checkpoint.sql
  - backend/src/test/java/com/aihub/reconciliation
  - docs/ai-spec/tasks/evidence/EVD-P5001-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P5001-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-001.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-001.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P5-001：Gitea Webhook Inbox 与全量 Reconciler

> 状态：`READY`

## 1. 目标

实现 REQ-INBOX-001 Gitea Webhook Inbox 与 REQ-REC-001 全量对账与安全修复。

## 2. 关联规格

```yaml
requirements: ['REQ-INBOX-001', 'REQ-REC-001']
acceptanceScenarios: ['AC-P5-INBOX-001', 'AC-P5-REC-001']
decisions: ['DEC-010']
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
| `AC-P5-INBOX-001` | Operator | Gitea webhook inbox signature verification idempotent delivery and async parse | Execute | PASS | `E4` |
| `AC-P5-REC-001` | Operator | Full reconciler scan repair dry-run across PG Gitea MinIO Job Outbox projections | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] webhook_inbox 表与 Handler；Reconciler Job 扫描/分类/修复；故障注入测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-001.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-001.md -CheckChangedPaths
cd backend && ./mvnw -o verify
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

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P5-004
status: READY
implementationAuthorized: true
phase: P5
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P4 agent integration gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-PERF-001
acceptanceScenarios:
  - AC-P5-PERF-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P5-PERF-001|E5|Performance gates at 100k Asset scale with search ticket webhook thresholds
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
  - deploy/perf
  - docs/runbooks/performance.md
  - docs/ai-spec/tasks/evidence/EVD-P5004-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E5
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P5004-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-004.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-004.md -CheckChangedPaths
  - test -d deploy/perf
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P5-004：目标规模性能基线与回归阈值

> 状态：`READY`

## 1. 目标

实现 REQ-PERF-001 10 万 Asset 目标规模性能基线与回归门禁。

## 2. 关联规格

```yaml
requirements: ['REQ-PERF-001']
acceptanceScenarios: ['AC-P5-PERF-001']
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
| `AC-P5-PERF-001` | Operator | Performance gates at 100k Asset scale with search ticket webhook thresholds | Execute | PASS | `E5` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 数据生成器；混合负载脚本；P50/P95/P99 报告绑定 Commit。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-004.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-004.md -CheckChangedPaths
test -d deploy/perf
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

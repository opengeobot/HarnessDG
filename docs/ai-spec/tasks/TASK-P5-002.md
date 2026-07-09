---
schemaVersion: harnessdg.task/v1
taskId: TASK-P5-002
status: READY
implementationAuthorized: true
phase: P5
baseCommit: 59f40d28dab5a9fb5fdeb5cb29080219b10ee4a6
stageGatePassed: true
stageGateEvidence: P4 agent integration gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-PRE-001
acceptanceScenarios:
  - AC-P5-PRE-001
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-P5-PRE-001|E5|Preview E5 format extension resource isolation pressure and security canary with P2 regression
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
  - backend/src/main/java/com/aihub/version/infrastructure
  - backend/src/test/java/com/aihub/version
  - deploy/compose/scripts/p5-preview
  - docs/ai-spec/tasks/evidence/EVD-P5002-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E5
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P5002-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-002.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-002.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P5-002：预览 E5 格式扩展与资源隔离

> 状态：`READY`

## 1. 目标

在 P2 最小预览上扩展 REQ-PRE-001 格式矩阵、资源隔离、压力与安全 E5，回归 AC-DST-PRE-*。

## 2. 关联规格

```yaml
requirements: ['REQ-PRE-001']
acceptanceScenarios: ['AC-P5-PRE-001']
decisions: ['DEC-008', 'DEC-010']
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
| `AC-P5-PRE-001` | Operator | Preview E5 format extension resource isolation pressure and security canary with P2 regression | Execute | PASS | `E5` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/MCP/Event/Migration（如适用）

## 7. 实施步骤

- [ ] 扩展 Preview Worker 格式与安全限制；p5-preview 压力脚本；PII canary 零泄漏验证。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-002.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P5-002.md -CheckChangedPaths
cd backend && ./mvnw -o verify
./deploy/compose/scripts/verify.sh
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

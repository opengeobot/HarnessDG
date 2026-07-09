---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-009
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-001
acceptanceScenarios:
  - AC-P1-AST-014
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P1-AST-014|E4|Clean Compose JRN-P1-001..004 two-principal journey with E4 evidence manifest
crossCuttingPlan:
  - AUTHN|Two real principals with distinct JWT sessions in journey script
  - AUTHZ|Journey proves authorized vs unauthorized paths for JRN-P1-001..004
  - DB_FILTER|Journey asserts list/detail never leak cross-org assets
  - STATE|Full asset lifecycle visible in journey assertions
  - IDEMPOTENCY|Journey includes create idempotency replay step
  - CONSISTENCY|Gitea repo state matches DB projection at journey checkpoints
  - ERRORS|Journey captures stable error codes for deny paths
  - AUDIT|Journey verifies audit events for create/update/lifecycle
  - NOTIFICATION|N/A - unless journey includes notification assertion
  - TAXONOMY_I18N|Journey uses governed dictionary and tag values only
  - CONFIG|Compose fixtures use secure defaults
  - OBSERVABILITY|Journey captures trace IDs for correlation
  - SECRETS|Journey scripts redact tokens in logs and evidence
allowedPaths:
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/p1-journey
  - deploy/compose/fixtures/p1
  - docs/ai-spec/tasks/evidence/EVD-P1009-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1009-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-009.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-009.md -CheckChangedPaths
  - docker compose -f deploy/compose/docker-compose.yml config --quiet
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-009：Compose 两主体 P1 纵向旅程出口

> 状态：`READY`

## 1. 目标

在干净 Compose 环境完成两主体 JRN-P1-001..004 纵向旅程 E4 出口。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-014']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- verify.sh P1 旅程脚本、fixtures、Evidence Manifest。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-014` | Maintainer | Clean Compose JRN-P1-001..004 two-principal journey with E4 evidence manifest | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 编写 p1-journey 脚本；两主体 fixture；绑定 AC-P1-AST-014 Evidence。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-009.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-009.md -CheckChangedPaths
docker compose -f deploy/compose/docker-compose.yml config --quiet
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

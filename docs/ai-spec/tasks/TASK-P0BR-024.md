---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-024
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-JOB-001
acceptanceScenarios:
  - AC-P0B-JOB-004
  - AC-P0B-JOB-005
decisions:
  - DEC-001
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|See task description
  - AUTHZ|See task description
  - DB_FILTER|N/A
  - STATE|See task description
  - IDEMPOTENCY|See task description
  - CONSISTENCY|N/A
  - ERRORS|See task description
  - AUDIT|See task description
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|See task description
allowedPaths:
  - backend/src/main/java/com/aihub
  - backend/src/test/java/com/aihub
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR024-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-024.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-024：DEAD、人工重试/取消、通知和审计 E4

> 状态：`READY`

## 1. 目标

验证不可恢复/达最大重试进入 DEAD 并产生通知/告警/审计；人工重试/取消检查状态和权限并审计。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001]
acceptanceScenarios: [AC-P0B-JOB-004,AC-P0B-JOB-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-JOB-004 | 不可恢复/最大重试 | 达上限 | DEAD + 通知/告警/审计 | E4 |
| AC-P0B-JOB-005 | 未授权/有权运维 | 重试/取消 | 前者拒绝，后者遵守状态前置条件 | E4 |

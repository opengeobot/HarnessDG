---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-030
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-OBS-001
acceptanceScenarios:
  - AC-P0B-OBS-005
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR030-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-030.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-030：P0-B 必需告警在故障注入下触发/恢复

> 状态：`READY`

## 1. 目标

在 Compose 环境中制造 DEAD 任务、Inbox/Outbox 积压、依赖故障和安全拒绝，验证对应告警产生，恢复后状态可确认。

## 2. 关联规格

```yaml
requirements: [REQ-OBS-001]
acceptanceScenarios: [AC-P0B-OBS-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-OBS-005 | 故障注入 | DEAD/积压/依赖故障/安全拒绝 | 对应告警产生，恢复后可确认 | E5 |

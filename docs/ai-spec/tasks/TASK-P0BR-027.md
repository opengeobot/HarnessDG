---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-027
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-NOT-001
acceptanceScenarios:
  - AC-P0B-NOT-001
  - AC-P0B-NOT-002
decisions:
  - DEC-001
  - DEC-020
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR027-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-027.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-027：站内通知+Outbox 原子性和读取 E4（含 DEC-020 修复）

> 状态：`READY`

## 1. 目标

验证业务事务内创建通知和 Outbox 原子性：事务提交后接收者可分页查询；事务回滚后两者均不存在。含 DEC-020 修复：IdPrefix.EVENT(evt)、publishOutboxEvent() 接入业务流程。

## 2. 关联规格

```yaml
requirements: [REQ-NOT-001]
acceptanceScenarios: [AC-P0B-NOT-001,AC-P0B-NOT-002]
decisions: [DEC-001,DEC-020]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-NOT-001 | 核心事务提交 | — | Notification + Outbox 原子可见，接收者可分页查询 | E4 |
| AC-P0B-NOT-002 | 核心事务回滚 | — | 通知和 Outbox 均不存在 | E4 |

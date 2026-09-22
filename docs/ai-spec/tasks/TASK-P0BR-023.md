---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-023
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
  - AC-P0B-JOB-001
  - AC-P0B-JOB-002
  - AC-P0B-JOB-003
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR023-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-023.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-023：可靠任务用真实 Handler 证明多 Worker/崩溃/恢复

> 状态：`READY`

## 1. 目标

使用真实业务 Handler（非 SampleJobHandler）验证：多 Worker 并发领取无重复执行、Worker 崩溃后租约过期重新领取、可重试错误指数退避后恢复。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001]
acceptanceScenarios: [AC-P0B-JOB-001,AC-P0B-JOB-002,AC-P0B-JOB-003]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-JOB-001 | 多 Worker | 并发领取任务 | 每个租约同一时刻只属于一个 Worker | E4 |
| AC-P0B-JOB-002 | Worker 已领取 | 强制终止 | 租约过期后重新领取，Handler 幂等 | E4 |
| AC-P0B-JOB-003 | 可重试错误 | 持续后恢复 | 指数退避+抖动，恢复后成功 | E4 |

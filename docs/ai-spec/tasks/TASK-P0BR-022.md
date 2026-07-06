---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-022
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IDM-001
acceptanceScenarios:
  - AC-P0B-IDM-001
  - AC-P0B-IDM-002
  - AC-P0B-IDM-003
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR022-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-022.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-022：IdempotencyService 接入真实写用例并证明重放/冲突/并发

> 状态：`READY`

## 1. 目标

将 IdempotencyService 接入首个真实写路径（如用户创建），验证：同 key 同 digest 重放不重复副作用、同 key 不同 digest 返回 IDEMPOTENCY_KEY_CONFLICT、并发只有一个执行。

## 2. 关联规格

```yaml
requirements: [REQ-IDM-001]
acceptanceScenarios: [AC-P0B-IDM-001,AC-P0B-IDM-002,AC-P0B-IDM-003]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-IDM-001 | 同主体/方法/路径/digest | 重放请求 | 返回首次结果，无重复业务/审计副作用 | E4 |
| AC-P0B-IDM-002 | 同幂等键 | 不同请求摘要 | IDEMPOTENCY_KEY_CONFLICT | E4 |
| AC-P0B-IDM-003 | 同幂等键 | 并发请求 | 只有一个执行，无重复记录 | E4 |

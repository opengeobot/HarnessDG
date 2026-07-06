---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-021
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-CFG-001
acceptanceScenarios:
  - AC-P0B-CFG-001
  - AC-P0B-CFG-002
  - AC-P0B-CFG-003
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR021-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-021.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-021：类型化配置、Secret 拒绝、并发冲突和生效提示 E4

> 状态：`READY`

## 1. 目标

验证配置管理：合法/非法类型值、Secret 键/值拒绝（CONFIG_SECRET_FORBIDDEN）、并发更新版本冲突（CONCURRENT_MODIFICATION）和 hotReloadable 生效提示。

## 2. 关联规格

```yaml
requirements: [REQ-CFG-001]
acceptanceScenarios: [AC-P0B-CFG-001,AC-P0B-CFG-002,AC-P0B-CFG-003]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-CFG-001 | 管理员 | 提交合法/非法配置 | 合法更新版本，非法返回 CONFIG_VALUE_INVALID | E4 |
| AC-P0B-CFG-002 | — | 写 password/token/privateKey 类 Key | CONFIG_SECRET_FORBIDDEN，日志/审计不保留值 | E4 |
| AC-P0B-CFG-003 | 两客户端同版本 | 并发更新 | 一个成功，另一个 CONCURRENT_MODIFICATION | E4 |

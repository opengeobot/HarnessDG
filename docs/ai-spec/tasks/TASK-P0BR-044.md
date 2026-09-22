---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-044
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-COM-001
acceptanceScenarios:
  - AC-P0B-ENG-004
  - AC-P0B-ENG-005
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
  - TAXONOMY_I18N|See task description
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|See task description
allowedPaths:
  - frontend/src
  - backend/src/main/java/com/aihub
  - backend/src/test/java/com/aihub
  - deploy/compose
  - docs/ai-spec
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR044-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-044.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-044：Runbook、V 编号和 AC 覆盖映射同步

> 状态：`READY`

## 1. 目标

更新 Runbook（compose.md）与实际 V1-V23+ 迁移和 Verify 用例同步；建立 AC ID 到 V 编号的覆盖映射；确保文档与代码一致。

## 2. 关联规格

```yaml
requirements: [REQ-COM-001]
acceptanceScenarios: [AC-P0B-ENG-004,AC-P0B-ENG-005]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-ENG-004 | 空 PostgreSQL | 执行 V1 到最新 | 全部成功，约束/Seed 存在 | E3 |
| AC-P0B-ENG-005 | 上一版本 | 升级 | 存量数据不丢失 | E3 |

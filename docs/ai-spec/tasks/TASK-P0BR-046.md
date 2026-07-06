---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-046
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
  - AC-P0B-ENG-001
decisions:
  - DEC-001
  - DEC-003
  - DEC-010
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR046-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-046.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-046：更新阶段状态并解除 P1 门禁

> 状态：`READY`

## 1. 目标

仅在 P0BR-045 无缺口时：更新 AGENTS.md 阶段状态、source-and-status-policy.md、manifest.yaml 为 P0-B VERIFIED；解除 P1 准入门。此任务不新增功能。

## 2. 关联规格

```yaml
requirements: [REQ-COM-001]
acceptanceScenarios: [AC-P0B-ENG-001]
decisions: [DEC-001,DEC-003,DEC-010]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| — | P0BR-045 全部 PASS | 更新文档 | AGENTS.md 阶段变更，P1 门禁解除 | E1 |

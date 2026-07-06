---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-042
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-UI-001
  - REQ-AGT-001
  - REQ-AUTH-003
acceptanceScenarios:
  - AC-P0B-UI-004
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR042-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-042.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-042：管理表单移除自由 Scope/Tool/Principal/Permission 输入

> 状态：`READY`

## 1. 目标

验证创建 Agent、角色、标签、配置等表单中 Scope/Permission/Tool/Tag/字典均来自受控选择（ControlledSelect），不接受任意 tags 输入或自由文本。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001,REQ-AGT-001,REQ-AUTH-003]
acceptanceScenarios: [AC-P0B-UI-004]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-UI-004 | 管理表单 | 创建 Agent/角色/标签/配置 | Scope/Permission/Tool/Tag/字典来自受控选择 | E4 |

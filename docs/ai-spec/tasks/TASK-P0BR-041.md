---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-041
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
  - REQ-COM-001
acceptanceScenarios:
  - AC-P0B-UI-003
  - AC-P0B-UI-005
decisions:
  - DEC-013
  - DEC-019
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR041-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-041：运行时 zh/en i18n 和统一错误/页面状态 E4（DEC-019）

> 状态：`READY`

## 1. 目标

验证管理页面 Loading/Empty/Error/Retry/Success 状态完整；切换 locale 后页面和后端错误使用对应语言渲染；全部 56 个 error.* i18n key 在 zh.json/en.json 中完整。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001,REQ-COM-001]
acceptanceScenarios: [AC-P0B-UI-003,AC-P0B-UI-005]
decisions: [DEC-013,DEC-019]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-UI-003 | 管理页面 | 加载/空/错误/成功 | Loading/Empty/Error/Retry/Success 均有交互 | E4 |
| AC-P0B-UI-005 | 切换 locale | 触发后端错误 | 页面和错误使用对应语言 | E4 |

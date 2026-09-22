---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-043
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
acceptanceScenarios:
  - AC-P0B-UI-006
decisions:
  - DEC-013
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR043-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-043.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-043：P0-B 管理页面组件测试+浏览器 E2E+视觉证据

> 状态：`READY`

## 1. 目标

为关键管理页面编写 Vitest/Testing Library 组件测试和 Playwright 浏览器 E2E 测试，覆盖表单、权限、Token、路由和页面状态。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-006]
decisions: [DEC-013]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-UI-006 | — | 运行前端测试 | 关键表单/权限/Token/路由/页面状态有自动化证据 | E2/E4 |

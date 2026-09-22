---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-040
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
  - REQ-IAM-003
acceptanceScenarios:
  - AC-P0B-UI-001
  - AC-P0B-UI-002
decisions:
  - DEC-001
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR040-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-040.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-040：前端 Auth、Route Catalog、缓存清理和 Token 存储 E4

> 状态：`READY`

## 1. 目标

验证未登录访问受保护路由正确跳转登录页并返回原路由；access Token 仅存内存，refresh 仅 Cookie；不同权限用户菜单/路由/按钮变化，直接请求仍被后端拒绝。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001,REQ-IAM-003]
acceptanceScenarios: [AC-P0B-UI-001,AC-P0B-UI-002]
decisions: [DEC-001,DEC-013]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-UI-001 | 未登录 | 访问受保护路由 | 跳转登录，登录后返回原路由；Token 仅内存 | E4 |
| AC-P0B-UI-002 | 不同权限用户 | 访问菜单/路由/按钮 | UI 按权限变化；直接请求后端拒绝 | E4 |

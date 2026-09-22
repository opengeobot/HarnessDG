---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-045
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
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-ENG-001
  - AC-P0B-ENG-006
decisions:
  - DEC-001
  - DEC-003
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR045-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-045.md
  - cd backend && ./mvnw -o verify
  - cd frontend && pnpm lint && pnpm typecheck && pnpm build
approvedBy: null
approvedAt: null
---

# TASK-P0BR-045：干净环境执行 70 条 P0-B MUST 场景并生成 Evidence Manifest

> 状态：`READY`

## 1. 目标

在干净 Compose 环境中执行 p0b-exit-catalog 全部 70 条 MUST 场景，每条生成 Evidence Manifest，不允许 SKIP。此任务不新增功能。

## 2. 关联规格

```yaml
requirements: [REQ-COM-001,REQ-UI-001]
acceptanceScenarios: [AC-P0B-ENG-001,AC-P0B-ENG-006]
decisions: [DEC-001,DEC-003]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-ENG-001 | 空工作区 | 按锁文件构建 | 编译/Lint/类型/单测/ArchUnit 全通过 | E1/E2 |
| AC-P0B-ENG-006 | — | 扫描可部署 Profile | 无空 Principal/allow-all/默认全 Scope | E1/E3 |

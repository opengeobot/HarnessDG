---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-015
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AUTH-001
  - REQ-AUTH-002
acceptanceScenarios: []
decisions:
  - DEC-001
  - DEC-011
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|N/A - authorization refactor, not authentication
  - AUTHZ|Move authorization from Controller boolean to Application Use Case service
  - DB_FILTER|Ensure queries use PrincipalContext scope
  - STATE|N/A
  - IDEMPOTENCY|N/A
  - CONSISTENCY|N/A
  - ERRORS|AUTH_PERMISSION_DENIED from application layer
  - AUDIT|Authorization decisions logged from application layer
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A
allowedPaths:
  - backend/src/main/java/com/aihub/authorization
  - backend/src/main/java/com/aihub/organization
  - backend/src/main/java/com/aihub/asset
  - backend/src/main/java/com/aihub/taxonomy
  - backend/src/main/java/com/aihub/configuration
  - backend/src/main/java/com/aihub/job
  - backend/src/test/java/com/aihub/authorization
  - backend/src/test/java/com/aihub/organization
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR015-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-015.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-015：授权从 Controller 布尔值收敛到可复用 Application Use Case

> 状态：`READY`

## 1. 目标

将分散在多个 Controller 中的 `platformAdmin` 布尔值和直接 AuthorizationService 调用收敛到统一的 Application Use Case 层，确保 REST/MCP/Worker 复用同一授权逻辑。消除 AUD-010。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-001, REQ-AUTH-002]
acceptanceScenarios: []
decisions: [DEC-001, DEC-011]
```

## 3. 范围

所有 Application Service 中的授权调用模式统一化；Controller 移除 `platformAdmin` 等可伪造参数。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | 开发者 | 代码审查 | 搜索 Controller 中 AuthorizationService 调用 | 仅在 Application Service 中调用 | `E2` |

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-025
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AUD-001
acceptanceScenarios:
  - AC-P0B-AUD-001
  - AC-P0B-AUD-003
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR025-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-025.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-025：必审计成功/失败/拒绝覆盖和不可篡改 E4

> 状态：`READY`

## 1. 目标

验证写操作成功/业务失败/权限拒绝 100% 审计记录正确 result/errorCode；审计记录不可通过 API/DB 更新/删除；查询受 audit:read 和游标限制。

## 2. 关联规格

```yaml
requirements: [REQ-AUD-001]
acceptanceScenarios: [AC-P0B-AUD-001,AC-P0B-AUD-003]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Given | When | Then | 证据 |
| --- | --- | --- | --- | --- |
| AC-P0B-AUD-001 | 分别触发写成功/失败/拒绝 | — | 必需事件均追加记录正确 result/errorCode | E4 |
| AC-P0B-AUD-003 | 管理员尝试更新/删除审计 | — | 无 API/DB 路径，约束阻止；查询按权限游标 | E4 |

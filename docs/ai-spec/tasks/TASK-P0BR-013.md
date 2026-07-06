---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-013
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IAM-005
acceptanceScenarios:
  - AC-P0B-IAM-008
  - AC-P0B-AGT-003
decisions:
  - DEC-001
scenarioEvidencePlan:
  - AC-P0B-IAM-008|E4|Disabled user's old access JWT rejected on next request, refresh/credential rejected
  - AC-P0B-AGT-003|E4|Disabled agent's old JWT and credentials immediately invalidated
crossCuttingPlan:
  - AUTHN|tokenVersion increment on disable, real-time check in filter
  - AUTHZ|user:manage / agent:authorize permissions
  - DB_FILTER|N/A
  - STATE|UserStatus/AgentStatus: ACTIVE->DISABLED
  - IDEMPOTENCY|Disable is idempotent
  - CONSISTENCY|tokenVersion increment + token family revoke atomic
  - ERRORS|AUTH_ACCOUNT_DISABLED, AUTH_UNAUTHENTICATED
  - AUDIT|USER_DISABLED/ENABLED, AGENT_DISABLED/ENABLED
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|Disable metrics
  - SECRETS|Credential never in response after first display
allowedPaths:
  - backend/src/main/java/com/aihub/identity
  - backend/src/main/java/com/aihub/platform/security
  - backend/src/main/java/com/aihub/bootstrap/security
  - backend/src/test/java/com/aihub/identity
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR013-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-013.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-013：禁用 User/Agent 后旧 Token 实时失效

> 状态：`READY`

## 1. 目标

验证管理员禁用用户/Agent 后，旧 access JWT 的下一请求立即被拒，refresh 和凭据交换均被拒绝。tokenVersion 递增确保实时失效。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-005]
acceptanceScenarios: [AC-P0B-IAM-008, AC-P0B-AGT-003]
decisions: [DEC-001]
```

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-008` | 管理员 | 用户正在使用 | 禁用用户 | 旧 access 下一请求拒绝，refresh 被拒 | `E4` |
| `AC-P0B-AGT-003` | 管理员 | Agent 活跃 | 禁用 Agent | 旧 JWT 和凭据全部立即失效 | `E4` |

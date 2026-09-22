---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-011
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IAM-002
  - REQ-IAM-006
acceptanceScenarios:
  - AC-P0B-IAM-004
  - AC-P0B-IAM-005
  - AC-P0B-IAM-012
decisions:
  - DEC-001
scenarioEvidencePlan:
  - AC-P0B-IAM-004|E4|Login with wrong username/password both return AUTH_INVALID_CREDENTIALS
  - AC-P0B-IAM-005|E4|Consecutive failures reach threshold, account LOCKED, correct password still rejected
  - AC-P0B-IAM-012|E4|Admin create/edit/reset/disable/enable user lifecycle
crossCuttingPlan:
  - AUTHN|Login failure handling, lockout mechanism
  - AUTHZ|Admin permissions for user lifecycle management
  - DB_FILTER|N/A
  - STATE|UserStatus: ACTIVE->LOCKED on threshold, LOCKED->ACTIVE on admin unlock
  - IDEMPOTENCY|User create idempotent by username
  - CONSISTENCY|N/A
  - ERRORS|AUTH_INVALID_CREDENTIALS, AUTH_ACCOUNT_LOCKED, USER_ALREADY_EXISTS
  - AUDIT|AUTH_LOGIN_FAILED, USER_CREATED/ENABLED/DISABLED/PASSWORD_RESET
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|Lockout threshold from system_config
  - OBSERVABILITY|Login failure metrics
  - SECRETS|Password never in logs/audit/responses
allowedPaths:
  - backend/src/main/java/com/aihub/identity
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR011-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-011.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-011：登录失败、锁定、解锁/启用状态机 E4

> 状态：`READY`

## 1. 目标

验证登录失败不暴露账号存在性、连续失败达到阈值锁定、管理员解锁/启用/禁用/重置密码的完整用户生命周期状态机。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-002, REQ-IAM-006]
acceptanceScenarios: [AC-P0B-IAM-004, AC-P0B-IAM-005, AC-P0B-IAM-012]
decisions: [DEC-001]
```

## 3. 范围

identity 模块认证和用户管理逻辑、对应测试、Compose Verify。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-004` | 用户 | — | 用户名不存在/密码错误登录 | 两者均返回 AUTH_INVALID_CREDENTIALS | `E4` |
| `AC-P0B-IAM-005` | 用户 | 连续失败达阈值 | 用正确密码登录 | AUTH_ACCOUNT_LOCKED | `E4` |
| `AC-P0B-IAM-012` | 管理员 | 有效权限 | CRUD 用户生命周期 | 状态、权限、审计均正确 | `E4` |

## 5-10. （同模板结构，详见 TASK-P0BR-010 格式）

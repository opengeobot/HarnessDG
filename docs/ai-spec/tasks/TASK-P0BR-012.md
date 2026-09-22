---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-012
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IAM-004
  - REQ-IAM-005
acceptanceScenarios:
  - AC-P0B-IAM-006
  - AC-P0B-IAM-007
  - AC-P0B-IAM-009
  - AC-P0B-IAM-011
decisions:
  - DEC-001
scenarioEvidencePlan:
  - AC-P0B-IAM-006|E4|Refresh rotates access/refresh, old jti marked ROTATED
  - AC-P0B-IAM-007|E4|Reused refresh JWT returns AUTH_REFRESH_REPLAYED, family REVOKED
  - AC-P0B-IAM-009|E4|Logout clears cookie, family revoked, reuse rejected, idempotent
  - AC-P0B-IAM-011|E4|Concurrent refresh: only one succeeds, other gets replay/concurrency semantics
crossCuttingPlan:
  - AUTHN|Token rotation, replay detection, logout
  - AUTHZ|N/A - token lifecycle, not authorization
  - DB_FILTER|N/A
  - STATE|RefreshTokenStatus: ACTIVE->ROTATED->REVOKED
  - IDEMPOTENCY|Logout is idempotent; refresh by jti single-consumption
  - CONSISTENCY|Token rotation in single transaction
  - ERRORS|AUTH_REFRESH_REPLAYED, AUTH_UNAUTHENTICATED, AUTH_TOKEN_EXPIRED
  - AUDIT|AUTH_TOKEN_REFRESHED, AUTH_TOKEN_REPLAY_REJECTED, AUTH_LOGOUT
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|Token TTL from system_config
  - OBSERVABILITY|Token metrics
  - SECRETS|JWT never in logs/audit; refresh in HttpOnly cookie only
allowedPaths:
  - backend/src/main/java/com/aihub/identity
  - backend/src/main/java/com/aihub/platform/security
  - backend/src/test/java/com/aihub/identity
  - backend/src/test/java/com/aihub/platform
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR012-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-012.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-012：刷新轮换、并发、重放、登出 E4

> 状态：`READY`

## 1. 目标

验证 refresh token 轮换、重放检测（Family 吊销）、并发刷新只有一个成功、登出幂等清除 Cookie 和 Token Family。覆盖 AC-P0B-IAM-006/007/009/011。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-004, REQ-IAM-005]
acceptanceScenarios: [AC-P0B-IAM-006, AC-P0B-IAM-007, AC-P0B-IAM-009, AC-P0B-IAM-011]
decisions: [DEC-001]
```

## 3. 范围

identity 模块 Token 管理、platform/security JWT 服务、对应测试和 Compose Verify。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-006` | 用户 | 有效 refresh Cookie | 刷新 | access/refresh 同时轮换，旧 jti ROTATED | `E4` |
| `AC-P0B-IAM-007` | 攻击者 | 已轮换的 refresh JWT | 再次使用 | AUTH_REFRESH_REPLAYED，Family REVOKED | `E4` |
| `AC-P0B-IAM-009` | 用户 | 已登录 | 登出后重用 refresh | Cookie 清除，Family 吊销，重用被拒 | `E4` |
| `AC-P0B-IAM-011` | 用户 | 两个并发请求用同一 refresh | 并发刷新 | 只允许一个成功，另一个触发并发语义 | `E4` |

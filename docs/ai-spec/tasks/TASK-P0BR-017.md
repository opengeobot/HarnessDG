---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-017
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
  - REQ-AUTH-003
acceptanceScenarios:
  - AC-P0B-AUTH-003
  - AC-P0B-AUTH-005
  - AC-P0B-AUTH-006
  - AC-P0B-AUTH-007
decisions:
  - DEC-004
  - DEC-011
scenarioEvidencePlan:
  - AC-P0B-AUTH-003|E4|Unauthorized user queries another org/private resource: SQL filtered, anti-enumeration
  - AC-P0B-AUTH-005|E4|Scope allows but RBAC/ACL denies, and reverse: both rejected
  - AC-P0B-AUTH-006|E4|Admin tries self-escalation: denied, built-in roles immutable
  - AC-P0B-AUTH-007|E4|Delete role with bindings / duplicate binding: stable conflict
crossCuttingPlan:
  - AUTHN|PrincipalContext required for all queries
  - AUTHZ|Scope+RBAC+ACL+resource policy combination; SQL pushdown
  - DB_FILTER|List queries merge principal scope in SQL; anti-enumeration
  - STATE|N/A
  - IDEMPOTENCY|Binding/ACL duplicate returns stable conflict
  - CONSISTENCY|N/A
  - ERRORS|AUTH_PERMISSION_DENIED, ROLE_IN_USE, anti-enumeration *_NOT_FOUND
  - AUDIT|All authorization management writes audited
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|N/A
allowedPaths:
  - backend/src/main/java/com/aihub/authorization
  - backend/src/main/java/com/aihub/asset
  - backend/src/test/java/com/aihub/authorization
  - backend/src/test/java/com/aihub/asset
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V4__authorization.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR017-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-017：Scope+RBAC+ACL+资源策略组合及防枚举 SQL 下推 E4

> 状态：`READY`

## 1. 目标

验证多条件组合授权（Scope 允许但 RBAC 拒绝、反向组合、自提权拒绝、防枚举 SQL 下推、角色引用删除冲突）的完整 E4 覆盖。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003]
acceptanceScenarios: [AC-P0B-AUTH-003, AC-P0B-AUTH-005, AC-P0B-AUTH-006, AC-P0B-AUTH-007]
decisions: [DEC-004, DEC-011]
```

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-003` | 无权用户 | — | 查询其他组织/私有资源 | SQL 不返回，详情防枚举 | `E4` |
| `AC-P0B-AUTH-005` | 用户 | Scope 允许但 RBAC 拒绝 | 执行操作 | 拒绝（两者均须满足） | `E4` |
| `AC-P0B-AUTH-006` | 管理员 | — | 自提权 | 拒绝，内置角色不可改 | `E4` |
| `AC-P0B-AUTH-007` | 管理员 | 角色有绑定 | 删除角色 | ROLE_IN_USE 稳定冲突 | `E4` |

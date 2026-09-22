---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-004
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AUTH-003
  - REQ-AUTH-001
  - REQ-UI-001
acceptanceScenarios: []
decisions:
  - DEC-018
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|N/A - role definition, not authentication
  - AUTHZ|Split READER into READER (7 perms) and OBSERVER (12 perms) per DEC-018
  - DB_FILTER|N/A - role seed change only
  - STATE|N/A - no state machine
  - IDEMPOTENCY|N/A - no write path
  - CONSISTENCY|N/A - no transaction
  - ERRORS|N/A - no error change
  - AUDIT|N/A - no audit event change
  - NOTIFICATION|N/A - no notification
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling
allowedPaths:
  - backend/src/main/resources/db/migration
  - backend/src/main/java/com/aihub/authorization
  - backend/src/test/java/com/aihub/authorization
  - frontend/src/features/admin
  - docs/ai-spec/01-requirements/personas-and-scope.md
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
  - backend/src/main/resources/db/migration/V4__authorization.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR004-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-004.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-004：内置角色 READER/OBSERVER 拆分（DEC-018）

> 状态：`READY`

## 1. 目标

按 DEC-018 将 READER 角色拆分为 READER（普通用户 7 项权限）和 OBSERVER（审计/运维 12 项权限），新增 Flyway Migration 并验证 ADMIN 角色不受影响。消除 AUD-017。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-003, REQ-AUTH-001, REQ-UI-001]
acceptanceScenarios: []
decisions: [DEC-018]
flywayBaseline: V23
```

## 3. 范围

### 允许修改

- Migration V23：创建 OBSERVER 角色，修改 READER 权限
- `authorization/`：角色常量（如有）
- 前端管理页面：OBSERVER 角色可见
- personas-and-scope.md：同步

### 明确不在范围

- V1-V22 Migration
- ADMIN 角色权限

### 禁止变化

- 不削弱 ADMIN 权限
- 不改变已有 RBAC 行为

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | 系统 | 空库执行 V23 | 查询 iam_role | READER(7 perms), OBSERVER(12 perms), ADMIN(全部) 均存在 | `E3` |

## 5. 横切要求

仅修改角色定义和 Seed 数据。

## 6. 契约与数据先行

新增 `V23__observer_role.sql`：
- INSERT OBSERVER role
- 从 READER 移除 user:read, authorization:read, audit:read, job:read, system:observe
- 将这 5 项权限授予 OBSERVER

## 7. 实施步骤

- [ ] 创建 V23 Migration
- [ ] 更新前端角色显示
- [ ] 测试 READER/OBSERVER/ADMIN 权限矩阵
- [ ] 全部测试通过

## 8. 验证命令

```text
cd backend && ./mvnw -o verify
# 预期：BUILD SUCCESS
```

## 9. 停止条件

无。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```

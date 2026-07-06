---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-010
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IAM-001
  - REQ-IAM-002
acceptanceScenarios:
  - AC-P0B-IAM-001
  - AC-P0B-IAM-002
  - AC-P0B-IAM-003
decisions:
  - DEC-001
  - DEC-010
scenarioEvidencePlan:
  - AC-P0B-IAM-001|E4|Compose: bootstrap admin created, password hash in DB, no plaintext in logs
  - AC-P0B-IAM-002|E4|Compose: login with temp password returns JWT, refresh cookie, force-change enforced
  - AC-P0B-IAM-003|E4|Compose: password change clears force-change, new password works for login
crossCuttingPlan:
  - AUTHN|Bootstrap creates first admin with temp password; login issues JWT
  - AUTHZ|Bootstrap admin gets ADMIN role binding; first login restricts to password-change scope
  - DB_FILTER|N/A - bootstrap is write-once, no list query
  - STATE|UserStatus: PENDING_ACTIVATION -> ACTIVE after password change
  - IDEMPOTENCY|Bootstrap is idempotent: no-op if admin already exists
  - CONSISTENCY|Bootstrap in single transaction: Principal + User + RoleBinding atomic
  - ERRORS|AUTH_INVALID_CREDENTIALS, PASSWORD_CHANGE_REQUIRED, PASSWORD_POLICY_VIOLATION
  - AUDIT|BOOTSTRAP_ADMIN_CREATED, AUTH_LOGIN_SUCCEEDED/FAILED, USER_PASSWORD_CHANGED
  - NOTIFICATION|N/A - no notification in bootstrap flow
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|Password policy from system_config
  - OBSERVABILITY|Trace spans for bootstrap and login
  - SECRETS|Password never in logs, audit, or responses; JWT signing key from Secret Store
allowedPaths:
  - backend/src/main/java/com/aihub/identity
  - backend/src/main/java/com/aihub/bootstrap
  - backend/src/main/java/com/aihub/platform/security
  - backend/src/test/java/com/aihub/identity
  - backend/src/test/java/com/aihub/bootstrap
  - deploy/compose
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
  - frontend
  - contracts
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR010-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-010.md
  - cd backend && ./mvnw -o verify
  - cd deploy/compose && docker compose config --quiet
approvedBy: null
approvedAt: null
---

# TASK-P0BR-010：Bootstrap -> 首登 -> 改密完整 E4

> 状态：`READY`

## 1. 目标

在干净 Compose 环境中验证：空库引导首个管理员、临时密码登录签发 JWT、强制改密完成后正常使用系统。覆盖 AC-P0B-IAM-001/002/003。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-001, REQ-IAM-002]
acceptanceScenarios: [AC-P0B-IAM-001, AC-P0B-IAM-002, AC-P0B-IAM-003]
decisions: [DEC-001, DEC-010]
```

## 3. 范围

### 允许修改

- `identity/`、`bootstrap/`：Bootstrap 和认证逻辑
- `platform/security/`：JWT 服务
- `deploy/compose/`：Fixture 和 Verify 脚本
- 对应测试类

### 明确不在范围

- V1-V3 Migration、前端、契约

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-001` | 部署管理员 | 空库 | 启动应用 | 首个管理员创建一次，密码仅存自适应哈希，日志无明文 | `E4` |
| `AC-P0B-IAM-002` | 管理员 | 临时密码 | 登录 | access JWT 返回，refresh 安全 Cookie，仅允许改密 | `E4` |
| `AC-P0B-IAM-003` | 管理员 | 已登录 | 完成改密 | 临时密码失效，新密码可登录，force-change 清除 | `E4` |

## 5. 横切要求

完整覆盖认证、授权、状态机、幂等、审计和脱敏。

## 6. 契约与数据先行

无新 Migration。使用已有 V3 表结构。

## 7. 实施步骤

- [ ] Compose 环境搭建并验证 Bootstrap
- [ ] 登录和改密 E2E 测试
- [ ] Verify 脚本覆盖 AC-P0B-IAM-001/002/003
- [ ] 单元和集成测试通过

## 8. 验证命令

```text
cd backend && ./mvnw -o verify
cd deploy/compose && docker compose up -d --build && bash scripts/verify.sh
# 预期：全部 PASS
```

## 9. 停止条件

无。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```

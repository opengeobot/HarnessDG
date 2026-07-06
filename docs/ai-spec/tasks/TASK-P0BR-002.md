---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-002
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0b152f3
stageGatePassed: true
stageGateEvidence: DEC-010 continuous implementation authorized
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-IAM-003
  - REQ-COM-001
acceptanceScenarios: []
decisions:
  - DEC-011
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|Ensure principalId=prn_ in JWT sub, PrincipalContext, and all token claims
  - AUTHZ|Ensure ACL/role binding use correct principalId type
  - DB_FILTER|Ensure query predicates use principalId consistently
  - STATE|N/A - no state machine change
  - IDEMPOTENCY|Ensure idempotency key scope uses correct principalId
  - CONSISTENCY|N/A - no transaction change
  - ERRORS|Ensure error responses use correct principalId in audit context
  - AUDIT|Ensure audit_log.principal_id stores prn_ prefix consistently
  - NOTIFICATION|N/A - no notification change
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|Ensure trace/span use correct principalId
  - SECRETS|N/A - no secret handling change
allowedPaths:
  - backend/src/main/java/com/aihub/identity
  - backend/src/main/java/com/aihub/platform/security
  - backend/src/main/java/com/aihub/bootstrap/security
  - backend/src/main/java/com/aihub/shared
  - backend/src/main/java/com/aihub/authorization
  - backend/src/test/java/com/aihub/identity
  - backend/src/test/java/com/aihub/platform
  - backend/src/test/java/com/aihub/bootstrap
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
  - backend/src/main/resources/db/migration/V4__authorization.sql
  - frontend
  - contracts
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR002-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-002.md
  - cd backend && ./mvnw -o verify -pl . -Dtest='*Test' -DfailIfNoTests=false
approvedBy: null
approvedAt: null
---

# TASK-P0BR-002：principalId/userId/agentId 语义统一

> 状态：`READY`

## 1. 目标

在 JWT sub、API 请求/响应、数据库列、审计记录和 PrincipalContext 中统一 `principalId=prn_`、`userId=usr_`、`agentId=agt_` 语义（TERM-001, INV-COM-001），消除 AUD-015 发现的语义不一致。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-003, REQ-COM-001]
acceptanceScenarios: []
decisions: [DEC-011]
```

## 3. 范围

### 允许修改

- `identity/`：PrincipalContext 注释、Token 签发逻辑
- `platform/security/`：JWT 解析和 PrincipalContext 构建
- `bootstrap/security/`：Filter 链中的 Principal 解析
- `shared/`：IdPrefix 注释
- `authorization/`：权限判定中的 principal 引用
- 对应测试类

### 明确不在范围

- Migration（不修改 V3/V4 已有的列数据）
- 前端

### 禁止变化

- 不改变数据库 Schema
- 不改变 API 签名

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | 开发者 | 代码审查 | 搜索 principalId 用法 | JWT sub=prn_、DB 列=prn_、审计=prn_ | `E2` |
| — | 系统 | 用户登录 | 检查 JWT claims | sub=prn_..., userId=usr_..., 两者不混用 | `E4` |

## 5. 横切要求

核心任务是统一 ID 语义，影响认证、授权、审计和可观测性全链路。

## 6. 契约与数据先行

无新 Migration。代码层面修复注释和逻辑中的语义不一致。

## 7. 实施步骤

- [ ] 审查并修复 PrincipalContext 注释中的 ID 语义
- [ ] 修复 JWT 签发逻辑确保 sub=prn_
- [ ] 修复审计记录中的 principalId 存储语义
- [ ] 添加单元测试验证 ID 一致性
- [ ] 全部现有测试通过

## 8. 验证命令

```text
cd backend && ./mvnw -o verify
# 预期：BUILD SUCCESS，所有测试通过
```

## 9. 停止条件

若发现数据库中存在混用 prn_/usr_ 的数据，需额外 Migration 清洗（不在本任务范围，需报告）。

## 10. 完成报告

```text
Completed:
Changed files:
Validation run and results:
```

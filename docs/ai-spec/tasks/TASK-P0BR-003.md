---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-003
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
acceptanceScenarios: []
decisions:
  - DEC-011
scenarioEvidencePlan: []
crossCuttingPlan:
  - AUTHN|N/A - permission catalog is authorization data, not authentication
  - AUTHZ|Permission Catalog, Java constants, V4+ Seed, OpenAPI security definitions must be in sync
  - DB_FILTER|N/A - catalog data change only
  - STATE|N/A - no state machine change
  - IDEMPOTENCY|N/A - no write path change
  - CONSISTENCY|N/A - no transaction change
  - ERRORS|Ensure error codes reference correct permission strings
  - AUDIT|Ensure audit events use correct permission names
  - NOTIFICATION|N/A - no notification
  - TAXONOMY_I18N|N/A - no governed value
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling
allowedPaths:
  - backend/src/main/java/com/aihub/authorization
  - backend/src/main/java/com/aihub/shared
  - backend/src/main/resources/db/migration
  - backend/src/test/java/com/aihub/authorization
  - contracts/openapi/aihub-v1.yaml
  - frontend/src/features/admin
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V3__identity.sql
  - backend/src/main/resources/db/migration/V4__authorization.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR003-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-003.md
  - cd backend && ./mvnw -o verify
approvedBy: null
approvedAt: null
---

# TASK-P0BR-003：Permission Catalog、Java 常量、Seed、OpenAPI 和 UI 无漂移

> 状态：`READY`

## 1. 目标

确保 `Permissions.java` 常量、数据库 `iam_permission` Seed（V4+V14+V20）、OpenAPI `securitySchemes` 和前端权限引用完全一致，消除 AUD-016 发现的漂移。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-003, REQ-AUTH-001]
acceptanceScenarios: []
decisions: [DEC-011]
```

## 3. 范围

### 允许修改

- `authorization/`：Permissions.java 常量
- `shared/`：错误码中的权限引用
- Migration V23+：如有缺失权限需补录
- 测试类
- 前端权限常量

### 明确不在范围

- V1-V22 Migration
- 业务逻辑

### 禁止变化

- 不改变已有权限的行为语义

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| — | 开发者 | 运行重复扫描 | Permissions.java vs iam_permission seed vs OpenAPI | 零差异 | `E2` |

## 5. 横切要求

核心是确保权限数据源一致性，影响授权、审计和前端。

## 6. 契约与数据先行

若发现缺失权限需新增 V23 Migration 补录。

## 7. 实施步骤

- [ ] 审计 Permissions.java 所有常量 vs V4+V14+V20 seed
- [ ] 修复不一致项
- [ ] 添加 ArchUnit 或集成测试断言无漂移
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

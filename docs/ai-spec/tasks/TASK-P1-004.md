---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-004
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-003
acceptanceScenarios:
  - AC-P1-AST-006
  - AC-P1-AST-007
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-006|E4|Two principals same query see disjoint authorized subsets with stable cursor
  - AC-P1-AST-007|E4|Unauthorized facet/count/detail use anti-enumeration ASSET_NOT_FOUND
crossCuttingPlan:
  - AUTHN|Authenticated Principal required for search
  - AUTHZ|Role/Scope/Membership/ACL/Visibility predicate in SQL
  - DB_FILTER|All search and facet queries apply authorization predicate
  - STATE|Provision incomplete and ARCHIVED excluded by default
  - IDEMPOTENCY|N/A - read-only search
  - CONSISTENCY|N/A - read-only query
  - ERRORS|COMMON_INVALID_ARGUMENT for bad cursor/sort; AUTH_PERMISSION_DENIED fail closed
  - AUDIT|Optional deny audit per platform policy
  - NOTIFICATION|N/A - read-only search
  - TAXONOMY_I18N|tagId-only tag filter; itemCode filters validated
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Search latency metrics for NFR-PERF-001
  - SECRETS|N/A - no secrets in search response
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/infrastructure
  - backend/src/test/java/com/aihub/asset/application
  - backend/src/test/java/com/aihub/asset/api
  - docs/ai-spec/tasks/evidence/EVD-P1004-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1004-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-004.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-004.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-004：权限过滤搜索、Cursor 与防枚举

> 状态：`READY`

## 1. 目标

实现权限过滤的资产搜索、Cursor 分页和 Facet 防枚举。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-003']
acceptanceScenarios: ['AC-P1-AST-006', 'AC-P1-AST-007']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围

### 允许修改

- AssetSearchDao、AssetApplicationService search、Controller 集成测试。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-006` | Maintainer | Two principals same query see disjoint authorized subsets with stable cursor | Execute | PASS | `E4` |
| `AC-P1-AST-007` | Maintainer | Unauthorized facet/count/detail use anti-enumeration ASSET_NOT_FOUND | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] SQL 授权谓词；Cursor 编码；Facet 同谓词；集成测试两主体。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-004.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-004.md -CheckChangedPaths
cd backend && ./mvnw -o verify
```

## 9. 停止条件

- validate-task-card 或 task-specific 命令失败
- AC 证据未达到 requiredEvidenceLevel

## 10. 完成报告

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```

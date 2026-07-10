---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-007
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-007
acceptanceScenarios:
  - AC-P1-AST-011
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-011|E4|Deprecate/archive/restore guard active published versions and search visibility
crossCuttingPlan:
  - AUTHN|JWT required
  - AUTHZ|asset:deprecate and asset:delete permissions
  - DB_FILTER|ARCHIVED hidden from default search for non-admin
  - STATE|AssetStatus ACTIVE->DEPRECATED->ARCHIVED transitions
  - IDEMPOTENCY|Idempotency-Key on lifecycle mutations
  - CONSISTENCY|Restore changes catalog state only not published versions
  - ERRORS|Reference check blocks illegal archive/delete
  - AUDIT|ASSET_DEPRECATED, ASSET_ARCHIVED, ASSET_RESTORED events
  - NOTIFICATION|Notify subscribers on deprecate/archive per policy
  - TAXONOMY_I18N|deprecation_reason itemCode required
  - CONFIG|Retention policy from system_config
  - OBSERVABILITY|Lifecycle transition metrics
  - SECRETS|N/A - no secrets in lifecycle API
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/domain
  - backend/src/test/java/com/aihub/asset/application
  - docs/ai-spec/tasks/evidence/EVD-P1007-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1007-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-007.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-007.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-007：弃用、归档、恢复与版本保护

> 状态：`READY`

## 1. 目标

实现弃用、归档、恢复与 Published Version 引用检查。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-007']
acceptanceScenarios: ['AC-P1-AST-011']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围

### 允许修改

- AssetStatus 转换、引用检查、搜索可见性规则。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-011` | Maintainer | Deprecate/archive/restore guard active published versions and search visibility | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] deprecate/archive/restore 用例；引用检查；搜索降权/隐藏。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-007.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-007.md -CheckChangedPaths
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

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-005
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-004
acceptanceScenarios:
  - AC-P1-AST-008
decisions:
  - DEC-010
  - DEC-014
scenarioEvidencePlan:
  - AC-P1-AST-008|E4|Detail returns Card projection, disabled governance markers and DEC-014 quick-use snippets without secrets
crossCuttingPlan:
  - AUTHN|JWT required for detail
  - AUTHZ|Same predicate as search; private asset ASSET_NOT_FOUND for unauthorized
  - DB_FILTER|Detail query applies authorization predicate
  - STATE|N/A - read path respects AssetStatus visibility rules
  - IDEMPOTENCY|N/A - read-only detail
  - CONSISTENCY|Card projection from locked sourceCommit with stale fallback policy
  - ERRORS|ASSET_NOT_FOUND anti-enumeration; GITEA_DEPENDENCY_UNAVAILABLE stable error
  - AUDIT|asset:read success audit per policy
  - NOTIFICATION|N/A - read-only detail
  - TAXONOMY_I18N|Disabled item/tag shown with code and locale label
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Card projection latency traced
  - SECRETS|Quick-use snippets exclude tokens and permanent credentials per DEC-014
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/domain
  - backend/src/test/java/com/aihub/asset/application
  - docs/ai-spec/tasks/evidence/EVD-P1005-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1005-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-005.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-005.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-005：资产详情、Card 投影与快速使用面板

> 状态：`READY`

## 1. 目标

实现资产详情、Card 投影和 DEC-014 快速使用代码片段面板。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-004']
acceptanceScenarios: ['AC-P1-AST-008']
decisions: ['DEC-010', 'DEC-014']
```

## 3. 范围

### 允许修改

- AssetApplicationService getDetail、CardProjectionPort、quick-use DTO。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-008` | Maintainer | Detail returns Card projection, disabled governance markers and DEC-014 quick-use snippets without secrets | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] 授权详情查询；Card 投影；快速使用片段生成；安全渲染标记。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-005.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-005.md -CheckChangedPaths
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

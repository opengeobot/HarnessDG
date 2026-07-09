---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-010
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-DST-TAX-001
acceptanceScenarios:
  - AC-DST-TAX-001
  - AC-DST-TAX-002
  - AC-DST-TAX-003
  - AC-DST-TAX-004
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-TAX-001|E4|DATASET created with governed fields discoverable by combined search with matchedFields
  - AC-DST-TAX-002|E4|Two principals see disjoint facet counts and items for same filter
  - AC-DST-TAX-003|E4|Disabled dict/tag historical display with new reference rejected
  - AC-DST-TAX-004|E4|Invalid display name, free tag, unknown code and bad cursor fail closed
crossCuttingPlan:
  - AUTHN|Authenticated principals for dataset search
  - AUTHZ|Facet and items share same authorization predicate
  - DB_FILTER|DATASET multi-value classification filters in authorized SQL
  - STATE|N/A - read/search path
  - IDEMPOTENCY|N/A - read-only facet
  - CONSISTENCY|N/A - read-only search
  - ERRORS|TAG_VALUE_INVALID, DICTIONARY_VALUE_INVALID, COMMON_INVALID_ARGUMENT
  - AUDIT|Deny audit for unauthorized facet access per policy
  - NOTIFICATION|N/A - search only
  - TAXONOMY_I18N|task/modality/format/language/license/sensitivity itemCodes and tagId only
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Facet query latency metrics
  - SECRETS|N/A - no secrets in search
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/infrastructure
  - backend/src/test/java/com/aihub/asset
  - frontend/src/features/assets
  - docs/ai-spec/tasks/evidence/EVD-P1010-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1010-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-010.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-010.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-010：DATASET 分类、标签与权限过滤 Facet

> 状态：`READY`

## 1. 目标

实现 DATASET 多值分类、受控标签、权限过滤 Facet 和 matchedFields。

## 2. 关联规格

```yaml
requirements: ['REQ-DST-TAX-001']
acceptanceScenarios: ['AC-DST-TAX-001', 'AC-DST-TAX-002', 'AC-DST-TAX-003', 'AC-DST-TAX-004']
decisions: ['DEC-008', 'DEC-010']
```

## 3. 范围

### 允许修改

- DatasetProfile 搜索、Facet SQL、前端筛选 UI。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-TAX-001` | Maintainer | DATASET created with governed fields discoverable by combined search with matchedFields | Execute | PASS | `E4` |
| `AC-DST-TAX-002` | Maintainer | Two principals see disjoint facet counts and items for same filter | Execute | PASS | `E4` |
| `AC-DST-TAX-003` | Maintainer | Disabled dict/tag historical display with new reference rejected | Execute | PASS | `E4` |
| `AC-DST-TAX-004` | Maintainer | Invalid display name, free tag, unknown code and bad cursor fail closed | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 多值分类索引；Facet 授权谓词；matchedFields 返回；测试 AC-DST-TAX-*。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-010.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-010.md -CheckChangedPaths
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

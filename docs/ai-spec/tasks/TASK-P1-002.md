---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-002
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-001
  - REQ-AST-006
acceptanceScenarios:
  - AC-P1-AST-001
  - AC-P1-AST-010
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-001|E3|Flyway V29+ backfill maps legacy coordinates and governed fields to Team Owner model
  - AC-P1-AST-010|E3|Backfill string Owner to Team ID and seed ACL rows with UNMAPPED remediation markers
crossCuttingPlan:
  - AUTHN|N/A - migration and backfill only
  - AUTHZ|Backfill preserves ACL semantics; admin remediation page requires asset:manage
  - DB_FILTER|Backfill SQL scoped by organization; no cross-tenant leakage
  - STATE|UNMAPPED assets flagged for admin review without breaking ACTIVE assets
  - IDEMPOTENCY|V29 migration rerunnable on empty DB and upgrade path
  - CONSISTENCY|Backfill in single Flyway transaction per batch with audit trail
  - ERRORS|DICTIONARY_VALUE_INVALID and TAG_VALUE_INVALID for unmapped legacy values
  - AUDIT|ASSET_BACKFILL_COMPLETED events for each remediated row batch
  - NOTIFICATION|N/A - backfill is batch operation
  - TAXONOMY_I18N|Free-form tags mapped to governed tagId or UNMAPPED flag
  - CONFIG|N/A - no runtime config change
  - OBSERVABILITY|Migration metrics logged for backfill row counts
  - SECRETS|N/A - no secrets in migration scripts
allowedPaths:
  - backend/src/main/resources/db/migration/V29__asset_backfill.sql
  - backend/src/main/java/com/aihub/asset/infrastructure
  - backend/src/test/java/com/aihub/asset
  - frontend/src/features/admin
  - docs/ai-spec/tasks/evidence/EVD-P1002-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1002-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-002.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-002.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-002：V29+ 资产数据回填与 UNMAPPED 整改

> 状态：`READY`

## 1. 目标

通过 V29+ Flyway 将自由标签和字符串 Owner 回填为受控 tagId/Team ID，并提供 UNMAPPED 管理端整改页。

> 编号说明：V27 已被 P3 `V27__publish_request_frozen_commit.sql` 占用；本任务回填下限为 **V29**。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-001', 'REQ-AST-006']
acceptanceScenarios: ['AC-P1-AST-001', 'AC-P1-AST-010']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围

### 允许修改

- V29__asset_backfill.sql、asset infrastructure 查询、admin 整改 UI；禁止修改 V1/V2。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-001` | Maintainer | Flyway V29+ backfill maps legacy coordinates and governed fields to Team Owner model | Execute | PASS | `E3` |
| `AC-P1-AST-010` | Maintainer | Backfill string Owner to Team ID and seed ACL rows with UNMAPPED remediation markers | Execute | PASS | `E3` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] 编写 V29 回填脚本；实现 UNMAPPED 标记查询；admin 页展示并引导整改。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-002.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-002.md -CheckChangedPaths
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

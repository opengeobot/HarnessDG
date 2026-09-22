---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-006
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-005
acceptanceScenarios:
  - AC-P1-AST-009
decisions:
  - DEC-010
  - DEC-011
scenarioEvidencePlan:
  - AC-P1-AST-009|E4|Concurrent update with rowVersion conflict and rename preserves alias atomically
crossCuttingPlan:
  - AUTHN|JWT required for update
  - AUTHZ|asset:update permission for Owner/Maintainer
  - DB_FILTER|N/A - update by assetId with scope check
  - STATE|Rename atomically reserves coordinate and adds alias
  - IDEMPOTENCY|Idempotency-Key on update mutations
  - CONSISTENCY|PostgreSQL projection and Gitea update via Saga/Job not in transaction
  - ERRORS|CONCURRENT_MODIFICATION, DICTIONARY_VALUE_INVALID, GITEA_DEPENDENCY_UNAVAILABLE
  - AUDIT|ASSET_UPDATED with before/after controlled summary
  - NOTIFICATION|Notify affected maintainers on Owner change per policy
  - TAXONOMY_I18N|Revalidate itemCode and tagId on update
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Update saga traced
  - SECRETS|Audit excludes full sensitive Card content
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/asset/domain
  - backend/src/test/java/com/aihub/asset/application
  - docs/ai-spec/tasks/evidence/EVD-P1006-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1006-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-006.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-006.md -CheckChangedPaths
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-006：乐观锁更新、重命名与 Alias

> 状态：`READY`

## 1. 目标

实现乐观锁更新、重命名/Alias 和 Gitea/投影一致 Saga。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-005']
acceptanceScenarios: ['AC-P1-AST-009']
decisions: ['DEC-010', 'DEC-011']
```

## 3. 范围

### 允许修改

- UpdateAssetCommand、rowVersion 冲突、rename alias 逻辑。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-009` | Maintainer | Concurrent update with rowVersion conflict and rename preserves alias atomically | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] expected rowVersion 校验；rename 原子预留；Gitea Job 更新。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-006.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-006.md -CheckChangedPaths
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

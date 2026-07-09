---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-001
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
acceptanceScenarios:
  - AC-P1-AST-001
decisions:
  - DEC-010
  - DEC-011
  - DEC-014
scenarioEvidencePlan:
  - AC-P1-AST-001|E3|OpenAPI asset schemas and entity invariants define coordinate, Owner, alias and visibility model
crossCuttingPlan:
  - AUTHN|N/A - contract and domain invariant documentation only
  - AUTHZ|OpenAPI permission annotations for asset endpoints align with REQ-AST-001
  - DB_FILTER|N/A - no database change in contract task
  - STATE|Document AssetStatus separation from VersionStatus in invariants
  - IDEMPOTENCY|N/A - no write path in contract task
  - CONSISTENCY|N/A - no saga in contract-only task
  - ERRORS|Add x-error-codes for asset coordinate validation failures per error-catalog
  - AUDIT|N/A - contract metadata only
  - NOTIFICATION|N/A - no notification in coordinate model task
  - TAXONOMY_I18N|MODEL/DATASET fields reference dictionary itemCode and tagId only
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling in contract task
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - contracts/events/events-v1.yaml
  - docs/ai-spec/02-domain/entity-invariants.md
  - docs/ai-spec/tasks/evidence/EVD-P1001-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1001-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-001.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-001.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-001：资产坐标、Owner、别名与可见性契约

> 状态：`READY`

## 1. 目标

将 REQ-AST-001 的坐标、Owner、别名、可见性和 MODEL/DATASET 字段固化为 OpenAPI 契约与 Domain Invariant，满足 DEC-011/014。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-001']
acceptanceScenarios: ['AC-P1-AST-001']
decisions: ['DEC-010', 'DEC-011', 'DEC-014']
```

## 3. 范围

### 允许修改

- OpenAPI asset schemas、事件补充、entity-invariants 同步；不修改后端产品代码。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-001` | Maintainer | OpenAPI asset schemas and entity invariants define coordinate, Owner, alias and visibility model | Execute | PASS | `E3` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 更新 aihub-v1.yaml asset 操作与 schema；补充 ASSET_* 事件；同步 INV-AST-* invariants。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-001.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-001.md -CheckChangedPaths
npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
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

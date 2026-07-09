---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-001
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-VER-001
  - REQ-MNF-001
acceptanceScenarios:
  - AC-P2-MNF-001
decisions:
  - DEC-010
scenarioEvidencePlan:
  - AC-P2-MNF-001|E3|Golden manifest fixtures and cross-implementation digest vectors in contracts and domain specs
crossCuttingPlan:
  - AUTHN|N/A - contract and golden fixture documentation only
  - AUTHZ|OpenAPI permission annotations for version/upload endpoints align with REQ-VER-001 and REQ-MNF-001
  - DB_FILTER|N/A - no database change in contract task
  - STATE|Document VersionStatus, UploadSessionStatus and Artifact invariants in state-machines and entity-invariants
  - IDEMPOTENCY|Document version create and upload session idempotency keys in OpenAPI
  - CONSISTENCY|N/A - no saga in contract-only task
  - ERRORS|Add x-error-codes for manifest path validation and digest mismatch per error-catalog
  - AUDIT|N/A - contract metadata only
  - NOTIFICATION|N/A - no notification in contract task
  - TAXONOMY_I18N|N/A - manifest schema uses stable codes only
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - no runtime service
  - SECRETS|N/A - no secret handling in contract task
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - contracts/events/events-v1.yaml
  - contracts/fixtures/manifest
  - docs/ai-spec/02-domain/entity-invariants.md
  - docs/ai-spec/02-domain/state-machines.md
  - docs/ai-spec/tasks/evidence/EVD-P2001-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2001-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-001.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-001.md -CheckChangedPaths
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-001：Version/Artifact/Manifest/Upload 契约与摘要算法

> 状态：`READY`

## 1. 目标

将 REQ-VER-001 与 REQ-MNF-001 固化为 OpenAPI、事件、Domain Invariant 和 Manifest Golden fixtures。

## 2. 关联规格

```yaml
requirements: ['REQ-VER-001', 'REQ-MNF-001']
acceptanceScenarios: ['AC-P2-MNF-001']
decisions: ['DEC-010']
```

## 3. 范围

### 允许修改

- OpenAPI version/upload/manifest schemas、事件补充、golden fixtures、invariants 与状态机文档。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-MNF-001` | Maintainer | Golden manifest fixtures and cross-implementation digest vectors in contracts and domain specs | Execute | PASS | `E3` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V16+ 为既有 version/transfer 下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 更新 aihub-v1.yaml version/upload/manifest 操作与 schema；补充 MANIFEST_* 事件；同步 INV-VER-*、INV-ART-*、INV-UPL-* invariants。
- [ ] 编写 Golden fixtures 与交叉 digest 向量
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-001.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-001.md -CheckChangedPaths
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

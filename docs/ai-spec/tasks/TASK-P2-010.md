---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-010
status: READY
implementationAuthorized: true
phase: P2
baseCommit: 26c3985c5092d1c64def2c33e8db1e5d6ac4a256
stageGatePassed: true
stageGateEvidence: P1 asset catalog gap closure committed; DEC-010 continuous implementation
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-UPL-004
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-P2-JRN-001
decisions:
  - DEC-008
  - DEC-010
  - DEC-015
scenarioEvidencePlan:
  - AC-P2-JRN-001|E4|JRN-P2-004 dataset detail Version/Files/Preview/CLI journey with two principals
crossCuttingPlan:
  - AUTHN|Two real principals with distinct JWT sessions in journey
  - AUTHZ|Detail, files, preview and CLI actions gated consistently with search predicate
  - DB_FILTER|Dataset detail never leaks cross-org assets in journey assertions
  - STATE|Exact version selection binds Card, Files, Preview and download actions
  - IDEMPOTENCY|N/A - read-heavy journey with CLI resume covered in P2-009
  - CONSISTENCY|UI version selector matches backend version projection and manifest digest
  - ERRORS|Journey captures deny paths for unauthorized preview/files/download
  - AUDIT|Journey verifies read and download audit for authorized principal only
  - NOTIFICATION|N/A - journey read path
  - TAXONOMY_I18N|zh/en detail shell with governed classification display
  - CONFIG|Compose fixtures for dataset detail journey
  - OBSERVABILITY|Journey trace correlation across UI and CLI steps
  - SECRETS|Journey evidence redacts tokens and download URLs
allowedPaths:
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/assets/PreviewPanel.tsx
  - frontend/src/features/version
  - deploy/compose/scripts/p2-journey
  - deploy/compose/fixtures/p2
  - docs/ai-spec/tasks/evidence/EVD-P2010-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2010-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-010.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-010.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - docker compose -f deploy/compose/docker-compose.yml config --quiet
  - ./deploy/compose/scripts/verify.sh
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P2-010：Dataset 详情 Version/Files/Preview/CLI 纵向旅程

> 状态：`READY`

## 1. 目标

完成 JRN-P2-004：Dataset 详情 Version/Files/Preview(Subset/Split/Stats)/CLI 纵向旅程 E4。

## 2. 关联规格

```yaml
requirements: ['REQ-UPL-004', 'REQ-DST-DETAIL-001']
acceptanceScenarios: ['AC-P2-JRN-001']
decisions: ['DEC-008', 'DEC-010', 'DEC-015']
```

## 3. 范围

### 允许修改

- AssetDetailPage、Version 面板、Preview 集成、p2-journey 脚本与 fixtures。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P2-JRN-001` | Data consumer | JRN-P2-004 dataset detail Version/Files/Preview/CLI journey with two principals | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 依赖 TASK-P2-008 Preview 与 TASK-P2-009 CLI 能力

## 7. 实施步骤

- [ ] 完善 Dataset 详情 Version/Files/Preview/CLI 聚合；p2-journey JRN-P2-004 脚本。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-010.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-010.md -CheckChangedPaths
cd frontend && pnpm lint
cd frontend && pnpm typecheck
cd frontend && pnpm test
cd frontend && pnpm build
docker compose -f deploy/compose/docker-compose.yml config --quiet
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

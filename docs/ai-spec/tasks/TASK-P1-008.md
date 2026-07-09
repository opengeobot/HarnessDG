---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-008
status: READY
implementationAuthorized: true
phase: P1
baseCommit: 38fd44d2dcbc1a611cfdfd923337e5599c3b3feb
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; DEC-010 continuous implementation; P0BR-046 gate lifted
stageGateEvidenceRefs:
  - DEC-010
requirements:
  - REQ-AST-009
acceptanceScenarios:
  - AC-P1-AST-013
decisions:
  - DEC-010
  - DEC-014
scenarioEvidencePlan:
  - AC-P1-AST-013|E4|PAGE-AST-001..006 full async states, permission gates and zh/en i18n with component and E2E tests
crossCuttingPlan:
  - AUTHN|Access token in memory only; route guard on protected pages
  - AUTHZ|UI buttons reflect backend permission and resource capability
  - DB_FILTER|N/A - frontend consumes filtered API
  - STATE|Loading/Empty/Error/Retry/Success on all asset pages
  - IDEMPOTENCY|Create form respects Idempotency-Key header
  - CONSISTENCY|N/A - frontend adapter only
  - ERRORS|Stable i18nKey for backend error codes
  - AUDIT|N/A - frontend does not write audit
  - NOTIFICATION|N/A - unless in-page notification display
  - TAXONOMY_I18N|Dictionary/tag controlled selectors; zh/en locale files
  - CONFIG|N/A - no deployment config
  - OBSERVABILITY|N/A - client-side only
  - SECRETS|No JWT or internal Git URL in localStorage or analytics
allowedPaths:
  - frontend/src/features/assets
  - frontend/src/locales/zh.json
  - frontend/src/locales/en.json
  - docs/ai-spec/tasks/evidence/EVD-P1008-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1008-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-008.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-008.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-008：资产目录前端 PAGE-AST-001..006

> 状态：`READY`

## 1. 目标

实现 PAGE-AST-001..006 完整页面状态、权限门控和 zh/en 国际化。

## 2. 关联规格

```yaml
requirements: ['REQ-AST-009']
acceptanceScenarios: ['AC-P1-AST-013']
decisions: ['DEC-010', 'DEC-014']
```

## 3. 范围

### 允许修改

- frontend/src/features/assets 及 locale 文件。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P1-AST-013` | Maintainer | PAGE-AST-001..006 full async states, permission gates and zh/en i18n with component and E2E tests | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V27+ 为 P1-002 回填下限；其他任务按需使用 V27+ 新 Migration

## 7. 实施步骤

- [ ] 列表/创建/详情/设置/访问/血缘页；异步状态；组件与 E2E 测试。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-008.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-008.md -CheckChangedPaths
cd frontend && pnpm lint
cd frontend && pnpm typecheck
cd frontend && pnpm test
cd frontend && pnpm build
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

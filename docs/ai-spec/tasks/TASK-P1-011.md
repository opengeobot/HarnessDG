---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-011
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
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-DST-DETAIL-001
  - AC-DST-DETAIL-002
  - AC-DST-DETAIL-003
  - AC-DST-DETAIL-004
decisions:
  - DEC-008
  - DEC-010
scenarioEvidencePlan:
  - AC-DST-DETAIL-001|E4|Dataset detail binds Card, tags and files to selected published version
  - AC-DST-DETAIL-002|E4|Detail URL and cache stay on selected version when latestPublished changes
  - AC-DST-DETAIL-003|E4|Unauthorized list/detail/files use anti-enumeration semantics
  - AC-DST-DETAIL-004|E4|Gitea unavailable returns stale projection with timestamp not forged content
crossCuttingPlan:
  - AUTHN|JWT required for dataset detail
  - AUTHZ|Same predicate as asset search for detail/files
  - DB_FILTER|Detail queries scoped by authorization
  - STATE|Version selection stable in URL and cache
  - IDEMPOTENCY|N/A - read-only detail
  - CONSISTENCY|Card projection from exact version sourceCommit
  - ERRORS|ASSET_NOT_FOUND anti-enumeration; GITEA_DEPENDENCY_UNAVAILABLE
  - AUDIT|asset:read audit for detail access
  - NOTIFICATION|N/A - read-only detail
  - TAXONOMY_I18N|Classification and tags locale display on detail
  - CONFIG|N/A - no config change
  - OBSERVABILITY|Detail projection latency traced
  - SECRETS|No clone credentials in detail response
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/test/java/com/aihub/asset/application
  - frontend/src/features/assets
  - docs/ai-spec/tasks/evidence/EVD-P1011-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1011-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-011.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-011.md -CheckChangedPaths
  - cd frontend && pnpm lint
  - cd frontend && pnpm typecheck
  - cd frontend && pnpm test
  - cd frontend && pnpm build
  - cd backend && ./mvnw -o verify
approvedBy: User (plan execution authorization 2026-07-10)
approvedAt: 2026-07-10T00:00:00Z
---
# TASK-P1-011：Dataset Card 详情外壳与精确版本导航

> 状态：`READY`

## 1. 目标

实现 Dataset Card 详情外壳和精确版本导航，不展示未交付 Placeholder。

## 2. 关联规格

```yaml
requirements: ['REQ-DST-DETAIL-001']
acceptanceScenarios: ['AC-DST-DETAIL-001', 'AC-DST-DETAIL-002', 'AC-DST-DETAIL-003', 'AC-DST-DETAIL-004']
decisions: ['DEC-008', 'DEC-010']
```

## 3. 范围

### 允许修改

- Dataset 详情 API、版本选择、前端 AssetDetailPage。

### 明确不在范围

- 未列于 allowedPaths 的模块与契约

### 禁止变化

- 不修改 V1/V2 Migration；不削弱授权或验证

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | Maintainer | Dataset detail binds Card, tags and files to selected published version | Execute | PASS | `E4` |
| `AC-DST-DETAIL-002` | Maintainer | Detail URL and cache stay on selected version when latestPublished changes | Execute | PASS | `E4` |
| `AC-DST-DETAIL-003` | Maintainer | Unauthorized list/detail/files use anti-enumeration semantics | Execute | PASS | `E4` |
| `AC-DST-DETAIL-004` | Maintainer | Gitea unavailable returns stale projection with timestamp not forged content | Execute | PASS | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 行为变更先更新 OpenAPI/事件/Migration（如适用）
- V29+ 为 P1-002 回填下限（V27 已用于 P3 frozen commit）；其他任务按需使用 V29+ 新 Migration

## 7. 实施步骤

- [ ] 版本绑定详情；防 Placeholder；不可用降级策略。
- [ ] 编写/更新测试覆盖全部 AC
- [ ] 更新 Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-011.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-011.md -CheckChangedPaths
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

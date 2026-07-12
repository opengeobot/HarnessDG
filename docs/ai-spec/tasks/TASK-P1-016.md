---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-016
status: READY
implementationAuthorized: false
phase: P1
baseCommit: b15a30918f53172087b80306d91cf9181494fac0
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P1 详情外壳已落地（Wave S）；本任务补 F7 接线 + F8 三页迁移
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-DST-DETAIL-001
  - AC-DST-DETAIL-003
  - AC-DST-DETAIL-004
decisions:
  - DEC-008
  - DEC-010
  - DEC-014
scenarioEvidencePlan:
  - AC-DST-DETAIL-001|E4|frontend e2e + vitest: 详情页内嵌「新建草稿版本」（createDraftVersion）+「DVC 配置」（getDvcConfig）入口
  - AC-DST-DETAIL-003|E4|e2e: VersionPage/ReviewPage/UploadPage 从详情页深链带 assetId/versionId 跳入，无手填
  - AC-DST-DETAIL-004|E4|vitest+e2e: 三页迁移 Ant Design，UI 栈一致
crossCuttingPlan:
  - AUTHN|复用 JWT AuthProvider + RouteGuard；三页路由维持现有权限点
  - AUTHZ|按钮按 asset:write/asset:submit/asset:manage 显示；后端权限为准
  - DB_FILTER|N/A - 前端
  - STATE|createDraftVersion 创建 DRAFT 版本；版本切换器刷新
  - IDEMPOTENCY|N/A - 前端读+草稿创建（后端幂等已有）
  - CONSISTENCY|N/A
  - ERRORS|Loading/Empty/Error/Success 五态；403 显示缺少 Scope
  - AUDIT|N/A - 前端
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|按钮/Modal 文案 i18n（zh/en）
  - CONFIG|N/A
  - OBSERVABILITY|N/A
  - SECRETS|getDvcConfig 展示 bucket/endpoint/prefix，不展示 secretKey；getDvcCredentials 已脱敏（Wave V）
allowedPaths:
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/version/VersionPage.tsx
  - frontend/src/features/version/ReviewPage.tsx
  - frontend/src/features/version/api.ts
  - frontend/src/features/upload/UploadPage.tsx
  - frontend/src/shared/i18n/en.json
  - frontend/src/shared/i18n/zh.json
  - frontend/e2e/version-management.spec.ts
  - frontend/e2e/publish-flow.spec.ts
  - docs/ai-spec/tasks/TASK-P1-016.md
  - docs/ai-spec/tasks/evidence/EVD-P1016-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1016-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-016.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-016.md -CheckChangedPaths
  - pnpm lint
  - pnpm typecheck
  - pnpm test
  - pnpm build
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P1-016：前端 F7 createDraftVersion/getDvcConfig 接线 + F8 三页迁移（Wave Z）

> 状态：`READY`（待用户授权）

## 1. 目标
AssetDetailPage 接线 `createDraftVersion`（新建草稿版本 Modal）+ `getDvcConfig`（DVC 配置展示）；VersionPage/ReviewPage/UploadPage 从 Tailwind 迁移 Ant Design，接受路由 query 带入 assetId/versionId，去手填 input。

## 2. 关联规格
```yaml
requirements: ['REQ-DST-DETAIL-001']
acceptanceScenarios: ['AC-DST-DETAIL-001', 'AC-DST-DETAIL-003', 'AC-DST-DETAIL-004']
decisions: ['DEC-008', 'DEC-010', 'DEC-014']
```

## 3. 范围
- AssetDetailPage 加新建草稿+DVC 配置入口；三页迁移 Ant Design + useSearchParams 带参。
- 不改后端。

## 4. 行为切片
| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | Maintainer | 详情页 | 新建草稿版本 | createDraftVersion 入口内嵌，Modal 输入 version/notes | `E4` |
| `AC-DST-DETAIL-003` | Maintainer | 详情页深链 /version?assetId= | 打开 VersionPage | 自动带入 assetId，无手填 | `E4` |
| `AC-DST-DETAIL-004` | Maintainer | 三页 | 渲染 | Ant Design 一致 UI | `E4` |

## 8. 验证命令
```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-016.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-016.md -CheckChangedPaths
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P1-013
status: READY
implementationAuthorized: false
phase: P1
baseCommit: 71a03efcbaf9408b7ed07df5a7015c4ee110f8c3
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED; P1 catalog/讨论/血缘已部分落地；本任务为 P1 详情页统一外壳差距闭合，依赖 DEC-008 端态 REQ-DST-DETAIL-001
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
  - DEC-014
scenarioEvidencePlan:
  - AC-DST-DETAIL-001|E4|frontend vitest + Playwright e2e: 详情页 Tab 顺序 Overview|Versions|Files|Preview|Discussions|Lineage|Access|Settings，未实现 Tab 不展示，合法 Empty 状态
  - AC-DST-DETAIL-002|E4|Playwright e2e + 组件测试: 版本切换器切换 Version 同步更新 URL 与所有版本化 Query Key，无跨版本缓存混用
  - AC-DST-DETAIL-003|E4|vitest + e2e: Files Tab 渲染选定 Version 的 Artifact 树（路径/大小/媒体类型/SHA-256），Preview Tab 渲染 schema/列类型/分页
  - AC-DST-DETAIL-004|E4|e2e: `/assets/:id/versions` 路由可达；Card/Markdown 安全渲染；私有资产防枚举语义；不泄露 Gitea/MinIO/DVC 内部 endpoint
crossCuttingPlan:
  - AUTHN|复用现有 JWT AuthProvider 与 RouteGuard，详情页路由维持 asset:read 门禁
  - AUTHZ|Tab 可见性按 asset:read/discuss/manage/write 与资产实时授权渲染；后端权限下推已有，前端仅做提示
  - DB_FILTER|N/A - 前端任务，数据库权限过滤由后端 search/detail API 已实现
  - STATE|版本切换器绑定精确 versionId，不绑定 latest；切换 Version 触发版本化 Query Key 失效与重取
  - IDEMPOTENCY|N/A - 前端读路径，无写幂等
  - CONSISTENCY|N/A - 无 saga；版本化数据一致性由后端 API 保证
  - ERRORS|Loading/Empty/Error/Retry/Success 五态；403 显示缺少 Scope 与申请入口，不泄露私有资产是否存在
  - AUDIT|N/A - 前端读路径不产生审计；用户切换 Version 不记录敏感查询
  - NOTIFICATION|N/A - 详情页外壳无通知动作
  - TAXONOMY_I18N|Tab 标题与 Empty 状态走 i18n（zh/en）；分类字段展示 itemCode 对应字典回显
  - CONFIG|N/A - 无新配置
  - OBSERVABILITY|N/A - 前端无新增指标；可选前端性能埋点不在本任务 MUST
  - SECRETS|Card/README/外链/图片/讨论为不可信数据，SafeMarkdown 渲染；不展示预签名 URL 查询串与内部 endpoint
allowedPaths:
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/assets/VersionDetailPage.tsx
  - frontend/src/features/assets/PreviewPanel.tsx
  - frontend/src/features/assets/VersionListPanel.tsx
  - frontend/src/features/assets/AssetsPage.tsx
  - frontend/src/features/assets/api.ts
  - frontend/src/app/router/routes.tsx
  - frontend/src/shared/types/api.ts
  - frontend/src/shared/i18n/en.json
  - frontend/src/shared/i18n/zh.json
  - frontend/e2e/dataset.spec.ts
  - frontend/e2e/version-management.spec.ts
  - frontend/src/features/assets/__tests__
  - docs/ai-spec/tasks/evidence/EVD-P1013-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P1013-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-013.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-013.md -CheckChangedPaths
  - pnpm lint
  - pnpm typecheck
  - pnpm test
  - pnpm build
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P1-013：前端详情页统一外壳与 Files/Preview Tab（Wave S 差距闭合）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

将资产详情页从堆叠式改造为 REQ-DST-DETAIL-001 要求的统一外壳：`Overview | Versions | Files | Preview | Discussions | Lineage | Access | Settings` Tab + 顶部版本切换器（切换 Version 同步 URL 与版本化 Query Key），补齐 Files 树与 Parquet/Schema Preview，修复 `/assets/:id/versions` 404 路由。

## 2. 关联规格

```yaml
requirements: ['REQ-DST-DETAIL-001']
acceptanceScenarios: ['AC-DST-DETAIL-001', 'AC-DST-DETAIL-002', 'AC-DST-DETAIL-003', 'AC-DST-DETAIL-004']
decisions: ['DEC-008', 'DEC-010', 'DEC-014']
adrs: ['ADR-0001', 'ADR-0002']
openapiOperations: ['getAsset', 'listVersions', 'getVersion', 'listArtifacts', 'getPreview', 'listDiscussions']
flywayBaseline: V36
```

## 3. 范围

### 允许修改

- 前端：详情页外壳、版本详情页、Preview 面板、VersionListPanel、AssetsPage facet 交互、路由、api/types、i18n、e2e、组件测试。

### 明确不在范围

- 后端预览自动取数（属 TASK-P2-011）、后端 `/datasets` 端点对齐（属 TASK-P2-011）。
- 详情页发布/下载动作集成（属 TASK-P3-009）。

### 禁止变化

- 不改变后端 API/状态/权限；不引入 mock 数据或静态假数据冒充未实现阶段能力。
- 不修改 V1/V2 Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | Reader | 进入资产详情 | 渲染外壳 | Tab 顺序固定；未实现阶段能力 Tab 不展示；后端「支持但无内容」显示合法 Empty；不展示可点击 Placeholder | `E4` |
| `AC-DST-DETAIL-002` | Reader | 详情页有多个 Version | 切换 Version | URL 更新 + 所有版本化 Query Key 失效重取；禁止跨版本缓存混用；`latestPublished` 由服务端计算 | `E4` |
| `AC-DST-DETAIL-003` | Reader | 选定 Version | 打开 Files/Preview | Files 渲染 Artifact 树（路径/大小/媒体类型/SHA-256）；Preview 渲染 schema/列类型/分页；绑定精确 Version | `E4` |
| `AC-DST-DETAIL-004` | Reader | 点击「view all versions」或访问 `/assets/:id/versions` | 路由可达 | 不再 404；Card/Markdown 安全渲染；私有资产防枚举；不泄露内部 endpoint/预签名查询串 | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 无新增 OpenAPI 操作；复用 `getAsset/listVersions/getVersion/listArtifacts/getPreview/listDiscussions`。
- 无 Migration 变更。
- 前端类型与 api 客户端对齐现有 OpenAPI 生成类型。

## 7. 实施步骤

- [ ] 详情页外壳 Tab 组件 + 版本切换器 + URL 同步
- [ ] Files Tab Artifact 树组件
- [ ] Preview Tab Parquet/Schema viewer（列类型/分页）
- [ ] 修复 `/assets/:id/versions` 路由 + VersionListPanel 链接
- [ ] AssetsPage facet 可点击驱动搜索（与 TASK-P2-011 字典对齐后端配合）
- [ ] 组件测试 + e2e（dataset/version-management）
- [ ] Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-013.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-013.md -CheckChangedPaths
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 9. 停止条件

- 后端 Preview/Artifacts API 不能支撑 Files 树/Parquet viewer 时停止并报告（依赖 TASK-P2-011）。
- 需要削弱授权/校验/测试时停止。

## 10. 完成报告

```text
Completed:
Changed files:
Requirements and scenarios satisfied:
Contract/database changes:
Validation run and results:
Evidence artifacts:
Validation not run and reasons:
Remaining risks or follow-ups:
```

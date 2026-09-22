---
schemaVersion: harnessdg.task/v1
taskId: TASK-P2-011
status: READY
implementationAuthorized: true
phase: P2
baseCommit: a580a3da403f894b9c9b3777b64e2867c1fb078f
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P2 版本/传输/预览已部分落地；本任务为预览自动取数与 /datasets 端点对齐差距闭合
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-PRE-001
  - REQ-DST-TAX-001
acceptanceScenarios:
  - AC-DST-PRE-001
  - AC-DST-PRE-002
  - AC-DST-PRE-003
  - AC-DST-PRE-004
  - AC-DST-TAX-001
  - AC-DST-TAX-002
decisions:
  - DEC-008
  - DEC-010
  - DEC-015
scenarioEvidencePlan:
  - AC-DST-PRE-001|E4|backend integration test: PreviewJobHandler 按选定 Version 的 artifact 路径从 MinIO 自动取数生成 Parquet/CSV/JSONL 样例，无需调用方传 content
  - AC-DST-PRE-002|E4|integration test: 图片/文本预览分支按 mediaType 安全渲染（SSRF/DNS rebinding 防护、大小/格式上限）
  - AC-DST-PRE-003|E4|integration test: 预览绑定精确 Version，schema/列类型/分页正确；不可信内容不改变 Tool Policy
  - AC-DST-PRE-004|E4|security test: 预览失败/越权/超限返回防枚举错误，不泄露私有资产存在
  - AC-DST-TAX-001|E4|backend test: GET /datasets 支持 taskCodes/modalityCodes/formatCodes/languageCodes/tagId 全分类维度，同维度 OR、跨维度 AND
  - AC-DST-TAX-002|E4|backend test: Facet 计数在权限/敏感度/状态过滤后计算；DEPRECATED 降权；ARCHIVED/未完成 Provision 默认不返回
crossCuttingPlan:
  - AUTHN|预览与 /datasets 端点复用现有 JWT PrincipalContext；匿名拒绝
  - AUTHZ|预览按 asset:preview/asset:read 与资产 ACL/可见性过滤；/datasets 查询权限下推至 SQL
  - DB_FILTER|/datasets 与 Facet 计算必须经 AssetSearchDao 权限/敏感度/状态过滤，不得泄露无权资产计数
  - STATE|预览仅对存在 Artifact 的 Version 生成；Version 状态非 PUBLISHED 时按权限决定可见性
  - IDEMPOTENCY|预览生成走既有 persistent job + Idempotency-Key，不引入 raw thread
  - CONSISTENCY|预览样例取自 Version 锁定 Commit 对应的 MinIO 对象，不随草稿漂移
  - ERRORS|PREVIEW_NOT_FOUND/PREVIEW_TOO_LARGE/ASSET_NOT_VISIBLE/COMMON_INVALID_ARGUMENT 按错误目录映射；防枚举语义
  - AUDIT|预览生成/失败审计 ASSET_PREVIEW_*；/datasets 搜索仅记脱敏访问指标，不记完整查询文本
  - NOTIFICATION|N/A - 预览与搜索无通知动作
  - TAXONOMY_I18N|/datasets 过滤与 Facet 使用 dataset_task/modality/format/language 字典 itemCode；停用项保留回显不可新建
  - CONFIG|预览大小/格式上限走 aihub.preview.* 配置；不硬编码
  - OBSERVABILITY|预览 job 指标与 trace；下载统计不并入
  - SECRETS|MinIO 取数使用 STS/scoped 凭据；预签名 URL 查询串不进入日志/审计/响应正文
allowedPaths:
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewJobHandler.java
  - backend/src/main/java/com/aihub/version/infrastructure/ParquetPreviewReader.java
  - backend/src/main/java/com/aihub/version/application/PreviewApplicationService.java
  - backend/src/main/java/com/aihub/version/domain
  - backend/src/main/java/com/aihub/asset/api/AssetCatalogController.java
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
  - backend/src/main/java/com/aihub/asset/application/AssetFacetView.java
  - backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java
  - backend/src/test/java/com/aihub/version/infrastructure/PreviewContentSecurityTest.java
  - backend/src/test/java/com/aihub/version
  - backend/src/test/java/com/aihub/asset/application
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/TASK-P2-011.md
  - docs/ai-spec/tasks/evidence/EVD-P2011-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P2011-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-011.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-011.md -CheckChangedPaths
  - ./mvnw verify
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
approvedBy: User (plan execution authorization 2026-07-12)
approvedAt: 2026-07-12T03:54:00Z
---
# TASK-P2-011：后端预览自动取数与 /datasets 端点对齐（Wave T 差距闭合）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

使 PreviewJobHandler 按选定 Version 的 artifact 路径从 MinIO 自动取数生成 Parquet/CSV/JSONL/图片/文本预览（无需调用方传 content），并使 `GET /datasets` 过滤维度与 Facet 对齐 `GET /assets` 全分类维度与字典。

## 2. 关联规格

```yaml
requirements: ['REQ-PRE-001', 'REQ-DST-TAX-001']
acceptanceScenarios: ['AC-DST-PRE-001', 'AC-DST-PRE-002', 'AC-DST-PRE-003', 'AC-DST-PRE-004', 'AC-DST-TAX-001', 'AC-DST-TAX-002']
decisions: ['DEC-008', 'DEC-010', 'DEC-015']
adrs: ['ADR-0001']
openapiOperations: ['getPreview', 'listDatasets', 'getAssetFacets']
mcpTools: ['asset_search']
flywayBaseline: V36
```

## 3. 范围

### 允许修改

- 后端：PreviewJobHandler、ParquetPreviewReader、PreviewApplicationService、version domain；AssetCatalogController、AssetApplicationService、AssetFacetView、AssetSearchDao；对应测试。
- 契约：aihub-v1.yaml 的 preview/datasets/facets schema 与描述。

### 明确不在范围

- 前端 Preview Tab UI（属 TASK-P1-013）；上传占位/血缘删除（属 TASK-P2-012）。

### 禁止变化

- 不削弱授权/权限下推/预览安全上限；不修改 V1/V2 Migration。
- 不引入向量检索或推理服务（DEC-008 非目标）。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-PRE-001` | Reader | Version 有 Parquet/CSV/JSONL artifact | 请求 preview | Job 按 artifact 路径从 MinIO 自动取数生成样例，无需调用方传 content | `E4` |
| `AC-DST-PRE-002` | Reader | artifact 为 image/text | 请求 preview | 按 mediaType 安全渲染；SSRF/DNS rebinding 防护；大小/格式上限生效 | `E4` |
| `AC-DST-PRE-003` | Reader | 选定精确 Version | 请求 preview | schema/列类型/分页正确；绑定 Version；不可信内容不改变 Tool Policy | `E4` |
| `AC-DST-PRE-004` | 无权主体 | 请求私有/越权 preview | 返回防枚举错误 | 不泄露资产存在；审计越权 | `E4` |
| `AC-DST-TAX-001` | Reader | 调用 `GET /datasets` | 带 taskCodes/modalityCodes/formatCodes/languageCodes/tagId | 全维度过滤；同维度 OR、跨维度 AND | `E4` |
| `AC-DST-TAX-002` | Reader | 调用 facets | 权限/敏感度/状态过滤后计算 | Facet 计数不泄露无权资产；DEPRECATED 降权；ARCHIVED/未完成 Provision 默认不返回 | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

1. OpenAPI：`getPreview` 请求不再要求 `content`；`listDatasets` 参数补 `taskCodes/modalityCodes/formatCodes/languageCodes`；facets schema 补全 dataset 维度。
2. 错误码：`PREVIEW_NOT_FOUND/PREVIEW_TOO_LARGE/ASSET_NOT_VISIBLE`。
3. 无新增 Migration；复用 V35 字典种子。
4. 校正 `x-implementation-status` 与项目状态一致（不谎报）。

## 7. 实施步骤

- [ ] OpenAPI 契约更新 + lint + breaking diff
- [ ] PreviewJobHandler 从 MinIO 自动取数 + 图片/文本分支 + 安全上限
- [ ] AssetCatalogController/AssetSearchDao 全分类维度 + Facet 权限下推
- [ ] 单元/集成/契约/授权/失败路径测试
- [ ] Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-011.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-011.md -CheckChangedPaths
./mvnw verify
npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
npx @redocly/cli diff contracts/openapi/aihub-v1.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
```

## 9. 停止条件

- 需要永久 MinIO 凭据进入代码/日志时停止。
- 需要削弱预览安全上限或权限下推时停止。

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

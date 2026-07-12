---
schemaVersion: harnessdg.task/v1
taskId: TASK-P3-009
status: READY
implementationAuthorized: false
phase: P3
baseCommit: 71a03efcbaf9408b7ed07df5a7015c4ee110f8c3
stageGatePassed: true
stageGateEvidence: P0-B VERIFIED；P3 发布治理已部分落地；本任务为详情/版本上下文集成发布与下载差距闭合
stageGateEvidenceRefs:
  - DEC-008
  - DEC-010
requirements:
  - REQ-REV-002
  - REQ-DL-001
acceptanceScenarios:
  - AC-P3-UI-001
  - AC-P2-DL-001
decisions:
  - DEC-008
  - DEC-010
  - DEC-014
scenarioEvidencePlan:
  - AC-P3-UI-001|E4|frontend e2e + vitest: 资产/版本详情页内嵌发布提交入口（submitPublishRequest）、审批决策入口与下载入口，无需手填 assetId；权限按钮与后端一致
  - AC-P2-DL-001|E4|frontend e2e + backend integration test: 详情/版本上下文 issueDownloadTicket 返回 PRESIGNED_URL/GIT_DVC，DVC 凭据经 getDvcCredentials 接线，日志脱敏
crossCuttingPlan:
  - AUTHN|发布提交与下载票据复用 JWT PrincipalContext；匿名拒绝
  - AUTHZ|发布提交需 asset:submit/asset:publish；下载需 asset:download；按资产 ACL/状态/敏感度
  - DB_FILTER|N/A - 写/签发路径按主键定位；下载票据按 versionId + 权限校验
  - STATE|发布提交仅在 Version 状态 PENDING_REVIEW 允许；下载仅对已授权 Version 签发
  - IDEMPOTENCY|发布提交与下载票据复用 Idempotency-Key
  - CONSISTENCY|发布走既有 Saga/Outbox；下载票据过期与对账复用现有机制
  - ERRORS|VERSION_NOT_SUBMITTABLE/DOWNLOAD_FORBIDDEN/VERSION_NOT_FOUND 按目录映射；防枚举
  - AUDIT|PUBLISH_SUBMITTED/DOWNLOAD_TICKET_ISSUED 审计，含 Principal/资源/敏感度/Trace；日志脱敏预签名查询串
  - NOTIFICATION|发布提交触发 VERSION_* fan-out（既有）；本任务不新增通知类型
  - TAXONOMY_I18N|按钮/错误文案 i18n；不涉及字典/标签变更
  - CONFIG|下载票据过期走 aihub.download.expiry 配置
  - OBSERVABILITY|发布/下载指标与 trace
  - SECRETS|预签名 URL 与 DVC 凭据不进入日志/审计/响应正文；仅返回 handle/方法
allowedPaths:
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/version/ReviewPage.tsx
  - frontend/src/features/version/VersionPage.tsx
  - frontend/src/features/version/api.ts
  - frontend/src/features/upload/UploadPage.tsx
  - frontend/src/features/assets/api.ts
  - frontend/src/shared/types/api.ts
  - frontend/src/shared/i18n/en.json
  - frontend/src/shared/i18n/zh.json
  - frontend/e2e/publish-flow.spec.ts
  - frontend/e2e/version-management.spec.ts
  - backend/src/main/java/com/aihub/transfer/api/DownloadController.java
  - backend/src/main/java/com/aihub/transfer/application/DownloadApplicationService.java
  - backend/src/test/java/com/aihub/version
  - backend/src/test/java/com/aihub/transfer
  - docs/ai-spec/tasks/evidence/EVD-P3009-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P3009-001.yaml
requiredValidationCommands:
  - pwsh ./docs/ai-spec/tools/validate-spec.ps1
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-009.md
  - pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-009.md -CheckChangedPaths
  - ./mvnw verify
  - pnpm lint
  - pnpm typecheck
  - pnpm test
  - pnpm build
approvedBy: pending-verification-authority
approvedAt: 2026-07-12T00:00:00Z
---
# TASK-P3-009：详情/版本上下文集成发布与下载（Wave V 差距闭合）

> 状态：`READY`（待用户授权；`implementationAuthorized: false`，AI 不自行授权）

## 1. 目标

在资产/版本详情上下文集成发布提交（submitPublishRequest）、审批决策与下载（issueDownloadTicket + DVC 凭据）入口，消除 `ReviewPage/VersionPage/UploadPage` 手填 `assetId` 的工具页式体验，接线已有但未用的 `createDraftVersion/getDvcConfig/getDvcCredentials` API。

## 2. 关联规格

```yaml
requirements: ['REQ-REV-002', 'REQ-DL-001']
acceptanceScenarios: ['AC-P3-UI-001', 'AC-P2-DL-001']
decisions: ['DEC-008', 'DEC-010', 'DEC-014']
adrs: ['ADR-0001']
openapiOperations: ['submitPublishRequest', 'submitDecision', 'issueDownloadTicket', 'getDvcCredentials', 'createDraftVersion']
flywayBaseline: V36
```

## 3. 范围

### 允许修改

- 前端：AssetDetailPage、ReviewPage、VersionPage、UploadPage、version/api、assets/api、types、i18n、e2e。
- 后端：DownloadController/DownloadApplicationService 调整（如需上下文签发）、对应测试。

### 明确不在范围

- 详情页 Tab 外壳（属 TASK-P1-013）；后端预览/端点对齐（属 TASK-P2-011）。

### 禁止变化

- 不改变发布状态机/四眼/不可变 Tag；不削弱下载授权/脱敏；不修改 V1/V2 Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P3-UI-001` | Maintainer/Approver | 进入资产/版本详情 | 提交发布/审批 | 入口内嵌于上下文，无需手填 assetId；权限按钮与后端一致；状态前置条件校验 | `E4` |
| `AC-P2-DL-001` | Downloader | 版本详情 | 请求下载 | issueDownloadTicket 返回 PRESIGNED_URL/GIT_DVC；DVC 凭据经 getDvcCredentials 接线；日志脱敏；过期生效 | `E4` |

## 5. 横切要求

见 Front Matter `crossCuttingPlan` 全部 13 项。

## 6. 契约与数据先行

- 无新增 OpenAPI 操作；复用 `submitPublishRequest/submitDecision/issueDownloadTicket/getDvcCredentials/createDraftVersion`。
- 无 Migration 变更。
- 前端 api/types 对齐现有 OpenAPI 生成类型。

## 7. 实施步骤

- [ ] 详情/版本页内嵌发布提交 + 审批决策入口（消除手填 assetId）
- [ ] 版本详情下载入口 + DVC 凭据接线
- [ ] createDraftVersion 表单接线
- [ ] e2e（publish-flow/version-management）+ 组件测试
- [ ] 后端下载上下文签发测试（如需）
- [ ] Evidence Manifest

## 8. 验证命令

```text
pwsh ./docs/ai-spec/tools/validate-spec.ps1
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-009.md
pwsh ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P3-009.md -CheckChangedPaths
./mvnw verify
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

## 9. 停止条件

- 需要削弱发布四眼/不可变 Tag 时停止。
- 需要将预签名 URL/DVC 凭据写入日志时停止。

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

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-068
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 13ddf2615769745fedc638e99db28c261047ede9
stageGatePassed: true
stageGateEvidence: 已授权（批准人 User，2026-09-29）。差距事实链：PreviewJobHandler L121-125 对 image/* 仅写
  note="render via presigned endpoint"，该端点在代码与契约中均不存在（E2 静态取证，2026-09-29 复证，
  见 iteration-plan-2026-09-24.md §4.2 G2 与 test-report-2026-09-29.md）；REQ-DST-DETAIL-001
  页面组成第 3/4 项要求 Files/Preview 绑定精确 Version，且 L111 明确「预签名查询串不进入页面数据」，
  故本卡采用服务端代理流式渲染而非 presigned 票据（与迭代计划 §4 第 4 条原建议的差异见 §3）。
  实施前提已闭合：TASK-P0BR-067 改动已由批准人授权提交为 13ddf26（本卡 baseCommit），contracts/openapi/aihub-v1.yaml 重叠风险解除。
approvedBy: User
approvedAt: 2026-09-29T03:31:53Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-067-001.yaml
requirements:
  - REQ-DST-DETAIL-001
acceptanceScenarios:
  - AC-DST-DETAIL-001
  - AC-DST-DETAIL-003
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-DST-DETAIL-001|E4|真实 Compose 栈 Playwright：上传含 fixture PNG 的数据集版本并生成预览后，
    详情页 Preview Tab 渲染出图片元素（img src 为 blob: ObjectURL，非任何 MinIO/内部 Endpoint 主机名）；
    切换 Version 后图片随版本化 Query Key 刷新；Vitest 组件测断言 image 分支渲染与 ObjectURL 回收
  - AC-DST-DETAIL-003|E4|Playwright：无 asset:read 主体直连资产详情无 Preview 内容；后端契约测试断言
    匿名 401、无权限 403、未知/他人 artifactId 与「不存在」采用同一 404 防枚举语义；
    非 image mediaType 与 SVG 白名单外类型 415；超过 maxRenderBytes 413
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；新端点属受保护业务读接口，不新增匿名面；
    前端经既有 axios 拦截器注入内存 access JWT（Blob 请求同走该实例），浏览器原生 <img src=平台URL>
    不可行正是选择代理+ObjectURL 的原因之一（JWT 不进 URL 查询串）
  - AUTHZ|在应用服务内 requirePermission("asset:read")（fail closed，先于任何存储访问，与
    DownloadApplicationService.issueTicket 同族口径）；artifact 必须属于给定 versionId、version 必须属于
    给定 assetId，跨绑定一律同 404 防枚举；不新增/不放宽任何权限口径
  - DB_FILTER|N/A - 不改任何列表查询与权限下推；单资源读取按 assetId/versionId/artifactId 主键链路校验归属
  - STATE|不改变 VersionStatus 取值；渲染仅允许 PUBLISHED 版本（与下载同口径），非 PUBLISHED 返回
    VERSION_STATE_NOT_ALLOWED(409) 前先完成权限检查（防状态探测）
  - IDEMPOTENCY|N/A - 纯只读 GET，无写操作、无副作用状态
  - CONSISTENCY|N/A - 单存储读（dvc-cache），不跨系统写入；对象缺失映射 PREVIEW_SOURCE_UNAVAILABLE，
    与 PreviewJobHandler 取数失败同口径
  - ERRORS|实施期 E2 复证更正（原起草表述失实，2026-09-29）：shared/error/ErrorCode.java 枚举
    **并不含**任何 PREVIEW_* 项——契约 x-error-codes 中的 PREVIEW_NOT_FOUND / PREVIEW_TOO_LARGE /
    PREVIEW_UNSUPPORTED_FORMAT / PREVIEW_SOURCE_UNAVAILABLE 只是 PreviewJobHandler 抛
    UnsupportedOperationException 的消息前缀，被 GlobalExceptionHandler 兜底为 500 INTERNAL_ERROR。
    PlatformException 的 HTTP 状态唯一来源是 ErrorCode.httpStatus()，仓内无 messages*.properties 兜底，
    故 §4 的 415/413/404 语义必须新增枚举项。已由批准人授权补正 allowedPaths 增
    backend/src/main/java/com/aihub/shared/error/ErrorCode.java（2026-09-29，沿用 TASK-067 同一补正先例），
    本卡仅**新增** PREVIEW_NOT_FOUND(404) / PREVIEW_UNSUPPORTED_FORMAT(415) /
    PREVIEW_TOO_LARGE(413) / PREVIEW_SOURCE_UNAVAILABLE(404，与 DVC_OBJECT_MISSING 同族语义) 四项，
    绝不修改或删除任何既有枚举项、状态码或权限口径；既有 ASSET_NOT_FOUND / VERSION_NOT_FOUND /
    AUTH_PERMISSION_DENIED / VERSION_STATE_NOT_ALLOWED 直接复用
  - AUDIT|新端点为版本工件内容读取，与既有 previews GET 同族只读；本卡不新增审计事件类型
    （DownloadApplicationService 的 DOWNLOAD_TICKET_ISSUED 对应「可离线外带」的完整下载票据，
    预览代理受 maxRenderBytes 上限约束、语义等同页面渲染，沿用预览族无审计现状）；如验证权威
    认定需审计，属口径变更须批准人裁定，不在本卡擅自扩缩
  - NOTIFICATION|N/A - 不触发通知
  - TAXONOMY_I18N|PreviewPanel 图片分支新增文案（加载失败提示）须 zh/en 同步（version.previewImageError 键）
  - CONFIG|PreviewProperties 增 maxRenderBytes（默认 5 MiB，@ConfigurationProperties 内置默认值，
    不改 deploy/；application.yml 显式键仅在需要覆盖时加入本卡允许的 resources 路径）；
    渲染白名单固定为 image/png、image/jpeg、image/webp、image/gif（服务端常量，SVG 明确排除——
    防内嵌脚本 XSS；新增类型须经规格变更而非代码私改）
  - OBSERVABILITY|沿用 PlatformMetrics 预览失败计数（limit_exceeded/unsupported_format/source_unavailable
    既有 reason 键），不新增指标或告警
  - SECRETS|对象存储凭据、内部 Endpoint、预签名查询串一律不进入响应体/日志/页面数据
    （REQ-DST-DETAIL-001 L111）；响应仅回字节流与 Content-Type。
    实施期 E4 复证发现（2026-09-29，凭据路由事实随本项登记）：backend 唯一 MinioClient 装配主凭据
    （aihub-minio-key，策略 aihub-asset-rw 仅覆盖 asset-staging/asset-preview），而预览代理/对账/回收
    均需按桶访问 dvc-cache（凭据隔离归 dvc-minio-key）→ objectExists 恒 false → artifact 404，
    且 reconciler 日志大量 Access Denied。已由批准人授权补正 allowedPaths 增
    integration/minio 三文件（2026-09-29，沿用 067/ErrorCode/client.ts 同一补正先例）：为 dvc-cache
    按已配置的 DVC scoped 凭据（AIHUB_MINIO_DVC_* 既有 env，不回退主凭据、不改 deploy/）
    构建独立 MinioClient 并在 StoragePort/存在性端口按桶路由；未配置 DVC 凭据时回退主客户端
    以兼容本地全权（minioadmin）场景。属缺陷修复：不新增权限口径、不放宽任何断言或策略
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/java/com/aihub/shared/error/ErrorCode.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioConfiguration.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/MinioStorageAdapter.java
  - backend/src/main/java/com/aihub/integration/minio/infrastructure/RestMinioObjectExistencePort.java
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewJobHandler.java
  - backend/src/main/java/com/aihub/version/infrastructure/PreviewProperties.java
  - backend/src/main/java/com/aihub/version/application/PreviewApplicationService.java
  - backend/src/main/java/com/aihub/version/api/PreviewController.java
  - backend/src/test
  - frontend/src/features/assets/PreviewPanel.tsx
  - frontend/src/features/assets/PreviewPanel.test.tsx
  - frontend/src/shared/api/client.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-068.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml
preExistingDirtyPaths:
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-068.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-068.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖预览渲染契约测试与越权/边界用例）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 含 PreviewPanel image 分支）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，含新预览渲染用例全量无回归）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-068-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（EVD-P0BR-068-base.yaml 由 git show baseCommit 导出为变更前基线，终态归档为 build-logs/EVD-P0BR-068-base.yaml.log；上游 redocly diff 已移除，实际以 oasdiff breaking 执行非破坏取证，TASK-067 §6.8 确立的替代路径；本环境 oasdiff 亦不可得，以基线 numstat 39/0 纯新增论证，见 EVD-P0BR-068-001 coverage.notProven；CI redocly diff 既存缺陷不在本卡修复）
---

# TASK-P0BR-068：图片预览渲染闭环（预览工件服务端代理端点）

> 状态：`READY`（批准人 User，2026-09-29T03:31:53Z；AUDIT 口径经批准人裁定：沿用预览族无审计现状，见 crossCuttingPlan.AUDIT）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

有权主体在数据集详情页 Preview Tab 能看到绑定精确版本的图片预览真实渲染出来；无权限主体、越界 artifactId、白名单外类型和超限对象全部 fail closed。修复迭代计划 §4.2 复证的 G2 断链（后端注释声称的渲染端点不存在、前端无 image 分支）。

## 2. 关联规格

```yaml
requirements: [REQ-DST-DETAIL-001]
acceptanceScenarios: [AC-DST-DETAIL-001, AC-DST-DETAIL-003]
decisions: [DEC-008]
adrs: [ADR-0001]
openapiOperations: [getPreview, triggerPreviewGeneration, 新增 renderPreviewArtifact]
mcpTools: []
eventSchemas: []
flywayBaseline: V2（无新增 Migration；asset_preview 表无需变更——图片 note 为 JSON 内容字段内扩展）
```

- `REQ-DST-DETAIL-001`（READY，E4）：页面组成第 3/4 项要求 Files/Preview 绑定选定 Version；
  L109「图片…视为不可信数据，安全渲染」；L110 防枚举语义；L111 预签名查询串/内部 Endpoint
  不进页面数据——本卡全部设计约束的直接来源。
- `AC-DST-DETAIL-001`（NORMAL）与 `AC-DST-DETAIL-003`（UNAUTHORIZED）取自
  `05-acceptance/dataset-agent-exit-catalog.md`，均要求 E4。

## 3. 范围

### 允许修改

- 契约：`GET /assets/{assetId}/previews/artifact`（operationId `renderPreviewArtifact`；query：
  `versionId` 必填、`artifactId` 必填；`x-required-permissions: [asset:read]`；200 响应
  `image/{png,jpeg,webp,gif}` 字节流；4xx 复用既有 ErrorResponse）。登记
  `x-implementation-status: implemented-unverified`。
- 后端：
  1. `PreviewJobHandler`：image 分支 note JSON 增 `artifactId`、`artifactPath` 分量（前端据以调用渲染端点；
     `fetchFromStorage` 已持有 target artifact，改动为透传）；
  2. `PreviewApplicationService`：新增 `renderPreviewArtifact(assetId, versionId, artifactId)` 用例——
     权限先行 → 版本归属校验 → PUBLISHED 校验 → artifact 归属与 mediaType 白名单校验 →
     dvc-cache 对象存在性 → 返回流 + Content-Type + 长度守卫；对象键复用
     `dvcObjectKey(dvcHash, path)` 既有算法（抽公共方法或同包工具，禁止复制漂移实现）；
  3. `PreviewController`：新 GET 端点（纯适配器，无业务规则）；
  4. `PreviewProperties`：增 `maxRenderBytes`（默认 5 MiB）。
- 前端：`PreviewPanel.tsx` 增 image 分支——解析 preview content JSON，`mediaType` 为白名单 image 类型且
  含 `artifactId` 时，经既有 apiClient（`responseType: 'blob'`，自动带内存 JWT）取字节流，
  `URL.createObjectURL` 渲染 `<img>`，卸载/换版本时 `revokeObjectURL`；`note` 缺 `artifactId`
  （旧投影）时显示合法 Empty + 触发重新生成的引导文案，不得白屏或裸 JSON。
  实施期 E2 复证：`client.ts` 的 `request()` 硬编码 `{data,requestId,...}` 成功包解包，二进制端点
  （200 裸字节流）无法复用；已由批准人授权补正 allowedPaths 增 `frontend/src/shared/api/client.ts`
  （2026-09-29，沿用 067/ErrorCode 补正先例），仅**新增** `getBlob` 方法（同一 axios 实例，
  JWT 注入 / 401 刷新重试 / Accept-Language / ApiError 规范化全部继承），不改任何既有方法行为。
- i18n：`version.previewImageError` 等新键 zh/en 对称。
- 收口期治理复证（2026-09-29）：`PreviewPanel.test.tsx`（本卡新增 image 分支 Vitest 断言载体，
  §scenarioEvidencePlan 明确要求）未在起草期列入 allowedPaths，属漏列；已由批准人授权第四次
  补正增列该文件（沿用 067/ErrorCode/client.ts/minio 同一补正先例），仅新增测试用例、不改被测实现。
- e2e：`frontend/e2e` 新用例（fixture PNG 随 e2e 目录入库；上传/发布链复用既有 e2e helper）。
- 文档：本卡、EVD、work-breakdown 状态列。

### 明确不在范围

- **不采用 presigned URL**（与迭代计划 §4 第 4 条原建议的差异）：REQ-DST-DETAIL-001 L111 禁止
  预签名查询串进入页面数据，且 `<img src=平台代理URL>` 无法携带 Authorization 头；代理 + blob
  ObjectURL 同时满足两条约束。迭代计划 §4.2 已登记该路径修正。
- Split 维度（G5）独立成卡；G1 文件内容浏览、G4 收藏不触碰。
- `DownloadApplicationService`、上传链路、CI redocly diff 既存缺陷（067 已申报）均不改。

### 禁止变化

- 不允许把内部 Endpoint、bucket 名、对象键、presigned 查询串写入响应体、日志或前端可见 JSON。
- 不允许放开 SVG/其他可执行媒体类型内联渲染；不允许用户 supplied 的路径/URL 参与对象键解析
  （对象键只从服务端 artifact 记录推导——SSRF/路径穿越攻击面不适用前提）。
- 不改变既有 previews GET/generate 两操作的行为与契约（note JSON 新增分量为向后兼容增量）。
- 不新增权限口径、不修改任何 `x-required-permissions` 既有值；不改已应用 Migration。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-DETAIL-001` | USER(有 asset:read) | PUBLISHED 版本含白名单 image artifact，预览投影 note 含 artifactId | 打开 Preview Tab | `<img>` 渲染 blob:ObjectURL，图片字节与 dvc-cache 对象一致；页面数据不含内部主机名 | `E4` |
| `AC-DST-DETAIL-001` | USER | 同上 | 切换 Version | 图片随版本化 Query Key 刷新；旧 ObjectURL 被 revoke（Vitest 断言 revoke 调用） | `E2`+`E4` |
| `AC-DST-DETAIL-003` | 匿名 | 无 Token | GET renderPreviewArtifact | 401（过滤器 fail-closed） | `E2` |
| `AC-DST-DETAIL-003` | USER(无 asset:read) | 存在他人/无权资产 | GET renderPreviewArtifact | 403，先于任何存储访问；无对象存在性差异泄露 | `E2` |
| `AC-DST-DETAIL-003` | USER(有 asset:read) | artifactId 不属于该 version / version 不属于该 asset | GET | 与「不存在」同一 404 语义（防枚举） | `E2` |
| `AC-DST-DETAIL-001` 边界 | USER | artifact mediaType=image/svg+xml 或 text/csv | GET | 415 PREVIEW_UNSUPPORTED_FORMAT（白名单在 mediaType 判定处拒绝） | `E2` |
| `AC-DST-DETAIL-001` 边界 | USER | 对象大小 > maxRenderBytes | GET | 413 PREVIEW_TOO_LARGE，不缓冲全量进内存（长度守卫基于 artifact.size 预检 + 流式读取上限双保险） | `E2` |
| `AC-DST-DETAIL-004` 相邻失败 | USER | dvc-cache 对象缺失 | GET | PREVIEW_SOURCE_UNAVAILABLE 稳定错误；不伪造空图 | `E2` |

覆盖正常/边界/失败/越权；无写操作故无并发/恢复切片。

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **OpenAPI**：新增 `renderPreviewArtifact` 操作（response `content: image/png|image/jpeg|image/webp|image/gif`，
   `format: binary`；`x-error-codes` 按 §2 ERRORS 列全）；纯新增路径 → oasdiff breaking 必须 PASS。
2. **示例**：note JSON 契约无 schema（不透明 JSON 字符串），以前端组件测试 + 后端契约测试为口径载体；
   在操作 description 中写明 note 约定 `{mediaType, artifactId, artifactPath, note}`。
3. **新增 Flyway 文件**：无（`asset_preview.content` 为 JSON 文本列，note 内增量字段无 schema 变更；
   旧投影无 artifactId → 前端合法 Empty + 重新生成路径，见 §3）。
4. **存量清点与回填**：既有 image 预览投影缺 artifactId 属兼容窗口内合法状态，前端按 §3 回退；
   不做数据回填（重新触发 generate 即产出新结构）。
5. **索引/约束**：不变。
6. **兼容窗口与失败恢复**：后端先发（新端点+新 note 结构），旧前端 image 分支本就不存在、无回归面；
   新前端遇旧后端 → note 无 artifactId → 合法 Empty。回滚顺序：前端 → 契约回退（端点无人调用即无害）。
7. **生成类型更新**：本仓前端 API 类型手工同步（与 067 §6.7 同口径）；`PreviewPanel` 内 note 类型定义补齐。

## 7. 实施步骤

- [x] 契约及契约测试（OpenAPI 新增 renderPreviewArtifact + redocly lint PASS；后端 PreviewApplicationServiceRenderTest 13 项渲染/越权/边界矩阵。oasdiff 环境不可得以纯新增 39/0 论证，见 EVD-P0BR-068-001 coverage.notProven）
- [x] Migration：不适用（§6.3，无数据变更）
- [x] Application/Domain 用例（renderPreviewArtifact 权限先行→归属链→PUBLISHED→白名单→长度守卫；Handler note 增 artifactId/artifactPath；MinIO 按桶 scoped 凭据路由为批准补正的缺陷修复）
- [x] REST Adapter（PreviewController 流式 GET，纯适配器；MCP/Worker 不涉及）
- [x] 前端 PreviewPanel image 分支 + ObjectURL 生命周期 + client.getBlob（批准补正）+ i18n zh/en
- [x] 正常/边界/失败/越权测试（Vitest 224/224 含 PreviewPanel 新增用例 + 后端 776/0/0/10 + e2e preview-image.spec.ts 含 fixture PNG）
- [x] Compose Fixture/Verify（真实栈全量 e2e 101 passed / 0 failed / 0 skipped 两轮复跑；新用例 E4 主证含 UI blob 渲染与 artifact 字节 sha256 一致 + 越权 403）
- [x] 文档同步（work-breakdown 068 状态列已更新；page-catalog PAGE-DST-002 不在 allowedPaths，已登记 EVD-P0BR-068-001 coverage.notProven 移交项，沿 067 §7 末项先例）

全部勾选项证据见 `docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml`。

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
治理        validate-spec / validate-task-card / -CheckChangedPaths        各 exit 0
backend/    ./mvnw.cmd -q verify                                           exit 0   全 AC 后端切片
frontend/   pnpm lint; pnpm typecheck; pnpm test; pnpm build               各 exit 0  AC-DST-DETAIL-001 组件层
frontend/   pnpm test:e2e（真实 Compose 栈，凭据注入同 066/067 口径）        exit 0   AC-DST-DETAIL-001/003 E4 主证
contracts/  redocly lint + oasdiff breaking（§FM 替代路径）                 exit 0   契约非破坏
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（066/067 既有口径）。

## 9. 停止条件

- 批准人认定 AUDIT 口径应新增审计事件（§3 crossCuttingPlan AUDIT 的裁定点）→ 停该部分并回报；
- 067 改动未提交、工作树与本卡 allowedPaths 存在重叠来源未定 → 不得开工（Front Matter stageGateEvidence 前提）；
- e2e 上传链无法在现有 helper 下产出 PUBLISHED 含图版本 → 不得降低为 E2/E3 交差，如实记 notProven 并回报；
- 发现对象键解析存在任何用户输入参与路径 → 立即停止（§3 禁止变化）；
- 工作树存在与本卡 allowedPaths 重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
G2 图片预览渲染闭环（E4，真实 Compose 栈）：
- 契约：新增 GET /assets/{assetId}/previews/artifact（renderPreviewArtifact，x-required-permissions
  [asset:read]，200 为 image/{png,jpeg,webp,gif} 字节流），登记 implemented-unverified；
  redocly lint PASS（3 warnings 与基线等价）。
- 后端：PreviewApplicationService.renderPreviewArtifact（权限先行→asset/version/artifact 归属链→
  PUBLISHED→mediaType 白名单→maxRenderBytes 预检+流式双保险→对象缺失/IO 失败同 404 防枚举）；
  PreviewController 流式 GET 纯适配器；PreviewJobHandler image 分支 note 增 artifactId/artifactPath；
  PreviewProperties.maxRenderBytes 默认 5 MiB；ErrorCode 仅新增 PREVIEW_* 四项（批准补正）。
- 缺陷修复（批准补正）：backend 唯一 MinioClient 持主凭据无 dvc-cache 策略导致 objectExists 恒
  false、预览代理 404——改为 dvc-cache 按既有 AIHUB_MINIO_DVC_* scoped 凭据独立 MinioClient
  按桶路由（StoragePort 与存在性端口同路由，未配置时回退主客户端兼容本地全权），不新增权限口径。
- 前端：PreviewPanel image 分支（blob/ObjectURL 渲染、卸载与换版本 revokeObjectURL、旧投影缺
  artifactId → 重新生成引导空态、白名单外不请求）；client.ts 新增 getBlob（批准补正，继承 JWT
  注入/401 刷新/ApiError 规范化）；version.previewImageError 等键 zh/en 对称。
- e2e：新增 preview-image.spec.ts（fixture PNG 完整链 + UI blob 渲染 + 泄漏红线 6 项 +
  artifact 字节 sha256 一致 + 越权 403）；profile.spec.ts 的 goto('/assets') 受 nginx 静态遮蔽
  既存缺陷影响，改为登录落地客户端导航口径（断言强度不变，deploy 属 forbiddenPaths）。

Changed files:
见 EVD-P0BR-068-001.yaml source.dirtyPaths（backend main 8 + backend test 4（含新增
PreviewApplicationServiceRenderTest）+ contracts 1 + frontend src 4 + frontend e2e 3（含新增
spec 与 fixture）+ docs 3）。全部命中 allowedPaths（含四次批准补正），validate -CheckChangedPaths PASS。

Contract/database changes:
契约纯新增 39 行/0 删除（oasdiff 环境不可得，以 numstat + redocly lint 论证非破坏，登记 notProven）；
无 Flyway Migration（§6.3 不适用，asset_preview.content JSON 内增量字段向后兼容）。

Validation run and results:
mvnw -q verify 776/0/0/10 exit 0；pnpm lint/typecheck exit 0（终态复跑）；Vitest 49 files/224 tests；
pnpm build ✓ 29.66s；pnpm test:e2e（nginx 8080 容器前端真实栈）101 passed/0 failed/0 skipped 两轮
（2.4m/2.5m）exit 0；redocly lint PASS；治理三连 PASS（见 governance-068.log）。
日志归档 build-logs/068-*.log 与 EVD-P0BR-068-*.log。

Validation not run and reasons:
oasdiff breaking（不在 PATH、go install 代理不可达）——以纯新增论证替代；
redocly diff --fail-on-breaking（上游已移除子命令，067 已申报的 CI 既存缺陷）；
新端点 standalone 401 断言与 E4 双版本切换断言未做——均登记 EVD coverage.notProven 提请裁定。

Remaining risks or follow-ups:
本卡为 IMPLEMENTED_UNVERIFIED：review.accepted=false（AI 不自批，DEC-009），-CheckCompletion
按交接态失败（accepted/dirtyWorktree），代码未提交，提交与验收由批准人处置。
移交项：① page-catalog PAGE-DST-002 Preview Tab 口径登记；② nginx /assets 深链遮蔽缺陷根治
（基础设施卡）；③ CI redocly diff 既存缺陷（067 已申报）；④ notProven 两项（401 端点级断言、
E4 双版本切换）是否补证由验证权威裁定；⑤ compose 未起 dvc 原生 push 链（夹具 SigV4 PUT 模拟）。
```


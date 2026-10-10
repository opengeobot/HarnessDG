# 设计-实现差距复核与迭代计划（2026-09-24）

> 状态：迭代计划（规划文档，非规范；目标态以 `docs/ai-spec/04-ui/page-catalog.md`、
> `docs/ai-spec/01-requirements/dataset-experience.md`（DEC-008）为准）
> 日期：2026-09-24 ｜ 作者：AxeXie
> 基线：HEAD `ede5c76`（工作树干净）。本文档取代
> `modelscope-page-gap-analysis-2026-09-21.md` §4 与 `implementation-status-2026-07-12.md` §7-§8
> 中已过期的差距清单，作为后续迭代的唯一开放差距输入。

## 1. 复核方法

对 2026-07-12（B1-B7/F1-F8/E 系列）、2026-07-30/31（G0-G3 分级）、2026-09-21（14 项范式 + D1-D6）
三份差距文档中的全部条目，逐项以当前 HEAD 代码、契约 `contracts/openapi/aihub-v1.yaml`、
Flyway 迁移（最新 V43）重新取证。**旧文档大量条目已在 Wave S-Z、A-R 及 09-21 之后的 commit 中闭合**，
不得再作为实施输入；其中 `2026-07-31.md` L66「Parquet 为占位实现」已与现行
`ParquetPreviewReader` 真实解析代码矛盾（属文档滞后，见 §4-G8）。
`2026-07-30/31.md` 引用的 `.trae/specs/gap-analysis-code-vs-design/gap-list.md` 已不存在。

## 2. 已闭合项（本轮复核实证，无需再实施）

| 原编号 | 内容 | 证据 |
| --- | --- | --- |
| B1 | Parquet 预览自动取数 | `PreviewJobHandler.fetchFromStorage`（SDK 直读 dvc-cache，非 presigned，目标达成） |
| B2/B6 | `/datasets` 四维过滤 + 字典接入 | `AssetCatalogController.searchDatasets` + `AssetSearchDao` jsonb 过滤；V6/V35 种子齐 |
| B3 | 上传空 files 占位文件 | `UploadApplicationService` 改为 400 拒绝，占位逻辑不存在 |
| B4 | 血缘删除 API | `AssetController` DELETE `/relations/{relationId}` + 审计 |
| B7 | OpenAPI 虚标 implemented | 144 处 `x-implementation-status` 全量 `implemented-unverified` |
| F1-F6/D5(大部) | Tab 化详情、Files 树、Preview 挂载、Facet 接线、版本路由、发布/上传深链 | `AssetDetailPage` 8-Tab、`buildArtifactTree`、`PreviewPanel` 双挂载、`getAssetFacets`/`getDvcConfig`/`createDraftVersion`/`lockThread` 均已接线 |
| D1/#13 | 上传假进度 | `UploadPage` 真实 XHR PUT + ETag + 断点恢复 + 单 Part 重试 |
| D2 | ReviewPage 捏造实体 | 改用 `listPublishRequests` 真实 `publish_request` |
| D3 | 三页裸 Tailwind | 已 antd 化，src 无 Tailwind 类残留 |
| D4 | 不可发现路由 | `/review`、`/upload`、`/admin/*` 全入导航，且有 `navigation.test.ts` 回归守护 |
| D6(大部) | 硬编码文案 | NotFound/RouteGuard/AccessPage 已 i18n；残余见 G7 |

## 3. 仍开放差距（本轮实证）

- **G1 文件内容浏览（范式 #4）**：Tree 有目录骨架但 `selectable={false}`、无单文件内容面板；
  契约无 blob/内容端点（仅 `/{versionId}/artifacts` 扁平清单）。需契约先行。
- **G2 图片预览渲染（B5 残余）**：`PreviewJobHandler` 对 image 只写引用 note，
  注释声称的 presigned 渲染端点在代码与契约中均不存在——链路断在最后一步。
- **G3 订阅（范式 #12 前半）**：`asset_subscription` 表（V15）仅承载讨论订阅
  （POST/GET `/discussions/subscription`）；无独立订阅资源 REST、无个人中心 UI、前端零接线。
- **G4 收藏（范式 #12 后半）**：全仓无 favorite/bookmark 建模——无表、无端点、无 UI。需前向迁移 V44+。
- **G5 Split 切换（范式 #6 残余）**：预览投影无 train/validation/test 维度，前端不传 split 参数。
  DEC-008 `REQ-DST-DETAIL-001` 要求确认是否含 Split；若含则需后端投影扩展。
- **G6 版本切换器（范式 #5 残余）**：详情页外壳内是否有版本下拉 + URL/query-key 联动
  （REQ-DST-DETAIL-001 明确规则）待细验；`dvcFile/dvcHash` 字段在版本面板的渲染待核。
- **G7 工程小项**：`publishVersion`（`features/version/api.ts`）死代码；
  `useDocumentTitle` BASE_TITLE 硬编码；`client.ts` 网络错误兜底中文；孤儿 i18n 键 `access.tokensComingSoon`。
- **G8 文档对账**：`implementation-status-2026-07-31.md` L66 Parquet 描述过期（已修正）；
  `aihub-v1.yaml` 注解数 135 vs 操作数 133 已对账：135 = 133 个端点级注解 + 文件头 2 处注释提及，无孤儿注解。
- **G9 目标态缺位（需产品决策，非代码差距）**：门户首页 #1、组织/用户主页 #9、全局搜索 #10、
  文档中心 #11、`aih` CLI（REQ-DST-CLI-001）、创空间 #8（建议正式声明非目标）。
  page-catalog 无对应 PAGE-* 条目，按门禁须先补 REQ/PAGE/ADR。
- **G10 验收阻塞（非开发工作）**：P1-P5 `-CheckCompletion` 人工验收、E5 P5003 破坏性恢复演练
  （`down -v`，待用户窗口）、部分 EVD notProven 回写。

## 4. 迭代批次（按依赖与门禁排序）

**约束**：P1-P5 保持 `IMPLEMENTED_UNVERIFIED`，任何批次不得表述为 VERIFIED；
每批实施前需对应 READY 且 `implementationAuthorized: true` 的 Task Card
（AI 可起草，不得自批）；实现前跑 `validate-spec.ps1` / `validate-task-card.ps1`。

### Wave α — 工程收尾（无契约/迁移，风险最低）
1. G7 全部小项：删 `publishVersion` 死代码（确认无调用方后）、i18n 残余三处、孤儿键清理。
2. G8 文档对账：修正 07-31 快照 Parquet 描述、OpenAPI 注解 135/133 对账。
- 建议 Task：并入既有 P1/P4 卡片的 remediation 口径，或新开 `TASK-P1-019`。

### Wave β — 获取体验（契约先行）
3. G1 文件内容浏览：新 REST 端点（版本文件树 + 单文件内容代理，权限过滤 + 防枚举 + 大小上限 + 审计），
   OpenAPI 登记为 `implemented-unverified`，前端 Files Tab 接 Tree onSelect + 内容面板。
4. G2 图片预览：新增 presigned 渲染票据端点（复用 `DownloadController` 票据模式，短时效），
   前端 PreviewPanel 渲染 image 引用。
- 需新 Task Card（建议 `TASK-P2-014` 文件浏览、`TASK-P3-011` 图片预览）。

### Wave γ — 订阅与收藏（迁移 + REST + UI）
5. G3 订阅：独立 `/subscriptions` 资源（列表/创建/退订，复用 `asset_subscription` 表；
   版本更新通知语义接既有通知总线），ProfilePage 增订阅区块，资产详情页增订阅按钮。
6. G4 收藏：前向迁移 `V44__asset_favorite.sql`（含表/字段注释），REST + 列表页收藏标记 + 个人中心区块。
- 需新 Task Card（建议 `TASK-P1-019`/`TASK-P1-020`，绑定 REQ 需先确认 page-catalog/需求文档补条目）。

### Wave δ — 预览完善
7. G6 版本切换器核验与补齐（详情页外壳版本下拉 + URL 联动 + dvcFile/dvcHash 渲染）。
8. G5 Split：先核对 DEC-008 验收口径；若属 REQ-DST-DETAIL-001 范围则扩展 preview 投影与前端选择器。

### Wave ε — 目标态立项（产品决策门，前置为 REQ/PAGE/ADR 增补）
9. G9 各项逐一决策：最小可行顺序建议 = 文档中心（Redoc 挂 OpenAPI，纯前端低成本）→
   组织主页（复用 `/assets` 按 organizationId 过滤）→ 门户仪表盘首页 → 全局搜索（或明确由 #2 列表承载）→
   `aih` CLI（独立交付物）；#8 创空间写入非目标声明。
- 本批开工前需人类评审先行批准规格变更，AI 仅起草。

### Wave ζ — 验收收口（与开发并行推进）
10. G10：编排 `-CheckCompletion` 人工验收清单；向用户申请 P5003 恢复演练窗口；EVD notProven 回写。

## 4.1 复核增补（2026-09-28，HEAD `aa0a8a7`）

- **G3 已闭合**：`0620940` 接线讨论订阅（DiscussionPanel）；独立 `/subscriptions` 资源仍属后续增强。
- **G7 已闭合**：`publishVersion` 死代码与 `access.tokensComingSoon` 孤儿键已清除（全仓检索零命中）；
  `useDocumentTitle` 改用 `t('app.title')`；`client.ts` 网络错误文案仅存于注释。
- **G6 已闭合**：详情页版本切换器已联动 URL（`?version=`）与版本化 Query Key，
  gitTag/sourceCommit/manifestDigest 渲染齐备（`AssetDetailPage` L538-565 + `AssetDetailPage.version.test.tsx`）。
- **G1/G2/G4/G5 仍开放**：Files Tree 仍 `selectable={false}`；`PreviewPanel` 无 image 渲染分支；
  全仓无 favorite 建模；预览无 split 维度。均需契约/迁移先行 → Task Card 授权。

## 4.2 复核增补（2026-09-29，HEAD `e9aa2a0` + 067 未提交改动）

065~067 三张整改卡闭环后的再次实证复核（点击式功能测试见 `test-report-2026-09-29.md`）：

- **G1 仍开放**：`AssetDetailPage.tsx` L452 Files Tree 仍 `selectable={false}`；契约无单文件内容端点。
  涉及新增内容代理端点 + `asset:download` 口径决策，建议单独成卡（Wave β）。
- **G2 仍开放，且原技术路径需修正**：`PreviewJobHandler` L121-125 对 image 仅写
  `note="render via presigned endpoint"`，注释声称的 presigned 渲染端点在代码与契约中均不存在
  （断链复证）。**迭代计划 §4 第 4 条「复用 DownloadController 票据模式签发 presigned URL」与
  `REQ-DST-DETAIL-001` L111「预签名查询串不进入页面数据」直接冲突**；且 `<img src>` 原生标签
  不携带 Authorization 头，浏览器直接加载 presigned URL 亦受该约束限制。正确路径改为
  **服务端代理流式渲染**：新增 `GET /assets/{assetId}/previews/artifact`（权限过滤 + image 白名单
  + 对象存在性校验 + 流式回传），前端经既有 axios 拦截器（自动注入内存 JWT）以 blob 取图，
  `URL.createObjectURL` 渲染。已按此口径起草 `TASK-P0BR-068`（DRAFT，待批准）。
- **G4 仍开放**：全仓（backend/frontend/migrations）favorite/bookmark 零命中，确认需前向迁移。
  **设计口径缺口**：REQ-DST-DETAIL-001 页面组成 1-6 项与 page-catalog 均无「收藏」条目；
  按门禁须先补 REQ/PAGE 增补并经人类评审（Wave γ 开工前置），不得直接建卡实施。
- **G5 前提闭合、仍开放**：REQ-DST-DETAIL-001 页面组成第 4 项明确 Preview 含「Split」，
  L106 要求 Split 绑定精确 Version——§4 第 8 条「先核对是否属 REQ-DST-DETAIL-001 范围」的
  答案确认为**是**。当前 `asset_preview` 投影与预览生成链均无 split 维度，需投影扩展
  （契约 + 迁移 + Job + 前端选择器），建议 G5 与 G1/G2 分开独立成卡。

## 4.3 复核增补（2026-10-09，HEAD `70f7874` + 074/075 未提交改动）

按「以当前 worktree 为权威」逐项复取证（grep/glob 全仓实测 + 运行态核验），本轮修订如下：

- **G1 仍开放（记录修正）**：迭代计划与 §4.2 引用的 `AssetBrowser.tsx` 在当前树中不存在；
  实际锚点为 `AssetDetailPage.tsx` L452 Files Tree `selectable={false}`（复证命中）。
  契约仍无单文件内容/blob 端点（全仓 `content|blob|tree` 端点检索仅命中 previews/artifact 说明文字）。
  Wave β 契约先行口径不变；建议 `TASK-P0BR-077`（文件内容浏览，绑定 REQ-DST-DETAIL-001 第 3 项
  「Files：选定 Version 的 Artifact 树、路径、大小、媒体类型和 SHA-256」）。
- **G2 已闭合**：`TASK-P0BR-068` 已实施并由批准人提交（收口记录见 work-breakdown 068 行）——
  `PreviewController`（`/api/v1/assets/{assetId}/previews` 服务端代理流式端点）+ 契约
  `renderPreviewArtifact` + `PreviewJobHandler` image 引用 + `PreviewPanel` blob/ObjectURL 渲染 +
  `preview-image.spec.ts` E2E 全在位。E4 人工验收（`review.accepted`）仍待验证权威，不改变
  IMPLEMENTED_UNVERIFIED 定性。
- **G3 讨论订阅已闭合、独立订阅资源仍开放**：`asset_subscription`（V15）仅讨论域；
  通知侧 `NotificationRecipientResolver.resolveSubscribers` 已接讨论订阅。
  个人中心订阅/退订属 Wave γ，且需先补 REQ/PAGE（同 G4 门禁）。
- **G4 仍开放**：favorite/bookmark 全仓零命中复证；REQ-DST-DETAIL-001 页面组成与 page-catalog
  均无「收藏」条目——开工前置 = 人类评审批准 REQ/PAGE 增补（Wave γ），不得直接建卡。
- **G5 前提最终闭合、仍开放**：DEC-015（**ACCEPTED**，decidedBy User，2026-07-06）明确
  PAGE-DST-001 需「Subset 下拉 + Split Tab 选择器（首版仅单 Subset）+ 统计卡片（行数/大小/文件数/列数）」；
  page-catalog L61 已同步该要求。当前 backend `com.aihub.version` 与 frontend `src` 均无 split 维度
  （仅 `String.split()` 噪音），PreviewJobHandler/asset_preview 投影未含 split。
  **无需再等 REQ/PAGE 评审**——决策已存在，实施卡建议 `TASK-P0BR-078`（Wave δ，契约+迁移+Job+前端）。
- **G6 已闭合（复证）**：`AssetDetailPage` 版本切换器 `?version=` URL 联动 + 版本化 Query Key +
  gitTag/sourceCommit/manifestDigest 渲染在位（L133-181、L548-561），与 §4.1 结论一致。
- **G7 已闭合（复证）**：`publishVersion` 全前端零命中；`features/version/api.ts` 导出 11 函数
  均有消费方（无死代码残留）。
- **G9 大幅修订——`aih` CLI 记录过时**：`scripts/aih/aih`（v0.2.0，bash 实现）已存在，
  search/inspect/pull/create/push/status/versions/resume/verify 九动作与退出码表 0-7
  对齐 REQ-DST-CLI-001；`verify-journey.sh` journey_v26 以该路径消费（栈运行时 PASS）。
  剩余缺口为 E4 验收回写，非缺位。G9 真缺位收敛为：门户首页 #1、组织/用户主页 #9、
  全局搜索 #10、文档中心 #11、创空间 #8（非目标声明）——仍属产品决策门。
- **验收口径补充（verify-journey 双口径事实）**：`verify-journey.sh` 覆盖 **18 旅程**
  （V05-V17、V19、V25-V28；074 实跑 PASS=18 SKIP=0 FAIL=0），为 PRD §13.2 行为旅程权威口径；
  `verify-journey.ps1` 仅 V05/V08/V12/V14 轻量子集（Windows 烟测定位）。ps1 不得表述为
  「与 .sh 结构对齐」的全量口径；journey.sh 未含 V18/V20-V24 由 verify.sh 编号域承接
  （两脚本编号语义不同，verify-case-mapping.md 已有映射）。
- **D1/D2 复核（implementation-status 缺陷清单）**：D1 资产创建鉴权——
  `AssetApplicationService` L134 `requirePermission(Permissions.ASSET_CREATE)` **已闭合**；
  D2 changeOwnPassword 越权——该端点已不存在，现为 `/me/password` 自助改密
  （契约 L197 + AuthController `context.principalId()`）**已闭合**。两文档描述滞后。

### 任务清单（10-09 复订，按依赖与门禁排序）

| # | 事项 | 前置门禁 | 建议载体 |
| --- | --- | --- | --- |
| 1 | 074/075 提交与 `review.accepted`；067/068/069/071/072/073 遗留人工验收 | 验证权威（人类） | G10 既有 |
| 2 | V26/V28 旅程验收回写（10-09 复订）：`verify-journey.sh` 全栈首跑实测 13 PASS/4 FAIL/1 SKIP（Run 20261009105006；V26 CLI 与 V28 本体 PASS，4 FAIL 均为探针作业被 reconcile 风暴饿死超时）——**CLI E4 补证走续用已授权 `TASK-P2-009`**（README/traceability 登记核对并入其收口）；**饿死/风暴/计数失真的基础设施修复另立 `TASK-P0BR-076`（DRAFT 待批准）**，其 18/18 复跑即本行 E4 刷新主证 | P2-009 已 READY；076 待批准人处置 | `TASK-P2-009` + `TASK-P0BR-076` |
| 3 | G1 文件内容浏览：契约新增版本文件树+单文件内容代理端点（权限过滤+防枚举+大小上限+审计），前端 Files Tree onSelect+内容面板 | 契约先行 + Task Card | `TASK-P0BR-077` |
| 4 | G5 Split/统计：preview 投影扩展 split 维度与基础统计（迁移 V44+、契约、Job、前端 Subset/Split 选择器） | DEC-015 已 ACCEPTED；迁移+契约 Task Card | `TASK-P0BR-078` |
| 5 | G3/G4 订阅与收藏：先补 REQ/PAGE 增补（含收藏语义、退订幂等、通知联动），人类评审后开工 | REQ/PAGE 评审（人类） | Wave γ 新卡 |
| 6 | G9 余项：文档中心/组织主页/门户首页/全局搜索逐项 REQ/PAGE/ADR 立项；#8 非目标声明 | 产品决策（人类） | Wave ε |
| 7 | iamLastShapeProbe 僵尸守卫加严裁定 | 批准人裁定 | 075 移交 |
| 8 | E5 P5003 破坏性恢复演练窗口 | 用户窗口 | G10 既有 |

## 5. 本轮实施范围声明

按用户目标「整理成迭代计划进行实施」：本文档即迭代计划产出。
实施自 **Wave α** 起（G7/G8 为已实证的工程缺陷与文档矛盾，属 DoD「无 placeholder / 文档同步」直接范围）；
Wave β 及之后涉及契约与迁移新增，须先补对应 Task Card 并经人类授权，不在本轮 AI 会话内自批。

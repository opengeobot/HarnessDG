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

### 4.4 复核与建卡（2026-10-10，HEAD `a022796`）

- **清单 #1 提交部分闭合**：074/075/076 三卡及其 EVD、JobWorker/Reconciler/verify-journey 修复已随
  `a022796` 提交，工作树干净；`review.accepted` 人工验收仍待验证权威（G10 既有，不因提交视为验收）。
- **差异以当前 worktree 复取证**：G1 开放（`AssetDetailPage.tsx` L448-453 `selectable={false}`；契约
  `listArtifacts` 无内容端点；迭代计划旧引用 `frontend/src/pages/...` 路径失实，实际
  `frontend/src/features/assets/`）；G2 已由 068 闭合（`renderPreviewArtifact` 在码）；G4 开放
  （favorite 全仓零命中，REQ/PAGE 人类评审门禁在前，清单 #5 不变）；G5 开放（backend/frontend 无
  split 维度、`asset_preview` 无统计列、迁移最高 V43）；076 M5 dedupeKey 证伪结论维持。
- **清单 #3/#4 已立卡（DRAFT 待批准，AI 起草不覆盖，DEC-009）**：
  `TASK-P0BR-077`（G1 文件内容浏览：契约新增 `getArtifactContent` 内容代理端点，校验链同族复用 068；
  text 白名单 + `maxTextBytes` + 404 防枚举；**审计口径按 068 批准人先例为「预览族无审计」，
  与清单 #3 原文「+审计」的差异列入卡 §9 批准人裁定点**）；
  `TASK-P0BR-078`（G5 Split/统计：前向迁移 `V44__preview_split_stats.sql` 扩 asset_preview 可空 JSONB，
  Job 侧 Split 识别与确定性上限统计，getPreview 契约增量，前端 Split 选择器+统计卡片）。
  两卡均已登记 work-breakdown §4.1；validate-task-card DRAFT 轮各余 5 项审批位设计内失败
  （与 074/075/076 DRAFT 轮口径一致）；validate-spec PASS（148 docs / 738 stable ID）。
- 两卡不合并：各交付一个可独立验收纵向结果（068 先例）；078 依赖的 V44 号位已由 076「禁占 V44」
  约束让出，无冲突。

### 4.5 Wave γ 规格增补与立卡（2026-10-10，清单 #5 G3/G4，HEAD `a022796` + 077/078 未提交改动）

- **G3/G4 差距以当前 worktree 复取证**：G4（收藏）全仓零命中——无 `asset_favorite` 表、无端点、无页面；
  G3（订阅）比迭代计划旧述**更接近闭合**：`asset_subscription` 表（V15）、讨论域布尔端点、
  `VERSION_PUBLISHED` fan-out（`resolveSubscribers`）、物理回收级联删除
  （`PhysicalRecycleJobHandler` L177）均已存在，剩余缺口=独立资源面（`PUT/DELETE` 独立端点、
  `GET /subscriptions` 我的订阅列表、`/subscriptions` 页面）。
- **规格增补先于立卡（清单 #5 门禁「先补 REQ/PAGE」）**：`dataset-experience.md` 新增
  `REQ-DST-FAV-001`（私有书签语义划界：不进排序/Facet/推荐、聚合计数默认不开放、不新增
  `asset:favorite` 权限位——三项留批准人裁定）与 `REQ-DST-SUB-001`（含现状基线三条 + 兼容别名
  委托同一应用服务禁止两套状态）；`page-catalog.md` 新增 `PAGE-AST-007`（`/favorites`）与
  `PAGE-COM-004`（`/subscriptions`）；`dataset-agent-exit-catalog.md` §3b 新增
  `AC-DST-FAV-001..004`、`AC-DST-SUB-001..003`。全部 `status: PROPOSED`（草案，待人类评审）。
  validate-spec **PASS（148 docs / 751 stable IDs）**。取证教训：PAGE 定义行 ID 单元格必须纯净
  （反引号紧跟 `|`），注记写进 ID 单元格即 unknown ID FAIL。
- **立卡（DRAFT 待批准，AI 起草不自批，DEC-009）**：`TASK-P0BR-079`（G4 收藏：前向迁移
  `V45__asset_favorite.sql` + `PUT/DELETE /assets/{id}/favorite` 幂等收敛 + `GET /favorites`
  谓词下推 + 回收序列插入 + 前端收藏按钮与 `/favorites` 页）；`TASK-P0BR-080`（G3 订阅独立资源：
  **零迁移**复用 V15 表 + `com/aihub/asset/subscription` 新包 + 讨论端点兼容别名委托 + fan-out
  实时授权过滤 + `/subscriptions` 页）。两卡均已登记 work-breakdown §4.1；**串行关系：079 先、
  080 后**（共享 AssetDetailPage/i18n/e2e/契约/routes/navigation 面，且 077/078 未提交产物同面交叠）。
- **DRAFT 轮 card 校验**：两卡各 **FAILED 6 项，全部为设计内门禁语义**（status 非 READY /
  implementationAuthorized / stageGatePassed / approvedBy 占位 / approvedAt 占位 /
  Requirement is not READY: REQ-DST-FAV-001 或 REQ-DST-SUB-001——REQ 现 PROPOSED 正是
  「人类评审前不得实施」的机器表达）。首跑曾各现 13 项：`IDEMPOTENCY` 条目含 SQL 省略号
  触发 Test-Placeholder（去省略号修文案）、`preExistingDirtyPaths` 误预填无摘要格式路径
  （改 `[]`，沿 077 先例——相关路径均在本卡 allowedPaths 内无需豁免）；修后余 6 项设计内。
  对照历史轮失败数：070=5、071=6、072=3、073=4、074/075/076=4、077/078=5、079/080=6
  （+1 为 PROPOSED REQ 引用项，非缺陷）。
- **提请批准人（本轮全部产物待人类处置，AI 不自批、未提交）**：
  ① 评审 `REQ-DST-FAV-001`（裁定：权限位、聚合计数）与 `REQ-DST-SUB-001`（裁定：列表最近通知
  时间字段）及配套 PAGE/AC，置 READY；② 批准 `TASK-P0BR-079`/`TASK-P0BR-080`（回写审批位，
  生效前置含 077/078 收口合入）；③ 此前未裁定项继续挂账：078 allowedPaths enumeration 缺口、
  077 application.yml 文述张力、脏目录（build-logs/playwright-report/test-results）.gitignore
  处置、validate-task-card.ps1 dotall 正则缺陷、077/078 提交与 `review.accepted`、
  iamLastShapeProbe 加严、E5 演练窗口。

### 4.6 077/078 交接验收包刷新复跑（2026-10-10，批准人处置前的无回归佐证）

批准人对 §4.5 三项提请暂无处置（HEAD 仍 `a022796`、079/080 仍 DRAFT、077/078 未提交）。
本轮在既有 077/078 合并工作树之上刷新交接验收包，全部真实栈复跑（7 容器 Up，
backend/postgres/minio/gitea healthy）：

- `deploy/compose/scripts/verify.sh`：V01–V28 全 37 项 PASS（含 V05c–V05j、V12–V28）。
- `deploy/compose/scripts/verify-journey.sh`：18/18 PASS、FAIL=0、SKIP=0，
  Run ID `20261010183139`；V06 `repo=aihub-svc/…` 证明 076 M2 物证保持。
- frontend 全量 Playwright：126/126 passed（3.0m，0 failed / 0 skipped）。
  凭据按 CI 口径（ci.yml L287-294）自 `deploy/compose/.env` 的
  `AIHUB_BOOTSTRAP_ADMIN_*` 注入 `E2E_ADMIN_USERNAME/PASSWORD`，值不落盘不落日志。
  首次裸跑 126 skipped 系缺凭据显式 skip（fail-closed 设计生效），注入后全量执行。
  用例数口径：073 轮基线 118 + 077/078 各 +4 spec = 126，与预期完全吻合。
- 契约层同步复核（2026-10-10 增）：`aihub-v1.yaml` 已完整登记 077/078 实施面
  （L3011 内容代理、L3228/L3254/L3293 previews 端点、L5056-5072 splits 可选分量）；
  `redocly lint` PASS（0 error；3 warnings 均在既有区域——L2099 relations allOf、
  L4623 `TagReference`、L4873 `FacetDimension` 未引用组件，非本轮引入，契约改动需任务卡授权，
  列入挂账）；前端为手写类型（无 codegen），`ArtifactContentPanel`/`PreviewPanel` 消费字段与契约对齐。
- MCP/迁移号位复核（2026-10-10 增；2026-10-12 更正计数并复跑）：`contracts/mcp/tools.yaml` **11** 工具与
  `McpToolCatalog` 代码注册逐项一致（名称、read/manage/publish/delete 权限、write 标记，
  含 `asset_get_upload_status` read 权限+write:true 特殊口径），零漂移；
  （原文误记「12 工具」：`git show a022796:contracts/mcp/tools.yaml` 实为 11 个 `- name:` 项，本次以两侧计数自证更正。）
  迁移号位实测 V44 = `V44__preview_split_stats.sql`（078），确认 079 规划号位须顺延 V45+
  （079 卡已按 V45 登记，一致）；事件契约为统一信封 schema，审计事件枚举复用无漂移。
- 差距维持复核：`backend/src` 对 `favorite` 零命中（G4 开放、079 DRAFT 待批正确）；
  `frontend/src` 对 `redoc|swagger-ui` 零命中（G9 文档中心未落地、待人类产品决策）。

以上为 077/078 **IMPLEMENTED_UNVERIFIED 状态下的最新无回归佐证**，不替代批准人的
`-CheckCompletion` 人类验收与提交处置；079/080 及后续 Wave 仍阻塞于 §4.5 人类门禁。

### 4.7 TASK-P2-009 E4 复跑与 presign 下载票据双重缺陷（2026-10-12，HEAD `a022796`）

批准人对 §4.5/§4.6 提请仍未处置（HEAD 未变、079/080 仍 DRAFT、077/078 未提交）。
按清单 #2 走续已授权的 `TASK-P2-009`（READY / implementationAuthorized: true / E4），
在真实栈复跑 CLI E4 以验证 `7e53aac`（TASK-P0BR-070）是否闭合 10-09 轮的 presign 500。
结论：**AC-002/AC-003 仍 BLOCKED，AC-001/AC-004 回归 PASS**，EVD-P2009-001 已据实刷新（`status` 维持 PARTIAL）。

- **复跑口径**：栈 7 容器 Up/healthy，镜像 created `2026-10-10T08:50`（含 070）；
  fixture 为 PUBLISHED 版本 `ver_01m4fhzxqpdsc0wkhzdhfh5ywg`（1.0.1791523550，
  manifestDigest `5c2a53d70f28be`，3 工件，DVC 内容已物化）。
- **AC-001**：以 `aih dataset search --query …` 正确口径复跑，CLI 4 条与 REST 4 条集合相等 PASS。
- **AC-004**：无权限/未知资源三面 rc=2/2/3 全对、错误输出无凭据泄漏、pull 失败目录零文件残留 PASS。
- **AC-002/003**：三工件下载票据 `GET /versions/{vid}/download?artifactId={aid}` 全 500；
  `aih dataset pull` 与 `--resume` 均 rc=7；**同版本整版票据 200（method=GIT_DVC）——票据面不对称复现**，
  证明 070 只闭合了上传 part presign，未覆盖下载票据面。
- **探针缺陷甄别（未据以改产品码）**：首轮误把 `AIH_API_URL` 设为已含 `/api/v1` 的基址
  （CLI 在 `http_request` 内自动补 `API_PREFIX`，L54/L161），造成 `/api/v1/api/v1/...` 双重前缀伪 500；
  另把 search 关键字当位置参数传（`cmd_dataset_search` 的 `*) shift` 静默丢弃，仅 `--query` 生效），
  造成 20 vs 4 伪不等；残留判定误把目录计入。三项均为**测试脚本自身缺陷**，修正后 AC-001/004 转 PASS。
- **服务端根因（实证，两层，全在 P2-009 allowedPaths 之外的 backend 主码）**：
  1. *签发即失败*：`DownloadApplicationService` L96 → `MinioStorageAdapter` L209 抛
     `StorageException: failed to presign download`，Caused by
     `ConnectException: Failed to connect to localhost/…:9000`。容器内实测
     `wget localhost:9000` 连接失败、`wget minio:9000` 返回 `200 / Server: MinIO`；
     presign 客户端走 `externalEndpoint`（`AIHUB_MINIO_PUBLIC_ENDPOINT` ← `.env` `MINIO_PUBLIC_URL`）
     且**未设 region**，SDK 因此发起 `GetBucketLocation` 网络往返。同文件 `s3Client`/`s3Presigner`
     显式 `Region.US_EAST_1`（纯本地签名）故上传面不受影响——即不对称的直接来源。
  2. *键布局错配*：`resolveObjectKey` L142 返回 `assetId/versionId/path`，而 dvc-cache 实际物化布局为
     `PreviewJobHandler.dvcObjectKey(dvcHash, path)` = `hash[0:2]/hash[2:]`（068 预览链 3 处调用的唯一权威实现）；
     presign 不校验对象存在性，故修好第 1 层后票据仍指向不存在的键，CLI 落盘 SHA-256 校验仍会失败。
     `resolveBucket` L139 硬编码 `"dvc-cache"`（忽略 asset）需一并核对。
- **E4 补证 blocked 判定口径更新**：AC-002/003 的复验前提（票据可签发且指向真实对象）不成立，
  属**产品缺陷阻塞**而非环境不可用；在修复卡落地前，P2-009 不得转 VERIFIED，亦不得降级判据或标 SKIP。
- **复测遗留项**：`GET /teams`、`GET /principals` 仍 500（与前轮一致，归属待批准人裁定）
  ——**已由本续跑轮第⑨项查实推翻**：真实 `/system/*` 端点实测 200，500 系探针路径不存在
  被 catch-all 归一化所致；该 EVD 遗留项①的更正随 P2-009 提交轮回写，本段不改 EVD。
- **门禁复核**：`validate-spec.ps1` PASS（148 docs / 751 stable IDs）；
  `validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P2-009.md` **FAILED 1 项**
  ——`baseCommit does not match current HEAD (a0227961e085a6feae627bff7fcc276937b63b6b)`，
  系卡登记 baseCommit 陈旧（非本轮引入），据实登记待批准人处置，AI 不自回写审批位。
- **提请批准人（第八、九项）**：⑧ 授权新建 backend 修复卡（号位建议 `TASK-P0BR-081`）覆盖上述两层
  ——presign 端点/region 化以消除 `GetBucketLocation` 往返、`resolveObjectKey` 对齐 `dvcObjectKey`
  （或经端口层统一键解析，禁止复制漂移）+ `resolveBucket` 桶归属核对；⑨ `teams`/`principals` 500 归属裁定。
  079/080 及其余挂账继续待批。
- **第⑧项落卡（2026-10-12 续跑轮）**：`TASK-P0BR-081` 已由 AI 起草为 **DRAFT**（未自批、
  审批位空）并登记 `work-breakdown.md` §4.1。卡面把上述**三层**同做列为不可拆前置
  （region 化 presign 客户端 / `dvcObjectKey` 上提 `version.domain` 单一权威 /
  票据面 `objectExists` 守卫映射既有 `DVC_OBJECT_MISSING` 404），
  并以 `forbiddenPaths` 机器化固定「零迁移、零 OpenAPI diff、不触 deploy/frontend」。
  三项口径列为卡 §9 停止条件回请裁定：权限位 `asset:read`（实现）vs `asset:download`
  （REQ-DL-001）、`presignUpload`（无生产调用方）归属、`region` 是否可配。
  `preExistingDirtyPaths` **有意留空**：本卡不接受以 077/078 越界面摘要换取
  `-CheckChangedPaths` 放行，基线未清理时该检查必须失败。
  DRAFT 轮门禁实测：`validate-spec.ps1` PASS（148 docs / **752** stable IDs，+1 为 081 卡号）；
  `validate-task-card.ps1 -TaskPath …TASK-P0BR-081.md` FAILED **5 项**，
  全为设计内门禁语义（status/implementationAuthorized/stageGatePassed/approvedBy/approvedAt）；
  追加 `-CheckChangedPaths` 另得 34 项越界（077/078/079/080 未提交产物 +
  `build-logs`/`playwright-report`/`test-results` 非产品脏目录 + 本文档），
  系基线未清理的预期结果，转绿前须由批准人处置提交面。

- **第⑨项根因查实（2026-10-12 续跑轮，替换原「归属待裁定」口径）**：对 healthy Compose 栈
  （`aihub-backend-1`）做只读 E4 探针，登录取 JWT 后逐路径实测，结论与前轮登记**不一致**——
  ① `GET /api/v1/system/principals` → **200**（返回主体列表）；
  ② `GET /api/v1/system/organizations/1/teams` → **200**（`data: []`，非 500）；
  ③ `GET /api/v1/teams`、`GET /api/v1/principals` → 500，`GET /api/v1/zz-not-mapped`（任意杜撰路径）→ **同样 500**。
  后端日志给出机制证据：`GlobalExceptionHandler` 记 `Unhandled exception` +
  `org.springframework.web.servlet.resource.NoResourceFoundException: No static resource api/v1/...`。
  **裁定结论**：`/teams`、`/principals` **不存在**（契约 `aihub-v1.yaml` L219/L675 亦仅定义
  `/system/principals` 与 `/system/organizations/{organizationId}/teams`，与实现一致、无缺失端点）。
  前轮「500 遗留项」系**探针用错路径**所致，不是业务查询缺陷，**撤销其挂账**、无需新建业务卡。
- **第⑩项（由 ⑨ 查实暴露的差异，提请裁定后建卡；本轮修正前轮错误表述）**：未映射路径返回
  **500** 而非 **404**——`GlobalExceptionHandler.handleUnexpected(Exception.class)`（L55-68）catch-all 把
  `NoResourceFoundException` 归一化为 `INTERNAL_ERROR`。真正无争议的缺陷面是**可观测性污染**：
  每条扫描/拼错路径都以 `LOG.error` + `AlertLevel.CRITICAL`（`ErrorCode.INTERNAL_ERROR`，L246）
  计入错误率与告警，**外部探测可刷出伪 CRITICAL**。
  *前轮登记的技术错误已更正*：本项原写「映射既有 404/405 `ErrorCode`、零新增 ErrorCode」**不成立**——
  `ErrorCode.java` 中 **不存在通用 404/405 码**（404 全为领域码 `*_NOT_FOUND` / `DVC_OBJECT_MISSING` /
  `PREVIEW_SOURCE_UNAVAILABLE`；405 一个都没有，`PREVIEW_UNSUPPORTED_FORMAT` 是 415）。
  据此拆为两个裁定选项，AI 不自决：
  - **选项 A（补 404/405 语义）**：新增通用码（如 `RESOURCE_NOT_FOUND` / `METHOD_NOT_ALLOWED`）。
    代价 = 新枚举项 + `error-catalog.md` §2/§3 同步 + 前端 `zh.json`/`en.json` i18n 键；
    **OpenAPI 零 diff**（`ApiError.code` 为自由 string，`aihub-v1.yaml` L3735-3738 无 enum），**零迁移**。
  - **选项 B（不动语义、只降噪）**：保留 500，仅将 `NoResourceFoundException` 的日志级别由
    `error` 降为 `warn`/`debug` 并排除出 CRITICAL 告警计数。
  *须由批准人裁定的语义边界*：`REQ-COM-001`（`p0b-platform-services.md` L24）明文要求
  「**未知异常统一 INTERNAL_ERROR**，不暴露堆栈/SQL/Bucket/内部 URL」——因此「未映射路径 → 500」
  存在**设计内**解释。请裁定 `NoResourceFoundException`（路由未命中，属**已知**可预期异常）
  是否落入该条的「未知异常」口径；若裁定「设计内」，则本项关闭、无需建卡。
  因当前无批准卡覆盖 `bootstrap` 包，AI 不实施，仅登记待批。
- **第⑪项（error-catalog.md 与 `ErrorCode.java` 漂移，本轮已按治理口径直接同步）**：
  逐字段机器比对（同一 `HttpStatus/i18nKey/retryable/alertLevel` 四元组）显示
  `error-catalog.md` §2 只有 **68** 表行、§3 自述总数 **56**，而枚举真实 **76** 项——三重数字不一致。
  缺失 8 项：`ASSET_COORDINATE_INVALID`(400)、`ASSET_VERSION_SYNTAX_INVALID`(400)、
  `UPLOAD_SIZE_EXCEEDED`(400)、`RATE_LIMIT_EXCEEDED`(429 retryable=**true**)、
  `PREVIEW_NOT_FOUND`(404)、`PREVIEW_UNSUPPORTED_FORMAT`(415)、`PREVIEW_TOO_LARGE`(413)、
  `PREVIEW_SOURCE_UNAVAILABLE`(404)；**既有 68 行四元组零差异**（无映射级错误，纯漏登记）。
  处置依据：`error-catalog.md` §7 维护规则第 1 条「**权威源是 `ErrorCode.java`——本文档必须与枚举保持同步**」，
  同步方向为文档跟代码，属「检查项目与设计文档差异并迭代实施」直接范围；
  本次改动**仅新增/修订文档行与统计，未触碰任何 product code / contracts / Flyway / deployment / tests**，
  不触发 DEC-009 实施门禁，故由 AI 直接落盘、不回请批准。
  已同步内容：§2.4 补 `RATE_LIMIT_EXCEEDED`、§2.14 补 2 个 ASSET 码、§2.16 补 `UPLOAD_SIZE_EXCEEDED`、
  新增 §2.25 预览族 4 码；§3 统计改为 **总数 76 / 已收录 76**、`retryable=true` 由误记的 5 项改为真实
  **6** 项（补 `RATE_LIMIT_EXCEEDED`）、`WARN` 10→**9**、`INFO` 14→**13**、`NONE` 28→**50**，并加口径注脚。
  复验结果：比对脚本输出 `catalog rows: 76 | java: 76`、缺失 0、CATALOG-ONLY 0、重复行 0、字段差异 0。
- **第⑫项（由 ⑪ 派生的三方冲突，仅上报、不自决）**：`error-catalog.md` 数字修正后，暴露出三处
  规格与实际实现的不一致，**按 AGENTS.md「代码、契约、ADR、设计互相矛盾时必须上报冲突，
  不得静默择一」处理**，AI 未改任何判据文本：
  1. **前端 i18n 键完全缺失**：`frontend/src/shared/i18n/zh.json`、`en.json` 各 802 个键（本轮递归展平后
     独立复算：双向差集**皆空**，两文件键集合对称；PS 5.1 须 `Get-Content -Encoding UTF8` 否则中文 JSON
     被按 ANSI 误读致 `ConvertFrom-Json` 抛错），其中
     **`error.*` 命名空间键为 0 个**（仅 `networkError`/`previewImageError`/`errorCode` 等 UI 文案，
     非 `error.<domain>.<camelCase>` 契约键）；后端 `backend/src/main/resources` 亦**无 `messages*.properties`**。
     → 即 `TASK-P0BR-041` 的 i18n 错误消息链路当前**两端都未落地**（该卡 E4 notProven 与此一致）。
     实现侧真实机制已取证：后端 `PrincipalContextFilter` 只把 `Accept-Language` 解析后写入
     **MDC 供日志使用**（L44/L138），**不做 message 本地化**；前端 `shared/api/client.ts` L128 用
     `body.i18nKey ?? 'error.common.unknown'`、L142 用 `'error.common.network'`，即**本地化在客户端**。
     `error-catalog.md` §4 原文却写「`message`：按请求 `Accept-Language` 解析 i18nKey 后的本地化文本」，
     并给了含 `timestamp`、缺 `requestId`/`traceId` 的示例，同时把 schema 误称为 `ErrorResponse`。
     **本轮已按实现与契约同步 §4**（与 ⑪ 同口径：文档跟契约/代码、纯文档改动、不触发 DEC-009 门禁）：
     schema 名改 `ApiError`（`components.schemas.ApiError` L3725，required
     `code/message/retryable/requestId/traceId`，**无 `timestamp`**；与 record
     `shared/api/ApiError.java` L25-32 完全一致）、补齐 `i18nKey`/`details`/`requestId`/`traceId` 示例、
     并加注说明服务端不按 `Accept-Language` 渲染 `message`。
     → 该小条的**文档侧已闭环**，剩余待裁定的只有 041 的**判据数字与 i18n 落地范围**（见小条 2）。
  2. **判据数字陈旧**：`TASK-P0BR-041.md` L65 与 `work-breakdown.md` L144 均写「**全部 56 个** error.* key」，
     权威枚举实为 **76** 项。该数字属**验收判据**：改成 76 是**扩大**验证范围（安全方向），
     但 041 是已存在 Task Card、其正文/判据变更须由批准人处置，AI 不自改；
     `decision-log.md` L452 的「56 个」是**历史时点记录**（记录当时建 catalog 的事实），按不可回写历史原则**保持原样**。
  3. **⑩ 选项 A 的连带面**：若裁定新增通用 404/405 码，则本项的 i18n 缺口会同步放大（键数 76→78），
     建议 ⑩ 与 041 一并裁定实施顺序。
  → 请批准人裁定：(a) `TASK-P0BR-041` 判据数字由「56 个」更正为 **76** 个（以及 ⑩ 选项 A 是否再 +2），
  并明确 i18n 落地范围（仅前端 `zh.json`/`en.json` 补 `error.*` 键，还是同时提供后端
  `messages_*.properties`；已取证实现走**纯前端按 `code`/`i18nKey` 映射**路线，见上小条 1）。
- **第⑬项（契约 ↔ 实现路由全量双向比对，2026-10-12 续跑轮；结论：唯一差异为 MCP 路径族，属三方冲突上报）**：
  比对口径（复现须知，四项解析缺陷曾产生假差集，务必按此口径重跑）：
  1. **契约侧必须取两份 OpenAPI 文件的并集**——`/agent/*` 九个操作定义在独立文件
     `contracts/openapi/aihub-agent-v1.yaml`（L20-22 `servers: url: /api/v1`，L32/79/102/136/159/188/228/266/320）。
     只比对 `aihub-v1.yaml` 会把 9 条 Agent 端点**误判为契约缺失**（前轮即犯，本轮证伪）。
  2. 后端侧须先收集每个 `*Controller.java` 的 `static final String` 常量表再解析路径（`PreviewController`
     用 `PREVIEWS_BASE` 常量拼接），且方法级路径以 `/` 开头时**不得丢弃类级 base**
     （曾误写 `if ($sub.StartsWith('/')) { $sub }`，导致 base 丢失、出现 `get /{}` 等垃圾项）。
  3. 契约存在**单引号包裹且含冒号**的路径键（`'/system/jobs/{jobId}:retry'`、`'…:action'` 形态），
     正则须同时支持 `'…'` 与 `/…` 两种键形，且路径体不能按 `:` 截断。
  4. 变量段统一归一为 `{}`、折叠重复斜杠、去尾斜杠后再比集合。
  实测结果：`backend=144`、`contract=144`（并集）**计数相等**；独立交叉验证一致——
  契约 `operationId` 数 `aihub-v1.yaml` **135** + `aihub-agent-v1.yaml` **9** = **144**，
  后端 37 个 `*Controller.java` 中 `@(Get|Post|Put|Patch|Delete)Mapping` 注解 **144** 次
  （AssetController 12、AgentController 9、CommentController 8、VersionController 8、SystemUserController 7、
  UploadController 7、AuthController 7、TeamController 7、RoleController 5、SystemAgentController 5、
  OrganizationController 5、TagController 5、PreviewController 4 …）。
  双向差集**各仅 1 项且互为同一事实**：契约 `post /api/v1/mcp` ↔ 实现 `post /mcp`（`McpController.java`），**1:1，非解析假象**。
  *方法学留痕*：曾为把差集压成 0 而给脚本加「`/mcp` 不加 `servers` 前缀」的特例——该特例无证据支撑，
  属**制造一致性而非发现差异**，已撤销。**规则：任何让结果变干净的解析特例，必须单独取证其成立依据。**
  **MCP 冲突证据链（静态即充分）**：① `McpController.java` L43 `@RequestMapping("/mcp")` + L80 `@PostMapping`
  → 实际路由 `POST /mcp`；② `application.yml` L74-75 仅 `server.port`，**无 `server.servlet.context-path`**；
  ③ 全仓**无** `configurePathMatch`/`addPathPrefix`/`setUrlPathHelper`（`IdentityWebConfiguration` 只注册拦截器）；
  ④ `nginx.conf` L95-104 `location /mcp { proxy_pass http://$aihub_backend; proxy_buffering off; }` **无 rewrite**
  （对照 L122-123 `location /git/ { rewrite ^/git/(.*)$ /$1 break; }`）、L60-67 `location /api/` 亦无 rewrite → 转发不改写 URI；
  ⑤ 契约 `aihub-v1.yaml` L3647 `  /mcp:` 是**顶层 paths 键**，继承 L22 全局 `servers: /api/v1`
  → **文档化的 `POST /api/v1/mcp` 实际 404**。
  **超出文档漂移的功能性缺陷**：`AgentBundleView.java` L31 后端**自行**向 Agent 下发
  `normalized + "/api/v1/mcp"` 作为 `mcpEndpointUrl`（前端 `IntegrationsPage.test.tsx` L10 断言同值）
  → 照官方接入包配置的 Agent 客户端会连到不存在的路由。而设计/运行手册一律写 `/mcp`
  （`p4-agent-integration.md` L27/L308、`AC-P4-MCP-001`、`docs/runbooks/compose.md` L33、
  `implementation-status` L162、`aihub-agent-integration/README.md` L20），
  `verify-journey.ps1` L144-146 亦用 `-Path "/mcp"` 且带 JWT **实测可达**。
  → 判定为「**契约 L3647 落点 + 服务端下发值**」两处偏离设计，倾向上报后按**契约侧更正**裁定；
  AI 不改任一禁改面，候选方案与代价：
  - **方案 A（推荐，改契约）**：`aihub-v1.yaml` 把 MCP 操作移出 `/api/v1` 继承（独立 path 键 + 该操作级 `servers: [{url: /}]`）。
    代价 = 契约文件改动 + breaking-change 检查复跑；**零后端改动、零 nginx 改动**、与设计/AC/运行手册/journey 脚本一致。
  - **方案 B（改实现）**：`McpController` 改挂 `/api/v1/mcp` + `nginx.conf` 加 rewrite。
    代价 = 后端主码 + 部署面 + 设计文档 `AC-P4-MCP-001`/runbook/README 全量改写 + journey 脚本改路径，改动面最大。
  - **方案 C（最小止血）**：仅把 `AgentBundleView` L31 下发值改为 `normalized + "/mcp"`，契约漂移另行挂账。
    代价 = 后端主码一行 + `IntegrationsPage.test.tsx` 断言同步；**不消除契约 404**，Agent 开发者按文档接入仍失败。
- **第⑭项（`verify.sh` 存在性探针假阳性——E4 证据完整性缺陷，提请裁定，AI 不改判据）**：
  本轮首次对 healthy 栈做**匿名** curl 实证：`/mcp` → **401**、`/api/v1/mcp` → **401**、
  杜撰路径 `/api/v1/zz-nope` → **401**（`SecurityConfiguration.java` L74-82 仅对 login/token/agent-token/refresh、
  actuator health、`/api/v1/webhooks/gitea`、swagger `permitAll()`，其余 `anyRequest().authenticated()`，
  **认证先于路由匹配**）。因此：
  - `deploy/compose/scripts/verify.sh` L30 `BASE_URL="http://localhost:8080"`（绕开 nginx）；
    L1192-1208 `check_mcp_endpoint()`（**V19**）打 `${BASE_URL}/api/v1/mcp`，body 无 `serverInfo` 时落入
    L1205-1206 回退判据 `[ "$code" = "401" ] || [ "$code" = "200" ] → return 0`
    → **该判据对任意路径恒真，V19 从未真正验证 MCP 端点可用性**（含注释「可能返回 401（需认证），也视为端点可用」）。
  - 同类弱判据另有两处：L1185-1186（**V18** webhook 接受 `401/400/200`）、L1316-1318（**V25** agent token 接受 `401|400|422`）
    ——均**无法区分「路由存在但需认证」与「路由不存在」**。
    扫描命中的其余 401 行（L188、L374/L399/L400、L477、L549、L910、L1031、L1046、L1297、L1299、L1307、L1326、L1334）
    是**合法的匿名拒绝负向断言**，不在本项范围。
  - 影响面：AGENTS.md「因 Docker 不可用而跳过的测试不是通过的集成测试」+ 本轮实证——
    **凡以 V19/V18/V25 为 PASS 依据的 EVD 记录，其 E4 证据强度不成立**，须按 completion audit
    「弱证据/间接证据 = 未完成」重判。`docs/runbooks/verify-case-mapping.md` L37 记录 V19 为
    「MCP `/api/v1/mcp` initialize 可用」、L44 记录 V26 为「MCP `/mcp` 根路径默认拒绝」——
    **映射文档自身同时使用两种路径写法**，与 ⑬ 同源。
  → 请批准人裁定：(a) 是否授权新建验证脚本整改卡（判据改为「匿名 401 **且** 认证态 200 + 响应体字段断言」双条件，
  并把 V19 目标路径与 ⑬ 裁定结果对齐）；(b) 引用 V19/V18/V25 的 EVD 是否需回溯更正为 notProven。
  **验证脚本判据属验收面，AI 不得自行改写以转绿**，故本项只登记、不动码。
- **第⑮项（E4 证据基线不可复现 + 用例计数口径矛盾，2026-10-12 续跑轮，仅登记）**：
  **A. 运行栈验证的是含未提交迁移的工作树，而非 HEAD。** 本轮直连 healthy 栈的 Postgres 实测
  `select version,success from flyway_schema_history order by installed_rank desc limit 6` →
  **`44 | preview split stats | t`**（V44 已应用且成功）。但
  ① `git ls-files backend/src/main/resources/db/migration` 最高号位是 **43**；
  ② `git status --porcelain --untracked-files=all -- …/db/migration` → `?? V44__preview_split_stats.sql`
  （TASK-P0BR-078 产物，**至今未提交**）；③ HEAD 仍 `a022796`（批准人零处置）。
  → 结论：当前承载「V01–V28 全 PASS」「journey 18/18 PASS」「Playwright 126/126」的数据库 schema
  **不是 HEAD 的 schema**，而是 HEAD + 未提交 077/078/079/080 产物的合并工作树。
  任何人 `git clean -fd` 后按 §4.6 复跑，得到的是 V43 schema 上的另一栈；
  AGENTS.md「`baseCommit` 必须是真实 Commit + 干净 Commit」的完成判据因此**无法由这些记录满足**。
  *缓解事实*（据实记录，非开脱）：V44 是 `asset_preview` 新增**可空** JSONB 列（078 卡 §140/§192 自述前向、仅扩可空列），
  向后兼容，故 HEAD 单独 checkout 起栈不会因缺列失败——但这不改变「证据绑定的是未提交状态」这一事实。
  → 请批准人裁定：是否要求 077/078 先提交、再以新 HEAD 复跑 E4 并刷新 EVD（AI 不代提交、不自批）。
  **B. V04「数据库迁移完整性」判据无判别力**（与 ⑭ 同类弱判据）：`verify.sh` L836-846 仅断言
  `count(*) where success = true` **≥ 33** 加 19 张关键表存在，**不校验迁移 head 版本、不校验
  代码期望 schema 与库实际 schema 的一致性**（`baseline-on-migrate: true`、`clean-disabled: true` 见
  `application.yml` L26-30/L206-208）→ 当前栈停在 V41 或 V44 都判 PASS。
  本项属验收面判据，**AI 不改**，与 ⑭ 一并提请裁定整改口径。
  **C. V01–V28 断言项数文档口径矛盾（37 vs 38）**：现存物证 `.trae/evidence/verify-sh-e4-20260923.log`
  实为 **37** 行 `[V..] … PASS`（V01–V05、V05b、V06–V11、V05c–V05j、V12–V28 = 5+1+6+8+17），
  `verify.sh` 的 `step` 调用亦恰为 **37** 处（L1051-1366）。
  而 `implementation-status-2026-07-10.md` L302 写「共 **38** 项 PASS」、`test-report-2026-09-24.md` L36 写
  「**38** 断言行」，本计划 §4.6 L246 写「全 **37** 项 PASS」——三处文档两种口径并存。
  更正方向是**收紧**（38→37）且与物证一致，但前两处均为**带日期的取证快照**，
  按 ⑫ 小条 2 已确立的「历史时点记录不回写」原则，AI **未改这两处数字**，只登记矛盾，
  请批准人裁定是否回溯更正，或改由后续轮次以新物证覆盖。
  **D. E4 物证指针指向 Git 忽略路径**：L302 的完整日志路径 `.trae/evidence/…` 命中 `.gitignore:2`（`.trae/`），
  即**验收物证不在版本控制内**——评审者 checkout 仓库后无法取得该日志，`-CheckCompletion` 无从核对。
  `build-logs/`（`test-report-2026-09-24.md` L69 自述「未纳入 Git」）同问题。
  本项为可追溯性缺陷，提请裁定处置方式（物证入库 vs 改指针指向受控存储）；AI 不擅自提交 `.trae/` 内容。

- **第⑯项（page-catalog ↔ 前端路由表双向差集，2026-10-12 续跑轮；24 条差异按四类归档，两侧均不擅改）**：

**方法学**：以 `docs/ai-spec/04-ui/page-catalog.md` 的 `PAGE-*` 条目（独立复算
`Select-String 'PAGE-[A-Z]+-\d+' -AllMatches | Sort-Object -Unique` → **42** 个去重 ID，非 43；
含 AUTH 2、COM 4、ADM 16、AST 7、UPL 2、VER 3、DST 2、REV 2、INT 4，减去 403/404（`PAGE-COM-001/002`）两条非路由页 = 40 条可映射路由）
为规范侧，以 `frontend/src/app/router/routes.tsx` L45-96 的全部路由（顶层 `/login`、`/` + 24 条业务路由
+ `admin/*` 20 条 + `*` NotFound）为实现侧，做双向差集；每条「缺失」逐一回源核实**真实挂载方式**
（独立路由 / 内嵌 Tab / query 参数 / 单页选择态 / 完全未实现），不得凭路由名直接判缺失。
**page-catalog（04-ui 规范，状态 READY）与 routes.tsx（产品码）本轮均不擅自修改**——规范向实现靠拢
或实现向规范靠拢都需批准人裁定。差集按性质分四类：

**A 类·导航形态漂移（功能已实现，但形态与规范「独立路由」写法不符 → 可文档同步项，非缺陷）**
1. `PAGE-AST-003` `/assets/:id/overview` ↔ 实现 `/assets/:assetId` + `?tab=overview`
   （`AssetDetailPage` L81/171/507，8-Tab 外壳，默认 overview）。规范写独立子路由，实现用 query tab。
2. `PAGE-VER-003` `/assets/:id/versions/:version/files` ↔ 实现同页 `?tab=files` + `ArtifactContentPanel`
   （`AssetDetailPage` L42/461/509；077 产物，IMPLEMENTED_UNVERIFIED）。
3. `PAGE-DST-001` `/assets/:id/versions/:version/preview` ↔ 实现同页 `?tab=preview` + `PreviewPanel`
   （L45/498/510；078 产物；§2 F1-F6 已记「`PreviewPanel` 双挂载」）。
4. `PAGE-DST-002` `/assets/:id/discussions` ↔ 实现同页 `?tab=discussions` + `DiscussionPanel`（L43/511）。
5. `PAGE-UPL-001` `/uploads` ↔ 实现 `/upload`（**单数**，`routes.tsx` L64；nav L26 同名单数）。
6. `PAGE-UPL-002` `/uploads/:sessionId` ↔ 实现 `/upload?sessionId=`（`UploadPage` L173
   `searchParams.get('sessionId')` + L70-72 测试守护）。规范路径段，实现 query 参数。
7. `PAGE-REV-002` `/reviews/:requestId` ↔ 实现 `/review` **单页左队列右详情**（`ReviewPage` L273
   `key={r.requestId}` + L313 `selectedRequest`），无独立可分享详情路由。
8. `PAGE-INT-004` `/integrations/clients` ↔ 实现顶层 `/access`（`AccessPage`，`token:create`）。
9. `PAGE-INT-002/003` `/integrations/agents/new`、`/integrations/agents/:id` ↔ 实现无 integrations 子路由
   （`features/integrations` 目录仅 `IntegrationsPage.tsx`）；Agent 注册/详情实际挂在 `/admin/agents`（`AgentsPage`）。
10. `PAGE-ADM-005` `/admin/organizations/:id/projects` ↔ 实现 `/admin/projects`（`ProjectsPage`，无组织上下文段）。

**B 类·实现有路由、page-catalog 无对应条目（目录缺项，须判「越界实现」还是「规范漏记」）**
1. `/version`（`VersionPage`，`asset:read`，nav「版本中心」L24）—— catalog 全表无此页。
2. `/admin/teams`（顶层，`TeamsPage` L76）—— catalog 仅有 `PAGE-ADM-004` `/admin/organizations/:id/teams`
   （实现两处都挂：L76 顶层 + L77 org 内，后者对齐 ADM-004，前者为 catalog 缺项）。
3. `/admin/idempotency`（`IdempotencyPage` L87，`job:read`）—— catalog 无。
4. `/admin/alerts`（`AlertsPage` L88，`system:observe`）—— catalog 无。
   → B 类四条是「实现跑在规范前面」，与 §3 G9「目标态缺位需先补 REQ/PAGE/ADR」相反方向；
   按门禁**不得默默保留无规范依据的产品路由**，提请裁定：补 `PAGE-*` 条目，还是收敛实现。

**C 类·权限位口径漂移（catalog `Permission` ↔ 路由 `requiredScopes`）**
（前端 `RouteGuard` 用粗粒度 Scope 做体验门，真实鉴权在后端 `requirePermission`；但 catalog 明列每条页面
`Permission`，口径不一致仍是需在规范层澄清的漂移，尤其 catalog L35 自己已承认权限表存在缺口。）
1. `PAGE-AST-004` `asset:update/delete` ↔ 路由 `assets/:assetId/settings` = `asset:manage`（L58）；
   后端 `Permissions` 同时存在 `ASSET_UPDATE/ASSET_DELETE/ASSET_MANAGE`（L47/53/67），三选一口径未定。
2. `PAGE-UPL-001` `asset:upload` ↔ 路由 `upload` = `asset:manage`（L64）；后端有独立 `ASSET_UPLOAD`（L55）。
3. `PAGE-REV-001` `asset:review` ↔ 路由 `review` = `asset:manage`（L65）；后端有独立 `ASSET_REVIEW`（L61），
   且 `NotificationRecipientResolver` L43 按 `asset:review` 解析审核人 → 路由守卫与后端真实权限点不同名。
4. `PAGE-ADM-003` `organization:manage` ↔ 路由 `organizations` = `project:view`（L75，**读级**）；
   catalog 写 manage、路由守 project:view，落差最大。
5. `PAGE-ADM-002` `agent:register/authorize` ↔ 路由 `agents` = `authorization:read`（L74）。
6. `PAGE-ADM-015` catalog 标「**待确认管理权限**」——**实现已定稿**：后端
   `NotificationAdminController` L47/54/62/69 全部 `requirePermission(SYSTEM_OBSERVE)`，前端
   `NotificationsPage` L31-32 `canObserveSystem = hasScope('system:observe')` 门控 Admin Tab → catalog
   「待确认」是**文档滞后**（`notification:read` 管本人 Tab、`system:observe` 管 Outbox/Delivery Tab），属可文档同步候选。

**D 类·规范目标态确实未实现（归 §3 开放差距，非文档能抹平）**
1. `PAGE-COM-004` `/subscriptions`（catalog 明文「草案，待人类评审」）—— 前端零独立接线；
   `asset_subscription` 仅承载**讨论**订阅（`DiscussionPanel` 内嵌，G3）。→ 属 §3 G3。
2. `PAGE-AST-007` `/favorites`（catalog 明文「草案，待人类评审」）—— **全仓 favorite/bookmark 零命中**
   （`features/**` grep `favorite` = No matches；i18n `zh.json` grep `收藏` = 0 键）；无表、无端点、无 UI。→ 属 §3 G4。
3. `PAGE-COM-003` `/notifications`（顶层）↔ 实现本人通知挂在 `/admin/notifications` Tab 1（`listNotifications`
   → `GET /system/notifications`）。功能存在但入口在 admin 下、非顶层，介于 A 类与 G 系列之间——提请裁定是否补顶层入口。

**小结**：A 类 10 条属「已实现、形态不符」（首选文档同步）；B 类 4 条属「实现越规范」（须补规范或收敛实现）；
C 类 6 条属「权限口径漂移」（含 1 条纯文档滞后、5 条需澄清口径）；D 类 3 条归 §3 G3/G4/开放差距。
**补充（2026-10-12 续跑轮取证）·page-catalog L38 纯文档滞后**：该句自述「当前路由缺少
role-bindings/resource-acls/teams 的独立可发现入口」，但实现早已齐备——`routes.tsx` L76/80/81
挂 `/admin/teams`、`/admin/role-bindings`（`RoleBindingsPage`）、`/admin/resource-acls`
（`ResourceAclsPage`），且 `app/layout/navigation.ts` L37/40/41 三条均为**一级导航项**
（`nav.teamManagement`/`nav.roleBindings`/`nav.resourceAcls`）→ 属可文档同步候选，
并入 A 类（规范向实现同步），同样待批准人确认后执行。
**总计 24 条 page-catalog ↔ 路由差异**（A 类 10 + 补充 1 + B 类 4 + C 类 6 + D 类 3；补充段已并入 A 类口径，
标题原写「23 条」系并入前口径，2026-10-12 复算更正），与前序 ①—⑮ 一并不在本轮 AI 自决实施范围内。

- **第⑰项（前端 API 调用路径 ↔ OpenAPI 契约路径双向差集，2026-10-12 续跑轮；FE 越契约 0 条、契约未接线 25 条按四类归档，两侧均不擅改）**：

**方法学**：调用侧采集 `frontend/src/**`（排除 `*.test.*`）中三类真实出口——
① `apiClient.{get|post|put|patch|delete|getBlob}`（方法面穷尽自 `shared/api/client.ts` L203-218）；
② `ControlledSelect` 的 `apiUrl=` 属性（组件内 L41 固定走 `apiClient.get`，共 28 处使用）；
③ `UploadPage` L158-161 的**变量路径**（正则不可见，手工补录 2 条）→ 归一去重 **126** 条。
契约侧采集 `contracts/openapi/aihub-v1.yaml` + `aihub-agent-v1.yaml` 中**仅顶层 `paths:` 块内**的
路径键 + 4 空格 HTTP 方法行 → **144** 条操作，与本计划 ⑬ 独立复算的 144 **完全吻合**（互为复验）。
两侧先剥离 `/api/v1` 前缀（`client.ts` L27 `API_BASE_URL` ↔ 契约 `servers`），再做**模板段级实例化匹配**
（段数相等、方法相等、契约 `{param}` 段通配），而非字符串全等——否则前端传字面量
`/system/dictionaries/asset_stage/items` 会被误判为契约外调用。

**解析伪影清单（首轮曾产出 3 条假 FE-only，全部回源证伪，登记以免后续复用错误判据）**：
1. 契约键含**内部冒号动作段**（`/system/alerts/{alertId}:acknowledge` L2673、`/system/jobs/{id}:retry` 等），
   `/[^:]*` 型正则在冒号处截断 → 必须允许内部冒号、只以行尾 `:` 定界。
2. `get<T>()` 的**跨行惰性泛型**会把上一处调用的方法名配到下一处调用的字符串字面量，
   使 `POST /assets/{X}/versions/{X}/upload-sessions`（契约 L3408，在用）被误记为 GET → 泛型组须限定不跨行/不跨括号。
3. 模板串**后缀插值**（`PreviewPanel` L170 `` `/assets/${assetId}/previews${params}` ``）与**查询串**
   须先删非 `/` 前导的 `${}`、再剥 `?` 之后内容，否则产出 `/assets/{X}/previews{X}` 假路径。
4. `getBlob`（`/assets/{X}/previews/artifact`、`/assets/{X}/versions/{X}/artifacts/{X}/content`）
   与 `apiUrl=` 是**独立调用面**，只采 `apiClient.get` 会漏 30 条。
5. 变量参数路径（`apiClient.get<UploadSession>(path)`）纯正则必漏。
6. PowerShell 传参**展平嵌套数组**，匹配函数内 `$c[1]` 退化为字符串首字符 → 须传原始键串并在函数体内切分。
7. 判据可信性自检：修好匹配器后 CT-only 仍稳定列出 `GET /datasets` 等真实未调用项，证明匹配器非恒真。

**结论：FE 越契约 = 0 条。** 前端不存在任何契约外调用（强于 ⑬「后端实现 144/144 命中契约」的单向结论）。

**契约未接线 25 条按四类归档**：
**A 类·Agent 消费面 9 条（非缺陷，`aihub-agent-v1.yaml` 本就由 Agent/CLI 而非浏览器消费）**
`GET /agent/assets/search`、`GET /agent/assets/{X}`、`GET /agent/assets/{X}/versions`、
`GET /agent/versions/{X}`、`GET /agent/assets/{X}/upload-sessions/{X}`、
`POST /agent/assets/{X}/upload-sessions`、`POST /agent/assets/{X}/upload-sessions/{X}/complete`、
`POST /agent/assets/{X}/versions/draft`、`POST /agent/versions/{X}/download`。
**B 类·非前端消费面 4 条（非缺陷）**：`POST /mcp`（MCP 适配器入口）、`POST /auth/token`、
`POST /auth/agent/token`（客户端凭据交换，前端仅走 `shared/api/auth.ts` L17/22/27 的
login/refresh/logout）、`POST /webhooks/gitea`（Gitea 入站回调）。
**C 类·前端确未接线 10 条（真实差距，须判「补页面」还是「契约裁剪」）**
1. `GET /datasets`、`GET /models`（契约 L2372 `searchDatasets` / L2336 `searchModels`）——
   前端统一走 `GET /assets?type=`，两个专用检索端点零调用。
2. `GET /system/principals`（L219）、`GET /system/users/{userId}`（L298）——
   前端仅有 `GET /system/users` 列表，无主体选择器与用户详情。
3. `GET /system/organizations/{organizationId}/teams/{teamId}`（L728 `getTeam`）——
   `TeamsPage`/`admin/api.ts` L346 只用列表 + PUT + members，详情端点未接（本轮回源确认）。
4. `GET /system/alerts/firing`、`GET /system/metrics/downloads`——
   `admin/api.ts` L327/337 只用 `metrics/summary` 与 `alerts` 列表。
5. `GET /assets/{assetId}/upload-sessions`（L3382 `listUploadSessions`）——
   `UploadPage` 只按 `sessionId` 取单会话（L159），不列历史会话。
6. `DELETE /assets/{assetId}/relations/{relationId}`——`assets/api.ts` L60 只创建关系，无法解除。
7. `POST /assets/{assetId}/previews/generate`——`PreviewPanel` 只读预览，无主动重生成入口。
**D 类·能力重复（代码与契约并存两套口径，按门禁须上报而非静默择一）**
`POST /versions/{versionId}/archive`（L3198）、`POST /versions/{versionId}/deprecate`（L3170）
两条专用端点未被使用；前端 `version/api.ts` L45-50 `transitionVersion` 统一走
`POST /assets/{assetId}/versions/{versionId}/transition`。后端两套入口是否等价、
审计事件与权限位是否一致，需批准人裁定收敛方向（保留 transition 并裁剪专用端点，或反之）。

**小结**：25 条中 A+B 共 13 条为**面别不同、非缺陷**；C 类 10 条为前端功能差距（与 ⑯ 的 §3 G 系列同源）；
D 类 2 条为契约/实现重复口径，属**必须上报的冲突**。本项与前序 ①—⑯ 一并不在本轮 AI 自决实施范围内。

- **第⑱项（events-v1 事件契约 ↔ 后端 Outbox 实际写入双向差集，2026-10-12 续跑轮；**更正本文 L263「审计事件枚举复用无漂移」的错误自述**，登记 3 类实质缺陷，两侧均不擅改）**：

**方法学**：规范侧取 `contracts/events/events-v1.yaml` `EventType` 枚举 **24** 项 +
`AggregateType` 枚举 **7** 项（ASSET / ASSET_VERSION / UPLOAD_SESSION / JOB / AGENT / STORAGE /
SYSTEM_DEPENDENCY）+ `EventEnvelope` required 字段集与 `eventId` pattern；实现侧穷尽
`publishOutbox` / `publishOutboxEvent` / `new OutboxEvent(` 三类**真实 Outbox 写入点**
（Grep 全量列出于 `AssetApplicationService`、`RepositoryProvisionJobHandler`、
`DiscussionApplicationService`、`VersionApplicationService`、`PublishApplicationService`、
`UploadApplicationService`、`MinioStorageReconciler`、`SystemDependencyService`、
`WebhookChannelAdapter`、`NotificationService`），与「仅出现在 audit / alert / notification 面」
的字符串严格区分——**审计事件不是事件面**，不得据审计命中宣称契约事件已实现。

**缺陷 1·信封字段缺失与主键前缀违约（最高优先级，属契约违约而非差距）**
1. `schemaVersion`：契约 `EventEnvelope.required` 明列该字段（integer，default 1，用于兼容演进），
   但 `OutboxEvent` record（11 字段）与 `V11__notification.sql:48-60` `outbox_event` 表**均无此列**，
   `NotificationService.publishOutboxEvent` 亦无从写入 → 消费方按契约声明的
   「依赖稳定的 eventType 与 schemaVersion 做分支与兼容演进」在实现面**不可能成立**。
2. `eventId` 前缀：契约 `pattern: '^evt_'`、V11 列注释 L64 自述「evt_+ULID」，
   但两处真实写入点（`NotificationService` L133、`WebhookChannelAdapter` L53）均使用
   `idGenerator.generate(IdPrefix.REQUEST)` → 实际全部落库为 **`req_*`**；
   `shared/id/IdPrefix.java`（36 个枚举）**不存在 EVENT("evt")** 项。
   → 违反契约 pattern 与库注释双重口径，且 `req_` 与「请求标识」语义冲突（同一前缀被
   发布请求/幂等键/事件 ID 三类对象复用），消费方幂等去重与人工排障均被误导。

**缺陷 2·aggregateType 越契约枚举 + 同 eventType 内部不一致**
1. `WebhookChannelAdapter` L54 写死 `"NOTIFICATION"`；`PublishApplicationService` L154/259/280/321
   与 `PublishJobHandler` 写 `"PUBLISH_REQUEST"` → 两个值均**不在** `AggregateType` 枚举内。
2. `PublishApplicationService.deprecateVersion` L357 用 `"VERSION"`，而
   `VersionApplicationService` L143 对**同一 eventType** `VERSION_DEPRECATED` 用 `"ASSET_VERSION"`
   （契约合法值）→ 同一事件类型两种聚合根口径，按 aggregateType 路由/订阅/对账的消费方会漏事件。
3. `auditService.record(... "VERSION" ...)`（L353/381）属审计面 resourceType，不在本缺陷计数内，
   登记仅为避免后续复算时误判。

**缺陷 3·eventType 双向差集**
- 实现越契约（Outbox 真实写入）：**1** 项——`UPLOAD_PROCESSING`（`UploadApplicationService` L230，
  `UPLOAD_SESSION` 聚合合法，但事件类型不在 24 项枚举内）。
  （`UPLOAD_MATERIALIZED`、`VERSION_ARCHIVED`、`JOB_LEASE_EXPIRED` 等仅出现在审计面，合规，不计入。）
- 契约声明但后端**无任何 Outbox 写入点**：**8** 项——
  `ASSET_CREATE_REQUESTED`、`ASSET_CREATE_FAILED`、`ASSET_RENAMED`、`ASSET_DEPRECATED`、
  `ASSET_ARCHIVED`、`ASSET_RESTORED`、`JOB_DEAD`、`AGENT_ACCESS_DENIED`。
  其中 `ASSET_DEPRECATED/ARCHIVED/RESTORED` 有 `auditAsset` 无 `publishOutbox`；
  `JOB_DEAD` 走审计+通知+告警三面（`JobWorker` L308 / `JobNotificationAdapter` L44 /
  `AlertService` L235）但**不进 Outbox**；`AGENT_ACCESS_DENIED` 仅审计（`McpController` L235）；
  `ASSET_CREATE_REQUESTED/FAILED`、`ASSET_RENAMED` 后端主码**零命中**。
  → 契约自述其为 Outbox/Webhook 权威来源，则 Webhook/Inbox 外部消费方**永远收不到这 8 类事件**；
  若这些类型本就是「仅站内」，应回契约标注 planned/面别，而非留作枚举歧义。
- 其余 **15** 项 eventType（ASSET_CREATED/UPDATED/DELETED/PROVISIONED、DISCUSSION_* 4、
  VERSION_REVIEW_REQUESTED/APPROVED/REJECTED/PUBLISHED/DEPRECATED、UPLOAD_FAILED、
  STORAGE_QUOTA_WARNING、SYSTEM_DEPENDENCY_UNHEALTHY）经逐条回源确认**写入点真实存在且聚合根合法**。

**对前序判据的更正**：本文 L259-263「MCP/迁移号位复核」条末句
「事件契约为统一信封 schema，审计事件枚举复用无漂移」把**审计面复用**误当**事件面一致**，
与本轮穷尽写入点的结果矛盾，按「证据不足/间接视为未达成」原则予以**推翻并登记 ⑱**。
L261 的 MCP 12 工具零漂移与迁移号位结论不在本更正范围（仍成立）。

**治理判定**：`contracts/events/events-v1.yaml`（x-contract-status: target-p0-b）与
`backend/**`、`V11__notification.sql`（已应用迁移，禁改，整改须 V45+ 新迁移）均为禁改面；
缺陷 1 属契约违约（信封不合规会影响所有既有事件的消费方），缺陷 2/3 属枚举口径漂移。
三项均**只登记待批**，提请批准人裁定整改方向（补 schemaVersion 列 + EVENT 前缀 + 修正
aggregateType 写入点，以及 8 类事件是「补 Outbox」还是「契约标注面别」）；
本轮 AI 不擅自改契约、不改迁移、不改后端。

- **第⑲项（OpenAPI `x-error-codes` ↔ `ErrorCode` 枚举 ↔ HTTP responses 三方一致性，2026-10-12 续跑轮；契约侧单向少声明 17 码 + 69 处状态缺口，规范层无缺口，三方均不擅改）**：

**方法学**：实现侧穷尽 `backend/src/main/java/com/aihub/shared/error/ErrorCode.java`
构造签名 `NAME(HttpStatus.X, "error.*", retryable, AlertLevel.Y)` → **76** 项（与
`docs/ai-spec/02-domain/error-catalog.md` §2 表行 **76** 行、§3「错误码总数 76」逐一对齐，
本轮为集合级对齐，未做逐格字段 diff）。契约侧穷尽 `contracts/openapi/aihub-v1.yaml` +
`aihub-agent-v1.yaml` 全部 `x-error-codes: [...]` 行（本轮独立复算：声明行 **144** = 每操作恰好 1 行、
与 ⑰ 的操作数完全吻合；数组内码位合计 **346**；去重后引用 **59** 个码）
与全部 4 空格 HTTP 方法块的 `responses` 状态键，再做三方向比对：
**A**（契约引用但枚举不存在）、**B**（枚举存在但契约从不引用）、**C**（码在枚举绑定的 HTTP 状态
未出现在该操作 `responses`）。

**方向 A = 0 条**：契约引用的 59 个码**全部**存在于枚举，无「契约凭空造码」。漂移是**严格单向**的。

**方向 B = 17 条**（枚举/目录有、契约全篇零引用），按可达性逐一回源分两族：
- **B-1·实现会抛但契约未声明（9 项，属契约缺漏）**：
  `ASSET_REPOSITORY_PROVISION_FAILED`（`GiteaAssetRepositoryProvisioner` ×3）、
  `ASSET_STATE_NOT_ALLOWED`（`AssetApplicationService` ×3 + `DiscussionApplicationService` ×8）、
  `ASSET_VERSION_SYNTAX_INVALID`（`Version.java` ×2）、
  `GITEA_DEPENDENCY_UNAVAILABLE`（`GiteaVersionMaterializer` ×2 + `RestGiteaTagPublisher` ×1）、
  `INTERNAL_ERROR`（`GlobalExceptionHandler` L58 + `InternalException` ×2）、
  `PASSWORD_CHANGE_REQUIRED`（`PasswordChangeRequiredInterceptor` L1）、
  `PRINCIPAL_NOT_FOUND`（`OrganizationApplicationService` L131 + `TeamApplicationService` L132）、
  `UPLOAD_SIZE_EXCEEDED`（`UploadApplicationService` L1）、
  `WEBHOOK_TARGET_FORBIDDEN`（`SsrfGuard` ×9）。
  （引用数以 `ErrorCode\.X\b` 全后端扫描计，并以裸名二次扫描排除静态导入漏判。）
- **B-2·主码零引用（8 项，准死码，且三处存在**语义挪用**）**：
  `PROJECT_NOT_FOUND`——`ProjectApplicationService` L113/121 在**项目**不存在时抛的是
  `ORGANIZATION_NOT_FOUND`（同文件 L86 `PROJECT_ALREADY_EXISTS` 在用）；
  `DISCUSSION_NOT_FOUND` / `COMMENT_TOO_LARGE`——`DiscussionApplicationService` L341/347
  （`loadThread`/`loadComment`）抛 `ASSET_NOT_FOUND`；
  `DISCUSSION_LOCKED`——同文件 L122/183/199/205/222/239/254/270 共 8 处锁态抛 `ASSET_STATE_NOT_ALLOWED`；
  `DVC_OBJECT_MISSING`——预览族实际用同族 `PREVIEW_SOURCE_UNAVAILABLE` 顶替；
  `JOB_RETRY_EXHAUSTED`——`JobWorker` 走 `markDead(<任意码字符串>)`，不经枚举；
  `MINIO_DEPENDENCY_UNAVAILABLE`——依赖不可用族只有 Gitea 侧在用；
  `ASSET_COORDINATE_INVALID`——全仓**唯一**引用在测试 `JobWorkerTest` L227/233。
  → 挪用后果：错误响应 `code` 与 i18nKey 对调用方**误导**（项目问题报「组织不存在」、
  讨论问题报「资产不存在」），且 `error-catalog.md` 为这 8 项登记的 i18nKey/告警级实际永不生效。

**方向 C = 69 处 / 60 个操作**（共 144 操作、346 码位）：操作声明了 `x-error-codes`，
但该码在枚举绑定的 HTTP 状态**不在其 `responses`** 中。最大组
`AUTH_PERMISSION_DENIED`(403)×18、`COMMON_INVALID_ARGUMENT`(400)×15、
`ORGANIZATION_NOT_FOUND`(404)×6、`VERSION_STATE_NOT_ALLOWED`(409)×5；
另**独立统计**得 **36** 个带 `requestBody` 的操作连 `'400'` 都未声明。
铁证示例：`POST /assets`（L1658-1691）L1665 声明 `AUTH_PERMISSION_DENIED`，
`responses` 实数仅 `201/400/409`（L1688 `'400'`、L1690 `'409'`）——**无 `'403'`**；
`'/system/jobs/{jobId}:retry'`(L1394) 与 `:cancel`(L1411) 均声明 `JOB_NOT_FOUND`，
`responses` 只有 `200/403/409`——**无 `'404'`**；`aihub-agent-v1.yaml` 5 处声明 `*_NOT_FOUND` 而操作无 `404`。

**定性（为何判「契约少声明」而非「实现漂移」）**：
1. `bootstrap/GlobalExceptionHandler` L46 `return ResponseEntity.status(errorCode.httpStatus()).body(body)`
   → 运行时状态码**由枚举绑定唯一决定**，与契约 `responses` 键无关；实现不可能产出别的状态。
2. 两契约**均无** `'4XX'`/`'5XX'`/`default` 通配响应（全文唯一 `default` 命中在 L4114，属 schema 关键字）
   → 少声明**不能**用通配豁免。
3. `error-catalog.md` 状态 READY 且自述「权威源：`ErrorCode.java`」，76 项 HTTP/i18nKey/retryable/alertLevel
   全表在册 → **规范层（枚举 + 目录）自洽无缺口**，缺口单侧落在 **OpenAPI 契约**。
   故本轮**不改** `error-catalog.md`、不改枚举、不改后端，冲突上报点集中在 `contracts/openapi/*.yaml`。

**根因（可工具化的缺口）**：`components.schemas.ApiError.code`（L3735-3738）仅
`type: string` + `example`，**无 enum / pattern 约束** → 契约侧任何错误码笔误或缺漏都无法被
lint / breaking-change 检查发现，方向 A/B 的漂移只能靠本轮式外部脚本才暴露。建议整改时一并补
`code` 的枚举约束（须先有受控的码全集来源，避免与目录二次漂移）。

**探针纠错链（判据可信性登记，后续复用须避开同一坑）**：
1. 首版状态扫描返回 **0** MISS 是**假阴性**：内层 break 条件写成
   `'^    [a-z]+:'`，在同 4 空格缩进的 `requestBody:` 处提前跳出，**从未到达 `responses` 块**。
   教训：**判据为 0 必须先自证扫描器真跑过**（此后每版都输出 `OPS`/`CODE_SLOTS`/`WITH_RESPONSES`/
   `UNMAPPED_STATUS` 自检计数）。
2. 修正 break 为 `'^  \S'` 后得 71 处，其中 **4 条 `PREVIEW_TOO_LARGE` 为假警**——
   状态映射表漏 `PAYLOAD_TOO_LARGE=413`；补齐 20 项 `HttpStatus.*` → 整型映射并加
   `UNMAPPED_STATUS` 报警后定数 **69**。
3. 路径键正则两度踩坑：先漏**内部冒号动作段**（`:retry`/`:cancel`/`:subscribe`）致 `$curPath`
   粘连滞后；后漏**带单引号的路径键**（`'/system/jobs/{jobId}:retry':`）致 `:retry` 与 `:cancel`
   在按操作聚合的哈希表中**相互覆盖**，一度得到「59 个受影响操作」的**低估**。
   终版（probe7）同时允许引号与内部冒号、逐操作出行不去重，得
   `PATH_KEYS=120`（v1 的 111 + agent 的 9，与 ⑰ 独立复算吻合）、`OPS=144`（与 ⑬/⑰ 的 144 三方吻合）、
   **69 处 / 60 操作**。**计数链 0 → 71（含 4 假）→ 69/59 → 69/60 全部如实登记，不追认前值。**

**整改待批（三选一或组合，AI 不自决）**：
① 契约补齐：为 60 个操作补 `responses` 状态键、为 9 项 B-1 码补 `x-error-codes` 引用，并给
`ApiError.code` 加受控枚举；② 目录+枚举收敛：删除或并档 8 项 B-2 死码（须 V45+ 迁移与 i18n 同步，禁改已应用迁移）；
③ 语义挪用回改：`PROJECT_NOT_FOUND` / `DISCUSSION_NOT_FOUND` / `COMMENT_TOO_LARGE` /
`DISCUSSION_LOCKED` 改回各自专码（属后端产品码改动，且会**改变对外错误码**，需评估调用方兼容与契约登记）。
→ 本项与 ①—⑱ 一并不在本轮 AI 自决实施范围内。

- **第⑳项（权限码四方一致性：`Permissions.java` ↔ `iam_permission` 迁移 seed ↔ 契约 `x-required-permissions` ↔ 前端 scope，2026-10-12 续跑轮；后端↔库 35=35 完全对齐，契约存在 1 个幻影权限码，均不擅改）**：

**方法学**：四侧穷尽 `resource:action` 形态码集合并做集合级差集——
① 后端 `authorization/domain/Permissions.java` 常量（正则 `String [A-Z_]+ = "x:y"`）→ **35**；
② 迁移 seed：穷尽所有 `V*.sql` 中单引号 `x:y` 字面量（实际落点 V4 + V12 + V14 + V20）→ 去重 **35**；
③ 契约 `x-required-permissions: [...]`（两 yaml 全量）→ 去重 **28**；
④ 前端 `frontend/src/**`（排除 `*.test.*`）单引号 `x:y` 字面量 → **34**（含 i18nKey、query key 等非权限噪声，仅作上限参考）。

**结论 1·后端 ↔ 数据库完全对齐（正向）**：`JAVA-not-in-DB = 0`、`DB-not-in-JAVA = 0`，
两侧均为 **35** 项 → `Permissions.java` 与 `iam_permission` 预置码**无漂移**（V20 注释自述「补 Permissions.java 中存在但 V4 seed 缺失」已闭环）。

**结论 2·契约幻影权限码（缺陷，须整改）**：`CONTRACT-not-in-JAVA = 1` 且
`CONTRACT-not-in-DB = 1` —— **`publish:approve`**（`aihub-v1.yaml` L3146，`submitDecision` 操作）。
该码**既不在 35 项枚举、也不在 `iam_permission` 任何 seed**，后端 `PublishApplicationService.submitDecision`
L174 实际强制的是 `Permissions.ASSET_PUBLISH`（`asset:publish`）→ 契约声明了一个**永远无法被任何角色授予**的权限码，
与 ⑲ 根因同源（契约侧权限/错误码均无受控枚举校验，lint 无法发现幻影引用）。
（附带观察：`ReviewController` L39 与 `VersionController` L128 均挂 `/publish-requests/{requestId}/decisions`
的 POST/GET，权限判定下沉到 `PublishApplicationService`，契约层 `x-required-permissions` 与实现层
`requirePermission` **不同名**，属规范↔实现口径漂移，非本轮新缺陷。）

**结论 3·契约未引用的后端权限码（8 项，须拆分定性；本项为首版落盘后的自我更正）**：
`asset:deprecate`、`asset:download`、`asset:moderate`、`asset:publish`、`asset:review`、
`asset:submit`、`asset:upload`、`mcp:invoke`。首版曾一律判为「合理下沉、不是死码」——**该定性经复核被推翻**，
正确口径应分两类：

- **3-1·有服务层强制/引用（5 项，合理下沉，契约少声明）**：`asset:publish`
  （`PublishApplicationService` L174 `requirePermission(ASSET_PUBLISH)` + `McpToolCatalog` L308）、
  `asset:moderate`（`DiscussionApplicationService` L217/234/249/265）、`asset:deprecate`
  （`AssetApplicationService` L243）、`mcp:invoke`（`AgentController` L281/297 + `McpController` L123/154）
  四项由服务层 `requirePermission` **在操作声明之下**强制；`asset:review` 仅在
  `NotificationRecipientResolver` L43 以 `resolvePrincipalsWithPermission(ASSET_REVIEW)` 用于**审核人解析**
  （非授权校验）。此 5 项属契约操作级权限面**不完整**（消费方仅读契约无从得知端点还受这些权限约束），
  建议整改时补齐（与 ⑲ 方向 B-1 同类：契约少声明）。
- **3-2·零引用死权限码（3 项，授权口径漂移/覆盖缺口候选，须人工处置）**：`asset:upload`（`Permissions.java` L55）、
  `asset:download`（L57）、`asset:submit`（L59）——三码**在 `Permissions.java` 定义 + `V4` 迁移 seed 授予角色，
  但 `backend/src/main` 全量无任何 `Permissions.ASSET_UPLOAD/DOWNLOAD/SUBMIT` 或字面量 `asset:upload/download/submit`
  引用**（仅 `PermissionCatalogConsistencyTest` 与若干 `*IT` 测试夹具命中）。实际授权口径为：上传走 `ASSET_MANAGE`
  （`UploadApplicationService` L99/162/185/239）、下载走 `asset:read`（`DownloadApplicationService` L67），
  **均不使用这三个专用码** → 三码是**已授予但永不校验**的死权限（存在授权口径漂移，非「下沉」）。
  （附带缺陷：`DownloadApplicationService` L67 以硬编码字符串 `"asset:read"` 调用 `requirePermission`，
  未用 `Permissions.ASSET_READ` 常量，违反「用共享 principal/常量、不散落字面量」约定。）
  `PermissionCatalogConsistencyTest` 仅做 Java↔DB **集合级**比对（TASK-P0BR-003/AUD-016），**不校验引用**，
  故无法发现此类死码——这也是 ⑲⑳ 共同根因（权限/错误码在契约与守卫层均缺受控枚举 + 引用完整性校验）。

**结论 4·前端 scope（低危，登记）**：`FE-not-in-JAVA = 2`（`asset:write`、`admin:all`）。
回源定性——二者**均不在生产导航/路由授权声明**中：`navigation.test.ts` L9/109-116 已按 TASK-P0BR-065
显式守护「全站导航不得使用幽灵码 `asset:write`」并 PASS；现存命中仅① 测试夹具（`test-utils.tsx`、
`AuthProvider.test`、`PermissionProvider.test`、`PermissionsPage.test`）、② `AccessPage.tsx` L35
`SCOPE_COLOR` **展示色映射**（非授权判定）、③ `RouteGuard.test` L95。→ 属**测试桩与展示层遗留字面量**，
非越权，低危；建议清理测试夹具改用真实码以免误导，不阻塞。

**探针纠错登记**：首版函数命名 `Diff` 与 PowerShell 内置别名（`Compare-Object`）冲突致 exit 1，
改名 `CodeDiff` 后跑通；四侧计数（35/35/28/34）自证扫描器真实执行。

**整改待批（AI 不自决）**：① 修正契约 `publish:approve` → `asset:publish`（或按需新增该权限并同步枚举+V45+ 迁移 seed，属**改契约 + 可能改后端/迁移**）；
② 契约补齐 **3-1 类 5 项**操作级 `x-required-permissions`（`asset:publish`/`moderate`/`deprecate`/`review`/`mcp:invoke`）；
③ **处置 3-2 类 3 项死权限码**（`asset:upload`/`download`/`submit`）——二选一：补上对应服务层 `requirePermission`
（收紧授权口径，属改后端 + 可能改角色 seed）或删除该码并同步 `Permissions.java` + `V45+` 迁移撤销授予 +
`PermissionCatalogConsistencyTest` + i18n/前端展示（属改后端 + 迁移），**AI 不得擅自择一**；
④ 顺手修正 `DownloadApplicationService` L67 硬编码 `"asset:read"` → `Permissions.ASSET_READ`（属改后端，一行）；
⑤ 视需要给权限码加受控枚举 + 引用完整性校验以根除幻影/死码（与 ⑲ 根因合并处置）；
⑥ 前端测试夹具清理（低优先）。→ 本项与 ①—⑲ 一并不在本轮 AI 自决实施范围内。

### ㉑ 状态机枚举四方差集：`02-domain/state-machines.md` ↔ 后端 enum ↔ DB CHECK ↔ 契约 enum

**方法学**：对 `state-machines.md` §1—§13 声明的每个状态机，逐一对齐四侧取值集合与转换表——
① 规范（`docs/ai-spec/02-domain/state-machines.md`，231 行 13 个状态机，L4 自定「每个转换必须在 Java Enum/Domain、
DB CHECK、OpenAPI/事件、前端生成类型和测试中一致」，本项即按此条取证）；② 后端 `public enum`（穷举 30 个）；
③ DB `ck_*status` CHECK（29 处迁移命中）；④ 契约命名 schema `enum` 与内联 `enum`。逐值比对集合 + 转换。
**已核验合规**（反向对照，证明「四方一致」是可达标准）：`AuditResult`（枚举 3 值 ↔ `ck_audit_log_result`(V10 L29) 3 值 ↔
`AuditLogView.result`(契约 L4380-4382) 3 值 ↔ spec §13 三态，全对齐）；`PatStatus`（3 值 ↔ `PatView` L5203 对齐）；
`UserStatus`/`AgentStatus`/`RefreshTokenStatus`/`CommentStatus`/`JobStatus`/`WebhookDeliveryStatus` 集合层面对齐 spec 对应章节；
`ck_validation_report_status` 未被违反（`ValidationJobHandler` L79/110 的 `"WARN"` 属 findings JSON **条目级**、非列值，
报告级 L123 仍 PASSED/FAILED——已排除假阳性）；`ck_alert_status`（FIRING/RESOLVED）合规。

**结论 1·PublishRequestStatus：四方仅 DB 有定义（最重）**。DB `ck_publish_request_status`：V17 L51 六值 →
V26 加 `CHANGES_REQUESTED` → **V38 L18-20 并集扩至八值**（`SUBMITTED/APPROVED/REJECTED/CHANGES_REQUESTED/
PUBLISHING/PUBLISHED/FAILED/SAGA_COMPENSATION_FAILED`）。其余三侧**全缺**：② 后端**无任何 Java 枚举**，
状态值以**裸 SQL 字面量**散落 `PublishApplicationService`（L139-140 INSERT 'SUBMITTED'、L243 'REJECTED'、
L266 'CHANGES_REQUESTED'、L302 'APPROVED'）与 `PublishJobHandler`（L85 'PUBLISHING'、L145/160 'PUBLISHED'、
L179 'FAILED'、L198 'SAGA_COMPENSATION_FAILED'）；③ 契约**无 `PublishRequestView` schema**，
`listPublishRequestsByAsset`（L2908-2930）返回 `data: { type: array, items: { type: object } }` **无类型**，
后端 `listPublishRequests`（L388）返回 `List<Map<String, Object>>` 直接透传；④ `state-machines.md` **无该状态机章节**
（§7 只讲 VersionStatus）。→ 一个有 8 个状态、含 Saga 补偿失败终态的核心发布流程，**规范无定义、代码无枚举、契约无类型**，
仅靠 DB CHECK 兜底，违反 AGENTS.md「稳定工作流状态是代码枚举 + DB 约束」与 state-machines.md L4 四方一致要求。

**结论 2·PrincipalStatus：契约声明库里不可能出现的状态值（幻影状态）**。契约 L3858-3860
`PrincipalStatus.enum: [ACTIVE, LOCKED, DISABLED]`，被 L3870/L3983/L4034/L4070 四处 schema 引用；
DB `ck_iam_principal_status`（V3 L20）仅 `('ACTIVE','DISABLED')`——**无 LOCKED**；
写入路径 `MyBatisLocalUserRepository` L67 `syncPrincipalStatus(..., user.status()==DISABLED ? "DISABLED" : "ACTIVE")`
只在 ACTIVE/DISABLED 间切换，L82-83 的 LOCKED **只写 `iam_user.status`、从不下沉 `iam_principal`**。
→ 契约声明的 `LOCKED` 在 principal 侧**永远不可能出现**；`PrincipalSummaryView` L17 javadoc 亦误述「ACTIVE/LOCKED/DISABLED」，
读路径 `MyBatisPrincipalQueryRepository` L45 直取 `entity.getStatus()`（只会返回 ACTIVE/DISABLED）。
spec §3 L47 要求 Agent/Principal 状态聚合一致，本项为其反向违背。

**结论 3·UploadSessionStatus：规范命名与集合双重落后三方**。spec §8 L127 自陈「设计未给出完整枚举，建议」并沿用
`CREATED/UPLOADING/COMPLETING`；实际后端 `UploadSessionStatus` 七值 `OPEN/COMMITTING/PROCESSING/COMPLETED/
FAILED/CANCELLED/EXPIRED`、契约 `UploadSessionView`（L5144-5155）内联同七值、DB V16 L99 原仅五值（缺
PROCESSING/FAILED）由 **V42** 补齐七值。→ 代码/契约/DB 三方已收敛为七值，**规范停留在早期命名建议**，属规范落后。

**结论 4·规范转换表落后代码（两处）**。① ThreadStatus：spec §12 L205-208 仅 `OPEN ↔ LOCKED`，
代码 `ThreadStatus`/DB `ck_discussion_status`(V15)/契约（L4899-4905）均为 `OPEN/LOCKED/CLOSED` → 规范缺 CLOSED。
② VersionStatus：枚举集合四方一致（六值），但 `VersionStatus.assertTransitionTo`（确被 `Version.java` L96 调用）
允许 `DRAFT/VALIDATING/PENDING_REVIEW/PUBLISHED → ARCHIVED` 与 **`DEPRECATED → PUBLISHED`**，
spec §7 转换表均未列出，且 `DEPRECATED→PUBLISHED` 与 spec §1.7「终态不可逆」原则直接冲突。

**结论 5·契约缺受控枚举（⑲ 同根因复发）**。① `WebhookDeliveryView.status`（L5023/L5031）
`{ type: string }` **无 enum**（代码/DB/spec §11 均四值）；② `aihub-agent-v1.yaml` L392
`provisioningStatus: { type: string }` **无 enum**（主契约 L4782-4785 有 enum，Agent 契约脱漏）；
③ 契约 `IdempotencyRecord`（L4495-4507）**完全没有 status 字段**（DB `ck_api_idempotency_status`(V9 L25)
三值、spec §10 状态机存在，契约视图不暴露状态）。

**结论 6·零写入死状态值（⑳ 死权限码同型）**。① `ck_webhook_inbox_status`（V18 L14）含 `DISCARDED`、`FAILED`，
但全 backend 检索：`DISCARDED` 除 V18 定义外**零命中**、`WebhookInboxHandler` 仅写 PENDING/PROCESSING/COMPLETED
（L99/L109）、各 Reconciler INSERT 恒写 'COMPLETED' → `DISCARDED`/`FAILED` 为**不可达死状态值**；
② `ck_upload_file_status`（V16 L135）四值，`UploadFileStatus` 枚举含 `FAILED`，但 `UploadApplicationService`
仅写 UPLOADING(L119)/COMPLETED(L218/274)、INSERT 依赖 DEFAULT 'PENDING'，**`FAILED` 无任何写入点** → 死状态值。
另 `UploadPartView` L21 仅由 `etag` 派生 COMPLETED/PENDING 展示态，与 `upload_file.status` 语义不同源（登记，非违背）。

**根因（与 ⑲⑳ 合并）**：状态机缺「规范↔枚举↔CHECK↔契约」四方引用完整性守卫。`PermissionCatalogConsistencyTest`
仅比对权限码集合；**不存在**任何测试断言「DB CHECK 值集 == Java enum == 契约 enum == spec 枚举」，
故 PublishRequestStatus 无枚举、PrincipalStatus 幻影 LOCKED、DISCARDED 死值均长期未被发现。

**整改待批（AI 不自决）**：① 为 PublishRequestStatus 新增领域枚举 + `PublishRequestView` 契约 schema +
state-machines.md 章节（属改 02-domain + 契约 + 后端）；② 契约 `PrincipalStatus` 移除幻影 `LOCKED`（或按需把 LOCKED
真正下沉 principal 并改 V CHECK，二选一，AI 不擅自择一）+ 修 `PrincipalSummaryView` L17 javadoc；
③ state-machines.md §8/§12/§7 升格为代码/DB/契约实际收敛态（含 `DEPRECATED→PUBLISHED` 是否违反终态不可逆的裁决）；
④ 给 `WebhookDeliveryView.status`/Agent 契约 `provisioningStatus` 补 enum、`IdempotencyRecord` 补 status 字段（属改契约）；
⑤ 处置 `webhook_inbox` 的 `DISCARDED`/`FAILED` 与 `upload_file` 的 `FAILED` 死状态值（补写入路径或 V45+ 撤销 CHECK 值，二选一）；
⑥ 新增「状态机四方一致性」守卫测试（与 ⑲⑳ 的权限/错误码引用完整性守卫合并处置）。
→ 本项与 ①—⑳ 一并不在本轮 AI 自决实施范围内。

### ㉒ 契约路径 ↔ 后端 Controller 路由双向差集：唯一真实漂移为 MCP 端点前缀

**方法学**：自包含探针解析两侧并做模板级 + 方法级双向差集。契约侧 = `servers[0].url` + `paths` 键
（`aihub-v1.yaml` L21-22 与 `aihub-agent-v1.yaml` base 均为 `/api/v1`），占位符归一 `\{[^}]+\}` → `{}`；
后端侧 = 类级 `@RequestMapping` + 方法级 `@(Get|Post|Put|Patch|Delete)Mapping`，按 Spring 语义方法级路径追加至类级 base、
**无参形式路径即类级 base**；已确认 `backend/src/main/resources` 全域**无 `server.servlet.context-path`**（Grep `context-path|servlet:` → No matches），
故 `/api/v1` 完全由各 Controller 显式声明。**三轮排除的工具性假阳性**（如实登记，避免后人重踩）：
① 契约 11 条 `:action` 路径以**单引号**声明（L349/L366/L383/L454/L470/L1279/L1295/L1388/L1405/L1474/L2824），
初版正则只认裸 `  /path:` 致整族漏采；② `PreviewController` 用 Java 常量声明路径
（L33 `private static final String PREVIEWS_BASE = "/api/v1/assets/{assetId}/previews"`，
L51 `@GetMapping(PREVIEWS_BASE)`、L60 `+ "/generate"`、L89 `+ "/artifact"`），初版只认字符串字面量；
③ 34 个类级 `@RequestMapping` 中 30 处方法级注解为无参形式，初版误做 `classBase + classBase` 双拼接。

**最终计数**：`CONTRACT_NORM=120 / BACKEND_NORM=120`，模板级差集**各 1 条**，
方法级差集（两侧同模板的 HTTP 动词集合比对）`METHOD-DIFF-COUNT=0`。
即：**除 MCP 前缀外，REST 契约与后端路由在路径模板与动词两个维度上完全对齐**——这是本轮迄今最强的一项一致性证据。

**唯一真实漂移·MCP JSON-RPC 端点前缀不一致（`/api/v1/mcp` vs `/mcp`）**。
① 契约：`aihub-v1.yaml` L3647 `  /mcp:` + L3648 `post:` + L3649 `operationId: mcpJsonRpc`，
在 `servers url: /api/v1` 下即声明完整路径 **`/api/v1/mcp`**（L3652 自标 `implemented-unverified`）；
② 后端：`McpController.java` L43 `@RequestMapping("/mcp")` + L80 `@PostMapping`（无参）→ 实际注册 **`/mcp`**；
全仓无 context-path、无 `configurePathMatch`/`addPathPrefix`/`setUrlPathHelper` 改写（Grep 仅命中无关的
`IdentityWebConfiguration` 拦截器注册），后端亦无任何 `/api/v1/mcp` 别名路由。
③ **该漂移已在仓内自我矛盾**，不是纯理论问题：
　· `AgentBundleView.java` L31 对外广播 `normalized + "/api/v1/mcp"`，
　　经 `IntegrationsPage.tsx` L47-49 以「MCP 端点」直接展示给接入方（`IntegrationsPage.test.tsx` L10/L37 断言该值）
　　→ **平台告诉 Agent 的接入地址按后端真实路由不可达**；
　· `nginx.conf` L95 `location /mcp`（配 `proxy_buffering off` 流式支持）与 L59 `location /api/` 分别代理两条路径，
　　但只有 `/mcp` 落到真实 handler；
　· **Compose 验收存在双向自相矛盾**：`verify.sh` V26（L1322-1327）断言 `POST /mcp` 无 Token 返回 401（走真实路由，检查有效）；
　　而 V19「MCP 端点可用性」（L1192-1208、注册于 L1263）断言的却是 `/api/v1/mcp`，
　　且其通过条件为 `[ "${code}" = "401" ] || [ "${code}" = "200" ]`（L1206）。

**V19 为假阳性通过（重要）**：`SecurityConfiguration.java` L82 `auth.anyRequest().authenticated()` 作用于
**安全过滤链**，先于 `DispatcherServlet` 的 handler 映射。因此对**不存在**的 `/api/v1/mcp` 发起无 Token POST，
也会因认证失败返回 401，而非 404 —— V19 遂判 PASS。该检查**无法区分「端点存在且默认拒绝」与「路由根本不存在」**，
在其声明的契约路径上恒真通过。仓内实际走通的 MCP 调用一律用 `/mcp`
（`verify-journey.sh` L106、`verify-journey.ps1` L144-146、`p4-onboarding.sh` L149/158/167、`perf-smoke.sh` L121/125），
与 V19 形成反证。另 `McpController` L180 幂等键用 `"/mcp/tools/call/" + toolName` 作 `request_path` 记录，
亦按 `/mcp` 口径写入审计面。

**根因**：`/api/v1` 前缀靠 34 个 Controller 各自手写而非集中配置（无 context-path、无统一 base 常量），
`McpController` 是**唯一**一个类级 `@RequestMapping` 不以 `/api/v1` 开头的业务控制器；
而契约→路由、路由→契约**双向存在性守卫测试缺失**（与 ⑲⑳㉑ 的引用完整性守卫缺失同源），
加上 V19 以 401 兜底判定可用性，使该漂移同时逃过了代码评审与 Compose E4 验收。

**整改待批（AI 不自决）**：① 择一对齐——把 `McpController` L43 改为 `@RequestMapping("/api/v1/mcp")`
（改后端，需同步 `nginx.conf` L95 location、`verify-journey.sh`/`.ps1`、`p4-onboarding.sh`、`perf-smoke.sh`、
`McpController` L180 幂等键口径）或改契约 L3647 为不带 servers 前缀语义的 `/mcp` 并调整文档（改契约）；
**两条路径的对外契约含义不同，AI 不得擅自择一**；
② 无论选哪条，`AgentBundleView` L31 与真实路由必须一致（否则接入指引持续给出不可达地址）；
③ 重写 V19：断言**契约声明的确切路径**且区分 401 与 404（建议先无 Token 期望 401、再带 Token 期望非 404），
使「路由不存在」不再被判为可用；④ 新增「契约 path × 后端 handler」双向存在性守卫测试（与 ⑲⑳㉑ 守卫合并处置）；
⑤ 考虑以集中配置统一 `/api/v1` 前缀（context-path 或 `configurePathMatch`）根除手写漂移，属架构口径变更，需 ADR。
→ 本项与 ①—㉑ 一并不在本轮 AI 自决实施范围内。

### ㉓ MCP 工具契约 ↔ 后端目录 ↔ 鉴权链 ↔ 前端白名单差集：13 项（含 1 项合规反向对照）

**对本文 §4.6 L259-261「零漂移」判语的限定范围更正**：该行结论口径**仅覆盖三个维度**
（工具名称、`requiredPermission` 映射、`write` 标记），在此三维内确为 11↔11 零漂移。
本轮把比对面扩展到 `requiredScopes`、参数 `required` 数组、字段 `pattern`、`auditEvent`、`idempotency`、
`toolListing` 过滤语义、`resources` 工具族、资源状态/敏感级别校验共八个额外维度后，发现 12 项偏差（另 1 项合规对照）。
原判语不应被读作「MCP 契约整体无漂移」，特此限定（与 ⑱ 对同一行的更正同法）。

**方法学**：四方交叉取证 ——
契约 `contracts/mcp/tools.yaml`（L1-220，11 工具）↔ 目录 `mcp/application/McpToolCatalog.java`（400 行）↔
协议适配 `mcp/api/McpController.java`（310 行）+ 资源面 `mcp/application/McpResourceHandler.java`（132 行）↔
授权链 `authorization/application/AuthorizationService.java` + `authorization/domain/Permissions.java` +
写入口 `identity/api/SystemAgentController.java` + 前端 `features/admin/pages/AgentsPage.tsx`。

**最终计数**：工具**名称** 11↔11 全对齐、**权限映射** 11/11 全对齐；语义/参数/鉴权/审计/披露维度差异 **12 项** + 合规反向对照 **1 项**。

**① auditEvent 漂移（最重，契约声明的审计事件永不由该工具路径产生）**
`tools.yaml` L209 `asset_publish_version.auditEvent: VERSION_PUBLISHED`；
但目录 L309 实际调用 `publishService.submitPublishRequest(...)`，
`version/application/PublishApplicationService.java` L144-148 写入的是
`new AuditEvent("PUBLISH_REQUEST_SUBMITTED", "publish:submit", ...)`，L153 Outbox 事件类型为 `VERSION_REVIEW_REQUESTED`，
L161 返回 `requestId`（即**只提交发布请求、并不发布**）。真实 `VERSION_PUBLISHED` 只在
`VersionApplicationService` L168/172/178 的另一条代码路径产生。
对照：`asset_delete` 的 `ASSET_DELETED`（L219）合规 —— `AssetApplicationService` L229 审计 + L231 Outbox 一致。
→ 按契约做审计对账的接入方会**永远查不到** `asset_publish_version` 的 `VERSION_PUBLISHED` 记录。

**② 两个写工具在契约侧完全无参数声明**
`tools.yaml` L202-210 与 L212-220 均**无 `parameters` 块**；后端却各有 inputSchema：
目录 L306-307 `schema(Map.of("versionId", …))`、L314-315 `schema(Map.of("assetId", …))`。
→ 契约对这些工具的实际入参欠定义，`redocly`/契约校验对其无从约束。

**③ `required` 参数数组在实现侧被完全丢弃**
契约在 L89/103/119/133/150/166/184/198 共 8 处声明 `required: [...]`；
目录 L352-358 的 `schema(Map<String,Object> properties)` 只输出 `type`/`additionalProperties`/`properties`，
**从不输出 `required`**；取值统一走 L374 `str(Map,key)`，缺失即返回 `null` 而不报错。
→ 客户端拿到的 JSON Schema 不声明必填，缺失必填参数会下沉到业务层以 `null` 传播（而非 MCP 层 4xx）。

**④ `tagId` 的 `pattern` 校验缺失**
契约 L75 `tagId: { type: string, pattern: '^tag_' }`；目录 L156 仅 `"tagId", Map.of("type","string")`，无 pattern、无格式校验。
→ 与 AGENTS.md「资产写入只接受已注册 `tagId`」的口径相比，MCP 侧少了前缀这一道最低限度防线。

**⑤ `requiredScopes` 不构成独立校验维度（契约第 2 步为空）**
契约 L24 声明「2) JWT 粗粒度 Scope；3) 业务 Permission」两步独立，且每个工具都同时写 `requiredScopes` 与 `requiredPermissions`；
但目录 L71-77 `record ToolDefinition` **只有 `requiredPermission`**，无 scopes 字段；
`AuthorizationService` L119 `if (context.scopes() != null && context.scopes().contains(permissionCode)) return true;`
—— Scope 与 Permission 命中**同一命名空间的同一字符串**，任一命中即通过。
→ 所谓「双重约束」（L28-32 `dualConstraint`）在 Scope/Permission 这一对上实际只有一道闸门。

**⑥ `tools/list` 未按白名单过滤，直接违背契约 L39-42**
契约 `toolListing`：「仅返回该主体被**显式授权**且在开关内启用的 Tool」。
实现：控制器 L120-128 `handleToolsList()` 只做 L123 `requirePermission(context, Permissions.MCP_INVOKE)`
后直接 `toolCatalog.listVisibleTools()`；目录 L133 `isDiscoverable(ToolDefinition)` **只检查**
`AGENT_DENIED_TOOLS` 与 write 开关，**不查 `iam_agent_tool`**。
`authorizationService.requireToolAllowed(principalId, toolName)` 仅在 `tools/call` 路径 L156-158 生效。
（另注：`AuthorizationService` L190-195 已提供 `isToolAllowed` 重载可供列表使用，但未被调用。）
→ 任何持有 `mcp:invoke` 的主体都能**枚举出全量目录**（过度披露），白名单只在调用时才生效。

**⑦ 高风险工具为代码级永久封禁，与契约「默认不授予（可显式授权）」语义不等价**
目录 L49 `AGENT_DENIED_TOOLS = Set.of("asset_publish_version","asset_delete")` 为 `static final`，
L124-126 与 L133-141 一律拒绝、且 `isDiscoverable` 一并隐藏；
契约 L206/L216 的表述是「默认关闭；`asset:publish`/`asset:delete` Scope **默认不授予** Agent」+ L52-57 `defaultDeniedScopes`，
即存在「显式授权后可用」的路径。
→ 当前实现下即使管理员把 `asset_publish_version` 加入某 Agent 白名单，也**永远无法调用**（静默不可能，非配置问题）。

**⑧ 纯读语义的工具被强制要求 Idempotency-Key**
契约 L188-200 `asset_get_upload_status` **无 `idempotency` 字段**（对比 L210/L220 两个 `idempotency: required`），
但目录 L300 将其注册为 `write=true`（权限仍是 `ASSET_READ`）→ 控制器 L174-178
`if (toolDef.write()) { 缺 Idempotency-Key 则报 "Idempotency key required for write tools" }`。
→ 「查询上传会话状态」这一读操作必须携带幂等键，契约与实现口径互斥（该特殊口径在 §4.6 L260-261 三维比对中曾被记为「一致」，
但仅限权限/write 两维，未覆盖幂等键后果）。

**⑨ `resources/list` 与 `resources/read` 全族不在契约内**
`tools.yaml` **无 `resources` 段**（顶层键穷尽 Grep 仅 15 项，无 `resources`）；
控制器 L94-107 却支持 `resources/list`、`resources/read` 两个 JSON-RPC 方法，
`McpResourceHandler` L51-61 在**代码内硬编码** 4 个 resourceTemplate
（`aih://assets/{assetId}`、`aih://…/version/{versionId}`、`…/card`、`…/manifest/{versionId}`）。
→ 对外暴露的 MCP 能力面大于契约声明面，接入方无从得知、亦无从 lint。

**⑩ resources 面鉴权显著弱于 tools 面**
`handleResourcesList` L264-266 直接返回 `resourceHandler.listResources()`，**无任何鉴权、连 `mcp:invoke` 都不校验**
（对比 `handleToolsList` L123、`handleToolsCall` L154 均校验 `Permissions.MCP_INVOKE`）；
`handleResourcesRead` L268-282 在控制器层同样不做校验，且 handler 内部
L76/85/96/113 使用**裸字面量** `requirePermission(…, "asset:read")` 而非 `Permissions.ASSET_READ` 常量。
（handler L66-70 对 principal 缺失是 fail-closed 的，故非「匿名可读」，但仍**绕过 `mcp:invoke` 这道 MCP 面总闸**。）
→ 契约 L33-38 只提「tools/call 与 resources/read 一律拒绝（未认证时）」，未授权 `resources/list` 的无闸披露行为。

**⑪ 契约声明的第 4 步「资源状态与敏感级别」校验完全未实现**
契约 L25「4) 资源状态与敏感级别」；`AgentIdentity.maxSensitivityLevel`（L28/L126）、`AgentEntity` L48 列、
`IdentityResponses` L123/141 全程**只持久化与回显**；全后端 Grep `sensitivity` 的命中项**无一在授权链上**
（仅 asset 元数据存取、`AssetSearchDao` L128/199/401 facet/指纹、`ValidationJobHandler` L120 `VAL-SEN-001` INFO finding）。
`McpResourceHandler` 与 `McpController` 亦无状态/级别判断。
→ 五步校验声明中第 4 步为空，`dualConstraint.failClosed` 的覆盖面被高估。

**⑫ Agent 工具白名单写入口无引用完整性（与 ⑳ 死权限码、㉑ 幻影 LOCKED 同族）**
`SystemAgentController` L133 `@PutMapping("/{agentId}/tool-allowlist")` → L138 `requirePermission(Permissions.AGENT_AUTHORIZE)`
→ L139-141 **只判 `tools != null`** → L149 `agentService.updateToolAllowlist(agentId, request.tools())`；
**不校验工具名是否存在于 `McpToolCatalog`**，也不拒绝对 `asset_publish_version`/`asset_delete` 这类被代码永久封禁的工具授权。
前端 `AgentsPage.tsx` L262-267 使用 `<Select mode="tags">` 自由文本输入（`zh.json`/`en.json` L676-677
`allowedTools`/`toolPlaceholder`），`AgentsPage.test.tsx` L19 以 `['asset_search','asset_get']` 为 fixture。
→ 管理员可写入任意幻影工具名并得到 2xx，形成「已授权但永不可用」或「已授权但不存在」的静默错配。

**⑬ 合规反向对照（证明上述偏差是缺陷而非标准不可达）**
· 工具名 11↔11、权限映射 11/11（含 `asset_get_upload_status` `ASSET_READ`+`write:true` 特殊口径）对齐；
· 限流参数一致：契约 L14-15 `60/10` ↔ `platform/security/AgentRateLimitProperties.java` L17 `requestsPerMinute=60`、L20 `burst=10`，
　且**确实双向生效**：`tools/call` 控制器 L164 `rateLimiter.tryAcquire(...)`、REST `/agent/*` 亦经同一 RateLimiter；
· `asset_delete` 的 `ASSET_DELETED`（L219）与实现一致；
· `deniedAudit` 错误码一致：契约 L43-45 `AGENT_ACCESS_DENIED`/`MCP_TOOL_NOT_ALLOWED` ↔ 目录 L232-246、控制器 L148-150；
· 未认证 fail-closed 一致：契约 L33-38 ↔ 目录 L366-372 `currentPrincipalId` 抛错、handler L66-70。

**根因**：与 ⑲⑳㉑㉒ 同源 —— 契约被当作「文档」而非「受控输入」，
缺少「`tools.yaml` 字段 ↔ `ToolDefinition` 记录 ↔ JSON Schema 输出 ↔ 授权链步骤」的逐字段守卫测试；
`tools.yaml` L8 `contractStatus: implemented-p4-synced` 在 ①②③④⑤⑥⑨⑩⑪ 多项未落实的口径下构成**过度声明**，
该状态位本身也应随本项一并复核。

**整改待批（AI 不自决）**：① 裁决 `asset_publish_version` 语义——改契约 `auditEvent` 为 `PUBLISH_REQUEST_SUBMITTED`
+ 工具改名/改描述以反映「提交发布请求」，或改后端使其真正走 `VersionApplicationService` 的发布路径（两条路径审计含义不同，不得擅自择一）；
② 契约补 `asset_publish_version`/`asset_delete` 的 `parameters`；③ `schema()` 透传 `required`、补 `tagId` 的 `pattern`、
`str()` 侧对必填缺失早失败（改后端 + 契约同步）；④ 裁决 Scope/Permission 是否真为两维：要么在 `ToolDefinition` 增加
`requiredScopes` 并让 `AuthorizationService` 分别校验，要么删除契约 L24 第 2 步与 `dualConstraint` 中的 Scope 表述（改契约需谨慎）；
⑤ `tools/list` 改用 `isToolAllowed` 过白名单（对齐契约 L39-42），或把契约改为「列表全量下发、调用点校验」并同步安全评审；
⑥ 裁决高风险工具是「代码永久封禁」还是「可显式授权」：前者须改契约 L206/L216/L52-57 表述，后者须移除 `AGENT_DENIED_TOOLS` 的硬编码；
⑦ 明确 `asset_get_upload_status` 的 write 语义：改契约补 `idempotency: required`，或后端拆分 write 与 idempotency 两维标记；
⑧ `resources` 族补入契约（含 4 个 uriTemplate 与各自的鉴权前置），并给 `handleResourcesList`/`handleResourcesRead` 补 `mcp:invoke` 校验、
把裸字面量替换为 `Permissions.ASSET_READ`；⑨ 实现或撤销契约 L25 第 4 步「资源状态与敏感级别」校验；
⑩ 白名单写入口补「工具名必须存在于目录」的引用完整性校验（前后端双侧）；
⑪ 新增「MCP 契约字段 ↔ 目录定义 ↔ JSON Schema ↔ 授权链」四方一致性守卫测试（与 ⑲⑳㉑㉒ 守卫合并处置）；
⑫ 复核并调整 `tools.yaml` L8 `contractStatus` 状态位，使其不超出实际实现范围。
→ 本项与 ①—㉒ 一并不在本轮 AI 自决实施范围内。

### ㉔ i18n 三方差集（zh/en 资源 ↔ 代码引用 ↔ 契约/后端/数据库产出的 i18nKey）：合规面很强，缺陷面集中在错误与数据两个平面

取证时间 2026-10-12。方法：以 Node 脚本对 `frontend/src/shared/i18n/{zh,en}.json` 做键集扁平化与插值占位符集合比对；对 `frontend/src` 全量 `.ts/.tsx`（剔除注释行）扫描 `t('…')` 字面量与 `t(\`…${}\`)` 动态前缀；对 `backend/src/main/java`、`backend/src/main/resources/db/migration`、`contracts/openapi/aihub-v1.yaml`、`docs/ai-spec/02-domain/error-catalog.md` 分别抽取产出的 i18nKey，再与前端键集、`system_i18n_message` 表实际行做差集。共跑 6 轮探针，其中三轮产生并已由人工复核推翻的假阳性（见下）。

**结论先说**：`NFR-UI-003`（`01-requirements/non-functional-requirements.md` L92，证据等级 E4，判据为「菜单、按钮、错误、字典、通知和向导不硬编码业务文案」）在**界面静态文案面已实质达成**，在**错误面与数据面结构性未达成**；`AUD-008`（`00-governance/current-state-audit-2026-07-02.md` L32）自述「i18n 已接入」与 `TASK-P0BR-041` 的 E4 结论**判据过宽**，二者均仅由界面文案面支撑。

**合规面（量化，作为限定更正的正面依据，不得被本项缺陷叙述掩盖）**
- `zh.json`/`en.json` 各 **802** 键、18 个顶层命名空间；`KEYS-ZH-MINUS-EN = 0`、`KEYS-EN-MINUS-ZH = 0`；两侧插值变量集合差 `ZH-EN-VARSET-DIFF = 0`、单键占位符数量差 `PLACEHOLDER-COUNT-DIFF = 0`。
- 生产码 92 个文件、735 处直接 `t('key')` 引用，`REFS-NOT-IN-ZH = 0`（无引用缺失键）。
- `en.json` 中混入 CJK 的取值 `CJK-VALUE-IN-EN = 0`；非注释前端代码中硬编码 CJK 仅 3 行。
- 3 处模板字面量动态键（`t(\`pre.${x}\`)`）的子键全部存在。
- antd 组件库 locale 随语言切换：`AppProviders.tsx` L33 依 `i18n.resolvedLanguage` 选 `enUS/zhCN`，L37 注入 `ConfigProvider`。
- 语言切换实现完整：`shared/i18n/index.ts` L13 声明支持语言、L19-23 建立 `zh→zh-CN`/`en→en-US` 映射、L44-49 导出 `currentAcceptLanguage()`、L52-59 `setLanguage()` 同步 i18next + 请求头 + 持久化、L65-73 `fallbackLng: 'zh'`。
- `navigation.ts` L21-53 的 25 个导航项以 `labelKey` 存键名、由 `AppLayout.tsx` L41/L46 渲染时求值，符合文件头 L4/L13 自述的「运行时切换即时生效」设计。

**缺陷面 A（最重）— 错误码 i18n 链条三面断裂，客户端无可解析目标**
1. 后端产出侧齐备：`shared/error/ErrorCode.java` 定义 **76** 个枚举，每个携带 `error.*` 键（形如 `COMMON_INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "error.common.invalidArgument", false, AlertLevel.NONE)`）；`bootstrap/GlobalExceptionHandler.java` L36-47 经 `errorCode.defaultI18nKey()` 把键**原样写入响应体 `i18nKey`**，L56-68 兜底分支 `message` 硬编码英文 `"Internal server error"`。服务端**不做**本地化。
2. 契约与规范同口径：`error-catalog.md` L254 明示「服务端不依据 Accept-Language 渲染 message，本地化由客户端按 code / i18nKey 完成」；L9 REQ-COM-001；L16-208 各域表声明 **78** 个 i18nKey（76 与 `ErrorCode` 对齐，另 `error.common.unknown`/`error.common.network` 按 L258-262 属前端本地 fallback 键）。
3. 前端消费侧完全缺失：`zh.json`/`en.json` 的 `error.*` 键数 = **0**（18 个顶层命名空间中无 `error`）；`shared/api/client.ts` L128 仍产出 `body.i18nKey ?? 'error.common.unknown'`、L142 产出 `'error.common.network'`，两个键自身也不存在。全部页面对错误的展示口径是 `error.message`（即服务端英文开发者文本），而非按 `i18nKey` 解析 —— 例：`NotificationsPage.tsx` L80 `onError: (error) => message.error(isApiError(error) ? error.message : …)`。
4. 数据库亦无兜底：`system_i18n_message` 中 `error.*` 行数 = **0**，故「服务端不渲染 + 客户端无表 + 数据库无行」三重同时为空，`error-catalog.md` L254 所设想的本地化路径**在三个制品中都没有落点**。
5. 附带矛盾（必须上报的冲突，AI 不择一）：`client.ts` L64 注释称设置 `Accept-Language` 是「使后端错误与字典文案按所选 locale 返回（AC-P0B-UI-005）」，与 `error-catalog.md` L254 直接冲突；`PrincipalContextFilter` L44/L62/L138-143 实际只把 Accept-Language 解析后写入 MDC，不参与任何文案渲染。注释所引 AC 的达成方式与规范定义不一致。

**缺陷面 B — `TASK-P0BR-041` 的交付声明与 E4 结论无物证**
`02-delivery/work-breakdown.md` L144 声称该任务交付「后端 `messages_zh/en.properties` + 前端 `zh.json`/`en.json` 中全部 **56** 个 `error.*` key + 2 个前端额外 key（DEC-019）」。实测：
- `Glob backend/src/main/resources/**/messages*` → **No file found**，后端不存在任何 messages 资源文件；
- 前端 `error.*` 键数 = 0（见缺陷面 A.3），「2 个前端额外 key」亦即 L128/L142 那两个同样不存在的键；
- 56 与实际 76（`ErrorCode`）/78（`error-catalog`）两个口径均不符。
`error-catalog.md` L272 自身仍标注「状态：待 TASK-P0BR-001 与 TASK-P0BR-041 实施时补充」。→ 该任务卡若已记 E4 PASS，属**证据与产物不符**，应由验收方复核撤回，而非由 AI 改写规范或补造资源文件。

**缺陷面 C — 数据面 i18nKey：解析通道在库侧存在但完全未接通，前端改为直出原始键名**
1. 键的产出：Flyway 种子向 `system_i18n_message` 写入 **49** 个 distinct `dict.*` 键，且 **49/49 同时具备 zh-CN 与 en-US 两行**（`V6__taxonomy_dictionary.sql` L150-…、`V35__dataset_dict_seed.sql` L42-…，`V6` L81 有 locale CHECK 约束、L82 有 `(locale, message_key)` 唯一约束、L31/L90 字段注释明确「前端据此从 system_i18n_message 取展示文案」）。后端另有 **11** 个 `notification.*` 字面量作为通知的 `i18nKey` 落库：`notification.version.published/approved/rejected/deprecated/review_requested`（`PublishApplicationService` L159/L260/L281/L322/L362、`VersionApplicationService` L148/L179）、`notification.discussion.mentioned/replied`（`DiscussionApplicationService` L302/L322）、`notification.storage.quota.warning`（`MinioStorageReconciler` L157）、`notification.job.dead.title`（`JobNotificationAdapter` L45）、`notification.system.dependency.unhealthy`（`SystemDependencyService` L114）—— 这 11 个在 `system_i18n_message` 中**行数为 0**。
2. 通道存在但零接线：`taxonomy/infrastructure/JdbcI18nMessageRepository.java` L28-36 正确实现 `findAllByLocale`，`taxonomy/application/I18nQueryService.java` L32-36 提供 `messages(locale)` 且 L16 注释自承「**前端集成不在本任务范围**」。但全仓 Grep 显示该类在生产码中**无任何消费方**（仅 `TaxonomyIT.java` L14/L64、`TaxonomyLifecycleIT.java` L12/L63 两处测试注入）；`contracts/openapi/aihub-v1.yaml` 中**不存在任何 i18n/messages 端点**（Grep `/i18n|messages:` → No matches）；前端亦无对应调用。
3. 前端表现：契约把 `i18nKey` 作为 DTO 字段直发（`aihub-v1.yaml` L4129/L4237/L4246/L4258/L4283/L4308/L4400 等 `required: […, i18nKey]`），前端原样渲染 —— `DictionariesPage.tsx` L85 `{ title: t('admin.dictionaries.i18nKey'), dataIndex: 'i18nKey' }`（已复核 L83-94：列标题被 `t()` 求值，但**单元格内容即 `dataIndex: 'i18nKey'` 的原始值，无任何 render 解析**）；`NotificationsPage.tsx` L90 同构；`PermissionsPage.tsx` L29、`TagsPage.tsx` L95 同构。即用户在字典、标签、权限、通知四个管理页看到的是 `dict.license_catalog.APACHE_2_0`、`notification.version.published` 这类键名本身。
4. 违规判定：`02-domain/entity-invariants.md` L106 `INV-DICT-003`「item 必须有 i18nKey，MVP 至少有 zh-CN/en-US 文案」—— 库侧文案其实**已满足**，断裂发生在展示层未解析；`REQ-TAX-001`（`01-requirements/p0b-platform-services.md` L43，另见 L62/L392）与 `traceability-matrix.md` L24 `CAP-TAX-001 = PARTIAL` 口径与此一致（PARTIAL 成立，但理由应更正为「展示层未接通」而非「数据未备」）。
5. 交叉差集：`system_i18n_message` 的 49 个 `dict.*` 键与前端 `zh.json` 键集交集 = **0** —— 两套 i18n 体系（数据库表 / 前端静态资源）完全平行、无同步机制，属新增的引用完整性缺口。

**缺陷面 D — 35 个 zh/en 双侧同存的死键**
`common.search/confirm/loading/retry/loadFailed`；`nav.review/roleBindingManagement/resourceAclManagement`；`assets.deleteAsset`；`assets.detail.repoInfo/repoName/link/sensitivityCode/knownRisks/usageRestrictions`；`assets.create.ownerTeamId/ownerTeamLabel/ownerTeamHint`；`assets.facets.licenses`；`assets.deprecation.pipInstall`；`discussion.loadMore/replies`；`upload.assetIdPlaceholder/versionIdPlaceholder`；`version.assetIdPlaceholder/findings`；`review.loadingDetails`；`integrations.protocol/endpoint/writeTools/disabledByDefault/writeToolsNote/readOnlyTools/writeToolsTitle`；`access.description`。数量对称（zh/en 各 35，不影响缺陷面 A 的零差集结论），属维护噪声与 `page-catalog` 迭代残留；与 ⑯ 的路由/页面差集存在共同根因，宜合并处置。

**取证过程偏差登记（不得作为缺陷写入，但影响后续探针可信度）**
- pass-1 报「60 个死键」，其中 25 个 `nav.*` 为**假阳性**：它们经 `navigation.ts` 的 `labelKey` 字符串 + `AppLayout.tsx` 的 `t(item.labelKey)` 间接引用，而首轮正则只匹配 `t('…')` 字面量。pass-2 改为对整个 `frontend` 目录做引号字面量全扫描后收回，得真死键 35 个（与 ㉒ 的三轮假阳性排除同法登记）。
- pass-3 报 `review.decisionSubmitted` 缺插值参数（`needs{decision} got{}`）为**假阳性**：提取选项对象的正则只识别 `key:` 冒号形式，漏判 ES6 简写属性。Read `version/ReviewPage.tsx` L220-228 已证其写法正确（`t('review.decisionSubmitted', { decision })`）。
- pass-6 报「前端 i18n/messages 端点调用方 = 2」为**假阳性**：正则跨越了多行字符串边界，两条命中实为 `ArtifactContentPanel.test.tsx` 的 `ApiError` 夹具与 `shared/api/errors.ts` 的类定义文本，均非网络调用。已用 `contracts/openapi` 端点缺失（缺陷面 C.2）独立反证真实结论为「零调用方」。
- 前序会话曾记「`dict.*` 51 + `notification.*` 20 = 147 个不可解析键」，本轮以精确正则复算更正为 **49 + 11**（原 51 含 2 个被 locale 上下文误捕的字面量；原 20 混入了 `perm./tag./validation.` 等非 `notification.` 命名空间）。本节以 49/11 为准。

**根因（与 ⑲⑳㉑㉒㉓ 同源）**：缺少「契约/后端/数据库产出的 i18nKey ↔ 前端资源键集 ↔ 实际渲染点」的引用完整性守卫。三个制品各自演进：`ErrorCode` 增删枚举、Flyway 增删字典种子、`error-catalog` 增删行时，都不触发任何一侧的校验；`error-catalog.md` L272 的「待补充」状态位与 `AUD-008`/`TASK-P0BR-041` 的正面结论并存而无人复核，说明**判据只覆盖了界面文案这一最易观测的面**。

**限定更正**：`00-governance/current-state-audit-2026-07-02.md` L32 `AUD-008` 的「i18n 已接入」应限定为「**前端界面静态文案面**已接入（zh/en 键集对称、引用零缺失、antd locale 联动、语言切换持久化）」，不覆盖错误面（缺陷面 A）与数据面（缺陷面 C）；`02-delivery/traceability-matrix.md` L32 `CAP-UI-001` 与 L24 `CAP-TAX-001` 的 PARTIAL 理由需按本节更正。本项不修改上述规范文件（禁改面），仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 裁决错误面落点 —— 前端补 76+2 个 `error.*` 键（需同步 `error-catalog.md` 与 `ErrorCode` 的 78/76 口径差），或改由后端按 Accept-Language 渲染并同步修改 `error-catalog.md` L254（两条路径互斥，不得擅自择一）；② 统一 `error-catalog.md` 78 与 `ErrorCode` 76 的口径，并说明 `error.common.unknown`/`error.common.network` 是否计入契约；③ 修正 `client.ts` L64 注释与 `error-catalog.md` L254 的矛盾，或删除该注释所引 AC-P0B-UI-005 的错误关联；④ 由验收方复核并撤回 `TASK-P0BR-041` 的 E4 结论（缺后端 messages 文件、缺前端 `error.*` 键、数量 56 与事实不符）；⑤ 为 `dict.*`/`notification.*` 数据面补展示层解析：新增 OpenAPI i18n 批量端点并让前端消费，或改由后端在 DTO 中直接返回已解析文案（须先补契约，不得静默改 DTO 语义）；⑥ 补 `system_i18n_message` 中 11 个 `notification.*` 键的 zh-CN/en-US 行（新迁移 V45+，不得改已应用迁移）；⑦ 建立「`system_i18n_message.message_key` ∪ 契约 i18nKey ∪ `ErrorCode` 键 ↔ 前端可解析源」三方引用完整性守卫测试（与 ⑲⑳㉑㉒㉓ 守卫合并处置）；⑧ 清理 35 个死键；⑨ 按上述「限定更正」修订 `AUD-008`、`CAP-UI-001`、`CAP-TAX-001` 判据与 `error-catalog.md` L272 状态位；⑩ 复核 `NFR-UI-003` 的 E4 证据是否需从「已达成」下调为「界面面达成、错误面与数据面未达成」。
→ 本项与 ①—㉓ 一并不在本轮 AI 自决实施范围内。

### ㉕ 权限目录五方差集（规范 ↔ `Permissions.java` ↔ Flyway 种子 ↔ OpenAPI `x-required-permissions` ↔ 前后端门禁）：目录本体零差集，缺口全部集中在「码到操作的绑定」与「跨发布路径的权限不变量」

**取证范围与方法**：五方分别是 `docs/ai-spec/02-domain/*`（规范）、`authorization/domain/Permissions.java` L19-87（常量）、`backend/src/main/resources/db/migration/V*.sql` 中 `INSERT INTO iam_permission` 语句块（种子）、`contracts/openapi/aihub-v1.yaml` 全部 135 个 operation 的 `x-required-permissions` 扩展（契约绑定）、后端 `requirePermission` 调用点与前端 `routes.tsx`/`navigation.ts` 门控（实现）。

**合规面（本轮最强项，须如实登记）**：

1. **权限目录本体完全对齐**：`Permissions.java` 35 个常量 ↔ `iam_permission` 种子 35 个 distinct code（V4 L156-194 共 32，V12 L69 `asset:manage`，V14 L17/L21 `asset:discuss`/`asset:moderate`，V20 L20-23 `team:manage` 等）**双向差集为 0**；DB 中不存在任何 `Permissions.java` 未定义的码，反之亦然。V12/V14/V20 对同码的重复插入均带 `ON CONFLICT (code) DO NOTHING` 或 `WHERE NOT EXISTS`，属幂等漂移修复（V14 L3 自述「修复 V4 权限种子漂移」、V20 L2 引 AUD-006/016/017），**不是缺陷**。
2. **契约绑定覆盖率极高**：135 个 operation 中 **126 个（93.3%）显式声明 `x-required-permissions`**，且**无任何一处声明为空数组**；未声明的 9 个全部为 AGENTS.md「匿名访问白名单」及其直接后果 —— `POST /auth/login`、`/auth/refresh`、`/auth/token`、`/auth/agent/token`（L48-L132，登录与凭据交换）、`POST /auth/logout`、`GET /me`、`PUT /me/password`（L162-L198，自服务）、`POST /webhooks/gitea`（L3624，签名校验而非 JWT）、`POST /mcp`（L3648，见第 4 条）。该分布与 fail-closed 要求一致。
3. **失败关闭可证**：`AuthorizationService.isPermitted` L110-125 在 context / principalId / code 任一为 null 时直接返回 false；Agent 命中 `Permissions.HIGH_RISK_ACTIONS`（L92-93 = `asset:publish`/`asset:delete`/`system:configure`）一律拒绝；未发现 allow-all、null principal 或 default-all-scopes 兜底。
4. **MCP 平面为双层校验而非缺失**：`McpController` L123/L154 先校 `mcp:invoke`，L155 再逐个校 `toolDef.requiredPermission()`，L156-158 对 AGENT 追加白名单校验。故 `POST /mcp` 契约侧无 `x-required-permissions` 不构成越权面（㉓ 已单独证明 11/11 工具权限映射零差集）。
5. **前端导航门控码全部合法**：`navigation.ts` L21-53 的 14 个 `requiredScopes` 与 `routes.tsx` L41-91 的 35 条 `guarded(...)` 所需码**均落在 35 码目录内**，无越界码。

**缺陷面 A（最重）：跨路径的发布权限不变量不成立 —— `PENDING_REVIEW → PUBLISHED` 可被 `asset:manage` 单独触发，而该码授予了 ASSET_AUTHOR。**

- 规范 `02-domain/state-machines.md` L110-123 给出的唯一进入 `PUBLISHED` 的边是 `PENDING_REVIEW | saga success | PUBLISHED | Tag+Commit+Digest 原子业务结论`，L122-123 进一步规定「发布 Saga 失败不能把版本标成 PUBLISHED」。
- 实现中通往该状态存在**两条路径**，权限要求不同：
  - 治理路径：`PublishApplicationService.submitDecision` L174 → `Permissions.ASSET_PUBLISH`（`asset:publish`），并经 `PublishJobHandler` L141/L158 调 `Version.publish(...)` 冻结 Tag+Digest；`asset:publish` 在 V4 种子 L181 描述中即标注「（高风险）」，且**未绑定 READER / ASSET_AUTHOR / OBSERVER 任何内置角色**（V4 L215-217、L223-225、V23 L36-52），只经 V4 L208-210 的 ADMIN 通配获得。此路径与规范一致。
  - 直转路径：`VersionController` L100-110 `POST /api/v1/assets/{assetId}/versions/{versionId}/transition` → `VersionApplicationService.transitionVersion` L124-128 → `VersionStatus.assertTransitionTo`，其 L27 明确允许 `case PENDING_REVIEW -> target == PUBLISHED`；而 L125 的守卫是 **`Permissions.ASSET_MANAGE`**。
- 不变量破裂链条（逐环有据）：`asset:manage` 经 **V12 L76-78 显式绑定 `rol_asset_author`** → ASSET_AUTHOR 可调用该 transition 端点 → 对处于 `PENDING_REVIEW` 的版本提交 `targetStatus=PUBLISHED` → `assertTransitionTo` 放行 → `versionRepository.update(v)` 落库；DB 侧 `V16 L30-32 ck_asset_version_status` 只校验状态值合法、不校验冻结字段，故 `git_tag`/`manifest_digest`/`published_by`/`published_at` 全为 NULL 的行**可成功写入**。
- 该路径**同时绕过三重控制**：`asset:publish` 权限位、`HIGH_RISK_ACTIONS` 对 Agent 的封禁（transition 校验的是 `asset:manage`，不在集合内，故 AGENT 主体只要持有 `asset:manage` scope 即可直转）、以及「发布必须经 Saga 冻结摘要」这一核心不变量（AGENTS.md 固定基线：「Published versions are immutable and identified by Git Tag + Commit SHA + Manifest/DVC Digest」）。
- 契约与前端**均承认该可达性**：`aihub-v1.yaml` L3051-3061 对 transition 声明 `x-required-permissions: [asset:manage]`，`TransitionVersionRequest.targetStatus` L5299-5301 枚举包含 `PUBLISHED`（即契约明文承诺调用方可直达发布态）；前端 `VersionPage.tsx` 未暴露该按钮（L208/L219/L242 仅提供 `VALIDATING`/`DEPRECATED`/`ARCHIVED`），但这只是界面缺省，**REST 面完全开放**。
- 唯一在测试中触碰该组合的用例是 `VersionApplicationServiceTest` L179-187，且其前置状态为 `DRAFT`，断言结果为 `ConflictException` —— **它证明的是 `DRAFT→PUBLISHED` 被拒，完全未覆盖 `PENDING_REVIEW→PUBLISHED` 这一合法转换下的权限不足问题**。故现有测试集对该缺陷是盲的。
- 附带同类面：`assertTransitionTo` L29 允许 `DEPRECATED → PUBLISHED`（`VersionTest` L79-88 将其固化为期望行为「deprecatedVersionCanBeRestoredToPublished」），而规范 L119 只定义了 `PUBLISHED → DEPRECATED` 一条边，未定义反向恢复；该回边同样只需 `asset:manage`。
- 登记性质：`VersionApplicationService.publishVersion` L157-159（同样用 `ASSET_MANAGE` 而非 `ASSET_PUBLISH`，L162 调 `v.publish`）在**生产码中无任何调用方**（全库仅 `VersionApplicationServiceTest` L203-209 引用，无 Controller/Job 入口），属**死代码 + 潜在越权种子**：一旦被接线即成为第二条免 `asset:publish` 的发布路径。本轮据此**推翻**上一阶段的「疑似越权」初判表述，改按「越权面存在于 transition 端点、`publishVersion` 为未被触发的同源缺陷」定性。

**缺陷面 B：契约声明了目录外权限 `publish:approve`，该发布决策入口因此不受任何权限校验。**

- `aihub-v1.yaml` L3137-3140 `POST /publish-requests/{requestId}/decisions` 声明 `x-required-permissions: [publish:approve]` —— 这是 126 个声明中唯一 1 个落在 35 码目录**之外**的码（`resource=publish` 在目录中不存在）。
- 实现侧 `PublishApplicationService.submitDecision` L174 实际校验 `Permissions.ASSET_PUBLISH`；由于 `isPermitted` 的 Scope 分支（L119-120）只在 `ctx.scopes()` 里查字符串，`publish:approve` 永不命中，RBAC 分支（L121）也解析不到（DB 无此码），**故若按契约实现拦截器将永远 403**。
- 该端点由前端 `ReviewPage.tsx` L223 直接调用、`version/api.ts` L73-78 亦有封装，即审核决策主链路。冲突必须上报（契约 `publish:approve` ↔ 实现 `asset:publish`），不得擅自择一。
- 同一功能存在**两套并存路径**：契约同时提供 `/assets/{assetId}/versions/publish-requests`（L2908）+ `/assets/{assetId}/versions/publish-requests/{requestId}/decisions`（L2933）与 `/versions/{versionId}/publish-requests`（L3109）+ `/publish-requests/{requestId}/decisions`（L3137）；前端 `ReviewPage.tsx` L211/L223 走后者、`api.ts` L83-88 走前者。

**缺陷面 C：4 个已种子化权限在后端主代码中不参与任何鉴权决策（目录冗余）。**

- 按主代码引用数取证：`asset:upload`、`asset:download`、`asset:submit` **主代码零引用**（各仅 2 处测试引用）；`asset:review` 仅 1 处，且位于 `NotificationRecipientResolver` L43 用于**解析收件人**而非鉴权。
- 真实门禁被更宽的码取代：上传走 `ASSET_MANAGE`（`UploadApplicationService` L99/L162/L185/L239）、下载走 `ASSET_READ`（`DownloadStatsService` L54、`DownloadStatsController` L41、`UploadApplicationService` L129/L137/L258）、提交发布申请走 `ASSET_MANAGE`（`PublishApplicationService.submitPublishRequest` L81）。
- 后果是**授权面比目录设计更宽且不可观测**：V4 L223-225 给 ASSET_AUTHOR 绑定了 `asset:upload`/`asset:download`/`asset:submit`，但真正决定其能否上传的是 V12 L77 的 `asset:manage`；反之，收回 `asset:submit` 不会阻止作者提交发布申请。契约侧亦无这三个码（`JAVA-MINUS-CONTRACT` 8 项含 `asset:upload`/`asset:download`/`asset:submit`/`asset:review`）。
- `JAVA-MINUS-CONTRACT` 另含 `asset:publish`（治理路径主用、契约一处未提，见缺陷面 B）、`asset:moderate`、`asset:deprecate`、`mcp:invoke`。`asset:deprecate` 同时存在**码漂移反向**：契约 L2104/L2158 的 `/assets/{assetId}/deprecate`、`/archive` 声明 `asset:update`，而 `PublishApplicationService` L345/L373 用 `ASSET_MANAGE`。

**缺陷面 D：`02-domain` 无权限目录规范，规范面缺整维。**

- `docs/ai-spec/02-domain/` 仅 error-catalog、state-machines、entity-invariants、consistency-and-compensation 四文档，**没有 permission catalog**；8 篇规范合计只提及 30 个合法码，`DB-MINUS-SPEC` = `project:manage`、`authorization:manage`、`asset:manage`、`asset:discuss`、`asset:moderate`、`team:manage`（6 个已实现、已种子化、已用于契约绑定的码在规范中无定义与语义）。
- 因此「ASSET_AUTHOR 应否持有 `asset:manage`」「`asset:manage` 与 `asset:update`/`asset:delete` 的包含关系」这类判定**无规范基准**，缺陷面 A 的严重性分级只能依据 AGENTS.md 固定基线与 `state-machines.md`，需验收方补规范后复评。

**缺陷面 E：前端权限类型为开放模板，门控码与后端不同码。**

- `shared/types/permission.ts` L13 `export type Scope = \`${string}:${string}\`` —— 模板字面量而非 35 码联合，任何 `x:y` 串都通过类型检查。实测污染：`AccessPage.tsx` 色板使用 `'admin:all'`、`test/test-utils.tsx` 夹具使用**不存在的** `'asset:write'`，二者均无编译错误（㉔ 的「无引用完整性守卫」同源）。
- 门控码与后端实际校验码不一致：`routes.tsx` L64 `/upload`、L65 `/review` 要求 `asset:manage`（后端提交发布申请亦为 `asset:manage`，但审核决策为 `asset:publish`，前端未把 `/review` 与发布权绑定）；L75-77 `/organizations`、`/teams` 仅要求 `project:view`，而后端对应写操作要求 `organization:manage`/`team:manage`。`RouteGuard.tsx` L3 自承「前端守卫仅做体验提示，真实鉴权在后端」，故为体验偏差而非越权，但会使无权用户进入页面后收到 403。

**取证偏差登记（不得作为缺陷写入）**：

- probe-perm4 首轮 DB 提取报 **38 码**，其中 `asset:view`、`asset:compliance:export`、`audit:audit` 为**假阳性**：源于 `inPerm` 状态机在语句块结束判定上的漏退，把相邻非 `iam_permission` 语句的字面量并入；已用 probe-perm5 的严格语句块解析（按 `INSERT INTO iam_permission` 起始至行尾 `;`）复算为 **35**，并经全库 Grep 独立反证这 3 个码在后端全目录中根本不存在（0 命中）。落盘以 35 为准。
- probe-perm4 报「契约↔Controller 权限不一致」4 处中，`DELETE /system/roles/{}` 与 `GET /system/organizations{,/{id}/projects}` **3 处为探针伪影**：其方法体窗口取 14 行，越界捕获了相邻方法的 `Permissions.*`。Read `RoleController` L122-127（deleteRole 仅 `AUTHORIZATION_MANAGE` L124，`AUTHORIZATION_READ` 属 L132 的 listPermissions）与 `OrganizationController` L65-71（`requirePermission(PROJECT_VIEW)` L67 + `isPermitted(ORGANIZATION_MANAGE)` L68 用于可见范围判定，非门禁）后收回。
- 第 4 处 `POST /system/agents` 为**契约 YAML 形态导致的解析假象**：探针把子串 `'/system/agents/{agentId}:enable'`、`':disable'`（L453/L469，单引号风格 11 处之一）并入同名 path，误报重复 `agent:authorize`。Read L404-451 确认 `POST /system/agents` 唯一，声明 `agent:register`，与 `SystemAgentController` L79 一致 —— **收回**。
- 「`job:cancel`/`job:retry`/`job:dead` 为权限码」系 ㉓ 期间同类误判，已定性为审计 action 标签（`JobApplicationService` L94/L110、`JobAuditAdapter` L43），本节不重复登记。
- 「15 个 DB 码未绑定角色」为**误导性表述**：V4 L208-210 的 ADMIN 通配 `SELECT 'rol_admin', permission_id FROM iam_permission` 使全部 35 码由 ADMIN 获得；准确说法是「未在任何内置角色中被**显式**列举」。

**根因（与 ⑲⑳㉑㉒㉓㉔ 同源）**：五方制品各自演进且无交叉守卫 —— 新增状态转换边（`VersionStatus` L27）不触发「该边终态是否要求高风险权限」的检查；新增/绑定权限码（V12 给 author 绑 `asset:manage`）不触发「是否与 `HIGH_RISK_ACTIONS`、state-machines 规范冲突」的检查；`x-required-permissions` 是自由字符串扩展、无 schema 约束到 35 码枚举，故 `publish:approve` 可静默写入；前端 `Scope` 类型不做枚举化，故任意串可通过编译。与 ㉓「契约↔目录↔鉴权↔白名单」缺守卫是同一类问题在权限维度的复现。

**限定更正**：㉑（状态机四方差集）当时把 `PENDING_REVIEW → PUBLISHED` 归为「实现与规范一致的正常边」，本节据缺陷面 A 更正为「该边本身合法，但承载它的端点使用了错误的权限码，构成跨路径不变量破裂」；`AUD` 侧关于「fail-closed 与高风险动作封禁已落实」的结论应限定为「**在 `asset:publish` 治理路径上**已落实，在 transition 直转路径上未落实」。本项不修改任何禁改面文件，仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 裁决缺陷面 A 的收口方式 —— 在 `transitionVersion` 中禁止 `target=PUBLISHED`（并相应收窄 `TransitionVersionRequest.targetStatus` 契约枚举 L5301，属破坏性变更需 ADR）、或把 L125 的守卫改为按目标状态动态要求（`PUBLISHED`/`DEPRECATED→PUBLISHED` 时强制 `ASSET_PUBLISH`）、或在 `VersionStatus.assertTransitionTo` 层移除该边并同步 `state-machines.md`；三条路径互斥，须经 `Q-203` 与验收方裁决；② 复核并（如认定过宽）撤销 V12 L76-78 对 `rol_asset_author` 的 `asset:manage` 绑定（须新迁移 V45+，不得改已应用迁移），或反向为 `asset:manage` 补规范定义并说明其与 `asset:update`/`asset:delete`/`asset:publish` 的包含关系；③ 撤回或改接 `VersionApplicationService.publishVersion` L157-183（当前无生产调用方且权限码错误）；④ 统一 `publish:approve` ↔ `asset:publish` 口径（改契约或改实现，须先补契约变更）；⑤ 消除 publish-requests/decisions 的两套并存路径，保留其一并标 deprecated；⑥ 裁决 `asset:upload`/`asset:download`/`asset:submit`/`asset:review` 是接回真实门禁（改 `UploadApplicationService`/`DownloadStats*`/`submitPublishRequest` 用窄码）还是从目录中退役（新迁移 + 同步 `Permissions.java` 与契约）；⑦ 修正 `asset:deprecate` 在契约（`asset:update`）与实现（`ASSET_MANAGE`）间的漂移；⑧ 在 `02-domain` 补 permission catalog 规范（35 码 + 语义 + 内置角色基线矩阵），并纳入 `validate-spec.ps1` 的稳定 ID 体系；⑨ 前端 `Scope` 改为由契约生成的 35 码联合（替换 `shared/types/permission.ts` L13 模板字面量），并清理 `'admin:all'`、`'asset:write'` 等非法串；⑩ 建立「`Permissions.java` ↔ `iam_permission` 种子 ↔ 契约 `x-required-permissions` ↔ 后端 `requirePermission` 调用点 ↔ 前端门控」五方引用完整性守卫测试（与 ⑲⑳㉑㉒㉓㉔ 守卫合并处置），其中须包含「任何进入 `PUBLISHED` 的转换边必须由 `HIGH_RISK_ACTIONS` 内的码守卫」这一跨维断言；⑪ 为缺陷面 A 补**失败路径测试**（ASSET_AUTHOR 主体 + `PENDING_REVIEW` 版本 + `target=PUBLISHED` 应 403），现有 `VersionApplicationServiceTest` L179-187 不覆盖该组合；⑫ 按上述「限定更正」复核 ㉑ 的边合法性结论与高风险封禁类 `AUD` 判据。
→ 本项与 ①—㉔ 一并不在本轮 AI 自决实施范围内。

### ㉖ 审计事件维度四方差集（契约 `x-audit-event` ↔ 后端审计写出 ↔ `01-requirements`/`02-domain`/`05-acceptance` 规范 ↔ `V10__audit.sql`）：契约与代码各说各话，最重的缺口不在事件名而在「审计写失败的处理策略」与「权限拒绝无审计」

**取证范围与方法**：四方分别是 ① 契约方 —— `contracts/openapi/aihub-v1.yaml` 全部 135 operation 的 `x-audit-event` 扩展（60 处声明 / **59 个 distinct 事件名**，`CLIENT_TOKEN_ISSUED_OR_REJECTED` 一名绑 `POST /auth/token` 与 `POST /auth/agent/token` 两个 operation）、`contracts/openapi/aihub-agent-v1.yaml` 全部 9 operation；② 代码方 —— 全后端 41 个引用 `AuditService|AuditPort|new AuditEvent` 的 Java 文件，在审计方法白名单内提取的事件字面量（**68 个 distinct**）；③ 规范方 —— `p0b-platform-services.md` L249-276 `REQ-AUD-001 不可变业务审计`、`p0b-identity-authorization.md` L30-34、`dataset-experience.md` L167/L221/L269、`02-domain/entity-invariants.md` L222-225（`INV-EVT-005..008`）、`02-domain/state-machines.md` L32、`05-acceptance/p0b-exit-catalog.md` L105-109（`AC-P0B-AUD-001..005`）；④ 数据库方 —— `V10__audit.sql` L4 必审计范围宣称、L13 `event_type VARCHAR(64) NOT NULL`、L29 唯一 CHECK（仅 `result`）、L35 列表示例、L56 `idx_audit_log_event`、L58+ 不可篡改触发器。探针：`probe-audit1.mjs`（写 operation 的 `x-audit-event` 覆盖率）、`probe-audit3.mjs`（契约↔代码双向差集 + 独发事件 file:line + 同族前缀对照）、`probe-audit4.mjs`（15 个候选的全仓字面量回查、复合名原子实现验证、`deriveResourceType` 影响量化）—— 三者为一次性取证脚本，落盘后已从 `.trae/`（gitignored）清理，不进入制品库；本节所有结论均可由下列 file:line 独立复现，不依赖探针存在。

**合规面（先记已落实项，避免只报缺陷）**：

- ~~审计持久化入口单一：`AuditService.record()` 是后端唯一的 `audit_log` 写入通道~~ —— **本节落盘后被 ㉗ 同期取证推翻，更正如下**：全仓 `INSERT INTO audit_log` 共 4 处命中 —— `JdbcAuditRepository` L64（正途）、`version/infrastructure/PublishJobHandler.java` L264（**生产旁路**）、`PerformanceFixtureGenerator` L84（测试夹具）、`docs/ai-spec/tasks/TASK-P0BR-064.md` L119（文档）。即 `REQ-AUD-001` L260「AuditService 是唯一持久化入口」在生产代码层面即不成立，且该旁路为**已知缺陷**：`PublishJobHandler` L246 Javadoc 自认「直写 audit_log 而非经共享 AuditService 属旁路缺陷，收敛登记为后续任务（卡 §2 事实 6）」。旁路面另有三项独立缺陷（详见 ㉗ 缺陷面 B）：绕过 `SensitiveDataMasker`、`audit_id` 用 `UUID.randomUUID().substring(0,24)`（L252）而非 `IdGenerator`/ULID（与 `V10` L34 列注释「aud_+ULID」不符）、INSERT 列清单不含 `scope_*`/`duration_ms`/`request_id`。41 个写出者中经 `AuditService`/`AuditPort` 的部分仍成立（`auditService.record(`/`auditPort.record(` 全仓 51 个调用点）。`Permissions.java` L75 `AUDIT_READ = "audit:read"` + `AuditLogController.java` L54 `requirePermission(Permissions.AUDIT_READ)` —— 审计查询面自身有门禁，此点维持。
- `INV-EVT-005`（追加写、无 UPDATE/DELETE）与 `INV-EVT-007`（载荷脱敏）在 DB/服务层有对应机制：`V10` L58+ 不可篡改触发器 + `AuditService.maskSummary()` 对 `__` 前缀键过滤；`V10` L29 `ck_audit_log_result` 约束 `result` 枚举域。
- 登录/Token/客户端凭据域的原子事件齐全：`AUTH_LOGIN_SUCCEEDED`(L151)、`AUTH_LOGIN_FAILED`(L170/L183)、`AUTH_TOKEN_REFRESHED`(L231)、`AUTH_TOKEN_REPLAY_REJECTED`(L253)、`CLIENT_TOKEN_ISSUED`(L287)、`CLIENT_TOKEN_REJECTED`(L265/L271/L276) 全部位于 `AuthenticationApplicationService`，且失败路径用独立 `failureTransaction`（L170/L183 `executeWithoutResult`）写审计，符合 `REQ-AUD-001` L261「认证失败即使业务回滚也必须保留」。
- 字典域事件已发出：`TagApplicationService` L151/L159 通过 `setStatus(..., "TAG_ENABLED"/"TAG_DISABLED")` 写出，契约 L1287/L1303 对应声明一致（见「取证偏差登记」第 1 条）。

**缺陷面 A：契约声明的 13 个事件后端从未写出（按契约 `event_type` 精确过滤的检索恒空）。**

`probe-audit4` A 段对 15 个候选做全仓 `"NAME"` 字面量扫描，13 项 `hits=0`：`AGENT_REGISTERED`、`AGENT_ENABLED`、`AGENT_DISABLED`、`AGENT_TOOL_ALLOWLIST_UPDATED`（契约 L432/L462/L478/L494；`SystemAgentController` L73-153 四端点齐备，`AgentManagementApplicationService` grep `audit` 0 命中 —— **注册与启停两个治理动作完全没有审计**）；`COMMENT_EDITED`、`COMMENT_UNHIDDEN`、`THREAD_UNLOCKED`（契约 L2447/L2530/L2580；代码只有 `COMMENT_CREATED` L135、`COMMENT_RETRACTED` L209、`COMMENT_HIDDEN` L226、`THREAD_LOCKED` L258 —— **hide/unhide、lock/unlock 成对动作只审计前一半，编辑动作完全不审计**，治理动作审计不对称）；`NOTIFICATION_MARKED_READ`（契约 L1482；notification 包全目录 grep `audit` 0 命中）；`PUBLISH_REQUESTED`、`PUBLISH_DECISION_SUBMITTED`（契约 L3119/L3147；代码实发 `PUBLISH_REQUEST_SUBMITTED` L146、`REVIEW_DECISION_SUBMITTED` L234 —— **命名漂移**，属"有审计事实、事件名不匹配契约"）。

**缺陷面 B：契约中三个 `A_OR_B` 复合事件名不可实现，按契约检索恒空。**

`AUTH_LOGIN_SUCCEEDED_OR_FAILED`、`AUTH_TOKEN_REFRESHED_OR_REPLAY_REJECTED`、`CLIENT_TOKEN_ISSUED_OR_REJECTED`（契约 L55/L83/L111/L139 等）在后端**零发出**；`probe-audit4` B 段证实对应 6 个原子名全部存在（见合规面第 4 条）。复合名把「成功/失败」「签发/拒绝」两类语义压进一个 `event_type` 值，而 `audit_log` 已有 `result` 列（`V10` L29 CHECK）承担该维度，故复合名与既有 schema 语义重复且不可由原子实现满足 —— 任何按契约文档写的审计查询（如「登录失败事件」）返回空集，`AC-P0B-AUD-001` 的「必需事件均追加记录」在契约口径下不可判定。

**缺陷面 C（最重）：审计写失败一律被吞，直接违反 `REQ-AUD-001` L269-270。**

规范要求「高风险写（发布、授权、成员/角色/ACL、配置变更）fail closed 并回滚业务；低风险写允许经持久化缓冲重试；**任何分类都不允许仅 warn 后静默丢失**」。实现中捕获异常后仅 `LOG.error` 不外抛、不回滚的点共 **9 处**：`AuthorizationAuditAdapter` L53-56、`IdentityAuditAdapter` L61-64、`ConfigurationAuditAdapter` L52-55、`OrganizationAuditAdapter` L52-55、`TaxonomyAuditAdapter` L52-55，以及 `AssetApplicationService` L543、`AssetRelationApplicationService` L166、`DiscussionApplicationService` L370、`VersionApplicationService` L226、`UploadApplicationService` L351（后五处同形）。授权/角色/ACL/配置恰是规范列举的高风险写域，当前若 `audit_log` 插入失败（约束冲突、连接耗尽、触发器异常），业务照常提交 → **资产公开发布、角色提权、ACL 放开这类操作可以无审计落地**。全仓不存在「持久化缓冲重试」实现，故低风险路径也不合规。`AC-P0B-AUD-001`（MUST / E4）在此前提下不可通过。

  **㉗ 同期补充（同一规范下两种相反实现并存）**：与上述 9 处 `catch → LOG.error` 相对，`PublishJobHandler.recordAuditEvent`（L241-244 Javadoc）走的是**另一个极端** —— 「审计写入失败上抛（不再静默丢行）…抛出将使 Worker 重试整个 Saga」。即 `REQ-AUD-001` L269-270 的失败策略在实现中同时存在「一律吞」与「一律上抛并触发 Saga 整体重试」两种形态，二者均未按规范区分高/低风险写；后者还会把审计行的插入失败放大为整条发布 Saga 的重放（其安全性依赖 L244 自述的「P1 状态守卫与幂等 UPDATE」，该前提未在本轮实测）。`IdentityAuditAdapter` L23 Javadoc 更进一步把吞异常教义化：「审计失败仅告警，不回滚业务事务（审计为旁路）」—— 与 L270-271「任何分类都不允许仅 warn 后静默丢失」构成规范解释层面的直接冲突。

  同一缺陷面内还存在**实现自述与调用方行为的互相矛盾**（按 AGENTS.md「不得静默择一」予以登记）：`AuditService` L33-34 头注释声明「不使用 `@Transactional`：在无事务上下文时追加写自动提交、即使随后抛出异常也保留；**在业务事务内则加入当前事务随业务原子提交**」，L30 并声明「P0-B 采用同步落库，确保审计 100% 记录」。但 `record()` 本体（L66-93）无任何异常处理、直接 `repository.append(record)`（L91），而全部 9 个调用点把该调用包在 `try/catch(Exception)` 内并只记日志。两种运行形态因此都与自述不符：① 在**无事务上下文**时（如 `IdentityAuditAdapter` 的认证失败路径，`AuditService` L33 明说此时自动提交）异常被吞即等价于**静默丢失**，与 L30「确保审计 100% 记录」矛盾；② 在**业务事务内**（`@Transactional` 应用服务）时，`record()` 无自身事务代理、语句加入外层事务，插入失败后 PostgreSQL 事务进入 aborted 状态（后续语句报 `current transaction is aborted`），"吞掉异常继续业务"在此形态下并不能真的保住业务，最终要么以一个与审计无关的驱动错误失败、要么提交出一条没有审计行的业务变更 —— 两种结果都不等于规范要求的「高风险写 fail closed 并回滚业务」，也不等于「低风险写持久化缓冲重试」。注：②的运行时行为未在本轮实测（需 Compose + 故障注入），仅由 `record()` 无 `@Transactional`（L33 自述 + L66 签名）与调用方 catch 结构推出，验收方复核时应以故障注入测试定论。

**缺陷面 D：权限拒绝（`AUTH_PERMISSION_DENIED`）全域不写审计，规范、DB 注释、验收三处口径同时落空。**

`V10` L35 列注释以 `AUTH_PERMISSION_DENIED` 作为 `event_type` 示例，`V10` L4 头注释宣称覆盖「被拒高风险」；后端 11 处命中该串**全部作为 `ErrorCode` 使用**（`AuthorizationService` L91/L241 抛出、`RestAccessDeniedHandler` L34 写 403 响应体），而该 Handler 全文 36 行**无任何审计写出**，`requirePermission` 抛出路径上亦无审计切面；契约 grep `x-audit-event: AUTH_PERMISSION_DENIED` → 0 命中（从未被任何 operation 声明）。唯一存在的是 MCP 侧的 `AGENT_ACCESS_DENIED`/`AGENT_RATE_LIMIT_DENIED`（`McpController` L235/L219，硬编码在控制器私有方法内）。`AC-P0B-AUD-001` 明确要求「分别触发写操作成功、业务失败和**权限拒绝** → 必需事件均追加记录正确 result/errorCode」—— REST 权限拒绝目前 0 事件。

**缺陷面 E：规范列为必审计但全链零实现的域。**

`REQ-AUD-001` L264-265 列举 10 类必审计域须 100% 覆盖，其中 **Webhook 与人工任务/对账** 在代码中零审计写出：`WebhookInboxApplicationService` L75/L86/L97/L100/L106/L125 全为 `LOG` 语句，三个 `*Reconciler` 与 `PatToken*`/`PersonalAccessToken*` 类 grep `audit` 0 命中（41 文件写出者全集中不含这几类，可反证）。另有三项规范/任务卡宣称但无实现：`BOOTSTRAP_ADMIN_CREATED` 全库仅 2 处文档（`p0b-identity-authorization.md:34`、`TASK-P0BR-010.md:34`），`BootstrapAdminInitializer` L62-106 只有 `LOG.info` + L101 建用户，无审计；`ASSET_FAVORITE_ADDED/REMOVED`（`dataset-experience.md` L221，需 V45+ 迁移与实现）、`ASSET_SUBSCRIBED/UNSUBSCRIBED`（同文档 L269，`DiscussionApplicationService.subscribe` L147-150 / `unsubscribe` L154-157 仅 `requirePermission(ASSET_READ)` + 仓储调用，无审计）。

**缺陷面 F：Agent/MCP 契约侧审计与权限双零。**

`aihub-agent-v1.yaml` 9 个 operation（L33/L79/L102/L136/L159/L188/L228/L266/L320）**既无 `x-audit-event` 也无 `x-required-permissions`**，其中 4 个是写端点（版本下载、草稿创建、上传会话创建、会话完成）。即机器通道的下载与写入在契约层没有登记任何审计义务，而 `REQ-AUD-001` L264 的「下载」「资产」「版本」域并不区分人机通道；AGENTS.md 固定基线要求「REST、MCP、Worker 适配器必须复用同一应用服务、授权、状态机与审计逻辑」，当前 `McpController` 把审计事件名硬编码在控制器私有方法（缺陷面 D），与该基线的"复用同一审计逻辑"存在张力。

  **㉗ 同期补充（MCP 目录用了第三个键名）**：`contracts/mcp/tools.yaml` L209 `auditEvent: VERSION_PUBLISHED`、L219 `auditEvent: ASSET_DELETED` —— 该制品声明审计义务时使用的键名是 `auditEvent`，既非 OpenAPI 的 `x-audit-event`，也不在 `validate-spec.ps1` 的校验范围内；两工具当前均 `enabled: false`，故不影响运行期，但意味着**审计义务在三个契约制品里有三种写法（`x-audit-event` / `auditEvent` / 无）**，任何按统一键名做的守卫测试都会漏采。此条与 `aihub-agent-v1.yaml` 的双零属**不同制品、不同缺陷**，需分别登记与整改。

**缺陷面 G：后端独发的 24 个事件在契约中无处登记。**

`probe-audit3` 第 3 段给出全部 file:line + 方法名，典型如 `VERSION_CREATED`(L98)、`VERSION_STATUS_CHANGED`(L135)、`UPLOAD_MATERIALIZED`、`DVC_CREDENTIALS_ISSUED`(`DvcConfigurationService` L135)、`DVC_REMOTE_CONFIG_ISSUED`(L65)、`RESOURCE_ACL_CREATED/DELETED`、`JOB_DEAD`。其中两项后果具体：① ㉕ 已登记的越权路径 `POST /assets/{assetId}/versions/{versionId}/transition`（契约 L3055，`asset:manage` 可直达 `PUBLISHED`）**在契约侧亦无 `x-audit-event`**，其唯一审计痕迹 `VERSION_STATUS_CHANGED` 不在契约 59 事件内 —— 越权写入既不受权限守卫也不受契约审计义务约束；② `DVC_CREDENTIALS_ISSUED`/`DVC_REMOTE_CONFIG_ISSUED` 属凭据签发事实，`REQ-AUD-001` L264 明列「下载」与凭据相关域必审，契约未登记意味着前端与合规导出（`AC-P5-OBS-001` 基于 `audit_log` 聚合下载授权事件）查不到。

**缺陷面 H：75 个写 operation 中 14 个未声明 `x-audit-event`，且分两类性质。**

`probe-audit1` 输出：写操作 75 个，未声明 14 个（已排除匿名端点）。其中 **2 个属「代码有审计、契约漏登记」**：`POST/DELETE /assets/{assetId}/access`（L1909/L1955）经 `AssetAccessController` L74-80/L102-108 复用 `ResourceAclApplicationService`，实际发 `RESOURCE_ACL_CREATED`(L117)/`RESOURCE_ACL_DELETED`(L133)。其余 **12 个属「契约与代码双缺」**：`POST/DELETE /tokens`（L1572/L1622，`perm=token:create` —— PAT 生命周期无审计）、`POST /assets/{assetId}/discussions/subscription`（L2623，见缺陷面 E）、`POST /system/alerts/{alertId}:acknowledge`（L2679，`AlertService.acknowledge` L110-114 裸 UPDATE）、`POST /system/notifications/admin/deliveries/{deliveryId}:retry`（L2825，`NotificationAdminService.retryDelivery` L74-78）、`POST /assets/{assetId}/versions`（L2881）、`.../transition`（L3055，缺陷面 G）、`POST /assets/{assetId}/previews/generate`（L3257；`PreviewController` L60-63 仅 `requirePermission("asset:manage")` 后入队 `PREVIEW_GENERATE`，`PreviewApplicationService` 全文 grep `audit` 0 命中）、`.../upload-sessions`(L3412) 与 `:complete`(L3523)/`:cancel`(L3573；`UploadApplicationService.cancelSession` L238-242 无审计，而 create/complete 有 `UPLOAD_SESSION_CREATED` L121/`UPLOAD_SESSION_COMPLETED` L228 —— **同一资源三个生命周期动作只审计两个**)、`POST /mcp`（L3648，正常工具调用成功路径不写审计，仅拒绝/限流写，见缺陷面 D/F）。

**缺陷面 I：`event_type` 在 DB 层零约束 + 双事件命名空间不互校，使上述漂移无法被发现也无法被阻止。**

`V10` L13 `event_type VARCHAR(64) NOT NULL` **无 CHECK、无外键到任何事件目录**，任何 UPPER_SNAKE 串都能入库；叠加 L58+ 不可篡改触发器（禁 UPDATE/DELETE），错名一旦写盘即永久污染检索面（与 L56 `idx_audit_log_event` 精确匹配语义组合，检索恒空且无法纠正）。同时 `contracts/events/events-v1.yaml` L47-70 定义了 outbox 的 `EventType` 枚举（23 值）与 L75 `AggregateType`（7 值），与审计 `x-audit-event`（59 值）**部分同名但互不校验**：`VERSION_PUBLISHED` 在 `VersionApplicationService` L168 作为审计事件写出、又在 `PublishApplicationService` L172 `publishOutbox` 作为 outbox 事件；`COMMENT_CREATED` 同（`DiscussionApplicationService` L135 审计 / L137 outbox）。`INV-EVT-008` 要求「Runtime Log/Audit/Inbox/Outbox 职责独立、不互相替代」，当前实现靠同名串复用，任一侧改名都不会触发另一侧检查（该文件 L45 还明文要求「不复用或重命名既有值」，可见 outbox 侧有治理、审计侧无治理）。

**缺陷面 J：同一治理动作在规范、契约、代码三方三名，规范自身承认未定稿。**

评论修订：规范 `COMMENT_REVISED` ↔ 契约 `COMMENT_EDITED` ↔ 代码两者皆无；讨论串锁定：规范 `DISCUSSION_LOCKED` ↔ 契约与代码 `THREAD_LOCKED`；规范 `DISCUSSION_MODERATED` 无人发出。`02-domain/state-machines.md` L32 对 `LOCKED | unlock/expiry | ACTIVE` 自注「**解锁方式和审计事件待确认**」—— 这正是缺陷面 A 中 `THREAD_UNLOCKED` 缺失的规范根源；`consistency-and-compensation.md` L186 要求补偿修复须记审计事件，而三个 `*Reconciler` 零审计（缺陷面 E）。

**取证偏差登记（不得作为缺陷写入）**：

- `TAG_ENABLED`/`TAG_DISABLED` 曾被 `probe-audit3` 报为「契约有、后端无」，**为探针漏采假阳性**：其 L53 正则 `\b(method)\s*\(\s*"([A-Z][A-Z0-9_]{4,})"` 只匹配「紧跟左括号的首参」，而 `TagApplicationService` L151/L159 的事件名位于 `setStatus` 第 5 参。已用双路反证闭合（直接 Grep 命中 + `probe-audit4` A 段全仓字面量扫描 `hits=1` 并打印原始行）。故契约独缺由 15 收敛为 **13**，落盘以 13 为准。
- 「后端独发 24 项」以 `probe-audit3` 第 3 段为准。`probe-audit4` C 段自写清单含 `VERSION_APPROVED`/`VERSION_REJECTED`，经核仅出现在 `PublishApplicationService` L259/L260/L280/L281/L321/L322 的 `publishOutbox`/`notifySubmitter`（outbox 与通知，非审计），`probe-audit3` 的白名单已正确排除，**不计入审计独发集**。同理 `JOB_DEAD` 全仓 5 处中 `AlertService` L232/L235/L240 三处是告警键，审计事实仅 `JobWorker` L308 `port.record` 与 `JobNotificationAdapter` L44。
- `deriveResourceType()` 按「最后一个下划线截断」推导 `resource_type`，对段数 > 3 的事件名会推出错误资源类型；`probe-audit4` D 段量化为 **3 例**：`AGENT_RATE_LIMIT_DENIED→AGENT_RATE_LIMIT`、`AUTH_TOKEN_REPLAY_REJECTED→AUTH_TOKEN_REPLAY`、`DVC_REMOTE_CONFIG_ISSUED→DVC_REMOTE_CONFIG`。`McpController` L219 已显式传 `"MCP_TOOL"` 规避。此为设计弱点而非本轮新增缺陷，仅登记量级。
- `probe-audit3` 第 5 段「同族前缀对照」（如 `COMMENT_*: CODE=[CREATED,HIDDEN,RETRACTED] vs CONTRACT=[CREATED,EDITED,RETRACTED,HIDDEN,UNHIDDEN]`）为人工判读辅助，其每项结论均已逐条回到 file:line 复核后方写入本节。
- 探针方法固有局限：代码方提取依赖"事件名出现在审计方法白名单调用的字面量位置"，若存在经变量、常量或配置传入的事件名则漏采；已用 41 文件写出者全集（不含 preview/notification/pat/reconciler/webhook/bootstrap 任一类）反向交叉验证，未发现额外写出者。
- **取证基准面声明**：本节四方证据全部取自**当前工作树磁盘状态**，而工作树存在早于本会话的未提交改动（`git status` 显示 `contracts/openapi/aihub-v1.yaml` +98 行、`docs/ai-spec/01-requirements/dataset-experience.md` +96 行、`02-domain/error-catalog.md`、`04-ui/page-catalog.md`、`05-acceptance/dataset-agent-exit-catalog.md` 同为 M，另有 `V44__preview_split_stats.sql` 与 `TASK-P0BR-077..081` 等未跟踪制品，属 preview 域的既有在途工作，本项未触碰）。已核对 `git diff contracts/openapi/aihub-v1.yaml` **不含任何 `x-audit-event` 变更**，故 60 声明 / 59 distinct 的口径在 HEAD 与工作树上一致；但本节引用的契约**行号仅对当前磁盘版本成立**，验收方若基于其他 revision 复核需重新定位行号。

**根因（与 ⑲⑳㉑㉒㉓㉔㉕ 同源）**：`x-audit-event` 是 OpenAPI 自由字符串扩展，**无 schema 约束到任何事件目录**（因为不存在审计事件目录制品），故复合名 `A_OR_B` 与代码不存在的名字可静默写入契约；`audit_log.event_type` 无 CHECK/无外键（缺陷面 I），故代码侧任意名字可静默入库；`AuditService.record()` 无 `@Transactional` 且各调用点自带吞异常，故"是否写出"在运行期不可观测、在测试期无断言。三者叠加意味着**没有任何一处制品能回答「这个平台的审计事件全集是什么」** —— 与 ㉕「权限目录本体合规但码到操作绑定无守卫」是同一类问题在审计维度的复现，也与 ㉓「契约↔目录↔鉴权↔白名单缺守卫」同构。

**限定更正**：㉑（状态机四方差集）与 ㉕（权限五方差集）在涉及审计时给出的「审计已覆盖」类结论，应统一限定为「**在 REST 治理路径上由 `PublishApplicationService`/`VersionApplicationService` 写入了 SUCCEEDED 结果的事件**」；按本节证据，权限拒绝、审计写失败、Webhook/对账/PAT/Bootstrap/通知已读、Agent 注册与启停、评论编辑与解除隐藏、讨论串解锁均无审计事实。本项不修改任何禁改面文件（契约、01/02/05 规范、tasks、backend、frontend、已应用迁移），仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 裁决 `REQ-AUD-001` L269-270 的 fail-closed 语义如何落地 —— 为高风险写引入「审计与业务同事务、写失败回滚」的实现（需 `AuditService` 参与调用方事务并移除 9 处 `catch→LOG.error`），或引入规范允许的「持久化缓冲重试」通道并声明事件分类目录；两条路径互斥且都需要 `01-requirements` 明确分类清单，须验收方裁决；② 裁决缺陷面 A/B 的收敛方向 —— 修契约（把 3 个复合名拆为原子名、把 `PUBLISH_REQUESTED`/`PUBLISH_DECISION_SUBMITTED` 改为实发名、删除或补登 13 项）或修代码（补 13 项写出 + 改名），须先补契约变更并同步生成物，属破坏性变更需 ADR；③ 在 `02-domain` 或 `contracts/events/` 建立**唯一审计事件目录**制品（含 `event_type`、`resource_type`、`result` 允许域、高风险分类），并为其加稳定 ID 纳入 `validate-spec.ps1`；④ 为 `audit_log.event_type` 增加 `V45+` 迁移的 CHECK 或外键到该目录（不得改 `V10`），同时评估不可篡改触发器与目录演进的先后顺序；⑤ 在 `RestAccessDeniedHandler`/`AuthorizationService` 拒绝路径补 `AUTH_PERMISSION_DENIED` 审计（含 `errorCode`/主体/资源/Trace，满足 `INV-EVT-006` 的 DENIED 语义），并回补 403 失败路径测试；⑥ 为 `BOOTSTRAP_ADMIN_CREATED`、`ASSET_SUBSCRIBED/UNSUBSCRIBED`、`ASSET_FAVORITE_ADDED/REMOVED`、Webhook inbox、三个 `*Reconciler`、PAT 生命周期补审计或反向从规范撤销该义务；⑦ 补齐成对治理动作的对称审计（`COMMENT_EDITED`/`COMMENT_UNHIDDEN`/`THREAD_UNLOCKED`、`UPLOAD_SESSION_CANCELLED`），并让 `state-machines.md` L32 的「待确认」定稿；⑧ 为 `aihub-agent-v1.yaml` 的 4 个写 operation 补 `x-audit-event` 与 `x-required-permissions`，并把 `McpController` L217-240 的控制器内审计下沉到应用服务，符合 AGENTS.md「适配器不含业务规则、复用同一审计逻辑」；⑨ 消除审计事件名与 outbox `EventType` 的同名混用（缺陷面 I），按 `INV-EVT-008` 明确两命名空间边界或显式声明复用关系；⑩ 修正 `deriveResourceType()` 的下划线截断推导（改为按事件目录显式声明 `resource_type`，3 例已有量级证据）；⑪ 建立「契约 `x-audit-event` ↔ 事件目录 ↔ `AuditService` 调用点字面量 ↔ `audit_log` 允许域」四方引用完整性守卫测试（与 ⑲⑳㉑㉒㉓㉔㉕ 守卫合并处置），并新增「每个 `AC-P0B-AUD-*` 场景都有对应失败路径测试」断言；⑫ 上述任一实现整改前需人类先出 `READY` + `implementationAuthorized: true` 的 Task Card（DEC-009），AI 不得自批。
→ 本项与 ①—㉕ 一并不在本轮 AI 自决实施范围内。

### ㉗ 审计查询面七层差集（`V10` schema ↔ `JdbcAuditRepository` 过滤 ↔ `AuditQueryService` ↔ `AuditLogController` 参数 ↔ OpenAPI 声明 ↔ `AuditLogsPage` 前端 ↔ `04-ui` 规范）：能落库的字段查不出来、能查的参数契约没写、契约写了的过滤前端没有，「数据范围限制」与「保留期」两项 MUST 完全没有实现基础

**取证范围与方法**：七层自上而下 —— ① `backend/src/main/resources/db/migration/V10__audit.sql`（74 行全文，磁盘状态）；② `audit/infrastructure/JdbcAuditRepository.java` L97-162（`list` / `search` 两路查询）；③ `audit/application/AuditQueryService.java` L43-110（含 `Cursor.decode/encode`）+ `AuditLogView.java`（48 行全文）；④ `audit/api/AuditLogController.java`（71 行全文）；⑤ `contracts/openapi/aihub-v1.yaml` L1422-1447（`/system/audit-logs`）与 L4371-4396（`AuditLogView` / `CursorPageAuditLog`）；⑥ `frontend/src/features/admin/{types.ts L339-359, api.ts L293-295, pages/AuditLogsPage.tsx（119 行全文）, pages/AuditLogsPage.test.tsx（41 行全文）}`；⑦ 规范侧 `01-requirements/p0b-platform-services.md` L248-277、`02-domain/entity-invariants.md`（`INV-EVT-005..008`）、`04-ui/page-catalog.md` L34、`04-ui/admin-pages-mips.md` L156-165、`05-acceptance/p0b-exit-catalog.md` L104-108、`05-acceptance/p5-exit-catalog.md` L73。写入侧另立一组量化：全 `com.aihub` main/java 内 `new AuditEvent(` 精确 **24 处**（PowerShell `Select-String` 全量枚举，非截断的 Grep 结果），逐点读取第 7/8 实参。取证方法为纯静态 file:line 核对 + 三次 PowerShell 全量枚举，无一次性探针留存；所有运行时推论均在下文显式标注「未实测」。

**合规面（已落实项）**：
- 查询面自身门禁与只读形态符合 `INV-EVT-005` / `AC-P0B-AUD-003` 前半句：`AuditLogController` 全文只有 1 个 `@GetMapping`（L43），无 UPDATE/DELETE 端点（L25 自述并属实）；`JdbcAuditRepository` 亦无改删方法；`V10` L62-74 `fn_audit_log_immutable()` + 两个 BEFORE 触发器在 DB 层兜底。
- 游标分页实现与契约的 `limit` 约束一致：控制器 L55 `Math.min(Math.max(limit,1),100)` ↔ 契约 L1435 `minimum:1 maximum:100 default:20`；`AuditQueryService` L48/L77 用 `limit+1` 探测 `hasMore`、L52-55 以本页末条编码 `nextCursor`，键集写法 `(occurred_at, id) < (?, ?)`（L115/L155）与 `ORDER BY occurred_at DESC, id DESC`（L119/L159）及 `V10` L53 `idx_audit_log_cursor` 三向匹配。
- `list` 路 3 个过滤参数（`principalId` / `action` / `resourceId`）在契约（L1431-1433）、后端仓储（L102-113）、前端表单（`AuditLogsPage.tsx` L76-84）、TS 参数类型（`types.ts` L353-359）四方**完全一致** —— 这是本链路唯一四方闭合的一段，也恰是 `MIPS-ADM-014`（L164「筛选器（principalId/action/resourceId）」）所要求的那一段。
- 视图不泄漏持久化实体：`AuditLogView` 为 10 字段 record，`from(AuditRecord)`（L42-47）显式挑选字段、不返回内部自增 `id` 与 `row_version`，符合 AGENTS.md「永不返回持久化实体」。

**缺陷面 A（最重）：`scope_type` / `scope_id` 在全部写出路径上恒为 NULL，导致 `REQ-AUD-001` L262「记录 …scope…」与 L267「查询 …受 `audit:read` **和数据范围限制**」两项同时失去实现基础。**

三段式证明（三者必须同时成立，缺一不足以支撑「恒 NULL」这一强断言）：
1. **构造点全量**：`new AuditEvent(` 精确 24 处，其第 7/8 实参（`scopeType` / `scopeId`）**24/24 全部为字面量 `null`** —— `AgentController` L325、`AssetApplicationService` L537（L539 `null, null`）、`AssetRelationApplicationService` L160、`DiscussionApplicationService` L364、`PhysicalRecycleJobHandler` L129、`AuthorizationAuditAdapter` L40、`ConfigurationAuditAdapter` L39、`IdentityAuditAdapter` L48、`JobAuditAdapter` L41、`McpController` L218/L234、`OrganizationAuditAdapter` L39、`TaxonomyAuditAdapter` L39、`DownloadApplicationService` L98/L120、`UploadApplicationService` L345、`UploadMaterializeJobHandler` L291、`DvcConfigurationService` L64/L134、`PublishApplicationService` L145/L233/L351/L379、`VersionApplicationService` L220。全 `backend/src/main` 无任何变量或常量向该两位传值。
2. **服务层无补全**：`AuditService.record()` L74-77 只对 `principalId` / `principalType` 做 `ctx` 兜底，L81-82 对 scope 两参**逐字透传** `event.scopeType(), event.scopeId()`，`PrincipalContext` 亦无 scope 字段可供兜底 → 调用方给 `null` 即入库 `NULL`。
3. **旁路面不含该列**：`PublishJobHandler` L263-266 的 INSERT 列清单为 `audit_id, event_type, principal_id, principal_type, action, resource_type, resource_id, result, error_code, request_summary, trace_id, occurred_at` —— 无 `scope_type` / `scope_id` / `duration_ms` / `request_id`，走 `V10` L19-20 的可空默认。

后果：`V10` 保留的两列在**当前全仓写入路径下永远是 NULL**，任何基于作用域的数据范围过滤都无从实现；控制器 L54 只有全局 `AUDIT_READ` 门禁，`JdbcAuditRepository` L100/L128 两条 SQL 无任何 scope 谓词 → 持 `audit:read` 者（`V4` L208-210 的 ADMIN 通配使其成为超级权限）可枚举**全平台所有主体**的审计流水。`AC-P0B-AUD-003` 后半句「查询按权限和游标工作」中的「按权限」若理解为数据范围，则不可通过。

**缺陷面 B：生产旁路写入绕过共享脱敏与 ID 设施（对 ㉖ 合规面第 1 条的更正，亦是查询面污染源头）。**

`PublishJobHandler.recordAuditEvent`（L248-271）不经 `AuditService`，因此：① L255-258 `requestSummary = objectMapper.writeValueAsString(Map.of("requestId",…,"versionId",…,"eventType",…))` 直接序列化入库，**`AuditService.maskSummary`（L69/L107-131）与 `SensitiveDataMasker` 全不生效** —— 与 `V10` L5「request_summary 字段级脱敏」及 L45 列注释「已脱敏」正面冲突（`AC-P0B-AUD-002` 的「audit_log 均不出现原文」在发布路径上无守卫）；② L252 `audit_id = "aud_" + UUID.randomUUID()…substring(0,24)` 而非 `IdGenerator.generate(IdPrefix.AUDIT)`（对比 `AuditService` L72），与 `V10` L34 列注释「aud_+ULID」不符，且 `AuditLogView.auditId` 的契约 pattern `^aud_`（L4375）两种都能满足，错误不可检；③ 该旁路已被 `TASK-P0BR-064.md` L119 与本文件 ㉖ 之外的任务卡「卡 §2 事实 6」登记为已知待收敛项 —— 即**规范冲突在任务卡层面已知但仍未整改**，属治理闭环缺口而非新发现。

**缺陷面 C：后端 9 个查询参数、契约只声明 5 个，且控制器二选一路由会静默丢弃 `action`。**

控制器 L44-53 接受 9 个参数，契约 L1430-1435 只列 `principalId/action/resourceId/cursor/limit` —— `eventType` / `resourceType` / `createdAfter` / `createdBefore` **4 个参数契约未声明**（属"实现超出契约"，客户端生成物无从得知其存在）。更严重的是路由逻辑：L57 `if (eventType != null || resourceType != null || createdAfter != null || createdBefore != null)` 走 `searchAuditLogs`，而 `searchAuditLogs` 签名（L73-75）**不含 `action`** → 一旦使用者（含直接按实现调用而未看契约者）同时传 `eventType` 与 `action`，`action` 被**静默忽略、不报错、不降级**，返回集比请求条件更宽。这是审计检索面上唯一一个"过滤条件静默失效"的形态，与 ㉖ 缺陷面 A 的「按契约事件名检索恒空」构成互补：一个是查不到，一个是查多了。

**缺陷面 D：仓储两路查询均无 `result` / `principal_type` / `scope_*` / `request_id` / `trace_id` 谓词，`SELECT *` 取回 18 列后仅 10 列出视图，8 列永不可见亦不可过滤。**

`list` L100-121 三谓词、`search` L128-161 五谓词，合计**没有任何一路支持按 `result` 过滤** —— 而 `result` 是 `V10` L29 全表唯一 CHECK 约束的列、是 `REQ-AUD-001` L263「SUCCEEDED/FAILED/DENIED 分开」的承载列、是 `AC-P0B-AUD-001`「记录正确 result」的验证列、也是前端 `RESULT_COLOR`（`AuditLogsPage.tsx` L16-20）**已经准备好**要展示的列。`trace_id` / `request_id` 可过滤性缺失使 `AC-P0B-AUD-004`「JSON 日志可用 requestId/traceId/principalId/resourceId/jobId 关联」只能在日志侧关联、无法在审计侧反查。8 列（`event_type`、`principal_type`、`scope_type`、`scope_id`、`request_summary`、`duration_ms`、内部 `id`、`row_version`）中除 `event_type` 可经未声明参数过滤外，**其余既不能过滤也不能出现在响应体**：`AuditLogView` record（L28-37）无 `eventType` 字段，故 L131 的 `event_type = ?` 过滤出来的行仍不告诉调用方事件名是什么；`request_summary`（脱敏载荷）在审计页完全不可见。两路 `SELECT *`（L100/L128）与 `AuditRecord` 全列映射进一步把 `row_version`、内部 `id` 读入内存后丢弃（性能与最小暴露面问题，量级小，登记不量化）。索引侧：`V10` L55 `idx_audit_log_resource(resource_type, resource_id)` 为**复合索引、`resource_type` 为前导列**，而 `list` 路只按 `resource_id` 过滤（L111）→ 该查询用不上此索引（PostgreSQL 复合索引最左前缀），须以 `EXPLAIN` 实测确认量级，本轮**未实测**。

**缺陷面 E：契约对 `AuditLogView` 的非空断言与 DB 可空性、后端可空返回互相矛盾，前端 TS 类型继承同一错判。**

契约 L4373 `required: [auditId, principalId, action, resourceType, resourceId, result, occurredAt]` 把 `principalId` / `resourceType` / `resourceId` 声明为**必填**；`V10` L14/L17/L18 三列在 DB **可空**；`AuditService` L74-75 在 `principalId` 为 null 且无 `ctx` 时**写 null**（`JobAuditAdapter` L41-55 的 Worker 线程场景，其 L44 注释自述「Worker 线程无上下文 → null」），`IdentityAuditAdapter` L44/L48 与 `OrganizationAuditAdapter` L44、`TaxonomyAuditAdapter` L44 的 `resourceType` 亦传 `null`；`AuditLogView.from` L43-46 直接透传 → 运行时**可产出违反自身契约的响应**（同文件对 `errorCode/requestId/traceId` 则正确写了 `type: [string,'null']`，L4383-4385，说明作者知道该写法，属遗漏而非设计）。前端 `types.ts` L340-351 把三列写作非可选 `string`（重复契约错判），`AuditLogsPage.tsx` L47 的 `` `${record.resourceType}:${record.resourceId}` `` 对 null 会渲染出字符串 **`"null:null"`** —— 且因后端从不返回 `event_type`（缺陷面 D），用户在页面上看到的是 `null:null` 而非任何事件标识。按契约生成客户端的场景下，此响应体过不了反序列化的必填校验。

**缺陷面 F：畸形游标返回 500 而非 400，契约只声明 403。**

`Cursor.decode` L92-103 对 `Base64.getUrlDecoder().decode`（L96，非法 base64url 抛 `IllegalArgumentException`）、`Instant.parse`（L101，非 ISO 时刻抛 `DateTimeParseException`）、`Long.valueOf`（L102，非数字抛 `NumberFormatException`）**三处均无 try/catch**；`GlobalExceptionHandler` L55-68 `@ExceptionHandler(Exception.class)` → 500 `INTERNAL_ERROR`。而契约 L1428 `x-error-codes: [AUTH_PERMISSION_DENIED]`、L1436-1447 响应只有 `'200'` 与 `'403'` —— 该 500 形态契约未声明。另有一处**静默降级**与之相反：L97-100 当游标解码后不含 `|` 分隔符时返回 `new Cursor(null, null)`，即一个**语法可解码但格式错误**的游标会被当作"无游标"，于是**第一页被重复返回**（翻页时静默重复，比 500 更难发现）。两种畸形输入走两种相反处理，`AUD` 侧无任何一处测试覆盖（本轮 grep `cursor` 于 audit 相关测试未见失败路径断言）。注：500 与重复翻页均为**由代码结构推出，未实测**。

**缺陷面 G：两份 UI 规范对同一页面的筛选面要求互不一致（须上报，不得择一）；实现满足其一、另一要求与「详情」项同时落空。**

`04-ui/page-catalog.md` L34 `PAGE-ADM-014` 的「主要能力」列为「游标筛选、主体/动作/**结果**/资源/**Trace**」（**5 个筛选维度**），边界列为「只读；**详情字段脱敏**；无导出越权」；而 `04-ui/admin-pages-mips.md` L164 `MIPS-ADM-014` 的交互元素为「游标分页列表（time/principal/action/resource/result/errorCode）、筛选器（**principalId/action/resourceId**）、**详情 Drawer**」（**3 个筛选维度**）。二者对同一页面要求不同：page-catalog 要求可按 result 与 Trace 过滤但**不要求**详情视图，MIPS 要求详情 Drawer 但**只要求 3 个筛选器**。实现侧 `AuditLogsPage.tsx` L76-84 只有 3 个 `Form.Item`（满足 MIPS 的筛选器集、**不满足 page-catalog 的 5 维**），且**无 Drawer、无展开行、无任何详情入口**（**两个制品的「详情」要求均未满足**，尽管 `request_summary` 已在 `AuditRecord` 里被读入内存）；`REQ-AUD-001` L276 只追踪 `PAGE-ADM-014` 一个页面 ID，故该冲突无法由上层规范裁决。**此为本段发现的规范侧真实冲突，按 AGENTS.md「代码、契约、ADR、设计互相矛盾时上报，不得静默择一」处理，本项不修改任何规范文件。**

**缺陷面 H：`SensitiveDataMasker` 无长度上限，`INV-EVT-007`「限制大小」与 `AC-P0B-AUD-005`「正文受大小规则限制」在审计面无实现证据。**

`shared/logging/SensitiveDataMasker.java` 全文 99 行：`MASK="***"` + 16 个敏感字段名（L26-42）+ 3 个正则（L45-55），三个 `mask*` 方法（L63-98）只做**内容替换、不做截断、不做长度校验**；`AuditService.maskSummary` L107-131 只做 `__` 前缀键剔除（L114）+ 逐字段 `maskField` + `writeValueAsString`（L127），**无字节/字符上限**；`V10` L23 `request_summary JSONB` 亦无长度约束。故 `AC-P0B-AUD-005`（MUST / E4）「发送超大…请求 → 日志正文受大小…规则限制」在 `audit_log` 面**无对应机制**（JSONB 单值上限约 1GB，事实上不构成规则）。注意与 ㉖ 已述的另一处：`V10` L22 `error_code VARCHAR(64)` 的截断由**旁路面** `PublishJobHandler.truncate`（L274-279）单独实现，正途 `AuditService` 不做长度校验，即长度规则的实现位置本身就是分裂的。

**缺陷面 I：保留期与归档无实现，`audit.retentionDays` 是惰性配置；真要裁剪还会撞上未登记关系的派生消费者。**

`audit.retentionDays` 全仓命中：**仅 3 处定义**（`V8__configuration.sql` L68 种子 `180` / 范围 `30..3650`；`configuration/application/ConfigKeys.java` L36 常量 `AUDIT_RETENTION_DAYS`）**+ 仅测试引用**（`PlatformConfigServiceTest` L86-88 把它当通用样例键，断言的是 `getString` 返回 `"180"`；`ConfigurationIT` L96）→ `grep AUDIT_RETENTION_DAYS backend/src/main` 除常量定义外 **0 命中**，即**生产无消费者**，不存在按天裁剪/归档作业。直接违反 `REQ-AUD-001` L268「保留期和归档不能由普通管理员绕过」——准确表述是「保留期与归档根本不存在，故谈不上绕过」，该 MUST 项处于**未实现**而非**已弱化**状态。叠加派生依赖：`V36__download_stats_mv.sql` L7-17 `mv_download_stats` 的定义为 `FROM audit_log JOIN asset_version … WHERE al.event_type='DOWNLOAD_TICKET_ISSUED' AND al.result='SUCCEEDED'`，即审计表是**下载统计物化视图的唯一事实来源**；`DownloadStatsRepository` L30-64 三个查询全部只读该 MV，`DownloadStatsRefresher` L34 `REFRESH … CONCURRENTLY`、L37 刷新失败仅 `LOG.warn`。**任何未来的裁剪都必须先定义 MV 一致性策略，而该依赖关系在 `V36` 头注释与所有规范文档中均未登记** —— 这是「保留期整改」与「`AC-P5-OBS-001` 基于 audit_log 聚合」两条要求之间未被人显式调停的耦合点。另注 `event_type='DOWNLOAD_TICKET_ISSUED'` 谓词与 `JdbcAuditRepository.search` L131 的 `event_type = ?` 一样依赖精确串匹配，㉖ 缺陷面 I 所述「事件名零约束」的污染面同样会击穿该 MV。

**缺陷面 J：`action` 域内三种命名风格混用，使唯一可用的动作过滤器（缺陷面 C 中 5 个契约参数之一）实际不可用。**

`V10` L38 列注释「动作（与事件类型对齐，便于按动作检索）」、`AuditService` L78 兜底 `event.action() != null ? event.action() : event.eventType()`（即缺省=UPPER_SNAKE 事件名）。但 24 个构造点里**至少 12 处显式传入小写冒号风格**：`"agent:rest:call"`(L326)、`"mcp:tool:call"`(L220/L236)、`"download:presign"`(L100)、`"download:dvc"`(L122)、`"dvc:config"`(L66)、`"dvc:credentials"`(L136)、`"upload:materialize"`(L293)、`"publish:submit"`(L146)、`"publish:review"`(L234)、`"version:deprecate"`(L352)、`"version:archive"`(L380)、`"job:dead"`(L42 兜底串)；另有 `"PHYSICAL_RECYCLE"`(L131) 与其余把 `action` 直接写成 `eventType`（UPPER_SNAKE）的点；测试夹具 `PerformanceFixtureGenerator` 另造第三种集合。结果：前端唯一的动作过滤（`AuditLogsPage.tsx` L79-81 → 仓储 L106-108 精确 `action = ?`）对同一语义动作**必须知道两种写法之一**才查得到，且平台既无动作目录也无 CHECK。附带发现（属事件名漂移、非动作域，一并登记）：`PerformanceFixtureGenerator` L38-50 的 `AUDIT_EVENTS` 含**不存在的**事件名 `LOGIN_SUCCESSFUL` / `TOKEN_REFRESHED`（真名为 `AUTH_LOGIN_SUCCEEDED` / `AUTH_TOKEN_REFRESHED`），夹具因此不能作为事件目录的反查依据。

**缺陷面 K：前端测试的 fixture 字段与生产视图不同名，且零断言覆盖上述任何缺口。**

`AuditLogsPage.test.tsx`（41 行全文）的 mock 数据 L11-12 使用 **`createdAt`**（真实 `AuditLogView` 字段为 `occurredAt`，契约 L4386）、`action: 'LOGIN' / 'CREATE_ASSET'`（生产该域为 `AUTH_LOGIN_SUCCEEDED` 族与小写冒号风格）、`resourceType: 'session' / 'asset'`（生产为大写资源域 `ASSET` / `VERSION` / `UPLOAD_SESSION` …）；断言仅 2 条（L30 标题存在、L37-38 两个 principalId 渲染）。由于 mock 直接顶替 `../api`（L18-20），`occurredAt` 列（`AuditLogsPage.tsx` L41）在测试中恒为空渲染而无人察觉。对**过滤器集合、详情视图、翻页/游标、错误态、`null:null` 渲染**全部零断言 → 缺陷面 C/D/E/F/G 在本仓库任何自动化门禁下**都不会变红**。

**取证偏差登记（不得作为缺陷写入）**：
- **口径区分（易与 ㉖ 混淆，必须分列）**：「`new AuditEvent(` 构造点 **24**」是本段用 PowerShell `Get-ChildItem -Recurse | Select-String` 全量枚举所得；「`auditService.record(` / `auditPort.record(` 调用点 **51**」是另一口径；「**41 个写出者文件**」是 ㉖ 使用的"引用 `AuditService|AuditPort|new AuditEvent` 的 Java 文件数"口径。三者互不蕴含，本段仅在 24 这一口径上下 scope 结论。注：Grep 工具对 `new AuditEvent\(` 返回 215 行含截断提示（「80 lines truncated」），**不足以支撑穷尽断言**，故弃用、改用 PowerShell 精确枚举并逐点 Read 复核。
- 契约行号的基准面：`git diff -U0 -- contracts/openapi/aihub-v1.yaml` 得 3 个 hunk（`@@ -3010,0 +3011,40 @@`、`@@ -5014 +5054,12 @@`、`@@ -5020,0 +5072,45 @@`）→ `/system/audit-logs` L1422-1447 **在 HEAD 与工作树完全一致**；而 `AuditLogView` L4371-4396 因 L3011 处 +40 行插入**仅对当前磁盘版本成立**（HEAD 约前移 40 行至 L4331 附近）。`04-ui/page-catalog.md` 同为工作树 M 状态，其 L34 行号同理仅对工作树成立。验收方基于其他 revision 复核须重新定位。
- 后端与 `V10`/`V8`/`V36` 的全部引用均来自**工作树磁盘状态**，其中 backend 侧存在早于本会话的 preview 域未提交改动（`PreviewController` / `PreviewApplicationService` / `V44__preview_split_stats.sql` / `TASK-P0BR-077..081` / `frontend/e2e/*` / `build-logs/` 等，`git status --short` 计 23 M + 21 未跟踪）；本项**未触碰**任何上述文件，且已核对 audit 查询面七个文件（`V10`、`JdbcAuditRepository`、`AuditQueryService`、`AuditLogView`、`AuditLogController`、`AuditLogsPage.tsx`、`types.ts`）与规范侧四文件在本会话内**无改动**（本会话仅修改本迭代计划一个文件）。
- 「`idx_audit_log_resource` 前导列导致 `list` 路用不上索引」「畸形游标 → 500」「无 `|` 游标 → 重复首页」「`AuditService` 无长度校验使超大请求正文入库」四项均为**静态结构推论，本轮未做运行时实测**（需 Compose + `EXPLAIN` / 故障注入 / 超大 Body 用例），验收方复核时应以实测定论，不得据本节文字当作 E4 证据。
- 「scope 恒 NULL」是对**当前 24 个构造点 + 1 个旁路 INSERT** 的全量断言，不排除 `AuditEvent` 未来新增构造点或外部直写；其规范含义（数据范围过滤无从实现）已由 `V10` L19-20 可空 + 仓储无谓词 + 控制器无 scope 上下文三点独立支撑，不依赖 24 这一数字精确成立。
- 本节把 ㉖ 合规面第 1 条推翻并在原位改写（未删除原文，以 `~~删除线~~` + 更正段保留可追溯性），并在 ㉖ 缺陷面 C、F 各追加一条 ㉗ 同期补充；㉖ 其余结论不受影响。

**根因（与 ⑲—㉖ 同源，且在查询面多一个特有因子）**：`/system/audit-logs` 这条链路上**没有任何一层是另一层的生成物或校验对象** —— 仓储谓词集、应用服务签名、控制器参数集、契约参数集、前端参数类型是**五次独立手写**，`openapi-typescript` 生成物未被前端 admin 域使用（`types.ts` L339-359 为手写），`x-implementation-status` 亦不约束参数集，故"实现超出契约 4 参数"与"契约必填而 DB 可空"都能长期共存。规范侧 `REQ-AUD-001` L267 的「数据范围限制」是一个**只写在需求句里、没有任何制品承载**的能力（既无 scope 目录、无 PrincipalContext 字段、无 DB 值、无谓词），属 ㉕「本体合规但绑定无守卫」在查询维度的复现。`scope_*` 与 `retentionDays` 两个"列已建、配置已种、消费为零"的形态，是典型的**schema 先行于语义**：DBA 视角预留的列在应用视角从未被认领，而 `V10` 的不可篡改触发器（禁 UPDATE）恰好堵死了事后补值的通道 —— 即使将来实现 scope 过滤，**历史审计行的 scope 也永久为 NULL**，形成不可回填的检索断层（此推论由 L62-74 触发器 + 无 UPDATE 路径得出，属结构结论）。

**限定更正**：① ㉖ 合规面第 1 条「`AuditService.record()` 是后端唯一的 `audit_log` 写入通道，符合 `REQ-AUD-001` L260」已被本节缺陷面 B 推翻，原位改写；② ㉖ 缺陷面 C 的「审计写失败处理策略」结论应补充「实现中存在吞异常与上抛-触发-Saga-重试两种相反形态」，不得表述为「一律吞」；③ ㉖ 缺陷面 F 仅指 `aihub-agent-v1.yaml` 双零，MCP 审计义务的第三种键名写法（`tools.yaml` L209/L219 `auditEvent`）为 ㉗ 新增，两者须分别整改；④ 本仓库任何早于本轮的轮次或任务卡中若出现过「审计查询能力符合 `AC-P0B-AUD-003`」类结论，应统一限定为「**在 `principalId`/`action`/`resourceId`/`cursor`/`limit` 五个契约参数上按游标工作**；`result`/`trace`/`scope` 过滤与数据范围限制、保留期与归档、审计正文大小规则均无实现」；⑤ `AC-P5-OBS-001`（`05-acceptance/p5-exit-catalog.md` L73「无权主体不能枚举他人下载行为」）的既有证据 `tasks/evidence/EVD-P5008-001.yaml` 自身即标注 **L25 `status: PARTIAL`**，其 L14-19 自述的防枚举修复只覆盖 `DownloadStatsService.getAssetStats`（单资产、`ASSET_READ` + `canAccess` 过滤 + `ASSET_NOT_FOUND`），**未覆盖** `DownloadStatsController` L46 `/api/v1/system/metrics/downloads`（`system:observe` → `DownloadStatsRepository.topAssets` L53-64 平台榜单，逐资产可见性一律不过滤）；另 `05-acceptance/p0b-exit-catalog.md` L148 自承「V08 只覆盖一次登录审计和特定明文口令扫描」，与本节缺陷面 H 的「无大小规则」一致 —— 即 `AC-P0B-AUD-002/005` 的 E4 从未在审计面完整取证。本项不修改任何禁改面文件（契约、01/02/04/05 规范、tasks、evidence、backend、frontend、已应用迁移），仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 裁决「数据范围限制」（`REQ-AUD-001` L267）的实现形态 —— 为 `PrincipalContext` 引入 scope 并在 `AuditQueryService` 强制注入谓词 + 要求全部 24 个构造点补传 scope（须新 `V45+` 迁移为 `scope_*` 建索引，且历史行为 NULL 需明确"不回填、按 occurred_at 之后生效"的语义），或反向把该句从 MUST 降级/删除并由验收方接受其安全后果；两条路径互斥，须出 `01-requirements` 变更 + ADR；② 收敛 `PublishJobHandler` 审计旁路到 `AuditService`（该任务已由 `TASK-P0BR-064` 卡 §2 事实 6 登记，需人类出 implementation Task，不得由 AI 自批），并在其之前为该路径补脱敏与 `IdGenerator` 一致性回归；③ 契约回补 `eventType/resourceType/createdAfter/createdBefore` 4 参数，或后端删除这 4 个未声明参数与二选一路由（**破坏性变更需 ADR**），无论哪个方向都必须消除"`action` 被静默丢弃"这一形态；④ `AuditQueryService.searchAuditLogs` 与仓储两路补 `result`、`principalType`、`traceId`、`requestId` 谓词（满足 `AC-P0B-AUD-001/004` 的可验证性），并把 `SELECT *` 收敛为显式列；⑤ 补 `V45+` 迁移为 `(resource_id)` 单列或调整 `idx_audit_log_resource` 顺序（不得改 `V10`），并以 `EXPLAIN` 实测确认后再定形态；⑥ 修正契约 `AuditLogView` 的 `required` 集与三列可空性（`principalId/resourceType/resourceId` → `type: [string,'null']`，属破坏性变更需 ADR + 同步前端 `types.ts` L340-351），并在 `AuditLogView` 增补 `eventType` 字段（否则 L131 的过滤结果不可解释）、撤销或改写其 L14-15「与 OpenAPI 对齐」的自述；⑦ `Cursor.decode` 对三处解析加显式校验并映射为 400（`VALIDATION_FAILED` 类），消除"无 `|` 即静默返回首页"的降级；契约 L1428/L1436-1447 回补 400 声明；⑧ 裁决 `PAGE-ADM-014`（page-catalog L34，5 维筛选）与 `MIPS-ADM-014`（admin-pages-mips L164，3 维 + 详情 Drawer）的**规范互斥**：先由人类定稿其中一份、同步另一份，再据定稿补前端 result/Trace 过滤器与详情 Drawer（Drawer 的展示内容即 `request_summary`，故须与 ⑨ 的大小规则同批设计，避免把未截断载荷整体渲染）；⑨ 在 `AuditService.maskSummary` 引入显式的字段级与总字节上限（承载 `INV-EVT-007` / `AC-P0B-AUD-005`），并把旁路面已有的 `error_code` 截断收敛到共享实现，避免长度规则两处分裂；⑩ 实现 `audit.retentionDays` 的裁剪/归档作业（走持久化 job 体系，不得 `@Async`），并同时给出 `mv_download_stats` 的一致性策略（裁剪前是否需先物化归档、REFRESH 失败是否阻断裁剪），二者必须同批设计 —— 该依赖关系应写入 `V36`/`02-domain` 或新 ADR，不得只存在于本节；⑪ 建立 `audit_log` 列 ↔ `AuditRecord` ↔ `AuditLogView` ↔ 契约 ↔ TS ↔ 前端列 的**七方字段一致性守卫测试**（与 ⑲—㉖ 守卫合并处置），至少断言：契约每个 `required` 字段在后端非 null 率、后端参数集 ⊆ 契约参数集、`AuditLogView` 字段集 ⊇ 契约 properties、TS 可选性与契约 nullable 一致；⑫ 为 `AuditLogsPage.test.tsx` 补 fixture 与真实视图同名的类型检查（fixture 应引用生成的 `AuditLogView` 类型而非裸对象字面量，令 `createdAt` 这类漂移在编译期失败），并新增过滤器集合、详情、翻页与错误态断言；⑬ 统一 `action` 取值域（建议动作目录制品 + `V45+` CHECK 或改为契约生成枚举），并修正 `PerformanceFixtureGenerator` L38-50 的不存在事件名；⑭ 上述任一实现整改前需人类先出 `READY` + `implementationAuthorized: true` 的 Task Card（DEC-009），AI 不得自批。
→ 本项与 ①—㉖ 一并不在本轮 AI 自决实施范围内。

### ㉘ 通知 / 出站 Webhook 面差集（`REQ-NOT-001`/`REQ-WHK-001` ↔ `events-v1.yaml` ↔ `V11`/`V18`/`V8` ↔ `com.aihub.notification` 全域 ↔ OpenAPI 四 admin 端点 ↔ `frontend/admin` ↔ `04-ui`/`05-acceptance` ↔ runbooks）：出站 Webhook 在结构上是死码（唯一写入目标的适配器永不被调用）、`AC-P0B-NOT-001..005` 五条 E4 静态不可满足、签名约定三方互斥、1970 哨兵使管理端 `processed` 语义反转

**取证范围与方法**：八层交叉核对 —— ① 规范侧 `docs/ai-spec/01-requirements/p0b-platform-services.md` 的 `REQ-NOT-001`（L279-310）与 `REQ-WHK-001`（L320-342）、`REQ-JOB-001` L190；② 事件契约 `contracts/events/events-v1.yaml`（431 行全文）与 `contracts/events/README.md` L27-47；③ 迁移 `V11__notification.sql`（98 行全文）、`V18__webhook_inbox.sql`、`V8__configuration.sql` L64/L69；④ 后端 `com.aihub.notification` 全域 19 个 Java 文件（domain 5 / application 4 / infrastructure 8 / api 2）逐文件 Read，另加 7 个跨模块 `publishOutboxEvent` 调用点与 `bootstrap/ManagedExecutorsConfiguration`；⑤ OpenAPI `contracts/openapi/aihub-v1.yaml` L2748-2851（四 admin 端点）与 L4398-4417 / L5008-5037（三个 View schema）+ L1482（`NOTIFICATION_MARKED_READ`）；⑥ 前端 `frontend/src/features/admin/{api.ts,types.ts,pages/NotificationsPage.tsx}`；⑦ `04-ui/page-catalog.md`（`PAGE-COM-003`/`PAGE-ADM-015`）与 `04-ui/admin-pages-mips.md` L176；⑧ `05-acceptance/p0b-exit-catalog.md` L113-119 与 `docs/runbooks/notification-channels.md`。方法为纯静态 file:line + PowerShell/Grep 精确枚举（关键穷尽断言均给出命中总数与逐点实参），**本轮未启动任何进程、未执行 Compose、未发起任何 HTTP 投递**；因此下文所有涉及并发、重试时序、SSRF 实际拒绝、密钥在生产是否生效的判断，一律标注「未实测」，属结构推论而非 E4 证据。

**合规面（已落实项，须与缺陷面同批阅读，避免以偏概全）**：
- `NotificationSeverity` 枚举与 `V11` L26 `ck_notification_severity(INFO,WARN,ERROR,CRITICAL)` 四值严格对齐；`WebhookDeliveryStatus` 与 `V11` L97-98 的四值 CHECK（PENDING/DELIVERED/FAILED/DEAD）对齐；`V11` L25 `ck_notification_read` 亦与 `read SMALLINT DEFAULT 0` 自洽。这三处是本域少见的「DB 约束 = 代码枚举」闭合段。
- 站内通知的越权防护到位：`NotificationService.markRead` L176-181 对「不存在」与「非本人」统一抛 `NOTIFICATION_NOT_FOUND`（同码同文，防枚举成立），`JdbcNotificationRepository.markRead` L106-111 的 UPDATE 带 `notification_id AND principal_id AND read = 0` 三条件，不存在跨主体改写路径。
- 游标分页形态与 ㉗ 的审计面同构且正确：`listNotifications` L157-167 用 `limit+1` 探测 `hasMore`、以本页第 `limit` 条 `(createdAt,id)` 编码 `nextCursor`，与 `V11` L42 `idx_notification_principal(principal_id, created_at, id)` 三向匹配（键集列序一致）。
- 权限口径在「后端 ↔ 契约」这两层是一致的：`NotificationAdminController` L47/54/62/69 四处均 `requirePermission(Permissions.SYSTEM_OBSERVE)`，与契约 L2757/2782/2806/2831 的 `x-required-permissions: [system:observe]` 完全对应（不一致发生在第三层「规范」，见缺陷面 K）。
- `OutboxDeliveryReconciler` 确实存在并被注册（`NotificationConfiguration` 内有 bean），其 `event_id` 前缀校验（L110）本身逻辑无误 —— 问题在于校验对象与生产写入者不符（缺陷面 B），不是校验缺失。

**缺陷面 A（最重）：出站 Webhook 事实上从未发生过一次 —— 唯一提供投递目标的适配器是不可达代码，因此全部 `outbox_event` 行在进入投递器后被静默丢弃。**

三段式闭合证明（本会话新增逐点实参核对，把承前的推断升级为穷尽事实）：
1. **生产写入者全量 7 点，headers 实参 7/7 为字面量 `Map.of()`**：`VersionApplicationService` L231-240（L235）、`PublishApplicationService` L427-436（L431）、`AssetApplicationService` L642-650（L646）、`UploadApplicationService` L356-364（L360）、`DiscussionApplicationService` L375-383（L379）、`SystemDependencyService` L104-109（L106）、`MinioStorageReconciler` L148-153（L150）。`NotificationService.publishOutboxEvent` L128-145 对该参数只做 `toJson(headers)`（L139）后原样落库，无补默认值路径。
2. **`extractTargetUrl` 因此恒返回 null**：`WebhookDeliveryService` L184-195 —— headers 为空即 return null，随后 `new ObjectMapper()` + `node.path("webhookUrl").asText(null)`，catch 亦 return null；调用方 L89-94 拿到 null 后**执行 `markProcessed(eventId)` 并 return** —— 即事件被标记为已处理然后丢弃，不产生 `webhook_delivery` 行、不记指标、不写审计、不报错。
3. **唯一写 `webhookUrl` 的代码不可达**：全 `backend/src` 对 `NotificationChannel` 的引用精确 11 处 —— 接口自身 `domain/NotificationChannel.java` L14/L17、三个 `@Component` 实现（`WebhookChannelAdapter` L9/L27/L44、`EmailChannelAdapter` L9/L28/L45、`ImChannelAdapter` L9/L21/L26）各自的 `import` + `implements` + `channelId()`，**没有任何注入点**（无 `List<NotificationChannel>`、无 `Map<String, NotificationChannel>`、无按 `channelId()` 的分发器、无任何 `.send(` 调用者）。故 `WebhookChannelAdapter.send` L49-65（含 L58 `toJsonHeaders(recipient)`、L67-68 的 `{"webhookUrl":"…"}` 拼装）是从不被执行的孤儿方法；三个渠道适配器整体为不可达代码。

后果与规范冲突：`REQ-WHK-001` L320-342 描述的「平台向外部系统出站投递签名事件」这一能力**在运行时恒为真空**，而 `V11` L79-98 的 `webhook_delivery` 表、契约 L2799-2822 的 `/admin/notifications/deliveries` 列表、前端 `NotificationsPage.tsx` L203 的 `targetUrl` 列、runbook 的投递排障章节各自**把它当作已存在的能力来描述**。这属于 `x-implementation-status: implemented-unverified` 的语义失效：该状态即使被满足，也不表示「有事件会被投递」。同时 `EmailChannelAdapter` / `ImChannelAdapter` 的孤儿性（含 Email L82-83 直接把 `i18nKey` 裸拼进正文，无模板、无 locale）意味着 `docs/runbooks/notification-channels.md` L10-16 与 L48-62 宣称的「按 `NotificationChannel` 分发到 in_app / email / im / webhook 四渠道」这条链**在代码里不存在**（该 runbook 段落应判为文档不实，见缺陷面 K）。

**缺陷面 B：`event_id` 前缀三方冲突 + `schemaVersion` 契约字段无承载列 + `OutboxEvent` 类自述两点不实。**

- 前缀三方互斥：`IdPrefix` L92 `REQUEST("req")`（`NotificationService` L134 与 `WebhookChannelAdapter` L53 都用它生成 `eventId`）↔ `V11` L64 表注释/列语义与 `contracts/events/events-v1.yaml` L95 的 `^evt_` pattern ↔ `OutboxEvent.java` L14/L17 自述「事件全局唯一 ID（evt_）」。`IdPrefix` 中**不存在** `EVENT` 常量（L59 `NOTIFICATION("ntf")`、L62 `WEBHOOK("whk")`、L107 `PUBLISH_REQUEST("pub")`）。因此 `OutboxDeliveryReconciler` L110 的 `!eventId.startsWith("evt_") → MANUAL_REVIEW` 会对**全部生产行每轮命中**（承 27 的机制此处闭合：不是误报，而是写入者从未按契约前缀生成）。
- `schemaVersion` 无承载：`events-v1.yaml` L9 `x-contract-status: target-p0-b`（target 契约而非现状契约），其 `EventEnvelope.required` 含 `schemaVersion`，而 `OutboxEvent` 的 11 参与 `V11` L48-60 的 11 列**均无该字段** → 一旦按契约消费 `outbox_event`，全部行缺必填字段。
- `OutboxEvent.java` L2 头注「承载与核心事务原子写入的事件负载」与 L13「在同一数据库事务内写入…保证事件不丢」**两点均与实现冲突**：`com.aihub.notification` 全包 `@Transactional` 命中 **0**（本会话对该包 Grep `@Transactional` 返回 No matches），且 7 个调用点全部把 `publishOutboxEvent` 包在 `try { … } catch (Exception ex) { LOG.warn(…) }` 内（缺陷面 A 列出的 7 个行号区间），即**写 outbox 失败会被吞掉且业务事务照常提交** —— 「不丢」不成立。`JdbcOutboxRepository` L23 类注释「保证多投递器不重复领取」同样在无事务前提下不成立（见缺陷面 E）。
- `JdbcOutboxRepository.append` L59-63 的 INSERT 以字面量 `NULL` 写 `processed_at`，`OutboxEvent` 第 9 参 `processedAt()` 在写路径上**恒被忽略**（新增，属"参数存在但无消费者"形态）。

**缺陷面 C：Webhook 签名的约定三方互斥，且时间戳双源、失败密钥 fail-open、部署面零覆盖。**

- 头名与原文算法三方不同：契约 `contracts/events/README.md` L33-36 与 `events-v1.yaml` L31-39 要求 `X-AIHub-Event` / `X-AIHub-Delivery` / `X-AIHub-Timestamp` / `X-AIHub-Signature`，签名值为 `sha256=<hex>`，原文为 `{timestamp}.{rawBody}`；实现 `WebhookDeliveryService` L36 类注释自称发三头（且**无 `X-AIHub-Event`**），L119-121 实际只发 `Content-Type` / `X-AIHub-Signature` / `X-AIHub-Timestamp` / `X-AIHub-Delivery-Id`；`WebhookSigner` L42 原文为 `deliveryId + "\n" + timestamp + "\n" + payload`、L44 输出 `t=<ts>,v1=<hex>`。**三者两两不通**：按契约实现的接收方永远验签失败。`V11` L81 的列注释还把实现侧的错误格式（`t=<timestamp>,v1=<hmac>`）写进了迁移（见缺陷面 D 关于「注释入库即不可改」的限定）。
- 时钟双源：`WebhookDeliveryService` L104 / L114 / L146 用注入的 `Clock`，而 L120 的 `X-AIHub-Timestamp` 与 `WebhookSigner` L50-51 的便捷重载各取 `Instant.now()` → 同一请求内时间戳可跨越注入时钟，测试可控性失效（该重载正是 L104 签名所用路径，故签名头与时间戳头的值不保证同源）。
- 密钥 fail-open 且部署面无覆盖：签名密钥缺省为 dev-only 默认串（`@Value` 默认值形态），`deploy/` 全目录对 `AIHUB_WEBHOOK` / `WEBHOOK_SIGNING` 命中 **0**，`application.yml` 仅有 L150-159 `aihub.notification.email.*`；同处 L60-61 的 `aihub.webhook.backoff-base-ms` / `backoff-cap-ms` 亦无任何配置文件声明，只吃代码默认值。生产是否以默认密钥签名**未实测**，但「无部署面显式配置」这一事实成立 → `REQ-WHK-001` 的密钥治理要求在配置层无承载。
- `aihub.notification.dispatcher.enabled` 同样无声明（`NotificationConfiguration` L29-34 的 `@ConditionalOnProperty(matchIfMissing = true)` → 生产恒开），而 L20-21 类注释称「调度受该键控制（默认开启）」—— 键存在但**从未被任何配置文件写入**，运维无关闭开关。
- SSRF：`SsrfGuard` L85-91 对 `.local` / `localhost` 命中即 `return`（跳过后续 IP 校验），且 `fc00::/7`、`100.64.0.0/10` 不在拒绝集；解析与请求之间存在 TOCTOU / DNS rebinding 窗口；`REQ-WHK-001` L326「创建期校验」所指对象（webhook 目标注册）**不存在**（见缺陷面 F）。拒绝后果方面：`WebhookDeliveryService` L95-101 把 SSRF 异常 `catch (Exception)` 后仅 `LOG.warn("webhook target rejected (SSRF)")` + `markProcessed` → 静默丢弃、无审计、无指标、无失败状态。

**缺陷面 D：`webhook_inbox`（入站表）被 7 个非入站写入点污染，且与 `webhook_delivery` 同名异宽。**

`V18__webhook_inbox.sql` L7 的 `delivery_id VARCHAR(64)` 与 `V11` L85 的 `delivery_id VARCHAR(40)` 同名异宽；全 `backend/src/main/java` 对 `INSERT INTO webhook_inbox` 精确 **8 处**命中，其中 **7 处为对账/补偿路径的内部写入**且列清单完全一致（`(delivery_id, event_type, source, signature_valid, payload, status)`）：`job/infrastructure/JobLeaseReconciler` L138、`integration/minio/infrastructure/SessionStagingReconciler` L130、`integration/minio/infrastructure/MinioStorageReconciler` L230、`integration/gitea/infrastructure/AuthorizationProjectionHandler` L127、`integration/gitea/infrastructure/PublishedVersionReconciler` L220、`integration/gitea/infrastructure/AssetRepositoryReconciler` L212、`notification/infrastructure/OutboxDeliveryReconciler` L136。这 7 处的 `delivery_id` **不走 `IdGenerator` / `IdPrefix`**，而是各自以字符串拼接生成（例：`JobLeaseReconciler` L136 `"job-lease-reconcile-" + jobId + "-" + System.currentTimeMillis()`），`source='internal'` / `signature_valid=true` / `status='COMPLETED'` 三者则在 VALUES 子句中为 SQL 字面量（同文件 L138-141 可逐字核对），并统一附 `ON CONFLICT (delivery_id) DO NOTHING`（该子句经全量复核为 **8/8** 命中，含第 8 处入站写入者）。第 8 处才是真正的入站写入者 `integration/gitea/application/WebhookInboxApplicationService` L93，其列清单为 `(delivery_id, event_type, signature_valid, payload)` —— **不写 `source` 也不写 `status`**，吃 `V18` 的列默认值，因此入站行与内部伪行在两个维度上仅靠默认值区分。这些内部行既非 Gitea 入站回调，也不携带 `delivery_id` 与真实投递的关联，却占据 `webhook_inbox` 的语义空间，使「入站去重 / 入站审计」两类查询被内部行污染。`signature_valid=true` 由写入者自行声明而非由验签逻辑产生，属**用数据填表而非用逻辑证明**，是本轮所见最典型的"合规伪装"形态。两项限定与一项补记：① 上述 7 处中 `AuthorizationProjectionHandler` L127-131 的 `event_type` 为**绑定参数**（同文件 L116 传入 `"ORG_MEMBERSHIP_PROJECTION"`）而非 SQL 字面量，且其方法名 / 类注释（L121「复用同一审计追溯通道」）自述语义为**权限投影审计**，不属对账差异，故它是 7 处中语义偏离最远的一处（既非入站、亦非对账）；② 其余 6 处的 `event_type` 各自为 `*DISCREPANCY*` 字面量但**六个字符串互不相同**（`JOB_LEASE_RECONCILE_DISCREPANCY` / `SESSION_RECONCILE_DISCREPANCY` / `MINIO_RECONCILE_DISCREPANCY` / `PUBLISHED_VERSION_RECONCILE` / `RECONCILE_DISCREPANCY` / `OUTBOX_RECONCILE_DISCREPANCY`），无任何字典、枚举或契约登记，与 `V18` 表内 `event_type` 的入站语义混用；③ 7 处内部写入**全部**包在 `try { … } catch (Exception e) { LOG.error(...) }` 内（逐点核对：`JobLeaseReconciler` L143、`SessionStagingReconciler` L135、`MinioStorageReconciler` L235、`AssetRepositoryReconciler` L217、`AuthorizationProjectionHandler` L132、`PublishedVersionReconciler` L225、`OutboxDeliveryReconciler` L141）→ **差异记录本身可以静默丢失**，即"用于发现不一致的通道"自身不一致时无告警、无审计、无指标。

**缺陷面 E：调度与并发 —— 纯内存调度违反 MUST 持久化，且 1970 哨兵 + 无事务使领取语义三重失效。**

- `REQ-WHK-001` L333 与 `REQ-JOB-001` L190 要求可靠的出站投递走**持久化 job 体系**；实现为 `NotificationConfiguration` L52 `scheduler.scheduleAtFixedRate(deliveryService::tick, Duration.ofSeconds(10))`（间隔硬编码 10s、无配置键），非 job 表驱动 → 进程内丢失即丢调度，与 AGENTS.md「Durable … work goes through the persistent job system, not raw threads or `@Async`」冲突。
- `JdbcOutboxRepository.claimPending` L85-100 把被领取行的 `processed_at` 写成哨兵 `'1970-01-01 00:00:01+00'`，产生三个独立后果：① 击穿 `V11` L76-77 的部分索引 `idx_outbox_event_pending … WHERE processed_at IS NULL`（滞留行不再被索引认领）；② `countPending()` L116-121 与 `OutboxDeliveryReconciler` L48-54 的 stale 检测都用 `processed_at IS NULL` → **双双看不见崩溃后永久滞留的行**（`/admin/notifications/outbox/pending-count` 因此会报 0）；③ `NotificationAdminService.OutboxEventView.from` L91 以 `e.processedAt() != null` 推导 boolean `processed` → 一条**刚被领取、尚未投递**的事件在管理端与契约（L5013 `processed` 为 required）上**显示为已处理**，即"投递中"被呈现为"已完成"。崩溃残留行无回捞路径：`markProcessed` 在 `WebhookDeliveryService` 共 **4 个**调用点 —— L93（无投递目标即丢弃）、L100（SSRF 拒绝即丢弃）、L130（2xx 成功）、L149（转 DEAD 后丢弃），全部位于 `deliverEvent` / `attemptDelivery` / `handleDeliveryFailure` 的**同一次执行流内**；一旦进程在 `claimPending` 之后崩溃，没有任何独立路径（对账器、调度器、管理端）会对 `processed_at = '1970-…'` 的行重新调用 `markProcessed` 或把它退回待领取集合。注意前两个调用点（L93/L100）正是缺陷面 A 的静默丢弃出口与缺陷面 C 的 SSRF 丢弃出口 —— 它们把事件写成「已处理」，因此这些事件连"滞留可见"的机会都没有。
- 领取全链**无 `@Transactional`**（全包 0 命中），因此 `FOR UPDATE SKIP LOCKED` 在无事务上下文中不成立（PostgreSQL 语义下每条语句自成一事务，锁在语句结束即释放）→ 跨实例互斥不保证（**未实测**，须 Compose 双实例 + 并发投递取证）。
- `markProcessed` L102-107 按 `event_id`（非主键）UPDATE，而 `event_id` 上无 UNIQUE 约束登记，重复行会被一次改完。
- `claimRetryable` L84-92（`JdbcWebhookDeliveryRepository`）是裸 `SELECT * … WHERE status IN ('PENDING','FAILED') AND (next_retry_at IS NULL OR next_retry_at <= ?) ORDER BY created_at LIMIT ?`，**无 `FOR UPDATE SKIP LOCKED`**（与同包 outbox 领取写法相反）→ 多实例重复重试（**未实测**）；`WebhookDeliveryService` L167-169 对 PENDING 行直接 `continue` → 每轮白耗 10 个名额中的一个；L172-173 为重试构造的占位 `OutboxEvent` 有 5 个空值（`aggregateType`/`aggregateId`/`eventType` 为 `""`，`headers`/`traceId`/`principalId` 为 null）→ 重试路径写出的行必然命中缺陷面 B 的空值校验分支。`calculateBackoff` L178-182 的 jitter 取值域为 `[exp/2+1, exp]`（非对称半窗）。`resetForRetry` L132-139 **不清零 `attempts`** → 管理端重试一条已 `attempts=5` 的 DEAD 行会立即再次 DEAD，而 `NotificationAdminController` L71 却返回 `"status","PENDING"`，返回体与真实状态相反。

**缺陷面 F：审计与规范义务的对象本身不存在；`AC-P0B-NOT-001..005` 五条 E4 静态不可满足。**

- `com.aihub.notification` 全包对 `auditService` / `AuditPort` / `new AuditEvent(` 的引用 **0 命中**（`NotificationAdminController` 全文亦然），而 `REQ-WHK-001` L337 明确要求「webhook 目标变更、投递失败、密钥轮换均须审计」三类义务 —— 其对象（webhook 目标注册端点、密钥轮换入口）在后端**根本不存在**，义务无从承载。`NOTIFICATION_MARKED_READ`（契约 L1482）后端 **0 实现**，`markRead` 幂等分支（`NotificationService` L183-186）只 `LOG.debug`。
- 事件断链：`AGENT_ACCESS_DENIED` 在通知面无生产者；`UPLOAD_FAILED` 仅出现在 outbox、无站内通知；`JOB_DEAD` 仅站内、无 outbox —— 与 `V11` L33 表注释所举「如 JOB_DEAD、AGENT_ACCESS_DENIED」的例子正好相反（注释举例的两个事件恰是链路不全的两个）。
- `05-acceptance/p0b-exit-catalog.md` L113-119 的 `AC-P0B-NOT-001..005` 五条：在「缺陷面 A（永不投递）+ 缺陷面 E（无事务、pending-count 恒 0）+ 缺陷面 F（无审计）」三重前提下**静态不可满足**；且 7 处 outbox 发布全被 `try/catch → LOG.warn` 包裹，意味着任何"事件不丢"类断言都不可能通过观测得到。本项据此判定该域的正确状态应是 `NOT_IMPLEMENTED` / `implemented` 描述失实，而非 `implemented-unverified`。
- 唯一"额外"实现：`fanOutInAppNotifications` L99-117 的「提交者排除」逻辑**超出** `REQ-NOT-001` L291-296 的规范描述（规范未要求排除提交者本人）—— 属实现多于规范的反向偏差，与 ㉗ 缺陷面 C 的「实现超出契约参数」同族。

**缺陷面 G：契约 / DB / 领域 / 视图 / TS 五层的字段与枚举口径不一（㉗ 缺陷面 D、㉑ 状态机四方差集在本域的第二、第三次复现）。**

- `severity` 三层存在、两层消失：`V11` L20/L26 有列与 CHECK、领域对象 L34 有字段、调用方逐点传值，但契约 `NotificationView`（L4398-4412）与后端 `NotificationView.java`（L26-32）**均无该字段** → 严重级被分级存储却从不呈现。
- `traceId` 契约必填而 DB 可空：契约 L5010-5021 的 `OutboxEventView.required` 含 `traceId`，而 `V11` L58 `trace_id VARCHAR(64)` 无 NOT NULL（L73 注释「可空」），`NotificationService` L142 仅在 `PrincipalContextHolder` 存在时取值 → 匿名/调度线程产生的行必为 null，违反自身 required。
- `WebhookDeliveryView` 的 `status`（L5031）为裸 `{type: string}` **无 enum**，与 `V11` L97-98 的四值 CHECK、前端 `types.ts` L393 的四值联合类型三方不一（契约侧最松）；`retry` 响应体的 `status`（L2843 附近）同样无 enum。
- 错误码与端点声明脱节：四端点 `x-error-codes` 均只列 `[AUTH_PERMISSION_DENIED]`（L2756/2781/2805/2830），而 `retryDelivery` 实抛 `NOTIFICATION_NOT_FOUND`（`NotificationAdminService` L78-80）且契约已在 L2850 声明 404 —— 声明与实现在同一文档内不自洽。
- `limit` 无上限声明：契约四端点的 `limit` 均无 `maximum`，后端唯一收敛在服务层（`NotificationAdminService` L48 / L61 夹到 200），控制器 L45 / L60 不夹取（与 ㉗ 中审计控制器在夹取上收敛于控制器的写法相反 → **同仓两域风格分裂**）；`L32 DEFAULT_LIMIT = 50` 声明后未被使用（死常量）。`/outbox/pending-count` 的 `data.count` 未标 `format: int64`，后端返回 `long`。
- `NotificationAdminService.countDeliveriesByStatus` L68-71 **全仓零调用者**（对 `countDeliveriesByStatus|WebhookDeliveryView|OutboxEventView` 于 `backend/src` 精确 15 命中中仅定义处 1 命中）→ 投递状态聚合能力写了但无人用。
- `WebhookDelivery` 的两处自述不实：L14/L25 与 `V11` L81 DB 注释均自称签名格式 `t=<timestamp>,v1=<hmac>`（即缺陷面 C 的错误格式已随迁移注释入库，按 AGENTS.md 不得改已应用迁移，只能新 `V45+` 以注释类迁移修正）；L25「lastError（已脱敏）」**不实** —— 全包对 `last_error`/`lastError` 的 9 处引用中，唯一处理是 `truncate()` L197-202（1000 字符截断，与 `V11` L96 `VARCHAR(1024)` 吻合），无任何 `SensitiveDataMasker` 参与（该包内 `masker` 只出现在 `NotificationService.maskParameters`），而写入内容包含 `ex.getMessage()`（L138/L140）→ 上游响应体片段可能原文入库并经 `/admin/notifications/deliveries` 原文回显。
- `maskParameters` L189-204 为三分支：L197 `value instanceof String s` → `masker.maskField(key, s)`；L199-200 非 String 且非 null → `masker.maskField(key, String.valueOf(value))`；L201-202 null → `put(key, null)`。即**非字符串仍经过脱敏器**（本会话据此收缩承前"被强转绕过"的表述）。真实缺口是掩码只按 key 名判定，且站内 `parameters` 与 outbox `payload` 共用同一实现（L138 `maskParameters(payload)`）。

**缺陷面 H：前端 admin 通知页与契约/规范脱节，且测试零门禁；i18n 文案面为 ㉔ 的既有取证，本项只引用。**

- `frontend/src/features/admin/api.ts` L1-6 头注称「写操作按契约携带 `Idempotency-Key`」，L71-73 `idempotent()` 提供该头，L321-323 `retryWebhookDelivery` 实际携带 —— 而契约 `POST …/deliveries/{deliveryId}:retry`（L2824-2850）**未声明该参数**，后端 `NotificationAdminController` L67-72 也不读取（承 22）。
- 前端全量渲染 `targetUrl`（`NotificationsPage.tsx` L203，`types.ts` L389-395 中 `targetUrl: string` 非可选）→ 与缺陷面 C 的 SSRF 面叠加：一旦未来真出现外部目标，该页会把内网探测地址列表暴露给所有 `system:observe` 持有者。重试按钮显示条件为 `FAILED || DEAD`（L224），与 `MIPS-ADM-015` L176 的要求不符。
- `PAGE-COM-003`（`page-catalog.md`）要求的用户侧 `/notifications` 路由在前端**不存在**；三层视图（站内通知 / outbox / deliveries）均无 `resourceType`/`resourceId` 引用字段 —— 与 `V11` L13-27 **无 resource 列**一致，即"通知指向哪个资产"这一核心体验在 schema、契约、前端三层同时缺失，用户点击通知无法跳转。
- `types.ts` L1-6 自称「对齐 OpenAPI 契约」但无 codegen（承 24），fixture 使用不存在的 `title`/`read` 字段并以 `vi.mock('../api')` 顶替 → 上述所有漂移在 CI 中不会变红。
- **i18n 面不得重复计入**：后端产出的 11 个 `notification.*` 键（`VersionApplicationService` L148/L179、`PublishApplicationService` L159/L260/L281/L322/L362、`DiscussionApplicationService` L302/L322、`SystemDependencyService` L114、`JobNotificationAdapter` L45）在 `system_i18n_message` 零行、前端原样渲染键名 —— 该缺口已由本文档 ㉔ 缺陷面 C（L1122-1127）完整取证，本项仅引用。可新增的一条是：`REQ-NOT-001` L295「模板按 locale 渲染」在**渲染层与展示层双重落空**，且全仓唯一形似"渲染"的实现是孤儿 `EmailChannelAdapter` L82-83（裸拼键名）。

**缺陷面 I：配置面「DB 说 8、代码用 5、配置不生效」的三值分裂。**

`V8__configuration.sql` L64/L69 种下 `job.webhook.maxRetries='8'` 与 `notification.webhook.enabled` 两键，`ConfigKeys` L28/L38 有对应常量，但二者在生产代码中**零消费者**；`WebhookDeliveryService` L43 另行硬编码 `private static final int MAX_ATTEMPTS = 5`。结果：管理员在配置界面把重试次数改成任何值都无效，代码用 5；`notification.webhook.enabled` 关闭开关无效（缺陷面 C 已述 dispatcher 由另一未声明键控制）。同族死字段：`EmailChannelProperties` L16 `enabled` —— `@ConditionalOnProperty` 与 `@EnableConfigurationProperties` 同挂在 `EmailNotificationConfiguration` L23-24 上，条件为假时配置类自身不装配，`enabled` 永无读取机会（该形态属 Spring 已知陷阱，登记但不判为独立缺陷，与 `enabled=false` 时邮件渠道本就是缺陷面 A 的孤儿代码这一事实合并处置）。

**缺陷面 J：可观测性三缺口 + 无界基数风险。**

`PlatformMetrics` L92-109：`updateWebhookInboxPending` **零调用者** → inbox pending gauge 恒 0；`recordWebhookDelivery` 无 `PENDING` 标签（只有 DELIVERED/DEAD/FAILED，对应 `WebhookDeliveryService` L204-212 的三态写入，与 `V11` L97-98 的四值状态机不符）；站内通知侧 `unread` / `created` 两类指标**不存在**。`recordCriticalEvent` L102-108 以 `deliveryId` 作 Counter 标签 → 每次投递产生新时间序列（无界基数），与 `V11` L81 注释同为"设计意图与实现互斥"的形态。承 19 的三种形态（gauge 恒 0 / 标签缺值 / 指标不存在）+ 缺陷面 G 的死聚合方法，共同构成「投递状态不可观测」的完整证据链。

**缺陷面 K：规范文档内部与跨文档互斥（须人类裁决，AI 不得择一）。**

- `REQ-NOT-001` **单块内自相矛盾**：L300-301「管理端页面在权限决策追加前不实现」 vs L308-310「Pages：`PAGE-COM-003`、`PAGE-ADM-015`」。
- 与之相邻的第三态：`NotificationAdminController` **已实现**四端点并**已登记**契约（L2750-2850），前端**已有页面** —— 即"不实现"这条限制在代码与契约两侧同时被违反，而 `PAGE-COM-003` 的路由又不存在。三者构成 `需求 ↔ 契约 ↔ 实现 ↔ UI` 四方各说各话。
- runbook 不实：`docs/runbooks/notification-channels.md` L10-16 / L48-62 描述的 `NotificationChannel` 分发链不存在（缺陷面 A 第 3 点已穷尽证明），应按"文档待重写"处置。

**取证偏差登记（不得作为缺陷写入）**：
- **两处自我收缩（本会话内主动更正）**：① 承前把 `OutboxDispatcherScheduler.stop()`（L55-59，无 `@PreDestroy`、全仓零调用者）判为"调度器泄漏"**过头** —— `ManagedExecutorsConfiguration` L78-79 的 `@Bean(name = "schedulerExecutor", destroyMethod = "shutdown")` 会在容器关闭时关停线程池，后果被上游兜住。正确表述是「`NotificationConfiguration` L37 注释『销毁时取消调度』把取消语义归给了没有该职责的对象，属注释与实现的职责归属错位」，本项按此收缩登记。② 承前「`maskParameters` 使非字符串值被强转绕过脱敏」**过头**，实为三分支且非字符串仍过 `masker`（见缺陷面 G 末段）。
- **未实测清单**（全部为静态结构推论，验收方须以 Compose 实测定论，不得据本节文字当作 E4 证据）：`FOR UPDATE SKIP LOCKED` 在无事务前提下的跨实例互斥是否真的失效；1970 哨兵行在生产是否有实际滞留样本；SSRF 拒绝的具体地址集合行为；`dev-only` 默认签名密钥是否在生产 profile 下生效；5xx 退避重试的实际时序；`/admin/notifications` 上"投递中显示为已处理"的可见性（依赖前两项）。
- **口径区分**：「`publishOutboxEvent` 调用点 7 处」是跨 5 个业务模块 + `platform/observability` + `integration/minio` 的口径（本会话逐点 Read 实参，非计数推断）；「`NotificationChannel` 引用 11 处」含接口自身与三个实现的 `import`/`implements`/`channelId()`，不可与「消费者数」混淆（消费者为 0）。
- **行号基准面**：本域引用的契约行号（L2748-2851、L4398-4417、L5008-5037、L1482）与 `04-ui`/`05-acceptance` 行号均以**当前工作树磁盘状态**为准（`contracts/openapi/aihub-v1.yaml` 处于 M 状态，HEAD `a022796`；`git status --short` 计 23 M + 21 未跟踪，其中含早于本会话的 preview 域改动 `V44__preview_split_stats.sql`、`TASK-P0BR-077..081`、`EVD-P0BR-077/001.yaml`、`frontend/e2e/*`、`build-logs/`、`playwright-report/`）。本会话**仅修改本迭代计划一个文件**，未触碰上述任何文件。
- **文档自身笔误登记（不静默改写）**：㉗ 收尾句位于 L1353，写作「本项与 ①—㉖ 一并…」，而其自身为 ㉗ —— 该句未把 ㉗ 纳入排除范围，属笔误。本项不改写 ㉗ 原文，仅在此登记；本项自身收尾句按 `①—㉘` 书写。
- Grep 工具的 count 模式摘要对本文件曾返回自相矛盾口径（正文 61 与「0 occurrence」并存），故本域的穷尽断言一律以带 `-n` 的 content 命中 + 逐点 Read 复核为准，未采信任何 count 摘要。
- 本节把「通知域此前无任何专项章节」作为编号前提：标题全量枚举（`^#{2,3} ` 共 26 项）中不存在通知/Webhook 专项，文件内 `notification|webhook` 命中全部位于 ㉔/㉕/㉖/㉗ 的交叉引用 → 编号 ㉘ 成立且不与既有项重复。

**根因（与 ㉕/㉖/㉗ 同源，本域另有两个特有因子）**：`REQ/事件契约 ↔ 迁移 ↔ 领域 ↔ 仓储 ↔ 服务 ↔ REST ↔ 契约 schema ↔ TS ↔ 前端` 九层中**没有任何一层是另一层的生成物或校验对象**（`openapi-typescript` 生成物未被 admin 域使用、`x-implementation-status` 不约束字段集与枚举、`events-v1.yaml` 自标 `target-p0-b` 因而不受任何一致性检查约束），故「契约要求 `evt_` 而代码生成 `req_`」「契约要求 `sha256=` 而代码输出 `t=,v1=`」「DB 有 `severity` 而视图没有」都能长期共存。本域两个特有因子放大后果：① **投递目标这一必要输入没有注册入口**（`REQ-WHK-001` L326/L337 假定的 webhook 目标管理端点不存在），于是唯一提供它的代码（`WebhookChannelAdapter`）既无调用者也无配置界面，形成"能力齐备但入口缺失"的**结构性死码**；② **用数据填表的合规伪装**（`webhook_inbox` 内部行硬编码 `signature_valid=true`、`processed_at` 哨兵、迁移注释里的错误签名格式、`WebhookDelivery` 自述「已脱敏」）—— 这些制品在文档层面让链路看起来成立，却在运行时层面无一对应真实行为。`implemented-unverified` 状态在本域被当成"差不多可用"使用，掩盖了 `NOT_IMPLEMENTED` 的真实分级。

**限定更正**：① 对本域此前任何「Webhook 投递已实现但未验证」的表述，统一限定为「**签名/退避/SSRF/对账等局部代码存在且形态自洽，但出站投递在结构上从未发生**（缺陷面 A 三段式），正确分级应为 `NOT_IMPLEMENTED`（至少对 `REQ-WHK-001` 的投递语义部分）」；② 对 ㉗ 缺陷面 D 的「`SELECT *` 与全列映射后丢弃」同族问题在本域为 `JdbcNotificationRepository` L82/89 与 `JdbcWebhookDeliveryRepository` L84-92/L119-124，量级相同，合并处置不重复计权；③ 缺陷面 E 的 pending-count 恒 0 结论**仅对"被领取后崩溃的滞留行"成立**，对"尚未被领取的等待行"（`processed_at` 确为 NULL）计数正确 —— 不得表述为"pending-count 全时段失真"；④ 缺陷面 F 的 `AC-P0B-NOT-*` 不可满足性是基于静态前提的推论，验收方仍可对其中的"站内通知"子项（`AC-P0B-NOT-001` 若仅指站内）另行取证，本项只对"涉及投递/审计/重试"的子项断言不可满足；⑤ i18n 通知键缺口归属 ㉔，本项引用不重复计入差集数量；⑥ 本项不修改任何禁改面文件（契约、01/02/04/05 规范、tasks/evidence、backend、frontend、已应用迁移），仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 裁决出站 Webhook 的存亡：要么补「webhook 目标注册」端点 + 存储 + 审计并把 `NotificationChannel` 分发链接入 `NotificationService`（须新 Task Card + 新 `V45+` 迁移建目标表），要么显式删除 `WebhookDeliveryService` / `webhook_delivery` / 四个 admin 端点 / `types.ts` 相关视图 / runbook 章节，并把 `REQ-WHK-001` 从 P0 移出 —— 两条路径互斥，且后者是破坏性契约变更，须 ADR；② 裁决签名约定三方（`contracts/events/README.md` L33-36、`events-v1.yaml` L31-39、`WebhookSigner` L42/L44）的唯一真相并出 ADR，同时把 `X-AIHub-Event` 补入实现、把时钟统一到注入 `Clock`（含 `WebhookSigner` L50-51 重载的移除）；③ `event_id` 前缀：新增 `IdPrefix.EVENT("evt")` 并出 `V45+` 数据迁移（`req_` → `evt_`）或反向把 `events-v1.yaml` L95 的 pattern 与 `OutboxDeliveryReconciler` L110 改为 `^req_` —— 后者会污染 `req_` 语义，须人类定夺；任何方向都不得改 `V11`；④ 把 outbox 投递接入持久化 job 体系（满足 `REQ-WHK-001` L333 / `REQ-JOB-001` L190），并以显式 `claimed_at`/`status` 列取代 1970 哨兵（`V45+` 增列，同步 `idx_outbox_event_pending` 谓词、`countPending`、`OutboxDeliveryReconciler` L48-54、`OutboxEventView.from` L91 四处口径），使"投递中"与"已处理"可区分；⑤ 为 outbox 写入与投递全链补 `@Transactional` / 事务边界，并把 7 个调用点的 `try/catch → LOG.warn` 改为「业务事务内写 outbox、不吞异常」或引入 Inbox/Outbox 对账补偿（AGENTS.md 的 Saga/Outbox 要求），同时修正 `OutboxEvent.java` L2/L13/L14/L17 与 `JdbcOutboxRepository` L23 的失实自述；⑥ `webhook_inbox` 去污染：7 个内部写入点改写专用表（或 `V45+` 拆列并回填真实 `source`），并统一与 `webhook_delivery.delivery_id` 的宽度（`V18` L7 VARCHAR(64) vs `V11` L85 VARCHAR(40)）；⑦ 配置面收敛：`MAX_ATTEMPTS`（L43）改读 `V8` 已种键，`notification.webhook.enabled` / `dispatcher.enabled` / `backoff-base-ms` / `backoff-cap-ms` 在 `application.yml` 与 `deploy/` 显式声明并纳入 profile 差异，签名密钥改为生产缺失即启动失败（禁止 fail-open 默认值）；⑧ 补 `REQ-WHK-001` L337 的三类审计（目标变更 / 投递失败 / 密钥轮换）与契约 L1482 的 `NOTIFICATION_MARKED_READ`，并把 `AGENT_ACCESS_DENIED` / `UPLOAD_FAILED` / `JOB_DEAD` 三事件的 outbox + 站内双路补齐（对齐 `V11` L33 的举例）；⑨ 契约回补 `NotificationView.severity`（**破坏性：新增 required 字段需 ADR**，若回补为可选则须同步 `types.ts`）、把 `OutboxEventView.required` 中的 `traceId` 移出或补 DB NOT NULL、为两个 `status` 加 enum、为 `limit` 加 `maximum`、为 `retry` 声明 `Idempotency-Key` 或前端删除该头、`pending-count` 标 `format: int64`、`x-error-codes` 补 `NOTIFICATION_NOT_FOUND`；⑩ `lastError` 走共享 `SensitiveDataMasker`（消除 `WebhookDelivery` L25 的失实自述与 `V11` L81 的错误签名注释，后者须以新迁移的注释修正实现）、`truncate` 与掩码收敛到共享实现；⑪ `resetForRetry` 清零 `attempts` 或 `retry` 返回真实状态（禁止 L71 硬编码 `"PENDING"`），`claimRetryable` 补 `FOR UPDATE SKIP LOCKED` 并与 `claimPending` 统一领取写法，`retryFailedDeliveries` L167-169 改为不占名额，L172-173 的占位 `OutboxEvent` 改为携带真实聚合信息（不得传 `""`/null）；⑫ 可观测性：`updateWebhookInboxPending` 接调用者、`recordWebhookDelivery` 补 `PENDING` 标签、`recordCriticalEvent` L102-108 移除 `deliveryId` 标签（改日志字段）、补站内通知 `unread`/`created` 指标、删除或接入 `countDeliveriesByStatus` 死方法；⑬ UI 面须由人类先裁决 `REQ-NOT-001` L300-301 与 L308-310 的互斥（管理端是否实现、`PAGE-ADM-015` 是否成立），再据定稿补 `PAGE-COM-003` 的 `/notifications` 路由与 resource 引用字段（依赖 ⑨/`V45+` 的 `resource_type`/`resource_id` 列），并按 `MIPS-ADM-015` L176 校正重试按钮显示条件、`targetUrl` 改为脱敏摘要；⑭ runbook `docs/runbooks/notification-channels.md` L10-16/L48-62 在 ① 裁决后重写，不得保留不存在的分发链描述；⑮ `AC-P0B-NOT-001..005` 的 E4 取证方案须人类先给出（含双实例并发投递、崩溃滞留回捞、SSRF 故障注入三个必测场景），本项只提供静态前提；⑯ 建立 `V11` 列 ↔ 领域 ↔ 视图 ↔ 契约 ↔ TS ↔ 前端列 的**六方一致性守卫测试**（与 ⑲—㉗ 的守卫合并为同一套门禁），至少断言：契约每个 `required` 字段在后端非 null 率、状态枚举四方一致（DB CHECK / Java enum / 契约 enum / TS 联合）、事件名前缀与 `events-v1.yaml` pattern 一致；⑰ 上述任一实现整改前需人类先出 `READY` + `implementationAuthorized: true` 的 Task Card（DEC-009），AI 不得自批。

→ 本项与 ①—㉘ 一并不在本轮 AI 自决实施范围内。

### ㉙ 持久化 Job / 幂等 / 调度平台面七层差集（REQ ↔ 不变量 ↔ 契约 ↔ 迁移 ↔ 领域 ↔ 基础设施 ↔ UI）

**取证范围与方法**：本项覆盖此前无任何专项章节的「可靠任务 + 写接口幂等」平台域（`^#{2,3} ` 标题全量枚举中不存在 Job/幂等/调度专项；既有项仅在 ㉑ 状态机四方、㉒ 路由前缀及 L531/L670/L724-726/L885/L903 零散触及，本项引用不重复计入）。规范侧锚点：`docs/ai-spec/01-requirements/p0b-platform-services.md` 的 `## REQ-IDM-001 写接口幂等`（L146-176，`status: READY / priority: MUST / minimumEvidenceLevel: E4`）与 `## REQ-JOB-001 PostgreSQL 可靠任务`（L178-215，`permissions: [job:read, job:manage]`）、`docs/ai-spec/02-domain/entity-invariants.md` §6.2 `INV-JOB-001..009`（L203-213）、`docs/ai-spec/05-acceptance/p0b-exit-catalog.md` L92-99（`AC-P0B-IDM-001..003`、`AC-P0B-JOB-001..005`）、`docs/ai-spec/04-ui/page-catalog.md` L33 与 `admin-pages-mips.md` L145-154（`PAGE-ADM-013` / `MIPS-ADM-013`）。代码侧锚点：`backend/src/main/java/com/aihub/job/**`（Glob 实测 37 个 .java，路径全集取自返回值）、`shared/idempotency/**`（4 类 + package-info）、`bootstrap/JobConfiguration.java`、`bootstrap/ManagedExecutorsConfiguration.java`、`contracts/openapi/aihub-v1.yaml`、`V9__job.sql` / `V40__idempotency_scope.sql` / `V41__job_attempt_trace.sql`、`frontend/src/features/admin/{pages/JobsPage.tsx,pages/IdempotencyPage.tsx,api.ts,types.ts}`、`deploy/**`。方法：内容 Grep 定锚 → 逐文件 Read 全文 → 跨层逐点比对 → 全仓调用点穷尽普查。所有穷尽性断言均以带 `-n` 的 content 命中 + 逐点 Read 复核为准。

**合规面（本域确有成立的部分，登记以免被差集叙述抹平）**：
- `JobStatus`（L14-22 六值 `PENDING/RUNNING/SUCCEEDED/RETRY_WAIT/DEAD/CANCELLED`）与 `V9` L63-64 的 `ck_job_task_status` CHECK、`aihub-v1.yaml` L4345 的 `JobStatus` enum **三方一致**（本域唯一一次四方对齐，与 ㉘ 的 `severity`/`signature` 形成正向对照）。
- `claimNext`（`JdbcJobRepository` L108-144）在**单条 CTE 语句**内使用 `FOR UPDATE SKIP LOCKED` + `RETURNING` 并写入 `leased_by`/`leased_until`，满足 `INV-JOB-004` / `REQ-JOB-001` L193-194 的「数据库锁 + lease owner/expiry」主路径。
- `V41__job_attempt_trace.sql` + `JdbcJobAttemptRepository` L28-37（九列 INSERT 含 `trace_id`）+ `JobWorker` L156/L161-163（崩溃滞留 attempt 的恢复补写）+ `JobApplicationService` L139-153 `resolveTraceId`，构成 `REQ-JOB-001` L201 / `INV-JOB-008` 的 trace 上下文传递链（Span Link 部分另论）。
- `JobAuditAdapter` 头注 L21-24 明确声明「审计写失败必须上抛」，且**未**吞异常 —— 与 ㉘ 缺陷面中 taxonomy 审计吞异常形成同域内的正向对照。
- `IdempotencySingleDecisionPointTest`（L20-37 类注释、L47/L68/L84/L100 四例）以源码扫描固化「全局只有一处幂等判定点」，把历史 `IdempotencyInterceptor` 缺陷变成回归门禁 —— 这是本域质量最高的工程制品。
- `contracts/openapi/aihub-v1.yaml` L1388-1420 的 `:retry` / `:cancel` 已登记 `x-audit-event: JOB_MANUALLY_RETRIED|CANCELLED` 与 `x-error-codes: [JOB_NOT_FOUND, JOB_STATE_NOT_ALLOWED, AUTH_PERMISSION_DENIED]`，对齐 `REQ-JOB-001` L199/L206 的部分要求。

**缺陷面 A：幂等完全没有 IN_PROGRESS 占位语义 —— `AC-P0B-IDM-003` 在代码层结构性不可满足。**
- 规范侧要求三处：`REQ-IDM-001` L164「首次请求原子登记 `IN_PROGRESS`，业务完成后保存原始 status/response」；L167「并发只允许一个执行」；L168「崩溃遗留 `IN_PROGRESS` 有 lease/owner/过期与恢复规则」。`AC-P0B-IDM-003`（catalog L94）＝「两个并发请求使用同一幂等键 → 只有一个执行」，`minimumEvidenceLevel: E4`。
- 实现侧相反：`JdbcIdempotencyStore.save`（L97-107）**只在业务 supplier 执行完成之后**才 `INSERT … status='COMPLETED'`；`find`（L44-78）只按作用域四列 + `status = 'COMPLETED'` 查行。**针对 `api_idempotency` 的 `'IN_PROGRESS'` / `'FAILED'` 写入点在全仓 `main/java` 为零**：Grep `IN_PROGRESS` 的 5 处命中全部属 `asset` 域的 `ProvisioningStatus`（枚举定义 L20 + Handler 推进 L84 等），Grep `"FAILED"` 的 12 处命中全部属 `job_attempt`（`JobWorker` L206/L250）、校验报告（`ValidationJobHandler` L94/L104/L123）、`publish_request`（`PublishJobHandler` L179/L182/L208）与 `webhook_delivery`（`JdbcWebhookDeliveryRepository` L88/L106/L137、`OutboxDeliveryReconciler` L61）—— **无一处写 `api_idempotency.status`**；该列的取值仅存在于 `V9` L25 的 CHECK 允许值列表。DB 用 CHECK **开放**了一个代码从不进入的状态域。
- 更强的一层：`REQ-IDM-001` L174 明文要求「State：IdempotencyStatus」，但 Glob `**/IdempotencyStatus.java` **返回 No file found** —— 该状态枚举在实现中**完全不存在**（对照 L213「State：JobStatus」确有 `JobStatus` 六值枚举且三方对齐）。故 L174 的领域模型项应标 `NOT_IMPLEMENTED`，而非"实现了但未验证"。
- **表结构本身不具备承载能力**：`api_idempotency`（`V9` L14-26）只有 `expires_at TIMESTAMPTZ`（L22，注释 L36「过期后可清理」）**一列**与恢复相关，**无 `lease_owner`、无 `lease_until`、无 `attempt`/`state` 迁移轨迹列** → `REQ-IDM-001` L168 的「lease/owner/过期与恢复规则」三项在**当前 schema 下无处表达**，整改必然需要新迁移增列，而不是改代码分支。
- 并发协议自述失实：`JdbcIdempotencyStore` 类注释 L28-29 称「`INSERT ON CONFLICT DO NOTHING` 保证并发下只有一个请求写入成功，其余读取已有记录」—— 把「**写入**只成功一次」当成了「**执行**只发生一次」。实际时序是 `find`（未命中）→ **执行业务** → `save`（DO NOTHING 静默丢弃），L108-111 更把 `DuplicateKeyException` 吞成 `LOG.debug`，L114 再 `find(...).orElse(record)` 返回**别人的**记录。两个并发同键请求因此**都完成了业务副作用**，败者的结果被无痕丢弃。
- 后果（静态推论，运行时行为**未实测**）：`V9` L16 的 `idempotency_key … UNIQUE`（及 L11/L30 的「全局唯一」注释）在 `V40__idempotency_scope.sql` L26-39 改为 `uq_api_idempotency_scope (idempotency_key, principal_id, method, path)` 后，同主体的并发请求命中同一作用域 → 至多一方写入成功，而**该唯一性冲突发生在业务副作用已经产生之后**。故 L167 与 `AC-P0B-IDM-003` 不是"未验证"，而是**当前写入协议下不可实现**。
- 派生 1：`expires_at` 在全仓**无任何写入点**（Grep `expires_at` 于 `backend/src/main` 的 13 处命中分属 `V16`/`V3` 的 `upload_session`、`iam_token` 与 `MinioStorageReconciler` L166，**无一处属于 `api_idempotency`**）→ 该列恒为 NULL，`find` 亦不判定 → 幂等记录**永不过期**、无 TTL 回收路径。
- 派生 2：HTTP 状态与响应体被**折叠进 JSONB**（`save` L83 + `writePayload` L130-135 写 `{"status":…,"body":…}`，`find` L69-72 读回），**没有 `response_status` 列**；当 payload 缺 `status` 键时 L69-70 **默认回填 200** —— 于是一个首次返回 201/202/409 的操作在回放时会**被伪装成 200**（触发条件为 payload 为空或 `readPayload` 解析失败走 L118-127 的 `Map.of()` 分支）。这属"用数据填表掩盖协议语义"的同族形态（对照 ㉘ 缺陷面的哨兵值与硬编码 `signature_valid=true`）。

**缺陷面 B：同键不同摘要的冲突检测在多数写端点上恒被跳过。**
- `IdempotencyService.execute` L55-60 的比对是**双侧非空才生效**（`existing.requestFingerprint() != null && incoming != null`），任一侧为 null 即直接回放。
- 全仓 `execute(key, …)` 调用点穷尽普查（Grep content 模式）：**12 处传 `null` 指纹** —— `JobController` L78/L96、`AssetController` L170、`AssetAccessController` L112、`RoleBindingController` L102、`ResourceAclController` L103、`SystemAgentController` L106/L123、`SystemUserController` L146/L163、`OrganizationController` L148、`TeamController` L192；其余端点（`AgentController` L180/L203/L229、`McpController` L181、`ConfigurationController` L81、`VersionController` L74、`TagController` L92/L119/L143/L163、`DictionaryController` L102/L131、`ProjectController` L84、`AssetController` L78/L153、`SystemUserController` L91/L124/L182、`SystemAgentController` L82/L145、`RoleController` L92、`OrganizationController` L87/L124、`TeamController` L89/L127/L166、`AssetAccessController` L87）计算并传入 SHA-256。
- 上述 12 处的共同形状是「无请求体的 DELETE / `:action` 端点」，即**恰恰是最需要防误重放的一族**（删资产、撤权、禁用用户、重试/取消任务）在同一幂等键下可被用于**不同**语义目标的复用而不被发现（作用域含 path，但同 path 不同 body/query 的组合仍无从判别）。`REQ-IDM-001` L166 的 `IDEMPOTENCY_KEY_CONFLICT` 对这些端点**永不可达**。
- 该形状正是 `IdempotencySingleDecisionPointTest` L23-27 记录的 `X-Idempotency-Replayed` 头来源。本项坐实：`X-Idempotency-Replayed` 在 `backend/src/main/java` 全仓 Grep **零命中** —— 拦截器已删，但该头从未在别处补上（契约侧也未声明），故 `replayed` 命中对外部**完全不可观测**（缺陷面 C 相关）。

**缺陷面 C：`IdempotencyResult` 被系统性丢弃，回放结果与原执行结果不可区分，HTTP 状态被硬编码覆盖。**
- 三档实现质量并存：① 正确消费型 —— `AssetController` L79-84 / L154-159 与 `VersionController` L75 反序列化 `result.response().body()` 后返回；② 半消费型 —— 多数 Controller 调 `execute` 后直接 `return respond(...)`；③ 完全丢弃型 —— **12 处**（与缺陷面 B 的 null 指纹集合高度重叠）。
- `JobController` L77-82 / L95-100 是最坏样本：先在 `execute` **之外**拼造常量响应（`:retry` L77 = `Map.of("jobId", jobId, "status", "PENDING")`；`:cancel` L95 = `Map.of("jobId", jobId, "status", "CANCELLED")`），supplier 内只调 `jobService.retryJob/cancelJob` 并返回序列化串，**返回值整个丢弃**，最后 `return respond(result)`（L82/L100）—— 于是**回放时既不返回首次真实状态、也不反映任务当前真实状态**：人工重试后任务可能已再次进入 `RETRY_WAIT`/`DEAD`，客户端却永远收到 `PENDING`；取消后任务若已因其它路径转为 `DEAD`，客户端仍永远收到 `CANCELLED`。
- `IdempotencyResult.replayed`（`IdempotencyService` L77 定义、L50/L61/L68 三个返回点赋值）在 `main/java` 中**除定义处零消费者**（Grep 实测 9 处命中全部位于 `IdempotencyService.java` 与 `IdempotencySupport.java` 的声明/构造）。`IdempotencySupport.executeWrite`（L78-86）作为"便捷方法"被建出来却**零调用者**（`main` 中仅 L78 定义行本身命中）；`AssetController` L250-265 与 `VersionController` L151 各自**私有复制**了 `buildIdempotencyKey` / `sha256Digest` / `serialize`，与 `IdempotencySupport` L32-51 逐行同构 —— 共享辅助类被绕过，违反 AGENTS.md「业务模块必须复用平台…idempotency/job 共享 API，不得创建本地替代」。
- 后果：`REQ-IDM-001` L164「保存原始 status/response」在**存储层只有折叠形态**（`IdempotencyRecord` L22-26 确有 `responseStatus`/`responseBody` 两个字段，但 `api_idempotency` 表**无对应独立列**，二者被压进 `response_payload` JSONB —— 见缺陷面 A 派生 2），在**协议层更不成立** —— 除 3 个端点外，没有任何调用方把存下来的 status/body 回放给客户端。

**缺陷面 D：契约 ↔ 实现的幂等记录字段名与必填集失配（`digest` vs `requestDigest`）。**
- `aihub-v1.yaml` L4495-4507 `IdempotencyRecord`：`required: [idempotencyKey, method, path, digest]`，另有 `responseStatus`、`expiresAt`。
- 实现侧 `IdempotencyRecordSummary`（L13-18 record 六组件）产出 `requestDigest`，**无 `digest`、无 `responseStatus`、无 `expiresAt`**；`IdempotencyQueryRepository` L29-42 的 SELECT 也只投影这六列（`idempotency_key, principal_id, method, path, request_digest, created_at`，`response_status`/`expires_at` 未选）。
- 前端 `types.ts` L410-417 `IdempotencyRecordView` 与实现对齐（同样是 `requestDigest`），**即 TS 类型是从实现抄的、不是从契约生成的**（`openapi-typescript` 生成物未被 admin 域使用，同 ㉘ 根因）。
- 于是 `/system/idempotency` 的 200 响应在**任何一次成功调用中都缺少契约声明的 required 属性** `digest`，属于契约校验器一旦接入即红的硬失配（当前无 schema 校验器接入，故长期存活 —— **未实测**）。
- `IdempotencyKey` 参数（契约 L3681-3689）声明 `in: header, required: true, minLength: 8, maxLength: 128`；`JobController` L72/L90 用 `@RequestHeader(value = "Idempotency-Key", required = false)`，其余 20+ 端点同样 `required = false`；`IdempotencySupport.buildKey` L34-36 对空值**返回 null 并跳过幂等**；`IdempotencyKey` 的紧凑构造器（L21-25）只校验非空白、**不校验 minLength/maxLength**。→ `required: true` 与 `minLength: 8` 两个契约约束在实现中**双双不存在**，且 `sha256Digest` 失败时返回字面量 `"unknown"`（L49）作为指纹参与比对（长度 7 < 8，若按契约本应非法）。

**缺陷面 E：幂等查询面存在跨主体信息暴露候选。**
- `IdempotencyQueryRepository.listRecent`（L28-43）的 SQL 谓词只有 `WHERE status = 'COMPLETED' ORDER BY created_at DESC LIMIT ?`，**无 `principal_id` 过滤、无组织/团队范围过滤**。
- `IdempotencyController` L40-45 只做 `requirePermission(Permissions.JOB_READ)`。→ 任何持 `job:read` 的主体可浏览**全平台所有主体**的幂等键、方法、路径与请求摘要。
- `IdempotencyQueryService` L27 `Math.min(Math.max(limit, 1), 200)` 对越界静默夹紧；契约 L2700-2723 只声明 `default: 50` / `maximum: 200`，未声明 `minimum` 与非法值语义 → 传 `limit=0`/`-1` 得到 1 条而非 400（**未实测**）。
- 归属判定：这不是"权限缺失"而是"权限粒度假定错误"—— `REQ-IDM-001` L162 把 principal 当作作用域维度，但查询侧把它当作了可展示属性。

**缺陷面 F：不存在受控 Task Type Catalog（`INV-JOB-001` / `REQ-JOB-001` L191 整段未实现），且注册冲突静默覆盖。**
- 规范侧：`REQ-JOB-001` L191「Task Type/Schema/Handler/最大重试/超时/并发策略在受控 Catalog 注册」；`INV-JOB-001`「Job Type 必须注册对应幂等 Handler」。
- 实现侧：`JobHandlerRegistry` L25-30 用 `handlers.put(handler.type(), handler)` 直接建表 —— **重复 `type()` 时后注册者静默胜出，无 fail-fast、无日志、无告警**；全仓 `type()` 实测 17 个实现（`sample.noop` + 16 个业务类型，逐一列举：`REPOSITORY_PROVISION`、`ASSET_PHYSICAL_RECONCILE` 系列的 `ASSET_REPO_RECONCILE`、`ASSET_PHYSICAL_RECYCLE`、`ASSET_CARD_SYNC`、`STAGING_CLEANUP`、`SESSION_STAGING_RECONCILE`、`MINIO_STORAGE_RECONCILE`、`VERSION_VALIDATE`、`VERSION_PUBLISH`、`PREVIEW_GENERATE`、`OUTBOX_DELIVERY_RECONCILE`、`WEBHOOK_PROCESS`、`PUBLISHED_VERSION_RECONCILE`、`AUTH_PROJECTION`、`UPLOAD_MATERIALIZE`、`JOB_LEASE_RECONCILE`），类型名是**散落在 10 个模块里的自由字符串**，无共享枚举、无常量类、无 Catalog 表。
- 存储侧：`V9` L48 `type VARCHAR(64) NOT NULL` **无 CHECK**（对照同文件 L63-66 为 status/attempts 都加了 CHECK，L25 为幂等 status 加了 CHECK —— 即"约束在别处存在、在类型这一列缺席"是显式设计而非疏漏）。
- 于是 L191 要求的 Schema/最大重试/超时/并发策略四项**完全没有注册位置**：`max_attempts` 只有入队默认值与 `RecurringJobBootstrapper` 的硬编码表，**超时**与**并发策略**两个概念在全仓无对应字段。
- `JobHandlerRegistry` 类注释 L17-18 仍自述「真实业务 Handler 属后续阶段，本任务提供框架 + 示例 Handler」，与同仓已存在 16 个业务 Handler 的事实矛盾 → 文档陈旧（同 ㉘ 的 runbook 失实同族）。

**缺陷面 G：`INV-JOB-009` / `REQ-JOB-001` L202 的 Sample Handler 治理要求不成立。**
- `SampleJobHandler` L20-21 是**无条件 `@Component`**（无 `@Profile`、无 `@ConditionalOnProperty`），L27 `type()` 返回 `"sample.noop"` → 在任何 profile（含生产）的容器里都注册进 Catalog 并常驻。
- `INV-JOB-009`「不允许 Sample/Noop Handler 承担正式业务类型」在"承担正式业务类型"字面上未被违反（`sample.noop` 确实不被任何业务入队点使用 —— 全仓入队点普查未见该字面量）；但 `REQ-JOB-001` L202「正式部署不能只有 SampleJobHandler」的**治理意图**（生产环境必须显式声明可用任务面）无任何机制承载：没有 Catalog 快照、没有启动期类型清单校验、没有 `sample.*` 类型在生产 profile 的禁用开关。精确表述为：**Sample Handler 常驻生产容器且与业务 Handler 在装配面上不可区分，平台无法在启动或验收时证明"当前容器承担了哪些正式业务类型"。**

**缺陷面 H：attempt 记录在「人工重试 DEAD 任务」路径上事实丢失（复合缺陷，三段独立证据）。**
1. `JdbcJobRepository.resetToPending`（L173-181）`UPDATE … SET status='PENDING' … WHERE job_id=? AND status IN ('DEAD','RETRY_WAIT')` —— **不清零 `attempts`、不清 `leased_by`/`leased_until`**；`JobApplicationService` L78-83 的注释把这写成"设计后果非缺陷（TASK-P0BR-024）"，L85-96 `retryJob` 审计记录 `budgetReset=false` 以示自洽。
2. `JdbcJobRepository.claimNext` L130 `attempts = CASE WHEN attempts < max_attempts THEN attempts+1 ELSE attempts END` —— **`attempts` 封顶于 `max_attempts`**，故第 1 段留下的"已耗尽"计数在重试后**无法再增长**。
3. `JobWorker.recordAttempt`（L281-288）先 `exists(jobId, attemptNo)`，命中即 `LOG.warn` + **直接 return**（不写、不改）。而 `executeOnce` L156 `int attemptNo = job.attempts()` 取自第 2 段封顶后的同一值。
- 合起来（**静态推论；实际运行时是否发生零记录属未实测**）：一个 `max_attempts` 已耗尽的 DEAD 任务被人工重试后，其**整轮新执行在 `job_attempt` 上零行记录** → 直接违反 `REQ-JOB-001` L198「attempt 记录时间、结果、errorCode、脱敏摘要」与 `INV-JOB-005`「attempt 单调递增」（此处既不递增、也不落表）。
- 同一路径的第二后果：`JobWorker.isTerminalFailure`（L264-269）`attemptNo >= maxAttempts` 立即为真 → 人工重试后的**第一次失败即回到 DEAD**，`RETRY_WAIT` 与退避计算完全不参与，`AC-P0B-JOB-002/004` 所设想的"重试后仍可多次尝试"语义在此路径不成立。
- 附带：`markSucceeded`/`markRetryWait`/`markDead`（L146-171）**均不校验 `row_version`**，而 `Job` 领域对象（L34-50 十七组件）**携带 `rowVersion`** 字段 —— 乐观锁列存在于模型却不被状态写路径使用，构成"两个 Worker 实例对同一 job 的状态互相覆盖"的结构前提（`INV-JOB-007` 终态不可逆的守护仅靠 SQL 的 `status=` 谓词，不靠版本）。

**缺陷面 I：存在两套互不知晓的租约恢复 / DEAD 判定写路径，第二套静默且副作用缺失。**
- 路径一（Worker 内建）：`claimNext` L119 允许领取过期租约的 RUNNING 行 → `JobClaim.recoveredFromAbandonedLease()`（`previousStatus == RUNNING`）→ `JobWorker` L161-163 恢复补写上一轮 attempt → `recordAbandonedAttempt` L200 以 `attempts-1` 编号落表。
- 路径二（`JobLeaseReconciler`）：L48-51 **无条件** `UPDATE job_task SET status='PENDING' … WHERE status='RUNNING' AND leased_until < NOW()` —— **不补写被终止的 attempt**、不与路径一协调。同一滞留行由谁先处置取决于时序，两条路径产生的 `job_attempt` 记录集合不同 → `AC-P0B-JOB-002`「Worker 领取后被强制终止 → 租约过期后重新领取；Handler 幂等」在两种处置下证据不同形（**未实测，须 Compose 双路径故障注入定论**）。
- 路径二的 DEAD 分支更严重：L58-62 直接把超限任务标 `status='DEAD'`、`error_code='MAX_ATTEMPTS_EXCEEDED'`，但**不写 `job_attempt`、不写 `JOB_DEAD` 审计、不发通知、不计 `jobsDead` 计数器** —— 与 `JobWorker` 的 DEAD 路径（L228-255 `handleFailure` 事务内三元组 + L290-295 `notifyJobDead` + L303-312 `auditJobDead`）副作用集完全不同。→ `REQ-JOB-001` L197「进入 DEAD 并通知/告警/审计」与 `AC-P0B-JOB-004` **对经由路径二死亡的作业不成立**；`AlertService` L177-188 的 `DEAD_JOB` 告警与 L230-238 `job_queue_size_dead` 指标也据此出现"DB 有 DEAD 行但告警/审计无痕"。
- `JobLeaseReconciler` 类注释 L16-19 声明三项检查，其中第三项「RUNNING 任务无对应 attempt → MANUAL_REVIEW」在 L106-130 `checkJobLease` 中**从不查询 `job_attempt`** → 注释所述的守卫整段未实现（第三个方法只做 `webhook_inbox`/outbox 侧检查，已计入 ㉘）。
- 另有两处实现质量问题：L98 `Thread.sleep(100)` 在 Worker 线程内阻塞；L121-122 对候选行逐行发查询（N+1）。
- 恢复路径还包含一个跨模块唯一调用：`JdbcJobRepository.hasActiveByTypeAndAssetId`（L200-207）全仓唯一调用者是 `AssetRepositoryReconciler` L178 —— 说明该"幂等前置查询"能力只被单个业务对账器使用，未上升为入队统一约束（对照 `INV-JOB-003`「同一业务工作有稳定 deduplication key」：`dedupe_key` 列存在，但入队侧去重仅在 `RecurringJobBootstrapper` L75-80 自行实现，`enqueue` 主路径无去重 —— **须由人类确认 `enqueue` 是否本就不该去重**）。

**缺陷面 J：所谓"周期任务"实为进程启动期一次性投递，`SCHEDULED` 状态不存在，且绕过统一入队口。**
- `RecurringJobBootstrapper` L24-25 类注释同时犯两错：提到一个**不存在的 `SCHEDULED` 状态**（`JobStatus` 六值无此项，`V9` L63-64 CHECK 也无），并称「周期调度由 JobWorker 的 lease-recovery 驱动，无需额外 `@Scheduled`」。
- 实现侧：L74 `jobRepository.list(null, null, null, 1000)` —— 走 `JdbcJobRepository.list`（L87-106）的**无 cursor 分支 `ORDER BY created_at, id LIMIT ?`，即升序取最旧的 1000 行**；L75-80 用 `isActive`（L98-102 只认 PENDING/RUNNING/RETRY_WAIT）去重；L82 用 `Instant.now()` 直算 `next_run_at`；L84-89 **绕过 `JobApplicationService.enqueue` 直接 `new Job(...)`，且 `traceId=null`**；L92-95 `catch → LOG.warn("will retry on next startup")`。
- 三重后果（前两项静态成立，第三项时序影响**未实测**）：① 一旦某周期任务的 `SUCCEEDED` 行落库，`isActive` 判假 → **下一次启动不会重建**，而当前行又已是终态 → 该周期任务事实上**只在整个数据库生命周期里执行一次**；② 最旧 1000 行窗口：随着历史作业累积，7 个周期定义（L46-54）中排在窗口外的类型**静默不再投递**，无任何告警；③ 绕过 `enqueue` 使这些作业的 `traceId` 恒 null，与 `REQ-JOB-001` L201 / `INV-JOB-008`「任务传递 trace/principal/resource context」冲突，且 `PAGE-ADM-013` 要求的 Trace 展示对这些作业永久为空。
- `JobLeaseReconciler` 的类型 `JOB_LEASE_RECONCILE` 在此表中 `maxAttempts` 为 10，其余 5（L46-54）—— 这是全仓唯一的"每类型重试预算"声明处，恰证缺陷面 F「无 Catalog」：预算存在但不受控、不集中、不与 Handler 同处。

**缺陷面 K：可观测性与错误码面缺 REQ 声明项；前端两页违反 MIPS 状态门控且无 Trace 列；部署基线未区分 Worker 开关。**
- 指标：`REQ-JOB-001` L208 逐项要求「pending/running/retry/dead、**claim conflict**、**lease expiry**、duration、attempt」。实测存在：`JobWorker` L110-113 四个 Micrometer 计数器（`job.processed{result=claimed|succeeded|failed|dead}`，`MeterRegistry` 缺失时回落 `NoopCounter` L327-345）、`JobConfiguration` L53-71 五个 `job.queue.size` gauge、`PlatformMetrics` L38 `aihub_job_processing_duration_seconds` / L55 `aihub_job_queue_depth` / L58 `aihub_job_dead_count`、`JobMetricsAdapter` L27-30 桥接 `countByStatus`。**全仓无 `claim conflict` 与 `lease expiry` 两项的任何计数器/仪表/日志埋点**（Grep `claim`/`lease` 于 `com/aihub/platform/observability` 仅命中 `PlatformMetrics` 的 L38/L55/L58 与 `AlertService` 的 L177/L180/L188/L230/L238，均非该两项）。→ L208 的两项指标是**结构性缺失**，而这两项恰是缺陷面 I 双路径竞争能否被发现唯一手段。
- 错误码：`REQ-JOB-001` L206 声明 `RETRY_EXHAUSTED`，但 `JobWorker` 实际产生 `INTERNAL_ERROR`（`resolveErrorCode` L271-273）、`MAX_ATTEMPTS_EXCEEDED`（`JobLeaseReconciler` L62）、`HANDLER_NOT_FOUND`（`handleNoHandler` L212-226）—— **`RETRY_EXHAUSTED` 在 `main/java` 零产生点**；契约 L1399/L1416 两个端点的 `x-error-codes` 也未包含它。
- Span Link：`REQ-JOB-001` L201 与 `INV-JOB-008` 要求「Worker 建 Span Link / 建立新上下文」。实测 `JobWorker` 只有 traceId 的**传递与落库**，无任何 `Span`/`addLink`/`ContextPropagation` 调用（全仓 Grep 无命中）→ 该半句未实现。
- 审计语义：`JobAuditAdapter` L36-53 的 `result` **恒 `AuditResult.SUCCEEDED`**（即使事件是 `JOB_DEAD`）、`principalType` 传 null、`actorId` 空则落 `"system"` —— 与 ㉘ 缺陷面同型，本项按同族合并计权不重复展开；`JobNotificationAdapter` L35 `recipient = principalId ?: "system"` 的"system 非真实主体 → 收件人悬挂"承 ㉘ 结论引用。
- UI：`page-catalog.md` L33 要求 `PAGE-ADM-013` 展示「状态、attempt、错误、**Trace**、重试/取消」。`JobsPage.tsx` L77-105 列为 jobId/jobType/status/retryCount/nextRunAt/errorCode —— **无 Trace 列**（尽管 `JobView` 契约 L4343-4369 与后端 `resolveTraceId` 已提供该字段）；L89-104 动作列对**每一行无条件**渲染 retry 按钮与 Popconfirm cancel，**无任何状态门控** → 直接违反 `MIPS-ADM-013` L154「只在合法状态展示动作按钮（retry: FAILED/CANCELLED；cancel: PENDING/RUNNING）」。
- `IdempotencyPage.tsx`：L21 `limit=100`、L67 `rowKey="idempotencyKey"`、L70 客户端 `pageSize 20` 无游标。`rowKey` 用裸键值，而 `V40__idempotency_scope.sql` 已把唯一性从全局改为作用域内 → **跨主体/跨方法/跨路径可出现同 `idempotencyKey` 的重复 React rowKey**（渲染后果**未实测**，结构前提成立）。
- `api.ts`：L284-290 `retryJob`/`cancelJob` **每次调用都 `newIdempotencyKey()` 新生成键** → `REQ-IDM-001` L170「客户端自动重试复用同一 key」在前端**主动不成立**（键形同随机数，幂等保护对这两个端点等于不存在）；L62-68 `newIdempotencyKey()` 在 `crypto.randomUUID` 缺失时回落 `Date.now().toString(36)+Math.random().toString(36)` —— 该拼接串的**长度与字符集未受契约 `minLength: 8` 约束核验**（与缺陷面 D 的"minLength 无处校验"叠加）。
- 部署：Grep `AIHUB_JOB|aihub\.job|job\.worker|job\.queue` 于 `deploy/` **仅 1 处命中**（`deploy/compose/scripts/p5-preview/preview-pressure-drill.sh` L149 的一句注释「aihub.job.worker.enabled matchIfMissing=true」）。→ 交付基线**从未显式区分 backend 与 worker 进程的 Worker 开关**，两个进程都实例化 `JobWorkerScheduler`（`JobConfiguration` L101-108 `@ConditionalOnProperty(matchIfMissing=true)`）并同时以 `interval-seconds:5` 轮询。这使缺陷面 H/I 的多实例竞争假设**在生产拓扑下默认成立**，而非仅故障场景（实际重复执行行为**未实测**）。
- 线程模型：`ManagedExecutorsConfiguration` L77-90 `schedulerExecutor` 的 `poolSize=2` + `CallerRunsPolicy`，而 `JobWorker.tick`（L126-137）在单次 tick 内**串行 for batchSize** 执行、**无 per-job 超时**，同池还承载其它周期任务。→ 本项据此把前一版"吞吐影响未实测"收缩为「**配置已坐实**：慢 Handler 会占用 poolSize=2 的共享调度器并可能饿死同池其它周期任务；**运行时饿死是否发生仍未实测**」。`REQ-JOB-001` L191 的「并发策略」正对应此处，却只有硬编码 poolSize 与一个未声明的默认值承载（契约 L1363-1386 `/system/jobs` 的 `limit {minimum:1,maximum:100,default:20}` 与 `JobApplicationService.listJobs` L62-73 的默认值亦未逐项核对为同源）。

**缺陷面 L：规范文档内部失配与文档↔迁移引用错误（须人类裁决，AI 不得择一）。**
- `MIPS-ADM-013`（L145-154）自身使用的状态名 **`FAILED` 不在 `JobStatus` 六值中**（六值为 `PENDING/RUNNING/SUCCEEDED/RETRY_WAIT/DEAD/CANCELLED`），其「retry: FAILED/CANCELLED」的门控条件若被字面实现，`FAILED` 永不匹配、`DEAD`（真正需要人工重试的态）反被排除 → 规范与领域状态机互斥，本项**不代为翻译**成 `DEAD`。
- `V9` 的注释与后续迁移冲突：L11「`idempotency_key` 全局唯一」与 L30 列注释「客户端幂等键，全局唯一（INSERT ON CONFLICT 处理并发重复）」在 `V40__idempotency_scope.sql` L26-39 把唯一性改为 `(key, principal_id, method, path)` 作用域复合唯一**之后即为失实注释**，同 ㉘ 的 `V11` L81 错误签名注释同型；`AGENTS.md`「不得修改已应用迁移」使该注释只能由新迁移的 `COMMENT ON` 修正。另 `V9` L71 列注释「任务类型，路由到对应**幂等** JobHandler」把 Handler 幂等当作既有事实陈述，而本域无任何 Handler 幂等实现或测试证据（缺陷面 F/H），属**未证成的口径**（与上一条性质不同，故分开登记，不并列为"失实"）。
- `docs/ai-spec/tasks/evidence/EVD-P0BR-023-001.yaml` **L141 引用 `V27__job_attempt_trace.sql`，而磁盘实际文件为 `V41__job_attempt_trace.sql`** —— E3 证据的工件路径指向不存在的迁移文件名。本项只登记，不改 evidence 目录。
- `JobHandlerRegistry` L17-18 与 `RecurringJobBootstrapper` L24-25 两处类注释分别陈述了与同仓事实/与自身代码不符的架构承诺（缺陷面 F、J 已述），归入"文档待重写"而非功能缺陷。

**取证偏差登记（不得作为缺陷写入）**：
- **落盘后自证更正记录（第二轮，逐条磁盘复核所得，非人工指出）**：本项首轮成文后经回读复核发现并已在原位修正六处表述：① 幂等查询仓储类名误写为 `JdbcIdempotencyQueryRepository`，Glob 实测磁盘仅有 `IdempotencyQueryRepository.java`（缺陷面 D、E 已改，方法名/行号同步定为 `listRecent` L28-43）；② 缺陷面 C 曾把 `:retry`/`:cancel` 都写成硬编码 `"PENDING"`，Read `JobController` L66-101 实测 `:cancel` L95 拼的是 `"CANCELLED"`，行号亦由 L75/L93 更正为 L77/L95（缺陷面 C 与整改待批 ③ 已同步）；③ 缺陷面 C 曾写「`response_status`/`response_body` 有列」，与缺陷面 A 派生 2「无 `response_status` 列」自相矛盾；Read `JdbcIdempotencyStore.save` L82-115 与 `V9` L14-26 坐实**表无独立列、仅 `IdempotencyRecord` record 有字段**，已改为「存储层只有折叠形态」；④ 缺陷面 A 的 `save`/`find` 行号由 L99-107/L52-60 更正为 L97-107/L44-78；⑤ 缺陷面 L 曾把 `V9` 的失实注释定位在 L72 并把「幂等 Handler」注释与「全局唯一」注释并列为同一条"失实"，Read `V9` 全文实测：失实注释在 **L11 表注释与 L30 列注释**，`type` 列注释在 **L71** 且其"幂等"提法应定级为**未证成口径**而非失实（整改待批 ⑭ 已同步）；⑥ 缺陷面 A 曾写「`'IN_PROGRESS'`/`'FAILED'` 字面量在全仓 `main/java` 零写入点」——**过头且不实**：Grep 实测 `IN_PROGRESS` 有 5 处、`"FAILED"` 有 12 处写入点，只是全部属于 `ProvisioningStatus`/`job_attempt`/`publish_request`/`webhook_delivery` 等**他域**。已改写为「**针对 `api_idempotency.status`** 的写入点为零」并逐条列出他域命中以自证分流依据。同一次复核另坐实 `IdempotencyStatus` 类**根本不存在**（Glob No file found），原「仅存在于…枚举名中」的措辞亦属不实，已改为独立一条 `NOT_IMPLEMENTED` 判定。六处均登记于此，不静默改口。
- **一处自我收缩（本会话内主动更正）**：承前把 `RecurringJobBootstrapper` L74 的 `list(null,null,null,1000)` 判为"取最新 1000 行"**过头** —— 实测 `JdbcJobRepository.list`（L87-106）在**无 cursor** 走 `ORDER BY created_at, id` **升序**，取的是**最旧** 1000 行；本项按"最旧窗口 + 排窗外类型静默不投递"表述（缺陷面 J 第 ② 项）。
- **一处口径收缩**：`INV-JOB-009` 字面（"不允许 Sample/Noop Handler 承担正式业务类型"）**未被违反**（全仓入队点普查未见 `sample.noop`），本项只在缺陷面 G 中以 `REQ-JOB-001` L202 的治理意图立论，不得表述为"`INV-JOB-009` 被违反"。
- **一处证据边界**：缺陷面 H 的三段证据（`resetToPending` L173-181、`claimNext` L130、`recordAttempt` L281-288）各为直接 Read 所得，**组合后的"零 attempt 记录"是推论**，未在真实数据库上复现（`JdbcJobRepositoryIT`/`JobWorkerTest` 是否覆盖该序列本项未逐例核对，见下一条）。
- **未实测清单**（全部为静态结构推论，验收方须以 Compose 实测定论，**不得据本节文字当作 E4 证据**）：并发同键双请求的实际交错与唯一键冲突抛错点；`limit=0`/负值在 HTTP 层是否返回 400；`IdempotencyRecord` 缺 `digest` 是否触发任何契约校验器（当前无校验器接入）；路径一与路径二对同一滞留行的**时序竞争结果**；人工重试后 `job_attempt` 是否真的零行；共享调度器 poolSize=2 下慢 Handler 的实际饿死表现；backend 与 worker 双进程同时轮询是否产生重复执行；`row_version` 不校验下的状态互相覆盖样本；`X-Idempotency-Replayed` 头缺席对客户端的实际影响。
- **测试面未逐例核对**：本域存在 10 个测试文件（Glob 实测：`JobWorkerTest`、`JdbcJobRepositoryTest`、`JdbcJobRepositoryIT`、`JdbcJobRepositoryInsertSqlTest`、`JdbcIdempotencyStoreIT`（L36 `@Testcontainers(disabledWithoutDocker = true)` → Docker 缺失即 SKIP，按 AGENTS.md「因 Docker 不可用而跳过不算通过的集成测试」）、`JobLeaseReconcilerTest`、`IdempotencyControllerTest`、`IdempotencyServiceTest`、`IdempotencySupportTest`、`IdempotencySingleDecisionPointTest`、`JobApplicationServiceManualOpsTest`、`JobApplicationServiceEnqueueTest`、`BackoffCalculatorTest`）。本项只逐行读了 `IdempotencyServiceTest` L100-131（`shouldReplayWhenExistingFingerprintNull` **把缺陷面 B 的跳过行为固化为期望**、`shouldExecuteWithoutIdempotencyWhenKeyNull` **把缺陷面 C 的 key=null 直放固化为期望**）与 `BackoffCalculatorTest`（三例均为区间断言，未断言抖动语义命名）。**其余测试是否掩盖同类缺陷，本项明确标注为未完成取证**，不得写成"测试全绿即合规"或"测试全盲"。
- **`BackoffCalculator` 的溢出问题按低概率登记、不作硬缺陷**：L33-38 中 `baseMillis * (1L << Math.max(0, attempt - 1))` 的乘法发生在 `Math.min` **之前**，理论上 attempt 足够大时 long 溢出会使 `exp` 变负、`ThreadLocalRandom.nextLong(exp/2+1, exp+1)` 抛 IAE；但 `max_attempts` 实测取值区间为 5–10（`V9` L51 默认 5、`RecurringJobBootstrapper` L46-54 为 5/10），`base-millis:2000`/`cap-millis:300000`（`JobConfiguration` L44-48）下**不可达** → 只登记两条可确证的次要问题：注释自称「full jitter」而实作区间为 `[exp/2, exp]`（应为 `[0, exp]`，属命名/语义失实）；`ThreadLocalRandom` 直接取用无注入点，使退避不可确定性测试。
- **路径推断错误更正**：本项曾按包名假设 `ManagedExecutorsConfiguration` 位于 `com/aihub/shared/config/`，Grep 返回 os error 3；改用 Glob 坐实真实路径为 `com/aihub/bootstrap/ManagedExecutorsConfiguration.java`。同理 `Glob 01-requirements/{job,idempotency}*.md` 返回 No file found —— `REQ-JOB-001`/`REQ-IDM-001` 实际写在 `p0b-platform-services.md` 内且为 **`## ` 级标题**（首次用 `^### REQ-` Grep 返回 No matches）。两处均记为工具/推断偏差，非代码缺陷。
- **行号基准面**：本域契约行号（L1363-1386、L1388-1420、L2700-2723、L3681-3689、L4343-4369、L4495-4507）与 01/02/04/05 规范行号均以**当前工作树磁盘状态**为准；`contracts/openapi/aihub-v1.yaml` 处于 M 状态（HEAD `a022796`）。本会话**仅修改本迭代计划一个文件**，未触碰契约、01/02/04/05 规范、tasks/evidence、backend、frontend、`deploy`、已应用迁移（`V9`/`V20`/`V40`/`V41`）中任何一件。
- 本节把「Job/幂等/调度域此前无任何专项章节」作为编号前提：标题全量枚举（`^#{2,3} ` 共 27 项）中不存在该专项，文件内既有 `job|idempotency` 命中全部位于 ㉑/㉒ 及 L531/L670/L724-726/L885/L903 的交叉引用 → 编号 ㉙ 成立且不与既有项重复。㉘ 收尾句按 `①—㉘` 书写且自身编号为 ㉘，无 ㉗ 那句的笔误，本项收尾句据实写为 `①—㉙`。

**根因（与 ㉕—㉘ 同源，本域另有三个特有因子）**：`REQ/不变量 ↔ 契约 ↔ 迁移 ↔ 领域 ↔ 仓储 ↔ Worker ↔ REST ↔ 指标 ↔ TS ↔ UI` 十层中**没有任何一层是另一层的生成物或校验对象**：`api_idempotency.status` 的 DB CHECK 允许 `IN_PROGRESS/FAILED` 而代码从不写（缺陷面 A）、契约 `required: true`/`minLength: 8` 而 Spring 注解 `required = false` 且值对象不校验（缺陷面 D）、`types.ts` 从实现抄写而非从契约生成（缺陷面 D/E）、`x-error-codes` 缺 `RETRY_EXHAUSTED` 而规范声明它（缺陷面 K）—— 四层各自"内部自洽"却两两不一致，且**没有任何一致性守卫测试**。本域三个特有放大因子：① **幂等被当成"缓存"而非"占位锁"**：整个实现只关心"结果可否复用"，从不关心"执行权可否独占"，于是 `IN_PROGRESS`、lease、并发独占三个要求同时缺失（缺陷面 A），而 `IdempotencyServiceTest` L104-116 又把这一缺失的副产品（指纹为 null 即回放）写成期望，形成"测试固化缺陷"的闭环；② **恢复语义有两个作者**：`JobWorker`（应用内、有审计/通知/attempt 副作用集）与 `JobLeaseReconciler`（对账器、无副作用集）各自实现了"租约过期后怎么办"，二者不共享判定也不互斥（缺陷面 I），而 `REQ-JOB-001` 从未说过可以有两套；③ **"周期"用启动钩子冒充**：`RecurringJobBootstrapper` 以 `ApplicationRunner` 语义 + 最旧 1000 行窗口 + `isActive` 去重三件事拼出"看起来会周期跑"的效果，实际是一次性投递且绕过统一入队口（缺陷面 J），类注释再用一个不存在的 `SCHEDULED` 状态把设计讲圆。`implemented-unverified` 在本域同样被当成"差不多可用"，掩盖了「`AC-P0B-IDM-003` 不可实现」「路径二 DEAD 无审计」这两个应为 `NOT_IMPLEMENTED` 的分级。

**限定更正**：① 对本域任何「幂等已实现」的表述，统一限定为「**存储与复用读取已实现，执行权独占与状态回放未实现**」，`REQ-IDM-001` 的 L164/L167/L168 三项与 L174（`IdempotencyStatus` State，类根本不存在）应标 `NOT_IMPLEMENTED`，L162/L166（作用域键、同键同摘要复用）标 `IMPLEMENTED_UNVERIFIED`；② 对本域任何「Job 框架已实现待验证」的表述，限定为「**领取/租约/退避/attempt 表已实现，Catalog、claim-conflict 指标、lease-expiry 指标、RETRY_EXHAUSTED、Span Link、周期调度六项不存在**」（不是"待验证"，是"没有"）；③ 缺陷面 H 的三段证据独立成立、其**组合后果**为推论，不得写成"已复现"；④ 缺陷面 K 的部署双进程结论只证明"配置面未区分"，不证明"实际发生重复执行"；⑤ Sample Handler 一项按缺陷面 G 的精确表述引用，不得升级成 `INV-JOB-009` 违反；⑥ `JobAuditAdapter`/`JobNotificationAdapter` 的审计 result 恒成功与 recipient 悬挂属 ㉘ 同族，**合并计权、不重复计入差集数量**；⑦ 本项不修改任何禁改面文件，仅登记冲突待验收方处置。

**整改待批（AI 不自决）**：① 为 `api_idempotency` 引入真正的占位写路径：新增 `V45+` 迁移（`lease_owner`/`lease_until`/`state` 语义列 + 恢复索引），把 `save` 拆为「执行前原子 `INSERT … status='IN_PROGRESS'` 冲突即等待/拒绝」与「完成后更新 `response_status`/`response_body`」两步，并按 `REQ-IDM-001` L169 为每个操作显式声明 FAILED 缓存语义（不得默认缓存失败响应）；② 统一全部 24+ 个 `execute` 调用点的指纹策略：无请求体的 DELETE/`:action` 端点必须计算**规范化请求指纹**（method + path + query + 影响主体的最小语义集），禁止传 `null`；同时把 `IdempotencyService.execute` 的"任一侧 null 即回放"改为显式三态（无指纹 / 指纹相同 / 指纹冲突），并**同步修订** `IdempotencyServiceTest` L104-116 与 L119-130 两个把缺陷固化为期望的用例（此改动属测试面，须与新 Task Card 同批）；③ 让所有调用方消费 `IdempotencyResult`：`:retry`/`:cancel` 返回真实当前状态（禁止 `JobController` L77/L95 在 `execute` 外拼造常量 `"PENDING"`/`"CANCELLED"` 响应并丢弃 supplier 返回值），统一经 `IdempotencySupport` 并**删除** `AssetController` L250-265 与 `VersionController` L151 的私有复制；决定是否以 `X-Idempotency-Replayed` 响应头对外可观测（若决定加，须先入契约再实现）；④ 契约↔实现字段名统一：要么把 `IdempotencyRecord.required` 的 `digest` 改为 `requestDigest` 并补 `responseStatus`/`expiresAt`（**破坏性变更须 ADR**），要么后端改为输出 `digest`；`limit` 补 `minimum` 与非法值语义；`Idempotency-Key` 参数 `required`/`minLength`/`maxLength` 三约束必须落到后端（`buildKey` 校验 + 400 映射），前端 `newIdempotencyKey` 的兜底路径须保证长度 ≥8；⑤ `/system/idempotency` 查询加 principal/组织范围谓词（缺陷面 E），或在契约与页面上明确它是**管理员全局视图**并据此收紧到更高的权限点与审计（两条路径互斥，须人类裁决）；⑥ 建受控 Task Type Catalog：新增枚举/常量类或 `V45+` 的 `job_task_type` 注册表（type、payload schema 版本、max_attempts、timeout、concurrency 策略），`JobHandlerRegistry` 改为**重复 type 启动失败**，并为 `V9` L48 增 `V45+` CHECK 或引用约束；`JobHandlerRegistry` L17-18 与 `RecurringJobBootstrapper` L24-25 两处类注释随之重写；⑦ 给 `SampleJobHandler` 加 `@Profile("dev")` 或 `@ConditionalOnProperty`，并在启动期输出/校验"本容器承担的正式业务类型清单"，使 `REQ-JOB-001` L202 与 `INV-JOB-009` 可被机器验收；⑧ 修 attempt 单调性：`resetToPending` 显式决策"清零 attempts"或"引入 generation 列"（后者更保守，须 `V45+` 增列并同步 `job_attempt` 唯一键为 `(job_id, generation, attempt_no)`），`recordAttempt` 改为 upsert 或按 generation 编号（禁止静默 return），`isTerminalFailure` 相应改判；同时把 `markSucceeded/markRetryWait/markDead` 接 `row_version` 乐观锁（模型已有字段）；`JobApplicationService` L78-83 那条"设计后果非缺陷"的注释必须在整改后据实改写；⑨ 合并两套恢复路径：`JobLeaseReconciler.checkJobLease` 要么删除、要么改为**只检测不改写**（产出 `MANUAL_REVIEW` 事件），DEAD 判定与副作用集（attempt / 审计 / 通知 / 计数器）单一归属 `JobWorker`，L98 `Thread.sleep(100)` 与 L121-122 N+1 一并消除，类注释 L16-19 的三项检查据实修订；⑩ 周期调度改为显式机制（持久化 job 自重投或 `@Scheduled` + 幂等入队），删除"最旧 1000 行窗口"式判定（改按 type 精确查询），一律经 `enqueue` 投递并携带 traceId，`maxAttempts`/间隔从 Catalog 或 `V8` 配置键读取而非硬编码 L46-54；⑪ 可观测性补齐：新增 `job.claim.conflict`（`SKIP LOCKED` 空转/竞争计数）与 `job.lease.expired` 两个指标、`RETRY_EXHAUSTED` 错误码产生点并回补到契约两个 `x-error-codes`、Worker 侧 Span Link（`INV-JOB-008` 后半句）、`JobAuditAdapter` 的 `result` 按事件语义分级；⑫ UI：`JobsPage` 补 Trace 列、按定稿状态集实现门控（**先由人类裁决 `MIPS-ADM-013` L154 的 `FAILED` → 六态映射并出规范修订**），`IdempotencyPage` 的 `rowKey` 改为作用域复合键并补服务端游标分页，`api.ts` L284-290 改为对同一逻辑操作复用键（失败自动重试不换键）；⑬ `deploy/compose` 显式为 backend 与 worker 两服务分别声明 `AIHUB_JOB_WORKER_ENABLED`（backend 关、worker 开），并把 `aihub.job.*` 全量键纳入配置清单与 `JobConfiguration` L53-71 gauge 的对应文档；⑭ `V9` L11/L30 的「全局唯一」失实注释以 `V45+` 的 `COMMENT ON` 修正，L71 的「幂等 JobHandler」口径随之改为可证表述（均不得改 `V9`），并向验收方申请修正 `EVD-P0BR-023-001.yaml` L141 的 `V27` → `V41` 引用（evidence 属禁改面，本项不动）；⑮ 建立**十层一致性守卫测试**（与 ⑲—㉘ 的守卫合并为同一套门禁），至少断言：DB CHECK 允许值 ⊆ Java enum ⊆ 契约 enum ⊆ TS 联合；契约每个 `required` 字段在后端视图 record 中存在且非 null 率 100%；每个 `JobHandler.type()` 出现在 Catalog 且无重复；`REQ-*` 声明的每个错误码存在产生点、每个指标存在注册点；已应用迁移文件名与 evidence 引用一致；⑯ `AC-P0B-IDM-001..003`、`AC-P0B-JOB-001..005` 的 E4 取证方案须人类先给出，本项只提供静态前提，至少须含四个场景：双实例并发同键写入、Worker 领取后 SIGKILL 的双路径竞争、人工重试 DEAD 任务后的 `job_attempt` 序列、backend+worker 双进程同时轮询的重复执行观测；⑰ 上述任一实现整改前需人类先出 `READY` + `implementationAuthorized: true` 的 Task Card（DEC-009），AI 不得自批。

→ 本项与 ①—㉙ 一并不在本轮 AI 自决实施范围内。

### ㉚ 认证 / 令牌生命周期面差集（REQ ↔ 不变量 ↔ 状态机 ↔ 契约 ↔ 迁移 ↔ 实现 ↔ 验收目录）

**取证范围与方法**：本项覆盖此前无任何专项章节的「认证与令牌生命周期」域（`^#{2,3} ` 标题全量枚举中不存在 IAM / 令牌 / 会话专项）。规范侧锚点：`docs/ai-spec/01-requirements/p0b-identity-authorization.md` 的 `REQ-IAM-001`(L8) / `002`(L42) / `003`(L90) / `004`(L134) / `005`(L170) / `006`(L202) 与 `REQ-AGT-001`(L236)（注意：认证域规范写在该文件而非 `p0b-platform-services.md`）；`docs/ai-spec/02-domain/entity-invariants.md` 的 `INV-IAM-001..009`、`INV-AGT-001..007`、`INV-TKN-001..008`（承重条目：INV-IAM-003 L36、004 L37、005 L38、008 L41；INV-TKN-001 L56、003 L58、004 L59、005 L60、008 L63）；`docs/ai-spec/02-domain/state-machines.md` §2（L16 起，用户状态机）、§3（L39 起，Principal 注册/启停，L47 的一致性硬要求）、§4（L49 起，刷新令牌 Family）；`docs/ai-spec/05-acceptance/p0b-exit-catalog.md` L37-48（`AC-P0B-IAM-001..012`，全部 MUST、`minimumEvidenceLevel: E4`，仅 010 为 E3/E4）与 L146「V05 只覆盖 `AC-P0B-IAM-002` 的一小部分」。契约侧锚点为**仓库根** `contracts/openapi/aihub-v1.yaml`（非 `docs/` 下）：`/auth/login`、`/auth/refresh` L78-101、`/auth/token` L103-127、`/auth/agent/token`、`/auth/logout`、`/me` L178-195、`/me/password` L197-217。迁移侧：`V3__identity.sql`、`V33__iam_token_jti_digest.sql`、`V34__iam_token_pat.sql`。实现侧逐行读取 `identity/{api,application,domain,infrastructure}/**`、`platform/security/{JwtTokenService,JwtProperties}.java`、`authorization/application/{AuthorizationService,EffectiveScopeResolver}.java`、`bootstrap/{PrincipalContextFilter,SharedKernelConfiguration,security/SecurityConfiguration,security/JwtAuthenticationFilter}.java`（**第四轮更正**：`SharedKernelConfiguration` 实为 `bootstrap\SharedKernelConfiguration.java`，**不存在 `config\` 子包**）、`platform/observability/application/PlatformMetrics.java`（**第四轮补记实际路径**）、`shared/security/PasswordPolicy.java`、`shared/error/ErrorCode.java`、`shared/identity/PrincipalType.java`，以及 `backend/src/main/resources/application.yml` L92-100。方法承 ㉘㉙：内容 Grep 定锚 → 逐文件 Read 全文或精确行段 → 跨层逐点比对 → 全仓调用点穷尽普查。**自证强度须据实描述（本项原有「所有引用行号在落盘前做第二次实盘复核」一句属夸大，已作废）**：实际执行的是四轮 —— 首轮成文前复核（撤除 1 项误判、降级 1 项，见登记）、落盘后第二轮实现侧逐行磁盘复核（推翻 C/M/R 三条论断、修正 20 余处实现侧行号、新增 G′ 与 O 死代码两条立论，但**该轮自身又引入 1 处错改**）、以及本段第三轮**规范侧锚点逐字回读**（`p0b-identity-authorization.md` L63/L64/L66/L68/L76-L82、`state-machines.md` L30/L32/L47/L59、`entity-invariants.md` L37/L41/L42 全部命中原文）、以及本段第四轮**全量行号对撞**（`Grep -n` 与 PowerShell `(Get-Content -LiteralPath $p)[$i-1]` 双确认，二者一致者为行号唯一权威）。第四轮的方法论结论必须前置登记：**`Read` 的 `offset` 行号在同一文件内偏移不规则，不得作为行号来源**——`Read(LocalUser, offset=66)` 把 `isLocked` 显示在 L69（磁盘实为 L70），`Read(MyBatisLocalUserRepository, offset=42)` 同样偏 -1，而 `Read(JwtAuthenticationFilter, offset=100)`、`Read(IdentityTokenRevocationChecker, offset=14)`、`Read(MyBatisRefreshTokenRepository, offset=30)` 与 Grep 完全一致（偏移 0）。第三轮系用 `Read(offset)` 取证规范文件，故 `REQ-IAM-003` 的四处锚点失实（现校正为 **L106 / L110 / L112 / L115-117**，其中 L110 即第三轮自认「已校正」的清理条款，其把 L110「更正」为 L109 的结论本身失实，一并回填）。**第二轮自述的「实现侧行号全部命中」同样失实**：本轮在实现侧另发现 14 处漂移（清单见「取证偏差登记」第四轮条目），并更正 1 处**符号归属错误**（`resolveKeyId` 属 `JwtTokenService` 私有静态方法，不在 `JwtProperties`）。**本节行号区间自本轮回填起统一采用「签名行 – 方法结束 `}`」口径，注解行（`@Override`/`@Transactional`）另注，不得再混用两种口径。**第三轮的结论之一是：**第二轮对规范行号的「更正」方向是错的**（把 `REQ-IAM-002` 行为条目整体上移一行、把 `state-machines` §3/L47/§4 下移一行），现已全部按磁盘原文回填，原方法段所列 §2（L16 起）/§3（L39 起，L47 一致性硬要求）/§4（L49 起）与 `INV-*` 锚点经逐字验证**本就正确**。被推翻的推断全部进入下方「取证偏差登记」而非静默删除。

**合规面（本域确有成立的部分，登记以免被差集叙述抹平）**：
- 刷新 Cookie 属性面成立：`RefreshCookieFactory.build` L33-42 硬编码 `httpOnly(true)`、`secure(properties.secure())`、`sameSite(properties.sameSite())`、`path(properties.path())`、`maxAge(maxAgeSeconds)`；`clear` L49-58 同属性 + `maxAge(0)`；`RefreshCookieProperties` L25-28 默认 `aihub_refresh` / `secure=true` / `SameSite=Strict` / `path=/api/v1/auth` → `AC-P0B-IAM-006` 的「新 Cookie 属性正确」在**静态面**满足（浏览器实际行为**未实测**）。
- 令牌 TTL 类型化且与不变量一致：`JwtProperties` L41-43 默认 access 15min / refresh 7d / PAT 90d，`application.yml` L98-100 同名键显式声明 → `INV-TKN-001` 满足。
- 失败计数走数据库原子自增：`MyBatisLocalUserRepository.recordLoginFailureAtomically`（**L73-91**，注解 L72）用 `setSql("failed_login_attempts = failed_login_attempts + 1")`（**L81**）+ 同一语句内 `CASE WHEN` 条件推进 `status`（**L82-83**）与 `locked_until`（**L84-85**）+ `row_version` 自增（**L87**），并在 **L74-76** 注释中自证「不用先读后写以满足 `REQ-IAM-002` 原子计数」，最后 **L90** `findByUserId` 回读权威结果 → `REQ-IAM-002` **L64**（「每次失败原子增加计数，达到配置阈值进入 LOCKED」）的失败路径原子性**成立**。
- 口令策略与契约同源，不存在两套真值：`PasswordPolicy.defaults()` L43-45 = `(12, 128, 3)`，`SharedKernelConfiguration` L86-87 提供该 bean，与契约 `LoginRequest`/`ChangePasswordRequest` 的 `minLength: 12` 一致；`validate` L53-65 返回 i18n 键（tooShort/tooLong/insufficientComplexity）→ `REQ-IAM-006` 的策略面成立（但**无 HTTP 层注解支撑**，见缺陷面 N）。
- 主体类型解析 fail-CLOSED：`JwtTokenService.parsePrincipalType` L158-167 对 null 与未知值一律 `throw AUTH_UNAUTHENTICATED` → 符合 AGENTS.md「认证授权失败即关闭」（同文件的 `parseTokenType` 相反，见缺陷面 B）。
- 外层过滤器统一清理执行上下文：`PrincipalContextFilter` L38 `@Order(Ordered.HIGHEST_PRECEDENCE)`，L65-76 先置匿名 `PrincipalContext`，L78-79 写 `REQUEST_ID`/`TRACE_ID` 到 MDC，**L84-89 `try { doFilter } finally { PrincipalContextHolder.clear(); MDC.clear(); }`** → `REQ-IAM-003` **L110**（第四轮回填：原文第 6 步「请求完成后清理 ThreadLocal/MDC…」在 **L110**；第三轮曾据 `Read(offset)` 把本行「校正」为 L109，该校正失实，现按 `Grep -n` 回填。防线程复用串主体）的清理要求由外层闭合（本项首轮曾据此误判缺陷，已在偏差登记中撤销）。
- 迁移链与 PAT 语义自洽：`V34` L5 先 `DROP CONSTRAINT IF EXISTS ck_iam_token_type`、L7-8 重建为 `('ACCESS','REFRESH','PAT')`、L10 增 `name`、L14-16 建部分索引 `(principal_id, token_type) WHERE token_type='PAT'`；`IdentityTokenRevocationChecker` L25 注释「PAT 存于 `iam_token.token_type=PAT`」与 `V34` L8 一致 → 首轮对 PAT 存储位置的疑点**解除**，非缺陷。
- 全链无状态且匿名面最小：`SecurityConfiguration` L70 禁用 CSRF、L72-73 `STATELESS`、L75-78 仅放行 `POST /auth/login`、`/auth/token`、`/auth/agent/token`、`/auth/refresh` 与 `GET /actuator/health/**`、`POST /webhooks/gitea`，L79-81 + L95-100 把 swagger 限定在非 compose/prod/production，L82 `anyRequest().authenticated()`，L84-86 挂 `entryPoint`/`accessDeniedHandler`，L87 `addFilterBefore(UsernamePasswordAuthenticationFilter)` → 符合 AGENTS.md 的匿名端点白名单口径。
- 授权判定不依赖请求携带的角色：`AuthorizationService.isPermitted` L119 `context.scopes().contains(code)`、L123 `roleBindingRepository.resolvePermissionCodes(principalId)` **实时查库**；`computeAccessScope` **L205-231**（第四轮二次回填：签名在 **L205**、`@Transactional(readOnly = true)` 在 L204、方法结束 `}` 在 L231；该条曾于第四轮被「更正」为「签名在 L210」，实为把 L210 的**方法体首语句** `String principalId = context.principalId();` 误当签名 —— 错误形态登记于偏差登记）的组织/项目/团队/ACL 集合同样来自仓储而非 `PrincipalContext` 字段 → 使缺陷面 E 的严重度**显著低于**首轮判断（见「限定更正」①）。
- 吊销与审计**显式脱离外层异常回滚**（落盘后第二轮磁盘复核补入的正向事实）：`AuthenticationApplicationService` L213 `rotationTransaction.execute(...)` 与 L249 `failureTransaction.executeWithoutResult(...)` 是两个独立 `TransactionTemplate`，L242-247 的 javadoc 自证其目的正是「重放处置（整族吊销 + DENIED 审计）不能被随后的 `AUTH_REFRESH_REPLAYED` 异常回滚掉」→ `REQ-IAM-004` L159 所要求的重放审计在**事务边界层面**是可靠的。该正确范式与同文件缺陷面 J（`readOnly = true` 内写审计）构成**同文件正反对照**，说明缺陷面 J 是局部疏漏而非全域设计。
- `iam_user` 的乐观锁真实生效：`MyBatisLocalUserRepository.update` L51 在 `lambdaUpdate` 谓词中带 `eq(UserEntity::getRowVersion, currentVersion)`、L64 显式 `set(rowVersion, currentVersion + 1)` → 同一模块内「用户」侧的 `row_version` 是**被校验并被推进**的，与 `iam_token` 侧（缺陷面 Q 的 `claimForRotation` 不递增、`RefreshTokenRecord` 无 `rowVersion`）形成**同域不一致**，据此可确认令牌侧是遗漏而非全局约定。
- 登录失败防账号枚举：`AuthenticationApplicationService.login` L122-126 对「用户不存在」分支同样调用 `recordDeniedInOwnTransaction` 后抛 `AUTH_INVALID_CREDENTIALS`（与 L143 的「口令不符」同码、同审计形态）→ `REQ-IAM-002` **L63**「用户名不存在与密码错误返回相同错误码」**成立**。
- 强制改密的豁免端点集与规范一致：`PasswordChangeRequiredInterceptor.isExempt` L51-56 放行 `startsWith("/api/v1/auth/")` ∪ `equals("/api/v1/me")` ∪ `equals("/api/v1/me/password")`，与 `REQ-IAM-002` **L66**（「PENDING_ACTIVATION/forcePasswordChange 用户仅能访问 `/me`、改密、刷新策略允许端点和登出」）/ `INV-IAM-008` L41 允许的「登录、刷新、登出、/me、改密」**逐字对齐** → 缺陷面 M 仅剩「双真值无约束」一项，行为面（哪些请求被拦）不成立为缺陷。

**缺陷面 A：改密后未为当前会话签发新 Family —— 规范与契约双侧落空。**
- 规范侧要求「保留当前会话」两处：`REQ-IAM-002` **L68**（「成功改密递增 tokenVersion、吊销该用户全部旧 Token Family，**并为当前会话签发新 Token Family（当前会话不中断）**，用户进入 ACTIVE」）与 `REQ-IAM-005` **L185**（「用户自行改密吊销该用户**其他会话**的 Token Family，当前会话轮换 refresh Token 后保留，不强制重新登录」）；`state-machines.md` **L30** 的 `PENDING_ACTIVATION | change password | ACTIVE | tokenVersion 递增；临时会话轮换；USER_PASSWORD_CHANGED` 同样要求**轮换**而非清除。
- 契约侧同样要求：`/me/password` L200 summary 写「…并吊销**其他** Token Family」。
- 实现侧只做了一半：`AuthenticationApplicationService.changePassword`（`@Transactional` **L335**、方法 **L336-352**）在 **L350** 调 `refreshTokenRepository.revokeAllByPrincipal(principalId)`，随后 **L351** 写 `USER_PASSWORD_CHANGED` 审计。**必须据实定级**：「吊销该用户全部旧 Family」这一半本身**是合规要求**（`REQ-IAM-002` L68 明文如此，且 L349 注释「改密后吊销其全部刷新令牌（access 由 tokenVersion 递增自动失效）」与之呼应），真缺陷在**另一半没有做** —— 该仓储方法（`MyBatisRefreshTokenRepository.revokeAllByPrincipal` **L86-92**，第四轮回填：原写 L85-91 整体偏 -1，注解在 L85；`in(status, ACTIVE, ROTATED)` 在 L89、`set REVOKED` 在 L90）对全部命中行改写 `REVOKED`，**不排除也不重建当前 Family**，方法体内也不产生任何新令牌；控制器侧 `AuthController` L127-135 返回 `ApiResponse<Void>`，既不下发新令牌对也不重写 Cookie，`withRefreshCookie` L139-141 又因 `result.refreshToken()==null` 而静默跳过 → 用户改密后**当前会话的 access 到期即无法续期**，规范承诺的「当前会话不中断」在实现与契约响应形状两侧同时缺失。
- 这是本域唯一一条「规范 + 契约 + 实现」三方文本齐全且实现直接对立的事实性缺陷（非解读分歧）。`AC-P0B-IAM-005`/`007` 的 E4 场景在此实现下不可能通过。

**缺陷面 B：`kid` 不参与密钥选择 + 未知 `token_type` 默认按 ACCESS 放行。**
- `JwtTokenService.parseClaims` L187-196 的容错策略是「先用当前 `publicKey` 验，失败再用 `previousPublicKey` 验」，**完全不读 JWS header 的 `kid`**，也不存在「未知 `kid` 即拒绝」的分支；`resolveKeyId`（**第四轮更正符号归属**：该方法是 `JwtTokenService` 的私有静态方法 `private static String resolveKeyId(JwtProperties, PublicKey)`，位于 **L259-270**（第四轮二次回填：原文「位于 L259-262…收尾于 L270」把分支区间与方法区间混写，自相矛盾；权威口径为签名 L259、`keyId` 非空直取分支 **L260-262**、公钥 SHA-256 前 8 字节派生 **L265-266**、`NoSuchAlgorithmException` 兜底 `return "default"` **L268**、方法结束 `}` L270）；原写「`JwtProperties.resolveKeyId`」把归属类写错了）仅在签发侧决定 `kid` 值，验证侧不使用。
- `parseTokenType` L169-178 对 `null` **返回 `TokenType.ACCESS`**、对未知字符串同样返回 ACCESS；而 `JwtAuthenticationFilter` L84-89 的 `claims.tokenType()!=ACCESS → reject` 因此**永远不会拦住一个缺失 `tokenType` claim 的 REFRESH/PAT 令牌**（只要签名由本域密钥发出）。
- 与同文件 L158-167 的 `parsePrincipalType` fail-closed 形成**同一类内的相反取向**，说明是疏漏而非设计。
- 违反点：`REQ-IAM-003` **L106**（第四轮回填：原文第 2 步「按 `kid` 查找允许的公钥并验证算法、签名、iss、aud、iat、exp；」在 **L106**，原写 L105 偏 -1，系第三轮 `Read(offset=100)` 整体 -1 偏移所致）、**L112**（第四轮回填：「未知 kid、错误算法…一律 fail closed」在 **L112**，原写 L111 偏 -1）、`AC-P0B-IAM-010`（双公钥轮换，且该条已把 `minimumEvidenceLevel` 降到 E3/E4 仍无静态支撑）。双公钥窗口内的实际验证行为**未实测**。

**缺陷面 C（落盘后据实缩小）：ROTATED 与 REVOKED 共用 `AUTH_REFRESH_REPLAYED`，被吊销令牌的重用被误报为重放。**
- **首轮文本失实并已作废**：本项原写「L235-238 对『查不到 / 状态非 ACTIVE』统一抛 `AUTH_REFRESH_REPLAYED`」。逐行回读 `AuthenticationApplicationService.refresh`（L197-240）后推翻：`findByJwtId(...).orElseThrow` 在 **L206-208** 抛 `AUTH_UNAUTHENTICATED`（「unknown refresh token」）、`tokenType != REFRESH` 在 L202-204 抛 `AUTH_UNAUTHENTICATED`、主体禁用 **L220-222** 抛**独立的** `AUTH_ACCOUNT_DISABLED`、`claims.tokenVersion() < owner.tokenVersion()` **L224-227** 抛 `AUTH_UNAUTHENTICATED`（「superseded」）。即错误码分流**大体符合** `REQ-IAM-004` L153/L160 的要求。
- 残余真缺陷只有一条：`claimForRotation`（`MyBatisRefreshTokenRepository` **L56-66**，注解 L55；第四轮回填：原写 L55-65 整体偏 -1）以 **L61** 的 `eq(status, ACTIVE)` 为唯一 CAS 谓词，因此 **ROTATED 重用与 REVOKED 重用同样**得到 `affected = 0` → L215 `return null` → L235-238 统一抛 `AUTH_REFRESH_REPLAYED`，**并连带在 L248-256 对一条本已 REVOKED 的 Family 二次执行 `revokeFamily` + 再写一次 `AUTH_TOKEN_REPLAY_REJECTED`/DENIED 审计**（L251 `LOG.warn`、L253-254 audit）。于是「管理员/改密吊销后令牌被重用」这一**应当是不同告警语义**的事件，对外表现为「检测到重放攻击」。
- 定级依据（不得自行拔高）：`state-machines.md` **L59** 明文「EXPIRED/REVOKED 重用统一 fail closed，**但重放告警语义需区分**」→ 本条成立为**告警与审计语义缺陷**，而非「未 fail closed」的安全缺陷；`REQ-IAM-004` L153 只要求 REVOKED「拒绝」，未强制分码，故**规范文本面不构成硬违反**，违反点是 L59 的告警区分要求与 L159 的 `AUTH_TOKEN_REPLAY_REJECTED` 事件语义。另：因 `RefreshTokenStatus.EXPIRED` 无写入点（缺陷面 D），`AUTH_TOKEN_EXPIRED`（L160 允许的另一码）在本域**无任何产生路径**。REVOKED 令牌重用的对外实际表现与审计条数**未实测**。

**缺陷面 D：`RefreshTokenStatus.EXPIRED` 本域生产代码零写入点 —— `iam_token.expires_at` 与状态列退化为装饰列。**
- `V3__identity.sql` L135 的 `ck_iam_token_status` 允许 `('ACTIVE','ROTATED','REVOKED','EXPIRED')`，L128 `expires_at NOT NULL`；`RefreshTokenStatus`（第四轮回填精确口径：枚举声明 L11，四个常量在 L14/L17/L20/L23，PowerShell 双确认）确有四值。
- 穷尽普查：在 `backend/src/main/java/com/aihub` 下 Grep `EXPIRED`，本域**无任何 `RefreshTokenStatus.EXPIRED` 赋值点**（他域命中为 `JOB_LEASE_EXPIRED`、`upload_session` 过期、`AUTH_TOKEN_EXPIRED` 错误码 —— 已逐条核对分流，作用域限定见「限定更正」③）。`MyBatisRefreshTokenRepository` 中**改写 `status` 列的语句只有三条**：`claimForRotation` L62（→`ROTATED`）、`revokeFamily` L81（→`REVOKED`）、`revokeAllByPrincipal` L90（→`REVOKED`）；`markRotated`（**L69-74**，注解 L68；**第四轮二次回填**：本条曾据 `Read(offset=67)` 错改为 L68-73，经 PowerShell `(Get-Content)[65..75]` 仲裁后按磁盘原文恢复为 L69-74）只写 `replaced_by_jti`（**L72**）/`updated_at`（L73），不触碰状态。新建行的状态唯一来源是 `toEntity` **L103** `record.status().name()`（域内默认 `ACTIVE`；第四轮回填：原写 L102 偏 -1），**无任何按 `expires_at` 推导过期的逻辑**（**L105** 只是把 `expiresAt` 原样落列；原写 L104 偏 -1）。
- 于是「过期」这件事**只由 JWT 自身 `exp` 在 `JwtTokenService.parseWithKey` L198-207 拦截**，数据库侧既无状态迁移也无清理任务 → `expires_at` 与 `EXPIRED` 值域在运行期不可达，属 DB CHECK 开放了代码从不进入的状态域（与 ㉙ 缺陷面 A 的 `api_idempotency.status` 完全同族形态）。

**缺陷面 E（已降级重述）：`PrincipalContext` 最低字段与契约 `/me` 承诺不符，但不构成授权绕过。**
- `JwtAuthenticationFilter.establishPrincipal` L102-119 构造的 `PrincipalContext` 把 `currentOrganizationId`、`accessibleProjectIds`、`roles`、`maxSensitivity` 一律硬置为空集合/0（L112 `null, List.of(), …`）；`AuthenticationApplicationService.toPrincipalView` L388 同样向视图传 `null, List.of(), …`。
- 判定依据在别处：`AuthorizationService` L123（角色权限实时解析）、L147-148（资源 ACL）、L214-219（组织集合 = `resolveOrganizationScopes` ∪ `OrganizationMembershipPort`）、L220/L222-227/L228-229（项目/团队/ACL 集合）→ **权限与可见范围不依赖这些字段，故不存在越权**（首轮按「空字段→越权」的推断已撤回）。
- 残余真缺陷两条：① `REQ-IAM-003` **L115-118**（第四轮回填：小节标题「### PrincipalContext 最低字段」在 **L115**，字段清单在 **L117-118**（跨两行折行），原写 L114-117 系把 **L116 空行**计入且把标题行漏计，方向相反）把「当前组织、可访问项目、角色、最高敏感级」列为 `PrincipalContext` 的**最低字段**，实现以空值占位使这些字段语义失效；② 契约 `/me` L180 summary 明写「获取当前主体、**角色**和粗粒度 Scope」，而 L388 恒传 `List.of()` → 对客户端承诺的字段**结构性恒空**。`EffectiveScopeResolver`（L25 `@Component`，javadoc L16-22：有效 Scope = 静态 scopes ∪ 角色绑定权限编码，在登录/刷新签发时并入 JWT）与 `AuthorizationService` L123 的实时解析构成**双通道**，登记为一致性风险（两通道在角色变更瞬间会给出不同答案），**不得**表述为越权缺陷。

**缺陷面 F：账号锁定阈值与时长硬编码，无配置项。**
- `LocalUser` L22 `MAX_FAILED_ATTEMPTS = 5`、L25 `LOCK_DURATION_MINUTES = 15`；`MyBatisLocalUserRepository.recordLoginFailureAtomically` **L77/L78**（第四轮回填：L77 `int threshold = LocalUser.MAX_FAILED_ATTEMPTS`、L78 `lockUntil = now.plusSeconds(LocalUser.LOCK_DURATION_MINUTES * 60L)`；原写 L76/L77 偏 -1，L74-76 是注释行）**直接引用这两个常量**（而非注入配置），故整改无法只改配置文件。
- `application.yml` 及 `backend/src/main/resources` 全目录 Grep 无 `lockout`、无任何阈值/时长键（亦无 `argon2` 键，见缺陷面 N 派生）→ 违反 `REQ-IAM-002` **L64**「每次失败原子增加计数，达到**配置**阈值进入 LOCKED」与 `AC-P0B-IAM-005` 的可配置断言面。

**缺陷面 G：登录成功的计数复位走读-改-写，与失败路径不对称。**
- `AuthenticationApplicationService.login` **L146** `user.recordLoginSuccess(clock.instant())` → **L147** `userRepository.update(user)`，落地为 `MyBatisLocalUserRepository.update` **L47-70**（第四轮回填：签名在 L47，注解 `@Override`/`@Transactional` 在 L45-46；原写 L45-70 系注解起算，与本轮统一口径不符）的整行覆写（内存对象 `LocalUser.recordLoginSuccess` L91-99 清零后回写；第四轮 Grep+PowerShell 双确认）；与 `recordLoginFailureAtomically` **L79-87**（第四轮回填：单语句 `setSql` 原子块起于 L79 的 `userMapper.update(...)`、止于 L87 的 `row_version` 自增；原写 L78-86 偏 -1）的单语句原子自增形成不对称（同一张 `iam_user` 的两条写路径使用两种并发协议）。差异可被 `update` **L51** 的 `row_version` 谓词部分吸收（`affected = 0` 时静默返回 `false`，见缺陷面 G′）。并发登录下是否发生计数覆盖**未实测**（属静态结构推论）。
- **G′（同族新立论）**：`update` 返回 `boolean` 表示 CAS 胜负，但 `login` **L147** 的调用点**未消费返回值** —— 乐观锁失败时既不重试也不报错，成功登录的 `last_login_at`/计数清零会被静默丢弃。

**缺陷面 H：CAS 谓词使用明文 `jti` 列，与 `V33` 的立项目标相反。**
- `V33__iam_token_jti_digest.sql` L2 注释自述「降低明文 jti 暴露面」，L8 增 `jti_digest`、L13-15 回填、L17 建部分索引。
- 实现却把最关键的状态迁移留在明文列上：`MyBatisRefreshTokenRepository.claimForRotation` **L60** `eq(getJti, jti)` 作为 CAS 谓词（方法整体 **L56-66**，注解 L55；第四轮回填：原写 L55-66 系注解起算），`findByJti` L38-42 与 `markRotated` **L72**（第四轮回填：原写 L71 偏 -1）、`revokeFamily`/`revokeAllByPrincipal` 的 `principal_id`/`token_family` 谓词亦以明文键为主；明文列 `jti` 仍保留 `V3` L121 的 UNIQUE 约束。→ `INV-TKN-004`（摘要化）只做到「加了一列」，未做到「用这一列」。

**缺陷面 I：SERVICE / API_CLIENT / WORKER 三类主体四向不一致，且无任何签发点。**
- `shared/identity/PrincipalType.java` L13-29 定义五值；`V3` L19 `ck_iam_principal_type` 允许五值；契约 `PrincipalType` 同样五值，且 `/auth/token` L106 摘要宣称支持客户端凭据换取令牌。
- 但 `IdentityTokenRevocationChecker` L60 对这三类**一律 `return true`（判定为已吊销）**，且 Grep `PrincipalType\.(SERVICE|API_CLIENT|WORKER)` 在 `backend/src/main/java/com/aihub` 下 **No matches found** → 平台**不存在**以这三类主体签发或接受令牌的代码路径。L50-54（USER：`isDisabled() || claims.tokenVersion() < user.tokenVersion()`，`orElse(true)`）与 L55-59（AGENT 同构）才是真实生效的两条分支。四者（枚举 / DB CHECK / 契约 / 校验器）互相矛盾，属须上报而非择一的口径分歧。

**缺陷面 J：在 `@Transactional(readOnly = true)` 事务内写审计。**
- `AuthenticationApplicationService.exchangeClientCredential` L261 标注 `readOnly = true`，方法体内有 4 处 `audit(...)`：L265、L271、L276、L287。审计是否真正落库取决于 JDBC 驱动/方言是否把 readOnly 下推为只读事务（**未实测**，Hibernate/HikariCP 常见行为为警告级而非硬失败）→ 存在「凭据交换的失败审计被静默丢弃」的结构风险，`REQ-IAM-001`/`AC-P0B-IAM-001` 的审计完整性以此为前提，须在 E4 场景专项断言。

**缺陷面 K（定性下调为结构风险，非现行缺陷）：吊销检查器经 `ObjectProvider` 取用且无装配守卫。**
- `JwtAuthenticationFilter` L91-96 `TokenRevocationChecker checker = revocationChecker.getIfAvailable(); if (checker != null && checker.isRevoked(claims)) reject` —— bean 缺失时**静默放行**，且没有任何启动期断言阻止这种部署形态。
- 现状不构成为缺陷：`IdentityTokenRevocationChecker` 带 `@Component`，`SecurityConfiguration` L43 以 `ObjectProvider<TokenRevocationChecker>` 注入、L65-66 传给过滤器构造 → 当前装配链必然提供该 bean。定性为「装配期不变量、运行期无守卫」的 fail-open 结构（同一文件内另有缺陷面 B、L 两个 fail-open 点，三者同族）。

**缺陷面 L：签名密钥缺失时自动生成临时 RSA 并继续启动（fail-open）。**
- `JwtTokenService.resolveKeyPair` L216-233：PEM 缺失时 L223-225 仅 `LOG.warn`，L227-229 **现场生成临时 RSA-2048 密钥对继续运行**；`application.yml` L96-97 两个 PEM 键默认为空串，L95 `key-id: ${AIHUB_JWT_KEY_ID:}` 亦默认为空 → 走 `JwtProperties.resolveKeyId` L259-270 的公钥 SHA-256 前 8 字节派生。
- 违反 AGENTS.md「Secrets…不得有默认值/兜底」精神与 REQ-IAM-003 的密钥治理面：生产误配会**静默换密钥**，导致跨实例/重启后全量令牌失效且无告警，而非启动失败。

**缺陷面 M（落盘后据实缩小）：强制改密存在 flag 与状态双真值，且无数据库约束防漂移。**
- **首轮文本过强并已作废**：本项原写「用 flag 而非状态机状态…违反 `INV-IAM-008`」。回读 `REQ-IAM-002` **L66** 后推翻：规范原文把「`PENDING_ACTIVATION`／**forcePasswordChange**」并列为同一限制的两条判据，即**规范自己承认两种表达**；`INV-IAM-008`(L41) 只约束「PENDING_ACTIVATION 用户不能访问业务」，不禁止以 flag 额外表达。且 `LocalUser.resetPassword`（**L130-139**，第四轮二次回填：原写 L129-138 整体偏 -1）在 **L133** 写 `mustChangePassword = true` 的同时于 **L134** 写 `status = PENDING_ACTIVATION` —— 两真值在**同一方法内同点写入**，并非各自漂移。行为面另有合规条目（拦截器豁免集与规范端点集逐字对齐）。
- 残余真缺陷两条：① `must_change_password`（`V3` L46，`SMALLINT NOT NULL DEFAULT 0`）与 `status`（`V3` L50 + `ck_iam_user_status` L54 四值）之间**没有任何 CHECK / 生成列 / 触发器**表达一致性，而 `LocalUser.changePassword`（**L116-125**，第四轮二次回填：原写 L115-124 整体偏 -1）**只清 flag**（**L119** `mustChangePassword = false`；L118 是 `passwordAlgorithm = "BCRYPT"`）、仅在 `status == PENDING_ACTIVATION` 时才推进为 ACTIVE（**L121-123**；L120 是 `tokenVersion += 1`）→ 「flag=true 而 status=ACTIVE」是合法可达且不被约束拒绝的组合；② 拦截器判据（`PasswordChangeRequiredInterceptor` L43 读 flag）与状态机叙述（`state-machines.md` L29/L30 用 status 表达）分属两套语言，验收方若按状态机写 E4 场景会判错。定级为**弱成立**（一致性风险，非现行行为缺陷）。

**缺陷面 N：契约 `x-error-codes` 与实现抛出的错误码三处缺口 + 校验注解缺失。**
- `/auth/token` L110 声明 `[AUTH_UNAUTHENTICATED, AUTH_ACCOUNT_DISABLED, COMMON_INVALID_ARGUMENT]`，而实现 L267-268、L277-278 抛 `AUTH_INVALID_CREDENTIALS` → **未声明**；`/me/password` L203 声明 `[AUTH_UNAUTHENTICATED, PASSWORD_POLICY_VIOLATION, COMMON_INVALID_ARGUMENT]`，而实现 **L340** 抛 `AUTH_INVALID_CREDENTIALS` → **未声明**；全域未声明 `PASSWORD_CHANGE_REQUIRED`（`ErrorCode` L43，HTTP 403）而拦截器（缺陷面 M）正是产生它的地方 —— 该码在规范侧**有明确要求**：`REQ-IAM-002` 错误/审计表 **L78**「未先改密访问业务 → `PASSWORD_CHANGE_REQUIRED` + 拒绝审计」，故缺口属**契约漏项**而非规范未定；`/me/password` L210-217 亦**无 403 响应**声明。
- `ErrorCode` → HTTP 映射实测：L19 `COMMON_INVALID_ARGUMENT`=400、L22 `AUTH_TOKEN_EXPIRED`=401、L25 `AUTH_UNAUTHENTICATED`=401、L31 `AUTH_INVALID_CREDENTIALS`=401、L34 `AUTH_ACCOUNT_LOCKED`=`HttpStatus.LOCKED`=**423**、L37 `AUTH_ACCOUNT_DISABLED`=403、L40 `AUTH_REFRESH_REPLAYED`=401、L43 `PASSWORD_CHANGE_REQUIRED`=403、L46 `PASSWORD_POLICY_VIOLATION`=400。423/403/401 在真实 HTTP 层的落点与错误码定调文档（㉛ 回填：原写 `03-errors`，Glob 实测**无该目录**，`docs/ai-spec` 下 03 号目录实为 `03-use-cases/`，错误码定调真实载体是 `docs/ai-spec/02-domain/error-catalog.md`，见 ㉛ 取证偏差登记）**本轮未完成取证**（见「未实测清单」）。
- 派生 1：契约对 `LoginRequest`/`ChangePasswordRequest` 声明 `minLength: 12`，但 `AuthController.login` L52-60 只做 `isBlank`（L54-55），**无 `@Valid`/`@Size`**；`changePassword` L127-130 同形 → 短口令只在服务层 `PasswordPolicy.validate` 才失败，400 的语义来源与契约声明不一致。
- 派生 2：`/auth/token` L119 描述「凭据有效，签发访问与**轮换刷新** JWT」，实现 L290 `new TokenPairResult(access.token(), null, expiresIn, 0L, principal)` 的 refreshToken 恒为 **null** → 文档与实现不符（并连带使 `withRefreshCookie` L139-141 静默不下发 Cookie，客户端无从察觉）。
- 派生 3：`resources` 全目录 Grep **无 `argon2` 命中**，而 `LocalUser` L54/L118/L132 与 `V3` L87 `credential_algorithm DEFAULT 'BCRYPT'` 全部落在 BCRYPT（第四轮二次回填：三行经 Grep 实测确为 `"BCRYPT"` 字面量所在行 —— L54 构造默认、L118 `changePassword`、L132 `resetPassword`，**无偏移**） → 规范若要求 Argon2id（`REQ-IAM-001` 口令散列口径），该要求既无配置键也无实现，属实现选型与本项未逐条回读的 `02-domain/error-catalog.md`（㉛ 回填：原写 `03-errors`，Glob 实测无该目录）/seed `system_config` 共同决定的**未完成取证**项。

**缺陷面 O（落盘后自降定级）：账号锁定存在两套并行判据，且内存版锁定方法为死代码。**
- `LocalUser.isLocked(now)` **L70-72** 只比较 `lockedUntil != null && lockedUntil.isAfter(now)`，**完全不读 `status`**；而锁定推进同时写 `status=LOCKED`（`MyBatisLocalUserRepository` **L82-83**，第四轮二次回填：原写 L81-82 偏 -1；L81 是 `setSql("failed_login_attempts = … + 1")`）。`changePassword`（**L116-125**）**不清 `failed_login_attempts`、不清 `locked_until`**（第四轮二次回填：原文「只有 `recordLoginSuccess` L91-99 清」**失实** —— `recordLoginSuccess` 在 **L92/L93** 清零，`resetPassword` 同样在 **L135/L136** 清零，`enable` 在 **L155/L156** 亦清零；三处都清，**唯 `changePassword` 不清**，故残余结论成立但表述须按此改写），故存在「`status=ACTIVE` 但 `locked_until` 仍指向未来」或反向漂移的路径空间。到期自动解锁的实际行为**未实测**。
- **自降定级的依据（不得略去）**：`INV-IAM-004`（L37）原文自带「锁定是定时解锁还是仅管理员解锁**待确认**」，`state-machines.md` **L32**（`LOCKED | unlock/expiry | ACTIVE | 解锁方式和审计事件待确认`）亦标「解锁方式和审计事件**待确认**」（同表 L34 的 `DISABLED | enable | 待确认` 同样是未定稿态）→ 规范本身未定稿，本条只能定级为**部分成立**（「双判据无约束」成立，「违反 INV-IAM-004」不成立，因该不变量自身处于待确认态）。
- **新立论（同族，落盘后 Grep 所得）**：`LocalUser.recordLoginFailure(Instant)`（**L104-111**，内存版：自增计数 + 达阈值置 `LOCKED`/`lockedUntil`）在 `backend/src/main/java/com/aihub` 下 **零调用点**（Grep `recordLoginFailure` 仅命中定义本身与 `recordLoginFailureAtomically` 三个不同符号）→ 该方法是**已被原子版取代的死代码**，却仍是 `REQ-IAM-002` **L64** 语义的第二份实现，任何后续改动若落在它身上都不生效（与 ㉙ 的「同一语义两处真值」同族）。

**缺陷面 P：认证域零指标 —— `REQ-IAM-002` 的四类计数无任何注册点。**
- `PlatformMetrics` L30-39 的全部常量为 `aihub_audit_events_total`、`webhook_deliveries`、`critical_events`、`reconciliation_discrepancies`、`upload_sessions_expired`、`mcp_tool_calls`、`upload_sessions_active`、`asset_count_by_status`、`job_processing_duration`、`preview_failures` —— **无一条属认证域**。全仓 `MeterRegistry` 使用点仅出现在 job 域、observability 与 `ManagedExecutorsConfiguration`。→ `REQ-IAM-002` **L82**（本轮复验原文：「指标至少区分成功、无效凭据、锁定、禁用，用户名不得作为高基数 label」；配套错误/审计表 **L76-80** 已把 `LOCKED`→`AUTH_ACCOUNT_LOCKED`、`DISABLED`→`AUTH_ACCOUNT_DISABLED`、未改密→`PASSWORD_CHANGE_REQUIRED` 逐条定死，即规范侧对口径**无歧义**）所要求的四类计数**不存在**（是「没有」，不是「待验证」），`AC-P0B-IAM-002` 与 catalog L146 所指的 V05 覆盖面缺口因此**无法通过补测关闭**。

**缺陷面 Q：仓储注释与实现不符 + 死参数。**
- `MyBatisRefreshTokenRepository` **L125-128** javadoc 称主体类型「由 `principalId` 前缀推断（usr_→USER，其余按 Agent/Service 归为 AGENT）」，**L129-131** 实作**无条件 `return PrincipalType.USER`** 且入参 `principalId` 未被使用（唯一调用点 L116）→ 注释失实 + 死参数；AGENTS.md「注释应解释为什么」与 DoD「文档同步」直接对立。
- 同文件 `toDomain` **L110-123** 内 **L118** `RefreshTokenStatus.valueOf(entity.getStatus())` 对未知 DB 值抛**未捕获**的 `IllegalArgumentException`（`V3` L135 + 未来任何 CHECK 放宽都会变成 500 而非映射错误码）。
- `RefreshTokenRecord` **全类无 `rowVersion` 字段**（Grep `rowVersion` 于该文件 No matches），而 `V3` L133 有 `row_version` 列 → 与 ㉙ 的 `row_version` 不校验同族：乐观锁列存在但无人读；`claimForRotation`（**L59-64** 的 `lambdaUpdate` 链）既**不以 `row_version` 为谓词**、也**不递增**该列，`last_used_at` 还取 **L63** 的 `Instant.now()` 而非注入 `Clock`。对照 `MyBatisLocalUserRepository.update` **L51**（谓词校验）/ **L64**（递增）与 `recordLoginFailureAtomically` **L87**（`setSql("row_version = row_version + 1")`，第四轮二次回填：原写 L86 偏 -1，Grep 实测在 L87）→ **同一 `identity` 模块内两仓储对该列的口径直接不一致**，可确认令牌侧是遗漏而非全局约定。

**缺陷面 R（落盘后据实改写并加重）：principal 状态同步只有二元折叠一条路径，且原子锁定路径完全绕过同步。**
- **首轮文本失实并已作废**：本项原写「用户锁定时 `iam_principal.status` **无处同步**」。回读 `MyBatisLocalUserRepository` 后推翻：**L65-68** 存在同步调用（L65 注释「用户被禁用时同步主体状态」，L67 `syncPrincipalStatus(user.principalId(), user.status() == UserStatus.DISABLED ? "DISABLED" : "ACTIVE")`），实作在 **L154-159**（`principalMapper.update(...)`）。同步机制**存在**，只是不正确。
- 改写后的真缺陷两条（各自独立成立）：
  ① **同步语义是二元折叠**：L67 的三元表达式把除 `DISABLED` 以外的一切状态（含 `LOCKED`、`PENDING_ACTIVATION`）**一律写成 `'ACTIVE'`**。这与 `V3` L20 `ck_iam_principal_status CHECK (status IN ('ACTIVE','DISABLED'))`（**本就不允许 LOCKED**）在值域上自洽，却使 `state-machines.md` **L47**（§3 表后正文，「Principal 的聚合状态必须与 User/Agent 状态一致，不允许扩展表 ACTIVE 而 Principal DISABLED 后仍签发 Token」）在**语义层**无法达成 —— principal 侧根本不存在承载 LOCKED/PENDING_ACTIVATION 的值域，一致性只能靠「扩展表 status」间接推断；契约却声明 `PrincipalStatus: [ACTIVE, LOCKED, DISABLED]`（schema 区，约 L3858，**未复验**）→ 三方冲突仍需人类裁决，冲突点是**值域**而非「有无同步」。
  ② **原子失败锁定路径绕过同步**：`recordLoginFailureAtomically`（**L73-91**，第四轮二次回填：原写 L72-90 整体偏 -1，注解 `@Override` 在 L72）全程走 `setSql` 且**不调用 `syncPrincipalStatus`**（Grep 全类仅 L67 一个调用点），因此把 `iam_user.status` 推到 `LOCKED`（**L82-83**）时 principal 侧不发生任何写入；又因 `PrincipalType` 在 principal 表无 LOCKED 值可用，即便想同步也无列可写。实际后果：账号锁定后 `iam_principal.status` 仍为 `'ACTIVE'`，而 `IdentityTokenRevocationChecker` L50-54 只读 `iam_principal`/`iam_user` 的 `disabled` 与 `token_version`（不读 `iam_user.status == LOCKED`）→ **已签发 access JWT 在锁定后继续有效直至 `exp`**（未实测）。锁定对已签发令牌的实际影响须由 E4 专项断言。
- 原「无 Java 枚举」一点保留但降级：Glob `**/PrincipalStatus.java` 确为 **No file found**，`iam_principal.status` 的合法值只以 L67 的字符串字面量 `"DISABLED"`/`"ACTIVE"` 出现在 Java 侧 → 属**编译期无表达**的弱问题（与 ① 的值域冲突同源），不再单独构成缺陷。

**缺陷面 S：规范 ↔ 契约在「刷新令牌是否接受请求体」上正面矛盾（须上报，不得择一）。**
- 规范侧：`REQ-IAM-004` L148 明确 refresh **只从安全 Cookie** 读取。
- 契约侧：`/auth/refresh` L85 `requestBody required: false`、L86 描述「浏览器使用安全 Cookie；**非浏览器客户端可在请求体提交刷新 JWT**」。
- 实现侧：`AuthController.refresh` L69 先 `readRefreshCookie`，L70-72 退回 `request.refreshToken()`，L75 `TokenPairPayload.from(result, fromBody)` 记录来源 —— 与**契约**一致、与**规范**冲突。
- 该矛盾同时触碰 AGENTS.md「Browser access JWTs stay in memory; refresh JWTs use the shared secure-cookie implementation」，按治理规则本项**只登记冲突**，由验收方裁决修订规范还是收紧契约与实现。

**缺陷面 T：明文 `jti` 回退是「让结果变干净的解析特例」，其成立依据未完成取证。**
- `MyBatisRefreshTokenRepository.findByJwtId` **L45-53**（第四轮二次回填：原写 L44-53 系从注解 `@Override`（L44）起算，按统一口径应取签名行 L45）：先按 `jti_digest = sha256Hex(jwtId)` 查（**L46-48**），命中即返回（L49-51），未命中则**回退** `findByJti(jwtId)` 明文查（**L52**）。
- 该回退使 `V33` 的摘要化在读取面被完全抵消（等价于两列都可用），并给 `findByJwtId` 增加一条语义不同的第二入口（接口 `RefreshTokenRepository` 同时声明明文与摘要两个查询方法，登记为一致性风险；**该接口自身的行号本轮未复验**，故不写行号）。按 AGENTS.md 与本项自设纪律，回退路径的存在以 `V33` L13-15 的**回填覆盖全部历史行**为前提，而该前提**未在真实数据库上验证**（迁移是否在含数据的环境成功执行、有无 NULL 残留均无证据）→ 本项**不把它写成缺陷**，仅登记为「依据未证成的兼容特例」，与 ㉙ 中同类特例同等处理。
- 附证（正向）：新建行时 `toEntity`（签名 **L94**）的 **L98** `entity.setJtiDigest(JtiDigest.sha256Hex(record.jti()))`（第四轮二次回填：原写 L97 偏 -1，**L97 是明文 `entity.setJti(record.jti())`**）与 `findByJwtId` **L46** 使用同一 `JtiDigest.sha256Hex`，摘要算法在读写两侧同源，不存在第二套摘要实现。

**取证偏差登记（不得作为缺陷写入）**：
- **本项首轮成文前的实盘复核推翻四处自我推断（逐条回读磁盘所得，非人工指出）**：① 曾判「`JwtAuthenticationFilter` L122-123 写 MDC 却无 `finally` 清理 → 违反 `REQ-IAM-003` L110」——**不成立**：`PrincipalContextFilter` L38 `@Order(HIGHEST_PRECEDENCE)` 且 L84-89 `finally { PrincipalContextHolder.clear(); MDC.clear(); }` 覆盖全链，该项已从缺陷面撤除并改写入合规面（该条所引的规范行号在第三轮锚点清扫中由 `REQ-IAM-003` L110 校正为 **L109**）；② 曾判「`issueUserTokenPair` L362-382 的 `RefreshTokenRecord.Builder` 未显式设 `status` 是缺陷」——**不成立**：`RefreshTokenRecord` L38 `this.status = builder.status == null ? RefreshTokenStatus.ACTIVE : builder.status` 提供域内默认，仅「无 `rowVersion` 字段」一点成立（并入缺陷面 Q）；③ 曾记「改密吊销发生在 `AuthenticationApplicationService` L350」，第一轮自证时把它「更正」为 L349（并称「L350 是 `audit`」）——**该更正本身失实**：第二轮精读 L330-359 证实 **L350 是 `revokeAllByPrincipal`、L351 是 `audit`、L349 是注释**，即**首轮记录才是对的**。这是「对更正的更正」，必须留痕：**自证过程自身可以引入新错误，且错误会伪装成已修正的成品**（缺陷面 A 现按 L350/L351/L349 书写）；④ 曾判「`PasswordPolicy` 与契约 `minLength: 12` 是两套真值」——**不成立**，L43-45 与契约一致，已移入合规面。
- **一处严重度自降**：首轮把 `PrincipalContext` 空字段判为「授权面重大缺陷」，Read `AuthorizationService` L119/L123/L147-148/L211-229 与 `EffectiveScopeResolver` L16-22 后证实权限与范围**全部实时解析**、空字段不改变任何判定 → 缺陷面 E 已降级为「契约字段面失守 + 上下文最低字段不符」，并明写「不构成授权绕过」，不得在后续叙述中被还原。
- **一处 K 的定性自降**：曾拟写「`ObjectProvider` 取不到 bean 即静默放行 = 现行认证缺陷」，Read `SecurityConfiguration` L43/L65-66 与 `IdentityTokenRevocationChecker` 的 `@Component` 后确认当前装配必然提供该 bean → 改述为「装配期不变量、运行期无守卫的结构风险」。
- **契约路径记账更正**：首轮把契约行号记在 `docs/ai-spec/contracts/openapi/aihub-v1.yaml`，对该路径 Grep 返回 IO error（os error 3）；`Glob **/contracts/**/aihub-v1.yaml` 实测真实路径为**仓库根 `contracts/openapi/aihub-v1.yaml`**（行号本身取自同一文件，内容无误，仅路径前缀写错）。本项已在修正后的路径上复验 L78-127 与 L178-217；**`securitySchemes`（约 L3671）与 schema 区（约 L3762-3901，含 `PrincipalStatus` L3858、`PrincipalType`）的行号仍属修正前的未复验残留**，引用处已显式标注「约」。
- **两处文件名/存在性推断更正**：Glob `SecurityConfig.java` 未命中，实际为 `bootstrap/security/SecurityConfiguration.java`；Glob `PrincipalStatus.java` **No file found** —— 后者不是工具错误而是**证据**，已作为缺陷面 R 立论。
- **第四轮全量对撞 + 二次回填（本节行号错误的总清算，须如实登记）**：① **方法性根因确认**：前三轮部分行号取自 `Read(offset)`，而该工具在同一文件内的**偏移不规则**（同文件不同区段可偏 0/±1/±5），凡以其为唯一来源的行号一律不可信；第四轮起权威口径改为 **`Grep -n` 与 PowerShell `(Get-Content -LiteralPath …)` 逐行索引双确认**，本节全部方法级引用按「签名行–方法结束 `}`」统一重述（注解行另注）。② **回填本身又引入新错误（第三错误形态）**：第四轮曾把 `AuthorizationService.computeAccessScope` 由 L211「更正」为「签名在 L210」——二次回填证实该「更正」仍失实：**L210 是空值守卫之后的方法体首语句** `String principalId = context.principalId();`，真实签名在 **L205**（`@Transactional(readOnly = true)` L204、方法结束 `}` L231）。「把方法体首语句当签名」是与「整体偏移」不同的新形态，证明**纠偏操作的产出同样必须双确认后才能写回**。③ **实质失实一处（超出行号范畴）**：缺陷面 O 原文「只有 `recordLoginSuccess` L91-99 清 `failedLoginAttempts`/`lockedUntil`」被证伪 —— `recordLoginSuccess` 在 **L92/L93** 清零，`resetPassword` 在 **L135/L136** 同样清零，`enable` 在 **L155/L156** 亦清零；**唯 `changePassword` 不清**的残余结论成立，但表述已按此改写。④ **本轮写回的失实行号清单（原值→权威值）**：`resolveKeyId` 文本自相矛盾（「L259-262…收尾于 L270」）→ **L259-270**；`changePassword` L115-124→**L116-125**（内部 L118→**L119**、L120-122→**L121-123**）；`resetPassword` L129-138→**L130-139**（内部 L132→**L133**、L133→**L134**）；`recordLoginFailureAtomically` L72-90→**L73-91**；锁定 status 写入 L81-82→**L82-83**；`row_version` `setSql` L86→**L87**（缺陷面 Q）；`findByJwtId` L44-53（注解起算）→**L45-53**；`toEntity.setJtiDigest` L97→**L98**（L97 为明文 `setJti`，缺陷面 T 附证）；`computeAccessScope` L210-229/L211-229→**L205-231**（合规面、限定更正①；上文「严重度自降」条目所引 L211-229 为该错误的最初源头，保留原文以留痕）；整改①「现 L334」→**L335**（注解）/L336（签名）。⑤ **经复核无需改写的本轮引用**：`LocalUser` L54/L118/L132 三处 `"BCRYPT"`（缺陷面 N 派生 3）、`update` L47-70 与 L51/L64/L65-68、`revokeAllByPrincipal` L86-92（L89 in / L90 set）、`revokeFamily` L77-83、`claimForRotation` L55-66（链 L59-64、`Instant.now()` L63）、`toDomain` L110-123（`valueOf` L118）、失实 javadoc L125-128 / 实作 L129-131（调用点 L116，与缺陷面 Q 措辞统一）、`isPermitted` L119/L123、`isResourcePermitted` L147-148、`revokeFamilyInOwnTransaction` L248-256（javadoc L242-247）、`AuthController.refresh` L65-75（降级链 L69-72）、`changePassword` 服务侧 L335/L336/L350/L351（缺陷面 A，首轮记录即正确）。⑥ **路径事实纠正**：`MyBatisLocalUserRepository`/`MyBatisRefreshTokenRepository` 实际位于 `identity/infrastructure/` **直属**（无 `persistence/` 子包）；第四轮曾按残留印象猜路径致 `Get-Content` 报「找不到路径」，经 Glob 纠正 —— 与上文「两处文件名推断更正」同族教训：**引用文件前必须先 Glob 实测路径，不得凭记忆拼装**。
- **未完成取证清单**（本项明确标注，不得写成已证）：契约 `securitySchemes` 与 `PrincipalView`/`TokenPairPayload`/`LoginRequest`/`ChangePasswordRequest` schema 区的逐字段复验；`BCryptPasswordHasher` 全文（散列参数、是否恒定时间比较）；seed `system_config`（`V8` 及后续）是否种有 Argon2id / token TTL / 锁定阈值键；`docs/ai-spec/04-ui/page-catalog.md` 的 `PAGE-AUTH-001/002` 与前端 `AccessPage` 实现面；`docs/ai-spec/02-domain/error-catalog.md`（㉛ 回填：原写 `docs/ai-spec/03-errors/**` —— Glob 实测无此目录，错误码定调载体实为 `02-domain/error-catalog.md`）对 423/403 的定调；`/me` 的 `roles` 在 OpenAPI schema 中的 `required` 归属。
- **未实测清单**（全部为静态结构推论，**不得据本节文字当作 E4 证据**）：并发刷新的 CAS 真实胜者与败者落点；已过期 refresh 令牌的实际错误码；`readOnly=true` 事务内审计是否被回滚或静默丢弃；423/403/401 在真实 HTTP 层（含 `RestAuthenticationEntryPoint`）的落点与响应体形状；`locked_until` 到期后的自动解锁路径是否真的放行；双公钥轮换期间新旧 `kid` 令牌的实际验证结果；改密后前端是否因 `Void` 响应而停在已失效会话（`AccessPage` 未取证）。
- **测试面未逐例核对**：本域存在既有 IAM 测试文件，但本项**未逐个 Read 其断言**，故不断言「测试固化缺陷」也不断言「测试覆盖充分」——该结论属 `AC-P0B-IAM-001..012` 的 E4 取证范围，须由人类方案给出（对照 catalog L146 已自认 V05 只覆盖 `AC-P0B-IAM-002` 的一小部分）。
- **行号基准面**：本节全部行号以**当前工作树磁盘状态**为准；本会话**仅修改本迭代计划一个文件**，未触碰契约、`01`/`02`/`04`/`05` 规范、`tasks`/`evidence`、backend、frontend、`deploy`、以及任何已应用迁移（`V3`/`V33`/`V34`）。

**根因（与 ㉕—㉙ 同源，本域另有四个特有因子）**：`REQ/不变量/状态机 ↔ 契约 ↔ 迁移 ↔ 领域 ↔ 仓储 ↔ 过滤器 ↔ 错误码 ↔ 指标 ↔ TS ↔ UI` 十层无生成关系也无一致性守卫，于是出现四组「每层内部自洽、两两互斥」：`PrincipalStatus`（契约三值 / DB 两值 / Java 无枚举，缺陷面 R）、`RefreshTokenStatus.EXPIRED`（DB 允许 / 代码不写，缺陷面 D）、`iam_token.row_version`（列存在 / 记录无字段 / 两仓储口径不一，缺陷面 Q）、`must_change_password` vs `UserStatus.PENDING_ACTIVATION`（双真值，缺陷面 M）。本域四个特有放大因子：① **令牌生命周期只有一个作者**：`JwtTokenService` 单方面决定 `exp`/`iat`/claim 形状，DB 侧 `iam_token` 只做「影子记录」，从不参与过期推进、从不被 JWT 校验读取（吊销检查器 L50-59 只读 `iam_principal`/`iam_agent` 的 `disabled` 与 `token_version`，**不读 `iam_token.expires_at`**）→ 于是 `EXPIRED`、`expires_at`、`row_version` 三样一起退化为装饰（缺陷面 D、Q）；② **fail-open 与 fail-closed 在同一文件内并存**：`parsePrincipalType` L158-167 正确拒绝未知值，`parseTokenType` L169-178 却默认放行，`resolveKeyPair` L223-229 缺密钥时自我修复，`JwtAuthenticationFilter` L91-96 缺守卫时静默跳过 —— 无统一约定，也无启动期不变量校验；③ **以 `orElse(true)` 表达「未知即拒」时连带误伤**：`IdentityTokenRevocationChecker` L60 为了让三类未支持主体 fail-closed 而写死 `return true`，代价是契约（L106 摘要）、枚举（`PrincipalType` L13-29）、DB CHECK（`V3` L19）三处对它们的存在性承诺全部落空（缺陷面 I），而这才是正确做法（应当显式 `NOT_SUPPORTED` 启动期失败或明确 `NOT_IMPLEMENTED` 分级）；④ **规范与契约在同一处安全属性上分头书写**：`REQ-IAM-004` L148 与契约 L86 关于「请求体是否可携带 refresh JWT」的正面矛盾（缺陷面 S）说明契约不是规范的派生物，而实现（L69-72）按契约写、按规范判即错，AI 无法在不获裁决的情况下收敛。`implemented-unverified` 在本域同样掩盖了应为 `NOT_IMPLEMENTED` 的分级：缺陷面 P（零指标）、D（`EXPIRED` 不可达）、R（无 `PrincipalStatus`）、I（三类主体零签发点）四项按 catalog 的 MUST + E4 口径都**不是「待验证」而是「没有」**。

**限定更正**：① 对本域任何「PrincipalContext 字段为空导致越权」的表述一律**作废**，统一改为「上下文最低字段不符 `REQ-IAM-003` L115（小节标题）/ L117-118（字段清单，第四轮回填：原写 L117-120 把 L119 空行与 L120 的 Query Port 说明一并计入字段清单）且契约 `/me` L180 承诺的 `roles` 恒空（`toPrincipalView` L388），授权判定本身由 `AuthorizationService` L123/L147-148/L205-231（第四轮二次回填：原写 L210-229，见偏差登记）实时解析、不受影响」；② 「刷新令牌吊销检查形同虚设」的表述限定为「`kid`/`tokenType` 两个 fail-open 点（缺陷面 B）+ `ObjectProvider` 无守卫（缺陷面 K，当前装配成立）」，不得写成「吊销检查未实现」——`IdentityTokenRevocationChecker` L50-59 对 USER/AGENT 的 `token_version` 比对是真实生效的；③ 缺陷面 D 的「`EXPIRED` 零写入点」**作用域限定为 `backend/src/main/java/com/aihub` 的生产代码**，他域 `EXPIRED` 命中（`JOB_LEASE_EXPIRED`、`upload_session`、`AUTH_TOKEN_EXPIRED`）已逐条分流，不得据此断言「全仓无 EXPIRED 字面量」；④ 缺陷面 J 只证明「readOnly 事务内含写操作」这一结构事实，**不证明审计一定丢失**（取决于驱动，未实测）；⑤ 缺陷面 T 不作缺陷计入差集数量，按「依据未证成的兼容特例」处理；⑥ 缺陷面 N 派生 3（Argon2id）**不作为缺陷断言**，因 `REQ-IAM-001` 口令散列口径的逐条回读与 seed `system_config` 取证未完成，只登记「`resources` 无 `argon2` 键 + 实现为 BCRYPT」这一事实；⑦ `AC-P0B-IAM-*` 的合规性判定全属 E4，本项只提供静态前提，任何「AC 通过/失败」结论均不由本节作出；⑧ 本项不修改任何禁改面文件，缺陷面 S、R、I 三处冲突仅登记待验收方处置。

**整改待批（AI 不自决）**：① 改密会话保全：`changePassword` 改为**只吊销非当前 Family**（`MyBatisRefreshTokenRepository` 增 `revokeAllByPrincipalExceptFamily(principalId, currentFamilyId)`，`@Transactional` 与现 L335 同边界（第四轮二次回填：原写 L334 偏 -1，Grep 实测注解在 L335、方法签名在 L336）），并按 `state-machines.md` L30 在改密成功后**轮换当前 refresh**（新 jti 入新 Family）、`AuthController` L127-135 返回 `TokenPairPayload` 并经 `withRefreshCookie` 重写 Cookie（须同步契约 `/me/password` L210-217 的 200 响应体 schema，属**破坏性变更须 ADR**）；② 验证侧改为按 `kid` 选密钥：`JwtTokenService.parseClaims` L187-196 读取 header `kid`，未知 `kid` 抛 `AUTH_UNAUTHENTICATED`，`parseTokenType` L169-178 对 null/未知改为 fail-closed（与 L158-167 对齐），并把「缺 PEM 即生成临时密钥」L216-233 改为**生产 profile 启动失败**（仅 dev/compose 允许，且 `application.yml` L95-97 三键的默认空值必须删除）；③ 刷新语义三分：`AuthenticationApplicationService` L235-238 按 `RefreshTokenStatus` 分流为 `AUTH_REFRESH_REPLAYED`（ROTATED）/ `AUTH_UNAUTHENTICATED`（REVOKED）/ `AUTH_TOKEN_EXPIRED`（EXPIRED），`x-audit-event` 拆成 `AUTH_TOKEN_REFRESHED` 与 `AUTH_TOKEN_REPLAY_REJECTED` 两个（契约 L83，须先入契约再实现）；④ 让 `expires_at` 真实生效：新增 `V45+` 迁移与持久化 JobHandler（复用 ㉙ 的 Job 体系，禁止 `@Async`/裸线程）把 `ACTIVE 且 expires_at < now` 的行推进为 `EXPIRED`，同时给 `RefreshTokenRecord` 补 `rowVersion` 字段并让 `claimForRotation` L63 递增该列（否则乐观锁继续是装饰）；⑤ 锁定阈值/时长配置化：`LocalUser` L22/L25 常量降级为 `@Value`/`record` 入参，`MyBatisLocalUserRepository` L77/L78 改读配置，`application.yml` 新增 `aihub.security.lockout.{max-attempts,duration}` 并种入 `V8` `system_config`，`AC-P0B-IAM-005` 的可配置断言据此可测；⑥ 成功路径原子化：登录成功复位改为与 L73-87 同形的单语句 `setSql`（`failed_login_attempts = 0, status = CASE WHEN … END, locked_until = NULL`），并把 `changePassword` L116-125 改为**同时清失败计数与锁定**，`isLocked` L70-72 改为 `status == LOCKED || lockedUntil 有效` 双判并出 `V45+` CHECK/生成列保证 `INV-IAM-004` 可被数据库约束（缺陷面 O、G）；⑦ 摘要是唯一键：`claimForRotation` L60、`markRotated` L69-74、`revokeFamily` L77-83 全部改为 `jti_digest` 谓词，`V45+` 迁移把 `jti` 列改为可空或经 `DROP CONSTRAINT` 撤 UNIQUE（**不得改 `V3`/`V33`**，以新迁移修正注释与约束），删除 `findByJti`（接口 L22）及其明文回退 L52，仅在缺陷面 T 的回填证明完成后分两步执行；⑧ 三类未支持主体：以人类裁决选一 —— 要么 `PrincipalType` 收缩为 `USER/AGENT`（契约 + `V45+` CHECK 同步，**破坏性须 ADR**），要么补 `SERVICE/API_CLIENT/WORKER` 的真实签发与校验路径；任何方向都不得保留「枚举/DB/契约有、校验器 L60 判必吊销」的四向并存；⑨ `PrincipalStatus`：新增 Java 枚举并与 `iam_principal.status` 值域、契约 `[ACTIVE, LOCKED, DISABLED]` 对齐，同时补 `V45+` CHECK 与「`iam_user.status=LOCKED` ⇒ `iam_principal` 同步」的约束或不变量测试，落实 `state-machines.md` L47；⑩ 强制改密改判据：`PasswordChangeRequiredInterceptor` 以 `UserStatus.PENDING_ACTIVATION` 为主判据、`must_change_password` 降为审计原因，并由 `V45+` 约束或不变量测试禁止二者背离（缺陷面 M）；⑪ 契约回补（先入契约再改实现）：`/auth/token` L110 与 `/me/password` L203 补 `AUTH_INVALID_CREDENTIALS`，全域补 `PASSWORD_CHANGE_REQUIRED`（403 响应体 + `Retry-After` 语义由 `02-domain/error-catalog.md` 定调，㉛ 回填：原写 `03-errors`，Glob 实测无该目录），`/auth/token` L119 描述据实现改为「仅签发访问 JWT」或按实现补 refresh，`AuthController` L52-60/L127-130 补 `@Valid` 使 `minLength: 12` 在后端真实生效；⑫ 缺陷面 S 的冲突**必须先由人类裁决**（改规范 L148 或改契约 L86 + 收紧 `AuthController` L69-72），AI 不得择一实现；⑬ 可观测性：`PlatformMetrics` 增 `aihub_auth_login_total{outcome}`（success/invalid_credentials/locked/disabled）、`aihub_auth_refresh_total{outcome}`（issued/replayed/revoked/expired）、`aihub_token_revocation_checks_total`，满足 `REQ-IAM-002` L82 四类计数并可被 `/actuator/metrics` 断言；⑭ 事务与守卫修正：`exchangeClientCredential` L261 的 `readOnly = true` 移除（或审计改走 `REQUIRES_NEW` 传播），`SecurityConfiguration` 增启动期断言「`TokenRevocationChecker` bean 必须存在」（`ObjectProvider` → 直接构造注入 + 缺 bean 即启动失败），`MyBatisRefreshTokenRepository` L126-131 的失实注释（javadoc 块 L125-128，`/**` 在 L125、`*/` 在 L128）与死参数（L129 签名 `resolvePrincipalType(String principalId)` 的入参未被使用）据实删除（第四轮二次回填：切分口径与缺陷面 Q 的 L125-128／L129-131 统一，Grep 实测），`toDomain` L118 的 `valueOf` 包成映射错误码；⑮ 建立**十层一致性守卫测试**（与 ㉙⑳ 的守卫合并为同一套门禁），至少断言：`iam_*` 各 CHECK 允许值 ⊆ Java enum ⊆ 契约 enum；`PrincipalContext` 每个「最低字段」在过滤器后有非空来源或有显式 `NOT_APPLICABLE` 注解；契约每个 `x-error-codes` 项存在产生点、每个产生点已声明；`REQ-IAM-002` L82 每个指标存在注册点；`iam_token`/`iam_user` 每个状态列存在写入点；⑯ `AC-P0B-IAM-001..012` 的 E4 取证方案须人类先给出，本项只提供静态前提，至少须含七个场景：并发刷新（同 jti 双请求单胜）、ROTATED 重用触发整族吊销、已过期与已撤销令牌的错误码区分、双公钥轮换窗口内新旧令牌同时可验、改密后当前会话可用性与旧 Family 失效、锁定阈值/时长改配置后生效、登录四类指标可被抓取；⑰ 上述任一实现整改前需人类先出 `READY` + `implementationAuthorized: true` 的 Task Card（DEC-009），AI 不得自批；本项亦不得以复跑 `validate-spec.ps1` 通过替代业务面 E4 证据。

→ 本项与 ①—㉚ 一并不在本轮 AI 自决实施范围内。

### ㉛ NFR 质量门禁面：七方差集（`non-functional-requirements.md` ↔ CI 工作流 ↔ 两份 validate-spec.ps1 ↔ ArchUnit 守卫 ↔ Spring 条件装配 ↔ Shell/PowerShell 双脚本 ↔ 前端 E2E 与类型面）

**权威口径**：行号一律取**当前工作树磁盘状态**，以「无 offset 的 `Grep -n`」为唯一计数基准，关键断言另以 `Read` 原文与 `RunCommand` 逐行索引复核；`Read(offset)` 在同一文件内偏移不规则（本项再次遇到，见「取证偏差登记」第 1 条），凡仅出自它的行号一律不采信。NFR 表内每条要求自带证据等级（`E1`–`E5`）与 `OPEN` 标记，本项**只判定「门禁是否存在、门禁强度是否为强制、静态结构是否满足」，不判定任何 E4/E5 业务结果**。

**七方差集取证表**（`✓` 有实现且有门禁 · `△` 有实现无门禁/门禁为软 · `✗` 缺 · `—` 不适用）：

| NFR 条目（行号） | 要求的门禁/机制 | 仓库实况（实测行号） | 判定 |
| --- | --- | --- | --- |
| `NFR-COMP-001` **L76** + **L83-84** 明句 | lint、示例校验、breaking diff；**未接受的破坏变更阻断 CI**；「continue-on-error 的『评审信号』不足以成为强制门禁」 | ci.yml **L140-144** lint 强制（㉛ 二次回填：原写 L140-143，逐行仲裁证实步骤体为 L140-144，Redocly 命令在 **L142-144**）；**L157-161** breaking diff 仅在 `IS_MAIN_PR=true` 分支带 `--fail-on-breaking`，else 分支 `|| echo "Breaking change detected (non-blocking for non-main PR)"` | **✗**（对撞规范原句） |
| `NFR-COMP-005` **L80** | 两个 Verify 包装**执行同一权威断言并产生相同结果语义**（`E4`） | `deploy/compose/scripts` 实测 13 项：仅 `verify`、`verify-journey` 有 `.sh`+`.ps1` 成对；`perf-smoke.sh`/`verify-schema.sh`/`verify-restore.sh`/`restore.sh`/`p5-restore-drill.sh`/`p4-onboarding.sh`/`backup.sh` **无 `.ps1` 对应**，`bootstrap.ps1` **无 `.sh` 对应**；CI 对 `.ps1` 的全部引用只有 **L125**（`./tools/validate-spec.ps1`）与 **L348**（`validate-task-card.ps1`）（㉛ 二次回填：原写 L124，PowerShell 逐行仲裁证实 L124 = `shell: pwsh`、L125 = `run: ./tools/validate-spec.ps1`），**CI 从不执行任何 `verify*.ps1`** | **✗**（等价性既无自动执行也无断言比对） |
| `NFR-MNT-006` **L104**（`E1`，AI 实施门禁） | 未通过正式 Task/REQ/DEC/base Commit/`allowedPaths`/Evidence 校验的会话不能修改产品 | AGENTS.md **L33** 规定跑 `./docs/ai-spec/tools/validate-spec.ps1`（实测 **276** 行：manifest 路径存在与去重段 **L49-75**（去重循环 **L71-75**）、`requiredPolicyPatterns` 声明 **L188** 含 20 项模式 **L189-208**、校验循环 **L210-214**、`acceptedDecisions` 必须 `ACCEPTED` **L221-233**、坏链 **L258-262**）（㉛ 二次回填：原写「模式 L194-212 / 去重 L49-73 / 坏链 L260」系把校验循环位置误记为模式条目位置、区间边界未逐行实测，PowerShell 逐行仲裁证实上述值）；CI 的 `spec-gate` 实跑 **L125** = `./tools/validate-spec.ps1`（实测 **79** 行，内容仅 `Test-Path` + `Length` 阈值 + `Write-Ok/Write-Fail`，见 **L13-14/L30-35/L48/L54-59/L69**）；全仓 Glob 实证**两份脚本同名不同物**并存 | **△→✗**（门禁名同实异，CI 侧未校验 Task/DEC/allowedPaths） |
| `NFR-MNT-001` **L102** | Controller/MCP/Worker 不调 Mapper；Domain 不依赖框架；**跨模块只走 API/Port**（`E1/E2`） | `LayeredArchitectureTest` 九规则位于 **L50/L57/L64/L72/L82/L89/L98/L105/L112**，**每条**尾随 `allowEmptyShould(true)`（**L54/L61/L69/L79/L86/L95/L102/L109/L116**），**L24** 注释自认「骨架阶段相关包多为占位，规则统一允许 should 集合为空」；`BUSINESS_MODULES` **L30** 的 16 个业务包之间**无任何相互依赖禁令** | **△**（规则可被「包为空」静默满足；跨模块边界零守卫） |
| `NFR-MNT-003` **L105**（`E1`） | 契约生成：前端 DTO 不手工漂移；生成结果可复现并有 diff | `frontend/package.json` 的 `scripts` 起于 **L7**，其中与契约相关的生成脚本**零命中**（全文件仅 `"test"` **L13**、`"test:e2e"` **L15** 等）；`frontend/src/shared/api/client.ts` **L16** `import type { ApiErrorBody, ApiResponse } from '@/shared/types';` 为手写类型；`frontend/**/generated/**` Glob 无产物 | **✗** |
| `NFR-MNT-005` **L107**（`E3/E4`） | **生产 Profile** 不含 Noop/allow-all/空主体/内存可靠任务/默认全 Scope | `backend/src/main/resources/application.yml` 仅有 4 个 `on-profile`：**L168** compose、**L186** api、**L195** worker、**L205** test；Glob `**/{application,bootstrap}-prod*` = **No file found**（生产 Profile 不存在）；且默认值即不合规组合：`aihub.gitea.enabled` 默认 **false**（**L81**）+ `NoopVersionMaterializer` **L18**、`NoopGiteaTagPublisher` **L21** 均 `matchIfMissing=true`；`aihub.rate-limit.backend` 默认 **memory**（**L147**）+ `MemoryRateLimitBackend` **L22** `matchIfMissing=true`；Java 侧无 `@Profile` 守卫、无启动期不变量校验 | **✗**（且无法被任何门禁发现：不存在 prod profile，也不存在针对 prod 的 CI job） |
| `NFR-SEC-002` **L34**（`E4`） | access 仅内存；refresh 为 **Secure/HttpOnly/SameSite** Cookie；LocalStorage/埋点/报告无 JWT | application.yml **L104** `secure: ${AIHUB_REFRESH_COOKIE_SECURE:true}`、**L105** `same-site: ${AIHUB_REFRESH_COOKIE_SAMESITE:Strict}`；**仅** test profile 降级（**L215** `secure: false`、**L216** `same-site: Lax`） | **✓**（默认值即合规，优于「可配置但默认宽松」） |
| `NFR-SEC-003` **L35**（`E3`） | 非对称 JWT + `kid`；缺凭据须失败 | 密钥三键默认为空串（**L96/L97** `:}`）；`JwtTokenService.resolveKeyPair` **L216**（调用点 **L72**）在缺 PEM 时仅 **L224** WARN「…for non-production use only…」后 **L228** `generator.initialize(2048)` 生成临时密钥继续启动 | **△**（fail-open 到日志级，且**无 profile 判据可依**——见 MNT-005 行） |
| `NFR-SEC-009` **L41**（`E5`） | Secret 扫描；镜像与依赖锁定；漏洞检查达到**待确认阈值** | `security-scan` job 头 **L36**，其内：**L49-55** Maven dependency-check step（`-DfailBuildOnCVSS=9` 在 **L53**）、**L57-63** `npm audit --audit-level=critical` step（命令在 **L60**）、**L65-66** Gitleaks step；另有独立 `secret-scan` job 头 **L164**，其 Gitleaks step **L172-175**（`GITLEAKS_CONFIG: .gitleaks.toml` 在 **L175**）（㉛ 二次回填：原写「dependency-check L49-52」少算步骤体尾 3 行、「secret-scan job L171-175」把 step 范围误作 job 范围，逐行仲裁证实 job 头 L164、step L172-175） | **△**（三项扫描存在且为强制；「阈值」本身仍是 `OPEN`，**L13** 明禁 AI 自选数字，故不得宣布达标） |
| `NFR-OBS-001` **L64**（`E4`） | Browser/Agent→Nginx→REST/MCP→应用→DB/外部依赖→Worker 可按 request/trace/principal/resource 关联 | `PrincipalContextFilter` **L43** `TRACEPARENT_HEADER = "traceparent"`、**L101-106** 解析 W3C trace-id、**L79** 写 MDC、**L83** 回写响应头；`JdbcJobRepository` **L76**、`JobWorker` **L159/L177** 持久化并透传 `traceId`；`JdbcAuditRepository` **L85** 落 `trace_id`；`SecurityResponseWriter` **L65-66** / `GlobalExceptionHandler` **L76-78** 把 traceId 返给调用方 | **△**（自研 traceId 关联**真实存在**，但无 OTel/SDK、无 span 模型，DB/外部依赖侧与 MCP 侧关联未证；**本行系本项对上一轮记录的实质更正**） |
| `NFR-OBS-002` **L65**（`E4`） | 持久化 Trace Context；Worker **建新 Span 并使用 Span Link** | `backend/pom.xml` 依赖面只有 **L48** actuator、**L52-53** `micrometer-core`、**L57-58** `micrometer-registry-prometheus`，**无** `micrometer-tracing`/OTel/brave/zipkin；main 代码对 `\bSpan\b|Tracer|OpenTelemetry` 的检索**仅命中 `PrincipalContextFilter` 的 traceparent 注释与常量**（**L31/L43/L101/L104-106**），无任何 Span/Link 构造点 | **✗**（结构性不可达标，非「待验证」而是「没有」） |
| `NFR-OBS-004` **L67**（`E4`） | liveness 不依赖外部系统；readiness 只含关键依赖；诊断端点受权且脱敏 | application.yml **L49** `include: health,prometheus,metrics`、**L56** liveness 仅 `livenessState`、**L58** readiness 仅 `readinessState,db`；**L44** 注释声明 env/heapdump 不暴露 | **✓** |
| `NFR-UI-007` **L96**（`E4`） | 关键页面在目标视口有**基准截图**且无未批准差异 | `frontend/playwright.config.ts` **L24** `trace: 'on-first-retry'`、**L25** `screenshot: 'only-on-failure'`；`frontend` 全域检索 `toHaveScreenshot|toMatchSnapshot|__snapshots__` **仅命中 playwright.config.ts L25 与构建产物 `frontend/playwright-report/index.html`**（后者是报告页自带字符串，非测试代码）；`frontend/e2e/fixtures` 下 4 个文件为测试输入数据，非基准图 | **✗**（唯一截图配置是「失败时截图」，与「基准比对」语义不同；CI **L273-321** 的 `playwright-e2e` 亦无 `--update-snapshots` 审批流，且 **无 windows/自托管 runner**） |

**合规面（不得在后续叙述中被写成缺陷）**：① `NFR-SEC-002` 的 Cookie 默认 `Secure + SameSite=Strict`（application.yml **L104-105**），只有 test profile 放宽，**不需要**整改即可满足静态默认；② `NFR-OBS-004` 健康分层三键配置齐备且与 **L44** 注释一致；③ 三类安全扫描（Maven dependency-check **L49-55**、`npm audit --audit-level=critical` **L57-63**、Gitleaks × 2 job **L65-66**/**L172-175**）**均在 CI 中真实存在且非 continue-on-error**——实证：`security-scan` job 头 **L36** 其内无任何 `continue-on-error`，且全文件 `continue-on-error` 经全量检索**恰为 3 处**（**L241**/**L260**/**L356**），不覆盖任何扫描步骤；④ `contracts` job 的 OpenAPI **lint 为强制**（**L140-144**，**L139** 注释自证「任何 error 阻断流水线」），且 MCP `tools.yaml` 另有独立契约 job（**L177-192**）（㉛ 二次回填：原写 L140-143/L178-194，逐行仲裁证实 lint 步骤体止于 L144、`mcp-contract` job 头在 L177 且末行在 L192，L194 已是下一 job `verify-schema` 的注释/头）；⑤ `task-card-gate`（**L323-350**，㉛ 二次回填：原写 L322-349，Grep 精确证实 job 头 L323、validate-task-card 调用 **L348**、exit 检查 **L349**、块闭合 **L350**）确实以 `validate-task-card.ps1 -TaskPath … -CheckChangedPaths` 强制校验变更过的 Task Card，**AGENTS.md 的 allowedPaths 门禁在 CI 侧是有实现的**（缺陷只在 spec-gate 那一半）；⑥ traceId 关联链（过滤器→MDC→响应头→Job 持久化→审计落库→错误响应）是**成体系的真实实现**，不得写成「无可观测性」。

**缺陷面**：

**A：CI 的 AI Spec 门禁跑的是浅脚本，AGENTS.md 规定的是深脚本 —— 「门禁」名同实异。**
- ci.yml **L125** `run: ./tools/validate-spec.ps1`（79 行，实测其全部检查为「文件存在 + 字节数 >100/>1000」，见 **L30-35/L48/L54-59/L69**，**L75** 输出 `AI Spec Gate: PASSED`、**L79** 为末行 `exit $exitCode`）；AGENTS.md **L33** 规定实施前必须跑 `./docs/ai-spec/tools/validate-spec.ps1`（276 行，含 manifest 去重 **L71-75**、`requiredPolicyPatterns` 声明 **L188** 含 20 项模式 **L189-208** 与校验循环 **L210-214**、`acceptedDecisions` 状态必须 `ACCEPTED` **L221-233**、Markdown 坏链 **L258-262**）。（㉛ 二次回填：原写 L124/去重 L64-73/模式 L194-212/末行 L75，PowerShell 逐行仲裁证实上述值；L194 只是 20 项模式列表中的一条 `aiExecutionPolicy` 条目，不是模式集合的起点。）
- 后果：**仓库根那份浅脚本被绿色通过，完全不能证明深门禁的四项核心不变量成立**；而 `NFR-MNT-006`（**L104**）的达标证据等级虽是 `E1`，其语义（「未通过正式 Task/REQ/DEC/阶段/base Commit/allowedPaths/Evidence 校验的会话不能修改产品」）只能由深脚本+Task Card 校验共同支撑。CI 里深脚本**一次都没跑过**（ci.yml 对 `validate-spec` 的引用仅 **L125** 一处）。

**B：生产 Profile 不存在，导致 `NFR-MNT-005`（L107）成为不可判定的空命题。**
- application.yml 只有 **L168/L186/L195/L205** 四个 profile 段；全仓 Glob `**/{application,bootstrap}-prod*` = **No file found**；Java 侧 Grep `@Profile` 与 `prod` 字符串判等仅得 `SecurityConfiguration` 与 `PublishedVersionReconciler` 两处零散字面量，**无 `EnvironmentPostProcessor`、无启动期不变量断言**。
- 于是 **L107** 的谓词「生产 Profile 不含 Noop/allow-all/内存可靠任务/默认全 Scope」缺少求值对象；同时**默认值本身就是不合规组合**（**L81** `enabled:false` + **L18/L21** `matchIfMissing=true`；**L147** `memory` + **L22** `matchIfMissing=true`），即「不加任何环境变量启动」= 直接进入 Noop + 内存限流形态。

**C：同一目录内两个 Noop 的条件装配方向相反，且其中一个的注释与全局默认矛盾。**
- `NoopVersionMaterializer` **L18** 与 `NoopGiteaTagPublisher` **L21** 是 `havingValue="false", matchIfMissing=true`（缺配置即装配 Noop）；`NoopAssetRepositoryProvisioner` **L21** 却是 `havingValue="false", matchIfMissing=false`（缺配置**不**装配 Noop，从而要求真实实现必须存在）。
- 该类 **L16** 注释声称「默认可部署环境须接通 Gitea」，而 **L81** 的全局默认恰为 `false` —— 三者叠加的结果是：`enabled=false` 时 materializer/publisher 静默 Noop、provisioner 直接缺 bean，装配形态**不可预测且无启动期校验**。这是 **B** 的机制性成因，单列为独立缺陷。

**D：缺 JWT 签名密钥只 WARN 不失败（`NFR-SEC-003` L35 + **L12**「不因环境是开发或 Compose 而放宽」）。**
- `JwtTokenService.resolveKeyPair` **L216**，缺 PEM 时 **L223-225** 输出「…generating an ephemeral RSA key pair for non-production use only. Configure deployment secrets before production.」，**L227-228** 生成临时 RSA 2048 并正常启动。
- 关键结构事实：由于 **B**（无 prod profile、无 `@Profile` 判据），该 WARN 文案里的「non-production」**在代码与配置两侧都没有对应判据**，即这条「仅限非生产」的自我限定**当前不可执行**；多副本部署下每副本各自生成不同密钥，刷新/验签跨副本必然失败——此为静态推论，**未实测**。

**E：ArchUnit 九条规则全部 `allowEmptyShould(true)`，`NFR-MNT-001`（L102）的三项中「跨模块只走 API/Port」根本无规则覆盖。**
- 九规则 **L50/L57/L64/L72/L82/L89/L98/L105/L112** 与九处 `allowEmptyShould(true)` **L54/L61/L69/L79/L86/L95/L102/L109/L116** 一一对应；**L24** 注释把理由写成「骨架阶段相关包多为占位」，但骨架阶段早已过去（P1–P5 已有大量实现），该豁免**未被回收**也未列入任何待办。
- `BUSINESS_MODULES`（**L30**，16 个业务模块包）之间**没有 `slice`/`independence` 类规则**，意味着「asset 模块直接 import version 模块内部类」不会使 `mvnw verify`（ci.yml **L31-32**）失败。

**F：`NFR-COMP-005` 的「双语等价」缺的不是 `.ps1` 文件，而是任何一次执行与任何一份断言对照。**
- 实测 13 个脚本中 **7 个 `.sh` 无 `.ps1` 对应**（`perf-smoke`/`verify-schema`/`verify-restore`/`restore`/`p5-restore-drill`/`p4-onboarding`/`backup`），**1 个 `.ps1` 无 `.sh` 对应**（`bootstrap`）。
- 更强的事实：ci.yml 对 `.ps1` 的引用只有 **L125** 与 **L348** 两处（㉛ 二次回填：原写 L124，证实 L124 = `shell: pwsh`、L125 = `run: ./tools/validate-spec.ps1`），**`verify.ps1`/`verify-journey.ps1` 在 CI 中零调用**（CI 实跑 **L252** `./scripts/verify.sh`、**L271** `./scripts/verify-journey.sh`、**L234** `./scripts/verify-schema.sh`）。`deploy/compose/scripts/verify.ps1` 自身是**独立完整实现**（其 **L20/L35/L47/L59/L109/L117/L218/L229/L245/L254/L262/L325** 处 `param(...)` 与大量自有断言段可证），因此两条路径**分头演化、互不校验**，**L80** 要求的「相同结果语义」没有任何机制保障。

**G：`NFR-COMP-001` 的 CI 实现与其规范原句直接对撞（**L83-84** 逐字反对 CI 现状）。**
- ci.yml **L157-161**：仅当 `IS_MAIN_PR=true` 才 `--fail-on-breaking`（**L159**），否则 **L161** `… --fail-on-breaking || echo "Breaking change detected (non-blocking for non-main PR)"`。
- NFR **L83-84** 原文即「Breaking diff 作为 `continue-on-error` 的『评审信号』不足以成为强制门禁；允许的破坏变更必须关联 Accepted ADR/Decision」，且 `docs/adr/` Glob 实测**只有 ADR-0001、ADR-0002 两份**，不存在任何「破坏变更 ↔ Accepted ADR」的关联校验脚本或 job。即规范预想的「关联校验」在仓库里**既无门禁也无载体**。

**H：`compose-e2e` 与 `journey-e2e` 在 PR 上是软门禁，与 `NFR` **L9**「不能统一推迟到优化阶段」及 `NFR-COMP-005`/`REL`/`SEC` 各条的 `E4` 要求相冲突。**
- ci.yml **L241** 与 **L260** 均为 `continue-on-error: ${{ github.event_name == 'pull_request' }}`（**L240**/**L259** 注释自认「PR 上暂为可选」）；`perf-smoke` **L356** `continue-on-error: true` 且 **L357** 仅 main push 触发。
- 结构后果：**唯一能证 `E4`（登录/JWT 生命周期/权限过滤/日志脱敏/审计完整性/持久化 Job/通知/可观测性）的两个 job，对贡献了绝大多数代码量的 PR 不阻断**。这与 AGENTS.md「A test skipped because Docker is unavailable is not a passing integration test」同源，但这里是**跑而不论**。

**I：`NFR-UI-007`（L96）的基准截图不存在，且 CI 无条件为其生成。**
- 见差集表 UI-007 行：**零** `toHaveScreenshot` 调用、**零** `__snapshots__` 目录、**零**基准图；playwright.config.ts **L25** 只有 `screenshot: 'only-on-failure'`。
- ci.yml **L273-321** 的 `playwright-e2e` job 只 `pnpm test:e2e`（**L314**）+ 上传报告（**L315-321**），**无 `--update-snapshots` 流程、无基准图审批/回写机制**，即使补上测试也无落地通道。

**J：`NFR-MNT-003`（L105，`E1`）的契约生成完全缺位。**
- 见差集表：**零** codegen 脚本、**零** `generated/` 产物、`client.ts` **L16** 手写类型。`E1` 是最低证据等级（脚本可证），意味着这是**本迭代内所有已登记域中少数「补齐成本最低、却连 E1 都未达」** 的条目。㉚ 缺陷面中「契约 `required` 与前端表单校验分头书写」类问题的根因即在此。

**K：`AUD-005` 的悬空引用：NFR 用它阻塞整组 OBS，但它的权威定义不在 NFR 而在 governance 审计文档。**
- NFR **L5** 与 **L70** 两处均以 `AUD-005` 为阻塞项，但 `AUD-005` 在 `docs/ai-spec/01-requirements/non-functional-requirements.md` 的**需求表里不存在**（NFR 表内 `E` 级检索仅得 `NFR-OBS-*`/`NFR-REL-*` 等），其定义位于 `docs/ai-spec/00-governance/current-state-audit-2026-07-02.md` **L29**（「可观测范围冲突：ADR-0002 与 P0-B Task 12 要求 OTel Trace 贯通；7 月 2 日快照称完整 OTel 导出在 P0-B 之外」）与 `docs/ai-spec/01-requirements/p0b-platform-services.md` **L3/L6/L350**（`REQ-OBS-001` `blockedBy: [AUD-005]`，状态 `OPEN`）。
- 三份文档的连带事实：`docs/adr/` 只有 2 份 ADR，即 **L29 提出的「若缩小范围需新 ADR」至今未出**；而 **E4 级**的 `NFR-OBS-001/002` 在**无 OTel 依赖**（**F**/**E** 行的 pom 事实）下已被写进表格 —— **状态声明与依赖事实长期背离且无任何门禁发现**（`validate-task-card.ps1` 只校验 Task，不校验 NFR 表与 pom 的一致性）。另注：`docs/ai-spec/tasks/evidence/EVD-P5008-001.yaml` **L40/L127** 已自认同一矛盾并被登记为「规格-任务卡矛盾，AI 不自行翻转需求状态」，与本项结论互相印证。

**L：`NFR-SEC-009`（L41）与 **L13**「AI 不得选择一个容易通过的数字」之间的张力未被处理。**
- L41 要求「漏洞检查达到**待确认阈值**」，但 CI 已把阈值硬编码为 `critical`（**L60** `--audit-level=critical`）并作为强制 job 运行。即：**一个未获批的数字已成为事实门禁**。本项**不判**该数字应当取多少（属人类裁决），只登记「阈值来源不可追溯」这一事实。

**未实测清单**（全部为静态结构推论，**不得据本节文字当作 E1–E5 任一级证据**）：ci.yml 任何 job 的**实际运行结果**（本项未读 Actions 运行记录）；多副本各自生成临时 JWT 密钥时的真实验签失败；`allowEmptyShould(true)` 在当前代码量下是否已有某条规则的 should 集合实际非空；`verify.ps1` 与 `verify.sh` 的**断言集合是否已经漂移**（本项只证「CI 从不执行 .ps1」与「.ps1 自成体系」，**未逐条比对两文件的断言**）；`NoopAssetRepositoryProvisioner` 在 `enabled=false` 下是否真的导致缺 bean 启动失败；浅脚本 `tools/validate-spec.ps1` 在深门禁四项不变量被人为破坏时是否仍 PASS（**该反证实验未做**，因其需临时改禁改面文件）；`gitleaks-action@v2` 在 **L66**（未设 `GITLEAKS_CONFIG`）与 **L172-175**（设了 `.gitleaks.toml`）两种配置下的实际覆盖差异；`NFR-SEC-001`（L33，默认拒绝）在真实 HTTP 层的 E4 结论。

**取证偏差登记（不得作为缺陷写入）**：
- **本项对上一轮记录作出两处实质更正，均为「未清空工具结果」推翻「已清空历史结论」**：① **CI 是否成对调用 sh/ps1**：上一轮记为「CI L209/L228/L240/L259 确实成对调用」，本项对 ci.yml 全量检索 `.ps1|pwsh|windows` **得 5 hits：L122/L124/L125/L331/L348**（前三者属 spec-gate：L122 `run: pwsh --version`、L124 `shell: pwsh`、L125 浅脚本调用；后两者属 task-card-gate）（㉛ 二次回填：原漏 L125，系以 L124 的 `shell: pwsh` 误代替 L125 的脚本调用行），**证伪**——CI 从不执行 `verify*.ps1`。缺陷面 F 据本项实测书写。② **traceId 是否零实现**：上一轮记为「main 代码 Grep `Span|TraceContext|tracing` 仅 3 处且全是注释/日志串」，本项以 `\bSpan\b|Tracer|OpenTelemetry|traceparent|micrometer\.tracing` 及 `TRACEPARENT_HEADER|traceId` 重检，发现 **traceId 关联是成体系的真实实现**（`PrincipalContextFilter` **L43/L79/L83/L101-106**、`JdbcJobRepository` **L76**、`JobWorker` **L159/L177**、`JdbcAuditRepository` **L85**、`SecurityResponseWriter` **L65-66**、`GlobalExceptionHandler` **L76-78**）→ 该结论**从「零实现」降级为「有实现但非 OTel、无 Span/Link」**，并据此把 `NFR-OBS-001` 判为 `△` 而非 `✗`（只有 `OBS-002` 的 Span/Span Link 部分维持 `✗`）。上一轮的错误源于**检索词过窄**，与 ㉚ 登记的「路径凭记忆拼装」同族教训：**否定性结论必须以「先宽后窄」的检索序列支撑**。
- **行号口径的再次实证**：`run: ./tools/validate-spec.ps1` 在无 offset 的 Grep 中为 **L125**，在 `Read(offset=113)` 的返回中为 **L124**（偏 -1）；`on-profile` 四段在无 offset Grep 中为 **L168/L186/L195/L205**，在带 offset 的 Read 中被记为 **L167/L185/L194/L204**（同样偏 -1）。本项统一采用**无 offset Grep 值**。这是 ㉚ 已立的「`Read(offset)` 不可信」口径在本域的第三次复现，登记以备后续会话沿用。
- **浅/深脚本的行数**（79 / 276）由 `RunCommand` 的 `(Get-Content -LiteralPath …).Count` 实测取得，非估算；两文件并存由 Glob `**/validate-spec.ps1` 实证（恰好 2 个命中）。
- **枚举性断言与区间边界失准**（㉛ 三次追加，落盘后全量逐行仲裁发现，均已原位回填）：初稿把深脚本的**校验循环范围 L210-214** 误当作「`aiExecutionPolicy` 20 项模式」的位置（模式集合实为 **L189-208**，声明 **L188**，L194 只是其中一条）、把 **Gitleaks step 范围 L172-175** 写成 `secret-scan` **job** 的范围（job 头实为 **L164**）、`dependency-check` 步骤体起止少算尾部（实为 **L49-55**）、`mcp-contract` job 范围两头各差一行（实为 **L177-192**）、把浅脚本 **L75** 称作「末行」（末行实为 **L79** `exit $exitCode`，L75 只是 PASSED 输出行）、`task-card-gate` 区间整体偏移一行（实为 **L323-350**）——**共 6 类**。根因是**对未逐行实测的区间引用做了方向性外推**（起止中只测了一端、或按语义块推测另一端）。教训固化：① 正文行号只允许出现本轮 Grep/PowerShell **逐行实测过**的值；② **区间引用必须两端各自验证**；③ **计数/「末行」/「job」类枚举断言必须先取全集再书写**（本轮 `continue-on-error`、`.ps1|pwsh|windows`、20 项模式三处均为先取全集后落字，一次通过）。
- **未采信项**：本项曾拟写「CI 无安全扫描（沿用旧 implementation-status 的 G3 结论）」——**不成立并已撤除**，ci.yml 实有 `security-scan` job（job 头 **L36**，三个扫描步骤 **L49/L57/L65**），旧快照结论已过时；该条改写入合规面③。

**限定更正**：① 差集表与 A–L 中凡涉及 `E3`/`E4`/`E5` 的条目，本项**只判定「门禁是否存在及其强弱」，一律不判定该 NFR 是否达标**——达标与否属对应 AC 的运行时证据，须由人类方案给出；② 「`NFR-OBS-001` 判 `△`」**不得被扩大引用为「可观测性达标」**，其 traceId 链的 MCP 侧与外部依赖（Gitea/MinIO）侧关联本项**未取证**；③ `NFR-SEC-002` 的 `✓` **仅指默认配置**，不覆盖「LocalStorage/埋点/报告均无 JWT」这一半要求（属前端运行时证据，本项未在 `frontend/src` 逐一检索 `localStorage`，不得据本行宣称合规）；④ 缺陷面 L **不含**「`critical` 阈值太低」的价值判断，只登记阈值来源缺失；⑤ 缺陷面 E **不**断言「ArchUnit 当前完全无效」——九条规则的**前两类**（Controller 不调 Mapper、Domain 不依赖框架）即使带 `allowEmptyShould` 仍能拦住新写的越界码，本项断言的仅是「跨模块边界零覆盖」与「可被空包静默满足」两点；⑥ 本项不修改任何禁改面文件，**A/B/C/D/G/K 五处冲突只登记不裁决**。

**整改待批（AI 不自决）**：① 让 CI 的 spec-gate 真正执行深门禁：ci.yml **L125** 改为 `./docs/ai-spec/tools/validate-spec.ps1`（㉛ 二次回填：原写 L124，证实 L125 才是脚本调用行），并把 `tools/validate-spec.ps1`（浅，79 行）删除或改为**转发到深脚本**，消除双真值（须与 `NFR-MNT-006` L104 的 `E1` 证据定义同步）；② 新建 `application-prod.yml`（或经人类裁决改用其他 profile 命名）并在**深脚本或新增启动期不变量校验**中强制断言：prod profile 下 `aihub.gitea.enabled=true`、`aihub.rate-limit.backend=redis`、无 Noop bean 参与装配、`aihub.security.jwt.*-key-pem` 三键非空（application.yml **L81/L96/L97/L147**），使 `NFR-MNT-005`（L107）成为可判定命题；③ 统一 Noop 条件装配方向：`NoopAssetRepositoryProvisioner` **L21** 的 `matchIfMissing` 与其同目录另两者（**L18/L21**）取齐，并删除或改写 **L16** 与 **L81** 矛盾的注释；④ `JwtTokenService.resolveKeyPair` **L216-228** 的「缺密钥自动生成」改为**按 profile 分流**：prod 缺失即启动失败，非 prod 保留但在日志中输出可被告警识别的 `ERROR`（并配 `NFR-OBS-005` L68 的告警阈值），同时删除 **L224** 中当前不可执行的「non-production」措辞或补齐其判据；⑤ 回收 ArchUnit 的骨架豁免：删除九处 `allowEmptyShould(true)`（**L54/L61/L69/L79/L86/L95/L102/L109/L116**），并**新增** `slices().matching("com.aihub.(*)..").should().notDependOnEachOther()` 类规则覆盖 `BUSINESS_MODULES`（**L30**）跨模块边界，落实 `NFR-MNT-001` L102 第三项；⑥ 双语等价按人类裁决二选一：要么补齐 **F** 行 7 个 `.ps1` 并让 CI 在 windows runner（或 `pwsh` on ubuntu，仓库已有 pwsh 用法：**L122** `run: pwsh --version` 与 **L124/L125** `shell: pwsh` + 脚本调用；㉛ 二次回填：原写 L121，证实 L121 = `shell: bash`）上**成对执行**同一断言集，要么以 ADR 正式撤回 `NFR-COMP-005` L80 的等价要求并把它降为「`.sh` 单一权威」——**不得维持「有 `.ps1` 但从不执行」的中间态**；⑦ breaking diff 改为强制：删除 ci.yml **L161** 的 `|| echo` 兜底，改为「若检出破坏则要求 PR 关联 `docs/adr/` 中 `status: ACCEPTED` 的 ADR 或 `manifest.acceptedDecisions` 条目，否则失败」，并在深脚本中实现该校验（对撞 NFR **L83-84**）；⑧ `compose-e2e`/`journey-e2e` 的 **L241/L260** `continue-on-error` 移除（或至少对**改动了 backend/frontend/deploy 的 PR** 强制），使 `E4` 门禁在合入前生效；⑨ `NFR-UI-007`：在 `frontend/e2e` 关键页 spec 中引入 `toHaveScreenshot()` 与基准目录，并在 CI **L314** 增加「基准图变更须由显式 `--update-snapshots` 分支 + PR 审批」的双 job 设计；⑩ `NFR-MNT-003`：新增 `pnpm api:codegen`（`openapi-typescript` 或等价，走国内镜像 + https，见用户规范）生成 DTO 至 `frontend/src/shared/api/generated/`，把 `client.ts` **L16** 的手写类型改为消费生成物，并在 CI 增「生成后 `git diff --exit-code`」步骤以证可复现；⑪ `AUD-005`：**必须由人类先出结论**——补一份解耦 ADR（承 **L29** 的「若缩小范围需新 ADR」）后把 `NFR-OBS-001/002`（**L64/L65**）改为 `READY` 并配 OTel 依赖（pom **L48-58** 区新增 `micrometer-tracing-bridge-otel` 等），或显式将其维持 `OPEN` 并在 AGENTS.md 与 exit catalog 中撤销一切「OBS 已达标」表述；⑫ `NFR-SEC-009` 阈值：由人类给出 `E5` 阈值定义并写入 `system_config`/配置项，CI **L60** 的数字改为引用该配置，消除「来源不可追溯的硬编码门禁」；⑬ 建立**门禁一致性守卫**（与 ㉙⑳、㉛⑮ 的守卫合并为同一套）：至少断言「AGENTS.md 规定的校验脚本路径 = CI 实际执行路径」「NFR 表引用的 `AUD-*`/`AC-*` ID 在对应治理/验收文档中存在」「NFR 中标为可达 `E4` 的条目所依赖的 Maven/npm 依赖确实存在于 pom/package.json」「`deploy/compose/scripts/*.sh` 与 `*.ps1` 文件名集合相等」四条，使本项 A–L 类缺陷在**未来由机器而非人工迭代发现**；⑭ 上述任一涉及 CI、pom、`application*.yml`、ArchUnit、`frontend/*`、契约或 NFR 表自身的整改，**须人类先出 `READY` + `implementationAuthorized: true` 的 Task Card**（DEC-009），AI 不得自批；本项亦不得以「浅脚本 PASS」冒充深门禁证据。

→ 本项与 ①—㉛ 一并不在本轮 AI 自决实施范围内。

### ㉜ Taxonomy 平台面（字典 itemCode + 标签 tagId 注册制）：七方差集（`REQ-TAX-001`/`REQ-TAG-001` ↔ `entity-invariants` ↔ OpenAPI ↔ `V6/V7/V25/V35` 迁移 ↔ `com.aihub.taxonomy` ↔ 资产消费方 ↔ 错误/验收目录 + 前端）

**权威口径**：本节全部行号均为「三重仲裁定版」——以**无 offset 的 `Grep -n`** 为唯一计数基准，无 offset 的全文 `Read` 与 PowerShell 无变量表达式 `(Get-Content -LiteralPath '…')[n-1]` 互验（REQ 文件、V6/V7/V25、契约、后端五组锚点各过一遍；批量仲裁脚本对 V6/V7 的索引映射自身出错，已以直接读取修正，见「取证偏差登记」第 4 条）。`Read(offset)` 偏 1 行在本项第 4 次复现（REQ 节头显示 L42、实为 **L43**）。本项**只做静态七方对账，不判定任何 `E4`/`E5` 运行时结论**（`REQ-TAX-001` **L51** / `REQ-TAG-001` **L86** 的 `minimumEvidenceLevel: E4` 一律 `unproven`）。REQ 权威文本位于 `docs/ai-spec/01-requirements/p0b-platform-services.md`（全库 Glob/检索证实**不存在**独立 taxonomy 需求文件，此前猜测落空）。

**七方差集取证表**（`✓` 七方一致 · `△` 部分满足/口径差 · `✗` 缺失或背离 · `—` 不适用）：

| 要求（定版行号） | 要求机制 | 仓库实况（实测行号） | 判定 |
| --- | --- | --- | --- |
| REQ-TAX **L59**（新建引用只接受 ACTIVE） | `validateItemCode` 拒非 ACTIVE | `DictionaryApplicationService` **L170-177**（**L171** findItem，非 ACTIVE 抛错）；消费点 `AssetApplicationService` **L516-521**（**L520** `dictionaryValidationPort.validateItemCode`）、**L503-514** 治理字段覆盖 license/framework/task/format/modality | **✓** |
| REQ-TAX **L60**（DISABLED 禁新引用保留回显） | 回显语义端口 + 读接口回显参数 | 回显端口 `isKnown` **L163-166**（独立缓存）**生产代码零调用**（仅测试引用）；`DictionaryController` **L77-81** 有 `includeDisabled`（默认 false），但契约 `listDictionaryItems`（**L1117/L1122** 参数区）**零声明该参数** | **✗**（两端各缺一半） |
| REQ-TAX **L61**（expected version + 递增字典 cache version）/ REQ-TAG **L97**（enable/disable/update 使用 rowVersion） | 乐观并发 + 缓存版本传播 | 字典 **L139** expectedVersion 比对、标签 `TagApplicationService` **L151-159**（**L159** CONCURRENT_MODIFICATION）✓；但标签实现用 `version` 列（V7 **L52**「对外暴露并用于 expectedVersion」），而 **L53** 明示 `row_version` 为**保留列**——REQ **L97** 字面与迁移/实现口径背离；「递增字典 cache version」（类型级版本传播）未逐行证实 | **△**（乐观锁 ✓，REQ L97 口径 ✗，cache version 传播 unproven） |
| REQ-TAX **L62/L63/L64**（双语 key；API 按 locale 返回 display metadata；缺失翻译回退 + 可观测信号） | i18n 消费链 | 数据面：V6 **L150-221**、V35 **L42-83** 双语齐全；**V25 五项 deprecation_reason 零消息 INSERT**（**L8-21**）。消费面：`I18nQueryService.messages` **L33-36** 存在但**生产零调用、无 REST 端点**（消费方仅 `TaxonomyIT` L64/L81、`TaxonomyLifecycleIT` L63/L178-179），全文**无缺失翻译日志/指标**；`DictionaryDtos.DictionaryItemView` **L31-36** 只透传 `itemCode+i18nKey`、**无 display 字段**；前端 `CreateAssetForm` **L221-223** `label: item.itemCode` 直显 code；locale 管道（`PrincipalContextFilter` **L44/L46/L140-146**）只进 MDC/correlation，不进业务响应；错误面仅回 `defaultI18nKey()`（`GlobalExceptionHandler` **L41/L62**、`SecurityResponseWriter` **L42**） | **✗**（整条 locale→display 链后端未交付，即缺陷 K） |
| REQ-TAX **L70**（`DICTIONARY_ITEM_CREATED/UPDATED/ENABLED/DISABLED` 四审计事件） | 契约 x-audit-event + 实现埋点 | 契约仅 **L1142** CREATED、**L1172** UPDATED（全契约 `x-audit-event` 恰 60 处，字典面缺 ENABLED/DISABLED 两值）；实现 `updateItem` **L149** status 变更后 **L155** 仍只记 `DICTIONARY_ITEM_UPDATED`——**契约+实现双缺**；对照标签面 **L1227/L1257/L1287/L1303** 四事件契约+实现三方齐全 | **✗**（即缺陷 A） |
| REQ-TAX **L56/L57-58**（稳定 code；MVP 八类） | V6 种子 | V6 **L98-110** 八类型在场（industry_tag **L105**、sensitivity_level **L107**）✓；**L115-145** 22 项全大写（**L122-143**）；`industry_tag` **零种子项**（REQ 只要求类型在场，定性登记） | **△**（F/G/H） |
| REQ-TAX **L65** + INV-DICT-006（`entity-invariants` **L109**，治理字典限管理员） | 权限映射 | `DictionaryController` **L82** READ、**L95/L124** MANAGE ↔ 契约 **L1099/L1122** `dictionary:read`、**L1141/L1171** `dictionary:manage` 逐端点对齐 | **✓** |
| REQ-TAG **L94**（写资产只提交 tagIds，拒绝 tags/name/code 自动创建） | 创建拒绝 + tagId 校验 | `AssetApplicationService` **L135/L136** rejectFreeFormOwners/Tags（仅 create 侧，**update 路径经三层结构证实无 tags/owners 字段，不构成缺口**，见偏差登记 5）；`resolveValidatedTagIds` **L523-532**（**L530** `resolveActiveTags`）；契约 `CreateAssetRequest.tagIds` **L4664-4667**、`UpdateAssetRequest` **L4724-4727**（`^tag_` pattern）；前端走 `apiUrl="/system/tags"`（**L206**） | **✓** |
| INV-TAG-002（**L114**「PLATFORM 标签 scopeId 必须为空」） | scopeId 归一化 | V7 **L16** `DEFAULT 'PLATFORM'` + **L29-33** CHECK 强制 `scope_id='PLATFORM'`；实现 `PLATFORM_SCOPE_ID` 哨兵 **L46/L179-182**；契约 `TagView.scopeId` **L4312** `type: [string, 'null']`——**不变量文本、迁移约束、实现、契约四方互异** | **✗**（三方背离，即缺陷 I） |
| REQ-TAG **L103/L104**（错误码四枚 + 审计四事件） | 契约 + ErrorCode + catalog | 契约 TAG_* 四审计 **L1227-L1303** ✓；错误码 `ErrorCode` **L128/L131/L137** ↔ `error-catalog` **L104-106/L112-114** ↔ REQ **L69/L103** 六码三向对齐 | **✓** |
| 缓存失效（REQ **L61** 失效语义的实现承载） | @CacheEvict 名单 ↔ @Cacheable 名单双向对账 | `@Cacheable("dictionaryValidation")` **L163** 与 **L81** dictionaryItems 均真实使用，但 **L92/L127** 两处 `@CacheEvict` 名单只含 `dictionaryItems, dictionaryTypes`——**dictionaryValidation 写后不清**，靠 `CaffeineCacheConfiguration` **L45** TTL 5min 兜底（**有界**陈旧窗口，非无限）；另 **L42/L46/L47** 注册的 `dictionaryCache/tagCache/permissionCache` 在 main 代码**零 @Cacheable 引用**（死注册），tag 模块**整体零缓存注解**（javadoc L27 声称有） | **✗**（B + L） |
| `AC-P0B-TAX-001`（p0b-exit-catalog **L78**：版本递增 + i18n zh/en + 审计）/ `AC-P0B-TAX-006`（**L83**：zh/en 切换无硬编码，`E4`） | 验收 | 静态链已证 A（审计双缺）、E（deprecation_reason 双语缺）、K（locale 链缺）→ 对应 `E4` **不可能以现有实现取证达标**；`page-catalog` **L30/L31** 管理页要求本项未逐一核前端页面实现 | **unproven→预期不达**（只登记，不裁决） |

**合规面**（七方一致，成文必列）：① `validateItemCode` 拒非 ACTIVE（REQ **L59** ✓）；② 字典 4 + 标签 5 端点权限契约↔实现逐端点对齐（REQ **L49/L84** ✓）；③ 写路径幂等（REQ **L50/L85** `REQUIRED for writes`，控制器/幂等面 ㉑㉚ 已证覆盖 ✓）；④ 创建路径自由 tags/owners 双拒（**L135-136**，REQ **L94** ✓）；⑤ update 路径**结构性**无自由 tags 入口（`UpdateAssetCommand` **L29-40**、后端 `UpdateAssetRequest` **L26-37**、契约 **L4695-4727** 三层皆无 `tags` 字段，`AssetApplicationService` **L198-211** 对 `Asset.updateMetadata`（`Asset.java` **L170-200**，**L183** PATCH-null 语义）**L204-205** 恒传 null ✓）；⑥ `TagValidationService` 三重拒绝（不存在/DISABLED/作用域不符，AC-004 ✓）；⑦ expectedVersion 乐观锁字典 **L139**、标签 **L151-159** ✓；⑧ 错误码↔error-catalog↔REQ 六码对齐 ✓；⑨ 标签四审计事件三方齐全 ✓；⑩ V35 `dataset_task/dataset_language` 17 项（**L22-38**）双语 i18n（**L42-83**）齐全 ✓；⑪ V6 八类型自身双语 i18n（含 `dict.industry_tag` **L162-163**）齐全 ✓。

**缺陷面**（A–M，全部 file:line 实证；定性修正见「限定更正」）：

- **A（审计双缺）** `DICTIONARY_ITEM_ENABLED/DISABLED` 契约（仅 **L1142/L1172**）与实现（仅 **L118/L155**）双双缺失，REQ **L70** 要求四事件；标签面齐全恰证明该要求可实现——字典面系遗漏而非设计。
- **B（有界陈旧）** `dictionaryValidation` 缓存（**L163**）不在 **L92/L127** evict 名单：停用项在 ≤5min 窗口内仍可被 `validateItemCode` 放行新引用（REQ **L59/L60** 的时间窗违背）；`isKnown`（**L163-166** 同缓存）生产零调用（C），实际暴露面收窄。
- **C（回显端口无消费）** REQ **L60**「保留回显」的端口 `isKnown` 无任何生产调用方；资产侧 legacy 回显走 `AssetCard`（J），字典停用项回显仅靠 `includeDisabled` 参数（D）间接承载。
- **D（契约落后实现）** `includeDisabled`（后端 **L77-81**，默认 false）在契约 `listDictionaryItems`（**L1117**）**零声明**——按契约生成的客户端永远看不到停用项，与 REQ **L60** 回显要求方向相反的漂移。
- **E（双语缺项）** `deprecation_reason` 五项（V25 **L15-19**，全大写）**无任何 zh/en 消息**（V25 全文无 `system_i18n_message` INSERT；全库 `dict\.deprecation_reason\.` 零命中）→ 违反 REQ **L62** 与 INV-DICT-003（**L106**）；弃用弹窗展示 itemCode 直显。
- **F（空类型）** `industry_tag` 类型有定义（V6 **L105**）+ 类型双语（**L162-163**）但**零字典项**（V6 **L122-143** 无该 dict_code，后续迁移无补种）——REQ **L57-58** 字面只要求类型在场，故登记为「实现完备性存疑」而非违规。
- **G（校验空洞）** `sensitivity_level` 有 4 项种子（V6 **L140-143**）+ 双语（**L166-167/L212-219**）+ 前端选择器，但 `AssetApplicationService` 无对应 DICT 常量（现有常量 **L72/L77** 等五条）、`validateGovernanceFields` **L503-514** 不覆盖 sensitivity；dataset 多值 `taskCodes/formatCodes/modalityCodes/languageCodes` 同样只在检索侧出现（`AssetSearchDao` **L195-199**，实际路径 `asset\infrastructure\`），写入侧无字典校验 → 违反 AGENTS.md「Governed fields reference active dictionary itemCode」与 INV-DICT 系要求。
- **H（大小写三口径）** 字典 itemCode：V6 种子全大写（**L122-143**）vs V35 全小写（**L22-38**）vs `ITEM_CODE_PATTERN` **L46** `[a-zA-Z0-9]` 双语放行；标签 `TAG_CODE_PATTERN` **L44** 仅小写。同一 `system_dict_item` 表内两种风格共存，下游按 code 精确匹配的集成（检索 **L195-199**、前端 value）无归一化守卫。
- **I（scopeId 四方互异）** INV-TAG-002（**L114**「必须为空」）vs V7 CHECK（**L16/L29-33** 强制 `'PLATFORM'`）vs 实现哨兵（**L46/L179-182**）vs 契约 `TagView.scopeId` 允许 null（**L4312**）——不变量文档若被当真则实现全部违规，若以实现为准则 INV 文本失实；`TagDtos.TagView.from` **L27-28** 还把 `name` 映射为 `displayName`（契约 **L4308** required）——属 V7 **L45** 已文档化的「兜底展示」设计，不计缺陷但登记口径。
- **J（legacy 渲染通道）** `AssetCard` **L47**（README「- 标签: …」）/**L62**（YAML `tags:`）仍把 `asset.tags()` 自由字符串渲染进资产卡导出——INV-TAG-007（**L119**「无自由字符串回退」）的适用性取决于「历史回显」还是「新回退」定性，本项**不裁决**（见限定更正），但契约 `AssetView.tags` **L4760-4762**「只读回显」声明与 README/YAML 导出面的「再分发」效果存在张力。
- **K（i18n 后端断链）** REQ **L63/L64** 两整行未交付：无 API 端点消费 `I18nQueryService`（生产零调用、类注释 **L16** 自认「前端集成不在本任务范围」）、无缺失翻译可观测信号（全文无日志/指标）、`DictionaryItemView` **L31-36** 无 display 字段、前端 `label: item.itemCode`（**L222**）——「按 locale 返回 display metadata」链仅有 DB 数据 + 内部函数两端，中段全缺。
- **L（缓存注册表失真）** `CaffeineCacheConfiguration` **L42/L46/L47** 三个缓存名（dictionaryCache/tagCache/permissionCache）在 main 代码**零注解引用**（全 main 检索 6 hits 全在本文件 L26-28 javadoc + L42/L46/L47），javadoc **L27** 声称标签有缓存而 tag 模块 `@Cacheable|@CacheEvict` **零命中**——注册表与注解双向不对账，误导后续维护者对失效语义的判断。
- **M（契约声明无实现背书）** 契约 `UpdateAssetRequest.owners` **L4718-4723** 声明 deprecated「非空写入将被拒绝」，但后端 record **无 owners 字段**（**L26-37**）+ `additionalProperties: false`（**L4698**）——「拒绝」实际由 Jackson 未知属性机制偶然达成（**未实测运行行为与错误码**，登记待实测）；连带 REQ **L97** rowVersion 字面口径差（见表格第 3 行）。

**未实测清单**（一律标注 unproven，不得据本节宣称达标）：① Jackson 拒 `owners`/`tags` 未知属性的运行态错误码映射（M）；② Caffeine TTL/evict 的运行期命中与失效行为（B/L 仅静态名单对账）；③ 类型级 cache version 是否随项变更自增（REQ **L61** 后半）；④ i18n 消息表的任何运行期消费（K 的断链判定仅证「无调用方」，不排除未来消费端）；⑤ `AC-P0B-TAX-001/006`（**L78/L83**）及全部 taxonomy `E4` 结论；⑥ 前端管理页（page-catalog **L30/L31**）实现完备性。

**取证偏差登记**：① `Read(offset)` 偏 1 行**第 4 次复现**（REQ 节头 L42→实 L43），行为清单全部 +1 修正后以 PowerShell 复核定版；② 路径猜错 4 处：`com\aihub\common`（不存在→实际 `shared\caching\CaffeineCacheConfiguration.java`）、`TagView.java`（→`TagDtos.java` 内部 record）、`asset\infrastructure\web\AssetRequestMapper`（→`asset\api\`）、`AssetSearchDao`（→`asset\infrastructure\`，批量脚本按 MISSING FILE 暴露）；③ PowerShell 控制台中文输出为 GBK mojibake，行序结构可用、关键行以 Grep 原文复核；④ 批量仲裁脚本对 V6/V7 的扁平索引映射出错（如把 V6 L140 内容显示为注释行，实测为 `sensitivity_level PUBLIC` 种子），已以无 offset 直接读取全量修正——再次确立「无 offset `Grep -n` 唯一计数基准」纪律；⑤ **撤销**前段误判「update 路径漏 rejectFreeFormTags」（三层结构证实无入口，见合规面 ⑤）；⑥ **降级**缺陷 B：「无限陈旧」→「≤5min 有界窗口」（`CaffeineCacheConfiguration` **L45/L61-66** TTL 实测）。

**限定更正**：① 缺陷 J 不裁决「历史回显 vs 自由回退」——INV-TAG-007 语义收窄与否须人类定夺；② I 行的 `displayName←name` 映射（V7 **L45** 注释自认兜底设计）**不计入**缺陷，只登记 INV/契约/实现三方对 scopeId 的互异文本须裁决；③ F（industry_tag 零项）**不判违规**，REQ **L57-58** 字面只列类型；④ 全部 `E4` 相关行（含 unproven→预期不达行）**只陈述静态断链事实**，不构成验收失败裁决；⑤ 本项不修改任何禁改面文件，A/D/E/I/K/M 六处规范↔实现冲突**只登记不裁决**。

**整改待批（AI 不自决，DEC-009 须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① A：契约补 `x-audit-event: DICTIONARY_ITEM_ENABLED/DISABLED` 两值 + `updateItem` **L149-155** status 变迁按标签面 **L1227-L1303** 模式发四事件（含 error-catalog/exit-catalog 同步）；② B：`@CacheEvict` **L92/L127** 名单并入 `dictionaryValidation`（或删 **L163** 缓存注解、以显式版本号承载 REQ **L61** cache version 语义，二者取一须与 REQ 措辞对账）；③ C/D：契约 `listDictionaryItems` 补 `includeDisabled` 参数声明，并给回显消费方（资产投影/前端）接 `isKnown` 或统一走该参数——两端口二选一收口；④ E：新增前向 Flyway 迁移补 `deprecation_reason` 五项 zh/en 消息（V35 **L42-83** 可作模板）；⑤ F：人类裁决 industry_tag 是否补种或撤类型；⑥ G：`validateGovernanceFields` 纳入 sensitivity 与 dataset 多值码集（性能路径注意批量校验一次查库，勿逐项往返）；⑦ H：出 itemCode 大小写规范 ADR 后统一种子与 pattern（V6 不可改，需前向迁移数据归一）；⑧ I：按人类裁决修订 INV-TAG-002 文本或迁移/实现/契约归一（推荐改不变量文本为「PLATFORM 标签 scope_id 归一化为字面量 PLATFORM」，因其已有 CHECK 背书）；⑨ K：i18n 消费链按 REQ **L63/L64** 补齐（端点 + display 字段 + 缺失计数指标）或以 ADR 把 REQ-TAX 该两行改判后续阶段——**不得维持「DB 有数据、代码有函数、无人消费」的现状**；⑩ L：删除三个死注册或补注解，使注册表↔注解双向对账可被守卫断言（并入 ㉛⑬ 门禁一致性守卫）；⑪ M：后端 record 补 owners 显式拒绝校验 + 契约错误响应声明，或契约删除 owners 字段并入 breaking 流程；⑫ J：legacy tags 导出口径由人类会同 INV-TAG-007 裁决。

→ 本项与 ①—㉜ 一并不在本轮 AI 自决实施范围内。

### ㉝ 运行配置面（REQ-CFG-001 类型化非敏感运行配置）：七方差集（`REQ-CFG-001` ↔ `INV-CFG-001..006` ↔ OpenAPI ↔ `V8` 迁移 ↔ `com.aihub.configuration` ↔ 配置消费方 ↔ 验收目录/Task/Evidence + 前端）

**权威口径**：REQ-CFG-001 位于 `docs/ai-spec/01-requirements/p0b-platform-services.md` **L112-144**（yaml **L115** READY、**L116** MUST、**L118** permission `system:configure`、**L119** idempotency REQUIRED、**L120** E4；行为 **L125-133**；错误与审计 **L137-138**；追踪 **L142-144**）。不变量 `entity-invariants.md` **L123-128**。契约：endpoint **L1311-1330**（list）/**L1332-1358**（PUT）+ schemas **L4320-4341** + 全局参数 `IdempotencyKey` **L3681-3689**（required: true）。迁移 V8 全文 71 行（表 **L10-31**、注释 **L33-49**、十项种子 **L55-71**）。实现 12 文件（`configuration/{api,application,domain,infrastructure}`）。验收 `p0b-exit-catalog` **L89-91**（三条全 E4）；Task `TASK-P0BR-021`（READY/authorized、E4）；EVD `EVD-P0BR021-001`（`status: PASS` 但 `evidenceLevel: E2` **L43**、`scenarios: []` **L8**、`accepted: false` **L74**）。本节全部行号以无 offset `Grep -n` / 全文 `Read` 为唯一基准。**E4 一律 unproven**；本节为静态取证，不宣称任何运行态结论。

| 要求（REQ/INV 锚点） | 口径 | 实现证据 | 判定 |
| --- | --- | --- | --- |
| REQ **L125**（Catalog 由**代码**声明 key/type/default/validator/scope/hotReloadable/sensitivity） | 代码级 Catalog 对象 | 无代码 Catalog：声明实体是 V8 **L55-71** DB 种子行（含 validator/description，但**无 sensitivity 列**，V8 **L10-31** 全列核对）；`ConfigKeys` **L20-38** 十常量仅存常量、无元数据绑定 | **✗**（解释冲突登记：DB 表承载「Catalog」与「代码声明」字面背离，且 sensitivity 维度整体缺失） |
| REQ **L126**（API 只能管理 Catalog 允许的运行时 key） | 白名单收口 | `updateConfiguration` **L73-75** `findByKey` 缺失即 `CONFIG_NOT_FOUND`；无创建/删除端点（契约仅 list+PUT）⇒ 运行期 key 集 = V8 种子集，只增不改 | **✓**（以 DB 表为白名单的弱实现） |
| REQ **L127**（六类型确定性解析） | `validateAndSerialize` | **L98-134** 六分支齐全；但 `parseInteger` **L159-164** 对 `Number` 直接 `intValue()` **截断**（JSON 数值 4294967296 → 0 通过）；JSON 分支 **L122** `null→"null"` 字面量入 JSON 列 | **△**（K：窄化 + JSON null 语义） |
| REQ **L128**（类型 + **范围** + 枚举/Schema + **作用域**校验） | 四重校验 | 仅类型（上表）；validator 列值（V8 **L60-69** `'>0'`/`'60..3600'`/`'300..86400'` 等）**在 main 代码零执行**（`validateAndSerialize` 不读 `SystemConfig.validator()`，全文件核对）；`scope_type/scope_id` 零校验零过滤（仅透传 **L79-80**） | **✗**（B/M：范围/Schema/作用域三缺） |
| REQ **L129**（key **或 value** 命中 Secret 规则拒绝，「不能只按 key 名判断」） | 双向扫描 | `rejectSecretKey` **L89-95** 只匹配 configKey（`SECRET_PATTERN` **L40**）；`command.value()` 全程无 Secret 扫描（**L81** 直接进类型校验） | **✗**（C：REQ 字面「不能只按 key 名」被直接违反；exit-catalog **L90** 要求「Key/Value」双侧） |
| REQ **L130** + INV-CFG-004（expected version；old/new 脱敏摘要审计） | 乐观锁 + 审计摘要 | 版本比对 **L76-80** + JDBC 条件更新 **L54-68**（`WHERE version=:expectedVersion`，affected==0 抛 `CONCURRENT_MODIFICATION`）**双重防线** ✓；审计 attributes 仅 `version+hotReloadable`（**L83-84**），**无 old/new 值摘要** | **△**（乐观锁 ✓，F：摘要缺） |
| REQ **L131** + INV-CFG-005（hotReloadable=true 原子刷新读模型；false 明确返回 restartRequired） | 生效语义 | 响应仅回 `hotReloadable` 布尔（`ConfigView` **L24-25**），无 `restartRequired` 信号（契约 **L4320-4330** schema 亦无该字段）；「原子刷新读模型」无从谈起——见下行双轨断裂 | **✗**（E：两半皆缺） |
| REQ **L131** 前置 + INV-CFG-001「代码通过类型化对象读取」（**L123**） | 读端消费链 | **核心断裂**：API 写 `system_config` 表，但全部十 key 的运行期消费读 **Spring Environment**——`McpToolCatalog` **L62**、`AgentController` **L90** `@Value("${mcp.writeTools.enabled:false}")`；`application.yml` 与 `deploy/` 全目录对十 key（`ConfigKeys` **L20-38**）**零定义**（按十 key 全称精确检索：resources 目录命中仅 V8 迁移——种子行 **L60-69** 与字段注释示例 **L36**，非任何属性定义；`deploy/` 零命中——其中 `presignedUrl` 3 处命中系 `p4-onboarding.sh` **L195-200** 对响应体字符串的测试断言，与配置定义无关）；`PropertySource`/`EnvironmentPostProcessor` 桥接 **main 代码零存在**（Grep 全 main 无命中）；`PlatformConfigService`（133 行、类型安全读取器）与 `ConfigKeys` 常量在 **main 代码零注入零消费**（引用仅 javadoc：`ConfigValueType` **L12**、`ConfigKeys` **L11**，及测试 `ConfigurationIT` **L74**/`PlatformConfigServiceTest`） | **✗**（A：经 API 修改任何配置对运行行为端到端无效，「配置管理」实为对一张无人读的表做 CRUD） |
| REQ **L132** + INV-CFG-006（安全/发布策略变更二次确认写审计） | confirmation 消费 | 请求 `confirmation` 字段三层齐备（契约 **L4339-4341**、DTO `UpdateConfigurationRequest` **L19**/`UpdateConfigCommand` **L33**、前端表单 `ConfigurationsPage` **L172-178**），但应用服务 **L68-86** 对 `command.confirmation()` **零读取**——任何值（含空）都直接通过；`security.agentToken.maxTtlSeconds`/`version.publish.requireApproval` 等安全项无确认门槛 | **✗**（D：UI/契约有壳、逻辑为空） |
| REQ **L133**（读取失败安全默认或阻止启动，不能静默宽松） | fail 策略 | 无启动期校验（任何组件不遍历 system_config 对账）；`typedValue` **L154-156** 吞 `NumberFormatException` 回退返回原始文本（静默宽松）；`getInt/getLong` 的**无 fallback 重载**在 key 缺失时抛 `InternalException` **L121-124**（500），**带 fallback 重载** **L63-73/L88-98** 静默回退——两径皆非「安全默认或阻止启动」；且因 A 行断裂该服务本就无生产调用方 | **✗**（J） |
| REQ **L137**（四错误码） + **L138**（`SYSTEM_CONFIGURATION_UPDATED`，不记 Secret/完整高敏 JSON） | 错误/审计三方 | error-catalog **L120-122**（NOT_FOUND 404/SECRET_FORBIDDEN 400/VALUE_INVALID 400）+ **L19** CONCURRENT_MODIFICATION 通用 ✓；审计事件契约 **L1340** ↔ 实现 **L83** ↔ 适配器 `ConfigurationAuditAdapter` **L36-56**（转发权威 AuditService、fail-closed 于 **L52-55** catch-log）三方齐全 ✓；attributes 不含值本身（仅 version/hotReloadable）**合规于「不记 Secret」**、但同时坐实 F（无摘要）；**契约 PUT `x-error-codes` L1338 漏 `CONCURRENT_MODIFICATION`**（实现 **L77**/**L65** 两处会抛 409） | **△**（H：契约错误声明不全） |
| REQ **L119**（idempotency REQUIRED） | 幂等强制性 | **四方互异**：REQ REQUIRED ↔ 契约全局参数 `IdempotencyKey` required:true（**L3684**）**未被 PUT 引用**（endpoint parameters **L1341-1342** 仅 configKey）↔ 后端 `required=false`（Controller **L73**）+ `buildKey` 空值返回 null 跳过幂等（`IdempotencySupport` **L34-36**）↔ 前端 `updateConfiguration`（api.ts **L271-276**）**不注入** `idempotent()`（同文件 **L70-72** 定义、其他写操作使用）；测试 `idm002_nullKeyExecutesWithoutIdempotency`（LifecycleIT **L163**）把「无键直放」固化为预期行为 | **✗**（G） |
| AC-P0B-CFG-001/002/003（**L89-91**，全 E4） | 验收 | 单测/IT 在场（`ConfigurationLifecycleIT` **L74/L94/L119** cfg001-003 命名直接对应场景），但 EVD 记 **E2**（**L43**）、`scenarios: []`（**L8**）、`accepted: false`（**L74**）、`notProven: E4 Compose`（**L50**）；`status: PASS`（**L3**）与 requiredEvidenceLevel E4 口径失真 | **unproven→预期不达**（L：只登记不裁决） |

**合规面**：① 权限三方对齐（REQ **L118** ↔ `Permissions.SYSTEM_CONFIGURE` **L83**（**L93** 高风险 Agent 拒绝集）↔ 契约 **L1318/L1339** ↔ Controller **L63/L75** fail-closed）；② 乐观锁双防线（应用层预比对 + JDBC 条件更新）；③ 类型集合六值四方一致（REQ **L127** ↔ `ConfigValueType` **L14-28** ↔ V8 CHECK **L27-28** ↔ 契约 enum **L4327**）；④ Secret 键名拒绝链完整（模式 **L40** → **L89-95** → 400 映射，ControllerTest **L127** 覆盖）；⑤ 无创建/删除端点、种子幂等（V8 **L71** `ON CONFLICT DO NOTHING`）；⑥ 审计桥接复用权威 `AuditService`（适配器 **L39-51**，无本地替身）；⑦ 请求 DTO 与契约字段名逐一对齐（**L19** ↔ **L4332-4341**）；⑧ 前端页面在场且与 PAGE-ADM-012（page-catalog **L32**）路由/权限一致（routes.tsx **L85**、navigation.ts **L45**）。

**缺陷面**（编号本节内自 A–H、J–M，共 12 条；不使用 I 以免与罗马数字/代词混淆）：**A 双轨断裂**（上表核心行——写库与读取轨道从未接通，`mcp.writeTools.enabled` 恒为 @Value 默认 false，工具面注释 **L40**「写工具受开关控制」具文）；**B** validator 列零执行；**C** Secret 只扫 key（违反 REQ **L129** 字面）；**D** confirmation 零消费；**E** 无 restartRequired 信号；**F** 审计无 old/new 脱敏摘要；**G** 幂等四方互异；**H** 契约漏 `CONCURRENT_MODIFICATION`；**J** 读失败静默宽松（typedValue **L154-156** 吞异常）；**K** INTEGER 窄化截断；**L** E2 证据标 PASS（治理面，沿 ㉛ NFR 门禁同型问题，互证为**系统性口径失真**而非孤例）；**M** 作用域维度零校验（scope 列存在但读写两侧皆不判）。REQ **L125**「代码声明 Catalog」与 sensitivity 列缺失登记为**解释冲突**（见限定）。

**未实测清单**（unproven）：① `@Value` 键是否存在任何运行期注入路径（env/compose/启动参数——静态检索 deploy 目录零命中，不排除部署侧手注）；② INTEGER 窄化、JSON null、confirmation 空值等边界的运行态响应；③ `ConfigurationLifecycleIT` 各场景实际执行结果（未运行 `mvnw verify`，本轮无环境）；④ 前端编辑流对不可热更项的提示完备性；⑤ 全部 `AC-P0B-CFG-*` 的 E4 结论；⑥ 审计事件落库后的脱敏抽检。

**取证偏差登记**：本节行号全部由无 offset `Grep -n` 与全文 `Read` 直接钉定，未使用批量映射脚本，无 ㉜ 段型偏差；`ConfigKeys`/`PlatformConfigService` 零消费结论的检索式为 `PlatformConfigService|ConfigKeys\.` 全 main（命中仅 javadoc/自身/常量定义），若存在反射或字符串拼接消费则本判定失效——已列入未实测①关联风险。

**限定与更正**：① REQ **L125**「由代码声明」与 V8 DB 种子实现的口径差、及 sensitivity 元数据整体缺失，登记为规范↔实现**解释冲突**，不裁决孰是；② 缺陷 A 的「端到端无效」以静态引用链为据，运行态需未实测①排除注入路径后方可坐实——判定暂记「静态层面断裂」；③ EVD `status: PASS` 是否构成违规以 `requiredEvidenceLevel: E4`（TASK **L43**）与 `evidenceLevel: E2`（EVD **L43**）字面对撞为据登记，裁决权归验证权威（人类验收方）；④ 本域与 ㉑-㉜ 已覆盖面零重叠（configuration 模块此前未做任何一节取证）。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① A（最高优先）：接通双轨——新增 `EnvironmentPostProcessor`/自定义 `PropertySource` 使 `system_config` 进入 Spring Environment（或强制全部消费方改走 `PlatformConfigService`），并为 `mcp.writeTools.enabled` 等在 `application.yml` 显式声明回退链；补「改表→消费方观测到新值」的 E4 场景；② B：`validateAndSerialize` 消费 `validator` 列（范围/正则解析器入代码 Catalog），M：scope 校验或删列（二选一须对账 REQ **L128**）；③ C：值侧 Secret 扫描（对序列化后的存储文本过同一模式 + 高敏 key 的 JSON 深度扫描），与 AC-P0B-CFG-002（**L90**）「Key/Value」对齐；④ D：安全/发布项白名单 + `confirmation` 缺失/错误时拒绝（新错误码或复用 `CONFIG_VALUE_INVALID` 须人类定）；⑤ E：`hotReloadable=false` 响应补 `restartRequired` 信号（契约 breaking 评估走流程）；⑥ F：审计 attributes 补 old/new 脱敏摘要（哈希/掩码，勿存原值）；⑦ G：契约 PUT 引用全局 `IdempotencyKey` 参数 + 后端 `required=true` + 前端 `updateConfiguration` 注入 `idempotent()` 三处同步（REQ **L119** 字面收口）；⑧ H：契约 `x-error-codes` **L1338** 补 `CONCURRENT_MODIFICATION`；⑨ J：`typedValue` 解析失败改为记 WARN + 明确回退语义（消灭静默吞异常）；⑩ K：INTEGER 分支拒绝窄化（`intValueExact`）；⑪ L：EVD-P0BR021-001 重出具（补 scenarioId 映射、如实标 E2/notProven，或补跑 E4）——并入 ㉛⑬ 证据门禁一致性守卫；⑫ **L125 解释冲突 + sensitivity 缺失**需 ADR 裁决：代码 Catalog 化为权威 + DB 行为投影，或修 REQ 措辞承认 DB 权威。

→ 本项与 ①—㉝ 一并不在本轮 AI 自决实施范围内。

### ㉞ REQ-IDM-001 写接口幂等专项：**契约↔实现↔REQ 三方对账矩阵与增量取证**（承 ㉙，不重复计权）

**定位声明（先划界）**：幂等域的七层差集已由 **㉙「持久化 Job / 幂等 / 调度平台面」**（本文 **L1460-1574**）系统覆盖——缺陷面 A（IN_PROGRESS 恒不写入/AC-P0B-IDM-003 结构性不可满足）、B（12 处 null 指纹豁免）、C（`IdempotencyResult` 系统性丢弃 + `X-Idempotency-Replayed` 零命中）、D（契约 digest vs 实现 requestDigest + required/minLength 双约束缺失）、E（查询跨主体枚举）、K 段（前端 api.ts L284-290 重试换键）、L（EVD-P0BR-023-001 **L141** V27↔物理 V41 引用错例）均已在 ㉙ 计权。本节**只登记 ㉙ 未覆盖的增量**，凡引用 ㉙ 已计权项不重复计入差集数量。**立项时序失误自登**：本节取证进行了 6 轮后才经 AC-P0B-IDM-003 文档检索发现 ㉙ 同域专节存在，违反「落盘新节前先 `^### ` 枚举查重」的既定纪律（㉝ 收尾时已确立该纪律但未在本节立项时执行），详见取证偏差登记。

**权威口径**：REQ-IDM-001 位于 `docs/ai-spec/01-requirements/p0b-platform-services.md` **L146-176**（适用范围 **L157**：创建类 + 状态迁移 + 版本提交/发布 + 上传 complete + Agent/MCP 写工具；第⑨条 **L166** 要求前端对可重试写操作主动携带键）。契约全局参数 `IdempotencyKey` **L3681-3689**（required: true，㉙ D 已计权）。本节行号基准：无 offset `Grep -n` / 全文 `Read`；**E4 一律 unproven**；静态取证，不宣称运行态结论。

**三方对账矩阵（本表为节骨干，全部逐行实定）**：

- 契约侧：全称精确式 `\$ref: '#/components/parameters/IdempotencyKey'` 复验恰 **17 处**：**L277** createUser、**L360** enableUser、**L377** disableUser、**L434** createAgent、**L536** createOrganization、**L587** addOrganizationMember、**L655** createProject、**L875** createRole、**L991** createRoleBinding、**L1056** createResourceAcl、**L1144** createDictionaryItem、**L1229** createTag、**L1399** retryJob、**L1416** cancelJob、**L1669** createAsset、**L1917** createAssetAccess、**L1963** deleteAssetAccess。
- 实现侧：全称精确式 `RequestHeader\(value = "Idempotency-Key"` 复验恰 **41 处**（全部 `required = false`，㉙ D 已计权）。
- **对齐 17 处**：上述 17 契约 $ref 与后端 17 处 header 逐一对上（SystemUserController **L86/L142/L159**、SystemAgentController **L77**、OrganizationController **L79/L115**、ProjectController **L75**、RoleController **L84**、RoleBindingController **L72**、ResourceAclController **L72**、DictionaryController **L93**、TagController **L84**、JobController **L73/L91**、AssetController **L74**、AssetAccessController **L78/L106**）——operationId 存在性无悬空。**✓ 合规**。
- **差集一（实现有 header、契约无 $ref）：24 处**（41−17=24 逐点闭合）：updateUser **L119**、resetUserPassword **L176**、enableAgent **L102**、disableAgent **L119**、updateAgentToolAllowlist **L136**、AssetController updateAsset **L149**/deleteAsset **L168**、deleteRoleBinding **L97**、deleteResourceAcl **L98**、updateConfiguration **L73**（㉝ G 已计权，此处只入矩阵）、createDraftVersion（VersionController **L68**）、updateTag **L111**/enableTag **L138**/disableTag **L158**、updateDictionaryItem **L122**、removeOrganizationMember **L143**、TeamController **L80/L118/L157/L186**（createTeam/updateTeam/addTeamMember/removeTeamMember——契约 **L698/L749/L801/L830** 四端点均无 $ref，其中 **createTeam/updateTeam/addTeamMember/removeTeamMember 四个写 operation 连契约引用都没有幂等声明**）、McpController **L83**、AgentController **L172/L195/L220**（见增量三）。即：**契约声明面 ⊂ 实现声明面，24 个后端写端点接受该 header 但契约未声明**——客户端按契约生成代码将不会携带键，幂等在这些端点上对契约合规调用方不可达。
- **差集二（REQ L157 范围内、契约与实现双重零保护）：7 个端点**（㉙ 未点名此集合）：UploadController **L44**（POST `/assets/{assetId}/versions/{versionId}/upload-sessions`，全文件对 `Idempotency-Key|idempotencyService` 零命中）、**L77**（`/upload-sessions/{sessionId}/complete`——REQ **L157**「上传 complete」字面在范围）、**L94**（:cancel）；ReviewController **L30**（POST `/versions/{versionId}/publish-requests`——「版本提交/发布」字面在范围）、**L39**（`/publish-requests/{requestId}/decisions`）、**L49**（:deprecate）、**L58**（:archive）。契约对应端点 **createUploadSession L3413 / completeUploadSession L3524 / cancelUploadSession L3574 / submitPublishRequest L3113 / submitDecision L3141 / deprecateVersion L3174 / archiveVersion L3202** 亦均无 $ref——**双向缺失**。加上 transitionVersion（契约 **L3056**，后端 VersionController **L100** 无 header）共 **8 个 REQ 范围写入口完全脱离幂等框架**。

**增量发现（㉙ 未覆盖）**：

- **N1 上传会话双入口一有一无**：Agent 侧 `POST /agent/assets/{assetId}/upload-sessions`（AgentController **L191**，header **L195**）与 Web 侧 `POST /assets/{assetId}/versions/{versionId}/upload-sessions`（UploadController **L44**，零 header）是同一业务动作的两个适配器入口，一个进幂等框架、一个不进——违反 AGENTS.md「REST/MCP/Worker 适配器必须复用同一应用服务与幂等逻辑」的适配器一致性基线（两入口共用 `UploadApplicationService`，但幂等装饰只在一侧）。
- **N2 MCP 带内强制与未声明参数通道**：McpController header 形参 **L83** required=false，但 **L174-178** 对 `toolDef.write()` 工具在键缺失/空白时返回 `toolError("Idempotency key required for write tools")`——写工具实际**强制**带键（与 41 处 HTTP 端点的「全员可空」形成同域双语义）；键来源 **L202-208** `resolveIdempotencyKey`：**JSON-RPC params 里的 `_idempotencyKey` 优先于 HTTP header**——该参数通道未在契约 **L3649** mcpJsonRpc 或 MCP 工具 schema 中声明，属带外协议面。MCP 幂等键统一折叠为 `POST /mcp/tools/call/{toolName}`（**L179-181**），指纹取 `sha256Digest(arguments)`（**L181**）。
- **N3 EVD-P0BR-056-001 证据链版本号漂移（㉙ L1553 V27↔V41 同族第二例）**：该 evidence 头注 **L4-5**、`dirtyPaths` **L36**（`V26__idempotency_scope.sql`）、执行命令 **L57-58**（flyway `version='26'`）、proven 叙述 **L92-94**（「迁移实测 version=26 success=t」）、DB 输出 **L148-149**（`26|idempotency scope|t`）五处一致声称 V26；磁盘现实：`V26__publish_request_changes_requested.sql`，作用域迁移物理文件 `V40__idempotency_scope.sql`（两 Glob 实定）。形态=取证后迁移重编号、evidence 工件未同步——**已应用迁移与证据目录的漂移是系统性的（两例），且 evidence 属禁改面，只登记**。
- **N4 契约内部 x-error-codes 与 $ref 不齐**：`IDEMPOTENCY_KEY_CONFLICT` 在全契约仅 **7 处** `x-error-codes` 声明（L273/L430/L532/L583/L651/L871/L1665）；17 个 $ref 端点中 **10 个未声明**该错误码（enableUser/disableUser/createRoleBinding/createResourceAcl/createDictionaryItem/createTag/retryJob/cancelJob/createAssetAccess/deleteAssetAccess）。createAsset（**L1665 + L1669**）是唯一「错误码+参数」双齐的端点。声明了 409 冲突却没引用键参数、或引用了参数却没声明 409，客户端契约消费两侧都不完整。
- **N5 测试面把豁免固化为期望（引用 ㉙，补用例清单）**：`IdempotencyServiceTest` 六用例全称复验：**L32** shouldReplayFirstResultWhenKeyExists、**L54** shouldExecuteAndSaveWhenKeyMissing、**L71** shouldThrowConflictWhenSameKeyDifferentFingerprint、**L88** shouldReplayWhenSameKeyAndSameFingerprint、**L104** shouldReplayWhenExistingFingerprintNull、**L119** shouldExecuteWithoutIdempotencyWhenKeyNull——后两者（㉙ B/C 已计权）以测试资产形式锁定 null 键直通与 null 指纹重放语义。测试全集另含 IdempotencyControllerTest、IdempotencySingleDecisionPointTest、JdbcIdempotencyStoreIT（**L117-119** EVD-056 自记 IT 全 Skipped：Testcontainers 连不上 Docker——「A test skipped because Docker is unavailable is not a passing integration test」口径下该 IT 对 E4 无贡献）。

**合规面（增量部分）**：① 17 契约 $ref 与后端 header 逐一对号、operationId 无悬空；② MCP 写工具带内强制（**L174-178**）是全仓唯一「缺键即拒」的幂等执行点，方向与 REQ 一致；③ MCP 与 REST 复用同一 `IdempotencyService.execute`（**L182-185**），未造本地替身（架构基线合规）；④ 契约 createAsset 端点参数+错误码双齐备，可作为差集一/差集二共 32 个端点的整改模板。

**未实测清单（unproven）**：① 24 处差集端点在带键调用时后端是否真正走 execute（多数在 Controller 体内消费 header——本矩阵只判「契约是否声明」，未逐行复核 24 处的 service 调用链）；② MCP `_idempotencyKey` 通道的运行态行为与安全评估；③ flyway 历史表中 V26/V40 的实际 success 状态（无 DB 环境，N3 以磁盘文件名+evidence 文本对撞为准）；④ 任何 E4 结论。

**取证偏差登记**：① **㉙ 晚发现**（立项未先枚举标题查重，6 轮后才对齐）——已导致本节重写定位为增量节，浪费取证轮次，纪律固化为「新节立项第一动作 = `^### ` 枚举 + 域关键词全文检索查重」；② 路径臆测 3 次（McpController 误猜 `agent\interfaces\rest`、EVD 目录误猜 `05-verification\evidence`、两 Controller 误猜 `auth\api`）——修复为 Glob 先行；③ Grep 模式误写 1 次（`name =` vs 实际 `value =`，0 命中后全称修正）；④ 本节全部计数（17/41/24/7/10/6）均以全称精确模式复验后落盘。

**限定与更正**：① 本节不修改 ㉙ 任何结论，差集数量不与 ㉙ 相加；② N1 的「适配器一致性」引 AGENTS.md 基线条文，属规范↔实现冲突登记，不裁决；③ N3/N4 涉及 evidence 与契约文件均为禁改面，仅登记上报；④ 8 个双零入口中 transitionVersion 的 REQ L157 归属（「状态迁移」）为语义归入，非字面点名，已如实区分表述。

**整改待批（AI 不自决）**：① 差集一 24 端点补契约 $ref（与 ㉙ 整改① 契约 digest 统一同批，走契约 breaking 评估流程）；② 差集二 8 入口按 N1 模板补幂等装饰（上传 complete 优先，REQ **L157** 字面点名）；③ N2 `_idempotencyKey` 通道补入 MCP 契约声明或移除；④ N3 evidence V26 叙述漂移上报证据治理方（禁改面，AI 不动）；⑤ N4 十个端点补 `IDEMPOTENCY_KEY_CONFLICT` 错误码声明；⑥ 17/41 对账矩阵固化为 CI 契约检查（挂 TASK-GOV-008）。

→ 本项与 ①—㉞ 一并不在本轮 AI 自决实施范围内。

### ㉟ Web 上传会话面（REQ-UPL-001..004）：**REQ ↔ 契约 ↔ DB ↔ 实现 ↔ 领域 ↔ Worker ↔ 存储适配器 ↔ 前端 ↔ 证据 九方差集**

**定位声明（先划界）**：上传 complete 的幂等缺失本体已计入 **㉞ 差集二/N1**（8 双零入口 + Web/Agent 双入口一有一无），审计/outbox catch-warn fail-open 已计入 **㉖ 同族**（本域再现 3 处，互证系统性，不重复计权），Job 框架重试/租约语义已计入 **㉙**。本节取证上传域**其余全部差集**：会话归属校验、权限口径、文件清单/路径/配额校验、complete 服务端核对、Worker 核验清单、DVC 语义、错误码族、状态枚举三方漂移、**证据等级自声称升级**（新维度，区别于 ㉞ N3 版本号漂移族）等。**行号基准**：全部无 offset `Grep -n` 精确式或全文/定段 `Read` 实定；**E4 一律 unproven**；静态取证，不宣称运行态结论。

**权威口径**：REQ-UPL-001 `p2-version-transfer.md` **L119-156**（permission `asset:upload` **L125**、idempotency REQUIRED **L126**、创建输入含 files 清单 **L132**、path 规范化/拒绝重复 **L137**、配额四要素 **L138**、20GiB 引导 CLI **L139**、**owner 绑定「其他主体不可读取/完成」L141**、同幂等同 Session **L143**、五族 `UPLOAD_*` 错误 **L149**）；REQ-UPL-002 **L158-192**（session owner 权限 **L164**、URL 日志/响应分析不记完整 query **L172**、服务端核对 MinIO metadata **L176**、重放同一结果/不同清单冲突 **L178**、状态动作严格限制 **L180**、TTL 15min **L184**、限制 Content-Length/Type/checksum 条件 **L186**）；REQ-UPL-003 **L194-217**（核验清单 size/SHA-256/媒体类型/路径/压缩结构/内容安全 **L208**、锁定基线检出 **L209**、DVC CLI add/push **L210**、Git commit+push **L211**、三元组分存 **L212**、staging 清理 **L214**、Saga 故障注入 **L216**）；REQ-UPL-004 **L262-280**（前端十条，含不入库 JWT、不盲信 LocalStorage）。DVC hash 混称禁令=同文件 **L109**（REQ-DVC-001 节）。契约上传家族 7 operation **L3382-3591**；`state-machines.md` §8 **L125-147**；DB=V16 **L85-165** 三表 + V42 补丁；实现=UploadController/UploadApplicationService（321 行全文）/UploadSession（158 行）/UploadMaterializeJobHandler（397 行）/MinioStorageAdapter（312 行）/Noop+Gitea 双 Materializer/双 Reconciler；前端 UploadPage.tsx（368 行全文）；证据=TASK-P2-003/004/005 + EVD-P2003/2005/2012。

| # | 要求（锚点） | 口径 | 实现证据 | 判定 |
| --- | --- | --- | --- | --- |
| **U-a** | REQ-UPL-001 **L141** | Session 绑定 Principal，**其他主体不可读取/完成** | **归属校验链全断（最高权重）**：`UploadApplicationService` 五方法逐行核验——getSession **L127-131**（仅 ASSET_READ）、getSessionForAsset **L135-143**（仅 assetId 匹配）、presignPartUpload **L160-176**、completeSession **L180-233**、cancelSession **L237-252** 后三者**均收 principalId 形参但与 `session.principalId()` 零比对**；listSessions **L256-263** 仅 ASSET_READ + 按 assetId 列举，无 principal 过滤；DB `upload_session.principal_id` NOT NULL（V16 **L92** 区域）绑而不用 | **✗**（REQ 字面违反；持有 asset:read/manage 的任意主体可枚举、签名推进、完成、取消他人会话） |
| **U-b** | REQ **L125/L164** | permission `asset:upload` | `Permissions.java` **L47** ASSET_MANAGE、**L55** ASSET_UPLOAD 双双定义；ASSET_UPLOAD 全仓（backend/src 全称 Grep）**仅定义行 1 处命中、零消费**；服务四处 **L99/L162/L185/L239** 全用 ASSET_MANAGE；契约 **L3418/L3501/L3530/L3579** x-required-permissions 亦 [asset:manage]——REQ↔(契约+实现) **双差** | **✗**（细粒度上传权限具文） |
| **U-c** | REQ **L143/L178** + state-machines **L144** | 同幂等同 Session；重复 complete 返回同一结果；不同清单冲突 | 幂等缺失本体→**㉞ 差集二/N1 已计权，不重复计**。增量：Web 二放 complete → `session.commit()` ISE → 409 `UPLOAD_SESSION_EXPIRED`（**L192-196**）——与规范 L144「重放同一结果」直接冲突（应重放而非报冲突）；Agent complete body null 兜底 `List.of()`（AgentController **L228**）——空清单可进服务层再拒，错误语义为 INVALID_ARGUMENT 非清单冲突 | **✗**（增量：重放语义违反规范文本） |
| **U-d** | REQ **L132/L137** | 创建输入含 files(path/size/mediaType/sha256) 清单；规范化 path、拒绝重复/绝对/穿越/保留 | 契约 createUploadSession requestBody **L3426-3429** 仅 totalBytes/fileCount/ttlSeconds——**无 files 清单**（REQ↔契约差）；complete 时 `persistCompletedFiles` **L267-275** 对 file.path() **零校验零去重直接 insert**；V16 upload_file **L121-136 无 UNIQUE(session_id,path)**——DB 层亦无兜底；唯一路径防线在 Worker `validatePath` **L348-361**（`..`/`/`/`\`/NUL/`~`/`->`），**时机后移到物化**：脏路径行先入库、Job 失败后残留 | **✗**（校验时机+层次双差） |
| **U-e** | REQ **L138** | 检查文件数、单文件、总量、Part 大小/数量和配额 | 仅 totalBytes≤20GiB（Service **L100-103** + 域 UploadSession **L51-55**）；fileCount 与 files 实数无对账；单文件 size 纯客户端申报；presignPartUpload **partNumber 无范围/上限校验**（L160-176，任意分片号可签）；平台/团队配额零接入（对照 MinioStorageReconciler **L124-161** 事后告警型 quota 检查存在但只读 version_artifact 求和，不在写入路径） | **✗** |
| **U-f** | REQ-UPL-002 **L172/L186** | 日志/响应分析不记录完整 query；presign 限制 Content-Length/Type/checksum 条件 | presigned URL **持久化入库**：Service **L172-173** `upsert(..., url.toString(), ...)` → V16 `upload_part.presigned_url TEXT` **L153**（列注释 **L165** 自陈「临时，不入日志」——注释与设计意图相符，实现反而落盘）；缓解面：`UploadPartView` **L14-18** 不回显该列、审计不记 URL（合规 **L150** ✓）；`MinioStorageAdapter.presignPartUpload` **L134-153** S3Presigner **无任何 Content-Length 区间/Content-Type/checksum 条件**（**L186 字面缺**）；响应回显完整 URL 系契约 **L3515** 声明（客户端必需），主要违反面=持久化+无条件签名 | **✗**（△缓解） |
| **U-g** | REQ **L176** | complete 服务端核对 MinIO metadata | `MinioStorageAdapter.completeMultipartUpload` **L156-180**：客户端清单 sorted+stripQuotes 后**直传** MinIO，无 ListParts/etag 预核对、无申报 size/sha256 与对象 metadata 对账（依赖 MinIO 服务端完成时自身校验——应用层核对环节不存在） | **✗** |
| **U-h** | REQ-UPL-003 **L208** | 核验 size/SHA-256/媒体类型/路径/压缩结构/内容安全 | `UploadMaterializeJobHandler.verifyStagingObjects` **L214-241**：objectExists false → `continue` **静默放行缺失对象（L230-232）**；sha256 比对仅「bundle 单文件且 expected 非空」或「文件存在且 expected 非空」时执行（客户端不申报即免检）；size/mediaType/压缩结构/内容安全**零实现**；`sha256Equals` 恒定时间比较 **L366-373 死代码**（实际 **L220/L236** 用 equalsIgnoreCase）；叠加 `MinioStorageAdapter.objectExists` **L278-289** catch→warn→return false——**存在性异常被吞后与 continue 合成静默放行链** | **✗**（核验清单六项仅一点半落地） |
| **U-i** | REQ-UPL-003 **L209-212** + REQ-DVC-001 **L109** | 锁定基线检出；DVC CLI add/push；三元组分存不混称 | **`sha256ToDvcMd5` L322-327 取 sha256 前 32 位伪造 `md5:` 字段**写入 .dvc 指针（buildDvcPointerContent **L329-331**）——直接违反 **L109**「分别保存，不混称」；无锁定版本 DVC CLI（全 handler 无进程调用）；无基线检出；`DVC_CACHE_BUCKET="dvc-cache"` 常量 **L54 声明未消费**——对象留 asset-staging 即宣告物化完成；`GiteaVersionMaterializer` **L63-96** contents API 写指针文件（无 push 语义区分） | **✗**（DVC 语义整面伪造） |
| **U-j** | REQ-UPL-001 **L149** | 补 `UPLOAD_*` Catalog 五族：NOT_FOUND/STATE_NOT_ALLOWED/LIMIT_EXCEEDED/PATH_INVALID/QUOTA_EXCEEDED | `ErrorCode.java` **L193/L196/L199** 仅 SIZE_EXCEEDED/SESSION_EXPIRED/SESSION_NOT_FOUND **3/5**；STATE_NOT_ALLOWED 缺→非法状态动作全部复用 SESSION_EXPIRED（cancel 已完成会话 **L244**、ensureSessionOpen 非 OPEN **L279-283** 皆报「EXPIRED」语义错位）；PATH_INVALID/QUOTA_EXCEEDED/LIMIT_EXCEEDED 缺 | **✗** |
| **U-k** | 契约 **L3428** + 前端 | ttlSeconds 可配 | 前端 UploadPage **L195** 发送 `ttlSeconds: 7200`；Controller **L44-53** 不读取传递；服务端恒 DEFAULT_SESSION_TTL=2h——契约声明字段为死字段 | **✗** |
| **U-l** | REQ **L145/L180** + state-machines §8 | 状态按规范确认后固化；EXPIRED/CANCELLED/COMPLETING/PROCESSING 动作严格限制 | **三方漂移**：规范 **L127** 自陈「设计未给出完整枚举，**建议**」CREATED/UPLOADING/COMPLETING ↔ 实现 `UploadSessionStatus` **L7-13** 七值 OPEN/COMMITTING/PROCESSING/COMPLETED/FAILED/CANCELLED/EXPIRED ↔ V16 CHECK 原缺 PROCESSING/FAILED 由 **V42**（**L5-10** 头注自陈）追补——DB fail-closed 曾暴露领域↔DDL 漂移；**过期终态双口径冲突**：`ensureSessionOpen` **L284-288** 惰性过期置 **CANCELLED**，而 `MinioStorageReconciler.expireStaleSessions` **L163-174** 每 5 分钟对同一事实（OPEN 且过期）置 **EXPIRED**——同一迁移两条路径两个终态；域 `cancel` 排除集仅 COMPLETED/CANCELLED/PROCESSING（UploadSession **L129-138**）→ **FAILED/EXPIRED 会话可被 cancel**，与 L180「严格限制」相悖；`detectStaleCommitting` **L176-185** COMMITTING>30min 置 **CANCELLED**（非 FAILED、无 FAILED→重试链）——域 **L74-81** 仅 OPEN 可 commit，CANCELLED 不可重入：MinIO 侧数据已 complete 而物化 Job 未建，**上传成功却永久失败**（正确恢复应为可重派物化或置 FAILED 供 retryProcessing，两皆无） | **✗**（规范未固化+实现自相矛盾） |
| **U-m** | p2-exit-catalog（四 AC 全 E4） | 证据等级 | **证据等级自声称升级**：TASK-P2-003 **L21-22/L97-98**、TASK-P2-004 **L94**、TASK-P2-005 **L93** 行为切片均声称 AC-P2-UPL-001..004 `Execute\|PASS\|E4`；EVD 实存：`EVD-P2003-001` **L3 status: PARTIAL**、**L49 evidenceLevel: E3**（**L17/L20/L23** 三 scenario result: null）、`EVD-P2005-001` 同型 PARTIAL/E3、`EVD-P2012-001` **L3 status: DRAFT**、**L22 result: FAIL**、L49 E3——**TASK 声称等级与证据卡等级直接矛盾**；出口规则「MUST 无 SKIP/notProven」不达 | **✗**（证据治理差集，新族：非 ㉞ N3 版本号漂移，为等级口径自升级） |
| **U-n** | REQ-UPL-003 **L216** | Saga 故障注入关注点 | createSession **先建 MinIO multipart 后插 PG**（Service **L96-122** 顺序）——崩溃窗口孤儿 multipart 无补偿（cancel 路径 abort **L246-249** 仅覆盖主动取消）；两 Reconciler 均不处理孤儿 multipart；**SessionStagingReconciler 的 AUTO_REPAIR 分类只告警不修复**：checkSession **L104-116** 返回 AUTO_REPAIR 后 recordDiscrepancy **L121-137** 仅写 webhook_inbox + 日志，无任何修复动作——分类名具文 | **✗/△** |
| **U-o** | AGENTS.md 审计基线 | 审计/Outbox fail-closed | Service `auditUpload` **L342-354**、`publishOutbox` **L356-365**、Handler `auditMaterialization` **L287-312** 三处 catch-warn——**㉖ 同族再现**（互证系统性，不重复计权） | 引用计权 |
| **U-p** | REQ **L176/L212** | 实际 size 入账 | markUploaded size 恒 `0L`（Service **L211-213**）；upload_file.size 与 artifact size 均为客户端申报值（Worker **L147** 默认 0）——「服务端核对」后仍无实测字节数 | **✗** |
| **U-q** | REQ **L211** | Git 提交并 push | `application.yml` **L81** `AIHUB_GITEA_ENABLED:false` 默认 → `NoopVersionMaterializer`（matchIfMissing=true，synthetic `local-`+digest sourceCommit）——默认/开发形态下「提交 Git」不发生；compose profile 显式 true（.env.example **L37**）——口径=profile 依赖，登记不裁决 | **△** |
| **U-r** | 契约内部 | schema 自洽 | completeUploadSession **L3526** description「files 必须非空」↔ **L3556** schema required 仅 `[parts]`、files 无 minItems——**契约文本与结构自相矛盾**；另 presign 为 **GET**（**L3496**）携带副作用语义（签名即 upsert part 行，Service **L172**）——HTTP 方法语义偏差登记 | **✗** |

**合规面**：① presign TTL 15min ✓（REQ **L184**，`PART_URL_TTL=Duration.ofMinutes(15)`）；② object key 服务端生成 ✓（**L292-294** `staging/{assetId}/{versionId}/{sessionId}`）；③ 前端零 localStorage/sessionStorage ✓（UploadPage 全称 Grep 零命中，REQ-UPL-004 不入库凭据）；④ 前端 20GiB 预检 ✓（**L187-190**）+ 刷新后无文件**诚实阻断**（**L303-308** 注释「不得伪造 sha256」）+ `restoreSession` 服务端对账 ✓（**L153-169**，REQ **L174**）；⑤ 失败 Part 重签重传 ✓（**L206-238/L240-294**，REQ **L175**）；⑥ STAGING_CLEANUP handler 在场 ✓（REQ **L214**）；⑦ Worker 幂等守卫完备：null/COMPLETED skip、FAILED→`retryProcessing` 重入（UploadSession **L119-126** ↔ state-machines **L135**）、物化前 `deleteArtifactsByVersion` 重放覆盖（Handler **L173-176**）；⑧ 物化后 VERSION_VALIDATE 入队 + DRAFT→VALIDATING 推进 ✓（**L178-191**）；⑨ 契约 7 operation 与实现路由一一对应、x-implementation-status 全部诚实标 `implemented-unverified` ✓；⑩ `upload_part` UNIQUE(file_id,part_number) ✓（V16 **L147-159**）；⑪ 两对账 Job 已注册周期化 ✓（RecurringJobBootstrapper **L50-51**，5min）；⑫ 审计事件不记预签名 URL ✓（REQ **L150**）；⑬ Nginx 不代理文件 bytes（REQ **L186** 后端面）：字节流全程不经后端（presign 直传 MinIO）✓。

**未实测清单（unproven）**：① 全部 AC-P2-UPL-001..004 的 E4 结论（U-m 已证 E3/PARTIAL/DRAFT）；② MinIO completeMultipartUpload 服务端对清单外 part/伪造 etag 的实际拒绝行为（U-g 只证应用层无预核对，MinIO 自身校验未实测）；③ 双 Reconciler 运行态效果（无 DB 环境）；④ `dvcHash` 伪造值在下游（下载票据/CLI/校验面）是否被消费——**若消费则 U-i 升级为跨域数据正确性缺陷**，本节未追下游；⑤ Agent/MCP 入口的 owner 校验同样缺失与否——AgentController 走同一 Service，静态推定同断，未逐行实定 MCP 通道；⑥ Nginx 配置层对上传路径的实际代理策略。

**取证偏差登记**：① **撤销**中间轮「EXPIRED 疑为死状态」判断——全称 Grep 证 `MinioStorageReconciler` **L163-174** raw SQL 置 EXPIRED + `PlatformMetrics` **L34/L128** 计数在场；改判为 U-l「过期终态双口径冲突」（惰性 CANCELLED vs 对账 EXPIRED）；② **升级**「ASSET_UPLOAD 上传链零消费」为「**全仓零消费**」（backend/src 全称 Grep 仅 Permissions **L55** 定义 1 命中）；③ 成文前逐项全称复验：五方法无归属（第 12 轮定段重读）、错误码 3/5、EVD 三卡 status/evidenceLevel/FAIL 行、18 表行计数；④ 本域立项第一动作已执行 `^### ` 标题枚举查重（㉞ 纪律固化后首次合规执行），确认 UPL 域无先行专节。

**限定与更正**：① U-c 幂等缺失本体、U-o fail-open 为**引用计权**，不与 ㉞/㉖ 差集数相加；② U-l COMMITTING→CANCELLED 的「上传成功却永久失败」链为静态推定（数据已 complete 至 MinIO 但 Job 未建 + 状态不可重入），运行态复现列入未实测③关联；③ U-q 为 profile 依赖口径，登记不裁决；④ 契约 L3515 响应回显完整 URL 系既有契约设计（客户端必需），**不判违反 L172**——L172 约束面是日志/响应分析（脱敏），日志侧已核：`MinioStorageAdapter` **L150** 失败日志不含 URL ✓；⑤ 本节涉契约/REQ/TASK/EVD/迁移五类禁改面文件**只登记不改动**；⑥ U-h「压缩结构/内容安全」在 P2 范围内是否强制以 REQ MUST 字面为据，若人类验收方判为后续阶段可整体降级该行。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① **U-a（最高优先）**：五方法补 `principalId.equals(session.principalId())` 归属校验（或管理员豁免位须进权限模型），listSessions 按 principal 过滤或以 `asset:manage` 显化跨主体语义并回改 REQ L141——二者取一须裁决；② U-b：上传链四处切 `ASSET_UPLOAD` + 契约 x-required-permissions 同步（breaking 评估走流程），或 ADR 改 REQ 口径；③ U-c：complete 重放改「返回同一 Job/结果」（幂等框架承接，与 ㉞ 整改②同批）；④ U-d：契约 create 请求体补 files 清单 + Service 创建时 path 规范化/去重校验前移 + 前向迁移补 `UNIQUE(session_id,path)`；⑤ U-e：fileCount 对账、单文件/partNumber 上限、配额接入写路径；⑥ U-f：presigned_url 列去持久化（或加密/截断存储），presign 补 Content-Length/Type 条件（**L186**）；⑦ U-g/U-p：complete 时 ListParts 核对 + 实测 size 回填；⑧ U-h：对象缺失/异常改 fail-closed（objectExists 三态化）、sha256 无条件必核（服务端自算，MinioStorageAdapter 已有 `sha256Hex` **L259-275**）、删除死代码 `sha256Equals` 或接入使用；⑨ U-i：DVC 指针 hash 字段改真实语义（md5 要么真算要么改用 DVC 规范 hash），引入锁定版本 DVC CLI + dvc-cache 桶写入，或 ADR 裁决 P2 降级「伪 DVC 指针」并回改 REQ L109/L210——**不得维持混称现状**；⑩ U-j：补 UPLOAD_STATE_NOT_ALLOWED/PATH_INVALID/QUOTA_EXCEEDED 三码 + 存量错位引用改判；⑪ U-k：ttlSeconds 接线或删除（契约 breaking 流程）；⑫ U-l：state-machines §8 固化七态（消灭「建议」态）、过期终态统一（惰性路径改置 EXPIRED 或删对账 SQL 二选一）、cancel 排除集补 FAILED/EXPIRED 须人类定；⑬ U-m：EVD-P2003/2005/2012 重出具（如实 E3/PARTIAL/DRAFT+FAIL），TASK 卡 PASS@E4 声称由验证权威撤回或补跑 E4——并入 ㉛⑬ 证据门禁一致性守卫；⑭ U-n：孤儿 multipart 对账（ListMultipartUploads 前缀扫描）+ AUTO_REPAIR 名副其实（执行修复或改名 DETECT）；⑮ U-r：契约 required/minItems 与 description 归一。

→ 本项与 ①—㉟ 一并不在本轮 AI 自决实施范围内。

### ㊱ Manifest 规范化与摘要面（REQ-MNF-001）：**REQ ↔ 领域实现 ↔ 写入侧 ↔ 读回/对账侧 ↔ 校验器 ↔ fixtures/测试 ↔ 契约 ↔ 证据卡 八方差集**：规范化算法违反 `algorithm:value` 与受保护字段两条 MUST、跨实现向量与规范化文档静态缺失、黄金标准系实现自产自检、证据卡三方口径互斥且关键 EVD 为空壳

**方法学**：权威锚点 = `docs/ai-spec/01-requirements/p2-version-transfer.md` L39-86（REQ-MNF-001；L45 `minimumEvidenceLevel: E3`；必需字段 L54 `sourceCommit`、L58 `dvcFile`、L59 `dvcHash`；规范化 L67-74，其中 L70 摘要须为 `algorithm:value`、L72 Java/Worker/CLI 产出相同 bytes/digest、L73 asset.yaml 冲突校验；验收 L78 六要素 golden fixtures、L80 受保护字段变→digest 变、L81 规范化文档与 Schema 版本化）与 `05-acceptance/p2-exit-catalog.md` L31（AC-P2-MNF-001 MUST/E3，判据同 L72/L80/L81）。对撞面：`version/domain/Manifest.java`（全库唯一规范化实现）、`transfer/…/UploadMaterializeJobHandler`（写端）、`integration/gitea/…/GiteaVersionMaterializer`、`RestGiteaTagVerificationPort`、`PublishedVersionReconciler`（Gitea 物化/读回/对账）、`version/…/ValidationJobHandler`（校验器）、`ManifestTest` + `contracts/fixtures/manifest/`（实存 3 json + 3 digest）、`contracts/openapi/aihub-v1.yaml`、`contracts/events/events-v1.yaml`、`cli/src`、TASK/EVD 四卡。

**已核验合规**（反向对照）：LF 统一换行（Manifest L85/L89）、NFC（L78/L87）、TreeMap 键排序、全串排序幂等、`generatedAt` 排除（L79-81，但写端链根本不产出该键，属死逻辑）、schemaVersion 常量在场（L24 `aihub/manifest-v1`）；GiteaVersionMaterializer L46-47 注释与实现一致（写失败上抛、不静默降级）；INV-ART-001 由 V16 UNIQUE 承接（㉔ 已核）；version 侧 digest/commit 发布冻结比对在位（`PublishApplicationService` L212）；fixtures 三个 sha256 与三个 sidecar 均为合法 64 hex（本轮实算复核）。

**缺陷面**：
- **V-1 摘要格式违反 REQ L70**：`computeDigest`（L55-64）输出裸 hex，无 `algorithm:value` 前缀；3 个 sidecar 实测同为裸 hex；`PublishedVersionReconciler` L34 `VALID_DIGEST=^[a-f0-9]{64}$` 把裸 hex 反向固化为校验规范。改算法（如换 SHA-512）digest 形状不变、不可辨识。
- **V-2 digest 输入面缺受保护字段，违反 REQ L54/L58-59/L80**：`forVersion`（L31-43）仅 {schemaVersion, assetId, versionId, artifacts}；`ArtifactEntry`（L136、toMap L138-147）仅 path/sha256/size/mediaType。Handler L150-151 构建期 dvcHash/dvcFile 在手却不入清单（L156）；无 sourceCommit 键 → **sourceCommit/dvc 字段变化 digest 不变**，AC「受保护字段变→digest 变」在这两个维度静态不成立。
- **V-3 跨实现面对撞为零，REQ L72/catalog L31 判据不可满足**：canonicalize/serializeList/serializeArtifactMap（L72-125）为自定义 `k=v\n`、`;`、`|`、`k:v` 拼接格式；`cli/src` Grep manifest/digest **零命中**；全库无第二实现方 → 交叉向量（REQ L79）无实施体，判据落空是结构性的、非仅未验证。
- **V-4 恶意 path 注入面**：L122 `entry.getKey() + ":" + strVal` 对 `:`/`;`/`|`/换行无转义、无长度限定 → artifact 内容可伪造相邻条目；fixtures 亦无恶意 path 用例（REQ L78 六要素之一）。
- **V-5 空值处理违反 REQ L67「明确空值处理」**：L77/L84/L88/L119/L121 将 null/空白一律剔除，`mediaType:""` 与缺失键、`{a:""}` 与 `{}` 摘要同值——把「剔除」当「处理」，且与 fixtures 中 dataset-v1 第二条目缺 mediaType 的空字段态叠加后语义不可辨。
- **V-6 golden fixtures 不达六要素（REQ L78）**：3 个 fixture 仅覆盖排序（dataset-v1 两条目）与空清单（empty-artifacts）；**Unicode、大整数、恶意 path、换行、显式空字段五要素缺失**；全部无 sourceCommit/dvcFile/dvcHash 键，受保护字段维度零覆盖。
- **V-7 黄金标准自产自检**：`ManifestTest` L59-66 在 `.digest` 缺失时首跑把**被测实现自己的输出**写入 sidecar 并仅断言 isNotBlank——sidecar 由被验实现生成，只能防未来回归、不证当前正确；L29-31 fixtures 目录缺失时静默零用例（假绿）。
- **V-8 规范化文档与 Schema 版本化全缺（REQ L81）**：全库 Grep canonical 无一为 manifest 规范化文档（唯一「规范」是 Manifest.java L18-19 一句 Javadoc 自述）；`docs/ai-spec/02-domain/` 仅 4 文件、无对应文档；openapi 仅 2 行提及 manifest（无 `aihub/manifest-v1` schema）；events-v1.yaml 仅 L352 一个 `manifestDigest` 字段、无 MANIFEST_* 事件；schemaVersion 兼容策略（REQ L71）无文档。
- **V-9 asset.yaml 冲突校验零实施（REQ L73）**：全 backend main 无任何 Manifest↔asset.yaml 比对代码；`ValidationJobHandler` L117-118 VAL-LIC-001 以「delegated to asset profile」**无条件假 PASS**。
- **V-10 校验器空转（REQ L74/INV-ART-005 仅弱证）**：VAL-CARD-001 L86-87、VAL-SEN-001 L120-121 无条件 PASS；VAL-MNF-001 L89-96 仅判 digest 非空白、**不重算不比对**；VAL-DVC-001 L108 `dvcHash!=null||sha256!=null` 恒真（Handler L150 必写 dvcHash）。（查重声明：plan L1053 提及 VAL-SEN-001 仅作 ㉞ 授权链证据、L848 属 ㉑ 假阳性排除，均非缺陷登记；本条为首次登记。）
- **V-11 读回自证循环**：`RestGiteaTagVerificationPort` L89 从 Gitea 取回 entries 后用**同一实现**重算 digest；GiteaVersionMaterializer L50 存储的是 Jackson 序列化的 manifestJson（**非 canonical bytes**）→ 存储物≠摘要输入物，规范化实现漂移在验签链上不可见；`PublishedVersionReconciler` L174-183 的比对同为此实现 vs 此实现。
- **V-12 三元组语义破口（与 ㉟ U-i 伪造 md5 同族互引）**：`resolveSourceCommit` L255-260 无 Gitea 时合成 `"local-"+manifestDigest`（69 字符）；materializeToGit L243-253 无仓库静默降级；`NoopVersionMaterializer` L27 头注自陈该语义；`PublishedVersionReconciler` L141 `VALID_COMMIT_SHA=^[a-f0-9]{40}$` → Noop/降级路径产生的 PUBLISHED 版本对账**必 INVALID_COMMIT→MANUAL_REVIEW**；V43 加宽 manifest_digest 列是此污染的既成余波。差异处置 `recordDiscrepancy` L211-227 仅 webhook_inbox 插入 + 日志，**无 audit_log 写、无告警分发**（SECURITY_INCIDENT 亦仅 LOG.error，L213-215）。
- **V-13 证据面三方口径互斥 + 关键 EVD 空壳**：REQ L45=E3、catalog L31=E3、`TASK-P2-001` L20=E3，而 `TASK-P2-012` L29 对同一 AC-P2-MNF-001 标 **E4**；`TASK-P2-012` frontmatter L5 `implementationAuthorized: true` 与正文 L80「`implementationAuthorized: false`，AI 不自行授权」**卡内自相矛盾**（新证，同 ㉟ 偏差家族）；EVD-P2012-001 L48 实测 E3 vs 要求 E4、L22 completionValidation FAIL；**EVD-P2001-001 为空壳卡**（requirements/scenarios/proven/notProven 全空、L43 command 与本任务无关）→ TASK-P2-001 的 E3 声称**无任何实证**；TASK-P2-001 L106-108 三步实施全未勾、allowedPaths L35-41 不含 `backend/src`——现行 Manifest.java 实现并非该卡授权产物，属 gate 外落码。

**偏差登记**：AC-P2-MNF-001 的 E3 声称（TASK-P2-001 L93）不成立——实证链断裂（空壳 EVD）+ 判据本体（跨实现向量、规范化文档、受保护字段）缺失；TASK-P2-012 双处口径/授权矛盾需人工裁决；fixtures sidecar 的自产性质使其作为「黄金标准」的证据等级 ≤E2。

→ 本项与 ①—㊱ 一并不在本轮 AI 自决实施范围内。

### ㊲ P3 发布治理面（REQ-VAL-001/REV-001/PUB-001/IMM-001/DEP-001 + REQ-REV-002）：**规范 ↔ 应用服务 ↔ Job Handler ↔ Gitea 适配器 ↔ DB 触发器 ↔ 契约/事件 ↔ 前端 ↔ 证据卡 八方差集**：治理链权限码整体错位一代、审批决策可静默倒退法定人数、发布 Saga 守卫假成功、不可变防护仅覆盖同态更新一个角、弃用原因结构性不可传入、全域 E4 声称仅有全 notProven 的 E3 空转卡

**方法学**：权威锚点 = `docs/ai-spec/01-requirements/p3-release-governance.md` 全文 218 行（六 REQ 块 `minimumEvidenceLevel` 全为 **E4**：L14/L56/L92/L134/L168/L194）与 `05-acceptance/p3-exit-catalog.md`（AC-P3-VAL/REV-001/PUB-001/UI-001/EXIT-001 为 E4，REV-002/SAGA-001/DEP-001 为 E3；L9-17 出口七规则）。对撞面：`version/application/PublishApplicationService`（437 行全读）、`version/application/VersionApplicationService`（250 行全读）、`version/application/ReviewQuorumPolicy`、`version/infrastructure/PublishJobHandler`（280 行全读）、`version/infrastructure/ValidationJobHandler`、`version/domain/Version`/`VersionStatus`、`version/api/VersionController`/`ReviewController`、`integration/gitea/infrastructure/RestGiteaTagPublisher`/`NoopGiteaTagPublisher`、`integration/gitea/…/PublishedVersionReconciler`、`mcp/application/McpToolCatalog`、`V16`/`V17`/`V30`/`V38`/`V43` 迁移、`contracts/openapi/aihub-v1.yaml`、`contracts/events/events-v1.yaml`、`frontend/src/features/version/ReviewPage.tsx`、TASK-P3-001..010 + EVD-P3001..010、`error-catalog.md`。

**查重声明（先于差集）**：① `DEPRECATED→PUBLISHED` 回边违反终态不可逆已由 **㉑ 结论 4②**（L878-880）登记，本节不重复计权，仅补其治理后果（见 X-1 附注）；② transition 直转路径以 `ASSET_MANAGE` 承载 `PENDING_REVIEW→PUBLISHED` 的跨路径不变量破裂、及 `VersionApplicationService.publishVersion`（L155-183）为**生产零调用方死代码**，已由 **㉕ 缺陷面 A**（L1157-1168，含全库引用检索实证）定装并推翻更早的「疑似越权」表述——本节 X-2 仅登记 P3 规范侧对应条款（REQ-PUB-001 L96「仅由已满足审批的 PublishRequest 触发」）与该既有结论的映射，不新立缺陷；③ `PublishJobHandler` 直写 `audit_log` 旁路已由 **㉖**（L1297 列清单缺列）登记，X-5 仅补「同仓双标」新证。

**已核验合规**（反向对照，逐条实读）：禁自审在位（`PublishApplicationService` L191-195 抛 `AUTH_PERMISSION_DENIED`，满足 REQ-REV-001 L64）；审批时漂移双值比对在位（L210-224 frozen_digest/frozen_source_commit → 409，满足 REV-002 的 PG 侧一半）；提交前三前置（L109-132 digest 非空/最新 PASSED 报告/commit 一致）；`publish_request` 冻结列实存（V17 INSERT L139-143 携 frozen_digest/frozen_source_commit/policy_version，满足 REQ L62）；三值决策状态机效果（REJECT→`REJECTED`+回 DRAFT L240-260、REQUEST_CHANGES→`CHANGES_REQUESTED`+回 DRAFT L261-283、足数 APPROVE→`APPROVED`+enqueue L286-323）；`Version.publish()` 三元组 `requireNonNull`（Version L102-113）；同态更新防改写触发器 `trg_prevent_published_version_update`（V17 L91-112）+ `git_tag` UNIQUE（V30 L9-10）；补偿告警**不吞**（`PublishJobHandler` L223-236 写 `system_alert` CRITICAL FIRING，Javadoc 明引 REQ-AUD-001——与 ㉖ 吞审计面构成同仓正反样板）；Agent 发布封禁双层（`McpToolCatalog` L49 `AGENT_DENIED_TOOLS` 含 `asset_publish_version` + `HIGH_RISK_ACTIONS`，满足 REQ L106）；前端审批面板展示冻结 digest/commit 与工件 Diff（`ReviewPage.tsx` L41-42/L74-99/L127-128，AC-P3-UI-001 主判据在场）。

**缺陷面**：

- **X-1 治理链权限码整体错位一代（三方口径互斥，须上报裁决）**。规范 `p3-release-governance.md` L12/L55/L90/L166 要求 `asset:submit/asset:review/asset:publish/asset:deprecate` 四窄码；`Permissions.java` 五常量**全在场**（L59/L61/L63/L65/L47）却仅 `ASSET_PUBLISH` 一处被接线（`submitDecision` L174）。实现现状：`submitPublishRequest` L81 用 `ASSET_MANAGE`（㉕ 缺陷面 C L1180 已登记「提交发布申请走 ASSET_MANAGE」）；`deprecateVersion` L345 用 `ASSET_MANAGE`；`submitDecision` 用 `ASSET_PUBLISH` 而规范要 `asset:review`——**审批权与发布权在实现中合并为同一码**，四眼分离失去权限层支撑。新证在**契约第三口径**：`aihub-v1.yaml` L3137-3140 decisions 端点声明 `publish:approve`（目录外幻影码，㉕ 缺陷面 B 已登记）；即同一操作规范=「asset:submit, asset:review」、契约=「publish:approve」、实现=「asset:publish」三方互斥，且 ㉕ 已证明幻影码若按契约实现拦截器将永 403。`asset:submit`/`asset:review`/`asset:deprecate` 属「已种子化、主代码零鉴权引用」的零接线窄码族（㉕ L1179 同族登记；查重：㉟ U-b 为零接线同族而非同码，本条为 P3 域首次逐操作定装）。X-1 附注（承 ㉑/㉕）：`DEPRECATED→PUBLISHED` 回边在权限侧同样仅需 `ASSET_MANAGE`（transition 端点），即「恢复发布态」既不满足终态不可逆、也不经任何发布权。
- **X-2 REQ-PUB-001 L96 发布唯一入口条款被 transition 端点结构性击穿**：`VersionController` L100-110 将 `VersionStatus.valueOf(request.targetStatus())` 任意合法态直转暴露为 REST（契约 L3051-3061 `x-required-permissions: [asset:manage]`、L5299-5301 枚举含 `PUBLISHED`——㉕ L1165 已证契约与可达性），使「仅由已满足审批的 PublishRequest 触发」在 API 面不成立；`publishVersion` L157-183 为同源死代码（㉕ L1168 已定装，无新证）。附带：L107 `valueOf` 对非法串抛 `IllegalArgumentException` 走 500 而非 400 错误映射（与 error-catalog 校验类 400 口径不符，轻）。
- **X-3 审批决策面三缺（decision 枚举无守卫 → 法定人数可被静默倒退）**。① `ReviewController` L42 `body.decision()` 透传任意字符串，无 APPROVE/REJECT/REQUEST_CHANGES 枚举校验：非三值串（如 `FOO`）会先 INSERT `review_decision`（L227）再无任何分支匹配 → 请求永留 `SUBMITTED`；更重的是该 INSERT 带 `ON CONFLICT (request_id, reviewer_id) DO UPDATE`（L230），**同一 reviewer 用 `FOO` 再提交一次即把其先前 APPROVE 覆盖掉**，已达标 quorum 静默倒退、且倒退动作无任何拒绝/审计。② reject 意见必填未判空（REQ L67 MUST；`DecisionRequest(String decision, String comments)` L64 直入库，V17 L85 列注释「APPROVE/REJECT/REQUEST_CHANGES」仅文档性）。③ expected request version 乐观锁未实现（REQ L66；`Version.rowVersion` L40 字段在 `VersionEntity` L34 有映射但**无 `@Version` 注解、无乐观锁拦截**，publish_request 更新链 L84-86/L243/L302 全为裸 UPDATE 不带版本谓词）。
- **X-4 法定人数仅 sensitivity 单维**：`ReviewQuorumPolicy` L19-29 只对 `CONFIDENTIAL/SECRET/HIGH/RESTRICTED` 要 2 人，其余恒 1；REQ-REV-001 L63 要求按 **License/Sensitivity/Risk** 三特征决定人数——License 与 Risk 两维零参与；策略亦无版本化/配置化（与 ㉘ 字典配置面无接口）。
- **X-5 发布 Saga（`PublishJobHandler`）七处治理缺口**。① P1 守卫失败 L78-81 `LOG.warn + return`——任务以成功终态收口，**审批达标后永不发布的请求在此路径下不可观测**（假成功）；② P3 L100-113 仅比对 PG 冻结列，不回读 Gitea/MinIO 重验 Commit/Manifest/DVC（REQ L98「重新验证」缺失，与 X-7 读侧缺口呼应）；③ Tag 冲突检测 L117-123 只查 PG 自身表（`git_tag=? AND status='PUBLISHED' AND version_id<>?`），Gitea 侧真实 Tag 不在视野（移交 X-6）；异 Commit 冲突处置为 `markFailed`+`IllegalStateException` L120-122，**非 REQ L101 要求的 `MANUAL_REVIEW` 态**（publish_request 八态 CHECK 中亦无 MANUAL_REVIEW，㉑ 结论 1 同域）；④ 同 Commit 幂等重放（REQ L100）无实现（无「已 PUBLISHED 且三元组相同 → 幂等成功」分支）；⑤ 「PG PUBLISHED 但外部事实缺失 → 停止新下载票据」联动（REQ L112）零命中；⑥ `PG_ONLY_GITEA_DISABLED` 仅 `LOG.warn` L134-137 后照常置 PUBLISHED（Tag/Commit 半边三元组为 NULL 的行可入库——V17 触发器只防同态改写、不防发布时缺列，X-7 同根）；⑦ Javadoc L34 自陈「P7 Outbox 事件（P3 简化为审计日志）」= 声明性偏差在册。**同仓双标新证（承 ㉖）**：`recordAuditEvent` L241-246 注释明言直写属旁路缺陷、失败上抛令 Worker 重试；而 `VersionApplicationService.auditVersion` L217-229 对同类直写**吞异常仅 LOG.error**——审计失败处理策略在同一 version 包内双标，㉖「审计写失败策略分裂」结论在 P3 域再添一例。
- **X-6 `RestGiteaTagPublisher` 的 Tag 保护与「永不覆盖」两条 MUST 失守**。① TOCTOU 覆盖路径：`fetchTagCommit` L118-137 把任何 `RestClientException`（含瞬时网络故障）**一律折返 null** → 上游 L51 视为「Tag 不存在」→ `createTag` L86-96 撞 409 后**直接 PUT 覆盖既有 Tag**；其注释自辩「重试后由上游 commit 一致性校验兜底」不成立——上游校验（Handler L117-123）只看 PG、`fetchTagCommit` 已失败才会走到这里。Gitea 瞬时不可达即可使指向不同 Commit 的同名 Tag 被改写，违反 REQ L101「不同 Commit 永不覆盖」与 AGENTS.md「发布版本以 Tag+Commit+Digest 不可变标识」。② 保护规则形同虚设：`protectTag` L101-105 提交 `allowlist_user:[]`/`allowlist_team:[]`/`prevent_creation:false`——Gitea 语义下空白名单+不防创建 = **任何有写权限者可改/删该 Tag**，REQ L102「Tag 保护策略和服务账号独占」未实现；注册失败仅 `LOG.warn` 不阻断（L111-113）。③ 超时后「先读 Gitea 核验、不直接创建第二 Tag」（REQ L113）无实现（与 ① 同根：读失败被吞成「不存在」）。
- **X-7 不可变性（REQ-IMM-001）防护只覆盖一个角**。V17 L94-103 触发器仅在 `OLD.status='PUBLISHED' AND NEW.status='PUBLISHED'` 同态时防五字段改写；**状态一变（PUBLISHED→DEPRECATED/ARCHIVED）触发器整体失效**，digest/git_tag/source_commit/published_* 在非同态 UPDATE 下可被任意改写且 DB 零阻止——REQ L139「PUBLISHED 版本记录不可 UPDATE」/L145「DB 约束阻止更新，不能只靠 UI」仅部分满足；L151「五入口尝试覆盖」验收在 DB 层即可被 transition→update 序穿。补 ㉑ X：`PublishedVersionReconciler` 对 digest 不符虽置 SECURITY_INCIDENT（L174-183），但**Tag 被删除/改指向而 materialized 文件仍自洽**的情形无专门检测路径（其读回面 X-8-③ 同源），REQ L142「Reconciler 检测手工 Tag 改动 → CRITICAL」不成立。
- **X-8 VAL/DEP 两 REQ 的行为面缺口**。① REQ-VAL-001 L23-33 十一条校验规则，`ValidationJobHandler` 实落仅 2-3 条真判（VAL-MNF L89-96 仅判非空白、VAL-ART L100-104 仅判行数、VAL-DVC L109 恒真——㊱ V-9/V-10 已逐行定装），Card/License/Sensitivity 五条无条件假 PASS；治理后果（承 ㊱ 互证链）：`submitPublishRequest` L117-120 的「最新 PASSED 报告」门禁因上游恒 PASSED 而**永真**，REQ-P3-VAL→REV 链条在源头即失效。② L38「重放返回同一校验 Job/报告」未实现（transitionVersion L137-139 每次直 `enqueue` 新 Job；Handler L132 INSERT 新报告行，无按 versionId+frozenCommit 复用分支），L41「sourceCommit 变化使旧报告失效」亦未实现（submit 查询 L117-120 仅 `ORDER BY created_at DESC LIMIT 1` 取最新，不按 commit 过滤——旧 commit 的 PASSED 报告在新 commit 下仍被采信，仅靠 L128-132 的 commit 等值比对兜底一半）。③ REQ-DEP-001 L173 `deprecation_reason` MUST **结构性不可满足**：`ReviewController` L49-53 deprecate 端点无请求体、`deprecateVersion(versionId)` L344 无 reason 形参、`VERSION_DEPRECATED` Outbox payload L350-356 无 reason 键、审计 `recordGovernanceEvent` L349-353 无 reason；L177 通知面仅 `resolveAssetOwners` L359，缺 Maintainer/订阅者；L179 下游引用检查全 backend 零命中；L176「已授权仍可下载」——下载票据签发路径未定位到对 `DEPRECATED` 的显式放行/拦截语义（`PreviewApplicationService` L275 仅预览侧要求 `PUBLISHED`），登记为未证。④ `events-v1.yaml` 全文 Grep `PUBLISH_REQUEST|publish_request` **零命中**：REQ-REV-001 L76 要求的提交/决策事件契约缺失（仅 `VERSION_PUBLISHED` L346-353 在场）；`error-catalog.md` L143 仅 `VERSION_STATE_NOT_ALLOWED`，REQ L118「Tag 冲突建议专用错误」与 L75 自陈待补的 PublishRequest 错误码均未落目录。
- **X-9 证据面：P3 全 E4 声称 vs 全部空转卡**。`p3-exit-catalog.md` 五场景 E4 + 三场景 E3；抽查 `TASK-P3-003`（REQ-REV-001）frontmatter 内**卡内自相矛盾**：L21-22 场景映射标 `E4|E4`，L46 `requiredEvidenceLevel: E3`（同 ㉟ 卡内漂移家族）；`EVD-P3003-001`：L3 `status: DRAFT`、L48 `evidenceLevel: E3`、L51-52 `proven: []` + `notProven: [AC-P3-REV-001, AC-P3-REV-002]`、L54-60 artifacts 六类全空、L16/L19/L22 三项 task-card 校验 `result: null`（未运行）——即该 REQ 的验收为**零证明**，且 E3 命令（L43）为通用测试集、非场景化执行。AGENTS.md 现状口径（P1–P5 = PARTIAL/IMPLEMENTED_UNVERIFIED）与此相自洽；**未发现任何 P3 场景存在 E4 实证**，六个 REQ 块 L14/L56/L92/L134/L168/L194 的 E4 门槛全部未达（登记为整体缺口，不逐卡展开——TASK-P3-001..010 与 EVD-P3001..010 十对卡逐一核查为后续项）。

**偏差登记**：① P3 六 REQ 全 E4、出口目录 8 场景无一有 E4 实证，本节全部结论为**静态取证（≤E2）**，不得转写为「已验证缺陷」而应读作「静态差集」；② X-2 的可达性虽经契约/代码双侧静态证实，但未做运行态 PoC（DEC-009 禁本轮实施）；③ X-6-② 的 Gitea `tag_protections` 语义（空白名单=不限制）依据 Gitea API 通行语义判读，未在本环境实调核验；④ X-8-③ 下载票据对 DEPRECATED 的放行语义为「未定位到证据」而非「确认缺失」；⑤ TASK/EVD 十对卡仅抽查 003 一对，其余九对未逐一核查，X-9 的「全部空转」表述限定为「抽查样本 + AGENTS.md 既有 PARTIAL 口径」。

→ 本项与 ①—㊲ 一并不在本轮 AI 自决实施范围内。

### ㊳ P5 入站与对账治理面（REQ-INBOX-001 + REQ-REC-001）：**规范 ↔ 入站端点/应用服务 ↔ 异步 Handler ↔ 周期调度 ↔ 七个 Reconciler ↔ DB（V18/V28）↔ 契约/指标/告警 ↔ 证据卡 八方差集**：入站链「处理」实为把行标记 COMPLETED 后打日志、Gitea 侧只订 push 一类使四分之三事件分支永不可达、CRITICAL 仅一维且只落计数器、"周期对账"实为每进程启动一次且人工触发零入口、AUTO_REPAIR 四舍五入只有 1/7 类型有真修复动作、四个 Dashboard gauge 无写入方恒 0

**立项与查重过程（先行声明，避免重复计权）**：本轮立项前对 `docs/ai-spec/01-requirements` 13 文件做 REQ-ID 全域普查，P5 八个 REQ（INBOX/REC/PRE/BKR/PERF/SEC/OPS/REL）在本文档 ①—㊲ 全文**零命中 = 整域未覆盖**；PRE 属 P2 已查预览域（㉟/㊱），BKR/PERF/SEC/OPS/REL 五块 `minimumEvidenceLevel` 均为 **E5**（p5-quality-operations.md L108/L143/L182/L220/L264，需故障注入与规模夹具，静态不可判定，本节排除并入偏差登记），故 ㊳ 定装为 P5 唯二 E4 且可静态取证的 **REQ-INBOX-001（L6-32）+ REQ-REC-001（L34-69）**。与既有节的互引/查重：① webhook_inbox 的 `FAILED`/`DISCARDED` 死状态值与 Handler 仅写三态已由 **㉑ 结论 6**（L888-897）定装 → 本节 Y-2 只补「PROCESSING 永滞留」与「`error_message` 列零写入者」两点新证；② 七个内部对账写入点污染 `webhook_inbox`（JobLeaseReconciler L138 / SessionStagingReconciler L130 / MinioStorageReconciler L230 / AuthorizationProjectionHandler L127 / PublishedVersionReconciler L220 / AssetRepositoryReconciler L212 / OutboxDeliveryReconciler L136）+ `delivery_id` V18 64 与 V11 40 异宽 + 字符串拼接 + try/catch 静默丢失已由 **㉘ 缺陷面 D**（L1391-1393）全量定装 → 本节**不重复登记该清单**，Y-6 仅补其「每轮新增一行、从不回收」的累积后果；③ Bootstrapper 存在性检查走 `list(...,1000)` 最旧窗口与「排窗外类型静默不投递」已由 **㉙ 结论 F/J** 定装 → Y-5 讲的是**生命周期**（SUCCEEDED/DEAD 后不重建），与 J 的**类型表**互补不重复；④ `JobLeaseReconciler` 的 webhook_inbox 死分支已由 **㉚ 结论 C-1** 定装；⑤ `PublishedVersionReconciler.recordDiscrepancy` 无审计无告警已由 **㊱ V-12**（L1973）定装 → Y-6 补「integration + minio 包 `system_alert` 全域零命中」这一范围扩展；⑥ Tag 被删/改指向无检测已由 **㊲ X-7**（L1996）定装 → Y-4 只补 `isCriticalEvent` 的判定维度缺失，不重述读侧；⑦ 审计/告警写失败策略「同仓双标」族由 **㉖** 与 **㊲ X-5⑦** 登记 → Y-4 为 CRITICAL 分发通路的第三例。

**方法学**：权威锚点 = `docs/ai-spec/01-requirements/p5-quality-operations.md` 全文 288 行实读（REQ-INBOX-001 十一条行为 L17-27、REQ-REC-001 七对账范围 L46-52 + 九行为 L56-64）。对撞面（全部实读或全库检索定装）：`integration/gitea/api/WebhookInboxController`（58 行全读）、`integration/gitea/application/WebhookInboxApplicationService`（179 行全读）、`integration/gitea/infrastructure/WebhookInboxHandler`（158 行全读）、`integration/gitea/infrastructure/GiteaAssetRepositoryProvisioner` L80-141、`GiteaProperties`（webhook 三项）、`job/infrastructure/RecurringJobBootstrapper`（106 行全读）、`job/infrastructure/JobWorker`（346 行全读）、`integration/gitea/infrastructure/AssetRepositoryReconciler` L60-224 与 `PublishedVersionReconciler` L190-251 与 `AuthorizationProjectionHandler`（136 行全读）、`integration/minio/infrastructure/SessionStagingReconciler`（146 行全读）、`platform/observability/application/PlatformMetrics`（280 行全读）、`bootstrap/security/SecurityConfiguration`、`V18__webhook_inbox.sql` / `V28__reconciliation_checkpoint.sql` 全文、`contracts/openapi/aihub-v1.yaml` L3620-3642、`02-domain/error-catalog.md`、`TASK-P5-001/002` 卡面、`EVD-P5001/5002/5004/5006`。

**已核验合规**（反向对照，逐条实读，构成 REQ-INBOX 的部分满足）：
- 验签 **fail-closed 且常数时间**：`WebhookInboxApplicationService` L72-78 + L129-146（secret 缺失即返回 false、强制 `sha256=` 前缀、HmacSHA256）、L163-170 `constantTimeEquals`；`GiteaProperties` L67-71 空串归一、L76-77 双条件门控；`application.yml` L85-89 密钥仅环境变量注入，不入 Git/日志（满足 REQ L18 签名维与 L27 Secret 面）。
- **先落库再响应 + Delivery ID 唯一**：L92-98 INSERT `ON CONFLICT (delivery_id) DO NOTHING` 先于 202 返回，V18 L19 `uq_webhook_delivery`（满足 L19）；`SecurityConfiguration` L78 该端点 `permitAll` 属 AGENTS.md「Agent/Client 凭据交换与最小健康端点」例外（HMAC 即认证），未扩面。
- **retry/lease 复用可靠 Job 体系**：L114-127 入队 `WEBHOOK_PROCESS`（maxAttempts=5）而非裸线程/`@Async`；`JobWorker` 的 DEAD 迁移 + attempt + `JOB_DEAD` 审计同事务（L216-222 / L236-241 `transactionTemplate`）、`RETRY_WAIT` 分路、`maskMessage` 经 `SensitiveDataMasker`（L318-324）、tick 永不抛出保调度（L126-137）——满足 L25 与 AGENTS.md「持久作业」硬约束。
- **内容层幂等**：`WebhookInboxHandler` L93-96 已 COMPLETED 直接返回、L99 CAS 式 `WHERE status='PENDING'`（满足 L20 的重复投递结果一致一半，另一半见 Y-2 附注）。
- **建仓侧 webhook 自注册真实存在（本节主动推翻立项时的怀疑）**：`GiteaAssetRepositoryProvisioner` L65 → L89-119 `registerPushWebhook` 经 `POST /api/v1/repos/{owner}/{repo}/hooks` 注册，`config.secret` 与入站验签同源（`GiteaProperties` L36-38 注「入站验签与出站注册共用同一值」，AppService Javadoc L33-34 的声称**成立**）；L123-141 `webhookExists` 先查后写保幂等；L114-118 注册失败降级不阻断开通。`EVD-P5001-001` L64-72 亦记录 2026-09-23 V07 旅程 **E4 实测**（真实 push → `source=gitea` 入站 ≤10s 可查、对应 Job 推进 COMPLETED，Summary PASS=18 SKIP=0 FAIL=0）。
- **checkpoint 表结构自洽**：`V28` L9 `last_cursor VARCHAR(64)` 且 L17 列注释明写「通常为 jobId 或 version_id/asset_id」→ `PublishedVersionReconciler` L233-243 存 jobId **不构成列误用**（本节撤回立项阶段的该怀疑，改判为 Y-8 的「写而不读」）。
- `REQ-REC` 对账范围第 1/2/4/5/7 项**有实现体在场**（Asset↔仓库存在性真调 `RestGiteaRepositoryExistencePort`、Version 三元组读回 Tag/Commit、MinIO 对象、Session↔staging、Outbox↔投递），非纯骨架——缺口集中在「触发周期、修复动作、差异分发」三段（见 Y-5/Y-6）；第 3 项（Role/ACL↔Gitea 投影）与第 6 项（搜索投影↔权威 Commit）不在此列，见 Y-9 与偏差登记③。

**缺陷面**：

- **Y-1 入站「处理」实为标记 + 打日志：REQ L22/L23 的回查与增量投影零实现**。`WebhookInboxHandler.processEvent` L115-142 的 switch 中：`push` 分支 L125-134 只做 `LOG.info`（forced push 亦仅 `LOG.warn` L130-133）、`create`（含 tag）与 `repository` 分支同样只日志、`default` L121 `LOG.debug` 后**仍无条件下游 L108-110 把行标为 COMPLETED**（未识别 eventType 被记为已处理 = 假处理，且 V18 L14 五态 CHECK 无「未知」态可承载）。REQ-INBOX L22「Payload 非最终事实，须回查 Gitea 取权威状态」与 L23「增量投影更新 Asset/Version/权限缓存」两则 **MUST 零命中**——全类对 Asset/Version/权限表**无任何写入**，唯一有副作用的分支是 `delete`+`ref_type=tag` L144-157（记一个计数器）。后果：入站链在标准部署下确实"通"（V07 E4 已证入库与 COMPLETED），但其**业务价值面为零**——Git 侧任何变更都不会反映到平台投影，投影一致性完全依赖 Y-5 中实际不周期的 Reconciler 全量扫描。
- **Y-2 失败面三缺：PROCESSING 永滞留、`error_message` 列零写入者、重复投递仍入队新 Job**。① `sweepPendingInbox` L69-72 只捞 `status='PENDING'`，而 `processDelivery` L99 已把行置 `PROCESSING`；若 `processEvent` 抛异常（L63-64 上抛使 Job 重试），重试再入 L99 时 `WHERE status='PENDING'` 不命中，而代码**不检查 `update` 返回值**即继续执行 `processEvent`（重复副作用无守卫）；若 Job 5 次耗尽转 DEAD，该行**永久停在 PROCESSING** 且没有任何 sweeper 会再捞它——补齐 ㉑ 结论 6：五态中 `PENDING/PROCESSING/COMPLETED` 有写入方、`FAILED/DISCARDED` 零写入方，而 PROCESSING 是**只进不出**的漏斗底。② V18 L15 `error_message TEXT` 列**全仓零写入者**（仅 V18 自身命中），运维在 DB 中看到 PROCESSING 行无从得知原因，与 ㉑ 死状态值同族新证。③ AppService L96-98 幂等仅落在内容层（`inserted==0` 只 `LOG.debug`），随后 **L110 无条件 `enqueueProcessJob`** → Gitea 每次重投递都产生一个新 Job 行，重复洪泛时 Job 表被同内容作业占满，与 REQ L20「重复投递幂等」的运维面（不产生新工作）不符。
- **Y-3 事件面只有 1/5、校验四维只有 1/4**。① 出站注册 `GiteaAssetRepositoryProvisioner` L102 的 `events` 仅 `List.of("push")`（`branch_filter="*"`），故 REQ-INBOX L17 要求的 tag create/delete、repository、release、member/team 四类事件 **Gitea 根本不会投递**，Handler 对应的 `delete`/`repository` 分支在自动注册链路上永不可达（人工在 Gitea UI 加钩子除外）；这与 Y-1 的分支空心构成**双重不成立**：投不到、投到也不处理。② L18 要求「签名 + 来源 + 时间窗 + 请求大小」四前置，实现仅签名：来源侧无 IP/网段或 Gitea 实例标识校验（端点 `permitAll`，㉑ 段 L448 已登记白名单清单）、时间窗零命中（重放旧合法签名无阻碍）、请求体大小无上限（`application.yml` 无 `max-request-size`/`spring.codec` 类配置，全库零命中），`payload` 原文入 JSONB（L92-98）；③ L26 的 payload 脱敏与保留期亦零命中——`webhook_inbox` 无任何 DELETE/归档/TTL 路径（叠加 ㉘ D 的内部差异行永久累积，表单调增长且内含未脱敏 payload）。
- **Y-4 CRITICAL 单维判定 + 只落计数器 + 分发通路同仓三标**。① `isCriticalEvent` L148-153 以字符串 `contains` 判 `"action":"delete"` 且含 `tag`，REQ L24 的「Tag 删除 / **改指向** / **force push**」三情形仅覆盖第一维（force push 在 L130-133 只 `LOG.warn` 不进 CRITICAL；改指向因 Y-9 的投影缺失而无处可比）。② `recordCriticalEvent`（`PlatformMetrics` L102-109）与 `recordReconciliationDiscrepancy`（L114-121）都是**纯 Micrometer counter**：无 `system_alert` 写入、无 `audit_log`、无通知（REQ L21「未知 → FAILED/DEAD 并告警」与 L63「通知/告警/人工队列」三处分发点全缺）。③ 对照同仓正样板 `PublishJobHandler` L223-236 会写 `system_alert` CRITICAL FIRING（㊲ 已列为合规面）——同一 P5 治理域内 CRITICAL 有「写库告警」「只打日志」「只加计数器」三标；`grep system_alert` 于 `integration` 包 **零命中**（新证：非仅 PublishedVersionReconciler 一处，而是整个对账/入站包域内无告警分发）。
- **Y-5 「周期对账」不成立：每进程启动一次 + 人工触发零入口**。`RecurringJobBootstrapper` L64-69 只实现 `ApplicationRunner.run`（无 `@Scheduled`、无 cron、无自重入队），其 L71-96 的存在性检查 `isActive` L98-102 只认 `PENDING/RUNNING/RETRY_WAIT`；因此七类周期作业一旦进入 **SUCCEEDED**（正常终态）或 **DEAD**（`AihubApplication` L30 注释自陈的 Gitea 不可达路径），**本进程余下生命周期内不再有任何入队路径**，直至下次重启——REQ-REC L56「以**周期**与人工两种方式触发并持久化 Job」中的「周期」实为「每次部署一次」，两种终局之后对账静默消失（与 ㉙ J 的类型表缺口互补）。人工触发面：全 `*Controller` Grep `reconcile` **零命中**，REST/OpenAPI 亦无任何对账触发或结果查询端点（`aihub-v1.yaml` 全文无 reconcile 路径）→ L56 后半句与 L57「checkpoint 可恢复」的运维入口在**实现与契约两侧同时缺位**。
- **Y-6 修复面空心：AUTO_REPAIR 是分类标签而非动作，dry-run/防漂移/留痕/人工队列四则全缺**。逐一定装：`AssetRepositoryReconciler` L131-133 空 namespace 判 `AUTO_REPAIR` 后**直接返回、无任何修复调用**；`SessionStagingReconciler` L104-116 CANCELLED/EXPIRED 仍有未上传 Part 时判 `AUTO_REPAIR`，同样只有 `LOG.warn` + 计数（146 行全类读完，无清理动作）；`PublishedVersionReconciler` 枚举 L246-250 **连 AUTO_REPAIR 都没有**；已实读范围内唯一确认的真修复是 AssetRepo L174-194 的 `enqueueReprovisionJob`（缺失仓库→入队 `REPOSITORY_PROVISION`，带 `hasActiveByTypeAndAssetId` 在途去重，**这是正确样态，可作其他类型的实现参照**）。其余 L60 dry-run、L61 apply 防漂移、L62 before/after + ruleVersion + principal + trace 留痕：全库 Grep `ruleVersion|dryRun|dry_run` **零命中**，差异行 payload 仅 `{assetId|versionId|sessionId, classification}`；L63「无法修复 → 通知/告警/人工队列」缺（Y-4② + 无差异查询/队列端点）；L64「差异不得被下一周期掩盖 + 防无限通知」缺（delivery_id 以 `System.currentTimeMillis()` 拼接 → 每轮为同一 Session/资产新增一行，既掩盖了"是否同一差异"、又与 ㉘ D 共同构成差异行单调膨胀）。
- **Y-7 依赖不可达时判「一致」而非「未知」**：`AssetRepositoryReconciler` L164-169 在 `giteaExistenceProvider.getIfAvailable()==null`（Gitea bean 未装配）时**跳过存在性检查**，整批恒 `CONSISTENT`；`PublishedVersionReconciler` L198-210 `classifyGiteaUnavailable` 仅在 compose/production profile 下 strict，其余 profile 把 Gitea 不可达直接归 `CONSISTENT`（其枚举亦无 UNKNOWN/DEGRADED 态）。REQ-REC L59「修复仅恢复权威事实，不以本地值覆盖外部权威」的隐含前提是**不可达须显式暴露**；现实现使「对账通过」在依赖缺席时成为默认值，属假阳性一致性（与 ㊱ V-12 的降级三元组、㉚ 的静默面同族）。
- **Y-8 checkpoint 写而不读，七类只有 1 类接入**：全库 Grep `reconciliation_checkpoint` 仅 `PublishedVersionReconciler` L233-243 三处（UPSERT 累计 `total_checked`/`discrepancies`），`SELECT ... FROM reconciliation_checkpoint` **零命中** → `last_cursor` 从不回读，所谓检查点只能证明"跑过"，不能续扫；其余六类的 `cursor` 均未见任何 checkpoint 读写，其中已实读的两个（AssetRepo L71-110、SessionStaging L45-76 的 `do/while` + `Thread.sleep(100)` 限流）确认为方法内局部变量，**重启即全表从头再扫**（余四个的游标形态未逐一实读，见偏差登记④），REQ-REC L57 的「限流」在已实读的 Reconciler 中普遍到位（批量 + sleep），「checkpoint」却仅 1/7 类接入且为装饰性。附带：`updateCheckpoint` 异常 `LOG.debug` 吞（L230-244），checkpoint 写失败无任何信号。
- **Y-9 `AUTH_PROJECTION` 是零生产者死 Worker，且「投影」本体不调 Gitea API：对账范围第 3 项两侧皆空**。`AuthorizationProjectionHandler` L43 声明 `type()="AUTH_PROJECTION"`，全仓 Grep **仅此 1 处命中**（Bootstrapper L46-54 七类型表亦无它）→ 无任何入队方，属死代码；其 L79-118 三个投影方法全部是 `SELECT` + `LOG` + `recordProjectionAudit`（L123-135 写 `webhook_inbox`，即 ㉘ D 已登记点），**无任何 Gitea Team/Repo API 调用**，与类 Javadoc L17-26「差异部分通过 Gitea API 同步」**直接矛盾**（须上报的注释↔实现冲突）。后果叠加 Y-1：REQ-REC L46-52 七项范围中第 3 项「Role/ACL ↔ Gitea Team/Repo 投影」**在投影侧与对账侧同时不存在**，且 SearchProjection↔权威 Commit（第 6 项）本轮未定位到实现（登记为未证，见偏差登记）。
- **Y-10 四个 Dashboard gauge 无写入方恒 0（观测面塌陷）**：`PlatformMetrics` 的 `updateJobQueueDepth` L136、`updateJobDeadCount` L143、`updateWebhookInboxPending` L150、`updateAssetCountByStatus` L182 四个 setter 经全库 Grep **仅定义处命中、零调用方** → 其注册的 `aihub_job_queue_depth`/`aihub_job_dead_count`/`aihub_webhook_inbox_pending`/`aihub_asset_count_by_status` 四个 gauge 在运行态恒为 0（Gauge 回调绑定的是从未被 set 的 AtomicInteger）。这使 P5 观测面板的作业积压/死信/入站积压三张图**结构性不可信**；由于 `EVD-P5006-001` L88 的告警规则集经 promtool 静态校验（14 rules）但未 live 求值（L93-94 自陈 `/actuator/prometheus` 401、Drill 5 SKIP），本处**不下「告警已失效」结论**，仅登记为「若 `alerting-rules.yaml` 消费上述 gauge 则与其 PASS 声称冲突，须人工复核」的取证待办。
- **Y-11 契约与证据面**：① `aihub-v1.yaml` L3622-3642 `/webhooks/gitea` 与实现对齐（三头 `required:false`、`security: []`、202/400/401），但 `x-error-codes` 仅 `COMMON_INVALID_ARGUMENT`——验签失败的 401 **无专用稳定码**（`error-catalog.md` L204 唯一 webhook 码是 `WEBHOOK_TARGET_FORBIDDEN`，属 SSRF 出站向，与本端点无关），且 `WebhookInboxController` L55 的 500 分支无响应声明（与 ㉘ 幻影码/错误目录缺口族同向，不重复计权）。② **`TASK-P5-001` allowedPaths L38-42 与实际代码位置错位**：卡面授权 `com/aihub/reconciliation`，该包经 Glob **不存在**，七个 Reconciler + Handler 实际全落在 `integration/{gitea,minio}` 与 `job/infrastructure` 下——即本 REQ 的实现整体位于 Task 授权路径之外（按 DEC-009 只登记上报，不判定归属）。③ `EVD-P5001-001` 卡面 `status: PARTIAL`：AC-P5-INBOX-001 有 E4（L64-72，V07 旅程）但同文件 L20-23 记 task-card 校验 **FAIL**（baseCommit 滞后，㉘ 已登记该治理项）、`AC-P5-REC-001` 整条 `notProven`（L74-75「dry-run 未在运行态取证」）——与本节 Y-5/Y-6 的静态结论方向一致：**REC 半句从来没有证据**。④ `TASK-P5-002` L45 及其余 P5 卡多为 `requiredEvidenceLevel: E5` 且 `EVD-P5002/5004/5006/5007/5008` 全为 `status: PARTIAL` 带成组 `notProven`（如 L86-88 格式矩阵未逐一实跑、L97-98 backup 全链 SKIP），可作 P5 出口未达的旁证（AGENTS.md 现行 PARTIAL/IMPLEMENTED_UNVERIFIED 口径自洽）。

**查重后新增结论计数**：Y-1/Y-2（PROCESSING 漏斗与 error_message 零写入）/Y-3（events 只订 push、四维缺一余三、无 TTL）/Y-4②③（CRITICAL 无分发 + 三包零告警写）/Y-5（周期=每进程一次 + 人工触发两侧皆无）/Y-6（AUTO_REPAIR 无动作 + dry-run/ruleVersion 零命中 + 差异行 currentTimeMillis 膨胀）/Y-7（不可达判 CONSISTENT）/Y-8（checkpoint 写而不读，**并撤回立项时「列误用」怀疑**）/Y-9（Javadoc↔实现冲突）/Y-10（四 gauge 无写入方）/Y-11②（`com/aihub/reconciliation` 包不存在 = 授权路径错位）为**新证**；㉑6 / ㉘D,E / ㉙F,J / ㉚C-1 / ㊱V-12 / ㊲X-5⑦,X-7 所指内容均已在文内以互引形式标注，不重复计权。

**偏差登记（须人工裁决或后续取证，AI 不自决）**：① 本节全部结论为**静态取证（≤E2）**，而 AC-P5-INBOX-001 已有 2026-09-23 的 E4 实证（V07 入站链路本身可用）——两者不冲突：E4 证明的是「能收能标 COMPLETED」，Y-1/Y-2/Y-3 说的是「收了之后不投影、失败不可观测、事件面只订一类」；表述时不得把 Y 系列转写为"已验证的运行态缺陷"。② 本轮**未复跑** `verify-journey.sh`（无 Docker 环境，DEC-009 禁自证），V07 的 PASS 引用为 EVD 卡既有记录而非本轮实测。③ REQ-REC 对账范围第 6 项「搜索投影 ↔ 权威 Commit」本轮**未定位到实现**，属「未找到证据」而非「确认缺失」。④ `MinioStorageReconciler` / `OutboxDeliveryReconciler` / `JobLeaseReconciler` 全文未重复读（其 webhook_inbox 写入面已由 ㉘ D、死分支面已由 ㉚ C-1 定装），Y-6 的「唯一确认的真修复」结论限定为**已实读的四个 Reconciler 范围内**。⑤ Y-10 与 `deploy/observability/alerting-rules.yaml` 的消费关系未核（须确认四条 gauge 是否被任何告警表达式引用，再判定 E5 演练 PASS 的有效性）。⑥ BKR/PERF/SEC/OPS/REL 五个 E5 REQ 与 PRE（属 P2 已查域）未纳入 ㊳，其静态可判定的行为子句留待独立成节。⑦ Y-11② 的路径错位若属"实现先于卡面演进"，修法应是人类重授 Task 范围，AI 不得改卡。

→ 本项与 ①—㊳ 一并不在本轮 AI 自决实施范围内。

### ㊴ P1 资产目录域（REQ-AST-001..008）：**规范 ↔ 领域模型 ↔ 应用服务 ↔ Controller ↔ SearchDao ↔ Job/Provisioner ↔ DB 迁移 ↔ 契约/事件 ↔ 证据卡 八方差集**：Facet 缓存键缺主体维度致跨主体计数泄露、契约 11 参数 vs 实现 2 参数、Cursor 不绑定 filter/sort/Principal 且解码失败静默当首页、organizationId 可经 update 三层贯通修改（直接违反 MUST）、永久 Alias 防线零调用方、物理回收 Job 零生产者且删除步骤 TODO 空壳、etag/sourceCommit 基线冲突零实现、AST-002..008 无一有 E4 证据卡

**立项与查重过程（先行声明，避免重复计权）**：本轮立项前对 `docs/ai-spec/01-requirements` 13 文件做 REQ-ID 全域普查（SPEC 63 项 − plan ①—㊳ 已提及 32 项 = 31 项未提及），`REQ-AST-001..008` 八个 REQ 块在 ①—㊳ 全文**零命中 = 整域未覆盖**；AST-009（L299-305，资产目录前端，E4）与 REQ-UI/DST-CLI 族一并留作 ㊵ 前端域候选，本节不取证。与既有节的互引/查重：① AST-008（L271-298）的「Role/ACL ↔ Gitea Team/Repo 投影」在投影侧与对账侧的两侧皆空已由 **㊳ Y-9**（零生产者死 Worker、不调 Gitea API）定装 → 本节不重复登记该行为，仅补规范侧条款映射；② `asset:*` 权限码接线现状、幻影码与零接线窄码族已由 **㉕ 缺陷面 A/B/C** 定装 → 本节仅在 Z 条目涉及处引用；③ 审计直写旁路与写失败策略分裂由 **㉖/㊲ X-5⑦** 登记 → 本节无 asset 域新例即不重复；④ Gitea 读回自证循环与 Noop 降级三元组由 **㊱ V-11/V-12、㊲ X-6** 定装 → 本节仅将其作为 AST-008 对账面的旁证引用；⑤ version 侧 deprecate reason 结构性不可传入由 **㊲ X-8③** 定装（不同实体、不同端点）→ Z-8 为 asset 侧首次定装，非重复。

**方法学**：权威锚点 = `docs/ai-spec/01-requirements/p1-asset-catalog.md`（AST-001 L10-47：L16 `E3`、L25 规范化四拒、L28 Alias 永久不复用 MUST；AST-002 L49-101：L58 `E4`、Idempotency REQUIRED、Provision 完成才进搜索；AST-003 L103-146：L112 `E4/E5`、L123 SQL 全维过滤、L129 Cursor 绑定 filter/sort/Principal、L132 Facet 与 items 同 Predicate 防聚合泄漏、L137 非法 cursor→`COMMON_INVALID_ARGUMENT`；AST-004 L148-178；AST-005 L179-213：L188 `E4`、L193 etag/sourceCommit、L195 **不能通过更新改变 Organization（MUST）**、L196 重命名原子预留坐标+保留 Alias、L199-200 基线冲突/并发单胜；AST-006 L215-240：L221 `E4`、L229 不能移除最后 Owner、L233 权限来源可解释；AST-007 L242-269：L250 `E4`、L255 deprecation_reason MUST、L257 归档前四检、L261 物理回收四要件；AST-008 L271-298：L277 `E4`）。对撞面（全部实读或全库检索定装）：`asset/domain/Asset.java`（680 行全读）、`asset/domain/AssetSearchCriteria.java`（96 行全读）、`asset/application/AssetApplicationService.java`（652 行全读）、`asset/application/AssetAccessPolicy.java`（121 行全读）、`asset/api/AssetController.java`（282 行全读）、`AssetAccessController.java`（118 行全读）、`AssetCatalogController.java`（84 行全读）、`asset/infrastructure/AssetSearchDao.java`（486 行全读）、`MyBatisAssetRepository.java`、`AssetEntity.java`（377 行全读）、`RepositoryProvisionJobHandler.java`（123 行全读）、`PhysicalRecycleJobHandler.java`（203 行全读）、`AssetCardSyncJobHandler.java`、`integration/gitea/infrastructure/Noop|GiteaAssetRepositoryProvisioner` + `GiteaProperties.java`（79 行全读）+ `application.yml` L77-89、`platform/idempotency/IdempotencyService` L46-66、`V2__asset_catalog.sql` / `V14__asset_remediation.sql` / `V22__asset_p1_remediation.sql`、`contracts/openapi/aihub-v1.yaml` /assets 族（L1665-1669/L1743/L1836-1874/L1978/L3682/L4782）、`contracts/events/events-v1.yaml` L47-56、`EVD-P1001..P1018` 十八张证据卡摘要、`Permissions.java`、`CaffeineCacheConfiguration` L54-58、`DownloadStatsController` L39。

**已核验合规**（反向对照，逐条实读，含两处本节主动撤回的立项怀疑）：
- **幂等机制链完整在场（撤回立项时「createAsset 全链未触 Idempotency-Key」的候选怀疑）**：create L71-85 / update L146-160 / delete L165-175 三写路径均接 `IdempotencyService.execute`，key = value+principalId+method+path（`buildIdempotencyKey` L250-255）、fingerprint = sha256(Principal+method+path+body)（L257-265），冲突→`IDEMPOTENCY_KEY_CONFLICT`（契约 L1667-1669 同步声明）、重放返回存储响应——缺口仅为 header `required=false`（见 Z-11）。
- **建仓 Saga 合规**：`RepositoryProvisionJobHandler` 幂等推进 PENDING→IN_PROGRESS→COMPLETED（已完成态跳过 L78-81）+ `ASSET_PROVISIONED` Outbox（L110-122）；AST-002「Provision 完成才进搜索」✓（检索门禁生效）；「Gitea 不可用保留可重试意图」由 Job 重试链承载 ✓。
- **Noop/Gitea Provisioner 条件互斥**：Noop L21 `havingValue="false", matchIfMissing=false`、Gitea L43 `havingValue="true"`——（撤回立项时「属性缺失双 bean 皆不立 → 启动崩溃」怀疑：`application.yml` L81 `enabled: ${AIHUB_GITEA_ENABLED:false}` 使属性恒有值）；口径张力另登偏差④。
- **分页与 fail-closed**：limit 默认 20 / 上限 100 clamp（`AssetSearchCriteria` L66-81 ↔ 契约 L1743 一致）；空可见性默认 `{PUBLIC}`（L73-75）；越权与不存在统一 `ASSET_NOT_FOUND` 防枚举（AST-007 L262 ✓）；DEPRECATED 默认返回但降权（AST-003 L126 降权在位，跨页漂移副作用见 Z-3）。
- **坐标格式拒**：`SLUG_PATTERN`/`NAME_PATTERN` 白名单正则（Asset L23/L26，创建 L136 与变更 L244 双点 `requireSlug`）——路径分隔、控制字符、非 ASCII（歧义 Unicode）三类因白名单天然拒绝；保留词拒零命中（偏差⑤）。`formatCoordinate` type 小写（L376）。
- **乐观锁与软删**：`UpdateAssetRequest.expectedVersion` 必填 + SQL 版本谓词双层；`AssetEntity` L118-120 `@TableLogic` 使 existsByCoordinate/update 自动获得 `deleted=0` 注入（**撤回立项时「update 缺 deleted 条件」怀疑**）。
- **ACL 与可见性面**：`AssetAccessPolicy.visibleVisibilities` RBAC 四级（L58-78）、`canAccess` Owner Team/legacy owners/ACL（L89-108）；`AssetAccessController` 三端点读需 `asset:read`+canAccess、写需 `asset:manage`——AST-006 L226/L230-231 半句在场。
- **事件契约齐备（推翻规范 AST-002「当前 Event Schema 需补」表述的现势性）**：`events-v1.yaml` L47-56 已有 `ASSET_CREATE_REQUESTED`/`ASSET_PROVISIONED`/`ASSET_CREATED`/`ASSET_CREATE_FAILED` 四条（偏差⑦）。
- 周边：`/assets/{assetId}/stats` 由 `DownloadStatsController` L39 实装（契约 L1978）；`Permissions.ASSET_CREATE="asset:create"` L43 与契约一致；V14 `asset_alias` 表 + V22 `owner_team_id` FK（L13）/`aliases` JSONB（L35）结构在场。

**缺陷面**：

- **Z-1 跨主体 Facet 缓存泄露（本节最重，直接违反 AST-003 L132 防聚合泄漏 MUST）**：`AssetSearchDao.buildFacetCacheKey` L382-405 只拼 keyword/type/namespace 等过滤维度，**不含 accessScope/principal**；而 facet SQL 本身按主体可见性过滤 → 同一查询串下，低权限主体可命中高权限主体写入的 60 秒缓存快照（`CaffeineCacheConfiguration` L54-58 facetCache），读到其不可见资产（含他组织 INTERNAL/PRIVATE）的计数分布。授权 Predicate 在 SQL 层一致、在缓存层被键设计击穿——泄露面是跨主体的，非仅同主体陈旧。
- **Z-2 facets 契约-实现参数缺口 11:2**：契约 L1836-1874 声明 11 个查询参数（namespace/organizationId/visibility/status/taxonomy itemCode/tagId 等），`AssetController` L127-133 实收仅 keyword+type 两参 → AST-003 L117-118 查询矩阵与 L132「Facet 与同条件检索计数一致」在九参数维度**结构性不可达**（实现面缺失，非未验证）；与 Z-1 叠加后 facets 端点两个 MUST 同时不成立。修哪一侧须人类裁决（偏差③）。
- **Z-3 Cursor 不绑定 filter/sort/Principal 且解码失败静默吞**：`AssetSearchDao` L305-313 游标载荷仅 (created_at, id) 两键，而 ORDER BY 为三键 `(CASE status, created_at, id)` → DEPRECATED 降权在翻页途中状态变化时产生跨页重复/漏项漂移；同一 cursor 串换 filter、换 sort、换主体照样可用（违反 L129「Cursor 绑定 filter/sort/Principal scope」）；L413-429 解码失败**静默按首页返回**而非 L137 的 `COMMON_INVALID_ARGUMENT`（非法输入错误 MUST 反向）。
- **Z-4 update 可改 organizationId，直接违反 AST-005 L195 MUST**：三层贯通定装——`UpdateAssetRequest.organizationId`（「为空表示不变更」语义与显式清除不可区分）→ `AssetApplicationService.updateMetadata` L199 → `MyBatisAssetRepository` L236 `set organization_id`。资产可借元数据更新在 Organization 间静默搬迁，绕开 Owner 移交与组织治理链。
- **Z-5 永久 Alias/坐标复用防线零调用方（AST-001 L28 MUST 击穿）**：`findByAlias` 全 backend 仅接口声明（`AssetRepository.java` L92）与实现（`MyBatisAssetRepository.java` L432-438）两处命中、**零调用方**；`existsByCoordinate` L207-214 只查 asset 表（软删行经 @TableLogic 排除）、不查 `asset_alias`（V14 `ux_asset_alias_coordinate` 仅防别名表自身重复）→ 重命名（Asset L252 写入旧坐标别名）或软删后的旧 `namespace/type/name` 可被新资产直接占用。且 `V2` L36 注释自陈「软删除记录不参与约束以便重建」为有意设计——规范 MUST 与已应用迁移注释**正面矛盾**，属须裁决的规范↔实现分裂（偏差②）。
- **Z-6 真坐标重命名未实现 + 别名两套表示**：Asset L258-259 注释自认「namespace/name 坐标不变，重命名仅修改 displayName + aliases」→ AST-005 L196「原子预留新坐标并保留 Alias」无实施体（Q-202 待确认口径不改变「已写入的 alias 无人校验」这一 Z-5 事实）；`asset.aliases` JSONB（V22 L35，ns/type/name 串形态）与 `asset_alias` 表（V14，old_namespace/old_name 列形态）并存、互不对账。
- **Z-7 etag/If-Match/sourceCommit 基线冲突零实现**：asset 包全 Grep `etag|If-Match|sourceCommit 基线比对` 零命中 → AST-005 L193「Request 携带目标 sourceCommit/etag」、L199「Gitea 分支在基线 Commit 后变化返回冲突，不强推覆盖」两则无实施面（L200 的 rowVersion 半边在位）；元数据更新对 Git 侧漂移完全无感，投影可被静默覆盖。
- **Z-8 归档前四检只有 1 检 + deprecation_reason 全链可空**：archive 仅查 PUBLISHED 版本计数（`AssetApplicationService` L268-273），AST-007 L257 要求的下游关系、任务、保留策略三检零命中；deprecate 链 `AssetController` L221 `@RequestBody(required=false)` → Service `validateDictItem` L516-521 空文本直接 return → 领域 `Asset.deprecate` 四参可传 null——L255「deprecation_reason itemCode 必填」MUST 在 REST/Service/Domain 三层全部可空（与 ㊲ X-8③ 的 version 侧同族，asset 侧首次定装）。
- **Z-9 物理回收整链死路（AST-007 L261 四要件落实 2/4）**：`ASSET_PHYSICAL_RECYCLE` 全 main 检索仅 `PhysicalRecycleJobHandler` 自身与测试命中——**零入队生产者**，Job 永不执行；即便入队，`deleteGiteaRepository` L151-156 与 `deleteMinioObjects` L163-167 为 TODO 日志空壳，唯 `deleteDbRecords` L175-186 真删九表（且**不清理 asset_alias**，与 Z-5 交叉：行存续与否语义未定）；保留期检查真在位（L76-98，默认 90 天）+ 审计在场，「引用计数、二次确认」两要件零命中。
- **Z-10 Owner 治理两缺**：AST-006 L229「不能移除最后 Owner」经全 backend 检索（last owner/最后 Owner 等关键词组合）零命中，`AssetAccessPolicy`（121 行全读）与 ApplicationService 均无该守卫；L233「权限来源可解释」——ACL 端点返回原始记录，未见 Role/Scope/Membership/ACL 来源枚举的解释性实现（定装为「未定位到实施体」，见偏差⑥）。
- **Z-11 Idempotency-Key REQUIRED 失守为 best-effort**：header 声明 `required=false`（AssetController 三写路径），空 key 时 `IdempotencyService` L48-51 无键直接执行业务——规范 AST-002/005/007 三块 `idempotency: REQUIRED` 与契约 `IdempotencyKey` 组件（L3682）的必填语义在实现层退化为「可绕过」，绕过时无任何拒绝或告警；另 AST-002「开通中」态仅有 `AssetView.provisioningStatus` 字段（契约 L4782）而无专用状态/重试端点（轻，SHOULD 级）。

**查重后新增结论计数**：Z-1—Z-11 全部为 ①—㊳ 范围内的**新证**（资产域首次整域取证）；㊳ Y-9 / ㉕ A,B,C / ㉖ / ㊱ V-11,V-12 / ㊲ X-6,X-8③ 所指内容均以互引形式标注、不重复计权；立项怀疑撤回三处（幂等缺失→改性为 Z-11、@TableLogic、启动崩溃）在文内逐条声明。

**偏差登记（须人工裁决或后续取证，AI 不自决）**：① 本节全部结论为**静态取证（≤E2）**；AST-002..008 各块 `minimumEvidenceLevel: E4`（L58/L112/L188/L221/L250/L277），而 `EVD-P1001..P1009` 十九张中资产域九张全为 PARTIAL/E3（P1001-001 `notProven` 点名 AC-P1-AST-001-E4），`EVD-P1015-001` 虽 PASS/E4 但覆盖面是 default-deny 断言、非 AST 行为，`EVD-P1018-001`（人类 accepted 的波次出口快照）自身 `execution.result: PARTIAL`、exitCode 1——**AST-002..008 无一存在 E4 行为级证据卡**，与本节缺陷面方向互洽；② Z-5 的 V2 L36「软删不参与约束以便重建」注释与 AST-001 L28 MUST 正面冲突，裁决点：改规范（允许软删后重建）或改约束（坐标永久占用），AI 不改已应用迁移也不改规范；③ Z-2 的 11:2 参数缺口修实现还是修契约须人类裁决；④ 「Noop 不进可部署 Profile」（AST-008）：条件互斥机制在位，但 `application.yml` L81 默认 false 使裸 jar 可部署形态默认即 Noop（Compose 置 true）——「可部署 Profile」的口径（是否仅以 Compose 为准）须裁决，与 AGENTS.md「null-principal/all-scopes 不得进可部署 profile」同族原则；⑤ AST-001 L25 四拒中保留词零命中，路径分隔/控制字符/歧义 Unicode 因白名单正则间接覆盖——「三拒半」而非「四拒全无」，转写时不得夸大；⑥ AST-004 Card 投影逐条（AssetCardSyncJobHandler 存在且 Gitea 门控 L29，行为细目未逐条对撞）、AST-009 前端目录、AST-003 L133 性能条款（E4/E5，需 10 万级夹具）均不在本节取证范围，属「未检」而非「合规」；⑦ 规范 AST-002 的「当前 Event Schema 需补」句与 `events-v1.yaml` L47-56 现状（四事件齐备）不符，属规范文档过时；⑧ Z-10 权限来源解释为「未定位到实施体」而非「确认缺失」——authorization 模块内的解释性投影未全量排查。

### ㊵ 前端域（REQ-AST-009 资产目录前端 + REQ-UI-001 Web 统一体验）：**page-catalog ↔ 页面/表单/权限/凭据/API 层 ↔ 共享组件 ↔ E2E ↔ 证据卡 五方对撞**：asset 域全部写操作零 Idempotency-Key 接入（admin 域早已实装同规范条款——同库两口径）、列表与详情查询失败态混同「资产不存在」（五态违反且语义误导）、变更按钮无 usePermission 门控、伪造 repo 显示怀疑经后端联检**撤回**、REQ-AST-009 验收场景零证据卡绑定（REQ-UI-001 侧存在 E4 PASS 卡）

**立项与查重过程（先行声明，避免重复计权）**：`REQ-AST-009` 与 `REQ-UI-001` 对 plan ①—㊴ 全文 Grep 零命中 = 字面未覆盖（㊴ 偏差⑥ 明确将「AST-009 前端目录」登记为「未检」而非「合规」，本节即补该检）。查重口径：① `asset:*` 权限码接线现状与幻影码族由 **㉕ 缺陷面 A/B/C** 在后端权限域定装 → 本节仅取前端可见性侧新例（W-3）；② **REQ-UPL-004 前端十条**（不入库 JWT、20GiB 预检、restoreSession 对账）已由 **㉟** 覆盖 → 本节的凭据内存化合规（C1）与 E2E 登录基建引用不与 ㉟ 重复计权；③ **REV-002** 已由 **㊲** 覆盖、不在本节范围；④ UI-001 的「写复用 Idempotency-Key」条款（L394）此前仅在后端侧由 **㊴ Z-11** 定装 → W-1 为前端侧首次定装并指出双口径（偏差③）。
**权威锚点**：`p1-asset-catalog.md` L299-342（AST-009 十条行为：PAGE-AST-001..006、Schema 驱动、URL 同步+保留返回位置、假 repo 不显示、按钮双考虑、Card 安全渲染、zh/en+键盘/焦点、凭据不持久化、组件/权限/两主体 E2E）；`p0b-platform-services.md` L373-402（UI-001 十二条：统一生成 Client、Permission Provider 非默认全 Scope、路由/导航共用权限元数据、Loading/Empty/Error/Retry/Success 五态、access 仅内存、403/404 防枚举、写复用 Idempotency-Key、乐观锁不覆盖、E2E/视觉证据、「页面存在或 Vite build 不构成完成」）；`04-ui/page-catalog.md` L44-49（PAGE-AST-001..006，含 AST-005「最终权限解释，不只显示原始绑定」、AST-003「快速使用」DEC-014）。
**对撞面**（全部实读定装行号）：`AssetsPage.tsx`（527）、`AssetDetailPage.tsx`（779）、`AssetSettingsPage.tsx`（561）、`AssetAccessPage.tsx`（183）、`AssetLineagePage.tsx`（218）、`CreateAssetForm.tsx`（399）、`CreateAssetModal.tsx`（59）、`DiscussionPanel.tsx`（局部）、`features/assets/api.ts`（240）、`types.ts`（232）、`shared/api/client.ts`（223）、`QueryBoundary.tsx`（49）、`SafeMarkdown.tsx`（64）、`RouteGuard.tsx`（62）、`routes.tsx`（97）、`PermissionProvider.tsx`、`usePermission.ts`、`navigation.ts`、`features/admin/api.ts` L62-72（对照样本）、`e2e/asset.spec.ts`、`e2e/permission.spec.ts`、后端联检 `NoopAssetRepositoryProvisioner.java`/`GiteaAssetRepositoryProvisioner.java` L66-69、`EVD-P0BR-050-001`/`EVD-P0BR-051-001`。

**已核验合规**（反向对照，逐条实读，含一处本节主动撤回的立项怀疑）：
- **凭据仅内存**：`tokenHolder.ts` L3 + `AuthProvider` L4 + `AuthProvider.test.tsx` L173-188 全量断言不写 localStorage；localStorage 全域仅语言键（`i18n/index.ts` L33/L59）——UI-001 L392 ✓（与 UPL-004 规范源同一，㉟ 已证后端侧）。
- **权限非默认全 Scope 且元数据共用**：`PermissionProvider` 从会话 scopes 读取（L3 注释自证移除骨架全 Scope）、`RouteGuard` 缺 Scope→403 Result、`routes.tsx` 全部业务路由 `guarded(..., scopes)`、`navigation.ts` L22-42 集中声明 requiredScopes——UI-001 L387-389 三条 ✓。
- **统一 API 层**：单一 `apiClient`（/api/v1、X-Request-Id、Accept-Language 注入 L61-76、401 单刷重试 L100-114、ApiError 规范化含 i18nKey L120-147）；features 全域无裸 `fetch`——UI-001 L386 的「不手写 URL」半边 ✓（「统一生成」半边见偏差①）。
- **乐观锁与 409 恢复**：`AssetSettingsPage` L79-94 expectedVersion=rowVersion + 409→conflictReload+invalidate 五 mutation 同模式；`AssetDetailPage` L219-226 deprecate 409 分支——UI-001 L395 ✓。
- **受控字典/标签**：`CreateAssetForm` 的 org/project/ownerTeam/tagIds/license/framework/task/format/modality/多 codes/sensitivity 全走 `ControlledSelect`（唯一例外：precision 字段 L263-265 为 `mode="tags"` 自由输入、未接受控字典——轻微，不单列缺陷）——AST-001/TAG 规范在前端侧基本 ✓。
- **Card 安全渲染**：readme/assetYaml 经 `SafeMarkdown`（DOMPurify 白名单 L31-44）+ `untrustedContent` 警告条 + `sourceCommit` 回显（Detail L398-421/L403-405）——AST-009 L316 ✓。
- **URL 同步**：列表 16 维筛选全走 `useSearchParams`（L53-97，replace:true）、详情 Tab/版本切换同步 URL（L171-181）——AST-009 L313 的「URL 可分享」半边 ✓（「保留返回位置」半边缺，偏差⑦）。
- **i18n 双语键对称**：zh/en 各 802 扁平键、双向缺失均为空集（本节脚本实测后删除脚本）。
- **两主体 E2E 套件在位**：`asset.spec.ts`（登记→搜索→详情→弃用/归档/恢复专用 Owner 主体→404 防枚举+UI 空态，且 L171 后端侧统一 404 断言引用 AssetAccessPolicy）+ `permission.spec.ts`（管理员/作者/OUTSIDER 三主体菜单与行级隔离差分守护 L71-111）——ER-AST-009-03 的「套件存在」✓（ER-UI-001-05 卡内自陈需 E4 Playwright，执行未证，偏差④）。
- **E4 级 UI 证据卡存在**：`EVD-P0BR-050-001`（status PASS：AC-P0B-UI-003 五态注入 HTTP 边界故障实证 + AC-P0B-UI-006 三主体 Chromium 死链回归，绑定 REQ-UI-001）——「UI 域完全无 E4 证据」假设不成立。
- **伪造 repo 不显示（撤回立项怀疑 W-H）**：`NoopAssetRepositoryProvisioner` L27 返回 `cloneUrl=null`（全仓 `gitea.invalid` 字面零命中，立项依据不成立），`GiteaAssetRepositoryProvisioner` L66-69 取真实 `clone_url`，前端 `AssetDetailPage` L432 `asset.repository?.cloneUrl &&` falsy 短路——AST-009 L314 前后端联合 ✓。

**缺陷面**：
- **W-1 asset 域全部写操作零 Idempotency-Key 接入（UI-001 L394 击穿，与 ㊴ Z-11 叠加成双穿）**：`features/assets/api.ts` 240 行全读，create L35-37 / update L40-45 / delete L63-66 / deprecate·archive·restore L97-116 / ACL L118+ / 讨论 / 血缘关系诸写**无一**附带 Idempotency 头；同库 `features/admin/api.ts` L62-72 早有 `newIdempotencyKey()`/`withIdempotency` 现成机制——服务端 header `required=false`（Z-11）× 客户端不发送 = 重试/双击重复提交防线两侧皆空。
- **W-2 列表与详情查询失败态混同「资产不存在」（五态违反 + 语义误导）**：`AssetsPage` L505-520 `query.isError` 全文未消费（Table 仅 loading + Empty、无 Error/Retry；共享件 `QueryBoundary` 在位但本页未用，admin 各页普遍使用）；`AssetDetailPage` L286 `if (query.isError || !query.data) return <Empty description={t('assets.notExist')} />`——临时网络/5xx 故障被呈现为「资产不存在」，用户无从得知应重试；比 UI-001 L391 的形态违反更重，因其给出**错误的确定性语义**。
- **W-3 变更按钮无 usePermission 门控（AST-009 L315 违反）**：`AssetsPage` L317-329 删除列、`AssetDetailPage` L560-574 弃用/归档/恢复、L606-654 提交发布/下载/DVC 凭证诸按钮一律无条件渲染；同域 `DiscussionPanel` L265-266 已示范 `hasScope('asset:moderate')` 门控——assets 域内标准做法存在但变更按钮未采用。后端强制点在位（`EVD-P0BR-050` 五态实证 + ㊴/㉕ 已证后端 fail-closed），故为**可见性缺陷**非安全缺口，违 UI-001 L388「导航/按钮共用权限元数据」的按钮半边。
- **W-4 facet 键盘可达性 + 计数维度双缺**：`AssetsPage` L484-489 facet 条目为 `div+onClick`，无 role/tabIndex/onKeyDown——AST-009 L317 键盘操作对 facet 不成立；facetsQuery 仅传 `{type, keyword}` 两参而列表查询 16 维——㊴ Z-2（后端契约 11:2 缺口）的前端侧确证：即便后端补齐参数，现调用面亦不自动获益。
- **W-5 弃用表单三点（AST-007 L255-256 + TAG 规范）**：`AssetSettingsPage` L481 `deprecationReason || undefined` + L502-513 Select `allowClear`——reason 可空提交在前端侧同穿（后端三层可空已由 ㊴ Z-8 定装）；replacementAssetId L518-520 为自由文本 `Input`（placeholder「ast_xxx」）非资产选择器；reason 选项为硬编码五值（恰为字典 itemCode 同名，字典联动未接）。
- **W-6 ACL 页三点（AST-009 L315 + PAGE-AST-005 L48）**：`AssetAccessPage` L150 principalId 为自由文本（无主体选择器、无「当前主体不可自提权」的前端确认）；L81-114 仅列原始 `principalId + permissionCodes` 绑定、无 Role/Scope/Membership/ACL 来源解释视图；L57-79 create/delete 幂等头缺失（归 W-1）。
- **W-7 血缘页三缺**：`AssetLineagePage` L149-158 上游/下游节点一律渲染为链接、未见不可见节点「仅占位不展元数据」处理（后端是否过滤未定位，登记为歧义而非缺陷实证）；L80-82 `onError: () => message.error(t('assets.lineage.createFailed'))` **丢弃后端具体错误**（全域其余页面均 `isApiError(err) ? err.message : …`）；L29-34 `RELATION_TYPES` 硬编码常量。
- **W-8 Detail 字典字段原始 code 直出（TAX 规范轻微）**：`AssetDetailPage` L307-312 visibility/license/ownership 等直显 code 无 `t()`/字典标签，同页 sensitivityCode L351-355 却有 t() 映射、`CreateAssetForm` 全走字典——页内自不一致。
- **W-9 DTO 面 organizationId 可透传（UI-001 L386 手写 DTO 漂移实例）**：`features/assets/types.ts` L147 `UpdateAssetRequest.organizationId?` 与后端 Z-4 三层贯通修改同族——前端无编辑入口但类型层放行。

**查重后新增结论计数与撤回**：W-1—W-9 为 ①—㊴ 未覆盖的**前端侧新证**（㊴ Z-11/Z-8/Z-4 为后端侧、㉕ 为权限码域——本节均只互引不重复计权）；立项怀疑撤回一处（伪造 repo 显示：Noop `cloneUrl=null` + 前端 falsy 短路，假设不成立）；「UI 域无 E4 证据卡」假设证伪一处（EVD-P0BR-050 PASS）。

**偏差登记（须人工裁决或后续取证，AI 不自决）**：① `package.json` 无 openapi-typescript/orval 等 codegen 依赖——UI-001 L386「统一生成 Client」实际以手写 `apiClient` + 手写 `types.ts` 落地，DTO↔契约漂移无机器检测（W-9 为其实例），口径须裁决；② AST-009 L311「表单按扩展 Schema 驱动」：`CreateAssetForm` 为手写静态 JSX——轻端偏差；③ 同一规范条款「写复用 Idempotency-Key」admin 域实现、asset 域零接入——UI-001 L394 与实现未拉平的双口径（修 asset 或改规范须人类裁决）；④ 证据卡存在性差集：`docs/ai-spec/tasks` 全目录 Grep `REQ-AST-009` 零命中、无一张卡的 acceptanceScenarios 绑定 AST-009，而规范 L319 要求组件/权限/两主体浏览器 E2E 齐备——E2E 套件存在（合规面）+ UI-001 侧 PASS 卡存在（EVD-P0BR-050），但 **AST-009 验收场景本身无任何证据卡绑定**；`asset.spec.ts`/`permission.spec.ts` 无运行记录、且带 `test.skip(!credentials)` 条件跳过——ER-AST-009-03 维持「套件在位、执行未证」（与 ㊴ 偏差① 的 ≤E2 总口径一致）；⑤ PAGE-AST-003 规定深链形态 `/assets/:id/{overview|settings|access|lineage}`，实现为 `/assets/:id?tab=` + 独立 `/settings|/access|/lineage` 路由——六页语义均覆盖，仅 URL 形态偏差（spa-deep-link.spec 与 EVD-P0BR-050 已证路由可达）；⑥ `SafeMarkdown` `ADD_ATTR:['target']` 强制新窗口但未同步强制 `rel=noopener`（DOMPurify 默认保留源属性、marked 不自动添加）——安全细则须人工确认；⑦ AST-009 L313「筛选写 URL 并**保留返回位置**」：全域未见 scroll-restoration 实现——URL 同步半边达成、返回位置半边未实施。

→ 本项与 ①—㊵ 一并不在本轮 AI 自决实施范围内。

### ㊶ P2 版本与传输残域（REQ-VER-001 + REQ-DVC-001 + REQ-DL-001）：**REQ ↔ 契约 ↔ Controller/Service ↔ 领域 ↔ DB 迁移 ↔ Job ↔ 证据卡 七方差集**：deprecate/archive 裸 ISE→500 与契约 409 声明正面冲突（同文件注释自证知晓陷阱却仅修半边）、审计主体 null、下载侧敏感度/用途/草稿三维检查零实施、乐观锁全链伪（DB 列+注释在位、仓储无谓词）、目标分支绑定无落点、createDraft 幂等三方漂移、TASK 声称 E4 PASS 对撞 EVD DRAFT 空壳

**立项与查重过程（先行声明，避免重复计权）**：立项前对 plan ①—㊵ 全文做 REQ-ID 查重：`REQ-VER-001`/`REQ-DVC-001`/`REQ-DL-001` 三块作为**专节对象零命中**（㉟ 仅引用同文件 L109 的 DVC hash 混称禁令、㊱ 仅覆盖 REQ-MNF-001、㊲ 覆盖 REQ-PUB/REV 发布治理、4.7 覆盖 presign 键布局）——三块为整域未覆盖，本节立项。划界口径：① 上传会话九面（UPL-001..004）已由 **㉟ U-a..U-r** 定装 → 本节不重复取 upload 链；② Manifest 规范化/摘要已由 **㊱ V 系列** 定装 → R 条目不涉规范化算法；③ 发布请求状态机/自审/评审已由 **㊲ X 系列** 覆盖 → 本节仅取 deprecate/archive/transition 的版本状态面与幂等面；④ `presigned_url` 持久化缺陷本体已由 **㉟ U-f** 计权 → 本节 V16 侧仅互引；⑤ 审计/Outbox catch-warn fail-open 族已由 **㉖/㉟ U-o** 定装 → 引用计权不重复；⑥ DVC 指针 hash 伪造已由 **㉟ U-i** 定装 → 本节 DVC-001 仅取 STS 权限面、L107 LFS 同径禁、L108 撤销后访问三个未覆盖条款。

**权威口径**：REQ-VER-001 `docs/ai-spec/01-requirements/p2-version-transfer.md` **L9-37**（permission `asset:update` **L15**、`idempotency: REQUIRED` **L16**、`minimumEvidenceLevel: E4` **L17**、创建时绑定 sourceCommit/创建者/**目标分支**/rowVersion **L25**、DRAFT 删除/取消语义需明确 **L31**）；REQ-DVC-001 同文件 **L88-117**（permission `asset:upload` **L95**、STS 短期凭据优先 **L103**、**L107 不允许 Git LFS 与 DVC 追踪同一路径**、**L108 权限撤销/凭据过期后不能访问未授权前缀**、L109 三摘要分存→㉟ U-i 已计权）；REQ-DL-001 同文件 **L225-260**（permission `asset:download` **L231**、idempotency NOT_REQUIRED **L232**、**L239 Sensitivity/License 用途检查**、**L240 P2 草稿仅维护协作者可下**、**L242 include/exclude/resume 稳定清单**、L245 15min 不可 List、**L253-254 四错误码含 DVC_OBJECT_MISSING**）。对撞判据：`05-acceptance/p2-exit-catalog.md` **L25**（AC-P2-VER-001 MUST/E4：重放 Idempotency-Key、绑定 sourceCommit/创建者/rowVersion）、**L61**（AC-P2-DL-001 MUST/E4：权限/**状态/敏感度/用途**检查、P2 草稿仅维护者可下）、**L67**（AC-P2-DVC-001 MUST/E4）。对撞面（全部实读或全称 Grep 定装）：`version/api/VersionController.java`、`version/api/ReviewController.java`（65 行全读）、`version/application/VersionApplicationService.java`、`version/application/PublishApplicationService.java`、`version/domain/Version.java` + `VersionStatus.java`、`transfer/application/DownloadApplicationService.java`（155 行全读）、`transfer/application/DvcConfigurationService.java`、`platform/storage/MinioStsCredentialIssuer.java`、`version/infrastructure/MyBatisVersionRepository.java`、`V16__version_transfer.sql`（192 行全读）+ `V17__release_governance.sql`、`contracts/openapi/aihub-v1.yaml` 版本/下载/DVC 诸 operation、`shared/error/ErrorCode.java`、`bootstrap/GlobalExceptionHandler.java`、`TASK-P2-002.md` + `EVD-P2001-001`/`EVD-P2002-001`。

**已核验合规**（反向对照，逐条实读；含一处本节主动改性——立项时「createDraft 服务端幂等零接入」怀疑被证伪，见 R-3）：
- **版本坐标 DB 唯一性 ✓**：V16 **L35** `ux_asset_version_coordinate UNIQUE (asset_id, version)`，服务层 `findByCoordinate` 预检（`VersionApplicationService` **L90-94**）+ `ASSET_VERSION_CONFLICT(409)`（`ErrorCode` **L181**）双层，竞态由 DB 兜底。
- **transition/publish 异常面 ✓**：`VersionApplicationService` **L127-133/L161-166** 两处 `transitionTo`/`publish` 均 catch 裸 ISE → `VERSION_STATE_NOT_ALLOWED(409)`——正证 R-1 的「同码不同款」是**同域内未拉平**而非全缺。
- **submitPublishRequest 前置检与幂等语义 ✓**：`PublishApplicationService` **L88-92/L107-132** 状态与三前置检均有映射意识（注释自证「裸 ISE→500」陷阱），已存在请求重放返回 existingId（**L96-105**，状态幂等非 header 幂等）；自审拦截→403（**L191-194**）、非 SUBMITTED→409（**L198-202**）✓。
- **下载票据 fail-closed 次序 ✓**：`DownloadApplicationService` **L64-67** 权限先行（注释自证防 404/409 探测枚举）、**L73-80** 非 PUBLISHED→409 `VERSION_STATE_NOT_ALLOWED`、审计直写不吞异常（对比 R-6 引用面）；TTL 15min 常量 **L37**（REQ **L245** 数值侧 ✓，键消费问题见 R-5）。
- **三摘要分存（DL/DVC 存储侧）✓**：V16 **L60-62** `version_artifact` dvc_file/dvc_hash/sha256 三列分立——REQ **L109** 的 schema 半边在位（写端伪造问题归 ㉟ U-i）。
- **发布请求 DB 幂等兜底 ✓**：V17 **L52** `ux_publish_request_version UNIQUE (version_id)`——同版本重复发布请求 DB 层拒绝。
- **错误码本体齐备 ✓**：`VERSION_NOT_FOUND`/`VERSION_STATE_NOT_ALLOWED(409)` **L187**、`DVC_OBJECT_MISSING(404)` **L202** 均在 `ErrorCode.java` 在位——R-2/R-7 缺的是**消费面与契约声明面**，非码本体。
- **ReviewController 权限口径 ✓**：类级 `/api/v1`、四 POST 全委托服务层（控制器零业务规则，符合 AGENTS.md 适配器约束），契约 `x-required-permissions:[asset:manage]` 与服务层 `requirePermission(ASSET_MANAGE)` 一致。
- **ValidationException 映射链闭合 ✓**：`ValidationException(String)`→`COMMON_INVALID_ARGUMENT`（L15-17）→`GlobalExceptionHandler` **L35-46** `errorCode.httpStatus()` 统一落 400——DVC 死字段报错语义有据（死字段本体见 R-5）。

**缺陷面（R-1..R-8）**：

| # | 要求（锚点） | 口径 | 实现证据 | 判定 |
| --- | --- | --- | --- | --- |
| **R-1** | 契约 **L3178/L3206** + REQ VER-001 状态机 | deprecate/archive 非法状态→`VERSION_STATE_NOT_ALLOWED(409)` | `PublishApplicationService` **deprecateVersion L349 / archiveVersion L377** `version.transitionTo(...)` **无 try-catch**——`VersionStatus.assertTransitionTo` **L33** 抛裸 ISE → `GlobalExceptionHandler` 兜底 **500**；同文件 **L89/L108 注释自证**知晓「裸 IllegalStateException 会被全局兜底映射为 500，误导客户端重试」且同域 `transitionVersion` **L127-133** 有 catch→409——**同码不同款、修半边上药** | **✗**（契约声明与运行态正面冲突） |
| **R-2** | 契约 **L3178/L3206** `x-audit-event: VERSION_DEPRECATED/VERSION_ARCHIVED` | 审计记录主体 | `deprecateVersion` **L351-354** / `archiveVersion` **L379-382** `new AuditEvent(..., null, null, ...)`——principalId/principalType **双 null**；对比同文件 submitPublishRequest **L145-149** 记真主体、`DownloadApplicationService` **L101-102** 经 `PrincipalContextHolder` 取真主体——审计**不可归因**，违 AGENTS.md 审计基线与 DL-001 100% 审计语义 | **✗** |
| **R-3** | REQ VER-001 **L16** + AC-P2-VER-001（05-catalog **L25**「重放 Idempotency-Key」） | createDraft 幂等 REQUIRED | **三方漂移（改性后口径）**：规范 REQUIRED **L16** ↔ 契约 createDraft operation（**L2854-2906** 区）**无 IdempotencyKey 参数声明** ↔ 实现 `VersionController` **L64-77** 有接入但 `required = false`（缺头即裸执行；`IdempotencyService` 缓存回放 201+serialize 在场）——立项时「服务端零接入」怀疑**证伪撤回**，改判可选弱化；transition **L100-110** 与 ReviewController 四 POST 无幂等头（VER-001 幂等条款未区分动作，登记口径张力） | **✗**（三方各说各话，须裁决） |
| **R-4** | REQ VER-001 **L25** | 创建时绑定 sourceCommit/创建者/目标分支/**rowVersion** | 创建者 ✓（`Version.createDraft` L96 传 principalId）；sourceCommit：DRAFT 创建时**不绑定**、由物化 Job 异步补绑（㉟ Handler **L180-184**，互引）；**目标分支：V16 asset_version 无 branch 列**（**L18-21** 仅 status/source_commit/manifest_digest/git_tag）——绑定**无落点**；**rowVersion：乐观锁全链伪**——V16 **L25** `row_version BIGINT NOT NULL DEFAULT 1` + **L49** 列注释「乐观锁版本号」在位，但 `MyBatisVersionRepository.update` **L46-57** WHERE 无 `row_version` 谓词、SET 无自增——并发转换后写覆盖前写，AC-P2-VER-001「冲突处理」判据（05-catalog **L25**）运行态不成立 | **✗**（四要素仅 1.5/4） |
| **R-5** | REQ DL-001 **L231/L239-240/L242** + AC-P2-DL-001（05-catalog **L61**「权限/状态/**敏感度/用途**；P2 草稿仅维护者可下」） | 权限码口径 + 下载三维检查 + include/exclude/resume 清单 | **权限码口径（引用计权）**：REQ **L231** 要 `asset:download`、实现走 `asset:read` 字面量（**L67**）——零接线本体由 **㉕ L1179-1181** 定装、「read vs download」口径回请由 **4.7 / TASK-P0BR-081 §9** 登记 → 本节仅补 REQ 锚点 L231 映射；**新证（三维检查）**：`DownloadApplicationService`（155 行全读）仅查 asset:read + PUBLISHED，transfer 包全称 Grep `sensitivity\|usage\|purpose` **零命中**（命中者仅为 SQL `EXCLUDED` 关键字，三词本体零）——敏感度/License 用途/P2 草稿维护者三维**全无实施**；**L242** include/exclude/resume 稳定清单无对应参数（单 artifactId 或 GIT_DVC 两形态）；对象键面：`resolveBucket` **L138-140** 恒返 `"dvc-cache"`、`resolveObjectKey` **L142-144** 与 `dvcObjectKey` 漂移——**本体由 4.7 L300-301 + TASK-P0BR-081 三层前置定装，引用计权**（增量：三方键布局互斥面登记，与 ㉟ staging 布局并置）；`transfer.presignedUrl.ttlSeconds` 配置键**无消费者**（**L37** 硬编码 15min，**新证**） | **✗**（AC 判据三维 + 清单为域内新证；权限码/键布局引用计权） |
| **R-6** | REQ DVC-001 **L103/L107/L108** | STS 优先；LFS/DVC 同径禁；撤销/过期后不能访问未授权前缀 | `DvcConfigurationService` **L51/L88** 以 **asset:read**（只读码）签发含 **Put/Delete** 的 MinIO STS 写凭据（`MinioStsCredentialIssuer` **L60-75** session policy 实证）——权限放大 + 撤销语义（**L108**：撤 read 即断供为巧合而非设计）；**L107** Git LFS 与 DVC 同路径互斥校验：transfer/version 全域 Grep 零命中，**无实施**；**L103** STS 优先的「优先」降级路径（长期凭据回退）未定位到禁止面，登记歧义 | **✗**（L107 整条缺、L108 语义错位） |
| **R-7** | 契约 download operation + REQ DL-001 **L253-254** | 四错误码声明（含 `DVC_OBJECT_MISSING`） | 契约 download `x-error-codes` **缺 DVC_OBJECT_MISSING**（码本体 `ErrorCode` **L202** 404/WARN 在位、零消费——静态 Grep 全仓无抛出点）；Agent POST download 端点**契约零声明**（实现存在，㉟ 双入口族互引） | **✗**（契约↔实现↔码三方各缺一角） |
| **R-8** | 05-catalog **L25/L67** + AGENTS.md 证据门禁 | AC-P2-VER-001 / AC-P2-DVC-001 证据等级 | `TASK-P2-002` **L98-99** 行为切片声称两 AC `Execute \| PASS \| E4`；`EVD-P2001-001`/`EVD-P2002-001` 实态 **status: DRAFT** 空壳（requirements 空、proven []）——**等级自声称与证据卡直接矛盾**，㉟ U-m 同族**首次跨域复现**（不同 Task 卡、不同 AC，佐证为系统性证据治理缺陷而非个案） | **✗**（证据门禁不成立） |

**查重后新增结论计数与撤回**：R-1/R-2（deprecate/archive 异常与审计面）、R-4（目标分支无落点 + 乐观锁全链伪的 DB↔仓储对撞）、R-5（敏感度三维零实施 + include/exclude/resume 缺）、R-6（L107 整条缺 + L108 语义错位）、R-8（E4 声称对撞 DRAFT 空壳）为 ①—㊵ 未覆盖的**新证**；撤回一处：立项时「createDraft 服务端幂等零接入」怀疑（`VersionController` **L64-77** 实有接入，改性为 R-3 三方漂移，与 ㊴ Z-11 的 `required=false` 同族退化口径拉平）；引用计权不重复相加：㉟ U-f（presigned_url 落盘）、㉟ U-i（DVC hash 伪造）、㉖ fail-open 族（`PublishApplicationService` **L419-435** notifySubmitter/publishOutbox catch-warn 为本域再现例）、4.7 presign 键布局、㊴ Z-11。

**未实测清单（unproven）**：① PRESIGNED_URL 的 `resolveObjectKey` 拼接键与物化实际写入键在运行态是否一致（静态三方不一致已登 R-5，物化键格式未逐字节复验）；② deprecate/archive 触发 500 的具体非法态路径（如 DRAFT→DEPRECATED：`VersionStatus` **L25** DRAFT 仅可 →VALIDATING/ARCHIVED，静态推定必抛，未实测）；③ STS 写凭据在真实 MinIO 下的前缀边界行为（L108 撤销语义）；④ 乐观锁伪的并发覆盖窗口（无 DB 环境）；⑤ MCP/Agent 通道的 deprecate/archive 是否同走 `PublishApplicationService` 同缺陷面（静态推定同，未逐行实定）；⑥ R-8 两 EVD 卡后续是否已被补跑重出（以当前磁盘态 DRAFT 为准）。

**取证偏差登记**：① **改性**：createDraft 幂等从「零接入」改「三方漂移」——教训固化：幂等判定必须契约/Controller/Service 三层分别核，不得由契约缺参数推断服务端缺实现；② **升级**：deprecate/archive 异常面由「未核」升「缺陷实证」——三轮读取定装（catch 缺失 + 同文件注释自证 + 同域对照组）；③ 契约 x-error-codes 全域 Grep 因输出截断（head_limit 100 止于 L1301 非版本域条目），版本域 **L3178/L3206** 以定段 Read 单独定装，无遗漏差集新增；④ ValidationException 映射链经 `GlobalExceptionHandler` 基类覆盖闭合，撤销「死字段报错无映射」候选怀疑（映射为 400 COMMON_INVALID_ARGUMENT，语义可接受）；⑤ 本域立项第一动作已执行 `^### ` 标题枚举 + 三 REQ-ID 全文查重（纪律合规）。

**限定与更正**：① R-3 的 transition/review 四 POST 无幂等头：VER-001 幂等条款是否覆盖全部写动作存在规范解释空间，登记口径张力不径行判反；② R-4 的 sourceCommit 异步补绑行为本体已在 ㉟ 计权，此处仅取「L25 绑定四要素验收判据不成立」的规范侧映射；③ R-6 的 STS 权限放大与 ㉟ U-b（asset:upload 具文）为同一细粒度权限缺失的两面，权重从低不重复计；④ `DVC_OBJECT_MISSING` 零消费与其说缺陷不如说「DL 清单/对账面未实施的症状」（R-5 主面），单列 R-7 仅为契约声明缺口；⑤ 本节涉契约/REQ/TASK/EVD/迁移五类禁改面文件**只登记不改动**；⑥ 全部结论为静态取证，E4 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① **R-1/R-2（同批同点）**：deprecate/archive 两处 `transitionTo` 补 catch→409（照抄 `VersionApplicationService` **L127-133** 先例）+ AuditEvent 主体改 `PrincipalContextHolder` 真值（照抄 Download **L101-102** 先例）；② R-3：契约 createDraft 补 `IdempotencyKey` 参数声明（非 breaking）+ header 改 `required=true` 或 ADR 裁决口径，三方拉平须人类定；③ R-4：仓储 update 补 `WHERE row_version = ?` + SET 自增 + 冲突→409（或 ADR 裁决删列删注释，消灭「注释承诺」与「实现事实」分裂）；目标分支绑定：迁移补列或 ADR 修订 L25 措辞二选一；④ R-5：下载权限码切 `asset:download`（契约 **L3604** + 服务层 **L67** 同步，落地 ㉕ 权限码整改面、与 ㉟ U-b 整改② 同族同批，breaking 评估走流程）+ 下载侧补敏感度/用途/草稿三维检查（对齐 AC-P2-DL-001 判据）+ include/exclude/resume 清单参数或 ADR 降级；统一对象键布局（单一权威 builder）；ttlSeconds 配置键接线或删除；⑤ R-6：STS 策略改只读或拆签发（read→只读凭据、upload→写凭据分码）+ L107 同径互斥校验入 createDraft/物化路径；⑥ R-7：契约 download 补 `DVC_OBJECT_MISSING` 声明 + Agent POST download 补契约登记；⑦ R-8：EVD-P2001/2002 如实重出具（DRAFT→实际等级），TASK-P2-002 **L98-99** 的 PASS@E4 由验证权威撤回或补跑——**并入 ㉟ 整改⑬ 及 ㉛ 域的证据门禁一致性守卫同一整改批**。

→ 本项与 ①—㊶ 一并不在本轮 AI 自决实施范围内。

### ㊷ REQ-LOG-001 结构化运行日志与脱敏（stdout 日志面对撞）

**立项与查重**：REQ 全域覆盖普查定装 `REQ-LOG-001` 为 ①—㊶ 零成节域（八文件 82 个 REQ-ID 清单对撞 22 个已立节划界后择定）。plan 全文 Grep `脱敏|SensitiveDataMasker|AUD-00\d|ecs|structured|MDC|LogFields` 共 29 处命中，均属 ㉖-H（**L1324-1326**：masker 无长度上限、AUD-005 审计面无实现）、㉘（**L1418**：`last_error` 注释自称已脱敏不实）、㉙（**L1521-1522**：`job_attempt` 脱敏摘要）、㉛（**L1767-1768** E4 job 跑而不论、**L1777-1778** AUD-005 悬空引用）、㊳（**L2013**：`maskMessage` 落库合规面）的**落库字段面与门禁面**——**stdout 日志面从未被成节实证**，本节补位；上述既登记面一律引用计权、不重复相加。

**权威锚点**：`01-requirements/p0b-platform-services.md` **L217-247**（`READY`/`MUST`/P0-B/**E4**）：行为十条 **L228-237**（L229-230 列举 15 字段：service/module/traceId/spanId/requestId/principalType/principalId/organizationId/projectId/resourceId/jobId/action/result/errorCode/durationMs）；七面 canary 验收 **L241-242**；追踪 `AC-P0B-AUD-002/004/005`（`05-acceptance/p0b-exit-catalog.md` **L106/L108/L109**，全 `MUST`/E4）。权威任务卡 `tasks/TASK-P0BR-026.md`。

**缺陷矩阵（L-1..L-8）**：

| ID | REQ 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **L-1** | L229-231 字段由 Filter/Task context 自动注入、业务不拼接 | `shared/logging/LogFields.java` L16-25 仅 4 键（requestId/traceId/principalId/principalType）；全仓 `MDC.put` 仅 `PrincipalContextFilter` L78-79 与 `JwtAuthenticationFilter` L122-123 两处 → 注入覆盖 **4/15**，spanId/jobId/resourceId/durationMs 等 11 字段无任何注入载体；`JobWorker` L183/208/225/244/252 手工拼 `jobId={}` 进 message（违「业务不拼接」）。AC-P0B-AUD-004 判据（五字段关联）在日志面静态不成立 | **✗** |
| **L-2** | L230 organizationId/projectId | `PrincipalContext` L29-39 有字段，但 main 全部三个组装点均置空：`JwtAuthenticationFilter` L112-113 硬编码 `null`/`List.of()`（`JwtClaims` L29-38 无 org/project claim）、`PrincipalContextFilter` L66-73（认证前，合理）、`DiscussionApplicationService` L289-290（合成上下文）→ 两字段**结构性恒空**，非「未接 MDC」而是上游无来源 | **✗** |
| **L-3** | L228 stdout 统一 JSON encoder | Glob `logback*.xml` **零文件**；`application.yml` L173-177 `logging.structured.format.console: ecs` **仅 compose profile**（L168 `on-profile: compose`），api/worker profile 段（L183-186 起）及默认段无任何 logging 键；`pom.xml` Grep `elastic|ecs-encoder` **零命中**（Boot 3.5.0 的 ecs 格式需外部编码器依赖） | **✗**（compose 下 ecs 运行态是否真出 JSON **unproven**） |
| **L-4** | L232/L234 八类机密统一脱敏 + URL 去敏感 query | `SensitiveDataMasker` main 消费方仅 `AuditService` L119/121、`NotificationService` L198/200、`JobWorker` L322——**全部是落库面**；stdout 输出边界零挂接（类注释 L18 自证无日志框架依赖、无 logback 配置可挂）；`McpController` L277 原文记 `uri={}`（预签名 query 剥离无载体）、L196 message 含 `e.getMessage()` | **✗** |
| **L-5** | L233 正文白名单 + 大小上限 | 日志面无任何正文过滤器/拦截器；与 **㉖-H** 同源（masker 只替换不截断），日志面同样无机制 | **✗**（㉖-H 引用计权，同整改批） |
| **L-6** | L235 异常堆栈只在统一边界记录一次 | HTTP 面 `GlobalExceptionHandler` L55-57 唯一 ✓；worker 面多边界并存：`JobWorker` L244-245 `LOG.warn(..., ex)` 打堆栈且堆栈内容不经 masker、`JobLeaseReconciler` L144、`JdbcIdempotencyStore` L125、`RecurringJobBootstrapper` L93；MCP 面反向缺位：`McpController` L195-198 仅记 `e.getMessage()` **永不出堆栈** | **✗**（多处 + 反向缺） |
| **L-7** | L241-242 七面 canary 扫全部容器日志+审计表 | `verify.sh` L303-321 仅 ADMIN_PASS 单值 canary、仅 `logs backend --tail 400`；`verify.ps1` L455 同型；`preview-pressure-drill.sh` L236-245 仅 scenario B（tail 1000）；`p4-onboarding.sh` 仅证据文件扫描——Header/Cookie/JSON/Form/Query/Job Payload/外部异常七面矩阵与 worker 等全容器扫描**不存在** | **✗** |
| **L-8** | E4 证据门禁（AGENTS.md 完成判据） | 卡 `TASK-P0BR-026` status `READY`/E4 但 `scenarioEvidencePlan: []`（L20）、`approvedBy: null`（L51）；唯一证据卡 `EVD-P0BR026-001.yaml` 标 `status: PASS`（L3）而：requirements 登记 **REQ-AUD-001**（L7）与卡面 REQ-LOG-001 **错位**、scenarios `[]`（L8）、evidenceLevel **E2**（L43）、proven 仅伪场景 "implementation-verified"（L47-48）、`review.accepted: false`（L74）。**本轮实测** `-CheckCompletion` → **FAILED 21 项**（含「No completion Evidence proves AC-P0B-AUD-002/004/005」）；`EVD-P0B-V01-V12.yaml` L155 AUD-003~005 显式 `notProven`、L134-136 AUD-002 proven 断言仅及审计表 | **✗**（与 ㉟ U-m/㊶ R-8 同族**首次复现于 P0-B 平台域**） |

**合规面（对照）**：① L237 线程复用前清理链完整——`PrincipalContextFilter` L86-89 finally `MDC.clear()`、`ContextPropagatingTaskDecorator` L25/31/37 copy-then-clear、`JwtAuthenticationFilter` L122-123 认证后补写；② `JobWorker` L135 tick 永不抛出保调度（㊳ L2013 已计权）；③ HTTP 面堆栈边界唯一（L235 该面满足）。

**查重后新增结论计数**：L-1/L-2（注入覆盖 4/15 + org/project 上游无源）、L-3（ecs 仅 compose 且疑缺依赖）、L-4（日志输出边界脱敏零接线）、L-6（worker 多堆栈边界 + MCP 反向缺）、L-7（七面 canary 不存在）、L-8（PASS 证据卡对撞 -CheckCompletion FAILED 实测）为 ①—㊶ 未覆盖新证；L-5 引用计权 ㉖-H；落库面脱敏（㉘/㉙/㊳）与 CI 门禁（㉛-L1767）不重复计。

**未实测清单（unproven）**：① compose 运行态下 `ecs` 格式在编码器依赖缺失时 Boot 3.5.0 的行为（启动异常 vs 静默回退 plain，未起容器实测）；② 现有 stdout 是否已实际出现 L232 八类机密原文（无脱敏机制已静态定装，全仓 message 参数注入面未穷举）；③ `McpController` L277 `uri` 运行态可否携带预签名 query；④ `JobWorker` L245 堆栈文本是否含 job payload 机密；⑤ L-8 以当前磁盘态为准，EVD 若被补跑重出需重验。

**取证偏差登记**：① 前段摘要口径「16 字段」经 L229-230 原文逐词清点修正为 **15 字段**；② `-CheckCompletion` 于当前 worktree 实跑（exit 1，21 项）而非引用他方声称——为 ①—㊷ 中首个对既有 Task 完成门禁的**直接实测反证**；③ 「MDC 注入点」以 `MDC.put` 精确检索定装为两处文件（TaskDecorator 用 setContextMap 传播而非新增键），未把传播面误计为注入面。

**限定与更正**：① L-3 不径行判「compose 日志非 JSON」——Boot 对 `logging.structured.format.console` 的回退行为属运行态问题，只登记静态缺依赖 + 默认面明文无结构化两事实；② L-6 worker 面多边界是否违「统一边界」存在规范解释空间（job 失败堆栈或可辩护为独立边界），登记但权重从 L-1/L-4；③ L-8 缺陷本体在**证据治理**而非日志代码，整改归 ㉟ 整改⑬/㉛ 证据门禁同批；④ 本节涉 REQ/TASK/EVD/验收脚本四类禁改面**只登记不改动**；⑤ 全部代码结论为静态取证，E4 一律 unproven。

**整改待批（须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① L-1/L-2：`LogFields` 扩至 REQ 列举 15 字段，jobId/traceId 经 `JobWorker`→`ContextPropagatingTaskDecorator` 入 MDC（删除手工拼接）、resourceId/action/result/errorCode/durationMs 经拦截器注入；org/project 主张先经 ㉚ 认证面（JWT claim 扩展或资源侧解析）定源；② L-3：ECS/logstash 编码器依赖入 `pom.xml` + `logging.structured` 提至默认 profile，删除 compose 独占；③ L-4/L-5：logback converter 层挂 `SensitiveDataMasker`（message+arguments 包裹）+ URL/正文白名单与大小规则（与 ㉖-H 长度上限同批）；④ L-6：堆栈边界收敛统一面或 ADR 裁决 worker 例外；MCP L195-198 补堆栈（带脱敏）；⑤ L-7：verify 脚本扩七面 canary 矩阵 + 全容器扫描，`EVD-P0B-V01-V12` AUD-002 断言补日志面；⑥ L-8：`EVD-P0BR026-001` 如实重出具（等级/需求/scenarios 与卡面对齐），卡补 `scenarioEvidencePlan` 与审批元数据——并入 ㉟ 整改⑬/㊶ 整改⑦ 统一证据门禁整改批。

→ 本项与 ①—㊷ 一并不在本轮 AI 自决实施范围内。

### ㊸ REQ-OBS-001 指标、Trace、健康、诊断与告警（观测整域对撞）

**立项与查重**：REQ 全域覆盖普查定装 `REQ-OBS-001`（`01-requirements/p0b-platform-services.md` **L344-372**）为 ①—㊷ 零成节域。plan 全文 Grep `REQ-OBS-001|OBS-00` 共 12 处命中，全部属 **㉛** 的 NFR 门禁面（**L1729-1731**：`NFR-OBS-001` △ / `NFR-OBS-002` ✗ / `NFR-OBS-004` ✓ 三行 + 合规面⑥ traceId 链真实 + 缺陷面 **K**（**L1776-1778**：`AUD-005` 悬空阻塞 OBS 整组）+ 整改⑪（**L1794**：OTel 路线须人类裁决））与 **㉜** 缺陷面 K 的 Span Link 半句（**L1543**，REQ-JOB 口径）——**REQ-OBS-001 十条必需行为（L357-366）的指标接线、告警面、Compose 观测栈与证据链整面从未成节实证**，本节补位；上述既登记面一律引用计权、不重复相加。**双口径声明**：REQ 本体 `status: OPEN`/`blockedBy: [AUD-005]`（**L347/L350**），而 `TASK-P0BR-029/030` 卡面对同一 REQ 标 `READY` + `implementationAuthorized: true`（029 **L4-5**、030 **L4-5**）——立项矛盾本身计入 O-8；OPEN 期间 OBS 任何「达标」表述均不成立。

**权威锚点**：必需行为十条 **L357-366**；验收 `AC-P0B-OBS-001..005`（`05-acceptance/p0b-exit-catalog.md` **L125-129**，OBS-005 为 P0-B 唯一 **E5** 故障注入场景）；权威任务卡 `tasks/TASK-P0BR-029.md`（OBS-001..004）/ `TASK-P0BR-030.md`（OBS-005）。

**缺陷矩阵（O-1..O-8）**：

| ID | REQ 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **O-1** | L358/L359 全链 Trace 贯通 + Worker Span Link | `backend/pom.xml` Grep `tracing\|opentelemetry\|otel\|brave` **零命中**（本轮实测；**㉛** L1730 已证，引用计权）；`PlatformMetrics` **L25** 注释自认「不引入额外 OTel agent 依赖」。本节新增**四面缺口取证**：① 网关——`deploy/compose/nginx/nginx.conf` 29 处 `proxy_set_header` 仅标准四件（**L53-56** 等）、全文件**无 `log_format`/`access_log` 自定义**（Grep 零命中）→ Nginx 不生成 traceparent、访问日志与 trace 零关联；② 浏览器端——`frontend/src` Grep `traceparent` **零命中** → 上游无注入，traceId 全靠后端 `PrincipalContextFilter` **L101-106** 自生成（该链真实但仅覆盖后端进程内，**㉛** 合规面⑥ 口径）；③ 外部依赖侧——`com/aihub/integration` 全域 Grep `traceparent\|Tracer\|SpanContext` **零命中** → Gitea/MinIO 调用不传播；④ Worker 侧——`com/aihub/job` 全域 Grep `MDC\|traceparent\|Span` **零命中**，`JobWorker` **L159/L177/L207/L220/L239/L250** 仅 `traceId` 列传落库，无新 Span/Span Link（**㉜** L1543 引用计权） | **✗**（结构性缺失；AC-P0B-OBS-001 静态不成立） |
| **O-2** | L357 受保护 Prometheus | 保护面 ✓：`SecurityConfiguration` **L77** permitAll 仅 `/actuator/health(/**)`、**L82** `anyRequest().authenticated()`，`/actuator/prometheus` 落认证后；`application.yml` **L49** exposure 含 prometheus、**L74-75** 无 `management.server.port` 独立口（与业务共 8080 共链）。**对撞面**：`prometheus/prom.yaml` **L9-12** 唯一抓取 job `metrics_path: /actuator/prometheus` **无任何 authorization/凭据/TLS 配置** → 结构推论每次抓取 401 | **△**（「受保护」半句 ✓；抓取可达性**未实测，unproven**） |
| **O-3** | L363 指标覆盖设计清单 | **Counter/Timer 族接线完整**（`platformMetrics.record*` 实测消费点：`AuditService` L104、`WebhookDeliveryService` L210、`McpController` L186/192/197、`JobWorker` L180、`MinioStorageReconciler` L170、`PreviewJobHandler` L740、`PreviewApplicationService` L352-354、6 个 Reconciler、`WebhookInboxHandler` L152、`WebhookInboxApplicationService` L107）；**但 Gauge 族五更新法 `updateJobQueueDepth`（**L136**）/`updateJobDeadCount`（**L143**）/`updateWebhookInboxPending`（**L150**）/`updateUploadSessionsActive`（**L172**）/`updateAssetCountByStatus`（**L182**）在 `backend/src/main` 全名检索**仅命中 5 行定义本身、零调用点** → 注册的 `aihub_job_queue_depth/job_dead_count/webhook_inbox_pending/upload_sessions_active/asset_count_by_status`（**L55/L58/L61/L64** + map）**恒为 0**。连带后果：`alerting-rules.yaml` **L7/L16/L36** 三条规则（JobQueueBacklog/JobDead/WebhookInboxBacklog）指标源断链**永不触发**；`aihub-dashboard.json` **L31/L40/L53/L97/L106** 五个面板恒空。同应用**双轨分裂**：`JobConfiguration` **L54-71** 以 repository 直探注册 `job.queue.size{status}` 真实值、`JobMetricsAdapter` L28-30 桥给 `/system/metrics/summary`——诊断摘要面是实数，Prometheus `aihub_*` Gauge 面是死数 | **✗** |
| **O-4** | L364 安全事件有告警 | `AlertService` **L165-175** @Scheduled 面四项检查（DEAD_JOB/DEAD_DELIVERY/OUTBOX_BACKLOG/JOB_DEAD）+ 事件驱动 DEP_UNHEALTHY/STORAGE_QUOTA，**无任何权限拒绝/安全类检查**；`alerting-rules.yaml` 实测 **5 组 11 条**（组名 L4/L33/L53/L73/L93），Grep `denied\|security\|403\|401` **零命中** → 规则面亦无安全类。数据源并非不存在：`aihub_audit_events_total{result="DENIED"}` 可产出（`AuditService` L104 透传 result；main 显式 `AuditResult.DENIED` 共 8 处：`AgentController` L334、`McpController` L227/243、`AuthenticationApplicationService` L130/135/171/253/271、`UserManagementApplicationService` L186），但**一般 HTTP 401/403 拒绝（`SecurityResponseWriter` 路径）不进该计数器也无告警** | **✗**（业务级 DENIED 有指标无告警；协议级拒绝无指标无告警） |
| **O-5** | L364 告警面质量 | `DEAD_JOB`（**L178-192**，CRITICAL，`DEAD_JOB_THRESHOLD=1` **L45** 硬编码）与 `JOB_DEAD`（**L228-242**，WARNING，`jobDeadThreshold` **L79** 默认 1）对**同一 SQL**（`SELECT COUNT(*) FROM job_task WHERE status='DEAD'`，L179-180 vs L229-230）同阈值**双告警**，一次 DEAD 恒产两条 `system_alert` | **△**（冗余；分级语义或可辩护，权重从低） |
| **O-6** | L365 observability Profile 含 Collector/Prometheus/Grafana | `compose.yaml` **L204-233**：prometheus（**L206-217**）+ grafana（**L219-233**）均在 `profiles: ["observability"]` ✓；**全文件无 Collector/otel 服务**（Grep 零命中）→ 实落 2/3。Loki 不入 profile 与「不进入 P0-B 验收断言」**合规** ✓ | **✗**（三分之一缺位） |
| **O-7** | L366 目标是实际 Trace/Target/Dashboard/Alert 证据 | 证据卡四类 artifacts 全空：`EVD-P0BR029-001.yaml` **L52-58** 与 `EVD-P0BR030-001.yaml` 同型（reports/logs/screenshots/**traces**/**metrics**/databaseQueries 均 `[]`），却 `status: PASS`（各 **L3**）；proven 仅伪场景 "implementation-verified"（029 **L47-48**、030 **L47-48**）且 030 断言「Grafana alerting rules…Recovery detection」是**文件存在性**而非告警触发证据。**对撞面**：`EVD-P0BR045-001.yaml` **L71-72** 以「V11 + V21」对 `AC-P0B-OBS-001..005` 宽泛声称 **E4/E5 PASS** 且 **L75 `notProven: []`**——`verify.sh` 实读证伪：V11（**L961-969**）仅测 `/actuator/health` 200 + `/system/dependencies` 默认拒绝/有权，V21（**L1229-1238**）仅查 `system_alert` 表与五字段存在性；全脚本 Grep `prometheus\|9090\|liveness\|otel\|collector` **零命中** → Prometheus Target UP、Trace 关联、liveness/readiness 故障分层、E5 故障注入告警**从未被任何脚本断言** | **✗** |
| **O-8** | AGENTS.md 完成门禁（DEC-009） | 卡面：029/030 均 `scenarioEvidencePlan: []`（**L21**/**L18**）、`approvedBy: null`（**L52**/**L49**）、`baseCommit: 0b152f3`（7 位短 SHA，**L7**）、REQ `OPEN` 而卡 `READY`；证据卡：EVD-029/030 `evidenceLevel: E2`（各 **L43**）< 卡要求 E4（029 **L44**）、scenarios `[]`（**L8**）、`notProven` 自认无 Docker（**L49-50**）、`accepted: false`（**L74**）——**却各自 `governance.completionValidation.result: PASS` 自称**（**L20-22**）。**本轮实测直接冲突**：`validate-task-card.ps1 -TaskPath TASK-P0BR-029.md -CheckCompletion` → **exit 1，24 项**（含「Requirement is not READY: REQ-OBS-001」「Evidence level below Task requirement」「No completion Evidence proves acceptance scenario: AC-P0B-OBS-001..004」）；030 同命令 → **exit 1，18 项**（含 AC-P0B-OBS-005 同型四项）。较 **㊷ L-8**（卡面假 PASS，**L2168**）升级为「**EVD 内嵌 completionValidation 字段自称 PASS 与门禁实测相反**」形态，系 ㉟ 整改⑬/㊶ 整改⑦/㊷ 整改⑥ 证据治理同族**第三次复现**且最严重（自称的校验命令与实际结果逐字冲突） | **✗** |

**合规面（不得在后续叙述中被写成缺陷）**：① L360 健康分层 ✓——`application.yml` **L52-58** liveness 仅 `livenessState`、readiness 仅 `readinessState,db`，`PostgresHealthIndicator`（bootstrap）**L14-19** 注释自证「DOWN→摘流量、不影响 liveness」、**L32-41** 短超时探测（故障行为 E4 **unproven**）；② L362 诊断端点受权 + 脱敏 ✓——`SystemDiagnosticsController` **L53/L68**、`SystemAlertController` **L45/L52/L59** 均 `requirePermission(SYSTEM_OBSERVE)`；`DependencyStatus` **L18** record 仅 `(name, status, latencyMs)`，`SystemDependencyService.summarize` **L64-69** 聚合无 Endpoint/凭据/拓扑 → 脱敏结构成立；权限注册链 V4 **L192/L217** + V23 **L50**（observer 角色）齐备；③ L361 `/system/metrics/summary` 端点存在且经 `JobMetricsPort` 取**真实**仓储计数，与 Prometheus 面解耦 ✓（「不替代 Prometheus」半句在 O-3 修复前实质被违反——同一应用两套 job 计数事实，登记于 O-3 不单列）；④ traceId 关联链（Filter→MDC→响应头→job 列→审计落库）真实存在——**㉛** 合规面⑥ 口径，不得写成零可观测；⑤ 告警 upsert 幂等 ✓——`hasFiringAlert`（**L244-248**）去重 + `resolveAlert` 回落恢复；⑥ `rule_files` 已挂接（prom.yaml **L5-6** ↔ compose L211 双挂载路径一致）✓；⑦ Counter/Timer 指标族接线完整（O-3 表左半）。

**查重后新增结论计数**：O-1 四面（网关/浏览器/外部依赖/Worker 上下文）为 ㉛（NFR 门禁面）/㉜（Span Link）之外的增量取证；O-2 抓取端无凭据对撞、O-3 五 Gauge 零接线（含 dashboard 5 面板/3 规则断链与双轨分裂）、O-4 安全告警零源（含 DENIED 计数有源无告警的精确边界）、O-5 DEAD 双告警、O-6 Collector 缺位（2/3 判定）、O-7 四 artifacts 空数组 + `EVD-P0BR045-001` 宽泛声称 ↔ `verify.sh` V11/V21 断言体对撞、O-8 completionValidation 自称 PASS ↔ 本轮实测 exit 1（24/18 项）——均为 ①—㊷ 未覆盖新证。AUD-005 阻塞（㉛-K）、OBS-002 ✗（㉛ L1730）、Span Link（㉜ L1543）引用计权不重复相加。

**未实测清单（unproven）**：① Prometheus 抓取是否实际 401（O-2 结构推论，未起 observability profile 实测）；② 三断链规则不触发、五面板恒零为 Gauge 零接线的静态推论（运行态未采数）；③ Target UP、全链 Trace、liveness/readiness 停库故障行为、E5 故障注入告警恢复——AC-P0B-OBS-001/002/003/005 全部 E4/E5 判据**从未运行**；④ `SecurityResponseWriter` 403 路径是否有本节未检索到的间接计数；⑤ 结论以当前磁盘态为准，EVD 若被重出需重验。

**取证偏差登记**：① **指标接线核查两步法定装**：首轮 Grep 方法名得 24 hits 多为 `PlatformMetrics` 定义行与 `record*` 合法消费，险些误判 Gauge 有接线；二次以 `platformMetrics\.(update…)` 与五法**全名**检索收敛为**仅定义 5 行、零调用**——教训固化：接线结论必须以「实例.方法」调用点形态检索；② 工作稿「12 条规则」实测修正为 **11 条**（`- alert:` 全集 Grep）；③ nginx 项**不写**「nginx 截断 traceparent」——Nginx 默认转发未知请求头，成立的事实是「不生成、不记日志、上游前端不注入」三件套；④ dashboard 断链面板实测 **5 个**（L31/L40/L53/L97/L106），非工作稿 4 个。

**限定与更正**：① O-2 不径行断言「抓取必败」，只登记服务端保护与抓取端无凭据的结构性对撞；② O-5 双告警的 CRITICAL/WARNING 分级存在可辩护空间，权重从低；③ O-3 结论限「五 Gauge 零接线」，**不外推**为「Prometheus 面全废」（Counter 族、`job.queue.size`、JVM/Hikari/http 指标真实可抓）；④ REQ `OPEN`/blockedBy AUD-005 的达标准予属人类裁决（㉛ 整改⑪），本节仅证「即便按替代口径，O-1/O-3/O-4/O-6 亦静态不成立」；⑤ 本节涉 REQ/TASK/EVD/验收/部署五类禁改面文件**只登记不改动**；⑥ 全部结论为静态取证，E4/E5 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① O-3：五 `update*` Gauge 接线（照 `JobConfiguration` L54-71 repository 直探模式补定时回填），或将 alerting-rules L7/L16/L36 与 dashboard 五面板改引既有实数指标（`job.queue.size{status=dead}` 等）——二选一 + 同步 AC-P0B-OBS-002 的 E4 断言；② O-2：prom.yaml 抓取端补凭据（bearer/basic，密钥走环境注入不入 Git）或经 ADR 增设 `management.server.port` 内网独立口并调整 SecurityConfiguration 口径；③ O-1/O-6 Trace 整组**从属于 AUD-005 人类裁决**（㉛ 整改⑪）：若走 OTel，同批装配 `micrometer-tracing-bridge-otel` + observability profile 增 Collector 服务 + nginx traceparent 生成与 access log 关联 + integration 客户端上下文传播 + Worker 新 Span/Span Link（并回收 ㉜ L1543 REQ-JOB 半句）；④ O-4：alerting-rules 增 `aihub_audit_events_total{result="DENIED"}` 速率规则 + AlertService 或协议层计数补一般 403 覆盖（方案须先定计数落点）；⑤ O-5：DEAD_JOB/JOB_DEAD 合并或以 ADR 记录分级理由并差异化查询；⑥ O-7/O-8：`EVD-P0BR029/030-001` 的 `governance.completionValidation.result` **由验证权威按本轮实测证伪重出**（含 E 级、scenarios、artifacts 实据），`EVD-P0BR045-001` **L71-75** OBS 段 E4/E5 声称与 `notProven: []` 按 verify.sh 断言体实况收缩；两卡补 `scenarioEvidencePlan`/`approvedBy`/完整 baseCommit；verify.sh 增 V-OBS 断言（Target UP、面板取数、停库分层、E5 注入）——**并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ 统一证据门禁整改批**；⑦ AUD-005 本体承 ㉛ 整改⑪，仍须人类先裁决。

→ 本项与 ①—㊸ 一并不在本轮 AI 自决实施范围内。

### ㊹ REQ-OPS-001 运行 Dashboard、告警与 Runbook（告警双真值与看板覆盖面撞验）

**立项与查重**：八候选 REQ-ID（PRE/CLI/AI-IDE/REL/SEC/BKR/PERF/OPS-001）对 plan 全文 Grep **零命中**后择定 `REQ-OPS-001`；REQ-PRE 因 worktree 在制（TASK-P0BR-077..081 + EVD-P0BR-078 涉 AC-DST-PRE-001/002）排除。与 ㊳ 划界：㊳ 证 Inbox/Reconciler 业务语义与审计链，本节的**告警规则装载真值、Dashboard 九类覆盖、Runbook 七要素、E5 演练证据链**从未成节实证；与 ㊸ 划界：㊸ O-3 五死 Gauge 属指标源侧，本节只承接其**告警/看板消费后果**（引用计权不重复相加）。

**权威锚点**：`01-requirements/p5-quality-operations.md` **L213-256**（`READY`/`MUST`/P5/`blockedBy: production/on-call model` L219/`minimumEvidenceLevel: E5` L220）：Dashboard 九类 **L225-233**、Alert 七要素+八必盖面 **L237-246**、Runbook 七要素 **L248-256**；`05-acceptance/p5-exit-catalog.md` **L61** `AC-P5-OPS-001`（E5：五类故障注入 + Dashboard 显示 + Alert 元数据 + Runbook 记录绑 Commit）。任务/证据卡：`tasks/TASK-P5-006.md`（READY/authorized L4-5、完整 baseCommit L7、approvedBy: User L52、allowedPaths=deploy/observability+docs/runbooks+本 EVD L34-37）与 `tasks/evidence/EVD-P5006-001.yaml`（`PARTIAL` L11、`accepted: false` L153、演练 exit 0 PASS=18/FAIL=0/SKIP=3 L64-67、notProven 三项 L92-98、followUps 六条 L133-139）。本轮全读文件：deploy/observability/alerting-rules.yaml（278 行）与 deploy/compose/grafana/alerting-rules.yaml（111 行）、compose.yaml、prom.yaml、p5-ops-fault-drill.sh（355 行）、ops.md（126 行）、backup-restore.md（116 行）、AlertService.java、PlatformMetrics.java、V21__alerts.sql、五 Reconciler 触点。

**缺陷矩阵（Z-1..Z-8）**：

| ID | REQ 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **Z-1** | 告警规则单一真值（隐含）+ L237 元数据 | **双真值**：`deploy/observability/alerting-rules.yaml`（8 组 **14 条**：JobDead L35/JobQueueBacklog L50/HighErrorRate L68/HighLatency L83/DBPool L101/JvmHeap L116/ReconciliationDiscrepancy L134/ReconcilerManualReview L149/WebhookDeliveryDead L168/WebhookInboxBacklog L184/AuthDenialSpike L205/BackendDown L223/StorageQuotaWarning L244/BackupFailed L262；全条七要素、live 改引、头注 L20-27 自认漂移+401）vs Prometheus **实际装载** `deploy/compose/grafana/alerting-rules.yaml`（5 组 11 条、labels 仅 severity、死 Gauge L7/L16/L36 依旧）——装载实证：compose.yaml **L211** `./grafana/alerting-rules.yaml:/etc/prometheus/alerting-rules.yaml:ro` + prom.yaml **L5-6** rule_files；权威源 14 条中 6 条（含 AuthDenialSpike/BackendDown/Storage/Backup/ReconcilerManualReview/WebhookDeliveryDead）运行态**不存在**——**更正 ㊸ O-4 口径**：安全告警非「未写规格」，是「规格在未被装载的那版里」 | **✗** |
| **Z-2** | L225-233 Dashboard 九类 | `aihub-dashboard.json` 11 面板（compose.yaml L230 挂载）逐类对撞：①API SLI 部分（速率 L13/P95 L22，无可用性面板）、②MCP 部分（调用速率 L67，counter live：PlatformMetrics L35+McpController L186/192/197 三调用点；无延迟/拒绝）、③Auth failure/lock/replay/denial **零面板**、④Job 部分（L31/L40 两面板引死 Gauge〔㊸ O-3 引用计权〕，pending/running/retry/lease 缺）、⑤Inbox/Outbox 部分（L53 死 Gauge、Outbox 缺）、⑥依赖 Gitea/MinIO/DVC 零面板（DB L76-78/JVM L87-88 ✓）、⑦Published consistency **零面板**、⑧Storage capacity/quota **零面板**、⑨Agent 部分（同 MCP，denials/high-risk 缺）+业务指标部分（audit L115 ✓、download 无面板仅 API 端点 ops.md L31、asset L106/upload L97 死 Gauge）→ **九类无一整类满足** | **✗** |
| **Z-3** | L237 七要素（severity/threshold/window/dedupe/owner/runbook/silence/恢复条件） | 装载版 11 条仅 severity；应用内 `system_alert` 表（V21 L4-20）无 owner/runbook/silence 列、dedupe 仅 `hasFiringAlert(alert_type,FIRING)` 硬编码（AlertService L244-249）无窗口配置；权威源 14 条要素齐备但**不装载** → 元数据与执行链割裂：「写规格的没上线、上线的没元数据」 | **✗** |
| **Z-4** | L239-246 八必盖面 | 装载版实盖：错误率/延迟 ✓（L56/L65）、DEAD/积压 **部分**（死 Gauge 恒 0 永不触发〔㊸ O-3〕+租约过期无规则）、一致性 **半**（L96 累积值 >0——历史残留致永久误报，权威源已改 `increase()[5m]` L137）、Webhook 连续失败 半（L45 事件计数非失败率）；缺：服务/依赖不可用（装载版无；权威源 BackendDown L223 未装载+401 CAVEAT L236）、token 重放/异常拒绝（AuthDenialSpike L205 未装载；应用内 `AUTH_FAILURE_RATE` 仅 V21 L24 注释示例、AlertService 无此类型）、容量/备份（L244/L262 `NOT_WIRED` 占位 `absent(up{no="match"})` 永不触发）、Reconciler MANUAL/SECURITY（见 Z-5）→ **八面装载态实盖 ≤3.5** | **✗** |
| **Z-5** | L245 Reconciler MANUAL/SECURITY 告警 | `system_alert` 无 SECURITY 类产生方：AlertService 全部类型={DEAD_JOB,JOB_DEAD,DEAD_DELIVERY,OUTBOX_BACKLOG,STORAGE_QUOTA_WARNING,SYSTEM_DEPENDENCY_UNHEALTHY:*}；Reconciler 侧仅 Counter 标签+`webhook_inbox` 留痕+LOG.error（SessionStagingReconciler L123-126、MinioStorageReconciler L223、JobLeaseReconciler L132-146）；**且 PublishedVersionReconciler.recordDiscrepancy L212-228 不调 `recordReconciliationDiscrepancy`**（只写 inbox+日志）→ 该 Reconciler 差异不计入告警指标；ops.md L47「按分类处理」的分类在告警表不可见 | **✗** |
| **Z-6** | E5 + AC-P5-OPS-001 五类故障注入 | `p5-ops-fault-drill.sh` **L354 `[ "${FAIL}" -gt 0 ] && exit 1`——SKIP 不致败**；实态：Drill5 Prometheus live SKIP（401）、Drill6 备份全链 SKIP（host 级）、Drill4 依赖不可用=单测代 live（ops.md L93 自认自举不可行）→ 五类注入中 **live 告警链实证仅应用内 2 类注入 + 去重/acknowledge**，AC 的「Alert 显示」对 Prometheus 链 0 实证；EVD 以 `PARTIAL`+notProven 如实登记（方向合合规，见合规面⑦），但 ops.md L87「SKIP=3（预期）」的 E5 记录口径**含水分** | **✗**（E5 达称 unproven） |
| **Z-7** | Runbook 与真值一致（L250 首查指向） | ops.md **L36**「`deploy/observability/alerting-rules.yaml`（与 Compose Grafana 配置同步）」——与 Z-1 装载实证及其文件头 L20-27 自述**直接矛盾（失实）**；**L42** JobDead 来源「Prometheus `aihub_job_dead_count>0`」——该规则只存在于未装载版且表达式引死 Gauge；L54 与 backup-restore.md **L30/L54/L57/L73** 引 `deploy/compose/docker-compose.yml`——Glob `deploy/compose/*.yml` **零文件**（实际 `compose.yaml`）；ops.md L54 retry 路径 `/jobs/{jobId}/retry` 与 L97 `:retry` 写法不一；权威源 L213/L254/L272 runbook 锚（`#告警类型与响应`、`#一致性备份故障演练`、`#一致性-备份故障演练`）与 ops.md L38/L65 标题不互配（Markdown 锚失配） | **✗** |
| **Z-8** | L254/L255/L256 三要素 | **升级/回退（DB 只前向）缺**：ops.md/backup-restore.md 均无「DB 只前向、恢复走 dump 重放而非改迁移」表述（backup-restore.md L37-48 恰为整库重放，语义相近未声明约束）；**演练频率缺**：两 runbook 无固定演练周期（backup-restore.md L7「需独立环境执行」非频率）；联系/升级路径**半**：ops.md L77-81 有时效+角色（oncall-platform/tech-lead）无具体联系通道 | **✗** |

**合规面（对照）**：① 双真值逐行比对本轮完成，**装载侧事实以挂载行实证**（compose.yaml L211 + prom.yaml L5-6），未凭文件名推断；② 权威源以头注**自认**漂移/401/占位不生效，未伪装生效；③ AlertService 六类告警均有真实调用方（MinioStorageReconciler L147 配额、SystemDependencyService L96 依赖、四定时检查 L165-242）且幂等去重+自动 RESOLVED 语义与 runbook L73 一致；④ MCP counter 为 live 指标（三调用点），dashboard L67 面板有真实源——不随五死 Gauge 一并判废；⑤ **E5 演练记录绑 Commit** 要素形式满足：ops.md L83-87 表格 + EVD L38 40 位 commitSha 44bbc67… 一致；⑥ 脏基线如实披露且给人工处置路径（ops.md L97，含「禁止删库清行」约束、`:retry` 走 `job:manage` 审计）；⑦ **EVD-P5006 governance 两 FAIL 均如实自报**（taskValidation baseCommit 漂移 L23-26、completionValidation 结构性残留 L31-34）——与 ㊸ O-8（EVD-029/030 自称 PASS ↔ 实测 FAIL）为**相反方向的差异化事实**，TASK-P5-006 卡片元数据完备度（baseCommit/approvedBy/scenarioEvidencePlan）亦优于 029/030，此三点必须与本节缺陷同权重记录，不得混写；⑧ runbook 引用的脚本族真实存在：backup.sh、verify-restore.sh、p5-restore/{restore-postgres,restore-minio,restore-gitea}.sh（Glob 实证）；⑨ backup-restore.md 其余四要素齐备：RPO/RTO 表 L11-15、隔离环境 L24、脱敏 L17、演练记录模板 L99-110、安全诊断命令（pg_dump -Fc/psql 只读）不泄密 ✓、恢复后验证 L43-48/L87-95 ✓。

**查重后新增结论计数**：Z-1（双真值+装载挂载行实证+六条规格未上线）、Z-2（九类逐面板对撞零整类满足）、Z-3（元数据与执行链割裂结构）、Z-4（八面装载态实盖 ≤3.5 + 累积值误报 + AUTH_FAILURE_RATE 幽灵类型）、Z-5（SECURITY 无告警产生方 + PublishedVersionReconciler 差异计数缺口）、Z-6（SKIP-不致败语义 + live 告警链注入实态）、Z-7（runbook 三处失实/断引汇总）、Z-8（三要素缺失）为 ①—㊸ 未覆盖新证；五死 Gauge（㊸ O-3）、401（㊸ O-2）、Inbox/Reconciler 业务语义（㊳）一律引用计权。

**未实测清单（unproven）**：① 装载版 11 条规则的 Prometheus 运行态求值与告警下发（401 未解，预期全部 no-data，未起栈实测）；② Grafana 面板实际渲染与变量；③ `deploy/observability` 版 promtool check 本轮未复跑（既有 EVD 记载 promtool 校验其语法通过）；④ acknowledge/静默/升级流程为人工动作未执行；⑤ 当前栈状态与 401 是否仍在（EVD followUps L133-139 口径为既有登记）；⑥ backup.sh/verify-restore.sh 内容本轮未读，仅证存在。

**取证偏差登记**：① 前段摘要口径「dashboard 全 14 expr」经本轮逐面板实读修正为 **11 面板 / 17 expr**；② 「5 组 11 条」「8 组 14 条」分别经 `- alert:` 逐名清点复核（11/14 hits 与摘要一致）；③ `recordReconciliationDiscrepancy` 调用方实测为 4 个 Reconciler（JobLease L134、SessionStaging L126、MinioStorage、AssetRepositoryReconciler 系），PublishedVersionReconciler 例外已升格为 Z-5 事实而非偏差。

**限定与更正**：① Z-1 整改方向（装载权威源 vs 双向同步）属部署面决策，本节只定装「谁被装载、差多少」；② Z-2 判据严格按 L225-233 九个分项行计，MCP/Agent 两类共源面板的重复计权已声明；③ Z-3 存在「Prometheus 侧管规格、应用内管执行」双链可辩护架构解释，但装载版缺元数据仍违 REQ 字面，权重中；④ AuthDenialSpike 以 DENIED 增量近似「重放/异常拒绝」语义有解释空间——即便未来装载，是否满足 L243 须人类验收，本节不代裁；⑤ 本节涉 REQ/TASK/EVD/规则/runbook/后端六类禁改面**只登记不改动**；⑥ 全部结论为静态取证 + 既有 EVD 引用，E4/E5 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① Z-1/Z-3：告警规则统一双真值——compose.yaml 挂载改指或生成同步 `deploy/observability` 版、装载版补七要素元数据、ops.md L36/L42 失实声称收缩；② Z-2：Dashboard 九类补缺（Auth/Published/Storage/业务 download-publish 四整类面板 + 死 Gauge 面板改引实数，与 ㊸ 整改①同批）；③ Z-3/Z-4/Z-5：Alert 元数据落地决策（system_alert 扩列 vs Prometheus 单链承担）、补 不可用/重放拒绝/容量/备份/MANUAL-SECURITY 五面**生效**告警（后端 emit 或规则装载二选一，租约过期/denials/high-risk/lock 需新增指标源）、修 L96 累积值→增量、PublishedVersionReconciler 补差异计数、清理 V21 L24 幽灵示例 `AUTH_FAILURE_RATE`；④ Z-6：E5 证据重出——收缩「SKIP=3（预期）」口径，按 AC 五类注入重出 live 注入矩阵（含 401 修复前置），**并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ / ㊸ 整改⑥ 统一证据门禁整改批**；⑤ Z-7/Z-8：runbook 补 演练频率 / DB 只前向回退声明 / 联系路径，修 `docker-compose.yml` 断引 ×5、retry 端点写法统一、runbook 锚链接互配。**TASK-P5-006 L50 验证命令的文件名断引随本项同修（卡片正文禁改——由验证权威重出）**。

→ 本项与 ①—㊹ 一并不在本轮 AI 自决实施范围内。

### ㊺ REQ-SEC-001 安全验证与内容供应链（扫描面坍缩与测试面缺口撞验）

**立项与查重**：六候选 REQ-ID（SEC/REL/BKR/PERF/CLI/AI-IDE）对 plan 全文 Grep，择定 `REQ-SEC-001`（与 ㊸/㊹ 重叠最小、供应链扫描为全新面；PERF 素材已预读留候选）。查重实况：plan 全文对本域唯一间接命中在 ㉛ 节（标题 L1712）——L1782 以 NFR 侧「L41 阈值未获批已成事实门禁」登记 `--audit-level=critical`（ci.yml L60）、L1784 登记 `gitleaks-action@v2` 双配置差异（L66 vs L172-175）与 NFR-SEC-001 未实测——**同一片 CI 扫描面、不同需求锚**；本节以 REQ-SEC-001 十一项自动化范围 + 六项门禁为判据整域对撞，上述两点**引用计权、不重复相加**。与 ㉟ 划界：预签名 URL 泄漏/过期/范围（REQ **L195**）已由 ㉟ U-f 实证，本节不重翻；与 ㊷ 划界：SensitiveDataMasker 本体缺陷归 ㊷，本节只计**canary 覆盖面**缺口；与 ㊸ 划界：Trace 整链缺位归 ㊸ O-1，本节只计 REQ L196 五路径的**canary 实证矩阵**。

**权威锚点**：`01-requirements/p5-quality-operations.md` **L176-211**（`READY`/`MUST`/P5/**无 blockedBy**/`minimumEvidenceLevel: E5` L182-183；自动化范围十一项 **L185-197**；门禁六项 **L199-206**；追踪 JRN-P5-004 L210）；`05-acceptance/p5-exit-catalog.md` **L55** `AC-P5-SEC-001`（E5：扫描+越权/注入/SSRF/压缩炸弹/Prompt Injection；门禁内 Critical/High、canary 四路径为 0、上传内容不执行、稳定错误+审计）。任务/证据卡：`tasks/TASK-P5-005.md`（REQ-SEC-001 L13、baseCommit 40 位 59f40d28… L7、scenarioEvidencePlan L18-19、allowedPaths=deploy/security+runbook+EVD L34-37、approvedBy: User L51；**步骤 L100 未勾**）与 `tasks/evidence/EVD-P5005-001.yaml`（`PARTIAL` L12、governance 三项 `null` L25/L27/L31（未跑——区别于 ㊹ 合规面⑦ 的 FAIL）、commitSha bbd48931… L35、头注 L9「PASS=6 FAIL=0 SKIP=1」、E5 复验长断言 L66、notProven L68-69、**accepted: false L99**）。本轮全读文件：ci.yml 安全段（L34-68/L165-176/L306/L317-320）、p5-security-drill.sh（166 行）、run-redaction-canary.sh（13 行）、security.md（40 行）、JwtTokenService/Test、SecurityVerificationTest、UploadContentSecurityTest、PreviewContentSecurityTest、SsrfGuard 及消费点。

**缺陷矩阵（X-1..X-10）**：

| ID | REQ 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **X-1** | L187「SAST、依赖/镜像漏洞、Secret、许可证扫描」 | CI `security-scan` job（ci.yml L36-68）仅三项：dependency-check `-DfailBuildOnCVSS=9`（L52-55）、`npm audit --audit-level=critical`（L60）、gitleaks（L65-68）；**SAST/镜像扫描/许可证扫描/SBOM 生成全仓零工具**（Grep semgrep/trivy/syft/sbom/codeql/license-check 在 .github+deploy 零命中）；EVD notProven L68-69 只自报「gitleaks/semgrep not installed」——**镜像与许可证两面连 notProven 登记都没有**（不是「装了没跑」，是「从未存在」） | **✗** |
| **X-2** | L201「Critical/High 漏洞阈值…需确认」 | ci.yml **L35 注释「对应 REQ-SEC-001，CVSS >= 9.0 阻断流水线」为失实声称**：CVSS≥9.0 仅覆盖 Critical，REQ 要求 Critical/**High** 双档；`--audit-level=critical`（L60）同偏；阈值取 9 的来源不可追溯（**㉛ L1782 已登记「未获批数字成事实门禁」——引用计权**，本处补的是**注释对 REQ-SEC-001 的点名失实**这一独立事实） | **✗** |
| **X-3** | L187 依赖扫描 + L203「保留…依赖锁」 | `git ls-files` 实证 **package-lock.json 与 pnpm-lock.yaml 双锁同入库**；npm audit（L60，读前者）与 `pnpm install --frozen-lockfile`（L91/L306，读后者）**扫描对象≠安装/部署对象**——前端依赖漏洞扫描存在整链漂移窗口（pnpm-lock 中的漏洞对 CI 不可见） | **✗** |
| **X-4** | L203「扫描结果保留 SBOM/镜像 digest/依赖锁」 | 全 CI 唯一 `upload-artifact` = playwright-report（L317-320）；dependency-check `-Dformat='ALL'`（L54）产物**生成即弃**；SBOM/镜像 digest 概念在 CI 中不存在 | **✗** |
| **X-5** | L201-202「例外流程…owner、到期、缓解和 Accepted 风险」 | 例外登记设施**全仓零落地**：Grep suppress/false-positive/accepted-risk/waiver/exception 仅命中需求文本自身；无任何 `*.exceptions.*`/漏洞台账文件；即 Critical 命中时唯一出路是改代码或改阈值，**无受控例外通道** | **✗** |
| **X-6** | L188「JWT 算法/kid/iss/aud/重放/时钟边界」 | 实现面 ✓（见合规面③）但**测试面半**：JwtTokenServiceTest 仅 5 案例——roundTrip L58/篡改 L74/过期 L87/prevKey 重叠 L101/refreshTtl L130；REQ 点名的 **alg 混淆（RS256→HS256）、alg=none、kid 错配、时钟偏斜边界（±skew）、jti 重放**五类对抗案例零覆盖 | **✗** |
| **X-7** | L190-192 注入/枚举/traversal 族 | 分面对撞：SQL 注入/排序筛选注入 **零测试**；Zip Slip 仅 UploadContentSecurityTest 相邻的 PathTraversalRejection（反射+Unsafe 测 validatePath，**无 zip 条目名攻击用例**——AC L55 点名「压缩炸弹」，EVD L90 assumption 自认 Zip-bomb 仅单元非 live）；Unicode 归一化绕过零测试；资源枚举 2 处（ProjectApplicationServiceTest L74、OrganizationControllerTest L179）；symlink 1 处（UploadMaterializeJobHandlerTest L157）；**SsrfGuard 全仓唯一消费点 WebhookDeliveryService L97**——其余出站 HTTP（Gitea/MinIO/MCP 代理）无该守卫接入证据 | **✗** |
| **X-8** | L196 canary 五路径 + E5 | run-redaction-canary.sh（13 行）L11 仅跑 3 个测试类=**日志掩码+审计+JWT 验签**；五路径实盖：日志 ✓、审计 ✓、**错误面零测试**、Trace 面（**㊸ O-1 引用计权**：Trace 整链缺位，canary 更无从谈起）、浏览器存储面有单测（合规面②）；p5-security-drill.sh **L28-47 scanners「if installed」否则 SKIP+canary 替代、L163-164 `FAIL>0` 才 exit 1——SKIP 不致败，与 ㊹ Z-6 同构（引用计权：㊹ 指 ops 演练，本处指 security 演练且叠加「以替代手段冒充扫描」语义）**；EVD 头注 L9 的「PASS=6 SKIP=1」即在此语义下取得 | **✗** |
| **X-9** | 门禁文档一致性 + L204 | security.md **L38-40**「供应链扫描（后续）：deploy/security/ 预留 SAST/依赖/镜像扫描配置入口；CI 接入前以本地 canary 脚本为门禁」**两头失真**：① Glob 实证 deploy/security 仅两脚本（drill+canary），无任何「预留配置入口」；② CI 已部分接入扫描（L36-68），「CI 接入前」前提失效；另 L33-36 演练检查清单**四项全未勾**——REQ/E5 的「演练记录绑 Commit」要素在 runbook 面零落地 | **✗** |
| **X-10** | L205「Skill/Agent 配置纳入代码评审和扫描」 | **零设施**：全仓 Glob CODEOWNERS 零文件；无任何扫描器路径覆盖 Skill/Agent 配置面（X-1 已证扫描器本身缺位）；「纳入评审」无仓内强制载体（分支保护属 GitHub 平台配置，仓外不可见——unproven） | **✗** |

**合规面（对照）**：① **上传内容不执行原语零命中**：backend main Grep ObjectInputStream/`pickle`/`yaml.load`/`Runtime.exec`/`ProcessBuilder` 全零——业务服务侧无反序列化/命令执行原语（但「预览 Worker 不执行」的完整隔离链只到单测面，见 unproven⑥）；② **浏览器存储面真实落地**：tokenHolder.ts L3 与 AuthProvider.tsx L4 声明内存态 + **AuthProvider.test.tsx L173-188 主动断言 `setItem` 不写入 access token**（取证初判「静态 N/A」，本轮复测修正为有测试 ✓）；③ JWT 主链实现完备：RS256+kid（JwtTokenService L48/L104）、`requireIssuer/requireAudience` 强校验（L201-202）、密钥重叠期验签（测试 L101）；④ SSRF+DNS rebinding 有专测且 live 实证（SsrfGuardTest + DnsRebinding nip.io 实测解析，EVD L66）；⑤ XSS/Prompt Injection 走「原文存储+JSON-only 交付+读回往返」模型并有 live 注入链（drill L99-159，EVD L61-66 proven 五项如实）；⑥ 卡片治理方向合规：TASK-P5-005 元数据完备（baseCommit L7/approvedBy L51/scenarioEvidencePlan L18-19）、EVD `PARTIAL`+notProven 自报+`accepted: false` L99——与 ㊹ 合规面⑦ 同方向、与 ㊸ O-8 相反方向（governance 三 `null` 为「未跑」非「假 PASS」，L25/L27/L31）；⑦ 双 gitleaks 配置差异 ㉛ L1784 已登记（引用计权）。

**查重后新增结论计数**：X-1（SAST/镜像/许可证/SBOM 四零+两连 notProven 都缺）、X-3（双锁漂移）、X-4（工件零留存）、X-5（例外流程零设施）、X-6（JWT 对抗案例五缺）、X-7（SQL/排序注入/Zip Slip/Unicode 零测试+SsrfGuard 单消费点）、X-9（runbook 两头失真+清单四未勾）、X-10（CODEOWNERS 零）为 ①—㊹ 未覆盖新证；X-2 的阈值数字来源、X-8 的 SKIP 同构、㉛ 双 gitleaks、㉟ U-f presigned、㊷ Masker 本体、㊸ O-1 Trace——一律引用计权不重复相加。

**未实测清单（unproven）**：① ci.yml security-scan job 的**实际运行历史与结果**（未读 Actions 运行记录）；② gitleaks L66（无配置）与 L172-175（.gitleaks.toml）覆盖差（㉛ L1784 同款未跑）；③ Skill/Agent 配置**文件群**本身未盘点（只证无扫描/评审设施）；④ REQ L197 限流/并发/幂等/资源耗尽测试面未普查；⑤ REQ L206「安全失败稳定错误+审计+不回显攻击细节」的对照面未逐端点核验；⑥ 预览 Worker 隔离仅证 main 源无执行原语，Worker 进程边界的 live 证据未采；⑦ GitHub 分支保护/required review 属仓外平台态。

**取证偏差登记**：① 卡片编号相邻误读：TASK/EVD-**P5-004 实为 REQ-PERF-001 卡**（全采留 ㊻+ 候选素材），经 `requirements` 字段 Grep 复证真卡为 P5-005——教训固化：taskId 相邻≠需求相邻；② PreviewContentSecurityTest 路径凭记忆致 Grep IO error，Glob 复证实际在 `version\infrastructure\`（非 application）；③ 浏览器存储面初判「零命中→静态 N/A」，本轮复测发现 AuthProvider.test.tsx 主动断言，**改判合规面②**。

**限定与更正**：① X-2 不判阈值应取多少（Critical-only vs Critical/High 属人类门禁裁决），只证注释点名 REQ-SEC-001 与条款字面背离；② X-3 不判应保留哪把锁（npm vs pnpm 统一属工程决策）；③ X-7 的 SsrfGuard 消费点判定限于「未见接入证据」，非「证明未接入」；④ EVD L9「PASS=6 SKIP=1」数字与脚本语义核对一致，本节不判其造假、判其**口径所依附的 SKIP-不致败语义本身不达 E5**；⑤ 本节涉 REQ/TASK/EVD/CI/runbook/前后端测试六类禁改面**只登记不改动**；⑥ 全部结论为静态取证+既有 EVD 引用，E4/E5 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① X-1/X-2/X-4：security-scan 扩面——补 SAST/镜像/许可证/SBOM 工具链、阈值与 REQ L201 对齐裁决、L35 注释收缩、扫描工件 upload-artifact 留存；② X-3：锁文件统一（删除其一+扫描面与安装面对齐）；③ X-5/X-10：例外登记台账（落 TASK-P5-005 allowedPaths：deploy/security+runbook）与 Skill/Agent 配置评审载体（CODEOWNERS）；④ X-6/X-7：测试补缺批——JWT 五类对抗案例、SQL/排序筛选注入、Zip Slip 条目名攻击、Unicode 归一化、SsrfGuard 消费点普查接入；⑤ X-8：drill 严格模式（扫描器缺失→FAIL 或 CI 专用开关）+ E5 证据按 AC L55 全矩阵重出，**并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ / ㊸ 整改⑥ / ㊹ 整改④ 统一证据门禁整改批**；⑥ X-9：security.md L38-40 双头失真收缩改写 + L33-36 清单以真实演练记录绑 Commit 勾选。**TASK-P5-005 步骤 L100 未勾与 EVD governance 三 `null`（L25/L27/L31 未跑）随本项重出（卡片与证据正文禁改——由验证权威重出）**。

→ 本项与 ①—㊺ 一并不在本轮 AI 自决实施范围内。

### ㊻ REQ-PERF-001 性能、容量与回归（授权基线悬空与目标规模负载坍缩撞验）

**立项与查重**：㊺ 收尾轮对 PERF/REL/BKR 三 REQ-ID 全 plan Grep 仅 ㊺ 节自引一处——三域零命中成立，择定 `REQ-PERF-001`（预读素材已在案，㊺ 偏差① 的误读卡转正为权威卡）。**引用计权面**（本轮 Grep 复测定位）：㉒ 节 L945/L955 已登记 `perf-smoke.sh` L121/125 实走 `/mcp`（MCP 路由漂移的反证素材，本域不重复计）；㉛ 节 L1721/L1759 已登记 perf-smoke 无 `.ps1` 对应（NFR-COMP-005 双语等价面）、**L1767（㉛ 缺陷 H）已登记 ci.yml `perf-smoke` L356 `continue-on-error: true` + L357 仅 main push**——性能门禁「跑而不论」的事实归 ㉛，本域只计其对 REQ-PERF-001 门禁语义（L166「不能关闭授权…校验」、AC「在门禁内」）的**需求侧后果增量**；㊺ 节 L2278 登记 P5-004/P5-005 误读教训。与 ㊹ 划界：SLI 九类指标面归 ㊹，本域只计**性能报告**对 REQ L157-158 的缺口；与 ㊺ 划界：CI 扫描/锁/工件面归 ㊺，本域 CI 只取 perf-smoke job。

**权威锚点**（本轮全重测）：`01-requirements/p5-quality-operations.md` **L135-173**（READY/MUST/P5、`blockedBy: [Q-303, production sizing]` **L141**、E5 L142；工作负载七项 **L147-153**；报告九类 **L157-158**；门禁七项 **L162-168**——Search P95≤500ms **L162**、ticket≤300ms **L163**、Webhook projection≤10s **L164**、报告绑数据生成器版本/Commit **L167**、Compose 冒烟与生产容量区分 **L168**；追踪 JRN-P5-003 L172）；`05-acceptance/p5-exit-catalog.md` **L48** `AC-P5-PERF-001`（10 万 Asset 混合负载 + 三阈值 + 不关授权/审计/Trace + 报告绑 Commit，E5；**前摘要记 L49，本轮实读定装 L48**）；PRD 母本 **L2110-2120**（§13.5：「100 并发…**持续 5 分钟**」L2114、「记录…**数据库连接池和 JVM 内存**」L2118、「只用于发现明显回归，不代表生产容量结论」L2120）与 **L180-182**（500ms/300ms/10s 三阈值原始出处——脚本 L165 注释所引「PRD §13.5」的 300ms 实出自 L181 指标表，本轮核正）。任务/证据卡：`tasks/TASK-P5-004.md`（READY+authorized L4-5、baseCommit 59f40d28… L7、REQ L13、allowedPaths=**deploy/perf / docs/runbooks/performance.md** /EVD **L34-37**、验证命令 `test -d deploy/perf` **L50/L110**、approvedBy L51、**步骤 L100 未勾**）与 `evidence/EVD-P5004-001.yaml`（E5 复验演进头注 L4-14、PARTIAL L16、taskValidation **FAIL（baseCommit 漂移）L29-30**、changedPath/completion 双 `null` L31-36、commitSha 0a9a76c L40、四场景 P95 实测 L69、presigned 旧归因证伪 L71、failpath 实测 L73、notProven 100k L77-78、**artifacts=deploy/compose/scripts/{perf-smoke.sh,p5-perf-baseline.md} L82-83**、assumptions/environmentDifferences L106-111、followUp 500→404 L113、accepted false L119）。本轮全读：perf-smoke.sh 212 行、p5-perf-baseline.md 56 行、ci.yml L352-372；命令实证：`git log -- deploy/perf` **零历史**、`git log -- docs/runbooks/performance.md` **零历史**、Test-Path 双 False、当前 HEAD=**a022796**。

**缺陷矩阵（P-1..P-10）**：

| ID | REQ/AC 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **P-1** | 卡片自洽性（DEC-009 治理面） | TASK-P5-004 allowedPaths 前两径 **`deploy/perf` 与 `docs/runbooks/performance.md` 在仓内从未存在**（git log 零历史 + Test-Path False，本轮命令实证）→ 卡片验证命令 `test -d deploy/perf`（L50/L110）**在任何工作区必 FAIL**——一张 `implementationAuthorized: true` 的 READY 卡自带不可满足的验证命令，validate-task-card 对此无结构校验（㉛ 缺陷 A 的深门禁缺口在本卡的具体显形） | **✗** |
| **P-2** | 同上（allowedPaths 约束力） | 实际工件全在 allowedPaths **之外**：`deploy/compose/scripts/perf-smoke.sh` + `p5-perf-baseline.md`（EVD artifacts L82-83 自认）；EVD L30 failureReason 自认「E5 复验仅改动**已授权脚本** deploy/compose/scripts/perf-smoke.sh」——**该路径并不在 L34-37 的任何授权径内**，自认措辞与卡片正文直接矛盾。（计权：deploy/compose/scripts 是历史 P0BR 系列的授权径，本项只判「对本卡越界」，不否认他卡可能覆盖——见限定④） | **✗** |
| **P-3** | L147-153 工作负载七项 | 坍缩为一次性 4 场景：100k seed 无（notProven L77-78 如实自报）；「100 并发 **5 分钟**」（L148，PRD L2114 原文同）→ 脚本 xargs 单轮 samples=concurrency（L69-70，asset-search samples=100 即 100 次请求完事）；「冷热缓存、不同角色和否定查询」（L153）零覆盖；「Webhook/Job/Outbox 背景流量」（L152）零覆盖 | **✗** |
| **P-4** | L164/AC L48 点名「Webhook projection P95≤10s」 | perf-smoke.sh **四场景无此场景**（L4-7 头注自列全集）；baseline.md L31 有「Webhook 积压」方法学场景但明示为演练占位（L3「E5 演练占位」）未落地——**AC 三个点名阈值只实现两个** | **✗** |
| **P-5** | L162「Search P95≤500ms」语义 | 实测路径非搜索：asset-search 请求 `?limit=20` **不带任何搜索词**（L109/L111）、mcp 场景 `keyword:""`（L116）——测的是列表/空词路径的延迟，含词搜索（`keyword` 参数，AssetController L92 实证参数名）的查询计划/全文路径零测量。数值达标（25ms）与语义达标是两回事 | **✗** |
| **P-6** | 阈值一致性 | 三处漂移/自设：① baseline 门禁表「p95<800ms」（L29）、「p95<400ms」（L30）≠ REQ L162 500ms/L163 300ms（标「初稿」L27，但已被 EVD 引用为方法学工件）；② mcp 1000ms（L115）与 multipart 1000ms（L190）**REQ/AC/PRD 三源皆无此名目**，脚本自设；③ baseline L29 示例命令 `?q=test` 引用的查询参数**在后端不存在**（AssetController 全 Grep 无 `q`，实参 `keyword` L92——同㉟ 端点臆写型缺陷） | **✗** |
| **P-7** | L157-158 报告九类 + L167 | 输出仅 P50/P95/P99/错误率（L92/L102）；DB pool/慢 SQL/JVM GC/CPU/磁盘网络/Gitea·MinIO latency/Job queue/**Trace sample** 九项全无（PRD L2118 连「数据库连接池和 JVM 内存」两项最低要求也未满足）；「报告绑定数据生成器版本」（L167）无对象可绑——**数据生成器本身不存在**（baseline L19「非本仓库默认路径」） | **✗** |
| **P-8** | AC「在门禁内」+ E5 严格性 | 场景级 SKIP return（L76-79 无 2xx；L170-172/L196-198 夹具缺失整场景跳过）**不计败**，汇总「PASS: N/4」N≥1 即 exit 0（L211）——部分 SKIP（如夹具漂移致 presigned 场景退化为 SKIP）仍以 PASS 收口。零测量保护（L28/L203-206）**只兜全 SKIP 底**，兜不住半 SKIP（与 ㊹ Z-6/㊺ X-8 同构面，但本脚本有 failpath 实测的零测量地板——计权见合规面②） | **✗** |
| **P-9** | L166 门禁强制性 | **㉛ L1767 引用计权**（continue-on-error true + 仅 main/dispatch 已登记）。本域增量：job 名自认「**optional**」（ci.yml L353）；登录取 token 用 **`change-me-admin-01` 明文默认口令**（L370，来自 `.env.example` 链）——「性能测试不能关闭授权」在实现上以硬编码引导凭据满足，授权链在场但凭据面与 ㊺ 的密钥治理同型 | **△**（新增面有限） |
| **P-10** | DEC-009 治理三态 | Commit 三漂移：TASK baseCommit 59f40d28（L7）↔ EVD commitSha 0a9a76c（L40）↔ **当前 HEAD a022796**（本轮实证，其后尚有 70f7874/7e53aac 等含前端/nginx/上传面改动）；EVD taskValidation 自报 FAIL（L29-30）、changedPath/completion 双 null（L33-36）；步骤 L100 未勾（与 ㊺/㊹ 同型）——**E5 证据所依附的 HEAD 已非当前 HEAD，且从未有过一次 -CheckChangedPaths 通过记录** | **✗** |

**合规面（对照，不得写成缺陷）**：① **E5 复验演进头注是全链最诚实的自证样本**（EVD L4-14）：死代码删除（09bf76a）、三处测量失真根因逐条、Git Bash fork 开销 200-400ms 假 FAIL 的证伪与口径修正（0a9a76c）——含**对自己旧结论的显式证伪**（L71：presigned 恒 SKIP 的「无 ACCESS_TOKEN」归因被推翻，真因是版本状态语义错误）；② **零测量防虚假绿灯 + 失败路径实测**（L28/L203-206 + EVD L73「1ms 阈值副本→4/4 FAIL 无短路；匿名→全 SKIP→exit 1」）——比 ㊹ ops 演练、㊺ security 演练的 SKIP-不致败结构多了一层地板保护，三域整改批引用时须区分此强度差；③ 测量口径有单一权威注释（L44-47 time_total 单调时钟 + 617ms vs 17ms 实测对照）；④ 「仅 2xx 入样」明示为**收紧而非放宽**门禁（L60-62），429 如实计入错误率 8/20（L69，burst=10 归因链完整至 assumptions L108）；⑤ presigned 300ms 阈值与 PRD L181 字面一致（本轮核正 §13.5 指向）；⑥ 卡片/EVD 治理元数据完备且状态诚实：PARTIAL（L16）+ notProven 100k（L77-78）+ environmentDifferences（L110-111）+ `accepted: false`（L119）；⑦ **followUp 主动登记后端缺陷候选**（L113：不存在路径/假 ID 返回 500 应 404/400，脚本 L176 注释同证）——契约缺陷 surfaced 而非静默绕过；⑧ 「E5-smoke」与「E5-100k」场景 ID 分离命名（L68/L77），REQ L168/PRD L2120 的「冒烟≠生产容量」口径在证据命名上被遵守。

**查重后新增结论计数**：P-1（allowedPaths 双径 git 零历史 + 验证命令必 FAIL——**本会话链首个「授权卡自带不可执行验证命令」实锤**）、P-2（自认「已授权」与卡片正文矛盾）、P-4（AC 点名三阈值缺一）、P-5（search 语义坍缩为无词列表）、P-6（`?q=` 参数臆写 + 双自设阈值）、P-7（PRD L2118 两项最低报告面也缺）、P-10（HEAD 三漂移新实测 a022796）为新证；P-3 的 100k 面为 EVD 已自报（本域补 L148/152/153 三项未自报坍缩）、P-8 与 ㊹/㊺ 同构但计零测量地板差、P-9 归 ㉛——**引用计权不重复相加**。

**未实测清单（unproven）**：① perf-smoke.sh 在**当前 HEAD a022796** 的复跑结果（E5 数值绑 0a9a76c，其后 70f7874 动过 nginx/构建、7e53aac 动过 upload 面——P95 是否退化未知，本轮未起栈）；② CI perf-smoke job 的实际运行历史与结果（Actions 记录未读）；③ dev DB 资产量级「≪100k」仅 baseline L55/EVD L78 口径，未 SELECT 实数；④ burst=10 与设计意图的对照未查 rate-limit 配置源；⑤ baseline 的 hey/k6/Prometheus 快照命令（L41-45）从未执行；⑥ Q-303/production sizing（blockedBy L141）所指裁决现状未查——本域「生产容量」半边整体不可判。

**取证偏差登记**：① 摘要口径 AC-P5-PERF-001「L49」经本轮 p5-exit-catalog 实读修正为 **L48**；② 「CI 不阻断」初判为本域新缺陷，Grep 复测发现 **㉛ L1767 已登记同事实**→ P-9 降级为引用计权+名实增量；③ 「PRD §13.5 规定 20 并发/300ms」的脚本注释引用经 PRD 原文对撞：20 并发在 §13.5 原文 L2115 ✓，**300ms 实际出自 L181 指标表而非 §13.5 正文**——注释引用不精确但数值有据，不改判缺陷、登记口径。

**限定与更正**：① 本域不判负载应取何值/seed 方案（blockedBy L141 属 Q-303 人类裁决域），只定装「七项条款 vs 四场景」差集；② P-6 的 800ms 标注「初稿」，本域不裁收紧方向（改 baseline 或补 REQ 名目均由人类定）；③ E5-smoke 的 4/4 PASS 数值真实性不在质疑范围（有时间戳/exitCode/failpath 对照），问题只在其所绑 HEAD 已漂移且无复跑；④ P-2 判定限「对本卡越界」——deploy/compose/scripts 系他卡历史授权径，不排除人类裁定「沿用他卡授权」的合规路径；⑤ 本节涉 REQ/AC/卡片/EVD/CI/脚本/runbook 素材七类禁改面**只登记不改动**；⑥ 全部结论为静态取证+git 实证，E4/E5 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① P-1/P-2/P-10：**卡片与证据由验证权威重出**——allowedPaths 与实际工件径二选一收口（补 `deploy/compose/scripts/perf-smoke.sh`+`p5-perf-baseline.md`，或工件迁 `deploy/perf/`），删除或兑现 `test -d deploy/perf`，重裁 baseCommit 漂移与复跑绑定；② P-3/P-4/P-5：负载面补齐——100k seed 生成器（新径）、5 分钟持续负载（k6/hey，baseline L41-45 已有占位）、Webhook projection 场景落地、真实搜索词参数化；③ P-6：阈值三方对齐（REQ L162-164 vs baseline 初稿 vs 脚本自设）、`?q=` 改 `keyword`；④ P-7：报告面至少补 PRD L2118 两项（DB pool/JVM）起步，九类全量与 Trace sample 以 ㉛ OBS-002/㊸ O-1 的 OTel 缺位修复为前置；⑤ P-8：SKIP 语义升级（部分 SKIP→FAIL 或 min-measured 门槛），与 ㊹ Z-6/㊺ X-8 并案时保留本域零测量地板的强度差注记，**并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ / ㊸ 整改⑥ / ㊹ 整改④ / ㊺ 整改⑤ 统一证据门禁整改批**；⑥ P-9：perf job 阻断化与触发面决策随 ㉛ H 项 compose-e2e/journey-e2e 同批裁决（不单列新锚）。**TASK-P5-004 步骤 L100 未勾、EVD changedPath/completion 双 `null`（L33-36）随本项①重出（卡片与证据正文禁改）**。

→ 本项与 ①—㊻ 一并不在本轮 AI 自决实施范围内。

### ㊼ REQ-REL-001 发布候选全量审计（审计对象悬空与注入链无绑定撞验）

**立项与查重**：本轮 Grep plan 全文 `REQ-REL-001|REQ-BKR-001|REQ-CLI-001|AI-IDE-001` **四候选零命中**，择定 REQ-REL-001（BKR 有 `blockedBy: [RPO, RTO, production topology]` L107 属人类裁决前置、CLI/AI-IDE 留后续）。二次查重 `p5-completion-audit|发布候选|pending-commit|7dc22d44|4c7dfc83|62c92f0c|CAP-REL` 全域**零命中**——本节为 ①—㊻ 未覆盖新域。**引用计权面**：㉛ H（L1766 compose/journey-e2e 软门禁）——本域只计「p5-completion-audit 在 CI 中**完全无作业**」增量（Grep ci.yml 零命中实证）；㉚ Z-7（L2234 `docker-compose.yml` 断引 ×5 + TASK-P5-006 L50 卡面断引）——本域计 TASK-P5-007 L51 为**同型扩散第二卡实例**；㊻ P-3/P-4/P-5/P-8（perf 门禁实现不达）与 ㊺ X-8（security drill SKIP 不致败）——REL 条款 6「性能/安全门禁通过」的 PASS **建立在两个不达门禁的实现之上**，本域只计继承关系不重复计缺陷；verify-journey 18 PASS 双口径（plan L160-162/L247 历史窗口态）引用计权。与 ㊳ 划界：Inbox/Reconciler 业务语义归 ㊳，本域只计条款 4「全量对账差异报告」缺失。

**权威锚点**（本轮全实读）：`01-requirements/p5-quality-operations.md` **L258-278**（READY/MUST/P5/E5 **无 blockedBy**；十条款：净栈 L269、全 MUST Journey L270、故障注入 L271、Reconciler 无未解释差异 L272、备份→隔离恢复→随机 DVC 校验 L273、性能/安全门禁 L274、五类契约无漂移 L275、**Evidence Manifest 全部绑同一 Commit L276**、无 MUST SKIP/无 Placeholder 入可部署 Profile L277、发布说明如实登记 L278）；`05-acceptance/p5-exit-catalog.md` **L67** `AC-P5-REL-001`（MUST/E5，含备份恢复链+同一 Commit+无 MUST SKIP）；`tasks/TASK-P5-007.md`（READY+authorized L4-5、baseCommit 59f40d28 L7、allowedPaths=**p5-completion-audit 目录/traceability-matrix/EVD-P5007 三径 L34-37（全部真实存在——卡片自洽，与 ㊻ P-1/P-2 对照）**、验证命令 **L50 `pwsh` 双语径 + L51 `docker compose -f deploy/compose/docker-compose.yml config --quiet`**、approvedBy L52、步骤 L101-103 全未勾）；`evidence/EVD-P5007-001.yaml`（PARTIAL L3、governance 三项 FAIL 如实自报 L11-35、**commitSha 自绑 62c92f0c L39**、execution **exitCode 1/result FAIL/evidenceLevel E4** L67-69、auditResults PASS=29 FAIL=1 SKIP=1 GAP=3 L77、条款 1/4/5 NOT_PROVEN L79-99、**条款 8 FAIL：81 不可解析+33 drift+2 null L109-115**、条款 9 PARTIAL L116-120、followUps 六条全指向人类验证权威 L177-183、accepted false L189）；审计器 `deploy/compose/scripts/p5-completion-audit/p5-completion-audit.sh` **342 行全读**（A1-A8 结构、语义注释 L11-17、`PY()` 依赖 worker 容器 L38、**A7 env 注入日志面 L241-276**、A4 基线推导自认「不严谨」L147-148、A8 前端 codegen N/A SKIP L327-331、结论行 L341「不宣告波次 VERIFIED」；git 史单提交 faa359d≤HEAD 实证）。**本轮命令实证**：`git tag -l` = **0**；`docker compose -f deploy/compose/docker-compose.yml` 实测 **EXIT=1 file not found**（仓内实文件为 compose.yaml，`-f compose.yaml` 实测 EXIT=0）；`git cat-file` 7dc22d44/4c7dfc83 **不可解析**（reflog 零命中）；EVD 总数 **130**（62c92f0c 时点 117，新增 13 份 P0BR-066..078 全绑历史/近期 Commit）；commitSha 分布实测：**7dc22d44×71**、4c7dfc83×2、pending-commit×5（P1013/P1014/P1015/P2011/P2012）、null×2、**P3009/P3010 已改绑可解析 Commit**（6aad901/178ee6b）；A6 工件 14 项 Test-Path 全存在；Noop 三类文件在仓。

**缺陷矩阵（R-1..R-9）**：

| ID | REQ/AC 条款 | 实现实证态 | 判定 |
| --- | --- | --- | --- |
| **R-1** | L267「在发布候选 Commit」前提 | 仓库 **git tag 数 = 0**、无 release 分支/候选声明物——「发布候选全量审计」卡（READY+authorized+已执行）的**审计前提对象在仓内不存在**；TASK baseCommit/approvedBy/EVD 绑定点均非候选；AC L67 与 REQ 同以候选为主语，全链悬空。PRD 母本「发布版本以 Git Tag+Commit SHA 标识」的边界声明在本仓自身从未兑现 | **✗** |
| **R-2** | 卡片自洽（DEC-009） | L51 验证命令引用 `deploy/compose/docker-compose.yml` **不存在**（本轮实测 EXIT=1）——**授权卡自带不可满足验证命令的第二卡实例**（首例 ㊻ P-1；同文件断引已在 ㉚ Z-7 登记 runbook×5+P5-006 L50，即同型缺陷至少 **3 卡 7 处**扩散）。口径补充：AGENTS.md L116 `docker compose config --quiet` 无 `-f` 且未注明工作目录（仓根执行必失败，CI L106 以 `working-directory: deploy/compose` 为正解）——三处书写口径互异 | **✗** |
| **R-3** | L276 条款 8 | 审计结论绑 62c92f0c（2026-09-24），其后 **13 份新 EVD 入仓、HEAD 漂移至 a022796 未复跑**；本轮静态复核条款 8 违例**未闭合且口径已漂移**：EVD 报 81 不可解析→实残 73（71+2）+pending 5（P3009/P3010 已局部收敛——恰证明「发布前统一重绑」是可执行且已零散发生的动作，但无重审记录）；「PASS=29 FAIL=1」汇总对当前仓态**不再可引用** | **✗** |
| **R-4** | E5 证据链绑定（L276 精神+DEC-009） | 运行时条款 2/6 的 PASS 依赖 `AUDIT_JOURNEY_LOG/AUDIT_PERF_LOG/AUDIT_SECURITY_LOG` **带外注入**（脚本 L241-276 仅 grep 格式匹配），对日志来源 Commit、时间戳、与 HEAD 的绑定**零校验**——历史日志可令「当前候选」报 PASS；EVD L83 自认「沿用 2026-09-23 复跑（HEAD 不同）」即注入链缺口的实证展示（编排真实跑日志进审计的设计本身可辩护——判链不绑，不判编排） | **✗** |
| **R-5** | L277「无 MUST SKIP」+门禁持续性 | 审计器 **CI 零接入**（Grep ci.yml `p5-completion-audit` 零命中）；A1/A4 依赖运行中容器（`PY()` L38、psql L164-165；容器不在→SKIP L80/L172）——发布门禁是**单机手动、依赖栈在跑、跑完即失效**的工件，无任何机制保证候选变更时重跑 | **✗** |
| **R-6** | 规范层依赖声明 | REQ-REL-001 **无 blockedBy**（L260-265）却内嵌条款 5=备份隔离恢复（BKR 演练工件），而 REQ-BKR-001 自身 `blockedBy: [RPO, RTO, production topology]` L107——REL 的可完成性逻辑上被 BKR 阻塞却未声明依赖，实态印证：条款 5 NOT_PROVEN（EVD L97-99「演练窗口待用户」）。AC L67 同样以全并集（含恢复链）为 MUST 验收但无依赖注记 | **✗** |
| **R-7** | E5 vs E4 | requiredEvidenceLevel E5（L42）↔ 实执 evidenceLevel E4（EVD L69）——缺口**如实自报**（非伪证），列此定装差距量级：即便条款 8 修复，仍缺「候选净栈+人工验收」两级 | △（登记级） |
| **R-8** | 审计器自覆盖 | A4 applied 对齐判据 `FS_MIG ≤ APPLIED`（L166）——applied **多于**文件系统（环境超前/迁移被回滚删除）时静默 PASS；A4 基线推导注释自认「不严谨」（L147-148）后以「取较新者」近似——两处于「forward-only+运行时一致」条款面上留小缺口 | **✗**（小） |
| **R-9** | 注释时效 | 脚本头 L4「117 清单」写死 vs 当前 130——纯注释失真，无行为影响；EVD L14「148 文档 / 725 ID」vs 当前 validate-spec 实跑 **148/752**（本轮㊻ 收尾实测）——同型口径滞后样本 | △（轻微） |

**合规面（对照，不得写成缺陷）**：① **卡片自洽为 P5 六卡最佳**：allowedPaths 三径全部真实、dirtyPaths 自列（EVD L42-44）全在授权径内、无 ㊻ P-1/P-2 型悬空——㊻ 的整改①在本卡无对应违例；② governance 三项 FAIL 全如实自报（L11-35），**-CheckCompletion 的 FAIL detail 直接点名「IMPLEMENTED_UNVERIFIED，不得宣告完成」**——与 ㉘ EVD-029/030 自称 PASS 相反方向（㊹ 合规⑦ 同族）；③ exit 1 = 条款 8 真实违例的正确结论且 EVD L72-74 明文「任何审计通过表述均不成立」——审计器不粉饰、FAIL 语义守住了 L12-15 自定契约；④ 脚本对**自身假阳/假阴修复留痕**：CRLF 剥 \r（L50/L90/L285）、GNU grep 选项位置致 havingValue 恒空漏报（L188-190）、两修复入 EVD environmentDifferences L170-172——审计工具证伪并修自己，全链最稀有样本；⑤ GAP/SKIP 语义自束：漂移/PARTIAL/DRAFT 入 GAP 不判 FAIL 且明示「待人工评审 DEC-009」（L12-13/L341）、前端 codegen N/A 如实 SKIP 不假通过（L327-331）、结论行拒绝宣告 VERIFIED；⑥ followUps 六条全部指向人类验证权威动作（L177-183），AI 未自签 baseCommit 重签——DEC-009 遵守范本；⑦ 条款 9 的 Noop 判定以 compose 实配×ConditionalOnProperty 交叉实证（三 Noop 类在仓、compose true→不装配），非凭文件名推断；⑧ A6 必需工件 14 项含 backup/restore/p5-restore-drill 全存在（本轮 Test-Path 复核）——「脚本存在但演练未做」与「脚本缺失」被条款 5 正确分流。

**查重后新增结论计数**：R-1（发布候选概念在仓内零兑现物——本会话链首个「审计对象本身不存在」级治理缺陷）、R-3（130/73/5 当前态复核+EVD 口径过期）、R-4（注入链不绑 Commit 的结构缺口）、R-5（审计器 CI 零接入+容器依赖）、R-6（REL 无 blockedBy 却含 BKR 依赖条款的规范层缺口）、R-8（applied 方向性判据）为新证；R-2 为 ㉚ Z-7 同型缺陷扩散至 P5-007 卡的新实例（断引计数 5→7 处、卡 1→2）；R-7 归 EVD 已自报（本节定装差距量级）。

**未实测清单（unproven）**：① 审计脚本**当前全量复跑**（需栈在跑+三日志注入；本轮仅静态复核 A2 commitSha 面与 A6 存在性面）；② verify-journey/perf/security 三日志的当次真跑窗口态（未起栈）；③ 7dc22d44 的来历（rewrite 历史未考古；不可解析性有 reflog 零命中+cat-file fatal 双实证）；④ 71 vs EVD 报 72 的 1 份差未逐文件对账（可能已局部改绑，同 P3009/P3010 模式）；⑤ 发布说明/SHOULD-COULD 登记面（条款 10）仓内是否存在独立工件未普查；⑥ A3 当前 P0*/P5* 状态分布实数未点齐。

**取证偏差登记**：① pending-commit 初查 6 文件含 EVD-P5007 自身**正文引用**（L113/L179 描述性文字），按其 source L39 实绑 62c92f0c 剔离后 commitSha-line 口径实为 **5**——「先宽后窄」纪律再次拦截计数虚高；② EVD 报「7×pending（含 P3009/P3010）」与当前态差 2 份：P3009/P3010 已改绑可解析 Commit——EVD 时点如实、过期属仓态演进，登记为 R-3 证据而非 EVD 造假；③ 前摘要口径「REL L258 附近/BKR 预读 L101-134」本轮实读精确命中，无需修正。

**限定与更正**：① 本域不裁发布候选机制选型（tag/分支/版本组合基线声明均属人类工程决策），只证前提不存在与审计卡的授权-前提张力；② R-3 不责 EVD 口径过期（取证时点准确；「其后 13 份新 EVD 未重审」恰是 followUp L179 要求成立的反向证明）；③ R-4 判「链不绑被审计 Commit」，不判「env 注入编排日志」的设计本身；④ R-2 的 AGENTS.md L116 口径注记仅登记书写不一，CI L106-112 实为正解、不判 AGENTS 违例；⑤ 本节涉 REQ/AC/卡/EVD/CI/脚本/AGENTS 七类禁改面**只登记不改动**；⑥ 全部结论为静态取证+git/CLI 实证，E4/E5 一律 unproven。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① R-1：**先定义发布候选**（Git Tag 打点+版本组合基线声明，与 PRD 资产发布同构的平台自举），在候选上重签 TASK-P5-007 baseCommit（EVD followUp L178 已指向，由验证权威执行）；② R-2：TASK-P5-007 L51 断引由验证权威重出（与 ㉚ 整改⑤ P5-006 L50、㊻ 整改① P5-004 并案——**validate-task-card 增加「验证命令引用路径存在性」结构校验**，一并覆盖 ㊻ P-1 的 `test -d` 缺校验面，三卡同批）；③ R-3：候选定桩后全量重跑审计+统一重绑 73 不可解析/5 pending/drift 全数（以当前仓态刷新 EVD followUp L179 计数口径）；④ R-4：运行时日志证据绑定——日志头部记录来源 Commit/时间戳并由 A7 比对审计 HEAD，或审计器自持执行三脚本（弃注入）；⑤ R-5：审计器接入 CI 发布作业（容器依赖面提供无栈降级路径），阻断化与否随 ㉛ H 同批裁决；⑥ R-6：REQ-REL-001 补 blockedBy 依赖声明（REQ-BKR-001+JRN 族）——规范面改动归人类；⑦ R-8/R-9：A4 改等值+前向双查、脚本头注释计数去写死。**⑤⑦ 之 SKIP/注入不致败同构面并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ / ㊸ 整改⑥ / ㊹ 整改④ / ㊺ 整改⑤ / ㊻ 整改⑤ 统一证据门禁整改批（本㊼ 为第八挂靠点，注入链不绑 R-4 为本批最强新证）**。**TASK-P5-007 步骤 L101-103 全未勾、EVD evidenceLevel E4 差距随本项①③重出（卡片与证据正文禁改）**。

→ 本项与 ①—㊼ 一并不在本轮 AI 自决实施范围内。

### ㊽ REQ-CLI-001 平台数据集 CLI 适配域（双 `aih` 实现并存 + TS 包无授权入仓 + 条款差集全表）

**立项与查重**：① 四步定装——`CLI-001` 精确查重的 3 命中全部甄别（plan L53/L157 系 REQ-DST-CLI-001 子串、L2323 为 ㊼ 自身立项段），CLI 域未成节；② REQ-AI-IDE-001 判已被 ㉛ NFR-MNT-006（plan L1722 双同名 validate-spec、L1734 task-card-gate CI 实现在案）实质覆盖，弃选、本节仅作 E-1 判据源引用；③ 与 §4.7 划界：§4.7 已做 **bash 侧运行态 E4 复跑取证**（含 presign 双重缺陷、081 立项），不重复计权；本域增量=**契约条款 ↔ 产品码本体差集**，且取证对象扩至 §4.7/EVD 全未提及的 `cli/` TS 包；④ 权威链：REQ-CLI-001（p2-version-transfer.md L281-299，READY/MUST/P2/DEC-008，sourceRequirement=REQ-DST-CLI-001，E4）→ 主文本 dataset-experience.md L278-336（六命令契约 L300-308、安全条款 L311-320、退出码表 L326-334）→ AC-DST-CLI-001..006（dataset-agent-exit-catalog.md L64-69 全 E4）→ TASK-P2-009（authorized）→ EVD-P2009-001（PARTIAL）。

**E-1（本域最重，实锤）：`cli/` TS 包 4141 行产品码无 Task Card 入仓 + 双 `aih` 实现并存**。git 实证：b5bb9a7（xieyong，2026-09-16 14:59 +0800，「feat(cli): 新增 HarnessDG 命令行客户端」）**单提交**新增 `cli/` 全部 21 文件 4141 行（git ls-files=21 项：src 12 + test 2 + 配置 7；行数分解 3218 src + 197 test + 2724 lock + 1002 配置/元数据 = 4141 对账吻合；`git ls-files cli/dist`=0，构建产物未入库），`merge-base --is-ancestor` 确证为 HEAD 祖先。授权面：**全部 tasks/evidence/work-breakdown 的 allowedPaths 无一含 `cli/` 径**（TASK-P2-009.md L47-50 仅授 `scripts/aih`、`backend/src/test/java/com/aihub/cli`、EVD 三径）；traceability-matrix L37 CAP-DST-004 只登记「`scripts/aih` 薄 CLI」，verify-journey.sh L1003 只引用 bash 路径。同名冲突：`cli/package.json` L7-8 `bin: { "aih": "dist/index.js" }` 与 `scripts/aih/aih`（Test-Path=True）**双实现同名 `aih`** 且行为语义分叉（见 E-2/E-3）。违例判据：REQ-AI-IDE-001（requirement-schema.md L161-162）「修改产品代码…前必须有正式 Task Card」+ AGENTS.md DEC-009 门禁。前摘要曾记「23 文件」，本轮 git 双口径重测为 **21**（Glob 含 dist 未跟踪文件的噪声已剔除）。

**E-2：退出码语义撞车且缺四档**。REQ 表（dataset-experience.md L326-334）七档 0/2/3/4/5/6/7；`cli/src/shared/errors.ts` L6-11 `enum ExitCode { Success=0, Error=1, AuthFailed=2, NetworkError=3 }`——**REQ 2=本地参数/Manifest 非法 vs cli 2=认证失败；REQ 3=未认证 vs cli 3=网络失败**（语义整体错位）；**4（无权）、5（幂等/并发/版本冲突）、6（可重试依赖失败）、7（摘要校验失败）四档不存在**。二次污染实证：pull L83-87 下载 HTTP 非 200（含 403/404）一律 `NetworkError(3)`、L120-123 catch-all 含本地写盘失败亦归 3；create L98-101、search L80-86 把全部 API 错误（含 401/403/409）归一化 `ExitCode.Error(1)`——与 §4.7 第⑨项在 bash 侧查实并修复的「catch-all 归一化」同型缺陷，TS 包因无 E4 流程而原样入仓。bash 侧实测口径 0/2/3/4/5/7（EVD L241）。

**E-3：凭据与认证通道违反安全条款**。REQ L315-316「从 OS Secret Store、受保护配置引用或进程环境取得短期平台凭据，命令参数、输出和历史不含 Token」；`cli/src/auth/credentialStore.ts` L20/L24/L42 将 **accessToken+refreshToken 双长期令牌**明文写入 `~/.aih-credentials.json`（仅 0600 文件权限，非 Secret Store）；`cli/src/auth/auth.command.ts` L44-45 login 以**命令行位置参数明文接收 `<username> <password>`**——直接进 shell history，为条款禁止面（bash 侧为 AIH_TOKEN 环境变量/`~/.aih/token` 0600，EVD L235/L194-196，相对合规）。小节：credentialStore L79-85 `tryRefreshToken` 空转（注释推给 client.ts，实际刷新逻辑在 client.ts L82-114 独立实现，调用面疑悬空）；脱敏面存在（errors.ts formatApiError 正则 `eyJ…→[REDACTED_TOKEN]`；pull L62 JSON 输出 `presignedUrl:'[REDACTED]'`）——正向登记。

**E-4：命令契约与 pull 语义差集**。REQ 六命令（L301-308）对 `cli/src/index.ts` L28-32 注册面：`inspect` **缺失**（仅 search/status/create/pull/push 五件）；坐标形态全线偏离——契约用 `aih://<ns>/dataset/<name>@<version>` URI，cli 用数字 `<asset-id>` + `--version <versionId>`（pull L24-25、push L37-38、create 用散 flags 而非 `--manifest <asset.yaml>`，L29-39）；pull **无 `--include/--exclude/--resume`** 且 `--verify` 语义未实现：L72/L105/L112/L118 只**计算并打印** SHA-256，**从不与期望摘要比对**，任何下载均走到「成功」路径（REQ L313「--verify 默认启用，校验 Manifest Digest 和每个 Artifact 的 SHA-256」+ 退出码 0 语义「成功**且校验完成**」双重落空）；push 无 `--resume`、无超限降级 DVC 路径（REQ L317-318），GIT_DVC 下载方法直接报错弃（pull L50-54，REQ L317 要求大型目录走 Git+DVC）。正向面：endpoints.ts L37-66 全部复用 REST 契约路径（upload-sessions/presign/complete/download ticket），未建第二套业务面，符合 L319 Adapter 定位。疑点登记（未运行、unproven）：push L96 `readFileSync(filePath, { start, end })` 的 `end` 为包含偏移，非末片每片多读 1 字节且与 L104 分片 PUT 的 ETag 计算错位——若服务端 complete 逐片比对 ETag 则此实现难以通过，与 bash 侧 E4 修复清单（EVD L148-150 五缺陷）互证 TS 包从未走过同一验收流程。

**E-5：稳定 JSON 输出契约缺失**。REQ L324 要求 JSON 至少含 `operationId/requestId/traceId/result/error.code/error.retryable`；cli 各命令 JSON 为散装字段（pull L60-66/L108-113、push L71/L125、create L84、auth L83-87），`errorMessage` 仅 `{success,error:<本地化文本>}`（output.ts L24-29）——且 L320「create/push/complete 自动生成并持久化稳定 Idempotency-Key」**全线未实现**（create L81、push L77-80/L118-122 均无 Idempotency-Key 头，client.ts 无注入拦截器）；client.ts L10-15 响应包裹类型已定义 `requestId/traceId` 字段但 apiGet/apiPost 解包时（L117-126）将其丢弃。`isJsonOutput` 用全局 `process.argv.includes('--json')`（output.ts L6-8）而非解析后的 program opts，属同族小缺陷。

**E-6：CI/工程链接入零**。`.github/workflows/ci.yml` 全文对 `cli` 无任何 job/步骤命中（Grep 5 命中全为 @redocly/cli、psql client 噪声）；仓库根无 `pnpm-workspace.yaml`（Glob 零命中），cli 包为自包含孤岛——lint/test/build/发布管线均无，`vitest` 两测件（api.client.test.ts 108 行、auth.test.ts 89 行）从属于**任何门禁都不运行它**的包。EVD L34「teams/principals 仍 500」与 §4.7 第⑨项查实推翻（探针路径不存在被 catch-all 归一化）的口径冲突为**既有登记面**（§4.7 L305-306 已裁定「更正随提交轮回写，本段不改 EVD」），与 TS 包无关，不再计权。

**合规性逐项**：① 全部结论有 file:line 或 git 命令实证（b5bb9a7 --stat、ls-files、merge-base、Test-Path、Glob、Grep 逐面列出）；② 查重双向（REQ-ID + 事实锚「两套 aih」「allowedPaths 无 cli」）先于立论；③ 行号本轮重测（L2352/L2354 插入边界、errors.ts/credentialStore/index.ts/pull/push/create/search/client/output/endpoints/auth.command/package.json 全文或关键段实读）；④ 否定性结论「先宽后窄」（23→21 文件自纠；CI 命中甄别去噪；dist 入库以 git ls-files 定案）；⑤ 未实测行为一律标 unproven；⑥ 禁改面零改动（规范/契约/卡片/EVD/CI/产品码只登记）；⑦ 与 ㉛/§4.7/㊼ 引用计权清晰、无重复立论；⑧ 正向面如实登记（REST 复用、脱敏、presignedUrl 遮蔽）。

**未实测清单（unproven）**：① cli 包可构建性/可运行性（未执行 pnpm install/build/test，node dist 未跑）；② push 分片 off-by-one 是否被服务端 ETag 校验实际拒绝（需栈）；③ client.ts refreshToken 轮换与后端 `/auth/refresh` 契约字段全对齐性（仅比对了响应解构面）；④ `~/.aih-credentials.json` 在真实 home 下是否已存在历史明文令牌（用户机状态，未查）；⑤ GIT_DVC/超限路径的 bash 侧对偶行为未复测（§4.7 已覆盖面不重复）。

**取证偏差登记**：① 前摘要「TS 包 23 文件」系 Glob 含未跟踪 dist 噪声，本轮 git ls-files+show --stat 双口径定案 21；② 前摘要「五命令缺 inspect」为推断，本轮 index.ts L28-32 注册面实证确证（另坐实 create 形态偏离、坐标 URI 全线缺失两项增量）；③ 立项时曾把 EVD-P2009-001 的 rerun 缺陷清单当作 TS 包素材，甄别后确认其全部属 bash 侧，本节仅引 L241/L235/L148-150 作对偶口径。

**限定与更正**：① 本节**不裁** cli/ TS 包保留或废弃（双实现收敛属人类工程决策），只证其入仓未过 Task Card 门禁、契约差集与零门禁三项事实；② E-2/E-5 判「实现偏离已 ACCEPTED 契约」，不判契约本身合理性（退出码七档设计归规范权威）；③ AI-IDE-001 在本节仅作 E-1 判据源引用，其治理门禁面结论全部以 ㉛ 为准；④ push off-by-one 为静态疑点登记，未运行证伪前不作缺陷定级；⑤ 涉规范/卡片/EVD/CI/产品码/lock 六类禁改面**只登记不改动**；⑥ 全部结论为静态取证 + git 实证，E4 一律 unproven（TS 包从未进入任何 E4 流程恰是 E-1 的组成部分）。

**整改待批（AI 不自决，须人类先出 `READY` + `implementationAuthorized: true` Task Card）**：① **E-1 前置裁决**：`cli/` TS 包去留——废弃（git 移除）或保留（补发 Task Card 将 `cli/**` 纳入 allowedPaths + traceability-matrix CAP-DST-004 登记双实现关系 + 明确与 scripts/aih 的收敛路线），由产品/验证权威裁决；② 若保留：按 E-2..E-5 差集出整改卡——七档退出码对齐并消灭 catch-all 归一化、凭据迁移 OS Secret Store/受保护引用 + login 取消命令行明文密码、补 `inspect` 与 `aih://` 坐标解析、pull 真 `--verify`（含摘要比对与退出码 7）+ `--include/--exclude/--resume`、JSON 输出补 `operationId/requestId/traceId/error.code/error.retryable`、create/push/complete 接 Idempotency-Key、push 分片边界修正；③ **validate-task-card 增「产品码径覆盖率」校验维度**：仓内存在 `bin.*`/可分发声明的源码目录须命中某卡 allowedPaths，否则告警——并入 ㊼ 整改② 的「路径存在性+授权面」同批工具链卡；④ 若保留：cli 包 lint/test/build 接入 CI（或显式登记为「未分发工件」豁免）；⑤ TASK-P2-009 若扩径须重出卡片与 baseCommit（现卡禁改，同 ㊼ R-1 候选位）；⑥ EVD L34 teams/principals 口径更正随 ⑤ 提交轮回写（§4.7 既有裁定）。**②④ 的 SKIP/注入不致败同构面并入 ㉟ 整改⑬ / ㊶ 整改⑦ / ㊷ 整改⑥ / ㊸ 整改⑥ / ㊹ 整改④ / ㊺ 整改⑤ / ㊻ 整改⑤ / ㊼ 整改⑤⑦ 统一证据门禁整改批（本㊽ 为第九挂靠点：TS 包零 CI 门禁 + 4141 行无卡入仓为本批「授权-门禁双缺」最强新证）**。

→ 本项与 ①—㊽ 一并不在本轮 AI 自决实施范围内。

### ㊾ REQ-BKR-001 备份恢复演练域（条款 ↔ 演练脚本 ↔ runbook ↔ backup/restore 脚本族 ↔ EVD 全对撞）

**立项查重**：`REQ-BKR-001` 对 plan 全文 Grep 仅 ㊼ R-6 引其 `blockedBy` 声明面；㉝ L1759 已做 `p5-restore-drill.sh` 等 7 脚本无 `.ps1` 双平台缺失；㊹ Z-8（L2235）已做 runbook 局部三要素（DB 只前向表述、演练频率）——**本节增量 = BKR 条款「备份范围六件 + 行为八条」全表 ↔ 演练/脚本/runbook/EVD 实态对撞**，与上述三面零重叠。`REQ-DST-AI-001`/`REQ-AIW-001` 全程零命中，留作后续候选。

**权威链定装**：REQ-BKR-001（p5-quality-operations.md L101-134：READY/MUST/P5、`blockedBy: [RPO, RTO, production topology]`、E5）；TASK-P5-003（READY + authorized、baseCommit 59f40d28、allowedPaths 四径 `deploy/backup`/`deploy/compose/scripts/p5-restore`/runbook/EVD）；AC-P5-BKR-001（p5-exit-catalog.md L43，五验收要素）；JRN-P5-002（user-journey-catalog.md L82「灾难清空→恢复→随机版本 DVC 校验」）；NFR-REL-007（E5）/**NFR-REL-008 状态 OPEN**（non-functional-requirements.md L56-57，头 L5 自认「若干阈值仍为 OPEN」）。取证对象：p5-restore-drill.sh（186 行）、p5-restore/restore-{postgres,gitea,minio}.sh、backup.sh（96 行）、backup-restore.md（116 行）、EVD-P5003-001.yaml（85 行）全读。

**差集矩阵**：
- **B-1 事实源自纠（撤回前轮「p5-restore 断链」误判）**：Glob `p5-restore*` 只命中文件名不匹配目录内容，曾致「目录及三脚本不存在」错判；本轮 `git ls-tree HEAD` + `Test-Path` 双证 **`deploy/compose/scripts/p5-restore/` 三恢复脚本实存**，runbook L22/L39/L63/L81 四引用**不断链**。真实断链收敛为单点：runbook L21「备份脚本位于 `deploy/backup/`」——该目录实存物仅 README（自称 artifacts directory）+ `.gitignore`（`drill-*/`），**备份脚本实际在 `deploy/compose/scripts/backup.sh`**（dc7efa0，2026-09-16），文档指向错位。
- **B-2 E5 自称 vs 覆盖差（本节核心）**：EVD L56 `notProven: []` 空、L46-48 演练 exit 0 自称 **E5**，但行为八条中 **WAL（L113）、备份加密+异地故障域（L122）、加密 Secret/签名密钥+轮换元数据（L116）、JWT 轮换不收回吊销 Token（L125）、隔离环境凭据不复用（L126）、随机抽样多个 Published Version 走 Git Clone+Tag checkout+DVC Pull+SHA-256（L127）、恢复后运行 Reconciler 无未解释差异（L128）七项在 drill/EVD/runbook 全线无实现无演练痕迹**；AC-P5-BKR-001 五要素仅「SHA-256 抽检」被 canary 面部分满足。条款 L129 红线「不以『备份文件存在』通过」被实质突破——drill 做的恰是植入文件+摘要一致。
- **B-3 canary 替代 Published Version 抽样**：drill L75-79 植入单文件明文 `e5-drill/canary.txt` 校摘要，非真实发布版本链；runbook L46/L66/L84 已写正确抽样法（`status='PUBLISHED' ORDER BY random() LIMIT 3` + Tag/commit 一致 + 对象 sha256）但**无脚本承载、无运行痕迹**——文档知、演练未。
- **B-4 RTO 口径失真**：drill L63 `START_MS` 起于植入/备份/`down -v` 之前，L175 `RTO_MS` 含备份+销毁全程，EVD L55「rto_ms=77607」不是恢复目标度量；且 RTO 目标值本身 `blockedBy` 待人类裁决（见 B-5）。drill L141-142 `pg_restore` 非零仅 log 继续——失败路径不致败。
- **B-5 阈值擅裁**：runbook L11-15 写死「RPO 24h / RTO 30-60min」表，与 REQ `blockedBy: [RPO, RTO, production topology]` + NFR-REL-008 `OPEN` 直接冲突——规范文档替人类预裁未决阈值（㊻ 第⑤项 perf 面同族：彼为先测后裁缺失，此为擅裁入文）。
- **B-6 脚本三轨分叉（backup↔restore 格式互不兼容）**：backup.sh 产 `pg_dumpall` 明文 SQL（L37-39），restore-postgres.sh 却以 `pg_restore --clean` 消费（pg_restore 不能读 plain SQL）；backup.sh 产 `gitea_data.tar.gz`（L50），restore-gitea.sh 期望 `gitea dump` zip（runbook L53-57 命令面又是第三套）；MinIO 面 drill 仅 mirror 2 bucket（L113 `dvc-cache asset-staging`）、runbook L71-74 注释称四桶命令仅一桶、restore-minio.sh L16 四桶——**四套口径无一同**；backup.sh L43/54/65 组件缺失打 `[SKIP]` 仍 exit 0（SKIP 不致败同族）。EVD L62 工件目录 `deploy/backup/drill-BKR-20260711-205416/` 因 `.gitignore drill-*/`（2eb90b0 加入）不可追认——引用证据零实物可复核。
- **B-7 Reconciler 正向存在、恢复后触发缺失**：后端实存 PublishedVersion/AssetRepository/MinioStorage 三 Reconciler + V28 checkpoint + RecurringJobBootstrapper（合规面）；但 drill 恢复后不运行、不校验差异（条款 L128 / runbook L92-94 仅列名）——实现与演练脱钩。
- **B-8 引用计权与卡片治理同型**：EVD L26 `commitSha: 7dc22d44` 即 ㊼ R-3 不可解析 Commit（同物不重立论）；L15-22 三项 taskValidation `result: null`；TASK-P5-003 L102-104 步骤全未勾 + 完成报告空模板；**下游 implementation-status L104/L259 已把该膨胀 E5 转抄为「E5 proven / RTO 77.6s / SHA-256 抽检一致」**——失真证据正在污染状态快照口径。
- **B-9 演练环境合规自认偏离**：drill L33 `--env-file deploy/compose/.env` 同栈同凭据本地 Compose 演练，EVD L77-78 自认 assumption「非隔离生产网」——与条款 L126、runbook L23「隔离网络/独立 Volume、凭据不复用」为已知未裁偏离。

**合规正向面**：drill 有 `DRILL_CONFIRM=yes` 破坏性操作闸门（L23-27）；restore 三脚本薄封装、参数校验齐；backup.sh 产 `checksums.sha256` + manifest `restore_order: [postgres→gitea→minio]`（L69-75，与事实源边界同向）；runbook 有演练记录模板（L97-110）；四脚本作者/日期头合规。

**偏差与 unproven 登记**：① B-1 为「先宽后窄」纪律拦截的本轮自纠（Glob 目录盲区教训，后续路径存在性判定一律 git ls-tree/Test-Path 双口径）；② drill 2-bucket 是演练范围缩窄非断链，已并入 B-6 不单列；③ 本轮无 Docker，drill/backup/restore 运行结果全部 unproven，全部结论为静态取证 + git 实证；④ `.gitignore` 禁 dumps 入库是合规设计，登记的是「EVD 引用了不可追认证物」而非 ignore 本身；⑤ runbook 抽样 SQL 的表字段名核对（`asset_version`/`version_artifact`）未做 schema 对撞，属残留小面；⑥ 涉规范/卡片/EVD/脚本/runbook/状态文档六类禁改面**只登记不改动**。

**整改待批（须人类先出/更新 `READY` + authorized Task Card，TASK-P5-003 现卡不得由 AI 扩径）**：① **E5 证据重写**：EVD-P5003-001 `notProven` 补列 B-2 七项 + B-4 口径更正 + B-9 显式豁免裁决，或补演练覆盖面；现有「E5」自称须验证权威重签，AI 不得改 YAML；② 脚本三轨收敛为一条主线（建议以 restore 侧期望格式为锚反推 backup：pg_dump -Fc + gitea dump zip + mc mirror 四桶），修复 backup.sh↔restore-postgres.sh 格式互斥，runbook L21 指向更正（deploy/backup 为工件目录）；③ runbook L11-15 阈值表撤回或显式标注「建议值，待 NFR-REL-008/blockedBy 人类裁决」；④ drill 补真实 Published Version 随机抽样链（承载 runbook 既有 SQL）+ 恢复后 Reconciler 运行与差异断言 + WAL/加密/异地面裁决（做或显式豁免）；⑤ SKIP 路径统一 fail（backup.sh 三处 + drill pg_restore 续跑 + SKIP exit 2 三型）并入 **㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④ 统一证据门禁整改批（本㊾ 为第十挂靠点：`notProven: []` 对七项覆盖缺失的 E5 自称 + 不可追认工件，是该批「证据卡零缺口声明与实态七缺并存」最强新证）**；⑥ implementation-status L104/L259 E5 措辞随 ① 回写下游。

→ 本项与 ①—㊾ 一并不在本轮 AI 自决实施范围内。

### ㊿ REQ-DST-AI-001 / REQ-DST-AIW-001 Agent 工具授权回路与 AI 数据工作流证据面（条款 ↔ 契约 ↔ 目录 ↔ 授权链 ↔ journey ↔ EVD 全对撞）

**立项查重**：`REQ-DST-AI-001`/`REQ-DST-AIW-001`、`EVD-P4008/4009/4010`、`journey_v28`、`tool_allowlist`、`ConfirmationToken` 对 plan 全文 Grep **零命中**（仅 ㊾ L2385 留「后续候选」声明），本节承接立项。**与 ㉓/㉝/㉞ 承接划界（不重复计权）**：㉓⑤（Scope 非独立维）、㉓⑥（tools/list 不查白名单、`isToolAllowed` 重载未被列表调用）、㉓⑧（`dataset_get_upload_status` write=true 无幂等）、㉓⑫（tool-allowlist 写入口无引用完整性）＋ contractStatus 过度声明及其整改⑫、㉓⑬（11↔11 计数与 fail-closed 正向）、㉞ N2（`_idempotencyKey` 带外通道）、㉝（`writeToolsEnabled` 运行配置恒 false 面）——以上六面**引用为准、本节不再立论**。㉓⑥⑫ 各自成立但均未打通「权威表零写路径 → AGENT 调用端恒拒」的闭环，该闭环即本节核心增量。

**权威链定装**：REQ-DST-AI-001（九条）与 REQ-DST-AIW-001（准入四条件、四个 MUST Tool、所有写 Tool 要求 `Idempotency-Key`、上传成功≠Published）见 dataset-experience.md L338-423；TASK-P4-008/009/010 ↔ EVD-P4008/4009/4010（**实际路径 `docs/ai-spec/tasks/evidence/`**）；实现面 McpToolCatalog（register 恰 11 处：只读 5＋写 6）、McpController L134-200、AgentController L276-303、AuthorizationService L174-179、JdbcAgentToolRepository、AgentToolRepository、MyBatisAgentIdentityRepository L55/L114、AgentManagementApplicationService L130-135；数据面 V4__authorization.sql L134/L146 与 V3 L90 旧列；契约面 tools.yaml（L8 contractStatus、L28-45 authorizationModel、L49 writeTools default false）。

**差集矩阵**：
- **W-1 工具授权回路断裂（本节核心）**：V4 L134 明言「`iam_agent.tool_allowlist` 仅保留兼容，**不再双写**」、L146 表注释「权威来源（取代双写）」；**读面**唯一为 `JdbcAgentToolRepository.isToolAllowed`（`SELECT count(1) FROM iam_agent_tool`），经 `AuthorizationService.requireToolAllowed` 被 **MCP 与 REST 双入口**共三处行使（McpController L157；AgentController L284 `requireAgentAccess`／L300 `requireAgentWriteAccess`）。**写面三重缺失**：全仓 `INSERT INTO iam_agent_tool` **零命中**（含迁移种子、deploy fixture、任务脚本三口径）；`AgentToolRepository` 端口**本身无任何保存方法**；唯一管理入口 `updateToolAllowlist`（AgentManagementApplicationService L130-135）经 MyBatisAgentIdentityRepository L55/L114 **只写旧列**。结论：静态上 `iam_agent_tool` 恒空 ⇒ 任何 AGENT principal 的 MCP tools/call 与 `/agent/*` 调用恒拒 `MCP_TOOL_NOT_ALLOWED`，注册默认 `toolAllowlist(List.of())`（同服务 L87）；**AC-DST-AIW-001「显式授权写 Agent」在真实部署不可达**——白名单可编辑但编辑结果不被任何判定读取。与 ㉝ 叠加＝双锁串接：即便补上表写路径，`writeToolsEnabled` 仍默认 false；现行状态是**两把锁的钥匙都不存在**。
- **W-2 E4 声称的 AGENT 链零行使（journey 新证）**：verify-journey.sh L20 `ACCESS_TOKEN=""`、L128-160 经 **admin bootstrap 登录取得 USER JWT**、L1025/L1035 `mcp_call` 复用该 token ⇒ V28「写工具默认拒绝」PASS 实际命中 McpToolCatalog L109-111 的 `writeToolsEnabled` **配置闸门**（principalType=USER 下 McpController L156-158 的 AGENT 白名单分支**从未被执行**；EVD-P4008 L51-53 亦自认拒绝原因「denied when write tools disabled」）。即 AC-DST-AIW-001/002 的 E4 声称对「AGENT 认证→白名单→写开关→业务」全链**零覆盖**；且按四处治理规范原文（source-and-status-policy L72、generic-execution-protocol L141、dataset-agent-exit-catalog L97、p0b-exit-catalog L147：401/403 拒绝不能证明授权能力完整），以拒绝断言充当 AC 出口本身不成立——叠加 W-1，被拒的原因链与授权无关。
- **W-3 EVD 三卡证据失真**：EVD-P4008 status PARTIAL（L3）却 `evidenceLevel: E4`（L48），唯一 proven 项为 W-2 所述拒绝断言、notProven 含 job_task 500 SKIP（L54-56）、三项 taskValidation `result: null`（L16/18/21）；EVD-P4009 与 EVD-P4010 **逐字节同文**（仅 taskId/evidenceId/TaskPath 三字段异，同 command L43、同时间戳 L31/44/45），`evidenceLevel: E3`（L48）对 TASK-P4-009 L23-24/L50 要求的 **E4 错配**，且 `proven: []`（L51）与 `notProven:` 空（L52）并存——空壳卡互证；两卡 commitSha `7dc22d44` 为 ㊼ R-3 不可解析 Commit **第四次出现**（同物引用计权，不重立论）。
- **W-4 allowedPaths 虚指扩至三卡**：TASK-P4-008 L48 `deploy/compose/scripts/p4-dst-ai`、TASK-P4-010 L47-48 `p4-journey`＋`fixtures/p4`、**TASK-P4-007 L36 `fixtures/p4`**——Glob 实锤 scripts/p4-* 仅 `p4-onboarding.sh`、`fixtures/p4` 无内容。三卡授权面均含不存在路径，直接强化 ㊼ 整改②／㊽ 整改③ 的「allowedPaths 目录存在性」校验器断言。
- **W-5 ConfirmationToken 死代码＋状态文档转抄**：REQ-MCP-005 人工审批闸门的 `ConfirmationToken`（domain 定义）在 **main 全链零引用**（Grep 20 命中全部为该定义＋两个测试类），无任何适配器/服务构造或消费它；implementation-status L111/L166 却将其记为「已实现」——与 ㊾ B-8 同族的「状态快照转抄未验证声明」下游污染。
- **W-6 规划 Tool 契约双缺（弱项）**：`asset_submit_version`/`dataset_get_preview` 在 README L43/45、dataset-experience L393、p4-agent-integration L192 规划在列，tools.yaml 与 McpToolCatalog 零存在——属路线图与契约的口径差而非 MUST 违约，低权重登记。

**合规正向面**：McpController tools/call 未认证 fail-closed（L140-144）、双 Permission 校验（L153-158）、限流（L164-168）、写工具幂等链（L174-188）结构完整；目录 11 Tool 与契约 11 一一对应（㉓⑬ 已钉）；tools.yaml `authorizationModel` 声明 default-deny 与 writeTools 默认 false 与代码装配方向一致；REST `/agent/*` 与 MCP 通道复用同一 `AuthorizationService`（适配器共享服务，边界合规）。

**偏差与 unproven 登记**：① 本轮曾按 `docs/ai-spec/evidence/` 臆测 EVD 路径致 Read 失败，Glob 修正为 `docs/ai-spec/tasks/evidence/`——与 ㉞ 偏差② 同型的「路径臆测」教训再现，已自登；② 无运行栈：`iam_agent_tool` 运行态是否被 DBA 手工 seed 未查库，W-1「恒拒」为静态口径（代码/迁移/fixture 三写面零证据）；③ TASK-P4-009/010 卡内 `mvnw verify`/redocly 命令未运行；④ 涉规范/卡片/EVD/契约/产品码五类禁改面**只登记不改动**；⑤ 全部结论静态取证，**E4 一律不宣称**。

**整改待批（须人类先出/更新 `READY` + authorized Task Card，AI 不自决）**：① W-1 授权写路径接通——`AgentToolRepository` 端口补保存方法、`updateToolAllowlist` 事务内同写权威表（旧列兼容期双写或显式废弃裁决）、补受影响 Agent 的迁移回填与引用完整性（承接 ㉓ 整改⑫）；产品码＋迁移，禁改面；② journey 增设真实 AGENT 链旅程：AGENT 凭据交换取 JWT→白名单命中→只读 Tool 正向断言→写 Tool 在 `writeToolsEnabled=true` 配置下由白名单裁决的**二段式正负断言**，替代现行「配置闸门拒绝即 PASS」；③ EVD-P4008 的 E4 自称按 W-2 撤回重签，P4009/P4010 同文空壳卡作废重写（TASK-P4-009 L97-98 表不得勾 PASS）；④ 三卡 allowedPaths 更正为实存路径或删项（随 ③ 提交轮回写）；⑤ ConfirmationToken 去留裁决（接入 REQ-MCP-005 或移除＋implementation-status L111/L166 更正）；⑥ **validate-task-card / EVD 校验断言扩充**并入统一证据门禁整改批：E3/E4 与 Task `requiredEvidenceLevel` 交叉校验、EVD 跨卡同文（哈希）查重、`proven: []`＋`notProven` 空并存告警、allowedPaths 目录存在性——**本㊿ 为第十一挂靠点**（「拒绝断言冒充授权链 E4」＋「同文互证空壳卡」是该批迄今最完整的两类负样本）。**⑥ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤ 挂靠清单。**

→ 本项与 ①—㊿ 一并不在本轮 AI 自决实施范围内。

### 51 REQ-DST-DISC-001 讨论治理域（条款 ↔ 实现 ↔ 契约 ↔ DB ↔ 前端 ↔ Task/EVD 全对撞）

**立项查重**：`REQ-DST-DISC-001`、`TASK-P1-012`、`EVD-P1012`、`asset_discussion` 对 plan 全文 Grep **零命中**（DiscussionPanel 仅 ㉚ L516/L554 与 ㊵ W-3 L2096 以路由/门控样本身份出现，不构成该域对撞节），本节承接立项。**承接划界（引用为准、不重复计权）**：⑯ 错误码挪用面（D-2 只打「讨论域零抛出」增量）、㉑ ThreadStatus 四方差集（D-7 只补行为零转换面）、㉖ 审计事件域普查（D-3 只钉条款六事件三方对撞增量）、㉘ Notification/Outbox 全域结构（D-8 只钉吞异常对条款 L166 原子声称的破口）、㊷ 合成 PrincipalContext（mention 通道的 D 面引用之）、G3 `asset_subscription` 独立资源裁决归 REQ-DST-SUB-001 不重开、㊿ W-3 `7dc22d44` 第四次出现（本节第五次，同物互证不重立论）、㊿ 整改⑥ E3/E4 交叉校验家族（本节为**第十二挂靠点**）。

**权威链定装**：REQ-DST-DISC-001（MUST/P1/E4，五 AC）见 dataset-experience.md L125-181——领域边界 L142-150、行为 L152-161、一致性/审计/错误 L163-171、目标契约与数据 L173-180；TASK-P1-012.md（baseCommit `08c25af8`、五 AC scenarioEvidencePlan 全 E4 L25-29、行为切片表五连「Execute | PASS | E4」L105-109、实施步骤三框**全未勾** L122-124）↔ EVD-P1012-001.yaml；实现面 `com.aihub.asset.discussion` 全包 27 类（DiscussionController、CommentController、DiscussionApplicationService 385 行、MyBatisDiscussionRepository、三 Entity/Mapper）；数据面 V15__discussion.sql（88 行：`asset_discussion`/`asset_comment`/`asset_comment_revision`/`asset_subscription`）；契约面 aihub-v1.yaml discussions 12 路径 L2214-2626 + schemas L4899-4972；前端面 DiscussionPanel.tsx（498 行）+ features/assets/api.ts L121-162。

**差集矩阵**：
- **D-1 幂等/并发双零（核心，AC-DST-DISC-003 静态不可满足）**：条款 L165 要求创建/修订用 `Idempotency-Key` + `rowVersion`。**实现**：DiscussionController L54-62 与 CommentController L34-42/L54-96 全端点零 `IdempotencyKey`/`RequestHeader` 参数（包内 Grep `idempotency` 零命中）；V15 两主表无 `version` 列；MyBatisDiscussionRepository `updateComment` L109-114 / `updateThread` L60-63 均无乐观锁谓词。**契约**：`IdempotencyKey` 参数在 L3681 定义且他域 17 处 `$ref`（L277/360/377/434/536/587/655/875/991/1056/1144/1229/1399/1416/1669/1917/1963），**discussions 区间 L2214-2626 零引用**；`rowVersion` 全契约仅他域一处。**前端**：api.ts 讨论写请求零幂等头。⇒ 「重复请求不产生重复回复」在契约、实现、DB 三层均无承载物，卡片 L107 该行 PASS|E4 为**无中生有**。
- **D-2 条款五错误码死枚举**：`DISCUSSION_NOT_FOUND`/`DISCUSSION_LOCKED`/`COMMENT_TOO_LARGE`（ErrorCode.java L172/175/178，注释齐备）+`CONCURRENT_MODIFICATION`（L119，他域在用）在 **discussion 包内零抛出**（包级全量比对）；实现实际语义挪用：thread/comment 不存在抛 `ASSET_NOT_FOUND`（Service L341/L347）、锁定线程发言抛 `ASSET_STATE_NOT_ALLOWED`（L122，包内八处同码）、超长正文抛无码 `ValidationException`（L353/L356，`COMMENT_TOO_LARGE` 弃用）。错误语义三方错位（条款 L169-170 ↔ 契约 x-error-codes ↔ 实现），客户端无法按条款码分支。
- **D-3 审计事件三方三套名 + 三动作零审计**：条款 L167-168 六事件（DISCUSSION_CREATED/COMMENT_CREATED/**COMMENT_REVISED**/COMMENT_RETRACTED/**DISCUSSION_LOCKED**/**DISCUSSION_MODERATED**）；契约 x-audit-event 八枚（L2227-2580 含 COMMENT_EDITED/COMMENT_HIDDEN/COMMENT_UNHIDDEN/THREAD_LOCKED/THREAD_UNLOCKED）；实现五处 `auditDiscussion`（Service L96/135/209/226/258 = DISCUSSION_CREATED/COMMENT_CREATED/COMMENT_RETRACTED/COMMENT_HIDDEN/THREAD_LOCKED）。差集：`COMMENT_REVISED` **无产者**（editComment L178+ 全程无审计调用）、`unhideComment`/`unlockThread` 无审计（契约却有 COMMENT_UNHIDDEN/THREAD_UNLOCKED）；命名错位 DISCUSSION_LOCKED↔THREAD_LOCKED、DISCUSSION_MODERATED↔COMMENT_HIDDEN。卡片 crossCuttingPlan L38 再写第四套名 `COMMENT_MODERATED`。承接 ㉖ 普查、此为该域条款级对撞增量。
- **D-4 排序键与 Cursor 形状违约**：条款 L161「列表按 `updatedAt` 和稳定 ID 排序，回复使用 Cursor」。**线程列表**：Repository L70 `orderByDesc(createdAt)` **单键**（≠updatedAt、无 ID 破并列），L78 `nextCursor=末条 createdAt` ⇒ 同秒记录跨页漏/重（对照 AssetSearchDao L96 两键合规做法）。**评论列表**：契约 L2332 声明 `data: $ref CursorPageComment`，实现返回**裸数组**——Service L168-174 端口 `List<CommentView>`、Repository L118-127 `LIMIT+1` 探测后 `limit()` **白取丢弃** hasMore/nextCursor、api.ts L153-162 `Promise<CommentView[]>`。游标分页契约形状违约且前端永远拿不到 nextCursor。
- **D-5 CommentController `threadId` 路径参零行使**：L55/L65/L73/L81 四端点收 `@PathVariable threadId` 但 Service 侧 `loadComment(commentId)`（L345-349）仅按 commentId 取数，**从不校验 comment.threadId == 路径 threadId**——任意合法线程 ID 均可对他人线程评论执行 edit/retract/hide/unhide（权限门槛 `asset:discuss`/`asset:moderate` 是资产级的，跨资产仍需 comment.assetId 命中，故非直接越权面，属**资源层级一致性断言缺失**；配合 D-6 幽灵 Thread 可放大）。
- **D-6 Thread 无 body、versionId 全缺、资产存在性不校验**：条款 L145「可选绑定精确 `versionId`，不能绑定含糊 latest」——V15 L12-16 无列、契约 CreateThreadRequest L4907-4914 无字段、实现无参。createThread（Service L82-101）权限→title 校验→**直接 insertThread**，不查 `asset` 表存在性；V15 L13 `asset_id` **无 FOREIGN KEY** ⇒ 对不存在/已删资产可入库幽灵 Thread（listComments L170 只验 threadId 不验 asset 存活）。
- **D-7 CLOSED 僵尸态**：`ThreadStatus.CLOSED` 在枚举、V15 L21 CHECK、契约 L4901 三处在册，但**零转换方法、零端点、零测试**——归档只读语义（条款 L159）仅靠 LOCKED 半边，CLOSED 为纯字面。承接 ㉑ 四方差集，此行为面增量。
- **D-8 同事务原子声称破口**：条款 L166「Discussion/Comment/Revision、Notification 和 Outbox 在同一 PostgreSQL 事务内提交」；卡片 crossCuttingPlan L36 声称「Notification/Outbox atomic with comment write」。**实现**：`auditDiscussion` L363-372、`publishOutbox` L377-383、mention 通知段 L298-308 全部 try-catch 吞异常（仅 LOG.warn/error）⇒ 任一侧失败**不回滚**，审计/出站/通知静默丢失。名义在同一 `@Transactional` 内，实为**尽力而为**——与 ㉖「审计写失败策略」同族，讨论域以吞为主且与卡片声称直接互斥。
- **D-10 untrustedContent / 受限 Markdown 双侧未实现**：条款 L156-157 要求受限 Markdown（禁脚本/危险 HTML/自动外链抓取）+ `untrustedContent=true` 标记。**契约**：CommentView L4950-4961 无 `untrustedContent` 字段（全契约该词仅 Preview 域 L4804/5055/5098）。**前端**：DiscussionPanel L198-199 `Typography.Paragraph whiteSpace:'pre-wrap'` **纯文本渲染**——恰好无 HTML 注入面，但「受限 Markdown」功能不存在、无 sanitizer、无 untrusted 徽标；DiscussionPanel.test.tsx 对 script/XSS/sanitize **零用例** ⇒ 卡片 L109 行 AC-DST-DISC-005 PASS|E4 **零对应实现、零对应测试**。
- **D-12 契约字段形状漂移（三面各说各话）**：契约 CommentView（L4950-4961）声明 `editedAt` 而实现 CommentView.java **无此字段**；契约**无** `parentId`/`revisionCount`/`assetId` 而实现有、且前端**依赖未声明字段**——DiscussionPanel L68/L341 `c.parentId`（线程树根判定）、L131 `c.revisionCount`（edited Tag）。title 长度三方不一致：契约 maxLength 200（L4914）↔ Service 校验 256（L87）↔ DB VARCHAR(256)（V15 L14）；CreateThreadRequest.java 记录**零 Bean Validation 注解**（契约 200 在实现侧完全不生效）。
- **D-11 E4 声称载体证据崩塌（八项）**：EVD-P1012-001.yaml——① status PARTIAL（L3）对卡表五连 PASS；② `execution.evidenceLevel: E3`（L46-53）对卡 `requiredEvidenceLevel: E4`（L55）与 scenarioEvidencePlan 五 E4（L25-29）；③ `coverage.proven` 与 `coverage.notProven` **同列全五 AC**（L57-72 自斥）；④ `commitSha: 7dc22d44…`（L31）经 `git cat-file -t` 实测 **fatal: could not get object info**（仓库不可解析，`7dc22d44` 系第五次虚指）；⑤ `governance.taskValidation.result: null`（L21）、changedPath/completion 校验缺位；⑥ artifacts 全空（L74-80）；⑦ environment.os Ubuntu 24.04 + `/home/axe/…` 路径与 Windows 仓库实况不符；⑧ `review.accepted: false`（L95）+ 卡内实施步骤三框未勾。**门禁实跑**：`./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P1-012.md` → **exit 1 FAILED**「baseCommit does not match current HEAD (a022796…)」（baseCommit `08c25af8` 存在但漂移）——按 AGENTS 门禁，该卡当前**不得作为实施与交接依据**。

**合规正向面**：① 搜索引擎不索引讨论正文✓（条款 L160；AssetSearchDao 对 comment/discussion 零命中 + BASE_SELECT L49-61 仅 asset 系表）；② Agent 默认无 discuss 通道✓（条款 L149；McpToolCatalog 对 discussion/comment 零命中，无 AI 自动发言 Tool）；③ mention 可见性校验✓（Service L291-294 逐 mention 过 `isResourcePermitted(ASSET_READ)`，不可见即静默跳过；合成 PrincipalContext 面归 ㊷）；④ Tombstone 不物理删✓（撤回/隐藏仅置 CommentStatus，revision 表只追加，V15 L66-70）；⑤ 权限种子无 AGENT 授予痕迹✓（`asset:discuss` 仅授 USER 系角色：V14 L17-37、V20 L22-38、V23 L48）；⑥ 读取继承 `asset:read`、写 `asset:discuss`、moderate 门槛三权限码与条款 L147-148 对齐，Controller 层鉴权在位（requirePermission 全端点覆盖）；⑦ 订阅链（G3 已闭合面）复用 notification/subscription 平台服务，未见局部替代。

**偏差与 unproven 登记**：① 全部结论静态取证 + git/门禁实测，**E4 一律不宣称**；EVD 声称的 `mvnw test`/`verify.sh` 本轮未复跑，不作为采信依据；② `7dc22d44` 不可解析为 git 实证（第五次），与 ㊼/㊾/㊿ 互证成族；③ 幽灵 Thread 的下游联动（Gitea 同步/发布链是否会消费 `asset_discussion.asset_id`）未追，D-6 仅钉写入侧无守卫；④ `asset_comment_revision` 的 Revision 不可变性仅查了表结构（只追加、无 UPDATE 语句），未做 DB 触发器对撞（㊲ 触发器族方法未复用，属残留小面）；⑤ 本轮抽测中一处路径臆测（`discussion/adapter` 目录不存在，实为 `api/`）即时 Glob 修正——与 ㉞/㊿ 「路径臆测」教训同型第三次再现，落盘前一律实测已执行；⑥ 涉规范/卡片/EVD/契约/产品码/迁移六类禁改面**只登记不改动**。

**整改待批（须人类先出/更新 `READY` + authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① D-1 幂等并发接通——契约 discussions 写端点补 `$ref: IdempotencyKey`、前向迁移补 `version` 列、Repository 更新语句补乐观锁谓词、Controller/前端补头透传（AC-003 才有静态可满足性）；② D-2/D-3 语义归位——讨论域按条款码抛出（DISCUSSION_NOT_FOUND/DISCUSSION_LOCKED/COMMENT_TOO_LARGE/CONCURRENT_MODIFICATION）、审计事件名三方对齐并补 COMMENT_REVISED/unhide/unlock 三处缺失写出；③ D-4/D-5/D-12 契约形状修正——评论列表按 CursorPageComment 实现（或契约降级为数组并显式裁决）、排序改 `updatedAt+id` 复合键与不透明 cursor、CommentController 补 `comment.threadId==path.threadId` 断言、CommentView 字段并集对齐（parentId/revisionCount 入契约、editedAt 做或删除）；④ D-6/D-7/D-8 行为补齐或显式豁免——versionId 绑定（做或修订条款）、CLOSED 转换端点、审计/Outbox 吞异常策略随 ㉖ 全域裁决一并定（原子 or 补偿 Job，二选一后卡片 crossCutting 措辞回写）；⑤ D-10 裁决「受限 Markdown + untrustedContent」落地或撤回条款，前端维持纯文本则须契约补 untrustedContent 语义说明；⑥ **EVD-P1012-001 作废重写**（proven/notProven 自斥、E3 对 E4、commitSha 虚指、artifacts 空、环境不符五项同修），并将「validate-task-card 增 baseCommit↔当前 HEAD 断言 + EVD commitSha `git cat-file` 存在性断言」**并入统一证据门禁整改批：本 51 为第十二挂靠点**（「E4 卡表 PASS 对 E3 自斥证据 + 门禁实跑 FAIL 仍挂 IMPLEMENTED 声称」是该批迄今唯一带门禁实证的样本）。⑥ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥ 挂靠清单。

→ 本项与 ①—51 一并不在本轮 AI 自决实施范围内。

### 52 REQ-DST-TAX-001 数据集分类/Facet 检索域（条款 ↔ 实现 ↔ 契约 ↔ DB ↔ MCP ↔ 前端 ↔ Task/EVD/证据时间轴全对撞）

**立项查重**：`REQ-DST-TAX-001`、`TASK-P1-010`、`EVD-P1010` 对本节前对 plan 全文 Grep 零独立成节（㉔ 仅以 Facet 缓存键/契约参数/Cursor 三偏差样本身份出现）。**承接划界（引用为准、不重复计权）**：㉔ Z-1 Facet 缓存键缺主体（buildFacetCacheKey L382-405 本段现码复测吻合）、Z-2 **限 /assets/facets 侧**（契约 L1849-1860 列 11 参：keyword/type/namespace/organizationId/framework/task/format/modality/tagId/owner/sensitivity ↔ AssetController L127-133 实收 2 参——本段定装 11:2 现势成立；**search 侧已失效**：AssetController L100-120 已演进 24 参含 L108-111 四 codes List，㉔ 对 search 的参数论断不再引用）、Z-3 Cursor 不绑定（L305-313/L413-429 复测吻合）、㉓ MCP 通道面、㊵ W-3 前端筛选门控、㊷ 日志脱敏全域（条款 L68-69 搜索脱敏引用之）、51 节 D-11 E4 崩塌样本结构（本节为**第十三挂靠点**并新增「合并回归孤儿证据」亚型）。

**权威链定装**：REQ-DST-TAX-001（dataset-experience.md L12-70）——九维分类表 L32-41、服务端派生不可伪造 L43、OR/AND 写入 OpenAPI/MCP L53、排序五白名单 L55 + 相关性顺序 L56、matchedFields L58、接口 L63-64、四错误码 L66-67、搜索脱敏 L68-69、E5 万级 L70；AC 权威＝dataset-agent-exit-catalog.md L20-23（001 组合搜索+排序 E4、002 双 Principal Facet/autocomplete/Cursor E4、003 停用字典回显+拒绝新建 E4、004 未知 code/非法 sort/cursor fail closed E3/E4）；载体 TASK-P1-010.md（baseCommit `38fd44d2` 实测**存在**、卡表四连「PASS | E4」、commitSha `7dc22d44` **第六次虚指**——本轮 `git cat-file -t 7dc22d44` 实测 `fatal: Not a valid object name`）↔ EVD-P1010-001.yaml（E3/PARTIAL、proven TAX-003 断言＝verify.sh V15、notProven 001/002/004）。实现面 AssetSearchDao（486 行）、AssetApplicationService、AssetController/AssetCatalogController、AssetRequestMapper、DatasetProfile、McpToolCatalog L149-175；数据面 V14 L93/L102、V22 L42-43、V25 L26/L38、V35 L10-35；验收面 verify.sh L1036-1049；测试面 AssetFacetConsistencyIT（446 行）+ AssetSearchPerformanceTest（299 行）。**多卡共担登记**：AC-DST-TAX-001 同时被 TASK-P2-011（L32/L125 /datasets 全维 E4）、TASK-P2-013（L22 languageCodes）绑定——三卡口径互斥（P1-010 卡 PASS|E4 ↔ P0BR-049 L22 E3/E4 ↔ P2-011 待做），卡间职责归属无裁决物。

**差集矩阵**：
- **T-1 facet SQL 语法死路（核心）+ 合并回归时间轴实锤**：AssetSearchDao.facet() L130-137 四多值维度把 `CROSS JOIN jsonb_array_elements_text(ad.task_codes) t(val)` 拼在 `FACET_FROM`（L67-72，以 `WHERE a.deleted = 0 AND a.provisioning_status='COMPLETED'` 收尾）+ `w`（appendCommonFilters L177 起全为 ` AND ...` 片段，恒非空）之后——`FROM…WHERE…CROSS JOIN…GROUP BY` 是 PostgreSQL 硬语法错误；且 L130-137 各段自带的 ` AND ad.task_codes IS NOT NULL` 亦悬空在 WHERE 之后。**该 UNION 无维度分支开关——任何一次 getAssetFacets 调用必携坏段** ⇒ 生产 Web（AssetsPage.tsx L114-116 每次列表加载即调 /assets/facets） facet 侧栏必 500。三层掩盖：① AssetSearchDaoTest 仅 2 用例且全 Mockito 字符串断言（L46 `when(jdbcTemplate.query(...))` 不执行真 SQL）、零 facet 用例；② AssetFacetConsistencyIT 与 AssetSearchPerformanceTest（L175-197 facetCountPerformance，真库 Testcontainers PG17、10 000 资产夹具）均 `@Testcontainers(disabledWithoutDocker=true)`（IT L70/Perf L55）无 Docker 即静默跳过；③ build-logs 对 IT 零运行记录（Grep 命中 0）。**时间轴三方 git 实证**：坏 SQL 唯一引入提交 `98d53de`（2026-07-08「Stage3 Task3.2 Facet SQL GROUP BY」，位于 origin/ai 业务波次侧支 `f974ad4`——实测该版本 CROSS JOIN×4）；AssetFacetConsistencyIT 诞生于 P0-B 底座侧 `8ed02ef`（2026-09-20，实测该版本 facet 区 **CROSS JOIN×0**，388 行版）⇒ EVD-P0BR-049 的「IT 9/9 通过」与 `.trae/evidence/facet-e4-049.txt`（在档实存，A—H 段含跨维 AND/双主体 E4 记录）**对当时无坏 SQL 的底座侧代码真实有效**；`c633ebc`（2026-09-22 merge origin/ai）取回 f974ad4 侧 facet() 实现 ⇒ **合并回归**：自 2026-09-22 起 HEAD 上不存在任何跑通过 facet 的证据，EVD-P0BR-063（2026-09-21，早于合并）的 flake 记录同不适用。此即「证据对旧码有效、对新码失效」的**合并回归孤儿证据**亚型——第五/六次虚指家族之外的新形态。
- **T-2 排序白名单双源零实现**：条款 L55 五白名单 `relevance/updatedAt/publishedAt/sampleCount/totalBytes` + L56 相关性顺序。**契约**：全仓零 `sort` 查询参数（L4246-4272 sortOrder 属字典字段、L4619 sampleCount 属响应字段）。**实现**：asset 包无 sort 入参（Grep 仅 discussion orderBy 两命中），ORDER BY 固定 `CASE status, created_at DESC, id DESC`（AssetSearchDao L96）。⇒ AC-DST-TAX-004「非法 sort fail closed」**结构性不可达**（无参数可非法）；AC-001「正确排序」无承载。
- **T-3 sizeBucketCode 客户端可伪造**：条款 L43「服务端派生、不可伪造」+ INV-DST-003（entity-invariants.md L180）。实测伪造链全通：CreateAssetRequest.DatasetInput L72 直收 → AssetRequestMapper L102-110 透传 → DatasetProfile（47 行十参 record 零派生逻辑）→ MyBatisAssetRepository L341/L374 落库 V14 L93 列；全仓零分桶规则代码。前端 AssetSettingsPage L272-304 表单直提 sizeBucketCode/taskCodes/languageCodes（伪造链用户可见面）。**字典侧**：V14 L102 注释引用「字典 size_bucket」，该字典种子全迁移目录 Grep 零命中（dataset_task/dataset_language 种子在 V35 L10-35，size_bucket 无）⇒ 即便补校验也无物可校。
- **T-4 分类维度写入 fail-open**：validateGovernanceFields（AssetApplicationService L503-514）仅验 licenseCode +（MODEL: framework/task｜DATASET: 单值 format/modality）；**四 JSONB codes + sizeBucketCode + sensitivityCode 全不过字典校验**（DICT 常量 L72-77 五个无 dataset_task/dataset_language/sensitivity/size_bucket）⇒ AC-004「未知 code fail closed」半破（tagIds 经 resolveValidatedTagIds L523-532 ACTIVE 校验 ✓ 为唯一合规多值维）。查询侧未知码静默回 0（IT L274/L414 用例在案）与写入侧放行组合成「写进库、查不出、不报错」的静默脏数据通道。
- **T-5 MCP asset_search 分类维整体缺失**：条款 L53「OR/AND 规则写入 OpenAPI/MCP Description」+ L63 目标 MCP asset_search `type=DATASET`。McpToolCatalog L149-175：schema 仅 keyword/type/namespace/tagId/cursor/limit 六参，L170-171 服务调用四 codes 传 **null**，Description L151 无 OR/AND 语义。⇒ OpenAPI 侧合规（契约 L1719-1737/L1842/L2376/L2392-2410 声明在场）、MCP 侧条款违约——与 ㉓「REST 演进 MCP 停滞」同族增量样本。
- **T-6 EVD-P1010 proven 断言错位（TAX-003 面崩）**：EVD L61 proven「AC-DST-TAX-003 = verify.sh V15 dataset Facet 权限过滤 PASS」。实测 V15（verify.sh L1036-1049）只做两件事：asset.visibility **列存在性** + 匿名 /assets/facets **401**。AC-003 权威语义（exit-catalog L22）＝「停用字典项仍回显、新引用被拒绝、审计留痕」——**三方零重合**。列存在 ≠ 停用回显；匿名 401 ≠ 停用项拒绝新建；无审计断言。该 AC 的 proven 状态按证据内容应为 notProven（与其自身 L64/L66 对 002/004 的 notProven 坦承形成选择性错位）。
- **T-7 autocomplete 后端全链零实现**：AC-002（exit-catalog L21）明列「不可见资产不影响 items/count/**autocomplete**/Cursor」。`autocomplete` 对 backend/src 全量 Grep **零命中**（契约同样零端点）；前端仅 auth/admin 两页命中（他域）。⇒ AC-002 四承载面中 Cursor 有形无绑（㉔ Z-3）、autocomplete 无物——该 AC 连「实现存在待验证」都不成立，EVD notProven 反而是卡表链路中最诚实的一格。
- **T-8 E5 载体规模不达标且同连坐**：条款 L70「索引和排序必须有 **10 万**资产规模的 E5 性能数据」。AssetSearchPerformanceTest L60 `SEED_ASSET_COUNT = 10_000`（差一个数量级）、且 facetCountPerformance 用例自 c633ebc 起被 T-1 连坐（有 Docker 环境 HEAD 上该测试**必失败**，无 Docker 静默跳过）⇒ REQ-PERF-001 的 Facet P95≤500ms 声称与 AC 的 E5 前置于 c633ebc 后双双失效；本域无任何 E5 在档。
- **T-9 Task 卡 ↔ EVD ↔ 门禁三方对质（承 51 样本结构）**：卡表四连 PASS|E4 ↔ EVD `execution.evidenceLevel: E3`/status PARTIAL（TAX-002 五 AC 中孤缺 proven 亦缺 notProven 列名）；environment Ubuntu/`/home/axe/` 与仓库实况不符；**门禁实跑**：`validate-task-card.ps1 -TaskPath TASK-P1-010.md` → exit 1 FAILED 唯一条目「baseCommit does not match current HEAD (a022796…)」（baseCommit `38fd44d2` 存在但漂移）——按 AGENTS 门禁该卡当前不得作为实施与交接依据。

**合规正向面**：① 查询侧 JSONB 过滤语法正确（appendJsonArrayContains L249-257 EXISTS 子查询——坏仅在 facet 聚合侧）；② 双主体 Facet 隔离**测试设计**在位（IT L346 member/outsider 真库断言、L397 无 asset:read 抛 AuthorizationException fail-closed、L325 跨维 AND、L373 ARCHIVED 默认排除、L274/L414 未知 tag/sensitivity 回 0）——该九用例是 002/004 的正确载体，只欠 T-1 修复后即可复用；③ getAssetFacets 全条件入口权限过滤齐备（Service L370-385 allowed+accessScope+statuses）；④ 契约 searchAssets/facets/datasets/models 的 OR/AND 语义声明齐（L1719-1737/L1842/L2376/L2392-2410）；⑤ DICTIONARY_VALUE_INVALID/TAG_VALUE_INVALID **有真实抛出**（TagValidationService L92/96/101、DictionaryApplicationService L173——与 51 D-2 死枚举相反）；⑥ matchedFields 产出在位（computeMatchedFields L445-464，keyword 驱动 name/displayName/description/taskCodes 四源，单测 AssetApplicationServiceTest L186 断言）；⑦ AssetCatalogController L58 javadoc 自声明与 REQ-DST-TAX-001 对齐、/datasets 15 参含四 List；⑧ V25 L26/L38 JSONB GIN 索引与 V22 索引对检索面有承载设计。

**偏差与 unproven 登记**：① 全部结论静态取证 + git/门禁实测；**「facet SQL 在 HEAD 上从未成功执行」不可证**——只能证「合并（2026-09-22）后仓内无任何 facet 真库运行记录（build-logs 零命中、surefire 报告文件实测缺失、E4 在档产物内容属合并前底座侧）」；② T-1 语法错误定性基于 PostgreSQL JOIN 先于 WHERE 的硬规则（`jsonb_array_elements_text` 作表函数须入 FROM），未起真库复现（本机无栈，unproven 项）；③ 98d53de/8ed02ef/f974ad4/c633ebc 四提交的文件版本比对为本轮 git show 实测，`git merge-base --is-ancestor` 三支均实测（98d53de≤HEAD、8ed02ef≤HEAD、98d53de≤f974ad4、8ed02ef≤e9d65b7）；④ E5 面（条款 L70）只钉规模与连坐，未对拍 ㊻ REQ-PERF-001 节（㊻ 域另册）；⑤ 前端筛选器交互完整面（facet 侧栏渲染、四 codes 筛选控件）归 ㊵ W-3 划界不重开，仅取 L114-116 调用点与 AssetSettingsPage 提交链两个增量证据；⑥ 涉规范/卡/EVD/契约/产品码/迁移六类禁改面**只登记不改动**；⑦ **落盘后现势复测（收尾链）**：发现工作树存在**另一批未提交变更**（Preview 域，文件 mtime 2026-10-12 11:06，早于本节 17:45 落盘）——`dataset-experience.md` +96（REQ-DST-FAV-001 草案插于 L182 后）、`aihub-v1.yaml` +98、`dataset-agent-exit-catalog.md` +12（L39 后）、`error-catalog.md` +47——本节对 dataset-experience.md 的条款行号已按现势 Read 全部重测修正（分类表 L32-41、E5 L70 等）；契约 L1836-1874/L4246-4272/L4619 与 exit-catalog L20-23、entity-invariants L180 现势复核成立。该批变更含新迁移 `V44__preview_split_stats.sql`、新卡 TASK-P0BR-077…081 与 EVD-077/078，**非本会话产物**，其合规性未审、只登记；历史章节抽测：㉑ 对 `error-catalog.md` L254/L258-262 的引用现势仍成立（该批 +47 未撼动该区间锚点）；其余历史节未逐节重测——行号锚点对未提交外部变更的脆弱性应并入统一证据门禁整改批（承 51⑥/52⑩）一并裁决。

**整改待批（须人类先出/更新 `READY` + authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① **T-1 facet SQL 修复为最高优先**——四 codes 维度改 `CROSS JOIN LATERAL` 入 FROM 段（或拆独立 UNION 支带自身 FROM/WHERE），并将 AssetFacetConsistencyIT 与 AssetSearchPerformanceTest 的真库用例纳入 CI（Docker 可用 runner 或对 disabledWithoutDocker 跳过显式告警），防「修好又静默回归」；② T-2 sort 五白名单契约+实现+非法值 `COMMON_INVALID_ARGUMENT` fail closed 三面同建（AC-001/004 载体）；③ T-3 sizeBucketCode 服务端派生（分桶规则入 Service、写路径拒收客户端值、存量回填迁移）+ 补 size_bucket 字典种子迁移（V14 注释引用悬空一并闭合）；④ T-4 validateGovernanceFields 扩至四 codes+sizeBucket+sensitivity（复用 DictionaryApplicationService 校验口，未知码 DICTIONARY_VALUE_INVALID 拒绝）；⑤ T-5 McpToolCatalog asset_search 补分类维参数与 OR/AND Description（对齐契约 L1719-1737 语义，承接 ㉓）；⑥ T-6/T-9 EVD-P1010 作废重写：proven TAX-003 断言按 V15 实测内容降级、TAX-002 补列、commitSha 虚指第七次样本入档；⑦ T-7 autocomplete 端点立项或 AC-002 措辞修订裁决（二选一，不得维持无承载 AC）；⑧ T-8 E5 夹具扩至 10 万并与 ㊻ REQ-PERF-001 整改批合流；⑨ 多卡共担 AC-DST-TAX-001 的 P1-010/P2-011/P2-013/P0BR-049 四卡职责裁决与 E3/E4 口径对齐；⑩ **「合并回归孤儿证据」检测项**（EVD 记录的 commitSha/时间戳 ↔ 被测代码路径在 HEAD 的引入历史对撞）**并入统一证据门禁整改批：本 52 为第十三挂靠点**。⑩ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥/51⑥ 挂靠清单。

→ 本项与 ①—52 一并不在本轮 AI 自决实施范围内。

### 53 REQ-DST-DETAIL-001 数据集详情聚合与精确版本导航域（条款 ↔ 实现 ↔ 契约 ↔ 防枚举 ↔ 证据五层全对撞）

**立项查重**：`REQ-DST-DETAIL-001`、`TASK-P1-011`、`EVD-P1011`、`TASK-P1-013`、`EVD-P1013` 对本节前对 plan 全文 Grep 零独立成节（52 仅以「Preview/Detail 域另册」身份划界；51 D-11 E4 崩塌样本同域但异 REQ）。**承接划界**：51 节 D-11 的 preview 渲染路径（P0BR-068 限定「不采用 presigned URL」）本节直接继承不重复计权；52 节偏差⑦「落盘后现势复测发现 Preview 批未提交」声明即本节取证基准。

**取证基准声明**：DETAIL 域实现面处于未提交 Preview 批中间——`git status` 实测 `AssetDetailPage.tsx / PreviewPanel.tsx / PreviewController.java / PreviewApplicationService.java / PreviewQueryPort.java / PreviewQueryRepository.java / PreviewJobHandler.java / PreviewProperties.java / contracts/aihub-v1.yaml / dataset-experience.md` 均为 `M`；`TASK-P0BR-077/078/079/080/081` 与 `EVD-P0BR-077/078` 为 `??`（未跟踪）；`ArtifactContentPanel.tsx / ArtifactContentPanel.test.tsx` 为 `??`。**基准＝现势（含未提交）**；该批非本会话产物，合规性不审、只登记。

**权威链定装**：REQ-DST-DETAIL-001（dataset-experience.md L88-123）——条款 L92-97 页面组成六项（八 Tab）、L99-100 Placeholder 禁令、L104 Card sourceCommit、L105 latestPublished 服务端精确计算、L106 版本绑定、L107 URL+Query Key、L109 安全渲染、L110 防枚举语义、L111 内部 Endpoint/凭据/预签名不进入页面数据；L115-120 目标接口六条（L119 `…/versions/{version}/preview` 形状差异见下）。AC 权威＝dataset-agent-exit-catalog.md L29-32：AC-001 外壳 Tab 顺序+Empty E4、AC-002 版本切换+Query Key+latestPublished E4、AC-003 Files/Preview+防枚举 E4、AC-004 versions 路由+不泄露 endpoint/预签名 E4。载体 TASK-P1-011.md（L7 baseCommit `38fd44d2`——`git cat-file -t` 实测存在但≠当前 HEAD `a022796`，即门禁 baseCommit 漂移样本；卡面无 commitSha 字段；L100-103 行为切片表四连 PASS|E4）↔ EVD-P1011-001.yaml（L3 status PARTIAL、L52 evidenceLevel E3、L64 notProven 列四 AC、L91 accepted:false、L20/23/25 三项门禁 result:null 未跑、**L30 commitSha `7dc22d44`——`git cat-file -t` 实测 `could not get object info`，对象不存在＝虚指样本第八次**）；TASK-P1-013.md（L7 baseCommit `08c25af8`——cat-file 实测存在但≠当前 HEAD；L124-127 四连 PASS|E4）↔ EVD-P1013-001.yaml（status DRAFT、completionValidation FAIL、AC-001 proven E4、AC-004 notProven）。实现面 AssetDetailPage.tsx（779 行，M）、PreviewPanel.tsx（449 行，M）、VersionController.java、PreviewApplicationService.java、VersionApplicationService.java、AssetLineageQueryService.java、AssetApplicationService.java；契约面 aihub-v1.yaml（M）/versions 族/previews 族/download/dvc/credentials；测试面 AssetDetailPage.version.test.tsx（161 行）、e2e dataset.spec.ts（166 行）。

**差集矩阵**：

- **N-1 latestPublished 三层断层（本域头号发现）**：条款 L105「latestPublished 只能是服务端明确计算的精确 Version，不能由页面或 Agent 猜测」。后端服务层确有计算能力：VersionQueryService.findLatestPublishedByAssetId（L42-44 透传）→ MyBatisVersionRepository.findLatestPublishedByAssetId（L97）。**消费方**：AgentController L120/L134、McpToolCatalog L173/L202（Agent/MCP 面合规）。**Web 断层**：Grep `latest` 对 aihub-v1.yaml **全文零命中**——AssetView（契约 L4737）、VersionView（契约 L5260）、CursorPageVersionView（契约 L5303）均无 latestPublished 字段。前端 AssetDetailPage.tsx L191 于是自行 `filter(status==='PUBLISHED').sort(publishedAt desc)[0]` 计算——实现注释 L186-190 自述「回退到最近一个已发布版本…避免猜测」，把前端排序当合法回退。**根因**：规格↔契约断层——后端有能力但未接入 Web 契约，前端只能自行实现，条款字面违反成立，整改方向应在契约补字段而非仅改前端逻辑。

- **N-2 Version 面防枚举归属缺口**：AC-DST-DETAIL-003「私有资产不存在与无权访问采用相同防枚举语义」。VersionController.java L84-88：`getVersion(@PathVariable assetId, @PathVariable versionId) → versionService.getVersion(versionId)` —— **assetId 接收但不传递**。VersionApplicationService.java L105 `getVersion(String versionId)` 无 assetId 参数；L187 `listArtifacts(String versionId)` 同；L211-213 `loadVersion` 仅 `findByVersionId.orElseThrow(VERSION_NOT_FOUND)` 无归属校验。**对比 Preview 面 good case**：PreviewApplicationService L271 `requirePublishedArtifact(assetId, versionId, artifactId)` 校验 `v.assetId().equals(assetId)` + PUBLISHED + artifact 归属，违规统一抛 PREVIEW_NOT_FOUND（同 404 语义）——**Version 面同族缺口**。后果：持 asset:read 主体可用真实 versionId 跨资产读取任意版本详情/工件列表，违反 AC-003。VersionApplicationServiceTest L150-165 仅有 getVersionReturnsView/getVersionReturnsNotFound 两用例，**无跨资产归属用例**。

- **N-3 getPreview 资产可见性缺口（Preview 面第二层）**：PreviewApplicationService.java L137-139 `getPreview(assetId, versionId)` 仅 `requirePermission("asset:read") + previewQueryPort.findLatestPreview(assetId, versionId)`——**不验 asset 存在性与主体可见性**（无 loadAccessible/findByAssetId 调用）。与 N-2 同理，对不可见 asset 的 preview 投影记录若存在则直接返回，构成私有资产预览内容泄露（preview 投影包含样例数据行）。**对照组**：AssetApplicationService.getAsset（L157-165）使用 loadAccessible 统一 ASSET_NOT_FOUND——平台有此模式但 Preview 面未复用。

- **N-4 presignedUrl/凭据 Modal 两读法歧义（须人类裁决）**：条款 L111「Gitea/MinIO/DVC 内部 Endpoint、服务凭据和预签名查询串不进入页面数据」。**读法一（违规）**：AssetDetailPage.tsx L675-678 presignedUrl 明文可复制渲染、L733 dvcConfig.endpoint 明文、L761 accessKey 明文（secretKey 已遮 L763）——均属「页面数据」。**读法二（合法按需签发）**：三处 Modal 均由用户显式点击触发，对应契约合法端点（L3595 /versions/{versionId}/download、L3356 dvc/credentials），presignedUrl 字段在契约 DownloadTicket schema L5320 建模为合法响应类型（`type: [string,'null'], format: uri`），p2-version-transfer.md L150/L184/L248/L275 承认票据机制，TASK-P0BR-068 已限定禁令于 Preview 渲染路径。**登记为歧义，上报裁决**，不单方面定性。AssetDetailPage.version.test.tsx 与 dataset.spec.ts 均无对此断言的测试覆盖（AC-004 notProven 列表包含「不泄露内部 endpoint」）。

- **N-5 EVD-P1011 卡证直接矛盾**：EVD-P1011-001.yaml L3 `status: PARTIAL`、L52 `evidenceLevel: E3`、L64-68 notProven 列出全部四 AC（AC-DST-DETAIL-001/002/003/004）、L91 `accepted: false`——TASK-P1-011.md L100-104 行为切片表四行均写「Execute | PASS | E4」。同域 EVD-P1013-001 亦现内部漂移：L3 `status: DRAFT`、L21-22 completionValidation `FAIL`（`-CheckCompletion` 实测），而 L52-55 AC-001 段有 `evidenceLevel: E4` + `e4Detail: dataset.spec.ts 4/4 PASS`——DRAFT+FAIL 状态含部分 E4 记录但未达 PASS。TASK-P1-013 §7 全部步骤 `[ ]`（未完成），§10 完成报告全空——**该卡实施尚未开始，EVD DRAFT 与 status 自洽**，但 AC-001 E4 细节存在说明 e2e 有执行但未更新卡状态。

- **N-6 preview 路径形状差异**：条款 L119 写 `GET /assets/{assetId}/versions/{version}/preview`，实现/契约/前端均为 `GET /assets/{assetId}/previews?versionId=`（契约 L3228、PreviewPanel.tsx L163-174）。功能等价，路径形状偏离条款描述——版本通过 query 参数而非路径段传递。**登记为文档差异**，不影响安全或功能，但条款精确性受损。

- **N-7 测试头注 vs 用例意图漂移**：AssetDetailPage.version.test.tsx L3 头注「latestPublished 由服务端计算，前端禁止猜测 latest」，但三个核心用例（L92/L110/L125）测试的是**前端回退行为**（「不自动选中 DRAFT」「无已发布版本不选中」「显式 URL 参数优先」）——**未测试服务端 latestPublished 字段的消费**（因契约无此字段，测试无法覆盖）。头注与实现/测试之间存在意图断层，承 N-1 根因。

- **N-8 Lineage Tab 合规正样本**：AssetLineageQueryService.java L57-64 `queryLineage` 先 `requirePermission(ASSET_READ)`，再 `findByAssetId.filter(canAccess).orElseThrow(ASSET_NOT_FOUND)`——完整防枚举链（同 AssetApplicationService.getAsset 模式）。AC-003 防枚举语义在 Lineage 面满足，与 N-2/N-3 的 Version/Preview 缺口形成同域内对照差异。

**合规正向面**：① 八 Tab 外壳完整（AssetDetailPage.tsx L506-542 + routes.tsx L57-63 八入口）；② Query Key 全版本化（artifacts/preview/preview-artifact 均含 versionId，L197-201 实测）；③ URL 同步 tab+version（L171-181 searchParams replace 模式）；④ Placeholder 禁令合规（Files 未选版本→Empty L463-464、统计 UNKNOWN→Alert L101-110、旧投影图片→Empty+重生成引导 L237-241，零假数据）；⑤ PreviewPanel 统计全取服务端投影、前端不重算（PreviewPanel.tsx L98-161）、图片经服务端 blob 代理无预签名（L381-449 getBlob 路径）、双重 mediaType 白名单守卫（L65-66/L399）；⑥ getAsset 防枚举（AssetApplicationService L157-165 loadAccessible）；⑦ Lineage 防枚举（L57-64 见 N-8）；⑧ AssetAccessController 读 ASSET_READ/写 ASSET_MANAGE 分权（L66/80/107 实测）；⑨ e2e dataset.spec.ts 用例 3 验证「无已发布版本→选择器空占位，不自动选 DRAFT」G6 语义回退在位；⑩ Card sourceCommit 投影在位（AssetApplicationService L428-459）。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796 + 未提交 M/?? 批）；② N-1 根因定性「契约无 latestPublished 字段」**已含未提交态实测闭合**：`git diff --numstat` 实测 Preview 批对 aihub-v1.yaml 为 +97/−1，diff 内 `latest` 零命中、新增端点仅 `/assets/{assetId}/versions/{versionId}/artifacts/{artifactId}/content`，且现势全文 Grep（-i）仍 `No matches found` ⇒ 前端猜测非「过渡期遗留」，断层在该批后依旧存在；③ N-2/N-3 防枚举缺口的实际可利用性未做运行验证（无栈环境），仅静态推理成立；④ presignedUrl 两读法中「p2-version-transfer.md 承认票据机制」的引用基于文档语义理解，未经人类确认是否覆盖「页面展示」场景；⑤ EVD-P1013 AC-001 E4 的「dataset.spec.ts 4/4 PASS（2026-09-24）」运行记录未独立复现（无 Compose 环境）；⑥ 涉规范/卡/EVD/契约/产品码五类禁改面**只登记不改动**；⑦ dataset-experience.md L182 后新增 REQ-DST-FAV-001（L182+）、REQ-DST-SUB-001（L230+）两草案（M 态，本批外部变更）——非 DETAIL 域，不展开，仅登记存在，两草案 status 均为 PROPOSED，不构成本节取证权威。

**整改待批（须人类先出/更新 `READY` + authorized Task Card，AI 不自批不自改）**：① **N-1**：Web 契约补 latestPublished 字段（GET /assets/{assetId} 响应或 GET /assets/{assetId}/versions 响应顶层）+ 前端改为消费服务端值——**优先出契约 Task，再出前端 Task**（不可仅改前端逻辑绕过断层根因）；② N-2：VersionController getVersion/listArtifacts 补传 assetId、VersionApplicationService 加 `loadVersion(versionId).assetId().equals(assetId)` 归属校验、跨资产统一抛 VERSION_NOT_FOUND（承 Preview 面 requirePublishedArtifact 模式）；③ N-3：PreviewApplicationService.getPreview 加 `loadAccessibleAsset(assetId)` 前置（复用 AssetApplicationService 已有 loadAccessible 模式），不可见/不存在统一 PREVIEW_NOT_FOUND；④ N-4：人类裁决 presignedUrl 两读法——若判违规则 Modal 改为「复制到剪贴板」而非渲染明文，若判合法则条款 L111 补「按需签发的下载票据 Modal 除外」措辞；⑤ N-5：EVD-P1011 重写（PASS/E4 vs PARTIAL/E3 互斥，须重跑验证决定真实状态）；⑥ N-6：条款 L119 或契约二选一统一路径形状（建议条款改为 `GET /assets/{assetId}/previews?versionId=` 对齐实际）；⑦ **合并回归孤儿证据检测项**（承 52⑩）：EVD-P1011 committed 批次与 Preview 未提交批的 AssetDetailPage 版本对撞应纳入统一门禁。⑧ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥/51⑥/52⑩ 挂靠清单。

→ 本项与 ①—53 一并不在本轮 AI 自决实施范围内。

### 54 REQ-DST-FAV-001 / REQ-DST-SUB-001 收藏与订阅双 PROPOSED 草案域（条款 ↔ 卡面 ↔ 实现三层对撞，含 toggle 断言证伪）

**立项查重**：`REQ-DST-FAV-001`、`REQ-DST-SUB-001` 对本节前未独立成节——53 节偏差⑦仅登记两草案存在（M 态、PROPOSED、不构成本节权威）；plan L79（历史 G4 条目）与 §4.5 L203-238（2026-10-10 规格增补+立卡轮）为**出身记录**而非差集成文。**承接划界**：§4.5 轮授权的草案文本与 TASK-P0BR-079/080 立卡本身不在本节复审范围（其 validate-task-card FAILED 6 项均为设计内门禁，含「Requirement is not READY」）；TASK-P0BR-081 经实读 L1-40 确认属下载域 presign 三层缺陷卡（REQ-DL-001/REQ-DST-CLI-001），非本域，仅登记 077—081 同批出身。

**取证基准声明**：基准＝现势（含未提交）。`dataset-experience.md` 为 `M`（+96 行含两草案节）；`TASK-P0BR-079.md`/`TASK-P0BR-080.md` 为 `??` 未跟踪；`git ls-files backend/.../db/migration` 实测 tracked 最大号 V43、`git cat-file -e a022796:...V44__preview_split_stats.sql` exit 128、`git status` 实测 `?? V44__preview_split_stats.sql`（Preview 批未跟踪产物）。证据卡面：Glob 实测 `docs/ai-spec/tasks/evidence/EVD-P0BR-*.yaml` 止于 078——**EVD-P0BR-079/080 不存在**（DRAFT 卡无证据，自洽）。

**权威链定装**：REQ-DST-FAV-001（dataset-experience.md L182-228，PROPOSED/MUST/P1/目标 E4）——私有书签不进排序/Facet/推荐、聚合计数默认不开放、不新增 asset:favorite 权限位（三项均留批准人裁定）；PUT/DELETE `/assets/{assetId}/favorite` 幂等 204、GET `/favorites` 静默过滤游标分页；新表 asset_favorite（V45+）、物理回收级联、审计 ASSET_FAVORITE_ADDED/REMOVED；契约/页面节 L226-228 明示「PROPOSED…不得据此开工」。REQ-DST-SUB-001（L230-276，PROPOSED）——订阅提升为独立资源 `PUT/DELETE/GET /assets/{assetId}/subscription` + `GET /subscriptions`（PAGE-COM-004）；讨论域布尔端点保留兼容别名委托同一应用服务、禁止两套状态；复用 asset_subscription 不加列；L262-263「通知投递按实时授权在收件人解析处拒绝」。AC＝dataset-agent-exit-catalog.md L43-49 七条在档（FAV-001..004、SUB-001..003，E4 为主）；PAGE＝page-catalog.md L15（PAGE-COM-004）/L50（PAGE-AST-007），均「草案，待人类评审」注记、单元格纯净。卡＝TASK-P0BR-079.md（236 行）/TASK-P0BR-080.md，DEC-009 三重声明齐备（079 L4 DRAFT、L5 authorized:false、L7 baseCommit a022796、L8 stageGatePassed:false、L9-18 生效前置三条）。实现面：DiscussionController.java、DiscussionApplicationService.java、JdbcDiscussionSubscriptionRepository.java、NotificationRecipientResolver.java、frontend DiscussionPanel.tsx/api.ts/i18n；契约面 aihub-v1.yaml L2595-2634。

**差集矩阵**：

- **S-1 条款/卡面「退订非幂等（toggle 语义）」断言与代码现实漂移（本域头号发现，证伪）**：条款 L253 与 080 卡 L16 均写「退订非幂等（toggle 语义）」。实测现码三层均**已幂等收敛**：DiscussionController L39 `if (request.subscribed())` 为布尔收敛分支（请求 DTO 携带 `subscribed` 显式目标态，非无参翻转）；JdbcDiscussionSubscriptionRepository L47 subscribe = `INSERT … ON CONFLICT (asset_id, principal_id) DO NOTHING`、L54 unsubscribe = `DELETE`（影响 0 行亦 void→204 成功）。「toggle」仅存在于**前端按钮交互层**（DiscussionPanel.tsx L354 `subscriptionMut.mutate(!subscribed)` 翻转后显式传参，api.ts L237-239），语义等价收敛写入、不构成缺陷。080 卡自身 L53-54 亦承认收敛设计（PUT DO NOTHING / DELETE 0 行 204），与其 L16 差距断言形成**卡内口径张力**。**修正后的真实剩余缺口**仅三条：独立资源端点族缺失、`GET /subscriptions` 个人列表缺失、PAGE-COM-004 管理入口缺失。此断言失真影响批准人对 SUB 条款「缺口正当性」的评审权重，须人类在规格批准轮修正条款 L253 与卡面 L12/L16 措辞（「既有讨论域 POST 端点」不宜称 toggle）。

- **S-2 fan-out 收件人实时授权过滤缺失（坐实 080 卡自述缺口）**：条款 L263 要求版本发布通知投递「按实时授权在收件人解析处拒绝」。实测 NotificationRecipientResolver.java L80-82 `resolveSubscribers` 直接返回 `subscriptionRepository.findSubscribers(assetId)` **无任何授权过滤**——对照同文件 L84 起 `resolvePrincipalsWithPermission` 有完整 RBAC/ACL 谓词模式可复用。后果：权限撤销后订阅者仍会收到 VERSION_PUBLISHED 通知（AC-DST-SUB-002 E4 语义未实现）。080 卡 L46-49 自述「现状缺口，本卡补齐 AC-DST-SUB-002 语义」——**卡面申报与实测吻合**（诚实申报，正向面⑤），非卡面缺陷。

- **S-3 FAV 全链零实现 + V45 号位链条**：后端 `asset_favorite` Grep 零命中、前端 `favorite|bookmark` 零命中、HEAD 无 V44/V45（tracked 最大 V43）。V45 号位**当前干净**；链条登记：plan L79 历史残留旧号「V44__asset_favorite.sql」（不改，历史条目）、§4.5 L200-201 记载 V44 因 076 让位于 Preview、现势 `?? V44__preview_split_stats.sql` 为 Preview 批未跟踪产物（若该批先提交则 V44 固化占用）；079 卡 L130-131 已含「若届时 V45 被占用则顺延并在批准回写时更新」预案（L68/L142/L182/L195 均落 V45）。**串行约束**：FAV 实施必须晚于 Preview 批落定 + 079 批准回写。

- **S-4 草案出身澄清（非外部批）**：FAV/SUB 草案与 079/080 卡系 plan §4.5 L203-238 记载的 2026-10-10 历史会话轮产物（G4/G3 清单 #5 授权的规格增补+立卡；validate 当时 751 stable IDs vs 现势 752＝Preview 批再 +1，漂移登记）。两卡 DRAFT 轮 validate-task-card FAILED 6 项均为设计内门禁。本节不重复审查该轮授权链。

- **S-5 GET /subscription 契约响应形状 ↔ 实现漂移（新发现）**：契约 aihub-v1.yaml L2618-2621 声明 data 为**对象** `{subscribed: boolean}`；实测 DiscussionController L48 `ApiResponse<Boolean>` 返回**裸布尔**；前端 api.ts L222-226 注释自述「经 apiClient 解包后为裸布尔值」并按 `get<boolean>` 消费——前后端互洽但与契约形状不符。080 卡 L131 称既有端点「契约不变」——若契约即此漂移态，「不变」将固化错误形状。契约修正须人类裁决（改契约 data:boolean 或后端改对象，前者成本低且前端已按裸布尔实现）。

**合规正向面**：① 订阅三点 fail-closed：DiscussionApplicationService L148/155/162 subscribe/unsubscribe/isSubscribed 均先 `requirePermission(ASSET_READ)`（Grep 全文件 13 处权限点普查吻合，讨论写入走 ASSET_DISCUSS、治理走 ASSET_MODERATE 分权）；② 契约既有双操作在档：GET L2601/POST L2623 均带 `x-required-permissions: [asset:read]` + `x-implementation-status: implemented-unverified` 诚实标注（L2605/2627）；③ 前端订阅 UI 完整闭环：DiscussionPanel.tsx L322-337 query+mutation+失效重取、L351-357 按钮、i18n en/zh L425 双语文案、DiscussionPanel.test.tsx L88-107 订阅/退订双向用例；④ AC 七条（L43-49）与 PAGE 两条（L15/L50）接线完整、ID 单元格无注记污染；⑤ 079/080 卡 DEC-009 三重声明齐备、079 顺延预案在档、080 fan-out 缺口诚实自述（见 S-2）；⑥ FAV 条款语义划界（L200-205 书签不入排序/Facet/推荐）与「明确非目标」社区排行的正向解释在文档层面自洽；⑦ 订阅单一存储源 asset_subscription（V15 建表，`ux_subscription` 唯一键被 AC-SUB-003 L49 引用）复用不加列，与「禁止两套状态」条款一致。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796 + M/?? 批），无运行验证；② S-1 幂等结论基于 SQL 语句与 void→204 语义推理，并发重复写入的 E3 实证（AC-FAV-003/AC-SUB-003 级别）不存在；③ 两草案与两卡均为未提交态（M/??），其权威地位待批准轮确立——本节仅以「现势文本」对撞「现势代码」，不预设批准结论；④ plan L79 旧号、§4.5 751→752 ID 漂移均为历史条目登记不改写；⑤ S-5 前端 apiClient 解包行为未运行验证（依据其注释与类型签名的静态一致）；⑥ FAV/SUB 全域 E4 无证据（EVD 卡不存在），AGENTS.md「P1–P5 IMPLEMENTED_UNVERIFIED」口径在本域表现为「规范未批准 + 实现未开工/半开工」双重未验证；⑦ 涉条款/卡/契约/产品码四类禁改面**只登记不改动**。

**整改待批（须人类先批准/更新 Task Card，AI 不自批不自改）**：① 批准人三项裁定（§4.5 已列）：FAV 权限位、聚合计数开放、SUB 最近通知时间字段；② S-1：修正条款 L253 与 080 卡 L12/L16 的「toggle 非幂等」失真断言（真实缺口＝独立资源面+列表+UI 三条）；③ S-5：契约 data 形状与实现二选一统一（建议契约改 boolean 对齐现状，成本最低）；④ 079/080 批准→READY→串行实施（079 先、080 后，079 迁移号位以批准回写时现势最大号+1 为准）；⑤ 080 实施时 AC-SUB-002 收件人过滤须按 S-2 定位的 `resolveSubscribers` 单点补齐并复用 L84 `resolvePrincipalsWithPermission` 谓词模式；⑥ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥/51⑥/52⑩/53⑧ 挂靠清单。

→ 本项与 ①—54 一并不在本轮 AI 自决实施范围内。

### 55 REQ-ORG-001 / REQ-ORG-002 组织项目团队域（条款 ↔ 实现 ↔ 契约 ↔ 证据四层对撞，启停域整体缺失实证）

**立项查重**：`REQ-ORG-001`/`REQ-ORG-002` 对本节前未独立成节；plan 全文命中 6 处均为附带引用——L420（Controller 端点计数）、L713（PRINCIPAL_NOT_FOUND 错误码普查）、L1197（probe-perm4 权限探针伪影收回）、L1484（幂等 null 指纹普查）、L1892（幂等 $ref 对齐 17 处）、L2267（X-7 枚举测试面），无组织域差集成文。**承接划界**：54 节为 FAV/SUB 草案域；本节组织域为已批准 REQ（READY）+ 已立卡（TASK-P0BR-016）+ 已有证据卡（EVD-P0BR016-001）的正统 P0-B 域。

**取证基准声明**：基准＝现势（HEAD a022796 + 工作区 M/?? 批）；TASK-P0BR-016 卡面 baseCommit 0b152f3（L7）与 HEAD 存在批次漂移，证据卡 commitSha 7dc22d44（L26）为第三值——三值不同源本身即 T-7 漂移佐证之一。

**权威链定装**：条款＝p0b-identity-authorization.md REQ-ORG-001 L273-306（九行为：单租户、平台管理员建/停 Organization L285、组织管理员建/停 Project+成员 L286、防枚举 L290、停用上级断新写入 L291、移除成员实时撤销 L292、Gitea Organization 仅投影 L293；错误与审计 L295-300 定稿启停事件名）+ REQ-ORG-002 L308-332（Team 八条：teamId/code/name/status/rowVersion L320、Member 须先是 Org Member L322、Team 可作 Role Binding/ACL/Asset Owner 主体 L323、至少一个 Team Owner L324、移除/停用不得留无 Owner 资产 L325、禁字符串 team name 作 Owner L326；**O02 无独立「权限、错误与审计」小节，仅追踪 L328-332**）；INV＝entity-invariants.md L69-74（INV-ORG-001..006）/L80-86（INV-TEAM-001..007）；AC＝p0b-exit-catalog.md L64-72 七条（AUTH-001/002/004/008/009 E4 主、003 E3/E4、005 E4）；PAGE＝page-catalog.md L23/24/25/48；卡＝TASK-P0BR-016.md（status READY L4、authorized true、requirements ORG-001/ORG-002/**AUTH-003** L13-15、allowedPaths 含 deploy/compose L46-48、forbiddenPaths V1/V2/V5 L51-53、requiredEvidenceLevel E4 L54、approvedBy null L62-63、五场景 E4 计划 L26-30）；证据＝EVD-P0BR016-001.yaml（注意卡 L57 文件名**无第二连字符**）。REQ-AUTH-003（L411-443）条款体 status **OPEN**（L414，blockedBy TASK-GOV-002）——卡将其列入 requirements 而条款未 READY，卡面自证矛盾。

**差集矩阵**：
- **T-1 启停域整体缺失（头号）**：条款 L285/286/291 要求 Organization/Project 停用+停用级联断新写入，L299-300 定稿 `ORGANIZATION_ENABLED/DISABLED`、`PROJECT_ENABLED/DISABLED` 事件。实测 organization 包 Grep `ORGANIZATION_ENABLED|ORGANIZATION_DISABLED|PROJECT_ENABLED|PROJECT_DISABLED|updateStatus` **零命中**；OrganizationApplicationService（create L88-110/addMember L126-145/removeMember L151-160）与 ProjectApplicationService（create L79-105）**均无启停方法**；JdbcOrganizationRepository 仅 insert（L81-98）、JdbcProjectRepository 同——DB 层 V5 status 列+CHECK(ACTIVE/DISABLED)（L24/L53）齐备但应用层永置 ACTIVE，**启停是纯死列**。INV-ORG-003/004 不满足；**AC-P0B-AUTH-008（卡场景 L29「Disable org/project/role removes access in real-time」E4）不可能通过**——禁用路径在应用层根本不存在。
- **T-2 Team 成员前置缺失**：条款 L322/INV-TEAM-003「Team Member 必须先是 Organization Member」——TeamApplicationService.addMember（L127-146）仅校验 principal 存在，无组织成员检查。
- **T-3 最后 Owner 保护缺失**：条款 L324-325/INV-TEAM-005「移除成员/停用 Team 不得留下无 Owner 资产」——removeMember（L152-162）仅存在性检查，零保护；卡场景 L30「asset ownership transferred」（AC-P0B-AUTH-009）责任转移**零实现痕迹**。
- **T-4 Team 无 code 属性**：条款 L320 声明 teamId/code/name/status/rowVersion、INV-TEAM-002「code 组织内唯一」——V19 无 code 列，唯一键为 `UNIQUE(organization_id, name)`（L19），服务层 existsByName（TeamApplicationService L81 → JdbcTeamRepository L63）按 name 判重。
- **T-5 Team 不能作 Role Binding/ACL 主体**：条款 L323/INV-TEAM-004——RoleBinding record（L21-24）与 ResourceAcl（L26-28）均仅 principalId 无主体类型维度；V4 iam_role_binding scope_type CHECK ('PLATFORM','ORGANIZATION','PROJECT')（L88）**无 TEAM**。**反向已实现面**：Asset Owner 主体已闭合（DEC-011）——Asset.java L140 `requireNonNull(ownerTeamId)`、AssetApplicationService L591-599 存在+ACTIVE 校验+L578-579 弃用 free-form owner、AssetAccessPolicy L111-114 团队成员判定、V32 L26 owner_team_id SET NOT NULL。
- **T-6 受控 Event Catalog 无实体**：条款 L300「成员事件一并进入受控 Event Catalog」——docs/ai-spec 全域 Grep 无 event-catalog 文件（Glob 零、03-contracts 目录不存在）；contracts/events/events-v1.yaml 为业务事件信封（ASSET_*/DISCUSSION_*）非审计事件目录；V10 audit_log.event_type（L13）无 CHECK 约束。契约侧 x-audit-event 操作标注八处齐备（ORGANIZATION_CREATED L534、MEMBER_ADDED L585、MEMBER_REMOVED L615、PROJECT_CREATED L653、TEAM_CREATED L704、TEAM_UPDATED L755、TEAM_MEMBER_ADDED L807、TEAM_MEMBER_REMOVED L836）——**启停四事件名在契约零出现**（与 T-1 一致）。
- **T-7 证据卡漂移（E4 未达成自证）**：EVD-P0BR016-001.yaml——`evidenceLevel: E2`（L43）vs 卡 E4（L54）；`requirements: [REQ-AUTH-001, REQ-AUTH-002]`（L7）vs 卡 [ORG-001, ORG-002, AUTH-003]（L13-15）**要求面完全错位**；`scenarios: []`（L8）空 vs 卡五场景；`status: PASS`（L3）但 `accepted: false`（L74）；notProven 自认「E4 Compose 未执行、无 Docker」（L50/L68）。卡 approvedBy null（L62-63）——**该卡从未被验收，PASS 系自记**。
- **T-8 E4 编排面缺失**：卡 allowedPaths L46-48 含 deploy/compose，但 deploy/compose Grep `organization` **零命中**——无组织域 compose 场景，E4 无处落地。
- **T-9 Q-103 已裁决未回写（文档滞后）**：open-questions.md L25 `ACCEPTED → DEC-011`；page-catalog.md L24 仍「待确认+受 Q-103 阻塞」、entity-invariants.md L78 同型残留——与 54 节 S-4 同型漂移。
- **T-10 Gitea 投影死分支＋悬空表名（新发现）**：AuthorizationProjectionHandler L67 `ORGANIZATION_MEMBERSHIP_CHANGED` 分支 → L108-117 `projectOrgMembershipChange` 查询 `org_membership` 表（L111），而全仓 Grep `org_membership` **仅此一处命中**、无任何迁移建表（V5 实为 organization_member）——一旦被投递即 500；且全仓 Grep `ORGANIZATION_MEMBERSHIP_CHANGED` 仅 handler 消费侧命中、**无生产者**（OrganizationApplicationService L142/L158 审计仅写 AuditService，不投 AUTH_PROJECTION job）——该分支为死代码且与「Gitea Organization 字段是投影/映射，不作为业务授权」（条款 L293）的意图不符。
- **T-11 OrganizationController.removeMember 幂等 execute 传 null**：L148（同型 TeamController L192）——承 L1484 幂等普查在册，键计算在位（L145-147）但结果槽 null，重放时响应体形状依赖 execute 实现；组织域写操作幂等**键面合规、值面存疑**（附带登记，不重复定性）。

**合规正向面**：① 防枚举三处语义正确（OrganizationApplicationService.requireOrganizationExists L162-167；ProjectApplicationService.requireOrganizationAccessible L110-124 注释「与'组织不存在'同语义，避免通过 403/404 差异枚举私有组织资源」——满足条款 L290/INV-ORG-005）；② 成员隔离 SQL 下推（JdbcOrganizationRepository L55-62 JOIN organization_member 过滤，注释「杜绝先全量再 Java 过滤」——卡 DB_FILTER 维度相符）；③ 权限码四件套齐备（Permissions L19/21/23/25 + V4 预置一致注释 + 契约 x-required-permissions 互恰）；④ 幂等键全写操作在位（三 Controller IdempotencySupport.buildKey，L1892 对齐 17 处含 OrganizationController L79/L115、ProjectController L75、TeamController 三处）；⑤ Team 乐观锁（JdbcTeamRepository.update L92-110 row_version 失配抛 CONCURRENT_MODIFICATION，契约 L753 在档）；⑥ 审计桥统一（OrganizationAuditAdapter 转发权威 AuditService，零本地实现）；⑦ 成员端口实时接通（AuthorizationService L52-53/215/223 经 ObjectProvider 消费 OrganizationMembershipPort/TeamMembershipPort；PrincipalContext L20/32 含 organizationId——卡 AUTHN crossCutting L32 相符）；⑧ 前端全链闭环（routes.tsx L75-78 三页 `guarded(['project:view'])`、navigation.ts L36、admin/api.ts L126-164/L346-395、OrganizationsPage/TeamsPage/ProjectsPage + 三测试文件）；⑨ 契约 8 端点族全在档（L507/556/607/624/675/724/776/828，均 implemented-unverified 诚实标注 + x-error-codes 21 处）；⑩ V5/V19/V32 迁移与卡 forbiddenPaths 纪律一致（V5 未被改动）；⑪ OrganizationIT 双用例在档（跨组织隔离 L81-82、AccessScope 成员组织集 L130-131），但 `@Testcontainers(disabledWithoutDocker=true)`（L46）——无 Docker 即跳过，与 E2 定性自洽；⑫ Team 是唯一具备启停能力的组织域实体（updateTeam L99-111 可改 status + TEAM_UPDATED 审计）——反证 T-1 缺的是 Org/Project 两侧。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796），无运行验证；② T-1 的「停用级联断新写入」零实现系 Grep 负证据 + 三 Service/三 Repository 全读双重确证，但**不排除**其他模块存在未点名谓词的局部 status 判断（已核 AccessScope 主链路无组织 status 过滤，Asset ownerTeam ACTIVE 校验见 T-5 正向面）；③ T-7「requirements 错位」定性基于卡↔证据两侧文本对撞，证据卡实际测的是什么（AUTH-001/002 域）与组织域实现的相关性未复核；④ 53/54 节既有锚点引用（PRINCIPAL_NOT_FOUND L713、幂等 L1484/L1892）为历史轮现势，本节仅复用不重测其全部行号；⑤ OrganizationIT 未运行（无 Docker），其断言形状为静态阅读所得；⑥ 涉条款/INV/AC/PAGE/卡/EVD/契约/迁移/产品码九类禁改面**只登记不改动**。

**整改待批（须人类先批准/更新 Task Card 与证据链，AI 不自批不自改）**：① **T-1 为本节头号缺口**：TASK-P0BR-016 处于 READY+authorized 状态却交付 E2 证据——须人类裁决重开实施（启停两端点+级联谓词+四事件+AC-008 compose 场景）或将卡降级/重立；② T-2/T-3/T-4/T-5 四项 INV-TEAM 缺口建议并入同卡整改（Team code 列须 V34+ 新迁移，禁改 V19）；③ T-6：受控 Event Catalog 落地为文档域缺口——建 03-contracts 或并入现有契约目录的审计事件枚举，须人类定载体；④ T-7：EVD-P0BR016-001 重写（requirements 对齐卡、scenarios 填充、E2/E4 与 status/accepted 互斥消解）；⑤ T-8：deploy/compose 组织域场景为 AC-001/002/004/008/009 全部 E4 前置；⑥ T-9：page-catalog L24 与 entity-invariants L78 回写 DEC-011 裁决（文档同步，低风险）；⑦ T-10：AuthorizationProjectionHandler L111 表名改 organization_member **或删除死分支**，并裁决 ORGANIZATION_MEMBERSHIP_CHANGED 生产者是否本应存在（涉及成员变更实时投影语义，须与 L292 条款对齐设计）；⑧ REQ-AUTH-003 仍 OPEN（条款 L414）——卡 requirements 含之（L15）本身违「引用仅 READY 需求」门禁，validate-task-card 若未拦截则属校验器缺口，一并登记；⑨ 承接 ㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥/51⑥/52⑩/53⑧/54⑥ 挂靠清单。

→ 本项与 ①—55 一并不在本轮 AI 自决实施范围内。

### 56 REQ-AUTH-003 Role、Binding 与 ACL 管理域（条款 ↔ 实现 ↔ 契约 ↔ 迁移 ↔ AC/PAGE ↔ 证据 ↔ 测试 ↔ 前端八层对撞，AC-005 语义冲突实证）

**立项查重**：`REQ-AUTH-003` 对本节前未独立成节；plan 全文命中 3 处均为附带引用——L817（TASK-P0BR-003/AUD-016 权限目录集合级比对定性）、L2550/L2569（55 节卡面引用与整改⑧登记），无 AUTH-003 行为面差集成文。**承接划界**：㉕（L1145）为权限目录五方差集（= 003/004 卡主目标 AUD-016/017，本节不重复其集合比对，仅引用其结论）；㉚ 为认证域 JWT 生命周期；55 节 T-1 为组织/项目启停死列、T-5 登记 Binding 无 TEAM、T-10 登记 ORGANIZATION_MEMBERSHIP_CHANGED 无生产者——本节角色启停为同型死列**第三例**、TEAM/事件缺口为**授权面扩展登记**，均不重复定性。

**取证基准声明**：基准＝现势（HEAD a022796 复测在位）；TASK-P0BR-003/004/017 卡面 baseCommit 0b152f3 与四证据卡 commitSha 7dc22d44 与 HEAD 三值不同源——承 55 T-7 同型漂移模板。

**权威链定装**：条款＝p0b-identity-authorization.md REQ-AUTH-003 L411-443（status **OPEN** L414、blockedBy [TASK-GOV-002] L417、E4 L418；九行为 L423-431：目录仅 migration 扩展、内置 Role 不可改删、**委派集合约束 L425**、**Binding 输入受控 Principal/Team L426**、ACL 受控 L427、重复幂等/稳定冲突 L428、ROLE_IN_USE L429、**禁止自提权/跨组织绑定/给停用主体新增授权 L430**、**实时生效+缓存失效 L431**；错误码族 L435-436）；AC＝p0b-exit-catalog.md L67-71（AUTH-004 成功路径 E4、**005「两种情况都拒绝；只有所有条件满足才通过」E4**、006 拒自提权+内置不可改删 E4、007 稳定冲突 E3/E4、008 禁组织/项目/**角色**/移成员实时失效 E4）；PAGE＝page-catalog.md L26-29（ADM-006 启停列+「不可授予超出委派范围」、ADM-007「使用角色数」、ADM-008「Principal/Team…所有引用用选择器」、ADM-009「资源类型受控+最终授权解释」）；卡＝TASK-P0BR-003（权限目录消 AUD-016）/004（DEC-018 READER 拆分消 AUD-017）/017（鉴权判定+管理域行为，四场景 E4 计划 L25-28）/042/016 五卡全 READY+authorized true+approvedBy null；证据＝EVD-P0BR003/004/017/042-001 四卡；实现＝authorization 包 38 文件（三 Service+三 Controller+两 Jdbc 仓储+AuthorizationService+EffectiveScopeResolver 全读）；DB＝V4 L38-46（iam_role status CHECK L46、row_version L44、scope_type 无 TEAM L45）/L88（binding scope_type 无 TEAM）/L156-205（31 权限+三角色种子）、V23 全文；契约＝aihub-v1.yaml 七路径 L846/895/916/962/1011/1027/1076 + schema L4177-4229。

**幽灵阻塞实证（本节定级前置）**：TASK-GOV-002 全仓 Grep 仅 5 命中，唯一定装处＝work-breakdown.md L48「Task 候选」表一行；`Glob TASK-GOV*` 于 tasks 目录仅 GOV-007 存在——**blockedBy 指向一个从未立卡的候选编号，REQ-AUTH-003 的 OPEN 状态没有可执行的解除路径**，而五张以其为 requirements 的卡全部 READY+authorized，门禁「引用仅 READY 需求」被系统性违反且 validate-task-card 未拦截（55 整改⑧在本域升级为**五卡+幽灵目标**的复合缺陷）。

**差集矩阵**：
- **T-1 AC-P0B-AUTH-005 语义冲突（实现级头号，AND vs OR）**：AC L68 要求「Scope 包含动作但 RBAC/ACL 不允许，及反向组合→两种情况都拒绝；只有所有条件满足才通过」＝AND 交集语义；AuthorizationService.isPermitted L119-121 `if (context.scopes() != null && context.scopes().contains(permissionCode)) return true;` 先命中即放＝**OR 并集语义**；EffectiveScopeResolver L40-49 登录/刷新时把 RBAC 权限编码并入 JWT scope（AuthenticationApplicationService L363/L386 接入），使 OR 路径进一步吞并 RBAC 判定面；类 javadoc L34-36 自辩「P0-B 合并规则…满足现有基于 Scope 的链路与测试」——与验收目录直接矛盾。测试面**固化错误语义**：AuthorizationServiceTest L73 `isPermittedAllowsWhenJwtScopeContainsPermission` 正面断言 scope-only 放行；AuthorizationLifecycleIT auth005（L155，注释 L152「两者都不满足时拒绝」）**名义 BothRequired、实际仅测「两者皆无才拒」**——L160 scope 单独命中即 assertTrue，与 017 卡场景 L26「both rejected」措辞相撞但未测反向组合（RBAC 有+scope 无）。017 卡四 E4 场景（005/006/007）按现实现**不可能通过**。
- **T-2 委派集合约束零实现**：条款 L425+PAGE-ADM-006 L26「不可授予超出委派范围」——全后端 Grep `delegat` 仅 ValidationJobHandler L117-118 license 无关命中；RoleManagementApplicationService createRole L85-86/updateRole 均不比对操作者可授予权限集，任意 AUTHORIZATION_MANAGE 持有者可造含全量 35 权限的自定义角色自绑＝水平提权通道。
- **T-3 禁止自提权零实现**：条款 L430+AC-006 L69——RoleBindingController.createBinding 仅 requirePermission(AUTHORIZATION_MANAGE)，无「给自己/向自己绑定更高角色/操作内置角色主体」防护；测试目录 Grep `escalat|self-|自提权` **零命中**；而 EVD-P0BR017-001 proven 断言（L47-48）明写「Self-elevation denied」——**证据断言超出代码与测试实存**（虚证，与 T-12 并案）。
- **T-4 跨组织绑定/停用主体授权防护零实现**：条款 L430 后两项——RoleBindingApplicationService.createBinding principalId 仅非空检查（L72-74），无主体存在性、无操作者与 scopeId 组织归属比对、无主体/角色停用状态检查（ALREADY_EXISTS L98-101 为唯一冲突路径）；给已停用 principal、他人组织、DISABLED 角色新增绑定全部畅通。
- **T-5 Binding/ACL 输入不受控（选择器契约双侧失守）**：条款 L426/L427+PAGE-ADM-008 L28「所有引用用选择器」+ADM-009 L29「资源类型受控」——前端 RoleBindingsPage principalId 为自由文本 `<Input placeholder="prn_xxx">`（L164-165）非选择器；契约 CreateResourceAclRequest.resourceType 为自由 string 仅 example（L4210），服务层零枚举校验（ResourceAclApplicationService 仅查 PERMISSION_UNKNOWN L97-101）；CreateRoleBindingRequest principalId 仅 `pattern: '^prn_'`（L4186）。
- **T-6 TEAM 作用域契约/DB/实现三层同缺**：条款 L426「受控 Principal/Team」+PAGE-ADM-008——契约 ScopeType 枚举 L4177-4179 `[PLATFORM, ORGANIZATION, PROJECT, ASSET]` 无 TEAM、V4 L88 CHECK 无 TEAM、Grep 零命中——承 55 T-5 组织面登记，本节补**契约 schema 层**实证；Team 作授权主体整条行为（L426）不可执行。
- **T-7 角色启停写面缺失（55 T-1 死列模式第三例）**：条款 L424 域+AC-008 L71「禁用…**角色**…实时失效」——V4 L41/L46 status CHECK(ACTIVE/DISABLED)+L58 注释齐备、JdbcRoleBindingRepository.resolvePermissionCodes L88 join `r.status='ACTIVE'`＝**读面半实现**；但 createRole 硬编码 "ACTIVE"（L86）、无 updateStatus/toggle 方法、RoleController 无端点（GET/POST/PATCH/DELETE 四端点全读）、契约无端点、RoleView 无 status 字段（AuthorizationDtos L37-38）、前端 RolesPage 无启停控件（仅 disabled={isSystem} 灰钮 L151/156/159）——**DISABLED 成为不可达死值，AC-008 角色半不可执行**。
- **T-8 实时生效缺口：JWT scope 滞留快照+事件无生产者**：条款 L431「实时生效+缓存失效」——无授权缓存（Grep `Cacheable|caffeine` 仅 4 个非授权文件）故 DB 直查天然实时、「缓存失效」为伪需求；**但** JwtTokenService L102 `.claim(SCOPE_CLAIM, String.join(" ", scopes))` 签发定死+登录合入 RBAC（T-1），使**已签发 access token 在 TTL 窗口内旧权限滞留**且因 OR 优先命中而实际放行；ROLE_BINDING_CHANGED/ACL_CHANGED 全仓 Grep 仅 AuthorizationProjectionHandler L65-66 消费侧命中、**无生产者**——与 55 T-10 同根模板，授权变更→投影/失效链路只有半截。
- **T-9 幂等三态**：POST role/binding/acl 三处完整 `execute(key, sha256Digest(request), …)`（RoleController L90-94、RoleBindingController L80-82、ResourceAclController L80-82）——键值双面合规，**优于 55 域**；DELETE binding（RoleBindingController L102）/DELETE acl（ResourceAclController L103）`execute(key, null, …)` **null 结果槽**（承 L1484 普查同型，新增 2 处）；DELETE role（RoleController L122-127）**连键都不计算**（无 @RequestHeader、无 buildKey）；PATCH role 无幂等。
- **T-10 契约↔实现反向漂移**：deleteRoleBinding（契约 L1011-1025）与 deleteResourceAcl（L1076-1090）**未声明 Idempotency-Key 参数**，实现却接收 header 并走 idempotencyService——实现超出契约（与「契约先行」流程相反方向的漂移；㉞ 普查为正向缺参，此为反向超参）。
- **T-11 PAGE-ADM-007「使用角色数」缺**：契约 PermissionView（L4127 起）仅 (permissionCode, resource, action, i18nKey) 四字段、AuthorizationDtos.PermissionView L29-34 同形、PermissionQueryApplicationService（全读 33 行）仅 findAll 转发——计数字段与统计零实现。
- **T-12 证据面系统性漂移（四卡合案）**：EVD-P0BR003/004/017/042-001 全 `evidenceLevel: E2`（017 L43）vs 各卡 requiredEvidenceLevel E4；全 `accepted: false`+`recordedBy: AI`+`reviewedBy: null`；commitSha 7dc22d44 三值漂移；**EVD-017 requirements 仅 [REQ-AUTH-002]（L7）vs 卡三 REQ（L12-15）错位、`scenarios: []`（L8）vs 卡四 E4 场景**；EVD-042 requirements 含 AUTH-003 但 proven 仅 AC-P0B-UI-004 ControlledSelect（行为无交集）；EVD-003/004 卡 scenarioEvidencePlan []；017/042 的 PASS 均系 `./mvnw -o verify` E2 自记且 proven 含未实证断言（T-3 虚证）——**AUTH-003 的 E4 证据在四卡中零存在**。
- **T-13 E4 compose 零落地**：deploy/ 全树 Grep `role-binding|resource-acl|/system/roles` **零命中**——017 卡 allowedPaths L48 含 deploy/compose 但无场景文件，AC-004/005/006/008 E4 无处执行（承 55 T-8 同型）。
- **T-14 AC-008 测试名实错位（附带登记）**：AuthorizationLifecycleIT auth008（L206 注释「AC-P0B-AUTH-008: 禁用角色绑定后…」）实际仅测**删除绑定**即时失效（L209-226），既未「禁用角色」亦未「禁用组织/项目」——编号占用与 AC 语义覆盖不符；删除绑定实时失效（服务层内）为正向实证。

**合规正向面**：① 内置角色双保护（updateRole L99-101+deleteRole L129-131 ROLE_BUILTIN_IMMUTABLE；createRole 硬编码 builtin=false L85 不可伪造；auth006 L173-178+ControllerTest deleteBuiltinRoleReturnsConflict L146 双测在档）；② ROLE_IN_USE（L133-135→countBindingsByRoleId JdbcRoleRepository L124；auth007 L195-197）；③ 乐观锁双层（服务 L104-108+仓储 row_version JdbcRoleRepository L94-107）；④ PERMISSION_UNKNOWN 双检（role L145+acl L97）；⑤ 重复冲突双层（服务 exists 预检+DB 唯一索引 ux_iam_role_binding L92-93 COALESCE 归一/ux_iam_resource_acl L117）；⑥ 错误码族九枚全齐（ErrorCode L58-82：ROLE_NOT_FOUND/ALREADY_EXISTS/BUILTIN_IMMUTABLE/IN_USE、PERMISSION_UNKNOWN、ROLE_BINDING_NOT_FOUND/ALREADY_EXISTS、RESOURCE_ACL_NOT_FOUND/ALREADY_EXISTS）；⑦ Binding 作用域规则（ASSET 拒 L83-85、PLATFORM/scopeId 互斥 L89-94）+ACL aclId 成组原子写（L112-116）；⑧ 审计经权威端口+契约 x-audit-event 七动作齐备；⑨ 权限目录只读（无 POST /permissions，扩展仅 V4/V14/V20/V23 migration——条款 L423 满足，㉕ 结论沿用）；⑩ V23 DEC-018 拆分种子正确（OBSERVER 12=READER7+5、READER 删 user:read 恢复 notification:read）；⑪ ensurePlatformAdmin 引导幂等（L128-140）；⑫ Agent 高风险拒绝（isPermitted L112-118+auth 测试 L86/L166 双在档）+防枚举 authorizeResourceOrNotFound L136-166；⑬ 测试面广度尚可：AuthorizationLifecycleIT 10 用例+AuthorizationIT 4 用例+两单测类在档（语义对错见 T-1/T-14，覆盖面另行登记）。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796），`./mvnw verify` 本轮未重跑，测试断言形状为静态阅读所得；② EVD-017 的「AuthorizationIT/LifecycleIT 覆盖组合鉴权」为 E2 自记，其 proven 文本含代码中不存在的 Self-elevation 防护（T-3 已定性为虚证），但**未排除**测试运行期以其他夹具间接覆盖的可能——按现势代码与测试全读判断为不可能，仍标注 unproven；③ T-8 的 TTL 滞留窗口时长＝access token 有效期配置值，未实测运行行为；④ 负证据（委派/自提权/TEAM/缓存/compose）均为 Grep 模式+核心文件全读交叉确证，模式外同义实现不可绝对排除；⑤ 前端仅 RolesPage/RoleBindingsPage 要点实读，PermissionsPage/ResourceAclsPage 存在性经 Glob+test 文件在册，细节未逐行；⑥ 涉条款/AC/PAGE/卡/EVD/契约/迁移/产品码/九类禁改面**只登记不改动**。

**整改待批（须人类先批准/更新 Task Card 与证据链，AI 不自批不自改）**：① **前置裁决：TASK-GOV-002 幽灵阻塞**——补立 GOV-002 卡并给出 AUTH-003 解除路径，或将 blockedBy 改指实体，否则本域一切完成判定悬空；② **T-1 语义裁决（头号）**：AND（改 isPermitted+EffectiveScopeResolver+两测试）或修订 AC-005/017 卡场景措辞承认 OR——属产品安全语义决策+后端变更，须人类定夺后走契约/实现/测试三方同步；③ T-2/T-3/T-4 三类防护（委派集合/自提权/跨组织+停用主体）为条款 MUST 行为，需新行为切片+契约错误码（现无 SELF_ESCALATION/DELEGATION_SCOPE 类码）——须立卡批准；④ T-7 角色启停端点（PATCH /roles/{id}/status 或等价）+RoleView.status+启停事件+前端列+compose 场景，与 55 整改①并案（同型死列）；⑤ T-6 TEAM 作用域涉 V4 已应用迁移 CHECK 扩展——须 V34+ 新迁移+契约 ScopeType+前端选择器三处同步（禁改 V4，承 AGENTS 迁移纪律）；⑥ T-8 授权变更事件生产者（OUTBOX/job 投递）设计裁决——与 55 T-10 并案，或修订条款 L431 承认「无缓存+JWT TTL 窗口」为可接受偏差；⑦ T-9 DELETE role 补幂等键或契约与实现两侧统一去除 DELETE 幂等接收；⑧ T-10 契约补 Idempotency-Key 参数声明（DELETE binding/acl）；⑨ T-12 四 EVD 卡重写（requirements 对齐、scenarios 填充、E2/E4/accepted 互斥消解、017 虚证文本删改）；⑩ T-13 compose 授权场景族（AC-004/005/006/008 E4 前置）；⑪ 承接 55 整改⑧校验器缺口登记（本域升级为五卡引用 OPEN 需求+approvedBy 全 null 仍 authorized）、㉟⑬/㊶⑦/㊷⑥/㊸⑥/㊹④/㊺⑤/㊻⑤/㊼⑤⑦/㊽②④/㊾⑤/㊿⑥/51⑥/52⑩/53⑧/54⑥/55⑨ 挂靠清单。

→ 本项与 ①—56 一并不在本轮 AI 自决实施范围内。

### 57 P4 Agent 集成残域三连 REQ-AGT-002 / REQ-API-AGT-001 / REQ-COMP-AGT-001（条款 ↔ Skill 包实物 ↔ 契约 ↔ 生成/门禁 ↔ E4 脚本 ↔ 证据 ↔ 测试 ↔ 实现八层对撞，README 凭据示例不可派发 + 三连 EVD 空壳 + E4 脚本零执行承接实证）

**立项查重**：以「抽取 plan 全集 REQ-ID 做差集」为主口径（PowerShell `Select-String -AllMatches` 抽出既有 59 个唯一 REQ-ID；Grep count 模式输出一度自相矛盾，弃用后经 content 模式精确复核），`REQ-AGT-002 / REQ-API-AGT-001 / REQ-COMP-AGT-001` 三连**零命中**——01-requirements 底账 75 条中整域未成节，且三卡 TASK-P4-005/006/007 一一对应、有实体交付物嫌疑，择定成本最低收益面最大。附带引用划界（现势复测）：㉓ L425-444/L938-959 已定性 MCP `/mcp` vs `/api/v1/mcp` 契约落点漂移（本节不重复 mcpEndpoint 裁定，仅承接 Skill 包侧登记）；㊼ L2418（W-4）已登记 TASK-P4-007 L36 `fixtures/p4` 虚指（本节仅定装 `p4-onboarding` 目录形为同型**第三例**）、L2420（W-6）已登记路线图工具双缺弱项；㉛ L1719/L1720 已定性 ci.yml 门禁质量与 `.ps1` 成对缺失（L1759 亦已登记 p4-onboarding.sh 无 `.ps1` 对应——本节 N-10/N-12 只登记 **agent 契约不在门禁覆盖面**与**脚本零执行承接**两个增量，不重复既有结论）；L945/L1861/L2168 为脚本路径事实引用，无定性重叠。

**取证基准声明**：基准＝现势（HEAD a022796 复测在位）；TASK-P4-005 L7 baseCommit 59f40d2 与三张 EVD commitSha 7dc22d44 与 HEAD 三值不同源——承 55 T-7/56 同型漂移模板。

**权威链定装**：条款＝p4-agent-integration.md L231-330——REQ-AGT-002 L231-269（status READY L234、blockedBy L237、E4 L238；**七文件目录树 L244-251**：common/API.md、common/SECURITY.md、common/examples/、openclaw/asset-hub/SKILL.md、qwenpaw/asset-hub/SKILL.md、compatibility.yaml、openapi/aihub-agent-v1.json；**九条 Skill 规则 L256-264**；compatibility MUST 记录字段 L268「客户端、最低/**已测**版本、transport/auth/tool schema 结果、测试日期/Commit、已知限制」）；REQ-API-AGT-001 L271-292（L280「从权威 OpenAPI **生成**裁剪契约」双 Profile、L288 breaking diff 门禁与导入测试）；REQ-COMP-AGT-001 L295-316（blockedBy「locked OpenClaw/QwenPaw versions」L301、十一条验收 L307-316：≤30 分钟、两客户端**分别** Evidence、Secret 不入聊天/日志、refresh 策略正常）；AC＝p4-exit-catalog.md **L55（API）/L61（SKL）/L67（COMP）三条全 MUST/E4**；JRN＝user-journey-catalog.md L74 JRN-P4-004；卡＝TASK-P4-005（L52/L113 验证命令）/006（L101 未勾步骤）/007（L35-36 allowedPaths、L50-51 泛化验证命令）三卡全 READY+authorized true+要求 E4；证据＝EVD-P4005/6/7-001 三卡全读；实物＝aihub-agent-integration/（README 54 行+SKILL 61 行+compatibility 51 行全读，LS 现势三文件）；契约＝aihub-agent-v1.yaml 全读 473 行（九 `/agent/*` 路径、L9 `x-verification-status: implemented-unverified` 自证、L204/L241/L283 Idempotency-Key required、tags read-only/contribution **单文件双 profile**）；实现＝AgentController 全读 380 行+AuthController L81/L93-96+IdentityRequests L30+IdentityResponses L28-47；E4 脚本＝p4-onboarding.sh 全读 227 行；测试＝AgentControllerTest 303 行 13 用例+AgentRestIT 166 行 4 用例全读。

**文内自纠（取证期推翻初判，先行登记）**：早期判断「compatibility.yaml L19 tokenEndpoint 指向不存在端点」**被 AuthController 全读推翻**——L81 `/auth/token` 与 L93 `/auth/agent/token` 为双别名端点（L94-95 委托 L82 实现，SecurityConfiguration 两者皆在凭据交换白名单），语义均可达；该缺陷降级为**同包口径不一致**（N-5），并由此深挖出 N-6/N-7 两个更实的派发级缺陷。

**差集矩阵**：
- **N-1 七文件缺六**：LS 现势仅 README/SKILL/compatibility 三文件——条款 L244-251 MUST 树中 common/API.md、common/SECURITY.md、examples/、openclaw 与 qwenpaw 两份分客户端 SKILL.md、openapi/aihub-agent-v1.json **六件产物全缺**。
- **N-2 README 自证伪**：README L12-13 目录树列出 `examples/search-and-download.md`，LS 零命中——README 与其自身声明直接矛盾。
- **N-3 分客户端 SKILL 缺失**：SKILL.md 为单文件合并声明（README L10「OpenClaw/QwenPaw 兼容」）≠ 条款要求的两目录分立；AC-SKL L61「OpenClaw 与 QwenPaw 加载 Skill 包」无分立加载物。
- **N-4 compatibility MUST 字段缺**：L9/L13 仅 minimumVersion（0.3.0/1.2.0），全文件 Grep `testedVersion`/日期/Commit/已知限制**零命中**——L268 六类记录字段五缺。
- **N-5 同包声明两 token 端点**：compatibility L19 `/api/v1/auth/token` vs SKILL L36+README L26/L45 `/api/v1/auth/agent/token`——同一接入包教客户端两个端点（均为别名可用，但口径分裂且未注明通用端点为 Client/Agent 共享面）。
- **N-6 README 凭据示例字段名不可派发（头号接入阻断）**：README L48-51 示例请求体 `{clientId, clientSecret}` ↔ 实现 `ClientCredentialTokenRequest`（IdentityRequests L30）字段为 `(grantType, subjectId, credential)`——按 README 照发必触发 AuthController L83-84 ValidationException；**30 分钟接入旅程第一步即断**。
- **N-7 README refresh 说明与实现矛盾**：L54「返回 access_token (15min) + refresh_token (24h)」↔ L87 `TokenPairPayload.from(result, false)`+IdentityResponses L43 `includeRefresh=false` ⇒ **响应体 refreshToken 恒 null**（L79 javadoc 自证「默认不下发刷新」）——README 教客户端取一个拿不到的令牌；AC-COMP L67「access 过期/刷新策略正常」在现 Agent 面无从验起（设计意图裁决见整改②）。
- **N-8 九条 Skill 规则 L256-264 多数无落点**：SKILL.md Constraints 仅 4 条；SHA-256 下载校验、retryable 退避、License/Sensitivity 下载前检查、README/Card 不可信声明、Secret Store 引用在包三文件内零命中——AC-SKL L61 点名 MUST 覆盖「下载校验」「不可信标注」「Secret Store」三处皆空。
- **N-9 裁剪契约「生成」无链路**：Glob scripts/ 仅 aih/ 零生成脚本；TASK-P4-005 allowedPaths 含**不存在的 scripts/openapi 目录**；契约以 tags 单文件双 profile ↔ 条款 L280+AC-API L55 要求「导入只读与 contribution **两个** Profile」的可分发产物（json L251）不存在。
- **N-10 breaking diff 恒真 + CI 零覆盖**：TASK-P4-005 **L52 与 L113 两处同为** `diff aihub-agent-v1.yaml aihub-agent-v1.yaml`（同文件自反，永不 breaking）；.github/workflows 全目录 Grep `agent-v1` **零命中**——ci.yml L140-161 lint/diff 覆盖面仅主契约，agent 契约「无基线比对+无 CI 承接」双缺失。
- **N-11 三连 EVD 空壳（同构）**：EVD-P4005/6/7-001 三卡全部 requirements/scenarios 空、taskValidation/changedPath/completion 均 null、`evidenceLevel: E3`（卡要求 **E4**）、`proven: []`、三卡同值 commitSha 7dc22d44、accepted false、command 为与本域验收对象无关的泛化 `mvnw -Dtest='*Asset*,*Auth*,*Mcp*,*Agent*' && verify.sh`——三条 E4 MUST 之证据链**零**。
- **N-12 E4 脚本零执行承接**：全仓 Grep `p4-onboarding` 仅 4 文件命中（脚本本体/TASK-P4-007 卡/implementation-status-2026-07-10 L154 登记/本 plan 自引）——**verify.sh、verify.ps1、CI 无一调用**；P4-007 卡验证命令 L50-51 为泛化 compose config+verify.sh（不含脚本本体）；卡 allowedPaths L35 `p4-onboarding`（目录形）↔ 实际 `p4-onboarding.sh`（文件形）错位（㊼ W-4 同型第三例定装）。
- **N-13 双客户端 Evidence+30 分钟实测零落地**：p4-onboarding.sh 是 curl 合成客户端旅程，非真实 OpenClaw/QwenPaw 装载；「≤30 分钟」仅 L7 注释预期；blockedBy「locked client versions」（AGT-002 L237 第二项、COMP L301）**无可解析工件/无 ID**——AC-COMP L67「两个锁定客户端分别生成 Evidence」双缺。
- **N-14 blockedBy 名实错位（轻于 56 型）**：AGT-002 L234 status READY 但 L237 blockedBy [Q-004]——Q-004 已 ACCEPTED→DEC-006 且内容为 AI IDE 多目标选型（open-questions L14），与 Agent Skill 包无关；56 型为「指向未立卡」，本型为「指向已解除之无关 Q+不可解析文本」，validate-task-card 两型皆不拦截。
- **N-15 AgentRestIT 名义覆盖与断言实存错位**：头注 L3-4 声称覆盖「草稿创建、上传会话及限流拒绝…AC-P4-API-001（贡献端点）、H2（写端点限流）」——166 行全读实存仅 4 用例（三件只读+匿名 401，L136-166），**写端点/限流零用例**；写面真实覆盖仅在 AgentControllerTest（@WebMvcTest mock 服务的切片层）。AC-API L55 核心验收「导入两 Profile+相同 JWT 得到 REST/MCP **等价授权**+breaking diff 阻断」无任何测试断言或 E4 通过记录；契约 L9 implemented-unverified 自证与实况吻合（如实登记为正向，见⑧）。

**合规正向面**：① 九 REST 端点全实现+fail-closed 三连环（AgentController L276-286：AUTH_UNAUTHENTICATED→MCP_INVOKE→业务权限）+AGENT 型工具白名单 L284→AuthorizationService L174→iam_agent_tool（MCP 通道 McpController L157 同源复用——适配器不载业务规则，合 AGENTS 基线）；② 写面三闸门：writeToolsEnabled 默认 false 短路（L90/L293）+写工具白名单复核（L300）+Idempotency-Key 强制（L305-308）——契约 L204/241/283 required 声明与实现**四方一致**（对照 ㊺ CFG 域幂等三方互异，本域执行堪称范式）；③ 限流+审计 L311-337（RateLimiter+AGENT_RATE_LIMIT_DENIED），切片测试 429 在档（AgentControllerTest L293-303）；④ 下载 handle-only 三防线：契约 schema 无 presignedUrl 字段+测试断言 doesNotExist（L184-197）+脚本响应体扫描泄漏即 fail（p4-onboarding L195）；⑤ 契约↔tools.yaml↔compatibility **三方工具名一致**（11 工具 L61-214 ↔ 白名单 L23-40 ↔ README L22-24）；⑥ Agent 高风险拒绝承 56 ⑫（AuthorizationService L112-118，README L40 文档口径一致）；⑦ 白名单/工具禁用拒绝路径测试在档（AgentControllerTest L162-169/L266-276 MCP_TOOL_NOT_ALLOWED 双测）；⑧ 状态面**零过度声称**：implementation-status-2026-07-31 L168-170 如实登记 G1/G1/G3、07-10 L152-154 H1/H2/H3 IMPLEMENTED_UNVERIFIED——文档诚实度与证据空壳（N-11）形成「总账如实、明细空挂」的结构性反差。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796），`./mvnw verify` 本轮未重跑；AgentRestIT L53 `disabledWithoutDocker`，其 4 用例近期实际执行与否 unproven；② N-8 基于包内三文件全读+包内 Grep，规则文字散落 docs/runbooks 的可能性未绝对排除——但 AC-SKL 验收对象为 Skill 包本身，散落不改变判定；③ N-7 的「Agent 应否下发 refresh」为设计意图，条款仅言「刷新策略正常」（COMP L67），AI 不自裁方向；④ TASK-P4-006 L101 步骤复选框未勾——卡面自认未完成，与 N-1 相合，非本节新增缺陷；⑤ 涉条款/AC/卡/EVD/契约/产品码/CI 禁改面**只登记不改动**；⑥ README L54 的 15min/24h 时长数值本身未对 `security.agentToken.maxTtlSeconds` 配置实值核验，仅 refreshToken 存在性判定为实证。

**整改待批（须人类先批准/更新 Task Card 与证据链，AI 不自批不自改）**：① 补齐七文件树六件产物+openclaw/qwenpaw 分客户端 SKILL 拆分（消 N-1/N-2/N-3，TASK-P4-006 在授权范围内可直接实施——须人类勾卡）；② **方向裁决**：Agent 端 refresh 语义——实现补 refresh 发放（改后端）或 README L54 改口径+条款 COMP「刷新策略正常」修订措辞（改文档），先裁后用，同步修 README L48-51 字段名为 `subjectId/credential`（N-6/N-7）；③ Skill 包 tokenEndpoint 三处统一锁定 `/api/v1/auth/agent/token`（N-5）；④ E4 实测后回填 compatibility 已测版本/日期/Commit/已知限制（N-4，依赖⑧先行）；⑤ 建「权威契约→agent 裁剪」**生成脚本**+双 Profile 独立产物（openapi/aihub-agent-v1.json 等）+以生成源 vs 产物比对替换 TASK-P4-005 L52/L113 同文件自反命令+agent 契约接入 ci.yml lint/diff（N-9/N-10，改契约与 CI 须立卡）；⑥ EVD-P4005/6/7 三卡按 E4 口径重写（requirements/scenarios 填充、proven 实测、级别/accepted 互斥消解——N-11，承 55⑨/56⑫ 同批）；⑦ p4-onboarding.sh 接入 verify.sh/正式 journey 执行承接+P4-007 allowedPaths 目录形修正+fixtures/p4 立或不删（N-12）；⑧ 真实 OpenClaw/QwenPaw 版本锁定工件+分别 Evidence+≤30 分钟实测（N-13，blockedBy 第二项的实体化即其解除路径）；⑨ AGT-002 L237 blockedBy 更正（去 Q-004，改指客户端锁定工件）（N-14）；⑩ AgentRestIT 补写端点/限流/两 Profile 导入+REST/MCP 等价授权联合用例，或头注改为实存口径（N-15）；⑪ 校验器缺口挂靠清单承 ㊼②/㊽③④/55⑧/56⑪ 追加三例：恒真 diff 命令、EVD 级别与卡要求错配、blockedBy 指向无关已解除 Q——validate-task-card 三型皆不设防。

→ 本项与 ①—57 一并不在本轮 AI 自决实施范围内。

### 58 P4 MCP 协议域五连 REQ-MCP-001/002/003/004/006（条款 ↔ Controller/Catalog/ResourceHandler/Handle 实现 ↔ tools.yaml ↔ 契约校验双层 ↔ 测试五类 ↔ nginx ↔ V8 配置 ↔ G 账九层对撞，协议错误 HTTP 200 包裹 + list_versions 零状态过滤 + handle 缺 digest + 1MB/30s 仅存 javadoc）

**立项查重**：主口径升级为「底账标题全集 − plan 内文引用全集」差集法（PowerShell 脚本文件法，内联 `$_` 被剥离教训沿用）：01-requirements 底账 `^## REQ-` 标题 74 条 vs plan 全集引用 62 条 → 差集 13 条＝REQ-AST-002..008（×7）、REQ-MCP-001/002/003/004/006（×5）、REQ-PRE-001；REQ-MCP-005 已 HIT（㊿ L2419 W-5）。择定 **MCP 五连**（整域同文件 p4-agent-integration.md、实体代码齐备、AC 七条在册，对撞密度最高）；AST-002..008 与 PRE-001（与 53 preview 交叠需另划界）留 59+ 候选。划界（现势复测）：㉓ L425-444 仅 MCP 端点路径漂移切面（`/mcp` vs `/api/v1/mcp`），本节不重复；㊿ W-5 已定性 ConfirmationToken 生产零调用，本节仅登记 MCP-005 两个**增量切面**；57 为 Skill 包/契约裁剪侧，与协议运行时互涉零；53 已处理传输域可见性/防枚举正向基线，本节 M-16 只登记 MCP 通道不对称增量。

**取证基准声明**：基准＝现势（HEAD a022796 复测在位）；本节全部产品码行号为落盘当轮实读。

**权威链定装**：条款＝p4-agent-integration.md L9-229——MCP-001 L9-45（POST /mcp、E4、JWT-only、PrincipalContext 同源、session 语义、Nginx 禁缓冲+idle/request timeout、requestId/traceparent 一致、**协议错误不返回 HTTP 200 伪业务成功 L41**、响应最大字节/条目/执行时间限制、SSE 默认不启用、tools/list 双控后过滤建议 L45）；MCP-002 L47-106（Catalog 14 字段 L60-75、授权八条件、Schema explicit required L85、write 修复+phase 调 P4 L99-101、Drift AUD-011 L105）；MCP-003 L108-142（复用 AST-003 Query+DATASET 分类、matchedFields 服务端、`mcp.maxResultItems` 配置≤REST L124、不返回完整 README/URL、Card 截断+untrusted）；MCP-004 L144-174（**list versions 只 PUBLISHED/DEPRECATED L153**、get version 要求 assetId+version L156、handle+expiresAt+**sha256/digest L163**、DEPRECATED 警告/ARCHIVED 默认不返回、审计不存 URL）；MCP-006 L205-229（四 URI、read 实时授权、**Card untrusted+限大小 L216**、防枚举、sourceCommit/digest、缓存不跨 Principal）；AC＝p4-exit-catalog.md L25 SCH-001（MUST/**E3**）、L31/37/43 MCP-001/003/004（MUST/E4）、L49 ALW-001、L73 RES-001（SHOULD/E4）；实现＝McpController 310 行+McpToolCatalog 400 行+McpResourceHandler 131 行+McpDownloadHandle 51 行+ConfirmationToken 70 行全读；契约＝tools.yaml 219 行全读+validate-tools.sh 65 行全读；测试＝五类全清单（ControllerTest 15+CatalogTest 9+ContractTest 4+EndToEndIT 4+TokenTest 7＝39 用例）；配置/基础设施＝V8__configuration.sql L65-66、nginx.conf L94-104、DownloadApplicationService L64-80、VersionApplicationService L104-120/L211-215、VersionQueryService 60 行、MyBatisVersionRepository L62-68。

**差集矩阵**：
- **M-1 协议错误统一 HTTP 200 包裹（条款 L41 正面冲突）**：McpController L94-107 五方法分支全 `ResponseEntity.ok()`，含 L106 `-32601 Method not found` 亦 200；同文件 L88 缺 method 却 400——**协议层两类错误口径互斥**。测试反镜像缺陷：ControllerTest L256 `status().isOk()`+error.code -32601、EndToEndIT L199 同。JSON-RPC over HTTP 规范允许 200+error，条款采更严口径——方向须人类裁决，本节不定裁。
- **M-2 tools/list 不按主体白名单/Scope 过滤（契约自述矛盾）**：tools.yaml L39-42 声称「tools/list 按主体过滤」；实现 L128 `listVisibleTools()` 零主体参数（Catalog L91-95/L133-141 仅 writeToolsEnabled 开关+L49 AGENT_DENIED 静态集），AGENT 白名单 `iam_agent_tool` 闸仅在 tools/call（Controller L156-158）——writeToolsEnabled=true 时 Agent 列表下发其必被拒的写工具。条款 L45 为「建议」级，但契约 L39-42 是**自身声明与实现矛盾**。
- **M-3 resources/list 零授权**：Controller L264-266 直返模板列表，无 `requireAuthenticatedPrincipal` 亦无 MCP_INVOKE（对照 tools/list L120-123 双闸）——仅 JWT 全局闸后的**任意认证主体**（含无 mcp:invoke 的用户）可枚举资源面。测试 L224-234 仅正用例，负路径零覆盖。
- **M-4 resources/read 入口无 MCP_INVOKE + 异常消息透传**：L268 入口无闸（handler 内 L74-118 各分支仅全局 `requirePermission("asset:read")` 字符串字面量）；L276-280 `catch (Exception e)` 把 `e.getMessage()` **字符串拼进 text JSON 响应**——内部异常细节泄漏给 Agent 客户端+引号注入可破坏 JSON 形状。
- **M-5 inputSchema 全缺 required**：条款 L85「explicit required」；Catalog `schema()` helper L352-358 仅 type/additionalProperties/properties，**11 工具零 required**（如 asset_get 的 assetId 可省调用）；校验双层皆不防：ContractTest 仅同步 name+write 两面（L34-52），validate-tools.sh L37 必填字段列表不含 parameters/inputSchema。
- **M-6 Catalog 14 字段大面积缺失**：条款 L60-75 要求 14 字段；ToolDefinition 实存 5 参数（name/description/inputSchema/write/requiredPermission，测试 L264-265 构造形状实证）；tools.yaml 侧 version/outputSchema/maxResultItems/highRisk/idempotency/applicationUseCase/auditEvents **零声明**（get_upload_status L188-200 write:true 却无 idempotency 声明，实现侧 Controller L174-188 强制幂等键——契约漏声明面）。
- **M-7 list_versions 零状态过滤（条款 L153 硬差，行为面头号）**：MyBatisVersionRepository L62-68 仅 `eq(assetId)+ORDER BY+LIMIT`；服务层 L113-120 仅权限+limit clamp；Catalog L207-222 透传——**DRAFT/PENDING_REVIEW/ARCHIVED 全量下发 Agent**，DEPRECATED 警告、ARCHIVED 默认不返回皆零落地（对照下载通道 issueTicket L73-80 PUBLISHED-only 有闸——同域三通道执行口径互斥）。
- **M-8 get_version 输入形状漂移**：条款 L156 要求 `assetId+version`（防臆测）；实现 L224-229 与 tools.yaml 一致采 `versionId`——契约↔实现一致但**双双偏离条款**，需裁决改实现或修订条款。
- **M-9 handle 缺完整性锚**：McpDownloadHandle L25-34 十字段无 sha256/manifestDigest；AC-P4-MCP-004 L43 验收措辞明列「handle/expiresAt/digest」——E4 验收对象缺字段。正向半边：fromTicket L39-51 剥 presignedUrl✓、CatalogTest L148 断言在档。
- **M-10 响应上限三件套仅存文档**：Controller javadoc L40「响应体最大 1MB，工具调用超时 30s」——mcp 包 Grep 全零实现码；nginx L103 `proxy_read_timeout 300s`（≠30s，禁缓冲 L101-102✓ 为条款达成面）；V8 L65 `mcp.maxResultItems='50'` **后端零消费**（全 resources Grep 仅 V8 自身命中）——MCP-003 L124 配置要求形同虚设，条目上限实为硬编码 search≤100/versions≤50。
- **M-11 session/requestId/traceparent 语义零落地**：条款 MCP-001 要求「session 语义明确」「requestId/traceparent 一致」；mcp 包 Grep `sessionId|traceparent|requestId` 零命中（SSE 零命中＝「默认不启用」合规，为正向）；JSON-RPC id 仅回显（L295-301），HTTP 层透传由全局过滤器承担与否未出包核验（见 unproven③）。
- **M-12 契约 phase 字样残留**：条款 L101 要求 Contract phase 调 P4——tools.yaml L8 `contractStatus: implemented-p4-synced` 已达成（正向），但头注 L1-6 仍「P0-B 阶段」措辞、L138 节标题「P0-B 默认关闭」；Grep 全 contracts 树 `target-p0-b` 残留在 events-v1.yaml L9（事件契约属他域，顺带登记同型字样漂移）。
- **M-13 ConfirmationToken 增量切面（承 ㊿ W-5 死代码定性）**：70 行+7 单测完整但生产零调用之外，新增两切面——validate L36-54 五条件**缺条款 L195「资源」绑定**；digest 基于 `arguments.toString()`（L64-65）对 Map 迭代顺序敏感，非规范化序列化，接驳前必改。
- **M-14 限流错误形状第三态**：L164-168 `throw RateLimitException` → 全局兜底 429 平台错误体（ControllerTest L326-327 `status().isTooManyRequests()`+`$.code` 实证）——同一 /mcp 端点响应三形状（200+result/200+error/429 非 JSON-RPC），客户端需三套解析；与 M-1 同族，统一裁决。
- **M-15 Resource 面形状缺陷**：card 分支 L87-89 README **原样全文无 untrusted 标注无大小限制**（条款 MCP-006 L216+MCP-003 Card 截断双要求；manifest 分支 L98-106 untrusted:true+sourceCommit+digest 齐备为正向对照）；contentEntry L129-131 `String.valueOf(view)` 输出 Java record toString 而非 JSON——mimeType 却声明 application/json（ControllerTest L239-240 的 mock 恰以合法 JSON 字符串掩盖之）。
- **M-16 版本/下载通道资产级可见性不对称（承 53 划界增量）**：search/get 路径传 principalId 走可见性过滤（Catalog L160/L170/L187-188）；list_versions/get_version/request_download 三路径仅全局 asset:read 无资产可见性（VersionQueryService L48-52 直查 404、VersionApplicationService L211-215 同、issueTicket L67 注释自证「防枚举」但仅全局权限层）——持有 asset:read 的主体可对**不可见资产**枚举版本明细。

**合规正向面**：① 认证/授权 fail-closed 主干：匿名 401（IT L183-190 实证）、tools/list+call 双闸（L120-123/L146-158）、`currentPrincipalId` 拒匿名（Catalog L366-372）、PrincipalContext 复用不-parse-token（合 AGENTS 适配器纪律）；② 写面三闸门齐备且四方一致：writeToolsEnabled 默认 false（V8 L66+yaml 六工具 enabled:false L140-213+ContractTest L65-76 隐藏断言）+AGENT_DENIED 静态集（L49+CatalogTest L181）+Idempotency-Key 强制（Controller L174-188，测试 L262-312 三态覆盖 params/header/缺失）；③ handle-only 基线：presignedUrl 剥离+测试断言+E4 脚本响应扫描三防线（承 57④），issueTicket PUBLISHED-only+权限先行注释（L64-80）；④ 契约同步机制**存在**（虽窄）：ContractTest name/write 双同步+validate-tools.sh 结构校验+CI mcp-contract 作业（07-10 L162 G3/L115 P35 登记）；⑤ AUD-011 双侧闭环实证：audit L35 **CLOSED**+yaml publish 段 write:true（L203-204）在位+phase 字样 p4-synced；⑥ matchedFields 服务端计算+摘要形状（L174-178/L336+CatalogTest L96 用例名实相符）、无完整 README/URL 泄漏（search/get 输出面核对在档）；⑦ nginx /mcp 禁缓冲禁缓存（L101-102）达成条款明确要求；⑧ 四 URI 模板（Handler L33-36）+11 工具全集在位+E4 脚本 initialize/tools-list/asset_search 三步（p4-onboarding L149-174）；⑨ manifest 分支 untrusted+sourceCommit+digest 形状合条款；⑩ 状态面诚实：07-10 L16 P4 行自记 IMPLEMENTED_UNVERIFIED+E4 notProven，无过度声称。

**偏差与 unproven 登记**：① 全部结论为静态取证（HEAD a022796），`./mvnw verify` 本轮未重跑，39 用例现势 PASS 与否 unproven；② McpEndToEndIT L62 `@Testcontainers(disabledWithoutDocker = true)`，其 4 用例近期实际执行与否 unproven；③ traceparent/requestId 零命中范围限 mcp 包——全局过滤器/拦截器层是否承担 HTTP 侧透传未出包核验；④ M-16 中 AssetApplicationService.getAsset 的可见性内部与 ResourceHandler 委托调用面未逐行互证，「不对称」判定基于三路径参数面（无 principalId 传递）实读；⑤ CORS/Origin 校验对 /mcp 的作用面未逐一核验；⑥ 涉条款/AC/卡/EVD/契约/迁移/产品码/测试/部署**只登记不改动**；⑦ M-1 的方向性（JSON-RPC 规范惯例 vs 条款严口径）为设计裁决，AI 不自裁。

**整改待批（须人类先批准/更新 Task Card 与证据链，AI 不自批不自改）**：① M-1/M-14 **统一裁决**协议错误与限流错误的状态码/封装策略（修订条款 L41 或改造 Controller+两测试+契约三处同步）；② M-2 listVisibleTools 增主体感知重载（agent 白名单+Scope 过滤），或契约 L39-42 改实存口径（N-2 型先例：文档服从实现须显式改措辞）；③ M-3/M-4 resources 双分支补 MCP_INVOKE 闸+主体校验，移除 e.getMessage 透传改错误码映射；④ M-5/M-6 分工具补 required+14 字段渐进落地（idempotency/outputSchema 优先），ContractTest 扩面对齐参数/required 面、validate-tools.sh 同步扩展（改契约/CI 须立卡）；⑤ M-7 版本三通道状态过滤行为切片（服务层统一，REST/MCP 复用同查询）——条款 L153 MUST，**本节头号实施项**；⑥ M-8 get_version 形状裁决（实现改 assetId+version 或修订条款 L156）；⑦ M-9 handle 补 sha256/manifestDigest+AC-MCP-004 E4 实测；⑧ M-10 接线 `mcp.maxResultItems`（消 V8 死配置）+1MB/30s 落地或 javadoc+nginx 300s 三处口径统一；⑨ M-11 session/requestId/traceparent 语义补全或条款显式降级；⑩ M-12 tools.yaml 头注/节标题 P0-B 字样清理+events phase 字样并案；⑪ M-13 ConfirmationToken 接驳（补资源绑定+规范化 digest 序列化）或删除并修订 MCP-005 口径——与 ㊿ W-5 整改并案；⑫ M-15 card 补 untrusted+大小上限、contentEntry 改 JSON 序列化；M-16 三路径补资产可见性（与 53 防枚举整改并案）；⑬ 校验器缺口挂靠清单追加两例：契约测试同步面窄于「同步」声称、validate 脚本不查 inputSchema/required——承 ㊼②/㊽③④/55⑧/56⑪/57⑪。

→ 本项与 ①—58 一并不在本轮 AI 自决实施范围内。

### 59 REQ-PRE-001 数据集/模型安全预览域（条款 ↔ 实现 ↔ 契约 ↔ 存储 ↔ 证据五层全对撞）

**立项查重**：`REQ-PRE-001` 对本节前 plan 全文 Grep 仅 L2644（58 差集查重行）单点命中＝**真缺口**。差集法教训：㉔ 节标题 L2039 以「REQ-AST-001..008」区间记法覆盖 AST 族，58 节按单 ID `REQ-AST-002`…`REQ-AST-008` 字面 Grep 误判为未覆盖＝**伪缺口**——立项差集法须追加前缀族 Grep（如 `REQ-AST-`）复核区间记法。**承接划界**：① MCP `dataset_get_preview` 双缺（tools.yaml/McpToolCatalog 零存在）㉔ W-6 L2420 已登记，59 不重复；② TASK/EVD-P0BR-077/078 requirements 绑 `REQ-DST-DETAIL-001` 非 `REQ-PRE-001`——证据绑定错位属 53 同批未提交产物，59 仅登记不重开；③ 53 节 N-3 getPreview 资产可见性缺口、N-6 preview 路径形状文档差异已立，59 从条款 L85 版本绑定与安全维度补充。

**取证基准声明**：HEAD `a022796`；Preview 域 12 主文件 7 全读、preview 测试 6 类清单在档、EVD-P5002-001/077/078 全读；V44/PreviewController/Service/JobHandler 等 23 文件 `M`+`??` 未提交，承 53 §取证基准声明同批——**基准＝现势工作树**。

**权威链定装**：条款 p5-quality-operations.md L71-99（READY/MUST/P2+P5/DEC-008/E4+E5）十四条目；AC-DST-PRE-001..004＝dataset-agent-exit-catalog.md L54-57（NORMAL/BOUNDARY/UNAUTHORIZED/FAILURE，E4/E3+E4/E4/E4+E5）；AC-P5-PRE-001＝p5-exit-catalog.md L37（E5）；直接绑 REQ-PRE-001 的唯一 EVD＝EVD-P5002-001（status PARTIAL、commitSha e33c14a）；P2 侧 AC-DST-PRE-001/002 由 EVD-P0BR-078（绑 DETAIL-001，AC-001/002 E4 PASS，AC-003/004 无专项覆盖）；实现面 PreviewController（121 行）/ PreviewApplicationService（357 行）/ PreviewJobHandler（753 行）/ PreviewProperties（71 行）/ PreviewQueryRepository（47 行）/ PreviewQueryPort（30 行）/ ParquetPreviewReader（110 行）；DB V16 L168-192 asset_preview / V44 split_stats JSONB；契约 openapi L3228-3309 preview 四端点/L5052-5110 PreviewView schema；配置 application.yml L128-136；存储 deploy/compose/minio/init.sh asset-preview 桶；前端 PreviewPanel.tsx（449 行）；测试 PreviewContentSecurityTest（145 行）等 6 类；文档面 current-state-audit L69 NOT_IMPLEMENTED / traceability-matrix L36 PARTIAL。

**差集矩阵**：

- **P-1 存储面双向硬违反（条款 L84/L97，本节头号发现）**：条款 L84「预览由持久化 Job 生成到 `asset-preview`（桶）」+L97「投影表 asset_preview 只保存 version/source digest/格式/状态/生命周期和对象引用，**不保存完整样本**」。实现 JobHandler L155-168 DELETE+INSERT `asset_preview.content` TEXT 列全量样例入库；全后端 Grep 对 asset-preview 桶**零代码写入**（fetchFromStorage L449-481 读写均为 dvc-cache 桶）。init.sh L18/L48 桶已建+后端账号有权限，但为规划空壳。**双向反向**：桶无写+DB 有存；无 status/expires_at/object_key 列（V16 L171-184）。

- **P-2 不支持格式冒充成功（条款 L83/AC-DST-PRE-002）**：JobHandler L140-142 else 分支对未知 contentType 写 `{"raw": truncate(content)}` 入库并标成功——**不抛 PREVIEW_UNSUPPORTED_FORMAT、不返回错误**。AC-002「不支持返回 PREVIEW_UNSUPPORTED_FORMAT，不伪造空成功」在 Job 侧字面违反。对照代理面 Service L306-310 正确 415；enqueuePreview L213 null contentType 强转 text/csv 掩盖入口类型缺失。

- **P-3 versionId 可空致版本绑定松弛（条款 L85）**：条款 L85「只从精确 Version/Artifact 生成」。Controller L51 `@RequestParam versionId` 可空（`required=false`）；Repository L33 SQL `CAST(? AS varchar) IS NULL OR version_id = ?`——null 时返回**资产任意版本**最新预览；Service L137-139 getPreview 仅 asset:read + findLatestPreview，无精确版本强制。承 53 N-6 路径漂移并叠加安全维度。

- **P-4 TTL/访问控制缺失（条款 L89）**：条款 L89「Preview Artifact 有 TTL/版本/访问控制，不是正式版本」。V16 表结构无 status/TTL/访问控制列；init.sh L24-28 仅 asset-staging 有 expire-days 2 生命周期，**asset-preview 无 lifecycle rule**。

- **P-5 source digest 缺失（条款 L85/AC-DST-PRE-001）**：条款 L85「保存 source digest」+AC-001「显示 source digest」。PreviewView 契约无 digest 字段；V16 无 digest 列；JobHandler 未计算 SHA-256；sourceCommit（V16 列）≠ 内容 digest。

- **P-6 脱敏策略化缺失（条款 L88/L94）**：条款 L88「按 sensitivity/PII/deidentification 策略脱敏」。JobHandler L744-747 sanitize 仅剥控制字符 `[\x00-\x08\x0B\x0C\x0E-\x1F]`。ContentSecurityTest L96-106 注释承认「PII 过滤在脱敏层（AuditService.maskSummary）处理」——预览内容侧无 sensitivity/PII 策略引擎。条款 L94「预览/日志 canary 泄漏为 0」仅在 E5 drill 日志侧实测，预览内容 canary=0 断言在测试类中缺失。

- **P-7 Model 安全元数据预览零实现（条款 L87）**：条款 L87「Model 只生成安全元数据/缩略信息，不执行不可信模型代码」。Grep `model.*preview|preview.*model|安全元数据` 对 backend/src **零命中**；JobHandler L116-143 分派无 Model 特化分支。unproven：Model 资产是否通过 P-2 else 分支静默处理。

- **P-8 REST 路径形状差异补强**：条款 L95 `GET …/versions/{version}/preview`（路径段）vs 实现 `/previews?versionId=`（可空 query 参数）。53 N-6 登记功能等价；59 补 P-3 维度：versionId 可空叠加路径漂移＝版本绑定安全松弛。

- **P-9 UI 免责文案缺失（条款 L93）**：条款 L93 UI 明确「样例，不代表完整数据」。Grep `不代表完整` 全仓**仅条款自身命中**；zh.json/en.json preview 键 12 个均无该措辞；PreviewPanel L345 `previewTruncated`「预览已截断，仅显示前 100 行」是截断警告非免责语。来源版本（L150-153 `previewStatsSource`）和生成时间（L321-325 `generatedAt`）显示合规，但核心免责六字零出现。

- **P-10 Worker 时间/压缩炸弹码面缺位（条款 L91）**：条款 L91「Worker 资源/时间/压缩炸弹限制」。Properties javadoc L14「cgroup/Runbook 托底」；码面仅 maxBytes 读取上限（readCappedBytes L315-350），无 wall-clock timeout/解压缩比 guard。EVD-P5002 证明 cgroup OOM 运行时隔离有效，但属编排层非码层。AC-P5-PRE-001 notProven 格式矩阵压力未逐一实跑。

- **P-11 REQ-PRE-001 P2 侧证据绑定缺失**：EVD-077/078 requirements=[REQ-DST-DETAIL-001]（实测 TASK L119/L122、EVD L19）；唯一绑 REQ-PRE-001 的 EVD-P5002 为 E5 场景（AC-P5-PRE-001）。AC-DST-PRE-003（撤销后旧 URL 不能绕过/桶不公开）、AC-DST-PRE-004（canary 0/恶意路径）在 EVD-078 中无专项覆盖。**P2 侧 REQ-PRE-001 无直接绑定证据卡。**

- **P-12 文档滞后**：current-state-audit L69 `NOT_IMPLEMENTED` vs 12 文件+6 测试类+契约 4 端点+E5 drill script 在位。traceability-matrix L36 `PARTIAL/IMPLEMENTED_UNVERIFIED` 与差集矩阵一致。

**合规正向面**：① 上限三项 100/50/1MiB 四方一致（Properties L35-48+application.yml L129-131+契约+条款 L82）；② SVG 排除注入面（Service L54-55 注释自证+前端 RENDERABLE_IMAGE_MEDIA_TYPES L66 对齐）；③ 防枚举同 404（requirePublishedArtifact L271-283 归属校验+PUBLISHED 409+统一 404）；④ size 预检+流式双保险（readCappedBytes L315-350 413/404）；⑤ 对象键服务端 hash 推导防穿越（dvcObjectKey L532-537）；⑥ split_stats 解析失败降级 UNKNOWN 不伪造零（Service L165-196+前端 isStatsUnknown L77-78）；⑦ PUBLISHED-only 409 门禁；⑧ 错误码四码齐备（ErrorCode L204-214+error-catalog L210-213）；⑨ E5 实跑在档（EVD-P5002 PASS=7/FAIL=0/SKIP=0，cgroup 隔离+超限 DEAD+canary 日志零泄漏+恢复全绿）；⑩ Parquet magic 校验+失败回退 metadata-only（ParquetPreviewReader L42-44/L73）；⑪ MinIO asset-preview 桶已创建+后端权限+匿名禁止（init.sh L18/L21/L48）。

**偏差与 unproven 登记**：① 全部结论静态取证；`./mvnw verify` 本轮未重跑，六测试类现势 PASS unproven（ContentSecurityTest L34 `sun.misc.Unsafe.allocateInstance` 在 JDK 21 有 warning 但可运行）；② PreviewJobHandler 753 行完整 diff（vs HEAD committed）未逐行审，结论基于现势全文 Read；③ EVD-P5002 commitSha=e33c14a vs HEAD a022796 漂移，governance.taskValidation result FAIL，E5 结论在 HEAD 的有效性 unproven；④ Model 预览 P-7 基于 Grep 零命中，不排除 JobHandler else 分支静默处理 Model 类型（待补 dataType 枚举复核）；⑤ V44 未提交（?? 状态），split_stats 相关结论限于现势工作树；⑥ 全仓「不代表完整」仅条款自身命中，不排除通过其他组件或 tooltip 承载免责（前端 Grep 未穷尽全部文案键）；⑦ 涉条款/AC/卡/EVD/契约/迁移/产品码/测试/部署九类禁改面**只登记不改动**。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① P-1/P-4 **存储架构裁决**——样例入 DB（现实现）vs 生成到 asset-preview 桶+投影只存引用（条款 L84/L97），二选一（改实现或修条款）；若改实现需 V45 迁移加 digest/status/object_key/expires_at 列、JobHandler 改写对象存储、readLatest 改引用解析、init.sh 加 asset-preview lifecycle rule；② P-2 **Job 侧 fail-closed**：JobHandler L140-142 else 分支改抛 PREVIEW_UNSUPPORTED_FORMAT（AC-002 硬 MUST）；③ P-3 versionId 强制非空：Controller required=true、Repository 移除 IS NULL 分支；④ P-5 source digest 补全：生成时计算 SHA-256、V45 digest 列、契约 PreviewView 加 digest 字段；⑤ P-6 策略化脱敏立项（可复用 AuditService.maskSummary 模式扩 preview 路径）；⑥ P-7 Model 预览实现或条款降级（L87 裁决）；⑦ P-9 UI 免责文案补全（zh.json/en.json previewDisclaimer 键+Alert 组件）；⑧ P-11 **REQ-PRE-001 P2 侧独立证据卡**：直接绑 REQ-PRE-001+AC-DST-PRE-001..004（含 AC-003/004 专项覆盖）；⑨ P-12 current-state-audit L69 更新为 PARTIAL（与 traceability-matrix L36 对齐）；⑩ P-10 Worker 码面 timeout/ratio guard（或显式记为编排层承担并修订条款 L91）；⑪ 校验器缺口挂靠清单追加一例：EVD-077/078 卡绑 DETAIL-001 但覆盖 AC-DST-PRE-*（跨 REQ scenario 绑定合法性未校验）——承 ㊼②/㊽③④/55⑧/56⑪/57⑪/58⑬。

→ 本项与 ①—59 一并不在本轮 AI 自决实施范围内。

### 60 REQ-AI-IDE-001 AI 执行门禁元域（全库三维承接矩阵立项 ↔ 条款 ↔ catalog ↔ TASK/EVD ↔ 权威/影子 validator ↔ CI ↔ 实跑对撞）

**立项查重**：`AC-AI-IDE` 族对本节前 plan 全文 Grep **零命中＝真缺口**（区别于 58 对 P4 族的区间记法伪缺口）。本节立项法为**全库三维承接矩阵**（.tmp-diffac.ps1/.tmp-trace.ps1 普查后已删）：① plan 差集维——REQ 全集 74/74 全覆盖（REQ 维宣告穷尽）、AC 全集 170 条中 71 条 plan 未触及、反向孤引 5 例（AC-FAV-003/AC-MCP-004/AC-SUB-002/AC-SUB-003/REQ-AIW-001）全部定性为 plan 行文简写族名伪缺口（完整 ID 均有实定义：AC-DST-FAV-003＝dataset-agent-exit-catalog.md L45、AC-DST-SUB-002/003＝L48/49、AC-P4-MCP-004＝p4-exit-catalog.md L43、REQ-DST-AIW-001＝dataset-experience.md L368）；② TASK 承接维——分母 AC=170/REQ=74，TASK 卡面覆盖 AC=169/REQ=73，**全库唯一零 Task 卡 AC＝`AC-P4-RES-001`（p4-exit-catalog.md L73）、唯一零 Task 卡 REQ＝`REQ-MCP-006`（p4-agent-integration.md L205）**，同属 MCP Resources 族，其条款行为面已由 58 覆盖（㉑ L1035-1048：resources 不在契约、handleResourcesList L264-266 无闸），「零卡」属治理维增量、并入本节登记不单独成节；③ EVD 承接维——EVD 卡面覆盖 AC=123/REQ=55，**取证债 48 AC（19 族）/19 REQ（14 族）**，无 TASK＝结构性无人实施、无 EVD＝未取证，后者为本节 AC-008 的现状。**择域裁决**：AI 执行门禁域入选——plan 全族零触及＋traceability-matrix L42 自标 `READY / CI_GAP`＋该域即 DEC-009 自身门禁的验收面（元域）＋validator 可本地实跑自取 E1 证据不违 DEC-009。

**取证基准声明**：HEAD `a022796`（2026-10-10）；TASK-GOV-007 baseCommit `e490857`（2026-07-02）距 HEAD **217 提交**；实跑 4 命令（validate-spec 双版本、task-card 正例/DRAFT 反例/-CheckCompletion）均本会话本机执行，输出为 E1 现势证据。

**权威链定装**：条款 requirement-schema.md L140-183（READY/MUST/P0-B/DEC-009/E1，acceptance 列 AC-AI-IDE-001..008 全八条）；AC 定义 ai-execution-gate-catalog.md L9-16 八条全读（001 双 validator 退出 0 才可实施/002 缺失-DRAFT-未授权阻断/003 OPEN-REQ、SUPERSEDED-DEC、未知 AC 逐项阻断/004 baseCommit 漂移与 stageGate 阻断/005 宽路径-通配-逃逸阻断/006 `-CheckCompletion` 拒伪完成/007 证据计划-专用命令-横切项不可留空/008 **受保护 CI 必须运行 spec/task/scope/evidence/breaking 五门禁并阻断合并**）；TASK-GOV-007.md 全读（Front Matter L17-23 列 001..007、**008 由 L163 显式让位 TASK-GOV-008**、allowedPaths L50-70 全 docs 窄面、requiredValidationCommands L83-87）；EVD-GOV007-001.yaml 全读（L29 completionValidation=EXPECTED_FAIL、L92-95 notProven 含 008、L121-125 review.accepted=false）；validator 双版本：权威 `docs/ai-spec/tools/validate-spec.ps1`（manifest/稳定 ID 引用对撞）+ `docs/ai-spec/tools/validate-task-card.ps1`；影子 `tools/validate-spec.ps1`（79 行，仅文件存在+字节数）；CI 面 `.github/workflows/ci.yml` L114-125 spec-gate、L323-350 task-card-gate、L127-162 contracts；登记面 README L123、current-state-audit L42 AUD-018、generic-execution-protocol L179-183 §11、work-breakdown L53 GOV-008 候选行。

**差集矩阵**：

- **P-1 CI spec-gate 指向弱化影子校验器（本节头号发现）**：ci.yml L125 `run: ./tools/validate-spec.ps1` 调用的是**根级影子版**（Get-FileHash 5904…，与权威版 656B… 完全不同源）——该脚本 L20-37/L52-60 仅做 6 个需求文档+3 个治理文档+OpenAPI+状态机的**存在性+字节数**检查，零 manifest/稳定 ID 引用校验；权威版实跑输出「manifest documents: 148 / stable ID definitions: 752」的引用对撞在 CI 中**从不执行**。AC-AI-IDE-008「CI 必须运行 spec 门禁」按条款 L168 语义仅获形式满足。ci.yml L3-4 头注释自称「唯一权威验收逻辑，PowerShell 本地脚本不得产生不同判定」与 AGENTS.md/DEC-009 指定 `docs/ai-spec/tools/` 为门禁入口的权威链**方向性冲突**。
- **P-2 TASK-GOV-008 无正式 Task 卡（AC-008 全库唯一无卡承接场景）**：Glob `tasks/TASK-GOV-*.md` 仅 GOV-007 一卡；GOV-008 只存在于 work-breakdown L53 候选行、audit AUD-018、protocol §11、GOV-007 L163 让位声明、EVD L95 notProven 五处**文字登记**，无任何可领取的 READY Front Matter 卡——五门禁中 evidence 维 CI 零实现、scope 维条件化（见 P-3），DEC-009 闭环缺口自 2026-07-02 起 217 提交未闭合。
- **P-3 task-card-gate 不防「无卡改产品」旁路**：ci.yml L341-344 仅对**变更集内含 TASK-*.md 的 PR** 逐卡跑 `-CheckChangedPaths`，无卡变更直接「无 Task Card 变更，跳过」exit 0——绕过方式＝不提卡直接改 backend，恰是 AC-008 假想敌「故意忽略规则的 IDE」（protocol L181 自认本地脚本不阻止此类）。另 L337-339 无基线提交（新建分支 push）同样跳过。
- **P-4 breaking 门禁仅 main PR 强制**：L157-161 非 main PR 的 `--fail-on-breaking` 失败仅 echo 警告不阻断——与 AC-008「阻断合并」语义存在收窄差（或需条款显式收窄为 main-only，二选一裁决）。
- **P-5 GOV-007 卡现势不可实施态（AC-004 实跑正证）**：本会话实跑 `validate-task-card -TaskPath TASK-GOV-007` **exit 1**「baseCommit does not match current HEAD (a022796…)」——卡 §7 L157「前三条预期退出 0」行文已失效；卡未随 217 提交演进重绑，**validator 正确拒绝背书＝门禁活着**，任何续用该卡的实施须人类重签。
- **P-6 EVD-GOV007 未验收+全域验收荒**：EVD L122-125 recordedBy=Codex/reviewedBy=null/accepted=false；本会话全 evidence 目录扫描：130 份 EVD 中 `accepted: true` **仅 6 份（4.6%）**、false 124——「验证责任方验收」在 DEC-009 自己的证据卡上从未发生，比 validator 漂移更深的治理债。
- **P-7 README L123 双向陈旧**：「`.github/workflows/ci.yml` 尚未接入这些检查」与现实冲突——CI 已有 spec-gate/task-card-gate 两 job（弱化但存在）；文档↔CI 漂移需双向修订（P-1 修 CI、README 修现状描述）。
- **P-8 GOV-007 正文区间记法滞后**：L102「Acceptance：`AC-AI-IDE-001..006`」vs Front Matter L17-23 实列 001..007、行为切片 L121 已含 007——正文 §2 未随增补同步（立项阶段普查曾据此误判 007 缺承接，实为卡内文本不一致）。

**合规正向面（含实跑复现）**：① 权威 validate-spec 实跑 **exit 0 PASS**（148 文档/752 ID 引用对撞全过）——规格引用完整性现势良好；② **AC-002/007 反例实跑复现**：DRAFT 模板 task-card.md exit 1、37 项逐项报告（占位符 ID/证据计划缺失/横切 13 行逐项/宽路径全中），粒度与条款 L166-167 要求一致；③ **AC-004 反例实跑复现**（见 P-5，baseCommit 漂移阻断并点名 HEAD）；④ **AC-006 反例实跑复现**：`-CheckCompletion` exit 1，四类完成要件（commit≠HEAD/dirtyWorktree/notProven 在档/review 未 accepted）逐项枚举+数十条 changed-path 越界报告，伪完成零空间；⑤ GOV-007 allowedPaths 窄面+forbiddenPaths 四产品面禁入符合条款 L165/L170；⑥ traceability L42 `READY / CI_GAP` 与 protocol §11 L182-183「本地可执行、服务端未强制，不得描述为全闭环」——**状态自标诚实**，与 P-1..P-4 实跑结果方向一致（仅精度差：现状应表述为「服务端部分接入但弱化」）；⑦ 本会话 ㉔—60 全程仅只读+文档落盘、validator 失败面不自批——条款 L168「任一失败只能继续只读调查」的执行自证。

**偏差与 unproven 登记**：① AC-008 的 E1 要求「PR 正反例与 required check 证据」——GitHub 分支保护设置（required checks）为仓库外状态，本地不可证，**unproven**；② ci.yml 各 job 在 GitHub Actions 上的实际运行结果未取证（仅静态读文件）；③ EVD 6/130 accepted 的 6 份明细未逐一核对验收主体合法性；④ TASK-GOV 以外其余 ~100 张 TASK 卡的 baseCommit 现势有效性未普查（GOV-007 漂移送了，推定大批陈旧但 unproven）；⑤ 影子 tools/validate-spec.ps1 是否被其他流程（如 pre-commit hook）引用未穷尽 Grep；⑥ 涉条款/catalog/卡/EVD/validator/CI 六类禁改面**只登记不改动**（DEC-009；本会话对 validator 仅只读实跑）。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① P-1 **CI spec-gate 换权威校验器**：ci.yml L125 改 `./docs/ai-spec/tools/validate-spec.ps1`（或两版合并并修正头注释 L4「唯一权威」表述）；② P-2 **TASK-GOV-008 实体化立项**：建正式卡+补 evidence/scope 门禁+受保护分支 required checks+PR 正反例 E1 证据；③ P-3 task-card-gate 增「变更产品码必须携带 READY 卡」主动 scope 检查；④ P-4 breaking 门禁语义裁决（修 L161 非 main 阻断，或 AC-008/protocol 显式收窄 main-only）；⑤ P-5 GOV-007 卡 baseCommit 人工重绑或状态行显式归档 DECLINED-STALE；⑥ P-6 **EVD 验收荒整肃**：124/130 未 accepted 的存量证据按域复核（含本节引用的 GOV007/P5002/P5004）；⑦ P-7 README L123 与 protocol §11 现状措辞更新（双向漂移收口）；⑧ 顺带登记面：零 Task 卡对 `REQ-MCP-006`/`AC-P4-RES-001` 挂靠裁决（建议并入 P4 后续实施卡）、EVD 取证债 48 AC/19 REQ 族清单（AC-DST-AI×5/AC-DST-AIW×5/AC-DST-FAV×4/AC-P0B-AUTH×5/AC-P0B-TAX×4/AC-P0B-NOT×4 等 19 族）、EVD-P5004-001.yaml 内 `AC-P5-PERF-001-E5-100` 非标准 scenarioId 后缀记法（validate-task-card 对其合法性口径待核）、plan 简写族名引用 5 例风格项；⑨ 校验器缺口挂靠清单追加：GOV-007 卡正文 L102 与 Front Matter 不一致 validator 不校验（只查 Front Matter）——承 ㊼②/㊽③④/55⑧/56⑪/57⑪/58⑬/59⑪。**下一节候选域**（plan 零触及真缺口）：AC-P0B-AGT×5/AC-P0B-ENG×6 平台域残族、AC-DST-AI×5/AC-DST-AIW×4 数据集 AI 工作流族（注意其 REQ 已由 ㊿ 覆盖、AC 维待对撞）。

→ 本项与 ①—60 一并不在本轮 AI 自决实施范围内。

### 61 AC-P0B-AGT-001..005 Agent 注册与凭据交换（条款 ↔ TASK-P0BR-013/014 ↔ 双 EVD 撞名 ↔ 实现 ↔ 空心 IT 测试 ↔ OpenAPI ↔ 实跑对撞）

**立项查重**：`TASK-P0BR-013/014` 与 `AC-P0B-AGT` 族对本节前 plan 全文 Grep **零命中＝真缺口**（60 节预告三候选之一）。60 节同批预告的 AC-DST-AI×5/AC-DST-AIW 族经深查确认已被 ㊿ 实质覆盖（W-1 授权回路断裂/W-2 journey 拒证/W-3 EVD 失真三卡均在 TASK-P4-008/009/010 权威链内），不重复立项；AC-P0B-ENG×6 留 62 候选。58 节 N-6/N-7 已钉凭据交换 README 派发面，本节不重复，聚焦注册/交换/吊销/审计/幂等五 AC 本体。

**取证基准声明**：HEAD `a022796`（2026-10-10）；TASK-P0BR-013/014 baseCommit `0b152f3`（**短 SHA**，git rev-parse 可解析但违反卡面规范全 SHA 要求）；假 PASS EVD 落款 2026-07-11。实跑证据：① `validate-task-card -TaskPath TASK-P0BR-014` 与 `-013` 各 **exit 1、8 issues**；② `git cat-file -t 7dc22d44…` **fatal 不可解析**（本仓 commitSha 不可解析**第四次**出现）、`a20c3fff` 可解析；③ 本域工作树零改动（git status 修改仅 version/Preview 域），全部代码引用为现势。

**权威链定装**：AC 定义 p0b-exit-catalog.md L54-58 五条（001 注册凭据一次显/DB 摘要/最小 Scope/审计、002 正确/错误凭据交换、003 禁用后旧 JWT/凭据立即失效、004 高风险默认拒绝、005 未知 Tool 名 Catalog 拒绝 E3/E4）；条款 p0b-identity-authorization.md L235-270 REQ-AGT-001（READY/MUST/P0-B/幂等 REQUIRED L243/E4；行为 L249-256：Scope 三交集 L252、Tool 来自发布 Catalog L253、高风险不可默认授予 L254、Agent 无 refresh L255、重放不再回显凭据 L256；错误与审计 L260-264：AGENT_NOT_FOUND 防枚举+AGENT_REGISTERED/AGENT_TOOL_ALLOWLIST_UPDATED/AGENT_DISABLED 三事件；INV-AGT-001..007 L268-270）；TASK-P0BR-014.md（READY/authorized:true/场景计划五 E4 但 003 委托 013/行为切片表缺 AGT-003 行 L84-87/IDEMPOTENCY「by displayName+agentType」L33 与条款幂等键语义存疑/allowedPaths L42-46 不含 tasks+evidence 目录）；EVD 卡**三张两份**：真绑定卡 EVD-P0BR014-001.yaml（无连字符）与双胞胎 EVD-P0BR013-001.yaml、孤儿卡 EVD-P0BR-014-001.yaml（有连字符，无任何卡 FM 引用它）；域级总盘 EVD-P0B-V01-V12.yaml **L149 notProven 明列「AC-P0B-AGT-001~005 Agent 全部场景」**＝五 AC E4 零达成全域自认。实现面 identity 域五文件+bootstrap/security 过滤器+契约 OpenAPI+IT 三文件全读。

**差集矩阵**：

- **P-1 EVD 假 PASS 三重实锤（本节头号发现）**：真绑定卡 EVD-P0BR014-001.yaml L3 `status: PASS`、L10-22 governance 四项（specValidation/taskValidation/-CheckChangedPaths/-CheckCompletion）全部自称 PASS——本会话实跑 validator 对 014/013 **各 exit 1×8 issues**（短 SHA baseCommit、approvedBy/approvedAt null、DEC-010 未在 decisions 声明、requiredValidationCommands 缺 -CheckChangedPaths、Compose scope 缺 docker compose config+功能验证命令、evidenceManifests 越出 allowedPaths），「未执行声称」铁证；L26 commitSha `7dc22d44…` git cat-file 不可解析（幽灵提交）；L43 `evidenceLevel: E2` 冒充卡面五 E4 场景；L8 `scenarios: []`＋L47-48 非标准 scenarioId「implementation-verified」单段散文打包五 AC。EVD-P0BR013-001.yaml 同文双胞胎。
- **P-2 双 EVD 卡撞名、证据链指向反**：孤儿卡 EVD-P0BR-014-001.yaml（连字符版）＝PARTIAL/E2/commitSha a20c3fff（可解析）/recordedBy: Trae AI/L116-118 自陈 DiscussionThreadMapper @MapperScan 既存缺陷致 E4 不可得——内容诚实却**不被任何 Task 卡 evidenceManifests 引用**；卡只绑无连字符假 PASS 版。文件名一横之差，背书方向相反。且孤儿卡 L3 注释「requiredEvidenceLevel 为 E3」与卡面场景计划 E4 矛盾、L63-78 三条 proven 全为 denial 类、AGT-005 证据实为 MCP 调用端 requireToolAllowed 拒绝（条款 L253 要求**注册端** Catalog 校验，面错位）、notProven 点名的审计事件 `AGENT_ACCESS_DENIED` 在条款（实为 COMMON_INVALID_ARGUMENT/AGENT_*）/卡 crossCutting/主码三方**均不存在**＝三方悬空引用。
- **P-3 两张 Task 卡从未合规过（区别于 60 P-5 的漂移失效）**：013/014 自诞生即带 8 项 validator 拒绝态，与 60 域 GOV-007「曾可过、现漂移」形态不同——DEC-010「continuous implementation authorized」让位声明（014 L9）未同步满足卡面校验要件。
- **P-4 两个空心 IT 测试＝假 PASS 散文的直接来源**：IdentityLifecycleIT L469 `agt004_agentRegisteredWithMinimalScopes` 注释称「AC-P0B-AGT-004 高风险权限被拒绝」、正文仅注册最小 Scope 断言成功，**零高风险拒绝断言**；L481 `agt005_toolAllowlistCanBeUpdated` 称「未知 MCP Tool 名称被拒绝」、正文仅已知名成功路径＋替换，**零未知名拒绝断言**，且 L486 注释「由受控 Tool Catalog 校验」与实现（见 P-5）直接矛盾。假 PASS 卡 L48「IdentityIT covers agent flows」的出处即此二例＋IdentityIT L162-182；叠加两 IT 均 `@Testcontainers(disabledWithoutDocker = true)`（IdentityIT L40/LifecycleIT L56）而假 PASS 卡 L50 自认「no Docker in current environment」——**这些测试在取证环境根本未运行**，声称覆盖双重虚假。
- **P-5 AGT-005 注册端零 Catalog 校验**：SystemAgentController L133-154 PUT tool-allowlist → AgentManagementApplicationService L130-135 → AgentIdentity.replaceToolAllowlist L89-92（List.copyOf 直换）——全链零 McpToolCatalog 查询；Catalog 校验仅存在于 MCP 调用端（McpToolCatalog requireToolAllowed）与 register 高风险拒绝（L128-129）。条款 L253「Tool 必须来自已发布 Catalog」在**授权写入口**不设防，白名单可写入任意字符串，调用端拒绝只是事后兜底且错误码为 COMMON_INVALID_ARGUMENT 而非条款事件。
- **P-6 四个审计事件死名**：条款 L260-264 要求 AGENT_REGISTERED/AGENT_TOOL_ALLOWLIST_UPDATED/AGENT_DISABLED（+ENABLED 卡面 L36 四事件）；主码 Grep 全仓：AGENT_DISABLED/AGENT_ENABLED 仅存在于枚举定义与测试断言常量，**AGENT_REGISTERED/AGENT_TOOL_ALLOWLIST_UPDATED 零产者**——AgentManagementApplicationService 全文无 audit 调用；在用的仅 CLIENT_TOKEN_ISSUED/CLIENT_TOKEN_REJECTED（AuthenticationApplicationService L265/271/276/287）。AC-001「写审计」码面断裂。
- **P-7 幂等头三方漂移**：条款 L243 `idempotency: REQUIRED`＋OpenAPI `IdempotencyKey` 参数 `required: true`（aihub-v1.yaml L3681-3685）＋`/system/agents` POST L434 显式引用——实现 SystemAgentController L77/L102/L119/L136 四端点全部 `@RequestHeader(required = false)`。契约真、条款真、实现单方面弱化；缺失时幂等语义退化为重复注册（卡 L33「by displayName+agentType」业务键与条款请求键语义亦未对撞）。
- **P-8 AGT-001 Scope 三交集缺失**：registerAgent L86 `scopes` 直采入参——无「平台 Scope Catalog ∩ 授权者可委派 ∩ Agent 允许」交集校验，无高风险 Scope 登记入口默认拒绝（HIGH_RISK 拒绝面在授权判定层 AuthorizationService L115/L141，属运行时兜底非登记门禁，条款 L252/L254 要求登记即拒）。
- **P-9 AGT-003 码面闭环在位但测试零覆盖＋过滤器 fail-open 面**：正向链完整——AgentIdentity.disable L72-76 tokenVersion+1 → JwtTokenService claim 嵌入 L101/解析 L138-140 → **IdentityTokenRevocationChecker L55-58 对 AGENT 查库比对（`isDisabled() || claims.tokenVersion() < agent.tokenVersion()`，orElse(true) fail-closed）→ JwtAuthenticationFilter L91-96 每请求执行**；但：① 全测试面**无任何一例断言「禁用后既有 Agent access JWT 被拒」**（IdentityIT isRevoked 断言 L125/129/140/144 全在 USER 路径；agent 用例 L162-182 只测禁用后**新交换**被拒），AC-003「旧 JWT 立即失效」E2 级亦无据；② 过滤器 `revocationChecker.getIfAvailable()` 为 null 时直接放行（L91-96），吊销防线系于 identity 域单个 @Component 的装配存在性，非编译期/启动期强制。
- **P-10 E4 全域零达成与假 PASS 直接矛盾**：域级总盘 EVD-P0B-V01-V12 L149 notProven 明列 AGT-001~005 全部场景（同盘 V25 verify.sh L1312-1318 仅端点存在性探测：空 body 得 401/400/422）；卡场景计划五 E4 的绑定证据只有 E2 假 PASS 卡——五 AC 现势验收态应为 **notProven**，总盘与假 PASS 卡并存即证据链自相矛盾。

**合规正向面**：① 一次性凭据链合规：registerAgent L74 generateCredential（32B Base64URL）→ L84 仅 passwordHasher.hash 落库 → L94 明文只随创建响应一次性返回（INV 防落盘面达成，AC-001 半句）；② toolAllowlist 注册默认空 L87＝fail-closed 白名单起点；③ 凭据交换防枚举：AuthenticationApplicationService L263-279 未知 client 与错口令统一 AUTH_INVALID_CREDENTIALS＋REJECTED 审计（条款 L261 AGENT_NOT_FOUND 防枚举语义以更保守的统一拒绝达成）；④ Agent 无 refresh token：L289-290 只发 access（条款 L255，IdentityIT L170 断言在档）；⑤ 签发透传主体现势 tokenVersion（L281-282）；⑥ disable→tokenVersion+1＋revokeAllByPrincipal（Service L119-124）；⑦ AGENT 吊销查库 orElse(true) fail-closed（孤儿 EVD 卡所述一致）；⑧ 权限门禁在位：AGENT_REGISTER/AGENT_AUTHORIZE（SystemAgentController L74/L133 区域）；⑨ USER 侧禁用即时吊销有实测试（IdentityIT L121-133 disableUserRevokesAccessImmediately，isRevoked 双态断言）——同机制 AGENT 路径仅缺断言。

**偏差与 unproven 登记**：① 全部结论静态取证＋validator/git 实跑；`./mvnw verify` 本轮未重跑，各 IT 现势 PASS unproven（且 disabledWithoutDocker 环境本就 SKIP）；② IdentityIT/IdentityLifecycleIT 非 AGT 场景未逐行审，仅 Grep+定点读；③ 孤儿卡与假 PASS 卡的落款时间先后（2026-07-11 vs 孤儿卡 recordedAt）未做 git log 谱系核证，「谁抄谁」方向 unproven；④ AgentEntity token_version 列的 Flyway 迁移版本未溯源（V1/V2 推定，未逐一读）；⑤ OpenAPI `/system/agents` 各端点 x-error-codes 与条款错误码全表对撞未穷尽（仅幂等头一钉）；⑥ 涉条款/catalog/卡/EVD/契约/产品码/测试七类禁改面**只登记不改动**（DEC-009；本会话对 validator 仅只读实跑）。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① P-1/P-10 **两张假 PASS EVD 作废重出**：EVD-P0BR013/014-001（无连字符版）status 改 notProven 或删除，按实况以 E3/E4 重取证；② P-2 双卡撞名收口：统一命名规范并显式 supersede 关系；③ P-3 013/014 卡面 8 issues 修复（全 SHA baseCommit 重绑、approvedBy/At、DEC-010 入 decisions、补 -CheckChangedPaths+Compose 命令、evidenceManifests 入 allowedPaths 或移出）；④ P-4 空心测试补真断言：agt004 登记入口授予高风险被拒、agt005 未知 Tool 名写入被拒——依赖 ⑤；⑤ P-5 注册端接线 Catalog：updateToolAllowlist 增加 McpToolCatalog 存在性+发布态校验（错误码对齐条款）；⑥ P-6 三审计事件产者接线（AGENT_REGISTERED/AGENT_TOOL_ALLOWLIST_UPDATED/AGENT_DISABLED 走共享 AuditService）；⑦ P-7 幂等头 required=true 对齐契约（或条款/契约显式降格裁决）；⑧ P-8 登记入口 Scope 三交集校验；⑨ P-9 补「禁用后既有 Agent JWT 被拒」IT 断言＋过滤器 TokenRevocationChecker 改必需依赖（缺 Bean 启动失败）；⑩ 校验器缺口挂靠清单追加两例：EVD governance 四项自声称 PASS 无任何工具复核（实跑即伪）、proven[].scenarioId 非标准值「implementation-verified」未被拒——承 ㊼②/㊽③④/55⑧/56⑪/57⑪/58⑬/59⑪/60⑨。**下一节候选域**：AC-P0B-ENG×6 工程门禁残族（EVD-P0B-ENG-001/V01-V12 证据面在档）。

→ 本项与 ①—61 一并不在本轮 AI 自决实施范围内。

### 62. AC-P0B-ENG×6 工程门禁族（2026-10-13，取证基准 HEAD a022796）

**立项查重**：plan 全文 Grep `AC-P0B-ENG` 仅 L1120 顺带提及（error-catalog 挂 TASK-P0BR-001），六 AC 从未成节；60 节 P-4 已钉 breaking diff 的 ci.yml 实况（main 阻断/非 main 警告），本节承继引用不重复审。

**取证基准**：HEAD a022796；实跑 validator×4（TASK-P0BR-001→EXIT=1×8、044→EXIT=1×11、047→EXIT=1×1、062→EXIT=1×1，后两者均仅 baseCommit 漂移）；git cat-file：7dc22d44→fatal（幽灵复证）、54f20f7/941ab67→commit；其余为静态读＋Grep/Glob 现势钉。

**权威链定装**：exit-catalog L26-31（六 AC 定义）→ REQ-COM-001（platform-services L15-41，追踪 ENG-001/002）→ 卡覆盖：ENG-002/003→TASK-P0BR-001；ENG-004/005→TASK-P0BR-044（另 047 重验 ENG-004|E3）；ENG-006→047（E4）＋062（E4）；**ENG-001 零卡**，仅有 taskId:null 的游离证据 EVD-P0B-ENG-001 → 证据五卡：EVD-P0BR001-001 / EVD-P0BR044-001（均无连字符 2026-07 批量代）vs EVD-P0BR-047-001 / EVD-P0BR-062-001（连字符 2026-09 逐个代）＋ EVD-P0B-ENG-001 → 落地面：contracts/openapi（135 枚举全 implemented-unverified）、Flyway V1–V44（43 文件、V31 缺号）、runbooks/compose.md L110/L130、verify.ps1 L171/L178。

**差集矩阵**：
- **P-1 假 PASS 批量代第三、四例**：EVD-P0BR001-001（governance 四项自称 PASS vs 实跑 8 issues；且卡特有缺陷「Backend scope requires a Maven wrapper verify command」「OpenAPI scope requires a blocking Redocly breaking-change diff」——即卡连声称要验的门禁命令自身都未列入 requiredValidationCommands）；EVD-P0BR044-001（四项自称 PASS vs 实跑 11 issues：baseCommit 短 SHA 0b152f3、approvedBy/At null、DEC-010 未声明、**scenarioEvidencePlan:[] 空**（两 AC 各 0 映射）、缺 -CheckChangedPaths、Frontend scope 缺 pnpm test、Compose scope 缺 docker compose config 与 verify 脚本）。
- **P-2 幽灵提交四卡连生＋批量指纹**：commitSha `7dc22d44…` 在 EVD-P0BR001/013/014/044 四卡同现（git cat-file fatal＝不存在于任何历史），四卡同属无连字符命名代、044 observedAt 2026-07-11T21:00 与 013/014 同批——高度疑似同会话批量生成且 governance 声称零复核；连字符代（047/062，2026-09-17/09-20）全 SHA、真 dirtyWorktree、EXPECTED_FAIL——两代形态断层本身即证据链质量的时间序列证词。
- **P-3 ENG-002 声称被现势全量证伪**：EVD-P0BR001-001 L49「105 operations **all** x-implementation-status=**implemented**」；实测 aihub-v1.yaml 枚举值 135 处**全部为 `implemented-unverified`**、纯 `implemented` **0 处**、`planned` 0 处（137 总命中中 2 处为定义/其他行，未穷尽枚举，标注见 unproven②）；aihub-agent-v1.yaml 9 处亦全 implemented-unverified——「all implemented」不是漂移而是无中生有。
- **P-4 ENG-003 双卡互斥**：EVD-P0BR001-001 L51-52 proven（「CI uses --fail-on-breaking for PRs to main」）vs EVD-P0B-ENG-001 L68-69 notProven（「breaking diff 工具未配置」）——同一 AC 两卡定性相反；60 P-4 已裁 ci.yml 实况为 main 阻断/非 main 仅警告，001 卡连自身 requiredValidationCommands 都不含 breaking diff 命令（P-1），其 proven 声称无执行载体。
- **P-5 ENG-004/005 证据内容错位＋断言陈旧**：EVD-P0BR044-001 proven ENG-005 断言仅为「Migration chain V1-V23 forward-only、无已应用迁移被改」——条款义是「从上一支持版本升级、存量数据不丢失」（E3 升级场景），静态 forward-only 检查不构成升级证据，**答非所问**且 notProven:[] 空（自称无缺口）；proven ENG-004 断言「compose.md V04 updated to V1-V23 matching actual」——现势 runbook L110/L130 仍写 V1-V23 而 Flyway 实际 43 文件至 V44：卡目标在取证当日成立、现势已两度漂移（047 时代 V25→HEAD V44），无任何后续卡承接同步（044 卡 §1 目标本身即「Runbook 与迁移同步」，其失效无人负责）。
- **P-6 门禁鉴别力自降**：verify.ps1 L171 注释「V1-V12 全部应用」、L178 断言 `[int]$count -lt 12`——固定下限面对 43 迁移现状恒真通过，「执行 V1 到最新」（ENG-004 条款义）退化为「至少 12 条」；verify.sh L838 同构计数。E3 门禁的判别力随迁移增长单调衰减，无告警机制。
- **P-7 ENG-001 零卡游离证据**：EVD-P0B-ENG-001 taskId:null（L6 自注「无独立 Task Card」）、commitSha:null、status PASS/E1、L47 记 213 单测＋26 IT skip——基于 2026-07-02 快照；对照 047 卡 484 单测、062 卡 520 项：构建面已翻倍，该 PASS 卡既无授权载体（违反 DEC-009「无证之卡」新形态——校验器不复核 EVD↔卡双向绑定，taskId:null 竟然通过）又现势失效。
- **P-8 校验器缺口追加两例**：① EVD taskId:null/commitSha:null 无校验（P-7）；② coverage proven 条目 scenarioId 非标值「AC-P0B-ENG-004-E3」（044 卡 L51，自造后缀逃过场景对账）——与 61 的「implementation-verified」同族；governance 四项自声称零复核（承 61⑩）在 001/044 双双兑现为实跑打脸。
- **P-9 Flyway V31 缺号无登记**：V1–V44 序列中 V31 无文件（43 迁移），Flyway 默认容忍缺号，但 forward-only 谱系完整性属 ENG-004 域，缺号成因/是否曾回滚删除无任何文档登记。

**合规正向面**：① 047/062 卡面全合规（全 SHA、approvedBy:User+approvedAt、六命令含 -CheckChangedPaths/pnpm/compose config/verify.ps1），实跑各仅 1 issue＝baseCommit 漂移孤证——证明 001/044 的 8/11 issues 是真卡面缺陷而非校验器过严，合规路径可行且已被同仓库示范；② EVD-P0BR-062-001 诚实范本：completionValidation:EXPECTED_FAIL、dirtyWorktree:true 九路径如实列、组合命令 exitCode:1 不改写、三条具体 notProven（组织路径仅 E2、删仓 TODO、Saga 属 P2）、双判别力对照（缺陷态 9/16、7/13 vs 修复态 18/18）；③ ENG-006 达证强度最高：D1-D4 compose config/printenv/日志痕迹＋「直连 Gitea API 读回 asset.yaml」不依赖平台自述、Noop 不可伪造（062 L125-126）；047 的 MapperScanCoverageTest 负例对照同理；④ @MapperScan 双星修复已提交 HEAD（AihubApplication L34），2026-07 批量卡「E4 不可得/需 Docker」的 notProven 理由对 2026-09 后的 HEAD 已陈化——E3/E4 重达使 P-1..P-8 各缺口全部具备整改取证条件；⑤ exit-catalog 六 AC 定义干净、work-breakdown 卡覆盖可溯源（ENG-001 无卡可被正向检出，本节 P-7 即由此产生）。

**偏差与 unproven 登记**：① 全部结论为 validator/git 实跑＋静态现势钉；001/044 各内层命令（mvnw、compose、Redocly）未逐条重跑，「假 PASS」定性依据＝governance 声称 vs validator 实跑 vs 契约/Flyway 现势三重对撞，已足；② aihub-v1 的 137 总命中与 135 枚举差 2 处未穷尽定值（疑为 schema 定义/示例行），不影响「implemented 纯值 0 处」主钉；③ 幽灵 SHA 7dc22d44 的产生机器/操作不可由现存取证手段判定；④ 批量代四卡 recordedBy 均为 AI、时间戳集中，但「同会话生成」是推断非实证；⑤ 044 L52「schema version 23, no migration necessary」原始日志未复核；⑥ V31 缺号成因无登记亦无法现势判定；⑦ 涉契约/卡/EVD/runbook/验证脚本禁改面**只登记不改动**（DEC-009，本会话对 validator 仅只读实跑）。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① P-1/P-2 假 PASS 两卡（EVD-P0BR001-001、EVD-P0BR044-001）作废重出，幽灵 SHA 四卡（含 61 的 013/014）统一挂勘误登记；② P-3 ENG-002 按实况重出：契约 135 枚举全 implemented-unverified 是**当前真值**，lint 完备性证据应引用真值而非「all implemented」；③ P-4 ENG-003 统一裁定：以 060 节 ci.yml 实况（main 阻断）为准绳，绑定含 Redocly breaking diff 命令的真实验证载体；④ P-5/④ runbook V1-V23 同步至 V44 实数＋ENG-005 补真实升级场景测试（上一支持版本快照→升级→存量断言），044 卡目标重开启并重绑 baseCommit；⑤ P-6 verify.ps1 L178/verify.sh 迁移断言改「== 目录内最大已应用版本」动态上界；⑥ P-7 ENG-001 正式立项取证：空工作区（git clean 环境）`./mvnw verify` 全绿＋锁文件钉，替代 2026-07-02 游离 PASS 卡；⑦ P-8 校验器缺口清单追加：EVD taskId/commitSha 非空与↔卡双向绑定复核、scenarioId 字面枚举校验、governance 声称执行复核（承 61⑩/55⑧/56⑪/57⑪/58⑬/59⑪/60⑨）；⑧ P-9 V31 缺号在 migration 谱系文档登记成因。**下一节候选域**：AC-P0B-OBS×5 可观测族（runbook L151 映射 V11/V21，域级证据 EVD-P0B-V01-V12 在档）或 AC-P0B-CD 交付族。

→ 本项与 ①—62 一并不在本轮 AI 自决实施范围内。

### 63. AC-P0B-AUTH×9 组织、Team 与授权验收族（整域差集：条款 OPEN 却双卡授权、六卡幽灵 SHA 之五/六、一张「70 条全 PASS」总声称卡与同域三张诚实卡互斥）

**立项查重**：62 节末候选「AC-P0B-OBS×5」经 Grep 证伪——plan ㊸ 节（L2185-2214，REQ-OBS-001 观测整域 O-1..O-8）已整域覆盖，排除、不计权（承 60→61 先例不回改 62 前文）。本轮对 P0B 十一族 70 AC（exit-catalog 行号地图：ENG L26-31/IAM L37-48/AGT L54-58/AUTH L64-72/TAX L78-83/CFG L89-91/IDM L92-94/JOB L95-99/AUD L105-109/NOT L115-119/OBS L125-129/UI L135-140）逐族普查 plan 既节：ENG→62、AGT→61、OBS→㊸、NOT→㉘（L1356）、CFG→㉝（L1848）、IDM→㉖㉞、IAM→㉚㉛、TAX→㉜（L1799）、JOB→㉙（L1460）、AUD→㉖㉗（L1209/L1282）、UI→㊵ 局部＋㉔ 矛盾登记——**唯一零整域真缺口收敛为 AC-P0B-AUTH×9**：plan 全文 `AC-P0B-AUTH` 仅 11 处命中且全为半句/表行级（L2553 T-3、L2597 T-14、L1818 等），无节。本域 60 T-3（组织管理员最后 Owner 保护缺失）为邻域既有结论，按查重先例不重复计权，本节仅登记其「AUTH 整域未成节」的旁证地位。

**取证基准（E1 实跑，本轮）**：`validate-task-card` TASK-P0BR-016 → **EXIT=1×9**、TASK-P0BR-017 → **EXIT=1×9**、TASK-P0BR-045 → **EXIT=1×11**；`git cat-file -t 7dc22d44…` → **fatal（不存在）**；`9742867…`/`1d058fdf…` → commit（017/053 卡活 SHA）；HEAD=`a022796`。幽灵提交 7dc22d44 家族由 62 的四卡（001/013/014/044）扩至**六卡**：EVD-P0BR016-001 L26、EVD-P0BR045-001 L26 同源同 SHA 同 `observedAt 2026-07-11T21:00` 模板。

**权威链定装**：exit-catalog L64-72 九条全 MUST、`E4`（003/007 为 E3/E4）→ 条款包 `p0b-identity-authorization.md`：REQ-ORG-001 L273、REQ-ORG-002 L308、REQ-AUTH-001 L334、REQ-AUTH-002 L380、**REQ-AUTH-003 L411（`status: OPEN`、`blockedBy: [TASK-GOV-002]` L414/L417，文件头 L3/L6 重申）** → work-breakdown L111（016 承接 AUTH-001/002/004/008/009）、L112（017 承接 AUTH-003/005..007）、L70/L74/L80（049/053/064 局部）→ EVD 七卡：EVD-P0BR016-001、EVD-P0BR-017-001、EVD-P0BR045-001、EVD-P0BR-053-001、EVD-P0BR-049-001、EVD-P0B-V01-V12、EVD-P0BR-064-001 → 实现面 `com.aihub.organization`/`com.aihub.authorization`。

**差集矩阵**：
- **P-1 条款 OPEN 与双卡授权的结构性矛盾**：016 卡 L15、017 卡 L15 均引用 REQ-AUTH-003，两卡 `status: READY`＋`implementationAuthorized: true`，而该条款 `OPEN`/`blockedBy: TASK-GOV-002`——实跑 validator 逐卡报「Requirement is not READY: REQ-AUTH-003」，即 DEC-009「只引用 READY 条款」门禁对存量卡从未生效；且 REQ-AUTH-003 追踪行 L441 自映射 `AC-P0B-AUTH-004..008`，006/007 恰好只能经此 OPEN 条款承接——**九条中两条的规范路径被阻塞且无绕行登记**。
- **P-2 假 PASS 批量代第五、六例**：EVD-P0BR016-001（status PASS、governance 四项含 `-CheckCompletion` 全自称 PASS、幽灵 SHA、`scenarios: []` 空、proven 仅一条自造 scenarioId `implementation-verified` L47、`evidenceLevel: E2` vs 卡要求 E4、approvedBy null、全 artifact 空）；EVD-P0BR045-001 同型且更甚（见 P-3）。016 卡 git 谱系 `4bef78c stage-3`、045 卡 `07c3c2b stage-6`——与 62 P-2 批量代指纹（无连字符文件名＋0b152f3 短 SHA＋DEC-010＋2026-07 日期）完全同代。
- **P-3 「70 条全 PASS」总声称卡**：045 卡目标（L65）「干净 Compose 环境执行全部 70 条 MUST、不允许 SKIP」，EVD-P0BR045-001 以 **11 条区间记法**（`AC-P0B-AUTH-001~009` L57、IAM-001~012、TAX-001~006、JOB-001~005、AUD/NOT/OBS/UI/CFG/IDM 各一行）声称全谱系 `E4: … PASS`、`notProven: []`、`evidenceLevel: E1+E3+E4`。区间记法 `~` 逃过 scenarioId 对账（62 P-8 校验器缺口追加**第二种逃账记法**：自造后缀之外新增区间通配）。
- **P-4 总声称与同域诚实卡互斥**：EVD-P0B-V01-V12 L150 明文 notProven「AC-P0B-AUTH-001~002, 004~009 全部场景」；EVD-P0BR-017-001 L3 自注「requiredEvidenceLevel 为 E4，本次仅取得 E2；不得据此声明完成」、L81-82 AUTH-003/005 真实下推与拒绝审计 notProven；EVD-P0BR-053-001（2026-09-17，活 SHA）才为 AUTH-003/004 取得首批真实 E4 且仍 `PARTIAL`。三条独立证据链均与 045（2026-07-11）「全 PASS」不可同真——045 为现势虚假陈述而非漂移。
- **P-5 AUTH-002 的双脚本证据空洞**：`AC-P0B-AUTH-002`（两组织不串权）主战场是 verify.sh V14（L1021/L1135「多组织权限主体搜索无泄漏」），而 verify.ps1 只覆盖 V01–V11（053 notProven L140-141 自认不对等）；053 notProven L125-131 又记录 verify.sh V12–V21 容器实跑 **7 项 FAIL**（V14 在列，schema 漂移既存缺陷）。045 引用的「V14 PASS」系 2026-07 快照、已被 2026-09 实跑证伪失效。**现势：AUTH-002 无任何脚本级 PASS 证据，016 卡所引 OrganizationIT 在本机 Testcontainers 恒 Skipped。**
- **P-6 AUTH-006 名义覆盖错位**：条款义含「拒绝自提权」（REQ-AUTH-003 L430）；实现面 grep `escalat` 于 `src/main` **零命中**，`RoleManagementApplicationService` 仅有 builtin 不可改删（L99-101/L129-131）与 `ROLE_IN_USE`（L134）；同名测试 `AuthorizationLifecycleIT.auth006_builtinRoleCannotBeModifiedOrDeleted`（L173）实测 builtin 保护而非「管理员给自己绑定更高角色」路径——**自提权守卫无实现证据、且该 IT 恒 Skipped、无 EVD 场景级卡**。
- **P-7 AUTH-009 Owner 责任转移零实现证据**：条款「资产 Owner 责任转移规则生效」（exit-catalog L72）；`transferOwner|OwnerTransfer|reassign|OWNER_TRANSFER` 于 `src/main/java/com/aihub` **零命中**，`TeamApplicationService.removeMember` L152-159 只删成员不触碰资产 Owner——离队后 Team 持有 Owner 的资产悬空。016 卡 `scenarioEvidencePlan` L30 明文承诺该场景 E4，证据面为空。
- **P-8 017 卡 evidenceManifests 引用悬空**：卡 L57 引用 `…/EVD-P0BR017-001.yaml`（无连字符），磁盘实存文件为 `EVD-P0BR-017-001.yaml`（有连字符，Glob 定装）——**按卡面路径证据文件不存在**；validator 只查 allowedPaths 包含关系、不查存在性（62 P-8 缺口追加第三种）。016 卡同位引用则匹配（同为无连字符代命名），命名代断层在本域表现为 016（无连字符）/017（卡无连字符、文件有连字符）互异。
- **P-9 卡面九项治理缺陷双卡同构**：016/017 各有短 SHA `0b152f3`、approvedBy/At null、requiredValidationCommands 缺 `-CheckChangedPaths`、Compose 域缺 `docker compose config` 与 verify 脚本、DEC-010 未入 decisions 列表、evidenceManifests 不在 allowedPaths——九项 issue 双卡逐条同构，与 62 P-1（001/044 卡 8-11 项）同一批量生成形态。

**合规正向面**：① EVD-P0BR-017-001 为 2026-09 诚实代范本：PARTIAL 自注、exitCode:1 不改写、三条具体 notProven、skippedTests 如实、并如实记录既存 @MapperScan 缺陷（L120-122）；② EVD-P0BR-053-001 为本域最强证据卡：EXPECTED_FAIL、NEGCTRL 四组负例对照（旧断言假阳性被实证 L93-97）、V05b 前置条件用例、auditEvents/databaseQueries 实字段、活 SHA＋dirtyWorktree 如实申报；③ EVD-P0B-V01-V12 的 AUTH notProven 清单与现势高度自洽（诚实的缺口目录）；④ 实现面 builtin 保护、ROLE_IN_USE 冲突、防枚举 `authorizeResourceOrNotFound`→NOT_FOUND（017 proven L74-79，单测 10+24 项）静态成立；⑤ 016/017/053/049/064 卡间承接关系与 work-breakdown 依赖列一致，谱系无断链。

**偏差与 unproven**：① AUTH-001/004/008 的 E4 仅 053（004）与 049/064（局部）在档，001/008 全域无场景级证据（016 卡自述测试无 artifact 佐证）——unproven；② AUTH-007 重复 Binding/ACL 稳定冲突未逐一取证——unproven；③ 本轮未起 Docker 实跑任何旅程，全部行为面结论均为静态＋历史卡对撞；④ P-6/P-7 的零命中 grep 覆盖 `src/main/java/com/aihub` 全包但命名变体未穷尽，定性保留「未寻获证据」而非「确证不存在」；⑤ validator 实跑仅证卡面不合规，不证卡内断言全伪（诚实卡同样报 baseCommit 漂移类 issue）。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① EVD-P0BR016-001、EVD-P0BR045-001 两假 PASS 卡作废重出，幽灵 SHA 六卡（001/013/014/044/016/045）统一挂勘误登记；② REQ-AUTH-003 的 OPEN 与 016/017 引用矛盾由人类裁决：解阻 TASK-GOV-002 或改卡移除该引用并将 AUTH-006/007 显式挂起；③ AUTH-002 补真实 E4：V14 schema 漂移修复（053 已另行上报项）落卡后在容器重跑 verify.sh V12–V21；④ AUTH-006 自提权守卫与 AUTH-009 Owner 责任转移：实现缺口经人类裁决立项（或修条款），不得以 builtin 保护冒充；⑤ 017 卡 L57 悬空路径勘误；⑥ 校验器追加清单（承 62⑦）：scenarioId 区间记法 `~` 与自造值拒绝、EVD `evidenceLevel` ↔ 卡 `requiredEvidenceLevel` 对账、evidenceManifests 存在性校验、`-CheckCompletion: PASS` 自声称与 E2 证据互斥检测。**下一节候选域**：AC-DST-AI/AC-DST-AIW×10（㊿ 已立项面续查证据尾量）或 AC-P0B-NOT×4（㉘ 差集已立、补 E4 取证尾量）。

→ 本项与 ①—63 一并不在本轮 AI 自决实施范围内。

### 64. AC-P0B-UI×6 公共管理端族（p0b-exit-catalog L135-140 全 MUST）：**AC-ID×证据卡全矩阵 ↔ work-breakdown 双代承接 ↔ 老代批量卡 040..043 ↔ 041 双代并存 ↔ 新代诚实卡 048/050/055/065/066 六方差集**：四卡 validator 实跑全灭、幽灵 SHA `7dc22d44` 家族扩至十卡、批量代三卡同五分钟执行窗口时间重叠新指纹、「E2 冒充 E4＋verify.sh 拒绝断言冒充 AC 出口」双族同现；矩阵面收口——六 AC 中五个有新代真卡覆盖，**AC-P0B-UI-001 现势无任何有效证据卡＝本域唯一真 E4 空档**

**立项与查重过程（先行声明，避免重复计权）**：查重口径覆盖「节标题地图＋前文证伪声明」两类记录（承 OBS 误提教训）。① 63 节末预告的两候选先证伪后再定：**AC-DST-AI×5/AC-DST-AIW×6 在 61 节立项段早已明文证伪**（L2747：「已被 ㊿ 实质覆盖（W-1/W-2/W-3 均在 TASK-P4-008/009/010 权威链内），不重复立项」），本节 Grep 复核确认 AC 级引用仅存于 ㊿ 节内（L2415-2416）与候选预告行、无增量场景 → 不立项；AC-P0B-NOT×4 → ㉘（L1356）已整域成节 → 不重复；② dataset 全 41 AC（`dataset-agent-exit-catalog.md` L20-85，TAX×4/DETAIL×4/DISC×5/FAV×4/SUB×3/PRE×4/CLI×6/AI×5/AIW×6）普查收口：TAX→52、DETAIL→53、DISC→51、FAV/SUB→54、PRE→59、CLI→㊽、AI/AIW→㊿，零尾量；③ p0b-exit-catalog 的 **UI×6 为唯一「AC 维证据矩阵缺失」真缺口**：㊵ 节做的是 REQ-AST-009/REQ-UI-001 **条款本体面**五方对撞（其合规面与 W-1..W-9 均按条款条目组织，未做 AC-ID×证据卡绑定矩阵）；60 节 P-8 取证债清单**明文登记「AC-P0B-UI-001..006 无逐 AC 证据卡」**（L2741）；62/63 普查仅记「UI→㊵ 局部」→ 本节以 AC-ID×证据卡矩阵面立项。划界：㊵ 已计权的产品码缺陷（W-1..W-9）本节只互引不重复；新素材仅来自卡谱系、治理对撞与矩阵差集。
**权威锚点**：`p0b-exit-catalog.md` L135-140（001 登录跳转＋access 仅内存＋refresh Cookie 轮换 E4；002 权限差分 UI E4；003 五态 E4；004 受控选择 E4；005 i18nKey/locale E4；006 组件测试＋E2E E2/E4；全 MUST）与 L145-148（上界声明：V05 仅覆盖 IAM-002 一小部分、401/403 拒绝断言不能充当 AC 出口）；`p0b-platform-services.md` L400（REQ-UI-001 Acceptance＝`AC-P0B-UI-001..006`）；`p0b-identity-authorization.md` L233（IAM-012 交叉引用 UI-002..006）；`work-breakdown.md` 老代 L143-146（040→UI-001/002、041→UI-003/005、042→UI-004、043→UI-006）与新代 L69/71/72/76/81/82（048/050/051/055/065/066 局部承接）。
**对撞面**（全部实读/实跑）：TASK-P0BR-040/041/042/043.md 四卡全文＋validator×4 实跑（040/041/043 各 11 项、042 11 项含「Requirement is not READY: REQ-AUTH-003」，全 EXIT=1）；批量代证据 EVD-P0BR040/041/042/043-001.yaml（无连字符）四卡全文；041 双代并存：EVD-P0BR-041-001.yaml（有连字符诚实代）全文对撞；新代 EVD-P0BR-048/055/065/066-001.yaml 四卡全文（050 ㊵ 已证）；git 实测：`git cat-file -t 7dc22d44…` → **fatal: could not get object info**、`f844aa58…`（041 诚实代卡）→ commit；文件实数：`frontend/e2e` Glob **25 个 spec**、`frontend/src` `*.test.*` Glob **50 个文件**。

**已核验合规（矩阵面收口新结论）**：
- **UI-002**：EVD-P0BR-065 E4 proven（真实导航逐项可见性过滤＋无 asset:manage 主体直连 /review 被 RouteGuard 403＋幽灵码 asset:write 修复前后对照，L98-103/L138-146）→ 现势真卡覆盖，040 假卡作废不产生缺口。
- **UI-003**：EVD-P0BR-050（㊵ 已证：五态注入 HTTP 边界故障实证）＋065（审查页诚实空态/失败态 alert+重试）＋066（Tab 门控/状态回显）→ 覆盖。
- **UI-005**：EVD-P0BR-065 E4 proven（review-workflow 用例 5-6 locale 切换中文残留归零＋全量 36 跑内 i18n.spec.ts 五条复验，L148-156）→ 041 双代卡自证 E4 未闭环（诚实代 notProven「需 E4 Playwright」L72）的情况下，**UI-005 的 E4 已由 065 他途落地**。
- **UI-006**：050（三主体 Chromium 死链回归）＋065（Playwright 全量 36/36 真栈）＋066（97 passed/1 skipped，唯一 skip 为 baseCommit 前既存 fixme 且拒绝删除断言）→ 覆盖。
- **041 双代并存样本（同卡异代双证据文件共五对：014/017/023/024/041，Glob 实测；041 为 UI 族内样本，其余四对属他域，登记为跨域观察项）**：诚实代 status PARTIAL＋首行注释自限「不得据此声明完成」＋completionValidation 自报 EXPECTED_FAIL＋两条具体 notProven＋真实 SHA＋artifact 绑定 `e2e/i18n.spec.ts`——与 63 合规面①② 同族的 2026-09 诚实代范本；但**旧假卡未被勘误标注，两文件互斥并存**。
- **新代卡谱系诚实面成立**：065 负向对撞取证（audit_log AUTH_TOKEN_REPLAY_REJECTED 历史 59 行止于竞态修复前）、066 dirtyPaths 将本迭代 plan 与既存脏文件显式登记 preExistingDirtyPaths、recordedBy AxeXie、两卡 notProven/limitations 具体到根因提交号（d8506bf、5ccbf47）。

**缺陷面**：
- **P-1 UI 族四卡 validator 全灭，批量代「governance 四项全 PASS」自称被直接证伪**：TASK-P0BR-040..043 同模板病灶全中（baseCommit 短 SHA `0b152f3`、approvedBy/At 空、scenarioEvidencePlan []、DEC-010 未声明、缺 -CheckChangedPaths/docker compose config/verify 脚本三类命令）；四张批量代证据卡 L14-22 自称 taskValidation/completionValidation PASS，与本节实跑 EXIT=1 矛盾——治理自称系模板复制（与 63 P-1 同构，UI 域为最大单一现场：一族四卡）。
- **P-2 幽灵 SHA 家族由六扩至十**：`7dc22d44c19e823edab0b03ca88e22d1bc5382a4` 复测 fatal；63 已登记六卡（001/013/014/044/016/045）＋本节新增 040/041/042/043 全部引用同一 SHA，且 observedAt 齐刷 2026-07-11T21:00、Ubuntu `/home/axe/…` 环境、recordedBy AI、artifacts 六字段全空。
- **P-3 时间重叠指纹（本域新证）**：批量卡 040/041/042 的 execution 窗口**完全相同**（startedAt 2026-07-07T23:35 → finishedAt 23:40）——三个不同任务切片不可能在同一五分钟窗口各自完成 lint+typecheck+build+test 全链；043 同代同 SHA 同风格。批量代伪造不止 SHA，连执行时间戳都是复制的。
- **P-4 双冒充链同现（61 拒绝断言家族＋62/63 级别冒充家族）**：四卡 `evidenceLevel: E2` 对 exit-catalog MUST 级 E4（043 卡面 E2/E4 亦靠 environmentDifferences 列 verify.sh V05/V07/V19 冒充 E4 出口，而 L145-148 明文 V05 仅覆盖 IAM-002 一小部分、拒绝断言不能充当 AC 出口）；proven 条目全为静态文件断言（RouteGuard/tokenHolder 行号、QueryBoundary 五态「在位」、ControlledSelect 调用点存在）；043 `notProven: []` 空。
- **P-5 计数声称与现势严重脱节**：043 声称「4 Playwright E2E specs（admin/auth/i18n/permission）/15 Vitest files/51 tests」；实测 e2e 25 个 spec（所称四件俱在）、src 50 个测试文件——声称属某个历史快照且无 artifact 对应，既不能证明当时通过、更与「本卡已验收」的 status: PASS 互斥。
- **P-6 055 AC 错绑**：EVD-P0BR-055-001 绑 `AC-P0B-UI-004`（受控选择器从受治理字典/标签取选项），卡内证据却全部是后端 `asset_model` 列名反引号 SQL 转义修复（precision 裸列名双向对照、真 E4）——内容与 AC 错位；UI-004 的浏览器受控选择实证实际由 048（PARTIAL）承担。
- **P-7 AC-P0B-UI-001＝本域唯一真 E4 空档**：exit-catalog L135「完整登录跳转＋access 仅内存＋refresh Cookie 轮换」唯一绑定卡是 040（假 PASS）；新代 048/050/051/055/065/066 无一绑 UI-001（065 自 UI-002 起）；㊵ 的 tokenHolder/AuthProvider 合规是静态核查（E2 以下、非 AC 级证据）。**六 AC 矩阵终值：002/003/005/006 新代覆盖，004 部分（048 闭环被外部缺陷阻断、notProven 如实），001 零有效卡——60 P-8 该条取证债由「六 AC 全无逐卡」修正为「五有主、UI-001 无主、UI-004 半额」。**
- **P-8 跨节回响与同域互斥**：042 引用 REQ-AUTH-003（OPEN/blockedBy TASK-GOV-002）→ 63 P-1 授权矛盾在 UI 族的第二现场（validator 实跑点名）；041 假卡声称 UI-003/005 全 proven vs 041 诚实卡同两 AC notProven/PARTIAL——同卡双文件互斥无互注，系 63 P-4「总声称 vs 诚实卡互斥」的 UI 族对偶形态。

**偏差与 unproven**：① 本节未起 Compose/浏览器复跑任何 UI 旅程，行为结论全部来自卡↔卡、卡↔文件静态对撞与历史卡内记录；② Glob 是文件数不是 test 数，P-5 仅证「声称与现势脱节」，不复证 test 计数当时真伪；③ 065/066 status PASS 但 dirtyWorktree=true、review.accepted=false，按 DEC-009 只登记「卡质诚实、待验证权威接受」，本节不将其升格为 AC VERIFIED；④ 048/055 的 2026-09-17 轮 Compose 结果不可由本轮（未起栈）复现，其后 master 侧变更是否引入回归 unproven；⑤ 新代 067..078 各卡与 UI 域的 slice 交集未普查，登记为尾量。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① EVD-P0BR040/041/042/043-001 四假 PASS 卡作废重出，幽灵 SHA 十卡（001/013/014/044/016/045/040/041/042/043）统一勘误登记；② **AC-P0B-UI-001 立项补卡**：登录跳转＋access 内存＋refresh Cookie 轮换浏览器链 E4（含失败/过期路径），承 62⑥ 旅程类要求；③ UI-004 闭环：048 notProven 所指两独立整改项（JdbcJobRepository.insert 16 列/15 占位符致 POST /assets 500；引导管理员 ADMIN_SCOPES 粗粒度与前端路由守卫互斥）经人类立项落卡后正式闭合；④ 055 错绑裁决：改卡面 scenarios 为其真实内容所属 REQ，或补 UI-004 浏览器侧验收；⑤ 双代并存五对（014/017/023/024/041）：各旧假卡加「已作废，见 EVD-P0BR-0NN-001」勘误注记，不得静默删除（UI 族仅 041，余四对本节未展开卡面对撞，随勘误一并处理）；⑥ 校验器追加清单（承 62⑦/63⑥）：evidenceManifests 命名↔目录实存一致性（`EVD-P0BR041` vs `EVD-P0BR-041` 双卡并存不被检出）、跨卡 execution 时间窗口重叠检测、proven 计数断言（N tests/N specs）须绑定 artifact。**下一节候选域**：新代卡 067..078×work-breakdown 全表卡↔AC 覆盖终查（含 P-8 各卡承接面尾量），或 62/63/64 三节校验器缺口⑥条款累计的跨域整合节。

→ 本项与 ①—64 一并不在本轮 AI 自决实施范围内。

### 65. 新代卡 067..078×work-breakdown×AC 全矩阵覆盖终查（2026-10-13，取证基准 HEAD `a022796`＋077/078 未提交工作树）：**64 偏差⑤尾量兑现**：对 64 节「AC-P0B-UI-001 现势无任何有效证据卡＝本域唯一真 E4 空档」的**主结论勘误**（EVD-P0BR-073/074-001 双卡在档 PASS/E4/scenarios 含 UI-001）、对 64 P-6「055 错绑」的重定性（WB 设计内绑定链）、㉚ 节 IAM 静态断言与 075 运行态十连 PASS 的口径合流、work-breakdown 074/075/076 行「未提交」与 `a022796` 实提交的**行状态漂移**实证、078×59 节同 AC 部分覆盖并存新互斥样本；UI×6 矩阵终值改写为**六 AC 全有主（两处半额待裁定）**，60 P-8「UI 族逐 AC 证据卡缺失」登记债至此清零

**立项与查重过程（先行声明，避免重复计权）**：① Grep plan 全文证 `EVD-P0BR-067/069/070/071/072/073/074/075/076/077/078` 十一个卡号从未被任何独立节以卡面证据身份引用（067..078 相关字符串的 23 处命中全部为脏文件申报行、59 节指针、WB 转述与「立项查而无」声明）；068 有 53 节划界互引（仅预览代理端点结论继承，卡面未展开）；② 本节域即 64 节整改段末「下一节候选域」原文（L2854 末行）＋64 偏差⑤自留尾量的兑现；③ 与 ㉚/59/62/63/64 的划界：产品码缺陷（㉚ 缺陷面 A..H、59 P-1..P-5、64 W-1..W-9 互引）不重复计权，本节只做**卡谱系×AC 矩阵×提交谱线×状态一致性**四个新维度。
**权威锚点**：`work-breakdown.md` L83-99（067..081 全行：状态、提交号、AC 绑定、批准记录）；`EVD-P0BR-0{67,68,69,70,71,72,73,74,75,76,77,78}-001.yaml` 十二卡全存（Glob 实测）；`p0b-exit-catalog.md` L41（IAM-005「达到配置阈值→LOCKED；后续正确密码也返回 AUTH_ACCOUNT_LOCKED」）/L43（IAM-007 重放四观察面）/L135（UI-001 登录跳转＋access 仅内存＋refresh 仅 Cookie）；git 实测：`git show --stat a022796`＝29 文件 2800 增 154 删（含 TASK/EVD-074/075/076 六文件、ci.yml、verify.ps1 +191、verify-journey.ps1/sh、JobWorker/AssetRepositoryReconciler/JdbcJobRepository 后端修复、review-workflow/admin-states/error-paths/lineage/support 五 e2e 注释勘误）；plan §4.4 L185-186（既有提交登记）、§4.6 L246-253（10-10 复跑 126/126 与用例数口径线）、§4.7（081 立项根因）。
**对撞面**（全部实读/实跑，作者无一卡为本会话产物）：073 卡全读 227 行（L13 PASS、L18 scenarios=[AC-P0B-UI-001, AC-P0B-UI-006]、L51 commitSha `7e53aac`==baseCommit、L104 E4、L117-125「四处绕行注释清零改真实 goto/reload＝会话恢复（access Token 内存＋refresh Cookie 引导）经浏览器实证」、L141-149 notProven「openAssetDetailViaCatalog helper 未改真实 goto」）；074 卡（scenarios 含 UI-001/UI-006/P3-VAL-001、governance 四项全 PASS 含 -CheckChangedPaths exit 0、PS5.1/pwsh7 双引擎 PASS=4 SKIP=0、CI 等价复放 118/0/0）；075 卡（十项 IAM 场景逐条 proven：V05c 引导管理员唯一性/首登改密门/防枚举、V05d refresh 轮换 Cookie 属性、V05e 锁定/复位、V05f 登出、V05g 重放吊销族、V05h 禁用即时失效、V05i 并发刷新、V05j 全链；notProven：`iamLastShapeProbe` 僵尸守卫恒 false 致 AUTH_LOGIN_FAILED 存在性断言永不执行）；078 卡全读 272 行（L14 PASS/L19 scenarios=[AC-DST-PRE-001, AC-DST-PRE-002]/L39 commitSha==baseCommit 未提交/L30-32 changedPathValidation FAIL 实跑 13→14 issue/L228-231 三实现文件 PreviewQueryPort/PreviewQueryRepository/application.yml 不在 allowedPaths 16 项枚举内＝卡起草 enumeration 遗漏/L241-243 validate-task-card.ps1 L709-710 dotall 正则指数回溯工具缺陷/L269-272 accepted=false）；绑定图谱十二卡全表（见合规面）；提交谱线十 SHA `cat-file -t` 全真（13ddf26/8fb8c32/28146df/7e53aac/0805b60/e77d0d2/70f7874/a022796 等）。

**已核验合规（矩阵面收口新结论）**：
- **E4 权威口径单调递增链**：playwright 8080 全量 067=99 → 068=101 → 069=110 → 071=111 → 072/073=112→118（073 起 baseURL 8080 权威化、5173 历史口径废止）→ §4.6 复跑 126（＋077/078 各 4）；每一步与新增 spec 数吻合、0 skipped——「遮蔽/桩替代」向「真实 goto/真实直传」的换代在计数与卡内 proven 文本两侧同时可核。
- **UI×6 矩阵终值（改写 64 P-7）**：001=073/074（**在档**，性质见 Q-1）；002=065/067；003=050/065/066/067/069/072；004=048/051（PARTIAL）/055（修复卡）；005=065/069；006=050/065/066/067/069/072/073/074。六 AC 全有主，其中 001/004 为带 notProven 尾量的「在档半额」。
- **IAM 族 Windows E4 面复活**：c633ebc 合并事故致 verify.ps1 V05c-V05j 连锁 CommandNotFoundException（074 limitations 首报→075 bce7674 L371-560 逐字节恢复 191 行、零放宽），十场景一卡恢复；075 的 dirtyPaths 全列＋僵尸守卫缺口的 notProven 系批准人未裁定的 bce7674 既存缺陷如实原样恢复（卡头注声明），诚实面成立。
- **跨域 AC 增量登记**：P2-UPL-002/004（070 presign 真实直传）、P1-AST-001/009（071 null-skip/ON CONFLICT）、P1-AST-012＋JOB-001/002（076 候选 A：对账去重/批量领取/计数诚实化，journey 13P/4F/1S 谎报→18/18/0）、P3-VAL-001（074 V08 诚实化）、DETAIL-001/003（068/077）、PRE-001/002 统计面（078，见 Q-6 限定）。
- **2026-10 代交接态范式**：073..078 六卡 governance 前两项 PASS＋completionValidation 一律 EXPECTED_FAIL＋dirtyWorktree/commitSha==baseCommit 交接态自证＋recordedBy AxeXie＋批准记录（批准人 User 批复时间戳回写）与 AI 起草声明齐备——与 63/64 揭露的批量代伪造指纹形成同库两代对照。

**缺陷面**：
- **Q-1 对 64 节 P-7/标题的主结论勘误（本会话授权文件内的既文失实，据实修正）**：64 标题与 P-7 断言「AC-P0B-UI-001 现势无任何有效证据卡＝本域唯一真 E4 空档」**不成立**——EVD-P0BR-073-001（PASS/E4/scenarios 明文含 UI-001，proven「会话恢复经浏览器实证」）与 EVD-P0BR-074-001（同绑）俱在；64 普查面止于 048/050/051/055/065/066，偏差⑤已自留「067..078 未普查」尾量，本节兑现。修正后口径：UI-001 **有卡有 E4 记录但非满额**——073 的 UI-001 proven 覆盖「会话恢复（内存 token＋Cookie 引导 reload）」半额，「未登录访问受保护路由登录后返回原路由」完整跳转链断言与 helper `openAssetDetailViaCatalog` 绕行残留（073 notProven L141-149）为尾量；64 整改②「立项补卡」相应**降级**为「批准人复核 073/074 绑定充分性＋helper 面补强卡（可并入他卡）」。64 矩阵终值行「五有主、UI-001 无主」读作「六有主、001/004 两处半额」。
- **Q-2 对 64 P-6 的重定性**：WB L76 明文将 AC-P0B-UI-004 绑定给 055，且卡旨（修复 `AssetModelEntity` 列名转义、恢复资产详情读取）正是 051 PARTIAL 注记「UI-004 提交后回读 500，根因在 AssetModelEntity」的阻断项修复——055→UI-004 系**设计内依赖绑定**而非「错绑」；残余问题弱化为：055 卡面未含 UI-004 浏览器验收断言，UI-004 闭环需「055 修复后复跑 051 被阻断用例」的收口证（当前无卡完成该复跑，055 亦未回填 051 链的收口记录）。
- **Q-3 work-breakdown 行状态漂移（文档级缺陷实证）**：L90/L91/L92 三行（074/075/076）末仍作「本卡为 IMPLEMENTED_UNVERIFIED，未提交、未自批」，而 `git show --stat a022796` 实测三卡全部产物（TASK/EVD/脚本/后端修复/五 e2e 注释）已随 `a022796` 入库，plan §4.4 L185-186 亦已登记「三卡已随 a022796 提交，工作树干净」；对照 073 行 L89 存在「**收口（2026-10-08）**：改动已由批准人授权提交为 `70f7874`」的规范形态——074/075/076 三行缺同款收口回写。注意语义分寸：提交≠验收，`review.accepted` 仍 false 合规保持；缺陷仅在**行状态文本与 git 现势脱节**，会误导后续按 WB 行做覆盖判断的读者（本节即是首个受害者的修正记录）。
- **Q-4 IAM 静态断言×运行态 PASS 的口径合流（㉚ vs 075，非互斥但需裁定）**：㉚ L1600「AC-P0B-IAM-005/007 的 E4 场景在此实现下不可能通过」与其论据缺陷面 F（L1623-1625 锁定阈值硬编码、无 lockout 配置键）系静态推论；075 运行态 V05e（失败至锁→LOCKED→复位）/V05g（重放→Family 吊销链）在硬编码 5 次/15 分钟实现下真实 PASS。两者可同真：catalog L41 的观察文本「连续错误达到配置阈值→LOCKED→正确密码也拒」在**固定阈值行为面**成立（075 所证），在「**配置**」属性面（REQ-IAM-002 L64 措辞、㉚ F 所判）不成立——除非验收含改阈值复测。㉚ 的「不可能通过」对「E4 场景」而言口径过宽（行为面已被证伪），应缩窄为「可配置性断言面无实现基础」。待批准人裁定：① catalog L41「配置阈值」是否要求动态改配置的重测（若是，IAM-005 的 E4 半额，075 proven 不充分）；② 075 notProven 的 `iamLastShapeProbe` 僵尸守卫使 IAM-004 类「失败审计存在性」断言永不执行——加严裁定挂清单 #7 至今未处置。
- **Q-5 077/078 治理缺口群（未闭合，阻塞下游）**：两卡 `-CheckChangedPaths` FAIL（077 实跑 15→16、078 13→14 issue），归因四类：078 三必要实现文件不在 allowedPaths（enumeration 遗漏，未隐式扩卡未刷 digest——诚实处置成立但缺口本身待人类裁定）、077/078 同工作树产物交叠、build-logs/playwright-report/test-results 非产品脏目录未 .gitignore、dotall 正则工具缺陷（实测 6 行 <1s、10 行 >20s 挂起）。连带效应：079/080（共享面串行）与 081（preExistingDirtyPaths 留空、基线未清理则必须 FAIL）全部阻塞于批准人处置；§4.5 L232-238 已提请、§4.6/4.7 两轮确认暂无处置。
- **Q-6 同 AC「新诚实卡部分覆盖×既登条款违反并存无互注」（互斥形态第三型）**：078 status PASS 绑 AC-DST-PRE-002，其 proven 的 PRE-002 边界面＝「无 Split 结构归 default／旧投影 NULL→未知空态／上限截断标记」；而 59 节 P-2（L2688：`PreviewJobHandler` L140-142 else 分支对不支持格式写 raw 截断内容标成功＝不抛 `PREVIEW_UNSUPPORTED_FORMAT` 的字面违反）与 P-5（L2694：source digest 全缺）恰在同两条 AC 的其余观察面——078 未触 else 分支、`generatedFrom`≠内容 digest。卡面无失实（未声称覆盖该面），但**证据库现势**给 UI 之外的 PRE 域留下「PASS 卡在档×违反在码」的并置：PRE-001/002 矩阵终值应为「在档半额」，59 P-2/P-5 的整改卡仍欠。此形态与 63 P-4「总声称 vs 诚实卡」、64 P-8「同卡双文件互斥」不同族——单卡诚实、跨节并置，校验器无任何机制检出（入 ⑥ 清单）。
- **Q-7 覆盖矩阵的其余半额/空档（诚实登记，防本节矩阵被过度引用）**：IAM-002 由旧 V05 局部覆盖（catalog L145 上界声明原文）、IAM-010（双公钥轮换）无 E4（㉚ 缺陷面 B 同域，075 未含）；UI-004 见 Q-2；PRE 族见 Q-6；079/080/081 全部 DRAFT＝FAV×4/SUB×3/CLI-002/003 下载票据面**零证据卡**（081 立项根因＝§4.7 双重缺陷实证）。

**偏差与 unproven**：① 本节未起 Compose/浏览器/e2e 任何复跑，全部运行态结论转引卡内记录＋WB＋§4.4/4.6/4.7 既有实跑登记；「单调递增链」是各轮记录的时间拼合，非单轮实测；② 073 对 UI-001 的覆盖程度（完整跳转链 vs 会话恢复半额）依据卡内 proven/notProven 文本判读，未复跑浏览器甄别，Q-1 的「半额」定性留批准人复核；③ Q-3 的实提交判定基于 `--stat` 文件清单对照 074/075/076 dirtyPaths（ci.yml/verify.ps1/五 spec 命中即坐实），未逐 blob 哈希核对「提交态==卡登记态」；④ Q-4 的行为面/配置面切分是文本口径裁定，若 catalog 本意即行为面，则 ㉚ L1600 仅需措缩窄、075 满额——两种读法均不改产品码结论；⑤ 12 卡的 `mvnw verify`/Vitest 计数为卡内自报（078 轮 835/0/0/10、239 tests 等），本节未复跑。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① **64 节既文勘误追认**：批准人确认后由人类在 64 P-7/整改② 处加注「已被 65 Q-1 勘误」或维持本节单点声明（64 原文保留作历史现场）；② WB L90/L91/L92 三行补「收口（提交 `a022796`）」回写（照 073 行 L89 形态，文档同步无需新卡）；③ Q-4 两项裁定：catalog L41「配置阈值」验收语义（行为面 or 可配置面）＋`iamLastShapeProbe` 加严（清单 #7 原案）；④ Q-5 四件套：078 三路径扩卡或改写裁定、脏目录 .gitignore 收编、dotall 正则工具修复、077/078 提交与 review.accepted；⑤ 59 P-2/P-5（unsupported 冒充成功＋digest 缺失）另立整改卡，落卡后 PRE-001/002 升满额；⑥ **校验器追加清单累计（承 62⑦/63⑥/64⑥）**：新增三条——PASS 卡×同 AC 他节违反登记的跨源并置检出（Q-6 型）、WB 行状态文本×git log 实提交一致性核对（Q-3 型）、AC 绑定的覆盖充分性注记字段（proven 面 vs AC 全文观察面差集，Q-1/Q-4/Q-6 三型的公共根因：卡绑 AC 号即可声称覆盖，无面级约束）；⑦ 081 presign 下载票据修复卡批准处置（AC-P2-DL-001/CLI-002/003 唯一解锁路径）。**下一节候选域**：62/63/64/65 四节校验器缺口⑥条款的跨域整合节（门禁元域收口，承 60 节 AI 执行门禁谱系），或全库 AC-ID×EVD 绑定反向终查（卡→AC 正向已毕，AC→卡反向合并 p0b＋dataset＋P1..P5 目录出终版空档清单——本节矩阵仅覆盖 p0b＋dataset 交集域）。

→ 本项与 ①—65 一并不在本轮 AI 自决实施范围内。

### 66. 全库 AC→EVD 绑定反向终查——8 catalog × 170 unique AC × 130 卡终版空档清单（2026-10-13，取证基准 HEAD `a022796`＋077/078 未提交工作树）：**65 候选域(b) 兑现**：以缩进状态机精确提取 130 张 EVD 卡 scenarios 区块 AC 绑定（UTF-8 安全版修正前版 PS5.1 默认 ANSI 导致中文注释头部 status 解析失配缺陷），对 8 份验收目录（170 unique AC-ID）执行全量反向三分类（HAS-PASS-CARD / CARD-ONLY-NONPASS / ZERO-CARD），产出**终版空档清单**——这是 62/63/64/65 局部矩阵从未完成的全库级收口，同时量化幽灵 PASS（24 张无 scenarios 老代 PASS 卡）与 P4 零覆盖（12/13 ZERO）两大结构性缺口。

**立项与查重过程（先行声明，避免重复计权）**：① 65 L2882 候选域原文「全库 AC-ID×EVD 绑定反向终查（卡→AC 正向已毕，AC→卡反向合并 p0b＋dataset＋P1..P5 目录出终版空档清单——本节矩阵仅覆盖 p0b＋dataset 交集域）」——精确匹配本节域；② Grep 节标题地图（60 L2718 AI 执行门禁元域、㉑ L1955、㉕ L1145、㉛ L1712 NFR 七方差集、57 L2607、62 L2774、63 L2801、64 L2828、65 L2858）＋Grep「反向|终查.*AC|零证据卡」34 命中：全部为局部反向对照（㉓/㉕/㊱ 八方差集）或 65 自述候选域原文——**全库 AC→卡反向矩阵从未做过**，定装候选(b) 胜出；③ 候选(a)（62—65 ⑥条款跨域整合节）落选理由：与 60 节重叠风险、且无 65 明文候选域支持。
**权威锚点**：`docs/ai-spec/05-acceptance/` 8 文件（Glob 实测）：ai-execution-gate-catalog.md(AC×8)、dataset-agent-exit-catalog.md(AC×41)、p0b-exit-catalog.md(AC×70)、p1-exit-catalog.md(AC×17)、p2-exit-catalog.md(AC×12)、p3-exit-catalog.md(AC×8)、p4-exit-catalog.md(AC×13)、p5-exit-catalog.md(AC×10)；`docs/ai-spec/tasks/evidence/` 130 张 EVD 卡（Glob 实测）；acrev2.ps1（UTF-8 安全版状态机引擎）完整输出（9456 bytes）。
**对撞面**（全部实跑，零人工判断介入）：acrev2.ps1 对 130 卡逐文件 `[System.IO.File]::ReadAllLines(UTF8)` 提取 scenarios 区块 AC 绑定、对 8 catalog 逐文件 `[regex]::Matches` 提取 AC 全集、交叉三分类。acrev.ps1（首版）PS5.1 `Get-Content` 默认 ANSI 解码含中文注释头部卡（EVD-P0BR-058 等 8+ 卡）时 status 解析失配——诊断脚本 acrev-dbg.ps1 坐实文件首 16 字节 `73 63 68 65 6D 61 56 65 72 73 69 6F 6E 3A 20 68`（无 BOM、无 CR），GBK 引导字节吞换行使多行合并为单行匹配 `^status:` 失败；v2 版改 UTF-8 后 status 正确分 PASS=70/PARTIAL=36/DRAFT=24、level 字段因缩进层级差异全显 `?`（不影响 AC 绑定抓取）。

**已核验合规（终版数字面）**：
- **全库 unique AC = 170**（179 raw entries − 9 跨库重叠：DETAIL×4 同现 dataset+p1、PRE×4 同现 dataset+p2+p5、TAX×2 同现 dataset+p1、CLI×1 同现 dataset+p2、DISC×1 同现 dataset+p1；AC-DST-AI-001..005/AIW-001/003 同现 dataset+p4 去重后 170）。
- **HAS-PASS-CARD = 65 entries**（per-catalog 计权；unique AC ≈ 63，因 PRE-001/DETAIL-001 等跨库重复计），覆盖 gate 7/8、dataset 7/41、p0b 36/70、p1 7/17、p2 5/12、p3 2/8、p4 0/13、p5 1/10。
- **CARD-ONLY-NONPASS = 51 entries**（仅 PARTIAL/DRAFT 卡覆盖、无 PASS），unique AC ≈ 42（含 DISC×5、CLI×6、DETAIL-002、PRE-003/004、TAX-003/004、AIW-002 等）。
- **ZERO-CARD = 63 entries**（无任何卡绑定），**终版空档清单**：gate×1（AC-AI-IDE-008 reserved for TASK-GOV-008）；dataset×17（AI×5/AIW×5 除-002/FAV×4/SUB×3）；p0b×31（AGT-001..004/AUD-003..005/AUTH-001/002/006..009/CFG×3/IDM-003/NOT×5/OBS-001/002/004/005/TAX-001..005）；p2×2（DVC-001/VER-001）；p4×12（DST-AI×5/AIW-001/003 + P4-ALW/API/COMP/EXIT/MCP-001/003/004/RES/SKL）。
- **孤儿 AC = 0**：所有卡 scenarios 绑定的 AC-ID 均存在于至少一份 catalog——无幽灵绑定。
- **撞名五对合并验证**：EVD-P0BR014-001（无连字符,PASS,scenarios=[]）vs EVD-P0BR-014-001（有连字符,PARTIAL,scenarios 含 AGT-005/AIW-002/ENG-006）；同理 017/023/024/041。反向分类以有连字符版绑定为准（无连字符版 scenarios=[] 不贡献覆盖）。五对均正确——无双重计权。
- **幽灵 PASS 量化**：24 张 EVD-P0BR0{0A,0B,02..04,10..17,20..30}-001（无连字符老代）status=PASS 但 scenarios=[]——它们不被任何 AC 反向命中（已验证不进入 HAS-PASS-CARD 列表），仅对「卡库存 PASS 总数」虚增 24 张（真实有效 PASS = 70 − 24 = 46 张）。

**缺陷面**：
- **R-1 P4 域全库最低覆盖（0% PASS）**：13 AC 中 12 ZERO + 1 PARTIAL（P4-SCH-001→P4001:PARTIAL）——P4 MCP/Skill/Composite/Resource/Allowlist 等核心能力仅有空心卡 P4002-4010（status DRAFT, scenarios=[]），无任何有效证据。此非新发现（61 节 L2774 候选、63 节已局部提及），但本节首次以数字坐实全貌。
- **R-2 p0b 零覆盖 31 AC 的结构分布**：NOT×5 + OBS×4 + TAX×5 + AUTH×7 + CFG×3 + AGT×4 + AUD×3 + IDM×1 = 31 项全为 Compose 验证范围 V01-V12 明确未覆盖的场景（EVD-P0B-V01-V12 L149-158 notProven 区逐条列出「AC-P0B-NOT-001~005 通知全部场景」等原文）——**与卡内 notProven 完全吻合**：V01-V12 自身诚实声明了未覆盖面，本节反向终查只是把声明面→全库统计面量化。非新增缺陷，而是既登缺陷的全库级聚合。
- **R-3 dataset AI×5/AIW×5/FAV×4/SUB×3 共 17 ZERO**：AI workflow（079/080 DRAFT 卡未产出 scenarios）与 FAV/SUB（081 DRAFT）全部未取证。Q-7 尾量「081 presign 下载票据修复卡批准处置」的 AC-P2-DL-001/CLI-002/003 在本表已体现为 NONPASS/HAS-PASS，但 FAV/SUB/AI 域仍为零。
- **R-4 幽灵 PASS 卡存量未清理**：24 张老代 PASS 空壳卡 + 15 张 P2/P4 DRAFT/PARTIAL 空壳卡 = **39 张 scenarios 空白的 EVD 文件在档**，占卡库 30%。它们通过 validate-spec 148 documents/752 IDs 检查（schema 合规但无内容），造成「库存 70 PASS 卡」的虚高观感。无卡 ID 被反向命中，但人工审计与 AI 上下文读取均受噪声干扰。
- **R-5 AC-AI-IDE-008 唯一 gate 空档**：GOV007-001 L95 notProven 原文「AC-AI-IDE-008 protected CI enforcement; reserved for TASK-GOV-008」——TASK-GOV-008 尚未立项，系 AGENTS.md 所述「known gate gap」的验收具象。
- **R-6 p2 DVC-001/VER-001 零覆盖**：版本与 DVC 核心流程仅有 P2001-2007 DRAFT 空壳卡，无 scenarios 绑定。与 AGENTS.md「P1–P5 business waves are PARTIAL / IMPLEMENTED_UNVERIFIED」定性一致，但本表首次量化「完全零证据」而非「有证据但未验证」。
- **R-7 首版取证引擎 status 解析缺陷（工具面，本节自纠）**：acrev.ps1 用 PS5.1 `Get-Content`（默认 `[System.Text.Encoding]::Default`＝GBK on zh-CN Windows）读取 UTF-8 无 BOM 文件，含中文注释的 YAML 头部被 GBK 双字节引导吞行，`(?m)^status:` 匹配多行合并后首非注释行失败。acrev-dbg.ps1 坐实字节流（`73 63 68 65 6D 61` = "schema"）无异常、文件本身合法。acrev2.ps1 改 `[System.IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)` 修正。**对 62-65 既有取证的影响**：此前各节卡→AC 正向绑定均用 Grep/Read 直读，不经 PS5.1 Get-Content，故不受此缺陷影响；本节为首个以脚本遍历 130 卡的取证，缺陷发现即修正。

**偏差与 unproven**：① 本节纯静态分析（文件→正则→矩阵），未起 Compose/浏览器/e2e 任何复跑——HAS-PASS-CARD 仅证「scenarios 区块声明了绑定」，不证「绑定对应的验收场景在当前 HEAD 可重跑通过」（此需 review.accepted=true 或真实复跑；**66 节初稿「全部卡 accepted 均 false」系误记，落盘后 Grep 实测勘误为 `accepted: true` 6 份 / false 124 份，明细与 60 节 P-6 一致**——EVD-P1015-001 L78、EVD-P1016-001 L79、EVD-P1017-001 L67、EVD-P1018-001 L94、EVD-P2013-001 L72、EVD-P3010-001 L85，均不在本节 HAS-PASS 判据的采信范围内）；② 170 unique AC 基于 catalog `[regex]::Matches` 全文匹配 `AC-[A-Z0-9]+-[A-Z0-9]+-\d{3}`，若 catalog 含 prose 中偶然提及的 AC-ID（如「参见 AC-xxx」），会被计入分母——已抽查 gate/p0b/p4 三库无非定义性命中；③ `requiredEvidenceLevel` 字段全 130 卡显示 `?`——因该字段位于嵌套缩进层级（如 `coverage.requiredEvidenceLevel`）且命名可能为 `evidenceLevel`（grep 坐实 P0BR-058 L95 为 `evidenceLevel: E4`），非顶层 key，acrev2 的 `^\s*requiredEvidenceLevel:` 模式不匹配，不影响 AC 反向分类正确性；④ dataset 41 + p1 17 + p4 13 跨库重叠（AI×5/AIW×2/DETAIL×4/PRE×4/TAX×2/CLI×1/DISC×5）使 unique 总数 170 < raw 179——各 catalog 各自分类正确，跨库重复项在两侧均按同规则分类（如 DETAIL-001 在 dataset HAS-PASS 且 p1 HAS-PASS，不矛盾）。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① **幽灵卡清理裁定**：24 张老代空壳 PASS + 15 张 P2/P4 DRAFT/PARTIAL 空壳是否标 `deprecated: true`/`status: SUPERSEDED` 或删除（文件删除不可逆，建议加元数据标记保 schema 合规）；② P4 域取证立项：12 ZERO 须至少 1 张 E4/PASS 卡绑定各 AC——当前 P4 卡群（P4001-P4010）无 scenarios 是「未启动取证」的具象，非设计遗漏；③ TASK-GOV-008 立项解锁 AC-AI-IDE-008；④ p0b 31 ZERO 中 NOT×5/OBS×4/CFG×3 三项可由 Compose verify 扩展覆盖（EVD-P0B-V01-V12 notProven L149-158 原文「通知全部场景」「配置全部场景」「观测其他场景」为明确待补断言清单），AGT-001..004/AUD-003..005 需专项 MCP/审计验证卡；⑤ dataset AI×5/AIW×5/FAV×4/SUB×3 共 17 ZERO 须 079/080/081 批准提交后由实施卡覆盖；⑥ **校验器追加清单（承 62⑦/63⑥/64⑥/65⑥）**：新增两条——scenarios=[] 但 status=PASS 的卡标 WARNING（幽灵 PASS 检出）、AC→卡反向覆盖报告作为 validate-spec 可选扩展步骤（当前 validate-spec 仅正向校验）。**下一节候选域**：62/63/64/65/66 五节校验器缺口⑥条款跨域整合（门禁元域收口，承 60 节），或 EVD 卡 `evidenceLevel`×`status`×`review.accepted` 三维合规终查（accepted 维量级已由 60 P-6 登为 6/130，但三维交叉从未做过——全部验收处于 AI 自证未接受态）。

→ 本项与 ①—66 一并不在本轮 AI 自决实施范围内。

### 67. EVD 卡 `evidenceLevel`×`status`×`review.accepted` 三维合规终查——130 卡 13 格矩阵、64 卡级差、6 份 accepted 明细核验（2026-10-13，取证基准 HEAD `a022796`＋077/078 未提交工作树）：**66 候选域(b) 兑现**：以 evd3.ps1（UTF-8 安全版，行内流式 `scenarios: [AC-…]` 兼容修正后与 66  acrev2 双引擎交叉验证一致）对 130 张 EVD 卡提取三维字段、对 126 张 TASK 卡提取 `requiredEvidenceLevel` 权威要求，首次产出**级差清单（64 卡 EVD 证据等级 < 任务要求）**与 **accepted=true 全 6 份明细核验**（兑现 60 偏差③「6 份 accepted 明细未逐一核对验收主体合法性」遗留项）。

**立项与查重过程（先行声明，避免重复计权）**：① 66 L2912 候选域原文「EVD 卡 evidenceLevel×status×review.accepted 三维合规终查」——精确匹配；66 初稿括注「全库无一张 accepted=true」已于落盘复测时 Grep 实测勘误为 6 份（见 66 偏差①勘误文），候选域描述同步修正为「accepted 维量级已由 60 P-6 登为 6/130，但三维交叉从未做过」；② Grep 节标题地图＋「三维合规|evidenceLevel」34 命中：60 P-6（accepted 单维量级）、58 N-11（P4 三卡 E3<E4 局部）、2337 行 R-7（P5 单卡 E5 级差）均为**单维或局部**提及，三维交叉矩阵、TASK required×EVD level 全库级差对撞、PASS 可信度分层从未做过——定装候选(b) 胜出；③ 候选(a)（62—66 五节校验器缺口跨域整合）再次落选：纯文字汇编无新撞对，且与 60 节门禁元域重叠。
**权威锚点**：schema 模板 `docs/ai-spec/templates/evidence-manifest.yaml` 全读 89 行（L3 `status: DRAFT`、L49 `evidenceLevel: E1 # E1|E2|E3|E4|E5` **单值枚举**、L85-89 review 块 `recordedBy/reviewedBy/accepted`）；130 张 EVD 卡（Glob 实测，与 66 同基数）；126 张 TASK 卡含 taskId 字段（如 TASK-P0BR-002.md L46 `requiredEvidenceLevel: E4`）；evd3b.out.txt（终版输出）。
**对撞面**（全部实跑）：evd3.ps1 逐卡 UTF-8 提取 status/evidenceLevel/accepted/recordedBy/reviewedBy/commitSha/taskId/scenarios 数 → 13 格三维矩阵；TASK 侧 `requiredEvidenceLevel` ↔ EVD 侧 `evidenceLevel` 取 MaxLevel（组合值取最大档，宽口径）逐级对撞出级差；首版跑至 P0BR044 组合值 `E1+E3` 崩（`[int]"1+E3"` 强转失败）→ 改 MaxLevel 正则提数字最大值后 EXIT=0；ghost PASS 首版 51（行内流式 scenarios 漏计）→ 补 `scenarios: [AC-…]` 行内解析后 **24，与 66 acrev2 完全吻合**（双引擎互为交叉验证）。

**已核验合规（终版数字面）**：
- **三维矩阵 13 格全表**：DRAFT×(E3 22＋E4 2)＝24；PARTIAL×(E2 3＋E3 17＋E4 10＋E5 6)＝36；PASS×(E1 5＋E1+E3 1＋E1+E3+E4 2＋E2 25＋E3 3＋E4 34)＝70——status 边际 24/36/70 与 66 完全一致；level 边际 E1 系 8／E2 28／E3 42／E4 46／E5 6＝130。
- **accepted=true 仅 6 份且全为 PASS**（E3×3＋E4×3）：EVD-P1015-001（L26 `commitSha: pending-commit`＋L76 reviewedBy＝「User (plan execution authorization 2026-07-12)」）、P1016（30059da7）、P1017（b4d244a2）、P1018（1593e3b3）、P2013（1593e3b3）、P3010（`178ee6b` 7 位短写 L26）——**60 偏差③ 兑现**：六份全部同一 recordedBy=AI/reviewedBy 同串文字/同日 2026-07-12，验收语义实为「计划执行授权」批量标记而非逐卡人工复核。
- **taskId 对撞**：130 卡中 128 卡 taskId 命中 126 张 TASK 文件；仅 EVD-P0B-ENG-001（L6）与 EVD-P0B-V01-V12（L6）显式 `taskId: null # 无独立 Task Card` 注释豁免——**零真孤儿**。
- **强证据集（PASS×level≥E4×scenarios>0）＝ 35 卡**，其中 accepted=true 仅 3（P1015/P1018/P3010）——32 张最高静态可证卡全部处于未验收态。
- **幽灵 PASS 双引擎互证**：evd3 24 张 ghost（status=PASS×scenarios 空白）与 66 acrev2 24 张**同名同数**——66 的 R-4 数字获独立实现复算。

**缺陷面**：
- **R-1 级差 64 卡（本节头号发现）**：EVD `evidenceLevel` 低于对应 TASK `requiredEvidenceLevel`——P0BR 老代 29（003/004/010..017/020..030/040..044，含撞名连字符版 014/017/041 三张，全部 E2 或 E1+E3 < E4）、P1×12（P1003..P1013/P1016，E3<E4）、P2×10（P2002..P2013 九张＋P2013，E3<E4）、P3×4（P3002/3004/3006/3008，E3<E4）、P4×8（P4002..P4010，E3<E4）、P5×1（P5007 E4<E5）。validate-task-card `-CheckCompletion` 按 AGENTS.md 应拒此类完成声称，但该比对（TASK required ↔ EVD level）在 validate-spec 与常规 CI 中**无静态检出路径**——60 P-3「无卡改产品」之外的另一门禁空档「卡级差静默通过」。
- **R-2 非标准组合 level 值 3 卡**：EVD-P0BR044/045/046-001 的 `evidenceLevel: E1+E3`/`E1+E3+E4` 违反模板 L49 单值枚举——schema 层无枚举校验（validate-spec 过），语义上「多档并证」是否等效最高档无裁定口径（本节 MaxLevel 宽口径处理并显式登记）。
- **R-3 验收主体合法性存疑**：6 份 accepted 卡的 reviewedBy 全为「User (plan execution authorization 2026-07-12)」同串——与 DEC-009「AI 不得自批」的形式合规（reviewer≠recorder）与实质存疑（同日同文批量、其中 P1015 sha=pending-commit 即验收时提交态未回填、P3010 sha 7 位短写）并存；「验证权威验收」在本库的全部实证＝这 6 份批量标记。
- **R-4 recordedBy 人签比例 12%**：AxeXie 16＋Codex 1＝17/130，其余 113 为 AI/Trae AI 泛称——与 60 P-6「验收荒」同构的**记录侧**问题：绝大多数卡的取证主体不可归因到具体人。
- **R-5 PASS×E1/E2＝33 卡的可信度分层缺口**：70 张 PASS 中 25 张 E2（Compose 级）、8 张 E1 系（本地命令级）——对 E4 要求域（p0b exit）而言这 33 张即便 accepted 也不满额，而 66 HAS-PASS-CARD 判据不区分 level——**66 的 65 entries 上界在三维视角下须再按 level 收窄**（两节互补而非矛盾，66 已自declare HAS-PASS 仅证绑定存在）。
- **R-6 引擎级缺陷自纠两例（工具面）**：① evd3 首版崩于组合 level 强转（登记后修复）；② 首版 nscen 状态机漏行内流式 `scenarios: [AC-…]` 致 ghost 51 虚高（修正后 24 与 66 对齐）——两缺陷均为「发现即修＋修正后与他节互证」形态，无未修残留。

**偏差与 unproven**：① 级差判定用 MaxLevel 宽口径（组合值取最大档），若按严口径（组合视同最低档）则 P0BR044 等 3 卡判档变化、总数不降；② 6 份 accepted 卡的 commitSha（30059da7/b4d244a2/1593e3b3/178ee6b）是否在 git 历史存在、是否真对应「验收时点」**未核对**（unproven，`git cat-file` 留批准人复核）；③ 126 TASK 卡的 requiredEvidenceLevel 提取自 front-matter 单行正则，卡正文散文中的等级表述未纳入对撞；④ 本节仍为纯静态——E4/E5 声明的真实性（Compose 实跑、e2e 录屏）不在三维矩阵可证范围（承 66 偏差①）；⑤ 三维交叉的「合规」判据本身（哪个格算达标）无 catalog 明文定义，本节只出矩阵与级差事实、定性留给批准人。

**整改待批（须人类先出/更新 READY+authorized Task Card 并重绑 baseCommit，AI 不自批不自改）**：① **级差 64 卡分型处置**：33 张老代 P0BR E2 卡属 66 幽灵卡同源（000A/000B/002..030 空壳＋其 level 字段一并随幽灵卡裁定作废或降级登记）；P1/P2/P3/P4 的 34 张 E3<E4 卡须按 60/66 待批项「E4 补证」统一立项，禁止就地改卡字段（level 是取证事实非标签）；② schema 枚举校验：evidenceLevel 值域（含组合值裁定）进 validate-spec 或 validate-task-card——承 62⑦/63⑥/64⑥/65⑥/66⑥ 追加清单累计（新增两条：TASK required×EVD level 级差静态报告、evidenceLevel 枚举校验）；③ 6 份 accepted 批量标记的合法性裁定：批准人确认「plan execution authorization」是否构成 DEC-009 验证权威验收，或要求逐卡复核＋P1015 pending-commit/P3010 短 sha 回填修正；④ recordedBy 归因规范（人签/AI 会话标识分离）修订模板并登记存量口径；**下一节候选域**：(a) 126 张 TASK 卡 baseCommit×HEAD 现势普查（60 偏差④「GOV-007 以外 ~100 卡 baseCommit 现势有效性未普查」遗留——纯脚本可取证的门禁漂移面）；(b) 62—67 六节校验器缺口条款跨域整合（门禁元域收口，多次落选的汇编项，若批准人倾向合并可直接执行）。

→ 本项与 ①—67 一并不在本轮 AI 自决实施范围内。

### 68. 126 张 TASK 卡 `baseCommit`×HEAD 现势普查——validator 默认模式全库实跑（124/126 exit 1、121 卡 baseCommit 漂移）、41 唯一值零幽灵、EVD 幽灵 sha 73 条脱钩实证（2026-10-13，取证基准 HEAD `a022796`＋077/078 未提交工作树）：**67 候选域(a) 兑现**：bcrun.ps1 以 validator 默认模式逐卡实跑 126 张 TASK 卡（首份全库 exit/issue 权威清单）＋bc3.ps1（v3）静态对撞 `git rev-list HEAD` 254 祖先链（唯一值漂移定位）＋130 条 EVD `commitSha` 六分类（67 偏差② 的 git 存在性子项闭环）。

**立项与查重过程（先行声明，避免重复计权）**：① 60 节偏差④原文「GOV-007 以外 ~100 张 TASK 卡的 baseCommit 现势有效性未普查…推定大批陈旧但 unproven」＋67 节末候选域 (a)「126 张 TASK 卡 baseCommit×HEAD 现势普查——纯脚本可取证的门禁漂移面」——双源头精确匹配；② Grep 本 plan「baseCommit」54 命中逐条核：60 节（GOV-007 单卡 217 漂移）、61 节（013/014 短 SHA exit 1×8 局部实跑）、62 节（四例实跑）、55—58 节（三值不同源模板句）——全部为**单卡或局部**，126 卡全库普查从未做过——定装候选(a)；③ 候选(b)（62—67 六节校验器缺口跨域整合）第六次落选：纯文字汇编无新撞对。
**权威锚点**：`docs/ai-spec/tasks/` 126 张 TASK 卡（Glob＝Grep `^baseCommit:` 126 行吻合）；validate-task-card.ps1 全读 804 行（L190 40 位强格式、L201 默认模式 `==HEAD`、L196-199 flag 模式改判 `merge-base --is-ancestor`、L181/184 status+auth、L226/766 人签禁词）；HEAD `a0227961e085a6feae627bff7fcc276937b63b6b`，祖先链 254 提交。
**对撞面**（全部实跑）：bcrun.ps1 逐卡 `validate-task-card.ps1 -TaskPath`（默认模式）126 卡 → 落盘逐卡 exit＋issue 全清单；bc.ps1 首版（B1 `Group-Object` 缺陷致派生列无效，B4/B6 段有效）→ bc2.ps1（C1 又崩：front-matter 探测窗口 15 行＋`$_[$3]` 误用自动变量；C2 EVD 分类有效）→ bc3.ps1 v3 全文件扫描＋独立 sort 键列后 EXIT=0 产出终版（详见 R-6 自纠链）。

**已核验合规（终版数字面）**：
- **validator 默认模式实测**：126 卡 **exit 0 仅 2**（P0BR-077/078，其 N=5 为横幅信息行非问题），exit 1＝124；TOTAL_ISSUES=462。baseCommit 类失败 **121 卡**＝89（40 位但 `does not match current HEAD`）＋32（`0b152f3` 7 位短 SHA 违反 L190 强格式）；恒等式闭合：126−2（exit 0）−3（079/080/081 绑 HEAD 但 DRAFT 态挂 status/auth 类）＝121 ✓。**60 偏差④「推定大批陈旧」由 unproven 转实证 121/126**。
- **唯一值零幽灵**：41 个唯一 baseCommit（含 1 短 SHA 值）**全部是 HEAD 祖先**——离线 `cat-file`/前缀匹配双重实跑，不存在任何「绑定到不可解析提交」的 TASK 卡；漂移谱 ancestor@1（074/075/076 三卡）至 ancestor@217（GOV-007，**与 60 节实测定值精确吻合，双节互证**）。
- **绑定簇形态**：32 张老代 P0BR 卡共绑 `0b152f3`（ancestor@196）；P1-001..011＝@163、P2-001..010＝@161、P3-001..008＝@158、**P4-001..010＋P5-001..008 共 18 卡同绑 `59f40d28`＝@157**（P4/P5 两波共基线为最大 40 位同值簇）；077..081 五卡绑 HEAD——「波次立项时点批量回填同一 baseCommit」是存量卡的生成形态。
- **462 issue 类型分布（与邻节互证）**：approvedBy 非法 35＋approvedAt 非 UTC 35——**与 66 节 accepted=false 124 卡中的老代 35 张同数同源**（新代卡的 approvedBy 人签串通过 L226 禁词校验）；status 非 READY 3＝implementationAuthorized false 3＝stageGatePassed false 3（同为 P0BR-079/080/081，bc B4 段独立复算吻合）；scenarioEvidencePlan 类 37＋23、-CheckChangedPaths 缺失 32、DEC-010 未声明 29、Compose 双缺 27/27、REQ 非 READY 10 等。
- **EVD↔TASK sha 脱钩实证**：130 条 `commitSha`＝ghost 73（`7dc22d44…` 单值×71 横跨 EVD-P0BR 老代全簇＋`4c7dfc83…`×2，`git cat-file` 全部 fatal 不可解析）＋非 HEAD 祖先 48＋EQ-HEAD 2（EVD-P0BR-077/078-001）＋短写祖先 2（`6aad901`/`178ee6b`）＋NONSHA 7（`null`×2＝ENG-001/V01-V12 豁免卡、`pending-commit`×5＝P1013/P1014/P1015/P2011/P2012）；**41 个 TASK baseCommit 值与幽灵 sha 交集为零**——EVD 取证声明的提交态在库内 git 历史完全不存在，且 validate 体系对 EVD sha 无任何存在性校验（幽灵静默）。
- **67 偏差② 部分闭环**：accepted 卡五 sha 中 30059da7（=TASK-P1-017）/b4d244a2（=P2-013）/1593e3b3（=P1-018）全部实证为真祖先（@106/@105/@100）且与对应 TASK 卡 baseCommit 同号异态（卡面≠HEAD、证据面自洽）；`178ee6b` 短写命中祖先；唯 P1015 `pending-commit` 仍无提交态。

**缺陷面**：
- **R-1 漂移 121 卡（默认模式口径）**：==HEAD 判据下全库存量卡不可提交态（89 漂移型＋32 短 SHA 格式型）；其中 89 张 40 位卡值合法（真祖先），属「时点陈旧」型；079/080/081 三卡 baseCommit 已绑 HEAD 但整体仍 exit 1（DRAFT 未批准态，另列）——任何「重跑 validator 验收」动作都须先由批准人重绑，重绑属改卡（AI 禁改，见整改①）。
- **R-2 两模式语义分裂（新取证面）**：`-CheckChangedPaths`/`-CheckCompletion` 态改判祖先即可过（L196-199），而 41 唯一值**全部为祖先**——意味着 handoff/completion 两种实跑态下 baseCommit 门对 **89 张 40 位漂移卡实际不拦截**（32 张短 SHA 卡在 flag 态仍先挂 L190 格式检查、到不了祖先判断；079/080/081 卡面绑 HEAD 过 baseCommit 但挂 DRAFT 态 status 类）；默认模式的 ==HEAD 强判与 flag 模式的祖先宽判之间无中间态（如「漂移 N 提交内警告」），门禁强度取决于调用者恰好传了什么参数。
- **R-3 证据面 sha 零校验（头号静默缺口）**：TASK 卡 baseCommit 有三种校验路径（格式/HEAD/祖先），EVD 卡 commitSha 一种都没有——71 张幽灵 sha 卡经 60/66/67 三节逐级放大仍无静态检出路径；validate-spec 过（本库 148 docs/752 IDs 口径不含 EVD sha 存在性）。
- **R-4 短 SHA 格式违规 32 卡**：`0b152f3` 值本身真实（@196）但违反 L190 40 位强格式——属「值对形态错」型，与幽灵值（值不存在）分型处置口径不同。
- **R-5 EVD↔TASK 基线失联簇**：IN-TASK-BC 同号仅 29 个唯一 sha（老代 P0BR-047..078 系＋P1-016/017/018/P2-013＋GOV-007）；P4/P5 的 EVD 用独立祖先 sha（@22..@91）非其卡面 @157、P1-001..014/P2-001..009/P3-001..008 的 EVD 卡直接绑 `7dc22d44` 幽灵——三形态并存（同号/异祖/幽灵），卡证据链基线一致性无统一口径。
- **R-6 引擎级缺陷自纠三版（工具面，承 66 R-7/67 R-6 传统）**：① bc.ps1 首版 B1 `$rows | Group-Object BC` 返回空组致 UNIQUE_BC=0、B2/B3/B5 派生列全废（B4/B6 不依赖该管道故有效）；② bc2.ps1 的 C1 front-matter 结束探测窗口仅 15 行（全 126 卡闭合 `---` 在 15 行外）→ 全部落到空键 n=126，且 `Sort-Object { $_[$3] … }` 误用自动变量 `$3` 致排序崩（C2 段未受影响）；③ bc.ps1 的 B6 误标「eq-HEAD=48」——凡祖先均计入该桶，bc2 C2 起拆分为 eqHEAD=2/ancNotHead=46/shortAnc=2，bcrun 独立实测佐证短写仅 178ee6b+6aad901 两条（26−24＝2 ✓）。三缺陷均「发现即修＋跨版本互证」，终版以 bc3＋bcrun 双引擎为准。

**偏差与 unproven**：① flag 模式（-CheckChangedPaths/-CheckCompletion）结论（R-2「不拦截」）为 804 行规则静态推演＋离线祖先测试实证，**未做 126 卡 flag 态逐卡实跑**（unproven，口径预告防歧义）；② baseCommit 值合法≠「该卡实施时确实以该提交为基线」——需 checkout 重跑测试套件方可证，纯静态不可达（承 66/67 全静态边界声明）；③ 幽灵 sha 是否存在于任何远端/原始仓库历史未核（本地库不可解析即登记，不推断成因）；④ 462 issue 归一化计数含少量多行 issue 续行混入（如 `crossCuttingPlan…: STATE`、`Requirement … is not READY` 长短两态分列），类型分布量级可信、个位尾差存在；⑤ 本节全程无批准人处置，全部整改为待批项。

**整改待批（须人类先出/更新 READY+authorized Task Card，AI 不自批不自改）**：① **121 卡漂移分型重绑**：89 张全卡态漂移＋32 张短 SHA 格式型（值真实）＋GOV-007（60 节整改⑤ P-5 已提请，@217 与本节吻合）——重绑属改卡，批准人处置后逐卡复跑 validator 收口；② **R-2 模式语义裁定**：==HEAD vs 祖先判据的统一（或引入「漂移阈值警告」中间态）＋是否将 baseCommit 现势检测接入 validate-spec/CI（当前 CI 无默认模式逐卡执行，漂移静默）；③ **R-3 EVD sha 存在性校验接入**：`git cat-file` 级静态检查进 validate 体系——新增两条入 62⑦/63⑥/64⑥/65⑥/66⑥/67② 追加清单累计；④ 幽灵 sha 71 卡与 32 短 SHA 卡并入 66/67「老代幽灵卡群分型裁定」同一决定（卡面值真实形态错 vs 证据值不存在，分型标注）；**下一节候选域**：(a) TASK 卡**审批字段×引用完整性**全库普查——approvedBy/approvedAt 唯一值分布＋stageGateEvidenceRefs/requirementRefs/acceptanceScenarioRefs 指向文件与 ID 存在性对撞（bcrun 已量化非法 35 卡，身份值与 ref 存在性两维未做）；(b) 62—68 七节校验器缺口条款跨域整合（门禁元域收口，汇编项第七次候选，若批准人倾向合并可直接执行）。

→ 本项与 ①—68 一并不在本轮 AI 自决实施范围内。

### 69. 126 张 TASK 卡审批字段全库普查——approvedBy 六值谱系、48 卡整点批量戳与建卡同提交注入（Git `-S` 实证）、32 卡 READY+authorized 零批准人（2026-10-13，取证基准 HEAD `a022796`＋077/078 未提交工作树）

**立项查重**：候选(a) 初版为「审批字段×引用完整性」双维；查重修正——**ref 存在性维度 validator 已强制**（stageGateEvidenceRefs 路径存在性 [validate-task-card.ps1](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L353-L361)、requirements 未知即错 L280-300、evidenceManifests⊆allowedPaths L571＋CheckCompletion 态文件存在性 L680），且 bcrun 589 行实测中 `Unknown requirement`/`does not exist in the repository` **0 命中**（唯一 ref 类问题＝DEC-010 not-declared 29 条，已计入 68 agg）——该维无增量面，候选(a) 收窄为**审批身份谱系×时间戳聚类×Git 时间线对撞**（67 R-3 只查了 6 份 EVD 卡 reviewedBy，TASK 卡 approvedBy 全库面此前无节做过；bcrun 仅量化过 35 卡非法计数、身份值与时间戳形态未做）。候选(b)（62—68 校验器缺口汇编）第七次落选。

**权威锚点**：[validate-task-card.ps1](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L224-L236) L224-236（approvedBy 占位/关键词排除、approvedAt TryParse）、L64-70（Test-Placeholder 定义：空串/`<`/`...`/`null` 字面量）、L181/L184（status×implAuth 联动）；AGENTS.md「Owner fields reference Principal/Team IDs」与 DEC-009「AI 不得自批」。

**对撞面**：卡面 approvedBy/approvedAt × status × implementationAuthorized × `git log -S`/`--diff-filter=A` 首次入库时间线。

**已核验**：
1. **approvedBy 全库仅 6 形态、126 卡封闭**（apc2 A1）：`User (plan execution authorization 2026-07-10)`×48（P1-001..012＋P2-001..010＋P3-001..008＋P4-001..010＋P5-001..008）、`User`×31（P0BR-047..078 无 057）、`User (plan execution authorization 2026-07-12)`×11（P1-013..018＋P2-011..013＋P3-009/010）、`User goal confirmation`×1（GOV-007）、占位 `<human-or-controlled-approval>`×3（079/080/081，DRAFT 诚实态）、`null`×32（P0BR-000A/000B/001..046 老代）。**DEC-009 自批关键词（ai|self|codex|trae|claude|cursor|qwen）扫描 0 命中**——形式合规面成立。
2. **48 卡共用同一 `2026-07-10T00:00:00Z`**（A2 最大簇，恰＝整点 UTC 即本地 08:00）＋null×32＋占位×3＋三卡同秒四组（048..061 的 04:42:50/07:00:06/08:40:35/10:15:33 各×3）＝批量粒度；singleton 时间戳仅 29 个（A3），集中在 062..078 新代与 GOV-007。
3. **审批戳与建卡同提交注入（Git `-S` 实证）**：`approvedAt: 2026-07-10T00:00:00Z` 的引入提交恰为五个 authorize 提交——`6280b12`(P1, 07:36:20+08)→`61d7ee9`(P2)→`59f40d2`(P3)→`39b53ce`(P4, 07:57:39+08)→`8ac366e`(P5, 07:57:44+08)；即卡首次入库那一刻审批字段已在卡面；五批建卡提交（07:36:20—07:57:44+08）全部**早于**其卡面审批戳（08:00:00+08），最小差值仅 **136 秒（P5 批 2分16秒）**——审批时间在被写入提交时尚未发生，不存在独立的事后人工批准动作，戳为生成模板默认值。
4. **32 张 null 批准人卡全部 status READY＋implementationAuthorized true**（A5：READY|null=32；A8：READY|true=123、DRAFT|false=3 严格对角、无违例）——老代 P0BR-001..046 以零批准人身份进入「已授权实施」态并提交入库；validator 默认模式对每卡报 approvedBy+approvedAt 各 1 issue（合计 64），bcrun 侧 35＝32 null＋3 占位 与 apc2 三方吻合（其中 0 张因此类被豁免）。
5. **approvedAt 格式并存**（A4）：UTC-Z×88、UTC-OFFSET(+08:00)×3（GOV-007/064/065）、null 字面量×32、占位×3——validator L233 仅 TryParse 不统一格式，「UTC timestamp」判据对两种时区写法均放行。
6. **引擎自纠（承 68 R-6 传统）**：apc v1 的 `Get-ScalarFM` 固定 60 行窗口把 crossCuttingPlan 长段卡的后部审批字段误判为空（A1 假象 46 卡空键）——与 68 R-6②（bc2 的 15 行窗口）同型缺陷；v2 改为扫至 front-matter 闭合 `---` 后与卡面 Grep 抽样（P1-002 L61-62、P1-013 L82-83）逐值吻合，终版以 apc2 为准。

**缺陷面**：
- **R-1 审批身份粒度退化**：91 张 READY+身份卡 approvedBy 仅 4 个文本族、`User` 不可解析到 Principal/Team 标识——AGENTS.md「Owner fields reference Principal/Team IDs」的受控引用要求对审批字段不适用也无人裁定；validator 只做关键词排除、不校验身份可解析性。
- **R-2 48 卡整点批量戳＝无个体批准事件**：同串＋同秒＋与建卡同提交注入（已核验③），与 67 R-3（6 份 EVD reviewedBy 同串）同构——「形式合规、实质批量」指纹从证据面扩展到任务面且规模大 8 倍。
- **R-3 32 卡零批准人 READY+authorized**：「AI 不得自批」门禁同时容纳「无人批准」——`null` 值实现授权声明无任何来源物证；这是全库最大的门禁空洞（默认模式虽报 issue，但 32 卡均已提交入库多年，报告无拦截力）。
- **R-4 审批戳晚于写入它的提交（future-dated）**：五批建卡提交均早于其卡面审批戳——P1 批差 23分40秒、P5 批仅差 136 秒，「批准事件」在提交时刻尚未发生；人工审批的唯一自洽方向应是「先批准后入库」（或至少批准事件有独立提交承载），实测坐实 R-2 的模板默认值推断（整点 08:00 生成）。
- **R-5 格式/时区双标**：UTC-Z 与 +08:00 并存无归一判据（对照 EVD 域 66/67 的同类发现——两域同缺口）。

**偏差与 unproven**：① Git 时间线 `-S` 对撞做了 P1..P5 五批＋抽样反查（P0BR-001 首入库 `1b2b7e7` 07-06、P0BR-047 `54f20f7` 09-17），**未对 126 卡逐一回溯**（外推时以「同代同提交」为假设）；② 「User」对应的真实自然人/角色无法在库内外部验证，只登记文本粒度；③ 审批戳与提交先后差值的「意图」属推断，客观事实仅为时间戳数值本身；④ flag 模式行为未复跑（承 68 偏差①）；⑤ 本节全程无批准人处置。

**整改待批（须人类先出/更新 READY+authorized Task Card，AI 不自批不自改）**：① **91 张身份卡批准人可解析化裁定**：approvedBy 绑定可解析 Principal 引用或由批准人显式声明「User 文本族即受控标识」并回写规范——二选一，不得维持现状双标；② **32 张 null 卡存量处置**：重签发或由批准人以单卡记录豁免（validator 默认模式拦截≠存量已入库卡的处置依据）；③ **48 卡批量戳重放**：R-2/R-4 卡群逐卡以真实批准事件时间重写（属改卡，AI 禁改）；④ validator 增强候选并入累计清单（62⑦/63⑥/64⑥/65⑥/66⑥/67②/68③）：approvedAt 格式归一（强制 UTC-Z 或强制带时区二选一）、同秒多卡批量指纹告警、可选的「首次入库提交含审批字段即告警」时间线检查；**下一节候选域**：(a) 126 卡**审批×入库×baseCommit 时间线全量回溯**（69 偏差① 外推面收口：`git log --diff-filter=A` 全卡首提交 × approvedAt × baseCommit 三者序关系矩阵，把 R-4 从抽样升为全量）；(b) 62—69 八节校验器缺口条款整合（汇编项第八次候选）。

→ 本项与 ①—69 一并不在本轮 AI 自决实施范围内。

### 70. 126 张 TASK 卡审批×入库×baseCommit 全量时间线回溯——75 卡审批戳晚于建卡入库（69 R-4 抽样升全量）、18 卡 baseCommit 晚于本卡入库、validator 同构引擎三代演进（2026-10-13，取证基准 HEAD `a022796`＋077..081 未提交工作树）

**立项查重**：候选(a)＝69 偏差①「未对 126 卡逐一回溯」外推面收口——126 卡 `approvedAt × 首次入库提交 × baseCommit 提交时间` 三者全量序关系矩阵。Grep 本 plan「diff-filter / 首次入库 / 全量回溯」仅 69 节自身 3 处命中，无先节做过全量时间线矩阵 → 定装。候选(b)（62—69 八节校验器缺口整合）第八次落选。

**权威锚点**：卡面解析复刻 [validate-task-card.ps1](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L149-L150) L149-150（`[IO.File]::ReadAllText` UTF8 ＋ `\A---\r?\n(?<body>.*?)\r?\n---\r?\n` Singleline 非贪婪首闭合切 front matter）＋ L20-33 `Get-Scalar`（对字符串跑 `(?m)^Key:\s*(?<value>[^\r\n]+?)\s*$`）＋ L64-70 `Test-Placeholder`（`.Contains('<')`）；首次入库提交＝`git log --diff-filter=A --name-only` 对 tasks 目录全史、新→旧覆盖取最早添加；baseCommit 时间＝`git show -s --format='%aI'`。

**对撞面**：每卡三维——rel1（approvedAt vs firstIngest）、rel3（baseCommit 提交时间 vs firstIngest）、族（approvedBy 归一：BATCH0710/BATCH0712/USER/GOVCONF/NULL/PLACEHOLDER/ABSENT_KEY）。

**已核验**：
1. **126/126 解析成功、FM_FAIL=0**；族计数 **BATCH0710=48 / USER=31 / BATCH0712=11 / NULL=32 / PLACEHOLDER=3 / GOVCONF=1** 完全复刻 69 apc2 六值谱系——69 的 approvedBy 分类经 validator 同构引擎独立复核成立，无需勘误（tl v1 行扫变体给出的 PLACEHOLDER=14/USER=20 系引擎缺陷，见缺陷面 R-4，判废）。
2. **rel1 全量分布**：`FUTURE_AT_INGEST=75 / BEFORE_INGEST=14 / NA_NO_APPROVAL=35 / UNCOMMITTED_CARD=2`。75＝BATCH0710 全 48 ＋ BATCH0712 十（P3-010 除外）＋ USER 族 17；35＝NULL 32 ＋ PLACEHOLDER 3。
3. **69 R-4 从抽样升为全量坐实**：BATCH0710 全 48 卡 rel1＝FUTURE_AT_INGEST（审批戳 `2026-07-10T00:00:00Z`＝本地 08:00 晚于五个建卡入库批 `6280b12/61d7ee9/59f40d2/39b53ce/8ac366e` 的 07:36—07:57）；T8 全 75 FUTURE 差值分布 **n=75 min=136 / median=749 / max=9174** 秒——min=136 恰为 P5 批（`8ac366e`@07:57:44 距 08:00），与修正后 69 已核验③/R-4「最小差 136 秒」逐秒吻合。
4. **BEFORE_INGEST 14 卡是唯一自洽方向（先批准后入库）**：`GOV-007`（leadSec=2920）、`P3-010`（370）、USER 族 12 卡（062/063/064/067/068/069/070/072/073/074/075/076，lead 3346—184800 秒）；USER 族另 17 卡（047..061/065/066/071）反为 FUTURE（delta 182—9019 秒）——**同族内审批与入库先后双向混杂**，无统一「批准→入库」流程约束。
5. **rel3（baseCommit vs 本卡入库）**：`BC_LE_INGEST=103 / BC_AFTER_INGEST=18 / NA=5`。BC_AFTER_INGEST 18 卡（P0BR-048..050/058..061/071 ＋ P1-012..018 ＋ P2-012/013 ＋ P3-009）——卡声明的实施基线提交在其自身卡首次入库之后才产生，「建卡时基线尚未在历史中存在」；T6b BC_UNRESOLVABLE 空＝TASK 卡 baseCommit 零幽灵（与 68 bc3「41 唯一值零幽灵」一致）。
6. **rel3 NA=5 精确归因**（tl4）＝P0BR-077..081 无 firstIngest（未提交工作树），其 baseCommit 全＝现势 HEAD `a022796`；T7 UNCOMMITTED 2 卡（077/078，USER 族 READY）＋ PLACEHOLDER 3 卡（079..081）同属该批未提交新卡。

**缺陷面**：
- **R-1 89 张已提交且有审批戳的卡中 75 张（84.3%）时间倒挂**：审批戳晚于卡首次入库——「批准事件」在被提交的卡面上指向未来时刻，仅 14 卡（15.7%）方向自洽；DEC-009「AI 不得自批」在时间线维度上对绝大多数批量卡无「先有批准、后有入库」的物证支撑。
- **R-2 USER 族流程双向性**：31 卡裂为 BEFORE 12 / FUTURE 17 / UNCOMMITTED 2——人工审批代际（047..078）内部对「批准 vs 入库」先后无一致约定，与 BATCH 族系统性 FUTURE 并存，说明门禁无时序校验。
- **R-3 18 卡 baseCommit 时间晚于本卡入库**：这是 68（baseCommit×HEAD 祖先，121 卡漂移）未覆盖的正交维度——68 查「基线是否仍在 HEAD 祖先链」，本节查「基线是否早于卡诞生」；两维交集外仍有 P1-012..018/P2-012/013/P3-009 等卡基线时序自相矛盾。
- **R-4 引擎三代自纠（承 66 R-7/67 R-6/68 R-6/69 已核验⑥ 第五代传统）**：① tl v1 逐行 `Get-Content`＋行扫 `Get-ScalarFM` 把卡面真值 `User` 的 062..078 大批错分（PLACEHOLDER=14/USER=20，与 apc2 3/31 矛盾）；② tl2 差分暴露行扫病态（067 `approvedBy` 读空但同卡相邻 `approvedAt` 读到、闭合 `---` 探测与文件真值 L90 不符），承认知因不明；③ tl3 复刻 validator 权威解析后六值谱系完全回归——**教训：唯一可信卡面解析＝validator 真跑逻辑同构复刻，69 行扫引擎结果正确系巧合、已由权威引擎替代坐实**。

**偏差与 unproven**：① firstIngest 取「文件路径最早添加提交」，若历史存在重命名/迁移则失真（未逐卡核 `--follow`）；② rel3 用 baseCommit 的 author date（`%aI`），rebase/cherry-pick 会重写 committer date 使基线时间偏移，未区分 author/committer；③ 差值（leadSec/deltaSec）仅为时间戳数值，「意图」不推断（承 69 偏差③）；④ flag 模式（-CheckChangedPaths/-CheckCompletion）未实跑（承 68/69 偏差）；⑤ 本节全程无批准人处置，所有整改为待批项；⑥ 全静态取证，未 checkout 复现卡实施时基线（承 66/67 边界）。

**整改待批（须人类先出/更新 READY+authorized Task Card，AI 不自批不自改）**：① **75 张 FUTURE 卡（重点 BATCH0710 全 48）审批戳重放**——以真实批准事件时间重写至早于建卡入库提交（属改卡，AI 禁改），或批准人显式声明批量戳为受控占位并回写规范；② **18 张 BC_AFTER_INGEST 卡基线时序裁定**——确立「baseCommit 提交时间必须早于卡首次入库」判据与否，若确立则逐卡重绑基线；③ **validator 增强候选并入累计清单**（62⑦/63⑥/64⑥/65⑥/66⑥/67②/68③/69④）：新增「approvedAt 晚于首次入库提交即告警」与「baseCommit author date 晚于卡首次入库即告警」两条可选时间线模式；**下一节候选域**：(a) 62—70 九节校验器缺口条款跨域整合（门禁元域收口，汇编项第九次候选，若批准人倾向合并可直接执行）；(b) 126 卡 `requirementRefs/acceptanceScenarioRefs × EVD 卡` 的**被引 ID 语义一致性**全库对撞（69 收窄掉的是 ref 存在性维——validator 已强制；被引 ID 指向内容是否语义匹配该卡 AC 未做，但增量面存疑，须先查重）。

→ 本项与 ①—70 一并不在本轮 AI 自决实施范围内。

### 71. 62—70 九节校验器缺口条款跨域整合 × validator/CI 现势覆盖度对撞——33 条台账三分处置（Ⅰ 已实现未接 CI 九条 / Ⅱ 真缺口约二十条 / Ⅲ 已覆盖四条）＋新发现 CI 跑浅版 validate-spec 变体（2026-10-13，取证基准 HEAD `a022796`＋077..081 未提交工作树）

**立项查重**：候选(a)＝70 末行预告的「汇编项第九次候选」，本节升格为**分析节**——非条款文字汇编，而是九节条款全集 × [validate-task-card.ps1](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) / [ci.yml](file:///d:/workspace/opengeobot/HarnessDG/.github/workflows/ci.yml) 现势代码逐条对撞，产出「规则已存在但调用态未接入／真缺口需新判据／已覆盖仅需裁定」三分处置（此对撞面九节从未并置）。候选(c) OpenAPI↔MCP↔代码双向差集判废（㉒㉓/50/58/㊿多次覆盖）；候选(b) 被引 ID 语义一致性顺延下节。取证中独立发现双 validate-spec 脚本漂移（缺陷面 R-0），与 Ⅰ 类同属「CI 调用态」族，并入本节。

**权威锚点**：
- CI 调用面全库枚举：Glob `.github/workflows/` 全量＝仅 ci.yml 一文件；其内 validate 调用仅两处——[L125](file:///d:/workspace/opengeobot/HarnessDG/.github/workflows/ci.yml#L125) `./tools/validate-spec.ps1` ＋ [L330-L350](file:///d:/workspace/opengeobot/HarnessDG/.github/workflows/ci.yml#L330-L350)（L341 变更集 `--diff-filter=ACMR` 圈定改动卡、L348 逐卡 `-CheckChangedPaths`、L338 无基线即 exit 0）。**`-CheckCompletion` 与深版 spec 校验在 CI 全库零出现**。
- validator 现势码八段：L211-212 卡面 level 枚举；L224-235 approvedBy 占位符＋AI 禁词、approvedAt 仅 `TryParse`（接受无 Z 本地时间串、无时序检查）；L308-317 卡面 scenarioId 严格格式＋catalog 表行存在；L379-380 plan level 对账；L72-78 `Get-LevelNumber` 非 `^E[1-5]$` 返回 0（组合值/非法值塌陷）；L570-572 默认态 evidenceManifests 仅 ⊆allowedPaths 不查存在；[L659-L782](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L659-L782) CheckCompletion 态全套 EVD 复核十三项（L680 存在性、L694 taskId 回绑、L709-713 governance 结构 PASS 正则、L717-719 sha==HEAD、L729 exitCode、L732-734 level、L738-747 proven scenarioId 正则收集、L749 notProven 必须 `[]`、L753 redaction、L757 skippedTests 必须 `[]`、L763-774 review 三项、L777-781 全 scenario 覆盖闭环）；L784-804 汇总退出。
- 双 spec 脚本实测（本节跑）：`tools/validate-spec.ps1` 72 行/2758B/SHA256 `59049913…`——仅查 12 个文件存在＋字节下限，打印 `AI Spec Gate: PASSED`；`docs/ai-spec/tools/validate-spec.ps1` 253 行/10451B/SHA256 `656BE703…`——manifest 148 docs/稳定 ID 752 深校验。两版均 EXIT=0（Temp vs_ci/vs_doc.out.txt）。
- 条款原文锚点行（Grep「校验器缺口」21 命中）：承前链 L2569(57⑪)/2603(58⑬)/2638(59⑪)/2672(60⑨)/2714(61⑩)/2741/2770；九节 L2797(62⑦)/2812(63⑥)/2854(64⑥)/2882(65⑥)/2912(66⑥)/2939(67②)/2967·2973(68③)/2996(69④)/3024(70③)。

**对撞面**：九节新增 24 条（62⑦3＋63⑥4＋64⑥3＋65⑥3＋66⑥2＋67②2＋68③2＋69④3＋70③2）＋承前五节 9 条（57⑪3/58⑬2/59⑪1/60⑨1/61⑩2）＝33 条，逐条判「validator 是否已有该路径→该路径在哪个调用态→CI 是否执行该调用态」。

**已核验（Ⅲ 类：已覆盖，仅需批准人裁定关闭条款）**：① 卡面 `requiredEvidenceLevel` E1-E5 枚举已查（L211-212）→67② 后半在卡面维已覆盖；② scenarioId 字面格式＋catalog 行存在已查（L308-317，严格正则天然拒绝区间记法 `~` 与自造后缀）→62⑦/63⑥ 对应条卡面维覆盖；③ plan level 级差对账已查（L379-380）；④ ref 存在性 69 立项段已核成立。

**缺陷面**：
- **R-0 双 validate-spec 漂移（本节新发现）**：AGENTS.md「AI implementation gate」 mandated `./docs/ai-spec/tools/validate-spec.ps1`，CI 实际执行 `./tools/validate-spec.ps1` 浅版——浅版对需求漂移、悬空 ID、752 个稳定 ID 的任何内容性缺陷**永不可能失败**，CI 绿灯「AI Spec Gate: PASSED」不覆盖深版任何校验面。文档↔CI 矛盾，按 DEC-009 纪律登记上报、AI 不改。
- **R-1 Ⅰ 类：规则已实现但 CI 永不接入（九条）**：CheckCompletion 态十三项复核（L659-782）与九节条款直接对应者——Manifest 存在性(63⑥)、EVD level↔卡 required 对账(63⑥/67②/57⑪)、notProven 必须空(63⑥ 自称-PASS 互斥的 EVD 面等价物)、sha==HEAD(68③ 半件)、taskId 回绑(62⑦)、governance 结构 PASS(62⑦ 半件)、全 scenario 覆盖闭环(L777-781)、exitCode、redaction——**早已写进码，CI 却只跑 `-CheckChangedPaths`**。68 R-2「门禁强度取决于调用者传参」在此坐实到机制：接通调用态即回收九条，零新判据。
- **R-2 Ⅱ 类：validator 无路径（约二十条），分五族**：(a) **时间线族 5**（70③ approvedAt×首次入库、baseCommit author date×入库；69④ 格式归一/UTC-Z 强制、同秒批量指纹、首入库含审批字段告警——均需 git 历史数据，现仅 L229-235 `TryParse`）；(b) **证据对象真实性族 4**（68③ EVD commitSha `git cat-file` 级存在性——L717 仅等于 HEAD 不验任意历史 sha；64⑥ Manifest 命名↔目录双代并存静态报告、proven 计数断言绑 artifact；66⑥ 幽灵 PASS：scenarios=[]×status=PASS 至少 WARNING）；(c) **跨卡/跨源族 5**（64⑥ 跨卡 execution 时间窗重叠、65⑥ 跨源并置 Q-6 型＋WB 行×git log、66⑥ AC→卡反向覆盖报告、69④ 同秒指纹的跨卡聚合面）——**架构注记：现 validator 是 `-TaskPath <单卡>` CLI，此族需跨卡/git/邻库并置，现有入口无法承载，须 validator v2 或审查脚本族作宿主**；(d) **卡面内容一致性族 3**（60⑨ 卡正文↔Front Matter 不一致不校验、57⑪ 恒真 diff 命令＋blockedBy 指向已解除无关 Q、59⑪ EVD 跨 REQ scenario 绑定合法性——末条待批准人裁定）；(e) **EVD 面静默塌陷族 3**（61⑩ `proven[].scenarioId` 非标值不匹配 L740 正则即被**静默丢弃**不报 Error——只有「越界」有路径、「格式非法」无路径；67② EVD 面 evidenceLevel 非法值经 `Get-LevelNumber` 塌 0 → L732 误报「below requirement」而非「枚举非法」，组合值 `E1+E3` 在 CheckCompletion 态恒误报、默认态根本不读 EVD 文件；63⑥ 卡面自称 completion-PASS×required=E2 互斥检测）。
- **R-3 半覆盖族 2**：62⑦ governance 四项**声称执行复核**——L709-713 只验 EVD 面写了 `result: PASS`，验不了声称有真实执行凭据（需运行产物/CI run 记录并置）；67② 级差「静态报告」模式（现仅 Error，无 WARN/报告态）。承前裁定：57⑪ level 错配条归 Ⅰ、58⑬ 契约同步面/inputSchema 两条归 Ⅱ(d) 族且宿主在 spec/契约审查族。

**偏差与 unproven**：① 覆盖度判定基于 validate-task-card.ps1 804 行全文静态精读分段核证＋双 spec 实跑 EXIT=0；`-CheckCompletion` 态本身未实跑（承 68/69/70 偏差）——Ⅰ 类「已实现」以源码为准，其十三项复核实际触发行为 unproven；② 「CI 从不执行」限 GitHub Actions 现势面（workflows 全库仅 ci.yml 已 Glob 穷尽）；仓库内未见 Gitea Actions/Jenkins 配置，但外部流水线是否存在 unproven；③ 本节无新数据取证，全部数字引自九节既有实测；④ 无批准人处置，整改全为待批项；⑤ R-0 浅版脚本历史动机未查 blame（不推断意图，承 69 偏差③）。

**整改待批（须人类先出/更新 READY+authorized Task Card，AI 不自批不自改）**：建议将 33 条台账合并为一份「**门禁调用态接入 × validator v2**」任务书由人类立 GOV 卡：① R-0 双脚本合一（CI 指向深版或并入浅版职能）——改动最小、回收面最大；② `-CheckCompletion` 接入合并收口门禁或对全库 EVD 关联卡跑批（Ⅰ 类九条零新判据即刻闭环）；③ Ⅱ 类 (a)(b)(e) 十二维以「单卡＋git 只读」宿主入 v2 任务书，(c) 族五维与 58⑬/66⑥ 反向覆盖入审查脚本族，(d) 族三条先裁定后实施；④ R-3 两条的凭据面并置方式须批准人定义。
**下一节候选域**：(a) 126 卡 `requirementRefs/acceptanceScenarioRefs × EVD 卡` 被引 ID **语义一致性**对撞（70(b) 承继，第十次查重——若对撞结果与存在性维重合即判废）；(b) governance 声称执行复核首次实证抽样：已提交 EVD 卡 `governance.*: PASS` 声称 × 其 execution 命令/产物可信度并置（R-3 半覆盖族的实证底料）。

→ 本项与 ①—71 一并不在本轮 AI 自决实施范围内。

### 72. 130 张 EVD 卡 governance 声称执行 × 事实一致性首次全库普查——completionValidation 六形值谱失控、73 卡幽灵 sha＋21 占位＋2 短 sha、6 卡 FAIL×accepted 快照双轨、三卡 `-CheckCompletion` 历史首跑收口四节累计偏差（2026-10-13，取证基准 HEAD `a022796`＋077..081 未提交工作树）

**立项查重**：候选(b)＝71 末行预告「governance 声称执行复核首次实证抽样（R-3 半覆盖族的实证底料）」，本节兑现；候选(a)「被引 ID 语义一致性」第十次查重判废（存在性维 62⑦/63⑥/70③ 已覆盖，语义维与 61⑩/66⑥ 重合）。取证方法三代引擎：gov1 正则值谱普查（130 卡矩阵）→ gov2/gov3 块抽取正则连续两代落空 → **gov4 逐行数组解析器一次成功**（确立「连续两代正则失败即改逐行扫描」自纠规则，教训并入偏差④）。

**权威锚点**：
- 普查对象：`docs/ai-spec/tasks/evidence/` 全库 **130 卡**；status 谱 PASS=70/PARTIAL=36/DRAFT=24；completionValidation 值谱**六形** null=44/EXPECTED_FAIL=37/**PASS=32**/FAIL=14/notRun=2/NOT_RUN=1（后两形同义异写，大小写失控）；矛盾族承 gov1：38（PASS×complete≠PASS）/29（PASS×dirty=true）/66（PASS×notProven 非空）/6（PASS×FAIL×accepted=true）。
- commitSha 物理核验总账（gov4＋`git cat-file -e "<sha>^{commit}"` 逐卡实跑，HEAD=`a0227961…`）：**FULL-ABSENT=73**——`7dc22d44…`×71（首例 [EVD-P0BR000A-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR000A-001.yaml#L26)）＋**第二幽灵 `4c7dfc83…`×2**（[EVD-P2006-001](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml#L31)/[EVD-P5001-001](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5001-001.yaml#L33)）；ghost×status 交叉 **PASS=33**/PARTIAL=21/DRAFT=19。**FULL-EXISTS=34**。**NON-HEX-PLACEHOLDER=21**：`pending-commit`×5（P1013/1014/1015/P2011/P2012）＋「40 位 sha＋行内注释『== baseCommit；本卡改动尚未提交』」形×14（EVD-P0BR-066..078＋P2009）＋`null # 基于 2026-07-02 快照`×2。**SHORT=2**：`178ee6b`（P3010 L26）、`6aad901`（[P3009-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P3009-001.yaml#L26) L26）。
- **既有自证锚（本节新发现）**：[EVD-P5007-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5007-001.yaml#L109-L115) 条款 8「EVD 绑定同一发布候选 Commit」verdict: **FAIL**，其 A2 自证 117 卡时代 81 份不可解析（72×7dc22d44＋2×4c7dfc83＋7×pending-commit）＋33 份绑历史＋2 份 null，L179 并承诺「统一重绑…至发布候选 Commit」——幽灵 sha 族**人类治理面早已登记在案**，但至今（130 卡）7dc22d44 声称数仅从 72 消到 71，重绑承诺基本未兑现；与 69④ 审批字段「快照豁免」同构：缺口知情、整改挂账。
- 六卡快照双轨明细（FAIL×accepted=true 全量细读）：P1015/1016/1017/1018/P2013/P3010 的 governance 前三项全 PASS×completionValidation=FAIL×accepted=true，reviewedBy 恒串 `User (plan execution authorization 2026-07-12)`，reviewedAt **六卡同秒** `2026-07-12T10:05:00Z` 且为**全库仅有的 6 个 reviewedAt 值**（reviewedBy 谱其余 124 卡=null）——同秒＋恒串坐实批量签发；sha 各异：pending-commit／30059da7(EXISTS)×2 卡（P1016；1593e3b3 另见 P1018/P2013）／b4d244a2(EXISTS)／178ee6b(SHORT)。[EVD-P1018-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P1018-001.yaml#L3-L8) 卡头 L3-8 有 2026-09-23 **人类后补注释**明言「波次出口快照已被人类 accept，字段保持原样不改写」→ 定性为**治理双轨**：人类快照授权轨 × validator 现势判据轨互斥——该 6 卡在 validator 判据下**永不可能复现** PASS，非单纯数据缺陷。
- `-CheckCompletion` **历史首次实跑**（三卡抽样，Start-Job 150s 超时防护，全部 <150s 无挂起）：TASK-P0BR-046→14 issue EXIT=1（卡面 9 项 baseCommit/approvedBy/scenarioEvidencePlan 等＋EVD 面 5 项 sha≠HEAD/level below/review 三非）；TASK-P1-015→**418 issue**（主因：CheckCompletion 态连带对**全工作树未提交 diff** 逐路径做 allowedPaths 校验——实跑新见的宿主行为，静态精读未预见）；TASK-P0BR-069→92 issue（sha≠HEAD×dirty×notProven×skipped×review 三非，与卡自述 EXPECTED_FAIL 语义吻合）。**68/69/70/71 连续四节「flag 未实跑」偏差就此收口**；078 预言的指数回溯挂起未在三卡复现。
- validator 机理三连（[validate-task-card.ps1](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)）：① [L704-708](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L704-L708) governance foreach 仅 `specValidation/taskValidation/changedPathValidation` 三项——**completionValidation 的声称值从不被校验**，32 张「PASS」是不可证伪字符串（R-3 半覆盖实证坐实）；② [L709-710](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L713) `result:\s*PASS\s*$` 行尾锚定 → 内联注释形（Grep `result:\s*PASS\s+#` 6 处：EVD-P0BR-066:19,25、068:22、069:27,30、P2009:49）实跑必报 not-PASS；③ [L95-105](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L95-L105) `Get-IndentedScalar` value 组 `[^\r\n]+?` **连行尾注释一并吞入** → 14 张「sha＋注释」卡即使 sha 部分等于 HEAD 亦字符串恒不等。EVD 面 evidenceLevel 组合值 3 卡（[EVD-P0BR044](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR044-001.yaml#L43):43、045:45、046:45 均 `E1+E3(+E4)` 形）→ 61⑩/67② 塌陷族实卡坐实。
- 执行塌卡（gov4 b 段；status=PASS×exec 不符，validator L726-731 判据若接通即报）：exec 谱 result PASS=119/PARTIAL=9/FAIL=1/带注释形=1，exitCode 0=121/1=7/空=2；**5 卡**：P0BR-049/062/063（exec=PASS×exit=1×EXPECTED_FAIL）、P1018（PARTIAL×exit=1×accepted=true）、P2006（exit 空×幽灵 sha×complete=null）。

**已核验（机理性）**：① sha==HEAD 比较（L717-719）在物理层面即已不可满足——73 幽灵＋21 占位＋2 短 sha＝**96/130 卡的 commitSha 字段在今日 HEAD 下不可能通过**任何形态的相等校验，与实跑 418/92 issue 中「source commit must equal current HEAD」项相互印证；② 六卡「FAIL×accepted=true」与 P5007 条款 8 自证共同表明：**库内存量 EVD 的 governance/commitSha/review 字段是历史快照的不可再生记录**，CheckCompletion 按现势判据对其全员必然 FAIL——「接通 `-CheckCompletion`」（71 整改待批②）须先由人类裁定存量豁免轨，否则接即全红。

**缺陷面**：
- **R-1（最重）声称不可证伪**：32 张 completionValidation=PASS ＋ 71 卡幽灵 sha×status=PASS 33 卡交叉 ＋ 66 卡 PASS×notProven 非空——「PASS」字符串的签发与其判据（validator 现势码）之间**无任何机械绑定**，正是 62⑦/R-3 半覆盖族的全库量化。
- **R-2 双轨未挂账**：6 卡快照豁免有 P1018 人类注释明示，但 P5007 条款 8 承诺的「统一重绑 81 份」与 130 卡现量间无后续卡登记消项进度——知情整改断链。
- **R-3 宿主缺口**：实跑揭示 CheckCompletion 对全工作树 diff 的连带校验（418 issue 主因）——「对全库 EVD 关联卡跑批」的 71 整改②若不加 `--diff` 基线隔离，在脏工作树下产出的将是千级 issue 噪声而非信号。
- **R-4 值谱卫生**：notRun/NOT_RUN 同义异写、EXPECTED_FAIL 语义重载（既表「预期失败已核」又表「未跑完」）、exec.result 带注释形×1——枚举无约束面（61⑩(e) 族实证扩展）。

**偏差与 unproven**：① 三卡实跑是**抽样**，其余 29 张 PASS 声称卡未逐一实跑（单卡 418 issue 级输出成本＋宿主噪声，R-3 已注记），「全 32 张在今日 HEAD 下不可复现」系由 sha 物理核验＋机理①③外推，外推面 unproven；② 内联注释形以 Grep 6 处＋gov1 值谱长串为权威，gov4 (c) 段 raw 扫描落空（解析器归一化吞注释，同 ③ 机理的自家复现）；③ 幽灵 sha 的历史成因（rebase/改写/失效仓库）未查 reflog 与远端（不推断意图，承 69 偏差③）；④ 正则两代失败＋逐行解析成功为**方法论留痕**：本引擎对 YAML 块抽取的确立规则＝逐行状态机；⑤ P5007 自证数字（72 orphan）与实测（71）差 1 卡的归属未逐卡对账（117→130 卡代际差异内），仅作趋势引用；⑥ 无批准人处置，整改全为待批项。

**整改待批（AI 不自批不自改）**：① 71「门禁调用态接入 × validator v2」任务书须补入本节 R-3 宿主前提（diff 基线隔离）与 R-1 存量豁免轨裁定（6 卡快照＋73 幽灵 sha 的处置：重绑、或声明「快照区只读」并冻结判据）；② P5007 条款 8 的 81→96 现量漂移应由人类立**消项进度卡**（可机检：`git cat-file` 全库跑批矩阵随 wave 更新）；③ R-4 值谱卫生并入 validator v2 枚举校验面（61⑩(e)/67② 同族）；④ 六卡双轨矛盾提交批准人裁定：修 validator 判据以承认「快照豁免」标记，或重发人类授权——两轨并存的现状使任何完成度声明不可审计。

**下一节候选域**：(a) P5007 整改承诺兑现全量对账：其 L179 承诺清单（81 重绑＋33 drift＋2 null）×130 卡现量逐族消项矩阵（第二幽灵、pending-commit 7→5 等消长是否有计划地发生）；(b) 130 卡 `taskId` 回绑 × `tasks/` 目录 TASK 卡全集**反向孤儿**对撞（EVD→TASK 存在性已查正向，TASK→EVD 缺卡与 EVD→不存在 TASK 的反向面未并置，第十一次查重——若与 63⑥ Manifest 存在性维重合即判废）。

→ 本项与 ①—72 一并不在本轮 AI 自决实施范围内。

### 73. 130 卡 taskId×Manifest 双向对撞 × P5007 重绑承诺兑现矩阵——正向仅 2 悬空、反向仅 3 缺失（DRAFT 预占）、5 组重名「新代诚实卡僵尸化 × 旧代幽灵卡仍被校验」、批量重绑 71 族落幽灵仅 10 族兑现（2026-10-13，取证基准 HEAD `a0227961`＋工作树 077–081 未提交）

**立项查重（第十一次）**：承 72 候选域 (a)(b) 并置。本轮实测：候选 (a)「P5007 L179 承诺消项矩阵」与候选 (b)「taskId 双向对撞」均**未与 63⑥ Manifest 存在性维重合**——63⑥ 只查 manifest 指向文件是否存在（单向、正向），本轮新增 (i) taskId 反向孤儿（TASK→EVD 缺引用）、(ii) 双代重名卡的**绑代选择**、(iii) sha 字段的 **git 历史多值消项**（承诺兑现的时间维）。三点均为 63⑥ 之外的新轴，(a)(b) 成立并合并成节。

**取证方法（gov5→gov9，逐行状态机，正则块抽取禁用）**：gov5 = 顶层 `^taskId:` 正则落空（130 卡全 `<NULL>`，与手工 Read 矛盾）→ 坐实 taskId 是 `task:` 块**二阶键**、方法论确立「EVD 卡字段一律走缩进状态机」（同 72 偏差④留痕）；gov6 = 嵌套状态机跑双向对撞；gov7 = Manifest 三向对撞（文件名↔卡内 taskId↔TASK.binder）；gov8 = 130 族按去 `-NNN` 归并五代重名后，逐族 `git log`×`git show` 提取 commitSha 全历史值、与现值差集分桶；gov9 = 5 组重名 `git log --reverse` 首末提交时序。所有脚本置于系统临时目录、不入库。

**taskId 双向对撞（干净）**：正向（EVD→TASK 存在性）——130 卡仅 **2** 张悬空：`EVD-P0B-ENG-001`、`EVD-P0B-V01-V12`，其 taskId 值为 `null # 无独立 Task Card，基于 p0b-exit-catalog…`（**带尾注释的 null**，validator `Get-IndentedScalar` 视作含注释串而非纯 null，但反正二者本无独立 TASK 卡，非缺陷）；反向（TASK→EVD 引用）——126 张 `tasks/TASK-*.md`（顶层，子目录 0）仅 **3** 张无 EVD 引用：`TASK-P0BR-079/080/081`，三者均 `status: DRAFT`＋`implementationAuthorized: false`＋未提交（`??`），卡内 `evidenceManifests` **预指不存在的** `EVD-P0BR-079/080/081-001.yaml` → 定性「**预占非缺陷**」（MANIFEST-DANGLING=3 由此而来，是未来卡的先行声明，非历史卡失联）。凡被绑卡 taskId↔文件名↔binder 三向 **MISMATCH=0、REBIND-MISMATCH=0**。

**双轨证据代际（核心发现，方向已用 gov9 时序＋cat-file 更正）**：5 个编号存在**双卡同 taskId**——`014/017/023/024/041` 各有**有连字符** `EVD-P0BR-NNN-001.yaml` 与**无连字符** `EVD-P0BRNNN-001.yaml` 两代：
- **新代 = 有连字符**（`6fc6d1c` 2026-09-17 / `0e6b9ed`·`c38cafc` 2026-09-21 首提交）：`commitSha` 实测**全部 EXISTS**（`a20c3fff`/`9742867`/`c6b9e2cc`/`7b88863`/`f844aa58` 均可解析），其中 `014/017/041` 三张**诚实标 `status: PARTIAL`**（[EVD-P0BR-014-001.yaml#L3](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-014-001.yaml) 人类注释「非 PASS：requiredEvidenceLevel 为 E3，本次仅取得 E2；不得据此声明完成」）；
- **旧代 = 无连字符**（`1b2b7e7` 2026-07-06 首提交 / `2eb90b0` 2026-07-11 末次）：`status: PASS`、`commitSha: 7dc22d44`（**GHOST**，cat-file 实锤不可解析）；
- **TASK 全部绑旧代**：`TASK-P0BR-014/017/023/024/041` 的 `evidenceManifests` 均指无连字符旧卡，且五张 TASK 末次提交全停在 `1b2b7e7` **2026-07-06（从未换绑）**。
- **校验器后果**：[validate-task-card.ps1#L693-L696](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) 只沿 TASK.manifest 读**所绑文件** → 读到的永远是 07 月旧代（PASS＋幽灵 sha，校验"通过"），而 09 月人类新写的**诚实新代（真实 sha＋PARTIAL）因无任何 binder 引用而永不被校验器看见**——EVD-NOT-REFERENCED=7（5 张 hyphen 新代僵尸＋2 张 null 卡）。**换代方向 = 新卡僵尸、旧幽灵卡仍被验收**，即人类的重做劳动被绑定关系架空。

**P5007 承诺兑现对账（gov8 消项矩阵，130 族 = 71+2+10+40+7）**：[EVD-P5007-001.yaml#L179](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5007-001.yaml) 承诺「统一重绑 81 份（72×`7dc22d44`＋2×`4c7dfc83`＋7×pending-commit）＋33 drift＋2 null 至发布候选 Commit」。逐族 sha 历史消项实测：
- **REBOUND-TO-GHOST = 71**：sha 字段 git 历史出现 ≥2 个不同值（`hist_extra≥2` 为主），当前统一落幽灵 `7dc22d44` → 批量重绑**确实执行过**，但**重绑目标本身失效**，承诺「重绑至发布候选 Commit」在 71 族上**落空**（含 P0BR000A/000B/001-046、P1001-1012、P2001-2010、P3001-3008、P4001-4010、P5003 等），与 72 新发现的「7dc22d44＝批量重绑产物」互证；
- **REBOUND-TO-RESOLVABLE = 10**：当前值可解析（治理族 P5001/P5002/P5004/P5005/P5006/P5007/P5008＋P0BR-058＋P2006/P2009）→ 承诺**唯一真正兑现**的 10 族；
- **REBOUND-TO-PLACEHOLDER = 2**：`EVD-P3009`→`6aad901`、`EVD-P3010`→`178ee6b`（短 sha，cat-file **EXISTS**＝物理存在的截断记法，非幽灵，修正 72 对 SHORT 的初判）；
- **SINGLE-VALUE-HISTORY = 40**：sha 字段自写入未变（含全部 hyphen 新代＋P0BR-047~078 系列，多为真实 sha）；
- **NEVER-REBOUND（placeholder/short/null）= 7**。
净结论：81 承诺中真正收敛到可解析发布候选 Commit 的仅 **10/81**，71 族被重绑进幽灵，pending-commit 7→5（P3009/P3010 离组转短 sha）。

**已核验（机理性）**：`EVD 校验视野 = 123/130`（130 卡 − 7 未被引用）；双向对撞面（正向 2、反向 3）与重名绑代、sha 历史消项均由 gov6/7/8 全库跑批得数、脚本可复现；5 组重名代序由 `git log --reverse` 时间戳直接判定、非推断。

**偏差与 unproven**：① REBOUND-TO-GHOST 的 71 族「历史多值」仅证 sha 字段**被改过**，其**历史值本身曾否可解析未逐值 cat-file**（可能是坏→坏平移），故此桶只坐实「当前＝幽灵且发生过重绑动作」，不坐实「可解析被改坏」——外推面 unproven；② 短 sha `6aad901`/`178ee6b` 的完整 40 位对应 commit 未反查（仅确认前缀唯一解析），记法规范性存疑但物理存在已证；③ 079–081 预占不判缺陷、其未来落地时的绑代正确性留待卡提交后复查；④ gov8 逐族全历史回溯对 `hist_extra` 大的卡（P5005=6）采样深度足够但未覆盖 reflog/远端，与 72 偏差③同界；⑤ 无批准人处置，全部为待批项。

**整改待批（AI 不自批不自改）**：① **换绑五组**——`TASK-P0BR-014/017/023/024/041` 的 `evidenceManifests` 应从无连字符旧代改指 09 月有连字符新代（真实 sha），并裁定旧代幽灵卡是删除、归档、还是标 superseded（涉及 tasks/ 与 evidence/ 目录改动，须人类授权 Task）；② validator v2 应增**僵尸卡检出**（EVD 存在但无 binder 引用 → WARN/FAIL）＋**同 taskId 多卡检出**（本轮 5 组重名即此族，与 72 R-4 值谱卫生同轨）；③ P5007 L179 承诺的 81→现量应由人类立**消项进度卡**引用本 gov8 五桶矩阵作机检基线；④ REBOUND-TO-GHOST 的 71 族重绑目标 `7dc22d44` 本身须先定「是哪一次发布候选、为何失效」（承 72 整改②），否则重绑承诺无法闭合。

**下一节候选域**：(a) 123 张在校验器视野内卡的 **status=PASS × requiredEvidenceLevel 自洽性**普查（本轮 hyphen 新代暴露「E3 要求只取得 E2 却曾标 PASS」，正向扫旧代 PASS 卡是否普遍存在 level 越级，第十二次查重——若与 63⑥/71 evidence-level 维重合即判废）；(b) `evidenceManifests` 之外的 **TASK.boundaryPaths/allowedPaths × 卡内 commitSha 实际改动文件集**对撞（sha 若可解析则 `git show --name-only` 提实际 diff 路径，验证「allowedPaths 窄声明」是否被 sha 指向的真实提交越界，第十二次查重）。

→ 本项与 ①—73 一并不在本轮 AI 自决实施范围内。

### 74. 65 张可判定 PASS 证据卡 `evidenceLevel × requiredEvidenceLevel` 越级普查——27 张越级（41.5%）全部在校验器视野内、0 僵尸，越级判据被 `-CheckCompletion` 门态系统性休眠、25 张 E2 冒充 E4 与旧代幽灵族逐一对齐、2 张 E3 差级与 72 双轨族交叠（2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十二次）**：承 73 候选域 (a)。核对既有覆盖：72 节 L3060 做过 `status` 谱（PASS=70/PARTIAL=36/DRAFT=24）与「PASS×completion/dirty/notProven/accepted」矛盾族，但**未做 `evidenceLevel`（execution 块）× `requiredEvidenceLevel`（TASK front-matter）的对撞**；72 节 L3065 的 `E1+E3` 组合值是「解析塌陷」维（3 卡），非「越级」维；1866/2337/2138 三处均为**单卡个案**登记（cfg-001/058/P2-002），无全库普查。validator 虽有 [L732-733](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L732-L733) 越级判据，但**从未对存量卡跑过**（72 三卡 `-CheckCompletion` 是历史首跑且本就 sha 全红）→ 全库越级面**此前无任何节量化过**，(a) 成立为新轴。候选 (b)「allowedPaths×实际 diff 对撞」因 sha 可解析面仅 12 张、采样过窄且与 71/72 幽灵面重合，**暂缓不成节**。

**权威锚点**：判据 [validate-task-card.ps1#L732-L733](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L732-L733)（`evidenceLevel < requiredEvidenceLevel → Add-Error`，`Get-LevelNumber` 对 `E1+E3` 组合值返回 -1 天然跳过）；越级首例 [EVD-P0BR014-001.yaml#L3](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR014-001.yaml#L3)（`status: PASS`）× L43（`evidenceLevel: E2`）× [TASK-P0BR-014.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-014.md) front-matter（`requiredEvidenceLevel: E4`）；双轨交叠 [EVD-P1016-001.yaml#L22](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P1016-001.yaml#L22)（`completionValidation.result: FAIL`）× L3（`status: PASS`）× L45（`evidenceLevel: E3`，req=E4）——单卡叠合 72「FAIL×accepted 双轨」与本节「越级」。

**取证方法（gov10/gov11，逐行状态机，正则块抽取禁用，承 72 偏差④/73 方法论）**：gov10 = 遍历 130 EVD 卡抽 `task.taskId`（二阶键）＋`execution.evidenceLevel`（二阶键）＋顶层 `status`；遍历 126 TASK 卡抽 front-matter `requiredEvidenceLevel`；对每张 `status: PASS` 卡，若 taskId 命中 TASK 且两侧 level 均可解析为单值 E1-E5，则比大小；同时用 TASK.manifest 的 `evidence/…yaml` 引用集标记每张 EVD 是否 BOUND。脚本置系统临时目录、不入库。

**普查总账（130 卡闭合）**：`status` 非 PASS=60（承 72 谱）；PASS=70 分解 = 越级 **27** ＋ 达标 **38** ＋ 组合值不可判 **3**（P0BR044/045/046 `E1+E3(+E4)`，与 72 L3065 同批）＋ 无 TASK 匹配 **2**（`EVD-P0B-ENG-001`/`EVD-P0B-V01-V12`，null taskId，承 73 正向悬空）= 70 ✓。**可判定面 65 卡（38+27）中越级 27 = 41.5%**。越级分布 **BOUND=27 / ZOMBIE=0**（全部在校验器视野内）。

**三族交集（核心结构）**：
- **越级 × 旧代幽灵族（25 张）**：`E2 冒充 E4` 的 25 张 = `EVD-P0BR003/004/010/011/012/013/014/015/016/017/020/021/022/023/024/025/026/027/028/029/030/040/041/042/043`——**全部为无连字符旧代、`commitSha=7dc22d44`（幽灵）**，与 73 gov8「REBOUND-TO-GHOST」及 72「73 卡幽灵 sha」族**逐卡对齐**。即批量重绑进幽灵的那批卡，恰恰也是 `evidenceLevel: E2` 但被 `status: PASS` 声称达标的卡——**幽灵 sha 与越级伪 PASS 是同一批 07 月代卡的两个共 defect 面**。
- **越级 × 72 双轨族（2 张）**：`E3 差 E4` 的 2 张 = `EVD-P1016-001`/`EVD-P2013-001`，二者均在 72 L3063 六卡「completionValidation=FAIL×accepted=true」双轨族内。交叉 gov11 实读：P1016 sha=`30059da7`(EXISTS)、P2013 sha=`1593e3b3`(EXISTS)——**这两张 sha 物理存在但越级**，说明双轨族缺陷不止「FAIL×accepted 互斥」，还叠加「E3 冒充 E4 达标」。双轨族另 4 卡（P1015/1017/1018/P3010）**不在越级清单**（level 达标），其缺陷面仍是 sha/双轨，非 level——精确交集坐实：六卡双轨族 ∩ 越级族 = {P1016, P2013}。

**机理（越级为何长期不可见）**：[L732-733](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L732-L733) 越级判据位于 EVD 复核块内，而**该整块仅在 `-CheckCompletion` 态执行**；默认态（72 L2950 实测 126 卡 exit0=2、462 issue）**不进此块**。72 历史首跑三卡的 `-CheckCompletion` 本就因 sha≠HEAD 全红，越级项被 sha 项掩盖。故**存量 27 张越级伪 PASS 从未被任何机检态捕获**——不是判据缺失，是判据所在的调用态对存量卡**系统性休眠**。这与 73「5 张 hyphen 新代僵尸卡不被 binder 引用→validator 永不可见」是两种「不可见」：73 是**引用侧**不可见，74 是**调用态侧**不可见。

**已核验（机理性）**：`Get-LevelNumber` 对 `E1+E3` 返回 -1 使组合值 3 卡天然跳过越级判定（不报越级也不报达标，落 NO_LEVEL 桶），与 72 L3065 组合值塌陷自洽；27 越级全 BOUND 由 `bound.Contains(basename)` 逐个判定、非推断；三族交集由卡名逐一比对（25 旧代名 ∩ REBOUND-TO-GHOST、2 双轨名 ∩ 72 L3063）坐实。

**偏差与 unproven**：① 越级面只证「卡面声称 `status: PASS` 且 `evidenceLevel` 字符串低于 TASK `requiredEvidenceLevel` 字符串」，**未复现真实测试执行是否达 E4**（Docker/Compose 缺席，同 72 偏差①、AGENTS.md「Docker 不可用而 skip 不算通过」）——即「E2 记录本身诚实、status:PASS 是伪标」与「E2 记录也失真」两解并存，本引擎只登记字符串矛盾，不裁决实测；② 组合值 `E1+E3(+E4)` 3 卡（044/045/046）无法纳入越级判定，其「E4 是否达成」在 gov10 视野外，与 61⑩/67② 塌陷族同界；③ `requiredEvidenceLevel` 取自 TASK front-matter 单值，若某 TASK 对不同 AC 分级要求（`scenarioEvidencePlan`，validator L379 另一判据）未逐场景展开，卡面对撞偏粗；④ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **27 张越级伪 PASS 的 `status` 应降为 `PARTIAL`**（其 `notProven`/`limitations` 已自陈缺 E4，与 `status: PASS` 自相矛盾——承 72 整改① P0BR-014 连字符新代 L3 人类注释即此判断）；但涉及 27 张 evidence 卡改写，须人类授权 Task；② validator 应把 **`evidenceLevel × requiredEvidenceLevel` 越级判定上提至默认态**（或至少新增「存量 PASS 卡 level 达标」机检项），否则调用态休眠使判据形同虚设——与 71「门禁调用态接入」、73「僵尸卡检出」并入 validator v2 同一整改轨；③ `E1+E3` 组合值 3 卡应改单值或让 `Get-LevelNumber` 支持复合枚举解析（与 61⑩/67② 值谱卫生同族）；④ P1016/P2013 既是双轨（FAIL×accepted）又越级（E3<E4），72 整改④「六卡双轨矛盾提交批准人裁定」应把本节越级面并入同一裁定单。

**下一节候选域**：(a) **70 张 PASS 卡的 `notProven`/`limitations` 非空 × `status: PASS` 自相矛盾普查**（72 L3060 已给「PASS×notProven 非空=66」总账，但**未列 66 卡名单、未与越级 27 卡做交集**——若 66 与 27 高度重叠则坐实「status:PASS 字段在全库范围被系统性误用」，第十三次查重：若仅为 72 总账展开名单而无新交集结构即判废）；(b) **TASK `scenarioEvidencePlan`（validator L379 逐场景分级判据）× EVD `coverage.proven[].scenarioId`** 的场景级证据覆盖对撞（比本节卡级单值对撞更细一轴，L379 判据同样只在 CheckCompletion 态休眠，第十三次查重）。

→ 本项与 ①—74 一并不在本轮 AI 自决实施范围内。

### 75. TASK `scenarioEvidencePlan` × EVD `coverage.proven[].scenarioId` 全库场景级对撞——70 张 PASS 卡仅 43 张有场景判据可对，123 计划场景实例覆盖 91、缺口 32 全部集中于 8 张以 `implementation-verified` 自造总括逃账的旧代卡，25 张 PASS 卡的 TASK 判据为空 `[]` 整体在场景轴外，19 张 PASS 卡同 scenarioId 同时现身 proven 与 notProven，L379 逐场景判据与 L732 一样被 `-CheckCompletion` 态休眠（2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十三次）**：承 74 候选域。**(a) notProven×PASS 交集判废**——72 节 L3060 已量化「PASS×notProven 非空=66」总账、L3071 已定性「PASS 签发与判据无机械绑定」，仅展开名单无新结构，按申报规则废。**(b) 场景级对撞成立为新轴**——既有覆盖均为个案或卡级：62 P-8（044 卡自造后缀「AC-P0B-ENG-004-E3」）、71 P-3（045 卡区间记法 `~`）、71 P-7（016 卡 plan 承诺 AUTH-009 E4 证据面为空）都是**单卡引用**；72/74 是**卡级**字符串对撞（status×notProven 存在性、卡级单值 level）；全库 126 TASK × 130 EVD 的**逐场景实例**覆盖账（plan 声明 vs proven 结构化条目）此前无任何节量化。validator [L379-380](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L379-L380) plan 级差对账判据存在但同 L732 一样只在 `-CheckCompletion` 态跑——本轴即「该判据若被接通，对存量 PASS 卡的现势结果会是什么」的全库预演。

**权威锚点**：判据侧 TASK plan 形如 [TASK-P0BR-014.md#L22-L27](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-014.md#L22-L27)（`scenarioEvidencePlan:` 五条 `AC-P0B-AGT-001..005|E4|…`）；空 plan 形如 [TASK-P0BR-003.md#L18](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-003.md#L18)（`scenarioEvidencePlan: []`）；逃账实证 [EVD-P0BR014-001.yaml#L46-L48](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR014-001.yaml#L46-L48)（proven 仅一条 `scenarioId: implementation-verified`——非标值，5 条 plan 场景 0 覆盖）；同 ID 双现身实证 [EVD-P0BR-054-001.yaml#L88](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-054-001.yaml#L88)（proven `AC-P0B-IDM-001`）× [#L117-L124](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-054-001.yaml#L117-L124)（notProven 同 ID「MCP 幂等空声明未实现」）——该卡 `status: PASS`。

**取证方法（gov12/gov13，逐行状态机＋尾锚定标准形正则 `^AC-[A-Za-z]+-[A-Z]+-\d{3}$`，承 73/74 方法论与 gov11 尾锚教训）**：gov12 = 解析 126 TASK front-matter 的 `taskId`＋`scenarioEvidencePlan` 条目（`id|level|…` 三段形），解析 130 EVD 的 `status`＋`task.taskId`＋`coverage.proven/notProven` 的 `- scenarioId:` 结构化条目；对每张 PASS 卡：plan 为空→EMPTY_PLAN 桶；plan 非空→按标准形过滤 proven，算 missing（plan 有而标准形 proven 无）与 evasive（非标形 proven 值）及 proven∩notProven 交集。gov13 = 三桶明细下钻（空 plan 卡清单、全状态 overlap 清单、044/045/046 plan 实况）。脚本置系统临时目录、不入库。

**总账发现（gov12/gov13）**：
- 70 PASS = 无 TASK 匹配 2（承 73 null 卡）＋ **TASK plan 空 25** ＋ 有判据 43；43 中 **35 张 plan 场景全被标准形覆盖、8 张零覆盖**（EVD-P0BR010/011/012/013/014/016/017/020-001，全为无连字符旧代）。场景实例总账：plan 123 × 覆盖 91 × 缺口 32（=8 卡 missing 之和 3+3+4+2+5+5+4+6，两本账闭合）；
- **缺口 32 的唯一形态是 `implementation-verified` 自造总括**（EVASIVE_FORMS=1×8 卡）——一张非标条目冒充全部计划场景，正是 61⑩/62 P-8「非标值静默丢弃」族的 PASS 态全量化：若 L379 判据接通，这 8 卡将报 32 条场景缺口 Error，**8 卡全部 ∈ 74 越级 25 卡族、且全部 ∈ 73 REBOUND-TO-GHOST 幽灵族**（010-017/020 无连字符旧代）——旧代卡「幽灵 sha×卡级越级×场景级逃账」三重缺陷同源；
- **25 张 PASS 卡整体在场景判据视野外**（TASK plan=`[]`）：其中 17 张属 74 越级 25 卡族（003/004/015/021-030/040-043 无连字符旧代）——即越级卡中 17 张连「应有哪些场景」的判据都没有，卡级 `scenarios` 亦多空白；另 8 张（gov13 A）＝ 023/024（**有连字符新代** PASS×真实 sha 也落空 plan 桶）＋ 000A/000B/002（旧代但卡级 level 达标，落 74 的 38 达标桶）＋ 044/045/046（组合值卡，即 74 的 NO_LEVEL=3 桶）——**74 组合值 3 卡与 75 空 plan 桶完全重合**，045 的 13 条区间记法 proven（71 P-3）因 plan=`[]` 根本无从对账——无判据可撞；
- **proven∩notProven 同 ID 双现身 29 卡：PASS 态 19 张**（047-078 有连字符族主体：054/055/056/058/066/067/068/069/071/072/073/074/075/076/077/078＋P1016/P1018/P2013）＋ PARTIAL/DRAFT 态 10 张（PARTIAL 6：047/048/051/052/053/P1009；DRAFT 4：P1013/P1014/P2011/P2012——gov15 独立复现 2026-10-13，本数初稿误记 28＝19＋9，成文时顺勘）。PASS 卡语义 = 「场景 X 已证成 PASS」×「场景 X 有未证残余」并存；其中 **P1016/P2013 为三族交集**（72 双轨×74 越级×75 同 ID 残余）。注意两族互斥：旧代 8 逃账卡的 notProven 是**纯字符串列表**（如 [EVD-P0BR014-001.yaml#L49-L50](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR014-001.yaml#L49-L50)），无 scenarioId 结构形，overlap 检测对旧代天然失明——**notProven 存在非结构化（旧代）与结构化（新代）两形**，本身即值谱卫生缺陷。

**机理（与 73/74 的「三种不可见」合成闭环）**：场景判据失效有**双通路**——① 调用态休眠：L379 plan 覆盖对账在 `-CheckCompletion` 块内，存量卡永不进块（同 74 L732 机理）；② 判据真空：25 张 PASS 的 TASK plan=`[]`，**即便接通 CheckCompletion 也无场景可对**——72 已证该三卡（044/045/046）CheckCompletion 实跑时同样只报 sha/taskId 类错、场景维零报。73 是引用侧不可见（僵尸卡）、74 是调用态侧不可见（休眠判据）、75 新增**判据真空侧不可见**（空 plan）＋**值形侧失明**（非标 scenarioId 静默丢弃、字符串形 notProven 无键可对）。四类合成：EVD 生态的机检面按「引用→调用态→判据完备→值形规范」层层塌缩。

**已核验（机理性）**：35 张全覆盖卡全为有连字符新代或 P1-P5 代（049-078/GOV007/P1015-1018/P2006/P2013/P3010），与 73「新代诚实」方向一致；8 逃账卡＝74 越级 25 的子集由卡名逐一比对坐实；123/91/32 三数满足 91+32=123；`implementation-verified` 8 卡同值复现 61⑩ 已命名的形态、非新造类型。

**偏差与 unproven**：① 本轴只证**计划-声称**对撞（plan 声明的场景有无标准形 proven 条目），不裁决 proven 条目内 assertion 的真实性（承 72 偏差①、无法起 Docker/Testcontainers 复测）；② plan 条目要求逐条取 `|E4|` 级差者仅字符串存在性对撞，未做「proven 断言之实际证据等级 ≥ plan 要求等级」的二级对撞（需逐 assertion 解析，留 validator v2 或后续节）；③ gov12 对 EVD `task.scenarios` 数组维未采集（与 plan 近重复），空 plan 桶内「卡级 scenarios 非空但 plan 空」的错位卡数未量化——unproven；④ overlap 定性只到「同 ID 双现身」字符串层，逐卡 assertion 语义是否构成真残余（如 054 的正向断言已补、残余为范围外 MCP 声明）未逐卡人工复核；⑤ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① 8 张逃账卡的 `implementation-verified` 条目应拆为逐场景标准形或删除（涉及 evidence 卡改写，须人类授权）；② 25 张空 plan 的 TASK 应补 `scenarioEvidencePlan` 或显式声明「无场景判据」并解释 PASS 依据（tasks/ 改动，须人类授权）；③ validator v2 增**非标 scenarioId 检出**（proven 条目不匹配标准形 → Error，终结静默丢弃——62 P-8 缺口追加的第一种逃账记法至此有了全库量化：8 卡×每卡整桶）＋**同 ID proven/notProven 并存报告**（PASS 态至少 WARN）＋**notProven 结构形统一**（字符串形与条目形二选一强校验）；④ L379/L732 与 74 整改② 并入同一「判据上提默认态」轨；⑤ P1016/P2013 三族交集卡应作为 72 裁定单的优先样本（单卡叠合双轨×越级×场景残余三类矛盾）。

**下一节候选域**：(a) **126 TASK 卡 front-matter 卫生全库普查**——`implementationAuthorized`/`status`/`baseCommit`/`approvedBy`/`approvedAt` 五字段值谱×组合矛盾（如 authorized:true×status:DRAFT、approvedBy 非 null 但 git 历史同秒批量入库——69④/70③ 时间线族只有个案，**全库值谱与组合矩阵未做**，第十四次查重：若与 69/70 时间线节仅判据维重合而无值谱量化则成立）；(b) **EVD `task.requirements[]` × TASK `scenarioEvidencePlan` 的 REQ↔AC 双向连坐**（卡声称覆盖的 REQ 域与其 TASK 绑定 AC 所属 REQ 是否一致，需求-场景二部图对账——比 75 的场景级再上一层，查重难度高、易与 66⑥ AC→卡反向覆盖报告条混同，倾向缓做）。

→ 本项与 ①—75 一并不在本轮 AI 自决实施范围内。

### 76. EVD `task.requirements[]` ⊆ TASK `requirements[]` 引用包含 × 卡面 REQ↔AC 域归属一致性全库对撞——5 张 EVD 声称 TASK 未声明的 REQ（全 PASS 态）而 validator 任何模式都不对账 requirements，20 卡 TASK 两列表语义域零重合、8 卡部分越域（合计 28 卡卡面漂移），EVD 的 requirements 轴是判据体系唯一的**结构性空位**（连 `-CheckCompletion` 都不查），承 70(b)/71(a)/75(b) 三度申报顺延的「语义一致性」轴首次落地（2026-10-13，取证基准 HEAD `a022796`）

**立项查重（第十四次）**：承 75 候选域。**(a)「126 TASK 五字段值谱×组合矛盾」判废**——69 节已做 approvedBy 六值谱系＋48 卡批量戳＋32 卡 `READY×authorized×零批准人`（组合矛盾正是 69 头号发现，[L3030 附近](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md)）、70 节已做全量序关系矩阵、68 节已做 baseCommit 实测 121/126；五字段值谱无独立增量面，按申报规则（候选与既有节仅判据维重合即判废）废。**(b) 成立为 76 轴**：「EVD↔TASK REQ 引用包含 × 卡面 REQ↔AC 域归属一致性」——70(b)/71(a) 两度申报顺延、75(b) 再次点名「REQ↔AC 双向连坐、易与 66⑥ AC→卡反向覆盖混同、倾向缓做」的**语义一致性轴**，全库从未量化。查重坐实：validator [L279-302](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L279-L302) 只校验 TASK `requirements[]` 的**存在性＋READY**、[L308-317](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L308-L317) 只校验 AC 的**存在性**（05-acceptance 表行 `\|`AC`\|` 形匹配），**对 EVD `task.requirements[]` 是否 ⊆ 其绑定 TASK 的 requirements 毫无判据**；[CheckCompletion L693-748](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L693-L748) 只 taskId 匹配＋scenarios 越界（L697）＋coverage（L737），**`requirements` 连 CC 态都不采**——这是引用完整性轴上的结构性空位（72/74/75 的休眠判据至少判据存在、只是调用态不进；此处判据根本不存在）。

**权威锚点**：E1 实证 [EVD-P0BR-024-001.yaml#L12](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-024-001.yaml#L12)（`requirements: [REQ-JOB-001, REQ-AUD-001]`）× [TASK-P0BR-024.md#L12-13](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-024.md#L12-L13)（TASK 仅 `REQ-JOB-001`）——EVD 多声称 `REQ-AUD-001`；[EVD-P0BR016-001.yaml#L7](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR016-001.yaml#L7)（`[REQ-AUTH-001, REQ-AUTH-002]`）× [TASK-P0BR-016.md#L12-15](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-016.md#L12-L15)（`ORG-001/ORG-002/AUTH-003`）——两条 REQ 全越界。T1 实证 [TASK-P4-010.md#L13-14](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P4-010.md#L13-L14)（REQ 域 `MCP`）× [#L15-20](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P4-010.md#L15-L20)（AC 域 `DST-AIW`/`EXIT`）——零重合。值形实证 [EVD-P0B-V01-V12.yaml#L8-14](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0B-V01-V12.yaml#L8-L14)（`taskId: null`＋六条 scenario 带行尾中文注释 `- AC-P0B-IAM-002 # 登录 JWT 签发`）。

**取证方法（gov14，逐行状态机＋剥引号，承 73/74/75 方法论；引擎自纠第五代）**：解析 126 TASK front-matter（`requirements`/`acceptanceScenarios`，inline `[]` 与块形 `- v` 两态）与 130 EVD `task` 块（`taskId`/`requirements`/`scenarios`），三桶对撞：E1＝EVD REQ∉TASK REQ、E2＝EVD scenario∉TASK scenario、E3＝EVD 绑定 TASK 不存在；T1/T2＝TASK 卡面 REQ 域段 vs AC 域段（`Get-Domain`：`REQ-X-NNN`→X、AC 丢 phase 段 P0B/P1..P5、多段域 AI-IDE/DST-AIW 保留）零重合/部分越域。**引擎自纠史（记入偏差）**：首跑 `-like '[*'` 非法通配符 EXIT=1；二跑 P3 族 10 条假阳性（EVD inline 带单引号 `'REQ-REV-001'` 与 TASK 裸值不等）→ 六处统一 `.Trim().Trim("'").Trim('"')`（与 validator [Get-IndentedList L125](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L110-L128) 同构）；**关键自纠**：validator `Get-IndentedList` 仅去引号**不去行尾注释**，故 V01-V12 的 `AC-... # 注释` 会被 validator 解析成 `AC-P0B-IAM-002 # 登录 JWT 签发`——**即便 L697 判据接通也永不匹配标准形**（休眠×值形双盲）。gov15 独立复现 overlap 账坐实 75 顺勘。脚本置系统临时目录、不入库。

**总账发现（gov14 定稿，EXIT=0）**：
- `TASKS=126 EVD=130`；**E1_REQ_OUTSIDE=5（全 PASS 态）**：EVD-P0BR-024-001（`REQ-AUD-001`∉`[REQ-JOB-001]`）、EVD-P0BR016-001（`REQ-AUTH-001`＋`REQ-AUTH-002` 两条∉TASK，计 2 命中）、EVD-P0BR026-001（`REQ-AUD-001`∉`[REQ-LOG-001]`）、EVD-P0BR028-001（`REQ-NOT-001`∉`[REQ-WHK-001]`）——去重 **4 张卡 5 条越界引用**，EVD 声称覆盖了 TASK 从未声明的 REQ，validator 任何态都不查；4 卡名 3/4 为无连字符旧代（P0BR016/026/028 ∈ 74 越级 25 族）；
- **E2_SCEN_OUTSIDE=4（全 PARTIAL 态）**：EVD-P0BR-014-001（`AC-P0B-ENG-006`＋`AC-DST-AIW-002`∉TASK[014]=5 条纯 AGT 场景）、EVD-P0BR-017-001（`AC-DST-DETAIL-003`）、EVD-P0BR-041-001（`AC-P0B-TAX-006`∉TASK[041]=UI-003/UI-005）——EVD 声称 TASK 卡面没有的 AC，但这些卡 `status: PARTIAL` **未签发 PASS**，与 E1 的 PASS 态越界形成**代际诚实度对比**：越界引用在 PARTIAL 卡上是「已知未完成项」、在 PASS 卡上是「伪标」；
- **E3_NO_TASK=2**：EVD-P0B-ENG-001、EVD-P0B-V01-V12（`taskId: null`，承 73 幽灵卡——且 ENG-001 的 `requirements: [REQ-COM-001]` 无处对账，V01-V12 的六条 scenario 全是注释形）；
- **T1_DOMAIN_ZEROOVERLAP=20 卡**（REQ 域与 AC 域完全不相交，如 001 `COM`vs`ENG`、026 `LOG`vs`AUD`、028 `WHK`vs`NOT`、P4-010 `MCP`vs`DST-AIW,EXIT`、P3-006 `REV`vs`UI`）＋ **T2_DOMAIN_PARTIAL=8 卡**（013/049/051/062/P1-014/P1-018/P2-011/P3-009，部分 AC 域越出 REQ 域）＝**28 卡卡面两列表语义漂移**（TASK 自称做的 REQ 域与它声明要验的 AC 域对不上）；TASK[003] `REQ=[AUTH]×AC=[]` 空对落 T 桶外（scen 空跳过）。
- 空值桶（形态拆分定稿）：TASK 侧 `EMPTY_REQ=1`（TASK-P0BR-000A，`requirements: []`×validator L250-251 必报错项却 READY×authorized）、`EMPTY_SCEN=6`（000A/000B/002/003/004/015 全 READY）；EVD 侧 `REQ` 空 14（inline-empty 1＝P0BR000A[PASS]＋block-empty 13＝P2/P4 族）、`SCEN` 空 37（inline-empty 24＋block-empty 13；缺键 0，`missing` 计数证伪「缺 scenarios 键」说）、`blockFilled=41`/`inlineFilled=52`（41+52=93 非空＝130−37 自洽）；EVD_REQ 注释形＝仅 EVD-P0B-V01-V12 一卡六条。

**机理（判据空位比休眠判据更严重）**：72/74/75 的失效判据**存在但调用态不进**（引用侧/调用态侧/判据真空侧/值形侧四重不可见）；76 揭示**第五重——判据本身缺席**：validator 全模式（默认态＋`-CheckChangedPaths`＋`-CheckCompletion`）从未对 EVD `task.requirements[]` 与其绑定 TASK 的 `requirements[]` 做任何包含性对账，也从未校验卡面 REQ 域↔AC 域归属。E1 的 4 张 PASS 卡正是钻这个空位——它们 `coverage.proven[].scenarioId` 受 L737 校验（CC 态）、`scenarios` 受 L697 校验（CC 态），唯独 `requirements` 无人看守。V01-V12 的注释形进一步使 scenarios 维**接通也空转**：`Get-IndentedList` 不去注释 → 值带 ` # ...` → 与 TASK 标准形永不相等 → 若 CC 态真跑，L697 会对**六条全报越界 Error**（当前因 `taskId: null` 在 L694 先短路，故连报都报不到）。

**已核验（机理性）**：E1 五命中/去重 4 卡由逐条 `taskHas=[]` 打印坐实（非推断）；E2 三卡全 PARTIAL、E1 四卡全 PASS 的状态代际由 evdStatus 字段直读；T1/T2 卡名与 74 越级 25 族、75 空 plan 桶部分交叠（如 044/045/046 同现 T1×74 组合值×75 空 plan）；空值桶 24/13/0/41/52 五数满足 24+13=37、41+52=93、37+93=130；gov15 独立脚本复现 75 overlap 29 卡（PASS 19＋PARTIAL 6＋DRAFT 4），据此顺勘 75 L3154。

**偏差与 unproven**：① **域段提取是启发式**——`Get-Domain` 按 ID 命名段位切，未建模「阶段横切 AC 域合法越界」（如 P4-010 的 `EXIT` 域 AC 是 P4 竖切出口、`DST-AIW` 是数据集 AI 工作流横切，其对应 REQ 可能合法跨域），故 **T1 20/T2 8 含合法越界未剔除**，逐卡语义是否构成真缺陷 **unproven**（本引擎只登记命名域字符串矛盾）；② E1 只证「EVD 声称的 REQ 不在 TASK 声明里」，**不裁决该 REQ 是否真被实现覆盖**（EVD 可能是诚实补记 TASK 漏声明——须逐卡人工复核 assertion 语义）；③ 注释形仅 V01-V12 一卡实测（inline 引号形实测 P3-001..008 八卡），值形族覆盖面未穷尽——可能有其它卡以别的形式逃避匹配，unproven；④ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **E1 四张 PASS 卡（024/016/026/028）的 REQ 越界应并入 72 整改①「27 越级伪 PASS 降级」同一裁定单**作为独立样本（它们同时可能属 74 越级族）；② **validator v2 增 `task.requirements ⊆ TASK.requirements` 对账**（终结引用包含空位，与 74/75「判据上提默认态」同轨）＋**卡面 REQ↔AC 域归属 WARN**（启发式，须配合法越域白名单，否则会误报 T1 20 卡中的合法横切场景）；③ **EVD-P0B-V01-V12 的行尾注释清理**（`AC-... # 中文` → 纯 `AC-...`＋注释上移独立行）待授权，且其 `taskId: null` 应绑到真实 TASK 或删除（承 73 幽灵卡处置）；④ validator `Get-IndentedList` 应**剥离行尾注释**（与 YAML 语义一致），否则任何带注释的 list 值都静默失真——这是值形族卫生的机检根因。

**下一节候选域（第十五次查重预告）**：(a) **72 整改① 27 越级卡 × 74 越级 25 卡 × 76 E1/T1 卡的三重缺陷同源交集矩阵**（同一批旧代卡在「卡级 level 越界」「场景逃账」「REQ 越界」三轴上的叠合度——若三轴卡集高度重合则坐实「无连字符旧代」为单一缺陷世代，查重难度：与 74/75/76 各自卡名族交叠，需证明交集结构而非名单堆叠，否则判废）；(b) **130 EVD 的 `governance.*` 三子块（specValidation/taskValidation/changedPathValidation）的 `result` 值谱 × `status` 组合**（PASS 卡是否 governance 三项全 PASS、null 值比例——L704-714 判据只在 CC 态跑，默认态下 governance 值谱未普查）。倾向 (a)：三源卡集已在手，交集矩阵成本低、结构新。

→ 本项与 ①—76 一并不在本轮 AI 自决实施范围内。

### 77. 九轴缺陷向量矩阵全库对撞（G 幽灵 sha×PL 占位 sha×X 卡级越级×M 场景缺口×E 非标 proven×S2 空 plan×R REQ 越界×N 同 ID 双现×E3 空 taskId）——72/73/74/75/76 五节单轴数字首次同引擎同 HEAD 全量闭环（G=73、X=27、Mpass=8、S2pass=25、R=4、Npass=19 逐一对账通过），37 卡叠合 ≥3 轴且 f=5 三卡全为无连字符旧代，70 张 PASS 仅 13 张九轴全零且全为有连字符新代，第二幽灵 `4c7dfc83` cat-file 实裁 ABSENT 坐实 73「REBOUND-TO-RESOLVABLE=10」误判 2 卡（顺勘 73），76 预告的三重交集塌缩为包含链复述判废、改判矩阵（2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十五次）**：承 76 候选域。**(a)「72 整改① 27 越级卡 × 74 × 76 E1/T1 三重交集矩阵」判废＋口径顺正**——其一，76 预告把 27 越级卡集记作「72 整改①」有误：27 卡集出自 74 gov10 普查（[L3124](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md)），72 整改①实为 R-1 存量豁免轨裁定（幽灵 sha＋6 卡快照的处置，[L3078](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md)）；其二，三源交集实测塌缩为**包含链复述**：74 越级 25 旧代 ⊂ 73 REBOUND-TO-GHOST 71 族（74 L3127 已逐卡坐实）、75 逃账 8 ⊂ 25（75 L3158）、76 E1 旧代 3/4 ⊂ 25——交集不产生任何新卡集，只是把「无连字符旧代是坏的」再说一遍，按申报规则（无新结构即判废）废。**(b) 成立为 77 轴——判废的是「用交集做」，不是同源结构本身**：升级为**九轴 per-card 缺陷向量矩阵**（同一引擎、同一解析器、同一 HEAD 下把五节各自单轴数字全量对账＋给出每卡轴叠合向量），增量结构为三处此前任何节未量化者：① 全状态×全轴叠合分布（pattern 直方图、facet≥3 卡集）；② PASS×幽灵×level 达标/组合值交集（74 分解的幽灵侧镜像，ghost×PASS=33 复现 72）；③ 新代残余缺陷定位（PASS_CLEAN 名单、024 反例）。查重坐实：既有各节均为单轴普查，全轴向量无先例；76 预告候选 (b)（governance 三子块值谱）未做、顺延 78。

**权威锚点**：双幽灵锚 [EVD-P2006-001.yaml#L31](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml#L31) × [EVD-P5001-001.yaml#L33](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5001-001.yaml#L33)（同值 `4c7dfc83d8dc583886779cf558f762818119b7d4`，72 L3061 已列 FULL-ABSENT，73 [L3100](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md) 却归入「当前值可解析」桶——本节 cat-file 实裁落槌）；主幽灵 `7dc22d44…`×71 首例 [EVD-P0BR000A-001.yaml#L26](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR000A-001.yaml#L26)；新代反例 [EVD-P0BR-024-001.yaml#L12](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-024-001.yaml#L12)（R：`REQ-AUD-001` 越界）× [TASK-P0BR-024.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-024.md)（S2：`scenarioEvidencePlan: []`）——真实 sha、不越级、不逃账，但 REQ 越界＋落空 plan 桶；f=5 三卡 [EVD-P0BR016-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR016-001.yaml)/EVD-P0BR026-001/EVD-P0BR028-001＝幽灵×越级×非标 proven×(逃账或空 plan)×REQ 越界五轴叠合；九轴判据出处承引：G/PL（cat-file 物理核验，72）、X（[L732-733](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L732-L733) CC 态休眠）、M/E（[L379-380](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L379-L380)）、R（判据根本不存在，76）、S2/N/E3（75/76 值形轴）。

**取证方法（gov16→gov16b，引擎自纠续史，承 72 偏差④方法论条）**：首代 gov16 连环报废三重——① UTF-8 无 BOM 脚本被 PS 5.1 按 GBK 解码，中文字面量吞引号致 ParserError → **取证脚本全 ASCII 化**；② `$ErrorActionPreference='Stop'` 下 `git cat-file` 对幽灵 sha 的 stderr fatal 变终止错 → Continue＋`| Out-Null`；③ **报废级缺陷**（输出自检当场识破）：PS 变量名大小写不敏感——循环内 `$g=` 布尔把同名计数器 `$G` 覆盖，首行输出 `AXIS G=False`；std 正则 `[A-Za-z]+` 段匹配不了含数字域段（`AC-P0B-*` 全错分，N=7 对不上 gov15 的 29）；sha 直方图误按唯一值而非卡数计数。**不修补、整体重写 gov16b**：移植 gov12/gov15 已验证解析器（`^AC-[A-Z0-9]+-[A-Z0-9]+-\d{3}$` 尾锚、plan `id|LEVEL|…` 三段形、`Get-IndentedList` 同构剥引号），计数器统一 `$c*`/卡级布尔 `$is*` 命名隔离，cat-file 唯一值缓存，补 M/R 轴。三版 **coverage 加固**：`- scenarioId:` 尾锚定主形＋裸串条形（ID 形状守卫 `^[-A-Za-z0-9]+$`＋level/evidence/artifact/note/reason/command/result/url/file/kind 关键字黑名单＋注释行跳过），杜绝 `- level: E4` 属性行被误捕进 proven/notProv——加固使 E 40→37、N 38→35、G+M 23→24，其余锚定轴纹丝不动。脚本置系统临时目录、不入库；DEC-009：全程只读取证，未改任何产品码/契约/tasks/evidence。

**总账发现（gov16b 定稿，EXIT=0）**：`TASKS=126 EVD=130`；`AXIS G=73 PL=7 X=27 M=46 Mpass=8 E=37 S2=26 S2pass=25 R=4 Rpass=4 N=35 Npass=19 E3=2`。
- **锚点实裁**：`ANCHOR-7dc22d44=ABSENT ANCHOR-4c7dfc83=ABSENT`——**顺勘 73**：P2006/P5001 非「REBOUND-TO-RESOLVABLE」而是第二幽灵载体，73 L3100 的 10 可解析桶实为 8，P5007 承诺「81 份重绑」真正兑现数 **10/81→8/81**；G=73＝7dc22d44×71＋4c7dfc83×2，**72 的「73=71+2」账反而复现正确**；
- **X−G ＝ 恰 {EVD-P1016-001（`30059da7` EXISTS）, EVD-P2013-001（`1593e3b3` EXISTS）}**——74「双轨族 ∩ 越级族」集合同引擎复现；**PASS×GHOST−X＝8 卡**（000A/000B/001 `E1×E1`、002/P2006 `E4×E4` 达标＋044/045/046 组合值）＝74 达标 38 桶中幽灵 5＋组合值桶 3 全成员，与越级旧代 25 合成 **ghost×PASS＝25+8＝33**，复现 72 L3061 交叉账；
- **PL=7**＝2×`{null}`（P0B-ENG-001/V01-V12，即 E3 二卡）＋5×`{pending-commit}`（P1013/1014/1015/P2011/P2012）；**Mpass=8 名单与 gov12 逐一同名**（EVD-P0BR010..014/016/017/020-001）；**R=4 与 76 E1 去重四卡一致**，其中 EVD-P0BR-024-001 `G=False X=False E=False S2=True`——**唯一同时踩 R×S2 的新代 PASS 卡**（pattern 直方图 `S2+R=1` 即它）；**N-and-G=7**（P1002/1004/1006/1008/1009/1011/1012-001，P1 族幽灵×同 ID 双现叠合）；
- **pattern 直方图（全轴闭环）**：`G+M=24`、`N=21`（047-078 新代族主体）、`G+X+E+S2=11`、`G+N=7`、`G+X+M+E=7`、`G+E+S2=6`、`G=6`、`G+X+S2=4`、`M=4`、`G+M+E=4`、`M+E=3`、其余 12 桶 ≤2——G 各桶求和=73、X=27、M=46、S2=26、N=35、PL=7、R=4、E3=2 逐轴复算全中；**facet≥3 共 37 卡**（f=5 三卡 016/026/028 全旧代 PASS；f=4 18 卡全旧代；f=3 含 4 PARTIAL＋2 DRAFT），全库 130 卡仅 **14 张九轴全零**（13 PASS＋1 非 PASS）；
- **PASS 侧收束**：`PASS_TOTAL=70 PASS_CLEAN=13`——GOV007、P0BR-**049/050/059/060/061/062/063/064/065/070（全有连字符）**、P1017、P3010；PASS 分解 31(f≥3)＋26(f=1..2)＋13(CLEAN)＝70 ✓；状态谱 `PASS=70 PARTIAL=36 DRAFT=24` 复现 72。

**机理（五节单轴合成为世代结构）**：矩阵把 72-76 的碎片拼成**缺陷世代论**——旧代（无连字符 07 月卡）＝「幽灵 sha 载体（73）×卡级越级（74）×场景逃账/空 plan（75）×REQ 越界（76）」四缺陷同源：`G+X+E+S2`(11)＋`G+X+M+E`(7)＋`G+E+S2`(6)＋`G+X+S2`(4) 等 pattern 主体皆其成员，f=5 三卡是叠加 REQ 越界的极端样本；新代（有连字符）残余缺陷**收窄为三型**：024 型（R×S2，物理与 level 面全干净）、P1016/P2013 型（双轨×越级×N 三轴叠合，sha 真实）、047-078 族 N 轴主体（`N=21` 单轴桶）；PASS_CLEAN=13 全为连字符新代——73/74/75 的「新代诚实」方向性结论在九轴全量下**成立但非绝对**。注意 S2/M 两轴经 `taskId` 传导：五组重名代际的旧卡与新卡绑同一 TASK，同落 S2 桶（EVD-P0BR024 与 EVD-P0BR-024 俱 S2=True）——向量叠合计数对重名族有共桶效应（见偏差⑤）。
**已核验（机理性）**：逐轴对账＝G 73（72 FULL-ABSENT）、X 27（74 gov10）、Mpass 8（75 gov12 同名）、S2pass 25（75 空 plan PASS 桶）、R 4（76 E1 去重）、Npass 19（gov15 PASS 19）、E3 2（73/76 null 卡）、状态谱 70/36/24（72）——五节八数在独立引擎下全部复现，无一漂移；`4c7dfc83`/`7dc22d44` 由 `git cat-file -e "<sha>^{commit}"` 现场实跑 ABSENT；X−G、PASS×GHOST−X、N-and-G、Mpass、R detail 均为逐卡打印名单、非推断。

**偏差与 unproven**：① **N=35 vs gov15/gov12 的 29**：口径差＝本引擎把旧代**纯字符串条形** notProven 纳入比对（+6，全为 PARTIAL/DRAFT；PASS 态 19 两引擎一致）——75「overlap 检测对旧代天然失明」在裸串口径下部分失效：旧代不是失明，是**结构形缺失**，且注释形清理前无法与 76 V01-V12 教训区分真值与噪声；② **PL=7 vs 72 占位 21**：72 的 NON-HEX-PLACEHOLDER 含「40 位真 sha＋行内中文注释」形 14 卡，本引擎按 hex 形归 cat-file 实裁侧（EXISTS/ABSENT）、null 归 E3——占位定义未沿用 72 值谱形，两数不可直接互校；③ **M=46 全状态桶**中 38 张非 PASS 卡的 missing 无「逃账」语义（PARTIAL 本应声明缺口、缺口在 notProven 侧），是否构成缺陷逐卡未核——unproven，只有 PASS×M=8 承 75 定论；④ 矩阵只量**卡面字符串/物理存在性叠合**，不裁决任一轴断言的真实性（承 74 偏差①，Docker/Compose 缺席）；E=37 的 29 张非 PASS 成员构成未下钻；⑤ 重名代际共桶（上段机理注）使 facet 在 014/017/023/024/041 五组上重复计入 TASK 侧轴——按卡计数正确、按「缺陷 TASK」计数须去重，后者未做；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **顺勘 73 L3100/L3104**：REBOUND-TO-RESOLVABLE 10→8、P5007 承诺兑现 10/81→8/81（P2006/P5001 移入幽灵桶），随 72 整改②「7dc22d44 溯源」同轨处置；72/73 整改②/③ 的 P5007 消项进度卡基线应改用本节九轴矩阵（G/PL/E3 三桶机检可复跑）；② EVD-P0BR-024-001 型**新代残余**（R×S2）并入 76 整改① E1 裁定单——它是「换代只修了 sha/level、REQ 与 plan 未修」的直接样本；③ **f≥3 的 37 卡**（尤其 f=5 三卡 016/026/028）应作为 72 整改① R-1 存量豁免轨裁定的优先样本名单；④ **PASS_CLEAN=13 登记为 validator v2 回归基准集**——七项新判据（72-76 各整改条）接通后此 13 卡应保持九轴全零、不新增告警，任何新增即判据误报；⑤ 取证方法论追加教训入 72 偏差④ 条：PS 5.1 脚本全 ASCII、EAP 与外部命令 stderr 分离、**变量大小写不敏感须计数器/布尔命名隔离**、std 正则须含数字域段、coverage 解析须属性行守卫——「输出自检＋跨节对账」在 gov16 首跑当场识破报废级缺陷，规则续立。

**下一节候选域（第十六次查重预告）**：(a) **EVD `governance.*` 三子块（specValidation/taskValidation/changedPathValidation）`result` 值谱 × status × 九轴向量的连坐**（76 预告 (b) 顺延升级——72 已做 completionValidation **单块**六形值谱，三子块全库值谱及其与九轴缺陷的共现率未做；若「三子块全 PASS 声称」×facet≥3 共现率高，则自证面与事实面脱钩再加一轴；查重条款：若与 72 值谱节仅判据维重合而无共现矩阵增量即判废）；(b) **TASK `baseCommit` × 绑定 EVD `commitSha` 同值/同代对撞**（68 已做 baseCommit 存在性 121/126，sha 对撞未做；但易与 73 sha 历史消项、72 快照豁免条混同，增量面偏薄，倾向缓做）。倾向 (a)：governance 三子块数据已在全库卡内、九轴向量在手，共现矩阵成本可控且结构新。

→ 本项与 ①—77 一并不在本轮 AI 自决实施范围内。

### 78. governance 四子块 `result` 值谱 × status × 九轴缺陷向量连坐（72 单块六形值谱的三子块补全 + `FAILED` 第七形新见 + 自证面/物理面共现矩阵首次量化）——`specValidation` 128/130 声称 PASS、`taskValidation` null=40、`changedPathValidation` null=45、`completionValidation` 六形与 72 逐字吻合；核心结构 **`comp=PASS` 的 32 卡 100% 携幽灵 sha（32/32）、0 张九轴全零、31 张叠合 ≥3 轴**，与「九轴全零的 13 张 PASS_CLEAN 卡无一声称 `comp=PASS`」构成反转对照；`PASS × 前三子块全 PASS=65` 中幽灵 32（脱钩率 49%），`cpv=PASS` 数（78）恰等于前三全 PASS 数（78）坐实 cpv 为最紧约束（2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十六次）**：承 77 候选域 (a)。判据维与 72 **重合**、但**共现矩阵增量成立**，按 77 预告自设条款（「若与 72 值谱节仅判据维重合而无共现矩阵增量即判废」）落槌：**(a) 成立为 78**。72 [L3055-3082](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md) 只做 `completionValidation` **单块**六形值谱（且只到「值谱卫生」为止，未与任何物理缺陷轴连坐），本节的三处增量结构均非 72 或任何既有节所有：① **另三子块（spec/task/changedPath）`result` 全库值谱**——72 从未枚举，本节实测得 `spec PASS=128/null=2`（几无诚实 FAIL）、`taskv PASS=82/null=40/FAIL=7/FAILED=1`、`cpv PASS=78/null=45/FAIL=6/FAILED=1`；② **同义异写第七形从 comp 单块扩展到子块面**——[EVD-P2009-001.yaml#L52](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml) `taskValidation/changedPathValidation` 用 `FAILED`（非 `FAIL`）、`completionValidation` 用 `NOT_RUN`（非 `notRun`），72 R-4 值谱卫生面只在 comp 块记过 `NOT_RUN` 一例，此卡把异形族一次带进三块；③ **自证面声称 × 九轴物理缺陷共现矩阵**——72 的机理条只说「validator [L704-714](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) 只 gate spec/task/changedPath 三块、`comp` 声称值从不被校验」，本节把这条静态观察升级为**可量化的脱钩率**，并给出 32/32、65×32、13-CLEAN×comp≠PASS 三张共现表。查重坐实：全库既有各节的 governance 侧仅 72 一节，且为单块无连坐；三子块值谱与九轴共现无先例。

**权威锚点**：`FAILED`/`NOT_RUN` 第七形 [EVD-P2009-001.yaml#L46-L76](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml)（status=PARTIAL，spec=PASS/taskv=FAILED/cpv=FAILED/comp=NOT_RUN 四形并置）；validator 三块硬 gate 与 comp 不 gate [validate-task-card.ps1#L703-L714](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（L704-708 `foreach` 只列 spec/task/changedPath，L710 正则锁 `result: PASS` 尾锚，L711 不中即 Add-Error；`completionValidation` 块不在名单内）；反转对照样本 [EVD-P3009-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P3009-001.yaml)（status=DRAFT、九轴 facet=0、四块 spec/taskv/cpv=PASS 而 comp=FAIL——「物理干净但 completion 诚实报失败」的新代样本）；13 张九轴全零 PASS_CLEAN 卡承 77 [L3208](file:///d:/workspace/opengeobot/HarnessDG/docs/architecture/iteration-plan-2026-09-24.md)（GOV007、P0BR-049/050/059/060/061/062/063/064/065/070、P1017、P3010）。九轴定义与 ghost 集承 77 gov16b 定稿。

**取证方法（gov17a→b→c→d，编码陷阱自纠续史，承 72 偏差④方法论条）**：本节暴露**「取证脚本全 ASCII 化」规则本身不足**——77 立的 ASCII 条只保证脚本**字面量**不被 PS 5.1 按 GBK 误解码，但**被读的数据文件可含非 ASCII**（EVD 卡内大量中文注释）。首代 gov17a/b 用 `Get-Content` 读卡，对 **LF-only 且含中文注释**的卡触发致命缺陷：UTF-8 双字节序列的尾字节与 `0x0A` 拼成双字节字**吞掉换行**，致整块错位（014 卡 LF=124 却被读成 108 行、071 卡 273→175 行），症状为 status 行「消失」（36 卡 `STATUS_LINE_ABSENT`）＋值谱失真（comp `EXPECTED_FAIL` 得 25、对不上 72 的 37）；CRLF 卡行数正常但内容乱码。经 gov17dbg/dbg2 字节级坐实（`size=4456 CR=0 LF=124` vs `GetContent lines=108`）＋ Grep 卡面证明 status 行确实存在，锁定根因为解码器而非解析器——**gov16b 无恙正因它用 `[IO.File]::ReadAllLines`（UTF-8 正确解码）**。修复＝读卡统一 `[IO.File]::ReadAllLines`＋注释行 `^\s*#` 永不闭合块守卫；修后 comp 六形与 72 **逐字吻合**，构成编码修复的双重验证（72 权威值作回归锚）。次代 gov17c 单遍同时产九轴与四块值，首次跑出 `E3=0` 与 gov16b 的 `E3=2` 漂移，逐卡定位为 `taskId: null` 字面量在 gov16b 里显式判 null、gov17c 仅判空串——补 `-or ($taskId -eq 'null')` 后九轴 `G=73 PL=7 X=27 M=46 E=37 S2=26 R=4 N=35 E3=2` **逐轴与 gov16b 全等**；另立 gov17d 独立复算 `taskId: null` 卡数＝**2**（P0B-ENG-001、P0B-V01-V12，即 E3 二卡），与 `spec=null` 二卡名单互证。PS 5.1 无三元运算符（`?:` 直接 ParserError，改 if 块）；内联 `-Command` 复发性吞 `$`（已知教训），一律落临时 .ps1＋`-File` 执行。脚本全置系统临时目录、不入库；DEC-009：全程只读取证，未改任何产品码/契约/tasks/evidence/CI。

**总账发现（130 张 EVD 卡，gov17b＋gov17c 两引擎交叉，EXIT=0）**：
- **四子块全值谱**：`spec PASS=128 null=2`｜`taskv PASS=82 null=40 FAIL=7 FAILED=1`｜`cpv PASS=78 null=45 FAIL=6 FAILED=1`｜`comp null=44 EXPECTED_FAIL=37 PASS=32 FAIL=14 notRun=2 NOT_RUN=1`（comp 六形与 72 L3057 逐字一致）。`specValidation` 128/130＝98.5% 声称 PASS、**无一诚实 FAIL**，是最「装饰性」的自证块；`taskv/cpv` 的 null 声称高达 40/45——多数旧代卡根本不填后两块。
- **最紧约束＝cpv**：`cpv=PASS` 数（78）**恰等于**「前三子块全 PASS」（f3）数（78）——即 `cpv=PASS ⟹ spec=PASS ∧ taskv=PASS` 在全库成立，f3 的上界被 cpv 单独决定；spec（128）、taskv（82）皆宽松于 cpv。
- **f3 × 九轴 facet 矩阵（共现核心）**：f3 卡按缺陷叠合分桶 `facet=0:14[PASS13 DRAFT1]`、`facet=1:25`、`facet=2:6`、`facet=3:12`、`facet=4:18`、`facet=5:3`——**`f3 ∧ facet≥3 = 33`（其中 PASS 态 31）**；非 f3 卡 `facet=0` 桶为空（无「既不声称三块全 PASS、又九轴全零」者），主体落 `facet=2:37`。含义：越级/幽灵旧代大量同时满足 validator 的 spec/task/changedPath 三 gate（f3=True）却叠 ≥3 条物理缺陷——**三 gate 与物理诚实几乎正交**。
- **`comp=PASS` 反转铁证（32/32）**：32 张声称 completion 已验证的卡 **全部携幽灵 sha（G=32/32＝100%）、无一九轴全零（facet=0=0）、31 张叠 ≥3 轴**；而 77 认定的 **13 张九轴全零 PASS_CLEAN 卡无一声称 `comp=PASS`**（其 comp 值全为 `EXPECTED_FAIL`×11 或 `FAIL`×2）——自证面的 completion 声称与物理面的幽灵载体呈**完全反相关**，把 72「comp 不被校验」的静态观察升级为「comp 声称越强、底层 commit 越不存在」的量化反转。
- **`PASS × f3` 脱钩率**：status=PASS ∧ f3=True 共 **65 卡**（承 gov17b），其中幽灵 32——**49% 的「全绿声称」PASS 卡底层 commit 不可解析**；`PASS ∧ ¬f3` 仅 5 卡（[EVD-P0BR-077-001](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-077-001.yaml)/078 `cpv=FAIL`＋ENG-001/V01-V12 `spec=null`＋[EVD-P2006-001](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml) `taskv/cpv/comp=null`）——诚实报 cpv/taskv 非绿的新代卡反而少。
- **异形（variant）卡**：`FAILED`/`NOT_RUN` 第七形唯一载体 P2009（`G=False`，facet=1 仅 M）；`notRun` 小写形 P5002/P5008（`G=False`，facet=2 M+E）——三卡皆 `G=False`（非幽灵），说明值谱卫生缺陷与幽灵族**不共线**，是独立的数据卫生问题。

**机理（自证面与物理面脱钩 49%，且 completion 声称反转）**：validator 唯一会 re-match 的 governance 面是 spec/task/changedPath 三块的 `result: PASS` 字面（[L703-714](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)），且**只匹配字符串、从不重跑被声称的校验**，`comp` 块更完全不在 gate 名单。于是卡面形成一个**自证绿洲**：`specValidation` 128/130 全绿、f3 卡 78/130（60%），与九轴物理缺陷（G=73、facet≥3=37）**并行不悖**。共现矩阵揭示绿洲下的三向结构：① f3（validator 认的三块）与「幽灵 sha」（validator 靠 L716-719 另轨 `commitSha==HEAD` 才拦、且历史卡已漂移）近似正交——三 gate 通过者照样是 ghost 载体；② `comp`（不被 gate）与幽灵**反相关到 100%**——填 `comp=PASS` 的人恰是那批把幽灵 sha 写进 `source.commitSha` 的旧代，等于「越是自认完工、越没有可解析的完工提交」；③ cpv 作为 f3 唯一紧约束（78=78）意味着 validator 的三块 gate **实为 changedPathValidation 单块 gate**，另两块近乎恒绿。九轴全零的 13 卡 comp 全报 EXPECTED_FAIL/FAIL 不是巧合而是**因果**：正因为不谎报 completion，才没触发把幽灵/占位 sha 写进去的那条链——诚实自证面与干净物理面**同向**，谎报自证面与幽灵物理面**同现**。

**已核验（机理性）**：① comp 六形值谱（null=44/EXPECTED_FAIL=37/PASS=32/FAIL=14/notRun=2/NOT_RUN=1）在编码修复后与 72 L3057 **逐字复现**——既验本节引擎、又反向验 72 数字未受同类 GBK 缺陷污染（72 用独立 Grep 口径）；② 九轴总量 `G=73 PL=7 X=27 M=46 E=37 S2=26 R=4 N=35 E3=2` 与 77 gov16b **逐轴全等**（gov17c 补 null 判定后），`taskId: null=2` 由 gov17d 第三引擎独立复算并与 `spec=null` 二卡名单交叉印证；③ 幽灵/快照双轨六卡（P1015/1016/1017/1018/P2013/P3010）承 72 L3063 未再动；④ `comp=PASS → G` 32/32、`PASS×f3=65 幽灵 32`、`f3 facet≥3=33`、`cpv=PASS=78=f3 总数` 均为逐卡打印名单经聚合、非推断；⑤ validator「只 gate 三块、不 gate comp」由 [L703-714](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) 源码直读坐实（`foreach` 名单＋`result: PASS` 正则＋comp 缺席），非从数字反推。

**偏差与 unproven**：① 三子块值谱采**块级解析器**口径（`Get-ResultRaw` 前瞻跳过注释/空行取 token），与 72 的 Grep `result:\s*PASS\s+#` 内联注释形口径不完全同——注释形实测 5 卡 6 处（ENG-001 spec、066 spec+cpv、068 spec、069 spec+taskv、P2009 spec），其中 ENG-001/P2009 的注释形是否落 `PASS` 形影响与 72 那 6 处的逐一对齐，成文按块级口径为准、Grep 口径差异登记不追平；② `comp=PASS ≡ PASS×f3∩G` 由**基数夹逼**成立（gov17b：PASS×f3=65 内 comp=PASS 32、ghost 32；值谱 comp=PASS 全局 32；gov17c：comp=PASS 32 全 G ⇒ 32⊆32 同基数即集合相等），但两引擎同名数字的**逐卡名单 diff 未打印复核**，恒等按算术推定而非直接实测；③ facet 计数承 77 偏差⑤（重名代际经 `taskId` 共桶，按卡计数正确、按缺陷 TASK 计数须去重、后者未做）；④ 矩阵只量**卡面字符串声称与物理 sha 存在性的共现**，不裁决任一 `result` 断言背后校验是否真跑过（validator 从不重跑，Docker/Compose 亦缺席——同 74 偏差①）；null 声称（taskv=40/cpv=45）究竟是「未跑」「未填」还是「不适用」逐卡未核；⑤ `FAILED`/`NOT_RUN` 第七形样本 n=1（P2009），异形族的完整分布未在 spec 块外验证；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① validator **governance gate 从「字符串匹配」升级为「重跑或交叉校验」**：[L703-714](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) 现只 re-match `result: PASS` 字面、且 `completionValidation` 不入名单——应把 `comp` 纳入 gate 并要求 governance 四值与 `source.commitSha` 可解析性（cat-file）、status 语义**交叉**（`comp=PASS` ∧ `commitSha` 幽灵应判 Add-Error，本节证此合取占 32 卡）；② **值谱枚举白名单**：spec/task/changedPath/comp 四块 `result` 限 `PASS|FAIL|notRun|EXPECTED_FAIL|null` 受控词表，禁 `FAILED`/`NOT_RUN` 同义异写（72 R-4 卫生条从 comp 扩到四块），并禁 `specValidation` 无诚实 FAIL 的「恒绿」漂移（现 128/130）；③ **`comp=PASS ⟹ commitSha 可解析` 立为硬不变量**、纳入 77 整改④ PASS_CLEAN=13 回归基准的负向样本集（32 张 comp=PASS×幽灵卡为其须转为不告警的对照）；④ EVD-P2009-001 的 `FAILED`/`NOT_RUN` 异形并入 72 整改① 存量豁免轨的**值谱卫生子轨**单独处置（其 G=False、不属幽灵族，勿与 sha 溯源混条）；⑤ 取证方法论追加教训入 72 偏差④/gov16b 条并**升格为独立硬规则**：读卡/读契约等**数据文件**（可含非 ASCII）一律 `[IO.File]::ReadAllLines`，**禁用 `Get-Content` 默认解码**——「脚本全 ASCII」不覆盖被读文件编码，GBK 吞换行会静默篡改行数与值谱，须以既有权威数字（本节用 72 comp 六形）作**编码回归锚**。

**下一节候选域（第十七次查重预告）**：承 78。倾向 (a)：**(a) validator 对 governance 四块的「gate 覆盖面 × 实际可解析性」自相矛盾闭环**——78 已证 L703-714 只 re-match 字符串，可进一步量化「validator 判 PASS 的卡」与「cat-file 判 commitSha ABSENT 的卡」的交集（预期＝G 家族子集），把「校验器绿 ≠ 底层真」做成对 validator 自身的证据级证伪；查重条款：须给出 validator 判定结果与物理核验的**逐卡背离表**方算增量，仅复述 78 的 32/32 即判废。(b) **`status` 三态 × 四块 `result` 全 5×6×… 联合分布热力**（比 78 的单块×九轴更细的自证面内部一致性），增量偏薄、缓做。78 未做且可延后：三子块 null 声称（40/45）与旧代/新代世代轴的交叉，以坐实「null＝旧代不填」假设。

→ 本项与 ①—78 一并不在本轮 AI 自决实施范围内。

### 79. 全库 126 张 TASK 卡 × validator 双态实跑（default/completion 252 次）× 影子物理面五字段双源核对 × 逐卡背离表（78 (a) 落槌：gate 覆盖面与可解析性矛盾首次实跑量化）——default 态仅 **2/126 全绿**（P0BR-077/078，且为 shadow 中唯二 `eqHEAD=True`），sha 族集合 **32(shaLen)+89(baseHead)+2(全绿)+3(无 manifest)=126 闭合**；completion 态 **exit0=0**、`outside` 族 **55161 issues 淹没 92 卡**；背离 B=**34 张 ghost×PASS 卡全部 compExit=1、evStatus=0、evShaHead=1**——拦下它们的是 [L719](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1) sha 轨而非 status 轨；背离 A=3（079/080/081 manifest 缺失）；双源 FIELD_DIFFS=0；P2-009/P5-007 completion 900s 挂起 unproven（2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十七次）**：承 78 候选域 (a)。78 预告自设条款「须给出 validator 判定结果与物理核验的**逐卡背离表**方算增量，仅复述 78 的 32/32 即判废」——本节不但给出背离表（A 组 3 卡、B 组 34 卡逐卡五列：compExit/evStatus/evShaHead/evExist/total，另附影子五字段），且把取证对象从 72/77/78 的**卡面字符串与静态源码**升级为 **validator 本体实跑**：全库 126 卡 × default/completion 双态 = 252 次执行、55 列账目、49 族＋unknown 全分类，此在任何既有节无先例。落槌：**(a) 成立为 79**。查重坐实：既有节对 validator 的引用全部是源码直读（72 L704-714、78 同上），从无逐卡运行结果账；影子 manifests 此前只在 77 作九轴数据源使用，从未与 validator 判定做逐卡对撞。

**权威锚点**：default 态结构族 [validate-task-card.ps1#L191](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（baseCommit 须 40 位十六进制——32 卡占位 sha 在此报错并短路）、[#L202](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（baseCommit 不得等于 HEAD——89 卡命中；L191 先于 L202，故占位卡不进此族）、[#L225/#L230](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（approvedBy/approvedAt 各 35 卡）、[#L342](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（gateRefDec 29 卡）；completion 态证据族 [#L646](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（outside 55161/92 卡）、[#L680](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（evExist＝manifest 缺失 3 卡）、[#L712](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（evGov 95/49 卡）、[#L719](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（evShaHead 121 卡——背离 B 的实际拦截轨）、[#L722](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（evDirtyWt 33）、[#L746](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（unknown 真身：`Evidence coverage proves a scenario outside the Task`，4 条）、[#L779](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1)（noProv 110/48 卡）；全绿样卡 [TASK-P0BR-077.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-077.md)/078（default exit=0、passSummary=1；completion 仍 32/30 不豁免）；挂起对 [TASK-P2-009.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P2-009.md)/[TASK-P5-007.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P5-007.md)。九轴与 ghost 集承 77 gov16b 定稿；背离 B 的 34 卡集与 78 的 `comp=PASS` 32 卡集同族不同轴（本节量 validator 实跑判定，78 量卡面声称）。

**取证方法（gov18a→l 十二脚本演进，含 ExitCode 自纠史）**：核心管线＝gov18g 全库 252 次实跑（子包装器 `gov18g_child.ps1` 末行打印 `__XIT__<exitcode>` 哨兵，父进程正则解析；无哨兵→-99 超时 Kill/−98 缺失）＋ gov18i 结构守门（252 行×55 列零违例）＋ gov18h 内容聚合（495 行：双态族表、SHADOW rollup、背离 A/B 逐卡表、各族名单）＋ gov18j 第二引擎独立复算 ＋ gov18k 影子双源逐字段 diff ＋ gov18l unknown 归因。**ExitCode 自纠史**：PS 5.1 `Start-Process -PassThru` 句柄在工作区刷新后对失败进程仍返回 0（实测坐实彻底不可信）→ 弃用句柄、改哨兵打印法，此为本节账目可信的前提。**分类器**：49 族有序正则字典（首中即归，`$fam` 字典 gov18g L19-68），gov18j 复算族总量 `FAM_SUM=56956 + unknown 4 = TOTAL_SUM=56960` 严丝合缝（famIdx 有意排除 unknown 末列）——**族分类零泄漏实证**。gov18l 逐字复制该字典重跑 unknown 四卡（600s），4/4 未匹配原文全为 L746 措辞（分类器 L699 只收卡侧 `contains a scenario outside` 措辞，未收证据侧 `coverage proves...outside`——缺口在工具、不在数据）。gov18k 影子双源：新源 126 行 vs 旧引擎 gov18b 102 行 SHADOW（旧头自称 CARDS=126、打印截断，属旧引擎自身缺陷），共同 102 卡五字段（manifest/exists/status/ghost/eqHEAD/dirtyWt）**FIELD_DIFFS=0**，ONLY_NEW=24 定性为打印范围差；两源同 HEAD `a0227961`。超时终局处置：P2-009/P5-007 completion 经 300s×2＋900s 三窗全超时 → 一律标 unproven；其余 124 卡（含 unknown 四卡 600s 内跑完）佐证挂起点为两卡专属而非全局性能。关键数 CSV 原文逐行 Grep 复抽（GOV-007 813、P0BR-054 843、077/078 双态行）全部对上。产物全置系统临时目录、不入库；DEC-009：全程只读（实跑 validator 不改卡面/产品码/契约/CI）。

**总账发现（126 卡 × 2 态 = 252 实跑 + 126 影子 manifest）**：
- **default 态（exit0=2，exit1=124）**：全库仅 [TASK-P0BR-077.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-077.md)/078 两张全绿，且正是影子中 `status=PASS manifests=66` 里唯二 `eqHEAD=True` 的卡——新代诚实卡结构与 validator 默认态判定完全咬合。结构缺陷群：**shaLen 32 卡**（占位 baseCommit，L191 报错即短路、L202 从不求值——推翻 68 以来隐含的「sha 问题每卡一条 baseHead」口径）、**baseHead 89 卡**（40 位真值但等于 HEAD）、apprBy/apprAt 各 35、gateRefDec 29、sepBad 60 issues/23 卡、rvcCompose 54/29；按卡集合 **32+89+2+3=126 闭合**（079/080/081 三卡 default 态 sha/baseHead 零命中：6/6/5 issues，manifest 缺失使其无从进入 sha 轨）。
- **completion 态（exit0=0，exit1=124，TIMEOUT 2）**：**全库无一张卡能过 completion 全绿**——该模式作为放行门事实上从未工作。`outside` 族（L646）以 **55161 issues 淹没 92 卡**（占全库 56960 issues 的 96.8%），top5＝P0BR-047 869、053 865、052 854、056 851、048 842；GOV-007 813（outside 807）、P0BR-054 843（outside 836）经 CSV 原文复抽坐实。077/078 亦不豁免（completion 32/30，outside 24/22 为主）——连全绿卡的证据面都超契约 AC 名单。证据族侧：evStatus 55、evShaHead 121、evGov 95/49、evNotProven 103、evAccepted/evReviewer/evReviewAt 各 115、noProv 110/48、evLevelLow 62、evSkipped 36、evDirtyWt 33、dirtyChanged 4。
- **背离 B=34（gate 覆盖面矛盾的核心实体）**：影子 `status=PASS ∧ ghost=True` 的 34 张 manifest 卡（P0BR-000A/000B/001-004/010-017/020-030/040-046＋P1-015 total=418＋P2-006 total=771）逐卡在 completion 态 **compExit=1、evStatus=0、evShaHead=1、evExist=0**——validator 对「PASS」声称本身**零质疑**（status 语义轨不拦），真正拦下全部 34 卡的是 L719 `commitSha==HEAD` 物理轨；影子侧独立正则计数（`,True,PASS,True,False,`）GPASS 34 行/34 文件与本卡集互证。
- **背离 A=3**：P0BR-079/080/081 是全库唯三 manifest 缺失卡（影子 exists=False=3 ↔ evExist=3，双向对撞成立）——背离形态是「**无从比对**」而非「沉默放行」。
- **影子 rollup**：PASS 66（ghost 34、eqHEAD 2、dirtyWt 27）／PARTIAL 33（ghost 21、eqHEAD 0、dirtyWt 8）／DRAFT 24（ghost 23、eqHEAD 0、dirtyWt 0）——**eqHEAD=True 全库仅 2/126**，与 default 全绿二卡同一集合。
- **unknown=4**：P0BR-045(AC-P0B-ENG-004)/052、056(AC-P0B-IDM-003)/P4-001(AC-P4-MCP-001) 各 1 条，全部归因 L746 新消息族（evCovOutside），分类器缺口、非数据异常。

**机理（为什么「gate 覆盖 ≠ 物理可解析」在实跑面成立）**：validator 的双态是两套判定层的叠加：default 只跑卡面结构 gate（L191/202/225/230/342…），completion 才打开证据轨（L680-779）。实跑揭示三个结构性事实：① **status=PASS 声称从不被 re-verify**——evStatus 族（55 卡）只拦「声称值不合语义格式」者，对「声称 PASS 但底层 commit 不存在」的 34 卡**零命中**，与 78 静态观察「L703-714 只匹配字符串」在运行面等值成立；把 ghost-PASS 拦下的是 L719 这条**独立物理轨**，即平台的「PASS 有效性」完全寄生于 sha 可比性——一旦 sha 与 HEAD 对齐（新代 2 卡）或 manifest 缺失（079/080/081 三卡 evExist），status 轨自身无所作为。② **outside 是组合放大器**：L646 按「每条 evidence 覆盖的 scenario × AC 名单」逐对报错，旧代卡普遍携超出自身 AC 名单的取证（92/124 命中、每卡 22-869 条），使 completion 态 exit 码被单一族恒压为 1——exit0=0 不是「所有卡都有 55161 个缺陷」，而是「该 gate 的噪声率使它不可能作为放行门使用」。③ **占位 sha 短路链**：L191 先于 L202 的结构使 32 张占位卡的 baseHead 缺陷**不被计数**——77 九轴（物理轨 G=73/PL=7）与本节 89+32 的口径差正来源于此：卡面缺陷族按「第一个报错的 gate」归属，物理轴按「事实存在性」计数，两套账的并集与交集本节已逐卡打印闭合。

**已核验（六重可信链）**：① 结构守门 gov18i：252 行×55 列零违例；② 内容聚合 gov18h 495 行全读（双态族表/rollup/背离表/名单逐段核）；③ 第二引擎 gov18j：族总量交叉 `56956+unknown4=56960` 闭合、GPASS 独立计数 34/34 与背离 B 卡集吻合、超时双卡复跑再现 -99；④ 影子双源 gov18k：共同 102 卡五字段 FIELD_DIFFS=0、ONLY_NEW=24 定性为旧引擎打印截断（其头行 CARDS=126 自证覆盖面）、两源同 HEAD；⑤ unknown 归因 gov18l：4/4 逐卡复现、逐字打印未匹配原文并锚定 L746；⑥ 关键数 CSV 原文 Grep 复抽（813/843/077/078 双态行、top5 计数）逐字对上。另：sha 族按卡集合 32+89+2+3=126 闭合为名单级实测非推断。

**偏差与 unproven**：① P2-009/P5-007 completion 挂起 unproven（三窗超时、payloads=0），其 completion 账目缺席，全库 completion 统计实为 124 卡口径；P2-009 另有 77 段 CPU 1035s 史证，两卡同为 PARTIAL＋dirtyWt=true＋（P2009）工作区 M 态脏 evidence，挂起点未定位；② unknown 族名记 evCovOutside 系**本次取证工具分类器缺口**（L699 正则清单未收 L746 措辞），修后 4 条应并入 Evidence 族——不影响 validator 侧任何数字；③ outside=55161 是 issue 条目和（per-evidence×per-AC 组合放大），非独立缺陷数，卡级口径 92；④ completion exit0=0 与 evDirtyWt=33、dirtyChanged=4 系**当前脏工作区**（079/080/081 untracked、EVD-P2009-001 为 M）下实测，干净 checkout 复跑数值会变，unproven；⑤ ONLY_NEW=24 以旧引擎自身截断定性，未修旧引擎重跑对照；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **outside 族（L646）降噪或分级**：96.8% 的 issue 占比使 completion 态不可用——要么把「evidence 覆盖 scenario 超出 Task AC 名单」降为 warning 不入 exit 码，要么立契约条款「AC 名单必须全列 evidence 所覆盖场景」并给存量 92 卡整改轨（涉契约改动，须人类裁决）；② **completion 模式要么做通要么显式废止**：exit0=0 证明其从未作为放行门工作；若保留，以 077/078 为基线给出「卡需满足的最小证据集」模板；③ **evStatus 轨补「PASS 声称 ∧ commit 不可解析 ⇒ Add-Error」合取**（承 78 整改①，本节以 34/34 逐卡实跑为新增证据）；④ P0BR-079/080/081 补建 EVD manifest（数据补齐，不触契约）；⑤ P2-009/P5-007 挂起点定位后再谈这两卡的 completion 账（下节候选 (a)）；⑥ 方法论沉淀入 72 偏差④/gov16b/gov17 编码条谱系：PS 5.1 `Start-Process` 句柄 ExitCode 不可信 → **哨兵打印法**（子进程末行 `__XIT__`）为长实跑标准姿势；跨会话引用旧产物格式必先 Grep 样例行核实（gov18k 首版按记忆假设 CSV 前缀解析落空的教训）。

**下一节候选域（第十八次查重预告）**：倾向 (a)：**(a) P2-009/P5-007 completion 挂起机理定位**——以临时卡副本做二分（裁剪 evidence 条目/替换 manifest sha/去 dirtyWt 轴），把挂起点钉到 validator file:line，并给出「同构数据但不挂起」的最小反例；查重条款：**须钉出挂点行号并给不挂起对照样本方算增量**，仅复述 79 的两卡超时事实即判废。(b) taskv/cpv null（40/45）× 卡世代轴交叉（78 遗留「null＝旧代不填」假设），增量偏薄、缓做。(c) 「completion 整改模拟账」：outside 92 ∪ evStatus 55 ∪ noProv 48 卡集并集测算全库过门所需最小改动面——属纸面推演，可与 (a) 合并为挂起解除后的全量复跑。

→ 本项与 ①—79 一并不在本轮 AI 自决实施范围内。

### 80. P2-009/P5-007 completion 挂起机理定位（79 候选 (a) 落槌：git 假设证伪 × 模式二分 × ReDoS 定罪——挂点钉至 [L711](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L711) 的 [L709-710](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L710) 嵌套量词正则，指数阈值实测 ~15 行 2s／~20 行 >10s／35 行 >45s 下限；2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十八次）**：承 79 候选域 (a)。79 自设条款「**须钉出挂点行号并给不挂起对照样本方算增量**，仅复述 79 的两卡超时事实即判废」——本节交付：① 挂点 file:line（L711 求值、L709-710 致病模式）；② 三重不挂起对照（077 同构块 0-2ms、P2009 同块截断 N≤10 仅 1-198ms、同块去 `s` 旗标 `(?m)` 变体 0ms）；③ 79 遗留两候选挂点（L600 远古 diff、L605 ls-files）**全部实测证伪**，此为 79 从未做过的排除法。既有节中 79 只记「三窗超时 unproven」，无节触及挂点。落槌：**(a) 成立为 80**。

**权威锚点**：致病模式构造 [validate-task-card.ps1#L709-L710](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L710)（`"(?ms)^\s+NAME:\s*\r?\n" + "(?:^\s{4}.*\r?\n?)*?^\s{4}result:\s*PASS\s*$"`）、求值触发点 [#L711](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L711)（`$governanceBlock -notmatch $validationPattern`，外层 [#L704-708](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L704-L708) foreach 三 name）、块抽取器 [#L87](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L87)（Get-YamlBlock 本身实测 1ms 无罪）；被证伪候选 [#L600](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L600)/[#L605](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L605)；completion 段入口 [#L659](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L659)；数据侧：[EVD-P2009-001.yaml#L49](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2009-001.yaml)（`result: PASS # 收口轮实跑…` 行内注释使 `PASS\s*$` 锚不可达）、同文件 taskValidation/changedPathValidation `result: FAILED` 两 name 同样不可达、[EVD-P5007-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5007-001.yaml)（specValidation 裸 PASS 但 `detail: >-` 多行块推高四缩进行数）。

**取证方法（gov19a→f 六脚本渐进排除）**：第一步 gov19a 证伪 79 段末「coverage 正则 L740 嫌疑」——三 EVD 的 scenarioId 行 maxLen 31-35、逐行 IsMatch 全 <100ms。第二步 gov19b 钉 git 侧：两卡 baseCommit（`26c3985c`/`59f40d28`，距 HEAD 161/157 提交）之 `diff --name-only`＝**503ms/775ms**（764/741 文件），`ls-files --others`＝381/361ms，`merge-base --is-ancestor` 全快——**远古大 diff 假设死亡**。第三步 gov19c 模式二分（子进程 + WaitForExit(ms) 墙钟判定）：两卡 `-CheckChangedPaths` 单态 **2819ms/2754ms 完成**（758/741 条 outside 全量打印），`-CheckCompletion` 双双 **TIMEOUT_KILLED@240s**；对照 077 双态 560ms/512ms——挂点域收敛至 L659 后证据段。第四步 gov19e 进程隔离定罪：以 validator L87 原正则抽 governance 块，逐 name 在可杀子进程跑 L711 原式——P2009 三 name 全 TIMEOUT_45S、P5007 两 TIMEOUT＋changedPathValidation NOMATCH@523ms、077 三 name 0-2ms。第五步 gov19f 进程内定量：10s cap 的 `[regex]::IsMatch` 抛 `RegexMatchTimeoutException`（即挂死实锤——validator 静态调用无超时＝InfiniteMatchTimeout）；**N 截断缩放曲线**（P2009 块取前 N 行）：N=5→1ms、N=10→198ms、N=15→>10s、N≥20 全触 cap；同输入去 `s` 旗标 `(?m)` 变体全部 **0ms**——指数律与致病旗标同时锁定。产物全置系统临时目录、不入库；DEC-009：全程只读 validator 与数据，无卡面/产品码/契约改动。

**总账发现**：
- **挂点钉死 L711**（求值行）／L709-710（致病模式）：`(?ms)` 使 `.` 跨行，贪婪 `.*` × 可选 `\r?\n?` × 外层惰性 `*?` 对「N 行四缩进块」给出指数多种切分；当 `^\s{4}result:\s*PASS\s*$` 锚不可达（FAILED 值、或 PASS 后带 `# 行内注释`），引擎穷举全部切分——静态调用无匹配超时 ⇒ 事实挂死。实测阈值：~15 四缩进行 2.4s、~20 行 >10s、35 行 >45s 未归（下限），即 O(2^N) 级。
- **唯二触发原因**：全库 completion 能跑的卡中，governance 块达到 ~20+ 四缩进行 **且** 被查三 name 至少一个锚不可达的，仅 P2009（35 行，三 name 全不可达：1 个行内注释＋2 个 FAILED）与 P5007（27 行，specValidation 匹配路径被多行 detail 推爆＋taskValidation FAILED）。79 账内 evGov 95 issues/49 卡全部 ≤900s 跑完＝块短或 name 无候选起点（key 不存在时 `[regex]` 无锚点即快退）。
- **79 两条候选挂点全灭**：L600/L605 合计 <1s，与「changed 单态 2.8s 完成」互相印证（该 2.8s 已含 L596-657 全段）；77 段 P2-009 CPU 1035s 史证亦归因同一 ReDoS（git 子进程 CPU 仅百毫秒级）。
- **附带语义盲区发现**：P2009 L49 的 `result: PASS # 注释` 即使正则跑完也是 NOMATCH→L712 报错——但人眼读作 PASS。EVD 行内注释写法与 L710 严格 `$` 锚存在**口径冲突**（数据写法和校验器各说各话），全库 `result: PASS # …` 写法实测 5 处（066×2/068/069×2/P2009×1 行号见 Grep）。
- **79 账修正**：79 之「completion 124 卡口径」不受影响；两卡 completion 结果定性由「unproven 挂起」升级为「**根因已定位的确定性挂死**」——只要不改 L709-710，任何重跑、任何超时窗都永不出账。

**机理（为什么 gate 在此卡死而在他卡放行）**：① 该检查的语义是「governance 下每个校验名须有 `result: PASS`」，实现选择了单条带惰性量词的跨行正则而非逐行状态机；② 失败是常态（旧代卡大量 FAILED/带注释），而**恰是失败穷举触发指数**——即 gate 在最需要它给出错误消息的地方挂死；③ 静态 `IsMatch`/`-notmatch` 不传 match timeout（.NET 默认 Infinite），PowerShell `-notmatch` 运算符无超时重载——无兜底。

**已核验**：① 三重对照样本（077 全 name 0-2ms、N≤10 截断 1-198ms、`(?m)` 变体 0ms）满足 79 自设条款；② 定罪模式串与 validator L709-710 逐字符一致、块抽取与 L87 逐字符一致（gov19e 直接复制源函数）；③ 超时判定用墙钟 `WaitForExit(ms)`＋Kill，不依赖 PS5.1 不可信句柄 ExitCode（79 纪律）；④ 两卡 `preExistingDirtyPaths: []`（L624 Get-FileHash 分支不可达）排除在册；⑤ 祖先性与 diff 计时直测（gov19b 12 行全 DONE）。

**偏差与 unproven**：① validator 真实进程内堆栈未采样（以逐字符等价的子进程隔离复现替代，等价性由模式/输入同源性保证）；② 900s 超时窗与单次 IsMatch 真实耗时的精确对应未测（10s/45s 均为下限证据，未做无限等待归本测定性）；③ 全库其余 47 张 evGov 卡的块长未逐一测量，「均低于阈值」由 79 完成时间账反推；④ gov19c 的 ExitCode 列空转（未打 `__XIT__` 哨兵，79 教训重演但本节节论仅依赖 DONE/TIMEOUT 墙钟判定，不受影响）；⑤ 挂死结论成立于当前 PS5.1/.NET Framework 正则引擎，PS7/.NET Core 引擎回溯路径数可能不同，unproven；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **L709-711 重写**（涉工具契约、须人类批准）：任一即可——去 `s` 旗标改逐 name 行锚正则（实测同数据 0ms）、`\s{4}`→`[ ]{4}` 且换行符改必选单义、或改用带 `TimeSpan` 超时参的 `[regex]::IsMatch` 重载并超时报 ERROR；② **EVD `result:` 行内注释口径裁决**：要么校验器容忍 `PASS #…`，要么立写法规范禁止 `result:` 行加注释并整改存量 5 处——与 79 整改②（completion 做通或废止）合并处理；③ 整改①落地后 P2-009/P5-007 completion 补跑归账（预期出 evGov 族错误而非挂死）；④ 方法论入 72 谱系：**「数据面驱动的嵌套量词正则，定罪必走『隔离块＋N 截断缩放曲线＋旗标对照』三件套」**（gov19f 姿势），嫌疑正则先过逐行探针再定罪（gov19a 对 79 静态嫌疑的证伪姿势）。

**下一节候选域（第十九次查重预告）**：(a) 「completion 整改模拟账」（承 79 候选 (c)）：outside 92 ∪ evStatus 55 ∪ noProv 48 卡集并集测算全库过门最小改动面——纸面推演，须给出并集去重后的卡级最小动作清单方算增量，仅复述三族计数即判废。(b) EVD 行内注释语义盲区全库普查（本节附带发现扩展）：`result:` 行值域×注释率×与 validator 判定面的对撞——**须给出超越本节 Grep 5 处抽样的全库账方算增量**。(c) taskv/cpv null（40/45）× 卡世代轴（78/79 两度遗留）：若 (a)(b) 之后仍无先例增量则永久除名。

→ 本项与 ①—80 一并不在本轮 AI 自决实施范围内。

### 81. EVD governance 块全库普查（80 候选 (b) 落槌：130 文件×39 name 级直接测量——「唯二触发」卡级成立、state-machine 判据证伪出 4 项误报；挂起判据由「行数×锚不可达」精化为「燃料区歧义密度」；80 顺勘两处——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第十九次）**：承 80 候选域 (b)，其自设条款「**须给出超越本节 Grep 5 处抽样的全库账方算增量**」——本节交付：① 全库 130 个 EVD 的 governance 块长 N4 谱（gov20，非抽样）；② 高嫌疑 12 块×3 name＝**36 name 级 45s-cap 直接测量**（gov20b，含 80 未测的 P5006/P5008/070-076 watch 群）＋逐 name 燃料/锚点模型账（gov20c，130×3＝390 判定）；③ 行内注释 `result: PASS #…` 全库确账 6 处（Grep 复跑，行号逐列）；④ 80 顺勘两条＋偏差③闭合。既有节中 80 只测 3 块 9 name，无全库账。落槌：**(b) 成立为 81**；候选 (a)「completion 整改模拟账」再次顺延（两度立项未落，见候选域段除名条款），候选 (c) null×世代轴维持 80 判据（无先例增量则永久除名）。

**权威锚点**：数据面＝`docs/ai-spec/tasks/evidence/EVD-*.yaml` 全 130 文件（均含 governance 块，无缺块）；致病模式＝validate-task-card.ps1 [L709-710](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L710)（80 已钉，本节沿用其实验装置 gov19d_run 子进程）；关键实据：[EVD-P5007-001.yaml#L3](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P5007-001.yaml#L3)（specValidation 裸 `result: PASS` 就在 header 下第 2 行，**锚可达却实测挂死**——其下 L8-L26 的 `detail: >-` 六缩进折叠块是燃料区）、[EVD-P0BR-068-001.yaml#L22](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml#L22) 与 [EVD-P0BR-069-001.yaml#L27](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml#L27) 与 [EVD-P0BR-066-001.yaml#L19](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml#L19)（PASS-CMT 被兄弟 name 的裸 PASS「洗白」MATCH）。产物 gov20/gov20b/gov20c 三份 csv+out 全在系统临时目录，不入库；DEC-009：全程只读。

**取证方法（三步）**：第一步 gov20 全库静态账——以 validator L87 同款正则抽块，计「4 缩进起头行」数 N4、逐 name 用**安全行状态机**（无嵌套量词）判 `result:` 值谱（裸 PASS/PASS+注释/非 PASS/缺 name）。第二步 gov20b 把 state-machine RISK 集（N4≥12∧存在不可达 name）∪ watch 集（N4≥19）共 12 块，逐 name 在可杀子进程跑 L709-710 原式（45s cap，`__XIT__` 哨兵收尾）。第三步 gov20c 对全部 130×3 name 建「燃料=header 后所有 ≥4 缩进行数、锚=其后首个恰好 4 缩进的裸 PASS 行」的精确模型账，与实测对撞。

**总账发现**：
- **全库块长谱**：130 文件中 **100 文件 N4=8**（四子块×command+result 标准模板）、9-11 少量、**≥12 仅 14 个**（067/070/068/069/071-076/P5006/P5007/P5008）、最长 P2009=29。主流卡离危险区有 4 倍行数裕度——80 偏差③「其余卡块长未逐一测量仅由 79 完成时间反推」**以本节直接测量闭合**。
- **挂起实账（39 name 级直接测量，全库无遗漏嫌疑）**：挂定点仅 **P2009 三 name（TIMEOUT×3，80 复现）＋ P5007 两 name（spec TIMEOUT＋task TIMEOUT）**；P5007 changedPathValidation NOMATCH@525ms 快退。其余 34 name 全过：watch 群 070-076 全 MATCH 2-20ms、RISK 误报群 066 系/068/069 全 MATCH ≤4ms、P5006 spec MATCH@**680ms**、P5008 spec MATCH@**344ms**（慢于常规两个数量级但未挂）。**卡级「唯二触发」成立**，且新出「近危带」账：P5006/P5008 与 P5007 是同构 FAIL+detail 卡，差在 detail 书写式——**挂起是数据细节级现象，不是波次级**。
- **state-machine 判据证伪（80 机理账精化的实证）**：gov20 的「name 自身子块锚可达性」把 066/068/069 判为 RISK，实测全 MATCH——因真正则 `(?ms)` 下 `.*` 可跨兄弟 name，**后文任意裸 PASS 行可被借用为锚**（洗白机制）；反向：P5007 spec 锚就在第 2 行仍挂——引擎在命中锚前须先穷尽燃料切分，其下 19 行六缩进折叠/多候选点把「首个命中路径」本身推爆。**结论：不存在可靠纸面判据（锚可达性、行数 N4、燃料总数 gov20c fuel 三档模型各有反例——071 fuel=21 MATCH@20ms 均匀四缩进 vs P5007 fuel=21 挂死 六缩进混合），真正的挂起判据是「header→锚之间燃料区的切分歧义密度」（空行、`detail: >-` 深缩进折叠、FAIL/PASS 混合 result: 候选密度），唯一定罪手段是带 cap 的直接测量**。80 已埋「MATCH 路径被多行 detail 推爆」一句话，本节将其升格为主判据并给全库边界。
- **行内注释全库确账（80 顺勘①）**：`result: PASS # …` 写法全库 **6 行/4 文件**——066:19、066:25、068:22、069:27、069:30、P2009:49（80 节正文写「实测 5 处」但同句列举 2+1+2+1=6，笔误）。语义后果逐卡核对：066/068/069 因兄弟裸 PASS 洗白而**校验器实际不误报**；P2009 因三 name 全灭于挂死而未及暴露——即 80 整改②的「口径冲突」在现存库内只影响 P2009 一卡，但属**巧合安全**（一旦有人给 P2009 的兄弟 name 补裸 PASS 或改正则，6 处全部转为 L712 假报错），仍须整改裁决。
- **80 顺勘②（触发原因账修正）**：80 称「P2009 三 name 锚不可达、P5007 spec 匹配路径被推爆」——对；但 80 把 taskValidation FAILED 列为 P5007 挂起原因之一时未测 P5006/P5008（同有 taskValidation FAILED）不挂——**FAILED 本身非充分条件**，须叠加折叠式 detail 燃料。79 的 900s 窗内 47 张 evGov 卡完成账不受影响（全库 ≥12 行的块仅 14 个，其中 12 个已被本节 45s-cap 实测覆盖）。

**已核验**：① 全库 130 文件无一缺 governance 块（NOBLOCK 计数=0）；② gov20b 36 name＋80 期 gov19e 3 name 合计 39 name 级直接测量覆盖全部 N4≥12 嫌疑面，N4≤11 的 116 文件由 79 完成时间账＋80 曲线阈值（11 行<<15 行拐点）双保险排除；③ 行号账 Grep 复跑与 gov20 状态机口径一致（PASS-CMT 4 文件 6 行完全吻合）；④ 全部超时判定走墙钟 WaitForExit(45s)＋Kill，脚本末行 `__XIT__` 哨兵打印后正常退出（79 纪律）。

**偏差与 unproven**：① 「歧义密度」判据为本节定性归纳（三模型×反例对撞法），未做密度量化（如需须造合成块做切分计数——80 的 N 截断曲线的块内版），unproven；② P5006 spec 680ms/P5008 344ms 的耗时构成未拆（MATCH 距离还是燃料切分主导，未测）；③ 390 条 gov20c 预测账中「exhaust fuel<15 ⇒ fast-NOMATCH」的低端未逐条实测（N4≤11 的 116 文件按 80 曲线 N=5→1ms 外推，间接证据）；④ 结论仅成立于 PS5.1/.NET Framework 引擎（80 偏差⑤原样适用）；⑤ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① 80 整改①（L709-711 重写）的回归样本集由本节给出：**必须含 P5006/P5008（近危 680ms/344ms）与 068/069（PASS-CMT 洗白样本）**——仅测 077 会同时漏掉「慢而未挂」与「口径冲突」两类边界；② 80 整改②（行内注释口径）的影响面确账＝4 文件 6 行（见总账第 4 条），其中 3 处（066/068/069）现为巧合安全；③ P5006/P5008 两张 watch 卡在整改前先不要重签 baseCommit 批量复跑 completion（680ms 距超时窗只差数量级，整改落地前任何 detail 加长即转挂死）。
**下一节候选域（第二十次查重预告）**：(a) 「completion 整改模拟账」（**第三顺延**）：outside 92 ∪ evStatus 55 ∪ noProv 48 ∪ 挂死 2 并集去重后卡级最小动作清单——查重条款不变（须出卡级清单方算增量）；**若第二十次查重仍不立项，本候选按 79/80 两度预告未落的先例永久除名，纸面推演类候选不再占位**。(b) 整改方案模拟账（承 80 整改①三方案）：以临时副本实测「去 s 旗标 / `[ ]{4}`＋必选换行 / TimeSpan cap」三写法在全部 39 name 样本上的判定等价性与耗时——**须三方案×全样本矩阵方算增量**（与 (a) 二选一，(b) 有本节现成样本装置、优先）。(c) null×世代轴：80 判据维持，无增量即永久除名。

→ 本项与 ①—81 一并不在本轮 AI 自决实施范围内。

### 82. 整改方案模拟账（81 候选 (b) 落槌：4 写法×16 块×3 name＝**48 name 样本**判定等价性与耗时矩阵——A（去 `s`）与 B（`[ ]{4}`＋必选换行）判定 100% 相同且全样本 ≤3ms、挂起 5→0；二者均**非等价重写**（4 处 MATCH→NOMATCH，全部落在行内注释卡）＝必须与整改②捆绑；C（TimeSpan cap）语义零变化但近危带 708/559/364ms 原样保留——不解决性能只兜底；81 顺勘一条——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿次）**：承 81 候选域 (b)，其自设条款「**须三方案×全样本矩阵方算增量**」——本节交付：① 三整改写法（80 整改①所列 A 去 `s` 旗标／B `\s{4}`→`[ ]{4}` 且换行改必选单义／C 带 `TimeSpan` 超时参重载）加 BASE（L709-710 原式，为量耗时亦须套 cap）共 **4 写法**，在 **16 块×3 name＝48 name 样本**（81 的 39 name 样本集＋补测 EVD-P0BR-066/067/070 三块，使全库 N4≥12 的 14 块与 80/81 全部涉案卡无一遗漏）上逐格给出判定＋墙钟毫秒矩阵（gov21/gov21b/gov21c）；② 语义差分账：与 BASE 的判定翻转逐条归因到具体 `result:` 行；③ 「结构唯一解析」定性论证（A 仍留 `\s{4}` 跨换行歧义、B 消除）；④ 81 顺勘一条（066 cpv 现网即假报错）。候选 **(a)「completion 整改模拟账」第三次预告未立项，按 81 条款永久除名，纸面推演类候选此后不再占位**；候选 (c)「null×世代轴」81 判据（无先例增量）成立，同步永久除名。落槌：**(b) 成立为 82**。

**权威锚点**：被测模式四写法定义见 gov21_run.ps1（临时目录）——BASE＝[validate-task-card.ps1#L709-L710](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L710) 逐字符原式、A＝`(?ms)`→`(?m)`、B＝燃料段改 `(?:^[ ]{4}[^\r\n]*\r?\n)*?` ＋锚段 `[ ]*PASS[ ]*$`、C＝BASE 套 10s cap；判定触发点 [#L711](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L711)（80 已钉）。翻转归因数据锚：[EVD-P0BR-066-001.yaml#L19](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml#L19) 与 [#L25](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml#L25)、[EVD-P0BR-068-001.yaml#L22](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-068-001.yaml#L22)、[EVD-P0BR-069-001.yaml#L27](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml#L27) 与 [#L30](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-069-001.yaml#L30)。产物 gov21/gov21b/gov21c 输出全在系统临时目录，不入库；DEC-009：全程只读 validator 与数据，无卡面/产品码/契约改动。

**取证方法（三步＋补测）**：第一步 gov21_run 单样本 runner——同一块同一 name 依次跑 BASE/A/B/C 四写法，每写法用 `[regex]::IsMatch($block,$pat,'None',[TimeSpan]::FromSeconds(10))`，捕获超时异常记 `TIMEOUT`（即 80 已证的 `RegexMatchTimeoutException` 路径；注意 BASE/C 因套 cap 而不再真挂死，这是为可测性的必要偏离，见偏差①）。第二步 gov21 编排 12 个 gov20_blocks 块＋077 块（gov19e dump）×3 name＝45 样本，逐样本再套一层可杀子进程（60s 双保险）＋`__XIT__` 哨兵。第三步 gov21b/gov21c 补 066/067/070 三块（81 只把 066 计入「066 系 MATCH」但从未 dump 其块、067/070 在 N4≥12 名单内但未直测）共 9 样本。合计 48 样本×4 写法＝**192 次判定测量**。

**总账发现（判定矩阵，48 name 样本）**：
- **耗时账**：BASE＝TIMEOUT×5（P2009 三 name、P5007 spec＋task，均触 10s cap）＋近危带三条（P5006 spec 714ms、P5007 cpv 567ms、P5008 spec 368ms）＋其余 2-24ms；**A 全样本 0-3ms、B 全样本 0ms**（挂起 5→0、无一样本超 3ms）；C 与 BASE 逐格相同（P5006 708ms／P5007 cpv 559ms／P5008 364ms／P2009＋P5007 类 TIMEOUT×5 触 cap）。
- **判定账**：BASE 36 MATCH／7 NOMATCH／5 TIMEOUT；**A 33 MATCH／15 NOMATCH／0 TIMEOUT；B 与 A 逐格全等（48/48 一致）**——即 A、B 二者对现网样本是**同一语义**；C 与 BASE 逐格全等（TIMEOUT 五格由「挂死」变「10s 报错」，性质改变但判定面不引入新语义）。
- **A/B 相对 BASE 的 4 处判定翻转全部是 `MATCH→NOMATCH`**，且逐条对上 81 的「洗白」名单：066 specValidation、068 specValidation、069 specValidation、069 taskValidation——四者的自身子块内只有 `result: PASS # 注释`，BASE 靠 `(?ms)` 跨兄弟 name 借裸 PASS 才 MATCH，A/B 把 `.` 收束到行内后燃料环无法越过 2 缩进的兄弟 name header（`^\s{4}` 失配）⇒ 回归**「本 name 子块自证」的设计语义**。翻转方向单调（无 NOMATCH→MATCH），即 **A/B 严格更严、不放过任何真 PASS**，但会新增 L712 报错——**82 最重要结论：A/B 不是等价重写，落地即引爆 4 行（叠加既有 066 cpv 共 5 行）行内注释假报错，故整改①与 80/81 整改②（`result:` 行内注释口径裁决）是**同一原子变更**，拆开落地必使 066/068/069 三卡 `-CheckCompletion` 由通过转失败。**
- **C 的定位（安全网而非修复）**：语义零漂移、把不可控挂死变可控 ERROR，但**近危带完全不解**（P5006/P5008 与 P5007 同构，81 已证挂起是数据细节级现象——一旦 detail 加长即从 680ms 滑向 cap），且 P2009 三 name 每次 completion 固定烧 30s。C 只宜作 A/B 之外的纵深兜底（A/B＋cap 组合），不宜单用。
- **结构定性（为何推荐 B 优先于 A）**：A 只消除 `.` 的跨行能力，燃料段 `^\s{4}` 里 `\s` **仍含换行**——空行＋3 空格的组合对仍是多义切分（本数据集无此形态，故实测 0-3ms，属样本外风险）；B 用 `[ ]{4}`（字面空格）＋`[^\r\n]*`＋**必选**单个换行，令每条燃料行的解析唯一、切分歧义从结构上归零，与 81 精化的判据「挂起＝燃料区切分歧义密度」正面对治。B 的两处待确认边缘见偏差②。
- **81 顺勘（82 新增事实）**：81 总账第 4 条称口径冲突「在现存库内只影响 P2009 一卡」——不确。EVD-P0BR-066 的 changedPathValidation 自身子块唯一 `result:` 行是 [#L25](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml#L25) 带注释写法、且它是块内最后一个 name（其后无裸 PASS 可借），**BASE 实测即 NOMATCH@8ms ⇒ 该卡今天跑 `-CheckCompletion` 就吃 L712 假报错**。即「巧合安全」的补集——「**已暴露**」在现网有一例，非仅 P2009 潜在。

**已核验**：① 48 name 样本覆盖全库 N4≥12 的 14 块＋80/81 全部涉案卡（含此前从未 dump 的 066）＋077 基线，N4≤11 的 116 文件由 79 完成时间账＋80 阈值曲线双保险排除（81 已核验②沿用）；② A≡B、C≡BASE 的逐格全等是对 48 行原始输出人工比对所得，非抽样；③ 翻转四例的行号与 81 Grep 确账的 6 行完全交叠（4 翻转＋1 已暴露＋P2009 挂死）——三份独立产物（gov20 状态机／81 Grep／gov21 实测）互证；④ 超时判定用 cap 异常而非墙钟挂死，A/B 的低耗时用同进程 Stopwatch（含 JIT 首触，P2009 A=2ms/B=0ms 已含），全部脚本末行 `__XIT__` 哨兵正常退出。

**偏差与 unproven**：① BASE/C 的「TIMEOUT@10s」是对真挂死的 cap 近似（80 偏差②同源）——validator 静态调用无 cap＝InfiniteMatchTimeout，真实表现是无限挂而非 10s 报错，故 **C 的落地形态必须显式传 cap 并超时报 ERROR**，本节账只证明「cap 能把不可控变可控」，不证明 validator 现样跑法的耗时；② B 的锚段写成 `result:[ ]*PASS[ ]*$`（空格类收窄），与 BASE 的 `result:\s*PASS\s*$` 在 `result: PASS\t`（制表符）或 `result:PASS`（零空格）形态下不同——现网 130 文件无此形态（Grep 未见），但**严格等价性 unproven**，落地前应做值谱扫描；③ A/B 的「严格更严」方向由 48 样本单调性支撑，样本外（若存在 6 缩进裸 PASS 且兄弟 name 有裸 PASS 的组合）是否仍单调，未做穷举；④ 192 次测量全在 PS5.1/.NET Framework 引擎（80 偏差⑤、81 偏差④原样适用），PS7 引擎下 A/B 是否同样归零耗时未测；⑤ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① 80 整改①的**选型结论**：首选 **B（或 B＋cap）**，A 为可接受但结构上仍留 `\s{4}` 换行歧义的次选，C 单用判为**不足以整改**（近危带不解＋每次 30s 固定开销）；② **强制捆绑条款**：整改①落地（A 或 B）必须与 80/81 整改②（`result:` 行内注释口径）同一批次，并同批整改存量 6 行注释（066:19、066:25、068:22、069:27、069:30、P2009:49）——否则 066/068/069 三卡 completion 由通过转失败，且 066 cpv 已现网失败；③ 回归样本集沿用 81 整改①（须含 P5006/P5008 近危＋068/069 注释洗白），并追加 **066 块**（本节证明它是唯一「现网已暴露」的注释假报错样本，只测 077/068/069 会漏掉「自身子块带注释且为块内末 name」这一形态）；④ 方法论入 72 谱系：**「整改写法的等价性不能靠推理，须在涉案样本全集上逐格跑判定矩阵；翻转必须逐条归因到数据行，并区分『更严（语义回归）』与『更松（漏检）』方向」**——本次 4 翻转全为更严方向且全部命中已知口径冲突，才使「A/B 可接受」成立。

**下一节候选域（第廿一次查重预告）**：(a) **全库现网判定账**：对 130 EVD×3 name 用带 cap 的 BASE 原式做全量直测（进程内批跑，非逐 name 起进程），出「validator 当前语义下的 PASS/ERROR 清单」并与各卡自述 completion／evidenceLevel 对撞——**须出对撞差异卡级清单方算增量**（预期差异集＝注释假报错 ∪ FAILED 卡 ∪ 挂死卡的未暴露面；仅复述 79/80/81 计数即判废）。(b) **整改落地包（纸面）**：B＋cap 的目标代码片段 × 6 行注释整改 × P2009/P5007 复跑预期出什么错（evGov 族错误而非挂死，80 整改③的兑现）——**须给卡级最小动作清单＋预期错误文案方算增量**；纯复述本节结论即判废。(c) null×世代轴、completion 整改模拟账：均按 81 条款**永久除名**，不再进入查重占位。

→ 本项与 ①—82 一并不在本轮 AI 自决实施范围内。

### 83. 全库现网判定账（82 候选 (a) 落槌：130 EVD×3 name＝**390 格全量直测** BASE 287/98/5 复现 81/82 全部挂死；对撞差异卡级清单闭合——注释假报错 6 行→4 翻转＋1 已暴露＋P2009 潜在，洗白面 C2×2＋GATE-FAIL-BUT-topPASS×1；**S2＝B 的 183 假报错机理归因零残差：177 格纯 CRLF 锚技术缺陷（`$` 在 .NET 多行下不匹配 `\r` 前）＋6 格真注释翻转，Bfix（`[ ]*\r?$`）合成复现给出修复方向；82 顺勘两处——S1 4→6（82 样本盲区）、A≡B 仅在全 LF 样本下成立（82 的 12 块源文件实测 CR＝0/12，现网 87/130 文件含 CRLF，「B 优先」推荐须推翻）——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿二次）**：承 82 候选域 (a)，其自设条款「**须出对撞差异卡级清单方算增量**」——本节交付：① 全库 390 格带 10s cap 的 BASE 原式进程内批跑（gov22，非逐 name 起进程），主账 287 MATCH／98 NOMATCH／5 TIMEOUT，5 挂死格＝P2009×3＋P5007 spec/task，与 81/82 实测账**逐格吻合**（全库复现闭合 82 已核验①的「N4≤11 间接排除」为直接测量）；② 卡级对撞账（gov22b：EVD 自述×现网判定×引用卡三方）——C1 自述 PASS 但现网≠MATCH **3 格**、C2 自述非 PASS 但现网 MATCH **2 格**、C5 卡级门失败但 top=PASS **1 卡**（EVD-P2006-001）、C6 UNREFERENCED EVD **7 文件**、C7-C9 值谱分布；③ 三写法全库账（gov22c：BASE/A/B 同格并跑，5 项 GUARD 自检模板唯一来源）——A 282/108/0、B 105/285/0，S1（BASE=MATCH→A=NOMATCH）＝**6 格**、S2（BASE=MATCH→B=NOMATCH）＝**183 格**、S3/S4（更松方向洗白）＝**0**、S5 挂死 5 格 A/B 全快退且 **P5007 spec A/B＝MATCH@0ms 正判救回**；④ S2 全量机理归因（gov22g 块级直方图 HEALS-UNDER-LF=177／FUEL-LT4=128／FUEL-BLANK=96／UNCLASSIFIED=**0**；gov22h 合成最小复现 CRLF 块 B=False、Bfix=True；gov22i/j 块文件与源文件 CR 字节审计）——**183 格无一残余全部归因**，技术面与语义面彻底分离。候选 (a) 落槌成立为 83；候选 (b)「整改落地包（纸面）」顺延（受本节选型推翻牵动，见收口条款）；候选 (c) 维持永久除名。

**权威锚点**：判定原式＝validate-task-card.ps1 [L709-712](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tools/validate-task-card.ps1#L709-L712)（L709-710 模式、L711 `-notmatch` 触发、L712 报错文案「Evidence governance check is not PASS ($validationName)」，80/82 已钉，本节沿用其写法定义与实验装置）；数据面＝`docs/ai-spec/tasks/evidence/EVD-*.yaml` 全 130 文件；关键实据：[EVD-P0B-ENG-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0B-ENG-001.yaml) 与 [EVD-P0B-V01-V12.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P0B-V01-V12.yaml)（C2 洗白双卡：specValidation 子块无自证 PASS、靠兄弟 name 裸 PASS 借锚，BASE=MATCH 而自述 null，且**两卡均 UNREFERENCED**——双重孤儿）、[EVD-P2006-001.yaml](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/evidence/EVD-P2006-001.yaml)（C5：top=PASS 但 task/changedPath 两 name 现网 NOMATCH，被 TASK-P2-006 引用即吃 L712）。产物 gov22/gov22b/gov22c/gov22g/gov22h/gov22i/gov22j 输出全在系统临时目录，不入库；DEC-009：全程只读 validator 与数据，无卡面/产品码/契约改动。

**取证方法（五步＋一段自纠史）**：第一步 gov22 主账——130 文件×3 name 进程内批跑 BASE（10s cap，超时计 TIMEOUT），得 287/98/5。第二步 gov22b 对撞账——按 EVD 文件名反查 tasks 全目录引用集，三方对撞出 C1-C9 九类差异清单。第三步 gov22c 三写法账——同格并跑 BASE/A/B，模板串唯一来源构造＋5 项 GUARD 自检（首版曾因列错位产出假账，修后 BASE 列与 gov22 主账逐格复现一致方采信）。第四步归因（自纠史）：先试全文件级归因（gov22f）——首跑被未捕获的 RegexMatchTimeoutException 崩（`ErrorActionPreference='Stop'` 下超时异常即中止全脚本），加 try/catch 重跑后最坏 390 格×3 写法×10s 的量级 >15 分钟无输出，击杀弃用；**教训固化：归因必须在 gov22c 同款「先切 governance 块」的块级作用域上做**（块级切分把搜索空间从全文件收束到病灶区，gov22g 约 2 分钟跑完且 S2=183 与 gov22c 逐格吻合）。第五步 gov22h 合成最小复现（七行标准块，LF/CRLF 双形态×四写法）＋`$` 语法学探针＋gov22i/j CR 字节审计（块文件 12/12 CR=0、源文件 12/12 CR=0）。

**总账发现**：
- **现网 BASE 账**：390 格＝287 MATCH／98 NOMATCH／5 TIMEOUT。挂死 5 格与 81 的 39 name 直测、82 的 48 样本 cap 账三方逐格吻合——81 的 N4≤11 间接排除自此升格为**全库直接测量**：挂死在全库层面就是且仅是 P2009×3＋P5007×2，无第三卡。
- **对撞差异卡级清单（82 候选 (a) 主交付）**：① C1 注释假报错面 3 格（066 cpv 现网即 NOMATCH 且卡自述 EXPECTED_FAIL、P2009 spec 挂死但自述 PASS、P5007 spec 挂死但自述 PASS）；② C2 洗白面 2 格（P0B-ENG-001／P0B-V01-V12 specValidation：validator 看 PASS、卡自述 null——BASE 借兄弟裸 PASS 洗白，**且两卡 UNREFERENCED**，属「洗白孤儿卡」新形态）；③ C5 EVD-P2006-001：top=PASS、exec=PASS，但两 name 现网 NOMATCH，被 TASK-P2-006 引用——**该卡今天跑 `-CheckCompletion` 必吃 2 条 L712**，是 82「已暴露」账（066 cpv 1 行）之外新增的卡级现网失败；④ C6 UNREFERENCED 7 文件（含 C2 双卡）＝引用面治理账，超出 validator 判定但入整改回归集；⑤ 6 行注释（81 确账）与三写法账对榫：4 行触发 S1 翻转（066/068/069 spec＋069 task）、1 行已暴露（066:25，BASE=NOMATCH 故不入 S1）、1 行潜在（P2009:49，挂死未及暴露）。
- **三写法全库账**：A 282/108/0——S1＝6 格（4 注释翻转＋2 洗白卡）、S3＝0、挂死 5→0 且 P5007 spec 由 TIMEOUT 变 **MATCH@0ms 正判**（80 整改③「evGov 族错误而非挂死」的兑现自此有全库账：A 把 5 挂死格全数归零为 NOMATCH×4＋MATCH×1）；B 105/285/0——S2＝183 格、S4＝0。**A 相对 BASE 的 6 格收紧全部有语义解释（注释借锚 4＋兄弟借锚 2），无一技术残差**；B 的 183 格中 177 格无技术解释即炸——**B 在全库层面出局，直至修复锚段**。
- **S2 机理归因（本节省级增量，零残差闭合）**：gov22g 直方图（非互斥标签）HEALS-UNDER-LF=**177**、FUEL-LT4=128（燃料含 2 缩进兄弟 name header 行，如 `[  taskValidation:<CR>]`)、FUEL-BLANK=96、FUEL-TAB=0、ANCHOR-CRLF=0、UNCLASSIFIED=**0**；文件级 130 中 **87 含 CRLF**、43 纯 LF，且 **6 个 LF 文件的 S2 格恰好＝S1 六格**——即 B 的炸格＝「CRLF 技术面 177」⊎「真注释/借锚语义面 6」。根因实锤（gov22h）：B 锚段 `[ ]*PASS[ ]*$` 中 `[ ]*` 吃不掉行尾 `\r`，而 .NET 多行模式 `$` 仅在 `\n` 前匹配——**一切 CRLF 文件的 PASS 行锚必失配**；Bfix（`[ ]*\r?$`）在合成 CRLF 块上 True。归因过程的 ANCHOR-CRLF 假设（查 BASE 匹配尾后字符是否 CR）恒 0 被自证伪：BASE 的 `\s*$` 先吞 `\r`，匹配值末字符恒为 `\n`——**探针须打失败写法的锚，不能打成功写法的尾**（方法论入收口条款）。
- **82 顺勘二条**：① 82 L3339「A/B 相对 BASE 的翻转全部是 4 处」——全库为 **6 处**（+P0B-ENG-001/P0B-V01-V12 specValidation 兄弟借锚洗白格）；非 82 误判而是**样本盲区**（82 的 16 块未含此二卡，且 82 时点尚无 C2 对撞装置）。② 82 L3338「B 与 A 逐格全等（48/48）」与 L3341「推荐 B 优先于 A」——**在被推翻意义上成立**：gov22i/j 实测 82 的 12 个块文件与其 12 个源文件**全部 CR=0**（样本恰好全取自 LF 文件群，含 watch 群 P5006/P5007/P5008/P2009/068-076），而现网 87/130 文件含 CRLF；B 的 `$`-vs-`\r` 缺陷（82 偏差②所列「制表符/零空格形态不同」是另一维度，未预见 CR）在全 LF 样本上被系统性掩盖，A≡B 只是**样本行尾同质的产物**。82 的「结构唯一解析」定性论证仍成立，但其选型结论必须修正（见整改待批①）。

**已核验**：① BASE 列在 gov22（独立批跑）与 gov22c（三写法并跑）两份产物中 390 格逐格一致，且 5 TIMEOUT 与 81 直测／82 cap 账三方吻合；② S2=183 在 gov22c（并跑差分）与 gov22g（块级归因重跑）中逐格一致，归因直方图 UNCLASSIFIED=0（177+6 恰闭合 183，FUEL-* 为形态细分标签不参与加法）；③ gov22h 合成复现与 `$` 探针同向（LF B=True／CRLF B=False／Bfix 双 True；`(?m)PASS[ ]*$` 对 `"PASS`r`n"`=False、对 `"PASS`n"`=True）——机理与全库账互证；④ CR 字节审计走 ReadAllBytes 逐字节计数（gov22i 块 12/12、gov22j 源 12/12，脚本末行 `__XIT__` 正常退出）；⑤ gov22c 的 5 项 GUARD（base≠B、三写法签名、运行时互异）全 OK 后方采信其账（上段列错位假账的防线）。

**偏差与 unproven**：① 归因作用域为块级（governance 全块），非子块级——BASE=MATCH 的格其匹配路径必在块内，故块级分类可靠，但 FUEL-LT4/FUEL-BLANK 细分对具体格的贡献未逐格拆（HEALS-UNDER-LF 是唯一决定性标签）；② 5 超时格在 gov22g 中并入 NOMATCH 口径，与 gov22c 主账的 TIMEOUT 分类有 5 格口径差（S2 差分不含它们，无碍结论）；③ Bfix 只在七行合成块上验证，未在 87 个现网 CRLF 文件的真块上全量复跑（预期与 HEALS-UNDER-LF=177 同构，**直接复跑 unproven**）；④ 现网文件行尾形态与 git checkout 的 autocrlf 配置相关——本节账成立于工作区磁盘现状（87/130 CRLF），若整改落地环境强制 LF checkout，则 B 的技术面 177 不显现但**不可依赖**（现网 validator 的调用环境即本工作区形态）；⑤ 全部结论仅 PS5.1/.NET Framework 引擎（80 偏差⑤、81 偏差④、82 偏差④原样适用）；⑥ 82 当时块文件生成是否另有 LF 化旁路未深查（gov20.ps1 L77-79 读写均原样，旁路无证据——按「样本恰全 LF」最简解释采信）；⑦ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改）**：① **推翻 82 整改待批①的选型**：B（未修复形态）在全库炸 177 格纯技术假报错，**出局**；可落地形态二选一——**A＋cap**（6 收紧格全有语义解释、须与整改②捆绑）或 **Bfix＋cap**（`[ ]*\r?$`，兼得结构唯一解析，但须先做偏差③的全量复跑）；82 L3341 的「B 优先」自本节起作废。② 强制捆绑条款升级：整改①落地（A 或 Bfix）除 82 所列 6 行注释存量外，还须同批处置 **C2 双孤儿卡（P0B-ENG-001/P0B-V01-V12 specValidation 无自证 PASS——A/Bfix 落地即转 L712 报错）与 C5 EVD-P2006-001（两 name 补自证 PASS 或改正自述）**，否则「已暴露」清单从 82 的 1 例扩为 3 例（066 cpv＋P2006 两 name）。③ 回归样本集追加 **CRLF 维度**：81/82 样本全 LF 的教训——任何写法等价性测试必须含 CRLF 形态样本（或先对样本集跑 CR 字节审计并声明形态构成）；存量 87 个 CRLF 文件全部入整改后回归集。④ **方法论入 72 谱系**：「判定矩阵的样本集必须先审计行尾字节谱；等价性结论只在样本形态构成内有效」＋「归因/批测脚本必须在病灶作用域（块级）上跑，全文件级扫描在 cap×格数 的量级下不可行；超时异常在 Stop 模式下必须 try/catch 记 None，否则崩全脚本」。
**下一节候选域（第廿三次查重预告）**：(a) **整改落地包（纸面，第四顺延）**：选型修正后形态（A＋cap 或 Bfix＋cap）的目标代码片段 × 存量数据整改卡级清单（6 行注释∪C2×2∪C5×1∪C6 引用治理）× 预期错误文案与回归样本集（含 CRLF 维度）——**须出卡级最小动作清单方算增量**；查重条款与 82 (b) 同源，若第廿三次查重仍不立项，按 79/80/81 三度未落先例**永久除名，纸面推演类候选自此全部清零**。(b) **CRLF 全库形态账**：130 EVD 的行尾形态×波次×卡世代交叉谱（87/43 的来源归因——checkout 还是提交即混），为整改③的回归集提供分层——**须出交叉表方算增量**。(c) null×世代轴：永久除名维持，不再占位。

→ 本项与 ①—83 一并不在本轮 AI 自决实施范围内。

### 84. CRLF 全库形态账（83 候选 (b) 落槌：130 EVD 行尾形态的 **git 索引×工作区×磁盘三层全量直测＋世代×形态交叉表**——index 侧 i/lf **130/130 全一色纯 LF**（`git show HEAD:` blob 无 CR 实锤），磁盘侧 86 纯 CRLF＋41 纯 LF＋1 行内混排（EVD-P1013-001）＋2 未跟踪 LF，三重闭合 130；`git ls-files --eol` 的 w 标记与磁盘逐字节 CR 审计**逐文件零失配**；**「提交即混」假设证伪——仓库层零 CRLF，工作区形态＝autocrlf checkout 转换（86+1）∥ 就地 LF 写入幸存（43）两条创建路径的分层**；世代相关性极强：无 dash P0BR0xx 32／P3xxx 10／P4xxx 10 全 CRLF，而 dash 变体 P0B-\* 2、P0BR-001..042 5 全 LF，82 的 12 样本 12/12 落 LF 群体自此有结构化解释——**83 偏差④「若强制 LF checkout」的前提被 .gitattributes 实测推翻（4 条 eol=lf 规则不覆盖 docs/ai-spec，check-attr text/eol 双 unspecified，autocrlf=true 的新 clone 会把 43 个 LF 文件也变 CRLF，B 缺陷面在新环境趋向 130/130 而非归零）——2026-10-13，取证基准 HEAD `a0227961`**）

**立项查重（第廿三次）**：承 83 候选域，其 (b) 自设条款「**须出交叉表方算增量**」——本节交付桶×形态交叉表、W-form×形态一致性表、43 个 LF 文件全名单与 MIXED 孤例明细，且顺带产出 83 未有的三层证据（index/工作区标记/磁盘字节）与 .gitattributes 覆盖面实测，(b) 落槌成立为 84；候选 (a)「整改落地包（纸面）」第四顺延未立，按 83 收口条款**永久除名，纸面推演类候选自此全部清零**；候选 (c) 维持除名，不再占位。

**权威锚点**：git 侧语义＝`git ls-files --eol` 的 i/w 双列（i 列为归一化后 index blob 形态、w 列为工作区检出形态）＋实测 `core.autocrlf=true`；[.gitattributes](file:///d:/workspace/opengeobot/HarnessDG/.gitattributes) 全文仅 4 条 `text eol=lf`（`deploy/compose/**`、`deploy/security/**`、`scripts/**`、`**/mvnw`），其头部注释自证「core.autocrlf=true 会让这些文件在 Windows 工作树检出为 CRLF…随之解析失败」——**autocrlf 之害在本仓库是已被作者点名处置过的已知问题，处置范围止步 deploy/scripts，docs/ai-spec 不在其列**；`git check-attr text eol` 对 EVD-GOV007-001（w/crlf 代表）与 EVD-P0BR-068-001（w/lf 代表）四行全 unspecified。对照锚＝83 节偏差④（其条件句前提在本节证伪）与 83 整改③（回归集 CRLF 维度的分层数据即本节交叉表）。产物 gov23/gov23b 输出全在系统临时目录，不入库；DEC-009：全程只读，无卡面/产品码/契约改动。

**取证方法（双侧对账＋列位自纠）**：gov23.ps1 六段——① git 侧 ls-files --eol 全解析；② 磁盘侧对 130 个 yaml 逐文件 ReadAllBytes 计 CR/LF/CRLF 行/纯 LF 行（沿用 83 gov22i/j 的逐字节法）；③ 双向集合差分（磁盘含 CR 的 87 ↔ git w/crlf 86＋w/mixed 1）；④ 未跟踪文件单列（077/078，均 CR=0）；⑤ 82 的 12 样本 W-form 复核；⑥ Get-Bucket 按卡名世代分桶出 ROW 明细（GOV／P0B-\*／P0BR0xx 无 dash／P0BR-001..042／P0BR-043..999／P1..P5）。随后 gov23b.ps1 对 ROW 重聚合出交叉表——**自纠史**：首版以内联 `-Command` 解析 ROW 定长列，取错列（`$p[3]` 实为 i 列）致交叉表全空假账，弃内联改落盘脚本、按表头核列位后重算，并以 W-form×形态独立一致性表交叉验证后方采信。git 侧另补三条验证命令：check-attr、`git show HEAD:` 首三行字节（GOV007 index 无 CR 而磁盘有 CR）、.gitattributes 全文 Read。

**总账发现**：
- **三层形态账**：index 层 **i/lf 130/130 全一色**（含 2 未跟踪文件的 blob 形态无 i 标记，不计入 128 跟踪账）——仓库层**没有任何一个 blob 混入 CR**；工作区标记层 w/crlf=86、w/lf=41、w/mixed=1（跟踪 128）；磁盘字节层含 CR=87（86 纯 CRLF＋1 行内混排）、纯 LF=43（41 跟踪＋2 未跟踪）；**43＋86＋1＝130 三重闭合，w 标记与磁盘 CR 逐文件零失配**。
- **世代×形态交叉表（本节省级增量，(b) 条款主交付）**：GOV 1/1 CRLF；无 dash **P0BR0xx 32/32 CRLF**；**P1xxx 18＝17 CRLF＋1 MIXED；P2xxx 13＝12 CRLF＋1 LF（仅 P2009）；P3xxx 10/10、P4xxx 10/10 CRLF**；dash 侧 **P0B-\* 2/2、P0BR-001..042 5/5 全 LF**，P0BR-043..999 31＝**30 LF（含 2 未跟踪 077/078）＋1 CRLF 异类（EVD-P0BR-050）**；P5xxx 8＝5 LF（P5002/04/06/07/08）＋3 CRLF（P5001/03/05）。LF 侧加总 2＋5＋30＋1（P2009）＋5＝**43**、CRLF 侧加总 1＋32＋17＋1（P0BR-050）＋12＋10＋10＋3＝**86**、MIXED 1（P1013），43＋86＋1＝**130** 与头账 DISK-WITH-CR=87（86 CRLF＋1 MIXED）／DISK-LF-PURE=43 复算一致。
- **82/83 悬念的结构化解释**：82 样本 12/12 落 LF 群体（w/lf 12/12 实锤）不是随机巧合——80/81/82 处置期反复重写的高危卡群（068-076 watch 群、P5006/07/08、P2009）**恰全部属于 LF 侧世代**；时间线推演：CRLF 世代＝早期 autocrlf checkout 产物且此后未再改写，LF 世代＝后期工具就地 LF 写入（git 归一化后与 index blob 等长，故 w/lf 显示 clean 而磁盘保持 LF）。
- **两个孤例**：① EVD-P0BR-050——LF 世代桶内唯一 CRLF，即「世代分层」并非绝对，单文件改写即可跨层；② EVD-P1013-001——全库唯一 git 与磁盘同判 MIXED 的文件（部分行 CRLF＋部分行纯 LF 的行内混排），形态自洽、成因未查。
- **.gitattributes 覆盖面账**：4 条 eol=lf 规则全部不及 docs/ai-spec；validator 现网判定所依赖的工作区形态（83 的 87/130 CRLF 基线）**完全由本机 git 配置决定**——同一仓库在不同 autocrlf 设置的机器上会给出两套 83 账，形态维度上现网判定**不可跨环境复现**。
- **「提交即混」证伪**：83 立 84 候选时预留的归因分叉（checkout 还是提交即混）落槌前者——index 全 LF 使「混」只存在于工作区表现层，且分两条生成路径（86＋1 checkout 转换 ∥ 43 就地写入幸存）。

**已核验**：① index 层全 LF 有三重证据：ls-files i/lf 128/128＋`git show HEAD:` GOV007 blob 首三行无 CR 而磁盘同文件有 CR＋check-attr unspecified（排除属性强制归一）；② w 标记与磁盘 CR 双向集合差分空集×2（87↔86+1 逐文件对榫，未跟踪 2 文件单列后总账仍闭合 130）；③ 交叉表由 ROW 全明细重聚合（gov23b），桶计数加总＝43 LF＋86 CRLF＋1 MIXED＝130 与 DISK-WITH-CR／DISK-LF-PURE 头账复算一致；④ 82 样本 12/12 w/lf 与 83 gov22i/j 的 CR=0 实测互证（git 侧标记与磁盘字节同向）；⑤ CR 计数走 ReadAllBytes 逐字节法（与 83 已核验④同装置）。

**偏差与 unproven**：① ROW 明细表 LFONLYLN 列因格式化占位错位实印 CR 字节数（判定列 `CR=<shape>` 用变量正确、行级 MIXED 检测独立可靠；异类候选仅 P0BR-050 一行存在标签歧义，已逐行复核其与 w/crlf 一致性，采信）；② w/lf 41 个文件的「工具就地 LF 写入」生成路径是形态自洽＋时间线推演，**未做逐文件确证**（git log --diff-filter=A／写盘痕迹未查，unproven）；③ 「autocrlf=true 且无属性覆盖的新 clone 会把 43 个 LF 文件变 CRLF、B 缺陷面趋 130/130」是标准 git 语义推演，**未做干净 clone 实测**（unproven）；④ 结论仅 PS5.1/.NET Framework 引擎适用（80 偏差⑤／81 偏差④／82 偏差④／83 偏差⑤ 原样沿用）；⑤ 2 个未跟踪文件（077/078）的形态账按磁盘现状记入，其未来 checkout/提交后形态不受本节账覆盖；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改，须人类裁决）**：① **形态统一策略选型**：候选一＝.gitattributes 扩 `docs/ai-spec/** text eol=lf`（validator 判定形态跨环境稳定；落地后 83 的 177 技术面在新检出环境不显现，但 **83 全账的形态基线即告失效，整改回归须按新形态重跑全库判定账**）；候选二＝反向统一 CRLF（不可取，B/Bfix 之争外又扩 43 文件整改面）；候选三＝不做属性统一、强制整改选型「形态免疫」（A 或 Bfix＋cap 本就对 CR 不敏感，配合 83 整改③把双形态并入回归集）。② 83 整改③ 的回归集分层**直接改用本节世代交叉表**：87 个 CRLF 文件非同质群体（含 P0BR-050 异类与 P1013 混排孤例），回归样本须按桶配额抽样并含两孤例。③ 若选候选一，须同步声明 83 的 S2=177 账改挂「本机 autocrlf=true 工作区」环境标签（83 偏差④ 由条件句改写成文）。

**下一节候选域（第廿四次查重预告）**：纸面推演类候选已按 83 条款全部除名，本节收口即**主动封账**：(a) **干净 clone 形态实测**：在临时目录做 `git clone`（保留 autocrlf=true 与 false 两组），实测 43 个 LF 文件检出后形态，把偏差③ 从推演升为直接测量——**须出双环境逐文件对比账方算增量**，属环境级取证动作（非纸面）。(b) **EVD-P0BR-050 与 EVD-P1013-001 双孤例成因账**：git log 逐文件改写史＋字节级混排分布，检验「世代分层」的抗例边界——**须出逐提交形态翻转账方算增量**。(c) 其余方向：无先例支撑不预告；若第廿四次查重两候选均不成立，本节为**本谱系最后一个纯形态学候选**，其后候选域须从产品面（非 validator 判定面）重新开题。

→ 本项与 ①—84 一并不在本轮 AI 自决实施范围内。

### 85. 干净 clone 双环境形态实测（84 候选 (a) 落槌：同一 HEAD `a0227961` 做 `file://` 本地双 clone（autocrlf=true/false 各一），128 跟踪 EVD×2 全量逐文件 ReadAllBytes——true 侧 **128/128 全 CRLF**（工作区 41 个 LF＋1 个 MIXED 悉数转换）、false 侧 **128/128 全 LF**，TRANS 矩阵零例外、EXC 双空集；**84 偏差③ 由推演升格为直接测量**（「B 缺陷面在新环境趋 130/130」修正为**跟踪全集 128/128**，未跟踪 077/078 不在 clone 之内）；blob 层三孤例直测 CR=0（P1013 4163B/96 LF、P0BR-050 12934B/238 LF、P5007 9278B/189 LF）＋false clone 全集反证——**「世代分层」机制钉死为工作区改写史而非提交内容：P0BR-050 异类降格为「未被改写波及的 checkout 原态幸存」、P1013 行内混排系工作区编辑路径产物且 `git status` clean（归一化掩盖面自 w/lf 扩至 MIXED，比 84 记载更宽）——2026-10-13，取证基准 HEAD `a0227961`**）

**立项查重（第廿四次）**：承 84 候选域，(a) 自设条款「**须出双环境逐文件对比账方算增量**」——本节交付 TRANS-T/TRANS-F 双向转移矩阵（逐文件 worktree 形态→cloneT/cloneF 形态）、T/F 两侧全量形态分布、异常文件零例外清单与 blob 层抽查，(a) 落槌成立为 85。(b)「双孤例成因账」**部分被本节顺带闭合**（blob 层证据直接回答「是否提交即混」的孤例半问；剩余的「哪个工具哪批改写」属编辑史归因，增量收窄，见候选域）；84 (c) 条款「候选域须从产品面重新开题」自本节候选域起生效执行。

**权威锚点**：被验对象＝84 偏差③（原句「未做干净 clone 实测（unproven）」）与 84 整改待批①候选一（.gitattributes 扩 eol=lf 的预期效果面）；git 语义锚＝`core.autocrlf` 的 checkout 转换与 add/clean 归一化（w/lf 与 MIXED 文件 status clean 的机制解释）；数据锚＝84 已核验①的 index 层 i/lf 128/128（本节将其单文件 `git show` 证据扩为**全集双 clone 反证**）。产物 gov24/gov24b 输出与两份临时 clone 均在系统临时目录、用毕已删（EXISTS=False 实锤），不入库；DEC-009：全程只读产品库，clone 为独立临时目录、零触碰工作区。

**取证方法（四步＋一次自纠）**：第一步双 clone——`git clone -c core.autocrlf=true|false file:///…`，两侧 HEAD 均回验为 `a0227961…`、CFG 回验 true/false 生效、FILES=128×2。第二步逐字节扫描——沿用 83/84 的 ReadAllBytes 法对两侧全量计 CR/纯 LF 行，得 T-DIST CRLF=128、F-DIST LF=128。第三步转移矩阵——以 84 gov23 ROW 行重建 worktree 形态映射（130 条、按表头核列位，规避 84 自纠的同款列位坑），逐文件输出 worktree→cloneT/cloneF 转移计数与「非预期形态」异常清单（双侧均空）。第四步 blob 抽查——`git cat-file blob HEAD:` 重定向落盘后逐字节，钉三孤例的仓库内容形态；**自纠史**：首版 cmd 重定向行漏 `HEAD:` 前缀致三连 fatal＋空文件（bytes=0 自信号暴露），修正后重跑成功——教训沿用 84「跨进程内联拼装不可靠，落盘脚本＋表头核列」谱系。

**总账发现**：
- **84 偏差③ 升格为实账**：autocrlf=true 的新 clone 把跟踪全集 128 个 EVD（含工作区本已 LF 的 41 个与 MIXED 的 1 个）一律检出为 CRLF——**B 型 `$`-锚缺陷在任何 true-clone 环境对全库发作**，84「趋向 130/130」的正确口径是「跟踪集 128/128，磁盘 130 中的未跟踪 2 卡不入 clone」。autocrlf=false 侧全 LF，等价于 84 整改待批①候选一的预期效果面（候选一落地收益自此有实测锚，非纸面推演）。
- **TRANS 矩阵（(a) 条款主交付）**：worktree CRLF→cloneT CRLF 86、worktree LF→cloneT CRLF 41、worktree MIXED→cloneT CRLF 1；对 cloneF 三桶全数→LF 86/41/1。**零例外、零分叉**——工作区形态与 clone 形态之间不存在任何「内容决定」的残留差异，检出形态 100% 由环境配置决定。
- **世代分层的机制改判（本节对 83/84 谱系的定性增量）**：blob 层全 LF（false-clone 全集反证＋三文件直测）⇒ 84 交叉表测的**不是提交世代，而是工作区改写史**——「dash 命名世代全 LF」实为「80-82 处置期被就地 LF 改写波及的文件群」。P0BR-050「LF 桶内 CRLF 异类」由此不再是世代抗例：它只是同代际中**未被改写波及时**的 checkout 原态幸存（其 blob 直测 CR=0/238 LF 与兄弟卡无殊）；命名世代与形态的相关性是处置波波及面的投影，不是 Git 内容差异。
- **P1013 混排定性**：blob 纯 LF、工作区行内混排、`git status` clean 三事实并存 ⇒ 混排是工作区编辑路径产物（CRLF 检出文件被局部 LF 改写，或部分行经 checkout、部分行经就地写）；具体编辑事件未归因（unproven②）。**掩盖面修正**：84 说 w/lf 显示 clean 因归一化等长——本节点出归一化对 MIXED 同样掩盖（gov24c 实测工作区＝**95 行 CRLF＋1 行纯 LF**（4258B），归一后恰等于 blob 的 96 行全 LF（4163B），差值 95 字节＝CR 数），**`git status` 对该目录的形态漂移整体失明**，形态审计只能逐字节，不能信 porcelain。
- **整改分类的净增量**：至此「卡内容治理」（82/83 的 6 行注释、C2/C5 借锚——真内容债）与「形态治理」（84/85 的 EOL——纯环境债，仓库内容零债务）彻底二分；84/85 全部形态账**不需要动任何一张卡的 blob**，整改面收窄为环境/属性层。

**已核验**：① 双侧 clone HEAD 与工作区取证基准三方一致（a0227961…全哈希，clone 前后回验）；② T/F 分布各 128 无缺文件，TRANS 桶加总 86＋41＋1＝128 与 84 w 标记账逐桶吻合；③ EXC 双空集（true 侧无一文件非 CRLF、false 侧无一文件非 LF）——无 .gitattributes 规则波及 EVD 目录的反证同 84 check-attr 互证；④ blob 三文件 CR=0 直测（4163/12934/9278 字节量级与 84 ROW 行印数一致，84 偏差①「LFONLYLN 列实印 CR 字节数」的列位结论在此旁证）；⑤ 临时 clone 目录测后删除（EXISTS=False），产品工作区 43 项脏文件清单前后未因本节取证变动；⑥ gov24b 首跑失败以 bytes=0 自信号捕获并修正重跑，未产生假账流入。

**偏差与 unproven**：① 双 clone 为本机 git 版本＋`file://` 本地传输下的行为，跨 git 版本/网络传输/`--filters` 组合未测（标准语义下不应有差，**未证**）；② P1013 混排的具体编辑事件（哪个工具、哪一批改写）未归因，仅钉「blob LF＋工作区 MIXED＋status clean」三事实；③ 未跟踪 2 卡（077/078）add 后再 checkout 的往返形态按 TRANS 机制推演为 true-clone CRLF，**未实测**；④ 仓库是否混用 LFS 未专项审计（.gitattributes 四行无 lfs 规则、EVD 目录 check-attr 无 filter 命中，倾向无涉，未穷尽）；⑤ 结论仅 PS5.1/.NET Framework 引擎适用（80⑤/81④/82④/83⑤/84④ 谱系沿用）；⑥ 无批准人处置，全为待批项。

**整改待批（AI 不自批不自改，须人类裁决）**：① 83 全库判定账（S2=177 等）与 84 形态账**统一加挂环境身份证**：`HEAD a0227961 + 本机 autocrlf=true 工作区 + 逐文件 CR 字节谱`——本节实锤任何非本工作区环境的形态账必不同（true-clone 全 CRLF/false-clone 全 LF），现网判定账**离开此环境即失效**，回归集须按此三元组钉环境后重跑。② 84 整改待批①候选一（`docs/ai-spec/** text eol=lf`）的收益面自此有实测锚：效果＝本节点 false-clone 侧（128/128 LF），validator 判定形态跨环境恒定；**决策权在人类**——AI 不代拟 .gitattributes 提交。③ 82/83 的卡内容整改（注释行、借锚卡）**与形态整改正式解绑**：任何「先统一行尾再谈选型」的捆绑顺序论证作废，选型（A/Bfix＋cap）可独立推进。④ 若候选一落地，83/84/85 三节形态账全部按新环境重跑并在各节追加改判记录。

**下一节候选域（第廿五次查重预告）**：执行 84 (c) 条款，候选域自产品面重新开题：(a) **implementation-status 声明×EVD 账对账**：`implementation-status-2026-07-10.md` §11 的 V01–V28 全 PASS 声明×对应 EVD 卡（P0B-V\* 族）自述/引用面逐条映射——**须出声明↔证据逐条映射表方算增量**（83 C2 已证 P0B-V01-V12 为「洗白孤儿卡」，其所在声明面的账未展开）。(b) **UNREFERENCED 7 卡逐卡处置面账**：83 C6 已数账、未逐卡展开「引用缺失原因×应然引用位」的 Grep 实证账——**须出逐卡证据链方算增量**（数据/文档取证动作，非已除名的纸面推演类）。(c) **EOL 形态谱系自本节起封账**：孤例剩余成因（unproven②）属编辑史考古，增量面不足不再占位；若第廿五次两候选均不成立，候选域须回到 goal 主线（docs↔产品差异的大账）重新开题，validator 判定面谱系整体收口。

→ 本项与 ①—85 一并不在本轮 AI 自决实施范围内。

### 86. §11 声明 × V\* 族 EVD 逐条映射（85 候选 (a) 落槌：`implementation-status-2026-07-10.md` §11「V01–V28 共 38 项 PASS」声明面对应 EVD 卡逐条对账——**V\* 族卡全库仅 `EVD-P0B-V01-V12` 一张（Glob 实测）**，其 18 条 coverage.proven 逐条映射 verify.sh 实存 **37 个 step**（L1051–1366 直测）后钉出结构性缺口：**V12–V28 全 17 步在 V\* 族卡面零 assertion**（`assertion: V1[2-9]|V2[0-8]` 全 evidence 目录 Grep 零命中）、「AGENTS.md P0-B VERIFIED」的 E4 声明实际由一张 `taskId: null`/`commitSha: null`/`accepted: false` 且覆盖域停在 V01–V12 语义域的孤儿卡间接承托；卡面 AUTH-003 行与 notProven L144 自相矛盾；同文档 V21/V28/37/38 四种口径并存——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿五次）**：承 85 候选域，(a) 自设条款「须出声明↔证据逐条映射表方算增量」——先例核对：plan 内 ⑮-C（L485–492）只账了**断言项数** 37 vs 38 的文档口径矛盾，P-10（L2764）只点开 V25 单点，均非「§11 声明面 × EVD 卡面」的逐条映射；本节交付完整映射表加总账，(a) 落槌成立为 86。(b)「UNREFERENCED 7 卡逐卡处置面账」查重确认增量仍成立（83 C6 仅数账、逐卡「应然引用位」Grep 实证未做）但本轮不双开，顺延至候选域。(c) EOL 谱系封账条款执行——本节及候选域均不含 EOL 项。

**权威锚点**：声明侧＝`implementation-status-2026-07-10.md` L303（「V01–V28 共 38 项 PASS / 0 FAIL / 0 SKIP / 0 警告」）、L304（V05–V05j IAM 全链 PASS）、L306（V12 实打实断言）、L12（状态表「P0-B VERIFIED；Compose **V01–V21** PASS」）、L299（复验环境「Flyway 40 迁移成功、最新 V41」）；证据侧＝`EVD-P0B-V01-V12.yaml` 全文 188 行（coverage.proven 18 条、notProven 11 条、review L184–188）＋交叉引用的 `EVD-P0BR-058-001.yaml`（status PASS L17、E4 L95、accepted false L450）＋`deploy/compose/scripts/verify.sh` L1051–1366 的 37 处 `step` 调用直测。DEC-009：全程只读，脚本/卡/文档零改动。

**取证方法**：四步——① Glob 全库 `**/EVD-*V*` 钉 V\* 族卡全集（命中仅 `EVD-P0B-V01-V12.yaml` 一张，`EVD-GOV007-001.yaml` 系 GOV 前缀误扫不含 V 族语义）；② Read 卡全文＋Grep 该卡 `scenarioId`/`assertion` 各 18 命中核对条数；③ Grep evidence 全目录 `assertion: V1[2-9]|V2[0-8]` 零命中＋verify.sh `^\s*step .*V[0-9]` 37 行全清单直测，两侧建映射；④ 交叉引用面 Grep `EVD-P0B-V01-V12` 全 docs（29 处命中：plan 12 处＋卡互引＋TASK-P0BR-053/058 修订指令）。

**总账发现（逐条映射表）**：
- **卡面 18 条 proven → verify.sh 37 步的映射**：V01×1（ENG-001）、V04×1（ENG-004）、V05×2（IAM-002/010）、V05c–V05j×9（IAM-001/003/004/005/006/007/008/009/011/012 中 008/009 合两行、全数交叉引用 EVD-P0BR-058）、V08×2（AUD-001/002）、V11×1（OBS-003）、**AUTH-003 一行覆盖 V06/V07/V09/V10/V11 五步**；V02/V03/V05b 三步在卡面无任何 assertion 提及。合计触及 22/37 步。
- **结构性缺口（本节主发现）**：**V12–V28 的 17 个 step（资产-Gitea 一致性、幂等重放、多组织隔离、Facet、版本/发布 Schema、Inbox、MCP、对账、告警、Outbox、五个默认拒绝面、降权）在 V\* 族卡面零直接映射**——evidence 目录 `assertion: V1[2-9]|V2[0-8]` 零命中实锤；该域的 E4 声明目前只由 P0BR-070/074/075/076 等卡的**间接自述**（「V01–V28 全 PASS」文字）承托，而其中 070/075 等卡本身在 83 普查的越级/待批账内。**「P0-B VERIFIED」声明的证据链末端是一张名字停在 V01-V12、覆盖域语义也停在 V01–V12 的卡**。
- **卡面自相矛盾行**：proven 的 AUTH-003 行断言「V06/V07/V09/V10/V11 无 Token 401、**越权 403**」，而同一卡 notProven L144 写「V05–V11 **仅验证 fail-closed 拒绝路径，未验证有权限 200 完整路径**」——现势 verify.sh 的 V06–V11 step 名已改标「默认拒绝 + 有权限正向可达/正向断言」（L1080–1098），说明卡冻结于 2026-07-02 快照后 P0BR 波次改了脚本、卡面两区未同步且无时点注记（对照 §14 对 P2006/P5001 有回写、对 V\* 族零回写零注记）。
- **环境账过期**：卡 L66「V04 Flyway **V1-V22**」vs §11 L299「40 迁移、最新 **V41**」vs ⑮-A「工作树实际含未提交 V44」——三层 schema 口径并存，且 V04 判据本身已被 ⑮-B 钉为无判别力（只数 success 记录 ≥33、不验 head 版本）。
- **口径四账并存**：L12「V01–V21」（状态表）、L303「V01–V28 共 38 项」（§11）、verify.sh 实存 37 步（⑮-C 已账 37vs38）、V\* 卡覆盖至 V12——第四种口径（**EVD 卡面覆盖域**）系 ⑮-C 未及的新增维度；另注意编号空间碰撞：verify-journey 的 V06/V07（DVC/Webhook 旅程，§12/13）与 verify.sh 的 V06/V07（权限过滤/字典，平台冒烟）同名异义，靠 `docs/runbooks/verify-case-mapping.md`（G5）人工对照。
- **治理字段全数失效**：该卡 `taskId: null`、`commitSha: null`、specValidation/changedPathValidation/completionValidation 三命令全 null、`review.accepted: false`——73 谱「幽灵卡」与 83 谱「可判定面」结论在此卡的声明承托地位上放大：**平台最高级别声明（VERIFIED）所绑的证据是治理字段最空的一张卡**。

**已核验**：① V\* 族卡全集＝1 张（Glob 全库模式实测，非目录抽样）；② 卡 proven/notProven 条数（18/11）与 Grep 命中一致；③ verify.sh 37 步全清单含行号，与 ⑮-C 的 5+1+6+8+17 分桶互证；④ 交叉引用面 29 处命中逐条过眼（TASK-P0BR-053 L126/L280、058 L63/L131/L204/L295 为授权修订指令文——即 058 波次曾按卡面条款**只**改 IAM 相关条目，V12+ 缺口自始不在其授权范围内）；⑤ 058 卡 accepted:false 直读（交叉引用的权威源自身亦未获人类验收）。

**偏差与 unproven**：① 本轮为**静态映射取证**，未复跑 verify.sh/verify-journey（复跑需全栈容器环境；37 步的「实际断言强度」仅按 step 名与 ⑮/76/77 谱既有账推断，未逐步审计——unproven，V25 弱断言已由 P-10 点名、其余步骤无同型专项账）；② P0BR-070/075 自述的「V01–V28 全 PASS」日志（build-logs/、.trae/）均在 Git 忽略路径，⑮-D 同病，映射表的「间接承托」面无法从受控存储复核；③ V02/V03/V05b 三步在卡面无映射系卡面文本事实，其断言是否由其他 P0BR 卡覆盖未逐卡反查（属候选 (b) 邻域）；④ 结论受 85 环境身份证约束（HEAD `a0227961`＋未提交 43 项工作树）；⑤ PS5.1 引擎谱系沿用；⑥ 无批准人处置。

**整改待批（AI 不自批不自改，须人类裁决）**：① V12–V28 域的 17 步**声明有、卡面无**缺口：要么为其建立逐条/分域 EVD 卡并绑定当前 HEAD 的真实 E4 复跑，要么在 §11 与 AGENTS.md 口径中把「V01–V28 PASS」的承托证据改指 P0BR-070/075 并处理其越级账——**AI 不代拟卡**；② V\* 卡的 AUTH-003/notProven 矛盾行与「V1-V22」环境账：按「历史快照不回写」原则，处置只能是追加时点注记或新卡覆盖，方向请裁定；③ L12「V01–V21」vs L303「V01–V28/38 项」vs 实测 37 步的口径统一（⑮-C 已有裁定请求，本节补充第四口径后并案处理）；④ 幽灵卡治理（taskId/commitSha/accepted 三空）与「VERIFIED 顶层声明」的绑定关系属验收 authority 议题（承 73/83/85 ①—④ 谱）。

**下一节候选域（第廿六次查重预告）**：(a) **UNREFERENCED 7 卡逐卡处置面账**（第廿五次顺延）：对 83 C6 名单（gov22b.out.txt L22-28 全量：EVD-P0B-ENG-001、EVD-P0B-V01-V12、EVD-P0BR-014-001、EVD-P0BR-017-001、EVD-P0BR-023-001、EVD-P0BR-024-001、EVD-P0BR-041-001）逐卡反查 tasks/requirements/runbooks 全目录，出「引用缺失原因×应然引用位」逐卡证据链——**须出逐卡账方算增量**。(b) **V02/V03/V05b 三步的卡面覆盖反查**（本节偏差③收窄项）：若 (a) 成立则并账处理，不单开。(c) **若第廿六次两候选均不成立，validator/EVD 判定面谱系整体收口，回到 goal 主线（docs↔产品差异大账）重新开题**——84 (c)、85 (c) 两度顺延后，此为硬性收口条款。

→ 本项与 ①—86 一并不在本轮 AI 自决实施范围内。

### 87. UNREFERENCED 7 卡逐卡处置面账（第廿六次查重落槌：(a) 成立——7 卡按「无宿主结构性孤儿×2／代际错位孤儿×5」两型收口，5 张撞名卡的新代 hyphen 卡全部未绑、TASK 卡绑定的仍是旧代无连字符 PASS 卡；063 自述回写 hyphen 023 卡而绑定面从未换轨，构成「复验账与绑定面脱钩」主发现；(b) 并账闭合 86 偏差③：V02/V03/V05b 三步在 evidence 全目录 Grep 零 assertion 命中；C6 判定口径经 gov22 源码复核（仅解析 evidenceManifests 块）全部成立、无误判，但「引用形态谱」新增「修订对象引用」近miss 形——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿六次）**：(a) 全 plan 检索无「7 卡逐卡反查处置面」先例——76 代 L3177-3179（E1/E2/E3 桶账）对的是 EVD 越界引用与 taskId:null 桶，非「缺失原因×应然引用位」逐卡账；P-4/P-7（L2786/L2789）仅 ENG-001 单点双卡账。(b) 系 86 偏差③明示的顺延项。**两候选均成立，(b) 并入 87 收口。**

**逐卡账（引用缺失原因 × 应然引用位，全部 file:line 实测）**：
- **型一·无宿主结构性孤儿（2 卡）**：① EVD-P0B-ENG-001——`taskId: null`（卡 L6 自注「无独立 Task Card」）、`commitSha: null`（L28）、status PASS（L3）；docs 全目录除 plan 与本身外零引用（Grep 13 命中文件逐一归属核验）。应然引用位**不存在**——须先立 TASK 卡才谈绑定（承 P-4/P-7 双卡互斥账）。② EVD-P0B-V01-V12——同 `taskId: null`/`commitSha: null` 病（86 已账）；但**非零引用**：[TASK-P0BR-058.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-058.md#L63) L63 与 TASK-P0BR-053.md L126/L280 在 allowedPaths 区（058 L58–L65）/正文引用它——语义是「**修订对象**」（058 L204/L295 只授权改 IAM 条目），不是证据绑定。gov22.ps1 L61-71 仅解析 front-matter `evidenceManifests` 块、L65 取 leaf 文件名、L93 判 UNREFERENCED——口径复核结论：**C6 七卡判定全部成立，058/063 的近miss引用不翻案**，但「UNREFERENCED ≠ 无人读写」的形态谱须登记。
- **型二·代际错位孤儿（5 卡）**：EVD-P0BR-014/017/023/024/041-001（连字符新代）——对应 [TASK-P0BR-014.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-014.md#L54-L55) L54-55、017 L56-57、023 L45-46、024 L44-45、041 L48-49 的 `evidenceManifests` **全部绑无连字符旧代**（EVD-P0BR014-001 等，Glob 实证两代并存：旧代 32 张 000A–046 连号、新代 hyphen 014/017/023/024/041＋047–078）。TASK 五卡全 READY×authorized×E4。**缺失原因＝换轨未完成**：九月整改波为同名 TASK 重制了连字符新卡，但从未回写绑定面。**两代账面对撞（本节最重发现）**：旧代 5 卡全 status PASS（同 commitSha `7dc22d44`、observedAt 2026-07-11、Ubuntu——一次快照批量签发）、且旧 014 卡 `scenarios: []` 空形（L8，validator L697 无可查）；新代 014/017/041 = PARTIAL（2026-09-17，如实带 76 代 E2 越界 AC）、新代 023/024 = PASS 但 `dirtyWorktree: true`（2026-09-20/21）。76 代 L3178 的「代际诚实度对比」现升级为**同名族内两代对比**：诚实降级后的复验结果（PARTIAL/dirty）全部落在绑定面之外的孤儿卡上，**validator 与完成声明看到的仍是旧代 PASS 卡**——「为何从未换轨」的机理账：一旦换绑，014/017/041 立即暴露 PARTIAL、023/024 立即触发 dirty 不可声明完成，五卡现存的「PASS 证据」即告失效。
- **063 自述回写矛盾（承上）**：[TASK-P0BR-063.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-063.md#L47) L47 在 allowedPaths（L36–L47 区）圈定 `EVD-P0BR-023-001.yaml`、L286 变更清单自述「撤销 notProven、回栈复验记录」——回写对象是**hyphen 新卡**（observedAt 09-20 与 063 波次吻合）；而 TASK-P0BR-023.md L45-46 绑定面纹丝未动仍是无连字符旧卡。**复验账记在新卡、效力账停在旧卡**，两卡同名族仅连字符之差而治理上互不相认。
- **(b) 并账（86 偏差③闭合）**：evidence 全目录 Grep `assertion:.*(V02|V03|V05b)` **零命中**——86「卡面未触及 15 步」中的 V02/V03/V05b 三步无任何其他卡补位，V\* 域卡面 assertion 覆盖账最终定格为 **22/37**。

**已核验**：① C6 名单逐卡 Grep 反查 7/7 跑毕（tasks *.md 层：ENG-001/014/017/024/041 零引用、V01-V12 与 hyphen 023 仅修订对象形）；② gov22/gov22b 口径回源（脚本 L61-71/L93 与 L45-46）；③ 旧代 5 卡与 TASK 绑定字段逐一 Read/Grep 实证；④ 058/063 的引用位置字段归属实读（allowedPaths 区间断言有据）；⑤ 新代 5 卡 status/taskId/commitSha/dirty 十字段合查。

**偏差**：① 纯静态账，未复跑 validator/task-card 对任一 TASK 卡做换绑模拟；② 旧代 32 卡↔新代 047–078 的全库换轨总账未展开（本节只圈 C6 七卡邻域——047+ 族绑定面正常与否未查，→88 候选 (a) 的实证对象）；③ 063-001/058-001 证据卡正文提及其他卡名的逐处语义未展开（以文件级命中+字段归属为准）；④ hyphen 023 新卡内 notProven 是否已被 063 真实撤销未逐行读卡核验（自述与 observedAt/dirty 时间线吻合为间接证）；⑤ PS5.1/bash 吞变量环境债沿用；⑥ 无批准人。

**整改待批（AI 不自批不自改，须人类裁决）**：① **五卡换绑裁定**：TASK-P0BR-014/017/023/024/041 的 evidenceManifests 是否指向连字符新代——换绑即暴露「三 PARTIAL＋二 dirty」的真实完成态，不换则两代卡并存账须至少在 AGENTS.md/status 文档注记「绑定证据为 2026-07-11 旧代快照」；② 旧代 5 卡的归档/注记/废止处置（历史快照不回写原则下只能注记或元数据标代际）；③ ENG-001 宿主卡缺失与 V01-V12 承托面（86 ①—④ 并案）；④ 063「撤销 notProven」回写的效力对象（新卡 PASS 是否意图替代旧卡成为 023 的完成证据）请裁定，AI 不代推测。

**下一节候选域（第廿七次查重预告）**：(a) **卡→EVD 反向 DANGING 绑定总账**：126 张 TASK 卡的 evidenceManifests/`stageGateEvidenceRefs` 所引 EVD 文件是否全部真实存在（83/87 只跑过 EVD→卡方向；卡→EVD 断链向从未实测，且系代际换轨的另一面——若有卡绑了不存在的路径，validator L550+ 前缀校验行为须实测）——须脚本实证方算增量。(b) **047–078 hyphen 新代族与旧代 000A–046 族的完整换轨账**：若 (a) 成立且发现更多错位，则并入 (a) 做全库代际总账；若 (a) 全净且仅 87 五卡错位，(b) 不成立。(c) **硬性收口条款（承 86）**：两候选均不成立时，validator/EVD 判定面谱系整体收口，回 goal 主线（docs↔产品差异大账）重新开题。

→ 本项与 ①—87 一并不在本轮 AI 自决实施范围内。

### 88. 卡→EVD 反向 DANGING 绑定全库总账＋validator 态别实测（第廿七次查重落槌：(a) 成立收口——修正解析器后全库 126 卡真断链仅 3 卡（079/080/081 的 DRAFT 预置位槽，validator 完成态如实报错、默认态静默，非缺陷）；stageGate 维 0 断链与 69 代一致；(b) 不成立——代际 twin 全库恰为 87 五对、零增量，047–078 新代绑定面全净；(c) 跨号绑定 0——063→023 系 allowedPaths 孤形；**本节最重增量为 validator 实测改判 87 机理账**：五张旧代绑定卡默认态即 FAIL 8–12 项（四卡报 `evidenceManifests` 不被自身 allowedPaths 覆盖）、CheckCompletion 态又以 sha 漂移/notProven/review 未受理拦截旧代 PASS 卡——旧代 PASS 卡的实际生效通道不是 validator，而是只读 `status: PASS` 字段的人账/静态账——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿七次）**：(a) 卡→EVD 正向全库断链账＋validator 态别实测无先例——69 代 L2967-2973 仅覆盖 stageGate 维默认态（且当时 bcrun 实测 0 断链），evidenceManifests 维默认态根本不查文件存在性（validator L554-573 只查 placeholder/rooted/escaping＋⊆allowedPaths 前缀，存在性检查在 L659 起 CheckCompletion 态内），该态别差异此前只做过源码推演、未跑过真卡实测；87 型二的「换绑即暴露」论断同样未经 validator 实证。(b) 系 87 候选域预告原文。**成立。**

**方法学自纠账（本节先勘己误）**：gov25 首轮 A 桶报 2502 条断链——`Get-BlockList` 正则误用 `(?ms)`＋`.*`（dotall 贪婪吞行）导致块越界，把 `requiredValidationCommands` 命令行、`allowedPaths` 目录项、`crossCuttingPlan` 的 `KEY|描述` 项全部当路径 Test-Path。gov25d 诊断脚本钉死泄漏点后，gov25e 以逐行状态机重写解析（键行开块、非 `  - ` 行收块），SCAN 自洽性核验：emEntries 829→**126**（126 卡各绑 1 张 EVD，恰等于卡数）、sgEntries 4860→**169**。两脚本交叉核验：A 桶 EVD 形断链、B 桶 twin、C 桶在修正前后**完全一致**（修正仅剔噪声、未漏真值）。**教训登记：取证脚本的解析器缺陷会把数百条假阳性混进真账，凡大计数结果必须先做量纲自洽核验再入账。**

**(a) 断链账（A1=3／A2=0）**：全库 evidenceManifests 真断链仅 [TASK-P0BR-079.md](file:///d:/workspace/opengeobot/HarnessDG/docs/ai-spec/tasks/TASK-P0BR-079.md#L92-L93)（L93）、080（L86）、081（L89）三卡，均绑 `EVD-P0BR-0NN-001.yaml` 而 evidence 目录 Glob 实证只到 078——三卡全 DRAFT×未授权×审批位占位（各卡 L4/L5/L106 实测），且 EVD 路径同时预置在各自 allowedPaths（079 L83），系「实施时生成证据」的**新代规范预置形**，plan L218/L311/L315 已有三卡立项待批账——**非完成态谎报**。validator 实测定性：079 默认态 FAIL 6 项全为 DRAFT 治理项、**不报断链**（默认态不查存在性，源码推演得实证）；CheckCompletion 态 FAIL 41 项、**明报** `Evidence Manifest does not exist: …EVD-P0BR-079-001.yaml`＋四个 AC 无完成证据——门禁位置设计正确。stageGateEvidenceRefs 维（169 项，剔 DEC-\*/命令/描述形后）纯路径项 **0 断链**，与 69 代一致。

**(b) 代际 twin 全库账（B=5，零增量）**：绑无连字符旧 EVD 且 hyphen twin 存在者**恰为 87 五对**（TASK-014/017/023/024/041→EVD-P0BR0NN-001，双代 status/observedAt/dirty 对比与 87 表逐字段吻合），全库无第六对。87 偏差②闭合：32 张旧代卡中 twin 率 5/32，其余 27 张旧代卡无新代同名卡、绑定面对应真实文件；047–078 hyphen 新代族绑定面**全净**（绑定的 hyphen 卡全部存在）——87 的「代际错位孤儿」即全库代际账的**全部**，(b) 独立成账不成立、并账收口。

**(c) 跨号绑定（C=0）**：evidenceManifests 绑定面全库无「EVD 编号≠TASK 编号」案例；87 的 063→023 仅在 allowedPaths 形（L47），C 桶零命中与其「修订对象引用」定性互证。

**validator 态别实测（本节最重增量——87 机理账改判）**：五张撞名卡逐一实跑（本轮 HEAD、未改卡）——默认态 FAIL：014=8 项、017=9 项、023=12 项、024=11 项、041=11 项；其中 **014/017/023/024 四卡各报 `evidenceManifests path must be included by allowedPaths: …EVD-P0BR0NN-001.yaml`**（旧代绑定路径根本不在自身 allowedPaths 内，Grep 实证四卡 allowedPaths 区无 evidence 目录项；041 因 allowedPaths L41 含 `docs/ai-spec` 宽前缀而免报），另见 baseCommit 短 SHA、approvedBy/approvedAt 占位、DEC-010 未在 decisions 声明、023/024/041 scenarioEvidencePlan 缺 AC 映射等历史降级项；014 CheckCompletion 态再 FAIL 19 项，含 `Evidence source commit must equal current HEAD`、`Evidence level is below Task requirement`、`Evidence still declares notProven`、`Evidence review must be accepted` 与 AC-P0B-AGT-001..005 全无完成证据。**改判**：87 说「一旦换绑立即暴露三 PARTIAL＋二 dirty、validator 与完成声明看到的仍是旧代 PASS 卡」——实测表明**validator 从未放行这五张卡**（默认态即红），旧代 PASS 卡若被用于完成声明也会被 CheckCompletion 多维拦截；让「旧代 PASS」持续生效的通道是**只读 EVD 卡面 `status: PASS` 字段的人账与静态文档账**（如 AGENTS.md 的 VERIFIED 声明谱系）。「从未换轨」的机理因此补全后半：五卡的绑定面换轨只是其**整体治理失效**（八项级默认态 FAIL）中的一格，单格换绑并不能使卡转绿。

**已核验**：① gov25/gov25e 双解析器交叉一致（A-EVD/B/C 三桶）＋gov25d 泄漏机理实锤；② 079/080/081 的 DRAFT 状态、预置形（allowedPaths＋evidenceManifests 双列）与 EVD 缺位 Glob 实证；③ 五卡默认态＋014 CheckCompletion 态 validator 真跑（EXIT=1，条目逐项落账）；④ 四卡 allowedPaths 区 Grep 实证不含 evidence 目录；⑤ stageGate 维 169 项静态全净。

**偏差**：① 080/081 未单独实跑 validator（与 079 同形，外推定性，标 unproven）；② 「人账通道」的上游文本位（哪些文档/声明实际引用了旧代 PASS 卡）未逐处回源——→ 89 候选；③ gov25e 对 stageGate 中含空格路径项一律跳过的保守口径可能漏「带空格真实路径」形（仓库惯例无空格路径，风险低）；④ 纯静态＋本地 validator 实测，未动 Compose/E4 现场；⑤ 无批准人。

**整改待批（承 87 四条，本节增量两条）**：⑤ **五卡治理修复账**：换绑裁定（87①）须与 baseCommit/approvedBy/DEC-010/scenarioEvidencePlan/Compose 命令项等默认态 FAIL 一并处置，否则换绑后卡仍不转绿——请人类裁决修复顺序与是否废止重建；⑥ 079/080/081 的批准流（plan L234 已提请批准 079/080；081 审批位仍占位）与三卡 DRAFT 期 EVD 预置位的既有规范地位请确认（本轮实测其非缺陷，无需整改，仅登记口径）。

**下一节候选域（第廿八次查重预告）**：validator/EVD 判定面谱系依 87(c) 硬收口条款**整体收口**——(a) 实测定性非缺陷、(b) 零增量并账、(c) 零命中，谱系内不再开新账。回 goal 主线重新开题：(a) **文档声明↔可实测现状差账**：AGENTS.md 与 implementation-status 的「P0-B VERIFIED／V01–V28 全 PASS」声明谱系，对照当前工作区可复跑性（`deploy/compose/scripts/verify.sh` 是否存在且在本环境可跑、P1–P5 `verify-journey.sh` 官方明账的缺位现状）——须先做第廿八次查重（86 的 V\* assertion 覆盖账与本节 88 的重叠面须切割：86/88 是卡面证据账，(a) 是声明文本↔运行时可复现账）。(b) 若 (a) 查重后仅剩复述，goal 主线以「剩余 blocker 官方清单复述＋收口报告」收束。

→ 本项与 ①—88 一并不在本轮 AI 自决实施范围内。

### 89. 文档声明↔可实测现状差账（第廿八次查重落槌：(a) 仅窄增量成立、并账收口——88 偏差②承诺的「人账通道」上游回源实锤：五张无连字符旧代 EVD 在全部治理文档（AGENTS.md/implementation-status/traceability）中**零引用**，旧代 PASS 的生效面仅是五张 TASK 卡自身绑定行；「V01–V28 全 PASS」顶层声明的直接承托是 §11 快照文本＋V\* 孤儿卡（86 已账），声明谱系与旧代 EVD 是两条不相交的账；新增两项机械实锤：声明锚点时间差（AGENTS.md L5 的 VERIFIED 声明定格于 `ed300a4`/2026-09-23，距现势 HEAD `a0227961`/2026-10-10 已 **43 个提交**未复验；implementation-status 末次更新 `62c92f0` 距 HEAD 22 个提交）＋ 37vs38 口径终局（物证 `verify-sh-e4-20260923.log` 全文 Read 实测＝37 行 `[V..] PASS`＋1 行汇总语，文档「共 38 项」系把汇总行计入项数之误读，⑮-C 的 37 步账就此定装）——2026-10-13，取证基准 HEAD `a0227961`）

**立项查重（第廿八次）**：承 88 候选域预告原文。切割核对：86 是「§11 声明面 × V\* 卡面」逐条映射账、88 是「卡→EVD 绑定面＋validator 态别」账，(a) 承诺的增量是「声明文本↔运行时可复现」——实测后其主体成立前提被证伪一半：本环境 Docker daemon 未运行（`docker ps` 实测连不上 `dockerDesktopLinuxEngine` 命名管道），「复跑 verify.sh 验现势」在当前环境**物理不可行**，可复现性账只剩时间线/引用链/物证文本三个静态维度；其中引用链回源与时间差实测为 86/88 未及的机械增量，声明↔复跑的完整运行时账则超出本轮 AI 可判定面。(b)「仅剩复述则收束」条款同时生效。**(a) 以窄增量成立、按预告并入收束。**

**(a1) 人账通道回源（88 偏差②闭合）**：Grep 全 docs＋AGENTS.md——`EVD-P0BR0(14|17|23|24|41)-001`（无连字符旧代）命中面恰为两类：五张 TASK 卡自身 evidenceManifests 绑定行（014 L55/017 L57/023 L46/024 L45/041 L49）＋ plan 自身取证账（60/62/86/87/88 谱）；`p0-platform-foundation-traceability.md`、两份 implementation-status、AGENTS.md 对五张旧代 EVD **零直接引用**（卡号 TASK-P0BR-0NN 亦未在 traceability 中出现）。**改判收窄**：88 说「让旧代 PASS 持续生效的通道是人账/静态文档账」——实测表明该通道并不经由文档点名旧代 EVD，而是仅经由「TASK 卡绑定行→卡面 validator 态静默→人工读卡时默认绑定即权威」这一条最短链；文档层的「全 PASS」声明谱系（86 主账）与旧代 EVD 谱系**互不相交**，五卡问题的严重面因此从「污染了顶层声明」修正为「五卡自身完成态长期不可判定且默认态 validator 已红、无人处置」。

**(a2) 声明锚点时间差**：`git log` 实测——AGENTS.md 末次提交 `ed300a4`（2026-09-23 11:07，L5 「re-run 2026-09-23… V01-V28 all PASS」定格时点）、implementation-status 末次提交 `62c92f0`（2026-09-24 09:14）、现势 HEAD `a0227961`（2026-10-10 14:03，rev-list 计数 43/22）；其间 070/074/075/076/077/078 等实现波次（含 §4.6 的 2026-10-10 真实栈复跑 37 项 PASS）均未回写声明文档。§4.6 复跑是**未提交工作树**上的结果（077/078 未提交，plan L242 自账），任何人 `git clean -fd` 后声明所绑的 2026-09-23 时点物证（`.trae/evidence/`，Git 忽略路径，⑮-D 已账）之外再无现势复跑物证可取。**声明为真与否的最强现势证据停留在 43 个提交之前。**

**(a3) 37vs38 口径终局**：物证日志全文 38 行＝37 条 `[V..] … PASS`＋末尾汇总语「验收未发现失败项…」（L39）；`verify.sh` 37 处 step 调用（⑮-C/86 双账互证）。`implementation-status` L303「共 38 项」与 test-report L36「38 断言行」均系**行数误读**（把汇总行计为断言项），plan §4.6 L246「全 37 项」为正确口径。历史快照不回写原则下两处文档数字仍不动，但 ⑮-C 的裁定请求至此有了机械定装依据——更正方向唯一（38→37）。

**已核验**：① 五张旧代 EVD 的文档引用面 Grep 全集（docs＋AGENTS.md 双跑零命中）；② `git log -1` 三文件锚点提交与时间戳、`git rev-list --count` 两组距离；③ 物证日志 38 行全文直读（37 PASS＋1 汇总）；④ 本环境 Docker daemon 不可达实测（构成 (a) 运行时维度不可判定的环境证据）；⑤ AGENTS.md L7 blockers 清单（`verify-journey.sh` 业务旅程、`-CheckCompletion`、E5 复验）与 implementation-status §13（journey 18 PASS/0 SKIP/0 FAIL、后两项仍开放）的口径关系：L7 把 journey 整体列为剩余 blocker 与 §13「六条核心旅程转真实断言 PASS、P1–P5 整体不得 VERIFIED」间存在**粒度落差**但无事实矛盾（平台旅程≠P1–P5 业务 AC 全覆盖），不立新账。

**偏差与 unproven**：① 「复跑验现势」因 Docker 未运行整体 unproven——非 AI 弃权而是环境事实，恢复窗口在用户侧；② `.trae/evidence/` 其余 40+ 物证文件（077/078 波次的 job-e4/publish-e4/verify-064 等）未逐一与对应 EVD 卡 sha 交叉核验（86 偏差②同病）；③ 声明锚点距 HEAD 的 43 提交中是否含**实质改动 V\* 断言面**的产品码未逐提交审（070–078 波次自述多为加法域，风险中等）；④ AGENTS.md L7 的 journey 粒度判读依赖 §13 文本自洽，无独立运行证据；⑤ 无批准人。

**整改待批（承前谱，本节增量一条）**：⑦ **现势 E4 复验窗口**：声明锚点（`ed300a4`）距 HEAD 43 提交期间无任何受控物证复跑（§4.6 复跑在未提交工作树＋Git 忽略路径），请人类裁定是否安排一次 Docker 窗口重跑 `verify.sh`＋`verify-journey.sh` 并以新物证回写 AGENTS.md/implementation-status 时点——AI 不擅自提交 `.trae/` 内容、不自批（承 ⑮-D、86①、§4.5 提请谱）。

**收束声明（goal 主线）**：至此，validator/EVD 判定面（83/86/87/88）与文档声明↔现状面（86/88/89）两条谱系的**全部可静态取证增量已尽**——89 的 (a2)(a3) 均为闭合既有开放账（88 偏差②、⑮-C）而非新缺陷，(a1) 反而将 88 的重判收窄。剩余全部推进项均已进入人类裁决域：整改待批池（86 四条＋87 四条＋88 ⑤⑥＋本节 ⑦）＋ AGENTS.md 明账三 blocker（journey 业务面、`-CheckCompletion`、E5 演练）＋ DEC-009 禁止 AI 自批的契约/卡/文档处置。按 88(b) 预告条款，goal 主线（docs↔产品差异迭代实施）在 AI 可自决范围内**收口**：不再开新取证节，后续每轮推进以待批池的人类处置为前置。

→ 本项与 ①—89 一并不在本轮 AI 自决实施范围内。

### 90. E4 复验窗口实际执行（89⑦ 物证回账：环境前提由「不可行」翻转为「已跑且双脚本全绿」；声明文档回写通道仍属人类）——2026-10-14，取证基准 HEAD `b593270`

**触发与性质**：goal 续轮复核现势：`docker info` 实测 daemon 不可达（`dockerDesktopLinuxEngine` npipe 报错，与 89(a) 环境账同态）→ 经 `Start-Process` 拉起 Docker Desktop 后 60s 内就绪（Server 28.7.0）。89 收束条款约束的是**静态差集取证**不再开新节；本节不是新取证谱系，而是 89 整改待批 ⑦ 所请「安排一次 Docker 窗口重跑 `verify.sh`＋`verify-journey.sh`」的**环境窗口执行记录**——窗口由 AI 实际打开并产出物证，而 ⑦ 的后半处置（以新物证回写 AGENTS.md/implementation-status 声明时点）按 ⑦ 原文与 ⑮-D 自缚条款**仍留人类裁定**，本轮未触碰两份声明文档。

**(a1) compose 静态校验**：`docker compose config --quiet` EXIT=0；服务面 6（control-plane/postgres/gitea/minio/backend/web/worker）。

**(a2) 全栈拉起**：本地镜像齐备无外拉（backend:local、control-plane:local、web:local、gitea/gitea:1.24.7、minio RELEASE.2025-09-07、postgres:16.14-alpine）；`compose up -d` EXIT=0；`compose ps` 6/6 healthy；网关 `http://localhost:8080/actuator/health` curl 实测 200。

**(a3) verify.sh 双轮＋exit 实锤**：run1（`build-logs/verify-sh-20261014.log`）与 run2（`verify-sh-20261014-rerun.log`）均 **37 断言 PASS、0 FAIL、0 SKIP**，尾行「验收未发现失败项」；两轮经 PowerShell tee/重定向包装器的 exit=1 经对照实验（Git Bash 单引号同管道复现 exit=0，`${PIPESTATUS}` 在双引号下被 PowerShell 先行展开）判定为**包装器伪影而非被测脚本失败**；第三轮裸跑（stdout→/dev/null，`echo $?` 落盘 `build-logs/verify-exit.txt`）实锤 **VERIFY_EXIT=0**。37 口径与 89(a3) 定装一致（38 行=37 断言＋1 汇总）。
**(a4) verify-journey.sh**：`build-logs/verify-journey-20261014.log`——**18 PASS / 0 SKIP / 0 FAIL，JOURNEY_EXIT=0**（六条核心业务旅程 PRD-Journey-{AssetLifecycle|DatasetPublish|McpInvoke|PreviewGrant|ReviewWorkflow|Subscription}＋授权 fail-closed/ACL/版本不可变/PAT/MCP 发布阻断/版本转移/P2 波次/范围收紧/幂等并发/不可变撤回/P4 Skill/ACL 撤销传播/P5 ops/PRD-V28 AI 写默认拒绝）。AGENTS.md L7「journey 未验」blocker 的**运行物证面就此具备**；但 §13 粒度口径维持——六旅程通过≠P1–P5 业务 AC 全覆盖，「P1–P5 不得 VERIFIED」表述不因本节翻转。
**(a5) E5 演练面机械事实**：`verify-e5-drills.sh` 与 `**/*e5*drill*` Glob **双零命中**——AGENTS.md L7「E5 drill re-verification」的隐含预设（演练资产存在待复验）不成立，该 blocker 当前不可执行的原因是**演练脚本从未落地**，等待的是「创建＋批准」而非「执行」。此为环境事实记录，创建本身属新 Task 域、AI 不自批。

**已核验**：① daemon 就绪、config EXIT=0、6/6 healthy、网关 200 四层环境链；② 双轮日志逐行 Grep 计数（PASS=37、FAIL/SKIP 零命中）＋第三轮 exit 文件实锤；③ journey 日志 18/0/0＋EXIT=0；④ E5 脚本缺失的两种 Glob 交叉；⑤ HEAD `b593270` 工作树干净锚点。

**偏差与 unproven**：① 物证落 `build-logs/`（Git 忽略路径），未入受控 `.trae/evidence/` 通道——受控归档格式与是否承托声明回写留人类定夺；② 43 提交间隔中 V\* 断言面是否被实质改动仍未逐提交审，本节全绿证明「现势 HEAD 的 compose＋journey 面可复现」，不自动构成 AGENTS.md L5 文本回写授权；③ playwright e2e 全量与 `verify-web-login-gate.sh` 未在窗口内执行（非 ⑦ 请款面）；④ 无批准人。

**整改待批状态更新**：⑦ **物证采集子项已执行**（本节 (a1)—(a4)），剩余处置＝人类裁定是否据此回写 AGENTS.md/implementation-status 时点；新增 **⑧ E5 演练脚本从未存在**（(a5)），「E5 复验」blocker 的正确表述应为「E5 演练资产待创建＋批准＋执行」。

**收束条款维护**：89 的「不再开新取证节」对静态差集谱系继续有效；后续每轮推进仍以待批池（86①—④、87①—④、88⑤⑥、89⑦剩余面＋本节⑧）的人类处置为前置。

→ 本项与 ①—89 一并不在本轮 AI 自决实施范围内（本节仅执行 ⑦ 已请款的复跑物证，未新增实施面）。

### 91. 更正 §90(a5)：E5 演练资产实际存在且已在窗口内复验三个（⑧ 撤回；restore 演练留人类确认）——2026-10-14，取证基准 HEAD `24cf305`

**更正对象**：§90 (a5)「`verify-e5-drills.sh` 与 `**/*e5*drill*` Glob 双零命中——演练脚本从未落地」及整改待批新增项 ⑧。**该结论错误**，成因是命名谱系误设：演练脚本实名 `p5-*-drill.sh`（非 `*e5*drill*`），两个 Glob 零命中只证伪了模式、未证伪存在。`git ls-tree -r --name-only HEAD` 命中四件且 `--diff-filter=D` 历史零删除：`deploy/observability/p5-ops-fault-drill.sh`、`deploy/security/p5-security-drill.sh`、`deploy/compose/scripts/p5-restore-drill.sh`、`deploy/compose/scripts/p5-preview/preview-pressure-drill.sh`；`docs/runbooks/ops.md` L83-87 另有 E5 演练记录表（`44bbc67`，PASS=18 FAIL=0 SKIP=3，EVD-P5006-001 承托）在 §90 立项前已可 Grep 触及。**⑧ 就此撤回**；(a5) 原文按「历史快照不回写」原则不动，以本节为权威更正。

**(a1) 演练复验执行物证（E4 窗口内，栈 running(7) 全程未 down）**：
- **security**：`build-logs/e5-security-drill-20261014.log`（＋r2 复跑）——**PASS=6 FAIL=0 SKIP=1，exit=0 落盘实锤**（`e5-security-exit.txt`＝0）。SKIP 项＝gitleaks/semgrep 缺位（本机未装，脚本自设 redaction-canary 替代且 PASS）；live 注入断言（2xx＋JSON-only＋存储回读）在真实栈通过。首轮包装器报 exit=1 与 §90(a3) 同型：裸 exit 落盘复测＝0，定性伪影。
- **ops 故障**：`build-logs/e5-ops-drill-20261014.log`——**PASS=19 FAIL=0 SKIP=3，exit=0**（`e5-ops-exit.txt`＝0），与 ops.md 记录谱系一致（3 个 SKIP 均为该文档 L93-95 已登记的结构性 SKIP：live 停库自指、Prometheus 401、host 级 backup 链）。**18→19 差异机械归因**：`62c92f0`（晚于记录锚点 `44bbc67`，`merge-base --is-ancestor` 实锤）给脚本新增 ephemeral-jwt fail-closed 断言（本次日志 `[PASS] ephemeral-jwt fail-closed… 401`），属脚本演进非结果差异。backend 经 override 临时改 3s 告警间隔后由 trap cleanup 恢复，演练后网关复测 200。
- **preview 压力**：`build-logs/e5-preview-drill-20261014.log`——**PASS=7 FAIL=0 SKIP=0，exit=0**。场景 A（64m cgroup 隔离、主栈探测 6/6 无外溢）、场景 B（超限→3 次尝试→DEAD、无部分产物、PII 金丝雀零泄漏）、恢复断言全过；副本经 `docker run` 临时容器实现，脚本头注自证非侵入（不改 compose.yaml）。
- 三演练后栈终态：`docker compose ls`＝running(7)、`/actuator/health` 200。

**(a2) restore 演练未执行（自限，非缺位）**：`p5-restore-drill.sh` 设计上要求显式 `DRILL_CONFIRM=yes` 并执行 `docker compose down -v` **销毁全部 Volume**（L23-27/L128），且 `docs/runbooks/backup-restore.md` L7 明账「完整 E5 演练需在独立环境中执行并记录 RPO/RTO 证据」——当前窗口承载 §90 的 verify/journey 物证与共享开发卷数据，破坏性销毁超出 ⑦ 请款面与本 AI 自决边界。执行与否属人类确认项。

**(a3) blocker 实质改判**：AGENTS.md L7「E5 drill re-verification」的正确现势表述＝**演练资产存在、四个中三个（security/ops/preview）已于 2026-10-14 在 HEAD `24cf305` 谱系全绿复验（exit 均有落盘实锤），余 restore 一个待人类确认执行环境**；ops.md 演练记录表是否增补本次行、以及是否据此回写声明文档，与 ⑦ 剩余面同属人类裁定通道。

**偏差与 unproven**：① 物证同样落 `build-logs/`（Git 忽略），未入受控 `.trae/evidence/` 通道（§90 偏差①同型）；② security 演练的 live 注入在真实栈留下 `e5-security-drill` 话题及评论行（脚本设计行为，原文存储＋前端转义模型），未清理；③ ops 演练基线显示 `job_task` 仍有 57 条真实 DEAD 行（L97 既有运维发现，本次未处置——`:retry` 属人工高风险动作）；④ preview 演练依赖 `docker.m.daocloud.io` 镜像本地缓存（本次命中，无外拉）；⑤ 无批准人。

**整改待批状态更新**：⑧ **撤回**（其事实前提被 (a1) 证伪）；新增 **⑨ restore 演练执行确认＋三个已复验演练的 runbook/声明回写裁定**——与 ⑦ 剩余面（声明文档回写）一并留人类处置。

→ 本项与 ①—90 一并不在本轮 AI 自决实施范围内（本节为 ⑦ 窗口内已请款复验面的执行记录＋对 90 自错的事实更正，未新增实施面）。

### 92. E4 窗口残余物证面收口：playwright e2e 全量 126/126 全绿＋verify-schema 六断言复验（含 §90 偏差③ 的 web-login-gate 幻影更正）——2026-10-14，取证基准 HEAD `55261d1`

**触发与性质**：goal 续轮在 E4 窗口内继续核对「文档点名的验证资产 vs 现势窗口物证」差集，命中两个未账面：① CI 强制作业 `verify-schema`（`.github/workflows/ci.yml` L196/L234 挂接、`docs/runbooks/verify-case-mapping.md` L83 映射账）从未在本窗口留证；② §90 偏差③ 点名的 playwright e2e。本节为该两面执行记录＋对 §90 偏差③ 的事实更正，仍属 ⑦ 请款谱系的窗口物证，未新增实施面。

**(a1) playwright e2e 全量复验——126 tests / 25 spec 全 passed，0 failed / 0 skipped，exit=0**：
- 覆盖面：`frontend/e2e/` 全部 25 个 spec（auth、spa-deep-link、permission、publish-flow、version-management、mcp-integration、review-workflow、upload-flow、lineage、preview×2、admin×3、dataset、asset、discussion、discoverability、error-paths、agent-management、user-management、settings-form、profile、i18n、artifact-content），`baseURL=http://localhost:8080` 真实 nginx 栈口径（playwright.config.ts 头注 TASK-P0BR-073），workers=1 顺序执行，总耗时 3.1m。
- 凭据注入谱系与 CI `playwright-e2e` 作业**完全一致**（ci.yml L287-294：从与栈同源的 `deploy/compose/.env` 解析 `AIHUB_BOOTSTRAP_ADMIN_*` → `E2E_ADMIN_USERNAME/PASSWORD`，值不落日志）。机械事实：现势栈管理员口令＝`.env` 原 bootstrap 值本身（curl 探测两候选：回退后缀形态 401、原值 200），§91 谱系的 `Journey-E2E-01!` 后缀回退假设在**本栈**不适用。
- 执行物证：`build-logs/e2e-full-wrap.log`（首跑 auth.spec 单跑 4 passed 另见 `e2e-auth-wrap.log`；全量尾行「126 passed (3.1m)」）；真实退出码落盘实锤 `build-logs/e2e-exit.txt`＝0。首跑未带凭据时 auth.spec 4 skipped 为 `bootstrapCredentials()` 设计行为（support.ts L1-10），带 CI 同款环境变量后全部实跑。
- `upload-flow` 用例经 `docker cp` 向 aihub-minio-1 注入分片夹具（日志可见），属测试夹具设计行为，未动 compose 定义。

**(a2) verify-schema.sh 窗口复验——6 断言全 PASS，exit=0**：`build-logs/verify-schema-20261014.log`＋`verify-schema-exit.txt`＝0。断言面：V28（AssetSearchDao DEPRECATED 降权源码断言）＋V04（迁移完整性 ≥33＋18 关键表）＋V16（版本 Schema）＋V17（发布审批 Schema）＋V21（告警 Schema）＋V22（Outbox 事件表），尾行「Schema 验收未发现失败项」。口径维护：此为 CI 强制轻量子集，与 verify.sh 37 项、journey 18 项是不同覆盖面，互不替代。

**(a3) §90 偏差③ 更正（web-login-gate 幻影）**：偏差③ 所写 `verify-web-login-gate.sh` 经全仓 Grep 仅 plan 自身该行 1 命中——**脚本不存在**，与 (a5) 的 `verify-e5-drills.sh` 同错型（幽灵文件声明，历史快照不回写、以本节为权威更正）。Web 登录门的真实验收资产＝e2e `auth.spec.ts`（AC-P0B-UI-001 四场景：未登录重定向/登录成功/错误凭据不泄露存在性/登出清 Token）＋`spa-deep-link.spec.ts`，两者已于 (a1) 全绿。「登录门脚本未执行」的正确表述＝「登录门无独立脚本、由 e2e 覆盖且已复验全绿」。

**(a4) 新发现 docs↔项目差异（登记，不回写）**：`docs/architecture/implementation-status-2026-07-10.md` 全文对 playwright/e2e **零提及**（Grep `playwright|e2e/` 0 命中），而 CI 存在 `playwright-e2e` 强制作业、`frontend/e2e/` 存在 25 spec 资产——现势状态文档遗漏了这项 E4 UI 验收证据资产的存在。是否据此回写 implementation-status/AGENTS.md 与 ⑦ 剩余面同属人类裁定通道，本节仅登记差异事实。

**偏差与 unproven**：① 物证落 `build-logs/`（Git 忽略），未入受控 `.trae/evidence/` 通道（§90 偏差①同型）；② 126 全绿证明「现势 HEAD 的 UI 验收面可复现」，不翻转 §13 粒度口径——e2e 非 P1–P5 业务 AC 全覆盖，「P1–P5 不得 VERIFIED」维持；③ 口令探测 curl 曾向真实栈发 1 次 401 失败登录（会进 auth 失败审计流水，属正常安全事件）；④ 无批准人。

**整改待批状态更新**：§90 偏差③ 两半项就此收口——「playwright e2e 全量未执行」→ 已执行全绿；「`verify-web-login-gate.sh` 未执行」→ (a3) 幻影更正。新增 **⑩ implementation-status 缺失 playwright 证据线的回写裁定**（(a4)），并入 ⑦/⑨ 人类通道。

→ 本项与 ①—91 一并不在本轮 AI 自决实施范围内。至此，AGENTS.md L5-L7 点名的可执行复验面在本窗口全部留有物证：verify.sh（37 全绿）、journey（18 全绿）、E5 三演练（§91）、verify-schema（(a2)）、playwright e2e（(a1)）；余 blocker 全部收敛为人类裁定/人类执行项（声明回写、restore 演练、⑩、`-CheckCompletion`、E5 剩余登记）。

### 93. AGENTS.md「Validation」命令族窗口复验：前端四连＋后端 mvnw verify 干净 rc=0（含 vitest 并发超时与 testcontainers 探测异常两项环境定性）——2026-10-14，取证基准 HEAD `825b8bf`

**触发与性质**：goal 续轮清点 AGENTS.md「Validation」代码块六条命令在窗口的物证覆盖：`verify.sh`/`verify-journey` 已有（§90/91 谱系），余四条前端命令与 `./mvnw verify` 从未在本窗口留证。本节为该五面的执行记录，属 §90—92 同谱系窗口物证执行，未新增实施面。

**(a1) 前端四连（落盘包装 `build-logs/validation-front.sh`，RESULT 行脚本内展开可信）**：`pnpm lint` exit=0、`pnpm typecheck` exit=0、`pnpm build` exit=0（`tsc -b && vite build`，日志 `validation-front-20261014.log` 尾行）。`pnpm test`（`vitest run` 默认并发）exit=1：20 文件/34 用例失败——**定性为本机资源争用伪影，非产品缺陷**，定罪三步：① 失败单跑（LoginPage.test.tsx）5 passed；② 失败形态 33/34＝「Test timed out」（`failshape.txt`）；③ `vitest run --maxWorkers=2` 重跑 **50 文件/239 用例全 passed（507.39s，`vitest-lowcc.log`）**。CPU 饥饿佐证：默认并发 import 累计 5856s vs 低并发 836s（放大 ~7 倍），antd 重组件 import 可达 19.4s、吃掉默认 5s timeout 大半。谱系：`build-logs/` 中 067/069/070/072/074/078 各有 serial/低并发全绿历史日志——本机满并发超时是**反复出现且已用低并发收口的已知环境模式**；vite.config.ts 与 setup.ts 均无 timeout/并发配置（环境项非缺陷项），CI（ubuntu-latest）不受影响。

**(a2) 后端 mvnw verify——干净 rc=0 实锤（93c）**：`./mvnw -q verify`（在线、国内镜像）10:30–10:38 完整跑通，脚本内落盘退出码 **0**（`mvnw-verify-93c-exit.txt`），日志 1855 行、maven 级 `[ERROR]` 行 0（`mvnw-93c-audit.txt`）。对账：SUREFIRE tests=835 failures=0 errors=0 skipped=10（139 报告）；FAILSAFE tests=123 failures=0 errors=0 **skipped=123**（21 报告，时间戳全落 10:36–10:38 本次窗口）；fat jar（140MB）10:37:47 repackage 成功——93b 的失败点正是本条。
- **离线变体账（93b）**：`-o` 复跑 rc=1，根因＝`spring-boot:repackage` 插件侧构件（maven-shade-plugin 3.5.0、spring-core 6.2.7 等）本地仓缺失且离线不可解析（`mvnw-fail-find.txt`）——属本地仓完备性的环境事实，在线跑即消除；93b 的 surefire 账与 93c 完全一致（835/0/0/10）。
- **failsafe 全 skip 机理（新发现）**：`@Testcontainers(disabledWithoutDocker = true)` 广布 IT 类（ApplicationContextSmokeIT 头注明示设计意图「保证 ./mvnw verify 在无 Docker 网络时仍可通过」）；而 testcontainers 1.21.0 对本机 Docker Desktop 的探测返回**全零 Info JSON＋BadRequestException 400**（日志实锤），判「无有效 Docker 环境」→ 123 IT 全 skip。93c 中两种策略同征：`UnixSocketClientProviderStrategy`（`/var/run/docker-cli.sock`）与 `NpipeSocketClientProviderStrategy`（`.testcontainers.properties` 钉死，2026-08-11 生成）均 400——**与钉死策略无关，Docker Desktop 的 docker_cli 管道应答即异常**。同时现势 `docker version`＝Server 29.1.2、compose 栈 running(7)——docker CLI 正常但 testcontainers 探测失败。推论：`./mvnw verify` 的 IT 层在本机窗口执行中从未真实运行＝**CI-only 覆盖**，此为 docs↔项目差集候选（AGENTS.md/声明文档未记载 IT 的本机不可执行性）。

**(a3) PowerShell 包装器 exit 伪影谱系第三变体**：`bash -c "...; echo exit=$?"` 中 `$?` 被 PowerShell 先行展开为布尔 → `mvnw-verify-93-exit.txt` 内容＝「exit=True」（污染直证）。同族三变体现已实锤齐备：① `${PIPESTATUS[0]}` 先行展开（§90(a3)）；② 内联 bash -c 中 `'...'` 拼接 ParserError（§92 轮）；③ `$?`→True（本轮）。**标准对策唯一化：exit 判定必须落盘进 .sh 脚本内执行**（validation-front.sh／e2e-run.sh／mvnw-verify-online.sh 均验证可信）。

**偏差与 unproven**：① 物证落 `build-logs/`（Git 忽略），未入受控 `.trae/evidence/` 通道（§90 偏差①同型）；② 首跑（93）日志止于 failsafe 开局（10:06:00）且 exit 文件被 (a3) 伪影污染——首跑整体退出码不可考，以 93b（rc=1、根因钉死离线插件解析）＋93c（rc=0、完整对账）两轮替代收口；③ failsafe skip 账来自报告文件（XML 为单测计数、不记 JUnit skip 原因），「disabledWithoutDocker×探测失败」机理为日志＋注解广布的推理链，直证需 CI 对照；④ vitest 低并发全绿为本机时点账，不翻转 §13 粒度口径；⑤ testcontainers 探测异常**是否登记为运维/环境项**留人类裁定（改 strategy/删 properties/升 testcontainers 均触本机环境，不属本窗口自决面）；⑥ 无批准人。

**整改待批状态更新**：新增 **⑪ IT 层本机从未真跑（CI-only）的差集登记与 testcontainers 探测异常处置裁定**（(a2) 发现），并入 ⑦/⑨/⑩ 人类通道。

→ 本项与 ①—92 一并不在本轮 AI 自决实施范围内。至此 AGENTS.md「Validation」六条命令在本窗口全部留有直接物证：mvnw verify（(a2) rc=0）、lint/typecheck/build（(a1) exit=0）、test（(a1) 超时定性＋低并发全绿）、compose verify.sh（§90）、journey（§90）。

### 94. CI 作业族未覆盖面窗口复验：Contracts/MCP/Compose/Task Card/Security/Perf 六面——含两项新差集候选（redocly diff 幻影命令、Task Card Gate 提交形态错配）——2026-10-14，取证基准 HEAD `f3da2f3`

**触发与性质**：goal 续轮清点 `.github/workflows/ci.yml` 作业族中本窗口尚未留证的作业（Security 三扫描、Compose config、Contracts、MCP contract、Task Card Gate、Perf smoke）。本节为该六面的运行时物证执行，属 §90—93 同谱系（AGENTS.md 点名资产 vs 现势窗口物证的差集核对＋执行），未新增实施面。包装脚本与日志全部落 `build-logs/`（Git 忽略），RESULT 行由 .sh/.ps1 内部写盘（§93(a3) 标准对策沿用）。**环境前置实锤（第四种陷阱变体）**：PowerShell 裸 `bash`＝WSL bash 5.0.17（无 node/npm/npx、`/d/` 路径不存在），须显式 `C:\Program Files\Git\bin\bash.exe`（Git Bash 5.2.26，继承 Windows npm 生态、`python3`＝商店 stub、`py`＝真 Python 3.13.3＋pyyaml 6.0.2）——`build-logs/ci-bash-probe.txt` 定罪。

**(a1) Compose config ＋ Contracts**：`docker compose config --quiet`（cp .env.example .env 后）rc=0（`ci-k1-results.txt`）。OpenAPI `redocly lint`（@latest 2.63.0，`--config contracts/openapi/redocly.yaml aihub-v1.yaml`）rc=0，3 warnings（2099:17 allOf 单 schema、4623:5 TagReference、4873:5 FacetDimension unused）与 071 谱系同征＝无回归。**breaking-change diff 不可执行（新差集候选⑫）**：CI L147-162 用 `npx @redocly/cli@latest diff --fail-on-breaking`，但 @latest(2.63.0)／1.34.1／1.25.0 三版（`--version` 核实真实安装）help 均无 `diff` 子命令、调用一律 rc=1「无法识别这些选项」，官方文档 `/docs/cli/commands/diff` **404**，071 谱系 `oasdiff` 亦从未跑通（`could not determine executable`）。→ 该步在 redocly CLI 中为**幻影命令**：非-main 场景被 `|| echo` 兜底掩盖、main push/PR-to-main 场景将真实断裂。物证 `ci-k1-results.txt`／`ci-k1b-*`／`ci-k1c-help-*.txt`。

**(a2) MCP contract**：`contracts/mcp/validate-tools.sh` 原件在 Git Bash `python3`＝商店 stub 下 rc=49（环境事实，非校验结论）；以 `PYBIN=py` 参数化内嵌 Python（逻辑零改动）等效复跑 **rc=0——「tools.yaml 结构有效，共 11 个工具」**（`ci-k2c-validate-tools.log`）。定向测试 `./mvnw -o -Dtest=McpToolCatalogContractTest,McpToolCatalogTest test` **Tests run: 13, F0/E0/S0, BUILD SUCCESS（41.187s）**，「MCP Tool Catalog initialized: 11 tools registered」——与 tools.yaml 的 11 工具账一致（`ci-k2-mcp-tests.log`）。⚠️ 包装产物 `ci-k2-results.txt` 首跑仅写第一行（`set -euo pipefail` 在 validate-tools rc=49 处中断，未达 mvnw 段），mvnw 段结果以完整日志为权威并补记 `MCP_TESTS_RESULT=0(log-verified)`。

**(a3) Task Card Gate -CheckChangedPaths 结构性失效（新差集候选⑬）**：复现 CI L323-350（pwsh：base→`git diff --name-only --diff-filter=ACMR -- docs/ai-spec/tasks/TASK-*.md`→逐卡 `-CheckChangedPaths`）。主跑 base=`c1aecec^`／head=`c1aecec`（077~081 五卡同提）→ **5 卡全 CARD_FAIL（35/33/47/47/47 issues）**，其中 079/080/081 附加 DRAFT 状态门禁项（Task status must be READY、implementationAuthorized、stageGatePassed、approvedBy/At、REQ-DST-FAV-001 not READY）**属预期正确行为**；077/078 的越界项源于五卡互判。对照单卡 `e77d0d2`（仅含 071，但混提 ci.yml/后端/前端全量）→ 071 单卡仍 **FAIL 103 issues**。校验器本身健全（能正确报出越界），失效点在**本仓库历史几乎全为批量提交**与门禁「单卡 diff⊆该卡 allowedPaths」前提的**结构性错配**（`ci-k2-cardgate-results.txt`／`ci-k2b-cardgate-results.txt`）。

**(a4) Security 三扫描——均定性 CI-only 环境依赖，非缺陷发现**：① frontend `npm audit --audit-level=critical` rc=1＝`registry.npmmirror.com` **未实现 `/-/npm/v1/security/*` 端点**（`NOT_IMPLEMENTED`，`ci-k3-npm-audit.log`）——镜像源能力缺位，critical 漏洞判定不成立、也不证伪；② gitleaks 本机 absent，容器 `ghcr.io/gitleaks/gitleaks:latest`（CI L65-68 用 gitleaks-action@v2＝PR-diff 制）：全量 git 扫 276 commits→12 命中（`ci-k3c-gitleaks.json`），工作区直扫 `--no-git`→316 命中（绝大多数落 `build-logs/`·`graphify-out/` 缓存，非受控）；过滤为 **Git 受控文件后仅 11 命中／7 文件**（`ci-k3d-*`），逐条定性全为 `generic-api-key`/`jwt` 规则对测试夹具与代码形态的误报（如 `PreviewApplicationService*Test.java` 的 `key = "ab/12cd34ef567890"` 对象存储键、`ErrorCode.java` 的 `this.retryable = retryable`、`scripts/aih/aih:829` 的 `local password=""`、`SensitiveDataMaskerTest` 内嵌测试 JWT）；无一条为真实生产凭据。CI 走 diff 制不扫 untracked/历史全量，故不阻断本窗口。③ OWASP `dependency-check-maven` 离线 rc=1＝国内镜像仓（huaweicloud/aliyun）**无该插件构件**（`Plugin not found in any plugin repository`，`ci-k3-depcheck.log`），CI 在线＋NVD API key 场景不受影响。→ 三项并入 ⑪ 谱系的「CI-only 覆盖」差集登记，处置属人类通道。

**(a5) Perf smoke 借现势栈实跑——exit=0、4/4 场景 PASS**：栈 running(7)（backend/frontend/worker/minio/nginx/postgres/gitea 全 Up，四 healthy），CI L367-372 语义登录 `admin`→取 accessToken（`LOGIN=ok`，口令不落账）。`./scripts/perf-smoke.sh` 于 `deploy/compose` 工作目录（CI working-directory 对齐，脚本真实路径 `deploy/compose/scripts/perf-smoke.sh`）执行 **exit=0，PASS: 4/4**（`ci-k3-perf.log`）：asset-search 100 并发 P95=18ms（阈 500，错误 0/100）、mcp-search 20 并发 P95=15ms（阈 1000，**samples=10/20、错误率 10/20**）、presigned-url 10 并发 P95=45ms（阈 300，0/10）、multipart-upload 5 并发 P95=60ms（阈 1000，0/5）。mcp-search 的 10/20 非缺陷：脚本错误率口径＝非(200/201/401/403)（perf-smoke.sh L101），单发×6 间隔探测全 200（`ci-k3b-results.txt`），20 并发瞬时突发触发 MCP `transport.rateLimit`（tools.yaml，11 工具）返回 429 被计为错误率——**限流按设计生效**，延迟样本仍取 2xx 故 PASS 成立。

**偏差与 unproven**：① 物证落 `build-logs/`（Git 忽略），未入受控 `.trae/evidence/` 通道（§90 偏差①同型）；② redocly diff／npm audit／dependency-check 三项为「本机不可执行」环境账，不等于 CI ubuntu-latest＋在线＋NVD key 场景的结论；③ gitleaks 全量/工作区直扫与 CI 的 diff 制覆盖面不同，12 与 11 命中均为历史/全量口径，非 CI 门禁实际判定集；④ `ci-k2-results.txt` 首跑缺行属包装脚本 `set -e` 中断，以 mvnw 完整日志为权威并补记（异常已如实留痕）；⑤ Task Card Gate 全红不翻转 §93「校验器健全」定性——失效在提交形态，非门禁逻辑；⑥ 无批准人。

**整改待批状态更新**：新增两项差集候选——**⑫ CI breaking-change 步骤（redocly `diff`／oasdiff）为幻影命令、任何被探版本均不可执行**（(a1)）；**⑬ Task Card Gate `-CheckChangedPaths` 与本仓库批量提交形态结构性不兼容、对历史提交必全红**（(a3)）。⑫/⑬ 是否登记为待批整改（改 CI 步骤／调整提交形态／放宽门禁口径）均属人类裁定通道，本节仅落事实账，并入 ⑦/⑨/⑩/⑪ 人类通道。

→ 本项与 ①—93 一并不在本轮 AI 自决实施范围内。至此 CI 作业族六面在本窗口留有物证：Compose config（(a1) rc=0）、Contracts lint（(a1) rc=0）、MCP contract（(a2) 结构 rc=0＋测试 13/13）、Task Card Gate（(a3) 机制复现＋形态错配定性）、Security 三扫描（(a4) CI-only 定性）、Perf smoke（(a5) 4/4 PASS）；两项幻影/错配发现（⑫⑬）与三项 CI-only 账（⑪ 谱系）全部收敛为人类裁定项。

### 95. 治理声明断言簇核面：AGENTS.md/README 的「CI 尚未接入门禁」断言 vs ci.yml 实况——spec-gate 幻影镜像实锤（新增差集候选⑭）——2026-10-14，取证基准 HEAD `492c4f7`

**触发与性质**：§90—94 将 AGENTS.md 点名的命令面与 CI 作业族全部留证后，goal 差集的其余未覆盖面收敛为「治理文档断言簇 vs 仓库实况」核面。本节选取 AGENTS.md L47-49、docs/ai-spec/README L123-124、protocol §11、AUD-018 四处同源声明（TASK-GOV-008 门禁缺口叙述）逐条对账，全部静态取证＋一次 pwsh 实跑，物证 `build-logs/ci-k95{a,b,c}-results.txt`。

**(a1) 声明簇对账——「尚未接入」已被实况超越**：ci.yml L114-125 `spec-gate` 作业（阻断式，无 continue-on-error）实跑 `./tools/validate-spec.ps1`，由 `8a75e4d`（2026-07-05）引入；L323-350 `task-card-gate` 实跑规范位 `./docs/ai-spec/tools/validate-task-card.ps1 -CheckChangedPaths`，由 `98e6608`（2026-07-11）引入但 L356 `continue-on-error: true` 非阻断。故 AUD-018「ci.yml 未调用 validate-spec.ps1、Task 范围校验」前半已被实况推翻（两者均已调用），README L123「尚未接入这些检查」为**过期声明**；仅「完成 Evidence 校验（-CheckCompletion）未接入」与 AGENTS.md L49「non-blocking」限定仍与实况相合。

**(a2) 新差集候选⑭：CI spec-gate 幻影镜像**：CI 引用的 `tools/validate-spec.ps1`（仓库根，79 行，`8a75e4d` 后未再演化）与 AGENTS.md L33 指示的规范位 `docs/ai-spec/tools/validate-spec.ps1`（276 行）**内容漂移 325 行**——镜像仅做文件存在性＋字节数检查（6 需求文档＋3 治理文档＋openapi 文件＋state-machines），无稳定 ID、无交叉引用、无任务卡校验。本机 pwsh 实跑镜像 rc=0「AI Spec Gate: PASSED」（`ci-k95b-root-validate.log`），与 §93 规范位 validate-spec PASS（148/752）并存说明当前两口径恰好同绿，但 **CI 绿灯检验的是弱化镜像，非治理规程指定的规范校验器**——与 ⑫ 同谱系（CI 门禁引用对象≠治理文档指定对象）。

**(a3) TASK-GOV-008 无卡承继确认**：Glob `tasks/TASK-GOV-*.md` 仅 GOV-007（与 §85 P-2 登记一致，非新发现）；其「受保护 CI required checks」缺口叙述经 (a1) 修正后仍部分成立（task-card-gate 非阻断＋-CheckCompletion 未接入＋无 required check 配置证据）。

**偏差与 unproven**：① 本机无法验证 GitHub 分支保护设置（required checks 实况属仓库托管面，unproven）；② 镜像规范位「恰好同绿」是当前快照结论，不代表历史各提交同绿；③ 声明回写（README L123、AUD-018 处置、AGENTS.md L47-49 措辞）属人类通道——§93 收束线已列「声明回写」为余留人类项，本节仅落事实账不动治理文档；④ 无批准人。

**整改待批状态更新**：新增**⑭ CI spec-gate 引用弱化镜像脚本与规范校验器漂移 325 行**（(a2)），并入 ⑦/⑨/⑩/⑪/⑫/⑬ 人类通道；连带人类裁定项：README/AUD-018 过期声明的回写口径。

→ 本项与 ①—94 一并不在本轮 AI 自决实施范围内。治理声明断言簇四文件（AGENTS.md/README/protocol/AUD-018）的 TASK-GOV-008 叙述完成实况对账：两处过期（「未调用」）、一处限定存活（「非阻断」）、一项幻影镜像（⑭）。

### 96. AGENTS.md「Current phase」状态段核面：Evidence 账面 130 份机械清点——「E4 notProven」总账措辞与件级账漂移＋一件已人批证据的后手改动（新增差集候选⑮⑯）——2026-10-14，取证基准 HEAD `af5fd6b`

**触发与性质**：§95 核完门禁缺口断言簇后，本轮推进到 AGENTS.md L5-L7 状态段剩余断言：「wave exit Evidence Manifests are PARTIAL（E3 proven, E4 notProven）」「P1–P5 IMPLEMENTED_UNVERIFIED」。对 `docs/ai-spec/tasks/evidence/` 全量 130 份 EVD 做机械清点（`ci-k96a/b-results.txt`）＋已批件归属与后手改动核查（`ci-k96c/d-results.txt`）。Flyway 面附带对账：迁移目录现存 V1–V44（44 文件），implementation-status §11「最新 V41」为 09-23 日期快照口径、后续 §12–19 有续账，无矛盾。

**(a1) 件级账分布**：status＝PASS 70 / PARTIAL 36 / DRAFT 24；`execution.evidenceLevel`＝E4 51 / E3 42 / E2 28 / E5 6 / E1 5 / 复合 3。status×level 交叉中 **PASS|E4＝34 件**（清单：P0BR 系 29 件含 049–078 段＋P0B-V01-V12＋P1015/P1018/P2006/P3010）。E5 级件 6 份与 §90—91 演练资产账相容。

**(a2) 差集候选⑯——「E4 notProven」措辞与件级账漂移**：AGENTS.md L7 以偏全称「wave exit Evidence Manifests are PARTIAL (E3 proven, E4 notProven)」，但件级账自 `6d6e263`（09-23，EVD-P2006 翻转 E4 PASS/notProven 清零）起已有 34 件 PASS|E4。波次退出**聚合口径**与**证据文件粒度**已不同步——要么波次清单未随翻转回写，要么总账措辞过期。与 ⑦/⑨ 的「声明回写」同属人类通道，本节日仅落事实账。

**(a3) 差集候选⑮——已人批证据件的后手改动**：`accepted: true` 共 6 件（EVD-P1015/1016/1017/1018/P2013/P3010），全部 `reviewedBy: User (plan execution authorization 2026-07-12)`、经 `f974ad4` 入账——DEC-009「人类置位」形式要件成立。但其中 **EVD-P3010（已批件）在 `ede5c76`（09-24）被后手改动**（`commitSha: pending-commit → 178ee6b` 单行补全；改动者 git author＝xieyong，与 AI 会话共用身份、归属不可判）。补全 commitSha 属良性维护，但「人批后账被再改」这一治理形态是否需要复审置位，属人类裁定。EVD-P1018 在 `6d6e263` 亦有快照追加注记（accepted 位未动）。

**(a4) E4 物证指针的受控性复核（承 §67-D）**：implementation-status §11 权威行日志指针 `.trae/evidence/verify-sh-e4-20260923.log` 实存于磁盘（Test-Path True）但命中 `.gitignore:2`（`git check-ignore` 实锤）——受控仓库内 `git ls-files .trae/` 零文件，即**声明文档引用的 E4 完整日志从未入库**，可复核性依赖单机。§67-D 已登记，本节补机械证据链；与 §90—95 物证落 `build-logs/` 的偏差①同谱系（受控证据通道缺失）。

**偏差与 unproven**：① 「波次 exit Evidence Manifest」若另有聚合文件则本节日以 EVD 件级账代审，聚合面逐波核对未做（Grep 未发现独立波次清单文件，unproven）；② `ede5c76` 改动者是否 AI 不可判（git 身份共用）；③ 34 件 PASS|E4 各自 artifact 指针的逐一可复验性超出本节范围；④ 无批准人。

**整改待批状态更新**：新增 **⑮ 已人批证据件后手改动的复审口径**（(a3)）与 **⑯ AGENTS.md L7 波次 E4 总账措辞与件级账同步**（(a2)，并入 ⑦/⑨ 声明回写人类通道）；⑭ 之外 CI/治理引用面再得一证（(a4) 受控证据通道缺失）。

→ 本项与 ①—95 一并不在本轮 AI 自决实施范围内。AGENTS.md 状态段三大断言簇至此核毕两面（门禁缺口＝§95、证据总账＝§96）：件级账 PASS|E4 34 件、人批件 6 件、后手改动 1 件、E4 日志指针不受控 1 处。

### 97. traceability 矩阵逐项对账核面：矩阵主体断言实测成立，两处账面偏差（新增差集候选⑰⑱）——2026-10-14，取证基准 HEAD `0840b80`

- (a1) **通过面（实测成立）**：`p0-platform-foundation-traceability.md`「实现状态（2026-07-01）」表所引迁移 V1–V20 与（2026-07-10）表所引 V21–V34 全部实存（k97a[1]/k97b[1]）；表/列级断言抽核全中——V29 `asset_relation`、V28 `reconciliation_checkpoint`、V32 `owner_team_id` NOT NULL backfill（UPDATE＋SET NOT NULL 均在文内）、V33 `jti_digest`、V34 PAT 约束扩展、V26/V27 `frozen_source_commit`（k97b[2][4][5]）；测试类断言 24 项抽查 24 项实存（`TagValidationServiceTest` 位于 `taxonomy/tag/application/`，与矩阵 `**` 双星通配相符；k97a[3]/Glob 复核）；前端 `DiscussionPanel.tsx`、后端 `McpController.java`/`SystemAgentController.java`、`contracts/mcp/tools.yaml` 均实存（k97a[4]/k97b[7][8]）。
- (a2) **差集候选⑰＝矩阵 L101 表名断言与 V15 实文矛盾**：「实现状态（2026-07-10）」明细行称 `V15`：`discussion_thread`、`discussion_comment`、`discussion_subscription`——V15 实文建表为 `asset_discussion`、`asset_comment`、`asset_comment_revision`、`asset_subscription`，全库无任何名为 thread/comment/subscription 的表（`discussion_thread` 仅命中约束名 `ux_discussion_thread`，k97b[3]）。同文档 L56 行写法正确（`asset_discussion`/`asset_comment`），即**治理文档内部自相矛盾**，且 AGENTS.md Required reading 第 6 条点名该文件。引入点 `3982240`(07-05)/`46a3837`(07-10)，`76e7f97`(09-16) 同步时未纠正。
- (a3) **差集候选⑱＝受控文档的 Evidence 权威指针指向不受控文件**：矩阵 L112「Evidence 验证参见 `.trae/specs/gap-analysis-code-vs-design/gap-list.md`」——该文件本地实存（455 行）但被 `.gitignore:2` 的 `.trae/` 忽略、`git ls-files .trae/` 零文件（k97a[6]）。受 Git 治理的追踪矩阵把验收证据的落点声明为**任何 clone 都不存在**的路径，与 ⑭（幻影镜像）、§96(a4)（E4 日志不入库存指针）同谱系：声明的检验/证据对象不受版本控制。`.gitignore` 该行自 `5b5f56c`(07-02) 即存在，非近期变更。
- (a4) 轻微备注（不单列候选）：「实现状态（2026-07-30）」V04 行「Flyway V1–V34（阈值 ≥33）」与现存 V44（V37–V41 重排见 implementation-status L285）为快照滞后；`verify.sh:839` 阈值 `< 33` 为下限型断言，V44 下仍通过，无实际破坏。
- 偏差：⑰⑱ 的登记/纠正（改矩阵文本还是改 V15 注释、gap-list 是否入库）均属治理文档回写，DEC-009 人类通道；本轮仅落账。
- 收束：→ 本项与 ①—96 一并不在本轮 AI 自决实施范围内。traceability 矩阵两面核毕：迁移/测试/契约锚点主体成立（a1），账面偏差两处（⑰内部矛盾、⑱不受控指针）；连同 §95—96 构成 AGENTS.md「Required reading」与「Current phase」点名文档的全部已核面。

### 98. DEC-008 dataset-experience 需求面核面：文内「现状基线」断言逐条实测全部成立，一处命令契约缺口（新增差集候选⑲）——2026-10-14，取证基准 HEAD `b993a6b`

- (a1) **通过面（实测成立）**：REQ-DST-SUB-001「现状基线（2026-10-10 取证）」五条断言逐条核中——V15 `asset_subscription` `ux(asset_id, principal_id)` 唯一键✓；`DiscussionController` 仅 `POST/GET /api/v1/assets/{assetId}/discussions/subscription`（布尔 toggle）✓；`NotificationRecipientResolver.resolveSubscribers`（L80）被 `VersionApplicationService`（L176）在 `VERSION_PUBLISHED` fan-out 调用✓；`PhysicalRecycleJobHandler:177` 级联 `DELETE FROM asset_subscription`✓；独立订阅资源面（`PUT/DELETE /assets/{}/subscription`、`GET /subscriptions`）确实不存在——文内「缺口」自我描述与实况一致（k98a[1]）。REQ-DST-TAX-001 字典面：六类型 seed 全实存（`dataset_modality`/`dataset_format`/`license_catalog`/`sensitivity_level` 于 V6 L103-107，`dataset_task`/`dataset_language` 于 V35）；`GET /api/v1/datasets` 与 OpenAPI `/datasets` 路径实存；`matchedFields` 后端四处实现（k98a[3][7]/k98b[2][8]）。REQ-DST-DISC-001 表名面：文内 L177 用 `asset_discussion`/`asset_comment`/`asset_comment_revision`/`asset_subscription`——与 V15 实文一致（反衬 ⑰ 为 traceability 独有错误）；`asset:discuss`/`asset:moderate` 权限 seed 于 V14✓；OpenAPI 讨论动作路径 comments/retract/hide/unhide/lock/unlock/subscription 全在（k98b[6]）。REQ-DST-AIW-001 门禁面：`mcp.writeTools.enabled` 默认 false 三处一致（tools.yaml featureFlags、`McpToolCatalog` `@Value(":false")`、ConfigKeys）；`asset:publish` 在 defaultDeniedScopes；P4 MUST 四工具 `asset_create_draft`/`asset_create_upload_session`/`asset_complete_upload`/`asset_get_upload_status` 在 tools.yaml，`asset_submit_version` 不在（与「不属 P4 MUST」语义一致）（k98b[3][4][7]）。PROPOSED 两件不越权面：`asset_favorite` 无迁移、后端前端零命中——草案未被提前实施✓（k98a[2]）。
- (a2) **差集候选⑲＝`aih` CLI 命令契约 5/6：`dataset inspect` 未实现**：REQ-DST-CLI-001（READY/MUST/P2）命令契约列 search/inspect/pull/create/push/status 六命令；`cli/` 工程实存（`@aihub/cli` 0.1.0，bin=aih）且 `src/commands/` 有 dataset.{search,pull,push,create,status}.ts 五件，**inspect 全工程零命中**（k98b[1]/grep）。REQ-DST-AI-001 又依赖 inspect 语义（`asset_get_version` 检查 License/敏感等级——MCP 侧有对应工具，CLI 面缺位）。账面侧 implementation-status/traceability 均无此缺口登记；属「需求 READY 文本与实现差集未入任何账面」形态，与 ⑯ 同谱系（声明面滞后件级事实）。
- (a3) 轻微备注（不单列候选）：讨论文档契约面用 Google 风格显式动作记法（`:lock/:retract/:moderate`、`/discussions/{discussionId}` 单资源路径），实现为 RESTful 子路径（`/lock`、`/retract`、`/hide`）且无 `/discussions/{threadId}` 单独 GET 路径——语义等价、记法形状差。REQ-DST-DISC-001 审计事件面 DISCUSSION_*/COMMENT_* 于 `DiscussionApplicationService`/`ErrorCode` 命中（-l 级抽查，逐事件六项核对留后续轮）。
- 偏差：REQ-DST-FAV/SUB 两件 PROPOSED 的批准/置 READY、⑲ 的补实施或需求回改均属人类通道；本轮仅落账。
- 收束：→ 本项与 ①—97 一并不在本轮 AI 自决实施范围内。DEC-008 需求面两面核毕：文内自证「现状基线」六条全中（治理文档自我描述质量良好），差集一处（⑲ CLI inspect 缺位且无账面登记）；AGENTS.md Required reading 七文件点名面至此全部至少核过一轮（设计文档/manifest/protocol/ADR×2/traceability 见 §95-97、dataset-experience 见本轮）。

### 99. dataset-experience 稳定 ID 交叉引用闭合面核检：ID 定义面全通过，审计事件命名谱系四面漂移（新增差集候选⑳㉑）——2026-10-14，取证基准 HEAD `46215b9`

- (a0) 取证面（k99a/k99b，物证 `build-logs/ci-k99{a,b}-results.txt`，Git 忽略）：核 REQ-DST-* 引用的
  PAGE-*/AC-*/错误码/审计事件四类稳定 ID 在 catalog 与代码双向闭合。
- 通过面：PAGE-* 9/9 在 page-catalog 有定义；AC-DST-* 37/37 在 05-acceptance catalog 有定义；
  dataset-experience 引用错误码 10/10 双面对账（error-catalog ↔ ErrorCode.java）全中；
  前端路由 `/favorites` `/subscriptions` 零命中（与 PROPOSED 未实施一致）；exit-catalog 对
  FAV/SUB 行已标注「草案，待人类评审」（§3b），门禁形态正确。
- 差集面（审计事件三面命名漂移＋契约声明未发射）：需求文本（dataset-experience L167-168）六事件
  `DISCUSSION_CREATED/COMMENT_CREATED/COMMENT_REVISED/COMMENT_RETRACTED/DISCUSSION_LOCKED/DISCUSSION_MODERATED`；
  OpenAPI（aihub-v1.yaml L2227-2580）八条 `x-audit-event` 用 `COMMENT_EDITED/THREAD_LOCKED/THREAD_UNLOCKED/
  COMMENT_HIDDEN/COMMENT_UNHIDDEN`；实现（DiscussionApplicationService `auditDiscussion` 发射共 5 处：
  L96/L135/L209/L226/L258）无 `COMMENT_EDITED`/`COMMENT_UNHIDDEN`/`THREAD_UNLOCKED` 对应发射——
  `editComment`(L178-191)、`unhideComment`(L233-244)、`unlockThread`(L264-) 三写路径**保留状态变更但零审计**，
  契约已声明事件而实现未发射。TASK-P1-012 crossCuttingPlan L38 又用第三套名 `COMMENT_MODERATED`（契约面不存在）。
  `COMMENT_REVISED`/`DISCUSSION_MODERATED` 在契约与实现两面均无同名对应（近似语义分散于 EDITED/HIDDEN/UNHIDDEN）。
- 候选登记⑳（人类门禁处置，本轮不动）：讨论域审计事件需求↔契约↔实现↔TaskCard **四面命名漂移**＋
  三处契约声明的审计发射缺失（COMMENT_EDITED/COMMENT_UNHIDDEN/THREAD_UNLOCKED）。处置属对外契约/审计面改动，
  AI 不自决：或回写需求文本对齐既成三面、或补发射改契约，均需 Task Card 与人批。
- 候选登记㉑（人类门禁处置）：`EVD-P1012-001.yaml` 账面自相矛盾——同一 5 个 DISC 场景**同时**列入
  `coverage.proven`（assertion 形态、scenarioId 字符串已非规范 ID）与 `coverage.notProven`（规范 ID）；
  `evidenceLevel: E3` 低于 TaskCard `requiredEvidenceLevel: E4`；taskValidation/completionValidation 均 null。
  status=PARTIAL 尚属诚实，但 proven 列表形态不符合 evidence 模板语义（proven 应为场景 ID 级断言绑定）。
- 收束：→ 本项与 ①—99 一并不在本轮 AI 自决实施范围内。稳定 ID 交叉引用闭合面：ID 定义面全通过，
  差集集中于审计事件命名谱系（⑳）与 Evidence 账本形态（㉑）。

### 100. error-catalog ↔ ErrorCode.java ↔ OpenAPI 全量三向对账：码集与四属性 76/76 全一致，一处契约完整性缺口（新增差集候选㉒）——2026-10-14，取证基准 HEAD `cf3c658`

- (a0) 取证面（k100a/b/c，物证 `build-logs/ci-k100{a,b,c}-results.txt`，Git 忽略）：§99 仅对了 dataset-experience 引用的 10 码子集，本轮扩展为全量——catalog 全表 ↔ `ErrorCode.java` 全枚举双向集合差＋逐码四属性（HTTP status/i18nKey/retryable/alertLevel）对账＋OpenAPI `x-error-codes` 引用面闭合。
- (a1) **通过面（实测成立）**：catalog 码 76 ↔ Java 枚举 76，双向集合差**空**（k100a[3][4]，k100b 提取器首轮报 3 个 catalog-only 系多行枚举格式漏匹配，逐码 grep 复核实存：`ORGANIZATION_MEMBER_ALREADY_EXISTS` L94、`ASSET_REPOSITORY_PROVISION_FAILED` L158、`ASSET_CONCURRENT_MODIFICATION` L162 均为参数换行写法）；四属性 76/76 全一致（STATUS 唯一差异 `PREVIEW_TOO_LARGE|413|?PAYLOAD_TOO_LARGE` 为脚本映射表漏项，413=PAYLOAD_TOO_LARGE 实一致；RETRY 4 行 `**true**|true` 为 catalog 粗体标记剥离后同值）；OpenAPI 135 行 `x-error-codes` 提取 57 个 distinct 引用码，**幻影码=0**（全部 ∈ catalog，k100c[2]）；messageKey 三面一致（catalog↔枚举↔无资源文件——后端 `MessageSource`/`messages*.properties` 零命中，架构为后端透传 i18nKey、前端翻译，`ApiError.i18nKey` 字段在位，与「资源文件缺失」非矛盾而是设计形态）。
- (a2) **差集候选㉒＝19 个 catalog 码未被任何端点 `x-error-codes` 引用（契约完整性缺口）**：`ASSET_COORDINATE_INVALID`、`ASSET_REPOSITORY_PROVISION_FAILED`、`ASSET_STATE_NOT_ALLOWED`、`ASSET_VERSION_SYNTAX_INVALID`、`COMMENT_TOO_LARGE`、`DISCUSSION_LOCKED`、`DISCUSSION_NOT_FOUND`、`DVC_OBJECT_MISSING`、`GITEA_DEPENDENCY_UNAVAILABLE`、`INTERNAL_ERROR`、`JOB_RETRY_EXHAUSTED`、`MINIO_DEPENDENCY_UNAVAILABLE`、`PASSWORD_CHANGE_REQUIRED`、`PRINCIPAL_NOT_FOUND`、`PROJECT_NOT_FOUND`、`RATE_LIMIT_EXCEEDED`、`UPLOAD_SESSION_NOT_FOUND`、`UPLOAD_SIZE_EXCEEDED`、`WEBHOOK_TARGET_FORBIDDEN`（k100c[3]）。其中 `INTERNAL_ERROR`/`RATE_LIMIT_EXCEEDED`/依赖类/`ASSET_REPOSITORY_PROVISION_FAILED` 属全局兜底或中间件形态，端点级不声明尚属合理；但 `DISCUSSION_NOT_FOUND`/`COMMENT_TOO_LARGE`/`DISCUSSION_LOCKED`/`ASSET_STATE_NOT_ALLOWED` 等在对应域端点实际可返回而契约未声明，属 OpenAPI 完整性差集——implementation-status/traceability 无此登记，与 ⑳（讨论锁态抛 `ASSET_STATE_NOT_ALLOWED` 而非 `DISCUSSION_LOCKED`，L722/773 已录）同域互证。
- (a3) 轻微备注（不单列候选）：catalog 表头声明「权威源：ErrorCode.java」（L118），即文档跟代码走；⑰⑱⑳ 类问题在此文件不存在——对账全绿恰说明该 catalog 维护纪律良好，㉒ 的缺口在契约声明面而非账面对。
- 偏差：㉒ 的处置（补 x-error-codes 声明还是接受全局兜底形态）属契约改动，需 Task Card 与人批；本轮仅落账。
- 收束：→ 本项与 ①—99 一并不在本轮 AI 自决实施范围内。error-catalog 全量三向对账：码集/四属性/OpenAPI 引用零幻影三个主面全绿，差集一处（㉒ 端点级错误声明完整性）。

### 101. page-catalog 未覆盖面补核：COM-001/002 非路由页完成条件＋§6 页面状态证据可证性（新增差集候选㉓㉔）——2026-10-14，取证基准 HEAD `2ee6c55`

- (a0) 取证面（k101a，物证 `build-logs/ci-k101-results.txt`，Git 忽略）：第⑯项（L498-569）已完成 40 条可映射路由↔`routes.tsx` 四类 24 条差集与 L38「缺入口」断言证伪（导航一级项齐备，`navigation.ts` L37/40/41 复证），本轮不重复；补核⑯自述排除的三面——(i) `PAGE-COM-001/002` 两条「非路由页」的完成条件、(ii) `PAGE-AST-003` DEC-014 面板内容面（⑯仅记 query-tab 形态）、(iii) §6 页面状态证据七项要求的账本可证性。
- (a1) **通过面（实测成立）**：`PAGE-COM-002`（404）完成条件「不回显未授权资源名称」——`NotFoundPage.tsx` L148-158 固定 i18n 文案＋返回 `/assets`，URL 内容零回显 ✓；`PAGE-AST-003` DEC-014「快速使用」面板实存——`AssetDetailPage` L423-453 git clone/dvc pull/aih CLI 三段代码片段，测试守护在位（`AssetDetailPage.test.tsx` L126-129）✓；`PAGE-COM-001` 的「不泄露私有资源」半边 ✓（403 仅回显所需 Scope 名）；组件测试广度和路由表结构回归（`routes.test.tsx` 重复注册防护＋`/access` 守卫口径锁定）形态良好。
- (a2) **差集候选㉓＝PAGE-COM-001 完成条件「申请路径」与「requestId 可复制」三面缺位（设计缺口，非纯编码缺口）**：403 实现 `RouteGuard.tsx` L50-61 ——(1) 「申请权限」按钮**永远 `disabled`**（L56 无任何跳转/联动）；(2) 页面**无 requestId 展示**（permission 目录 grep `requestId` 零命中，catalog L12 明列「requestId 可复制」）；(3) OpenAPI 无平台权限申请端点（`CreateAssetAccessRequest` L4680 是资产访问申请，非 Permission/Scope 申请）。三面对照：完成条件在需求文本、契约、实现三层同时无着落——与 ⑯ C-6「申请路径」仅记口径漂移不同，本轮坐实其**无可申请目标**。
- (a3) **差集候选㉔＝§6 每 Page ID 七项证据要求在账本面不可证**：130 个 Evidence Manifest 仅 4 个引用任何 `PAGE-*`（EVD-P0BR-067/068/069、EVD-P1008；其中 068 行还把 PAGE-DST-002 误标为「Preview Tab 图片渲染」——DST-002 实为讨论页，口径登记本身有漂移）；126 张 Task Card 仅 12 张绑定 PAGE-*；组件测试内 PAGE-ID 映射注释**零**（k101a[8]）→「每个 Page ID 至少关联一个组件/Hook 测试」无机械核对面。七项中实测：视觉基准零（⑯外已录 UI-007，L1771）；§6 第 6 条「LocalStorage/console/error report 的 Secret 检查」在 e2e **零场景**（k101a[7]）；zh/en「每个 Page ID 两种关键状态」仅 4 个 spec 触及 locale；五态断言散在 13 个 spec 但非 per-Page-ID 口径。→ 处置（补 per-Page-ID 证据矩阵 vs 收敛 §6 措辞为 Journey 级）属规范层裁定。
- 偏差备注（不单列候选）：下载票据 Modal（`AssetDetailPage` L675-681）完整展示 `presignedUrl`（含签名查询串）——catalog「不显示预签名查询串」措辞仅系于 `PAGE-UPL-002`/`PAGE-VER-003`，票据归 `PAGE-VER-002`「下载票据按权」，字面不直接冲突但张力存在；DEC-014 面板四要素中「预签名 URL 示例」未在快速使用面板出现（仅三段 CLI）。均待人类评审裁定口径。
- 收束：→ 本项与 ①—100 一并不在本轮 AI 自决实施范围内。page-catalog 三面补核：路由面（⑯已闭环）、非路由页完成条件面（㉓）、证据可证性面（㉔）。

### 102. user-journey-catalog 全量账本对账：§8 拆任务硬规则执行面＋JRN↔可执行验证面映射（新增差集候选㉕㉖）——2026-10-14，取证基准 HEAD `ca1e510`

- (a0) 取证面（k102a，物证 `build-logs/ci-k102-results.txt`，Git 忽略）：journey-catalog 实测全集 **31 个 JRN**（P0B 10＋P1 5＋P2 4＋P3 3＋P4 5＋P5 4；plan 此前无整域对账，仅 ㊺㊻㊾/P4 节 4 处 REQ 权威链单点引用，本轮不重复）↔ 126 张 Task Card（§8 L90 硬规则「每个任务必须引用本目录中的 Journey ID、Requirement ID 和 Acceptance Scenario ID」）↔ 130 个 Evidence Manifest ↔ 各 wave exit-catalog ↔ `verify-journey.sh`/`verify-case-mapping.md` 可执行验证面四向对账。
- (a1) **通过面（实测成立）**：REQ 文档（01-requirements）27 处 JRN 引用**幻影=0**（k102a[4b]，全部 ∈ catalog 全集）；`validate-spec.ps1` L129-131/L161 对 JRN 定义与引用形态有机械检查在位；`verify-journey.sh` 实存且 CI 挂钩（`ci.yml` journey-e2e job，main push 强制、PR continue-on-error，mapping L85/L136）；`verify-case-mapping.md` 已登记 PRD-V ↔ 函数 ↔ 仓库 verify.sh V 的近似语义漂移（L102-121，「仓库 V05=JWT vs journey V05=创建模型」类同号异义**已有账面**，不另列）。
- (a2) **差集候选㉕＝§8「每个任务必须引用 Journey ID」硬规则在账本面基本未执行**：126 张 Task Card 仅 **5 张**提及任何 JRN（TASK-P1-009/P2-002/P2-007/P2-010/P4-010，且全部是 wave 出口串联任务而非功能任务）；逐 ID 计：25/31 旅程零 Task 挂靠，10 个 JRN-P0B-* 在 P0-B 已宣称 VERIFIED 的情况下**无任何 Task 引用**；EVD 面 130 个仅 2 个文件触及 JRN 字样（EVD-P1009 系 `JRN-P1-001..004` 范围记法、EVD-P2010 命中的实为场景 ID `AC-P2-JRN-001` 非旅程 ID）。与 §8 L89「不允许以后端＋前端任务数量替代旅程完成」的意图正面对撞——账本面上旅程恰恰全部由出口任务单点代表。㉔（PAGE 证据面）同构的旅程域缺口。
- (a3) **差集候选㉖＝JRN↔可执行验证面无账本映射（AGENTS.md 点名 blocker 的覆盖证明缺失）**：`verify-journey.sh` 全文 **JRN-* 字样零命中**——其 18 个 `journey_vNN_*` 函数实现的是 PRD §13.2 V 编号旅程（V18、V20-V24 无函数），与 journey-catalog 的 31 个 JRN 之间无任何一侧建立映射；mapping 文档仅闭合 PRD-V ↔ 函数面。AGENTS.md 将「authenticated P1–P5 business journeys (`verify-journey.sh`)」列为 P1-P5 剩余 blocker，但**账本无法证明该脚本覆盖哪些 JRN**——29/31 旅程（除 P1-001/P2-001/P2-004 经 Task 文本间接挂钩外）无可执行验证面挂靠。E5 三旅程（P5-002/003/004）㊺㊻㊾ 节已有散证，唯缺口在映射账面而非纯演练本身。
- 偏差备注（不单列候选）：(i) Task 内 `JRN-P1-001..004` 范围记法未逐 ID 显式引用，per-ID 机械计数在该处欠计——口径本身亦属 §8 执行形态问题；(ii) JRN-P2-002/003 仅出现在 decision-log L130 权威链，零 Task/零 EVD；(iii) validator 不校验 §8 的任务引用规则（121 张无 JRN 的 Task 照常过验证），归 ⑬ TaskCardGate 形态错配域互证。
- 收束：→ 本项与 ①—101 一并不在本轮 AI 自决实施范围内。journey-catalog 四向对账：REQ 引用幻影零（通过），差集两处——§8 拆任务规则执行面（㉕）、JRN↔verify 旅程映射面（㉖）。

### 103. 未覆盖规范文档普查核面：traceability-matrix 账本矛盾＋前端规范 MUST 缺位面（新增差集候选㉗㉘）——2026-10-14，取证基准 HEAD `6c7523b`

- (a0) 取证面（k103a/k103b/k103e，物证 `build-logs/ci-k103*-results.txt`，Git 忽略）：对 plan 此前 §16—§102 未整域核过的规范文档做引用普查（31 篇中 plan 零覆盖七篇：glossary/personas-and-scope/page-spec-template/product-scope/trae-adapter/information-architecture/frontend-conventions），择其可与项目实况对撞者立面——(i) `02-delivery/traceability-matrix.md`（54 行 DRAFT「初始审计版」）↔ REQ 全集与 P0-B 收口账本；(ii) `04-ui/frontend-conventions.md`（204 行）＋`04-ui/information-architecture.md`（175 行）↔ `frontend/src/**` 实况。AGENTS.md Required reading 所指「api-contract」文档不存在（Glob 证实，contracts 实体＝4 YAML＋redocly.yaml）——措辞失准记偏差。
- (a1) **通过面（实测成立）**：前端四禁面干净——`dangerouslySetInnerHTML` 仅 SafeMarkdown 豁免、`mode="tags"` 仅注释、`\bfetch\(` 真实违例零（k103b[5] 命中全为 TanStack `.refetch()` 假阳性，k103e[3] 精化复证零）、Idempotency-Key helper 在位（`admin/api.ts` L71-72）；共享组件四件套全实存且被用（QueryBoundary 21 files/ControlledSelect 10/SafeMarkdown 2）；i18n zh/en 键面 812=812 零差集（node 平铺比对）；生产代码硬编码中文无真实违例（k103e 排除 test/注释后仅 LanguageSwitcher `'中文'` 语言名 label，属合理）；菜单权限过滤已实现（`AppLayout.tsx` L31-53 `hasAllScopes`）；matrix 幻影 REQ=0（前窗口「REQ-AST-001/REL-001 无定义」系提取器只匹配反引号而二者定义于 `## ` 标题，直接 Grep 证伪）。
- (a2) **差集候选㉗＝traceability-matrix 与收口账本的状态矛盾＋REQ 覆盖缺口**：矩阵 L46 自称「VERIFIED：尚无任何能力在本初始审计中获得该状态」、L37「不是新的产品完成声明」，但 AGENTS.md 与 `implementation-status-2026-07-10.md` §11 宣告 P0-B 已 VERIFIED（038e135 落地、V01-V28 全 PASS）——**权威收口账本与追踪矩阵在「是否存在 VERIFIED 能力」上直接矛盾**，且矩阵口径早于 plan §16—㉖ 全部取证（无一行反映后续证据）。REQ 覆盖双向核对：矩阵 24 行 CAP 之外，**5 个已定义 REQ 无矩阵行**——REQ-AST-003（p1 L103）、REQ-AUTH-003（p0b-identity L411，保持 OPEN 等待 TASK-GOV-002）、REQ-DL-001（p2 L225）、REQ-JOB-001（p0b-platform L178）、REQ-OBS-001（p0b-platform L344，受 AUD-005 阻塞）。→ 处置（矩阵重裁 vs 明确其「历史审计快照」定位并声明不再维护）属规范层裁定。
- (a3) **差集候选㉘＝前端规范 MUST 面在代码面系统性缺位**：(1) **OpenAPI 生成类型全库零落地**——`frontend/src/shared/api/` 仅 6 个手写模块、全库无 generated 文件、`package.json` 无 codegen 依赖/脚本，而 conventions L30「返回类型使用 OpenAPI 生成的 View 类型，禁止页面内声明重复 DTO」；派生面＝features 手写 `export interface` 77 个（admin/types.ts 50＋assets 16＋version 5＋access 3＋integrations 3）。(2) **双份权限映射违反单源禁令**——`navigation.ts` NAV_ITEMS 每项自带 `requiredScopes` 与 `routes.tsx` 各 `guarded()` 调用各自维护（值抽查一致，但 IA L88/conventions L177「菜单和 Route Guard 使用同一受控 Route Catalog，禁止维护两份」在结构上被违）。(3) MUST 行为面零实现——Breadcrumb 全库零命中（IA L92「所有二级详情页稳定面包屑」）、beforeunload 零（conventions L131）、AbortController/查询取消零（conventions L119）；Header 四要素缺位（AppLayout L72-100 仅品牌＋语言切换＋用户菜单，缺环境标识/组织项目选择器/全局搜索/未读通知）；useSearchParams 仅 5 文件（conventions L181-183 URL 状态同步推广面低）。→ 实施面（补生成链/单源化/逐条落地）与工作豁免（修订规范措辞）均属人类裁定。
- 偏差备注（不单列候选）：(i) conventions L58 usePermission「待实现」标注陈旧——该 Hook 实存于 `app/permission/usePermission.ts`，文档滞后；(ii) IA L61「当前 /version、/upload、/access 是脱离具体资源的占位路由」断言陈旧——routes.tsx L61/64/66 三路由实存且为已实现功能页；(iii) AGENTS.md Required reading「Relevant OpenAPI…」无 `api-contract.md` 实体（见 a0）。三处均为文档↔实况单向滞后，回写属人类通道。
- 收束：→ 本项与 ①—102 一并不在本轮 AI 自决实施范围内。未覆盖文档普查核面：四禁面/i18n/幻影 REQ 全通过（a1），差集两处——traceability-matrix 账本矛盾（㉗）、前端规范 MUST 缺位面（㉘）。

### 104. 零覆盖治理文档收尾核面：Trae 历史 spec 不受控勾选＋AC-AI-IDE-008 五类 CI 门禁实测（新增差集候选㉙㉚）——2026-10-14，取证基准 HEAD `cf91cae`

- (a0) 取证面（k104a/k104b/k104c，物证 `build-logs/ci-k104*-results.txt`，Git 忽略）：承接 §103 引用普查剩余的零覆盖文档——`06-ide/trae-adapter.md`（160 行 ACTIVE）、`05-acceptance/ai-execution-gate-catalog.md`（8 条 AC）、`01-requirements/product-scope.md`、`glossary.md`、`personas-and-scope.md`、`04-ui/page-spec-template.md`。编号域声明：本节 ㉙㉚ 沿 §95 以来「近期核面域」序号（与文件中部旧 Wave 域 ㉑—㉚ 重名系既有状态，互见时以节号消歧，不回改旧文）。
- (a1) **通过面（实测成立）**：gate-catalog `AC-AI-IDE-001..007` 的 validator 本体实存——治理版 `docs/ai-spec/tools/validate-spec.ps1`（10451 字节，manifest 148 文档/752 稳定 ID 全量校验）＋`validate-task-card.ps1`（`-CheckChangedPaths` 支持）均在；`glossary`/`personas-and-scope` 为定义性文档，无与实况可对撞的 MUST 行为条款；AGENTS.md 自身即要求跑治理版两脚本（本地执行链闭合）。
- (a2) **差集候选㉙＝Trae 历史 spec 的 70 项勾选不受控且 §8 迁移规则零执行（⑱ 同谱系坐实）**：trae-adapter L21 断言「当前检出中 `.trae/` …目录不存在」**失准**——本地 `.trae/specs/` 实存 **11 个 spec 目录**（bootstrap-p0-baseline/complete-p0b-verification/establish-p0b-platform-foundation/fix-code-gaps/fix-g2-gaps/gap-analysis-code-vs-design/implement-code-iterations 等；`.gitignore:2` 忽略属实）。对账实况：(i) 其中 6 个 spec 的 checklist.md 共 **70 项 `[x]`**、tasks.md 共 49 项 `[x]`（k104a[3][4]）；(ii) **全部 spec.md 无一个 `> Source Task:` 或 Base Commit 头**（k104b[1]，11/11 不符合 §3 格式，即 §2 八项前置无一体现）；(iii) §6 evidence/ 子目录格式**零 spec 遵循**（k104c[2]，仅 complete-p0b-verification 有单文件 evidence-manifest.yaml）；(iv) §8「迁移现有 Trae Specs」要求标记 `HISTORICAL`、不把旧 `[x]` 导入新状态——**HISTORICAL 全库零命中**（k104b[1]）。风险面与 ⑱（traceability-matrix L112 把 Evidence 权威指针指向不受控的 `.trae/specs/gap-analysis-code-vs-design/gap-list.md`）闭合为同一事实的两端：不受 Git 追踪的本地勾选/证据被受控文档引用，任何 clone 不可复验；trae-adapter L22-23 的免责句（「不构成已安装证据」「勾选不能替代正式 Task Card」）方向正确但与 L21 存在性断言自相矛盾（文档写作时目录确实不存在，其后又产生——断言未随实况更新）。
- (a3) **差集候选㉚＝AC-AI-IDE-008 要求五类受保护 CI 门禁，实测落地 2.5/5**：gate-catalog L16 要求「受保护 CI 必须运行 spec/task/scope/evidence/breaking 门禁并阻断合并」↔ `ci.yml` 实况——(1) spec 类：spec-gate job L125 跑 `./tools/validate-spec.ps1`＝**root 浅探针版**（2758 字节，仅 4 组 Test-Path＋尺寸检查，不校验 manifest/稳定 ID/引用闭合）＝ ⑭ 幻影镜像的门禁目录侧复证，治理版 validator 在 CI **零运行**；(2) task/scope 类：task-card-gate L323-350 存在但**双条件跳过**——无基线 SHA `exit 0`（L338-339）、无 Task Card 变更 `exit 0`（L342-344），scope 检查（`-CheckChangedPaths`）仅在 Task Card 本身被改动时触发，产品代码越权改动 allowedPaths 不触发任何 job（⑬ 形态错配的 CI 侧复证）；(3) evidence 类：**ci.yml 全文零 evidence job/step**（k104c[1]），130 个 EVD manifest 无任何 CI 机械校验（result 值、commitSha、redaction 声明均裸文件）；(4) breaking 类：contracts job L127 实存 ✓。→ AC-AI-IDE-008 按其自身判据即 UNSATISFIED，而 gate-catalog 状态为 READY、REQ-AI-IDE-001 视为已确认——目录与 CI 实况的差集无人账面。
- 偏差备注（不单列候选）：(i) product-scope L6「当前焦点：P0 尚未完成，先保持产品代码冻结」——READY 文档状态行滞后于 AGENTS「P0-B VERIFIED」与 P1-P5 大量实现事实（㉗ 矛盾体在第三份治理文档的复现，与 ⑯ 同族）；(ii) page-spec-template.md 自创建**零实例**（04-ui 无 per-page spec 文件，page-catalog 以表代）——模板按需使用不构成违例，但与 ㉔（per-Page-ID 证据不可证）同方向：页面规格深度停留在目录表格；(iii) `.trae/specs/fix-g2-code-gaps`、`iterate-contracts-and-verification` 两目录为空壳（k104b[5]）；(iv) `tools/` 仅一个文件（root 浅版），无治理版镜像。
- 收束：→ 本项与 ①—103 一并不在本轮 AI 自决实施范围内。零覆盖治理文档收尾：validator 本体/i18n 定义面通过（a1），差集两处——Trae 不受控勾选面（㉙）、五类 CI 门禁实测面（㉚）。§103—104 两节合计后，31 篇规范文档的 plan 零覆盖清单已全部处置（七篇：四篇入候选/偏差、glossary 与 personas 通过、page-spec-template 偏差）。

### 105. 02-domain 状态机/不变量域＋架构双文档核面：READY 状态机文档三处枚举漂移＋publish_request 状态机脱离治理面（新增差集候选㉛㉜）——2026-10-14，取证基准 HEAD `61c5703`

- (a0) 取证面（k105a—k105g，物证 `build-logs/ci-k105*-results.txt`，Git 忽略）：plan 从未整域核对的 `02-domain` 三篇（`state-machines.md` 232 行、`entity-invariants.md` 225 行、`consistency-and-compensation.md` 201 行，均 READY）↔ 后端 15 个 `*Status.java` 枚举＋44 个 Flyway 迁移的 `ck_*_status` CHECK 约束逐一对撞；另 plan 提及=0 实测确认的 `docs/architecture/data-ownership.md`（56 行）与 `module-map.md`（109 行）。编号域声明沿 §104：本节 ㉛㉜ 为近期核面域序号（旧 Wave 域 L1712 亦有 `### ㉛`，互见以节号消歧）。
- (a1) **通过面（实测成立）**：state-machines 十三节中 9 节与实现逐值一致——UserStatus 四值、Agent/RefreshToken/Org/Dict/Tag 各 CHECK、AssetStatus 三值（`ck_asset_status`＝Java 枚举）、VersionStatus 六值（V16 `ck_asset_version_status`＝`VersionStatus.java` 且 `assertTransitionTo` 转换表与文档 §7 表一致）、JobStatus 六值、Idempotency/Webhook 四值、Comment VISIBLE/RETRACTED/HIDDEN 均对。表名面：文档 L52 所指 `job_task`＝V9 实表，`outbox_event`（V11）/`webhook_inbox`（V18）/reconciliation checkpoint（V28）主体在位；§6「建议新增独立 provisioning 状态」被实现采纳（`ProvisioningStatus` 五值独立枚举）；data-ownership 为 ADR-0001 同源的边界定义文档，无可对撞枚举条款。
- (a2) **差集候选㉛＝READY 状态机文档冻结在「审计/建议」口径，三处枚举漂移无回写**：(i) UploadSessionStatus 文档 §8 自述「设计未给出完整枚举，建议 CREATED→UPLOADING→COMPLETING→…」「需要在 P2 Accepted Requirement 中确认名称…后才能成为稳定枚举」——实规定**由实现侧单方面完成且命名不同**（Java/DB＝OPEN/COMMITTING/PROCESSING/COMPLETED/FAILED/CANCELLED/EXPIRED，无 CREATED/UPLOADING/COMPLETING），V42 迁移头注释自证曾发契约漂移（「V16 建立 ck 时遗漏 PROCESSING/FAILED…正确暴露了契约漂移」）；(ii) DiscussionStatus 文档 §12 两态 OPEN↔LOCKED，DB `ck_discussion_status`＋`ThreadStatus.java` 三态含 CLOSED，而 CLOSED 全后端仅枚举定义行一处命中、零业务转换写入＝文档未声明的数据库允许死状态；(iii) §3 PrincipalStatus、§10 IdempotencyStatus 无 Java 枚举承载（find 证实仅 `AuditResult.java` 存在；幂等状态以 `ck_api_idempotency_status` 字符串约束存活），文档 L61「Principal 聚合状态必须与 User/Agent 一致」无代码层载体（与 ⑨ 旧域整改项 ⑨ 同源复证）。文档全文「待确认」10 处、状态头 READY——状态与口径互斥，同 ㉗（matrix DRAFT vs VERIFIED）同族矛盾的状态机侧。entity-invariants 141 项 INV 的头部 MUST「必须同时体现在 Domain、数据库约束、API 校验和自动化测试」仅 job 域有账面回指（4 个 Java 文件＋V41 SQL），其余 140 项无机械可证的满足记录。
- (a3) **差集候选㉜＝publish_request 七态状态机整体脱离 AGENTS.md 固定基线的「代码枚举」半句**：`ck_publish_request_status` 七值（V17 六值＋V26 增 CHANGES_REQUESTED）是全平台最大的业务状态机之一，但——state-machines.md 无该状态机一节（§7 仅覆盖 VersionStatus，发布审批流转只在模块图口头提及）；**Java 侧无 `PublishRequestStatus` 枚举**（15 个 `*Status.java` 清单证实），实现以裸字符串比较推进（`PublishJobHandler` L78/L85 等 7 处＋UPDATE SQL 字面量）；OpenAPI 无该 status enum 声明（grep 全契约仅 `x-audit-event` 命中）；前端 `ReviewPage.tsx` L289/L397 直接判字符串字面量。→ 与 AGENTS.md「稳定工作流状态保持代码枚举与数据库约束」「永不返回持久化实体」「契约优先」三条对撞：DB 约束半句在，枚举/契约/文档半句全部缺位，且 V26 加态时四处（文档/枚举/契约/前端）零同步。
- 偏差备注（不单列候选）：(i) Flyway 序列 **V31 缺号**（V30→V32；`git log --all -- '*V31*'` 空＝从未入库），Flyway 容忍跳号非功能缺陷，但编号域空洞与「forward-only」账本的直觉预期相悖；(ii) module-map.md（plan 提及=0）17 个逻辑模块名 vs 实际 18 个 Java 包三处形态漂移——`agent` 包（AgentController）无模块行归属、`com.aihub.observability` 与 `com.aihub.platform.observability` 双置并存、`module-` 前缀系逻辑命名未按目录采纳；(iii) V38 `publish_saga_compensation` 以 `ALTER TABLE publish_request` 加列实现，文档 L52「Saga/Step 复用 job_task 还是独立表需决定」的悬置决策已被实现单方面选择；(iv) state-machines 通用规则 §1 动作端点/禁 PATCH status 面抽查未发现违例。
- 收束：→ 本项与 ①—104 一并不在本轮 AI 自决实施范围内。02-domain＋架构双文档核面：9/13 状态机与表名面通过（a1），差集两处——READY 状态机文档枚举漂移无回写（㉛）、publish_request 状态机治理缺位（㉜）。

### 106. runbooks 零覆盖面核面：publish-governance 整篇通过＋JWT 轮换部署面断裂＋DVC STS 路径双缺口（新增差集候选㉝㉞）——2026-10-14，取证基准 HEAD `55ed4c5`

- (a0) 取证面（k106a—k106e，物证 `build-logs/ci-k106*-results.txt`，Git 忽略）：九篇 runbook 的 plan 提及普查（k106a[2]）实测 `jwt-key-rotation.md`=0、`dvc-sts-credentials.md`=0、`publish-governance.md`=0——三篇零覆盖整篇对撞部署/实现实况；`ops.md`(11)/`compose.md`(3)/`security.md`(5)/`backup-restore.md`(6)/`notification-channels.md`(4) 经行号抽查确认命中均为**实质整改记录**（Z-7 docker-compose.yml 断引×5、P-5 V1-V23↔V44 漂移、X-9 两头失真、L1441 分发链缺失判「文档待重写」）而非泛提，故以互见方式处理、不重复立项。编号域声明沿 §104/105：本节 ㉝㉞ 为近期核面域序号（旧 Wave 域 L1848/L1882 亦有 `### ㉝`/`### ㉞`，互见以节号消歧）。
- (a1) **通过面（实测成立）**：**publish-governance.md 全篇九项断言与实现对齐**——本核面域罕见的整篇通过文档：①多评审人四敏感级（CONFIDENTIAL/SECRET/HIGH/RESTRICTED→需 2 名不同 Principal）＝`ReviewQuorumPolicy.java` L20＋`PublishApplicationService` L289 消费；②saga P1 前置（APPROVED/PUBLISHING）＝`PublishJobHandler` L78；③P2 版本须 PENDING_REVIEW＝`Version.java` L103-105；④补偿矩阵症状字面「git tag conflict」＝`RestGiteaTagPublisher` L57、`GITEA_DEPENDENCY_UNAVAILABLE`＋RETRY_WAIT＝`GiteaVersionMaterializer` L84/L123；⑤RECONCILE 三分类表＝`PublishedVersionReconciler` 分类集恰 {CONSISTENT, MANUAL_REVIEW, SECURITY_INCIDENT}（无 AUTO_REPAIR，与 `AssetRepositoryReconciler` 四分类正确区分）；⑥`:retry` 端点＝`JobController` L70＋OpenAPI L1388；⑦DEAD→PENDING 重试语义＝`JobApplicationService` L76-91；⑧VERSION_PUBLISH/VERSION_VALIDATE/PUBLISHED_VERSION_RECONCILE 三 job 类型实名（`RecurringJobBootstrapper` L53）；⑨审计最小字段面＝`DvcConfigurationService` L96-100 仅记 expiresAt/bucket/credentialType/accessKeyId。dvc-sts 通过半边：`init.sh` L54 dvc-scoped 策略四 Action（GetObject/PutObject/DeleteObject/ListBucket）↔ dvc-cache 前缀与 runbook 逐字一致；TTL 15min/上限 1h、STS 优先→scoped 回退→未配 fail-closed、AWS SDK v2 sts 依赖（pom L154-157）、`/assets/{assetId}/dvc/credentials` 端点（OpenAPI L3356）全对。jwt 实现面：`JwtTokenService` previousPublicKey 重叠校验（L65/L191-194/L209-213）＋`JwtProperties` record 字段＋测试 `verifiesTokenSignedWithPreviousKeyDuringOverlap`（L101）在位。
- (a2) **差集候选㉝＝JWT 轮换 runbook 与部署面断裂：Compose 形态下禁令无机械执行面**：(i) `compose.yaml` 的 `AIHUB_JWT_*` 注入计数=0（k106d/k106e 双证）且无 `application-compose.yml`/`application-api.yml`（仅 test profile），SPRING_PROFILES_ACTIVE=compose,api 下 JWT 六键全走空默认→`JwtTokenService` L222-228 **永远生成 ephemeral 密钥对**（仅 LOG.warn，无 profile 门禁/无 fail-closed），而 runbook L37-38「生产环境禁止使用临时生成的 ephemeral 密钥对」——禁令零机械面；p5-ops-fault-drill 后补的 `ephemeral-jwt fail-closed` 断言（62c92f0，§103 物证）证项目自知该态。(ii) `application.yml` jwt 节（L92-100）其余六键均有 `${AIHUB_JWT_*}` 占位，**唯独缺 `previous-public-key-pem`**——runbook L15 配置表的核心轮换键在部署注入面无路径（Spring relaxed binding 理论可注入，不定性为功能缺陷，差集点为与六键注入惯例的不对称＋文档键面与配置模板脱节）。
- (a3) **差集候选㉞＝DVC STS 优先路径三缺口：root 凭据静默回退＋roleArn 无实体＋零测试**：(i) `MinioStsCredentialIssuer.issue()` L49-52——`dvc-access-key` 未配置时**静默回退 `minioProperties.getAccessKey()/getSecretKey()`（MinIO root 凭据）调用 STS AssumeRole**，与 runbook L18「服务端使用 dvc-user 凭据调用」及 L7「永不返回平台 MinIO root 密钥」形成返回值合规/调用方违例的半面一致（回退失败路径 L97-99 仅 LOG.debug 静默降级）；(ii) L34 roleArn 硬编码 `arn:aws:iam::minio:role/dvc-scoped`，但 `init.sh` 仅 `mc admin policy create` 建**策略**、无 IAM role 实体——依赖 MinIO 对 roleArn 的宽容解析，隐含契约文档未声明；(iii) STS 路径零测试：Glob `backend/src/test/**/*Sts*.java` 无命中，runbook L49 验证命令 `DvcConfigurationServiceTest` 仅断言 SCOPED_CONFIG_KEY（L91）——文档宣称的**优先路径**无实证覆盖。
- 偏差备注（不单列候选）：(i) compose 主文件实名 `compose.yaml`（AGENTS.md/部分文档口径 `docker-compose.yml` 系 Z-7 已录断引族复现）；(ii) `previous-public-key-pem` 缺占位不定性为缺陷（relaxed binding 可用），仅登记注入惯例不对称；(iii) ephemeral 密钥对 dev/test 是 `application.yml` L90 注释声明的合法兜底，差集点仅在 compose 形态无 profile 分离配置；(iv) `JwtTokenService`/`JwtTokenServiceTest` 位于 `com.aihub.platform.security` 而非 identity 包，runbook 验证命令按类名 `-Dtest=` 仍可命中，不构成违例。
- 收束：→ 本项与 ①—105 一并不在本轮 AI 自决实施范围内。runbooks 零覆盖三篇核面：publish-governance 整篇九项通过（a1，罕见全对齐样本），差集两处——JWT 轮换部署面断裂（㉝）、DVC STS 路径三缺口（㉞）；四篇旧域覆盖 runbook 互见不重复立项。

### 107. ADR 双篇零覆盖核面：技术基线/事实源/JWT/迁移冻结条款全对齐，worker profile 被 ENTRYPOINT 覆盖＋MinIO 默认凭据回退（新增差集候选㉟㊱）——2026-10-14，取证基准 HEAD `aa77547`

- (a0) 取证面（k107a—k107d，物证 `build-logs/ci-k107*-results.txt`，Git 忽略）：plan 提及普查（k107a[2]）实测 `ADR-0001-technology-baseline.md`=0、`ADR-0002-p0-platform-foundation-gate.md`=0——docs 全域唯一仅剩的零覆盖整域（其余：168 篇 ai-spec tasks 中 59 篇零提及，属逐卡对撞面、非架构断言面；architecture 目录 14 篇中日期快照件属历史账面域）。ADR 是 AGENTS.md「Fixed architecture baseline」与「AI implementation gate」的判据源（required reading 第 4/5 条），两篇逐断言 ↔ 仓库实态对撞。编号域声明沿 §104—106：本节 ㉟㊱ 为近期核面域序号（旧 Wave 域 L1916/L1955 亦有 `### ㉟`/`### ㊱`，互见以节号消歧）。
- (a1) **通过面（实测成立）**：ADR-0001 技术路线七条全对——Java 21（pom L26）＋Spring Boot 3（mybatis-plus-spring-boot3-starter）＋MyBatis-Plus 3.5.9（L28）、React 18.3＋TS 5.7（package.json L25/L45）、四类事实源矩阵 ↔ MinIO 四桶（init.sh L18 gitea-storage/dvc-cache/asset-staging/asset-preview）＋`asset-staging` 生命周期 2 天回收（L26-27，对应「临时上传有生命周期」断言）、「DVC 仅由受控 Worker 镜像携带并锁定版本」＝`backend/Dockerfile` worker 阶段 `dvc[s3]==3.55.2`＋`pathspec==0.11.2` 双锁（L50-51，V06 崩溃教训注释在位）、无 Python 业务服务旁路、REST/Worker 同 jar 复用应用内核（profile 分化）。ADR-0002 十条全对——JWT 访问 15 分钟/刷新 7 天默认值（`JwtProperties` L41-42 精确匹配）、刷新令牌仅存摘要（V33 `jti_digest` SHA-256＋回填＋索引）、Cookie Secure 默认 TRUE（`RefreshCookieProperties` L26，compose 显式关闭有注释说明「本地 http 验收」）、**V1/V2 迁移从未被修改**（`git log --diff-filter=M` 对 V1/V2 为空，前向-only 成立）、tag→tagId/tagIds 三项收紧（OpenAPI `pattern: '^tag_'` ×8＋tagIds schema ×3）、「不定义为 OIDC Provider」（全仓 oidc 仅 2 处注释词汇、无端点实现）、通知最小集（站内＋签名 Webhook 实现在位，Email/IM 适配器 `@ConditionalOnProperty` 默认关闭＝ADR L43「供应商确定后接入」允许的前瞻实现）、ArchUnit（`LayeredArchitectureTest`/`MapperScanCoverageTest`）。
- (a2) **差集候选㉟＝worker 容器的 compose profile 被镜像 ENTRYPOINT 命令行参数静默覆盖，两处运行时门禁在任务执行侧失效**：compose.yaml L165 注入 `SPRING_PROFILES_ACTIVE: compose,worker`，但 `backend/Dockerfile` L54 worker 阶段 ENTRYPOINT 硬编码 `"--spring.profiles.active=worker"`——Spring Boot 命令行参数优先级**高于环境变量**→ worker 进程 activeProfiles 实为 `[worker]`，compose profile 永不生效。后果两点（「compose」字符串全后端仅 2 个消费点，k107d[1] 穷举）：(i) `PublishedVersionReconciler.isStrictGiteaReconciliationProfile()` L207-209 判 compose‖production——而 PUBLISHED_VERSION_RECONCILE 周期任务恰由 worker 侧 JobWorker 驱动，执行进程判定恒 false → GITEA_UNAVAILABLE 走 L204 `return CONSISTENT` 静默放行（§106 a1⑤ 所证三分类表在 worker 运行时缺一分支的实效）；(ii) `application.yml` L168-176 compose profile 节的 ECS JSON 结构化日志对 worker 不生效（数据源因 env 显式注入不受影响）。backend 容器（runtime 阶段 L29 无参数 ENTRYPOINT）compose,api 正常生效——**仅 worker 单侧断裂**，且 `verify.sh` 对 worker 仅断言 python3 可用/service_running（L852/L882），从未断言 profile 实效，E4 全绿未覆盖此面。
- (a3) **差集候选㊱＝MinioProperties 硬编码默认凭据＋compose worker 段注入不对称，worker 侧 MinIO 访问回退 minioadmin**：`MinioProperties.java` L21/L24 accessKey/secretKey 默认值硬编码 `minioadmin/minioadmin`；compose.yaml backend 段显式注入 `AIHUB_MINIO_ACCESS_KEY/SECRET_KEY`（L145-146），**worker 段缺这两行注入**（仅 L175 endpoint＋L177-178 AWS_* DVC 侧键）→ worker 进程任何经 MinioClient 的任务路径（预览清理/staging 回收类 job）以默认 root 口令尝试连接。与 AGENTS.md「认证授权 fail-closed」「Secrets 不得进入…测试 fixtures」及 ADR-0002 凭据治理口径相悖；默认值设计使缺失注入**静默可连**（MinIO root 口令恰来自同 `.env` 的 `MINIO_ROOT_USER`，默认口径一致则连成——凭据漂移时则任务静默失败）。
- 偏差备注（不单列候选）：(i) 「可选的 Gitea LFS/附件对象」桶未建——ADR 措辞可选，不构成违例；(ii) ADR-0002 L21「P0-B 状态为当前阶段」的时间性措辞 vs AGENTS「P0-B VERIFIED」——ADR 系历史决策记录本不回改，仅注记；(iii) `patTokenTtl=90d`（JwtProperties L43）为 ADR 未覆盖的 P1 域新增，无冲突；(iv) `JobConfiguration` L102 `matchIfMissing=true`＋backend 段未设 `AIHUB_JOB_WORKER_ENABLED` → api 进程同样跑 JobWorkerScheduler，与 L34 注释「Worker 调度受 enabled 控制」的实例角色分离语义弱化（单机 PoC 可辩护，双进程竞争靠租约机制兜底）；(v) 59 篇 ai-spec Task 卡零提及属逐卡对撞面，留作后续候选面。
- 收束：→ 本项与 ①—106 一并不在本轮 AI 自决实施范围内。ADR 双篇核面：技术基线/事实源/JWT/迁移冻结/契约收紧 17 项断言全通过（a1），差集两处——worker profile ENTRYPOINT 覆盖（㉟）、MinIO 默认凭据回退（㊱）。docs 稳定面（adr/architecture/runbooks/ai-spec 规范文档）的 plan 零覆盖整域至此收敛完毕。

### 108. prd 主设计文档 §8 后端接口核面：envelope/乐观锁/限额/TTL 六项对齐＋端点形态三代漂移＋transfer 配置四层断裂（新增差集候选㊲㊳）——2026-10-14，取证基准 HEAD `887a127`

- (a0) 取证面（k108a—k108c，物证 `build-logs/ci-k108*-results.txt`，Git 忽略）：判据源 `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` §8（后端接口设计，L1303-1482）——AGENTS.md required reading 第 1 条、plan 既往 107 节对 prd §8 端点清单/响应结构/CLI/限额/TTL 判据面零核（㊞ REQ-CLI、⑮/⑯ 旧域仅触及 CLI 传输面）。四方对撞：prd §8 ↔ OpenAPI 全契约约 90 端点清单（k108a[1] 全列）↔ 后端实现（transfer/asset/agent/configuration 包）↔ CLI＋前端消费面。编号域声明沿 §104—107：本节 ㊲㊳ 为近期核面域序号（旧 Wave 域 L1980/L2004 亦有 `### ㊲`/`### ㊳`，互见以节号消歧）。
- (a1) **通过面（实测成立）**：(i) prd 8.2 统一响应 envelope 逐字段完全对应——OpenAPI `ApiResponse` required=[requestId,traceId,timestamp]＋`ApiError` required=[code,message,retryable,requestId,traceId]＋i18nKey/details（契约 L3695-3770 全文实读），契约描述句「Agent 不应依赖本地化 message…只依赖 code 与 retryable」与 prd L1388 同文；(ii) prd 8.1「避免 PATCH status 绕过状态机」——`UpdateAssetRequest`（L4695-4734）无 status 字段，仅可变元数据＋expectedVersion 乐观锁；(iii) prd 8.1 `/models` `/datasets` 兼容入口在位（L2336/L2372）；(iv) prd 8.3 Cursor 分页——cursor query 参数＋`CursorPage*` schema 全契约 8 处广泛在位；(v) prd 8.4/L1452 20 GiB 单会话限额——`UploadSession.MAX_SESSION_BYTES = 20L*1024*1024*1024`（L16）＋`UploadApplicationService` L175/L183 同值校验；(vi) prd 8.5/L1478 预签名 15 分钟——`DownloadApplicationService.TICKET_TTL = Duration.ofMinutes(15)`（L37）＋类注释「15 分钟 TTL，最小对象权限，100% 审计」；(vii) CLI 复用 REST 契约路径（正向面，已由旧域 ㊽ E-4 登记，不重复计权）。
- (a2) **差集候选㊲＝prd §8.1 端点形态与契约实态三代漂移：冒号动作端点全契约零命中＋下载票据语义降级＋discussions 单线程获取契约缺失**：(α) 冒号动作端点——prd 的 `:validate/:submit/:approve/:deprecate`（L1325-1328）、`parts:sign`（L1346）、`:complete`（L1347）在全契约 grep 零命中（仅 L3146 `x-required-permissions: [publish:approve]` 权限码命中）；实态＝`POST …/transition`＋`TransitionVersionRequest.targetStatus` 六值 enum（L5295-5301）、`/versions/{id}/deprecate|archive`、`/assets/{id}/deprecate|archive|restore`、publish-requests+decisions 审批链、`GET /upload-sessions/{id}/parts/{n}/presign`＋`POST …/complete|cancel` 斜杠子资源——presign GET 经三方一致检验（契约/前端 `UploadPage.tsx` L215/CLI `dataset.push.ts` L100 全 GET），prd 系唯一漂移方（研究文档未回写实现演化，裁决不判契约/后端违例）；(β) 下载票据形态——prd L1332/L1462 `POST …/download-tickets` 返回 `methods[]` 数组（PRESIGNED_URL＋GIT_DVC 并列、客户端能力协商）vs 契约 `GET /versions/{versionId}/download`（L3595-3621）返回 `DownloadTicket` required=[method] **单值** enum（L5312-5327）＋`DownloadApplicationService` L112/L134 服务端二选一分叉——票据语义降级，客户端协商面消失；(γ) prd L1338 `GET /discussions/{discussionId}` 单线程获取全契约零命中；`:lock/:retract/:moderate`（L1340-1342）实态为 `/lock` `/retract` `/hide` `/unhide` `/unlock` 斜杠形态＋threadId 命名——同 (α) 漂移族。
- (a3) **差集候选㊳＝transfer 限额/TTL 配置治理四层断裂：DB seed 值≠代码硬编码值≠契约≠prd，且两配置键零消费**：`V8__configuration.sql` L60 seed `transfer.web.maxSessionBytes`='5368709120'（**5 GiB**）≠代码硬编码 20 GiB（仅满足 prd 数值语义）≠无消费——`ConfigKeys.TRANSFER_WEB_MAX_SESSION_BYTES`（L20）与 `TRANSFER_PRESIGNED_URL_TTL_SECONDS`（L22，seed 900 s ↔ prd 15 分钟数值巧合一致）全 `backend/src` grep 仅定义行命中＝**双 dead key**；限额与 TTL 真实生效面均为 `UploadSession`/`DownloadApplicationService` 编译期常量，管理端「配置可调」语义（system_config 表＋configuration 模块全套设施）对 transfer 域形同虚设，且 seed 值与硬编码值互相矛盾（管理员改 DB 值无任何效果、按 DB 值口径答疑则错 4 倍）。
- 偏差备注（不单列候选）：(i) prd 8.3「默认只返回有权访问且状态为 PUBLISHED 的版本」——docs 规范链唯一着落 `p4-agent-integration.md` L123 仅约束 **Agent 面**；实态 `AssetSearchCriteria` L70-72 默认 statuses={ACTIVE,DEPRECATED}（资产级）＋accessScope 下推满足「有权」半边，`AgentController` L120-121 `findLatestPublishedByAssetIds` 仅富化不过滤——人类检索面版本级 PUBLISHED 语义在规范链无载体，不升格候选；(ii) CLI 全部差集（inspect 契约缺失、`--verify` 只算 SHA 不比对（pull L72/L104）、无 `--resume`、凭据明文、退出码撞车）与旧域 ㊽ REQ-CLI-001 E-1—E-6 逐面一致，互引不重复计权；(iii) prd L1388 幂等/错误码面与 [11]/⑩ 旧域覆盖面对撞未发现新增违例；(iv) 前端目录实名 `frontend/`、资产检索包实名 `com.aihub.asset`——prd/文档口径 `web/`、catalog 属命名漂移族（与 ㉘/Z-7 同族），不单列。
- 收束：→ 本项与 ①—107 一并不在本轮 AI 自决实施范围内。prd §8 核面：envelope/乐观锁/兼容入口/游标分页/限额/TTL 六项对齐（a1），差集两处——端点形态三代漂移＋票据降级（㊲）、transfer 配置四层断裂双 dead key（㊳）。prd 判据面中 §5 平台能力/§15 AI 规则为治理文档映射域（⑯/㉗ 等旧域已触及）、附录面属参考性质；至此 prd 核心可机判接口条款收敛完毕。

### 109. 权限五方差集／契约↔控制器路由对账／Swagger 匿名面三候选面独立复证：全部撞旧域或证伪，无新增差集候选——2026-10-14，取证基准 HEAD `1ec1a89`

- (a0) 取证面（k109a—k109f，物证 `build-logs/ci-k109*-results.txt`，Git 忽略）：§108 收束后探测新候选面三个——(i) OpenAPI `x-required-permissions` 标注 ↔ 后端 `Permissions` 常量 ↔ `SecurityConfiguration` 匿名白名单五方对撞；(ii) 契约 operationId/path ↔ 39 控制器 139 路由键全量集合差；(iii) swagger-ui / v3/api-docs 匿名放行面。定面前查重不足（以核面名称而非发现关键词 Grep），执行后深读方确认：面 (i) 全部发现被 ㉕「权限目录五方差集」（L1145-1207）逐条覆盖，面 (ii) 被 plan L406「契约侧必须取两份 OpenAPI 文件的并集」条款覆盖，面 (iii) 证伪。本节按复证＋方法论面收束，不新增候选编号（㊴—㊶ 经查证均已被旧 Wave 域 L2074/L2097/L2150 占用，近期域本轮无新项）。
- (a1) **复证面（㉕/L406 台账独立实测全部吻合）**：(α) 权限面——契约 135 operation 中 126 处 `x-required-permissions`（93.3%）；NOPERM 端点 python yaml 精确计数 **=9**，与 ㉕ 合规面第 2 条逐一对应（`/auth/login` `/auth/token` `/auth/agent/token` `/auth/refresh`、`/me` `/me/password`、`/webhooks/gitea`、`/mcp`），与 `SecurityConfiguration` 匿名清单（L69-77）全对齐；`publish:approve` 为 126 声明中唯一目录外幻影码＝㉕ 缺陷面 B 复证；后端权限码 35＝`Permissions.java` 常量全集（V4 seed 31＋V12 `asset:manage`＋V14 discuss/moderate＋V20 team:manage 补至 35，无 `enum Permission`），`Permissions.ASSET_DOWNLOAD/SUBMIT/UPLOAD` 常量引用计数 **=0**＝㉕ 缺陷面 C 死码复证（`ASSET_DEPRECATE`=1、`ASSET_MODERATE`=4、`MCP_INVOKE`=4 在用）；`HIGH_RISK_ACTIONS`={ASSET_PUBLISH, ASSET_DELETE, SYSTEM_CONFIGURE}、`McpToolCatalog` 工具映射（5×READ/3×MANAGE/PUBLISH/DELETE）、`requirePermission` 消费 147 处／40 文件——均与 ㉕ 账面一致，无新增差集。(β) 路由面——契约（仅主文件）130 去重键 vs 后端 139 路由键，表面差集 8 MISSING＋16 UNDOC **全部为探针伪影**：UNDOC 的 `/agent/*` 十端点实定义于第二契约文件 `contracts/openapi/aihub-agent-v1.yaml`（九 path，L33-320 本轮实读），per plan L406 并集条款收回；MISSING 的 previews×3/stats/artifacts content/datasets/models/metrics-downloads 系 `PreviewController` 常量路径 `PREVIEWS_BASE`（L33）裸注解正则不可解析＋`AuthController` 类级 `@RequestMapping("/api/v1")` 前缀双计。(γ) Swagger 面——`SecurityConfiguration` L79-81 swagger permitAll 受 `swaggerPermitAll()`（L95-100：activeProfiles 含 compose/prod/production 任一即关闭）门禁，类注释 L36 与实现一致，非缺陷，收回。
- (a2) **探针伪影方法论修正（取证偏差登记，不升格候选）**：(i) awk 解析 OpenAPI 两轮均 operationId↔path 错位（方法行缩进正则失配）→ YAML 对撞一律 `python3 yaml.safe_load`；(ii) 权限码消费面字面量串 grep 假阴性（`'"asset:deprecate"'` 仅命中定义行）→ 必须 `Permissions\.常量名` 引用计数；(iii) Spring 路由静态探针须展开常量路径与类级前缀，且契约侧必取双文件并集，单文件对撞必致 `/agent/*` 类大面积假阳。流程教训：定面前查重应以**发现关键词**（幻影码、NOPERM、`/agent/`、`x-required-permissions`）Grep plan 全文，而非核面名称——本节 k109a/k109b 两轮因此消耗于已覆盖域（结论转作 ㉕ 复证）。
- 收束：→ 本项与 ①—108 一并不在本轮 AI 自决实施范围内。§109 为复证节：权限五方/路由对账/Swagger 三候选面独立实测全部落回 ㉕ 与 L406 既有账面，零新增差集候选；产出为三类探针伪影修正方法论。备选新面（前端手写 TS 类型 ↔ 契约 schema、契约 `x-error-codes` 端点级绑定）经关键词查重亦已分别被 L258/L1426/L1497/NFR-MNT-003/W-9 与第⑲项/㉒ 覆盖，不再列为候选。

### 110. Task 卡逐卡对撞面（§107 偏差(v) 留任面）：「零提及 33 张」证实为计数伪影＋已兑现存量卡，零新增差集候选——2026-10-14，取证基准 HEAD `617cf7d`

- (a0) 定面与取证面（k110a—k110i，物证 `build-logs/ci-k110*-results.txt`，Git 忽略）：§107 偏差(v) 明文留任「59 篇 task 卡零提及属逐卡对撞面」→ 本轮执行。k110a bash `grep -c … || echo 0` 双行算术法失败（grep -c 自带 0 输出，不可再叠 `|| echo 0`，弃）；k110b python 双形态字面量计数（`TASK-XXX.md`＋裸 `XXX`）实测 126 卡中**零提及 33 张**：P0BR-052/059/060/061/071/072/075＋老代 000B/004/011/012/015/020/022/025/027/028/042＋P1-003..008＋P2-008＋P3-002/004/005/007/008＋P4-002/003＋P5-008。k110c 抽 13 张代表卡卡头：全为 READY＋authorized＋E4/E5 scenarioEvidencePlan（059/060/061＝DEF-058 修复卡、075＝verify.ps1 合并事故恢复卡、P1-003 等＝DEC-010 连续实施代主卡）。
- (a1) **第四类探针伪影：短号多义性与 brace/区间形态盲区（k110e 复查证伪 k110b）**：① §65 已以区间/brace/裸短号形态深覆盖新代卡 067..078 十二卡卡面（L2861 `EVD-P0BR-0{67,…,78}` brace 展开式字面不含 `P0BR-071`、L2862「075 卡（十项 IAM 场景逐条 proven）」、L2868「071 null-skip/072」）→ 071/072/075 之「零提及」纯系字面量计数伪影；② 052/059/060/061 在 plan 以连字符族形态被点名（L3154「054/055/056/058/066/067…075/076」、L3208「P0BR-**049/050/059/060/061/062**…全有连字符」）；③ 裸短号与节号/AC 号/行号同形歧义（L2457「### 52 REQ-DST-TAX-001」系本文 §52 节标题而非卡 052；「61 拒绝断言」系 §61）。字面量 count 无法识别 brace 展开、区间列举、连字符族、裸短号四形态，且无法消歧节号同形——**卡面覆盖查重必须逐形态＋逐语境人工判读，机器计数仅可作初筛**。§107「59 篇」口径修正：python 双形态实测 33 → 简称形态剔除后真「未经卡面断言对撞」残余≈24（老代 9 张另论），且经 (a2) 核验全部为已兑现存量卡。
- (a2) **真残余卡「卡面断言 × 现势代码」逐卡兑现核验（24/33 深验，全部兑现，零缺陷）**：k110d（7 卡）——052 `IDEMPOTENCY_KEY_CONFLICT`＋`job/application/IdempotencyService`（注意实名非 platform/idempotency）；059 `failed_login_attempts`×4 文件＋AUTH_ACCOUNT_LOCKED；060 `AUTH_REFRESH_REPLAYED`@AuthenticationApplicationService；061 USER_* 五审计事件 refs=1..2＋`isLastActivePlatformAdmin` L183/L224；071 `renameRequested = command.displayName() != null`@AssetApplicationService L190；072 AssetSettingsPage L250 `rules required`；075 verify.ps1 九函数 def=1×9＋IamProbePassword 10 处。k110g/h/i（17 卡）——P1-003 Provision 14 文件＋`asset_provision` job 型；P1-004 cursor 51 文件；P1-005 CardProjection 4 文件；P1-006 expectedVersion/@Version 乐观锁 asset 包 6 文件＋alias 11；P1-007 deprecat/archiv/restore 24/19/16；P1-008 AssetsPage 等全套＋PAGE-AST-002 注释着落（CreateAssetPage.tsx L2）；P2-008 preview 18＋parquet 6；P3-002/004/005/007/008 ValidationReport/GiteaTagPublisher（Rest/Noop/Reconciler 三态）/PublishJobHandler 补偿/降权/verify.sh 全在位；P4-002 McpController streamable 命中；P4-003 download ticket 14 文件；P5-008 observability DownloadStats 全栈 8 文件（DEC-016 兑现）；P0BR-011 LOCKED 状态机 22 文件；P0BR-042 自由输入残留探针 **=0**（断言＝移除，零命中即兑现）。零命中消歧三处均命名漂移非缺陷：CreateRepository=0（实为 Gitea*Publisher 族）、quickstart=0（快速使用内嵌 AssetDetailPage）、McpSession=0（会话态收敛于 McpController 单文件）。
- (a3) 老代 9 卡（000B/004/012/015/020/022/025/027/028）不单列：其治理面已被 §69（32 卡 null 批准人谱系 L2983）/§72（REBOUND-TO-GHOST 含 000A/000B/001-046 L3099）覆盖，其卡面断言属 P0-B 已 VERIFIED 域（V01—V28 Compose 全绿即兑现证据，AGENTS.md §Phase），逐卡重验与 §68—70 全库治理面重复计权。
- 收束：→ 本项与 ①—109 一并不在本轮 AI 自决实施范围内。§110 收束 task 卡逐卡对撞面：33 张「零提及」＝字面量计数伪影（简称/族形态）＋已兑现存量卡（24/33 深验全绿），**零新增差集候选**（近期域本轮无新项，㊴—㊶ 旧 Wave 占用不变）；产出第四类探针伪影方法论＋§107「59 篇」口径闭合。至此 §107 偏差(v) 留任面处置完毕；ai-spec 126 卡账面（治理维 §68—70 × 卡面维 §65/63/64 × 兑现维本节）三面闭合。

### 111. manifest 登记完整性双向差集核面：正向差集为空（validate-spec 每轮守护），反向差集 22 篇（真未登记 19 篇＝17 张 READY 卡＋2 篇 04-ui 规范；DRAFT 3 卡属登记惯例），validate-spec 无逆向守护（新增差集候选㊷㊸）——2026-10-14，取证基准 HEAD `ad26541`

- **(a1) 定面与查重**：上窗口末候选面「manifest 登记路径 ↔ 磁盘 *.md 全集差集」查重：plan 全文无「登记差集/未登记文档/册外文档」等实质对账节（仅 §R-9/L2339 以数字口径「148 文档」滞后样本轻触边界，非集合级双向差集）→ **面新，可推进**。
- **(a2) k111a 取证（双向差集初测）**：manifest 登记 `path:` 项 148 条（145 .md + 3 非 .md：tools/validate-spec.ps1、tools/validate-task-card.ps1、templates/evidence-manifest.yaml）；磁盘 ai-spec/*.md 共 168 篇。正向：148 条登记全部在磁盘解析成功（UNRESOLVED_REF=0）→ **正向差集=0**，validate-spec L64 每轮 PASS 实证守护（`manifest path does not exist` 为 Add-Error 条件）。反向 basename 法初测：UNREGISTERED_MD=**22 篇**（分布：tasks/×20、04-ui/×2）。物证 `build-logs/ci-k111a-results.txt`。
- **(a3) k111b 取证（卡头状态普查）**：manifest 登记 task 卡数 = 106（磁盘 126，差集 comm-23 = **20 张卡**：P0BR-058/059/060/061/066/067/068/069/070/071/072/073/074/075/076/077/078/079/080/081）；登记反向差集 comm-13 = **0**（无悬空引用）。058 子串遮蔽检验：manifest `058` 全文零命中 → 058 真未登记，非 basename 法伪阳性。卡头状态分布：17 张（058—061/066—078）全为 **READY＋implementationAuthorized: true**＋各绑定 1 份 EVD 证据文件（EVD-P0BR-{058..078}-001.yaml 均存在）；3 张（079/080/081）为 **DRAFT＋authorized: false**＋EVD=0。04-ui 2 篇（admin-pages-mips.md/frontend-conventions.md）文头状态均 READY，被 EVD-P0BR000B-001.yaml 各引用 4 处（文档活跃在用）。物证 `build-logs/ci-k111b-results.txt`。
- **(a4) k111c 取证（登记惯例与工具盲区定性）**：① 062/063/064/065 在 manifest 各有 1 处 `TASK-P0BR-06N` 登记 → 断档自 066 起（非全段漏登）；058—061 为同期漏登（058＝IAM 场景 E4 证据补齐卡，059/060/061＝DEF-058 修复批）。② manifest 106 张已登记卡中 status 分布：106 READY，**零 DRAFT** → 不登记 DRAFT 卡是现存惯例，但 tasks/README.md 无明文规则（Grep `登记|manifest|全量|索引` 零命中），惯例口径未文档化。③ validate-spec 登记完整性守护分析：L54-58 仅提取 `^  - path: (.+)$` 条目并检查磁盘存在（正向）；L245 `Get-ChildItem -Recurse -Filter '*.md'` 全集扫描**仅用于相对链接有效性**，L163 ID 引用扫描**仅用于 ID 定义存在性**——三处扫描均**不检查「磁盘 *.md 须有对应 manifest 登记」**，逆向守护结构性缺失。④ 148 口径构成澄清：validate-spec 输出「manifest documents: 148」计数 = `manifestEntries.Count`（含 3 条非 .md 登记），而非磁盘 .md 文件数；磁盘 .md=168，差额 = 22 未登记＋3 非 .md 条目，账目闭合。物证 `build-logs/ci-k111c-results.txt`。
- **(㊷) 差集候选（人类门禁处置，本轮不动）**：**manifest 登记断档 19 篇**——17 张 READY+authorized+EVD 的 task 卡（P0BR-058/059/060/061/066—078，其中 058—061 为 IAM 证据补齐＋DEF-058 修复批同期漏登，066—078 为 Wave I 增量起全量断档）＋2 篇 04-ui READY 规范文档（admin-pages-mips.md、frontend-conventions.md，DEC-013 产物）。DRAFT 卡 079/080/081 不登记属惯例（106 张登记卡零 DRAFT），但惯例无文档声明，随本候选提请补注。整改动作：由人类批准人在 manifest `documents:` 段追加 19 条 `- path: tasks/TASK-P0BR-0XX.md` 及 `- path: 04-ui/xxx.md`；同步评估 079/080/081 在批准转 READY 时是否自动追加（惯例文档化）。
- **(㊸) 差集候选（工具化缺口，本轮不动）**：**validate-spec 对 manifest 登记完整性仅有正向守护，无逆向守护**——新增磁盘 .md 文件无需登记即可进入 validate-spec PASS，结构性漏洞与「DEC-009 规格包完整性」语义不符。整改：在 validate-spec L245 附近（已有 `$markdownFiles` 全集扫描）增加逆向差集检查（`$markdownFiles | Where-Object { $_.FullName 相对于 specRoot 不在 $manifestEntries }` → `Add-Warning` 或 `Add-Error`），须人类裁决以 Error（阻断）还是 Warning（软告警）形态落地，以及 DRAFT 卡是否入豁免名单（与 ㊷ 惯例文档化同步）。
- **收束**：→ 本项与 ①—110 一并不在本轮 AI 自决实施范围内。§111 首次完整量化 manifest 登记差集：正向=0（工具守护确认），反向=22（真未登记19＋DRAFT惯例3）；㊷ 是 DEC-009 治理链中「登记断档」的第一次集合级定装，㊸ 是工具层面「为何断档能长期无告警」的根因定位。编号消歧（沿 §99 候选㉑/旧节㉑ 共存惯例）：本节 ㊷㊸ 为**差集候选号**，与旧 Wave **节号** ㊷（L2152 REQ-LOG-001 日志面）/㊸（L2185 REQ-OBS-001 观测面）字面同形异域；候选序列自 ㊳（§108）续进，本轮跳 ㊴—㊶（§110 已声明旧 Wave 占用）取 ㊷㊸；后续取号与节号占用域（㉗—㊿）同形时按本节惯例逐处消歧，不再视为禁用域。两候选均属人类登记/工具整改通道，AI 不动 manifest 不改动 validate-spec 脚本。

### 112. docs 全域路径引用可达性机械普查：反引号 PATHISH 666 种/1750 处、UNRESOLVED 453 种/1232 处，伪阳与先科甄别后硬面收敛为 V 号漂移族＋已删文件现势引用＋docker-compose 断引第 6 例＋validate-task-card allowedPaths 零存在性校验——2026-10-14，取证基准 HEAD `442aef0`

- **(a1) 定面与查重**：候选面「docs 全域（Git 受控 *.md）路径引用可达性机械普查」查重：历史节仅单例处置（§97 gap-list 指针、§106 Z-7 docker-compose 断引 ×5、§㉙ N3 evidence 端 V 号漂移），无集合级普查节 → **面新，可推进**。k112a 先扫 Markdown 相对链接面：受控 docs/prd *.md=193 篇，`\[…\]\(…\)` 链接 142 处，剥 http/mailto/锚点后相对链接仅 **12** 处，DEAD=**0** → 该子面干净（validate-spec L245 守护有效），主面转**反引号路径引用**（本仓库文档惯例，validate-spec 完全不扫）。物证 `build-logs/ci-k112a-results.txt`。
- **(a2) k112b 全量普查**：反引号 token 18058 个；PATHISH（带扩展名相对/绝对路径样式，含 `:L123` 行号后缀）distinct=**666** / occurrences=**1750**；四策略解析（所在文件目录/仓库根/`docs/`/`docs/ai-spec/`）全失败 → UNRESOLVED distinct=**453** / occurrences=**1232**。其中 BASENAME_EXISTS 伪阳为大头（verify.sh×44、verify.ps1×41、verify-journey.sh×37、application.yml×28、aihub-v1.yaml×24 等——文件实存但引用者书写目录基准不符，属散文短名非硬断引）；TRULY_ABSENT（全库 basename 亦无）硬命中=**58** 条进入精核；build-logs/* 为 Git 忽略物证的合法非受控引用，整体除名。物证 `build-logs/ci-k112b-results.txt`。
- **(a3) k112c/d/e 精核与除名甄别（七类先科引用，防重复立项）**：Flyway 实名序列：V26=`publish_request_changes_requested`、V27=`publish_request_frozen_commit`、idempotency_scope 实物=**V40**、max=**V44**=`preview_split_stats`；deploy/compose 实存仅 `compose.yaml`；runbooks 9 篇无 performance.md。除名谱系：① §㉙ N3/L1899 已录 **evidence 端** V26↔V40 漂移五处＋§㉙ L1554 已录 V27__job_attempt_trace；② §106 Z-7 已录 docker-compose 断引 ×5（ops.md L54＋backup-restore.md L30/L54/L57/L73）及 performance.md/deploy/perf 从未存在（L2296 P-1）；③ §108 偏差(iii) 已录 api-contract.md 缺失；④ §62 E-6 已录 pnpm-workspace.yaml 负断言；⑤ TASK-P0BR-060 L205「V27 保留未用（allowedPaths 已声明，未创建）」＝**合规负断言**，非漂移；⑥ implementation-status L46「历史曾预留 V27__asset_backfill、编号已被 P3 占用」＝叙述性自曝；⑦ phantom 短名四处（AssetBrowser.tsx/SecurityConfig.java/PrincipalStatus.java/TagView.java）经语境复读**全部已录于 plan 既有修正账**（L130 G1 记录修正、L1696-1697 两处文件名推断更正、L1839 取证偏差登记②）→ 除名。物证 `build-logs/ci-k112c/d/e-results.txt`。
- **(a4) 硬面一：V 号漂移族（k112f，Git rename 感知谱系定装）**：`33b0e4a`（2026-09-17）按 056 卡面字面新增 `V26__idempotency_scope.sql`；`c633ebc`（2026-09-22，merge P1–P5）因 V26 号位已被 publish_request_changes_requested 占用，以 **RA（rename-add）** 将其重命名为 `V40__idempotency_scope.sql`。TASK-P0BR-056 卡面现势 **V26×4 处、V40×0 处**（L128 新增迁移全路径＋L132「严禁修改 V9、V20」语境）——**卡正文漂移未随合并回写**，EVD-P0BR-056-001 按其声称编号取证；§㉙ N3 只录 evidence 端五处，卡面端为本节**新登记增量**。同族第二例：plan L79（Wave γ 早期目标稿）「前向迁移 `V44__asset_favorite.sql`」vs 02-delivery/work-breakdown L96 与 TASK-P0BR-079 L182 `V45__asset_favorite.sql`（V44 实物已被 preview_split_stats 占用）——同号族两口径，L79 属历史目标区未回写（严重度低，登记从略）。物证 `build-logs/ci-k112f-results.txt`。
- **(a5) 硬面二/三：已删文件现势引用＋断引第 6 例**：TASK-P0BR-054 L96 以「缺陷事实（已复核）」**现势口吻**全路径描述 `backend/src/main/java/com/aihub/shared/idempotency/IdempotencyInterceptor.java`，该文件删除于 `84255e4`（2026-09-17，现势 ls-files 零命中、全库 basename NONE）——卡面描述对当前树失效（卡为禁改面，须验证权威重出时收缩为历史时点口径）。`deploy/compose/docker-compose.yml` 断引**第 6 处**：runbooks/ops.md **L122**（进程隔离行 worker `mem_limit` 配置指认），§106 Z-7 已录 ×5 未含此例 → 并入 Z-7 整改批，断引计数修正为 **×6**。
- **(a6) 硬面四：工具存在性校验双缺口**：① validate-task-card.ps1 L519-551 对 allowedPaths 仅做 placeholder/通配/过宽/逃逸/不可变迁移**六类结构检查，无 `Test-Path` 存在性校验**（L623/L679 的 Test-Path 属 changed-paths/evidence 另路）——056 卡幽灵路径 `V26__idempotency_scope.sql`、TASK-P5-004 `deploy/perf` 均从此漏网，是「幽灵授权路径可获批 READY」的结构性根因；② validate-spec.ps1 L245 全集扫描仅覆盖 `[]()` 相对链接，**1750 处反引号 PATHISH 零守护**（本普查实证），断引可长期静默。两缺口与 §111 ㊸ 同族不同工具/不同面。
- **(㊹) 差集候选（人类门禁处置，本轮不动）**：**056 卡面 V26 幽灵迁移引用**（L128/L132 四处，实物 V40）＋**054 卡面已删文件现势描述**（L96）——两卡均为禁改面，AI 不改动；须验证权威重出卡面（056 卡编号回写 V40 或将 L128 收缩为「作用域迁移（落地 V40）」叙述、054 卡 L96 标注 `84255e4` 前历史时点限定），并随 §㉙ N3 evidence 端五处同批处置，杜绝「卡面声称→evidence 按声称取证→实物另一文件」的取证链自证循环。ops.md L122 断引第 6 例并入 §106 Z-7 整改批（×5→×6 计数已在 (a5) 定装）。
- **(㊺) 差集候选（工具化缺口，本轮不动）**：**validate-task-card 增 allowedPaths `Test-Path` 存在性校验**（对 READY+authorized 卡：逐条 allowedPath 做「将存在」白名单——新增迁移/新增文件类路径须要求卡面显式声明 `plannedNew: true` 类标记方可豁免，否则 Error），修掉 (a6)① 幽灵授权漏网根因；**validate-spec 增反引号 PATHISH 抽样守护**（至少对 `db/migration/`、`contracts/`、`runbooks/`、`deploy/` 四前缀的硬断言式引用做磁盘存在性检查，BASENAME_EXISTS 类散文短名入豁免，避免误报洪水），须人类裁决 Error/Warning 形态与豁免名单粒度。
- **收束**：→ 本项与 ①—111 一并不在本轮 AI 自决实施范围内。§112 首次对 docs 反引号路径引用做集合级普查：Markdown 链接面 0 死链（守护有效），反引号面 1232 处未解析经四层甄别（basename 伪阳/build-logs 合法物证/先科已录七类/合规负断言）收敛为硬面四组：V 号漂移族（卡面端新增量）、已删文件现势引用、断引 ×6 计数修正、工具存在性双缺口。编号消歧（沿 §111 惯例）：本节 ㊹㊺ 为**差集候选号**，与旧 Wave **节号** ㊹（L2218 观测 Z 面）/㊺（L2251 REQ-SEC-001）字面同形异域；候选序列自 ㊷㊸（§111）续进取 ㊹㊺。两候选分属人类重卡与工具整改两条通道，AI 不动 task 卡/EVD，不改动 validate-spec 与 validate-task-card 脚本。

## 5. 本轮实施范围声明

按用户目标「整理成迭代计划进行实施」：本文档即迭代计划产出。
实施自 **Wave α** 起（G7/G8 为已实证的工程缺陷与文档矛盾，属 DoD「无 placeholder / 文档同步」直接范围）；
Wave β 及之后涉及契约与迁移新增，须先补对应 Task Card 并经人类授权，不在本轮 AI 会话内自批。

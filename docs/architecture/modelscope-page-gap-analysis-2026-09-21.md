# 对照魔搭社区（ModelScope）页面范式的差距分析

> 状态：分析报告（非规范文档；页面目标态以 `docs/ai-spec/04-ui/page-catalog.md` 与
> `docs/ai-spec/01-requirements/dataset-experience.md`（DEC-008）为准）
> 日期：2026-09-21 ｜ 作者：AxeXie
> 背景：本产品按魔搭社区的资产管理页面范式仿制（PRD《内部AI资产管理平台设计》），
> 本文档盘点当前实现与目标范式之间的差距，作为 P0-B 出口后 P1+ 迭代的输入。
> 门禁提醒：当前处于 P0-B 平台底座整改期（ADR-0002），本文档第 4 节标注了
> 哪些缺口可在门禁内以「占位实现/契约一致性整改」处理，哪些必须等出口后立项。

## 1. 方法

- 现状证据：逐文件盘点 `frontend/src`（路由 [routes.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/app/router/routes.tsx)、导航 [navigation.ts](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/app/layout/navigation.ts)、各 feature 页面与其 API 层）+ 契约 `contracts/openapi/aihub-v1.yaml` + 迁移 V1-V27。
- 对照基准：魔搭公网站点的核心页面范式（门户首页、模型/数据集列表（facet+排序+卡片）、详情多 Tab（README/文件树/版本/在线体验/讨论）、组织/用户主页、全局搜索、文档中心、个人中心（收藏/订阅）），以及项目自身 `page-catalog.md` 的 PAGE-* 目标态。
- 定位差异前提：本项目是**企业内部** AI 资产管理平台（PRD §5 平台化能力 + 授权 fail-closed），不复制魔搭的公网社区运营面（榜单、点赞、Stars、论坛区、内容推荐算法）；差距分析按「资产发现→理解→获取→协作」的范式主干对照，运营面仅列出不作为目标。

## 2. 范式对照总表

| # | 魔搭范式 | 项目目标态（PAGE-*） | 当前实现 | 完成度 | 主要差距 |
| --- | --- | --- | --- | --- | --- |
| 1 | 门户首页（banner、精选、推荐位、快捷入口） | 未定义（page-catalog 无 Home 条目；`/` 直接重定向 `/assets`，routes.tsx L52） | 无 | 0% | **目标态缺位**：需先补 REQ/PAGE 定义再实现；内部平台可做「最近发布/待我审批/我的订阅」仪表盘式首页而非营销 banner |
| 2 | 模型/数据集列表：搜索 + 多维 facet 侧栏 + 排序 + 卡片流 + 分页 | `PAGE-AST-001`（facet 不泄漏、URL 保存筛选） | [AssetsPage.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/assets/AssetsPage.tsx)：关键词 + 单一 sensitivity 下拉 + 游标"加载更多"+ Table 行 | ~50% | facet 后端已就绪（契约 `/assets/facets`、前端 [api.ts](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/assets/api.ts#L50) `getAssetFacets` **已封装但零页面调用**）；缺类型/任务/框架/语言/标签/组织多维过滤、排序白名单、卡片视图、筛选状态入 URL |
| 3 | 详情页 Overview（Card/README、治理字段、License、使用限制、克隆地址） | `PAGE-AST-003` | [AssetDetailPage.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/assets/AssetDetailPage.tsx)：基本画像 + README 投影安全渲染 + 生命周期 | ~40% | 无 Tab 化信息架构（Overview/Files/Versions/Preview/Discussions/Settings）；`cloneUrl` 类型已有但 UI 只渲染 htmlUrl、无 `git clone`/DVC 命令展示（魔搭的核心获客动作）；latestPublished 未呈现 |
| 4 | 文件树/单文件浏览/下载 | `PAGE-VER-003` | **无对应页面与后端端点**（契约无 `/versions/{v}/files`） | 0% | 需契约先行（Gitea tree/blob 代理端点 + 权限过滤 + 路径安全），再做 UI；下载票据仅藏在 `/version` 手输 ID 页 |
| 5 | 版本切换器（版本/Commit/Tag 三元组 + 按版本浏览） | `PAGE-VER-001/002` | 版本列表面板 + 版本详情页（Commit/GitTag/Digest/工件表） | ~60% | 缺「详情页内版本下拉切换 + URL 与 query key 联动」（REQ-DST-DETAIL-001 明确要求）；dvcFile/dvcHash 字段已定义未渲染 |
| 6 | 数据集预览（Schema/Split/样例表格） | `PAGE-DST-001` | [PreviewPanel.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/version/PreviewPanel.tsx) 已实现，但只挂在版本详情页 | ~35% | 资产详情外壳不展示预览；无 Schema/Split 维度（后端 preview 投影当前为整表样例） |
| 7 | 讨论区（楼层、回复、编辑、治理） | `PAGE-DST-002` | DiscussionPanel：线程/嵌套回复/修订/撤回，API 11 端点 | ~80% | 项目内**完成度最高**的魔搭范式；缺 lockThread/unlockThread/hideComment 等已封装未渲染的治理操作 |
| 8 | 在线体验/创空间（Studio/Demo） | 未定义（PRD 无对应概念；最接近的是 MCP Agent 接入） | 无 | 0% | 与内部定位相关：若要「模型试用」需推理网关（P5+ 外部依赖），当前不建议立项；建议以「文档化非目标」收口 |
| 9 | 组织/用户公开主页（资产列表、成员） | 未定义 | 仅管理端 `/admin/organizations|teams|users`（管理员 CRUD） | ~15% | 缺面向普通用户的组织空间页（组织下资产聚合、Team 简介）；需先补 PAGE 定义 |
| 10 | 全局搜索（顶栏大搜索框 + 结果聚合页） | 未定义 | 仅列表页内 keyword 输入 | 0% | 内部平台可由 `#2` 的列表页升级承载，不必独立门户搜索 |
| 11 | 文档/指南中心（使用手册、API 文档） | 未定义 | 仅 [IntegrationsPage.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/integrations/IntegrationsPage.tsx) 静态接入说明（MCP 清单硬编码） | ~10% | 可低成本起步：OpenAPI 契约已有，挂 Redoc/Swagger UI 只读页即达「API 文档」范式 |
| 12 | 个人中心（我的资产/收藏/订阅/活动） | `PAGE-AUTH-002` 仅要求主体+改密 | ProfilePage：账户信息 + 改密 | ~20% | **订阅后端半就绪**：`asset_subscription` 表已在 V15 建好（讨论/版本更新通知语义），但无 REST API、无 UI；收藏未建模（需迁移，V28+ 前向） |
| 13 | 上传体验（大文件断点续传、真实进度） | `PAGE-UPL-001/002` | [UploadPage.tsx](file:///d:/workspace/opengeobot/HarnessDG/frontend/src/features/upload/UploadPage.tsx)：会话/presign/complete/cancel 调用真实 API，**分片上传为假进度**（L99-121 presign 后 setTimeout 伪造、L136 `etag:'mock'`，从不 PUT 字节） | 表面~70% 实际~30% | 详见 §3-D1（占位实现缺陷） |
| 14 | 视觉框架（门户风顶部导航 + 卡片瀑布流 + 主题） | 未定义 | antd 5 管理后台风（Header + 左 Sider + Content） | — | 范式性差异：内部平台保留后台风可接受；若要贴近魔搭需布局级改造 + 主题定制（当前零主题 token 定制、无自定义 CSS） |

## 3. 现有页面内的工程缺陷（可审计，附证据）

这些不是「新页面缺口」，而是已声明实现处的**占位/伪实现/断链**，与 DoD「不得有 placeholder implementation」冲突，属 P0-B 允许的「契约、一致性、测试缺口整改」范畴候选：

- **D1 上传假流程**：UploadPage L99-121 拿到 presign URL 后从不 PUT 文件字节，进度为 setTimeout 伪造；L136 `etag:'mock'`。真实上传需 XHR/fetch PUT 到预签名 URL + 分片状态回填；后端 `REQ-UPL-002` 契约已存在（上传会话端点真实可调，056 幂等作用域已修）。
- **D2 ReviewPage 捏造实体**：L47-56 由版本列表拼造 `requestId: pub_${versionId}`、`frozenDigest:'pending'`、`submittedBy:'system'`——该 requestId 对真实 `publish_request` 表不存在，决策提交必 404/403；且发布链路在 063/064 修复后已真实可用（REST 提交→审批→VERSION_PUBLISH SUCCEEDED 已 E4 实证），页面应改为查询真实请求队列（`PAGE-REV-001` 语义）。
- **D3 三页样式失效**：VersionPage/ReviewPage/UploadPage 通篇 Tailwind className，但 `frontend/package.json` 无 Tailwind 依赖、src 无 CSS 文件——类名无引擎、页面裸样式，且绕过 antd/react-query（`alert()` 反馈）。与其余 antd 页面视觉断裂。
- **D4 不可发现路由（准死链）**：`/review`（routes.tsx L60）与 `/assets/:assetId/settings`（L55）无任何导航/链接入口，只能手输 URL——050 修过一批死链，此处是同型残留。
- **D5 已封装未接线能力**：`getAssetFacets`（facets 过滤）、`createDraftVersion`/`publishVersion`（版本创建入口）、`getDvcConfig/getDvcCredentials`（DVC remote 引导）、讨论治理 `lockThread` 等——API 层就绪而 UI 缺，属「最后一公里」而非新能力。
- **D6 零散硬编码文案**：NotFoundPage/RouteGuard 中文硬编码未走 i18n；AccessPage API Token 卡片明示"即将上线"（后端 `token:create` 属 P4 `PAGE-INT-004` 范围，占位需按 REQ-DST-DETAIL「未实现阶段不得显示可点击 Placeholder」原则处置）。

## 4. 迭代路线建议（按门禁分层）

**层 0 — P0-B 出口前（允许：占位实现整改、契约一致性）**
1. D2 ReviewPage 改真实数据源（发布链路 063/064 已可用，纯前端接线 + 测试）；
2. D4 补导航入口；D6 占位文案处置；
3. （可选）D3 三页 antd 化——若判定为「P1 冻结区整改」范围不足，可与 050 的口径比对后决定推迟。

**层 1 — P0-B 出口后第一批（P1 目录体验，DEC-008 已给目标态）**
4. `PAGE-AST-001` 完整化：facet 侧栏（接 D5 的 getAssetFacets）+ 排序白名单 + 筛选入 URL + 卡片视图；
5. `PAGE-AST-003` Tab 化详情外壳 + 克隆命令展示（git clone/DVC 引导，消费 getDvcConfig）；
6. D1 真实分片上传（对齐 REQ-UPL-002 与 E4 上传回归）。

**层 2 — P2/P3（需契约与迁移先行）**
7. 文件树页 `PAGE-VER-003`（新 REST 端点代理 Gitea tree/blob，权限过滤 + 防枚举）；
8. 版本切换器与按版本聚合（REQ-DST-DETAIL-001 的 URL/query-key 规则）；
9. 订阅/收藏：订阅走 `asset_subscription`（V15 表已有，需 API + UI）；收藏需前向迁移 V28+。

**层 3 — 目标态缺位，需产品决策（补 REQ/ADR 后才进矩阵）**
10. 门户首页（仪表盘式）、组织/用户主页、文档中心（Redoc 挂 OpenAPI 可作最小方案）；
11. 创空间/在线体验建议正式声明为**非目标**（内部平台无推理网关），或按 PRD 增补决策。

## 5. 结论

- 骨架与底座（认证、授权、治理、任务、审计、版本/发布链路）已经真实可用并有 E4 证据；
- 与魔搭范式的差距集中在**发现（facet/排序/首页）、获取（文件树/下载/克隆/上传）、空间（组织主页）**三组体验面；
- 最刺眼的是三类「表面已实现」缺陷（假上传、捏造审批队列、裸样式页）——它们比缺失的新页面更先侵蚀可信度，建议按层 0 优先处置。

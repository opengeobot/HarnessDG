# 实现状态快照（2026-07-12）——模型/数据管理完整性审计与差距登记

> 本文记录在 `implementation-status-2026-07-10.md` 之后，针对「对标 HuggingFace 的模型管理与数据管理本地化部署」两个核心功能的完整性审计结论与差距登记。
> 不回写更早快照。作者：AxeXie
> 基线 Commit：`71a03ef`（HEAD at audit time）

## 0. 审计结论

对标 HuggingFace 的两个核心功能（模型管理、数据管理）**未完整实现**。P1–P5 仍为
`PARTIAL / IMPLEMENTED_UNVERIFIED`；`-CheckCompletion` 仍 FAIL（420 issues，E4 `notProven`）。

本审计以设计事实源为准绳：
- `prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` §5.2 / §6 / §8.3 / §8.4 / §10.1
- `docs/ai-spec/01-requirements/dataset-experience.md`（DEC-008 端态 MUST 需求）
- 明确非目标（DEC-008 §「阶段出口与非目标」）：公网匿名社区、点赞排行、完整 HF SDK 复制、
  P1/P2 提前实现 MCP 业务 Adapter、AI 永久 MinIO/DVC 凭据、AI 自动审批发布删除扩权、向量数据库。

因此「完整实现」= 闭合 DEC-008 MUST + PRD §5/§6/§8/§10 的设计要求，**不是** HuggingFace 全功能对等。

## 1. 已实现（设计覆盖部分，引用文件:行号）

### 模型管理 + 数据管理（共享统一资产目录）

| 能力 | 后端证据 | 前端证据 |
| --- | --- | --- |
| 资产 CRUD/搜索/Facet | `asset/api/AssetController.java` L71–130；`AssetSearchDao.java` | `features/assets/AssetsPage.tsx` L92–253 |
| MODEL/DATASET Profile | `asset/domain/ModelProfile.java` L26–35；`DatasetProfile.java` L27–36 | `AssetDetailPage.tsx` L230–305 |
| 版本生命周期 DRAFT→PUBLISHED | `version/api/VersionController.java` L55–110；`VersionApplicationService.java` L111–142 | `features/version/api.ts`；`VersionDetailPage.tsx` |
| Multipart 上传/恢复/完成 | `transfer/api/UploadController.java` L52–86；`UploadApplicationService.java` | `features/upload/UploadPage.tsx` |
| 下载（PRESIGNED_URL + GIT_DVC） | `transfer/api/DownloadController.java`；`DownloadApplicationService.java` L81–120 | `features/version/api.ts` L83–89（仅 `VersionPage` 使用） |
| 发布治理（四眼/冻结 Commit/Gitea Tag/Saga） | `version/application/PublishApplicationService.java` L78–296；`PublishJobHandler.java` | `features/version/ReviewPage.tsx` |
| Discussion/Comment 治理 | `asset/discussion/api/DiscussionController.java`；`CommentController.java` | `features/assets/DiscussionPanel.tsx` |
| 血缘查询+创建 | `asset/application/AssetLineageQueryService.java`；`AssetRelationApplicationService.java` | `features/assets/AssetLineagePage.tsx` |
| ACL/Owner/Team | `asset/api/AssetAccessController.java`；`AssetApplicationService.java` L137/L186 | `features/assets/AssetAccessPage.tsx` |
| Archive/Deprecate/Restore | `AssetController.java` L204–232 | `AssetSettingsPage.tsx` |
| Agent/MCP/PAT | `agent/api/AgentController.java`；`mcp/api/McpController.java`；`identity/api/PatController.java` | `features/integrations/`；`features/access/` |
| 下载统计 | `observability/api/DownloadStatsController.java` | `features/assets/DownloadStats.tsx` |

## 2. 未实现/缺口（设计 MUST 项，本轮登记）

### 2.1 后端（`backend/src/main/java/com/aihub/`）

| # | 缺口 | 文件:行号 | 违反设计 |
| --- | --- | --- | --- |
| B1 | Parquet 预览不能从对象存储自动取数；需调用方在 payload 传 `content`/`sampleContent` | `version/infrastructure/PreviewJobHandler.java` L66/L80–86；`ParquetPreviewReader.java` | REQ-DST-DETAIL-001「Preview：选定 Version 的安全样例、Schema、Split 和基础统计」绑定 Version |
| B2 | `GET /datasets` 过滤维度窄于 `/assets`，缺 `taskCodes/modalityCodes/formatCodes` | `asset/api/AssetCatalogController.java` L57–74 vs `AssetController.java` L108–110 | REQ-DST-TAX-001「数据集过滤支持上述所有分类维度」 |
| B3 | 上传 `files` 为空时生成 `upload-N.bin` 占位文件，削弱 Manifest 真实性 | `transfer/application/UploadApplicationService.java` L260–280/L322–335 | PRD §6.5「Manifest/DVC Digest 为机器事实」 |
| B4 | 血缘关系无删除 API（仅 create+query） | `asset/application/AssetRelationApplicationService.java` | PRD §5.2「依赖血缘」治理可逆 |
| B5 | 图片/文本预览缺失（`PreviewJobHandler` 无 `image/*`/`text/*` 分支） | `version/infrastructure/PreviewJobHandler.java` | PRD §5.2「安全预览」MVP |
| B6 | Dataset task/language/modality/format 字典已种子（V35）但未全部接入 `/datasets` 查询 | `asset/api/AssetCatalogController.java`；`V35__dataset_dict_seed.sql` | REQ-DST-TAX-001 接口要求 |
| B7 | OpenAPI 全部 109 端点标 `implemented`，但项目状态为 IMPLEMENTED_UNVERIFIED；契约 overstated | `contracts/openapi/aihub-v1.yaml`；`aihub-agent-v1.yaml` | AGENTS.md「不得将 PARTIAL 表述为 VERIFIED」 |

### 2.2 前端（`frontend/src/`）

| # | 缺口 | 文件:行号 | 违反设计 |
| --- | --- | --- | --- |
| F1 | 详情页缺 Tab + 版本切换器；为堆叠式 | `features/assets/AssetDetailPage.tsx` | REQ-DST-DETAIL-001「Overview \| Versions \| Files \| Preview \| Discussions \| Lineage \| Access \| Settings」+「切换 Version 必须更新 URL」 |
| F2 | Files Tab 缺 Artifact 树（路径/大小/媒体类型/SHA-256） | `features/assets/VersionDetailPage.tsx` L204–213 | REQ-DST-DETAIL-001「Files」 |
| F3 | Preview Tab 缺 Parquet/Schema viewer（schema/列类型/分页） | `features/assets/PreviewPanel.tsx` L69–128 | REQ-DST-DETAIL-001「Preview」 |
| F4 | Facet 不可点击驱动搜索（只读计数） | `features/assets/AssetsPage.tsx` L92–113 | REQ-DST-TAX-001 搜索/Facet 语义 |
| F5 | `/assets/:id/versions` 路由 404（链接到未注册路由） | `features/assets/VersionListPanel.tsx` L92–96；`app/router/routes.tsx` | PRD §10.1「版本中心」入口 |
| F6 | 发布/下载未在详情/版本上下文集成（需手填 assetId） | `features/version/ReviewPage.tsx`；`VersionPage.tsx`；`features/upload/UploadPage.tsx` L330–341 | PRD §10.1 详情页含版本/文件/发布入口 |
| F7 | `createDraftVersion`/`getDvcConfig`/`getDvcCredentials` API 已定义未接线 | `features/version/api.ts` L33–41/L108–114 | PRD §8.4/§8.5 |
| F8 | UI 栈不一致（Ant Design vs Tailwind on Version/Review/Upload） | `VersionPage.tsx`；`ReviewPage.tsx`；`UploadPage.tsx` | PRD §10.1 一致体验 |

### 2.3 E4 验收

| # | 缺口 | 证据 |
| --- | --- | --- |
| E1 | Compose `verify.sh` V05 JWT 生命周期 FAIL（持久化 DB admin 口令漂移） | `implementation-status-2026-07-10.md` §7 |
| E2 | Compose `verify.sh` V20 对账 Worker FAIL（依赖 V05 token） | 同上 |
| E3 | `verify-journey.sh` 资产创建/发布 SKIP（`REPOSITORY_PROVISION` job 500） | 同上 §7「Compose E4 行为旅程 PARTIAL」 |
| E4 | E2E（Playwright）本地未执行 | 同上 §「剩余 E4 差距」 |

## 3. 阶段一（本轮）动作

本轮**不写产品代码**，仅做合规文档与 Task Card 起草（DEC-009 门禁）：

1. 本文件：登记差距清单。
2. PRD §10.1 追加「详情页统一外壳」澄清段，对齐 REQ-DST-DETAIL-001。
3. 盘点现有 READY Task Card 覆盖度，起草 Wave S/T/U/V/W Task Card（`status: READY`，
   `implementationAuthorized: false`，AI 不自行授权）。
4. 更新 `docs/ai-spec/manifest.yaml` documents 列表。
5. 运行 `validate-spec` 与 `validate-task-card` 校验。

## 4. 阶段二（用户授权后）实施波次

| Wave | 主题 | 覆盖差距 | 依赖 |
| --- | --- | --- | --- |
| S | 前端详情页统一外壳 | F1/F2/F3/F5/F8 | T、U |
| T | 后端预览与端点对齐 | B1/B2/B5/B6 | — |
| U | 上传与血缘治理 | B3/B4 | — |
| V | 详情页集成发布/下载 | F6/F7 | S |
| W | E4 验收闭合 | E1/E2/E3/E4 | T、V |

实施顺序（依赖优先）：T、U → S → V → W。每 Wave 严格遵守 Task Card `allowedPaths` 与
AC 证据映射；完成后跑 `-CheckCompletion`，未达 PASS 保持 `IMPLEMENTED_UNVERIFIED`。

## 5. 验证快照（本文件日期）

| 检查 | 结果 |
| --- | --- |
| `validate-spec.ps1` | 待阶段一末运行 |
| 新 Task Card `validate-task-card -CheckChangedPaths` | 待阶段一末运行（预期 PASS） |
| 新 Task Card `-CheckCompletion` | 预期 FAIL（未实施） |
| 产品代码改动 | 本轮无 |

## 6. 诚实声明

本快照不将任何 PARTIAL 翻为 VERIFIED。B7（OpenAPI overstated）需在阶段二 Wave T 中据实
校正契约 `x-implementation-status` 与项目状态的一致性。阶段二 E4 旅程若本机 Docker 不可用，
保持 `notProven`，不谎报。

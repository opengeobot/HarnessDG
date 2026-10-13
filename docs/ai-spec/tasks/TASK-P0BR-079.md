---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-079
status: DRAFT
implementationAuthorized: false
phase: P1
baseCommit: a0227961e085a6feae627bff7fcc276937b63b6b
stageGatePassed: false
stageGateEvidence: AI 起草 DRAFT（2026-10-10，DEC-009：AI 不得自批、不得回写审批位）。
  差距事实链（2026-10-10 以 baseCommit 复证）：全仓 favorite/bookmark 零实现命中（仅迭代计划
  文档自身）；REQ-DST-DETAIL-001 页面组成与 page-catalog 均无收藏条目；iteration-plan-2026-09-24.md
  §4.3 任务清单第 5 项（G4）指定「先补 REQ/PAGE 增补（含收藏语义、退订幂等、通知联动），
  人类评审后开工」。规格增补已于本起草轮落盘：REQ-DST-FAV-001（status: PROPOSED）、
  PAGE-AST-007、AC-DST-FAV-001..004（validate-spec PASS，148 docs / 749 IDs）。
  本卡生效前置（缺一不得开工）：① 批准人评审 REQ-DST-FAV-001 并置 READY（含两项裁定：
  是否引入独立权限位、聚合收藏计数是否开放）；② 批准人回写本卡审批位并按届时 HEAD 更新
  baseCommit（工作树存在 077/078 未提交产物，与本卡共享 AssetDetailPage/i18n/e2e/契约面，
  必须在其合入后串行开工）；③ stageGatePassed 回写 true。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-077-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-078-001.yaml
requirements:
  - REQ-DST-FAV-001
acceptanceScenarios:
  - AC-DST-FAV-001
  - AC-DST-FAV-002
  - AC-DST-FAV-003
  - AC-DST-FAV-004
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-DST-FAV-001|E4|真实 Compose 栈 Playwright：有权用户在详情页收藏 → /favorites 列表出现该
    资产 → 取消后移除；重复 PUT 幂等 204；审计 ASSET_FAVORITE_ADDED/REMOVED 可关联 Principal 与
    资产；Vitest 断言 FavoriteButton 三态（未收藏/已收藏/失败回退）
  - AC-DST-FAV-002|E4|Playwright + 后端契约测试：无 asset:read 主体 PUT → 403/防枚举 404（与
    「资产不存在」同语义）；收藏后撤销授权 → /favorites 静默过滤不报错、响应不含该资产名称；
    收藏不参与搜索排序与 Facet 计数（对照断言）
  - AC-DST-FAV-003|E4|后端并发测试：同 asset_id+principal_id 并发 PUT×N 与 DELETE 竞态 →
    ux 唯一键无重复行、全部幂等成功、审计事件与状态变更一一对应（无多余事件）
  - AC-DST-FAV-004|E4|Compose 真实栈：资产走删除→物理回收 Job 后，asset_favorite 该资产行清零、
    /favorites 无残留无错误；deleteDbRecords 单测断言含 asset_favorite 清理
crossCuttingPlan:
  - AUTHN|复用既有 JWT 过滤器与 PrincipalContextHolder；新端点均为受保护业务接口，不新增匿名面；
    业务模块内不解析令牌
  - AUTHZ|收藏写入与列表读取的前置是主体对目标资产持有实时 asset:read（复用 AssetAccessPolicy
    既有授权判定，fail closed 先于任何写入）；不新增权限位（REQ-DST-FAV-001 评审焦点，若批准人
    裁定引入 asset:favorite 则本卡停止回报、属规格变更）；principal_id 一律取自当前 Principal，
    拒绝请求体/参数传入
  - DB_FILTER|GET /favorites 的 SQL 必须与资产列表同族权限谓词下推（PostgreSQL 过滤，不做内存
    二次过滤），无权/已删资产静默过滤不报错、不泄露名称；游标分页复用既有 Cursor 实现
  - STATE|无状态机变更；收藏不改变 AssetStatus/Visibility/治理字段
  - IDEMPOTENCY|PUT /assets/{assetId}/favorite = 收敛为「已收藏」（INSERT ON CONFLICT DO
    NOTHING，重复调用 204）；DELETE = 收敛为「未收藏」（影响行数 0 亦 204）；状态收敛语义，
    不要求 Idempotency-Key
  - CONSISTENCY|单 PostgreSQL 存储，无跨系统写；物理回收级联清理与既有 deleteDbRecords 同事务
    （asset_favorite 加入子表删除序列，先于 asset 主表）
  - ERRORS|复用既有 ASSET_NOT_FOUND（防枚举语义）与 AUTH_PERMISSION_DENIED；本卡不新增
    ErrorCode 枚举项
  - AUDIT|新增审计动作类型 ASSET_FAVORITE_ADDED、ASSET_FAVORITE_REMOVED（actionType 沿用
    VersionApplicationService.auditVersion 字符串先例，非 ErrorCode 枚举）；审计不落敏感正文
  - NOTIFICATION|N/A - 收藏不触发通知
  - TAXONOMY_I18N|收藏按钮/列表/空态文案 zh/en 对称（favorites.* 键族）；无字典/tag 引用
  - CONFIG|N/A - 无新配置项；不改 deploy/ 与 application.yml
  - OBSERVABILITY|沿用既有请求级指标与 Trace，不新增指标或告警
  - SECRETS|无凭据面；响应/日志不含对象键、Token、内部 Endpoint
allowedPaths:
  - contracts/openapi/aihub-v1.yaml
  - backend/src/main/resources/db/migration/V45__asset_favorite.sql
  - backend/src/main/java/com/aihub/asset/favorite
  - backend/src/main/java/com/aihub/asset/infrastructure/PhysicalRecycleJobHandler.java
  - backend/src/test
  - frontend/src/features/favorites
  - frontend/src/features/assets/AssetDetailPage.tsx
  - frontend/src/features/assets/AssetsPage.tsx
  - frontend/src/app/router/routes.tsx
  - frontend/src/app/layout/navigation.ts
  - frontend/src/shared/api/client.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/04-ui/page-catalog.md
  - docs/ai-spec/tasks/TASK-P0BR-079.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-079-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V15__discussion.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-079-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-079.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-079.md -CheckChangedPaths
  - ./mvnw.cmd -q verify（backend 目录；覆盖 favorite 用例、防枚举/幂等/并发矩阵与回收级联单测）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录；Vitest 含 FavoriteButton/FavoritesPage 用例）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；真实 Compose 栈 E4，收藏全链 + 撤销授权过滤用例，既有套件无回归）
  - npx @redocly/cli@latest lint contracts/openapi/aihub-v1.yaml --config contracts/openapi/redocly.yaml
  - npx @redocly/cli@latest diff EVD-P0BR-079-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking（基线由 git show baseCommit 导出；上游不可得时按 067/068/077 先例等价论证，环境不可得登记 notProven）
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-079：资产个人收藏（G4，草案待批准）

> 状态：`DRAFT`（AI 起草，2026-10-10；DEC-009——批准人评审 `REQ-DST-FAV-001` 置 READY 并回写本卡审批位前，禁止实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。

## 1. 目标

已登录用户可在资产详情页与列表页收藏/取消收藏资产，并在 `/favorites`（`PAGE-AST-007`）查看仅本人可见、当前仍有权读取的收藏列表；所有写入按实时 `asset:read` 前置、幂等收敛，越权与防枚举路径 fail closed；资产物理回收级联清理收藏记录。修复 iteration-plan §4.3 复证的 G4 断链（全仓收藏能力零实现）。

## 2. 关联规格

```yaml
requirements: [REQ-DST-FAV-001（PROPOSED，待人类评审——本卡生效前置）]
acceptanceScenarios: [AC-DST-FAV-001, AC-DST-FAV-002, AC-DST-FAV-003, AC-DST-FAV-004]
decisions: [DEC-008]
adrs: [ADR-0001]
openapiOperations: [新增 listFavorites, addFavorite, removeFavorite]
mcpTools: []
eventSchemas: []
flywayBaseline: V44 → 新增 V45__asset_favorite.sql（asset_id+principal_id 唯一键 + 表/字段中文注释；
  若届时 V45 被占用则顺延并在批准回写时更新本行）
```

## 3. 范围

### 允许修改

- 契约：`PUT/DELETE /assets/{assetId}/favorite`（幂等 204）、`GET /favorites`（游标分页，
  复用资产列表项 schema）；登记 `x-implementation-status: implemented-unverified`。
- 后端：新包 `com.aihub.asset.favorite`（Controller 纯适配器 / ApplicationService 用例 /
  domain 仓储接口 / infrastructure Jdbc 实现）；`PhysicalRecycleJobHandler.deleteDbRecords`
  增加 `asset_favorite` 级联删除；V45 迁移（含表注释与字段注释）。
- 前端：`frontend/src/features/favorites`（FavoritesPage + FavoriteButton，按钮经 Query
  失效与详情页/列表共享状态）；AssetDetailPage 头部与 AssetsPage 列表项挂收藏标记；
  routes/navigation 增加 `/favorites` 入口；i18n zh/en。
- e2e：收藏全链用例（含撤销授权后列表过滤）。
- 文档：本卡、EVD、work-breakdown 状态列、page-catalog `PAGE-AST-007` 实施注记。

### 明确不在范围

- 订阅独立资源（G3）→ `TASK-P0BR-080` 独立成卡；两卡共享 UI 面必须串行（079 先）。
- 聚合收藏计数、热度排序、推荐流（REQ 非目标划界）；Agent/MCP 收藏 Tool。
- 不新增权限位、不新增 ErrorCode 枚举项、不改任何既有端点行为。

### 禁止变化

- 收藏数据不得进入任何搜索排序、Facet 计数或推荐输入。
- `principal_id` 只从服务端 Principal 取得；请求体/查询参数出现主体标识即拒绝。
- 不改已应用 Migration；不动 `V15__discussion.sql` 及订阅表。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-FAV-001` | USER(有 asset:read) | 有权资产 | 详情页 PUT 收藏 → 打开 /favorites | 204；列表含该资产；取消后移除；审计两条 | E4 |
| `AC-DST-FAV-001` | USER | 已收藏 | 重复 PUT | 幂等 204；无重复行、无多余审计 | E2 |
| `AC-DST-FAV-002` | USER(无 asset:read) | 他人私有资产 | PUT | 与不存在同 404/403 防枚举语义；零写入 | E2 |
| `AC-DST-FAV-002` | USER | 收藏后授权被撤销 | GET /favorites | 静默过滤：不报错、不含名称、游标连续 | E4 |
| `AC-DST-FAV-003` | 并发 | 同键并发 PUT×N / PUT+DELETE 竞态 | 收敛后查询 | 唯一键单行；终态与最后写一致；无异常 | E3 |
| `AC-DST-FAV-004` | 系统 | 资产进入物理回收 | Job 执行后查询 | asset_favorite 清零；列表无残留 | E4 |

覆盖正常/边界/失败/越权/并发/回收。

## 5. 横切要求

以 Front Matter `crossCuttingPlan` 13 键为准，不另行放宽。

## 6. 契约与数据先行

1. **OpenAPI**：新增 3 操作（PUT/DELETE 204 + GET 游标列表）；纯新增路径 → 非破坏取证必须 PASS。
2. **示例**：无既有 schema 变更。
3. **新增 Flyway**：`V45__asset_favorite.sql`——`CREATE TABLE asset_favorite(id BIGINT GENERATED
   ALWAYS AS IDENTITY PRIMARY KEY, asset_id VARCHAR(40) NOT NULL, principal_id VARCHAR(40) NOT NULL,
   created_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT ux_favorite UNIQUE(asset_id, principal_id))`
   + `COMMENT ON` 表/列注释（沿用 V15 风格）。
4. **存量清点与回填**：不适用（全新表）。
5. **索引/约束**：`ux_favorite` 唯一键；`(principal_id, created_at)` 列表索引随迁移建立。
6. **兼容窗口与失败恢复**：后端先发（新端点无人调用即无害）；新前端遇旧后端 → 404 → 按钮错误态；
   回滚顺序：前端 → 契约 → 迁移不回滚（Flyway 前向原则，空表残留无害）。
7. **生成类型更新**：前端 API 类型手工同步（067/068/077 同口径）。

## 7. 实施步骤

- [ ] 契约及契约测试（OpenAPI 3 操作 + redocly lint；越权/防枚举/幂等矩阵）
- [ ] Migration V45（含注释）+ 仓储单测
- [ ] Application 用例（收藏/取消/列表；实时 asset:read 前置；Principal 取用）
- [ ] REST Adapter（FavoriteController 纯适配器）+ PhysicalRecycleJobHandler 级联
- [ ] 前端 FavoritesPage/FavoriteButton + 详情页/列表挂载 + 路由/导航 + i18n zh/en
- [ ] 正常/边界/失败/越权/并发测试（Vitest + 后端单测/并发测试）
- [ ] Compose Fixture/Verify（E4 全链 + 撤销授权过滤 + 回收清理）
- [ ] 文档同步（work-breakdown、page-catalog 注记、EVD）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
治理        validate-spec / validate-task-card / -CheckChangedPaths        各 exit 0
backend/    ./mvnw.cmd -q verify                                            exit 0   全 AC 后端切片
frontend/   pnpm lint; pnpm typecheck; pnpm test; pnpm build                exit 0   组件层
frontend/   pnpm test:e2e（真实 Compose 栈，凭据注入同 066/067/068 口径）    exit 0   E4 主证
contracts/  redocly lint + 非破坏取证（§FM 替代路径）                       exit 0   契约非破坏
```

SKIP 判定：任何因 Docker/凭据缺失导致的跳过视为 notProven，不构成 PASS（既有口径）。

## 9. 停止条件

- 批准人将 `REQ-DST-FAV-001` 评审判定为拒绝、或裁定引入 `asset:favorite` 权限位/聚合计数 →
  停止回报（属规格变更，不在本卡擅改）；
- 开工时工作树存在与 allowedPaths 重叠且来源不明的变更（含 077/078 未提交面）→ 不得开工；
- 列表权限过滤无法在 SQL 谓词层实现（被迫内存过滤）→ 停止回报；
- 需要改动 ErrorCode 枚举、权限口径或任何 forbiddenPaths 才能推进 → 停止回报等待补正裁定。

## 10. 完成报告

```text
Completed:
Changed files:
Contract/database changes:
Validation run and results:
Validation not run and reasons:
Remaining risks or follow-ups:
```

（DRAFT 轮留空；实施完成后按实际证据填写，成果表述在人工验收前一律 IMPLEMENTED_UNVERIFIED。）

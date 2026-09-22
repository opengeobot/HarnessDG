---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-065
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 63f3d9a4135603a1b97aab36b42063fa482b0377
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「立卡整改层 0（D2+D4+D6）」，明确批准本卡触碰冻结区 frontend/）。依据 READY 需求 REQ-UI-001（公共管理端：页面不手写 URL、权限过滤按真实 Scope、Loading/Empty/Error/Retry/Success、zh-CN/en-US 运行时切换、关键页面有 Vitest 与浏览器 E2E）与 ACCEPTED 决策 DEC-013（Ant Design 企业后台基线、Compose 为功能验收环境、Playwright E2E），修复魔搭差距文档层 0 的六项已复核缺陷：审查页捏造数据、前端幽灵权限码 asset:write、/review 导航缺失、契约外幽灵端点封装、硬编码文案。不新增业务能力，符合 ADR-0002 的「公共底座接入与契约一致性整改」例外。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-002
  - AC-P0B-UI-003
  - AC-P0B-UI-005
  - AC-P0B-UI-006
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-002|E4|真实 Compose 栈：以显式持有不同 JWT scopes 的夹具用户登录 UI——持 asset:read 者看不到/进不了 /upload 与 /review，持 asset:manage 者在 asset:write 幽灵码修复后可进入上传中心与设置页（修复前被恒拒）；无 asset:publish 的主体提交审批决策仍被后端 403（UI 不是安全边界）
  - AC-P0B-UI-003|E4|真实 Compose 栈：审查页在空数据、后端错误、成功三种情形下 Empty/Error+Retry/Success 均可用；任何状态下页面不再出现捏造的 requestId、submittedBy、submittedAt
  - AC-P0B-UI-005|E4|真实 Compose 栈：运行时切换 zh-CN/en-US，notFound、guard、nav.review 及审查页新文案均按 locale 渲染；后端错误仍以稳定 code/i18nKey 渲染
  - AC-P0B-UI-006|E4|自动化双层：Vitest（E2 级组成）新建 ReviewPage.test.tsx 断言不渲染任何非会话内真实 requestId 的行、扩展 navigation.test.ts 断言 /review 入口存在且全站不再出现 asset:write 幽灵 scope；Playwright（真实 Compose 栈）覆盖夹具用户导航可见性随 scope 变化与审查闭环（提交→会话内真实 requestId→决策）
crossCuttingPlan:
  - AUTHN|不改变认证端点、Token 生命周期与重放防御语义；仅修复 AuthProvider 启动静默刷新缺少跨挂载并发去重的存量缺陷（见 §2 缺陷事实 7）——StrictMode 双挂载会并发发出两次 POST /auth/refresh，后端按重放整族吊销 refresh token，导致 E2E 整页加载后登录态间歇丢失。修复为模块级共享刷新 Promise，每次真实整页加载恰好刷新一次；失败仍走 clearSession，不放宽任何准入
  - AUTHZ|routes.tsx 两处与 navigation.ts 一处守卫由未注册的 asset:write 对齐到后端实际要求的 asset:manage（064 已把发布链路统一为 asset:manage，Permissions.java 从未注册 asset:write）；/review 导航项 requiredScopes 与既有路由守卫 asset:manage 一致；不放宽任何准入——无相应 Scope 的主体在前端不可见且直接请求仍被后端拒绝
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|不改变 VersionStatus/PublishRequestStatus 取值；审查页仅在真实状态（PENDING_REVIEW 可提交、本会话已知 requestId 可决策）上启用动作
  - IDEMPOTENCY|N/A - 沿用后端既有幂等实现，前端不改请求语义
  - CONSISTENCY|N/A - 不跨系统写入；仅移除前端对不存在端点的调用（transitionVersion/publishVersion 无后端映射，恒 404）
  - ERRORS|不新增错误码；审查页错误展示改为经统一 API 层的 code/i18nKey 渲染；403/404 防枚举语义不变
  - AUDIT|N/A - 不新增审计事件类型；写操作的审计由后端既有实现承担
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|NotFoundPage、RouteGuard 硬编码中文改为 i18n key（notFound.*、guard.*），新增 nav.review；zh.json/en.json 同步；删除随幽灵端点一并移除的 version.submitValidation/transitionFailed 键
  - CONFIG|导航与路由共用同一处权限元数据（navigation.ts 的 requiredScopes 与 routes.tsx 的 guarded scopes 必须一致），不引入新配置
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|N/A - 不接触 Secret；E2E 凭据一律取自环境变量（沿用 e2e/support.ts 惯例）；证据不得包含 Token 或口令
allowedPaths:
  - frontend/src/app/router/routes.tsx
  - frontend/src/app/router/NotFoundPage.tsx
  - frontend/src/app/permission/RouteGuard.tsx
  - frontend/src/app/auth/AuthProvider.tsx
  - frontend/src/app/layout/navigation.ts
  - frontend/src/app/layout/navigation.test.ts
  - frontend/src/features/version/ReviewPage.tsx
  - frontend/src/features/version/ReviewPage.test.tsx
  - frontend/src/features/version/api.ts
  - frontend/src/features/version/VersionPage.tsx
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-065.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend
  - contracts
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-065.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-065.md -CheckChangedPaths
  - cd frontend; pnpm lint
  - cd frontend; pnpm typecheck
  - cd frontend; pnpm test
  - cd frontend; pnpm build
  - cd frontend; pnpm test:e2e
approvedBy: User
approvedAt: 2026-09-22T10:00:00+08:00
---

# TASK-P0BR-065：审查页数据流真实化与导航权限口径修正（差距层 0）

> 状态：`READY`（用户已批准并授权实施，含触碰冻结区 frontend/）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-22

## 1. 目标

管理端 UI 不再展示捏造的审查数据、不再使用后端不存在的权限码与端点，`/review` 可经导航发现，占位与守卫文案完成 i18n——即魔搭差距文档（`docs/architecture/modelscope-page-gap-analysis-2026-09-21.md`）层 0 的 D2/D4/D6 与幽灵端点清理全部闭环。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-002, AC-P0B-UI-003, AC-P0B-UI-005, AC-P0B-UI-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核，全部有代码级定位）

1. **D2 捏造审查队列**：`frontend/src/features/version/ReviewPage.tsx` 的 `publishedRequests`
   以版本列表派生虚构行——`requestId: pub_${versionId}`、`frozenDigest: 'pending'`、
   `submittedBy: 'system'`、`submittedAt: new Date()`。后端契约（`ReviewController`）只有
   `POST /versions/{versionId}/publish-requests` 与 `POST /publish-requests/{requestId}/decisions`
   两个端点，**不存在发布请求列表/读取端点**，`VersionView` 亦无 requestId/frozenDigest/submittedBy
   字段——「审批队列列表」在当前契约下物理上不可实现，任何展示行必然是捏造。
2. **D4-1 幽灵权限码 asset:write**：后端 `Permissions.java` 只注册
   `asset:read/manage/delete/publish/discuss/moderate`，从未有 `asset:write`（TASK-P0BR-064
   已把发布链路统一到 `asset:manage`）。前端 `routes.tsx` 的 `/assets/:assetId/settings` 与
   `/upload` 守卫、`navigation.ts` 的 `/upload` 导航项却要求 `asset:write`，恒 403/不可见，
   把真实持有 `asset:manage` 的主体挡在页面外。
3. **D4-2 /review 无导航入口**：`routes.tsx` 已注册 `/review`（守卫 `asset:manage`），
   但 `navigation.ts` 的 `NAV_ITEMS` 无该项，审查页不可从侧边导航发现。
4. **幽灵端点封装**：`frontend/src/features/version/api.ts` 的 `transitionVersion`
   （POST `/versions/{id}/transition`）与 `publishVersion`（POST `/versions/{id}/publish`）
   无后端映射——`VersionController` 仅有 GET/POST `/assets/{assetId}/versions`、
   GET `/{versionId}`、GET `/{versionId}/artifacts`；真实的 DRAFT→VALIDATING 迁移发生在
   `UploadMaterializeJobHandler` 内部，不经 REST。`VersionPage.tsx` 的「提交校验」按钮调用
   恒 404 的端点（假动作）；`publishVersion` 全仓库零引用。
5. **D6 硬编码文案**：`NotFoundPage.tsx`（页面不存在/返回资产目录）与
   `RouteGuard.tsx`（正在恢复会话…/缺少访问权限/所需权限…/申请权限）为硬编码中文，
   违反 REQ-UI-001「zh-CN/en-US 运行时切换」。
6. **口径分裂（登记，不在本卡修复）**：前端权限判定唯一来源是 JWT scopes
   （`AuthProvider.tsx` L135），后端另有 RBAC 兜底（`AuthorizationService.java` L110-125）；
   admin 的 `/me` scopes 不含任何 `asset:*`，故 admin 在 UI 上看不到资产类页面而 REST 可达。
   正式修复需后端提供「有效权限」查询端点，属新能力，出本卡范围；本卡 E4 一律使用
   显式赋 scopes 的夹具用户（沿用 `frontend/e2e/support.ts` 既有惯例）。
7. **启动静默刷新无并发去重（E4 过程中发现的存量缺陷，用户批准并入本卡）**：
   `AuthProvider.tsx` 的 `setRefreshHandler` 路径用 `refreshingRef` 做了并发去重，但应用启动
   的静默刷新 `useEffect`（L107-129）没有——`main.tsx` 的 StrictMode 使该 effect 在 dev 下
   挂载→cleanup→再挂载，`cancelled` 标志只丢弃结果、不阻止请求发出，于是每次整页加载并发
   发出两次 `POST /auth/refresh` 携带同一 jti。后端
   `AuthenticationApplicationService.refresh()` 的 CAS 抢占 + 败者整族吊销是**有意的安全设计**
   （注释明言不引入可绕过重放检测的时间窗），非缺陷。结果：E2E 整页加载后登录态间歇丢失
   （实测 `--repeat-each=3` 为 1 过 2 败，后端日志 6 条 `refresh token replay detected,
   revoked family` WARN 与测试窗口吻合）。既有 `discoverability.spec.ts` 的同款
   `login→goto→断言` 模式一直暴露于该竞态，历史绿灯含运气成分。

## 3. 范围

### 允许修改

- `frontend/src/features/version/ReviewPage.tsx`：改造为「输入 assetId 查询真实版本列表 →
  仅对 PENDING_REVIEW 版本可提交发布请求 → 真实 requestId 保留在组件 state →
  决策面板只接受本会话已知 requestId」；删除全部派生/捏造行与虚构字段；空态展示诚实说明
  （无列表端点，仅本会话提交的请求可见）。
- `frontend/src/features/version/api.ts`：删除 `transitionVersion`、`publishVersion`。
- `frontend/src/features/version/VersionPage.tsx`：删除调用幽灵端点的「提交校验」按钮及其
  处理函数；下载等真实动作保留。
- `frontend/src/app/router/routes.tsx`：两处 `asset:write` → `asset:manage`。
- `frontend/src/app/layout/navigation.ts`：`/upload` 的 `asset:write` → `asset:manage`；
  新增 `/review` 导航项（`labelKey: nav.review`，`requiredScopes: ['asset:manage']`，
  与路由守卫一致）。
- `frontend/src/app/router/NotFoundPage.tsx`、`frontend/src/app/permission/RouteGuard.tsx`：
  硬编码文案改 i18n key（`notFound.*`、`guard.*`）。
- `frontend/src/shared/i18n/**`：新增 `notFound.*`、`guard.*`、`nav.review` 与审查页新文案
  的 zh-CN/en-US；移除随幽灵端点一并失效的 `version.submitValidation`、`version.transitionFailed`。
- `frontend/src/features/version/ReviewPage.test.tsx`（新建）、
  `frontend/src/app/layout/navigation.test.ts`（扩展）、`frontend/e2e/**`（新增本卡场景规格）。
- 契约：无变更（复用既有 publish-requests 两端点）；Migration：无；
  Compose Fixture/Verify：不改动 `deploy/`。

### 明确不在范围

- 不改 `AuthorizationService` 权限判定模型（口径分裂修复另卡）；`AuthProvider.tsx` 仅做启动
  静默刷新的跨挂载并发去重（缺陷事实 7），不改变认证端点、Token 生命周期或重放防御语义；
- 不新增后端列表端点或改变任何 OpenAPI 契约；
- 不做审查页/版本页的 Tailwind→Ant Design 整体改造（差距层 1，且属 UI 基线重构而非缺陷整改）;
- 不实现 `/reviews`、`/uploads` 的独立路由化或审批工作台完整体验（P1 范围）；
- 不改 `AccessPage.tsx`（其占位已是 i18n 化的非可点击 Empty，无冒充行为）。

### 禁止变化

- 不放宽任何守卫或后端准入：幽灵码修复只允许 `asset:write`→`asset:manage` 的同位替换；
- 不得以本地常量、mock 或随机数据填充审查列表——本卡的核心正是删除捏造；
- 不新增依赖；不触碰 backend/contracts/deploy。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-002` | 持 `asset:read`+`asset:manage` 的夹具用户 | 已登录 UI | 查看侧边导航 | 上传中心、审查页可见可进入（修复前被 asset:write 挡掉） | `E4` |
| `AC-P0B-UI-002` | 仅持 `asset:read` 的夹具用户 | 已登录 UI | 查看侧边导航并直接请求 /upload | 导航项不可见；直接访问被 RouteGuard 403 | `E4` |
| `AC-P0B-UI-002` | 持 `asset:manage` 无 `asset:publish` 的用户 | 本会话存在待决 requestId | 提交审批决策 | 前端可发起，后端 403（UI 不是安全边界，错误以稳定 code 渲染） | `E4` |
| `AC-P0B-UI-003` | 审查者 | 后端不可达或查询报错 | 打开 /review 查询版本 | Error 态 + Retry 可用 | `E4` |
| `AC-P0B-UI-003` | 审查者 | 资产无 PENDING_REVIEW 版本 | 打开 /review | 诚实 Empty（说明队列仅含本会话请求），无任何捏造行 | `E2/E4` |
| `AC-P0B-UI-003` | 持 `asset:manage` 的用户 | 存在 PENDING_REVIEW 版本 | 提交发布请求→对真实 requestId 做 APPLIED/REJECTED 决策 | Success 态展示后端返回的真实 requestId/状态 | `E4` |
| `AC-P0B-UI-005` | 任意已登录用户 | 切换 zh-CN/en-US | 访问 /review 导航项、404 页、403 页 | 全部文案按 locale 渲染 | `E4` |
| `AC-P0B-UI-006` | CI/审查者 | 运行 Vitest | ReviewPage.test.tsx、navigation.test.ts | 断言无捏造行渲染、/review 入口存在、全站无 asset:write | `E2` |
| `AC-P0B-UI-006` | CI/审查者 | 运行 Playwright（真实栈） | 本卡新增规格 | 导航可见性随 scope 变化 + 审查闭环通过 | `E4` |

必须覆盖正常、边界、失败、越权。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改认证路径；沿用既有 RouteGuard |
| Permission/Scope/ACL | 幽灵码同位替换为真实注册码；导航与路由共用同一口径；UI 过滤不替代后端拒绝 |
| 数据库权限下推 | N/A |
| 状态前置条件 | 仅 PENDING_REVIEW 版本可提交；决策仅对本会话真实 requestId 开放 |
| 幂等与并发 | N/A：复用后端既有幂等 |
| 事务与外部一致性 | 移除恒 404 的幽灵端点调用，前端动作全部对应真实映射 |
| 错误码 | 不新增；后端错误经统一 API 层以 code/i18nKey 渲染 |
| 审计事件 | N/A：写操作审计由后端既有实现承担 |
| 通知 | N/A |
| 字典/标签/i18n | 新文案 zh-CN/en-US 双份；删除失效键 |
| 配置 | navigation.ts 与 routes.tsx 权限元数据一致，无双份映射 |
| 指标/Trace/告警 | N/A |
| Secret/日志脱敏 | E2E 凭据取自环境变量；证据不含 Token/口令 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 变更：无——本卡使前端调用收敛到既有契约（两端点），并删除契约外调用；
2. 示例请求/响应：复用 `POST /versions/{versionId}/publish-requests` 与
   `POST /publish-requests/{requestId}/decisions` 既有形状；
3. 新增 Flyway：无；4. 存量清点与回填：无；5. 索引/约束：无；
6. 兼容窗口：纯前端收敛性修改，无对外兼容影响；
7. 生成类型更新：无。

## 7. 实施步骤

- [x] 契约及契约测试（无契约变更，N/A：前端收敛到 OpenAPI 既有两端点，删除契约外调用）
- [x] Migration（N/A）
- [x] Application/Domain（N/A，backend 冻结）
- [x] REST/MCP/Worker Adapter（N/A）
- [x] 前端页面与状态（D2 ReviewPage 数据流真实化；D4 routes/navigation 幽灵 asset:write→asset:manage
      + /review 入口；D6 NotFoundPage/RouteGuard i18n 化；api.ts/VersionPage 幽灵端点清理；
      AuthProvider 启动刷新跨挂载去重）
- [x] 正常/边界/失败/越权/恢复测试（E2：ReviewPage.test.tsx 4 项 + navigation.test.ts 2 项 +
      既有套件，Vitest 21 files/85 tests 全绿；E4：真实栈 Playwright 全量 36/36，
      含新增 review-workflow.spec.ts 8 条——导航逐项过滤、/review 守卫、诚实空态、
      zh/en locale（含 404 页正向）、后端 403/500 独立拒绝）
- [x] Compose Fixture/Verify（不改 deploy/：E2E 夹具全部经真实 REST 幂等置备，
      support.ts 修复目录分页挤出首屏的搜索路径）
- [x] Runbook/追踪矩阵（卡内 §10 + EVD-P0BR-065-001；work-breakdown 065 行已于立卡提交登记）

## 8. 验证命令

```text
cd frontend; pnpm lint
cd frontend; pnpm typecheck
cd frontend; pnpm test
cd frontend; pnpm build
cd frontend; pnpm test:e2e
```

每条命令声明：

- 预期退出码：全部 0；`pnpm test:e2e` 需要已启动的 Compose 栈，未启动时记 SKIP 并列入
  Evidence `limitations.skippedTests`，不得记 PASS；
- 覆盖 AC：`AC-P0B-UI-002/003/005/006`；
- 所需依赖：Node 20+、pnpm、Playwright 浏览器、真实 Compose 栈与显式赋 scopes 的夹具用户；
- SKIP 判定：无可用后端时本卡不得声明完成（E4 是最低等级）；
- 产物：`frontend/coverage`、Playwright 报告、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-065.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 需要后端新增端点（如发布请求列表）或改变权限模型才能完成断言——超出层 0 范围；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- Compose 栈不可用导致 E4 无法产生；
- 工作树存在与任务重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
  差距层 0 六项缺陷 + 扩卡竞态修复全部闭环（D2 审查页数据流真实化、D4 幽灵权限码
  asset:write→asset:manage 与 /review 导航入口、D6 NotFoundPage/RouteGuard 硬编码 i18n 化、
  契约外幽灵端点 transitionVersion/publishVersion 清理、AuthProvider 启动刷新跨挂载去重）。

Changed files:
  - frontend/src/features/version/ReviewPage.tsx / api.ts / VersionPage.tsx（D2 + 幽灵端点）
  - frontend/src/app/router/routes.tsx、layout/navigation.ts、permission/RouteGuard.tsx、
    router/NotFoundPage.tsx、auth/AuthProvider.tsx（D4 + D6 + 竞态）
  - frontend/src/shared/i18n/zh.json、en.json（notFound/guard/nav.review 双份，失效键删除）
  - frontend/src/features/version/ReviewPage.test.tsx（新增，E2 4 项）
  - frontend/src/app/layout/navigation.test.ts（065 新增 2 项守卫断言）
  - frontend/e2e/review-workflow.spec.ts（新增，E4 8 项）、e2e/support.ts（搜索路径修复）
  - docs/ai-spec/tasks/TASK-P0BR-065.md（本卡）、docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml

Requirements and scenarios satisfied:
  - REQ-UI-001 / AC-P0B-UI-006（E2+E4）：Vitest 21 files/85 tests、Playwright 全量 36/36
    （含新增 8 条）；导航可见性随 scope 逐项过滤；--repeat-each=3 21/21；
    重放 WARN/审计行修复后零新增（audit_log 59 行 max=02:00:16 UTC 止于修复前）
  - AC-P0B-UI-003（E2+E4）：E2 4 用例证明诚实态/仅 PENDING_REVIEW 开放/会话内真实
    requestId 闭环/失败重试；E4 证明可达、诚实空态、zh/en、后端 403/500 独立拒绝
  - AC-P0B-UI-005（E4）：/review、404 页、导航、403 页文案按 locale（中文残留归零断言）；
    既有 i18n.spec.ts 5 条在全量跑内复验；E2 双语键齐备
  - AC-P0B-UI-002（E4+E2）：manage 主体可见可入、无 manage 主体不可见且 /review 直连被拒、
    路由表与导航单一口径；卡面「无 asset:publish 决策 403」行因闭环断链不可达，
    已如实记 Evidence limitations（非 proven 项）

Contract/database changes:
  无。前端收敛到 OpenAPI 既有两端点（POST /versions/{id}/publish-requests、
  POST /publish-requests/{id}/decisions）；无 Flyway、无生成类型变更。

Validation run and results:
  - validate-spec.ps1 → PASS（36 文档/654 稳定 ID）
  - validate-task-card -TaskPath …065.md → PASS；-CheckChangedPaths → PASS（15 路径全命中）
  - pnpm lint / typecheck → exit 0；pnpm build → ✓ 20.98s exit 0
  - pnpm test → 21 files/85 tests 全过（136.37s；与 e2e 并行负载、默认 5s 超时下全过）
  - pnpm test:e2e（等价 playwright test，真实 Compose 栈 7 容器 healthy）→ 36 passed / 46.3s exit 0
  - -CheckCompletion → 按设计 EXPECTED_FAIL（review.accepted 须由人类评审者置 true，
    AI 不得自批，DEC-009；其余门禁项已按提交后终态填写）

Evidence artifacts:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml（E4，status PASS）
  - .trae/evidence/e2e-065-final.txt（36/36 终轮记录，gitignored 仓库内取证产物）
  - 数据库交叉验证：audit_log AUTH_TOKEN_REPLAY_REJECTED 修复后零新增（SQL 见 EVD）

Validation not run and reasons:
  - 后端 ./mvnw verify、OpenAPI 契约破坏检查：本卡 backend/contracts 冻结零变更，未运行
  - verify.ps1/deploy/compose：不改 deploy/，未运行（栈健康以 7 容器 Up + e2e 全绿实证）

Remaining risks or follow-ups:
  - 闭环断链属后端范围（本卡 backend 冻结，如实登记非本卡缺陷）：
    UPLOAD_MATERIALIZE/VERSION_VALIDATE 无入队方 → PENDING_REVIEW 运行态不可达、
    DRAFT 提交发布请求实测 500；审批正向 E4 链路待该后端卡后补验
  - ReviewController 裸 Map 契约化、前端有效权限口径统一（JWT scopes vs 后端判定）建议另立卡
  - vitest 默认 5s 超时对负载敏感（历史 2 次抖动，本轮全过）：建议统一超时/隔离并行的工程基线卡
  - nginx location /assets/ 遮蔽 SPA 深链（EVD-050 既有）：e2e 走主机 vite 5173 的口径不变
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。

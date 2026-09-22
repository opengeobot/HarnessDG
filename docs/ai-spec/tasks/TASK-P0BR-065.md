---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-065
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 2fe671690f255c940810268f88b8bc34546107b6
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
  - AUTHN|N/A - 不改变认证路径与 Token 生命周期；沿用既有 RouteGuard 与 AuthProvider
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

- 不改 `AuthProvider.tsx` / `AuthorizationService` 权限判定模型（口径分裂修复另卡）；
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

- [ ] 契约及契约测试（无契约变更，N/A）
- [ ] Migration（N/A）
- [ ] Application/Domain（N/A，backend 冻结）
- [ ] REST/MCP/Worker Adapter（N/A）
- [ ] 前端页面与状态（D2/D4/D6/幽灵端点）
- [ ] 正常/边界/失败/越权/恢复测试（E2 + E4）
- [ ] Compose Fixture/Verify（不改 deploy/，E2E 夹具经 support.ts）
- [ ] Runbook/追踪矩阵（卡内 §10 + EVD）

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
Changed files:
Requirements and scenarios satisfied:
Contract/database changes:
Validation run and results:
Evidence artifacts:
Validation not run and reasons:
Remaining risks or follow-ups:
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。

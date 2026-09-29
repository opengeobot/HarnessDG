---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-066
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: ff2e3886d307d5f7a430993aa606515f6834ca5a
stageGatePassed: true
stageGateEvidence: 缺陷事实来自 TASK-P0BR-065 §11.2/§11.5 的 E4 复核（真实 Compose 栈复跑，V01-V28 与 94 项 e2e 全绿），
  其修复位置均落在 065 的 allowedPaths 之外（features/admin、features/access、features/auth、app/router）。
  2026-09-28 逐项复核已定稿 D-01/D-05/D-07/D-08/D-09/D-10 的代码位置与 OpenAPI `x-required-permissions` 权威口径，
  并由用户在 TASK-P0BR-066 批准问句中明确选择「全部批准」，解除 065 遗留的范围外阻塞。
  同轮实地复核修正两处卡内事实错误：(a) 后端 GET /api/v1/tokens 的 fail-closed 已在服务层成立
  （PatApplicationService.listOwnPats 调 requirePermission(Permissions.TOKEN_CREATE)，与契约 listPats 的
  [token:create] 一致），OrganizationController.listMembers 亦已要求 ORGANIZATION_MANAGE 且与契约一致，
  故 D-09/D-10 为纯前端可见性口径收敛，本卡不含任何后端改动；(b) D-02（/profile 登录名）需要
  OpenAPI CurrentPrincipal 新增字段 + 后端视图改动，而 contracts 属本卡 forbiddenPaths，
  经用户在 2026-09-28 批准问句中选定「本卡移除 D-02，另立契约优先新卡」，D-02 移交后续契约先行 Task Card，
  其 e2e 断言在本轮保持 test.fixme 锁定。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-UI-001
  - REQ-AUTH-001
acceptanceScenarios:
  - AC-P0B-UI-003
  - AC-P0B-UI-006
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-003|E4|真实 Compose 栈：用户管理重置口令后列表行状态/时间即时回显（D-01）；通知中心对仅持
    notification:read 的主体展示「我的通知」成功态且不渲染「系统管理」Tab，对持 system:observe 的主体
    该 Tab 出成功表格而非错误态（D-08）；组织列表对无 organization:manage 的主体不渲染成员管理入口（D-10）
  - AC-P0B-UI-006|E4|真实 Compose 栈 Playwright 扩展 admin-pages.spec，断言上述行为；越权用例断言
    /access 口径统一为 token:create 后，无 authorization:read 但持 token:create 的主体正常取数、
    无 token:create 的主体仍被后端 403 拒绝；Vitest 覆盖 D-05 路由表去重后每个 admin 路径唯一注册
crossCuttingPlan:
  - AUTHN|不改认证端点、Token 生命周期与 /me 载荷；D-02（登录名展示）已移交后续契约先行 Task Card，本卡不动
  - AUTHZ|D-08/D-09/D-10 属前端可见性口径收敛：前端门控必须与契约 `x-required-permissions`
    及后端 requirePermission 同一口径（listPats=[token:create]、listOrganizationMembers=[organization:manage]）；
    后端两处校验经复核已存在，本卡严禁任何放宽后端权限的改动
  - DB_FILTER|N/A - 不改任何查询与权限下推条件
  - STATE|不改变 VersionStatus/UserStatus/NotificationStatus 取值
  - IDEMPOTENCY|沿用后端既有实现；D-01 为纯前端失效刷新
  - CONSISTENCY|N/A - 不跨系统写入
  - ERRORS|不新增错误码；D-08 若 Tab 隐藏后仍收到 403，须以统一 code/i18nKey 渲染
  - AUDIT|N/A - 不新增审计事件类型
  - NOTIFICATION|D-08 影响通知中心展示口径，不触发新通知发送
  - TAXONOMY_I18N|D-07 使 admin.alerts.readonlyHint 生效或删除该死键；zh.json/en.json 同步
  - CONFIG|D-05 路由去重后，navigation.ts requiredScopes 与 routes.tsx guarded scopes 必须仍一致
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|N/A - 不接触 Secret；E2E 凭据取自环境变量，证据不得含 Token 或口令
allowedPaths:
  - frontend/src/features/admin/pages/UsersPage.tsx
  - frontend/src/features/admin/pages/NotificationsPage.tsx
  - frontend/src/features/admin/pages/NotificationsPage.test.tsx
  - frontend/src/features/admin/pages/AlertsPage.tsx
  - frontend/src/features/admin/pages/OrganizationsPage.tsx
  - frontend/src/features/admin/pages/OrganizationsPage.test.tsx
  - frontend/src/features/access/AccessPage.tsx
  - frontend/src/app/router/routes.tsx
  - frontend/src/app/router/routes.test.tsx
  - frontend/src/app/layout/navigation.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-066.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml
preExistingDirtyPaths:
  - docs/architecture/iteration-plan-2026-09-24.md|dc5f2e2137261cdd9fedafaa9b4daf327da27830c157ba1ec9f52b64dada155b
  - build-logs/verify-bom.ps1|b5003338d283e84b4b6dabaf0d9e47b6d90fe18b780d0fd2e59851d467ac9b3d
forbiddenPaths:
  - contracts
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-066.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-066.md -CheckChangedPaths
  - cd frontend; pnpm lint
  - cd frontend; pnpm typecheck
  - cd frontend; pnpm exec vitest run --pool=threads --testTimeout=20000 --no-file-parallelism
  - cd frontend; pnpm build
  - cd frontend; pnpm test:e2e
approvedBy: User
approvedAt: 2026-09-28T08:15:23Z
---

# TASK-P0BR-066：管理端权限口径与状态刷新缺陷整改（065 复核产物）

> 状态：`READY`（用户已批准「全部批准」，见 Front Matter `approvedBy/approvedAt` 与 `stageGateEvidence`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-28

## 1. 目标

管理端与访问页的前端可见性、数据刷新与后端真实权限口径一致：重置口令后列表即时回显、
无 `system:observe` 的主体不再撞上 403 错误态、PAT 列表与组织成员入口的三层口径统一到契约声明、
重复路由清除。本卡为**纯前端**整改，不含后端与契约改动。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001, REQ-AUTH-001]
acceptanceScenarios: [AC-P0B-UI-003, AC-P0B-UI-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [listPats, listOrganizationMembers]   # 仅作口径权威来源，本卡不改契约
mcpTools: []
eventSchemas: []
flywayBaseline: V43
```

### 缺陷事实（全部由 TASK-P0BR-065 §11.2/§11.5 在真实 Compose 栈上复核；2026-09-28 逐项实地复核修正）

- **D-01**：`frontend/src/features/admin/pages/UsersPage.tsx` L114-123 `resetMutation.onSuccess`
  是该文件 5 个 mutation 中唯一漏 `void invalidate()` 的一处。
- **D-02**（**本卡不处理**）：`ProfilePage.tsx` L72 渲染 `principal?.subject`，而后端
  `AuthenticationApplicationService.toPrincipalView` L385-391、`currentPrincipal` L324、
  `PrincipalQueryApplicationService` L35 三处一律以 principalId 填充 subject。
  正解需要 OpenAPI `CurrentPrincipal` 新增可展示用户名字段 + 后端视图/payload 改动；
  `contracts` 属本卡 forbiddenPaths，且该契约改动会强制 redocly lint+diff
  （diff 子命令在 @redocly/cli 各版本均不存在，见 `EVD-P0BR-049-001.yaml` 既存工具链缺陷记录）。
  经用户 2026-09-28 决定：移交后续契约先行 Task Card，`frontend/e2e/profile.spec.ts` L84 的
  `test.fixme` 在本轮保持锁定。
- **D-05**：`frontend/src/app/router/routes.tsx` L80/L88 与 L81/L89 重复注册
  `admin/role-bindings`、`admin/resource-acls`，后两条为死代码。
- **D-07**：`admin.alerts.readonlyHint`（`zh.json`）在 `AlertsPage.tsx` 无任何引用。
- **D-08**：`NotificationsPage.tsx` L34-48 Tab 项无 `system:observe` 判定，
  L148-166 三个 admin query 无 `enabled` 门控。
- **D-09**：三层口径不一致——契约 `GET /tokens` 的 `operationId: listPats` 声明
  `x-required-permissions: [token:create]`；后端 fail-closed 已在**服务层**成立
  （`PatApplicationService.listOwnPats` 调 `authorizationService.requirePermission(Permissions.TOKEN_CREATE)`，
  控制器层不再重复校验，符合「适配器不含业务规则」的分层规则）；前端取数门控也用 `token:create`
  （`AccessPage.tsx` L49/L54）。唯一偏差是前端路由与导航守卫用了 `authorization:read`
  （`routes.tsx` L66、`navigation.ts` L27）。
  **因此本卡正解是把前端守卫统一到 `token:create`，不含任何后端改动。**
  （065 起草时误记为「后端 `GET /api/v1/tokens` 无 requirePermission」，实地复核已推翻该表述。）
- **D-10**：契约 `listOrganizationMembers` 声明 `x-required-permissions: [organization:manage]`，
  后端 `OrganizationController.listMembers` L102-106 亦要求 `ORGANIZATION_MANAGE`（两层已一致）；
  偏差仅在 `OrganizationsPage.tsx` L125-127 的成员管理入口对任何 `project:view` 主体无条件渲染。
  **正解为前端按 `organization:manage` 门控，不含后端改动。**

## 3. 范围

### 允许修改

- 模块：前端 admin/access 特性包页面、应用路由表与导航权限元数据、i18n 文案。
- 契约：无改动。OpenAPI 仅作为权限口径的权威来源被读取核对；本卡结论是「前端对齐契约」，
  契约本身已正确声明 `listPats=[token:create]`、`listOrganizationMembers=[organization:manage]`。
- 后端：无改动。复核确认 `PatApplicationService.listOwnPats` 与 `OrganizationController.listMembers`
  的 fail-closed 校验均已存在且与契约一致；本卡 `allowedPaths` 不含任何 `backend/` 路径。
- Migration：无。
- 页面：Users / Notifications / Alerts / Organizations / Access。
- Fixture/Verify：`frontend/e2e` 内扩展既有用例，不改 `deploy/compose`。

### 明确不在范围

- 新增业务能力、新增页面、字典/标签治理模型改动。
- D-02（`/profile` 登录名展示 username）：需 OpenAPI `CurrentPrincipal` 新增字段 + 后端视图改动，
  移交后续契约先行 Task Card；其 e2e 断言保持 `test.fixme` 锁定。
- D-03（资产详情不单独渲染 namespace/name）：属可接受设计，不在本卡处理。
- D-04（antd tags Enter 语义）：已在 065 测试侧规避并记录，不在本卡处理。

### 禁止变化

- 不改变登录、刷新、吊销的 Token 生命周期语义，也不改变 `/me` 载荷结构。
- 不改变任何状态枚举取值。
- 不允许通过放宽后端权限注解、移除校验或跳过断言使测试变绿；本卡的前端口径一律**向契约收紧**
  （`authorization:read` → `token:create`），不得反向放宽契约或删除 `test.fixme` 之外的断言。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-003` | 用户管理员 | 用户列表已加载 | 重置某用户口令成功 | 该行状态/时间立即回显，无需手动刷新 | `E4` |
| `AC-P0B-UI-003` | 仅 notification:read 的主体 | 打开通知中心 | 页面渲染 | 不出现「系统管理」Tab，「我的通知」为成功态，无 `.ant-result-error` | `E4` |
| `AC-P0B-UI-003` | 持 system:observe 的主体 | 打开通知中心并切「系统管理」 | 表格渲染 | 出真实表格，非错误态 | `E4` |
| `AC-P0B-UI-003` | 无 organization:manage 的主体 | 打开组织列表 | 表格渲染 | 行内不出现成员管理入口；后端成员端点仍返回 403 | `E4` |
| `AC-P0B-UI-003` | 持 organization:manage 的主体 | 打开组织列表 | 表格渲染 | 成员管理入口可见且抽屉出成功态 | `E4` |
| `AC-P0B-UI-006` | 无 token:create 的主体 | 直连 `GET /api/v1/tokens` | 请求发出 | 后端 403（fail-closed，UI 非安全边界） | `E4` |
| `AC-P0B-UI-006` | 持 token:create 无 authorization:read 的主体 | 打开 /access | 页面渲染 | 页面可进入且 PAT 列表取数成功（守卫口径已统一） | `E4` |
| `AC-P0B-UI-006` | 任意主体 | 构建路由表 | 单测断言 | 每个 admin 路径在 `routes.tsx` 中唯一注册 | `E4` |

须覆盖正常、边界、失败、越权四类路径。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 不改认证链路、Token 生命周期与 `/me` 载荷；D-02 已移交契约先行卡 |
| Permission/Scope/ACL | D-08/D-09/D-10 前端门控与契约 `x-required-permissions` 及后端 requirePermission 同口径 |
| 数据库权限下推 | N/A - 本卡不改任何查询条件或权限下推 |
| 状态前置条件 | 无枚举变更 |
| 幂等与并发 | N/A - 写动作沿用既有 Idempotency-Key |
| 事务与外部一致性 | N/A - 不跨系统 |
| 错误码 | 不新增；错误仍按 code/i18nKey 渲染 |
| 审计事件 | N/A - 由后端既有实现承担，本卡不触后端 |
| 通知 | D-08 仅改展示门控 |
| 字典/标签/i18n | D-07 生效或删除死键，zh/en 同步 |
| 配置 | 路由与导航权限元数据保持单一来源（`navigation.ts` 与 `routes.tsx` 同步为 `token:create`） |
| 指标/Trace/告警 | N/A |
| Secret/日志脱敏 | 不接触 Secret；证据不含 Token/口令 |

## 6. 契约与数据先行

1. 已核对 OpenAPI：`GET /tokens` 的 `operationId` 是 `listPats`（065 起草时误记为 `listTokens`），
   其 `x-required-permissions: [token:create]`；`listOrganizationMembers` 为 `[organization:manage]`。
   两者均已与后端实现一致，故 D-09/D-10 是「前端补齐契约」而非「变更契约」。
2. 无请求/响应结构变更；本卡不动 `/me`（D-02 属后续契约卡的响应体新增可选字段议题）。
3. 无 Flyway 迁移。
4. 存量数据无需回填。
5. 无索引/约束变更。
6. 兼容窗口：仅收紧前端可见性；现有夹具主体（admin 持 `admin:all`/全部 scope）均满足新口径。
7. 生成类型：无变更。

## 7. 实施步骤

- [x] 契约与后端口径核对（OpenAPI `x-required-permissions` 对 `PatApplicationService`/`OrganizationController`）
- [x] 卡内事实修正（两处错误后端路径、`listTokens`→`listPats`、D-09/D-10 服务层校验、D-02 移交）
- [x] 前端 D-01（UsersPage 补 invalidate）— E4：`user-management.spec.ts` 重置口令后行内状态/时间即时回显
- [x] 前端 D-05（routes.tsx 去重 + Vitest 唯一注册断言）— E2：`routes.test.tsx` 唯一注册 + 导航/守卫 scope 一致
- [x] 前端 D-07（`admin.alerts.readonlyHint` 生效，zh/en 同步）— E4：`admin-pages.spec.ts:157` 用例内断言提示文案可见
- [x] 前端 D-08（NotificationsPage Tab 与 admin query 按 `system:observe` 门控）— E4 双向：`admin-pages.spec.ts:204`（无 scope 不渲染）/ `:181`（有 scope 出成功表格）
- [x] 前端 D-09（/access 路由与导航守卫统一到 `token:create`）— E4 双向：`admin-pages.spec.ts:296`（直连 403）/ `:311`（仅持 token:create 正常取数）
- [x] 前端 D-10（OrganizationsPage 成员管理入口按 `organization:manage` 门控）— E4 + E2：`admin-pages.spec.ts:99/:80` 与 `OrganizationsPage.test.tsx`
- [x] Vitest 与 Playwright 扩展（`test.fixme` 仅保留 D-02 一处）— 全量 Vitest 217/217、Playwright 97 passed / 1 skipped（skip 即 D-02 fixme）
- [x] 全量回归与 Evidence Manifest — `docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml`（status PASS，交接态）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

## 8. 验证命令

```text
# 治理校验（仓库根，PowerShell）
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-066.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-066.md -CheckChangedPaths
# 前端（frontend/）
pnpm lint; pnpm typecheck
pnpm exec vitest run --pool=threads --testTimeout=20000 --no-file-parallelism
pnpm build
# E2E 需真实 Compose 栈 + E2E_* 环境变量，见 TASK-P0BR-065 §11.1
pnpm test:e2e
```

- 预期退出码：0。
- AC 覆盖：lint/typecheck/vitest/build → `AC-P0B-UI-006`（路由唯一注册、类型与口径一致性）；
  `pnpm test:e2e` → `AC-P0B-UI-003` 全部行为切片 + `AC-P0B-UI-006` 越权项。
- 后端 `./mvnw verify` **不在本卡范围**：本卡不含任何 `backend/` 改动，后端权限行为由既有
  测试与 065 的 E4 证据覆盖，e2e 越权断言直接对真实后端发出生效请求即为其回归证明。
- 依赖：Compose 全栈（7 个容器 Up）、`E2E_ADMIN_USERNAME/PASSWORD` 等夹具变量。
- SKIP 判定：Docker 不可用导致 e2e 跳过时，本卡**不得**声明 E4 完成；因工作树含未忽略取证产物导致 `-CheckChangedPaths` 报产物路径时，须逐条核对实际变更文件仍全部命中 `allowedPaths`。
- 产物：`frontend/playwright-report/`、`frontend/test-results/`（不入库，终态取证后删除）；`build-logs/*-066-*.log`（`*.log` 已 gitignore）；`docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml`。
- `build-logs/verify-bom.ps1`（09-24 既存取证脚本，非 `*.log` 故不被忽略）已按校验器要求登记进 Front Matter `preExistingDirtyPaths` 并附 SHA-256。

## 9. 停止条件

- 实施中发现契约与后端实现不一致（而非前端与两者不一致），须停止并上报为契约缺陷；
- 需要修改已应用 Migration 或 V1/V2；
- 需要放宽任何权限、校验或断言；
- D-02 在实施中被误纳入（其修复必须走契约先行卡）；
- 无法产生 E4 证据；
- 工作树存在与本卡重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
纯前端六项（D-01/D-05/D-07/D-08/D-09/D-10）全部实施并取证；D-02 按用户决策移出本卡范围，
移交契约先行新卡。本卡不含任何 backend/contracts/deploy/migration 改动。

Changed files:
frontend/src/features/admin/pages/UsersPage.tsx           D-01 重置口令成功回调补 invalidate，列表行状态/时间即时回显
frontend/src/app/router/routes.tsx                        D-05 admin 路由去重（重复注册项收敛为单一 guarded 注册）
frontend/src/app/router/routes.test.tsx                   D-05 新增：唯一注册 + 导航/守卫 scope 口径一致断言
frontend/src/features/admin/pages/AlertsPage.tsx          D-07 admin.alerts.readonlyHint 由死键变为页面渲染（zh/en 键早已对称存在，核对后 i18n 文件零改动）
frontend/src/features/admin/pages/NotificationsPage.tsx   D-08 「系统管理」Tab 与其 admin query 按 system:observe 门控
frontend/src/features/admin/pages/NotificationsPage.test.tsx  D-08 E2 双向门控
frontend/src/app/layout/navigation.ts                     D-09 /access 导航 requiredScopes → token:create
frontend/src/features/admin/pages/OrganizationsPage.tsx   D-10 成员管理入口按 organization:manage 门控
frontend/src/features/admin/pages/OrganizationsPage.test.tsx  D-10 E2 门控与「列表仍出数」对照
frontend/e2e/support.ts                                   新增 expectAssetLinkViaSearch helper；补注册 E2E_NOTIFICATION_READER 夹具主体
frontend/e2e/admin-pages.spec.ts                          D-07 文案断言 + D-08/D-09/D-10 双向门控用例
frontend/e2e/dataset.spec.ts                              版本回退用例按 G6 语义（仅回退 PUBLISHED，否则空占位）重写
frontend/e2e/asset.spec.ts                                两处目录断言改用搜索 helper（同 050/065 既有约定）
frontend/e2e/user-management.spec.ts                      D-01 回归口径同步
docs/ai-spec/tasks/TASK-P0BR-066.md, docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml
docs/ai-spec/02-delivery/work-breakdown.md                 候选表校正 066 范围（D-02 移出）并登记后继契约卡 TASK-P0BR-067

Requirements and scenarios satisfied:
REQ-UI-001 / REQ-AUTH-001；AC-P0B-UI-003、AC-P0B-UI-006 均由 EVD-P0BR-066-001 coverage.proven 证明。

Contract/database changes:
无。仅核对既有 OpenAPI `x-required-permissions`（listPats=[token:create]、
listOrganizationMembers=[organization:manage]）并让前端补齐同一口径；无请求/响应结构变更、
无 Flyway 迁移、无生成类型变更。

Validation run and results:
./docs/ai-spec/tools/validate-spec.ps1                                   PASS（148 documents / 726 stable IDs）
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath ...TASK-P0BR-066.md PASS
同上 -CheckChangedPaths                                                   PASS（终态清理产物后复跑，EXIT=0）
cd frontend; pnpm lint                                                    EXIT=0（2 条既存 react-refresh 警告，0 error）
cd frontend; pnpm typecheck                                               EXIT=0
cd frontend; pnpm exec vitest run ...                                     49 files / 217 tests 全绿（1445.94s）→ E2
cd frontend; pnpm build                                                   EXIT=0（3257 modules / 33.82s）
cd frontend; pnpm test:e2e（真实 Compose 栈，7 容器 Up/healthy）
                                                                          98 tests / 97 passed / 1 skipped / 0 failed，
                                                                          3.0m，EXIT=0 → E4
收敛过程如实记录：首轮全量 1 failed（asset.spec.ts:139 恢复后断言，历史夹具挤出首屏 limit=10），
以仓内既有约定抽 helper 后复跑转绿；D-07 断言新增后再跑一次全量，结果同口径复现。

Evidence artifacts:
docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml（status PASS，evidenceLevel E4）
build-logs/{e2e-066-final,e2e-066,vitest-066,fe-build-066,fe-lint-066-final,
            fe-typecheck-066-final,governance-066,e2e-066-full-fail}-2026-09-28.log

Validation not run and reasons:
- ./mvnw verify、docker compose verify.sh：本卡零后端/零部署改动，不在 Front Matter
  requiredValidationCommands 内；后端权限行为由既有测试与 065 的 E4 证据覆盖，
  本卡 e2e 越权断言直接对真实后端发出生效请求即为回归证明。
- ./docs/ai-spec/tools/validate-task-card.ps1 -CheckCompletion：按设计必然 FAIL——
  门禁要求 dirtyWorktree=false（代码须先提交为干净 Commit）、coverage.notProven 与
  limitations.skippedTests 为空、且 review.accepted 由验证权威置 true（AI 不得自批，DEC-009）。
  前两项在本卡语境下受 D-02 fixme（早于 baseCommit 的 5ccbf47 引入，删除即等同放宽断言）约束，
  须由 D-02 契约先行卡闭环；故本卡只申报 IMPLEMENTED + E4 证据齐备的交接态，不申报完成态。

Remaining risks or follow-ups:
1. D-02（/profile 登录名回显 username）需契约先行卡：OpenAPI CurrentPrincipal 新增可选字段 +
   后端视图/payload；contracts 属本卡 forbiddenPaths。其 e2e 断言保持 test.fixme。
2. contracts 改动触发的 redocly breaking-change diff 有既存工具链缺陷，契约卡须以人工逐行核对替代并留痕。
3. nginx 的 location /assets/ 静态规则遮蔽 SPA 深链（既有基础设施缺陷，见 EVD-P0BR-050-001），
   故 e2e 仍走主机 Vite 5173、API 经 nginx 8080；属基础设施后续项，不影响本卡权限/写路径的真实性。
4. 验收栈历史夹具持续累积会不断把新夹具挤出目录首屏；仓内既有约定是「先页内搜索再断言」，
   根治需夹具清理或隔离机制（平台侧后续项）。
5. vitest serial 全量耗时 1445.94s（217 tests），单文件超时对 CPU 负载敏感；
   测试执行编排优化属工程基线后续项。
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。

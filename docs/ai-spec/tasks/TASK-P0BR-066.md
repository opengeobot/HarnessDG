---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-066
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: aa0a8a7289b1184f3182fda8c865b2297158aa62
stageGatePassed: false
stageGateEvidence: 待人工批准。缺陷事实来自 TASK-P0BR-065 §11.2/§11.5 的 E4 复核（真实 Compose 栈复跑），
  其修复位置均落在 065 的 allowedPaths 之外（features/admin、features/access、features/auth、app/router、backend），
  且 050/061 等既有含 features/admin 的卡 objective 与本批缺陷无关，不得扩用。
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
    该 Tab 出成功表格而非错误态（D-08）；/profile 显示可辨识登录名（D-02）
  - AC-P0B-UI-006|E4|真实 Compose 栈 Playwright 扩展 admin-pages.spec 与 profile.spec，断言上述三项行为；
    越权用例断言 D-09 口径统一后无 authorization:read 主体仍被后端拒绝；Vitest 覆盖 D-05 路由表去重后
    每个 admin 路径唯一注册
crossCuttingPlan:
  - AUTHN|不改认证端点与 Token 生命周期；D-02 仅在 /me 载荷增加可展示的用户名（或改 subject 语义），
    不改变 principal 判定来源
  - AUTHZ|D-08/D-09/D-10 属权限口径收敛：前端可见性与后端 requirePermission 必须同一口径；
    严禁以放宽后端权限（例如给 GET /tokens 继续留空）换取测试通过——D-09 的正解是补齐后端 requirePermission
  - DB_FILTER|D-02 如需按 principalId 反查 username，沿用既有 PrincipalQueryApplicationService，不新增越权查询
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
  - frontend/src/features/admin/pages/AlertsPage.tsx
  - frontend/src/features/admin/pages/OrganizationsPage.tsx
  - frontend/src/features/auth/ProfilePage.tsx
  - frontend/src/features/access/AccessPage.tsx
  - frontend/src/app/router/routes.tsx
  - frontend/src/app/layout/navigation.ts
  - frontend/src/shared/i18n
  - frontend/e2e
  - backend/src/main/java/com/aihub/platform/security/api/PatController.java
  - backend/src/main/java/com/aihub/identity/api/OrganizationController.java
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-066.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml
preExistingDirtyPaths:
  - docs/architecture/iteration-plan-2026-09-24.md|dc5f2e2137261cdd9fedafaa9b4daf327da27830c157ba1ec9f52b64dada155b
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
  - cd backend; ./mvnw verify
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-066：管理端权限口径与状态刷新缺陷整改（065 复核产物）

> 状态：`DRAFT`（AI 起草，未获批准，**不得实施**）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-28

## 1. 目标

管理端的权限可见性、数据刷新与身份展示与后端真实权限口径一致：重置口令后列表即时回显、
无 `system:observe` 的主体不再撞上 403 错误态、PAT 列表三层口径统一且后端 fail-closed、
`/profile` 展示可辨识登录名、重复路由清除。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001, REQ-AUTH-001]
acceptanceScenarios: [AC-P0B-UI-003, AC-P0B-UI-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [listTokens, listOrganizationMembers, getCurrentPrincipal]
mcpTools: []
eventSchemas: []
flywayBaseline: V43
```

### 缺陷事实（全部由 TASK-P0BR-065 §11.2/§11.5 在真实 Compose 栈上复核）

- **D-01**：`frontend/src/features/admin/pages/UsersPage.tsx` L117-121 `resetMutation.onSuccess`
  是该文件 5 个 mutation 中唯一漏 `void invalidate()` 的一处。
- **D-02**：`ProfilePage.tsx` L72 渲染 `principal?.subject`，而后端
  `AuthenticationApplicationService.toPrincipalView` L387-388、`currentPrincipal` L324、
  `PrincipalQueryApplicationService` L35 三处一律以 principalId 填充 subject。
- **D-05**：`frontend/src/app/router/routes.tsx` L80/L88 与 L81/L89 重复注册
  `admin/role-bindings`、`admin/resource-acls`，后两条为死代码。
- **D-07**：`admin.alerts.readonlyHint`（`zh.json`）在 `AlertsPage.tsx` 无任何引用。
- **D-08**：`NotificationsPage.tsx` L34-47 Tab 项无 `system:observe` 判定，
  L148-166 三个 admin query 无 `enabled` 门控。
- **D-09**：前端路由要 `authorization:read`（`routes.tsx` L66）、前端取数要 `token:create`
  （`AccessPage.tsx` L49/L54）、后端 `GET /api/v1/tokens`（`PatController` L44-48）无 requirePermission。
- **D-10**：组织行 `project:view` 即可见，但成员抽屉数据源
  `OrganizationController.listMembers` L102-106 要求 `ORGANIZATION_MANAGE`。

## 3. 范围

### 允许修改

- 模块：前端 admin/access/auth 特性包、应用路由表、i18n；后端 PAT 与组织成员两个只读端点的权限声明。
- 契约：无破坏性变更（D-09 为补齐既有 OpenAPI 中已声明的 security 要求；须先核对契约再改）。
- Migration：无。
- 页面：Users / Notifications / Alerts / Organizations / Profile / Access。
- Fixture/Verify：`frontend/e2e` 内扩展既有用例，不改 `deploy/compose`。

### 明确不在范围

- 新增业务能力、新增页面、字典/标签治理模型改动。
- D-03（资产详情不单独渲染 namespace/name）：属可接受设计，不在本卡处理。
- D-04（antd tags Enter 语义）：已在 065 测试侧规避并记录，不在本卡处理。

### 禁止变化

- 不改变登录、刷新、吊销的 Token 生命周期语义。
- 不改变任何状态枚举取值。
- 不允许通过放宽后端权限注解、移除校验或跳过断言使测试变绿；D-09/D-10 若需产品决策
  （PAT 列表究竟以哪个权限为准、成员抽屉是否降级为公开信息），必须停止并上报，不得自行选定。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-003` | 用户管理员 | 用户列表已加载 | 重置某用户口令成功 | 该行状态/时间立即回显，无需手动刷新 | `E4` |
| `AC-P0B-UI-003` | 仅 notification:read 的主体 | 打开通知中心 | 页面渲染 | 不出现「系统管理」Tab，「我的通知」为成功态，无 `.ant-result-error` | `E4` |
| `AC-P0B-UI-003` | 持 system:observe 的主体 | 打开通知中心并切「系统管理」 | 表格渲染 | 出真实表格，非错误态 | `E4` |
| `AC-P0B-UI-003` | 已登录用户 | 打开 /profile | 页面渲染 | 显示可辨识登录名而非 principalId | `E4` |
| `AC-P0B-UI-006` | 无 authorization:read 的主体 | 直连 `GET /api/v1/tokens` | 请求发出 | 后端 403（fail-closed，UI 非安全边界） | `E4` |
| `AC-P0B-UI-006` | 任意主体 | 构建路由表 | 单测断言 | 每个 admin 路径在 `routes.tsx` 中唯一注册 | `E2` |

须覆盖正常、边界、失败、越权四类路径。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 不改认证链路；D-02 仅补展示字段 |
| Permission/Scope/ACL | D-08/D-09/D-10 三层口径对齐，前端可见性与后端 requirePermission 一致 |
| 数据库权限下推 | D-02 反查 username 沿用既有查询服务，不下推新条件 |
| 状态前置条件 | 无枚举变更 |
| 幂等与并发 | N/A - 写动作沿用既有 Idempotency-Key |
| 事务与外部一致性 | N/A - 不跨系统 |
| 错误码 | 不新增；错误仍按 code/i18nKey 渲染 |
| 审计事件 | N/A - 由后端既有实现承担 |
| 通知 | D-08 仅改展示门控 |
| 字典/标签/i18n | D-07 生效或删除死键，zh/en 同步 |
| 配置 | 路由与导航权限元数据保持单一来源 |
| 指标/Trace/告警 | N/A |
| Secret/日志脱敏 | 不接触 Secret；证据不含 Token/口令 |

## 6. 契约与数据先行

1. 核对 OpenAPI 中 `listTokens`、`listOrganizationMembers` 的既有 security 声明，确认 D-09 是「实现补齐契约」而非「变更契约」。
2. 无请求/响应结构变更；若 D-02 需给 `/me` 增加字段，属响应体新增可选字段，须先更新 OpenAPI 再生成类型。
3. 无 Flyway 迁移。
4. 存量数据无需回填。
5. 无索引/约束变更。
6. 兼容窗口：`/me` 新增字段向后兼容；PAT 端点加权限为收紧，须确认现有夹具主体均满足。
7. 生成类型：随 `/me` 字段变更同步。

## 7. 实施步骤

- [ ] 契约核对（OpenAPI security 与 `/me` 字段）
- [ ] 后端 D-09 权限补齐 + 越权测试
- [ ] 前端 D-01/D-02/D-05/D-07/D-08 修复
- [ ] D-10 决策落地（须先取得产品口径）
- [ ] Vitest 与 Playwright 扩展
- [ ] 全量回归与 Evidence Manifest

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
# 后端（backend/）
./mvnw verify
```

- 预期退出码：0。
- AC 覆盖：lint/typecheck/vitest/build → `AC-P0B-UI-006`；`pnpm test:e2e` → `AC-P0B-UI-003` + 越权项；`./mvnw verify` → D-09 后端。
- 依赖：Compose 全栈（7 个容器 Up）、`E2E_ADMIN_USERNAME/PASSWORD` 等夹具变量。
- SKIP 判定：Docker 不可用导致 e2e 跳过时，本卡**不得**声明 E4 完成；因工作树含未忽略取证产物导致 `-CheckChangedPaths` 报产物路径时，须逐条核对实际变更文件仍全部命中 `allowedPaths`。
- 产物：`frontend/playwright-report/`（不入库）、`docs/ai-spec/tasks/evidence/EVD-P0BR-066-001.yaml`。

## 9. 停止条件

- D-09 或 D-10 需要产品决策（权限口径取舍）而契约未明；
- 需要修改已应用 Migration 或 V1/V2；
- 需要放宽任何权限、校验或断言；
- 无法产生 E4 证据；
- 工作树存在与本卡重叠且来源不明的变更。

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

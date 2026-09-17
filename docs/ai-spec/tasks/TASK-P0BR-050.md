---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-050
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: 67a0ec14230c41f8996272eefa6384f88df01c4b
stageGatePassed: false
stageGateEvidence: 待用户批准。拟以 READY 需求 REQ-UI-001 与 ACCEPTED 决策 DEC-013 作为阶段依据；本卡为 P0-B 公共管理端可发现性与死链整改（PAGE-ADM-004/008/009 与 PAGE-AST-* 导航可达性），不新增业务能力。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-003
  - AC-P0B-UI-006
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-003|E4|浏览器在真实 Compose 栈上分别制造空数据与后端错误，断言角色绑定、资源 ACL 与版本列表页面的 Loading/Empty/Error/Retry/Success 五种状态均有可用交互
  - AC-P0B-UI-006|E4|浏览器 E2E：从侧边导航可依次发现并打开 Team、角色绑定、资源 ACL 三个入口；点击版本列表「查看全部」到达真实页面而非全局 404
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；新增路由沿用既有 RouteGuard
  - AUTHZ|新入口必须声明与页面一致的真实 Scope（authorization:read / project:view），不得默认全 Scope 或放宽准入
  - DB_FILTER|N/A - 不涉及资产查询过滤或授权下推
  - STATE|N/A - 不改变任何稳定状态枚举
  - IDEMPOTENCY|N/A - 只读导航与列表页面，无写副作用
  - CONSISTENCY|N/A - 不跨系统写入
  - ERRORS|页面保留 Loading/Empty/Error/Retry/Success；403/404 沿用防枚举语义，不回显未授权资源名称
  - AUDIT|N/A - 不新增审计事件类型
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|导航标题以 i18nKey 存储并在渲染时求值；新增文案具备 zh-CN/en-US
  - CONFIG|导航与路由必须共用同一受控 Route Catalog 元数据，禁止维护两份权限映射
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|N/A - 不接触 Secret；证据不得包含凭据或 Token
allowedPaths:
  - frontend/src/app/layout/navigation.ts
  - frontend/src/app/router/routes.tsx
  - frontend/src/features/admin
  - frontend/src/features/version/VersionListPanel.tsx
  - frontend/src/shared/i18n
  - frontend/e2e
  - docs/ai-spec/tasks/TASK-P0BR-050.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-050-001.yaml
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR-050-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-050.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-050.md -CheckChangedPaths
  - cd frontend; pnpm lint
  - cd frontend; pnpm typecheck
  - cd frontend; pnpm test
  - cd frontend; pnpm build
  - cd frontend; pnpm test:e2e
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-050：管理端页面可发现性与版本入口死链修复

> 状态：`DRAFT`（待用户批准后改为 `READY`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

管理端已实现的能力可以从侧边导航被发现，且资产详情页的版本入口不再指向未注册路由而落到全局 404。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-003, AC-P0B-UI-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核）

1. **死链**：`frontend/src/features/version/VersionListPanel.tsx` 的「查看全部版本」链接到
   `/assets/${assetId}/versions`，但 `frontend/src/app/router/routes.tsx` 只注册了
   `/assets/:assetId/versions/:versionId`，点击后落入 `path: '*'` 的 `NotFoundPage`。

2. **能力不可发现**：`page-catalog.md` 已明确记录
   「当前路由缺少 role-bindings/resource-acls/teams 的独立可发现入口；"存在 API"不能替代管理体验。」
   - `/admin/organizations/:orgId/teams` 已注册路由，但 `navigation.ts` 中
     `/admin/organizations` 无子项，`AppLayout` 的 `Menu` 无法展开该路径；
   - 角色绑定与资源 ACL 的前端 API 函数已存在
     （`frontend/src/features/admin/api.ts` 的 `listRoleBindings/createRoleBinding/deleteRoleBinding`
     与 `listResourceAcls/createResourceAcl/deleteResourceAcl`），但没有对应页面与路由。

## 3. 范围

### 允许修改

- 模块：`frontend/src/app`、`frontend/src/features/admin`、`frontend/src/features/version`、
  `frontend/src/shared/i18n`
- 文件或目录：
  - `frontend/src/app/layout/navigation.ts`（把已注册路由补充为可发现的导航子项）
  - `frontend/src/app/router/routes.tsx`（新增角色绑定与资源 ACL 路由；修正版本列表入口）
  - `frontend/src/features/admin/**`（角色绑定与资源 ACL 页面及其组件测试）
  - `frontend/src/features/version/VersionListPanel.tsx`（「查看全部」指向真实可达路由）
  - `frontend/src/shared/i18n/**`（新增文案的 zh-CN/en-US）
  - `frontend/e2e/**`（导航可发现性与死链回归场景）
- 契约：无变更（复用既有 `/system/role-bindings`、`/system/resource-acls` 端点）
- Migration：无
- 页面：`PAGE-ADM-004`、`PAGE-ADM-008`、`PAGE-ADM-009`、`PAGE-VER-001`
- Fixture/Verify：不改动 `deploy/compose`

### 明确不在范围

- 不实现 `/assets/:id/overview`、`/assets/new`、`/assets/:id/discussions`、`/assets/:id/lineage`
  等资产详情外壳重构（属 `TASK-P1-008`/`TASK-P1-011`）；
- 不实现 `/reviews`、`/uploads`、`/notifications` 的独立路由化，`/version`、`/review`、
  `/upload`、`/access` 的扁平占位形态在本卡保持现状；
- 不为角色绑定与资源 ACL 新增后端端点或改变其权限、幂等与错误语义；
- 不实现讨论 Moderator（隐藏/恢复/锁定）UI；
- 不把导航重构为跨页共享的 Route Catalog 抽象（仅在同一处维护元数据，避免两份映射）。

### 禁止变化

- 不改变的 API/状态/权限：所有既有端点形状、Scope 要求与错误码；
- 不允许的依赖升级或重构：不新增依赖；
- 不允许的兼容破坏：不得把「查看全部」指向一个仍不可达的路由；不得为了可发现性放宽页面 Scope。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-006` | 有 `asset:read` 的用户 | 资产存在多个版本 | 在资产详情点击版本列表「查看全部」 | 到达可用的版本列表页面，不回落到全局 404 | `E4` |
| `AC-P0B-UI-006` | 有 `project:view` 的用户 | 组织存在 | 展开侧边导航的管理中心 | Team 入口可见并可进入 | `E4` |
| `AC-P0B-UI-006` | 有 `authorization:read` 的用户 | 存在角色绑定 | 打开角色绑定页面 | 列表可读、可创建、可删除；字段使用受控选择器 | `E4` |
| `AC-P0B-UI-006` | 有 `authorization:read` 的用户 | 存在资源 ACL | 打开资源 ACL 页面 | 列表可读、可创建、可删除；资源类型受控 | `E4` |
| `AC-P0B-UI-003` | 审查者 | 数据为空或后端报错 | 打开上述页面 | Loading/Empty/Error/Retry/Success 均有可用交互 | `E2` |
| `AC-P0B-UI-006` | 无 `authorization:read` 的用户 | 已登录 | 尝试直接访问角色绑定/资源 ACL 路由 | 前端展示 403；后端仍然拒绝（UI 不是安全边界） | `E2/E4` |
| `AC-P0B-UI-003` | 审查者 | 导航被按权限过滤 | 无相应 Scope 的主体登录 | 对应导航项不展示；直接访问仍由 RouteGuard 与后端拒绝 | `E2/E4` |

必须覆盖正常、边界、失败、越权。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；新增路由沿用既有 `RouteGuard` |
| Permission/Scope/ACL | 新入口声明真实 Scope，不放宽准入；UI 过滤不替代后端拒绝 |
| 数据库权限下推 | N/A：不涉及查询过滤 |
| 状态前置条件 | N/A：不改变稳定状态枚举 |
| 幂等与并发 | N/A：只读列表与受控写操作复用既有幂等实现 |
| 事务与外部一致性 | N/A：不跨系统写入 |
| 错误码 | 保留既有稳定错误码与 i18nKey 渲染；403/404 防枚举 |
| 审计事件 | N/A：不新增审计事件类型，写操作沿用后端既有审计 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | 新增导航与页面文案具备 zh-CN/en-US；受控值仍只提交 code/id |
| 配置 | 导航与路由共用同一处权限元数据，禁止维护两份映射 |
| 指标/Trace/告警 | N/A：不新增指标或告警 |
| Secret/日志脱敏 | N/A：不接触 Secret；证据不得包含凭据或 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口，复用既有 `/system/role-bindings` 与 `/system/resource-acls`；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：无数据变更；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：新增路由与导航项为纯增量；修正死链不改变任何既有可用路由；
7. 生成类型更新：无。

## 7. 实施步骤

- [ ] 契约及契约测试
- [ ] Migration 及空库/升级迁移测试
- [ ] Application/Domain 用例
- [ ] REST/MCP/Worker Adapter
- [ ] 前端页面与状态
- [ ] 正常/边界/失败/越权/恢复测试
- [ ] Compose Fixture/Verify
- [ ] Runbook/追踪矩阵

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 修正版本列表入口：使「查看全部」指向已注册路由（新增版本列表路由或改为资产详情的版本区锚点，
   取不引入未实现能力的最小方案）；
2. 新增角色绑定与资源 ACL 页面，复用既有前端 API 函数与受控选择器组件；
3. 在 `navigation.ts` 与 `routes.tsx` 中补齐 Team、角色绑定、资源 ACL 的可发现入口；
4. 补充 zh-CN/en-US 文案；
5. 新增组件测试覆盖四种页面状态与权限过滤；
6. 在真实 Compose 栈上以两个权限不同的主体执行浏览器 E2E。

## 8. 验证命令

```text
cd frontend; pnpm lint
cd frontend; pnpm typecheck
cd frontend; pnpm test
cd frontend; pnpm build
cd frontend; pnpm test:e2e
```

每条命令声明：

- 预期退出码：全部 0；`pnpm test:e2e` 需要已启动的后端（Compose 或本地 dev），未启动时记为 SKIP
  并在 Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-UI-003`、`AC-P0B-UI-006`；
- 所需依赖：Node 20+、pnpm、Playwright 浏览器；E4 需要可用 Compose 栈与真实授权数据；
- SKIP 判定：无可用后端时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`frontend/coverage`、Playwright 报告、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-050-001.yaml`。

路径触发强制命令：修改 frontend 必须包含 `pnpm lint/typecheck/test/build`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-050.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 `page-catalog.md` 要求的入口与当前路由形态无法在不越阶段的前提下对齐）；
- 存在未关闭的产品决策（例如角色绑定/资源 ACL 页面的管理权限是否需补决策）；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- 无法产生任务要求的最低证据等级；
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

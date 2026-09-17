---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-041
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 6bdbd51cee45fac9752c5723701ba86b7af270b3
stageGatePassed: true
stageGateEvidence: 用户批准（2026-09-17）以 READY 需求 REQ-UI-001 与 ACCEPTED 决策 DEC-013 作为本任务阶段依据
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-005
  - AC-P0B-TAX-006
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-005|E4|Playwright 在 zh/en 切换后触发后端错误的页面与错误文案截图及断言
  - AC-P0B-TAX-006|E4|切换语言后菜单、按钮、字典、错误、通知文案的浏览器验证与硬编码扫描报告
crossCuttingPlan:
  - AUTHN|N/A - 语言切换不影响认证会话与令牌存储方式
  - AUTHZ|N/A - 不改变权限判定；菜单过滤仍按 Scope 生效且与语言无关
  - DB_FILTER|N/A - 前端不承载数据库查询
  - STATE|语言状态经唯一入口切换并持久化；刷新后保持，不产生半切换的 UI/antd 不一致
  - IDEMPOTENCY|N/A - 切换为本地 UI 状态变更，无服务端写副作用
  - CONSISTENCY|antd ConfigProvider、i18next 与请求 Accept-Language 三者必须同步，不出现文案与请求语言不一致
  - ERRORS|后端错误按稳定 i18nKey 渲染当前语言；缺 key 时安全回退到 code 并保持可观测
  - AUDIT|N/A - 语言切换不是业务审计事件
  - NOTIFICATION|N/A - 不触发通知；仅消费既有通知文案 key
  - TAXONOMY_I18N|本任务核心：zh-CN/en-US 双语文案齐备，导航标签改为 key 驱动以响应切换，不新增未翻译运营文案
  - CONFIG|语言偏好持久化键与默认语言需固定；不新增后端部署配置
  - OBSERVABILITY|缺失翻译需可观测（沿用既有信号），不上报用户内容或令牌
  - SECRETS|语言偏好不得写入包含令牌的存储；access JWT 仍只驻留内存
allowedPaths:
  - frontend/src/shared/i18n
  - frontend/src/shared/components
  - frontend/src/app/layout
  - frontend/src/app/providers
  - frontend/src/features/auth
  - frontend/e2e
  - docs/ai-spec/tasks/TASK-P0BR-041.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-041-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend
  - deploy
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-041-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md -CheckChangedPaths
  - cd frontend; pnpm lint
  - cd frontend; pnpm typecheck
  - cd frontend; pnpm test
  - cd frontend; pnpm build
  - cd frontend; pnpm test:e2e
approvedBy: User
approvedAt: 2026-09-17T01:12:43Z
---

# TASK-P0BR-041：前端运行时 zh-CN/en-US 切换与错误文案按 key 渲染

> 状态：`DRAFT`
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-16

## 1. 目标

已登录用户在界面内切换 zh-CN/en-US 后，导航、按钮、antd 组件文案与后端错误提示立即以所选语言渲染，
刷新后保持该语言，且请求的 `Accept-Language` 与所选语言一致。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-005, AC-P0B-TAX-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

所有需求必须是 `READY`，所有决策必须是 `ACCEPTED`。否则任务不得进入实施。
正文 ID 必须与 Front Matter 完全一致。AI 可以起草 DRAFT，但不得填写批准人并授权自身实施。
`stageGateEvidenceRefs` 只能引用本 Task 已声明的 Accepted Decision 或仓库内真实 Evidence 文件。
`scenarioEvidencePlan` 必须以 `AC-ID|E1-E5|预期产物` 逐条覆盖 Front Matter 的全部 AC。
`crossCuttingPlan` 使用模板中的 13 个稳定机器键；每项必须写具体要求，或写 `N/A -` 加理由。

## 3. 范围

### 允许修改

- 模块：`shared/i18n`、`shared/components`、`app/layout`、`app/providers`、`features/auth`（登录页入口）
- 文件或目录：
  - `frontend/src/shared/i18n/**`（初始化、切换与持久化、资源补齐）
  - `frontend/src/shared/components/**`（语言切换组件及其导出）
  - `frontend/src/app/layout/**`（Header 入口、导航 key 化）
  - `frontend/src/app/providers/**`（antd locale 与语言状态联动）
  - `frontend/src/features/auth/**`（未登录页可用入口）
  - `frontend/e2e/**`
- 契约：无变更；请求头 `Accept-Language` 由前端按所选语言设置，不改后端接口签名
- Migration：无
- 页面：`PAGE-AUTH-*`、`PAGE-COM-*` 的公共外壳；不改业务页面布局
- Fixture/Verify：仅新增/修正前端 E2E 与组件测试

### 明确不在范围

- 不实现后端字典 i18n 查询端点（若实施中发现该端点缺失，须作为独立任务提出，不在本卡内联）；
- 不实现 `TASK-P0BR-014`、`TASK-P0BR-017` 的后端权限整改；
- 不新增第三种语言，不重构 i18n 资源命名空间结构；
- 不改变认证、权限与令牌存储方式；
- 不修改 `frontend/src/features/**` 下业务页面的展示逻辑（仅允许补齐缺失翻译 key）。

### 禁止变化

- 不改变的 API/状态/权限：认证与权限判定、菜单 Scope 过滤规则；
- 不允许的依赖升级或重构：不新增 i18n 检测/持久化依赖（如 i18next-browser-languagedetector），沿用 i18next 与 antd 既有能力；
- 不允许的兼容破坏：不改变既有 `Accept-Language` 与 `X-Request-Id` 头的存在性，不移除任何既有翻译 key。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-TAX-006` | USER | 已登录，默认 zh-CN | 切换为 en-US | 菜单、按钮、antd 分页/日期文案同步变英文，无残留中文硬编码 | `E4` |
| `AC-P0B-TAX-006` | USER | 已切换为 en-US | 刷新页面 | 仍为 en-US；请求头 `Accept-Language` 与之匹配 | `E4` |
| `AC-P0B-UI-005` | USER | 已切换为 en-US | 触发后端错误（如无权限或校验失败） | 错误按稳定 i18nKey 渲染英文，不显示原始中文或堆栈 | `E4` |
| `AC-P0B-UI-005` | USER | 未登录 | 访问登录页并切换语言 | 登录页文案随之切换，不依赖登录态 | `E4` |
| `AC-P0B-UI-005` | USER | 已切换语言 | 打开管理页面并观察导航 | 导航标签随语言变化，不出现模块加载时固化的旧语言文案 | `E4` |

必须覆盖正常、边界、失败、越权；涉及异步/并发时还须覆盖恢复和并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：语言切换不读取或改变 Principal，也不改变令牌驻留方式 |
| Permission/Scope/ACL | N/A：切换语言不改变可见菜单集合；权限过滤结果必须与语言无关 |
| 数据库权限下推 | N/A：前端不做数据过滤 |
| 状态前置条件 | 语言状态经单一入口设置并持久化；刷新、路由跳转后保持一致；antd locale 与 i18next 不得出现分叉 |
| 幂等与并发 | N/A：无服务端写副作用；连续切换须以最后一次选择为准，不出现竞态残留 |
| 事务与外部一致性 | antd ConfigProvider、i18next 与请求头三者同步；不做跨系统写入 |
| 错误码 | 错误渲染以 `ApiError.i18nKey` 为输入；缺失 key 时回退显示 code 并保留可观测信号，不改错误码语义 |
| 审计事件 | N/A：语言偏好不属于业务审计事件 |
| 通知 | N/A：不发送通知；仅确保既有通知文案 key 在两种语言下齐备 |
| 字典/标签/i18n | 核心：补齐 zh/en 文案，导航标签改为 key 驱动；不新增未翻译的运营文案；不把稳定枚举改为字典项 |
| 配置 | 固定持久化键名与默认语言；不新增后端部署配置，不引入远程文案服务 |
| 指标/Trace/告警 | 沿用既有缺失翻译可观测信号；不得上报用户输入内容或令牌 |
| Secret/日志脱敏 | 语言偏好不得与令牌写入同一存储；access JWT 仍仅驻留内存；E2E 产物不含凭据 |

任何一项若不适用，填写 `N/A` 并说明理由；不得留空。

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；仅请求头取值随语言变化；
2. 示例请求、响应和错误：补充"en-US 下后端错误响应按 i18nKey 渲染"的示例；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：清点 `zh.json` 与 `en.json` 的 key 差异，补齐缺失并在报告中给出差异结论；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：语言切换失败时保持上一有效语言，不清空翻译资源；
7. 生成类型更新：无。

禁止修改已应用 Migration。

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

## 8. 验证命令

```text
cd frontend; pnpm lint
cd frontend; pnpm typecheck
cd frontend; pnpm test
cd frontend; pnpm build
cd frontend; pnpm test:e2e
```

每条命令声明：

- 预期退出码：全部 0；`pnpm test:e2e` 需要已启动的前端与后端（Compose 或本地 dev），未启动时记为 SKIP
  并写入 Evidence `limitations`，不得将 E4 场景记为 PASS；
- 覆盖的 AC ID：`AC-P0B-UI-005`、`AC-P0B-TAX-006`；
- 所需依赖：Node 20+、pnpm 9+、Playwright 浏览器、可访问的后端与数据库；
- SKIP 判定：后端不可用时 i18n 错误渲染无法验证，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`frontend/playwright-report`、`docs/ai-spec/tasks/evidence/EVD-P0BR-041-001.yaml`。

精确命令同时写入 Front Matter `requiredValidationCommands`；至少包含三条治理校验和一条本任务
专用测试/验收命令。正文描述不能替代机器字段。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；修改 frontend 必须包含
`pnpm lint/typecheck/test/build`；修改 OpenAPI 必须包含 lint 和 blocking diff；修改 Compose 必须
包含 config 与仓库 verify 脚本。任务领取前如果项目还没有这些命令，先建立并批准测试基线，不能
用构建命令替代行为测试。

实施前固定执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md -CheckChangedPaths
```

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-041.md -CheckCompletion
```

`-CheckCompletion` 要求工作树干净、Evidence Commit 等于当前 HEAD、每条 AC 均有足够等级的 PASS
证据、没有 SKIP/notProven、已完成 Secret 检查并由验证责任方接受。未通过时最多交接为
`IMPLEMENTED_UNVERIFIED`。

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突；
- 存在未关闭的产品决策；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更。

**已知待处理项**：现有 `frontend/e2e/i18n.spec.ts` 的断言为条件式（`if (hasSwitcher)`、恒真断言），
不能作为 `AC-P0B-UI-005` 的证据。本任务必须将其改写为无条件断言；不得以减少断言的方式让测试通过。

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

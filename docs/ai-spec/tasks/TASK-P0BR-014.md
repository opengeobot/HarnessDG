---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-014
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 6bdbd51cee45fac9752c5723701ba86b7af270b3
stageGatePassed: true
stageGateEvidence: 用户批准（2026-09-17）以 READY 需求 REQ-AGT-001 与 ACCEPTED 决策 DEC-009 作为本任务阶段依据
stageGateEvidenceRefs:
  - DEC-009
requirements:
  - REQ-AGT-001
acceptanceScenarios:
  - AC-P0B-AGT-005
  - AC-P0B-ENG-006
  - AC-DST-AIW-002
decisions:
  - DEC-009
scenarioEvidencePlan:
  - AC-P0B-AGT-005|E4|MCP 客户端提交未知与未授权 Tool 名称的拒绝响应与拒绝审计记录
  - AC-P0B-ENG-006|E3|Profile 与适配层扫描报告证明无空 Principal 放行分支与 anonymous 降级
  - AC-DST-AIW-002|E4|默认只读 Agent 调用写工具的 tools/list 与 call 双重拒绝证据
crossCuttingPlan:
  - AUTHN|MCP 适配层不得在缺少已认证 Principal 时继续执行；无 Principal 一律拒绝并记录请求 ID
  - AUTHZ|每次 tools/call 必须先经 requireToolAllowed；取消 null 分支，未通过即拒绝
  - DB_FILTER|N/A - MCP 适配层不直接查询数据库，授权集合由 AuthorizationService 与既有仓储提供
  - STATE|校验 Agent 与工具处于 ACTIVE/允许状态，禁用后不得继续调用
  - IDEMPOTENCY|N/A - 只读工具调用无持久化副作用，不引入幂等键
  - CONSISTENCY|N/A - 不跨系统写入，无 Saga/Outbox/补偿需要
  - ERRORS|拒绝使用 MCP_TOOL_NOT_ALLOWED 或统一凭据错误，不返回工具存在性与白名单细节
  - AUDIT|未授权与未知工具调用写入拒绝审计（AGENT_ACCESS_DENIED），不记录 JWT 或凭据原文
  - NOTIFICATION|N/A - 拒绝路径不触发业务通知
  - TAXONOMY_I18N|N/A - 不新增受控值；错误以稳定 code 返回，文案由前端 i18nKey 渲染
  - CONFIG|读取既有配置键，不新增部署配置；不得用开关默认放行
  - OBSERVABILITY|拒绝计数与日志保留 tool 名与 requestId，不得记录 Secret
  - SECRETS|不使用 anonymous 等字面量伪造身份；日志与证据不得出现 JWT、credential、预签名 URL
allowedPaths:
  - backend/src/main/java/com/aihub/mcp
  - backend/src/test/java/com/aihub/mcp
  - contracts/mcp/tools.yaml
  - docs/ai-spec/tasks/TASK-P0BR-014.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-014-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - deploy
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-014-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T01:12:43Z
---

# TASK-P0BR-014：MCP Tool 调用在缺少已认证主体时失败关闭

> 状态：`DRAFT`
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-16

## 1. 目标

MCP 客户端在未携带有效平台 JWT、或调用未知/未授权 Tool 时，`tools/call` 一律被受控拒绝并产生拒绝审计，
不再出现"无主体即跳过白名单校验"或把主体降级为字面量 `anonymous` 的放行路径。

## 2. 关联规格

```yaml
requirements: [REQ-AGT-001]
acceptanceScenarios: [AC-P0B-AGT-005, AC-P0B-ENG-006, AC-DST-AIW-002]
decisions: [DEC-009]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: [asset_search, asset_get, asset_list_versions, asset_get_version]
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

- 模块：`com.aihub.mcp.api`、`com.aihub.mcp.application`
- 文件或目录：
  - `backend/src/main/java/com/aihub/mcp/api/McpController.java`
  - `backend/src/main/java/com/aihub/mcp/application/McpToolCatalog.java`
  - `backend/src/main/java/com/aihub/mcp/application/McpResourceHandler.java`
  - `backend/src/test/java/com/aihub/mcp/**`
  - `contracts/mcp/tools.yaml`
- 契约：MCP Tool 白名单与认证约束声明（仅澄清失败关闭语义，不新增或删除 Tool）
- Migration：无
- 页面：无
- Fixture/Verify：仅新增 MCP 拒绝路径测试，不改动 `deploy/compose` 脚本

### 明确不在范围

- 不新增或删除 MCP Tool，不改变既有只读工具的入参与语义；
- 不修改 `SecurityConfiguration` 的端点白名单（`/mcp` 保持 `authenticated()`）；
- 不实现 `TASK-P0BR-017` 的资产详情防枚举与可见性下推；
- 不实现 Agent 注册、凭据轮换、Scope 委派（属 `REQ-AGT-001` 其余验收场景）；
- 不改变 REST 适配层的既有授权路径。

### 禁止变化

- 不改变的 API/状态/权限：既有 REST 授权路径、`AuthorizationService` 判定语义、Tool 所需 Scope/Permission；
- 不允许的依赖升级或重构：不新增 MCP/安全相关依赖，不做与本任务无关的包结构调整；
- 不允许的兼容破坏：不改变已发布 Tool 的名称、参数 Schema 与成功响应结构。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AGT-005` | AGENT | Agent 已认证且工具不在白名单 | 调用 `tools/call` 提交该 Tool | 返回 `MCP_TOOL_NOT_ALLOWED` 语义的拒绝，不执行工具，产生拒绝审计 | `E3/E4` |
| `AC-P0B-AGT-005` | AGENT | 调用提交未在 Tool Catalog 注册的名称 | 调用 `tools/call` | 以受控 Tool Catalog 校验并拒绝；不得创建自由工具值 | `E3/E4` |
| `AC-P0B-ENG-006` | 验证方 | 已完成适配层修改 | 扫描 MCP 适配层与可部署 Profile | 不存在空 Principal 放行分支、`anonymous` 身份降级或默认全 Scope | `E1/E3` |
| `AC-DST-AIW-002` | AGENT | 默认只读 Agent 已认证 | 提交写工具（`asset_publish_version` / `asset_delete`） | `tools/list` 不暴露或 `call` fail closed；无资产副作用 | `E4` |

必须覆盖正常、边界、失败、越权；涉及异步/并发时还须覆盖恢复和并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | `tools/call` 与 `resources/read` 缺少已认证 Principal 时必须拒绝；不得以字面量替代主体；`tools/list` 按主体与写工具开关过滤 |
| Permission/Scope/ACL | 保留 `requireToolAllowed` 为唯一白名单入口，取消 null 守卫使其对每次调用生效 |
| 数据库权限下推 | N/A：MCP 适配层不直接访问 Mapper，授权集合由 `AuthorizationService` 实时计算 |
| 状态前置条件 | Agent 被禁用或工具被关闭时调用必须失败关闭，不依赖 JWT 内旧状态 |
| 幂等与并发 | N/A：只读工具无持久化写副作用，不引入 Idempotency-Key |
| 事务与外部一致性 | N/A：不写业务库、不调用 Gitea/DVC/MinIO，无跨系统一致性要求 |
| 错误码 | 未知/未授权 Tool 使用 `MCP_TOOL_NOT_ALLOWED`；错误文本不含白名单查询条件或内部堆栈 |
| 审计事件 | 未授权调用写 `AGENT_ACCESS_DENIED`，含 principal、tool、result=DENIED，不含凭据原文 |
| 通知 | N/A：拒绝路径不触发站内通知或 Webhook |
| 字典/标签/i18n | N/A：不新增受控值；仅返回稳定 code 与可选 i18nKey |
| 配置 | 复用既有写工具开关，默认关闭；不得以配置项默认放行 null 主体 |
| 指标/Trace/告警 | 拒绝次数与 Tool 名进入既有指标/日志，关联 requestId/traceId |
| Secret/日志脱敏 | 日志与测试 Fixture 不得出现 JWT、credential、预签名 URL；拒绝响应不回显凭据 |

任何一项若不适用，填写 `N/A` 并说明理由；不得留空。

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：`contracts/mcp/tools.yaml` 明确"缺少已认证主体即拒绝"和 `failClosed: true` 的适用范围；Tool 集合与参数不变；
2. 示例请求、响应和错误：新增未授权 `tools/call` 的拒绝响应示例（错误码 + 不泄露工具存在性）；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：无数据变更；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：拒绝路径为纯收紧，不改变成功响应的结构；
7. 生成类型更新：若前端/MCP 客户端类型由契约生成，需同步重新生成并纳入本任务。

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
cd backend; ./mvnw.cmd verify
npx @redocly/cli lint contracts/openapi/aihub-v1.yaml
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；若本机 Docker 不可用，`verify.ps1` 与依赖真实依赖的 IT 记为 SKIP 并在 Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-AGT-005`、`AC-P0B-ENG-006`、`AC-DST-AIW-002`；
- 所需依赖：JDK 21、Maven Wrapper 离线仓库、Node/pnpm、Docker Compose（E4 需要）；
- SKIP 判定：Docker 守护进程不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、`docs/ai-spec/tasks/evidence/EVD-P0BR-014-001.yaml`。

精确命令同时写入 Front Matter `requiredValidationCommands`；至少包含三条治理校验和一条本任务
专用测试/验收命令。正文描述不能替代机器字段。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；修改 frontend 必须包含
`pnpm lint/typecheck/test/build`；修改 OpenAPI 必须包含 lint 和 blocking diff；修改 Compose 必须
包含 config 与仓库 verify 脚本。任务领取前如果项目还没有这些命令，先建立并批准测试基线，不能
用构建命令替代行为测试。

实施前固定执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md -CheckChangedPaths
```

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-014.md -CheckCompletion
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

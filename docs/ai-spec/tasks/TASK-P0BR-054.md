---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-054
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: a3f45152fce3701dfa2a7018eb251d868d21fb24
stageGatePassed: false
stageGateEvidence: 待用户批准。依据 READY 需求 REQ-IDM-001（写接口幂等，P0-B）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），移除与设计冲突且使摘要比对在 HTTP 层不可达的全局幂等拦截器；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-IDM-001
acceptanceScenarios:
  - AC-P0B-IDM-001
  - AC-P0B-IDM-002
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-IDM-002|E4|在真实 Compose 栈上以同一 Idempotency-Key 提交不同请求体：HTTP 层返回 409 IDEMPOTENCY_KEY_CONFLICT，第二请求无业务副作用，既有记录未被覆盖
  - AC-P0B-IDM-001|E4|同键同请求体重放仍返回首次状态码与响应体且无重复副作用（确认移除拦截器未破坏复用语义）
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；幂等作用域继续由既有 Principal 提供 principalId
  - AUTHZ|本卡正面约束：拦截器移除后，原先被它「顺带」拦截的端点将不再校验幂等键。必须逐端点确认权限断言仍由既有 requirePermission 覆盖，不得因移除拦截器而放宽任何授权
  - DB_FILTER|N/A - 不涉及查询过滤或授权下推
  - STATE|不改变 IdempotencyStatus 取值与 `ck_api_idempotency_status` 约束
  - IDEMPOTENCY|本卡正面主题：摘要比对必须由 IdempotencyService 统一负责；不得存在第二个在它之前抢先回放、且不比对摘要的幂等判定点
  - CONSISTENCY|不改变业务事务边界；不得以内存 Map 或 try/catch 吞异常作为回退
  - ERRORS|冲突必须返回既有稳定错误码 IDEMPOTENCY_KEY_CONFLICT（409）；不得以 500 或静默复用表示
  - AUDIT|N/A - 幂等复用不新增审计；首次执行的业务审计保持一次
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不涉及受控值或文案
  - CONFIG|不得引入运行时可配置的幂等开关；不得以 Profile 分支恢复全局回放
  - OBSERVABILITY|拦截器移除后不得出现新的 INTERNAL_ERROR 告警噪声
  - SECRETS|N/A - 不接触 Secret；证据不得包含 Token
allowedPaths:
  - backend/src/main/java/com/aihub/shared/idempotency
  - backend/src/main/java/com/aihub/job/application/IdempotencyService.java
  - backend/src/main/java/com/aihub/authorization/api/RoleController.java
  - backend/src/main/java/com/aihub/version/api
  - backend/src/main/java/com/aihub/transfer/api
  - backend/src/main/java/com/aihub/asset/discussion/api
  - backend/src/test/java/com/aihub/shared/idempotency
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-054.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-054-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V9__job.sql
  - contracts
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-054-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-054.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-054.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: null
approvedAt: null
---

# TASK-P0BR-054：移除抢先回放的全局幂等拦截器，使摘要比对在 HTTP 层可达

> 状态：`DRAFT`（AI 起草，待用户批准；不得由 AI 置为 READY）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

同一 `Idempotency-Key` 提交**不同**请求体时，HTTP 层返回 `409 IDEMPOTENCY_KEY_CONFLICT`，
而不是静默复用首次响应；同键同请求体的重放语义保持不变。

## 2. 关联规格

```yaml
requirements: [REQ-IDM-001]
acceptanceScenarios: [AC-P0B-IDM-001, AC-P0B-IDM-002]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [createRole, createUser, createOrganization, createRoleBinding, createAsset]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核，证据见 EVD-P0BR-052-001）

`IdempotencyInterceptor`（`backend/src/main/java/com/aihub/shared/idempotency/IdempotencyInterceptor.java`）
经 `IdempotencyWebConfiguration` 注册在 `/api/v1/**`，其 `preHandle` 命中已有记录即：

```java
response.setStatus(record.responseStatus());
response.setHeader("X-Idempotency-Replayed", "true");
response.getWriter().write(record.responseBody());
return false; // 已返回缓存响应，不进入 Controller
```

**它从不比对 `request_digest`**，因此无论请求体是否相同都回放首次响应。

三层证据：

1. **实现与设计直接冲突**：`IdempotencyService` 的类注释写明
   「P0-B 采用显式调用（**非 AOP 全局拦截**），避免过度工程；控制器在需要时调用本服务」，
   而该拦截器恰是全局拦截。同一模块内两处代码对同一能力给出相反设计。
2. **实测（真实 Compose 栈）**：同键不同请求体第二次调用
   `POST /api/v1/system/organizations` 返回 `201` 且 `X-Idempotency-Replayed: true`，
   期望的 `409 IDEMPOTENCY_KEY_CONFLICT` 不可达。
   已排除「修复未部署」：从运行容器取出的 `app.jar` 中
   `IdempotencyService.class` 确含 `different request payload` 与
   `IDEMPOTENCY_KEY_CONFLICT` 字节串。
3. **影响面**：`ErrorCode.IDEMPOTENCY_KEY_CONFLICT`（409）在 `IdempotencyService`
   修复后**仍无 HTTP 生产者**；`AC-P0B-IDM-002` 的 E4 无法满足。

### 移除拦截器的影响面（已逐端点盘点）

拦截器是全局的，移除前必须确认没有端点**依赖它**提供幂等。实测盘点：

| 类别 | 端点数 | 说明 |
| --- | --- | --- |
| 已由 `IdempotencyService` 显式实现幂等 | 12 个控制器（含 user/agent/organization/team/project/role-binding/ACL/dictionary/tag/configuration/job/asset） | 拦截器对它们**有害**：抢先回放使摘要比对不可达 |
| 契约声明 `Idempotency-Key` 但**无**幂等实现 | `createRole`（`RoleController`）、`mcpJsonRpc`（`McpController`） | 移除后该声明成为空声明，须在本卡一并处理或明确排除 |

契约中 `IdempotencyKey` 参数被声明为 `required: true`（`contracts/openapi/aihub-v1.yaml`），
但后端各处均以 `required = false` 读取，且缺失时不报错——该漂移不在本卡范围（见「明确不在范围」）。

## 3. 范围

### 允许修改

- 模块：`com.aihub.shared.idempotency`、`com.aihub.job.application`
- 文件或目录：
  - `backend/src/main/java/com/aihub/shared/idempotency`（移除拦截器及其 Web 装配）
  - `backend/src/main/java/com/aihub/job/application/IdempotencyService.java`（如需要，收敛契约文档）
  - `backend/src/main/java/com/aihub/authorization/api/RoleController.java`（为 `createRole` 补幂等，使其契约声明为真）
  - `backend/src/test/java/com/aihub/shared/idempotency`、`backend/src/test/java/com/aihub/job`（回归测试）
- 契约：无变更（`IDEMPOTENCY_KEY_CONFLICT` 与 `Idempotency-Key` 已声明）
- Migration：无
- 页面：无
- Fixture/Verify：不改动 `deploy/compose`

### 明确不在范围

- 不实现 `IN_PROGRESS` 原子登记、租约与并发恰好一次（`AC-P0B-IDM-003`），归独立切片；
- 不改 `api_idempotency.idempotency_key` 的全局 UNIQUE 约束（跨主体隔离缺陷），归 `TASK-P0BR-056`；
- 不为 `McpController` 的 JSON-RPC 补幂等：MCP 是协议端点，其幂等语义应按 `REQ-MCP-*` 单独定义，
  本卡只在报告中记录该契约声明仍为空声明；
- 不修改 `Idempotency-Key` 的 `required` 语义（契约声明 REQUIRED、实现按可选读取的漂移），
  归独立的契约对齐任务；
- 不修复 `AssetModelEntity` 的列名缺陷（`TASK-P0BR-055`）。

### 禁止变化

- 不改变的 API/状态/权限：`Idempotency-Key` 头语义、`IDEMPOTENCY_KEY_CONFLICT`（409）、
  `api_idempotency` 表结构与约束、各写端点权限要求；
- 不允许的依赖升级或重构：不新增依赖；不改写各控制器既有的幂等调用形式；
- 不允许的兼容破坏：不得为「让 409 出现」而在拦截器里补一个摘要比对（那会保留两个判定点）；
  不得删除或跳过既有幂等测试；不得把冲突吞成静默复用。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IDM-002` | 有 `organization:manage` 的用户 | 真实 Compose 栈，同键已成功执行一次 | 用同一 key、**不同**请求体再次调用 | 返回 409 且 `code == IDEMPOTENCY_KEY_CONFLICT`；不产生第二个组织；既有记录未被覆盖 | `E4` |
| `AC-P0B-IDM-002` | 同上 | 同上 | 检视响应头 | 不得再出现 `X-Idempotency-Replayed: true` 与 201 的组合 | `E4` |
| `AC-P0B-IDM-001` | 同上 | 同键已成功执行一次 | 用同一 key、**相同**请求体再次调用 | 返回首次状态码与响应体；只 1 条 `api_idempotency` 记录；无第二个业务副作用 | `E4` |
| `AC-P0B-IDM-001` | 有 `user:manage` 的用户 | 同上 | 在 user/agent/tag/asset 等其它已实现幂等的端点上重复上两条 | 行为一致（回归证明移除拦截器未破坏复用） | `E4` |
| `AC-P0B-IDM-001` | 审查者 | 源码树 | 检索 `/api/v1/**` 的幂等判定点 | 全局仅剩 `IdempotencyService` 一处判定；无第二处回放 | `E2` |
| `AC-P0B-IDM-002` | 有 `authorization:manage` 的用户 | `createRole` 契约声明 `Idempotency-Key` | 带同一 key 与相同请求体两次调用 | 返回首次结果；只创建 1 个角色 | `E4` |
| `AC-P0B-IDM-002` | 最小权限主体 | 栈就绪 | 调用带幂等键的写端点 | 仍返回 403 `AUTH_PERMISSION_DENIED`（移除拦截器不得放宽授权） | `E4` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；`principalId` 继续取自既有 PrincipalContext |
| Permission/Scope/ACL | 移除拦截器不得放宽任何授权；必须逐端点确认 `requirePermission` 仍在 Controller 内执行（原拦截器在 Controller 之前返回时，权限断言根本没跑） |
| 数据库权限下推 | N/A |
| 状态前置条件 | 不改变 `IdempotencyStatus` 与 CHECK 约束 |
| 幂等与并发 | 全局只保留 `IdempotencyService` 一个判定点；它必须比对摘要 |
| 事务与外部一致性 | 不改变业务事务边界；禁止内存 Map 或吞异常回退 |
| 错误码 | 冲突用既有 `IDEMPOTENCY_KEY_CONFLICT`（409） |
| 审计事件 | N/A：首次执行的业务审计仍只发生一次 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 不得引入运行时可配置的幂等开关或按 Profile 的回放回退 |
| 指标/Trace/告警 | 不新增指标；修复后不得出现新的 INTERNAL_ERROR 噪声 |
| Secret/日志脱敏 | N/A；证据不得包含 Token 或响应原文中的敏感字段 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无（本卡让既有声明变为真实可达）；
2. 示例请求、响应和错误：`IDEMPOTENCY_KEY_CONFLICT` 由「已定义未使用」变为真实可达；
3. 新增 Flyway 文件：无（缺陷在 Java 侧判定顺序）；
4. 存量清点与回填：无；
5. 索引、唯一约束、外键及理由：无变更；
6. 兼容窗口与失败恢复：移除拦截器后，先前被静默复用的「同键不同体」请求将改为 409——
   这是契约要求的正确行为；无兼容窗口；
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

1. 删除 `IdempotencyWebConfiguration` 的 `addInterceptors` 注册；按仓库「确定未使用则彻底删除」的
   约定，确认 `IdempotencyInterceptor` 无其它引用后一并删除该类（不得保留 `// removed` 之类残留）；
2. 为 `RoleController.createRole` 补与其它控制器一致的幂等实现
   （`buildIdempotencyKey` + `sha256Digest` + `idempotencyService.execute`），
   使其 OpenAPI 声明为真；同步确认该端点权限断言位置未变；
3. 新增回归测试：断言 `IdempotencyService` 在**同键不同摘要**时抛 `ConflictException`
   且 `errorCode() == IDEMPOTENCY_KEY_CONFLICT`、supplier 未执行（若 `TASK-P0BR-052` 已覆盖则复用并补断言）；
   新增一条静态门禁：断言源码树中不存在第二处「命中即回放」的全局幂等判定点；
4. 在真实 Compose 栈上端到端验证 409 可达，且同键同体复用、越权 403 均未回归。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml up -d
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：`mvnw verify` 为 0（既存 `PreviewJobHandlerTest` 的 2 项 CSV 失败不计入本卡，
  但必须在 Evidence `limitations` 中如实列出）；`verify.ps1` 为 0；
- 覆盖的 AC ID：`AC-P0B-IDM-001`、`AC-P0B-IDM-002`；
- 所需依赖：JDK 21、Docker（Compose 栈）；E4 需要真实 PostgreSQL；
- SKIP 判定：Docker 不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-054-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-054.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 发现某端点**只能靠**该拦截器获得幂等保护，而非仅「顺带」被覆盖；
- 需要修改已应用 Migration 才能对齐；
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
必须显式说明 `McpController` 的 `Idempotency-Key` 声明在移除拦截器后仍为空声明。

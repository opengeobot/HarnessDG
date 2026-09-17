---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-053
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 1d058fdfa226760ac3526c4a2f43b8f167d256d1
stageGatePassed: true
stageGateEvidence: 用户批准。依据 READY 需求 REQ-AUTH-001/REQ-AUTH-002（统一实时授权判定与默认拒绝，P0-B）与 ACCEPTED 决策 DEC-003（P0-B 未完成、必须逐项重验）、DEC-013（Compose 为功能验收环境），修复验收脚本无法区分权限拒绝与改密门导致的假阳性；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-AUTH-001
  - REQ-AUTH-002
acceptanceScenarios:
  - AC-P0B-AUTH-003
  - AC-P0B-AUTH-004
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-AUTH-003|E4|在真实 Compose 栈上以最小权限主体执行 verify 脚本的默认拒绝断言：受保护端点返回 403 且错误码为 AUTH_PERMISSION_DENIED；脚本输出可区分「权限拒绝」与「改密门未完成」，不再出现假阳性
  - AC-P0B-AUTH-004|E4|同一脚本以持有相应权限的主体执行正向断言：受保护端点返回 200 并返回真实数据，证明系统不是「全部拒绝」
crossCuttingPlan:
  - AUTHN|不改变认证路径；脚本继续经 /api/v1/auth/login 取得 access Token，仍断言无 Token→401
  - AUTHZ|本卡正面主题：默认拒绝断言必须以真实 AUTH_PERMISSION_DENIED 为依据；正向断言必须证明有权限路径可达（AC-P0B-AUTH-004）
  - DB_FILTER|N/A - 脚本只经 REST 断言，不直接读业务表判断可见性
  - STATE|断言前必须确认主体不处于 PASSWORD_CHANGE_REQUIRED 状态；该状态下的 403 不得计为权限拒绝
  - IDEMPOTENCY|N/A - 脚本只做只读断言；创建验证主体的写调用须按端点契约携带或无幂等键（见 TASK-P0BR-052）
  - CONSISTENCY|N/A - 不跨系统写入；不涉及 Gitea/MinIO/DVC
  - ERRORS|断言必须检查响应体稳定错误码，不得只看 HTTP 状态码；PASSWORD_CHANGE_REQUIRED 必须报为前置条件未满足而非 PASS
  - AUDIT|N/A - 脚本不新增审计事件；创建验证主体的操作沿用既有 USER_CREATED 审计
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不涉及受控值或文案
  - CONFIG|验证主体与其权限集合必须显式、可审查地写在脚本中；不得引入运行时可配置的「跳过断言」开关
  - OBSERVABILITY|N/A - 不新增指标或告警
  - SECRETS|验证主体口令仅用于本地 Compose 验收栈，不得写死为生产口令、不得进 Git 之外的证据正文；证据不得包含 Token
allowedPaths:
  - deploy/compose/scripts/verify.ps1
  - deploy/compose/scripts/verify.sh
  - docs/runbooks/compose.md
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-053.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-053-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - contracts
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-053-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-053.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-053.md -CheckChangedPaths
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
  - bash ./deploy/compose/scripts/verify.sh
approvedBy: User
approvedAt: 2026-09-17T07:00:06Z
---

# TASK-P0BR-053：修复验收脚本的默认拒绝假阳性，并补有权限正向断言

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

`verify.ps1` / `verify.sh` 的默认拒绝用例只在「确实是权限拒绝」时 PASS；
主体因首登强制改密而被拒时，脚本明确报出前置条件未满足而不是冒充通过；
同时补上正向断言，证明有权限的调用能真实返回 200。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-001, REQ-AUTH-002]
acceptanceScenarios: [AC-P0B-AUTH-003, AC-P0B-AUTH-004]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核，证据见 EVD-P0BR-048-001）

`deploy/compose/scripts/verify.ps1` 的 `Assert-DefaultDeny`（第 208-217 行）仅比较
HTTP 状态码 403，不检查响应体错误码，也不区分拒绝来源：

```powershell
$auth = Invoke-Api -Method GET -Path $Path -Headers @{ Authorization = "Bearer $($script:accessToken)" }
if ($auth.Status -ne 403) { throw "越权 Token 访问 $Path 返回 $($auth.Status)，期望 403（默认拒绝）" }
```

真实栈实测（同一脚本、同一栈、仅主体状态不同）：

| 主体状态 | V06 | V07 | V09 | V10 | V11 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `PENDING_ACTIVATION`（首登未改密） | PASS | PASS | PASS | PASS | PASS | **假阳性**：403 实为 `PASSWORD_CHANGE_REQUIRED`，与权限无关 |
| `ACTIVE`（已完成首登改密） | FAIL | FAIL | FAIL | FAIL | FAIL | 返回 200，因引导管理员经 ADMIN 角色合法持有 `audit:read`/`dictionary:read`/`job:read`/`notification:read`/`system:observe` |

即：**这五条默认拒绝用例只在改密门恰好挡住所有请求时才「通过」**，通过的是与
被测命题（权限默认拒绝）无关的原因。一旦主体按产品设计完成首登改密，用例全部失败——
既证明原断言无鉴别力，也说明当前没有任何一条脚本断言覆盖「有权限→200」
（`AC-P0B-AUTH-004`）。

`docs/runbooks/compose.md` 第 117-119 行记录的既有设计说明是：

> 由于 bootstrap 管理员默认仅持 `ADMIN_SCOPES`（user/authorization/agent），V06-V11 以
> "无 Token→401、越权 Token→403"验证**默认拒绝**语义，而非以管理员令牌断言 200。

该说明与 `REQ-AUTH-001` 的两层判定（JWT Scope 允许 **且** Role Binding/Permission 允许）
不一致：管理员令牌在后端经 RBAC 解析后**是有权限的**，不是「越权 Token」。
因此脚本选择的被测主体是错的，而不只是断言太松。

### 与既有出口证据的关系

`EVD-P0B-V01-V12.yaml`（`status: PASS`，2026-07-02）把 V06/V07/V09/V10/V11 记为
`AC-P0B-AUTH-003` 的 `denial` 证据。按本卡结论，该条证据的取得条件与命题无关，
**不足以支持 `AC-P0B-AUTH-003`**。本卡不修改该历史证据文件（它记录了当时的真实输出），
但须在完成报告中明确指出其结论不可再被引用为 AUTH-003 的 PASS 依据。

## 3. 范围

### 允许修改

- 模块：`deploy/compose/scripts`（验收脚本）、`docs/runbooks`
- 文件或目录：
  - `deploy/compose/scripts/verify.ps1`（断言改为检查错误码 + 补正向断言）
  - `deploy/compose/scripts/verify.sh`（与 .ps1 保持等价）
  - `docs/runbooks/compose.md`（同步 V05-V12 的前置条件与覆盖范围说明）
- 契约：无变更
- Migration：无
- 页面：无
- Fixture/Verify：本卡正面主题

### 明确不在范围

- 不修改后端授权判定逻辑（403 的产生方式是符合 `REQ-AUTH-001` 的正确行为）；
- 不修改 `Idempotency-Key` 与 `job_task` 缺陷，归 `TASK-P0BR-052` 与 `TASK-P0BR-051`；
- 不推广到 `AC-P0B-AUTH-005..009` 与其余 60 余条出口场景（归 `TASK-P0BR-045` 的出口执行）；
- 不修改 `deploy/compose/compose.yaml`、`.env.example` 或任何服务定义；
- 不修改历史 Evidence 文件内容。

### 禁止变化

- 不改变的 API/状态/权限：不改任何端点、权限码、错误码或数据库约束；
- 不允许的依赖升级或重构：不新增依赖；不重写脚本执行框架；
- 不允许的兼容破坏：不得为了让脚本变绿而放宽断言（例如把「期望 403」改成「期望 403 或 200」）、
  不得删除失败用例、不得把断言标记为 SKIP 来规避；
  不得在脚本内放宽或绕过 `PASSWORD_CHANGE_REQUIRED` 语义（那是产品行为，不是脚本可豁免的）。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-003` | 最小权限验证主体（不含 `audit:read`/`job:read`/`notification:read`/`system:observe`/`dictionary:read`） | 真实 Compose 栈，主体已 ACTIVE | 调用对应受保护端点 | 返回 403 且响应体 `code` 为 `AUTH_PERMISSION_DENIED`；脚本 PASS | `E4` |
| `AC-P0B-AUTH-003` | 已完成首登改密的管理员 | 同上 | 调用同一端点 | 返回 200（脚本此处不得再断言 403）；若脚本仍断言 403 则必须失败 | `E4` |
| `AC-P0B-AUTH-003` | 未完成首登改密的主体 | 同上 | 调用任意受保护端点 | 响应 `code` 为 `PASSWORD_CHANGE_REQUIRED`；脚本须报为前置条件未满足（非 PASS），并给出可操作的提示 | `E4` |
| `AC-P0B-AUTH-003` | 匿名请求 | 栈就绪 | 不携带 Token 调用受保护端点 | 返回 401（既有断言保持） | `E4` |
| `AC-P0B-AUTH-004` | 持有相应权限的主体 | 栈就绪 | 调用 `/api/v1/system/dictionaries` 等端点 | 返回 200 且返回真实数据，不是空集合占位 | `E4` |
| `AC-P0B-AUTH-003` | 审查者 | 脚本被误改为只比较状态码 | 运行脚本 | 用例失败，防止假阳性回归 | `E2/E4` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 不改变认证路径；继续断言无 Token→401；验证主体经既有登录端点取得 Token |
| Permission/Scope/ACL | 默认拒绝断言必须以真实 `AUTH_PERMISSION_DENIED` 为依据；正向断言必须证明有权限可达 |
| 数据库权限下推 | N/A：脚本只经 REST 断言，不直接读业务表判断可见性 |
| 状态前置条件 | 断言前确认主体不处于 `PASSWORD_CHANGE_REQUIRED`；该状态的 403 不计为权限拒绝 |
| 幂等与并发 | N/A：只读断言；创建验证主体的写调用须与 `TASK-P0BR-052` 的幂等现状兼容 |
| 事务与外部一致性 | N/A：不跨系统写入 |
| 错误码 | 断言必须检查响应体稳定错误码；`PASSWORD_CHANGE_REQUIRED` 报为前置条件未满足而非 PASS |
| 审计事件 | N/A：不新增；创建验证主体沿用既有 `USER_CREATED` 审计 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | N/A：不涉及受控值或文案 |
| 配置 | 验证主体与权限集合显式写在脚本中；不得引入运行时可配置的「跳过断言」开关 |
| 指标/Trace/告警 | N/A：不新增指标或告警 |
| Secret/日志脱敏 | 验证主体口令仅用于本地验收栈；证据不得包含口令或 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；断言的 `AUTH_PERMISSION_DENIED` 与
   `PASSWORD_CHANGE_REQUIRED` 均已在 Error Catalog 中定义；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：无；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：脚本判定收紧后，未完成首登改密的栈将不再「全绿」——
   这是预期行为，须在 Runbook 中写明该前置条件；
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

1. 在 `verify.ps1` 中把默认拒绝断言改为：解析响应体 `code`，仅当为
   `AUTH_PERMISSION_DENIED` 时 PASS；为 `PASSWORD_CHANGE_REQUIRED` 时以明确的
   「前置条件未满足」失败信息终止该用例（不可 PASS、不可静默 SKIP）；
2. 引入一个**最小权限**验证主体用于拒绝断言（经既有管理员 API 创建，权限集合显式列出，
   且不含被测端点所需权限），使「403 来自权限判定」成为真实命题；
3. 补正向断言：以持有相应权限的主体调用同一端点，断言 200 且返回真实数据
   （覆盖 `AC-P0B-AUTH-004`），从而区分「默认拒绝」与「全部拒绝」；
4. 在 `verify.sh` 中实现等价改动，保持两脚本语义一致；
5. 同步更新 `docs/runbooks/compose.md` 的 V05-V12 前置条件与覆盖说明，
   撤回与被测命题无关的表述。

## 8. 验证命令

```text
docker compose -f deploy/compose/compose.yaml config --quiet
docker compose -f deploy/compose/compose.yaml up -d
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
bash ./deploy/compose/scripts/verify.sh
```

每条命令声明：

- 预期退出码：`docker compose config --quiet` 为 0；两个验收脚本为 0
  （前提：栈就绪且验证主体已 ACTIVE）；
- 覆盖的 AC ID：`AC-P0B-AUTH-003`、`AC-P0B-AUTH-004`；
- 所需依赖：Docker（Compose 栈）、Node 仅用于前端构建（本卡不改前端）；
- SKIP 判定：服务不可达时相关用例按既有约定 SKIP 并计数，**不得**把 SKIP 或
  `PASSWORD_CHANGE_REQUIRED` 计为 PASS；
- 报告或产物路径：脚本输出日志、`docs/ai-spec/tasks/evidence/EVD-P0BR-053-001.yaml`。

路径触发强制命令：修改 `deploy/compose` 必须包含 `docker compose config`
与仓库验收脚本。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-053.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如收紧断言后必须改后端授权行为才能通过）；
- 需要降低权限、校验、约束或测试才能让脚本变绿；
- 需要修改 `V1`/`V2` 或其他已应用 Migration；
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
必须显式说明 `EVD-P0B-V01-V12.yaml` 中 AUTH-003 相关结论为何不可再被引用。

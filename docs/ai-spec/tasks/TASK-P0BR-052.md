---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-052
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: bcf1243e5a78e5b3fe0e5b55d6ee866842473e7a
stageGatePassed: false
stageGateEvidence: 待用户批准。依据 READY 需求 REQ-IDM-001（写接口幂等，P0-B）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），修复幂等存储的类型绑定缺陷以恢复带 Idempotency-Key 的写接口；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
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
  - AC-P0B-IDM-001|E4|在真实 Compose 栈上用同一 Idempotency-Key 与相同请求体重复调用同一写端点：两次均不返回 500，第二次复用首次状态码与响应体，且只产生一次业务副作用（数据库行数不增加）
  - AC-P0B-IDM-002|E4|同幂等键提交不同请求摘要：返回 409 IDEMPOTENCY_KEY_CONFLICT，第二次请求不产生业务副作用
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；幂等作用域继续由既有 Principal 提供 principalId
  - AUTHZ|N/A - 不新增或放宽权限；各写端点权限要求不变
  - DB_FILTER|N/A - 不涉及查询过滤或授权下推
  - STATE|不改变 IdempotencyStatus 取值与 `ck_api_idempotency_status` 约束；仅修正参数类型绑定
  - IDEMPOTENCY|本卡正面主题：key+principalId+method+path+digest 构成作用域；同 key 同 digest 复用首次结果；同 key 不同 digest 必须返回 IDEMPOTENCY_KEY_CONFLICT；并发同 key 只允许一次执行
  - CONSISTENCY|幂等记录的写入不得改变业务事务边界；不得以内存 Map 或 try/catch 吞异常作为回退
  - ERRORS|使用既有稳定错误码 IDEMPOTENCY_KEY_CONFLICT；不得以 500 INTERNAL_ERROR 表示客户端可纠正的冲突
  - AUDIT|N/A - 幂等复用本身不新增审计事件；首次执行的业务审计保持一次
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不涉及受控值或文案
  - CONFIG|不得引入运行时可配置的幂等开关或按 Profile 分支的回退实现
  - OBSERVABILITY|不新增指标；修复后带幂等键的写请求不得再产生 INTERNAL_ERROR 级别的告警噪声
  - SECRETS|请求摘要为不可逆 SHA-256；响应负载入库前须脱敏，不得存入凭据或 Token，证据不得包含 Token
allowedPaths:
  - backend/src/main/java/com/aihub/job/infrastructure/JdbcIdempotencyStore.java
  - backend/src/main/java/com/aihub/job/application/IdempotencyService.java
  - backend/src/test/java/com/aihub/job
  - backend/src/test/java/com/aihub/shared/idempotency
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-052.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-052-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V9__job.sql
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-052-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-052.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-052.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-052：修复幂等存储的类型绑定与摘要校验，恢复带幂等键的写接口

> 状态：`DRAFT`（待用户批准）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

所有声明支持幂等的写端点在接受 `Idempotency-Key` 时正常工作：首次执行成功并落库，
同键同请求体重复调用复用首次结果且无重复副作用，同键不同请求体返回稳定的
`IDEMPOTENCY_KEY_CONFLICT`。

## 2. 关联规格

```yaml
requirements: [REQ-IDM-001]
acceptanceScenarios: [AC-P0B-IDM-001, AC-P0B-IDM-002]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [createOrganization, createUser, createRoleBinding]
mcpTools: []
eventSchemas: []
flywayBaseline: V9
```

### 缺陷事实（已复核，证据见 EVD-P0BR-048-001）

**缺陷 A：`Instant` 无类型提示导致插入失败。**
`JdbcIdempotencyStore.save`（`backend/src/main/java/com/aihub/job/infrastructure/JdbcIdempotencyStore.java`）
用 `MapSqlParameterSource.addValue("now", Instant.now())` 绑定 `completed_at`，
pgjdbc 无法推断 `java.time.Instant` 的 SQL 类型：

```text
Caused by: org.postgresql.util.PSQLException:
  Can't infer the SQL type to use for an instance of java.time.Instant.
  Use setObject() with an explicit Types value to specify the type to use.
```

真实栈实测：`POST /api/v1/system/organizations`、`POST /api/v1/system/users`、
`POST /api/v1/system/role-bindings` 在**携带** `Idempotency-Key` 时返回
HTTP 500 `INTERNAL_ERROR`；调用链经 `IdempotencyService.execute:55` →
`JdbcIdempotencyStore.save:93`。**不携带**该头时同一端点正常工作——
这解释了为何既有后端单测（不经真实 pgjdbc 路径）未发现。

**缺陷 B：请求摘要从不比较，`AC-P0B-IDM-002` 未实现。**
`IdempotencyService.execute` 把 `requestFingerprint` 写入记录但**从不与既有记录比对**：
命中已有 key 时无条件返回首次响应。因此同键不同请求体不会返回
`IDEMPOTENCY_KEY_CONFLICT`，而是静默复用首次结果——违反 `REQ-IDM-001`
「同 key 不同 digest 返回 `IDEMPOTENCY_KEY_CONFLICT`」与 `AC-P0B-IDM-002`。
`ErrorCode.IDEMPOTENCY_KEY_CONFLICT`（`ErrorCode.java:226`，HTTP 409）已定义但无生产者。

**缺陷 C：IN_PROGRESS 原子登记缺失（范围待批准）。**
`REQ-IDM-001` 要求「首次请求原子登记 IN_PROGRESS」与「并发只允许一个执行」。
当前实现直接执行业务再保存 COMPLETED，`IN_PROGRESS` 状态值与
`ck_api_idempotency_status` 约束存在但从未写入，`AC-P0B-IDM-003`（并发同键）
不可满足。见「明确不在范围」。

两个缺陷均由 `d8506bf`（2026-07-02）引入，早于本卡 baseCommit，本次由
`TASK-P0BR-048` 的 E4 验证首次在真实栈上暴露。

## 3. 范围

### 允许修改

- 模块：`com.aihub.job.infrastructure`、`com.aihub.job.application`
- 文件或目录：
  - `backend/src/main/java/com/aihub/job/infrastructure/JdbcIdempotencyStore.java`（修正 `Instant` 绑定）
  - `backend/src/main/java/com/aihub/job/application/IdempotencyService.java`（摘要比对）
  - `backend/src/test/java/com/aihub/job`、`backend/src/test/java/com/aihub/shared/idempotency`（回归测试）
- 契约：无变更（`Idempotency-Key` 参数与 `IDEMPOTENCY_KEY_CONFLICT` 已在契约与 Error Catalog 中定义）
- Migration：无
- 页面：无
- Fixture/Verify：不改动 `deploy/compose`

### 明确不在范围

- **不实现 `AC-P0B-IDM-003`（并发同键）与 IN_PROGRESS 原子登记/租约恢复**：
  该能力需要改 `IdempotencyService` 的状态机与并发语义（含 INSERT ON CONFLICT 抢占、
  lease/owner 过期回收），属独立纵向切片。本卡只要求「不因并发产生重复记录或 500」，
  不声明并发恰好一次；
- 不修复 `job_task` 插入缺陷，归 `TASK-P0BR-051`；
- 不修复 `verify.ps1` 假阳性，归 `TASK-P0BR-053`；
- 不为各写端点补 `Idempotency-Key REQUIRED` 声明（`REQ-IDM-001` 要求逐操作声明
  REQUIRED/NOT_REQUIRED，属独立整改）；
- 不重构幂等存储的抽象或改表结构。

### 禁止变化

- 不改变的 API/状态/权限：`Idempotency-Key` 头语义、`IDEMPOTENCY_KEY_CONFLICT` 状态码（409）、
  `api_idempotency` 表结构与约束、各写端点权限；
- 不允许的依赖升级或重构：不新增依赖，不改 `IdempotencyService.execute` 的公开签名语义；
- 不允许的兼容破坏：不得以内嵌内存 Map 或按 Profile 分支作为幂等回退；
  不得把冲突吞成静默复用（那正是缺陷 B）；不得为规避类型问题把时间列改成字符串。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IDM-001` | 有 `organization:manage` 的用户 | 真实 Compose 栈 + 真实 PostgreSQL | 带同一 `Idempotency-Key` 与相同请求体两次调用 `POST /api/v1/system/organizations` | 两次均非 500；第二次返回首次状态码与响应体；`organization` 表只新增 1 行；`api_idempotency` 只 1 条 COMPLETED 记录 | `E4` |
| `AC-P0B-IDM-001` | 同上 | 同上 | 检视后端日志 | 无 `Can't infer the SQL type`、无 `PSQLException` | `E4` |
| `AC-P0B-IDM-002` | 同上 | 同键已成功执行一次 | 用同一 key、不同请求体再次调用 | 返回 409 `IDEMPOTENCY_KEY_CONFLICT`；不产生第二个组织；既有记录未被覆盖 | `E4` |
| `AC-P0B-IDM-002` | 同上 | 同 key 但**不同主体** | 另一用户用同一 key 调用 | 不命中他人记录（作用域含 principalId），按新请求处理 | `E4` |
| `AC-P0B-IDM-001` | 审查者 | Testcontainers 或真实 PostgreSQL | 运行幂等仓储回归测试 | 覆盖 save/find 往返、同键同摘要复用、同键不同摘要冲突 | `E3` |
| `AC-P0B-IDM-001` | 同上 | 不携带 `Idempotency-Key` | 调用同一写端点 | 正常工作且不写幂等记录（既有行为不变） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；`principal_id` 继续取自既有 PrincipalContext |
| Permission/Scope/ACL | N/A：不新增或放宽权限；各写端点权限要求不变 |
| 数据库权限下推 | N/A：不涉及查询过滤或授权下推 |
| 状态前置条件 | 不改变 `IdempotencyStatus` 与 CHECK 约束；本卡只写 COMPLETED |
| 幂等与并发 | 同键同摘要复用首次结果；同键不同摘要返回 `IDEMPOTENCY_KEY_CONFLICT`；作用域含 principalId/method/path |
| 事务与外部一致性 | 幂等记录写入不得改变业务事务边界；禁止内存 Map 或吞异常回退 |
| 错误码 | 使用既有 `IDEMPOTENCY_KEY_CONFLICT`（409）；不得以 500 表示客户端可纠正冲突 |
| 审计事件 | N/A：幂等复用不新增审计；首次执行的业务审计仍只发生一次 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | N/A：不涉及受控值或文案 |
| 配置 | 不得引入运行时可配置的幂等开关或按 Profile 的回退实现 |
| 指标/Trace/告警 | 不新增指标；修复后带幂等键的写请求不得再产生 INTERNAL_ERROR 告警噪声 |
| Secret/日志脱敏 | 请求摘要为不可逆 SHA-256；响应负载入库前脱敏，不得含凭据/Token；证据不含 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无（参数与错误码已声明）；
2. 示例请求、响应和错误：`IDEMPOTENCY_KEY_CONFLICT` 从「已定义未使用」恢复为真实可达；
3. 新增 Flyway 文件：无（表结构正确，缺陷在 Java 侧类型绑定与校验逻辑）；
4. 存量清点与回填：无需回填；先前失败的请求未写入记录（插入即失败）；
5. 索引、唯一约束、外键及理由：无变更；
6. 兼容窗口与失败恢复：修复后所有带幂等键的写路径一并恢复；无兼容窗口；
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

1. 在 `JdbcIdempotencyStore` 中为时间参数提供显式 JDBC 类型（例如
   `addValue("now", Timestamp.from(Instant.now()))` 并声明 `Types.TIMESTAMP_WITH_TIMEZONE`，
   或等价的可被 pgjdbc 正确推断的绑定方式），使 `completed_at` 可写入；读回路径保持一致；
2. 在 `IdempotencyService.execute` 中比对既有记录的 `requestFingerprint`：
   相同则复用首次响应；不同则抛 `IDEMPOTENCY_KEY_CONFLICT`（409），且**不执行** supplier；
3. 新增回归测试覆盖 save/find 往返、同键同摘要复用、同键不同摘要冲突；
   测试须能在类型绑定再次失败或摘要比对被移除时失败；
4. 在真实 Compose 栈上端到端验证上述行为（含跨主体同键隔离）。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml up -d
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：`mvnw verify` 为 0（既存 `PreviewJobHandlerTest` 的 2 项 CSV 失败不计入本卡，
  但须在 Evidence `limitations` 中如实列出）；`verify.ps1` 在 `TASK-P0BR-053` 完成前
  可能因默认拒绝假阳性而失败，须逐项说明；
- 覆盖的 AC ID：`AC-P0B-IDM-001`、`AC-P0B-IDM-002`；
- 所需依赖：JDK 21、Docker（Compose 栈）；E4 需要真实 PostgreSQL；
- SKIP 判定：Docker 不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
  `AC-P0B-IDM-003` 未纳入本卡，须在 Evidence `notProven` 中显式列出；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-052-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-052.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如修好类型绑定后暴露出更多幂等语义缺口，需要扩大范围到 IN_PROGRESS/并发）；
- 需要修改 `V1`/`V2`/`V9` 或其他已应用 Migration 才能修好；
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

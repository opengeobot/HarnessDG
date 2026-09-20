---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-024
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 8ed02ef7e2664b09d878c69f09506027696a764b
stageGatePassed: true
stageGateEvidence: 用户批准方向。依据 READY 需求 REQ-JOB-001（PostgreSQL 可靠任务）与 REQ-AUD-001（不可变业务审计），以及 ACCEPTED 决策 DEC-003（P0-B 必须逐项重验）、DEC-013（Compose 为功能验收环境），补齐 DEAD 终态与人工重试/取消的审计与告警缺口；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-JOB-001
  - REQ-AUD-001
acceptanceScenarios:
  - AC-P0B-JOB-004
  - AC-P0B-JOB-005
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-JOB-004|E4|真实 Compose 栈：任务达最大重试进入 DEAD，产生 JOB_DEAD 审计、JOB_DEAD 站内通知、DEAD 告警与 job_attempt(DEAD) 记录，且 attempts 不再增长
  - AC-P0B-JOB-005|E4|真实 Compose 栈：无 job:manage 主体重试/取消返回 403 且任务状态不变；有 job:manage 的运维按前置条件成功重试/取消并产生 JOB_MANUALLY_RETRIED/CANCELLED 审计
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；人工操作的 actor 取自 PrincipalContext
  - AUTHZ|既有 job:read/job:manage 判定不放宽；无权主体的拒绝必须有可观察证据，不得因新增审计而改变 HTTP 状态码
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|沿用 JobStatus 与 CHECK 约束；重试仅允许 DEAD/RETRY_WAIT，取消仅允许 PENDING/RETRY_WAIT，终态不可再迁移
  - IDEMPOTENCY|人工重试/取消继续经 IdempotencyService；重复调用不得产生第二次状态迁移或重复审计
  - CONSISTENCY|审计与状态变更必须在同一事务内原子提交：状态迁移成功而无审计、或审计而无迁移均视为缺陷
  - ERRORS|沿用 AUTH_PERMISSION_DENIED / JOB_NOT_FOUND / JOB_STATE_NOT_ALLOWED；不新增错误码
  - AUDIT|补 JOB_MANUALLY_RETRIED、JOB_MANUALLY_CANCELLED、JOB_DEAD 三个事件落点；经共享 AuditService 追加写，不得新建本地审计表或旁路
  - NOTIFICATION|JOB_DEAD 站内通知复用既有 JobNotificationAdapter；通知失败不得回滚任务状态
  - TAXONOMY_I18N|N/A - 不涉及受控值；JOB_DEAD 通知沿用既有 i18nKey
  - CONFIG|告警阈值改为受控配置项并给出安全默认值；不得硬编码不可覆盖的阈值
  - OBSERVABILITY|DEAD 必须可由告警记录与既有指标观察；不在指标 label 中加入 jobId/principalId
  - SECRETS|N/A - 不接触 Secret；审计摘要不得包含 payload 原文、凭据或 Token
allowedPaths:
  - backend/src/main/java/com/aihub/job
  - backend/src/main/java/com/aihub/platform/observability/application/AlertService.java
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/tasks/TASK-P0BR-024.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-024-001.yaml
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR-024-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-024.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-024.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-20T05:03:23Z
---

# TASK-P0BR-024：DEAD 终态与人工重试/取消的审计、通知与告警

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-20

## 1. 目标

任务进入 DEAD 时可被审计、通知与告警三条渠道观察到；运维人员对有权限与无权限的重试/取消操作
分别得到遵守前置条件的成功结果与拒绝结果，且两类操作都有对应的审计记录。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001, REQ-AUD-001]
acceptanceScenarios: [AC-P0B-JOB-004, AC-P0B-JOB-005]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: [retryJob, cancelJob, listJobs]
mcpTools: []
eventSchemas: []
flywayBaseline: V26
```

### 缺陷与缺口事实（已复核）

以下均在当前 HEAD 上直接读取源码确认：

1. **契约已声明但实现缺失的审计**：`contracts/openapi/aihub-v1.yaml` 已为两个操作声明
   `x-audit-event: JOB_MANUALLY_RETRIED` 与 `x-audit-event: JOB_MANUALLY_CANCELLED`，
   但 `backend/src/main/java/com/aihub/job` 整个模块内对
   `Audit`/`audit`/`AuditPort`/`JOB_MANUALLY` 的检索结果为零命中。
   `JobApplicationService.retryJob`/`cancelJob` 只做状态迁移，不写任何审计。
2. **DEAD 无审计**：`REQ-JOB-001` 声明的事件集合包含 `JOB_DEAD`，
   但 `JobWorker.handleNoHandler` 与 `handleFailure` 的 DEAD 分支只写
   `job_attempt`、递增计数器并调用通知，无审计落点。
3. **DEAD 无告警检查**：`AlertService.checkAlerts()` 只检查两类——
   `DEAD_DELIVERY`（webhook 投递）与 `OUTBOX_BACKLOG`，没有任务 DEAD 的检查项。
   因此 `AC-P0B-JOB-004` 要求的「告警产生」当前不成立。
4. **告警阈值硬编码**：`AlertService` 的 `DEAD_DELIVERY_THRESHOLD`/`OUTBOX_BACKLOG_THRESHOLD`
   为私有常量，无配置项。
5. **前置条件与拒绝路径本身已实现**：`retryJob` 仅接受 `DEAD`/`RETRY_WAIT`
   （`resetToPending` 的 `WHERE status IN ('DEAD','RETRY_WAIT')`），
   `cancelJob` 仅接受 `PENDING`/`RETRY_WAIT`；不满足时抛
   `JOB_STATE_NOT_ALLOWED`（409），任务不存在时抛 `JOB_NOT_FOUND`（404）。
   授权由 `JobController` 的 `job:manage` 判定，`READER` 角色不持有该权限。
   即 `AC-P0B-JOB-005` 的拒绝与前置条件部分已有实现，缺的是审计。
6. **人工重试不重置重试预算**：`resetToPending` 只改
   `status`/`next_run_at`/`error_code`/`lease`，不重置 `attempts`。
   DEAD 任务被人工重试后，Handler 若再失败一次即立刻回到 DEAD。
   本卡需明确该语义：要么重置预算，要么在契约/文档中固定「不重置」并给出理由——
   不得保持沉默的现状。
7. **既有证据缺失**：`EVD-P0B-V01-V12.yaml` 已把 `AC-P0B-JOB-001..005`
   整体列为 `notProven`；`ConfigurationLifecycleIT` 的 `job004`/`job005`
   仅直接调用应用服务，不经 Worker、不验证权限与审计。
8. **契约口径不一致（本卡不修，记录备查）**：`/system/jobs/{jobId}:retry` 与
   `:cancel` 在 OpenAPI 中把 `Idempotency-Key` 声明为 `required: true`，
   实现为可选；实现还会返回 404，而契约只声明 403/409。

## 3. 范围

### 允许修改

- 模块：`com.aihub.job`（application / infrastructure）、
  `com.aihub.platform.observability.application`（仅 AlertService）
- 文件或目录：
  - `backend/src/main/java/com/aihub/job/**`
    （审计适配器、DEAD 审计落点、人工操作审计、重试预算语义显式化）
  - `backend/src/main/java/com/aihub/platform/observability/application/AlertService.java`
    （新增任务 DEAD 检查项；阈值改为受控配置）
  - `backend/src/test/java/com/aihub/job/**`（E1/E2/E3 回归）
- 契约：无既有结构变更。`x-audit-event` 已在契约中声明，本卡使其在实现中成立，
  因此不改动 `contracts/openapi/aihub-v1.yaml`
- Migration：无。`audit_log`（V10）与 `system_alert`（V21）已具备所需结构
- 页面：无（任务管理页面已存在，本卡不新增交互）
- Fixture/Verify：不改动 `deploy/compose` 脚本；E4 以独立取证脚本执行

### 明确不在范围

- 通用 403 拒绝审计与审计不可篡改 → `TASK-P0BR-025`（`AC-P0B-AUD-001/003`）；
  本卡只补 `JOB_MANUALLY_RETRIED`/`JOB_MANUALLY_CANCELLED`/`JOB_DEAD` 三个已声明事件；
- 告警规则文件与指标名对齐、Prometheus/Grafana 编排 → `TASK-P0BR-030`（`AC-P0B-OBS-005`）；
- 修正 OpenAPI 的 `Idempotency-Key` 必填口径与缺失的 404 声明（既存契约缺陷）；
- 租约、退避、attempt 记账与崩溃恢复 → `TASK-P0BR-023`（`AC-P0B-JOB-002/003`）；
- `JOB_LEASE_RECONCILE` / `OUTBOX_DELIVERY_RECONCILE` 的调度接线；
- webhook 与通知投递链路 → 通知/Webhook 专项任务。

### 禁止变化

- 不改变的 API/状态/权限：`/system/jobs` 的路径、方法、权限码与错误码集合；
  `JobStatus` 取值集合与 CHECK 约束；`job:read`/`job:manage` 判定不得放宽；
- 不允许的依赖升级或重构：不新增依赖；不新建本地审计表或审计旁路，
  必须经共享 `AuditService`；不引入内存队列替代持久化任务状态；
- 不允许的兼容破坏：不得改变重试/取消的状态前置条件使其更宽松，
  不得让终态（DEAD/CANCELLED/SUCCEEDED）可再迁移。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-JOB-004` | 运维 | 任务持续失败且已达 `max_attempts` | Worker 执行最后一次尝试 | 任务进入 DEAD；`job_attempt` 记为 DEAD；`error_code` 与脱敏摘要完整 | `E4` |
| `AC-P0B-JOB-004` | 审查者 | 上述 DEAD 任务 | 查询审计与通知 | 存在 `JOB_DEAD` 审计记录，且目标主体收到 `JOB_DEAD` 站内通知 | `E4` |
| `AC-P0B-JOB-004` | 运维 | 上述 DEAD 任务 | 等待后续多个调度周期 | `attempts` 不再增长，不出现无限重试，不产生新的 attempt 行 | `E4` |
| `AC-P0B-JOB-004` | 运维 | DEAD 任务数越过阈值 | 触发告警检查 | 产生 DEAD 类告警；数量回落后告警可恢复为 RESOLVED | `E4` |
| `AC-P0B-JOB-005` | 无 `job:manage` 的主体 | 存在 DEAD 任务 | 调用 `:retry` 与 `:cancel` | 均返回 403；任务状态与 attempts 不变；不产生成功类审计 | `E4` |
| `AC-P0B-JOB-005` | 有 `job:manage` 的运维 | 存在 DEAD 任务 | 调用 `:retry` | 返回成功；任务转 PENDING；产生 `JOB_MANUALLY_RETRIED` 审计 | `E4` |
| `AC-P0B-JOB-005` | 有 `job:manage` 的运维 | 存在 PENDING 任务 | 调用 `:cancel` | 返回成功；任务转 CANCELLED；产生 `JOB_MANUALLY_CANCELLED` 审计 | `E4` |
| 边界 | 有 `job:manage` 的运维 | 任务处于 RUNNING/SUCCEEDED 等不可迁移状态 | 调用 `:retry` 或 `:cancel` | 返回 409 `JOB_STATE_NOT_ALLOWED`；无状态变更、无成功审计 | `E4` |
| 失败 | 有 `job:manage` 的运维 | 任务不存在 | 调用 `:retry` 或 `:cancel` | 返回 404 `JOB_NOT_FOUND`，不泄漏其他任务信息 | `E2` |
| 恢复 | 运维 | 任务已 DEAD 且依赖已恢复 | 人工重试 | 任务可重新执行至 SUCCEEDED；重试预算语义与第 6 节声明一致 | `E4` |

必须覆盖正常、边界、失败、越权。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；人工操作的 actor 取自 `PrincipalContext` |
| Permission/Scope/ACL | 既有 `job:read`/`job:manage` 判定不放宽；新增审计不得改变 HTTP 状态码 |
| 数据库权限下推 | N/A：不涉及查询条件或授权下推 |
| 状态前置条件 | 重试仅 DEAD/RETRY_WAIT，取消仅 PENDING/RETRY_WAIT；终态不可再迁移 |
| 幂等与并发 | 人工操作继续经 `IdempotencyService`；重复调用不得产生第二次迁移或重复审计 |
| 事务与外部一致性 | 状态迁移与审计必须同一事务原子提交；通知为旁路，失败不得回滚状态 |
| 错误码 | 沿用 `AUTH_PERMISSION_DENIED`/`JOB_NOT_FOUND`/`JOB_STATE_NOT_ALLOWED`；不新增 |
| 审计事件 | 补 `JOB_MANUALLY_RETRIED`、`JOB_MANUALLY_CANCELLED`、`JOB_DEAD`；经共享 `AuditService` 追加写 |
| 通知 | 复用 `JobNotificationAdapter`；通知失败不得回滚任务状态 |
| 字典/标签/i18n | N/A：不涉及受控值；`JOB_DEAD` 通知沿用既有 i18nKey |
| 配置 | 告警阈值改为受控配置项并给出安全默认值，不得硬编码且不可覆盖 |
| 指标/Trace/告警 | DEAD 可经告警与既有指标观察；指标 label 不得加入 jobId/principalId |
| Secret/日志脱敏 | 审计摘要不得包含 payload 原文、凭据或 Token；证据不得包含此类内容 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无。`JOB_MANUALLY_RETRIED` 与
   `JOB_MANUALLY_CANCELLED` 已在契约中声明，本卡使其在实现中成立；
   `JOB_DEAD` 是 Worker 侧事件、无对应 REST 操作，故不进入 OpenAPI。
2. 示例请求、响应和错误：无新增；沿用既有 403/404/409 语义。
3. 新增 Flyway 文件：无。`audit_log`（V10）与 `system_alert`（V21）
   已具备所需列；不得修改已应用迁移。
4. 存量清点与回填：无数据回填。需在实现中确认 DEAD 任务在
   补齐审计后不会对历史行做回溯写入（审计只记录新发生的事实）。
5. 索引、唯一约束、外键及理由：无新增。
6. 兼容窗口与失败恢复：新增审计与告警为纯增行为，不改变既有响应；
   **人工重试是否重置 `attempts` 的语义必须在本卡内显式固定**：
   若选择重置，需说明上限如何防止无限人工重试；若选择不重置，
   需在契约描述与本卡文档中写明「人工重试不授予新预算」并给出理由。
   两种选择都必须有对应测试，不得保持现状的沉默语义。
7. 生成类型更新：无前端类型变更（`JobView` 未变）。

## 7. 实施步骤

- [ ] 契约及契约测试（本卡无契约结构变更，仅补实现落点）
- [ ] Migration 及空库/升级迁移测试（本卡无 Migration）
- [ ] Application/Domain 用例
- [ ] REST/MCP/Worker Adapter
- [ ] 前端页面与状态（本卡不接线前端）
- [ ] 正常/边界/失败/越权/恢复测试
- [ ] Compose Fixture/Verify
- [ ] Runbook/追踪矩阵

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 在 `com.aihub.job` 内新增审计端口与适配器，委托共享 `AuditService`
   （对齐既有 `TaxonomyAuditAdapter` 的模块桥接模式，不新建本地审计表）；
2. 在 `JobApplicationService.retryJob`/`cancelJob` 的成功路径写
   `JOB_MANUALLY_RETRIED`/`JOB_MANUALLY_CANCELLED`，
   与状态迁移处于同一事务；
3. 在 `JobWorker` 的两个 DEAD 分支写 `JOB_DEAD` 审计，
   摘要只含 jobId/type/attempts/errorCode；
4. 在 `AlertService` 增加任务 DEAD 检查项，阈值改为受控配置，
   并在数量回落后支持 RESOLVED；
5. 显式固定人工重试与 `attempts` 的关系（见第 6 节第 6 点）并补测试；
6. E3：Testcontainers 真实 PostgreSQL，经真实 Worker 驱动 DEAD，
   断言审计行、通知行、告警行与 attempt 行；
7. E4：真实 Compose 栈上以两个权限不同的主体执行重试/取消，
   并在栈上制造 DEAD（依赖持续失败或达上限）后核对三条渠道。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；若本机 Docker 不可用，依赖真实依赖的 IT 记为 SKIP 并在
  Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-JOB-004`、`AC-P0B-JOB-005`；
- 所需依赖：JDK 21、Maven Wrapper；E3 需要真实 PostgreSQL（Testcontainers）；
  E4 需要完整 Compose 栈（postgres/backend/worker）；
- SKIP 判定：Docker 守护进程不可用时 E3/E4 不可 SKIP 为 PASS，只能交接为
  `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-024-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-024.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 `REQ-AUD-001` 要求拒绝 100% 审计，但本卡范围只含三个已声明事件，
  与 `TASK-P0BR-025` 的边界需要澄清）；
- 存在未关闭的产品决策（例如人工重试是否重置 `attempts` 会影响告警与运维语义）；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2/V9 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试（例如为让审计出现而放宽 `job:manage` 判定）；
- 无法产生任务要求的最低证据等级（E4 必须在真实 Compose 栈取得，
  不得以直接调用应用服务代替「经 Worker 进入 DEAD」）；
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

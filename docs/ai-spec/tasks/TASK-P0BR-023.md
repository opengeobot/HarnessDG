---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-023
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 8ed02ef7e2664b09d878c69f09506027696a764b
stageGatePassed: true
stageGateEvidence: 用户批准方向。依据 READY 需求 REQ-JOB-001（PostgreSQL 可靠任务）与 ACCEPTED 决策 DEC-003（P0-B 必须逐项重验）、DEC-013（Compose 为功能验收环境），补齐可靠任务的崩溃恢复与重试正确性；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-JOB-001
acceptanceScenarios:
  - AC-P0B-JOB-002
  - AC-P0B-JOB-003
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-JOB-002|E4|真实 Compose 栈：Worker 领取后强制终止，租约到期后被重新领取；job_task 经 RETRY/RUNNING 回到 SUCCEEDED，资产建仓不重复且幂等短路生效
  - AC-P0B-JOB-003|E4|真实 Compose 栈：停掉 Gitea 使建仓依赖持续失败，任务按指数退避+抖动进入 RETRY_WAIT；恢复 Gitea 后下一次重试 SUCCEEDED，job_attempt 逐次可查且 errorCode 为可重试码
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；Worker 沿用任务行的 principalId/traceId 上下文
  - AUTHZ|N/A - 不新增或放宽权限；人工重试/取消的授权属 TASK-P0BR-024
  - DB_FILTER|N/A - 不涉及查询条件或授权下推；领取仍用 FOR UPDATE SKIP LOCKED
  - STATE|沿用 JobStatus 与 job_attempt 的既有 CHECK 约束；仅修正 attempts/attempt_no 的单调性，不新增状态值
  - IDEMPOTENCY|Handler 必须按业务 dedupe key 幂等；被强杀后重领不得产生重复建仓或重复副作用
  - CONSISTENCY|重试调度只依赖 job_task 持久化状态，不得引入纯内存调度保证可靠性
  - ERRORS|抽离可重试判定：仅 ErrorCode.retryable() 为真的错误进入 RETRY_WAIT，不可重试错误进入 DEAD；错误信息落库前必须经 SensitiveDataMasker 脱敏
  - AUDIT|N/A - DEAD 与人工操作的审计落点属 TASK-P0BR-024
  - NOTIFICATION|N/A - JOB_DEAD 通知已由 JobNotificationAdapter 实现，本卡不改变其触发条件
  - TAXONOMY_I18N|N/A - 不涉及受控值或文案
  - CONFIG|租约时长改为受控配置项并给出安全默认值；不得引入按环境分叉的退避逻辑
  - OBSERVABILITY|沿用 job.processed 与 job.queue.size 指标；重试与恢复必须可由 job_attempt 与任务行列取证，不新增依赖内存的计数
  - SECRETS|N/A - 不接触 Secret；job payload 与 attempt 摘要不得包含凭据，证据不得包含 Token
allowedPaths:
  - backend/src/main/java/com/aihub/job
  - backend/src/main/resources/application.yml
  - backend/src/main/resources/db/migration/V27__job_attempt_trace.sql
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/tasks/TASK-P0BR-023.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-023-001.yaml
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR-023-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-023.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-023.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-20T05:03:23Z
---

# TASK-P0BR-023：可靠任务的崩溃恢复与退避重试正确性

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-20

## 1. 目标

被强制终止的 Worker 所留下的任务能按租约语义被重新领取并最终成功，且可重试依赖错误按指数退避
重试、依赖恢复后成功；这两条路径都能在真实 Compose 栈上被观察到确定的终态与 attempt 记录。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001]
acceptanceScenarios: [AC-P0B-JOB-002, AC-P0B-JOB-003]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V27
```

### 缺陷与缺口事实（已复核）

以下均在当前 HEAD 上直接读取源码确认：

1. **领取机制本身正确**：`JdbcJobRepository.claimNext` 已使用
   `FOR UPDATE SKIP LOCKED`，且领取条件包含
   `OR (status = 'RUNNING' AND leased_until < ?)`，即过期租约可被重新领取，
   不依赖额外的 reaper。
2. **租约时长硬编码**：`JobWorker.tick()` 以字面量 `60` 调用 `claimNext(workerId, now, 60)`，
   没有配置项。租约不可缩短，使「强杀后重领」在真实栈上无法在可接受时间内被观察。
3. **可重试判定未生效**：`JobWorker.handleFailure` 仅比较
   `attemptNo >= job.maxAttempts()`，从不读取 `ErrorCode.retryable()`。
   与 `INV-JOB-006`「只有定义为 retryable 的错误进入 RETRY_WAIT」不一致。
4. **attempt 序号可重复**：`attempts` 只在失败时自增，领取时不自增，
   而 `attemptNo = job.attempts() + 1`。被强杀后重领会复用同一个 `attempt_no`，
   与 `INV-JOB-005`「attempt 单调递增」不一致；`job_attempt` 上也没有
   `(job_id, attempt_no)` 唯一约束。
5. **被强杀的尝试无记录**：`job_attempt` 无任何行能反映「领取后被终止」，
   因此 JOB-002 的回收事实在数据上不可观测。
6. **attempt 无 trace 关联**：`job_attempt` 没有 `trace_id` 列，
   只能通过 `job_task.trace_id` 间接回查，attempt 级追踪不完整。
7. **调度器无自愈**：`JobConfiguration.JobWorkerScheduler` 以
   `scheduleAtFixedRate` 调度 `tick()`，而 `tick()` 内的 `claimNext` 与
   `handleFailure` 的落库调用在 try 之外。`ScheduledThreadPoolExecutor` 在任务抛出
   未捕获异常后会静默取消该任务且无 error handler，Worker 将永久停摆——
   与类注释「tick 内部捕获所有异常，绝不向外抛出导致调度中断」不符。
8. **错误摘要未脱敏**：`JobWorker.maskMessage` 只做 1000 字符截断，
   未调用仓库既有的 `SensitiveDataMasker`，与 `REQ-JOB-001`
   「attempt 记录时间、结果、errorCode、脱敏摘要」不一致。
9. **E3/E4 证据均缺失**：`EVD-P0B-V01-V12.yaml` 已把
   `AC-P0B-JOB-001..005` 整体列为 `notProven`；现有
   `JobWorkerTest` 为纯 Mockito（backoff 被 mock），`JdbcJobRepositoryIT`
   只验证首次领取，没有任何测试覆盖租约过期重领或生产参数下的退避时序。

## 3. 范围

### 允许修改

- 模块：`com.aihub.job`（domain / application / infrastructure）
- 文件或目录：
  - `backend/src/main/java/com/aihub/job/**`
    （领取租约、attempt 记账、可重试判定、异常边界、摘要脱敏）
  - `backend/src/main/resources/application.yml`（租约时长配置项与安全默认值）
  - `backend/src/main/resources/db/migration/V27__job_attempt_trace.sql`
    （`job_attempt.trace_id` 与 `(job_id, attempt_no)` 唯一约束）
  - `backend/src/test/java/com/aihub/job/**`（E2/E3 回归与故障注入）
- 契约：无（本卡不改变 REST/MCP 响应结构或错误码集合）
- Migration：新增 `V27`；不得修改 V1/V2/V9 等已应用迁移
- 页面：无
- Fixture/Verify：不改动 `deploy/compose` 脚本；E4 以独立取证脚本执行

### 明确不在范围

- DEAD 终态的审计落点、人工重试/取消的审计与前置条件验证 → `TASK-P0BR-024`；
- 告警规则与 Grafana 指标名对齐 → `TASK-P0BR-030`；
- 把 `JOB_LEASE_RECONCILE` 等既有 Reconciler 接入调度（属对账能力，另立任务）；
- `system_config` 中 `job.dvc.maxRetries` / `job.webhook.maxRetries` 的接线；
- OpenAPI 中 `/system/jobs` 的 404 缺失与 `Idempotency-Key` 必填口径不一致（既存契约缺陷）；
- 前端任务管理页面改动。

### 禁止变化

- 不改变的 API/状态/权限：`JobStatus` 取值集合、`job_task`/`job_attempt` 的既有 CHECK 约束、
  `FOR UPDATE SKIP LOCKED` 的原子领取语义、`job:read`/`job:manage` 权限判定；
- 不允许的依赖升级或重构：不新增依赖，不引入内存队列/线程池替代持久化任务状态，
  不引入 `@Async` 或裸线程；
- 不允许的兼容破坏：不得降低 `max_attempts` 下界，不得让不可重试错误无限重试，
  不得删除既有 `job.processed`/`job.queue.size` 指标。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-JOB-002` | 运维 | 任务已被 Worker 领取，进程随后被强制终止 | 等待租约过期 | 任务被其他 Worker 重新领取，不出现两个 Worker 并行执行同一条任务 | `E4` |
| `AC-P0B-JOB-002` | 运维 | 上述任务对应的资产建仓已完成但任务未标记成功 | 重领后再次执行 Handler | Handler 幂等短路，不产生第二个仓库，任务最终 SUCCEEDED | `E4` |
| `AC-P0B-JOB-002` | 审查者 | 存在一条被终止的历史尝试 | 查询任务与 attempt | 被终止的尝试有独立记录，`attempt_no` 严格递增不重复 | `E3` |
| `AC-P0B-JOB-003` | 运维 | 建仓依赖（Gitea）不可用，错误码可重试 | 任务执行失败 | 进入 RETRY_WAIT；`next_run_at` 落在指数退避+抖动区间内 | `E4` |
| `AC-P0B-JOB-003` | 运维 | 同一任务已多次失败 | 依赖恢复后再次执行 | 下一次尝试 SUCCEEDED，期间没有人工干预 | `E4` |
| `AC-P0B-JOB-003` | 审查者 | 上述任务的历史 | 查询 attempt 与任务行 | 每次尝试均可按 trace 关联，errorCode 与脱敏摘要完整 | `E3` |
| 边界 | 审查者 | 错误码 `retryable=false` | 任务执行失败 | 直接进入 DEAD，不进入 RETRY_WAIT（不可重试错误不得被退避重试） | `E2` |
| 边界 | 审查者 | 退避参数取到上限 | 计算下一次运行时间 | `next_run_at` 被 cap 截断，且恒晚于当前时间 | `E2` |
| 失败 | 运维 | 数据库瞬时不可用导致领取或落库抛异常 | 调度器下一次 tick | 调度任务不被取消，Worker 在后续 tick 自愈并继续工作 | `E3` |

必须覆盖正常、边界、失败、越权。本卡无越权路径（人工操作权限属 `TASK-P0BR-024`），
但须覆盖恢复与并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；Worker 沿用任务行持久化的 principalId/traceId |
| Permission/Scope/ACL | N/A：不新增或放宽权限；人工重试/取消授权属 `TASK-P0BR-024` |
| 数据库权限下推 | N/A：不涉及查询条件或授权下推 |
| 状态前置条件 | 沿用 `JobStatus` 与 CHECK 约束；只允许 PENDING/RETRY_WAIT 与过期 RUNNING 被领取 |
| 幂等与并发 | 同一租约同一时刻只属于一个 Worker；重领后 Handler 必须幂等，不得重复副作用 |
| 事务与外部一致性 | 重试调度只依赖 `job_task` 持久化状态；不得用纯内存调度保证可靠性 |
| 错误码 | 仅 `ErrorCode.retryable()` 为真进入 RETRY_WAIT，否则进入 DEAD；不新增错误码 |
| 审计事件 | N/A：DEAD 与人工操作审计属 `TASK-P0BR-024` |
| 通知 | N/A：`JOB_DEAD` 通知已实现，本卡不改变其触发条件 |
| 字典/标签/i18n | N/A：不涉及受控值或面向用户的文案 |
| 配置 | 租约时长与退避参数改为受控配置项并给出安全默认值，不得按环境分叉逻辑 |
| 指标/Trace/告警 | 沿用既有指标；attempt 必须补齐 trace 关联，使重试与恢复可回溯 |
| Secret/日志脱敏 | attempt 摘要落库前必须经 `SensitiveDataMasker`；证据不得包含凭据或 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无。本卡不改变 `/system/jobs` 的请求/响应结构
   与错误码集合，`JobView` 字段保持兼容。
2. 示例请求、响应和错误：无新增；既有 `GET /system/jobs` 足以观察 RETRY_WAIT/SUCCEEDED。
3. 新增 Flyway 文件：`V27__job_attempt_trace.sql`，内容为
   `job_attempt` 增加 `trace_id` 列，并补 `(job_id, attempt_no)` 唯一约束。
   必须为前向迁移；已应用迁移一律不改。
4. 存量清点与回填：唯一约束建立前须确认存量 `(job_id, attempt_no)` 无重复；
   若有重复，须在迁移中以确定规则消歧（不得随机改名），并在 Evidence 中记录清点结果。
5. 索引、唯一约束、外键及理由：唯一约束用于强制 `INV-JOB-005`
   的 attempt 单调性；不新增外键（保持与 V9 现状一致）。
6. 兼容窗口与失败恢复：新增列可空，旧行读回为 null，不破坏既有查询；
   迁移失败即回滚，不产生部分结构。
7. 生成类型更新：无前端类型变更（`JobView` 未变）。

## 7. 实施步骤

- [ ] 契约及契约测试（本卡无契约变更）
- [ ] Migration 及空库/升级迁移测试
- [ ] Application/Domain 用例
- [ ] REST/MCP/Worker Adapter
- [ ] 前端页面与状态（本卡不接线前端）
- [ ] 正常/边界/失败/越权/恢复测试
- [ ] Compose Fixture/Verify
- [ ] Runbook/追踪矩阵

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 把租约时长与退避参数改为受控配置项（安全默认值保持现有行为），使过期重领可在秒级观察；
2. 抽离「可重试判定」，按 `ErrorCode.retryable()` 决定 RETRY_WAIT 或 DEAD；
3. 修正 attempt 记账：重领时递增而非复用 `attempt_no`，并为被终止的尝试留下记录；
4. 为 `tick()` 建立异常边界，使领取与落库异常不会取消调度任务；
5. `maskMessage` 改用既有 `SensitiveDataMasker`；
6. 新增 `V27` 迁移补齐 attempt 的 trace 关联与唯一约束；
7. E3：Testcontainers 真实 PostgreSQL 驱动真实 Worker 与真实 Handler，
   以真实 runnable 覆盖重领、退避、恢复与不可重试分支；
8. E4：真实 Compose 栈上以「终止 Worker + 停用 Gitea」做故障注入，
   观察终态、attempt 与退避区间。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；若本机 Docker 不可用，依赖真实依赖的 IT 记为 SKIP 并在
  Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-JOB-002`、`AC-P0B-JOB-003`；
- 所需依赖：JDK 21、Maven Wrapper；E3 需要真实 PostgreSQL（Testcontainers）；
  E4 需要完整 Compose 栈（postgres/gitea/backend/worker）；
- SKIP 判定：Docker 守护进程不可用时 E3/E4 不可 SKIP 为 PASS，只能交接为
  `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-023-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；
本卡不改动 `deploy/compose` 文件，但 E4 依赖 Compose 栈运行。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-023.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 `INV-JOB-005` 的 attempt 单调性与既有数据无法在不破坏兼容的前提下同时满足）；
- 存在未关闭的产品决策（例如租约时长是否允许低于 60 秒）；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2/V9 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试（例如为让重领通过而放宽 `FOR UPDATE SKIP LOCKED` 或删除唯一约束）；
- 无法产生任务要求的最低证据等级（E4 必须在真实 Compose 栈取得，
  不得以 Mockito 单测冒充「崩溃恢复」）；
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

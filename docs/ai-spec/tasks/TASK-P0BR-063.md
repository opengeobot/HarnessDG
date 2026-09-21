---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-063
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 0e6b9ed672b0ef279e0079665bf711d48449c7ba
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「另立任务修复（推荐）」）。依据 READY 需求 REQ-JOB-001（PostgreSQL 可靠任务）与 ACCEPTED 决策 DEC-003（P0-B 必须逐项重验）、DEC-013（Compose 为功能验收环境），修复任务入队时 trace 上下文未被填充的缺陷，使 TASK-P0BR-023 的 AC-P0B-JOB-003「Trace/attempt 完整」子判据可达并回栈复验收尾；属 ADR-0002 允许的「合同、授权、治理、审计、一致性整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-JOB-001
acceptanceScenarios:
  - AC-P0B-JOB-003
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-JOB-003|E4|真实 Compose 栈（Gitea 已接通）：经 REST 创建资产后，job_task.trace_id 非空且等于触发请求的 X-Trace-Id 响应头；作业成功后的每条 job_attempt.trace_id 与任务行一致非空；VERSION_PUBLISH 入队路径同样非空；随后撤销 TASK-P0BR-023 的 EVD notProven 中该项并复跑 023 E4 至全绿
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；trace 只从既有共享 PrincipalContext 读取，不解析任何 Token
  - AUTHZ|N/A - 不新增或放宽权限；入队仍由既有 asset:create 与版本发布授权触发
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|不改变 JobStatus/ProvisioningStatus 取值与约束；仅填充既有可空列 trace_id 的值
  - IDEMPOTENCY|不改变入队与领取的幂等语义；trace 是关联标识，不参与去重判定
  - CONSISTENCY|入队与业务事务原子性不变；trace 必须在同一事务内从请求上下文取值，禁止异步阶段猜测生成
  - ERRORS|不新增错误码；PrincipalContext 缺失（如 Worker/调度线程内入队）时以确定性生成值兜底并记录 debug 日志，不得静默留空
  - AUDIT|正面关联：ASSET_PROVISIONED Outbox 事件与审计的 traceId 随本修复获得真实值；不新增或删除审计事件
  - NOTIFICATION|N/A - 不改变 JOB_DEAD 通知触发条件
  - TAXONOMY_I18N|N/A - 不涉及受控值或面向用户文案
  - CONFIG|无新配置项；trace 来源固定为共享 PrincipalContext，不提供按环境分叉的开关
  - OBSERVABILITY|核心主题：job_task/job_attempt 的 trace_id 必须真实填充，使「单请求触发 DB+依赖+后台任务」可按 traceId 关联（AC-P0B-AUD-004 的基础）；日志沿用既有 MDC trace_id 字段
  - SECRETS|trace/request 标识不是 Secret，可入日志与证据；证据仍不得包含 Token、口令或预签名 URL
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application
  - backend/src/main/java/com/aihub/version/application
  - backend/src/main/java/com/aihub/job/application
  - backend/src/test/java/com/aihub/asset
  - backend/src/test/java/com/aihub/version
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-063.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-063-001.yaml
  - docs/ai-spec/tasks/TASK-P0BR-023.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-023-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V9__job.sql
  - backend/src/main/resources/application.yml
  - contracts
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-063-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-063.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-063.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/trace-e4-063.ps1
  - powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/job-e4-023.ps1
approvedBy: User
approvedAt: 2026-09-20T08:55:00Z
---

# TASK-P0BR-063：修复任务入队时 trace 上下文未被填充（job_task/job_attempt.trace_id 恒为 NULL）

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-20

## 1. 目标

由用户请求触发的可靠任务在入队时携带真实 trace 上下文：`job_task.trace_id` 等于
触发请求的 traceId，每条 `job_attempt.trace_id` 与任务行一致非空；
使 `TASK-P0BR-023` 的 `AC-P0B-JOB-003`「Trace/attempt 完整」子判据在真实栈上可达并闭环。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001]
acceptanceScenarios: [AC-P0B-JOB-003]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: "无（本卡无迁移；trace_id 列与唯一约束已由 V27 建好）"
```

### 缺陷事实（已复核，2026-09-20，真实 Compose 栈）

以下由 `TASK-P0BR-023` 的 E4 复验暴露，并在 `0e6b9ed` 上直接读源码确认：

1. **全部 trace_id 为 NULL**：实测最近 30 分钟内 19/19 条 `job_attempt` 与全部
   `job_task` 的 `trace_id` 均为 NULL。`REQ-JOB-001` 要求
   「任务传递 trace/principal/resource context」，当前在两条入队路径上均不成立。
2. **根因点 A（asset）**：`AssetApplicationService.createProvisionJob`
   （`backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java` L474-482）
   以 `new Job(..., null, principalId, ...)` 直接构造并 `jobRepository.insert(job)`，
   `traceId` 实参硬编码为 `null`。
3. **根因点 B（version）**：`PublishApplicationService` L187 调用
   `jobApplicationService.enqueue("VERSION_PUBLISH", payload, reviewerId, null, versionId, 3)`，
   `traceId` 形参同样传 `null`。
4. **透传链路本身正确**：`JobWorker` 以 `job.traceId()` 构造 `JobContext` 并把同一值写入
   每条 attempt（成功/失败/DEAD/补记的 JOB_LEASE_EXPIRED），
   `RepositoryProvisionJobHandler` 把 `context.traceId()` 写入 Outbox 事件。
   即：只要入队时填了 trace，attempt 与事件的 trace 关联自动成立，无需改 `com.aihub.job.infrastructure`。
5. **trace 来源已存在**：`PrincipalContextFilter` 从 W3C `traceparent` 提取或生成 32 位
   hex traceId，写入 `PrincipalContextHolder` 与 MDC，并以 `X-Trace-Id` 响应头回显。
   业务模块禁止解析 Token，但允许读取共享 principal context（平台规则原文即「use the shared
   principal context」），大量既有服务已按 `PrincipalContextHolder.current().map(PrincipalContext::traceId)` 使用。
6. **既有测试未覆盖**：没有任何测试断言入队后的 `job_task.trace_id` 非空；
   023 的 E4 脚本原先用 `IS DISTINCT FROM` 做一致性断言，两侧同为 NULL 时空洞通过
   （已由 023 收尾提交补上「必须已填充」断言，当前显式 FAIL）。
7. **E4 实施中新暴露（VERSION_PUBLISH 路径阻塞缺陷，经用户批准一并修复）**：
   `PublishApplicationService.submitDecision` 的 APPROVE/REJECT 分支以
   `jdbcTemplate.update(..., Instant.now(), ...)` 绑定 `decided_at` 参数；
   PG JDBC 驱动无法为 `java.time.Instant` 实参推断 SQL 类型
   （`Can't infer the SQL type to use for an instance of java.time.Instant`），
   真实栈上审批接口必然 500 且事务回滚，VERSION_PUBLISH 任务从不会入队——
   若不修复，本卡 T3 场景在该链路上不可达。已改为 SQL `now()` 求值。
   同型缺陷还存在于 `PublishJobHandler`（4 处）与 `PhysicalRecycleJobHandler`（1 处），
   两者不在本卡 allowedPaths 内，登记为后续任务，不在本卡修复。
8. **E4 实施中另暴露（不在本卡修复，仅登记）**：`submitPublishRequest` 要求权限码
   `asset:write`，但该码从未注册进 `iam_permission`（注册的是 `asset:manage`），
   REST 提交入口对任何主体都 fail-closed 403。E4 夹具因此以 SQL 置备 publish_request 行，
   使被测的 `submitDecision → enqueue` trace 路径仍经真实 HTTP 触发。
   该权限码不一致属授权/契约缺陷，需另立任务（改码或注册权限均需独立验收）。

## 3. 范围

### 允许修改

- 模块：`asset.application`、`version.application`（入队点填充 trace）、
  `job.application`（`enqueue` 增加「调用方未提供时从共享上下文兜底」的单一入口逻辑，
  使未来新增调用点不再复现同类缺陷）
- 对应测试目录；`TASK-P0BR-023` 的卡与 EVD（撤销 notProven 中该项并记录复验结果）
- 契约：无。Migration：无。页面：无。
- Fixture/Verify：不改动 `deploy/compose` 与 `verify.ps1`；E4 用独立取证脚本

### 明确不在范围

- 不引入 OpenTelemetry/SkyWalking 等追踪后端；沿用平台自有 traceId 约定；
- 不改 `PrincipalContextFilter` 的 trace 解析规则与 `X-Trace-Id` 契约；
- 不回填历史 NULL 行（V27 列本就可空，历史行无真实 trace 来源，伪造回填违反审计原则）；
- 不改 Worker 侧 attempt 写入逻辑（已验证正确）；
- Worker 线程内再入队（如 Reconciler 派发新任务）的 span link 属 `TASK-P0BR-030`。

### 禁止变化

- 不得把 trace 改为随机生成而不关联触发请求（那会使 AC-P0B-AUD-004 的关联语义失效）；
- 不得在业务模块解析 JWT（必须经 `PrincipalContextHolder`）；
- 不得改变 `Job`/`JobView` 字段集合与 `/system/jobs` 响应结构；
- 不得删除或放松 V27 的唯一约束。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-JOB-003` | 运维 | 客户端带/不带 traceparent 创建资产 | 任务入队并被 Worker 执行成功 | `job_task.trace_id` == 该请求 `X-Trace-Id` 且非空；每条 `job_attempt.trace_id` 与任务一致非空 | `E4` |
| `AC-P0B-JOB-003` | 运维 | 发布版本触发 VERSION_PUBLISH | 任务入队 | `job_task.trace_id` 非空且等于请求 traceId | `E4` |
| `AC-P0B-JOB-003` | 审查者 | 023 的 E4 脚本 | 修复后重跑 | 「trace 已填充」断言由 FAIL 转 PASS，且全脚本 28/28 全绿 | `E4` |
| 边界 | 审查者 | 无请求上下文（如测试内直接调用）时入队 | enqueue 执行 | trace 取共享上下文值；上下文缺失时生成确定性非空值（不可为 NULL） | `E2` |
| 边界 | 审查者 | 调用方显式传入 traceId | enqueue 执行 | 尊重传入值，不被兜底覆盖 | `E2` |
| 失败 | 审查者 | attempt 一致性查询在两侧均 NULL 时 | 执行断言 SQL | 必须判为不满足（非空洞通过） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证；trace 只读共享 PrincipalContext |
| Permission/Scope/ACL | N/A：不新增或放宽权限 |
| 数据库权限下推 | N/A |
| 状态前置条件 | 不改变 JobStatus；仅填充既有可空列的值 |
| 幂等与并发 | trace 不参与去重；入队原子性不变 |
| 事务与外部一致性 | trace 在同一事务内从请求上下文取值 |
| 错误码 | 不新增；上下文缺失时兜底生成并 debug 日志 |
| 审计事件 | Outbox/审计的 traceId 随本修复获得真实值 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 无新配置项 |
| 指标/Trace/告警 | 核心：job 链路 trace 可关联，支撑 AC-P0B-AUD-004 |
| Secret/日志脱敏 | trace 非 Secret；证据仍不含 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 变更：无（`X-Trace-Id` 响应头为既有行为）。
2. 新增 Flyway 文件：无。
3. 存量清点：历史 NULL 行保留（见 §3 不在范围）；本卡只对**新入队**任务生效。
4. 生成类型更新：无。

## 7. 实施步骤

- [x] 契约及契约测试（本卡无契约变更）
- [x] Migration 及空库/升级迁移测试（本卡无迁移）
- [x] Application 用例（入队点 trace 填充 + enqueue 单一兜底入口；E4 中按用户批准
  一并修复 `submitDecision` 的 Instant 绑定缺陷——§2 事实 7）
- [x] Worker/Handler 验证透传（只读验证，不改代码；E4 断言 attempt 与 Outbox trace 一致）
- [x] 正常/边界/失败测试（E2：`JobApplicationServiceEnqueueTest` 5 项 + 既有套件回归）
- [x] E4 取证（trace-e4-063 全绿 + 复跑 job-e4-023 28/28 全绿）+ 更新 023 卡与 EVD
- [x] Runbook/追踪矩阵（023 EVD notProven 已撤销，闭环）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 在 `JobApplicationService.enqueue` 收敛兜底逻辑：`traceId == null` 时读
   `PrincipalContextHolder.current().map(PrincipalContext::traceId)`；仍为空则生成
   32 位 hex 确定格式值（与 filter 同规则）并记 debug 日志。
2. `AssetApplicationService.createProvisionJob` 改为经 `enqueue`（或同规则取值），
   删除硬编码 null；`PublishApplicationService` L187 的 null 实参同样收敛。
3. E2：新增/扩展测试断言两条路径入队后 `trace_id` 非空、显式传入不被覆盖、
   无上下文时兜底非空；用 SQL 语义测试固化「双 NULL 不算一致」。
4. E4：`trace-e4-063.ps1` 在真实栈（交付基线，Gitea 接通）创建资产，
   比对 HTTP 响应 `X-Trace-Id` 与 `job_task/job_attempt.trace_id`；
   触发一次 VERSION_PUBLISH 同样断言。
5. 复跑 `.trae/evidence/job-e4-023.ps1`（023 环境覆盖下）至 28/28 全绿；
   更新 023 卡 §7.2 与 EVD 的 notProven（撤销 trace 项，改为 proven 引用 063 证据）。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/trace-e4-063.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/job-e4-023.ps1
```

每条命令声明：

- 预期退出码：0；Docker 不可用时 E4 不可 SKIP 为 PASS，只能交接 IMPLEMENTED_UNVERIFIED；
- 覆盖的 AC ID：`AC-P0B-JOB-003`；
- 所需依赖：JDK 21、Maven Wrapper、完整 Compose 栈与真实 `GITEA_SERVICE_TOKEN`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-063-001.yaml`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-063.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 需要改变 ADR 固定的技术路线（例如引入外部 APM/追踪后端）；
- 需要修改已应用 Migration 或放松 V27 约束；
- 需要在业务模块解析 Token 才能获得 trace（违反平台规则，必须停止并报告）；
- 无法产生 E4（不得以单测冒充「真实栈 trace 可关联」）；
- 复跑 023 E4 出现新的 FAIL 且根因在本卡 allowedPaths 之外。

## 10. 完成报告

```text
Completed:
  用户请求触发的可靠任务在入队时携带真实 trace 上下文：
  enqueue 收敛单一兜底入口（显式 > 共享 PrincipalContext > 32-hex 兜底 + debug 日志），
  建仓任务改经 enqueue，VERSION_PUBLISH 路径经审批请求自然贯通。
  真实栈 E4（trace-e4-063，交付基线）18/18 断言 ALL PASS：注入 traceparent 时
  job_task.trace_id 等于注入值、未注入时等于响应 X-Trace-Id，每条 attempt 一致非空，
  Outbox 事件携带同一 trace，VERSION_PUBLISH 同样贯通。
  复跑 TASK-P0BR-023 的 E4：28/28 ALL PASS，其 trace 断言由 FAIL(populatedRows=0)
  转 PASS(populatedRows=2)；023 的 EVD notProven 已撤销，状态脱离 IMPLEMENTED_UNVERIFIED。
  E4 中按用户批准一并修复 submitDecision 的 Instant 绑定缺陷（§2 事实 7，
  审批接口原在真实栈必然 500，VERSION_PUBLISH 从不入队）。

Changed files:
  - backend/src/main/java/com/aihub/job/application/JobApplicationService.java（resolveTraceId）
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
    （JobRepository → JobApplicationService，PROVISION_MAX_ATTEMPTS=5）
  - backend/src/main/java/com/aihub/version/application/PublishApplicationService.java
    （decided_at 改 SQL now() 两处；trace 收敛注释；删未用 Instant import）
  - backend/src/test/java/com/aihub/job/application/JobApplicationServiceEnqueueTest.java（新增，5 项）
  - backend/src/test/java/com/aihub/asset/application/AssetApplicationServiceTest.java（协作者与 verify 改 enqueue）
  - docs/ai-spec/02-delivery/work-breakdown.md（登记 063 行）
  - docs/ai-spec/tasks/TASK-P0BR-063.md（本卡）
  - docs/ai-spec/tasks/evidence/EVD-P0BR-063-001.yaml（新增，E4）
  - docs/ai-spec/tasks/TASK-P0BR-023.md + evidence/EVD-P0BR-023-001.yaml（撤销 notProven、回栈复验记录）

Requirements and scenarios satisfied:
  - REQ-JOB-001 / AC-P0B-JOB-003「Trace/attempt 完整」（E4）：见上；
    边界（上下文缺失兜底、显式值优先）为 E2（5 项 + 鉴别力自证 4/5 FAIL）。

Contract/database changes:
  - 无契约、无迁移、无配置项变更；仅填充既有可空列 trace_id 的值。
  - 行为变化：/system/publish 审批接口由「必然 500」修复为可用（Instant 绑定缺陷）。

Validation run and results:
  - validate-spec.ps1 -> PASS；validate-task-card.ps1（本卡）-> PASS；-CheckChangedPaths -> PASS
  - cd backend; ./mvnw.cmd -o verify -> 525 项，2 失败、6 跳过：
    AssetCatalogIT（@SpringBootConfiguration 搜索，既有共性缺陷）；
    AssetFacetConsistencyIT 全量运行时 1 项 facet 计数失败、单跑 9/9 PASS
    （共享 Testcontainers 资源的 flake，本卡未触碰 facet/search）。
    均与本卡无关，不记为 PASS。本卡相关套件：EnqueueTest 5/5、AssetApplicationServiceTest 24/24。
  - .trae/evidence/trace-e4-063.ps1（交付基线）-> 18/18 ALL PASS（exit 0）
  - .trae/evidence/job-e4-023.ps1（023 覆盖复跑）-> 28/28 ALL PASS（exit 0）

Evidence artifacts:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-063-001.yaml（E4）
  - .trae/evidence/trace-e4-063.txt、.trae/evidence/job-e4-023.txt（全文输出）

Validation not run and reasons:
  - 契约破坏检查：本卡无契约变更；@redocly/cli diff 为已登记工具链缺陷。
  - pnpm lint/typecheck/test/build：未触碰 frontend/。

Remaining risks or follow-ups:
  - 同型 Instant 绑定缺陷仍在 PublishJobHandler（4 处）与 PhysicalRecycleJobHandler（1 处）
    ——不在本卡 allowedPaths，另立任务（连带补 PublishJobHandler 的 decided_at 断言）。
  - submitPublishRequest 要求未注册的权限码 asset:write（§2 事实 8）→ 另立任务
    （统一改用 asset:manage 或前向迁移注册权限码，需契约决策）。
  - 历史 NULL trace 行不回填（§3 不在范围）；Worker 线程内再入队的 span link 属 TASK-P0BR-030。
  - AssetFacetConsistencyIT 的 flake 与 16 个 IT 的 @SpringBootConfiguration 缺陷同属
    既有测试治理任务范围。
```

报告中必须区分证据等级，并给出 023 的「trace 已填充」断言由 FAIL 转 PASS 的对照，
以及 023 EVD notProven 撤销后的最终状态。

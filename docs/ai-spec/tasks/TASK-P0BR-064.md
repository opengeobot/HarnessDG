---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-064
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: bbc170ba6be6c14c709672b512584c2e889abb11
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「批准立卡并实施（推荐）」）。依据 READY 需求 REQ-AUTH-001（统一实时授权判定）与 REQ-AUD-001（不可变业务审计，明确「任何分类都不允许仅 warn 后静默丢失」），以及 ACCEPTED 决策 DEC-003（P0-B 必须逐项重验）、DEC-013（Compose 为功能验收环境），修复 TASK-P0BR-063 的 E4 暴露的发布链路缺陷簇：未注册权限码 asset:write 使三处入口对任何主体恒 403、Handler 内 Instant 参数绑定使 decided_at 更新失败并使审计/告警 INSERT 被 catch 静默丢弃；属 ADR-0002 允许的「合同、授权、治理、审计、一致性整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-AUTH-001
  - REQ-AUD-001
acceptanceScenarios:
  - AC-P0B-AUTH-004
  - AC-P0B-AUD-001
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-AUTH-004|E4|真实 Compose 栈：持 asset:manage 的主体经 REST 提交发布请求返回 2xx、审批决策返回 200、VERSION_PUBLISH 任务被 Worker 执行至 SUCCEEDED、版本弃用/归档各返回 200——修复前三处入口要求未注册权限码 asset:write，对任何主体恒 403
  - AC-P0B-AUD-001|E4|真实 Compose 栈：发布成功后 audit_log 存在该 requestId 的 PUBLISH_SUCCEEDED 行（result/errorCode/主体/资源/trace 正确）且 publish_request.decided_at 非空、system_alert 结构可用——修复前 INSERT 绑定 java.time.Instant 抛异常被 catch 吞掉，审计与告警行静默丢失
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径与 Token 校验；仅改变授权判定使用的权限码取值
  - AUTHZ|三处 requirePermission 由未注册的 asset:write 对齐到契约声明的 asset:manage（OpenAPI submitPublishRequest/deprecateVersion/archiveVersion 的 x-required-permissions 均为 [asset:manage]）；不放宽任何判定——asset:manage 已在 Permission Catalog 注册且为内置角色实际持有的码；无该码的主体必须仍返回 403
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|不改变 VersionStatus/PublishRequestStatus/JobStatus 取值与 CHECK 约束；仅使既有状态迁移在真实栈上可达
  - IDEMPOTENCY|不改变发布请求与决策的幂等语义；时间值由应用侧参数改为数据库 now() 求值，不影响去重判定
  - CONSISTENCY|发布 Saga 的 decided_at 与 status 仍在同一条 UPDATE 内原子写入；审计与告警写失败的行为差异必须可观察（不再静默丢行）
  - ERRORS|不新增错误码；修复前 submitPublishRequest 恒 403、decisions 成功后 Worker 侧 decided_at 更新抛异常，均改为正常路径
  - AUDIT|核心：recordAuditEvent 的 INSERT 必须真实落行（不再因类型推断异常被 catch 吞掉）；直写 audit_log 而非经共享 AuditService 属旁路缺陷，本卡只修复其失败模式并登记为后续任务，不在本卡重构
  - NOTIFICATION|N/A - 不改变通知触发条件
  - TAXONOMY_I18N|N/A - 不涉及受控值或面向用户文案
  - CONFIG|无新配置项
  - OBSERVABILITY|SAGA_COMPENSATION_FAILED 告警 INSERT 必须真实可用（fired_at 由数据库求值）；审计行的 trace_id 沿用 TASK-P0BR-063 已贯通的 trace 上下文
  - SECRETS|N/A - 不接触 Secret；审计摘要与告警 detail 仍只含治理字段
allowedPaths:
  - backend/src/main/java/com/aihub/version/application/PublishApplicationService.java
  - backend/src/main/java/com/aihub/version/infrastructure/PublishJobHandler.java
  - backend/src/main/java/com/aihub/asset/infrastructure/PhysicalRecycleJobHandler.java
  - backend/src/test/java/com/aihub/version
  - backend/src/test/java/com/aihub/asset
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-064.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-064-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V10__audit.sql
  - backend/src/main/resources/application.yml
  - contracts
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-064-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-064.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-064.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
  - powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/publish-e4-064.ps1
approvedBy: User
approvedAt: 2026-09-21T14:20:00+08:00
---

# TASK-P0BR-064：修复发布链路的未注册权限码与 Handler 内 Instant 绑定缺陷

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-21

## 1. 目标

发布链路（提交发布请求 → 审批决策 → VERSION_PUBLISH 任务执行 → 版本弃用/归档）在真实
Compose 栈上端到端可用，且发布成功的审计与失败告警**真实落库**：

- 三处入口的权限判定使用已在 Permission Catalog 注册、且与 OpenAPI 声明一致的 `asset:manage`；
- `PublishJobHandler` / `PhysicalRecycleJobHandler` 的时间列不再以 `java.time.Instant`
  作为 JdbcTemplate 参数绑定，改由数据库 `now()` 求值，消除「UPDATE 失败」与「INSERT 被
  catch 静默吞掉」两类真实缺陷。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-001, REQ-AUD-001]
acceptanceScenarios: [AC-P0B-AUTH-004, AC-P0B-AUD-001]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: [submitPublishRequest, deprecateVersion, archiveVersion, submitDecision]
mcpTools: []
eventSchemas: []
flywayBaseline: "无（本卡无迁移；权限码与列均已由既有迁移注册/建好）"
```

### 缺陷事实（已复核，2026-09-21，真实 Compose 栈 + 源码逐行确认）

1. **未注册权限码使三处入口恒 403**（`TASK-P0BR-063` §2 事实 8 登记）：
   `backend/src/main/java/com/aihub/version/application/PublishApplicationService.java`
   L67（`submitPublishRequest`）、L204（`deprecateVersion`）、L222（`archiveVersion`）
   均为 `authorizationService.requirePermission("asset:write")`。
   实测 `iam_permission` 只有 `asset:manage` 与 `asset:publish`，**没有** `asset:write`；
   授权判定 fail-closed ⇒ 任何主体（含平台管理员）调用这三者必返 403。
   `contracts/openapi/aihub-v1.yaml` 对应三个 operation 的
   `x-required-permissions` 全部声明 `[asset:manage]`（L2213/L2262/L2284）⇒
   **实现漂移于契约**，取齐方向是改为 `Permissions.ASSET_MANAGE`（同模块
   `VersionApplicationService` L59/L101/L120 与 `AssetApplicationService` 已一致使用常量）。
2. **`PublishJobHandler` 的 6 处 Instant 绑定**（`version/infrastructure/PublishJobHandler.java`）：
   `decided_at = ?` 分别出现在 L118-119（P7 成功）、L141-142（补偿重试）、
   L158-159（`markFailed`）、L176-178（`sagaCompensationFailed`）；
   另有 L202-209 的 `INSERT INTO system_alert (... fired_at ...)` 与
   L230-237 的 `INSERT INTO audit_log (... occurred_at ...)` 各绑一个 `Instant.now()`。
   PG JDBC + JdbcTemplate 无法推断 `Instant` 的 SQL 类型
   （`Can't infer the SQL type to use for an instance of java.time.Instant`，
   同型缺陷在 `TASK-P0BR-063` §2 事实 7 已实证并使 `submitDecision` 必然 500）。
   后果分两类：`decided_at` 的 UPDATE 抛异常 ⇒ 发布 Saga 状态推进失败；
   两处 INSERT 被 `catch (Exception ex) { LOG.error(...) }` 包裹 ⇒ **真实栈上静默丢审计/告警行**，
   HTTP 与任务仍显示成功——正是 `REQ-AUD-001` 末条禁止的「仅 warn 后静默丢失」。
3. **`PhysicalRecycleJobHandler` L121-122**：`UPDATE asset SET recycled_at = ? ...`
   绑 `Instant.now()`，同型缺陷使物理回收在标记回收时间处抛异常（其外层 Worker 会重试至 DEAD）。
4. **既有 E2 不具判别力**：`PublishJobHandlerTest` 全量 mock `JdbcTemplate`，
   因而从未暴露类型推断问题；其断言还把缺陷 SQL（`decided_at = ?`）与
   `any(Instant.class)` 当作预期固化——本卡必须同步更正，否则测试反向锁定缺陷。
5. **契约口径差异（本卡不改契约，仅登记）**：`submitDecision` 的
   `x-required-permissions` 声明 `[publish:approve]`，而 `publish:approve` 同样未注册；
   实现使用已注册的 `asset:publish`（L123），实测可用。统一二者需要一个
   权限 Catalog 层面的契约决策（改契约 or 前向迁移注册新码），与
   `TASK-P0BR-003`（Permission Catalog/常量/Seed/OpenAPI/UI 无漂移）同源，
   不在本卡范围内，故本卡**不动** `contracts/`（列入 forbiddenPaths）并在 §9 交接。
6. **旁路审计写入（本卡不重构，仅登记）**：`recordAuditEvent` 直插 `audit_log`，
   绕过 `AuditService` 这一唯一持久化入口（REQ-AUD-001 首条），因此不参与统一脱敏与
   trace/request 补全。本卡只让它「不再失败」，把旁路收敛到共享入口作为后续任务。
7. **实施中确认的两个连带修复点（属本卡已改文件，随修复一并处理）**：
   审计 INSERT 一旦不再被吞，`request_summary`（JSONB 列）以 String 绑定必须显式
   `?::jsonb` 转型（PG 驱动对 String→JSONB 的默认绑定报类型错——权威
   `JdbcAuditRepository` 用 `setObject(..., Types.OTHER)` 是同一原因）；
   `error_code` 列为 VARCHAR(64)，`sagaCompensationFailed` 传入的
   `异常类名: message` 可能超长，超长会使审计写入失败连带任务失败，故绑定前截断到 64。

## 3. 范围

### 允许修改

- `version.application.PublishApplicationService`：三处权限码取齐 `Permissions.ASSET_MANAGE`
  （并把 L123 的字面量一并换成 `Permissions.ASSET_PUBLISH` 常量，避免再次漂移；语义不变）。
- `version.infrastructure.PublishJobHandler`：6 处时间参数改为 SQL `now()` 求值，删除未用 import。
- `asset.infrastructure.PhysicalRecycleJobHandler`：`recycled_at` 改为 SQL `now()` 求值。
- 对应测试目录：更正被固化的缺陷 SQL 断言、新增防回归断言（时间列由数据库求值）
  与授权正/反路径断言。
- 契约：无变更。Migration：无。页面：无。
- Fixture/Verify：不改 `deploy/compose` 与 `verify.ps1`；E4 用独立取证脚本。

### 明确不在范围

- 不注册 `asset:write`、不改 OpenAPI、不动 `iam_permission` Seed（前向迁移注册新权限码属
  `TASK-P0BR-003` 的 Catalog 收敛范围，需契约决策）。
- 不重构 `recordAuditEvent` 为经共享 `AuditService`（§2 事实 6：登记为后续任务）。
- 不处理 `submitDecision` 的 `publish:approve` 契约口径（§2 事实 5）。
- 不引入新的时间源抽象或全局 JdbcTemplate 类型映射配置（`argTypes`/`StatementCreatorUtils`
  的注册会影响所有模块的既有 SQL，超出本卡可验收边界）。
- 不回填历史数据、不改 `frontend/`。

### 禁止变化

- 不得为了让 E4 通过而放宽授权（例如去掉 `requirePermission` 或改用 `asset:read`）：
  无 `asset:manage` 的主体必须仍返回 403。
- 不得把被吞的审计/告警异常改为「继续静默」的新形式（例如加 `if` 跳过写入）；
  必须让写入真实发生。
- 不得修改 V1/V2/V10 等已应用迁移。
- 不得改变发布 Saga 的步骤语义、状态取值或幂等键。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-004` | 资产维护者 | 主体持 `asset:manage` | POST `/versions/{id}/publish-requests` | 2xx 并返回 requestId（修复前恒 403） | `E4` |
| `AC-P0B-AUTH-004` | 审查者/管理员 | 发布请求 PENDING_REVIEW | POST `/publish-requests/{id}/decisions` APPROVE | 200，`publish_request.decided_at` 非空，VERSION_PUBLISH 入队 | `E4` |
| `AC-P0B-AUTH-004` | Worker | 版本 PENDING_REVIEW 且 digest 与 frozen_digest 一致 | 领取并执行 VERSION_PUBLISH | 任务 SUCCEEDED、`asset_version.status=PUBLISHED`、`publish_request.status=PUBLISHED` | `E4` |
| `AC-P0B-AUTH-004` | 资产维护者 | 版本已 PUBLISHED | POST `/versions/{id}/deprecate`、`/archive` | 各 200（修复前恒 403） | `E4` |
| `AC-P0B-AUTH-004` | 无 `asset:manage` 的主体 | READER 角色 | 提交发布请求 | 仍 403 且无 publish_request 行（证明是取齐而非放宽） | `E4` |
| `AC-P0B-AUD-001` | 审查者 | 发布成功 | 查询 `audit_log` | 存在该 requestId 的 `PUBLISH_SUCCEEDED` 行，result=SUCCEEDED、principal/资源/trace_id 正确非空（修复前 0 行） | `E4` |
| `AC-P0B-AUD-001` | 审查者 | 版本状态前置不满足（非 PENDING_REVIEW） | Handler 执行 | `markFailed` 生效：`publish_request.status=FAILED` 且 `decided_at` 非空 + `PUBLISH_FAILED` 审计行 | `E2`/`E4` |
| 边界 | 审查者 | 物理回收任务对过期 ARCHIVED 资产执行 | `UPDATE asset SET recycled_at` | 不因类型推断异常失败（SQL 以 `now()` 求值） | `E2` |
| 失败 | 审查者 | `PublishJobHandlerTest` 断言 SQL 文本 | 若有人把时间列改回参数绑定 | 断言 FAIL（防回归） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改认证；actor 仍取共享 PrincipalContext |
| Permission/Scope/ACL | `asset:write`→`asset:manage` 取齐契约；拒绝路径必须仍 fail-closed |
| 数据库权限下推 | N/A |
| 状态前置条件 | 不变；仅使既有迁移真实可达 |
| 幂等与并发 | 不变；`decided_at` 与 `status` 同条 UPDATE 原子写 |
| 事务与外部一致性 | 审计/告警 INSERT 失败模式必须可观察，不再静默丢行 |
| 错误码 | 不新增 |
| 审计事件 | `PUBLISH_SUCCEEDED`/`PUBLISH_FAILED`/`SAGA_COMPENSATION_FAILED` 必须真实落行 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 无新配置项 |
| 指标/Trace/告警 | `system_alert` 的 `SAGA_COMPENSATION_FAILED` INSERT 可用；审计行携带 trace_id |
| Secret/日志脱敏 | 证据不含 Token/口令/预签名 URL |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 变更：**无**（本卡是实现向既有契约取齐；`contracts/` 在 forbiddenPaths）。
2. 新增 Flyway 文件：无。
3. 存量清点：历史 `publish_request.decided_at` 为 NULL 的行、缺失的 `PUBLISH_SUCCEEDED`
   审计行不回填（无真实来源，伪造违反审计原则）。
4. 生成类型更新：无。

## 7. 实施步骤

- [x] 契约及契约测试（本卡无契约变更；`publish:approve` 口径差异登记 §2 事实 5）
- [x] Migration 及空库/升级迁移测试（本卡无迁移）
- [x] Application/Infrastructure：三处权限码取齐 + 7 处 Instant 绑定改数据库求值
      （含被 catch 吞掉的 audit_log/system_alert INSERT 改为失败可观察 + JSONB `?::jsonb`
      显式转型 + error_code 超长截断）
- [x] E2：更正 `PublishJobHandlerTest` 被固化的缺陷 SQL 断言并加防回归守卫（遍历 update
      调用：无 Instant 绑定、时间列必含 `now()`）；新增
      `PublishApplicationServiceAuthorizationTest`（7 项：三处 asset:manage、
      submitDecision asset:publish、deny 路径零副作用）与
      `PhysicalRecycleJobHandlerTest`（2 项：recycled_at=now() 与保留期前置）
- [x] 鉴别力自证：`git stash` 回退实施改动 → 重建镜像 → E4 复跑 **17 FAILED**
      （S1 提交发布请求真实 403 `requiredPermission=asset:write`、S3 审批链路断、
      S4 弃用/归档 403、PUBLISH_SUCCEEDED 审计行恒 0）；E2 的判别力来自实施期真实观察
      ——主代码三处编辑一度未生效（仍 asset:write）时
      `PublishApplicationServiceAuthorizationTest` 3 项显式 FAIL
      （Wanted asset:manage / Actual asset:write），生效后 7/7 PASS；
      恢复后 Grep 哨兵清零 → 重建复跑全绿
- [x] E4 取证：`.trae/evidence/publish-e4-064.ps1` 在真实栈（交付基线，Gitea 接通）
      跑通完整发布链路（提交→审批→VERSION_PUBLISH SUCCEEDED→PUBLISHED→弃用→归档）
      + 审计行对照，修复形态 **26/26 ALL PASS**（首轮与恢复复跑各一次）
- [x] Runbook/追踪矩阵：`work-breakdown.md` 的 064 行已在立卡提交登记；
      后续任务交接见 §9 与 EVD environmentDifferences

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File .trae/evidence/publish-e4-064.ps1
```

每条命令声明：

- 预期退出码：0；Docker 不可用时 E4 不可 SKIP 为 PASS，只能交接 IMPLEMENTED_UNVERIFIED；
- 覆盖的 AC ID：`AC-P0B-AUTH-004`、`AC-P0B-AUD-001`；
- 所需依赖：JDK 21、Maven Wrapper、完整 Compose 栈与真实 `GITEA_SERVICE_TOKEN`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-064-001.yaml`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-064.md -CheckCompletion
```

## 9. 停止条件与交接

AI 遇到以下情况必须停止受影响部分并报告：

- 需要修改契约或注册新权限码才能通过（属 `TASK-P0BR-003` 与契约决策，越界即停止）；
- 需要注册全局 JdbcTemplate 类型映射才能修复（影响面超出本卡，停止并报告为架构议题）;
- 需要修改已应用迁移或放松数据库约束；
- 无法产生 E4（不得以 mock 单测冒充「发布链路真实走通」）；
- E4 出现新 FAIL 且根因在本卡 allowedPaths 之外。

实施后必须交接（不在本卡修复）：

- `submitDecision` 契约声明 `publish:approve`（未注册）vs 实现 `asset:publish` 的口径统一；
- `recordAuditEvent`/`triggerCompensationAlert` 旁路直写 `audit_log`/`system_alert`
  收敛到共享 `AuditService`/`AlertService` 入口（含脱敏与 trace 补全）；
- 全库排查是否仍有其它 `JdbcTemplate` 绑定 `Instant` 的调用点（本卡已覆盖 063/064 已知 7 处）。

## 10. 完成报告

```text
Completed:
  发布链路在真实 Compose 栈（交付基线，Gitea 接通）上端到端可用，且发布成功
  的审计与失败告警真实落库：
  - 三处入口（submitPublishRequest/deprecateVersion/archiveVersion）的权限判定由
    未注册的 asset:write 取齐到契约声明的 asset:manage；submitDecision 的字面量
    asset:publish 改为 Permissions.ASSET_PUBLISH 常量（语义不变）。
  - PublishJobHandler 的 4 处 decided_at + system_alert.fired_at + audit_log.occurred_at
    共 6 处时间参数改由数据库 now() 求值；PhysicalRecycleJobHandler.recycled_at 同法。
    两处原被 catch 吞掉的 INSERT 改为失败可观察（不再静默丢审计/告警行），
    并连带处理 JSONB 显式转型与 error_code 超长截断（§2 事实 7）。
  真实栈 E4（publish-e4-064）修复形态 26/26 断言 ALL PASS：S1 admin 经 REST 提交发布
  请求 200（修复前恒 403）、S2 READER 提交/弃用仍 403 且零副作用（反空洞授权守卫）、
  S3 APPROVE→VERSION_PUBLISH 由 Worker 执行至 SUCCEEDED→版本 PUBLISHED、
  audit_log 存在该 requestId 的 PUBLISH_SUCCEEDED 行（主体/trace/时间/摘要齐备，
  trace 等于审批请求 traceparent，063 链路保持）、S4 弃用/归档各 200。
  同一脚本对缺陷基线（git stash 回退实施改动、重建镜像）复跑 17 FAILED / 9 PASS——
  证明断言具备判别力；恢复后 Grep 哨兵清零、重建复跑再次 26/26 全绿。

Changed files:
  - backend/src/main/java/com/aihub/version/application/PublishApplicationService.java
    （三处 asset:manage + asset:publish 常量化）
  - backend/src/main/java/com/aihub/version/infrastructure/PublishJobHandler.java
    （6 处时间列改 now()；两处 INSERT 失败上抛；?::jsonb；error_code 截断；删未用 Instant import）
  - backend/src/main/java/com/aihub/asset/infrastructure/PhysicalRecycleJobHandler.java
    （recycled_at 改 now()）
  - backend/src/test/java/com/aihub/version/infrastructure/PublishJobHandlerTest.java
    （重写：更正被固化的缺陷 SQL 断言 + 无 Instant 绑定/时间列必含 now() 的防回归守卫）
  - backend/src/test/java/com/aihub/version/application/PublishApplicationServiceAuthorizationTest.java
    （新增，7 项：三处 asset:manage、submitDecision asset:publish、deny 零副作用）
  - backend/src/test/java/com/aihub/asset/infrastructure/PhysicalRecycleJobHandlerTest.java
    （新增，2 项：recycled_at=now()、保留期前置）
  - docs/ai-spec/tasks/TASK-P0BR-064.md（本卡）
  - docs/ai-spec/tasks/evidence/EVD-P0BR-064-001.yaml（新增，E4）

Requirements and scenarios satisfied:
  - REQ-AUTH-001 / AC-P0B-AUTH-004（E4）：授权码取齐后成功路径真实通过，
    且无 asset:manage 主体仍 fail-closed 403（不是放宽）；
  - REQ-AUD-001 / AC-P0B-AUD-001（E4）：发布成功审计 PUBLISH_SUCCEEDED 真实落行，
    缺陷基线恒 0 行对照成立。

Contract/database changes:
  - 无契约、无迁移、无配置项变更；仅使实现权限码取齐既有 OpenAPI 声明、
    时间列改数据库求值。行为变化：三处入口由「任何主体恒 403」修复为按 asset:manage 判定；
    发布 Saga 的 decided_at/审计/告警由「失败/静默丢行」修复为可用。

Validation run and results:
  - validate-spec.ps1 -> PASS；validate-task-card.ps1（本卡）-> PASS；-CheckChangedPaths -> PASS
  - cd backend; ./mvnw.cmd -o verify -> 540 项，2 失败、6 跳过：
    PreviewJobHandlerTest 的 2 项既存 CSV 解析缺陷（024 轮同样出现，本卡未触碰 preview），
    与本卡无关，不记为 PASS。本卡相关套件：PublishJobHandlerTest 4/4、
    AuthorizationTest 7/7、PhysicalRecycleJobHandlerTest 2/2、VersionApplicationServiceTest 12/12。
  - .trae/evidence/publish-e4-064.ps1（交付基线）-> 修复形态 26/26 ALL PASS（exit 0）；
    缺陷基线 17 FAILED（exit 1，判别力自证）；恢复后复跑再次 26/26 ALL PASS
  - docker compose config --quiet -> exit 0
  - deploy/compose/scripts/verify.ps1 -> 20 步全 PASS（V01..V11 + V05b..V05j）

Evidence artifacts:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-064-001.yaml（E4）
  - .trae/evidence/publish-e4-064.txt、-restored.txt、-defectbaseline.txt、verify-ps-064.txt

Validation not run and reasons:
  - 契约破坏检查：本卡无契约变更；@redocly/cli diff 为已登记工具链缺陷。
  - pnpm lint/typecheck/test/build：未触碰 frontend/。

Remaining risks or follow-ups:
  - submitDecision 契约声明 publish:approve（未注册）vs 实现 asset:publish 的口径统一，
    需权限 Catalog 层契约决策，属 TASK-P0BR-003（§2 事实 5）。
  - recordAuditEvent/triggerCompensationAlert 旁路直写 audit_log/system_alert，
    应收敛到共享 AuditService/AlertService（§2 事实 6）。
  - 全库仍有其它把 java.time.Instant 作为 JdbcTemplate 参数绑定的调用点需排查
    （本卡覆盖已知的 063/064 共 7 处 + JdbcIdempotencyStore 的 OffsetDateTime 经
    NamedParameter 已正常）；建议独立治理任务扫描全库时间列绑定写法。
  - AssetCatalogIT / TaxonomyIT 的 @SpringBootConfiguration 搜索缺陷与
    AssetFacetConsistencyIT flake 同属既有测试治理任务范围。
```

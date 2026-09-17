---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-051
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 1d058fdfa226760ac3526c4a2f43b8f167d256d1
stageGatePassed: true
stageGateEvidence: 用户批准。依据 READY 需求 REQ-JOB-001（PostgreSQL 可靠任务，P0-B）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），修复可靠任务的插入语句缺陷以恢复资产创建主链路；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-JOB-001
acceptanceScenarios:
  - AC-P0B-JOB-001
  - AC-P0B-UI-004
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-JOB-001|E4|在真实 Compose 栈上登记资产，createProvisionJob 成功插入 job_task（无 PSQLException），并断言新任务行 lease 字段与 payload 可读回
  - AC-P0B-UI-004|E4|浏览器登录后经「登记资产」弹窗提交，POST /api/v1/assets 返回 201 且资产详情回读的治理字段与所选 itemCode 一致
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；任务插入由既有 PrincipalContext 提供 principalId
  - AUTHZ|N/A - 不新增或放宽权限；createAsset 仍要求其既有权限，本卡只修 SQL 绑定
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|不改变 JobStatus 枚举与数据库 CHECK 约束；仅修正 insert 的列/占位符对应关系
  - IDEMPOTENCY|N/A - 本卡不改幂等语义；幂等存储缺陷归 TASK-P0BR-052
  - CONSISTENCY|入队须与业务事务原子提交：修复后 createAsset 的事务内插入不得失败，不得引入 try/catch 吞异常或异步补偿
  - ERRORS|插入失败必须以明确异常暴露，不得退化为静默跳过任务入队
  - AUDIT|N/A - 不新增审计事件；既有 ASSET_CREATED 审计保持
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不涉及受控值或文案
  - CONFIG|不得引入按环境切换的列清单或运行时可配置插入语句
  - OBSERVABILITY|修复后 V09 持久化任务默认拒绝断言必须保持 PASS（不因修复而放宽）
  - SECRETS|N/A - 不接触 Secret；job payload 不得包含凭据，证据不得包含 Token
allowedPaths:
  - backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java
  - backend/src/test/java/com/aihub/job
  - backend/src/test/java/com/aihub/asset
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-051.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-051-001.yaml
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
  - docs/ai-spec/tasks/evidence/EVD-P0BR-051-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-051.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-051.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T07:00:06Z
---

# TASK-P0BR-051：修复 job_task 插入的列/占位符不一致，恢复资产创建主链路

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

登记资产不再因持久化任务入队失败而返回 500：`POST /api/v1/assets` 在真实环境返回 201，
创建出的资产可被检索与读回。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001]
acceptanceScenarios: [AC-P0B-JOB-001, AC-P0B-UI-004]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [createAsset]
mcpTools: []
eventSchemas: []
flywayBaseline: V9
```

### 缺陷事实（已复核，证据见 EVD-P0BR-048-001）

`JdbcJobRepository.insert`（`backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java`）
的 `INSERT INTO job_task` 列出 **16** 个列，但 `VALUES` 只提供 **15** 个占位符：

```text
INSERT INTO job_task (job_id, type, payload, status, max_attempts, attempts, next_run_at,
    leased_until, leased_by, trace_id, principal_id, asset_id, error_code,
    created_at, updated_at, row_version)     -- 16 列
VALUES (?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?)  -- 15 个占位符
```

真实栈实测（见 EVD-P0BR-048-001）：

- `POST /api/v1/assets` → HTTP 500 `INTERNAL_ERROR`；
- 后端日志根因：`org.postgresql.util.PSQLException: The column index is out of range: 16, number of columns: 15`；
- 调用链：`JdbcJobRepository.insert:66` ← `AssetApplicationService.createProvisionJob:471`
  ← `AssetApplicationService.createAsset:133`。

实测表结构 `job_task` 共 17 列（含自增 `id`），即 16 个非自增列——与列清单一致，
**占位符少 1 个**，参数列表亦只传 15 个值（`row_version` 固定传 0）。

该缺陷由 `d8506bf`（2026-07-02，"feat:新增平台公共能力"）引入，早于本卡 baseCommit，
本次由 TASK-P0BR-048 的 E4 验证首次在真实栈上暴露（此前 Compose 栈因
`TASK-P0BR-047` 的 Mapper 扫描缺陷无法启动，该路径从未被真实执行）。

影响范围：**所有需要入队可靠任务的写路径**（首当其冲是资产创建），不限于资产。

## 3. 范围

### 允许修改

- 模块：`com.aihub.job.infrastructure`
- 文件或目录：
  - `backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java`（修正 insert 绑定）
  - `backend/src/test/java/com/aihub/job`（新增回归测试）
  - `backend/src/test/java/com/aihub/asset`（如需要，补资产创建入队的集成断言）
- 契约：无变更
- Migration：无（表结构本身正确，不改 `V9__job.sql`）
- 页面：无
- Fixture/Verify：不改动 `deploy/compose`（`verify.ps1` 的假阳性问题归 `TASK-P0BR-053`）

### 明确不在范围

- 不修复幂等存储的 `Instant` 绑定缺陷，归 `TASK-P0BR-052`；
- 不修复 `verify.ps1` 默认拒绝断言的假阳性，归 `TASK-P0BR-053`；
- 不实现 `AC-P0B-JOB-002..005` 的租约回收、退避、DEAD 与人工 retry/cancel 场景；
- 不重构 Job 模块的仓储抽象或引入新依赖；
- 不改动 `job_task` 表结构与既有 Migration。

### 禁止变化

- 不改变的 API/状态/权限：`job_task` 列语义、`JobStatus` 枚举、`ck_job_task_status` 约束、
  `createAsset` 的权限要求；
- 不允许的依赖升级或重构：不新增依赖，不改动 `JdbcJobRepository` 的公开方法签名；
- 不允许的兼容破坏：不得通过删除列、放宽 NOT NULL 或默认值来"消掉"这个不匹配；
  不得把插入失败吞成静默跳过。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-JOB-001` | 有资产创建权限的用户 | 真实 Compose 栈 + 真实 PostgreSQL | 调用 `POST /api/v1/assets` | 返回 201；`job_task` 出现一条 PROVISION 任务行，`job_id/type/payload/status/principal_id/asset_id/created_at/updated_at/row_version` 均可正确读回 | `E4` |
| `AC-P0B-JOB-001` | 同上 | 同上 | 检视后端日志 | 无 `PSQLException`、无 `column index is out of range` | `E4` |
| `AC-P0B-JOB-001` | 审查者 | 构造列数多于占位符的同类 SQL | 运行回归测试 | 测试失败并指出列数与占位符数量不一致 | `E2` |
| `AC-P0B-UI-004` | 有 `asset:create` 权限的用户 | 已登录的浏览器 | 经「登记资产」弹窗提交 MODEL 资产 | 创建成功；详情的 license/framework/task 与所选 itemCode 一致 | `E4` |
| `AC-P0B-UI-004` | 同上 | 同上 | 提交 DATASET 资产 | 创建成功；详情的 format/modality 与所选 itemCode 一致 | `E4` |
| `AC-P0B-JOB-001` | 无 `job:read` 的用户 | 栈已就绪 | 访问 `/api/v1/system/jobs` | 仍返回 403（本次修复不得放宽任务端点权限） | `E4` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；`principal_id` 继续取自既有 PrincipalContext，禁止从请求体读取 |
| Permission/Scope/ACL | N/A：不新增或放宽权限；`createAsset` 权限要求不变 |
| 数据库权限下推 | N/A：不涉及查询过滤或授权下推 |
| 状态前置条件 | 不改变 `JobStatus` 枚举与 CHECK 约束；新任务初始状态沿用既有约定 |
| 幂等与并发 | N/A：不改幂等语义；插入需保持唯一键冲突的既有处理 |
| 事务与外部一致性 | 入队必须与业务事务原子提交；不得引入 try/catch 吞异常、异步补偿或内存回退 |
| 错误码 | 插入失败以明确异常暴露；不得退化为静默跳过任务入队 |
| 审计事件 | N/A：沿用既有 `ASSET_CREATED` 与任务审计 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | N/A：不涉及受控值或文案 |
| 配置 | 不得引入运行时可配置的列清单或按 Profile 分支的插入语句 |
| 指标/Trace/告警 | 不新增指标；任务行的 `trace_id` 必须写入且与请求 trace 一致 |
| Secret/日志脱敏 | N/A：不接触 Secret；`payload` 不得含凭据，证据不得包含 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；`POST /api/v1/assets` 由 500 恢复为契约声明的 201；
3. 新增 Flyway 文件：无（表结构正确，缺陷在 Java 侧绑定）；
4. 存量清点与回填：无需回填；先前失败的创建请求未产生资产行（事务回滚）；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：修正后所有依赖任务入队的写路径一并恢复；无兼容窗口；
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

1. 修正 `JdbcJobRepository.insert` 的列/占位符/参数三元对应关系，使 16 个非自增列与
   16 个占位符、16 个参数一一对应（`leased_until`/`leased_by`/`error_code` 传 null，
   `row_version` 传 0，`created_at`/`updated_at` 传实例时间）；
2. 新增回归测试：以真实 PostgreSQL（Testcontainers 或 Compose）执行 insert 并读回，
   断言不抛 `PSQLException`；测试须能在占位符数量再次不匹配时失败；
3. 在真实 Compose 栈上端到端确认资产创建恢复（借 `TASK-P0BR-048` 已建立的
   `.trae/e4-048-full.mjs` 路径，或等价的一次性脚本）。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml up -d
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：`mvnw verify` 为 0（既存 `PreviewJobHandlerTest` 的 2 项 CSV 失败不计入本卡，
  但必须在 Evidence `limitations` 中如实列出）；`verify.ps1` 在
  `TASK-P0BR-053` 完成前可能因默认拒绝假阳性而失败，须逐项说明哪一条失败及原因；
- 覆盖的 AC ID：`AC-P0B-JOB-001`、`AC-P0B-UI-004`；
- 所需依赖：JDK 21、Docker（Compose 栈）；E4 需要真实 PostgreSQL；
- SKIP 判定：Docker 不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-051-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-051.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如列清单与表结构不一致，需要改 Migration 才能对齐）；
- 需要修改 `V1`/`V2`/`V9` 或其他已应用 Migration；
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

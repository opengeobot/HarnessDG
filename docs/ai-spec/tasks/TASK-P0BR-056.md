---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-056
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: a3f45152fce3701dfa2a7018eb251d868d21fb24
stageGatePassed: false
stageGateEvidence: 待用户批准。依据 READY 需求 REQ-IDM-001（写接口幂等，P0-B）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），将幂等键唯一约束对齐到 REQ 声明的作用域（含 principalId）；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
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
  - AC-P0B-IDM-002|E4|在真实 Compose 栈上让两个不同主体使用同一 Idempotency-Key 各提交自己的请求体：两次都被真实执行且各产生自己的业务副作用，api_idempotency 中该 key 有 2 条记录（每主体 1 条），互不覆盖
  - AC-P0B-IDM-001|E4|同一主体同键同体重放仍复用首次结果且无重复副作用（确认新约束未破坏同主体复用语义）
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；principalId 继续取自既有 PrincipalContext
  - AUTHZ|本卡正面约束：跨主体隔离必须由数据库约束与查询条件共同保证，不得只靠查询条件而让唯一键仍是全局的
  - DB_FILTER|N/A - 不涉及列表查询的授权下推
  - STATE|不改变 IdempotencyStatus 取值与 `ck_api_idempotency_status` 约束
  - IDEMPOTENCY|本卡正面主题：key + principalId + method + path 构成作用域，数据库唯一约束必须与之一致
  - CONSISTENCY|不改变业务事务边界；不得以内存 Map 或 try/catch 吞异常作为回退
  - ERRORS|同键不同主体的写请求不得因唯一键冲突而被静默丢弃；同主体同键不同摘要仍返回 IDEMPOTENCY_KEY_CONFLICT
  - AUDIT|N/A - 幂等复用不新增审计
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|不得引入运行时可配置的幂等作用域
  - OBSERVABILITY|不新增指标；不得因约束变更引入新的 INTERNAL_ERROR 噪声
  - SECRETS|N/A - 证据不得包含 Token
allowedPaths:
  - backend/src/main/resources/db/migration/V26__idempotency_scope.sql
  - backend/src/main/java/com/aihub/job/infrastructure/JdbcIdempotencyStore.java
  - backend/src/test/java/com/aihub/job
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-056.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-056-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V9__job.sql
  - backend/src/main/resources/db/migration/V20__permission_seed_and_idempotency.sql
  - contracts
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-056-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-056.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-056.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml up -d
approvedBy: null
approvedAt: null
---

# TASK-P0BR-056：将幂等键唯一约束对齐到含 principalId 的作用域

> 状态：`DRAFT`（AI 起草，待用户批准；不得由 AI 置为 READY）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

同一 `Idempotency-Key` 在不同主体下各自独立：两个主体重用同一 key 时，
双方的请求都被真实执行并各自留存幂等记录，互不覆盖、互不吞没。

## 2. 关联规格

```yaml
requirements: [REQ-IDM-001]
acceptanceScenarios: [AC-P0B-IDM-001, AC-P0B-IDM-002]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [createOrganization]
mcpTools: []
eventSchemas: []
flywayBaseline: V9
```

### 缺陷事实（已复核，证据见 EVD-P0BR-052-001）

`REQ-IDM-001` 明确规定作用域：

> key 与 principalId、method、canonical path、request digest 共同构成作用域

读路径已按此实现——`JdbcIdempotencyStore.find` 的 WHERE 含
`idempotency_key = :key AND principal_id = :principalId AND method = :method AND path = :path`。

但**写路径与数据库约束只认 key**：

- `V9__job.sql:16` 声明 `idempotency_key VARCHAR(128) NOT NULL UNIQUE`（全局唯一，不含 principal_id）；
- 该约束在库中体现为索引 `api_idempotency_idempotency_key_key ON (idempotency_key)`；
- `JdbcIdempotencyStore.save` 使用 `ON CONFLICT (idempotency_key) DO NOTHING`。

后果：第二个主体用同一 key 写记录时，`DO NOTHING` 使**该记录被静默丢弃**，
`save` 随后 `find` 也命中不到自己的记录（因 principal 不匹配），
于是该主体永远没有幂等记录——其后续重放不会复用，而是**重复执行业务**。

真实栈实测（两个主体、同一 key、各自请求体）：

```text
PASS  same key under another principal is executed, not replayed
PASS  response carries the other principal's own org code
NOT-PROVEN(DB)  per-principal record for a shared key :: rows=1 (期望 2)
```

即：**查询侧隔离生效，持久侧隔离失效**。这是「同键不同主体」场景下的重复提交风险，
且修复必须改数据库约束——正是 `TASK-P0BR-052` 停止条件所列的
「需要修改已应用 Migration 才能对齐」，故当时未在该卡内实施。

## 3. 范围

### 允许修改

- 模块：`com.aihub.job.infrastructure`
- 文件或目录：
  - `backend/src/main/resources/db/migration/V26__idempotency_scope.sql`（**新增**前向迁移）
  - `backend/src/main/java/com/aihub/job/infrastructure/JdbcIdempotencyStore.java`（`ON CONFLICT` 目标改为新唯一键）
  - `backend/src/test/java/com/aihub/job`（回归测试）
- 契约：无变更
- Migration：**新增 V26**；严禁修改 `V9`、`V20` 或任何已应用迁移
- 页面：无
- Fixture/Verify：不改动 `deploy/compose`（`verify.sh` 的既存缺陷归独立任务）

### 明确不在范围

- 不实现 `IN_PROGRESS` 原子登记、租约与并发恰好一次（`AC-P0B-IDM-003`）；
- 不处理历史存量数据的去重（见「契约与数据先行」第 4 条）；
- 不修改 `IdempotencyInterceptor`（归 `TASK-P0BR-054`）；
- 不为各写端点补 `Idempotency-Key REQUIRED` 声明；
- 不改 `api_idempotency` 的其它列、状态约束或过期清理策略。

### 禁止变化

- 不改变的 API/状态/权限：`Idempotency-Key` 头语义、`IDEMPOTENCY_KEY_CONFLICT`（409）、
  `ck_api_idempotency_status` 约束、各写端点权限要求；
- 不允许的依赖升级或重构：不新增依赖；不改 `IdempotencyStore` 接口签名；
- 不允许的兼容破坏：**不得直接改 `V9`**；不得为绕过约束而删除唯一索引后不建替代约束
  （那会让并发下产生重复记录）；不得把 `find` 的 principal 条件去掉以求「对得上」；
  不得降低任何既有断言。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IDM-002` | 主体 A、主体 B（各有 `organization:manage`） | 真实 Compose 栈 | 两者用**同一** `Idempotency-Key` 各提交自己的请求体 | 两次都返回 201 且各创建自己的组织；`api_idempotency` 中该 key 有 2 条记录，`principal_id` 分别为 A、B | `E4` |
| `AC-P0B-IDM-002` | 主体 B | 上一条已执行 | B 用同一 key、不同请求体再次调用 | 返回 409 `IDEMPOTENCY_KEY_CONFLICT`（B 命中**自己的**记录并检出摘要不同），且不影响 A 的记录 | `E4` |
| `AC-P0B-IDM-001` | 主体 A | A 已有记录 | A 用同一 key、相同请求体重放 | 复用首次结果；A 的记录仍为 1 条；无重复业务副作用 | `E4` |
| `AC-P0B-IDM-001` | 审查者 | 迁移后的库 | 查询 `api_idempotency` 的唯一约束 | 唯一约束覆盖 `(idempotency_key, principal_id, method, path)`；不再有仅含 `idempotency_key` 的唯一索引 | `E3` |
| `AC-P0B-IDM-001` | 审查者 | 空库 | 从零执行全部迁移 | 迁移链完整可重放；`V9`～`V20` 未被改动 | `E3` |
| `AC-P0B-IDM-002` | 审查者 | 旧库（已有数据） | 执行 V26 | 迁移成功且不丢失既有记录 | `E3` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：`principalId` 继续取自既有 PrincipalContext，禁止从请求体读取 |
| Permission/Scope/ACL | 不新增或放宽权限；各写端点权限要求不变 |
| 数据库权限下推 | N/A |
| 状态前置条件 | 不改变 `IdempotencyStatus` 与 CHECK 约束 |
| 幂等与并发 | 唯一约束必须与查询条件同范围；`ON CONFLICT` 目标必须与新约束一致，使并发写入按作用域互斥 |
| 事务与外部一致性 | 不改变业务事务边界；禁止内存 Map 或吞异常回退 |
| 错误码 | 同主体同键不同摘要 → `IDEMPOTENCY_KEY_CONFLICT`（409）；跨主体不得因此码失败 |
| 审计事件 | N/A |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 不得引入运行时可配置的幂等作用域 |
| 指标/Trace/告警 | 不新增指标；不得因约束变更引入新的 INTERNAL_ERROR 噪声 |
| Secret/日志脱敏 | N/A；证据不得包含 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；
3. 新增 Flyway 文件：`V26__idempotency_scope.sql`——先 `DROP` 仅含 `idempotency_key` 的唯一约束，
   再建覆盖 `(idempotency_key, principal_id, method, path)` 的唯一约束（或等价唯一索引），
   并附**表注释与字段注释**（仓库规范）；SQL 需写成可重复执行的安全形式；
4. **存量清点与回填**：`principal_id` 列由 `V20` 引入，而 `V9` 建的唯一约束不含它，
   故旧库可能存在 `principal_id IS NULL` 的行。**迁移前必须先清点**：

   ```sql
   select count(*) as total, count(*) filter (where principal_id is null) as null_principal
   from api_idempotency;
   ```

   本地 Compose 栈实测为 `15|0`（无 NULL 行），但**这不能推广到其它环境**。
   若存在 NULL 行，须择一处理并写入 Evidence 的 assumptions：
   PostgreSQL 的唯一索引把 `NULL` 视为互不相等，因此 NULL 行天然不参与作用域唯一性判断——
   这会让这些行既不被新约束保护、也无法被按 principal 查询命中。
   实施者需明确选择「回填哨兵值」还是「显式保留为不受约束」，不得默认假设无历史数据；
5. 索引、唯一约束、外键及理由：唯一约束的作用域由 `(idempotency_key, principal_id, method, path)`
   构成，理由同上文缺陷事实；
6. 兼容窗口与失败恢复：迁移是前向的；回滚依赖数据库备份，不写 down 迁移（仓库约定）；
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

1. 新增 `V26__idempotency_scope.sql`：删旧唯一约束、建新作用域唯一约束，
   按需回填 `principal_id`，并补注释；
2. `JdbcIdempotencyStore.save` 的 `ON CONFLICT` 目标改为与新约束一致；
   同时确认 `save` 后 `find` 能命中**本主体**的记录（这是缺陷的直接体现）；
3. 新增回归测试：覆盖双主体同键各留一条、同主体同键不同摘要 409、
   同主体同键同摘要复用；测试须能在唯一约束退回全局时失败；
4. 在真实 Compose 栈上执行迁移并端到端验证跨主体隔离；
5. 在空库上重放整条迁移链，确认 `V9`～`V20` 未被改动。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml up -d
docker compose -f deploy/compose/compose.yaml exec -T postgres psql -U aihub -d aihub -c "\d api_idempotency"
```

每条命令声明：

- 预期退出码：`mvnw verify` 为 0（既存 `PreviewJobHandlerTest` 的 2 项 CSV 失败不计入本卡，
  但必须在 Evidence `limitations` 中如实列出）；`psql` 输出须显示新的作用域唯一约束；
- 覆盖的 AC ID：`AC-P0B-IDM-001`、`AC-P0B-IDM-002`；
- 所需依赖：JDK 21、Docker（Compose 栈）；E4 需要真实 PostgreSQL；
- SKIP 判定：Docker 不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-056-001.yaml`。

路径触发强制命令：修改 backend 与数据库迁移必须包含 Maven wrapper `verify`
与 Compose 栈上的迁移实测。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-056.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 存量数据中存在无法安全回填 `principal_id` 的行，且放行会破坏唯一性；
- 发现必须修改 `V9`/`V20` 或其它已应用迁移才能对齐（本卡只允许新增 V26）；
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
必须显式说明存量 `principal_id IS NULL` 行的处理方式与依据。

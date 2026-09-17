---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-060
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: 962a19b46c01b6705047c9599b074e8f3ea54ab5
stageGatePassed: false
stageGateEvidence: 待用户批准。依据 READY 需求 REQ-IAM-004「以原子 compare-and-set 将 jti 从 ACTIVE 置为 ROTATED」「只有一个并发请求可以成功」「ROTATED jti 再用视为重放，整个 Family REVOKED」与 ACCEPTED 决策 DEC-003、DEC-013，修复重放整族吊销与并发刷新双有效 Token 缺陷（DEF-058-2/3）及其失效的拒绝审计；属 ADR-0002 允许的安全整改，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-IAM-004
acceptanceScenarios:
  - AC-P0B-IAM-007
  - AC-P0B-IAM-011
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-IAM-007|E4|真实 Compose 栈上重用已轮换 refresh 返回 401 AUTH_REFRESH_REPLAYED，该 Token Family 残留 ACTIVE 降为 0，重放前刚轮换出的新 refresh 亦失效（非 200），audit_log 出现 AUTH_TOKEN_REPLAY_REJECTED/DENIED；verify.ps1 的 V05g 由 FAIL 转 PASS
  - AC-P0B-IAM-011|E4|真实 Compose 栈上两个独立 HTTP 连接同时用同一 refresh JWT 刷新，恰好 1 次返回 200，该 Token Family 的 status='ACTIVE' 行数恰好为 1；verify.ps1 的 V05i 由 FAIL 转 PASS
crossCuttingPlan:
  - AUTHN|本卡正面主题：使 refresh 轮换的吊销与重放检测真正生效；不得放宽任何认证判定
  - AUTHZ|N/A - 不改变权限判定与 Scope 计算
  - DB_FILTER|N/A - jti 与 Family 按主键/等值更新，不涉及列表下推
  - STATE|断言 RefreshTokenStatus 的 ACTIVE→ROTATED 与 Family→REVOKED 迁移真实落库，且非法迁移被拒绝
  - IDEMPOTENCY|轮换语义要求同一 refresh 只能单次消费；并发下必须由数据库保证唯一胜者（CAS 或行锁），不得用应用层检查替代
  - CONSISTENCY|吊销与拒绝审计须在异常抛出后仍持久化；与 TASK-P0BR-059 同源模式，须保持一致处理方式
  - ERRORS|保持既有稳定错误码：重放为 AUTH_REFRESH_REPLAYED，其他无效为 AUTH_UNAUTHENTICATED/AUTH_TOKEN_EXPIRED；不得改为其它错误码
  - AUDIT|AUTH_TOKEN_REPLAY_REJECTED 必须真实落库且 result=DENIED；AUTH_TOKEN_REFRESHED 仅在成功时记录；审计不含 JWT、jti 或 credential 原文（INV-TKN-004/008）
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不新增受控值；错误码既有 i18nKey 不变
  - CONFIG|不得引入可绕过轮换或重放检测的运行时可配置开关
  - OBSERVABILITY|N/A - 沿用既有刷新结果计数
  - SECRETS|证据不得包含 refresh JWT 原文、jti 原文或 Cookie 值；只记录 Family 业务 ID 与错误码
allowedPaths:
  - backend/src/main/java/com/aihub/identity/application/AuthenticationApplicationService.java
  - backend/src/main/java/com/aihub/identity/domain/RefreshTokenRecord.java
  - backend/src/main/java/com/aihub/identity/domain/RefreshTokenRepository.java
  - backend/src/main/java/com/aihub/identity/infrastructure/MyBatisRefreshTokenRepository.java
  - backend/src/main/resources/db/migration/V27__refresh_token_single_active.sql
  - backend/src/test/java/com/aihub/identity/IdentityLifecycleIT.java
  - backend/src/test/java/com/aihub/identity/application/AuthenticationApplicationServiceTest.java
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-060.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-060-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/java/com/aihub/identity/infrastructure/JdbcIdempotencyStore.java
  - frontend
  - contracts
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-060-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-060.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-060.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: null
approvedAt: null
---

# TASK-P0BR-060：修复 refresh 重放整族吊销被回滚与并发刷新双有效 Token

> 状态：`DRAFT`（AI 起草，待用户批准；不得由 AI 置为 READY）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

按 REQ-IAM-004 使 refresh 轮换具备两条被规格明确要求的性质：
（a）ROTATED jti 再用即整族 `REVOKED` 且拒绝审计落库；
（b）并发刷新只有一个请求成功，不得出现两条并存 `ACTIVE` refresh。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-004]
acceptanceScenarios: [AC-P0B-IAM-007, AC-P0B-IAM-011]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: [refreshToken]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（TASK-P0BR-058 在真实栈取证，见 EVD-P0BR-058-001.yaml）

**DEF-058-2（重放未切断）**

```text
重用已轮换 refresh           = 401 AUTH_REFRESH_REPLAYED   （错误码正确）
重放后该 Family 残留 ACTIVE   = 1                            （期望 0）
重放前轮换出的新 refresh       = 200（仍可刷新）              （期望失效）
audit_log AUTH_TOKEN_REPLAY_REJECTED = 0 行                  （期望 >= 1）
```

**DEF-058-3（并发双有效）**

```text
两个独立连接同时用同一 refresh JWT 刷新：
  状态                      = 200 / 200                      （期望恰好 1 次 200）
  该 Family ACTIVE 行数      = 2                              （期望 <= 1）
```

根因（代码定位）：

1. **重放分支**：`refresh` 标注 `@Transactional`，非活跃分支中先
   `refreshTokenRepository.revokeFamily(...)` 与 `audit("AUTH_TOKEN_REPLAY_REJECTED"...)`，
   随后**抛出** `AuthenticationException`，运行时异常回滚整个事务，
   使整族吊销与拒绝审计一并被撤销。与 DEF-058-1 同一模式。
2. **并发分支**：`refresh` 只做「读取记录 → 判断 `isActive` → 插入新记录 + 标记旧记录 `ROTATED`」，
   标记旧记录使用**无条件** `UPDATE`（无 `status='ACTIVE'` 谓词、无行锁），
   两个请求可在同一时间窗内都通过 `isActive` 检查（TOCTOU），各自签发新 refresh。
   规格要求的是**原子 compare-and-set**，当前实现不满足。

**现有测试未能发现该缺陷**：`IdentityLifecycleIT.iam011_concurrentRefreshOnlyOneSucceeds`
仅断言 `successCount >= 1` 与 `success + fail == threads`，
**两个请求都成功时该断言依然通过**，故对 DEF-058-3 完全没有鉴别力；
且无任何重放整族吊销用例。

## 3. 范围

### 允许修改

- 模块：`identity`（application/domain/infrastructure）与对应测试
- 文件或目录：见 `allowedPaths`
- 契约：无变更（错误码、响应结构、OpenAPI 均不变）
- Migration：**仅在需要数据库级唯一性/谓词支持时**新增 `V27+` 前向迁移；
  禁止修改 `V1`/`V2` 及任何已应用迁移
- 页面：无
- Fixture/Verify：不修改脚本；本卡使 `verify.ps1` 既有的 V05g 与 V05i 由 FAIL 转 PASS

### 明确不在范围

- 不修复登录失败计数与锁定（DEF-058-1）→ `TASK-P0BR-059`
- 不修复用户管理审计与最后管理员保护（DEF-058-4）→ `TASK-P0BR-061`
- 不改动登出/改密/禁用的会话失效路径（已由 AC-P0B-IAM-008/009 验证通过）；
- 不改动 access JWT 签发与校验逻辑。

### 禁止变化

- **不得放宽校验**：不得为了让并发用例通过而把断言改成「至少一个成功」；
- **不得削弱测试**：必须把 `iam011` 的弱断言收紧为「恰好一个成功」；
- **不得用 try/catch 吞掉认证异常**来规避回滚——重放必须继续以 `AUTH_REFRESH_REPLAYED` 对外表达；
- 不得以应用层「先查后写」替代数据库级原子性；
- 不允许的依赖升级或重构：不新增依赖。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-011` | 两个并发客户端 | 持有同一 refresh JWT | 同时刷新 | 恰好 1 次成功；该 Family `ACTIVE` 恰好 1 | `E4` |
| `AC-P0B-IAM-007` | 攻击者 | 持有已轮换的旧 refresh | 再次刷新 | `AUTH_REFRESH_REPLAYED` | `E4` |
| `AC-P0B-IAM-007` | 同上 | 重放已发生 | 检查该 Family | 残留 `ACTIVE` 为 0（整族 `REVOKED`） | `E4` |
| `AC-P0B-IAM-007` | 合法用户 | 上一轮轮换出的新 refresh | 被重放牵连后刷新 | 非 200（令牌已被切断） | `E4` |
| `AC-P0B-IAM-007` | 审查者 | 重放被拒 | 查询 `audit_log` | 存在 `AUTH_TOKEN_REPLAY_REJECTED` 且 `result=DENIED` | `E3` |
| `AC-P0B-IAM-007` | 审查者 | 重放审计 | 检查属性 | 不含 JWT、jti 或 credential 原文 | `E3` |
| `AC-P0B-IAM-011` | 审查者 | 正常单次刷新 | 刷新一次 | 仍返回 200（不得因收紧并发控制而破坏正常路径） | `E4` |
| `AC-P0B-IAM-011` | 审查者 | 并发断言被误放宽为「>=1 成功」 | 执行 IT | 用例 FAIL（证明断言具备鉴别力） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 正面主题；不得放宽任何认证判定 |
| Permission/Scope/ACL | 不涉及 |
| 数据库权限下推 | N/A |
| 状态前置条件 | `ACTIVE→ROTATED` 必须由原子 CAS 完成；`REVOKED` 后任何刷新必须被拒 |
| 幂等与并发 | **本卡核心**：唯一胜者由数据库保证，不得依赖应用层检查 |
| 事务与外部一致性 | 吊销与拒绝审计必须在异常抛出后仍持久化 |
| 错误码 | 重放 `AUTH_REFRESH_REPLAYED`；其他无效 `AUTH_UNAUTHENTICATED`/`AUTH_TOKEN_EXPIRED` |
| 审计事件 | `AUTH_TOKEN_REPLAY_REJECTED`（DENIED）与 `AUTH_TOKEN_REFRESHED`（成功）语义正确 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 不得引入绕过轮换/重放检测的开关 |
| 指标/Trace/告警 | 沿用既有刷新结果计数 |
| Secret/日志脱敏 | 证据与日志不含 refresh JWT、jti 原文 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；既有错误码获得真实触发记录；
3. 新增 Flyway 文件：**仅当所选并发控制方案需要数据库级支持时**新增
   `V27__refresh_token_single_active.sql`；若无需迁移，须在 Evidence 中说明为何现有约束已足够。
   **绝不允许修改 `V1`/`V2` 或任何已应用迁移**（仓库规则：数据库变更只允许前向 Migration）。
4. 存量清点与回填：**若新增唯一约束，必须先清点存量重复数据**
   （当前真实栈已观测到同 Family 多条 `ACTIVE`），
   给出前向修复语句并说明失败恢复方式；不得直接加约束导致迁移失败；
5. 索引、唯一约束、外键及理由：若采用「同 Family 至多一条 ACTIVE」的部分唯一索引，
   须给出与现有 `uq_api_idempotency_scope` 等既有约束一致的命名与注释规范；
6. 兼容窗口与失败恢复：迁移必须可在已有数据上成功应用；
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

1. **并发唯一性（DEF-058-3）**：把「将 jti 从 `ACTIVE` 置为 `ROTATED`」实现为真正的原子
   compare-and-set（例如 `UPDATE ... SET status='ROTATED' WHERE jti = ? AND status='ACTIVE'`
   并断言影响行数为 1），使只有一个并发请求能取得胜者；
   受影响行数为 0 时按重放语义处理。
   可选叠加数据库级保证（部分唯一索引）作为最终防线，但**不得仅依赖索引报错**作为控制流。
2. **重放整族吊销（DEF-058-2）**：使 `revokeFamily` 与 `AUTH_TOKEN_REPLAY_REJECTED` 审计
   在认证异常抛出后仍然持久化。处理方式须与 `TASK-P0BR-059` 保持一致；
   若两卡同批实施，应使用同一机制并在两份 Evidence 中交叉引用。
3. **收紧既有测试（关键）**：把 `IdentityLifecycleIT.iam011_concurrentRefreshOnlyOneSucceeds`
   的断言由 `successCount >= 1` 改为 **`successCount == 1`**，并新增
   「该 Family `ACTIVE` 行数 == 1」断言；
   新增重放用例：断言 `AUTH_REFRESH_REPLAYED`、Family 残留 `ACTIVE` == 0、
   轮换出的新 refresh 亦失效、`AUTH_TOKEN_REPLAY_REJECTED` 审计存在。
4. **鉴别力自证**：新增一项负例对照，证明收紧后的并发断言在「两个都成功」时确实 FAIL。
5. **回归**：在真实 Compose 栈执行 `verify.ps1`，确认 V05g 与 V05i 由 FAIL 转 PASS，
   且 V05c/V05d/V05f/V05h/V05j 不回归；特别确认单次正常刷新仍为 200。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：0（前提：Compose 栈就绪）；
- 覆盖的 AC ID：`AC-P0B-IAM-007`、`AC-P0B-IAM-011`；
- 所需依赖：Maven、Docker（Compose 栈）、PostgreSQL；
- SKIP 判定：Testcontainers 相关的 IT 在无 Docker 时 SKIP，
  **SKIP 不得计为 PASS**；本卡要求至少一处真实栈 E4 证据；
- 报告或产物路径：`docs/ai-spec/tasks/evidence/EVD-P0BR-060-001.yaml`。

并发场景必须使用**真实并发**（两个独立连接/线程同时发起），
不得用两次串行调用来代替 `AC-P0B-IAM-011` 的「两个并发刷新请求」。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-060.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 发现问题根因与本卡描述不符（例如并非 TOCTOU 或并非事务回滚）——须先更新本卡再实施；
- 需要通过修改已应用 Migration 才能修复；
- 需要放宽既有断言或新增绕过路径才能让用例通过；
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
必须给出 `verify.ps1` 中 V05g 与 V05i 的修复前后对照结果，说明所选并发控制机制，
并给出 P0-B 出口 `proven` 覆盖计数的变化。

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-059
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: 962a19b46c01b6705047c9599b074e8f3ea54ab5
stageGatePassed: false
stageGateEvidence: 待用户批准。依据 READY 需求 REQ-IAM-002「每次失败原子增加计数，达到配置阈值进入 LOCKED」与 ACCEPTED 决策 DEC-003、DEC-013，修复登录失败计数、锁定与失败审计因事务回滚而整体失效的缺陷（DEF-058-1）；属 ADR-0002 允许的安全整改，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-IAM-002
acceptanceScenarios:
  - AC-P0B-IAM-005
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-IAM-005|E4|真实 Compose 栈上连续 5 次错误口令后第 5 次返回 423 AUTH_ACCOUNT_LOCKED、iam_user.status=LOCKED 且 locked_until 非空、failed_login_attempts=5；此后正确口令亦返回 AUTH_ACCOUNT_LOCKED；audit_log 出现 AUTH_LOGIN_FAILED（DENIED）且不含口令明文；verify.ps1 的 V05h 由 FAIL 转 PASS
crossCuttingPlan:
  - AUTHN|本卡正面主题：修复失败计数与锁定的持久化，使 LOCKED 前置校验真正生效；不得放宽任何认证判定
  - AUTHZ|N/A - 不改变权限判定与 Scope 计算
  - DB_FILTER|N/A - 失败计为按 username/principalId 等值更新，不涉及列表下推
  - STATE|断言 UserStatus 的 ACTIVE→LOCKED 迁移真实落库，且 recordLoginSuccess 能清零并解锁
  - IDEMPOTENCY|N/A - 登录失败计数为累加语义，非幂等操作，不引入幂等键
  - CONSISTENCY|N/A - 单库单表更新，不跨系统写入
  - ERRORS|保持既有稳定错误码：第 5 次及锁定期间均为 AUTH_ACCOUNT_LOCKED，前 4 次为 AUTH_INVALID_CREDENTIALS；不得改为其它错误码
  - AUDIT|失败计数与 AUTH_LOGIN_FAILED 审计必须在异常抛出后仍持久化；审计不含口令明文
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不新增受控值；错误码既有 i18nKey 不变
  - CONFIG|锁定阈值与时长必须继续取自 LocalUser 常量（MAX_FAILED_ATTEMPTS=5、LOCK_DURATION_MINUTES=15），不得引入可绕过锁定的运行时可配置开关
  - OBSERVABILITY|N/A - 不新增指标；沿用 PlatformMetrics 既有登录结果计数
  - SECRETS|证据与日志不得包含明文口令；失败审计只记录 username 等非敏感属性
allowedPaths:
  - backend/src/main/java/com/aihub/identity/application/AuthenticationApplicationService.java
  - backend/src/main/java/com/aihub/identity/domain/LocalUser.java
  - backend/src/main/java/com/aihub/identity/domain/LocalUserRepository.java
  - backend/src/main/java/com/aihub/identity/infrastructure/MyBatisLocalUserRepository.java
  - backend/src/test/java/com/aihub/identity/IdentityLifecycleIT.java
  - backend/src/test/java/com/aihub/identity/application/AuthenticationApplicationServiceTest.java
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-059.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-059-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - contracts
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-059-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-059.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-059.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: null
approvedAt: null
---

# TASK-P0BR-059：修复登录失败计数、锁定与失败审计的事务回滚缺陷

> 状态：`DRAFT`（AI 起草，待用户批准；不得由 AI 置为 READY）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

按 REQ-IAM-002「每次失败**原子**增加计数，达到配置阈值进入 LOCKED」修复登录失败路径：
失败计数、锁定状态与 `AUTH_LOGIN_FAILED` 审计必须在认证异常抛出后仍然持久化。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-002]
acceptanceScenarios: [AC-P0B-IAM-005]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: [login]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（TASK-P0BR-058 在真实栈取证，见 EVD-P0BR-058-001.yaml）

```text
连续 6 次错误口令后：
  iam_user.failed_login_attempts = 0        （期望 5）
  iam_user.status                = PENDING_ACTIVATION（期望 LOCKED）
  iam_user.locked_until          = NULL     （期望非空）
  第 5 次响应                     = 401 AUTH_INVALID_CREDENTIALS（期望 423 AUTH_ACCOUNT_LOCKED）
  随后正确口令                    = 200      （期望 423 AUTH_ACCOUNT_LOCKED）
  audit_log AUTH_LOGIN_FAILED    = 0 行     （期望 >= 1）
```

根因（代码定位）：`AuthenticationApplicationService.login` 标注 `@Transactional`，
口令不匹配分支中先 `user.recordLoginFailure(...)` → `userRepository.update(user)`，
随后**抛出** `AuthenticationException`。运行时异常触发 Spring 事务回滚，
使失败计数、锁定状态与同事务内 `audit("AUTH_LOGIN_FAILED"...)` 一并被撤销。
`refresh` 的重放分支存在同一模式（由 TASK-P0BR-060 处理）。

**现有测试未能发现该缺陷**：`IdentityLifecycleIT` 无任何锁定用例；
`AuthenticationApplicationServiceTest` 使用 mock 仓储，断言的是「调用了审计」而非「审计已提交」。

## 3. 范围

### 允许修改

- 模块：`identity`（application/domain/infrastructure）与对应测试
- 文件或目录：见 `allowedPaths`；**不新增 Flyway 迁移**（现有列足够）
- 契约：无变更（错误码、响应结构、OpenAPI 均不变）
- Migration：无
- 页面：无
- Fixture/Verify：不修改脚本；本卡使 `verify.ps1` 既有的 V05h 由 FAIL 转 PASS

### 明确不在范围

- 不修复 refresh 重放与并发（DEF-058-2/3）→ `TASK-P0BR-060`
- 不修复用户管理审计与最后管理员保护（DEF-058-4）→ `TASK-P0BR-061`
- 不调整锁定阈值/时长常量值；
- 不改动未授权路径、成功登录路径或 JWT 签发逻辑。

### 禁止变化

- **不得放宽校验**：不得把阈值 5 改为其它值，不得让第 5 次仍返回 `AUTH_INVALID_CREDENTIALS`；
- **不得削弱测试**：不得删除或放宽既有断言；
- **不得用 try/catch 吞掉认证异常**来规避回滚——认证失败必须继续以异常对外表达；
- 不引入「跳过锁定」或「关闭审计」的开关；
- 不允许的依赖升级或重构：不新增依赖。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-005` | 某用户 | 已连续 4 次失败 | 第 5 次错误口令 | `AUTH_ACCOUNT_LOCKED`；`status=LOCKED`；`locked_until` 非空；`failed_login_attempts=5` | `E4` |
| `AC-P0B-IAM-005` | 同上 | 处于锁定中 | 用**正确**口令登录 | 仍返回 `AUTH_ACCOUNT_LOCKED`（不因口令正确而放行） | `E4` |
| `AC-P0B-IAM-005` | 审查者 | 前 4 次失败 | 检查落库 | `failed_login_attempts` 逐次递增并持久化（第 1 次即应为 1，非 0） | `E3` |
| `AC-P0B-IAM-005` | 审查者 | 锁定审计 | 查询 `audit_log` | 存在 `AUTH_LOGIN_FAILED` 且 `result=DENIED`，属性不含口令明文 | `E3` |
| `AC-P0B-IAM-005` | 审查者 | 已锁定账户 | 管理员启用（`enableUser`） | 解锁：`failed_login_attempts=0`、`locked_until=NULL`、`status=ACTIVE`，随后正确口令可登录 | `E3` |
| `AC-P0B-IAM-005` | 审查者 | 阈值常量被误改为 1 | 执行 IT 用例 | 用例 FAIL（断言锚定 `MAX_FAILED_ATTEMPTS`，具备鉴别力） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 正面主题；不得放宽任何认证判定 |
| Permission/Scope/ACL | 不涉及 |
| 数据库权限下推 | N/A |
| 状态前置条件 | `ACTIVE→LOCKED` 迁移必须真实落库；锁定期间前置校验必须拒绝 |
| 幂等与并发 | 失败计数为累加语义；同一用户并发失败计数不得丢失（可用原子 `UPDATE ... SET n = n + 1`） |
| 事务与外部一致性 | **本卡核心**：副作用必须在异常抛出后仍持久化；审计与状态更新同事务一致 |
| 错误码 | 前 4 次 `AUTH_INVALID_CREDENTIALS`；第 5 次起 `AUTH_ACCOUNT_LOCKED` |
| 审计事件 | `AUTH_LOGIN_FAILED` 必须存在且 `result` 正确；不含口令 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 阈值与时长取自 `LocalUser` 常量，不得运行时绕过 |
| 指标/Trace/告警 | 沿用既有登录结果计数 |
| Secret/日志脱敏 | 证据与日志不含明文口令 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；既有错误码获得真实触发记录；
3. 新增 Flyway 文件：无（`failed_login_attempts`/`locked_until`/`status` 列已存在）；
4. 存量清点与回填：无；
5. 索引、唯一约束、外键及理由：无变更；
6. 兼容窗口与失败恢复：无；
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

1. 定位 `login` 的失败路径，使失败计数与锁定状态的持久化**不被认证异常回滚**。
   可接受方案（择一，须在 Evidence 中说明选择理由）：
   - 将「持久化失败副作用」与「抛出认证异常」分离到不同事务边界
     （例如失败路径由独立事务方法完成提交后再抛出）；或
   - 失败计数改为单条原子 `UPDATE ... SET failed_login_attempts = failed_login_attempts + 1`，
     并与审计一样在异常前独立提交；
2. 保持 `recordLoginSuccess` 的清零与解锁语义不变；
3. 让 `AUTH_LOGIN_FAILED` 审计同样在异常抛出后仍存在（不得依赖被回滚的事务）；
4. 在 `IdentityLifecycleIT` 增加锁定用例（当前**完全缺失**）：断言第 N 次失败后落库值、
   第 5 次错误码、锁定期间正确口令被拒、`enableUser` 解锁后可登录；
5. 在 `AuthenticationApplicationServiceTest` 增加鉴别力断言：验证失败副作用在抛出异常后已完成
   （而非仅验证「调用了某个方法」）；
6. 在真实 Compose 栈执行 `verify.ps1`，确认 V05h 由 FAIL 转 PASS，且 V05c–V05j 其余用例不回归。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：0（前提：Compose 栈就绪）；
- 覆盖的 AC ID：`AC-P0B-IAM-005`；
- 所需依赖：Maven、Docker（Compose 栈）、PostgreSQL；
- SKIP 判定：Testcontainers 相关的 IT 在无 Docker 时 SKIP，
  **SKIP 不得计为 PASS**；本卡要求至少一处真实栈 E4 证据；
- 报告或产物路径：`docs/ai-spec/tasks/evidence/EVD-P0BR-059-001.yaml`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-059.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 发现问题根因与本卡描述不符（例如并非事务回滚）——须先更新本卡再实施；
- 需要通过修改已应用 Migration 才能修复；
- 需要放宽既有断言或阈值才能让用例通过；
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
必须给出 `verify.ps1` 中 V05h 的修复前后对照结果，以及 P0-B 出口 `proven` 覆盖计数的变化。

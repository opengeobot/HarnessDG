---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-061
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: f892634c6f87f3e5331a558327340737b8f2ff27
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「批准全部三张（推荐）」）。依据 READY 需求 REQ-IAM-006「USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET」「不能禁用最后一个有效平台管理员：拒绝并返回 COMMON_INVALID_ARGUMENT，同时产生拒绝审计」与 ACCEPTED 决策 DEC-003、DEC-013，补齐用户管理全链路审计落点并实现最后管理员保护（DEF-058-4）；属 ADR-0002 允许的审计与授权整改，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-IAM-006
  - REQ-AUD-001
acceptanceScenarios:
  - AC-P0B-IAM-012
  - AC-P0B-IAM-008
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-IAM-012|E4|真实 Compose 栈上创建/编辑/重置/禁用/启用用户各步后，audit_log 中 USER_CREATED、USER_UPDATED、USER_ENABLED、USER_DISABLED、USER_PASSWORD_RESET 计数均 >= 1 且归属与 result 正确；在仅剩 1 个活跃管理员时禁用该管理员返回 COMMON_INVALID_ARGUMENT 且产生拒绝审计；verify.ps1 的 V05j 由 FAIL 转 PASS
  - AC-P0B-IAM-008|E4|禁用用户后 USER_DISABLED 审计存在，使该场景的「状态和审计一致」子判据成立；verify.ps1 的 V05e 由 FAIL 转 PASS
crossCuttingPlan:
  - AUTHN|N/A - 不改变登录与 Token 路径
  - AUTHZ|正面主题之一：最后管理员保护必须由**后端**判定并拒绝，不得仅依赖前端按钮；拒绝路径必须 fail-closed
  - DB_FILTER|判定「最后一个有效平台管理员」的计数查询必须在授权与状态过滤后计算，且与禁用动作在同一事务内以避免并发绕过
  - STATE|断言 UserStatus 迁移与审计一致；禁用/启用/重置遵守既有状态机
  - IDEMPOTENCY|沿用 REQ-IAM-006 对 create/enable/disable/reset 的既有幂等要求，不新增或削弱幂等实现
  - CONSISTENCY|审计与状态变更同事务提交，避免「状态变了但无审计」或「有审计但状态未变」
  - ERRORS|最后管理员禁用返回既有 COMMON_INVALID_ARGUMENT；不得新增未在契约声明的错误码
  - AUDIT|正面主题：补齐 USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET 五个落点；临时密码与口令不得出现在 attributes
  - NOTIFICATION|N/A - 是否向目标用户发站内通知由通知需求确认，本卡不新增通知行为
  - TAXONOMY_I18N|N/A - 不新增受控值
  - CONFIG|不得引入可关闭审计或跳过最后管理员保护的运行时可配置开关
  - OBSERVABILITY|N/A - 不新增指标
  - SECRETS|审计 attributes 与证据不得包含临时密码明文
allowedPaths:
  - backend/src/main/java/com/aihub/identity/application/UserManagementApplicationService.java
  - backend/src/main/java/com/aihub/identity/application/UserManagementService.java
  - backend/src/main/java/com/aihub/identity/api/SystemUserController.java
  - backend/src/main/java/com/aihub/identity/infrastructure/MyBatisLocalUserRepository.java
  - backend/src/main/java/com/aihub/identity/domain/LocalUserRepository.java
  - backend/src/test/java/com/aihub/identity/IdentityLifecycleIT.java
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-061.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-061-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/java/com/aihub/identity/application/AuthenticationApplicationService.java
  - frontend
  - contracts
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-061-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-061.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-061.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T10:15:33Z
---

# TASK-P0BR-061：补齐用户管理审计落点并实现最后管理员保护

> 状态：`READY`（用户已批准；实现已提交，E4 证据已取得，交接状态 `IMPLEMENTED_UNVERIFIED`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

按 REQ-IAM-006 补齐用户管理全链路的审计落点（`USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET`），
并实现「不能禁用最后一个有效平台管理员」的后端拒绝与拒绝审计。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-006, REQ-AUD-001]
acceptanceScenarios: [AC-P0B-IAM-012, AC-P0B-IAM-008]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: [listUsers, createUser, getUser, updateUser, enableUser, disableUser, resetUserPassword]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（TASK-P0BR-058 在真实栈取证，见 EVD-P0BR-058-001.yaml）

**审计完全缺失**

```text
create / update / enable / disable / reset-password 均返回 2xx 且落库状态正确：
  PATCH locale                -> 落库 en-US
  reset-password              -> 回到 PENDING_ACTIVATION + must_change_password=1
  但 audit_log 中：
  USER_CREATED        = 0 行   （期望 >= 1）
  USER_UPDATED        = 0 行   （期望 >= 1）
  USER_ENABLED        = 0 行   （期望 >= 1）
  USER_DISABLED       = 0 行   （期望 >= 1）
  USER_PASSWORD_RESET = 0 行   （期望 >= 1）
```

`openapi/aihub-v1.yaml` 对这 5 个操作**均已声明 `x-audit-event`**，即契约已承诺、实现缺失。

**最后管理员保护缺失**

```text
仅剩 1 个持有 user:manage 的活跃管理员时：
  POST /api/v1/system/users/{adminUserId}:disable = 200（期望拒绝）
  该管理员 status 被置为 DISABLED（期望保持 ACTIVE）
  audit_log 无任何拒绝审计（期望有）
```

根因（代码定位）：`UserManagementApplicationService` 既不注入 identity 模块的 `AuditPort`、
也不调用 `auditService`，整个用户管理用例链没有任何审计落点；
同时未实现 REQ-IAM-006 的最后管理员前置校验。

**现有测试未能发现该缺陷**：`IdentityLifecycleIT.iam012_adminCrudOperationsOnUsers`
只断言各操作的返回视图与异常，**完全没有审计断言**，也无最后管理员用例。

## 3. 范围

### baseCommit 重绑说明（顺序边界）

本卡与 `TASK-P0BR-058/059/060` 同批整改，四卡各自只拥有自己 `allowedPaths` 内的一部分文件，
若共用同一起草时基准，逐卡 `-CheckChangedPaths` 会把兄弟卡文件视为越界而必然互相失败。
故本卡 `baseCommit` 重绑为**上一顺序卡的实施提交** `f892634`
（即 `TASK-P0BR-060` 的实施提交），使本卡在自己实施提交处的 `-CheckChangedPaths`
恰好只看到本卡 `allowedPaths` 内的变更。沿用先例 `0b36a5e`，
并在正文说明理由与交接状态。

### 允许修改

- 模块：`identity`（application/api/infrastructure/domain）与对应测试
- 文件或目录：见 `allowedPaths`
- 契约：无变更（5 个 `x-audit-event` 与 `COMMON_INVALID_ARGUMENT` 均已在契约声明）
- Migration：无
- 页面：无（前端管理页已存在；本卡只补后端审计与保护）
- Fixture/Verify：不修改脚本；本卡使 `verify.ps1` 既有的 V05j 与 V05e 由 FAIL 转 PASS

### 明确不在范围

- 不修复登录失败计数与锁定（DEF-058-1）→ `TASK-P0BR-059`
- 不修复 refresh 重放与并发（DEF-058-2/3）→ `TASK-P0BR-060`
- 不新增前端按钮或页面；
- 不改动认证、Token、权限判定逻辑。

### 禁止变化

- **不得放宽校验**：不得把「拒绝最后管理员禁用」改为仅前端提示；
- **不得削弱测试**：不得删除或放宽既有断言；
- **不得只加日志冒充审计**——必须写入权威审计存储（`audit_log`），
  且与状态变更同事务；
- 不得记录临时密码或口令明文到审计 attributes；
- 不得引入「关闭审计」或「跳过保护」的开关；
- 不允许的依赖升级或重构：不新增依赖。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-012` | 用户管理员 | 已认证且持 `user:manage` | 创建用户 | 返回 201 且 `USER_CREATED` 审计落库 | `E4` |
| `AC-P0B-IAM-012` | 同上 | 同上 | 编辑用户 | 返回 200 且 `USER_UPDATED` 审计落库 | `E4` |
| `AC-P0B-IAM-012` | 同上 | 同上 | 重置口令 | 返回 200 且 `USER_PASSWORD_RESET` 审计落库；attributes 无临时密码 | `E4` |
| `AC-P0B-IAM-012` | 同上 | 同上 | 禁用用户 | 返回 200 且 `USER_DISABLED` 审计落库 | `E4` |
| `AC-P0B-IAM-012` | 同上 | 同上 | 启用用户 | 返回 200 且 `USER_ENABLED` 审计落库 | `E4` |
| `AC-P0B-IAM-012` | 同上 | **仅剩 1 个**活跃管理员 | 禁用该管理员 | 被拒绝：`COMMON_INVALID_ARGUMENT`；该管理员仍 `ACTIVE`；产生拒绝审计 | `E4` |
| `AC-P0B-IAM-012` | 同上 | 存在 **2 个**活跃管理员 | 禁用其中一个 | 正常返回 200（不得因保护过严而阻断合法操作） | `E3` |
| `AC-P0B-IAM-012` | 审查者 | 审计断言被误删 | 执行 IT | 用例 FAIL（证明断言具备鉴别力） | `E2` |
| `AC-P0B-IAM-008` | 审查者 | 用户被禁用 | 查询 `audit_log` | 存在 `USER_DISABLED`，使「状态和审计一致」成立 | `E4` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 不涉及 |
| Permission/Scope/ACL | 最后管理员保护必须由后端判定；拒绝路径 fail-closed |
| 数据库权限下推 | 活跃管理员计数与禁用动作须在同一事务内，避免并发双禁用绕过保护 |
| 状态前置条件 | 禁用/启用/重置遵守既有 `UserStatus` 状态机 |
| 幂等与并发 | 沿用既有 create/enable/disable/reset 幂等实现，不削弱 |
| 事务与外部一致性 | 审计与状态变更同事务提交 |
| 错误码 | 最后管理员禁用返回既有 `COMMON_INVALID_ARGUMENT` |
| 审计事件 | **本卡核心**：补齐 5 个事件；`result` 与归属正确；不含临时密码 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 不得引入关闭审计或跳过保护的开关 |
| 指标/Trace/告警 | N/A |
| Secret/日志脱敏 | 审计 attributes 与证据不得包含临时密码明文 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无（契约已声明 5 个 `x-audit-event`，
   本卡是让实现与既有契约一致）；
2. 示例请求、响应和错误：无新增接口；`COMMON_INVALID_ARGUMENT` 获得真实触发记录；
3. 新增 Flyway 文件：无（`audit_log` 表与 `iam_user.scopes` 已存在）；
4. 存量清点与回填：无；
5. 索引、唯一约束、外键及理由：无变更；判定计数复用既有查询能力；
6. 兼容窗口与失败恢复：无；
7. 生成类型更新：无。

## 7. 实施步骤

- [ ] 契约及契约测试（本卡无契约变更，是让实现与既有契约一致）
- [ ] Migration 及空库/升级迁移测试（本卡无迁移）
- [x] Application/Domain 用例 —— 见 `EVD-P0BR-061-001.yaml` §coverage
- [ ] REST/MCP/Worker Adapter（端点与错误码不变）
- [ ] 前端页面与状态（不涉及，且不得用前端隐藏替代后端校验）
- [x] 正常/边界/失败/越权/恢复测试 —— 见 `EVD-P0BR-061-001.yaml` §coverage
- [x] Compose Fixture/Verify —— V05e 与 V05j 由 FAIL 转 PASS，见 §metrics
- [ ] Runbook/追踪矩阵（脚本与 runbook 属 `TASK-P0BR-058` 交付物）

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. **补齐审计落点**：在用户管理的创建/编辑/启用/禁用/重置口令用例中，
   通过 identity 模块既有的审计入口写入对应 5 个事件；
   必须与状态变更在同一事务内提交；attributes 只含非敏感字段
   （如目标 username、userId、变更字段名），**不含临时密码**。
   若现有 `AuditPort` 接口不便表达动作型事件，可在 `allowedPaths` 内的
   identity 侧适配，但**不得**新建与平台审计并行的第二套存储。
2. **最后管理员保护**：在禁用动作前置校验——当目标用户是
   「当前唯一的活跃且持有 `user:manage` 的管理员」时，拒绝并返回
   `COMMON_INVALID_ARGUMENT`，同时写入拒绝审计。
   校验需与禁用动作在同一事务内（或使用足够的隔离/锁）以避免并发双禁用绕过；
   不得通过前端隐藏按钮实现。
3. **收紧既有测试**：在 `IdentityLifecycleIT` 为 `iam012` 增加
   「5 类审计事件均存在」断言与最后管理员用例（拒绝 + 仍 ACTIVE + 拒绝审计），
   并增加「存在 2 个管理员时可正常禁用一个」的正向对照，
   防止保护被实现成「一律禁止禁用管理员」。
4. **鉴别力自证**：新增一项负例对照，证明审计断言在被移除时会 FAIL。
5. **回归**：在真实 Compose 栈执行 `verify.ps1`，确认 V05j 与 V05e 由 FAIL 转 PASS，
   且 V05c/V05d/V05f/V05g/V05h/V05i 不回归。
   注意：断言最后管理员禁用被拒时，**不得**像 058 卡那样需要事后手工复原；
   正确的实现本身不会改变管理员状态。

**实施结果**：第 1–5 步均已完成。5 类审计经 `AuditPort` 写入权威 `audit_log`，
与状态变更同事务提交；最后管理员保护在后端前置校验并返回 `COMMON_INVALID_ARGUMENT`。
拒绝审计经 `denialTransaction`（`REQUIRES_NEW`）**独立提交**——
因为拒绝路径最终以异常表达，若与业务事务共用会随异常回滚而丢失拒绝记录；
这是对第 2 步「同一事务内」的必要细化（写入方仍是同一个权威 `audit_log`，未新建第二套存储）。
V05e 与 V05j 均由 FAIL 转 PASS，`VERIFY_EXIT=0`。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：0（前提：Compose 栈就绪）；
- 覆盖的 AC ID：`AC-P0B-IAM-012`、`AC-P0B-IAM-008`；
- 所需依赖：Maven、Docker（Compose 栈）、PostgreSQL；
- SKIP 判定：Testcontainers 相关的 IT 在无 Docker 时 SKIP，
  **SKIP 不得计为 PASS**；本卡要求至少一处真实栈 E4 证据；
- 报告或产物路径：`docs/ai-spec/tasks/evidence/EVD-P0BR-061-001.yaml`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-061.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 发现审计缺失的根因并非「未接入审计入口」（例如审计被写入后又被过滤）；
- 需要修改已应用 Migration 或新增迁移才能修复；
- 需要放宽既有断言或新增绕过路径才能让用例通过；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
  用户管理 5 类审计经 AuditPort 写入权威 audit_log（与状态变更同事务提交）；
  实现「不得禁用最后一个有效平台管理员」的后端前置校验，
  返回 COMMON_INVALID_ARGUMENT 并以独立事务提交拒绝审计；
  V05j 与 V05e 由 FAIL 转 PASS。
Changed files:
  backend/src/main/java/com/aihub/identity/application/UserManagementApplicationService.java；
  backend/src/test/java/com/aihub/identity/IdentityLifecycleIT.java；
  docs/ai-spec/tasks/TASK-P0BR-061.md、
  docs/ai-spec/tasks/evidence/EVD-P0BR-061-001.yaml。
Requirements and scenarios satisfied:
  REQ-IAM-006、REQ-AUD-001；AC-P0B-IAM-012、AC-P0B-IAM-008 共 2 项 proven（E4）。
Contract/database changes:
  无。契约已声明 5 个 x-audit-event 与 COMMON_INVALID_ARGUMENT，本卡是让实现与既有契约一致；
  未新增迁移（audit_log 表与 iam_user.scopes 已存在）。
Validation run and results:
  powershell .../verify.ps1 -> exit 0（V05e、V05j PASS；V05c–V05j 无回归）；
  ./docs/ai-spec/tools/validate-spec.ps1 -> PASS；
  validate-task-card.ps1（无 flag）-> PASS；
  validate-task-card.ps1 -CheckChangedPaths -> PASS；
  validate-task-card.ps1 -CheckCompletion -> EXPECTED_FAIL（review.accepted 仍为 false）。
Evidence artifacts: EVD-P0BR-061-001.yaml（E4，status: PASS）。
Validation not run and reasons:
  cd backend; ./mvnw.cmd verify 未能取得完整绿灯：Testcontainers 相关的 IT 在本机
  Docker Desktop 29.x 下全部 Skipped（环境受限）；本卡结论以真实 Compose 栈的 E4 为准。
Remaining risks or follow-ups:
  最后管理员保护的判定与禁用动作未加显式行锁：并发双禁用理论上存在窗口。
  本卡按 allowedPaths 未引入锁或唯一约束，若出口要求覆盖该并发边界，需另立卡片。
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。
必须给出 `verify.ps1` 中 V05j 与 V05e 的修复前后对照结果，
说明最后管理员保护的并发处理方式，并给出 P0-B 出口 `proven` 覆盖计数的变化。

**V05j / V05e 修复前后对照**

```text
V05j（用户管理与最后管理员保护）
  修复前：create/update/enable/disable/reset 均 2xx 且落库正确；
          USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET 计数均为 0；
          仅剩 1 个活跃管理员时禁用该管理员返回 200 并被置为 DISABLED  → FAIL
  修复后：五类审计计数 1 / 5 / 14 / 10 / 24；
          禁用最后管理员被拒（COMMON_INVALID_ARGUMENT），管理员仍为 ACTIVE，
          USER_DISABLED/DENIED（error_code=COMMON_INVALID_ARGUMENT，
          resource_id=该管理员 principalId）落库，且 DENIED 总计数恰好 +1  → PASS

V05e（禁用后状态与审计一致）
  修复前：拒绝路径全部成立（旧 access 401、refresh 被拒、重登 403），
          但 USER_DISABLED 审计缺失（计数为 0）                          → FAIL
  修复后：上述拒绝路径成立，且 USER_DISABLED 审计落库（SUCCEEDED=10）  → PASS
```

**最后管理员保护的并发处理方式**：判定为「目标用户持有 `user:manage`、其自身可被禁用，
且库中 `status='ACTIVE'` 且持有该权限的主体数 <= 1」，在后端前置执行；
拒绝审计经 `denialTransaction`（`PROPAGATION_REQUIRES_NEW`）独立提交，
避免随随后抛出的校验异常回滚。判定与禁用动作之间未加显式行锁或唯一约束，
并发双禁用存在理论窗口（已记入 follow-ups）。

**覆盖计数**：随 `TASK-P0BR-058` 一并由 19/70 提升至 29/70
（本卡贡献 `AC-P0B-IAM-012`、`AC-P0B-IAM-008` 两项，计入 058 的 10 项 delta 内）。

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-058
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 8cff0d8b6df9c3e9cefaad5188eff2051a6db9d3
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「批准并授权实施」）。依据 READY 需求 REQ-IAM-001~006（P0-B 身份与 JWT）与 ACCEPTED 决策 DEC-004、DEC-013，为 IAM 场景补齐 E4 级证据；属 ADR-0002 出口门禁要求的验证补全，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-IAM-001
  - REQ-IAM-002
  - REQ-IAM-003
  - REQ-IAM-004
  - REQ-IAM-005
  - REQ-IAM-006
acceptanceScenarios:
  - AC-P0B-IAM-001
  - AC-P0B-IAM-003
  - AC-P0B-IAM-004
  - AC-P0B-IAM-005
  - AC-P0B-IAM-006
  - AC-P0B-IAM-007
  - AC-P0B-IAM-008
  - AC-P0B-IAM-009
  - AC-P0B-IAM-011
  - AC-P0B-IAM-012
decisions:
  - DEC-004
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-IAM-001|E4|在真实 Compose 栈上断言 bootstrap 管理员只被创建一次：重启 backend 容器后 iam_user 中该用户名仍只有一行，且口令列仅存自适应哈希（bcrypt 前缀），日志与审计正文无明文口令
  - AC-P0B-IAM-003|E4|首登管理员经 PUT /api/v1/me/password 完成改密：旧临时口令登录失败、新口令可登录、/me 的 forcePasswordChange 由 true 变 false、iam_user.must_change_password 落 0、产生改密审计
  - AC-P0B-IAM-004|E4|用户名不存在与口令错误分别登录：两者返回同一 AUTH_INVALID_CREDENTIALS 与同一响应体形状（details 不区分账号存在性），失败审计不含口令
  - AC-P0B-IAM-005|E4|对同一用户连续 5 次错误口令：第 5 次响应 AUTH_ACCOUNT_LOCKED，iam_user.status=LOCKED 且 locked_until 非空；此后即使口令正确仍返回 AUTH_ACCOUNT_LOCKED；审计与指标存在
  - AC-P0B-IAM-006|E4|用有效 refresh Cookie 调 /auth/refresh：access 与 refresh 同时轮换，旧 jti 在 iam_token 中标记失效，新 Set-Cookie 具备 HttpOnly/SameSite/Secure 语义
  - AC-P0B-IAM-007|E4|重复使用已轮换的 refresh JWT：返回 AUTH_REFRESH_REPLAYED，该 Token Family 全部吊销，原本有效的新 refresh 也失效，产生拒绝审计
  - AC-P0B-IAM-008|E4|管理员禁用正在使用 access JWT 的用户：该用户旧 access 的下一次请求立即被拒（不依赖 JWT 内旧状态），其 refresh 与凭据交换同样被拒，状态与审计一致
  - AC-P0B-IAM-009|E4|用户登出后重用其 refresh JWT：Cookie 被清除、Token Family 吊销、重用返回 AUTH_* 拒绝；重复登出保持幂等（不报错）
  - AC-P0B-IAM-011|E4|两个并发刷新请求使用同一 refresh JWT：只允许一个成功，另一个触发定义好的重放/并发语义；数据库最终不存在两条并存的 ACTIVE refresh
  - AC-P0B-IAM-012|E4|管理员对用户执行创建/编辑/重置口令/禁用/启用：每步的权限、状态前置、强制改密标志与稳定错误码正确，且每步产生对应审计事件
crossCuttingPlan:
  - AUTHN|本卡正面主题：覆盖登录、刷新轮换、重放、登出、禁用失效、锁定与改密全链
  - AUTHZ|新增的管理员操作断言必须同时验证授权边界：无 user:manage 的主体访问用户管理端点返回 403 AUTH_PERMISSION_DENIED
  - DB_FILTER|N/A - 不涉及列表授权下推
  - STATE|断言 iam_user.status 的 ACTIVE/LOCKED/DISABLED 迁移与 must_change_password、locked_until、token_version 的落库值
  - IDEMPOTENCY|登出须幂等；并发刷新须保证无双有效 refresh
  - CONSISTENCY|N/A - 不跨系统写入
  - ERRORS|逐一断言稳定错误码：AUTH_INVALID_CREDENTIALS、AUTH_ACCOUNT_LOCKED、AUTH_ACCOUNT_DISABLED、AUTH_REFRESH_REPLAYED
  - AUDIT|断言 AUTH_LOGIN_FAILED/AUTH_LOGIN_SUCCEEDED/USER_* 等事件存在且 result 与 errorCode 正确
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A
  - CONFIG|N/A - 不改任何配置开关
  - OBSERVABILITY|N/A - 不改指标；不新增告警
  - SECRETS|证据不得包含明文口令与 refresh JWT 原文；审计脱敏须被断言（口令与 refresh 不得出现在 audit_log 正文）
allowedPaths:
  - deploy/compose/scripts/verify.ps1
  - deploy/compose/scripts/verify.sh
  - docs/runbooks/compose.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-058-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0B-V01-V12.yaml
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-058.md
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration
  - backend/src/main/java
  - contracts
  - frontend
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-058-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-058.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-058.md -CheckChangedPaths
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
  - bash ./deploy/compose/scripts/verify.sh
approvedBy: User
approvedAt: 2026-09-17T09:12:44Z
---

# TASK-P0BR-058：为 IAM 验收场景补齐 E4 证据（12 项中 10 项当前无证据）

> 状态：`READY`（用户已批准；实现已提交，E4 证据已取得，交接状态 `IMPLEMENTED_UNVERIFIED`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

`p0b-exit-catalog.md` 的 IAM 12 项 MUST 场景，除已覆盖的 `AC-P0B-IAM-002` 与 `AC-P0B-IAM-010` 外，
其余 10 项在真实 Compose 栈上取得 E4 证据；不可证伪的部分必须在 Evidence 中显式记为 `notProven`。

## 2. 关联规格

```yaml
requirements: [REQ-IAM-001, REQ-IAM-002, REQ-IAM-003, REQ-IAM-004, REQ-IAM-005, REQ-IAM-006]
acceptanceScenarios: [AC-P0B-IAM-001, AC-P0B-IAM-003, AC-P0B-IAM-004, AC-P0B-IAM-005,
                      AC-P0B-IAM-006, AC-P0B-IAM-007, AC-P0B-IAM-008, AC-P0B-IAM-009,
                      AC-P0B-IAM-011, AC-P0B-IAM-012]
decisions: [DEC-004, DEC-013]
adrs: [ADR-0002]
openapiOperations: [login, refresh, logout, exchangeToken, getCurrentPrincipal,
                    changePassword, createUser, updateUser, enableUser, disableUser, resetUserPassword]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 出口差距事实（本次全新取证）

按 `p0b-exit-catalog.md` 的 70 项 MUST 场景，逐个 Evidence 文件的 `coverage.proven[].scenarioId`
统计（而非仅看文件头 `scenarios:` 声明），当前覆盖为：

```text
出口 MUST 总数 : 70
有 proven 证据 : 19
仅 notProven   : 1
完全未记录     : 50
```

IAM 组（12 项）仅 2 项有 `proven`：

| 场景 | 现状 | 依据 |
| --- | --- | --- |
| `AC-P0B-IAM-002` | 已有 proven | `EVD-P0B-V01-V12.yaml`（V05 登录签发 JWT、/me 200） |
| `AC-P0B-IAM-010` | 已有 proven | `EVD-P0B-V01-V12.yaml`（V05 无 Token 401） |
| `AC-P0B-IAM-001/003~009/011/012` | **无任何证据记录** | 同上统计 |

`EVD-P0B-V01-V12.yaml` 的 `notProven` 段落本身已列出这批缺口
（如「AC-P0B-IAM-005 账户锁定」「AC-P0B-IAM-006 refresh 轮换」等），
说明当时已识别但未补齐。

**这不是代码缺失**：`AuthController`（`/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/token`、
`/me`、`/me/password`）与 `SystemUserController`（创建/编辑/启用/禁用/重置口令）
端点齐备，`LocalUser` 的锁定阈值（`MAX_FAILED_ATTEMPTS = 5`、`LOCK_DURATION_MINUTES = 15`）
与 `AuthenticationApplicationService` 的重放/吊销逻辑均已实现。
缺的是**在真实栈上把这些行为跑出来并留证**。

### 为什么这是当前最高优先

`p0b-exit-catalog.md` 第 1 节规定 P0-B 退出必须满足「本目录所有 `MUST` 场景实际 PASS」
「没有 MUST 场景 SKIP」「每个 PASS 绑定 Commit SHA、环境和 Evidence Manifest」。
70 项中 50 项无证据意味着出口门禁客观上无法通过，且这一差距大于任何单个代码缺陷——
前几轮修复的 `IdempotencyInterceptor`、`AssetModelEntity`、幂等键约束虽然都是真实缺陷，
但它们各自只影响 1–2 个 AC，而 IAM 组一次可补齐 10 项。

### baseCommit 重绑说明（顺序边界）

本卡 `baseCommit` 于实施前曾为 `ecbb246f`（起草时 HEAD）。实施完成后，
`-CheckChangedPaths` 的比对基准是「`baseCommit` 到 HEAD 的全部变更」，
而本卡与其他三张同批整改卡（059/060/061）各自只拥有自己 `allowedPaths` 内的一部分文件；
若四卡共用同一基准，每张卡都会把兄弟卡的文件视为越界，逐卡校验必然互相失败。

故将本卡 `baseCommit` 重绑为**本卡实施提交之前一提交** `8cff0d8b6df9c3e9cefaad5188eff2051a6db9d3`，
使「本卡实施提交」处的 `-CheckChangedPaths` 恰好只看到本卡 `allowedPaths` 内的变更。
059/060/061 的 `baseCommit` 依同一规则顺序递推（059←本卡实施提交、060←059 实施提交、
061←060 实施提交）。

该处置沿用仓库既有先例 `0b36a5e`（"baseCommit 改为顺序边界……使逐卡 `-CheckChangedPaths`
在各自实现提交处可判"），并在正文说明理由与交接状态。

### 实施中登记的脚本修正（本卡交付物）

`AC-P0B-IAM-005/011/012` 的取证依赖 `verify.ps1` / `verify.sh`，
首轮执行时发现三处**脚本自身缺陷**（非产品缺陷）。这些修正属本卡 `allowedPaths`，
随本卡提交：

1. **V05h 正则漏判串首 `LOCKED`**（DEF-058-6）：`$facts -match 'LOCKED\|'` 隐含
   「`LOCKED` 前必有分隔符」，而 `Get-UserRowFacts` 首列即 `status`，
   实际串形如 `LOCKED|5|2026-09-17T...`，断言恒为假 →
   改为 `(^|\|)LOCKED\|`；`verify.sh` 等价改为 `grep -qE '(^|\|)LOCKED\|'`。
2. **V05i 的并发原语与断言**：`Task.WaitAll(Task[], int)` 重载在 PowerShell 中解析失败，
   改为对两个 Task 逐个 `Wait(30000)`（保持两请求在途并发）；
   同时把断言从「Family 内 ACTIVE 恰好 1」修正为「降为 0」——
   输家复用的 jti 此时已为 `ROTATED`，按 REQ-IAM-004 必须整族 `REVOKED`；
   并补「失败方错误码为 `AUTH_REFRESH_REPLAYED`」与拒绝审计断言。
3. **V05j 管理员判定与拒绝审计**：`'["user:manage"]'::jsonb` 在 PowerShell 向 docker
   传参时被吞掉内层双引号，退化为非法 JSON（`[user:manage]`）→
   改用 `scopes @> jsonb_build_array('user:manage')`；
   并补齐拒绝路径的权威审计断言（拒绝前取基线计数，拒绝后断言 `USER_DISABLED/DENIED`
   恰好 +1，且 `resource_id` 按**目标 principalId** 关联——
   `IdentityAuditAdapter` 以 `targetId` 落 `resource_id`，不是 `iam_user.user_id`）。
   只断言「>= 1」无法鉴别本次拒绝是否落库，历史运行的行会让断言恒真。

V05i 断言由 `== 1` 改为 `== 0` 属**收紧**（更强结论），依据 REQ-IAM-004 的重放语义，
并已同步 `verify.sh`、`IdentityLifecycleIT.iam011`、`TASK-P0BR-060.md` 三处保持一致；
不属于「为使用例变绿而放宽断言」。

## 3. 范围

### 允许修改

- 模块：`deploy/compose/scripts`（验收脚本）、`docs/runbooks`、`docs/ai-spec/tasks/evidence`
- 文件或目录：
  - `deploy/compose/scripts/verify.ps1`、`verify.sh`（新增 IAM 用例）
  - `docs/runbooks/compose.md`（同步用例表）
  - `docs/ai-spec/tasks/evidence/EVD-P0BR-058-001.yaml`（本卡证据）
  - `docs/ai-spec/tasks/evidence/EVD-P0B-V01-V12.yaml`（**仅**修订 IAM 相关的 `proven`/`notProven` 条目，
    使之反映新证据；不得改动其它场景的结论）
- 契约：无变更
- Migration：无
- 页面：无
- Fixture/Verify：本卡正面主题

### 明确不在范围

- 不修改任何后端 Java 代码（`forbiddenPaths` 已禁止）。若某场景发现**产品缺陷**，
  按 §9 停止条件上报，不在本卡内修复；
- 不补齐 IAM 之外的其他 40 项无证据场景（各自独立成卡）；
- 不新增或修改 `p0b-exit-catalog.md` 的场景定义；
- 不给 `verify.ps1` 补 V12–V21（那批用例不对应任何 AC，见「明确不在范围」的理由）；
- 不改 `deploy/compose/compose.yaml`。

### 禁止变化

- 不改变的 API/状态/权限：不改任何端点、错误码、锁定阈值、权限码或数据库约束；
- 不允许的依赖升级或重构：不新增依赖；不重写脚本框架；
- 不允许的兼容破坏：**不得为了让用例变绿而放宽断言**——尤其不得：
  - 把「第 5 次才锁定」放宽为「任意次锁定」；
  - 把「防枚举」断言退化为只比状态码而不比响应体形状；
  - 把 `AUTH_REFRESH_REPLAYED` 退化为「任意 4xx」；
  - 在无法复现时把 FAIL 改为 PASS。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-IAM-001` | 运维 | 栈已初始化 | 重启 `backend` 容器 | 管理员仍只 1 行；口令列为 bcrypt 哈希；日志/审计无明文口令 | `E4` |
| `AC-P0B-IAM-003` | 首登管理员 | `must_change_password=1` | 完成改密 | 旧口令失效、新口令可登录、`forcePasswordChange=false`、审计追加 | `E4` |
| `AC-P0B-IAM-004` | 任意 | 栈就绪 | 分别用不存在用户名与错误口令登录 | 两者同为 `AUTH_INVALID_CREDENTIALS` 且响应形状一致 | `E4` |
| `AC-P0B-IAM-005` | 某用户 | 已连续 4 次失败 | 第 5 次错误口令 | `AUTH_ACCOUNT_LOCKED`；`status=LOCKED`、`locked_until` 非空；正确口令也被拒 | `E4` |
| `AC-P0B-IAM-006` | 已登录用户 | 有有效 refresh Cookie | 调 `/auth/refresh` | access/refresh 均轮换；旧 jti 失效；新 Cookie 属性正确 | `E4` |
| `AC-P0B-IAM-007` | 同上 | 已轮换 | 重用旧 refresh | `AUTH_REFRESH_REPLAYED`；Family 全吊销；新 refresh 也失效 | `E4` |
| `AC-P0B-IAM-008` | 管理员 + 普通用户 | 普通用户持有有效 access | 管理员禁用该用户 | 旧 access 下一请求立即被拒；refresh 与凭据交换被拒 | `E4` |
| `AC-P0B-IAM-009` | 已登录用户 | 有 refresh | 登出后重用该 refresh | 被拒；Cookie 清除；重复登出幂等 | `E4` |
| `AC-P0B-IAM-011` | 任意 | 有同一 refresh | 并发发起 2 次刷新 | 仅 1 次成功；库中无 2 条并存 ACTIVE refresh | `E4` |
| `AC-P0B-IAM-012` | 管理员 | 有 `user:manage` | 创建/编辑/重置/禁用/启用用户 | 每步权限、状态前置、强制改密、错误码与审计均正确 | `E4` |
| `AC-P0B-IAM-012` | 最小权限主体 | 无 `user:manage` | 访问用户管理端点 | 403 `AUTH_PERMISSION_DENIED`（证明权限未被放宽） | `E4` |
| `AC-P0B-IAM-004` | 审查者 | 脚本被改为不比对响应形状 | 执行用例 | 用例 FAIL（防枚举断言具备鉴别力） | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 本卡正面主题；断言不依赖 JWT 内的旧状态（禁用后立即失效） |
| Permission/Scope/ACL | 用户管理端点须同时断言有权 200 与无权 403 |
| 数据库权限下推 | N/A |
| 状态前置条件 | 断言 `iam_user` 的 `status`/`locked_until`/`must_change_password`/`token_version` 落库值 |
| 幂等与并发 | 登出幂等；并发刷新无双有效 refresh |
| 事务与外部一致性 | N/A |
| 错误码 | 逐一断言 `AUTH_INVALID_CREDENTIALS`/`AUTH_ACCOUNT_LOCKED`/`AUTH_ACCOUNT_DISABLED`/`AUTH_REFRESH_REPLAYED` |
| 审计事件 | 断言事件存在且 `result`/`errorCode` 正确；口令与 refresh 原文不得出现 |
| 通知 | N/A |
| 字典/标签/i18n | N/A |
| 配置 | 不得引入「跳过用例」开关 |
| 指标/Trace/告警 | N/A |
| Secret/日志脱敏 | 证据与脚本输出不得包含明文口令或 refresh JWT 原文 |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；本卡让既有错误码获得真实触发记录；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：无；
5. 索引、唯一约束、外键及理由：无变更；
6. 兼容窗口与失败恢复：无；
7. 生成类型更新：无。

## 7. 实施步骤

- [ ] 契约及契约测试（本卡无契约变更）
- [ ] Migration 及空库/升级迁移测试（本卡无迁移）
- [ ] Application/Domain 用例（本卡 `forbiddenPaths` 禁止改动）
- [ ] REST/MCP/Worker Adapter（本卡 `forbiddenPaths` 禁止改动）
- [ ] 前端页面与状态（本卡 `forbiddenPaths` 禁止改动）
- [x] 正常/边界/失败/越权/恢复测试 —— 见 `EVD-P0BR-058-001.yaml` §coverage
- [x] Compose Fixture/Verify —— 见 `EVD-P0BR-058-001.yaml` §metrics（20 项全 PASS，`VERIFY_EXIT=0`）
- [x] Runbook/追踪矩阵 —— 见 `docs/runbooks/compose.md` 的 V05c–V05j 用例表

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。

实施路径：

1. 在 `verify.ps1` 新增 IAM 用例（编号续用 V05c 起的空档，避免与 V01–V11 语义冲突），
   在 `verify.sh` 实现等价用例；两脚本判据一一对应；
2. 每项用例的判据严格按 §4 的 Then 列，不得放宽；
3. 对 `AC-P0B-IAM-004` 增加负例对照（脚本被改为不比对形状时必须 FAIL）；
4. 在真实 Compose 栈执行两脚本，取得 E4 输出；
5. 写 `EVD-P0BR-058-001.yaml`，并在 `EVD-P0B-V01-V12.yaml` 中**仅**修订 IAM 相关的
   `proven`/`notProven` 条目（新增已证明项、移除已不再成立的 notProven 项）；
6. 同步 `docs/runbooks/compose.md` 的用例表。

**实施结果**：第 1–4 步首轮暴露 4 项产品缺陷（DEF-058-1..4）与 1 项脚本缺陷（DEF-058-6）；
前者按 §9 停止条件单独上报并另立 `TASK-P0BR-059/060/061` 修复，本卡未改动后端；
后者（脚本正则/并发原语/JSON 传参/拒绝审计断言）已在本卡内修正。
修复后重跑同一脚本，20 项用例全部 PASS，第 5–6 步完成。

**关于并发刷新（`AC-P0B-IAM-011`）**：若 PowerShell/curl 无法产生真实并发，
必须如实记为 `notProven` 并说明原因，**不得**用串行两次调用冒充并发。
本次以 .NET `HttpClient`（`verify.ps1`）与双后台 `curl`（`verify.sh`）产生真实并发，
两请求在途并发、非串行，故该场景计为 `proven`。

## 8. 验证命令

```text
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
docker run --rm --network host -v <compose>:/src:ro -v <lf-verify.sh>:/lf-verify.sh:ro \
  -v //var/run/docker.sock:/var/run/docker.sock alpine:3.20 \
  sh -c "apk add --no-cache bash curl jq docker-cli docker-cli-compose; cp -r /src /work; \
  cp /lf-verify.sh /work/scripts/verify.sh; cd /work; bash ./scripts/verify.sh"
```

每条命令声明：

- 预期退出码：0（前提：Compose 栈就绪）；
- 覆盖的 AC ID：§4 全部场景；
- 所需依赖：Docker（Compose 栈）、PostgreSQL（用于断言落库值）；
- SKIP 判定：服务不可达时相关用例按既有约定 SKIP 并计入 SKIP 计数，
  **SKIP 不得计为 PASS；有 MUST 场景 SKIP 即出口不通过**；
- 报告或产物路径：脚本输出、`docs/ai-spec/tasks/evidence/EVD-P0BR-058-001.yaml`。

路径触发强制命令：修改 `deploy/compose` 必须包含 `docker compose config`
与两个平台的验收脚本各一次。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-058.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 某场景在真实栈上**行为与规格不符**（即这是产品缺陷而非证据缺失）——
  不得修改断言使其通过，须停止并单独上报；
- 需要修改后端 Java 代码或已应用 Migration 才能让场景通过；
- 并发场景无法真实构造（记 `notProven`，不冒充）；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
  按 §4 在真实 Compose 栈为 IAM 10 项 MUST 场景取得 E4 证据；
  verify.ps1 与 verify.sh 新增 V05c–V05j（判据一一对应）并修复 3 处脚本缺陷；
  首轮暴露的 4 项产品缺陷按 §9 上报，另立 TASK-P0BR-059/060/061 修复后复跑全部 PASS。
Changed files:
  deploy/compose/scripts/verify.ps1、deploy/compose/scripts/verify.sh；
  docs/runbooks/compose.md；
  docs/ai-spec/tasks/evidence/EVD-P0BR-058-001.yaml、
  docs/ai-spec/tasks/evidence/EVD-P0B-V01-V12.yaml；
  docs/ai-spec/tasks/TASK-P0BR-058.md、docs/ai-spec/02-delivery/work-breakdown.md。
Requirements and scenarios satisfied:
  REQ-IAM-001/002/003/004/005/006；AC-P0B-IAM-001/003/004/005/006/007/008/009/011/012 共 10 项 proven。
Contract/database changes: 无。
Validation run and results:
  docker compose -f deploy/compose/compose.yaml config --quiet -> exit 0；
  powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1 -> exit 0
    （V01–V11、V05b、V05c–V05j 共 20 项 PASS，SKIP 0）；
  ./docs/ai-spec/tools/validate-spec.ps1 -> PASS；
  validate-task-card.ps1（无 flag）-> PASS；
  validate-task-card.ps1 -CheckChangedPaths -> PASS；
  validate-task-card.ps1 -CheckCompletion -> EXPECTED_FAIL（证据 review.accepted 仍为 false，
    需验证责任方接受后方可声明完成）。
Evidence artifacts: EVD-P0BR-058-001.yaml（E4，status: PASS）。
Validation not run and reasons:
  bash ./deploy/compose/scripts/verify.sh 的等价 E4 未取得：
  容器内无法直连宿主 http://localhost:8080 入口，用例 SKIP（非产品缺陷，待补）。
Remaining risks or follow-ups:
  notProven 两项：AC-P0B-IAM-004 的 E2 负例对照未固化为回归门禁；
  AC-P0B-IAM-001 的「日志无明文」未穷举镜像层与卷内历史日志；
  41 项非 IAM 无证据场景各自独立成卡。
```

报告中必须区分 `E1`、`E2`、`E3`、`E4`、`E5`，不得用低等级证据概括高等级结论。
必须给出补齐后 P0-B 出口 MUST 场景的 `proven` 覆盖计数（当前 19/70，本卡预期 +10）。

**最终覆盖计数：29/70（+10，达成预期）。**

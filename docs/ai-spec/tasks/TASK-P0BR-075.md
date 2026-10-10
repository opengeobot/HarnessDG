---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-075
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 70f78747fbba0c310457bc69cf5efaec8f30e250
stageGatePassed: true
stageGateEvidence: 既存合并回归修复卡（验证脚本层，不动产品代码）。上游 TASK-P0BR-073 已收口为
  70f7874（EVD-P0BR-073-001，E4 全量 118/0/0 @ 8080）；TASK-P0BR-074 已实施完毕待批准人处置提交
  （EVD-P0BR-074-001，其 requiredValidationCommands 明确「本卡不改 verify.ps1」）。本卡处置
  074 收口时移交的 follow-up：verify.ps1 V05c-V05j 既存合并回归。取证链（2026-10-08 按
  「以当前态为权威」重新实测，全部 exit 0）：
  ① git 史：bce7674「test(compose): IAM 验收用例 V05c-V05j 补齐证据并修复脚本判据」
  （verify.ps1 1062 行，含 IAM 辅助段 L371-560）→ c633ebc 合并丢失底层 helper 定义 →
  63b65d1「找回合并丢失的验收断言」仅恢复调用段（+39 行）→ HEAD 70f7874（864 行）。
  ② Compare-Object 对比 bce7674 与 HEAD 的 ^function 名单：丢失 **9 个函数定义 +
  1 个脚本变量定义**，HEAD 无新增函数。丢失清单（括号为 bce7674 行号）：
  Get-RefreshFromSetCookie(L377)、Assert-RefreshCookieAttributes(L387)、
  Get-ComparableErrorShape(L401)、Get-UserRowFacts(L408)、Reset-UserLockState(L417)、
  New-IamProbeUser(L430)、Assert-BootstrapAdminUniqueness(L450)、
  Assert-FirstLoginPasswordChange(L466)、Assert-LoginAntiEnumeration(L532)，以及
  $script:IamProbePassword(L425)。HEAD 中前 6 项被 13 处调用（L372/373/382/383/409/415/
  442/453/490/522/569/636 等），后 3 项被 V05c 段 L812-814 调用——运行至 V05c 即
  CommandNotFoundException 连锁 FAIL。Get-ComparableErrorShape 经确认被
  Assert-LoginAntiEnumeration 内部调用（bce7674 L552-553），9 个函数缺一不可。
  ③ 权威缺陷记录：docs/architecture/test-report-2026-09-24.md **F-01（高）**明文
  「verify.ps1 引用了未定义的函数……运行至 V05c 起连锁 FAIL（CommandNotFoundException）。
  该脚本自称与 verify.sh 等价，实际不可用……建议：补齐或删除缺失断言段（需 Task Card）」。
  本卡即该 Task Card。
  ④ 恢复可行性实锤：HEAD 的 Invoke-Api（L58-92，.NET HttpClient 实现）返回
  @{ Status; Body; SetCookie }，其中 SetCookie 为 Set-Cookie 头原文 join（L83-85），与
  待恢复的 Get-RefreshFromSetCookie / Assert-RefreshCookieAttributes 消费接口完全匹配；
  Invoke-Psql（L108-113）、Get-ErrorCode（L217）、Test-Step/Skip-Step（L34-50）、
  $AdminUser/$AdminPass（L29-30）等基础设施在位；外层 Assert-RefreshRotation /
  Assert-RefreshReplayRevokesFamily / Assert-DisableRevokesSessions 函数体健在
  （HEAD L364-474），仅缺底层 helper。插入点：HEAD L362（Assert-Permitted 尾）与
  L364（V05d 注释块）之间，即按 bce7674 原位置整段恢复（bce7674 L371-560）。
  ⑤ 与 EVD-P0BR-074-001 limitations[1] 的范围差异：该条登记「四函数全仓无定义」，本卡
  按当前态全量名单实测为 9 函数 + 1 变量。074 EVD 不在本卡可改面，差异由本卡与
  EVD-P0BR-075-001 登记勘误。
  恢复源唯一权威 = git show bce7674:deploy/compose/scripts/verify.ps1。
  本卡为 AI 起草的 DRAFT：approvedBy/approvedAt/status 回写与实施授权由批准人审阅后
  另行处置（DEC-009：AI 不得在同一实施会话自行填写 approvedBy/approvedAt 或将 DRAFT
  置 READY）。
approvedBy: User
approvedAt: 2026-10-08T05:55:06Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/architecture/test-report-2026-09-24.md
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
  - DEC-009
scenarioEvidencePlan:
  - AC-P0B-IAM-001|E4|verify.ps1 实跑 V05c 段 Assert-BootstrapAdminUniqueness 真实 PASS：
    引导管理员恰好 1 行、password_algorithm=BCRYPT、bcrypt 摘要前缀、审计正文与 backend
    容器日志无明文口令（bce7674 原断言零放宽恢复）
  - AC-P0B-IAM-003|E4|V05c 段 Assert-FirstLoginPasswordChange 真实 PASS：临时口令登录→
    403 PASSWORD_CHANGE_REQUIRED 改密门→改密 200→旧口令 401→新口令登录→/me
    forcePasswordChange=false→USER_PASSWORD_CHANGED 审计存在
  - AC-P0B-IAM-004|E4|V05c 段 Assert-LoginAntiEnumeration 真实 PASS：不存在用户名与错误
    口令同 401/AUTH_INVALID_CREDENTIALS、Get-ComparableErrorShape 规范化后响应体形状一致、
    审计无口令明文
  - AC-P0B-IAM-005|E4|V05h 段（调用 Reset-UserLockState/Get-UserRowFacts，bce7674
    L636 一带）真实 PASS：连续失败锁定、锁定期间登录拒绝、锁定状态复位不污染后续用例
  - AC-P0B-IAM-006|E4|V05d 段 Assert-RefreshRotation 真实 PASS：登录/刷新双次
    Assert-RefreshCookieAttributes（HttpOnly/SameSite=Strict/Path=/api/v1/auth/Max-Age）、
    access+refresh 双轮换、旧 jti 标记 ROTATED、族内 ACTIVE 恰好 1
  - AC-P0B-IAM-007|E4|V05g 段 Assert-RefreshReplayRevokesFamily 真实 PASS：重用已轮换
    refresh → 401 AUTH_REFRESH_REPLAYED、整族吊销、族内新 refresh 亦失效、拒绝审计
  - AC-P0B-IAM-008|E4|V05e 段 Assert-DisableRevokesSessions 真实 PASS（函数体健在，恢复
    其调用的 New-IamProbeUser/Get-RefreshFromSetCookie 后即真实执行）：禁用主体后既有
    access 与 refresh 实时失效
  - AC-P0B-IAM-009|E4|V05f 段（登出断言，调用 New-IamProbeUser/Reset-UserLockState）
    真实 PASS：登出后会话材料失效、重放被拒
  - AC-P0B-IAM-011|E4|V05i 段（并发刷新，双独立连接真并发，compose.md L156-177 口径）
    真实 PASS：并发刷新仅一次成功或按设计判定，Token Family 状态一致
  - AC-P0B-IAM-012|E4|V05j 段（用户生命周期，compose-verify-iam12 专用主体 + 最后管理员
    保护复原）真实 PASS：创建→改密→禁用→启用→删除全链断言
crossCuttingPlan:
  - AUTHN|恢复的断言全部走既有登录/刷新/登出链与平台 JWT；不触碰产品认证实现，不在脚本
    内解析 Token 语义（仅按既有方式取 data.accessToken 与 Set-Cookie 原文）
  - AUTHZ|不改权限判定；New-IamProbeUser 沿用 bce7674 原 scopes（project:view）与既有
    system:manage 管理员链路
  - DB_FILTER|N/A - 纯验证脚本恢复，不触达列表查询与权限下推 SQL
  - STATE|不改状态机；断言观测既有 IAM 状态契约（PENDING_ACTIVATION/ACTIVE/LOCKED、
    Token Family ACTIVE/ROTATED/REVOKED）
  - IDEMPOTENCY|New-IamProbeUser/Assert-FirstLoginPasswordChange 沿用 bce7674 幂等设计：
    409 USER_ALREADY_EXISTS 时回读 user_id 并重置前置状态，脚本可重复运行
  - CONSISTENCY|恢复后 verify.ps1 头注「与 verify.sh V01-V11 等价」的自述重新成立
    （V05c-V05j 段与 verify.sh 同名用例判据同源，恢复源 bce7674 即该等价口径的建立提交）；
    verify-case-mapping.md 如涉 ps1 V05 子集表述漂移则同卡勘误
  - ERRORS|无新增 ErrorCode；脚本恢复断言以既有 AUTH_INVALID_CREDENTIALS /
    AUTH_REFRESH_REPLAYED / PASSWORD_CHANGE_REQUIRED / USER_ALREADY_EXISTS 契约为判据
  - AUDIT|N/A - 不触达审计写入路径；断言只读校验 audit_log（USER_PASSWORD_CHANGED /
    AUTH_LOGIN_FAILED 存在性与脱敏）
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|N/A - 无 i18n 键变更
  - CONFIG|deploy/compose/scripts/verify.ps1 为验收脚本非 compose 运行时配置：compose *.yml、
    .env、.env.example、nginx 配置零改动（docker compose config --quiet 回归取证）
  - OBSERVABILITY|N/A - 不新增指标/告警
  - SECRETS|恢复段含 bce7674 既有的本地 Compose 验收栈探针口令字面量（历史上已入库的
    非生产占位口令，仅存在于本地默认栈）：原样恢复不新增扩散；本卡、EVD、日志与
    commit message 一律不复制口令明文；口令断言仅以变量引用形式出现在脚本内
allowedPaths:
  - deploy/compose/scripts/verify.ps1
  - docs/runbooks/verify-case-mapping.md
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-075.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-075-001.yaml
forbiddenPaths:
  - backend
  - contracts
  - frontend
  - worker
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/verify-journey.sh
  - deploy/compose/scripts/verify-journey.ps1
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-073.md
  - docs/ai-spec/tasks/TASK-P0BR-074.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
  - docs/architecture/test-report-2026-09-24.md
preExistingDirtyPaths:
  - .github/workflows/ci.yml|f3af6208f936dbef669b43cd9b6f42dce2e6ae07fdbcd41da150d0a6c64c569a
  - deploy/compose/scripts/verify-journey.ps1|21790ce73a65b414280b6a062041e68d66d0b4067965ed2d977c0424c3eefcc3
  - docs/runbooks/verify-case-mapping.md|81d8042d175a3197428b3a7b786cd88278c87fdac9218c2f7d713bc987d51261
  - frontend/e2e/admin-states.spec.ts|67bf867ce66b2577da55a0b0bfb2bc547f9c12596c3f519ebb6ba24660c16928
  - frontend/e2e/error-paths.spec.ts|f22aa2594500aeb83d363889f582aecfeb92db5e4aaa381b21826e8869a47d40
  - frontend/e2e/lineage.spec.ts|442c945b73a61c571ed1bb9be8e451db9fbea724e807bf7c5993a2e47811fbf4
  - frontend/e2e/review-workflow.spec.ts|b96bea79753b2c977031f56da69ca437941b16dd8895900be85fdc8d2fb91fbe
  - frontend/e2e/support.ts|918d8d9c6bc6d0faa7aa5130990e1718021c38d37d364d040aa784240bc12317
  - docs/ai-spec/tasks/TASK-P0BR-074.md|41121f18aa2629c7e340123ea90aac698eafa293a8c92d8a56533aae50cd6d60
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml|75b7aa2689e2fc256d43fd1ed2a69e6edc0fad639dbb1afb90887aa817a7147a
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-075-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-075.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-075.md -CheckChangedPaths
  - docker compose config --quiet（deploy/compose 目录；本卡不改 compose 配置，回归性取证）
  - pwsh -File deploy/compose/scripts/verify.ps1（真实 Compose 8080 栈；V05c-V05j 全部真实
    PASS、整体 exit 0；出现任何 CommandNotFoundException、SKIP 或 FAIL 即本卡未完成，
    skip=notProven）
  - bash deploy/compose/scripts/verify.sh（WSL 可用时复跑 V05 段确认 ps1/sh 双口径一致；
    不可用则如实登记，不视为失败——verify.sh 为既存权威口径且本卡零改动）
---

# TASK-P0BR-075：恢复 verify.ps1 V05c-V05j 合并丢失的 IAM 验收断言辅助（修复 F-01）

> 状态：`READY`（起草于 2026-10-08，baseCommit `70f7874`；批准人 User 于 2026-10-08 审阅 DRAFT
> 全文后批准实施，`implementationAuthorized: true` 四字段经批准授权回写，
> `approvedAt: 2026-10-08T05:55:06Z`）。批准人未对 §3 裁定项 1（iamLastShapeProbe）另行裁定，
> 按 §5 缺省条款执行：**逐字节恢复 bce7674 原样，僵尸守卫缺口在 EVD limitations 登记**。
> 本卡为验证脚本层单文件修复，**不动产品代码、不动 verify.sh/verify-journey.*、
> 不放宽任何既有断言**。
> 起草轮校验（2026-10-08）：`validate-spec.ps1` PASS；`validate-task-card.ps1` FAILED（4）——
> 全部为 DRAFT 设计内项（status/implementationAuthorized/approvedBy/approvedAt 待批准人回写），
> 与 TASK-P0BR-074 DRAFT 轮口径一致；`-CheckChangedPaths` 无 allowedPaths/preExistingDirtyPaths
> 之外的越界变更。

## 1. 目标与立项依据

test-report-2026-09-24.md **F-01（高）**：verify.ps1 自 2026-09 合并事故（c633ebc）后引用 9 个
未定义函数与 1 个未定义脚本变量，运行至 V05c 起连锁 CommandNotFoundException FAIL——脚本头注
「与 verify.sh V01-V11 等价」的自述失实，Windows 侧 E4 IAM 验收（AC-P0B-IAM-001..012 面）
不可用。报告建议「补齐或删除缺失断言段（需 Task Card）」，本卡即该 Task Card，采**补齐**方案。

不采删除方案的理由：V05c-V05j 是 AC-P0B-IAM-001/003/004/005/006/007/008/009/011/012 的
Windows 侧唯一 E4 断言面（compose.md L156-177 权威映射表）；删除=永久失去等价口径且违背
「不弱化验证」铁律。

## 2. 需求与追踪

- REQ-IAM-001（L8，READY）→ AC-P0B-IAM-001：引导唯一性（V05c）。
- REQ-IAM-002（L42，READY）→ AC-P0B-IAM-002..005：口令策略/首登改密/防枚举/锁定（V05c/V05h）。
- REQ-IAM-003（L90，READY）→ AC-P0B-IAM-008/010：禁用实时失效（V05e）。
- REQ-IAM-004（L134，READY）→ AC-P0B-IAM-006/007/011：刷新轮换/重放吊销/并发刷新（V05d/V05g/V05i）。
- REQ-IAM-005（L170，READY）→ AC-P0B-IAM-008/009/012：登出与会话失效（V05f）。
- REQ-IAM-006（L202，READY）→ AC-P0B-IAM-012：用户生命周期（V05j）。
- DEC-009（AI 不自批不授权）。

## 3. 修复方案

**唯一恢复源：`git show bce7674:deploy/compose/scripts/verify.ps1`（1062 行版）**。将其
L371-560 的「IAM 验收辅助 + V05c」整段（9 个函数定义 + `$script:IamProbePassword` 变量 +
段落头注）按原位置插入 HEAD verify.ps1 L362（`Assert-Permitted` 尾）与 L364（V05d 注释块）
之间。恢复后与 bce7674 该段逐字节一致（比对命令与结果记录进 EVD）。不重写、不「改进」断言
逻辑（§3 裁定项除外），两处例外裁定如下：

1. **`$script:iamLastShapeProbe` 僵尸守卫（恢复时必须处置，批准人裁定项）**：
   Assert-LoginAntiEnumeration 末段（bce7674 L557）以
   `if ($script:iamLastShapeProbe -eq $true -and [int]$failed -lt 1)` 决定是否断言
   AUTH_LOGIN_FAILED 审计存在——但该变量在 bce7674 全文件仅此 1 处引用、**0 处定义**，
   PowerShell 下恒为 false，审计存在性断言永不执行（既存假通过缺口，F-01 同源的次要缺陷）。
   建议处置：恢复时删除 `$script:iamLastShapeProbe -eq $true -and` 前置，使
   `[int]$failed -lt 1 → throw` 无条件执行（**加严**断言，符合「不得为使用例变绿而放宽」；
   V05c 本身执行两次失败登录，AUTH_LOGIN_FAILED 审计必然 ≥1，运行态可证）。若批准人裁定
   保持逐字节原样恢复，则本缺口在 EVD limitations 登记。
2. **`Reset-UserLockState` 的 update 仅在 status='LOCKED' 时生效、返回行数不断言**：属
   bce7674 既有幂等设计（注释言明「避免污染后续用例」），原样恢复，不改。

配套：work-breakdown.md 补 075 登记行；verify-case-mapping.md 若涉 ps1 V05 子集口径漂移则
同卡勘误（预期仅「恢复后与 .sh 等价」的表述回归成立，可能零改动）；实施后产出
EVD-P0BR-075-001（含 V05c-V05j 实跑逐条 PASS 证据、与 074 EVD limitations[1]「四函数」
登记的「九函数 + 一变量」范围差异勘误）。

## 4. 实施步骤（授权后）——2026-10-08 实施完成，证据见 EVD-P0BR-075-001

1. [x] `git show bce7674:deploy/compose/scripts/verify.ps1` 提取 L371-560 段，插入 HEAD 对应位置
   （§3 裁定 1 处置 iamLastShapeProbe）。——Node Buffer 字节级 splice（写前工作区=HEAD 字节一致
   核验；段切片三重断言 190 行/首行横幅/末行 `}`；插入 HEAD L363/L364 之间）；批准人未裁定
   守卫处置，按 §5 缺省逐字节原样恢复（引用 1 处、0 定义随段恢复，缺口登记 EVD limitations）。
2. [x] 静态自检：`Select-String '^function'` 双版本 Compare-Object → 差集为空；`$script:IamProbePassword`
   定义 1 处、调用 ≥1 处。——四项全过：恢复段与 bce7674 逐字节一致；函数集合 31=31 差集空；
   IamProbePassword 定义 1（L418）/引用 10；git diff --numstat=+191/-0、eol=lf、无 BOM（与 HEAD 基线一致）；
   pwsh -File 语法校验 PARSE OK。
3. [x] 栈健康确认（docker compose ps 全 healthy）后 `pwsh -File deploy/compose/scripts/verify.ps1`
   实跑：V01-V11 全 PASS、V05c-V05j 逐条真实 PASS、exit 0；完整输出留档 build-logs/（不入库）。
   ——7 服务 Up/4 healthy 前置确认；20 步全 PASS、步骤级 0 SKIP/0 FAIL、exit 0
   （build-logs/verify-075-run1.log；对照 074 留档恢复前该八步必炸 CommandNotFoundException）。
4. [x] （WSL 可用时）`bash verify.sh` 复跑 V05 段对照，确认双口径一致。——WSL Ubuntu-24.04 可用，
   全量 V01-V28 全 PASS exit 0（build-logs/verify-sh-075-compare.log；verify.sh 零改动，前 3 行
   WSL localhost 转发告警非失败项）；「与 verify.sh 等价」头注自述回归成立。
5. [x] 治理三连 + `docker compose config --quiet`；EVD + work-breakdown 回写；`-CheckChangedPaths`
   零越界。——validate-spec PASS、validate-task-card PASS、-CheckChangedPaths PASS（14 路径
   Get-FileHash 基线核验：10 个 preExisting 全吻合、仅 allowedPaths 内变更；批准版曾误将
   work-breakdown 列入 preExisting，与本卡 allowedPaths 冲突，校验器正确报错后移除、复跑 PASS）；
   compose config exit 0；
   EVD-P0BR-075-001 与 work-breakdown 075 行回写均为 allowedPaths 内；verify-case-mapping 实测零改动。
6. [x] 向批准人汇报，提交与 `review.accepted` 留批准人（DEC-009）。——已汇报；`-CheckCompletion`
   为交接态 EXPECTED_FAIL（commitSha=baseCommit、dirtyWorktree=true、accepted=false）。

## 5. 停止条件

- 实跑出现任何 V05c-V05j FAIL 且根因指向**产品行为**（而非脚本）：立即停止、如实登记 FAIL
  证据并移交，禁止改断言迁就运行态。
- bce7674 段与 HEAD 基础设施（Invoke-Api/Invoke-Psql/Get-ErrorCode 签名）出现本卡取证未覆盖的
  不兼容：停止并上报，不自行重写 helper。
- 批准人未裁定 §3 裁定项 1 而实施已开始：以「逐字节恢复 + EVD limitations 登记」为缺省。

## 6. 越界禁止

不改 backend/contracts/frontend/worker；不改 verify.sh / verify-journey.sh / verify-journey.ps1 /
compose 配置 / V1-V2 迁移；不改 073/074 卡与 EVD（074 limitations 差异以本卡 EVD 勘误登记）；
不放宽任何既有断言；本卡、EVD、日志、commit message 不复制探针口令明文；
`-CheckChangedPaths` 以 baseCommit `70f7874` + preExistingDirtyPaths（074 未提交 10 路径）
为基准，除该 10 路径与本卡 allowedPaths 外不得有任何其他变更。

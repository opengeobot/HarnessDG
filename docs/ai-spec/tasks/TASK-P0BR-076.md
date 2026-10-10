---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-076
status: READY
implementationAuthorized: true
approvedBy: User
approvedAt: 2026-10-09T03:16:35Z
phase: P0-B
baseCommit: 70f78747fbba0c310457bc69cf5efaec8f30e250
stageGatePassed: true
stageGateEvidence: 旅程验收基础设施 + 对账回路修复卡（产品代码级，首个触达 backend 的 07x 卡）。上游
  TASK-P0BR-073 已收口为 70f7874（EVD-P0BR-073-001，E4 全量 118/0/0 @ 8080）；TASK-P0BR-074 /
  TASK-P0BR-075 已实施完毕待批准人处置提交（EVD-P0BR-074-001 / EVD-P0BR-075-001）。本卡处置
  074 收口后按「继续实施」指令实跑的认证业务旅程验收（2026-10-09，Run ID 20261009105006，
  WSL 真实 Compose 全栈）：**18 用例中 13 PASS / 4 FAIL / 1 SKIP，Summary 谎报 FAIL=1**。取证链
  （全部为行级实测 + 库内数据实证）：
  ① 计数失真（双脚本同族缺陷）：verify-journey.sh L121-125 `journey_fail(){ … FAILED=1 }` 为
  赋值而非自增，4 个 FAIL 在 L1062/1086 Summary 中恒显示 FAIL=1；verify-journey.ps1 L17/L84
  `$script:failed = $true` 为布尔旗标，Summary L121/230 打印 FAIL=True/False——均不反映真实
  失败数，且 PASSED/SKIPPED 为真实计数、唯独 FAILED 失真，验收报告不可审计。
  ② 4 个 FAIL 根因 = 作业队列饿死：journey 的 4 个探针资产在 60s 轮询窗（sh L204-224，
  每 3s 轮询 provisioningStatus）内未达 COMPLETED，库内实查 attempts=0（从未被领取）。
  JobWorker.executeOnce L130-135 每 tick 仅 `claimNext` 1 个作业（调度节拍 5s，
  JobConfiguration L105 默认），实测消化速率 ≈12s/作业；而队列被 ③ 的对账风暴填充。
  ③ 对账风暴 + 空转回路：job_task 库内 type='REPOSITORY_PROVISION' AND source=reconcile
  历史累计 1246 条（本轮风暴 1117 条，本轮新增 PENDING 71 条）。AssetRepositoryReconciler
  handle() L73-109 每轮扫全部 COMPLETED 资产；checkAssetRepository L145-150 判定仓库缺失后
  enqueueReprovisionJob L155-168 **裸 jobRepository.insert、无任何在途作业去重**；
  RepositoryProvisionJobHandler L78-80 见 provisioning_status=COMPLETED 即 skip 成功；
  Reconciler 不改 provisioning_status（返回 MANUAL_REVIEW，L149）→ 下一轮再判定缺失、再入队
  → 反馈回路闭环，队列永久被同批资产占据。
  ④ 「缺失」为误报（结构性路径错配）：Reconciler 查询 L76/L80 仅取 `asset_id, name,
  namespace, provisioning_status`，existence 判定按 `asset.namespace` 列拼
  `GET /api/v1/repos/{namespace}/{name}`（L139-146）；而 GiteaAssetRepositoryProvisioner
  createRepository L156-172 在建 org 失败时 fallback 到 `/api/v1/user/repos`（owner=令牌用户，
  运行时实测为 gitea_admin），真实落点仅回写 `asset.repo_full_name`（PhysicalRecycleJobHandler
  L101-102 / PublishJobHandler L174 均读该列，证明权威投影在位而 Reconciler 未读）→
  namespace 列（journey 等）≠ 实际 owner 时**每个 COMPLETED 资产每轮必判「缺失」**。
  （修正早期取证口径：RestGiteaRepositoryExistencePort 全文复核为 404→false、其余 HTTP→
  DependencyException 抛出、传输类 RestClientException→DependencyException；「任意异常判缺失」
  不成立，误报根源即上述路径错配。）
  ⑤ dedupe 机制疑似连带失效（待实机验证项）：JdbcJobRepository 的 dedupeKey 查询使用
  MySQL 风格 `payload->>'$.dedupeKey'`（claimNextByDedupeKey / requeueWithDedupe 两处）；
  PostgreSQL 下 `->>` 按顶层文本键取值，'$.dedupeKey' 作为字面键名恒不存在 → 恒 NULL →
  JobApplicationService 的 dedupe 合并/重入队疑似永不命中。与本卡 ①③ 同属 REQ-JOB-001
  「按业务 dedupe key 幂等」面。实施步骤第一步为最小 SQL 实证，确认则同卡修复，
  证伪则在 EVD limitations 如实登记。
  ⑥ 迁移基线：Flyway 现有最高 V43__widen_source_commit.sql；本卡预期**零迁移**（去重用
  INSERT…WHERE NOT EXISTS 或端口存在性查询，不需要新列/新索引；若批准人采候选 B 需加
  provisioning 审计列则另议 V44，G5 Split 统计顺延）。
  本卡由 AI 起草为 DRAFT；2026-10-09 批准人审阅后明示「批准，按候选 A 实施」，据此回写
  status=READY、implementationAuthorized=true、approvedBy=User、approvedAt=2026-10-09T03:16:35Z
  （批准回写发生于请示批复之后，非 AI 自批，符合 DEC-009）。裁定项按批准口径锁定候选 A
  （止血：M1–M4 + M5 若实机证实；候选 B 不实施）。
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-075-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/architecture/implementation-status-2026-07-10.md
requirements:
  - REQ-JOB-001
  - REQ-AST-008
acceptanceScenarios:
  - AC-P0B-JOB-001
  - AC-P0B-JOB-002
  - AC-P1-AST-012
decisions:
  - DEC-009
scenarioEvidencePlan:
  - AC-P1-AST-012|E4|真实 Compose 栈（Gitea 启用）对账 2 轮以上实测：existence 判定改按
    repo_full_name 权威投影（缺失时回退 namespace 列）后，全部实际存在的 COMPLETED 资产判
    CONSISTENT、不再产生 reconcile 入队；对确认删除仓库的资产，去重后的 reprovision 作业将
    provisioning 推进至 COMPLETED（候选 B：状态回退→handler 真实重建；候选 A：仅在途去重），
    webhook_inbox/audit 留痕可查；verify-journey.sh 复跑 18/18、Summary FAIL=0 真实计数
  - AC-P0B-JOB-001|E4|JobWorker 有界批量领取（每 tick ≤N）后，JdbcJobRepositoryTest/
    JobWorkerTest 并发用例 + 真实栈双 Worker 场景验证同一租约同一时刻仅属一个 Worker、
    无重复并行执行；reconcile 存量清空后 journey 探针作业在 60s 窗内 COMPLETED（4 FAIL 转
    PASS 的运行态主证）
  - AC-P0B-JOB-002|E4|批量领取改造不破坏租约恢复既有断言（JobLeaseReconcilerTest 全绿 +
    mvnw verify 通过）；若 ⑤ 实机证实 dedupeKey 恒 NULL，补 PG 语法修正的单测
    （同 payload 命中合并、异 payload 不合并）作为 Handler 幂等前置的 E2 级佐证，
    E4 面仍由旅程复跑承载
crossCuttingPlan:
  - AUTHN|N/A - 不改认证链；Reconciler/Worker 均为系统内作业，无新入口
  - AUTHZ|不改权限判定；JobRepository 端口扩展为纯数据访问，人工 retry/cancel 面（REQ-JOB-001
    末段）零触碰；禁止任何绕过授权的新写入路径
  - DB_FILTER|existence 判定改读 repo_full_name 权威投影属于「按真实状态过滤」修正，
    不放宽任何查询条件
  - STATE|候选 A 不改状态机；候选 B 新增「对账确认缺失 → provisioning_status COMPLETED→
    PENDING」回退动作：Asset.advanceProvisioning L460-463 现无状态守卫（任意态可设，注释言明
    由 Saga/Job 调用），实施时须以显式领域方法承载（如 resetForReprovision）而非裸 setter，
    并补状态转换单测——该方案属行为变更，须批准人裁定
  - IDEMPOTENCY|本卡核心：reconcile 入队去重（端口 existsActiveByTypeAndAssetId 或
    INSERT…WHERE NOT EXISTS，禁裸 insert）；⑤ dedupeKey SQL 若证实失效则同卡修复；
    不引入内存态去重（跨实例失效）
  - CONSISTENCY|对账-修复回路闭合是 REQ-AST-008「可确定漂移自动修复并审计」的直接兑现；
    recordDiscrepancy L177-183 每轮唯一 delivery_id 写 webhook_inbox 的无限增长噪声在
    误报修复后自然消退，本卡不单独改造（EVD limitations 登记残留增长面，另卡处置）
  - ERRORS|无新增 ErrorCode；JobRepository 端口方法命名与既有契约风格一致
  - AUDIT|漂移修复动作（候选 B 的状态回退）必须走既有审计面记录；对账既有
    platformMetrics.recordReconciliationDiscrepancy 保留
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|N/A - 无字典/i18n 变更
  - CONFIG|Worker 批量上限若做成配置项，仅允许 `aihub.job.worker.batch-size`（默认小值如 5）
    走 @Value 既有模式（JobConfiguration L90/L105 先例）；compose *.yml/.env 零改动
  - OBSERVABILITY|批量领取后 job.processed claimed 计数语义不变；如加指标仅复用既有
    counter 风格，不新建面板
  - SECRETS|本卡、EVD、日志、commit message 不出现任何令牌/Gitea 凭据明文；探针口令仅经
    既有环境变量引用
allowedPaths:
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/AssetRepositoryReconciler.java
  - backend/src/main/java/com/aihub/job/domain/JobRepository.java
  - backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java
  - backend/src/main/java/com/aihub/job/infrastructure/JobWorker.java
  - backend/src/main/java/com/aihub/job/infrastructure/JobConfiguration.java
  - backend/src/main/java/com/aihub/asset/infrastructure/RepositoryProvisionJobHandler.java
  - backend/src/main/java/com/aihub/asset/domain/Asset.java
  - backend/src/test/java/com/aihub/integration/gitea/infrastructure/AssetRepositoryReconcilerTest.java
  - backend/src/test/java/com/aihub/job/infrastructure/JobWorkerTest.java
  - backend/src/test/java/com/aihub/job/infrastructure/JdbcJobRepositoryTest.java
  - backend/src/test/java/com/aihub/asset/infrastructure/RepositoryProvisionJobHandlerTest.java
  - deploy/compose/scripts/verify-journey.sh
  - deploy/compose/scripts/verify-journey.ps1
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-076.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-076-001.yaml
forbiddenPaths:
  - contracts
  - frontend
  - worker
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/verify.ps1
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-073.md
  - docs/ai-spec/tasks/TASK-P0BR-074.md
  - docs/ai-spec/tasks/TASK-P0BR-075.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-075-001.yaml
  - docs/architecture/iteration-plan-2026-09-24.md
preExistingDirtyPaths:
  - .github/workflows/ci.yml|f3af6208f936dbef669b43cd9b6f42dce2e6ae07fdbcd41da150d0a6c64c569a
  - deploy/compose/scripts/verify.ps1|c4e9763a836400f9591da1d975905cbb15cc1e0659d67842baca33bcee7b1012
  - docs/architecture/iteration-plan-2026-09-24.md|4c7c5fa64ec9646b53ae6b2c1f74019ad337f35539b0cc0f48dee592b7822f05
  - docs/runbooks/verify-case-mapping.md|81d8042d175a3197428b3a7b786cd88278c87fdac9218c2f7d713bc987d51261
  - frontend/e2e/admin-states.spec.ts|67bf867ce66b2577da55a0b0bfb2bc547f9c12596c3f519ebb6ba24660c16928
  - frontend/e2e/error-paths.spec.ts|f22aa2594500aeb83d363889f582aecfeb92db5e4aaa381b21826e8869a47d40
  - frontend/e2e/lineage.spec.ts|442c945b73a61c571ed1bb9be8e451db9fbea724e807bf7c5993a2e47811fbf4
  - frontend/e2e/review-workflow.spec.ts|b96bea79753b2c977031f56da69ca437941b16dd8895900be85fdc8d2fb91fbe
  - frontend/e2e/support.ts|918d8d9c6bc6d0faa7aa5130990e1718021c38d37d364d040aa784240bc12317
  - docs/ai-spec/tasks/TASK-P0BR-074.md|41121f18aa2629c7e340123ea90aac698eafa293a8c92d8a56533aae50cd6d60
  - docs/ai-spec/tasks/TASK-P0BR-075.md|bcc9202f7d6c30b0ed3fe9f5a495a01aec73aef52befd009bae92dd14cbdf5f3
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml|75b7aa2689e2fc256d43fd1ed2a69e6edc0fad639dbb1afb90887aa817a7147a
  - docs/ai-spec/tasks/evidence/EVD-P0BR-075-001.yaml|64f6c72cb1d7ab5558fa7be060eddb8143ae2cdbca10287152f9be4c8d916f7c
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-076-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-076.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-076.md -CheckChangedPaths
  - ./mvnw verify（Windows 用 .\mvnw.cmd；unit 全绿，failsafe IT 按既有条件口径，
    跳过项如实登记 limitations——不得以跳过冒充通过）
  - docker compose build backend && docker compose up -d（deploy/compose；改动镜像重建后
    全服务 healthy）
  - bash deploy/compose/scripts/verify-journey.sh（WSL 真实 8080 栈：**18 用例全 PASS、
    FAIL=0、Summary 为真实计数**；前置=清理存量 reconcile PENDING 风暴的一次性运维 SQL
    在 EVD 留痕；任何 FAIL 且根因指向产品行为 → 按 §5 停止）
  - pwsh -File deploy/compose/scripts/verify-journey.ps1（计数修复后 Summary 输出真实
    FAIL 数字而非 True/False；PS5.1 与 pwsh7 双引擎口径与 074 一致）
  - bash deploy/compose/scripts/verify.sh（WSL；本卡触碰 backend 产品代码，V01-V28 全量回归
    必须全 PASS exit 0——脚本本身零改动，仅运行）
  - docker compose config --quiet（本卡不改 compose 配置，回归性取证）
---

# TASK-P0BR-076：修复对账反馈回路、作业领取饿死与旅程脚本计数失真（E4 旅程验收解锁）

> 状态：`READY`（起草于 2026-10-09，baseCommit `70f7874`；批准人于 2026-10-09 批复
> 「批准，按候选 A 实施」，回写 `approvedBy: User` /
> `approvedAt: 2026-10-09T03:16:35Z`，裁定项=候选 A）。
> 起草轮校验（2026-10-09）：`validate-spec.ps1` PASS；`validate-task-card.ps1` FAILED——
> 失败项为 4 个 DRAFT 设计内审批位（status/implementationAuthorized/approvedBy/approvedAt，
> 待批准人回写），与 074/075 DRAFT 轮口径一致（首轮另有 1 项 compose 范围缺功能验证脚本
> 注册，已补 `verify.sh` 回归命令后消除）。
> 本卡是 07x 序列中**首个触达 backend 产品代码**的卡：074 修 ps1 验证口径、075 修 verify.ps1
> 断言面，本卡修「旅程验收 4 FAIL」背后的真实产品缺陷链（对账风暴→队列饿死→探针超时）。

## 1. 目标与立项依据

2026-10-09 按「继续实施」指令首次实跑认证业务旅程验收 `verify-journey.sh`（Run ID
`20261009105006`，WSL 真实 Compose 全栈）：**13 PASS / 4 FAIL / 1 SKIP，Summary 谎报
FAIL=1**。这是 AGENTS.md 明示的 P1–P5 收口主阻塞（authenticated P1–P5 business journeys）。

排查证明 4 FAIL 不是业务功能缺陷，而是一条**基础设施反馈回路**（stageGateEvidence ①–⑥，全部
行级 + 数据级实证）：

- **误报**（④）：Reconciler 按 `asset.namespace` 列查仓库，而 provisioner fallback 把仓库建在
  令牌用户名下、真实落点在 `asset.repo_full_name` → 每个 COMPLETED 资产每轮被误判「缺失」；
- **风暴**（③）：误判 → `enqueueReprovisionJob` 裸 insert 无去重 → 每轮再判再插 →
  `REPOSITORY_PROVISION` 存量 1246 条（本轮 1117 + 71 PENDING）；
- **空转**（③）：handler 见 COMPLETED skip「成功」→ 状态不变 → 回路闭合；
- **饿死**（②）：Worker 每 tick 单领（5s 节拍 ≈12s/作业）→ journey 探针 60s 窗内
  attempts=0 → 4 FAIL；
- **失真**（①）：双 journey 脚本 FAILED 计数为赋值/布尔旗标 → 报告不可审计。

对应规范义务：REQ-AST-008「Reconciler 检查 repo 存在…可确定漂移自动修复并审计」（READY/
MUST/E4）与 REQ-JOB-001「按业务 dedupe key 幂等 / FOR UPDATE SKIP LOCKED 原子领取」（READY/
MUST/E4）；验收面 AC-P1-AST-012、AC-P0B-JOB-001/002（p1/p0b exit catalog，跨阶段挂法有
074 挂 AC-P3-VAL-001 先例）。不修复则旅程验收永久不可过、Summary 永久不可信，且 reconcile
作业以每轮全量速率无限消耗存储与队列。

## 2. 需求与追踪

- REQ-JOB-001（p0b-platform-services.md L178，READY/MUST/E4）→ AC-P0B-JOB-001（批量领取
  不破坏租约唯一性）、AC-P0B-JOB-002（失租恢复不回归）、dedupe key 幂等（①③⑤ 直接义务方）。
- REQ-AST-008（p1-asset-catalog.md L271，READY/MUST/E4）→ AC-P1-AST-012（p1-exit-catalog.md
  L71，MUST/E4）：漂移判定正确（④）、可确定漂移自动修复并审计（③ 回路闭合 / 候选 B）。
- DEC-009（AI 不自批不授权）。

## 3. 修复方案（四项必修 + 一项待证 + 一个批准人裁定项）

**M1 计数诚实化（①，双脚本）**：`verify-journey.sh` L124 `FAILED=1` → `FAILED=$((FAILED+1))`；
`verify-journey.ps1` L84 `$script:failed = $true` → 改整型计数（L17 初始化、L121/230 Summary
与 `if` 判退出同步）。只改计数聚合，不改任何用例判据。

**M2 existence 权威投影（④）**：Reconciler 扫描 SQL（L76/L80）增列 `repo_full_name`；
`checkAssetRepository` 判定顺序 = `repo_full_name` 非空 → 按其 owner/name 查 existence；
为空 → 回退现行为（namespace 列）。同时 handler 调用失败的异常语义保持 fail-safe：
DependencyException（非 404）向上传播由外层记 skip/告警，**不得计入「缺失」**（现状 L146
无 try，异常会炸整个 handle——M2 一并明确 catch-and-continue-without-enqueue 的口径并单测）。

**M3 入队去重（③ 止血）**：`JobRepository` 端口新增存在性查询（如
`boolean hasActiveByTypeAndAssetId(String type, String assetId)`，覆盖
PENDING/RETRY_WAIT/RUNNING），JdbcJobRepository 以单条 `SELECT EXISTS` 实现；
`enqueueReprovisionJob` 先查后插（同事务内竞态由既有 FOR UPDATE 面兜住；实现若选
`INSERT … WHERE NOT EXISTS` 一条 SQL 亦可——两式均不占迁移号，**禁建 V44**，
批准人若另有裁定除外）。

**M4 领取吞吐（②）**：`JobWorker.executeOnce` 改为有界批量循环（每 tick ≤N，N 默认 5，
`@Value("${aihub.job.worker.batch-size:5}")` 进 JobConfiguration，沿用 L90/L105 既有模式），
`claimNext` 端口与 SQL 零改动（保持单条 FOR UPDATE SKIP LOCKED 语义，AC-P0B-JOB-001 并发
单测不回归）。禁改 5s 节拍默认值。

**M5（待证项，⑤）**：实机验证 PG 下 `payload->>'$.dedupeKey'` 恒 NULL 是否使
JobApplicationService dedupe 合并失效；证实 → 同卡改 `payload->>'dedupeKey'` 并补
JdbcJobRepositoryTest 用例；证伪 → EVD limitations 登记，不扩大范围。

**裁定项（已裁定：批准人 2026-10-09 批复选择候选 A；候选 B 不实施）**：
- **候选 A（止血，推荐）**：M1–M4（+M5 若证实）。回路表现为「去重后仅 1 个在途 reprovision
  作业空转成功」。REQ-AST-008「自动修复」名义上未闭合（真缺失资产仍停在 COMPLETED+skip），
  但本轮数据中「缺失」全为误报（④），M2 落地后预期无任何入队；AC-P1-AST-012 的 E4 证据 =
  误报消除 + 去重生效 + 旅程 18/18。「真缺失」修复面留观察期后另卡。
- **候选 B（回路真闭合）**：A 全部 + Reconciler 确认缺失时经显式领域方法
  （`Asset.resetForReprovision`，承载 COMPLETED→PENDING 回退 + 审计事件，替代
  advanceProvisioning L460-463 无守卫裸改）将资产置回 PENDING，handler 走真实重建路径
  （其 L78-80 skip 条件仅当 repo 实际存在时才可能命中，逻辑自洽）。行为变更面大，需
  补旅程级「人为删仓→对账→自动恢复」E4 断言（可在 journey 或单测 + 手工栈实验取证）。

配套：存量 1117/71 条 reconcile PENDING 风暴属**运行态数据**，不写迁移、不改 V1/V2；清理以
一次性运维 SQL（`UPDATE job_task SET status='CANCELLED' …` 按 type+source+status 精确圈定）
执行并在 EVD 留完整语句与前后计数；若批准人选候选 B，被回退资产的最终一致性由 handler 重建
保证。work-breakdown.md 增 076 行；EVD-P0BR-076-001 产出。

## 4. 实施步骤（授权后）

1. [x] M5 实机验证：compose PG 容器内跑
   `SELECT payload->>'$.dedupeKey' FROM job_task WHERE type='REPOSITORY_PROVISION' LIMIT 1`
   ——恒 NULL 即证实；结论与语句留档。（**实机证伪**：`dedupeKey` 三符号全仓零命中，
   `claimNextByDedupeKey`/`requeueWithDedupe`/`payload->>'$.dedupeKey'` 均不存在于代码库，
   ⑤ 系起草误把设计当在码事实；按 §5 停止条件「证伪则不强行改，登记 limitations」执行，
   详见 EVD-P0BR-076-001.environmentDifferences。）
2. [x] M2+M3：Reconciler（扫描 SQL 增 repo_full_name、existence 判定顺序、DependencyException
   不判缺失）+ JobRepository/JdbcJobRepository 端口与实现 + AssetRepositoryReconcilerTest /
   JdbcJobRepositoryTest 用例（去重命中、误报路径、异常路径三组）。（AssetRepositoryReconcilerTest
   11 用例、JdbcJobRepositoryTest 3 用例全绿；DependencyException 不判缺失面已落地，
   port 层既有吞错的残余风险面见 EVD limitations——收紧另卡。）
3. [x] M4：JobWorker 批量循环 + JobConfiguration 配置项 + JobWorkerTest 扩展（批量领取
   租约唯一、空队列快速返回、异常不取消调度三断言面）。（`aihub.job.worker.batch-size:5`
   进 JobConfiguration；JobWorkerTest 19 用例全绿；claimNext 端口与 SQL 零改动。）
4. [x] 裁定项落地（A=无额外动作；B=Asset 领域方法 + Handler 配合 + 审计 + 转换单测 +
   「删仓→对账→恢复」运行态实验）。（批准人裁定候选 A：无额外动作；候选 B 设计内不实施，
   真缺失修复面留观察期后另卡，已登记 EVD coverage.notProven。）
5. [x] M1 双脚本计数修复 + 本地注入 2 个失败路径冒烟验证 Summary 数字正确（不留用例改动）。
   （sh L124 改 `FAILED=$((FAILED+1))`、ps1 L84 改整型自增；冒烟注入 2 失败 → FAILED=2/failed=2
   → 判退出非零，双语言通过；整脚本注入在本环境不可行，采用逐字节复刻真实函数体的最小独立
   片段取证，口径降级已登记 EVD environmentDifferences；临时副本已全部删除、源脚本零残留。）
6. [x] `./mvnw verify` 全绿；compose backend 镜像重建、全栈 healthy。（exit 0；surefire 汇总
   800 用例 FAIL=0 ERROR=0 SKIP=10——10 项为 testcontainers 经 Windows npipe 探测 Docker 失败的
   既有条件跳过，归因登记 EVD skippedTests，未冒充 IT 通过；`docker compose build backend worker`
   + `up -d` 重建后 backend healthy、worker 运行态日志零异常。）
7. [x] 一次性清理存量风暴（§3 配套，EVD 留痕）→ `bash verify-journey.sh` 复跑
   **18/18、FAIL=0 真实计数**；`pwsh verify-journey.ps1` 同口径复跑。
   （**清理 SQL 未执行**：镜像重建前旧 worker 已将 reconcile PENDING 自然消化 230→132→118→0，
   部署新镜像后无清理对象，以计数序列物证替代 SQL 留痕，登记 EVD assumptions；
   verify-journey.sh Run 20261009113539 → **PASS=18 FAIL=0 SKIP=0 exit 0**（V06 日志
   `repo=aihub-svc/mdl-dvc-…` 为 M2 权威投影生效直接物证）；verify-journey.ps1 → 4/4 FAIL=0。）
8. [x] 治理三连 + `docker compose config --quiet` + `-CheckChangedPaths`；EVD +
   work-breakdown 回写；向批准人汇报，提交与 `review.accepted` 留批准人（DEC-009）。
   （validate-spec PASS（148 docs/736 stable ID）；validate-task-card 076 PASS；
   `docker compose config --quiet` exit 0；verify.sh V01-V28 全 PASS（脚本零改动仅运行）；
   EVD-P0BR-076-001 产出 + work-breakdown 076 行回写 + `-CheckChangedPaths` 结果以本次实跑为准。
   提交与 review.accepted 留批准人。）

## 5. 停止条件

- 旅程复跑仍有任何 FAIL 且根因指向**产品业务行为**（而非本卡四项）：停止、如实登记证据并
  移交，禁止改断言或扩窗（60s 轮询窗不放宽——M4 的验收判据就是探针在既有窗内 COMPLETED）。
- M5 实机证伪（dedupe 正常）且修复动作被证明多余：不强行改，登记 limitations。
- 批准人未回写裁定项 A/B 而实施已开始：**缺省候选 A（止血）**，候选 B 永不隐式实施。
- 端口扩展或批量循环需要触碰 forbiddenPaths（compose 配置、verify.sh、V1/V2）：停止上报。

## 6. 越界禁止

不改 contracts/frontend/worker、verify.sh/verify.ps1（verify-journey.* 两脚本仅在 M1
聚合计数面动，用例判据零改动）、V1/V2 迁移、073/074/075 卡与 EVD、iteration-plan；
不新建 V44（去重用查询/SQL 方案，索引优化证明需要时回批准人另行授权）；不改 5s 节拍、
backoff、lease 既有默认值；不把 60s 旅程轮询窗当修复手段；不放宽任何既有断言或数据库
约束；不在代码/日志/EVD/commit message 出现凭据明文；`-CheckChangedPaths` 以 baseCommit
`70f7874` + preExistingDirtyPaths（13 条既有脏路径）为基准，除该基准与本卡 allowedPaths
外不得有任何其他变更。

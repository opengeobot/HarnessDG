---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-074
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 70f78747fbba0c310457bc69cf5efaec8f30e250
stageGatePassed: true
stageGateEvidence: 验证/CI 口径勘误卡（不动产品代码）。上游 TASK-P0BR-073 已实施完成并
  由批准人授权提交为 70f78747fbba0c310457bc69cf5efaec8f30e250（EVD-P0BR-073-001，
  E4 全量 118 passed / 0 failed / 0 skipped @ 8080 权威口径）。本卡处置 073 取证中发现、
  且经 2026-10-08 V08 根因链取证实锤的三处「虚假通过」缺口：
  ① .github/workflows/ci.yml playwright-e2e 作业（L273-314）三层虚假：L306
  E2E_BASE_URL 指向 CI 中从未启动的 5173（作业只启动 compose 8080 栈）；步骤 env 无
  E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 注入，frontend/e2e/support.ts L12-16
  bootstrapCredentials 返回 null → L103/L827 使全部 30+ spec 静默 skip；L278
  PR 上 continue-on-error——三层叠加后该作业在 PR 上永远绿色且不执行任何浏览器断言。
  ② deploy/compose/scripts/verify-journey.ps1 V08 段（L162-191）僵尸断言：L166 非法
  SemVer「1.0.0-ps」致 draft 版本创建必 400 → L169 SKIP；即便版本存在，L171 手工
  transition VALIDATING 会被 ValidationJobHandler fail-closed 校验打回 DRAFT（此回退
  正是 AC-P3-VAL-001「失败回 DRAFT 保留报告」的设计内行为，产品与设计相符），L173
  DRAFT 态 submit publish-requests 必 409；L182 三条 -match 仅匹配字段名而非三元组
  非空值；L194-196 SKIP 不影响退出码。运行态物证：build-logs/074-journey-run1.log
  「[PRD-V08] ... SKIP: draft version unavailable / Summary: PASS=3 SKIP=1 exit 0」。
  ③ 仓内历史失实叙述：frontend/e2e/review-workflow.spec.ts L7/L356「UPLOAD_MATERIALIZE/
  VERSION_VALIDATE 无入队方，正向审批闭环不可达」已被反证——UploadApplicationService
  L305 入队 UPLOAD_MATERIALIZE、VersionApplicationService L138 入队 VERSION_VALIDATE，
  且 verify-journey.sh journey_v08（L537-697，真实上传链+双主体审批）在本仓多次
  PASS；另 L4/L20「决策端点因闭环断链不可达」为连带叙述。admin-states.spec L147、
  lineage.spec L116、error-paths.spec L11-12、support.ts L122-124 四处「nginx
  /assets/ 遮蔽」叙述在 073 修复后已失实（EVD-P0BR-073-001 limitations 登记的
  follow-up）。docs/runbooks/verify-case-mapping.md L131 对 ps1 子集的描述随本卡
  V08 改造同步勘误。批准人 User 于 2026-10-08 经 AskUserQuestion 裁定本卡范围为
  「脚本+CI 口径全修」：①ps1 V08 真实化（对齐 .sh 或如实删除虚假断言并论证，
  本卡 §4 采诚实化改造方案呈审）；②ci.yml playwright-e2e 改 8080 权威口径；
  ③历史遮蔽/失实叙述勘误 + work-breakdown 补 073「收口提交为 70f7874」悬置句。
  均为验证/CI/文档层，不动产品代码。
  实施批准与 approvedBy/approvedAt/status 回写由批准人在本卡 DRAFT 审阅后另行处置
  （DEC-009：AI 不得在同一实施会话自行填写 approvedBy/approvedAt 或将 DRAFT 置 READY）。
approvedBy: User
approvedAt: 2026-10-08T02:43:12Z
stageGateEvidenceRefs:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml
requirements:
  - REQ-UI-001
  - REQ-VAL-001
  - REQ-IMM-001
acceptanceScenarios:
  - AC-P0B-UI-001
  - AC-P0B-UI-006
  - AC-P3-VAL-001
decisions:
  - DEC-009
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-001|E4|ci.yml playwright-e2e 改造后以「CI 等价口径」在本机真实 Compose
    8080 栈复放全量 e2e（注入 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，取自
    deploy/compose/.env.example 与 CI 作业同源；E2E_BASE_URL=8080）：0 failed /
    0 skipped，登录→会话恢复→受守卫页面真实到达路径即 8080 权威入口实证
  - AC-P0B-UI-006|E4|CI 三层虚假消除的静态+运行双证据：ci.yml diff 可证
    E2E_BASE_URL=8080、凭据 env 注入、作业不再对 PR 静默放行；四组 e2e 失实注释
    （review-workflow L4/L7/L20/L356、admin-states L147、lineage L116、
    error-paths L11-12、support L122-124）勘误后 git diff 全为注释行、
    零断言变更，pnpm lint/typecheck/test/build 与全量 e2e 复放不回归
  - AC-P3-VAL-001|E4|verify-journey.ps1 V08 改造为真实断言：以合法 SemVer 创建
    draft 版本后推进 VALIDATING，实测 ValidationJobHandler fail-closed 校验
    使版本回退 DRAFT 且校验报告保留（失败回 DRAFT 半段的真实产品证据）；
    脚本对回退不发生给出 FAIL 而非 SKIP，退出码如实反映（不再出现
    PASS=3 SKIP=1 exit 0 的僵尸通过）
crossCuttingPlan:
  - AUTHN|CI 复放经 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 环境变量注入 bootstrap
    凭据走既有登录链（access Token 仅内存，support.ts 现状）；不改任何产品认证实现
  - AUTHZ|不改权限判定；脚本与 e2e 依既有授权门断言真实 409/403/400 契约
  - DB_FILTER|N/A - 纯验证/CI/文档层变更，不触达列表查询与权限下推 SQL
  - STATE|不改状态机本身；ps1 V08 仅将断言改为如实观测 AC-P3-VAL-001 既有
    DRAFT→VALIDATING→（校验失败）→DRAFT 回退契约
  - IDEMPOTENCY|N/A - 不改幂等面；ps1 既有 V14 幂等重放断言零变更
  - CONSISTENCY|ps1 与 .sh 双口径职责统一：正向发布三元组旅程由 verify-journey.sh
    journey_v08 独占（CI journey-e2e main 强制），ps1 收敛为失败回退子集；
    verify-case-mapping L131、work-breakdown（074 登记 + 073 收口句）与本卡
    脚本行为同卡同步，消除文字与运行态漂移
  - ERRORS|无新增 ErrorCode；脚本从「SKIP 假阳性」改为按既有 400/409/fail-closed
    契约给出真实 PASS/FAIL
  - AUDIT|N/A - 不触达审计写入路径；旅程产生的审计由后端既有实现覆盖
  - NOTIFICATION|N/A - 不触碰通知面
  - TAXONOMY_I18N|N/A - 不新增/修改任何 i18n 键（e2e 注释勘属代码注释非 i18n）
  - CONFIG|.github/workflows/ci.yml（CI 作业定义）与 deploy/compose/scripts/verify-journey.ps1
    （验证脚本）非 deploy/ 运行时配置：compose *.yml、.env.example、nginx 配置零改动
  - OBSERVABILITY|N/A - 不新增指标/告警；playwright-report 工件上传既有机制不变
  - SECRETS|CI 注入的 bootstrap 凭据来源为仓内 .env.example 非机密默认值（与 CI
    栈启动同源，compose-e2e/journey-e2e 既有用同值跑栈）；口令/Token 不写入
    EVD、日志或提交内容；build-logs 工件 *.log 不入库
allowedPaths:
  - deploy/compose/scripts/verify-journey.ps1
  - .github/workflows/ci.yml
  - frontend/e2e/review-workflow.spec.ts
  - frontend/e2e/error-paths.spec.ts
  - frontend/e2e/admin-states.spec.ts
  - frontend/e2e/lineage.spec.ts
  - frontend/e2e/support.ts
  - docs/runbooks/verify-case-mapping.md
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-074.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
forbiddenPaths:
  - backend
  - contracts
  - frontend/src
  - worker
  - deploy/compose/scripts/verify-journey.sh
  - deploy/compose/scripts/verify.sh
  - deploy/compose/scripts/verify.ps1
  - V1__baseline.sql
  - V2__asset_catalog.sql
  - docs/ai-spec/tasks/TASK-P0BR-073.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-073-001.yaml
  - docs/ai-spec/tasks/evidence/EVD-P0BR-065-001.yaml
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-074-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-074.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-074.md -CheckChangedPaths
  - docker compose config --quiet（deploy/compose 目录；本卡不改 compose 配置，回归性取证）
  - pwsh -File deploy/compose/scripts/verify-journey.ps1（真实 Compose 8080 栈；V08 必为真实 PASS 或 FAIL，出现 SKIP 即本卡未完成）
  - pwsh -File deploy/compose/scripts/verify.ps1（V01-V11 功能验收回归；本卡不改该脚本，Compose 栈全绿口径与 073 基线一致）
  - pnpm lint（frontend 目录）
  - pnpm typecheck（frontend 目录）
  - pnpm test（frontend 目录，Vitest 单测）
  - pnpm build（frontend 目录）
  - pnpm test:e2e（frontend 目录；CI 等价复放——注入 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD
    与 E2E_BASE_URL=http://localhost:8080，全量 0 failed / 0 skipped；skip 视为 notProven）
  - bash deploy/compose/scripts/verify-journey.sh --help 不可用则跳过并登记（本机无 WSL 时
    .sh 属环境不可实测项，以 CI journey-e2e main 强制口径为权威，EVD limitations 如实登记）
---

# TASK-P0BR-074：验证/CI 口径勘误——消除 Playwright CI 三层虚假、ps1 V08 僵尸断言与历史失实叙述

> 状态：`READY`（起草于 2026-10-08，baseCommit `70f7874`；批准人 User 于 2026-10-08
> 审阅 DRAFT 全文后批准实施，`implementationAuthorized: true` 四字段经批准授权回写）。
> 范围经批准人 2026-10-08 裁定：「脚本+CI 口径全修」——验证脚本、CI 作业、e2e 注释与
> 文档四层，**不动产品代码**（backend/contracts/frontend/src/worker 全在 forbiddenPaths）。
> 纵向结果 = 「CI 与脚本口径与运行态真相一致：playwright-e2e 真实执行不静默 skip、
> ps1 V08 真实断言 fail-closed 回退契约、仓内失实叙述清零」。

## 1. 目标

PAD 设计-功能相符性复查（goal 第一段）已定论：**产品行为与设计相符**——AC-P3-VAL-001
（`docs/ai-spec/05-acceptance/p3-exit-catalog.md` L23）明文「失败回 DRAFT 保留报告」，
V08 链上 ValidationJobHandler 的 fail-closed 回退正是该设计内行为。真正与设计不符的是
**验证与 CI 层的口径**，三处虚假（逐字取证见 stageGateEvidence）：

1. **ci.yml playwright-e2e 永远假绿**：baseURL 指向未启动的 5173 + 无凭据注入导致
   全部 spec `test.skip` + PR `continue-on-error`。REQ-UI-001（L395-396：浏览器 E2E
   是必需证据、页面存在或 Vite build 不构成完成）与 DEC-013 的 E2E 基线承诺在该作业
   上实际为零执行。
2. **verify-journey.ps1 V08 僵尸断言**：四路 SKIP 全绿收场（运行态物证
   `build-logs/074-journey-run1.log`），既未验证正向三元组（那是 .sh journey_v08
   的真实上传链+双主体审批独占面），也未验证回退契约，是纯粹的假覆盖。
3. **失实叙述**：review-workflow.spec.ts「无入队方/闭环不可达」四处（L4/L7/L20/L356）
   与四处「nginx /assets/ 遮蔽」旧述（073 修复后失效，EVD-P0BR-073-001 limitations
   登记为 follow-up）。失实注释会误导后续 AI/工程师的设计-功能相符性判断。

## 2. 需求与追踪

- REQ-UI-001（p0b-platform-services，READY/E4）：E2E 证据真实性——CI 作业必须实际
  驱动浏览器断言，静默 skip 的绿灯不构成验收。
- REQ-VAL-001 + AC-P3-VAL-001（p3-release-governance L6 / p3-exit-catalog L23）：
  校验失败回 DRAFT 保留报告——ps1 V08 改造即对该契约的 Windows 侧真实断言。
- REQ-IMM-001（p3 L128）：三元组完整标识由 .sh journey_v08 权威覆盖（本卡不动 .sh，
  仅在 CONSISTENCY 面确立 ps1/shell 分工并把该边界写进 verify-case-mapping）。
- AC-P0B-UI-001 / AC-P0B-UI-006（p0b-exit-catalog L135/L140）：8080 权威口径的
  会话恢复与自动化证据面（073 同锚，本卡把「CI 复放」纳入证据口径）。
- DEC-009（AI 不自批）、DEC-013（ACCEPTED：UI/Playwright/Compose 验收基线）。

## 3. 取证事实（全部为本卡 baseCommit 现状）

- ci.yml L273-314 逐字：L277 `needs: [compose-e2e]`、L278 PR coe、L287 只启动
  compose 栈、L306 `E2E_BASE_URL: http://localhost:5173`、L303-307 步骤 env 无
  E2E_ADMIN_* 键。support.ts L12-16/L103/L827 凭据闸门：无凭据 → 全 spec skip。
- verify-journey.ps1 L162-191 逐字 + `build-logs/074-journey-run1.log` 运行态
  （PASS=3 SKIP=1 FAIL=False exit 0）。`.sh` journey_v08（L537-697）为正向三元组
  权威实现：run_upload_chain + 双主体审批 + 三非空断言。
- 入队方反证：UploadApplicationService L305（UPLOAD_MATERIALIZE）、
  VersionApplicationService L138（VERSION_VALIDATE）。
- 遮蔽叙述现存四处（073 limitations 口径 + 本轮复核）：admin-states.spec L147、
  lineage.spec L116、error-paths.spec L11-12、support.ts L122-124。
  asset.spec/profile.spec 复核后为真实历史叙述（「曾遮蔽…已由 073 修复」），不改。
- work-breakdown.md L89（073 行）缺收口句；068/069/072 先例格式：「**收口（日期）**：
  改动已由批准人授权提交为 `<SHA>`，E4 人工验收（`review.accepted`）仍待验证权威」。
- verify-case-mapping.md L131：「PowerShell 轻量子集（V05/V08/V12/V14）」随 V08
  语义变化同步勘误。

## 4. 修复方案（裁定与论证）

**① ps1 V08：诚实化改造（不整体移植 .sh 上传链，亦非删除旅程）**
批准人选项为「改走真实上传链对齐 .sh，或如实删除虚假断言并论证」。本卡采第三条——
删除虚假断言并替换为 Windows 环境可真实执行的**失败回退断言**：
- 合法 SemVer 创建 draft 版本（修复 `1.0.0-ps` 非法版本号）；
- 断言 DRAFT 直接 submit publish-requests 返回 409（后端拒绝语义，review-workflow
  测试已固化的同一契约）；
- transition VALIDATING 后轮询版本状态，断言 fail-closed 校验使其**回退 DRAFT 且
  校验报告存在**（AC-P3-VAL-001 失败半段的真实产品证据）；
- 以上任一步不达预期即 `Journey-Fail`（消除四路 SKIP 僵尸路径；环境不可达 SETUP
  失败仍按既有 exit 1 fail-fast）。
- 论证不移植上传链：presign→容器内 PUT→ETag→complete→双主体审批在 .sh 已有权威
  实现且 CI journey-e2e（main 强制）执行；ps1 重复实现=双维护漂移源，且其定位
  （头注 L2-3「轻量子集；全量旅程请用 Bash 脚本」）不要求正向闭环。

**② ci.yml playwright-e2e 真实化**：`E2E_BASE_URL` 改 `http://localhost:8080`；
新增步骤从 `deploy/compose/.env`（由 .env.example 复制）解析
`AIHUB_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` 注入 `E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD`
（echo >> $GITHUB_ENV，不落日志）；移除该作业的 `continue-on-error`（与 journey-e2e
的过渡期豁免不同——073 已实证 8080 口径本机全绿，具备强制条件；若首轮 CI 出现
flaky 再回退 coe 并登记）。

**③ 注释/文档勘误**：review-workflow L4/L7/L20/L356 改写为真实口径（作业入队方
实存、正向闭环由 .sh V08 覆盖、本 spec 断言面为 UI 可达面与 DRAFT 提交 409 契约
——测试断言零变更）；四处遮蔽旧述同步；verify-case-mapping L131；work-breakdown
补 073 收口句。

## 5. 垂直切片

| # | 切片 | 证据 |
| --- | --- | --- |
| ① | ps1 V08 改造（合法 SemVer + 409 断言 + 回退 DRAFT/报告断言 + 去 SKIP） | 实跑 exit 0 且 V08 真实 PASS；物证 build-logs/074-journey-run2.log |
| ② | ci.yml baseURL 8080 + 凭据注入 + 去 coe | diff + CI 等价本机复放全量 e2e 0 skipped |
| ③ | review-workflow 四处失实注释勘误 | diff 全为注释行；spec 重跑通过 |
| ④ | 四处遮蔽旧述 + verify-case-mapping 勘误 | diff 与 §3 清单逐项对应 |
| ⑤ | work-breakdown：074 登记行 + 073 收口句 | 校验器登记检查 PASS |
| ⑥ | EVD-P0BR-074-001 + 治理四连 | validate-spec / task-card / -CheckChangedPaths |

## 6. 门禁清单（实施前逐条过；2026-10-08 收口勾验）

- [x] DEC-009：本卡 DRAFT→READY 与四字段回写仅在批准人明确批准后执行；（批准人 User 2026-10-08 经 AskUserQuestion 裁定「脚本+CI 口径全修」后回写，`approvedAt: 2026-10-08T02:43:12Z`）
- [x] 全量 e2e 复放必须 0 skipped（skip=notProven，与 073 口径一致）；（CI 等价口径本机复放 118 tests / 118 passed / 0 failed / **0 skipped**，exit 0，物证 `build-logs/074-e2e-replay.log`）
- [x] ps1 改造后实跑出现任何 V08 SKIP 即本卡未完成，禁止以 SKIP 交卷；（PS5.1 与 pwsh7 双引擎实跑均 `[PRD-V08] ... PASS / Summary: PASS=4 SKIP=0 exit 0`，物证 `074-journey-run2.log` / `074-journey-pwsh.log`；改造前假绿 `074-journey-run1.log`（PASS=3 SKIP=1）留档对照）
- [x] `-CheckChangedPaths` 零越界（allowedPaths 11 项）；（PASS exit 0——期间三条 untracked 运行产物经取证删除；EVD/work-breakdown/本节回写均在 allowedPaths 内，写后复跑确认）
- [x] 不修改 .sh、compose 配置、产品代码与任何既有 EVD；（`git status` 全 11 个变更路径逐一命中 allowedPaths；`docker compose config --quiet` exit 0 为 compose 未改的回归性取证；`.sh` 经 WSL 实跑 PASS=18 反证零改动无破坏）
- [ ] 提交需批准人另行授权（DEC-009），EVD `review.accepted` 留给验证权威。（**待处置**：本卡改动未提交、未自批；`EVD-P0BR-074-001` `review.accepted=false`，`-CheckCompletion` 按交接态登记 EXPECTED_FAIL）

### 6.1 门禁执行结果（2026-10-08 收口轮）

| 门禁命令 | 结果 |
| --- | --- |
| `validate-spec.ps1` | PASS（起草/批准/收口三轮） |
| `validate-task-card.ps1 -TaskPath …074.md` | PASS |
| `…-CheckChangedPaths` | PASS exit 0（三条 untracked 产物删除后） |
| `…-CheckCompletion` | EXPECTED_FAIL（交接态：dirtyWorktree=true、commitSha=baseCommit、未自批） |
| `docker compose config --quiet` | exit 0 |
| `pwsh -File …verify-journey.ps1` | PASS=4 SKIP=0 exit 0（V08 真实 PASS，门禁口径满足） |
| `pwsh -File …verify.ps1` | V01-V11+V05b 12 步全 PASS（§6「V01-V11 功能验收回归」口径满足）；整体 exit 1 系 **既存** V05c-V05j 合并回归——该脚本在 forbiddenPaths、§7 明文不改，取证链与处置见 `EVD-P0BR-074-001` limitations[1] |
| `pnpm lint` / `typecheck` / `build` | 均 exit 0（build ✓ 31.54s） |
| `pnpm test`（Vitest） | 并行 16 files failed（既存资源竞争超时）→ `--no-file-parallelism` 串行 **49/49 files passed exit 0**；本卡 diff 不含任何测试文件，串行口径为验收证据 |
| `pnpm test:e2e`（CI 等价复放） | 118 passed / 0 failed / **0 skipped** exit 0 |
| `bash …verify-journey.sh`（WSL） | 全量 **PASS=18 SKIP=0 FAIL=0 exit 0**（含 V08 正向三元组）——优于卡设「不可用则登记」路径 |

## 7. 越界禁止

不改 backend/contracts/frontend/src/worker；不改 V1/V2 迁移与任何 Flyway 文件；
不改 verify-journey.sh / verify.sh / verify.ps1（正向三元组权威实现保持唯一）；
不放宽任何既有断言或校验器规则来「让任务通过」；不在仓内任何文件写入口令明文
（CI 注入值运行时取自 .env.example，不复制到 workflow 字面量）。

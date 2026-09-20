---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-062
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 941ab67e1e8648df67b0d3bd7280a51a540aa741
stageGatePassed: true
stageGateEvidence: 用户批准（AskUserQuestion 选择「另立任务修复（推荐）」与「开启真实 Gitea（推荐）」「一并修复（推荐）」）。依据 READY 需求 REQ-JOB-001（PostgreSQL 可靠任务）与 ACCEPTED 决策 DEC-003（P0-B 必须逐项重验）、DEC-013（Compose 为功能验收环境），修复 Gitea 仓库开通的 URL 编码与建仓非幂等缺陷，使可部署 Profile 真正接通 Gitea 并恢复 AC-P0B-JOB-003「依赖恢复后成功」的可达性；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-003
  - DEC-013
requirements:
  - REQ-JOB-001
  - REQ-IAM-001
acceptanceScenarios:
  - AC-P0B-JOB-003
  - AC-P0B-ENG-006
decisions:
  - DEC-003
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-JOB-003|E4|真实 Compose 栈（Gitea 已接通）：资产建仓作业在 Gitea 停机期间落入 RETRY_WAIT，Gitea 恢复后下一次尝试 SUCCEEDED；asset.provisioning_status=COMPLETED、repo_full_name 非空且经 Gitea API 可读到 asset.yaml 与 README 内容，job_attempt 逐次可查且 trace_id 与任务一致
  - AC-P0B-ENG-006|E4|真实 Compose 栈：交付基线 compose.yaml 显式设置 AIHUB_GITEA_ENABLED=true，扫描 compose/api/worker 三个可部署 Profile 的生效环境不存在未声明的 Noop 开通器；以真实 Gitea 容器内的 GET /api/v1/repos/{owner}/{repo}/contents/asset.yaml 返回 200 证明写路径确实生效（而非静默走 Noop）
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；Gitea 调用沿用既有服务账号 Token，Token 仍只来自 Secret/环境变量
  - AUTHZ|N/A - 不新增或放宽业务权限；建仓作业仍由既有 asset:create 触发与 job:manage 管理
  - DB_FILTER|N/A - 不涉及查询条件或授权下推
  - STATE|不改变 JobStatus 与 ProvisioningStatus 取值集合及数据库 CHECK 约束；仅修正外部调用的成功判定
  - IDEMPOTENCY|正面主题之一：createRepository 必须在「仓库已存在」时复用既有仓库而非返回失败，使 Handler 在部分成功后重试可收敛；不得以吞掉全部异常实现幂等
  - CONSISTENCY|写路径失败必须仍以可重试依赖错误上抛，不得以「仓库已存在」掩盖真实的 Gitea 不可用
  - ERRORS|沿用既有 ASSET_REPOSITORY_PROVISION_FAILED（retryable=true）与 GITEA_DEPENDENCY_UNAVAILABLE；不新增错误码，不降低 retryable 语义
  - AUDIT|N/A - 建仓审计落点不变；本卡不新增或删除审计事件
  - NOTIFICATION|N/A - 不改变 JOB_DEAD 通知触发条件
  - TAXONOMY_I18N|N/A - 不涉及受控值或面向用户的文案
  - CONFIG|Gitea 开关必须由部署配置显式声明；不得以「未配置即静默降级为 Noop」作为可部署 Profile 的默认行为
  - OBSERVABILITY|复用既有日志与指标；建仓成功/失败的日志必须能区分「URL 不可达」「已存在并复用」与「写入失败」三种情形
  - SECRETS|Gitea Token 与预签名 URL 不得进入日志、证据、测试夹具或 Git；证据不得包含 Token 明文
allowedPaths:
  - backend/src/main/java/com/aihub/integration/gitea
  - backend/src/test/java/com/aihub/integration/gitea
  - deploy/compose/compose.yaml
  - deploy/compose/.env.example
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-062.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-062-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V9__job.sql
  - backend/src/main/resources/application.yml
  - contracts
  - frontend
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-062-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-062.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-062.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-20T07:35:00Z
---

# TASK-P0BR-062：修复 Gitea 仓库开通的 URL 编码与建仓非幂等，并接通可部署 Profile

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-20

## 1. 目标

资产建仓作业在启用真实 Gitea 后可完整成功（初始 `asset.yaml` 与 README 真正写入仓库），
且 Gitea 短暂不可用后无需人工干预即可自行恢复成功；同时交付的 Compose 基线必须显式声明
Gitea 开关，不得以未声明的 Noop 开通器冒充真实建仓。

## 2. 关联规格

```yaml
requirements: [REQ-JOB-001, REQ-IAM-001]
acceptanceScenarios: [AC-P0B-JOB-003, AC-P0B-ENG-006]
decisions: [DEC-003, DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: "无（本卡无迁移）"
```

### 缺陷与缺口事实（已复核，2026-09-20）

以下均由 `TASK-P0BR-023` 的真实 Compose 栈 E4 取证暴露，并在本卡起草时直接读取源码与
对运行中的 Gitea 发起真实请求复核：

1. **仓库全名被当作单个路径变量传入，斜杠被百分号编码**。
   `GiteaAssetRepositoryProvisioner.writeFile` 以
   `.uri("/api/v1/repos/{full}/contents/{path}", fullName, path)` 调用，而
   `fullName` 取自创建仓库响应的 `full_name`（形如 `owner/repo`）。
   Spring 的 `DefaultUriBuilderFactory` 默认在 `TEMPLATE_AND_VALUES` 模式下
   对模板变量值整体做百分号编码，`/` 变成 `%2F`，请求最终落到
   `/api/v1/repos/owner%2Frepo/contents/...`，Gitea 返回 404。
   实测（同一容器、同一 Token、同一仓库，仅编码方式不同）：

   ```text
   GET  /api/v1/repos/aihub-svc/crashed-0920143437/contents/README.md?ref=main   -> 200
   GET  /api/v1/repos/aihub-svc%2Fcrashed-0920143437/contents/README.md?ref=main -> 404
   ```

2. **同一缺陷共 4 处调用点**：
   - `GiteaAssetRepositoryProvisioner.writeFile`（`PUT contents`）
   - `GiteaAssetRepositoryProvisioner.fetchFileSha`（`GET contents?ref=`）
   - `GiteaCardProjection.fetchFileContent`（`GET contents?ref=`）
   - `GiteaCardProjection.fetchHeadCommit`（`GET branches`）
   后三处的异常被捕获后返回 null，因此表现为「读不到 Card / 读不到 sha」而不是显式报错，
   使缺陷更难发现。

3. **createRepository 非幂等**：`createRepository` 先试
   `POST /api/v1/orgs/{namespace}/repos`，失败则回退 `POST /api/v1/user/repos`。
   实测：命名空间不是 Gitea 组织时前者返回 **404**（因此总是回退），
   而后者在仓库已存在时返回 **409 Conflict**。
   于是「仓库已建但写文件失败」的任务在重试时永远拿到 409，
   被归类为可重试依赖错误后反复重试直到预算耗尽进入 DEAD，
   不可能是 `AC-P0B-JOB-003` 要求的「依赖恢复后成功」。

   ```text
   POST /api/v1/orgs/aihub-svc/repos  -> 404（命名空间非组织）
   POST /api/v1/user/repos（重名）     -> 409 Conflict
   ```

4. **交付基线未接通真实 Gitea**：`deploy/compose/compose.yaml` 为 backend/worker
   设置了 `AIHUB_GITEA_BASE_URL` 与 `AIHUB_GITEA_TOKEN`，但**从未设置
   `AIHUB_GITEA_ENABLED`**；`application.yml` 中该开关默认 `false`，
   于是 `NoopAssetRepositoryProvisioner`（`havingValue="false", matchIfMissing=true`）
   成为生效实现，建仓从不调用 Gitea，只按 `namespace/name` 拼一个确定性引用。
   这解释了历史取证的异常现象：暂停 Gitea 后建仓作业仍在毫秒级 SUCCEEDED。
   该状态与 `NFR-MNT-005`「生产 Profile 不含 Noop」及
   `AC-P0B-ENG-006`「无未声明 Noop」直接冲突。

5. **后果**：在真实 Gitea 下，`RepositoryProvisionJobHandler` 执行到
   `repositoryProvisioner.provision(...)` 时先成功创建空仓库，随后在写
   `asset.yaml` 失败，`catch` 将 `provisioning_status` 置为 `FAILED` 并上抛；
   重试又因第 3 条拿到 409。因此仓库里始终只有 Gitea 自动初始化的默认 README，
   `asset.yaml` 缺失，作业最终 DEAD，`AC-P0B-JOB-003`
   「恢复后成功」在当前基线**不可达**（该 MUST 级场景为 E4）。

6. **既有测试未覆盖**：`backend/src/test/java` 下没有任何针对
   `GiteaAssetRepositoryProvisioner` 或 `GiteaCardProjection` 的用例
   （`grep` 确认只有 `WebhookInboxHandlerTest` 与 `AssetRepositoryReconcilerTest`），
   也没有任何断言约束请求 URI 的形状。

7. **Gitea 调用无超时上界（E4 取证新增事实）**：`GiteaAssetRepositoryProvisioner`
   与 `GiteaCardProjection` 各自以 `RestClient.builder().baseUrl(...).build()` 构造客户端，
   未设置任何连接/读取超时。建仓作业在可靠任务 Worker 的租约内执行
   （`aihub.job.worker.lease-seconds` 默认 60 秒），而租约恢复的判定前提正是
   「租约时长显著大于单次执行时间」。无界调用使该前提不成立：
   实测 `docker pause` Gitea 后单次尝试阻塞 **122717ms**，租约在尝试未结束时到期，
   任务被第二个 Worker 重新领取并重复执行，进而抛出
   `ASSET_CONCURRENT_MODIFICATION`（`asset was modified concurrently`）伪冲突。
   该缺陷不在本卡起草时的四条事实之内，但与本卡目标直接相关——它使
   `AC-P0B-JOB-003` 的「依赖恢复后成功」即使修好写路径也不稳定可达，
   因此一并修复：抽出 `GiteaRestClientFactory` 统一施加有界超时
   （默认连接 3s / 读取 10s，满足 `4 × 10s < 60s`）。

8. **Gitea contents 接口的写入动词语义（E4 取证新增事实）**：实测
   `PUT /api/v1/repos/{owner}/{repo}/contents/{path}` 无论文件是否存在都要求 `sha`，
   缺失时返回 **422** `{"message":"[SHA]: Required"}`；而
   `POST` 同一路径不带 `sha` 新建返回 **201**、对已存在路径返回 **409 Conflict**。
   原 `writeFile` 无条件走 PUT，因此新建 `asset.yaml` 必然 422，
   这正是首轮 E4 阻断在 A4 的直接原因。修复为真 upsert：
   先读回 `sha` 决定动词，并在 POST 撞 409 时按并发新建重读 `sha` 转 PUT。

### 与其它卡的关系

- 本卡是 `TASK-P0BR-023` 的**前置阻塞修复**：023 在真实栈取证时命中第 1/3/4 条，
  其 E4 的 `AC-P0B-JOB-003`「依赖恢复后成功」子判据因此无法成立。
  本卡完成并提交后，023 在真实栈复验并补齐该子判据。
- 023 自身的实现缺陷（租约恢复时 `started_at` 为 null 导致 NPE）已在
  `941ab67` 修复并附真实 PostgreSQL 回归用例，不在本卡范围。

## 3. 范围

### 允许修改

- 模块：`com.aihub.integration.gitea`（infrastructure 适配器）与对应测试
- 文件或目录：见 `allowedPaths`
- 契约：无（不改变 REST/MCP 响应结构或错误码集合）
- Migration：无
- 页面：无
- Fixture/Verify：修改 `deploy/compose/compose.yaml` 与 `.env.example`
  以显式接通 Gitea；不修改 `verify.ps1`/`verify.sh`

### 明确不在范围

- 不实现 Gitea 组织自动创建或权限投影（属 `REQ-AST-008`，P1 且状态 `PROPOSED`）；
- 不实现 `DELETE /api/v1/repos/{owner}/{repo}` 的仓库删除
  （`PhysicalRecycleJobHandler.deleteGiteaRepository` 目前是 TODO，另立任务）；
- 不把 `asset.yaml` 的 Schema 校验或 Card 渲染纳入本卡；
- 不引入 Gitea SDK 依赖，继续使用既有 `RestClient`；
- 不改动 `JdbcJobRepository`/`JobWorker` 的重试逻辑（属 023）；
- 不修改 `application.yml` 的 `aihub.gitea.enabled` 默认值
  （默认仍为 `false`，以便无 Gitea 的本地开发可用；
  可部署 Profile 由 Compose 显式置 `true`）。

### 禁止变化

- 不改变的 API/状态/权限：`ASSET_REPOSITORY_PROVISION_FAILED` 的 `retryable=true`
  与 `ProvisioningStatus` 状态机、`asset:create` 权限判定；
- 不允许的依赖升级或重构：不新增依赖，不引入第二套 HTTP 客户端；
- 不允许的兼容破坏：不得删除 `NoopAssetRepositoryProvisioner`
  （无 Gitea 的本地/单测仍需要它），只要求可部署 Profile 显式声明开关；
- **不得放宽校验**：不得把「仓库已存在」实现为「忽略所有异常」，
  也不得把真实的连接失败一并吞掉。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-JOB-003` | 运维 | Gitea 可用，资产已登记 | 建仓作业执行 | 仓库内 `asset.yaml` 与 README 内容与请求一致，作业 SUCCEEDED | `E4` |
| `AC-P0B-JOB-003` | 运维 | Gitea 停机，建仓作业执行失败 | 等待 | 进入 RETRY_WAIT，`next_run_at` 落在指数退避+抖动区间 | `E4` |
| `AC-P0B-JOB-003` | 运维 | 同一作业已多次失败 | Gitea 恢复 | 下一次尝试 SUCCEEDED，期间无人工干预；`provisioning_status=COMPLETED` | `E4` |
| `AC-P0B-JOB-003` | 审查者 | 仓库已创建但写文件曾失败 | 作业重试 | 复用既有仓库（409 不再被当作终态失败），最终成功且不产生第二个仓库 | `E3` |
| `AC-P0B-JOB-003` | 审查者 | 上述作业的历史 | 查询 attempt 与任务行 | 按 `trace_id` 可关联，`errorCode` 均为可重试码 | `E3` |
| `AC-P0B-ENG-006` | 验证方 | 交付基线 `compose.yaml` | 扫描可部署 Profile 生效环境 | `AIHUB_GITEA_ENABLED=true` 显式声明；不存在未声明的 Noop 降级 | `E4` |
| `AC-P0B-ENG-006` | 验证方 | 同一栈上新建资产 | 直接查询真实 Gitea API | `GET contents/asset.yaml` 返回 200，证明写路径生效而非 Noop | `E4` |
| 边界 | 审查者 | `full_name` 不含斜杠或多于一个斜杠 | 构造写文件请求 | 明确失败，不发出编码错误的请求去猜测 | `E2` |
| 失败 | 审查者 | Gitea 连接被拒（真实不可用） | 建仓作业执行 | 仍以可重试依赖错误上抛，未被「已存在即复用」吞掉 | `E2/E3` |

必须覆盖正常、边界、失败、越权。本卡无越权路径（Gitea 调用的授权由既有服务账号 Token 承载），
但须覆盖恢复与幂等重试。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；Gitea Token 仍只来自 Secret/环境变量 |
| Permission/Scope/ACL | N/A：不新增或放宽权限 |
| 数据库权限下推 | N/A：不涉及查询条件或授权下推 |
| 状态前置条件 | 不改变 `ProvisioningStatus` 状态机；仅修正外部调用的成功判定 |
| 幂等与并发 | **核心**：仓库已存在时复用；部分成功后重试必须收敛，不得产生第二个仓库 |
| 事务与外部一致性 | 写失败必须仍上抛可重试错误；不得以幂等为由吞掉真实依赖故障 |
| 错误码 | 沿用 `ASSET_REPOSITORY_PROVISION_FAILED`（retryable）与既有错误码，不新增 |
| 审计事件 | N/A：建仓审计落点不变 |
| 通知 | N/A：不改变 `JOB_DEAD` 触发条件 |
| 字典/标签/i18n | N/A：不涉及受控值或面向用户文案 |
| 配置 | Gitea 开关必须由部署配置显式声明；可部署 Profile 不得静默降级为 Noop |
| 指标/Trace/告警 | 复用既有日志；建仓日志须能区分 URL 不可达 / 已存在并复用 / 写入失败 |
| Secret/日志脱敏 | Token 与预签名 URL 不得进入日志、证据、测试夹具或 Git |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无。本卡不改变任何对外请求/响应结构与错误码集合。
2. 示例请求、响应和错误：无新增。
3. 新增 Flyway 文件：无。
4. 存量清点与回填：无。已存在的空仓库（仅含 Gitea 默认 README）在修复后由重试补齐
   `asset.yaml`，无需数据迁移。
5. 索引、唯一约束、外键及理由：无变更。
6. 兼容窗口与失败恢复：修复为纯行为修正；`NoopAssetRepositoryProvisioner` 保留，
   无 Gitea 环境仍可用。
7. 生成类型更新：无。

## 7. 实施步骤

- [x] 契约及契约测试（本卡无契约变更）
- [x] Migration 及空库/升级迁移测试（本卡无迁移）
- [x] Application/Domain 用例
- [x] REST/MCP/Worker Adapter
- [x] 前端页面与状态（本卡不涉及）
- [x] 正常/边界/失败/越权/恢复测试
- [x] Compose Fixture/Verify
- [x] Runbook/追踪矩阵

勾选必须附 Evidence Manifest 条目；提交代码本身不能作为勾选依据。
以下每个勾选项均对应 `EVD-P0BR-062-001.yaml` 中的 `coverage.proven` 条目与
`.trae/evidence/gitea-e4-062.txt`（E4，24 项断言全 PASS）中的具体断言。

实施路径：

1. **修正仓库全名的路径拼接**：把 4 处 `{full}` 拆为 `{owner}`/`{repo}` 两个独立路径段
   （`/api/v1/repos/{owner}/{repo}/contents/{path}`），使 `/` 不被编码；
   对 `full_name` 做显式解析（必须恰好一个斜杠），不合法时以明确错误拒绝而不是发出错误请求。
2. **让 createRepository 幂等**：仓库已存在（409）时回查并复用既有仓库引用而不是失败；
   组织路径不可用（404）仍按既有回退逻辑处理；连接被拒、5xx、超时等真实依赖故障
   必须继续以可重试依赖错误上抛，不得被幂等分支吞掉。
3. **接通可部署 Profile**：在 `compose.yaml` 为 backend 与 worker 显式设置
   `AIHUB_GITEA_ENABLED: "true"`；同步更新 `.env.example` 与 bootstrap 提示，
   使 `GITEA_SERVICE_TOKEN` 成为明确的前置条件（未配置真实 Token 时建仓会失败，
   这是预期行为，不得回退为 Noop）。
4. **收紧测试**：新增适配器级测试约束请求 URI 形状（`owner/repo` 必须落在两个路径段，
   断言不出现 `%2F`），并覆盖「已存在即复用」与「连接失败仍可重试」两条分支。
   不具备真实 Gitea 的测试用轻量 HTTP 桩（不新增依赖），并在 Evidence 中说明其为 E2。
5. **E4 取证**：在真实 Compose 栈（Gitea 已接通）上——
   新建资产 → 断言 `asset.yaml` 与 README 经 Gitea API 可读；
   暂停 Gitea → 断言作业落入 RETRY_WAIT 且退避区间正确；
   恢复 Gitea → 断言无人工干预自行 SUCCEEDED 且 `repo_full_name` 唯一。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：0；若本机 Docker 不可用，依赖真实依赖的 IT 记为 SKIP 并在
  Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-JOB-003`、`AC-P0B-ENG-006`；
- 所需依赖：JDK 21、Maven Wrapper、完整 Compose 栈（postgres/gitea/minio/backend/worker/nginx）
  以及 `deploy/compose/.env` 中真实可用的 `GITEA_SERVICE_TOKEN`；
- SKIP 判定：Docker 守护进程或 Gitea Token 不可用时 E4 不可 SKIP 为 PASS，
  只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-062-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；
修改 `deploy/compose` 必须包含 `docker compose config` 与验收脚本。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-062.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 `REQ-AST-008` 要求「Noop 不进入可部署 Profile」与本卡
  「保留 Noop 供本地开发」在同一 Profile 上无法同时满足）；
- 需要改变 ADR 固定的技术路线或事实源（例如改用 Gitea SDK 或自建 Git 服务）；
- 需要修改 V1/V2/V9 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试（例如为让建仓通过而放宽
  `ASSET_REPOSITORY_PROVISION_FAILED` 的 retryable 语义或删除 Noop 空实现）；
- 无法产生任务要求的最低证据等级（E4 必须在真实 Compose 栈取得，
  不得以 Mock 单测冒充「依赖恢复后成功」）；
- 工作树存在与任务重叠且来源不明的变更。

## 10. 完成报告

```text
Completed:
  修复 Gitea 仓库开通的 3 个既有缺陷（URL 编码、建仓非幂等、contents 写入动词）
  与 E4 取证暴露的 1 个关联缺陷（Gitea 调用无超时上界），并在交付基线
  compose.yaml 中显式接通真实 Gitea；新增适配器级回归测试 18 项并自证鉴别力。

Changed files:
  新增：
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/GiteaRepoPath.java
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/GiteaRestClientFactory.java
  - backend/src/test/java/com/aihub/integration/gitea/infrastructure/GiteaAssetRepositoryAdapterTest.java
  - docs/ai-spec/tasks/TASK-P0BR-062.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-062-001.yaml
  修改：
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/GiteaAssetRepositoryProvisioner.java
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/GiteaCardProjection.java
  - backend/src/main/java/com/aihub/integration/gitea/infrastructure/GiteaProperties.java
  - deploy/compose/compose.yaml
  - deploy/compose/.env.example
  - docs/ai-spec/02-delivery/work-breakdown.md

Requirements and scenarios satisfied:
  - AC-P0B-JOB-003（E4）：建仓完整成功（asset.yaml + README 真实落库且内容正确）；
    依赖停机时落入 RETRY_WAIT 且退避区间正确；恢复后无人工干预自行 SUCCEEDED；
    仓库已存在时重试复用同一仓库并收敛。
  - AC-P0B-ENG-006（E4）：交付基线显式 AIHUB_GITEA_ENABLED=true（backend 与 worker 各一处），
    生效环境为 true，日志出现真实 Gitea 调用痕迹，且以容器内直连 API 读到真实
    asset.yaml 作为「非 Noop」的独立证据。
  - REQ-JOB-001：可靠任务的退避重试与租约语义在本卡修复后真正可达。

Contract/database changes:
  无。不改变任何 REST/MCP 响应结构、错误码集合、状态机取值或数据库结构；
  无新增 Flyway 迁移；application.yml 未改动（Gitea 超时以记录默认值提供，
  并在类注释中固定 4 × readTimeout < lease-seconds 的不等式）。

Validation run and results:
  - ./docs/ai-spec/tools/validate-spec.ps1 -> PASS（36 documents / 651 stable IDs）
  - validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-062.md -> PASS
    （2 requirements / 2 scenarios / 7 allowed paths）
  - validate-task-card.ps1 ... -CheckChangedPaths -> PASS
  - cd backend; ./mvnw.cmd -o test -Dtest=GiteaAssetRepositoryAdapterTest -> 18/18 PASS
  - cd backend; ./mvnw.cmd -o verify -> 520 项，2 失败、6 跳过、0 错误；
    2 项失败均为既存 PreviewJobHandlerTest CSV 解析（见 EVD limitations，不记为 PASS）
  - docker compose -f deploy/compose/compose.yaml config --quiet -> 退出码 0
  - ./deploy/compose/scripts/verify.ps1 -> V01–V11 + V05b–V05j 共 19 步全部 PASS，
    无 FAIL、无 SKIP，退出码 0
  - E4 取证 .trae/evidence/gitea-e4-062.ps1 -> 24 项断言全 PASS，RESULT: 0 FAILED

Evidence artifacts:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-062-001.yaml（E4）
  - .trae/evidence/gitea-e4-062.txt（E4 全文，RESULT: 0 FAILED）
  - .trae/evidence/gitea-062-BASELINE-upsert.txt（写入动词切片鉴别力对照，缺陷态 9/16 失败）
  - .trae/evidence/gitea-062-BASELINE.txt（URL 编码切片鉴别力对照，缺陷态 7/13 报错）
  - .trae/evidence/verify-ps1-062.txt、.trae/evidence/mvn-verify-062.log

Validation not run and reasons:
  - 契约破坏检查（@redocly/cli diff）：本卡无契约变更，未运行；
    且该子命令在 @redocly/cli 各版本均不存在，属已登记的既有工具链缺陷。
  - pnpm lint / typecheck / test / build：本卡未触碰 frontend/，且 forbiddenPaths 明确排除。

Remaining risks or follow-ups:
  - 无界调用缺陷的同类风险：本卡只修复了 Gitea 客户端。平台其它外部依赖
    （MinIO、DVC、Webhook 投递）应由独立任务审计其超时上界是否同样满足
    「调用有界且显著小于租约」这一前提。
  - Gitea 组织的自动创建与权限投影（REQ-AST-008，P1/ PROPOSED）未实现，
    当前 namespace 恒回退到服务账号用户名下（E4 中 repo 为 aihub-svc/<name>）。
  - PhysicalRecycleJobHandler.deleteGiteaRepository 仍为 TODO，仓库删除未实现。
  - 本卡修复后，TASK-P0BR-023 的 AC-P0B-JOB-003「依赖恢复后成功」已可达
    （见下方对照），023 需回栈复验收尾。
```

### TASK-P0BR-023 修复前后的 E4 对照（本卡 §10 强制要求）

同一判定在 023 的 E4 取证实现在修复前后由 FAIL 转为 PASS：

| 023 的 E4 判定 | 修复前（941ab67 基线的真实栈） | 修复后（本卡，同一真实栈） |
| --- | --- | --- |
| `asset.yaml` 经真实 Gitea API 可读 | FAIL：Gitea 内始终只有 auto_init 的默认 README，`asset.yaml` 缺失 | PASS：HTTP 200，且解出明文含 namespace/name |
| 建仓作业终态 | FAIL：`job_task.status=DEAD`，`attempts=5/5`，`error_code=ASSET_REPOSITORY_PROVISION_FAILED` | PASS：`SUCCEEDED`，`provisioning_status=COMPLETED` |
| `repo_full_name` | FAIL：空（从未走到绑定步骤） | PASS：`aihub-svc/<name>`，形如 owner/repo |
| `AC-P0B-JOB-003` 依赖恢复后成功 | 不可达：写路径必然失败且重试恒 409 | PASS（B6）：停机 → RETRY_WAIT → 恢复后自行 SUCCEEDED |
| 单次尝试耗时（依赖无响应） | 122717ms（跨 60s 租约，触发伪并发冲突） | 20095ms（有界，租约内完成，无伪冲突） |

对照中的「修复前」数据取自 023 的取证记录与本次实测
（`.trae/evidence/gitea-e4-062.txt` 之前的历史运行、`job_attempt #1 dur=122717ms`）。

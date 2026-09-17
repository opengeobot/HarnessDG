---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-047
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 54f20f76102bdbad8cf5f1f2e83e73f42823176d
stageGatePassed: true
stageGateEvidence: 用户批准。依据 READY 需求 REQ-IAM-001/REQ-JOB-001（P0-B 公共底座）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），修复可部署 Profile 装配缺陷以恢复 AC-P0B-ENG-004/006 的 E3/E4 可达性。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-IAM-001
  - REQ-JOB-001
acceptanceScenarios:
  - AC-P0B-ENG-004
  - AC-P0B-ENG-006
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-ENG-004|E3|干净卷启动 Compose 后，真实 PostgreSQL 上 Flyway V1..V25 全部应用且 V04 数据库迁移完整性检查 PASS
  - AC-P0B-ENG-006|E4|compose/api/worker 三个可部署 Profile 装配成功、backend 容器 Healthy、verify.ps1 V01-V11 全部 PASS 的退出码与输出
crossCuttingPlan:
  - AUTHN|N/A - 本任务只修复容器装配，不新增或放宽任何认证路径；修复后 /api/v1/system/users 无 Token 仍返回 401
  - AUTHZ|N/A - 不改变权限判定与 Scope 计算；修复后 V06/V07/V09/V10 默认拒绝断言保持 PASS
  - DB_FILTER|N/A - 不涉及查询条件或授权下推；Mapper 注册变化不改变任何 SQL 文本
  - STATE|N/A - 不引入业务状态；仅修正 Spring 组合根对 Mapper 的扫描范围
  - IDEMPOTENCY|N/A - 启动期 Bean 注册为幂等声明式行为，无写副作用
  - CONSISTENCY|N/A - 不跨系统写入；Gitea/MinIO/DVC 适配器行为不变
  - ERRORS|装配失败必须在启动期以明确诊断暴露，不得以 try/catch 或默认空实现掩盖缺失的 Mapper Bean
  - AUDIT|N/A - 启动过程不产生业务审计事件；修复后 V08 登录审计与脱敏断言保持 PASS
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|N/A - 不新增受控值或文案
  - CONFIG|不回退为按 Profile 分别硬编码扫描路径的运行时可配置开关；扫描范围必须是单一、可审查的代码事实
  - OBSERVABILITY|修复后 /actuator/health 分层就绪探针可用（backend Healthy 是 Compose 依赖条件），V11 保持 PASS
  - SECRETS|N/A - 不接触 Secret；验证证据不得包含 .env 中的口令、Gitea Token 或 MinIO 密钥
allowedPaths:
  - backend/src/main/java/com/aihub/bootstrap/AihubApplication.java
  - backend/src/test/java/com/aihub/arch
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-047.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-047-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - deploy
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-047-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T03:34:20Z
---

# TASK-P0BR-047：修复 Composable Profile 的 Mapper 扫描覆盖，恢复可部署栈启动

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

`compose`/`api`/`worker` Profile 在真实 Compose 环境中能够完成 Spring 上下文装配并启动，
使 `OUT-008`（Compose 可复现完整验收）与全部 P0-B E4 证据重新可达。

当前 `@MapperScan("com.aihub.*.infrastructure")` 只匹配**一级**模块包，而
`com.aihub.asset.discussion.infrastructure` 是**两级**包，其 `DiscussionThreadMapper`
未被注册为 Bean，导致 `MyBatisDiscussionRepository` → `DiscussionApplicationService` →
`CommentController` 装配链失败，应用启动即终止：

```text
Parameter 0 of constructor in com.aihub.asset.discussion.infrastructure.MyBatisDiscussionRepository
required a bean of type '...DiscussionThreadMapper' that could not be found.
```

## 2. 关联规格

```yaml
requirements: [REQ-IAM-001, REQ-JOB-001]
acceptanceScenarios: [AC-P0B-ENG-004, AC-P0B-ENG-006]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: []
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

所有需求必须是 `READY`，所有决策必须是 `ACCEPTED`。否则任务不得进入实施。
正文 ID 必须与 Front Matter 完全一致。AI 可以起草 DRAFT，但不得填写批准人并授权自身实施。
`stageGateEvidenceRefs` 只能引用本 Task 已声明的 Accepted Decision 或仓库内真实 Evidence 文件。
`scenarioEvidencePlan` 必须以 `AC-ID|E1-E5|预期产物` 逐条覆盖 Front Matter 的全部 AC。
`crossCuttingPlan` 使用模板中的 13 个稳定机器键；每项必须写具体要求，或写 `N/A -` 加理由。

### 缺陷事实（已复核）

- 缺陷位于**变更前基线** `6bdbd51`，由 `ac62e2d`（2026-07-05）引入 `asset.discussion`
  两级包后产生，晚于 2026-07-02 的全栈验证快照；
- 仓库内 14 个 `*Mapper` 接口中，**11 个**位于一级 `com.aihub.<module>.infrastructure`，
  **3 个**位于两级 `com.aihub.asset.discussion.infrastructure`；
- 影响面：`compose`/`api`/`worker` 三个可部署 Profile 全部无法装配，故
  `docker compose up` 的 backend 依赖条件永不满足，整个 Compose 栈不可用；
- 后果：P0-B 出口规则第 4/5 条要求的 E3/E4 证据对**任何**任务均不可达，
  包括已完成实施的 `TASK-P0BR-014/017/041`。

## 3. 范围

### 允许修改

- 模块：`com.aihub.bootstrap`、`com.aihub.arch`（测试）
- 文件或目录：
  - `backend/src/main/java/com/aihub/bootstrap/AihubApplication.java`（修正 Mapper 扫描范围，使两级模块包同样被覆盖）
  - `backend/src/test/java/com/aihub/arch/**`（新增回归门禁：断言每个位于 `infrastructure` 包的
    `*Mapper` 接口都被组合根声明的扫描范围覆盖，防止再次出现更深的包静默逃逸）
- 契约：无变更（无 API/MCP/Event 形状变化）
- Migration：无（本缺陷与库结构无关）
- 页面：无
- Fixture/Verify：不改动 `deploy/compose` 脚本；仅复用既有 `verify.ps1` 作为验收入口

### 明确不在范围

- 不新增、删除或重命名任何 Mapper、Repository、Controller 与业务用例；
- 不修改 `SqlSessionFactory`、数据源、连接池或 MyBatis-Plus 配置；
- 不修改 `SecurityConfiguration` 端点白名单与任何认证/授权判定；
- 不调整 `asset.discussion` 的包结构或业务实现（那是独立的 P1 Discussion 任务）；
- 不修复 `PreviewJobHandlerTest` 的 2 项既存失败（属既有 CSV 解析缺陷，另行建卡）；
- 不引入按 Profile 区分的扫描路径配置。

### 禁止变化

- 不改变的 API/状态/权限：所有 REST/MCP 响应结构、错误码、权限判定与可见性语义；
- 不允许的依赖升级或重构：不新增依赖，不做与本缺陷无关的包结构调整或重命名；
- 不允许的兼容破坏：不得改变任何既有 Mapper Bean 的名称与可注入性。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-ENG-004` | 验证方 | 干净卷、无历史 schema | 启动 Compose 栈 | 真实 PostgreSQL 上 Flyway V1..V25 全部应用；`platform_schema_info`/关键表存在；V04 检查 PASS | `E3` |
| `AC-P0B-ENG-004` | 验证方 | 已有完整 schema | 重启 backend | Flyway 报告 up to date，不重复执行、不报校验失败 | `E3` |
| `AC-P0B-ENG-006` | 验证方 | 修复已应用 | 启动 `compose,api` 与 `worker` Profile | 上下文装配成功，无 `No qualifying bean`；backend 容器进入 Healthy | `E3` |
| `AC-P0B-ENG-006` | 验证方 | 全栈就绪 | 运行 `verify.ps1` | V01-V11 全部 PASS 或明确 SKIP，退出码 0；无 FAIL | `E4` |
| `AC-P0B-ENG-006` | 验证方 | 未来新增更深层模块的 Mapper | 运行 ArchUnit 门禁 | 未被扫描范围覆盖时测试失败，阻止再次静默逃逸 | `E1/E2` |

必须覆盖正常、边界、失败、越权；涉及异步/并发时还须覆盖恢复和并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不触及认证路径；修复后无 Token 访问受保护端点仍 401 |
| Permission/Scope/ACL | N/A：不改变授权判定；修复后 V06/V07/V09/V10 默认拒绝保持 PASS |
| 数据库权限下推 | N/A：Mapper 注册变化不产生任何 SQL 文本差异 |
| 状态前置条件 | 应用必须在装配失败时快速终止并打印可诊断错误，不得带病启动 |
| 幂等与并发 | N/A：Bean 注册是启动期声明式过程，无持久化写副作用 |
| 事务与外部一致性 | N/A：不写业务库，不调用 Gitea/MinIO/DVC |
| 错误码 | 不得新增吞掉装配错误的兜底；缺失 Bean 必须仍以启动失败暴露 |
| 审计事件 | N/A：启动不产生业务审计；修复后 V08 断言保持 PASS |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | N/A：不新增受控值或文案 |
| 配置 | 不回退为运行时可配置的扫描开关；扫描范围保持单一可审查的代码事实 |
| 指标/Trace/告警 | 修复后分层健康探针可用；容器 Healthy 是 Compose 依赖条件，V11 保持 PASS |
| Secret/日志脱敏 | 证据不得包含 `.env` 口令、Gitea Token、MinIO 密钥或预签名 URL |

任何一项若不适用，填写 `N/A` 并说明理由；不得留空。

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：无新增接口；
3. 新增 Flyway 文件：无（本缺陷与库结构无关，且禁止修改 V1/V2）；
4. 存量清点与回填：无数据变更；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：修复只扩大 Bean 注册范围，不改变既有 Bean 的语义与名称，
   对已经正常装配的一级包 Mapper 无影响；
7. 生成类型更新：无。

禁止修改已应用 Migration。

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

1. 修正 `AihubApplication` 的 `@MapperScan`，使其覆盖两级及以上模块包；
2. 新增 ArchUnit 回归门禁：枚举 classpath 中位于 `..infrastructure` 包的 `*Mapper` 接口，
   断言每一个都被组合根声明的扫描范围匹配；未匹配即失败；
3. 以干净卷启动 Compose 栈，确认 backend/worker/frontend/nginx 全部就绪；
4. 运行 `deploy/compose/scripts/verify.ps1`，记录 V01-V11 结果。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；若本机 Docker 或 Testcontainers 不可用，依赖真实依赖的 IT 记为 SKIP
  并在 Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-ENG-004`、`AC-P0B-ENG-006`；
- 所需依赖：JDK 21、Maven Wrapper、Docker Compose（E3/E4 必需）、`deploy/compose/.env`（gitignored，需本地存在）；
- SKIP 判定：Docker 守护进程不可用时 E3/E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、`deploy/compose/scripts/verify.ps1` 输出、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-047-001.yaml`。

精确命令同时写入 Front Matter `requiredValidationCommands`；至少包含三条治理校验和一条本任务
专用测试/验收命令。正文描述不能替代机器字段。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；修改 frontend 必须包含
`pnpm lint/typecheck/test/build`；修改 OpenAPI 必须包含 lint 和 blocking diff；修改 Compose 必须
包含 config 与仓库 verify 脚本。

实施前固定执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md -CheckChangedPaths
```

任务结束前再执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md -CheckChangedPaths
```

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-047.md -CheckCompletion
```

`-CheckCompletion` 要求工作树干净、Evidence Commit 等于当前 HEAD、每条 AC 均有足够等级的 PASS
证据、没有 SKIP/notProven、已完成 Secret 检查并由验证责任方接受。未通过时最多交接为
`IMPLEMENTED_UNVERIFIED`。

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突；
- 存在未关闭的产品决策；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更；
- 修复需要改动 `asset.discussion` 业务实现或包结构（应改为独立任务）。

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

### 已验证的前置调查结论（DRAFT 阶段，供批准参考）

在一次性 worktree 中对当时 HEAD 施加**单行** `@MapperScan` 修正后实测：

- `api` Profile：`Started AihubApplication in 11.402 seconds`；
- `worker` Profile：`Started AihubApplication in 10.653 seconds`；
- 干净卷 `docker compose up -d`：backend 进入 `Healthy`，worker/frontend/nginx 全部 Started；
- `verify.ps1`：V01-V11 全部 PASS，退出码 0；
- 后端全量单测：482 项，失败 2 项（`PreviewJobHandlerTest` 的既存 CSV 缺陷，与本修复无关）。

该调查在一次性 worktree 中进行，未在主仓库写入任何产品代码修改，不构成实施。
本节为批准前参考，不替代本次实施后取得的正式 Evidence。

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-017
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 6bdbd51cee45fac9752c5723701ba86b7af270b3
stageGatePassed: true
stageGateEvidence: 用户批准（2026-09-17）以 READY 需求 REQ-AUTH-001/002 与 ACCEPTED 决策 DEC-011 作为本任务阶段依据
stageGateEvidenceRefs:
  - DEC-011
requirements:
  - REQ-AUTH-001
  - REQ-AUTH-002
acceptanceScenarios:
  - AC-P0B-AUTH-003
  - AC-P0B-AUTH-005
  - AC-DST-DETAIL-003
decisions:
  - DEC-011
scenarioEvidencePlan:
  - AC-P0B-AUTH-003|E4|两组织/两项目数据下跨组织主体访问列表与详情的响应与查询计划证据
  - AC-P0B-AUTH-005|E4|Scope 与 RBAC/ACL 四种组合的授权判定结果与拒绝审计
  - AC-DST-DETAIL-003|E4|无权主体访问列表/详情/预览的防枚举响应对比与 Facet 计数证据
crossCuttingPlan:
  - AUTHN|详情与列表必须基于已认证 Principal 计算可见性；无 Principal 时 fail closed，不回退全可见
  - AUTHZ|可见性按 4.1 判定矩阵实时计算；详情与列表使用同一 Predicate，并复用 authorizeResourceOrNotFound 保持防枚举语义
  - DB_FILTER|visibility 条件与 AccessScope 三元条件继续在 SQL 合并为单条查询；组织/项目条件仅对 INTERNAL 生效，PRIVATE 仅接受 ACL 与 Owner；禁止全量查询后 Java 过滤
  - STATE|停用组织/项目/成员或移除 Team 后可见性实时收紧，不缓存越权结果
  - IDEMPOTENCY|N/A - 只读查询不引入幂等键，Cursor 仍需绑定主体作用域防跨主体复用
  - CONSISTENCY|N/A - 不跨系统写入；授权数据与业务数据同库读取
  - ERRORS|无权详情返回与随机不存在 ID 相同的 ASSET_NOT_FOUND；不因 403/404 差异泄露存在性
  - AUDIT|按策略记录拒绝事件，含 principal、resource、result=DENIED；不含资产敏感字段原文
  - NOTIFICATION|N/A - 读取与拒绝路径不触发业务通知
  - TAXONOMY_I18N|N/A - 不新增受控值；可见性为代码枚举而非字典项
  - CONFIG|N/A - 不新增配置项，不下放可见性策略为运行时可改配置
  - OBSERVABILITY|查询耗时与权限过滤指标沿用既有埋点，禁止在日志输出不可见资产内容
  - SECRETS|证据与日志不得出现 JWT、下游凭据；查询计划证据需脱敏参数值
allowedPaths:
  - backend/src/main/java/com/aihub/asset/application/AssetAccessPolicy.java
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
  - backend/src/main/java/com/aihub/asset/domain
  - backend/src/main/java/com/aihub/asset/infrastructure
  - backend/src/main/java/com/aihub/authorization/application/AuthorizationService.java
  - backend/src/main/java/com/aihub/authorization/domain
  - backend/src/main/java/com/aihub/organization/application
  - backend/src/main/java/com/aihub/organization/infrastructure
  - backend/src/test/java/com/aihub/asset
  - backend/src/test/java/com/aihub/authorization
  - backend/src/test/java/com/aihub/organization
  - docs/ai-spec/tasks/TASK-P0BR-017.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-017-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-017-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T01:12:43Z
---

# TASK-P0BR-017：资产可见性与作用域组合判定下推，详情采用防枚举语义

> 状态：`DRAFT`
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-16

## 1. 目标

不同组织/项目/可见性的主体在资产列表、Facet 与详情上得到一致且实时计算的授权结果：
列表与 Facet 在 SQL 阶段过滤，详情对无权主体返回与"不存在"相同的语义，不再出现"所有主体可见全部
可见性、详情仅做恒真内存判断"的越权读取路径。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-001, REQ-AUTH-002]
acceptanceScenarios: [AC-P0B-AUTH-003, AC-P0B-AUTH-005, AC-DST-DETAIL-003]
decisions: [DEC-011]
adrs: [ADR-0002]
openapiOperations: [searchAssets, getAsset, getAssetFacets]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

所有需求必须是 `READY`，所有决策必须是 `ACCEPTED`。否则任务不得进入实施。
正文 ID 必须与 Front Matter 完全一致。AI 可以起草 DRAFT，但不得填写批准人并授权自身实施。
`stageGateEvidenceRefs` 只能引用本 Task 已声明的 Accepted Decision 或仓库内真实 Evidence 文件。
`scenarioEvidencePlan` 必须以 `AC-ID|E1-E5|预期产物` 逐条覆盖 Front Matter 的全部 AC。
`crossCuttingPlan` 使用模板中的 13 个稳定机器键；每项必须写具体要求，或写 `N/A -` 加理由。

## 3. 范围

### 允许修改

- 模块：`com.aihub.asset.application`、`com.aihub.asset.domain`、`com.aihub.asset.infrastructure`
- 文件或目录：
  - `backend/src/main/java/com/aihub/asset/application/AssetAccessPolicy.java`
  - `backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java`
  - `backend/src/main/java/com/aihub/asset/domain/**`（仅补充可见性判定所需的查询条件值对象）
  - `backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java`
  - `backend/src/main/java/com/aihub/asset/infrastructure/MyBatisAssetRepository.java`（若详情需单资产下推查询）
  - `backend/src/test/java/com/aihub/asset/**`
  - `backend/src/test/java/com/aihub/authorization/**`（如需在授权集成测试中复现组合场景）
- 契约：无变更（请求/响应结构与既有 OpenAPI 操作保持一致）
- Migration：无（可见性列与 ACL 表已存在）
- 页面：无
- Fixture/Verify：仅新增/调整授权组合测试数据，不改动 `deploy/compose` 脚本

### 实现路径（已核实的既有能力）

1. **列表与 Facet 下推**：修改 `AssetSearchDao.appendAccessScopeFilter`，使组织/项目条件仅对 `INTERNAL`
   生效，`PRIVATE` 仅接受 `scopeAclIds` 与 Owner 条件；保持单条 SQL，禁止全量加载。
2. **详情与列表同一 Predicate**：`loadAccessible` 不再依赖恒真的 `canAccess`，改为应用与列表等价的
   判定；无权时抛出既有 `ASSET_NOT_FOUND`（`authorizationService.authorizeResourceOrNotFound`
   已实现该防枚举语义，可直接复用）。
3. **Owner 判定**：`AccessScope` 当前不含 Team 作用域。按既有出站端口模式
   （`com.aihub.organization.application.OrganizationMembershipPort`，实现位于
   `organization/infrastructure/JdbcOrganizationMemberRepository`）新增或扩展只读端口，按
   `principalId` 解析其所属 `teamId` 集合，在数据库阶段完成；缺端口时 fail closed
   （Owner 条件视为不满足），不得放宽为组织级可见。
4. **移除死代码**：`AssetAccessPolicy.visibleVisibilities` 恒返回三值且注释仍称"P1 现状……身份与 RBAC
   属 P3"，须改为按主体真实计算或删除该恒真集合，并同步修正过期注释。

### 明确不在范围

- 不实现 `TASK-P0BR-014` 的 MCP 失败关闭；
- 不实现 Role/Binding/ACL 管理与自提权防护（`AC-P0B-AUTH-006/007`，属 `REQ-AUTH-003`，当前 `OPEN`）；
- 不新增资产状态、敏感等级或发布流程语义；
- 不实现前端展示与 i18n（属 `TASK-P0BR-041`）；
- 不引入缓存层或外部授权服务。

### 禁止变化

- 不改变的 API/状态/权限：既有授权判定的 Permission 语义、可见性枚举取值、Cursor 语义；
- 不允许的依赖升级或重构：不新增权限引擎/缓存依赖，不做无关包结构或命名重构；
- 不允许的兼容破坏：不放宽任何既有拒绝路径；不得以"修复"名义降低校验或删除既有测试。

## 4. 行为切片

### 4.1 可见性判定矩阵（本任务规范）

依据 `AC-P0B-AUTH-003`（MUST：列表 SQL 不返回；详情使用防枚举语义）、`REQ-AUTH-002`（详情与列表使用
相同访问 Predicate）、设计要求 5.5（"资源不存在和无权访问私有资源对外均可返回 `ASSET_NOT_FOUND`"）
与既有 `Visibility` 枚举定义（`PRIVATE` 仅 Owner 与显式授权者；`INTERNAL` 组织内可见；`PUBLIC` 全平台
已认证主体），列表与详情必须使用同一判定：

| 可见性 | 可见条件（与 SQL 条件等价） |
| --- | --- |
| `PUBLIC` | 任意已认证且状态正常的 Principal |
| `INTERNAL` | 主体在资产所属组织（`scopeOrgIds`）或项目（`scopeProjectIds`）内 |
| `PRIVATE` | 仅显式 ACL 授权（`scopeAclIds`）或 Owner（`ownerTeamId`/`owners` 成员关系） |

`platformAdmin` 为显式 Permission 结果，是唯一绕过上述条件的合法路径；不得以空过滤条件表达。

**当前缺陷**：`appendAccessScopeFilter` 的 `OR a.organization_id IN (:scopeOrgIds)` 对全部可见性生效，
使本组织的 `PRIVATE` 资产对全体组织成员可见，与 `PRIVATE` 定义冲突；`loadAccessible` 仅做恒真的
内存可见性判断，使详情绕过上述全部条件。

**待确认（不阻塞本任务）**：`INTERNAL` 是否应进一步收紧至项目级。在本任务中 `INTERNAL` 保持
"组织或项目作用域内可见"；若后续决策收紧，仅需调整该一行条件。

### 4.2 场景

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-AUTH-003` | USER | 主体属组织 A，资产属组织 B 且为 PRIVATE | 请求该资产详情 | 返回 `ASSET_NOT_FOUND`，与随机不存在 ID 语义一致 | `E3/E4` |
| `AC-P0B-AUTH-003` | USER | 主体与资产同属组织 A，资产为 PRIVATE 且无 ACL/Owner 关系 | 查询列表与详情 | 列表不返回该资产；详情返回 `ASSET_NOT_FOUND` | `E3/E4` |
| `AC-P0B-AUTH-003` | USER | 同组织资产为 INTERNAL | 查询列表与详情 | 列表返回该资产；详情成功（证明不是"全部拒绝"） | `E3/E4` |
| `AC-P0B-AUTH-005` | USER | Scope 含 `asset:read` 但 RBAC/ACL 不授予 | 请求详情或列表 | 拒绝；仅当全部条件满足才通过 | `E4` |
| `AC-DST-DETAIL-003` | USER | 无权主体已知 assetId 与随机 ID | 分别请求详情、Facet、预览 | 两者不可区分；Facet 不泄露名称与数量 | `E4` |
| `AC-DST-DETAIL-003` | USER | 有权主体（Owner/ACL/同组织 INTERNAL） | 请求同一资产详情 | 成功返回，证明系统不是"全部拒绝" | `E4` |

必须覆盖正常、边界、失败、越权；涉及异步/并发时还须覆盖恢复和并发。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | 可见性与作用域按已认证 Principal 实时计算；无 Principal 时必须 fail closed，不得回退为全部可见性 |
| Permission/Scope/ACL | 详情使用资源级授权与防枚举 API；Scope、RBAC、ACL、成员关系需同时满足 |
| 数据库权限下推 | `visibility` 与 AccessScope（PUBLIC + 组织/项目/ACL）条件继续合并到同一条 SQL；Facet 使用同一 Predicate；禁止全量加载后内存过滤 |
| 状态前置条件 | 停用组织/项目/成员或移除 Team 后，后续请求实时收紧，不复用旧判定 |
| 幂等与并发 | N/A：只读查询无写副作用；Cursor 必须绑定主体作用域以防跨主体复用 |
| 事务与外部一致性 | N/A：授权与资产同库读取，不跨系统写入 |
| 错误码 | 无权详情统一 `ASSET_NOT_FOUND`；一般权限缺失使用 `AUTH_PERMISSION_DENIED` |
| 审计事件 | 拒绝按既有策略记录，含 principal/resource/result；不记录不可见资产敏感内容 |
| 通知 | N/A：查询与拒绝路径不触发通知 |
| 字典/标签/i18n | N/A：可见性为代码枚举与数据库约束，不改为字典项 |
| 配置 | N/A：不允许把可见性策略做成运行时可改配置 |
| 指标/Trace/告警 | 沿用既有查询与授权指标，日志不得输出不可见资产内容 |
| Secret/日志脱敏 | 查询计划与 SQL 证据需脱敏参数值；不得输出 JWT、下游凭据或敏感字段 |

任何一项若不适用，填写 `N/A` 并说明理由；不得留空。

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无接口结构与错误码枚举变更；如错误语义文档需澄清，只在既有描述内补充；
2. 示例请求、响应和错误：补充"无权详情与不存在 ID 响应一致"的示例；
3. 新增 Flyway 文件：无；
4. 存量清点与回填：评估既有 PRIVATE/INTERNAL 资产在收紧后是否对当前 Owner 仍可见，形成清点结论；
5. 索引、唯一约束、外键及理由：若查询计划显示缺失，需在 `V3+` 前向迁移中补充索引并给出理由；
6. 兼容窗口与失败恢复：收紧为纯拒绝强化，不改变有权主体的成功响应；
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

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；Docker 不可用时依赖真实 PostgreSQL 的 IT 记为 SKIP 并写入 Evidence `limitations`，
  不得将越权与防枚举场景记为 PASS；
- 覆盖的 AC ID：`AC-P0B-AUTH-003`、`AC-P0B-AUTH-005`、`AC-DST-DETAIL-003`；
- 所需依赖：JDK 21、Maven Wrapper、PostgreSQL（Testcontainers 或 Compose）、Docker Compose（E4）；
- SKIP 判定：缺少真实数据库时下推与防枚举结论不成立，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、查询计划证据、`docs/ai-spec/tasks/evidence/EVD-P0BR-017-001.yaml`。

精确命令同时写入 Front Matter `requiredValidationCommands`；至少包含三条治理校验和一条本任务
专用测试/验收命令。正文描述不能替代机器字段。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`；修改 frontend 必须包含
`pnpm lint/typecheck/test/build`；修改 OpenAPI 必须包含 lint 和 blocking diff；修改 Compose 必须
包含 config 与仓库 verify 脚本。任务领取前如果项目还没有这些命令，先建立并批准测试基线，不能
用构建命令替代行为测试。

实施前固定执行：

```powershell
./docs/ai-spec/tools/validate-spec.ps1
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md -CheckChangedPaths
```

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-017.md -CheckCompletion
```

`-CheckCompletion` 要求工作树干净、Evidence Commit 等于当前 HEAD、每条 AC 均有足够等级的 PASS
证据、没有 SKIP/notProven、已完成 Secret 检查并由验证责任方接受。未通过时最多交接为
`IMPLEMENTED_UNVERIFIED`。

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突，例如 `REQ-AUTH-003` 权限矩阵与 `/mcp` 边界定义不一致；
- 存在未关闭的产品决策，例如 INTERNAL 的组织可见边界未被任何 READY 规格或 ACCEPTED 决策确定；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试；
- 无法产生任务要求的最低证据等级；
- 工作树存在与任务重叠且来源不明的变更。

**关于 `INTERNAL` 边界**：4.1 已依据 `AC-P0B-AUTH-003`、`REQ-AUTH-002`、设计要求 5.5 与既有
`Visibility` 枚举定义确定判定矩阵，本任务据此实施，不再等待 `TASK-GOV-002`。`TASK-GOV-002` 若后续
收紧 `INTERNAL` 至项目级，属独立范围调整。

**必须停止并报告的情形**：实施中发现 4.1 判定矩阵与任何 READY 规格、ACCEPTED 决策或既有 AC 冲突。

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

---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-055
status: READY
implementationAuthorized: true
phase: P0-B
baseCommit: 483af61ad55386165229479c247f0f82ba547908
stageGatePassed: true
stageGateEvidence: 用户批准。依据 READY 需求 REQ-UI-001（公共管理端，P0-B）与 ACCEPTED 决策 DEC-013（Compose 为功能验收环境），修复模型扩展实体的列名转义缺陷以恢复资产详情读取；属 ADR-0002 允许的「公共底座接入与安全整改」，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-013
requirements:
  - REQ-UI-001
acceptanceScenarios:
  - AC-P0B-UI-004
decisions:
  - DEC-013
scenarioEvidencePlan:
  - AC-P0B-UI-004|E4|在真实 Compose 栈上登记 MODEL 与 DATASET 两类资产后分别读取资产详情：GET /api/v1/assets/{id} 均返回 200，model 与 dataset 治理字段与创建时提交的 itemCode 一致，且后端日志不再出现 syntax error at or near
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；详情读取继续要求既有 asset:read
  - AUTHZ|N/A - 不新增或放宽权限；查询仍经既有权限下推
  - DB_FILTER|不改变详情读取的可见性判定；修复只影响 SQL 的列名转义
  - STATE|不改变任何资产状态枚举与 CHECK 约束
  - IDEMPOTENCY|N/A - 只读路径
  - CONSISTENCY|N/A - 不跨系统写入
  - ERRORS|修复后该路径不得再产生 500 INTERNAL_ERROR；未知资产仍返回既有 NOT_FOUND 语义
  - AUDIT|N/A - 读取不新增审计事件
  - NOTIFICATION|N/A
  - TAXONOMY_I18N|N/A - 不涉及受控值来源，只修 SQL 语法
  - CONFIG|不得引入按数据库类型分支的列名映射配置
  - OBSERVABILITY|修复后详情路径不再产生 BadSqlGrammarException 级别的 ERROR 日志噪声
  - SECRETS|N/A - 不接触 Secret；证据不得包含 Token
allowedPaths:
  - backend/src/main/java/com/aihub/asset/infrastructure
  - backend/src/test/java/com/aihub/asset
  - docs/ai-spec/02-delivery/work-breakdown.md
  - docs/ai-spec/tasks/TASK-P0BR-055.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-055-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - backend/src/main/resources/db/migration/V14__asset_remediation.sql
  - contracts
  - frontend
  - deploy
requiredEvidenceLevel: E4
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-055-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-055.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-055.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: User
approvedAt: 2026-09-17T08:40:35Z
---

# TASK-P0BR-055：修复资产模型扩展实体的列名转义，恢复资产详情读取

> 状态：`READY`（用户已批准并授权实施）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

登记资产后能读到自己的详情：`GET /api/v1/assets/{assetId}` 返回 200，
MODEL 与 DATASET 两类资产详情的治理字段与创建时提交的 `itemCode` 一致。

## 2. 关联规格

```yaml
requirements: [REQ-UI-001]
acceptanceScenarios: [AC-P0B-UI-004]
decisions: [DEC-013]
adrs: [ADR-0002]
openapiOperations: [getAsset]
mcpTools: []
eventSchemas: []
flywayBaseline: V14
```

### 缺陷事实（已复核，证据见 EVD-P0BR-051-001）

`AssetModelEntity`（`backend/src/main/java/com/aihub/asset/infrastructure/AssetModelEntity.java:34`）
的列名注解使用了 **MySQL 风格反引号**：

```java
@TableField("`precision`")
private String precision;
```

该注解是**全仓库唯一**使用反引号的 `@TableField`。MyBatis-Plus 将其原样拼进 SQL，
而 PostgreSQL 不认反引号作为标识符引用符，生成：

```text
SELECT asset_id,framework,task,architecture,parameter_scale,`precision`,weight_format,runtime,
       known_risks,usage_restrictions,sensitivity_code FROM asset_model WHERE asset_id=?
```

真实栈实测报错（`Position: 72` 正好落在反引号处）：

```text
org.postgresql.util.PSQLException: ERROR: syntax error at or near ","
  Position: 72
### The error may exist in com/aihub/asset/infrastructure/AssetModelMapper.java (best guess)
```

调用链：`AssetController.getAsset:118` → `AssetApplicationService.getAsset:145`
→ `loadAccessible:386` → `MyBatisAssetRepository.findByAssetId:71` → `toDomain:317`
→ `attachProfile:323` → `AssetModelMapper.selectById` → `BadSqlGrammarException`。

**反证（排除误判）**：在真实 PostgreSQL 中执行
`select ... parameter_scale, precision, weight_format ... from asset_model limit 1`
成功返回行——`precision` 作为列名**不加任何引用符完全合法**（它虽是 SQL 关键字，
但在该位置不产生歧义）。因此反引号是唯一病因，修复方向是去掉转义而非改列名。

影响：**所有读取 MODEL 资产详情的路径**均返回 500，包括详情页、资产设置页与 MCP 详情工具。

## 3. 范围

### 允许修改

- 模块：`com.aihub.asset.infrastructure`
- 文件或目录：
  - `backend/src/main/java/com/aihub/asset/infrastructure/AssetModelEntity.java`（移除反引号）
  - `backend/src/main/java/com/aihub/asset/infrastructure`（如发现同类转义一并修正）
  - `backend/src/test/java/com/aihub/asset`（新增回归测试）
- 契约：无变更
- Migration：**无**（`asset_model.precision` 列本身正确，缺陷在 Java 侧注解）
- 页面：无
- Fixture/Verify：不改动 `deploy/compose`（`verify.sh` 的 V12 等缺陷归独立任务）

### 明确不在范围

- 不改 `asset_model` 表结构或任何 Migration（列名与类型均正确）；
- 不重写 `MyBatisAssetRepository` 的查询方式或改用显式 SQL；
- 不升级 MyBatis-Plus 版本；
- 不新增或修改全局的方言/转义配置（那会掩盖单点缺陷并影响其它实体）；
- 不修复 `verify.sh` 的 V12–V21、幂等键唯一约束、全局幂等拦截器（归各自任务）。

### 禁止变化

- 不改变的 API/状态/权限：`GET /api/v1/assets/{assetId}` 的响应结构、`asset:read` 权限要求、
  资产的可见性判定逻辑；
- 不允许的依赖升级或重构：不新增依赖；不改实体字段的 Java 名与 getter/setter；
- 不允许的兼容破坏：不得以「重命名数据库列」或「删除该列」规避语法问题；
  不得把 `precision` 改为字符串拼接的显式 SQL 而绕过实体映射。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-P0B-UI-004` | 有 `asset:read` 的用户 | 真实 Compose 栈，存在一个 MODEL 资产 | `GET /api/v1/assets/{id}` | 返回 200；`model.framework/task/precision` 与创建时提交的 itemCode 一致 | `E4` |
| `AC-P0B-UI-004` | 同上 | 同上 | 检视后端日志 | 不再出现 `syntax error at or near` 与 `BadSqlGrammarException` | `E4` |
| `AC-P0B-UI-004` | 同上 | 存在一个 DATASET 资产 | `GET /api/v1/assets/{id}` | 返回 200 且 dataset 字段一致（确认非只修 MODEL 分支） | `E4` |
| `AC-P0B-UI-004` | 同上 | 不存在的 assetId | `GET /api/v1/assets/{id}` | 返回既有 NOT_FOUND 语义（不得因修复变成 500） | `E4` |
| `AC-P0B-UI-004` | 审查者 | 源码树 | 检索 `@TableField` 中的反引号 | 结果为 0 处；同类转义不得残留 | `E2` |
| `AC-P0B-UI-004` | 审查者 | 修复后的实体 | 运行回归测试 | 测试能在列名再次被反引号包裹时失败 | `E2` |

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径 |
| Permission/Scope/ACL | N/A：不新增或放宽权限；详情的可见性判定不变 |
| 数据库权限下推 | 不改变查询过滤与授权下推；修复只涉及列名转义 |
| 状态前置条件 | N/A：不改变任何状态枚举 |
| 幂等与并发 | N/A：只读路径 |
| 事务与外部一致性 | N/A |
| 错误码 | 未知资产保持既有 NOT_FOUND；不得再出现 500 |
| 审计事件 | N/A：读取不新增审计 |
| 通知 | N/A |
| 字典/标签/i18n | N/A：不改变受控值来源；修复只让既有治理字段可被读出 |
| 配置 | 不得引入按方言分支的列名映射或全局转义开关 |
| 指标/Trace/告警 | 不新增指标；修复后该路径不再产生 ERROR 级日志噪声 |
| Secret/日志脱敏 | N/A；证据不得包含 Token |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：无；
2. 示例请求、响应和错误：`getAsset` 由 500 恢复为契约声明的 200；
3. 新增 Flyway 文件：**无**——此为关键判断点：表结构正确，缺陷纯在 Java 侧注解，
   不需要也不允许改 Migration；
4. 存量清点与回填：无需回填（资产行已正确写入，只是读不出来）；
5. 索引、唯一约束、外键及理由：无；
6. 兼容窗口与失败恢复：修复后所有资产详情读取路径一并恢复；无兼容窗口；
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

1. 将 `AssetModelEntity` 的 `@TableField("`precision`")` 改为 `@TableField("precision")`；
   同时全仓库检索其余 `@TableField`，确认无同类反引号残留；
2. 新增回归测试：以源码扫描断言 `asset/infrastructure` 下 `@TableField` 注解值不含反引号
   （该测试须在缺陷重现时失败，附负例对照）；
   如条件允许，补一条真实 PostgreSQL 的仓储测试断言 `findByAssetId` 对 MODEL 资产可读回；
3. 在真实 Compose 栈上验证 MODEL 与 DATASET 两类资产的详情读取，
   并确认后端日志不再出现 `syntax error at or near`。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml up -d
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：`mvnw verify` 为 0（既存 `PreviewJobHandlerTest` 的 2 项 CSV 失败不计入本卡，
  但必须在 Evidence `limitations` 中如实列出）；`verify.ps1` 为 0；
- 覆盖的 AC ID：`AC-P0B-UI-004`；
- 所需依赖：JDK 21、Docker（Compose 栈）；E4 需要真实 PostgreSQL（该缺陷只在真实 PG 上复现）；
- SKIP 判定：Docker 不可用时 E4 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-055-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-055.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 发现除注解外还有其它 PostgreSQL 不兼容的 SQL 生成点（需要更大范围整改）；
- 需要修改 `V1`/`V2`/`V14` 或其它已应用 Migration 才能修好；
- 需要降低权限、校验、约束或测试；
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
必须说明为何本卡**不需要**新增 Migration。

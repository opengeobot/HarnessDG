---
schemaVersion: harnessdg.task/v1
taskId: TASK-P0BR-049
status: DRAFT
implementationAuthorized: false
phase: P0-B
baseCommit: 67a0ec14230c41f8996272eefa6384f88df01c4b
stageGatePassed: false
stageGateEvidence: 待用户批准。拟以 READY 需求 REQ-AUTH-002/REQ-TAX-001 与 ACCEPTED 决策 DEC-008 作为阶段依据；本卡为 P0-B 列表权限下推与聚合计数一致性整改，不新增业务能力。
stageGateEvidenceRefs:
  - DEC-008
requirements:
  - REQ-AUTH-002
  - REQ-TAX-001
acceptanceScenarios:
  - AC-P0B-AUTH-003
  - AC-DST-TAX-002
decisions:
  - DEC-008
scenarioEvidencePlan:
  - AC-P0B-AUTH-003|E3|两个权限不同的 Principal 对同一 Facet 请求得到各自授权计数；不可见资产不进入任何维度的计数，且 totalCount 与同一过滤条件下 items 的可达总数一致
  - AC-DST-TAX-002|E4|按 type=DATASET、sensitivity、tagId 分别请求 Facet，返回的计数与施加同一过滤条件后的检索结果一致；不同维度之间为 AND
crossCuttingPlan:
  - AUTHN|N/A - 不改变认证路径；Facet 端点继续要求已认证主体
  - AUTHZ|Facet 与 items 必须使用同一授权谓词（AccessScope 下推），不得因聚合查询放宽可见性
  - DB_FILTER|Facet 的 type/sensitivity/tagId 及各分类维度过滤必须在 SQL WHERE 阶段下推，禁止取回全量后在应用层过滤
  - STATE|沿用与 search 相同的状态集合（默认排除 ARCHIVED）；不新增状态枚举或迁移
  - IDEMPOTENCY|N/A - 只读聚合查询，无写副作用
  - CONSISTENCY|N/A - 不跨系统写入；不涉及 Gitea/MinIO/DVC
  - ERRORS|非法过滤值保持既有稳定错误码；不得因聚合失败静默返回空 Facet 冒充成功
  - AUDIT|N/A - 搜索与聚合只记录脱敏访问指标，不记录可能含敏感业务文本的完整查询
  - NOTIFICATION|N/A - 不触发站内通知或 Webhook
  - TAXONOMY_I18N|分类过滤只接受稳定 itemCode/tagId；停用项保留历史回显，禁止自由值
  - CONFIG|N/A - 不新增配置项
  - OBSERVABILITY|N/A - 不新增指标或告警；沿用既有访问指标
  - SECRETS|N/A - 不接触 Secret；证据不得包含凭据、Token 或预签名 URL
allowedPaths:
  - backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java
  - backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java
  - backend/src/main/java/com/aihub/asset/api/AssetController.java
  - backend/src/test/java/com/aihub/asset
  - contracts/openapi/aihub-v1.yaml
  - docs/ai-spec/tasks/TASK-P0BR-049.md
  - docs/ai-spec/tasks/evidence/EVD-P0BR-049-001.yaml
preExistingDirtyPaths: []
forbiddenPaths:
  - backend/src/main/resources/db/migration/V1__baseline.sql
  - backend/src/main/resources/db/migration/V2__asset_catalog.sql
  - frontend
  - deploy
requiredEvidenceLevel: E3
evidenceTemplate: docs/ai-spec/templates/evidence-manifest.yaml
evidenceManifests:
  - docs/ai-spec/tasks/evidence/EVD-P0BR-049-001.yaml
requiredValidationCommands:
  - ./docs/ai-spec/tools/validate-spec.ps1
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-049.md
  - ./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-049.md -CheckChangedPaths
  - cd backend; ./mvnw.cmd verify
  - npx --yes @redocly/cli lint --config contracts/openapi/redocly.yaml contracts/openapi/aihub-v1.yaml
  - git show HEAD:contracts/openapi/aihub-v1.yaml > .trae/aihub-base.yaml; npx --yes @redocly/cli diff .trae/aihub-base.yaml contracts/openapi/aihub-v1.yaml --fail-on-breaking
  - docker compose -f deploy/compose/compose.yaml config --quiet
  - powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
approvedBy: <human-or-controlled-approval>
approvedAt: <UTC-timestamp>
---

# TASK-P0BR-049：资产 Facet 过滤条件下推一致

> 状态：`DRAFT`（待用户批准后改为 `READY`）
> 规则：一个任务只交付一个可独立验收的纵向结果。
> 门禁：YAML Front Matter 是机器权威；正文不得与其冲突。
> 作者：AxeXie ｜ 时间：2026-09-17

## 1. 目标

`GET /api/v1/assets/facets` 的聚合计数与同一过滤条件下的检索结果一致：调用方传入的类型、
敏感度、受控标签与分类维度真实生效，且计数仍在数据库阶段完成权限、敏感度与状态过滤。

## 2. 关联规格

```yaml
requirements: [REQ-AUTH-002, REQ-TAX-001]
acceptanceScenarios: [AC-P0B-AUTH-003, AC-DST-TAX-002]
decisions: [DEC-008]
adrs: [ADR-0002]
openapiOperations: [getAssetFacets]
mcpTools: []
eventSchemas: []
flywayBaseline: V1
```

### 缺陷事实（已复核）

`AssetSearchDao.facet(AssetSearchCriteria)` 构造的聚合 SQL 只下推了
`statuses`、`visibilities`、`keyword` 与 `AccessScope`，**未**下推
`type`、`sensitivity`、`tagId`/`tagIds` 及 `taskCodes`/`modalityCodes`/`formatCodes`/`languageCodes`。

同时 `AssetApplicationService.getAssetFacets(String keyword, AssetType type, String principalId)`
在构造 `AssetSearchCriteria` 时把除 `keyword`/`type` 之外的过滤位全部置为 `null`，
而 `AssetController.getAssetFacets` 也只暴露 `keyword` 与 `type` 两个请求参数。

后果：`?type=DATASET` 返回的是**全类型**计数，与 `AC-DST-TAX-002`
「不可见资产不影响 items、count、autocomplete 或 Cursor」及
`REQ-AUTH-002`「facet/count/autocomplete 与 items 使用同一授权 Predicate」的语义不符。

参考实现对比：`AssetSearchDao.search(AssetSearchCriteria)` 已正确下推上述全部过滤位，
且 `appendAccessScopeFilter` 已实现可见性判定矩阵（`TASK-P0BR-017` / `AC-P0B-AUTH-003`）。
本卡把 `facet` 对齐到 `search` 的过滤语义，不新增权限逻辑。

## 3. 范围

### 允许修改

- 模块：`com.aihub.asset`（application / infrastructure / api）
- 文件或目录：
  - `backend/src/main/java/com/aihub/asset/infrastructure/AssetSearchDao.java`
    （`facet` 复用与 `search` 相同的过滤下推；抽取共享的条件拼装以避免两处漂移）
  - `backend/src/main/java/com/aihub/asset/application/AssetApplicationService.java`
    （`getAssetFacets` 接受完整过滤条件，不再丢弃过滤位）
  - `backend/src/main/java/com/aihub/asset/api/AssetController.java`
    （`/assets/facets` 暴露与检索一致的受控过滤参数）
  - `backend/src/test/java/com/aihub/asset/**`（Facet 与 search 计数一致性的回归测试）
- 契约：`/assets/facets` 的参数面变化按 OpenAPI 契约先行处理（见第 6 节）
- Migration：无
- 页面：无
- Fixture/Verify：不改动 `deploy/compose` 脚本

### 明确不在范围

- 不新增 Facet 维度（如 `matchedFields`、数据集分类多值维度、`sizeBucketCode` 维度）；
  这些属 `REQ-DST-TAX-001`，由 `TASK-P1-010` 处理；
- 不实现排序白名单（`relevance/updatedAt/publishedAt/sampleCount/totalBytes`）；
- 不实现前端 Facet 侧栏与 URL 筛选同步；
- 不改变 `search` 的既有过滤与排序语义，只抽取共享拼装；
- 不改变授权判定本身（`AuthorizationService.computeAccessScope`、`AssetAccessPolicy`）。

### 禁止变化

- 不改变的 API/状态/权限：既有 Facet 响应结构（各维度 `Map<值, 计数>` 与 `totalCount`）保持兼容；
  授权与可见性语义不得放宽；
- 不允许的依赖升级或重构：不新增依赖，不改变查询的 `BASE_SELECT` 语义；
- 不允许的兼容破坏：不得删除既有 `keyword`/`type` 参数；不得让 Facet 返回比 `search` 更多的可见数据。

## 4. 行为切片

| 场景 ID | Actor | Given | When | Then | 最低证据 |
| --- | --- | --- | --- | --- | --- |
| `AC-DST-TAX-002` | 有 `asset:read` 的主体 | 同时存在 MODEL 与 DATASET | 请求 `?type=DATASET` 与 `?type=MODEL` 的 Facet | 两种请求的计数只包含对应类型；与同条件 `search` 的可达总数一致 | `E3` |
| `AC-DST-TAX-002` | 同上 | 资产带不同敏感度 | 按 `sensitivity` 请求 Facet | 计数与施加同一 `sensitivity` 的检索结果一致 | `E3` |
| `AC-DST-TAX-002` | 同上 | 资产带受控标签 | 按 `tagId` 请求 Facet | 计数只含带该标签的资产；不存在标签的计数为 0 而非全量 | `E3` |
| `AC-P0B-AUTH-003` | 两个权限不同的 Principal | 同一 Facet 请求 | 各自请求 | 每人只见有权计数；`totalCount` 不泄漏无权资产数量 | `E3` |
| `AC-P0B-AUTH-003` | 同上 | 无可见资产的 Principal | 请求 Facet | 返回空维度与 `totalCount = 0`，不报错、不回退全量 | `E3` |
| `AC-DST-TAX-002` | 有 `asset:read` 的主体 | 存在 ARCHIVED 资产 | 请求默认 Facet | ARCHIVED 不进入计数；与 `search` 默认状态集合一致 | `E3` |
| 边界 | 审查者 | 非法或未知过滤值 | 请求 Facet | 返回稳定错误码，不静默返回空 Facet 冒充成功 | `E3` |

必须覆盖正常、边界、失败、越权。

## 5. 横切要求

| 关注点 | 本任务要求 |
| --- | --- |
| 认证与 Principal | N/A：不改变认证路径；端点继续要求已认证主体 |
| Permission/Scope/ACL | Facet 与 items 使用同一授权谓词；聚合查询不得放宽可见性 |
| 数据库权限下推 | `type`/`sensitivity`/`tagId`/分类维度与 AccessScope 一律在 SQL `WHERE` 下推 |
| 状态前置条件 | 沿用与 `search` 相同的状态集合（默认排除 ARCHIVED） |
| 幂等与并发 | N/A：只读聚合查询 |
| 事务与外部一致性 | N/A：不写业务库，不调用外部系统 |
| 错误码 | 非法过滤值保持既有稳定错误码；禁止空 Facet 冒充成功 |
| 审计事件 | N/A：沿用脱敏访问指标，不记录完整查询文本 |
| 通知 | N/A：不触发站内通知或 Webhook |
| 字典/标签/i18n | 过滤只接受稳定 `itemCode`/`tagId`，禁止展示名或自由值 |
| 配置 | N/A：不新增配置项 |
| 指标/Trace/告警 | N/A：不新增指标或告警 |
| Secret/日志脱敏 | N/A：不接触 Secret；证据不得包含凭据或预签名 URL |

## 6. 契约与数据先行

1. OpenAPI/MCP/Event 的具体变更：`GET /assets/facets` 需要声明与检索一致的受控过滤参数
   （至少 `type`、`sensitivity`、`tagId`），并说明「同一维度多值为 OR、不同维度为 AND」的聚合语义；
2. 示例请求、响应和错误：新增按 `type` 与 `sensitivity` 组合过滤的响应示例；
3. 新增 Flyway 文件：无（不新增维度，仅对齐既有过滤）；
4. 存量清点与回填：无数据变更；
5. 索引、唯一约束、外键及理由：无（沿用既有 `ix_asset_dataset_size_bucket`、
   `ix_asset_org_project_status` 等；本卡不新增聚合专用索引）；
6. 兼容窗口与失败恢复：新增参数均为可选，未传时行为与当前默认（全类型、全敏感度）一致；
   既有 `keyword`/`type` 调用方不受影响；
7. 生成类型更新：如前端存在 Facet 类型声明，需同步（本卡不接线前端，仅保证契约与类型不漂移）。

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

1. 更新 OpenAPI 的 `/assets/facets` 参数与描述（契约先行）；
2. 在 `AssetSearchDao` 抽取 `search` 与 `facet` 共享的过滤条件拼装，使两处不再各自漂移；
3. 扩展 `getAssetFacets` 签名与 `AssetController` 参数，使过滤条件完整下推；
4. 新增回归测试：对每个过滤维度断言「Facet 计数 == 同条件 search 的可达总数」；
5. 在真实 Compose 栈上以两个权限不同的主体验证授权隔离。

## 8. 验证命令

```text
cd backend; ./mvnw.cmd verify
docker compose -f deploy/compose/compose.yaml config --quiet
powershell -NoProfile -ExecutionPolicy Bypass -File ./deploy/compose/scripts/verify.ps1
```

每条命令声明：

- 预期退出码：全部 0；若本机 Docker 或 Testcontainers 不可用，依赖真实依赖的 IT 记为 SKIP
  并在 Evidence `limitations.skippedTests` 中列出，不得记为 PASS；
- 覆盖的 AC ID：`AC-P0B-AUTH-003`、`AC-DST-TAX-002`；
- 所需依赖：JDK 21、Maven Wrapper；E3 需要真实 PostgreSQL（Compose）；
- SKIP 判定：Docker 守护进程不可用时 E3 场景不可 SKIP 为 PASS，只能交接为 `IMPLEMENTED_UNVERIFIED`；
- 报告或产物路径：`backend/target/surefire-reports`、
  `docs/ai-spec/tasks/evidence/EVD-P0BR-049-001.yaml`。

路径触发强制命令：修改 backend 必须包含 Maven wrapper `verify`。

需要声明"完成"时还必须执行：

```powershell
./docs/ai-spec/tools/validate-task-card.ps1 -TaskPath docs/ai-spec/tasks/TASK-P0BR-049.md -CheckCompletion
```

## 9. 停止条件

AI 遇到以下情况必须停止受影响部分并报告：

- 规范事实冲突（例如 `AC-DST-TAX-002` 与现有 `AssetFacetView` 结构无法在不破坏兼容的前提下同时满足）；
- 存在未关闭的产品决策（例如 Facet 维度集合是否属 P1 范围）；
- 需要改变 ADR 固定的技术路线或事实源；
- 需要修改 V1/V2 或其他已应用 Migration；
- 需要降低权限、校验、约束或测试（例如为让计数一致而放宽授权谓词）；
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

# P1 可治理资产目录需求

> 状态：`OPEN`
> 阻塞：P0-B 必须 VERIFIED；Q-101..106、Q-201/202 需确认。
> 范围：MODEL、DATASET 的目录与 Gitea 仓库，不含 DVC 上传、发布审批和 MCP。
> 数据集专项：`dataset-experience.md` 中 `REQ-DST-TAX-001`、`REQ-DST-DETAIL-001`、
> `REQ-DST-DISC-001` 已由 `DEC-008` 确认为 MUST；本文件不得用通用 Asset 字段削弱其要求。

## REQ-AST-001 资产坐标、类型与责任模型

```yaml
status: OPEN
priority: MUST
phase: P1
blockedBy: [Q-101, Q-103, Q-104, Q-201, Q-202]
minimumEvidenceLevel: E3
```

### 建议模型

- 稳定 assetId 与人类坐标 `aih://{namespace}/{type}/{name}` 分离；
- type 首期仅 MODEL/DATASET；
- 每个 Asset 属于一个 Organization 和一个 Project（是否允许组织级无 Project 资产待确认）；
- namespace 的所有权、格式、唯一边界待确认，建议映射 Project code 或受控 Namespace 资源；
- name 规范化规则必须固定，拒绝路径分隔、控制字符、歧义 Unicode 和保留词；
- `(namespace,type,name)` 唯一；
- 至少一个有效 Team Owner，个人 Principal 作为 Maintainer（建议）；
- 重命名不改变 assetId，旧坐标进入永久 Alias，不能被另一资产复用；
- AssetStatus 与 VersionStatus 分离；
- MODEL/DATASET 共享核心字段，扩展字段有类型专属 Schema。

### MODEL 字段

framework/task/license/sensitivity 等使用 itemCode；architecture、parameterScale、precision、weightFormat、
runtime、knownRisks、usageRestrictions 的必填与格式需形成字段表。

### DATASET 字段

按 `REQ-DST-TAX-001` 使用 `taskCodes/modalityCodes/formatCodes/languageCodes/licenseCode/
sensitivityCode/sampleCount/totalBytes/sizeBucketCode/tagIds`。Split、Schema、来源、脱敏与质量属于
精确 Version 或 Card 投影；不得继续以自由 `format/modality` 字符串作为最终契约。

### 追踪

- Invariants：`INV-AST-001..009`
- Pages：`PAGE-AST-001..006`
- Journey：`JRN-P1-001..004`

## REQ-AST-002 创建资产与仓库开通

```yaml
status: OPEN
priority: MUST
phase: P1
actor: 资产维护者或显式授权 Agent
permission: asset:create
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 前置条件

- Principal、Organization、Project ACTIVE；
- Principal 是 Project 成员并具有 `asset:create`；
- Owner/Team ACTIVE 且属于目标 Organization；
- 所有 itemCode/tagId ACTIVE 且适用于目标作用域；
- 坐标和 Gitea repo name 合法、未占用；
- Agent 还需 Scope、Tool（未来 MCP）与默认写开关允许。

### 行为

1. 接受类型化 CreateAssetRequest，不接受自由 Owner/tag/治理文案；
2. 以 Principal+path+request digest 处理 Idempotency-Key；
3. 预留 assetId/坐标和 Asset Provision Saga；
4. 事务提交后持久化 Worker 创建 Gitea 仓库；
5. 写入与请求一致的初始 `asset.yaml`、README 模板和必要保护/Webhook；
6. 核验仓库 ID、默认分支、初始 Commit 和策略；
7. 标记 Provision 完成后才进入普通资产搜索；
8. 返回可查询 provisioning 状态，不能让 HTTP 长时间等待 Git 操作；
9. 创建、失败、重试、补偿均审计和可观测。

### 错误

| 条件 | 错误 |
| --- | --- |
| 坐标已存在 | `ASSET_ALREADY_EXISTS` |
| Owner/治理引用非法 | `PRINCIPAL_NOT_FOUND` / `DICTIONARY_VALUE_INVALID` / `TAG_VALUE_INVALID` |
| 无权或跨作用域 | `AUTH_PERMISSION_DENIED` |
| Gitea 暂时不可用 | 接受意图后返回任务状态，或同步失败 `ASSET_REPOSITORY_PROVISION_FAILED`；必须择一 |
| 同键不同请求 | `IDEMPOTENCY_KEY_CONFLICT` |

### Saga 与审计

复用 `consistency-and-compensation.md` A1-A6；事件至少为
`ASSET_CREATE_REQUESTED/REPOSITORY_PROVISIONED/ASSET_CREATED/ASSET_CREATE_FAILED`。当前 Event Schema 需补。

### 追踪

- Acceptance：需从 `JRN-P1-001` 生成正常、重放、Gitea 失败、超时后实际成功、越权、治理非法场景
- Page：`PAGE-AST-002`
- NFR：`NFR-REL-003/005`、`NFR-MNT-002`

## REQ-AST-003 权限过滤的资产搜索

```yaml
status: PROPOSED
priority: MUST
phase: P1
actor: 任意已认证且状态正常 Principal
permission: asset:read
idempotency: NOT_REQUIRED
minimumEvidenceLevel: E4/E5
```

### 查询

支持 keyword、type、namespace、organizationId、projectId、ownerId/teamId、visibility、status、
license/framework/task/format/modality itemCode、tagId、updated/published time（阶段适用）和受控 sort。
DATASET 还必须完整实现 `REQ-DST-TAX-001` 的多值分类、权限过滤 Facet、匹配字段和维度组合语义。

### 行为

- SQL 同时应用 Role/Scope/Membership/ACL/Visibility/Sensitivity/状态过滤；
- PRIVATE 仅 Owner/明确授权，INTERNAL 的组织边界需确认，PUBLIC 仅全平台已认证主体；
- Provision 未完成、软删除和 ARCHIVED 默认不返回；
- DEPRECATED 默认可返回但降权/显式标识；
- P1 没有 Published Version 时，是否向普通 Reader 展示 ACTIVE 草稿资产需确认，建议只向维护协作者展示；
- keyword 只查有权限投影，facet/count/autocomplete 也不能泄露；
- Cursor 绑定 filter/sort/Principal scope，默认 20、最大 100；
- 返回小型 AssetSummary，不返回完整 Card/文件；
- tag 查询只用 tagId；
- DATASET 的 Facet/count/autocomplete 与 items 使用同一授权 Predicate，禁止因聚合查询泄漏；
- 达到 10 万数据时 P95 ≤ 500 ms 的数据分布和并发按 NFR 执行。

### 错误

- 非法 sort/filter/cursor：`COMMON_INVALID_ARGUMENT`；
- 无 read Permission：`AUTH_PERMISSION_DENIED`；
- 数据库/授权计算失败：fail closed，不返回宽松结果。

### 追踪

- Invariants：`INV-AST-006..010`、`INV-AUTH-007`
- Journey：`JRN-P1-002`
- Page：`PAGE-AST-001`
- NFR：`NFR-PERF-001`、`NFR-SEC-005`

## REQ-AST-004 资产详情与 Card 投影

```yaml
status: PROPOSED
priority: MUST
phase: P1
permission: asset:read
minimumEvidenceLevel: E4
```

### 行为

- 详情加载使用与搜索一致的授权 Predicate；
- 私有资产无权与不存在均返回 `ASSET_NOT_FOUND`；
- 返回稳定 assetId、当前坐标/aliases、类型、作用域、Owner/Maintainer、visibility/status、治理字段、
  Tag metadata、Gitea 仓库受控引用、sourceCommit/投影时间；
- Card 从锁定 Commit 读取/投影，Markdown 作为不可信内容安全渲染；
- 外链、图片、HTML、脚本和 Prompt Injection 按内容安全策略处理；
- DISABLED item/tag 仍显示 code/历史文案/停用标记；
- P1 不伪造 latestPublished 或 Version 列表；
- DATASET 详情外壳遵守 `REQ-DST-DETAIL-001`；P2/P3 未交付的 Files/Preview/Version 不展示可点击
  Placeholder；
- Gitea 暂时不可用时可返回已标注时间的最后投影，是否允许实时 Card 失败降级需确认；
- 不返回 Gitea Service Token、内部 clone credential 或 MinIO 信息。

### 追踪

- Journey：`JRN-P1-002`
- Pages：`PAGE-AST-003`
- NFR：`NFR-SEC-005`、Card 不可信内容安全

## REQ-AST-005 更新资产元数据与卡片

```yaml
status: OPEN
priority: MUST
phase: P1
actor: Owner/Maintainer
permission: asset:update
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- Request 携带 expected rowVersion 和目标 sourceCommit/etag；
- 重新校验 Owner、itemCode、tagId、作用域和状态；
- 不能通过更新改变 assetId/type/Organization；Project 移动是否允许待确认；
- 重命名规则受 Q-202，需原子预留新坐标并保留 Alias；
- README/asset.yaml 与 PostgreSQL 投影通过 Saga/Job 更新，禁止事务内 Git Push；
- `asset.yaml` 是机器事实，更新后解析结果必须与请求一致；
- Gitea 分支在基线 Commit 后变化时返回冲突，不强推覆盖；
- 两个并发更新只允许一个基于相同 rowVersion/Commit 成功；
- 已发布 Version 不因资产目录更新改变其 Commit/Card；
- 完成后审计 before/after 的受控摘要，不记录完整敏感 Card。

### 错误

`ASSET_NOT_FOUND/CONCURRENT_MODIFICATION/DICTIONARY_VALUE_INVALID/TAG_VALUE_INVALID/
GITEA_DEPENDENCY_UNAVAILABLE`。

### 追踪

- Journey：`JRN-P1-003`
- Page：`PAGE-AST-004`
- Invariants：`INV-AST-*`、`INV-COM-003`

## REQ-AST-006 Owner、Maintainer 与资产 ACL

```yaml
status: OPEN
priority: MUST
phase: P1
blockedBy: [Q-103, Q-104, final permission matrix]
minimumEvidenceLevel: E4
```

### 建议行为

- Owner 是正式 Team 引用且至少一个；
- Maintainer 是 Principal/Team 的 Role Binding，不与 Owner 字段重复表达权限；
- 更换 Owner 需要现 Owner 或组织管理员，并验证新 Owner 接受/有效（是否需要接受流程待确认）；
- 不能移除最后 Owner；
- 资产 ACL 只授予中央 Permission Catalog 中适用于 ASSET 的权限；
- ACL 不能突破 Scope/Sensitivity/Version 状态；
- Team 成员变化实时影响权限；
- Owner/ACL 列表对有权主体可解释“权限来源”，不只返回原始记录；
- 所有变更审计并按策略通知受影响主体。

### 追踪

- Invariants：`INV-TEAM-*`、`INV-AUTH-*`、`INV-AST-004/005`
- Acceptance：从 `AC-P0B-AUTH-005/009` 扩展资产场景
- Page：`PAGE-AST-005`

## REQ-AST-007 弃用、归档、恢复与删除

```yaml
status: OPEN
priority: MUST
phase: P1/P3
blockedBy: [Q-206]
permissions: [asset:deprecate, asset:delete]
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- ACTIVE→DEPRECATED 需要 deprecation_reason itemCode、说明、可选替代 asset/version；
- DEPRECATED 在搜索中显式标记并降权，不自动失去访问；
- ACTIVE/DEPRECATED→ARCHIVED 前检查发布版本、下游关系、任务和保留策略；
- ARCHIVED 默认搜索隐藏，有权管理员可查；
- 恢复只改变目录状态，不修改任何已发布版本；
- DELETE API 的外部语义必须明确：建议仅软删除/归档，正式资产无直接物理删除；
- MinIO/DVC/Gitea 物理回收为独立持久化 Job，经过引用计数、保留期、二次确认和审计；
- 越权访问继续防枚举；
- 高风险动作不默认授予 Agent。

### 追踪

- State：AssetStatus
- Journey：`JRN-P1-004`、后续 `JRN-P3-003`
- Page：`PAGE-AST-004`

## REQ-AST-008 Gitea 仓库映射、权限投影与对账

```yaml
status: PROPOSED
priority: MUST
phase: P1
minimumEvidenceLevel: E4
```

### 行为

- 每个 Asset 精确映射一个 Gitea repoId/repo owner/name；
- Gitea 保存 README、asset.yaml、配置、脚本和 DVC 指针；
- PostgreSQL 不存 Card 的独立权威副本，只存带 sourceCommit 的投影；
- Gitea 权限由业务授权单向投影；
- 外部手工权限变化不反向修改业务 Role/ACL；
- Webhook 使用签名 Inbox，重复 delivery 幂等；
- Reconciler 检查 repo 存在、坐标/assetId 标记、默认分支、Card/Manifest、Webhook、保护和权限；
- 可确定漂移自动修复并审计，归属/内容冲突进入 MANUAL_REVIEW；
- Noop Provisioner 不进入可部署 Profile；
- Gitea 不可用时创建/更新用例保留可重试意图，不返回伪成功。

### 追踪

- Consistency：资产创建 Saga、Webhook Inbox、权限投影/Reconciliation
- Invariants：`INV-AST-009/010`
- Journey：`JRN-P1-001/003`

## REQ-AST-009 资产目录前端

```yaml
status: OPEN
priority: MUST
phase: P1
blockedBy: [REQ-AST-001, REQ-AST-006]
minimumEvidenceLevel: E4
```

### 行为

- 实现 `PAGE-AST-001..006` 的 P1 范围；
- 创建/编辑由共享核心字段+MODEL/DATASET 扩展 Schema 驱动；
- Organization/Project/Owner/Maintainer/Dictionary/Tag 均受控选择；
- 列表筛选同步 URL，保留返回位置；
- Provisioning/失败/重试状态可见，不显示伪造 repo；
- 权限按钮同时考虑全局 Permission 与资源能力；
- Card 安全渲染；
- 所有页面具备完整异步状态、冲突恢复、zh/en、键盘/焦点基础；
- access Token/内部 Git URL/凭据不持久化或上报；
- 组件测试、权限测试和两主体浏览器 E2E。

### 追踪

- Journey：`JRN-P1-001..004`
- Pages：`PAGE-AST-001..006`
- Acceptance：在 P1 验收目录中生成，不复用 P0-B UI 场景冒充

## P1 出口

P1 只有在以下结果同时可复现时退出：

1. 两类资产可用受控字段创建；
2. Gitea 仓库/初始 Commit 与 DB 投影一致；
3. 相同 Idempotency-Key 重放不重复建仓；
4. Gitea 在每个关键窗口失败/超时后可恢复或进入人工状态；
5. 至少两个 Organization、三个权限主体的列表和详情无泄漏；
6. 更新并发冲突不覆盖；
7. Owner/Team、字典、标签停用的历史/新写语义正确；
8. 弃用/归档/恢复不修改版本事实；
9. 数据集分类/Facet、Dataset Card 和 Discussion 按 `AC-DST-TAX-*`、`AC-DST-DETAIL-*`、
   `AC-DST-DISC-*` 完成 E4；
10. UI 完整状态和两语言 E4；
11. OpenAPI、V14+、事件、类型、Fixture、Runbook、Evidence 同步。

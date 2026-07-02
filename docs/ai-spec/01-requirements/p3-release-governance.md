# P3 发布治理需求

> 状态：`OPEN`
> 阻塞：P2 VERIFIED；Q-203..206。

## REQ-VAL-001 发布前完整性与治理校验

```yaml
status: OPEN
priority: MUST
phase: P3
permission: asset:submit
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 校验输入冻结

versionId、sourceCommit、asset.yaml/README digest、Manifest digest、Artifact 清单、治理字段、策略版本。

### 校验规则

- Card/asset.yaml/PG 投影的 assetId/坐标/type/Owner/治理字段一致；
- Manifest Schema/规范化/digest 正确；
- 每个 Artifact path/size/SHA-256/DVC hash/media type 完整；
- 正式 DVC 对象存在且 metadata 匹配；
- License/sensitivity/usage restrictions/known risk 已声明；
- MODEL/DATASET 类型必填字段完整；
- 高风险 Pickle/动态代码/未知可执行文件有风险标记和审批要求；
- README/Card 作为不可信内容扫描；
- 依赖/血缘引用精确、存在、有权且无禁止环；
- Gitea Commit 可达且不是已发布内容的非法覆盖；
- 策略失败返回结构化 ruleId/severity/i18nKey/resource path，不仅一段文本。

### 行为

- validate 动作将 DRAFT 置 VALIDATING 并创建持久化 Job；
- 重放返回同一校验 Job/报告；
- 校验通过保存不可变报告和 policyVersion，进入 PENDING_REVIEW；
- 失败回 DRAFT 并保留报告；
- sourceCommit 变化使旧报告失效，必须重新校验；
- 报告不包含敏感样本、Token 或内部路径。

### 追踪

- State：DRAFT→VALIDATING→DRAFT/PENDING_REVIEW
- Journey：`JRN-P3-001`

## REQ-REV-001 提交、审批、驳回与职责分离

```yaml
status: OPEN
priority: MUST
phase: P3
permissions: [asset:submit, asset:review]
blockedBy: [Q-203, Q-204]
minimumEvidenceLevel: E4
```

### 建议行为

- submit 只接受最新通过且 sourceCommit 未变化的校验报告；
- 创建 PublishRequest，冻结 version/sourceCommit/manifestDigest/policyVersion；
- 根据 License/Sensitivity/Risk 决定需要的审核角色/数量；
- 默认提交人不能审批自己的版本；
- Reviewer 必须在目标 Organization/Project 有权限且满足敏感等级；
- approve/reject 使用 expected request version，重复相同决定幂等；
- reject 意见必填，版本回 DRAFT；内容/Commit 改变后旧审批无效；
- approve 记录结构化检查项和意见；
- 所需审批全部完成后创建唯一 Publish Saga；
- 审批权限被撤销或策略变化时未完成审批重新评估；
- 管理员紧急越权若允许，必须填写原因、二次确认、专用高风险审计和通知。

### 错误与审计

需补 PublishRequest 错误 Catalog；`VERSION_STATE_NOT_ALLOWED` 不能承担所有细节。
事件：`VERSION_REVIEW_REQUESTED/APPROVED/REJECTED` 和高风险 override。

### 追踪

- State：PENDING_REVIEW→DRAFT/Publish Saga
- Pages：`PAGE-REV-001/002`
- Journey：`JRN-P3-001`

## REQ-PUB-001 不可变发布 Saga

```yaml
status: PROPOSED
priority: MUST
phase: P3
permission: asset:publish
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- 仅由已满足审批的 PublishRequest 触发；
- 再次确认 Principal/权限/策略/冻结 revision；
- 重新验证 Commit、Manifest、DVC 对象；
- 在 Gitea 创建受保护 Tag，Tag 命名与用户 version 映射规则固定；
- 已存在且指向同 Commit/同 Manifest 视为幂等；
- 已存在但指向不同 Commit 永不覆盖，进入 MANUAL_REVIEW；
- 验证 Tag 保护策略和服务账号独占发布；
- PG 单事务保存 PUBLISHED、tag、commitSha、manifestDigest、publishedAt/By，并写 Outbox；
- PUBLISHED 结论对外可见前三元组必须完整；
- 通知/搜索/统计异步失败不回滚发布；
- P4 Tool 默认不允许 Agent 发布。

### 故障处理

- 每个 P1-P7 步骤前后注入失败；
- Tag 成功但 PG 失败：重试/对账完成 PG，不盲删 Tag；
- PG PUBLISHED 但外部事实缺失：停止新下载票据、CRITICAL 告警、人工处置；
- 不确定超时先读 Gitea 核验，不能直接创建第二 Tag；
- Saga/Job 可查询、重试、补偿并完整审计。

### 错误与审计

`VERSION_STATE_NOT_ALLOWED/DVC_OBJECT_MISSING/GITEA_DEPENDENCY_UNAVAILABLE`，Tag 冲突建议专用错误；
`VERSION_PUBLISHED` 仅在 PG 结论提交后记录 SUCCEEDED。

### 追踪

- Consistency：P1-P7
- Journey：`JRN-P3-001/002`
- NFR：`NFR-REL-001..005`

## REQ-IMM-001 已发布版本不可变与完整标识

```yaml
status: PROPOSED
priority: MUST
phase: P3
minimumEvidenceLevel: E4
```

### 不变量

- Published 的 version/tag/commitSha/manifestDigest/Artifact 集合/校验报告不可 UPDATE；
- 不提供覆盖 Tag、重用 version、替换对象或改写 Manifest 的业务 API；
- Gitea 保护规则阻止非发布服务账号覆盖/删除 Tag；
- Webhook/Reconciler 检测手工修改/删除并产生 CRITICAL 安全事件；
- 对外 VersionView 和下载票据始终返回三元组；
- Card 的后续默认分支修改不改变历史版本 Card；
- PostgreSQL 触发器/约束或 Repository 设计阻止意外更新，不能只靠 UI；
- 修复发布错误必须创建新版本；外部事实恢复仅恢复到原摘要；
- 备份恢复随机抽样验证三元组和 SHA-256。

### 验收

- 通过 REST、Mapper/Repository、直接受限 DB 用户、Gitea API 分别尝试覆盖；
- 模拟 Tag 删除/改指向，Reconciler 告警且下载受控；
- 重放发布不产生第二记录/事件。

### 追踪

- Invariants：`INV-VER-001..009`
- Acceptance：设计 V08/V09 的严格扩展

## REQ-DEP-001 版本弃用与归档

```yaml
status: OPEN
priority: MUST
phase: P3
permission: asset:deprecate
idempotency: REQUIRED
blockedBy: [Q-206]
minimumEvidenceLevel: E4
```

### 行为

- PUBLISHED→DEPRECATED 要求有效 deprecation_reason、说明和可选替代版本；
- 不修改 Tag/Commit/Manifest/Artifact；
- 默认搜索降权并显式警告；
- 已授权用户仍可下载，除非独立安全吊销策略明确；
- 通知 Owner、Maintainer、已知订阅者/依赖方；
- DEPRECATED→ARCHIVED 前检查下游引用和保留期；
- ARCHIVED 默认不搜索、不签普通下载票据，管理员恢复/特殊访问策略待确认；
- 物理对象回收与状态转换分离；
- 状态、原因、替代版本和通知审计。

### 追踪

- State：PUBLISHED→DEPRECATED→ARCHIVED
- Journey：`JRN-P3-003`

## REQ-REV-002 版本与审批前端

```yaml
status: OPEN
priority: MUST
phase: P3
blockedBy: [Q-203, Q-204]
minimumEvidenceLevel: E4
```

- 实现 `PAGE-VER-001/002` 和 `PAGE-REV-001/002`；
- 版本列表明确 DRAFT/VALIDATING/PENDING_REVIEW/PUBLISHED/DEPRECATED/ARCHIVED；
- 详情展示精确 Commit/Tag/Manifest、Artifact、校验规则和报告版本；
- validate/submit/approve/reject/publish/deprecate 仅在权限+状态允许时出现；
- 审批页显示冻结 Commit 和与当前分支差异，防止审错版本；
- 风险、License、Sensitivity、文件类型和内容安全结果可审阅；
- 驳回意见、审批确认、高风险 override 规则；
- Saga 进度/重试/人工处置可观测但不泄露内部凭据；
- Published 页面没有编辑/覆盖内容入口；
- 两语言、完整页面状态、两角色 E2E 和视觉证据。

## P3 出口

1. 从 DRAFT 到 PUBLISHED 的正常闭环；
2. 校验失败、驳回、内容漂移后旧审批失效；
3. 提交人自审/越权发布拒绝；
4. Tag+Commit+Digest 100% 完整；
5. 每个 Saga 故障窗口恢复且无“DB 发布、Tag 不存在”；
6. 覆盖 Tag/修改 Published 通过各入口均失败并审计；
7. DEPRECATED/ARCHIVED 语义不改变内容事实；
8. UI、通知、审计、Trace、Runbook、E4 全部同步。

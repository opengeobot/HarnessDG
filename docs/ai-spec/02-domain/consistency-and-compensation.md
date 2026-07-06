# 跨系统一致性、补偿与对账

> 状态：`READY`
> 固定边界：PostgreSQL 事务不能覆盖 Gitea、DVC 或 MinIO。

## 1. 总体模式

```text
在线请求
  → PostgreSQL 记录意图/状态 + Outbox/Job（单本地事务）
  → 提交
  → Worker 领取持久化 Job
  → 调用 Gitea/MinIO/DVC（每步带幂等键）
  → PostgreSQL 记录步骤结果
  → 成功推进业务状态，失败重试/补偿
  → Reconciler 周期比较权威事实源
```

禁止：

- 在数据库长事务内 Git Clone/Push、DVC、Multipart Copy 或远程重试；
- 事务提交前启动依赖该数据的线程；
- 用内存 Future/`@Async` 承担可靠工作；
- 远程失败后返回成功并仅写日志；
- 把 Noop 外部调用的确定性假值写成生产事实。

## 2. Saga 公共模型

建议所有跨系统流程使用统一可查询模型：

```yaml
sagaId: sag_...
sagaType: ASSET_PROVISION | UPLOAD_MATERIALIZE | VERSION_PUBLISH | ...
aggregateType: ASSET | VERSION | UPLOAD_SESSION
aggregateId: ...
state: PENDING | RUNNING | RETRY_WAIT | COMPENSATING | SUCCEEDED | FAILED | MANUAL_REVIEW
currentStep: ...
idempotencyKey: ...
requestedBy: prn_...
traceId: ...
attempts: ...
nextRunAt: ...
lastErrorCode: ...
rowVersion: ...
steps:
  - name: ...
    state: PENDING | SUCCEEDED | FAILED | COMPENSATED
    externalReference: ...
    requestDigest: ...
```

Saga/Step 状态是否复用 `job_task` 还是使用独立表需在具体阶段决定，但上述可观察信息不可丢失。

## 3. 资产创建与 Gitea 建仓 Saga

### 3.1 目标不变量

- 用户只收到一个 assetId；
- 重放不创建第二个 DB 资产或 Gitea 仓库；
- 普通搜索只展示已完成建仓的资产；
- DB 与 Gitea 任一侧失败都有可恢复状态；
- 仓库包含与 assetId/坐标匹配的初始 `asset.yaml`/README；
- Gitea Token 和内部 URL 不进入响应/日志。

### 3.2 建议步骤

| Step | 动作 | 幂等键/核验 | 失败处理 |
| --- | --- | --- | --- |
| A1 | 在 PG 预留 assetId、坐标和建仓意图 | `(org,type,name)` + 请求幂等键 | 唯一冲突直接失败 |
| A2 | 创建/确认 Gitea Organization 映射 | organizationId | 不自动创建未知外部组织，进入人工/重试 |
| A3 | 创建仓库 | assetId 作为外部标记/Topic | 已存在且归属一致视为幂等；冲突进入 MANUAL_REVIEW |
| A4 | 提交初始卡片/Manifest | content digest | 失败重试；不得把空仓库标成 READY |
| A5 | 配置保护、Webhook 和权限投影 | repoId + policy version | 可重试；部分成功可对账 |
| A6 | PG 标记建仓完成并发布 Outbox | 校验 repo URL/id/commit | 与搜索可见状态同事务 |

### 3.3 补偿

- A3 后 A4/A5 永久失败：优先保留隔离仓库并标记 orphan，等待人工/对账；不盲目删除可能含用户内容的仓库；
- PG 提交前远程仓库已创建：Reconciler 通过 assetId 标记发现并接管或隔离；
- 用户取消且仓库仍为空：经过引用/内容校验后可删除，必须审计；
- 不允许 Noop Provisioner 生成看似真实的 repo URL 进入可部署 Profile。

## 4. Web Upload Materialization Saga

| Step | 动作 | 权威事实/校验 | 失败处理 |
| --- | --- | --- | --- |
| U1 | 创建 Upload Session 和 Multipart | PG session + MinIO uploadId | 同幂等请求返回原会话 |
| U2 | 客户端直传 Parts | MinIO | Part 大小/编号/ETag 受控 |
| U3 | complete 请求冻结 Part 清单 | PG expected parts | 重放返回同一 Job |
| U4 | MinIO 完成暂存对象 | MinIO object metadata | 失败可重试，不推进 PROCESSING |
| U5 | Worker 流式校验路径/大小/SHA-256/内容策略 | 校验报告 | 不可恢复错误→FAILED |
| U6 | DVC add/push 到正式 Remote | DVC hash + MinIO 正式对象 | 内容寻址使重放幂等 |
| U7 | 生成 Manifest/.dvc 并 Git commit/push | Gitea commit SHA | 远程冲突需重新基于锁定基线 |
| U8 | PG 保存草稿版本投影并完成 Session | commit/digest/artifacts | 与完成事件同事务 |
| U9 | 清理 staging | 生命周期/Job | 失败不回滚完成结果，但告警/重试 |

暂存对象永远不能直接成为 Published Artifact。

## 5. Version Publish Saga

### 5.1 发布前冻结

开始 Saga 前在 PG 锁定：

- versionId 和用户可读 version；
- sourceCommit；
- 预期 asset.yaml/README digest；
- Manifest digest 和 Artifact 清单；
- 校验报告版本；
- 审批决定、审批人和策略版本。

后续仓库出现新 Commit 不改变本次待发布 revision。

### 5.2 步骤

| Step | 动作 | 成功证据 | 失败/补偿 |
| --- | --- | --- | --- |
| P1 | 重新确认审批和权限/策略 | policy decision digest | 权限撤销/策略变化则停止 |
| P2 | 校验 Gitea Commit/卡片一致 | commit SHA、content digest | 不一致回 DRAFT/需重校验 |
| P3 | 校验每个 DVC/MinIO 对象 | DVC hash、SHA-256、size | 缺失→DVC_OBJECT_MISSING |
| P4 | 创建受保护 Tag | tag→精确 commit | 已存在同指向视为幂等；不同指向进入 MANUAL_REVIEW |
| P5 | 验证 Tag 保护和不可覆盖 | Gitea policy/result | 失败不能发布 |
| P6 | PG 标记 PUBLISHED，写 Version 三元组和 Outbox | 单 PG 事务 | 事务失败后由对账发现已存在 Tag |
| P7 | 异步搜索/通知/统计 | Outbox deliveries | 失败不回滚发布，持续重试 |

### 5.3 关键补偿

- P4 成功、P6 失败：不立即删除 Tag；重试 P6 并由 Reconciler 核验冻结证据；
- Tag 已存在且指向不同 Commit：绝不覆盖，进入人工处置和安全审计；
- PG 显示 PUBLISHED 但 Tag/对象缺失：严重一致性告警；停止新下载票据，进入 MANUAL_REVIEW；
- PUBLISHED 后禁止以补偿名义修改三元组，修复必须创建新版本或恢复外部事实。

## 6. Gitea Webhook Inbox

```text
验证签名/来源/大小
→ INSERT webhook_inbox(deliveryId) ON CONFLICT
→ 快速返回
→ Worker 解析事件版本
→ 查询 Gitea 当前事实
→ 幂等更新 PG 投影
→ 记录 processed/failed/dead
```

- 不信任 Webhook Payload 是最新事实，消费时按需要回查 Gitea；
- 重复 deliveryId 返回首次接收结果；
- 未知事件版本进入可观测失败，不能静默丢弃；
- 删除/覆盖正式 Tag 事件触发高优先级一致性检查和安全审计；
- Webhook 只做增量，不能替代全量 Reconciler。

## 7. Notification Outbox

- 业务状态、站内 Notification 和 Outbox Event 在同一 PG 事务写入；
- Dispatcher 领取事件必须支持并发和 lease；
- 每个外发目标生成稳定 Delivery ID；
- HTTP 请求只在事务外执行；
- retryable 分类、退避和最大次数由类型化配置控制；
- DEAD 产生运维通知/告警，但不得递归制造无限通知；
- Outbox processed 的定义必须是“所有要求的渠道已进入确定终态”或使用每渠道独立状态，不能首个渠道成功就完成。

## 8. 权限投影与 Gitea 对账

PostgreSQL 是业务授权事实源，Gitea 权限是单向投影：

1. 角色/成员/ACL 变更提交 Outbox；
2. Worker 计算目标 Gitea Team/Repo 权限；
3. 使用最小服务账号写入；
4. 保存 projection version/digest；
5. Reconciler 定期读取 Gitea 并比较；
6. 外部手工扩大权限默认收敛回目标并审计；
7. 无法安全自动修复的差异进入 MANUAL_REVIEW。

禁止把 Gitea 当前权限反向合并成业务授权。

## 9. Reconciliation

| 对账器 | 比较 | 自动修复 | 必须人工 |
| --- | --- | --- | --- |
| Asset Repository | PG asset ↔ Gitea repo/card | 缺失 Webhook、可确定的投影字段 | 仓库归属冲突、未知内容 |
| Published Version | PG version ↔ Tag/Commit/Manifest/DVC | 重建查询投影/索引 | Tag 指向冲突、正式对象缺失 |
| Authorization Projection | PG role/ACL ↔ Gitea permission | 移除/恢复确定权限 | 外部所有者/管理员冲突 |
| Staging Lifecycle | PG upload ↔ MinIO staging | 清理过期无引用对象 | 无法判断引用的对象 |
| Job Lease | RUNNING lease ↔ Worker heartbeat | 回收过期租约 | 非幂等 Handler 的不确定结果 |
| Outbox/Delivery | event ↔ channel delivery | 重试缺失投递 | 外部系统返回不确定结果 |

每次修复记录 before/after、规则版本、主体（SYSTEM/运维）、traceId 和审计事件。

## 10. 故障注入验收

每个 Saga 至少自动覆盖：

1. 每个远程步骤前失败；
2. 远程成功但本地确认前进程退出；
3. 相同任务/事件重复执行；
4. 两个 Worker 并发执行；
5. 超时后远程实际已成功；
6. 非 retryable 冲突；
7. Reconciler 发现并自动修复；
8. 无法修复时进入 MANUAL_REVIEW 且告警。

只测试“依赖返回 500 后重试”不足以证明跨系统一致性。

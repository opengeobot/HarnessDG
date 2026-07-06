# 稳定状态机

> 状态：`READY`
> 规则：每个转换必须在 Java Enum/Domain、DB CHECK、OpenAPI/事件、前端生成类型和测试中一致。

## 1. 状态机通用规则

1. API 使用动作端点表达转换，不允许直接 PATCH status；
2. 非表内转换统一返回稳定的 `*_STATE_NOT_ALLOWED`；
3. 每次转换检查 Principal、Permission、Scope、资源状态和 expected rowVersion；
4. 重复相同动作要么幂等返回原结果，要么返回明确冲突，不产生第二次副作用；
5. 状态和 Outbox/Audit 在同一 PostgreSQL 事务提交；
6. 跨系统步骤由 Saga/Job 驱动，不把外部调用包进长事务；
7. 终态不可逆，除非规格明确恢复动作和审计。

## 2. UserStatus

```text
PENDING_ACTIVATION → ACTIVE → LOCKED
        │              │        │
        └──────────────┴────────┴→ DISABLED
                                       │
                                       └→ PENDING_ACTIVATION 或 ACTIVE（待确认）
LOCKED → ACTIVE（管理员解锁或锁定到期，待确认）
```

| 当前 | 动作 | 目标 | 前置/副作用 |
| --- | --- | --- | --- |
| 无 | create user/bootstrap | PENDING_ACTIVATION | 临时密码；force change；USER_CREATED |
| PENDING_ACTIVATION | change password | ACTIVE | tokenVersion 递增；临时会话轮换；USER_PASSWORD_CHANGED |
| ACTIVE/PENDING_ACTIVATION | failed login 达阈值 | LOCKED | AUTH_LOGIN_FAILED/DENIED；安全指标 |
| LOCKED | unlock/expiry | ACTIVE | 解锁方式和审计事件待确认 |
| 任意非 DISABLED | disable | DISABLED | tokenVersion 递增、Family 吊销、USER_DISABLED |
| DISABLED | enable | 待确认 | 是否重新强制改密由启用原因决定 |
| ACTIVE/LOCKED | reset password | PENDING_ACTIVATION | 临时密码、强制改密、全部 Token 吊销 |

`DISABLED` 不能登录、刷新或访问资源。不存在硬删除用户路径。

## 3. AgentStatus 与 PrincipalStatus

| 当前 | 动作 | 目标 | 副作用 |
| --- | --- | --- | --- |
| 无 | register | ACTIVE | 一次性 credential；最小 Scope/Tool；AGENT_REGISTERED |
| ACTIVE | disable | DISABLED | tokenVersion 递增、凭据/Token 吊销、AGENT_DISABLED |
| DISABLED | enable | ACTIVE | 是否轮换 credential 待确认；AGENT_ENABLED |

Principal 的聚合状态必须与 User/Agent 状态一致，不允许扩展表 ACTIVE 而 Principal DISABLED 后仍签发 Token。

## 4. RefreshTokenStatus

```text
ACTIVE ──成功刷新──> ROTATED
   ├──登出/禁用/改密/重放──> REVOKED
   └──到期──> EXPIRED
```

- 只有 ACTIVE 可刷新；
- ROTATED jti 再次使用触发 Family 全部 REVOKED；
- EXPIRED/REVOKED 重用统一 fail closed，但重放告警语义需区分；
- ROTATED/REVOKED/EXPIRED 不回到 ACTIVE；
- 并发刷新必须通过原子 compare-and-set 保证单成功。

## 5. Organization/Project/Role/Dictionary/Tag

这些治理对象统一使用：

```text
ACTIVE ↔ DISABLED
```

但“启用”不是简单翻转：

- 所属上级必须 ACTIVE；
- code 唯一和引用仍合法；
- 启停使用 expected rowVersion；
- DISABLED 保留历史引用但禁止新引用/授权；
- 是否允许删除只由独立引用检查动作决定；
- 启停均审计。

## 6. AssetStatus

当前 V2 状态：

```text
ACTIVE → DEPRECATED → ARCHIVED
   └────────────────→ ARCHIVED
DEPRECATED → ACTIVE（是否允许恢复待确认）
ARCHIVED → ACTIVE/DEPRECATED（仅恢复元数据，待确认）
```

此状态描述资产目录生命周期，不描述仓库创建或版本发布。建议新增独立 provisioning/saga 状态，而不是
把 PROVISIONING/FAILED 塞入 AssetStatus；该决定需形成契约和 Migration。

| 转换 | 规则 |
| --- | --- |
| ACTIVE→DEPRECATED | 需要原因 itemCode、替代资产/版本（可选）、通知和审计 |
| ACTIVE/DEPRECATED→ARCHIVED | 检查活跃发布/下游引用；默认搜索隐藏 |
| 恢复 | 只恢复目录元数据，不修改任何已发布 Version |

## 7. VersionStatus

设计规定：

```text
DRAFT → VALIDATING → PENDING_REVIEW → PUBLISHED → DEPRECATED → ARCHIVED
  ↑         │              │
  └─────────┘              └──驳回──> DRAFT
```

| 当前 | 动作 | 目标 | 关键不变量 |
| --- | --- | --- | --- |
| 无 | create version | DRAFT | `(assetId,version)` 唯一 |
| DRAFT | validate | VALIDATING | 冻结 source Commit；创建持久化 Job |
| VALIDATING | validation failed | DRAFT | 保存结构化失败报告；不创建 Tag |
| VALIDATING | validation passed | PENDING_REVIEW | 保存 Manifest/DVC 校验结果；通知审核者 |
| PENDING_REVIEW | reject | DRAFT | 审批意见必填；内容修改后需重新校验 |
| PENDING_REVIEW | approve/publish saga start | PENDING_REVIEW | 锁定 revision，避免审批后漂移 |
| PENDING_REVIEW | saga success | PUBLISHED | Tag+Commit+Digest 原子业务结论 |
| PUBLISHED | deprecate | DEPRECATED | 内容仍可下载；搜索降权；原因审计 |
| DEPRECATED | archive | ARCHIVED | 默认搜索隐藏；保留事实和引用 |

“审批”与“发布”是一个动作还是两个角色动作受 `Q-203` 阻塞。发布 Saga 失败不能把版本标成
PUBLISHED；应保留可重试 Saga/Job 状态。

## 8. UploadSessionStatus

设计未给出完整枚举，建议：

```text
CREATED → UPLOADING → COMPLETING → PROCESSING → COMPLETED
   │          │            │             │
   ├──────────┴────────────┴─────────────┴→ CANCELLED
   └──────────到期────────────────────────→ EXPIRED
                         PROCESSING ─失败→ FAILED
                         FAILED ─重试────→ PROCESSING
```

| 状态 | 允许动作 |
| --- | --- |
| CREATED/UPLOADING | sign parts、登记完成 Part、查询、取消 |
| COMPLETING | 校验 Multipart 清单；禁止新 Part |
| PROCESSING | Worker 校验/转存/DVC/Git；客户端只查询/取消策略待确认 |
| FAILED | 查看错误；仅 retryable 错误可重试 |
| COMPLETED | 返回生成的版本/Job；重复 complete 返回同一结果 |
| CANCELLED/EXPIRED | 只读查询；异步清理暂存对象 |

需要在 P2 Accepted Requirement 中确认名称、超时和取消语义后才能成为稳定枚举。

## 9. JobStatus

```text
PENDING → RUNNING → SUCCEEDED
   │          ├→ RETRY_WAIT → PENDING
   │          ├→ DEAD
   │          └→ CANCELLED（仅安全点）
   └──────────→ CANCELLED
RUNNING --lease expired--> PENDING/RETRY_WAIT
```

| 转换 | 条件 |
| --- | --- |
| PENDING/RETRY_WAIT→RUNNING | nextRunAt 到期；原子领取；写 lease owner/expiry |
| RUNNING→SUCCEEDED | Handler 成功且业务副作用完成/可证明 |
| RUNNING→RETRY_WAIT | retryable error 且 attempts < max |
| RUNNING→DEAD | 不可重试或达到 max；通知/告警 |
| lease expired | Handler 必须幂等；记录失租 Attempt |
| retry/cancel | 只有 `job:manage` 且状态允许；审计 |

SUCCEEDED/DEAD/CANCELLED 是终态。人工 retry DEAD 是创建新 Attempt/重开原 Job，精确语义待确认。

## 10. IdempotencyStatus

V9 定义 `IN_PROGRESS/COMPLETED/FAILED`，建议转换：

```text
无 → IN_PROGRESS → COMPLETED
            └────→ FAILED
FAILED → IN_PROGRESS（仅明确可重试且所有权/超时满足）
```

- 相同 key + 不同 request digest 永远冲突；
- IN_PROGRESS 遗留必须有 owner/lease/expiry，否则崩溃后永久卡死；
- COMPLETED 保存原始 HTTP status 和响应摘要/载荷；
- FAILED 是否缓存错误及何时允许重试必须按用例声明；
- 业务提交与 COMPLETED 结果记录必须避免“副作用成功但幂等记录缺失”窗口。

当前 V9/Service 是否满足上述原子性需单独审计。

## 11. WebhookDeliveryStatus

```text
PENDING → DELIVERED
   └────→ FAILED → PENDING/FAILED → DEAD
```

- 每次 Attempt 记录状态码、错误分类和 nextAttemptAt；
- 只有 retryable 网络/5xx/约定 429 可重试；
- 4xx 默认 DEAD（具体白名单待确认）；
- DELIVERED/DEAD 为终态；
- Delivery ID 对接收方稳定，重试不生成新业务事件；
- Outbox processed 不能在 Delivery 真正进入确定终态前错误标记。

## 12. DiscussionStatus 与 CommentStatus

Discussion 使用稳定枚举：

```text
OPEN ↔ LOCKED
```

- OPEN 允许有权主体回复；LOCKED 只读；
- lock/unlock 需要 `asset:moderate`、expected rowVersion 和审计；
- Asset ARCHIVED 时 Discussion 有效能力被资源策略收紧为只读，不篡改原状态；
- 删除 Thread 不作为普通业务动作，Moderator 采用可审计隐藏策略。

Comment 使用 `ACTIVE/RETRACTED/HIDDEN`：

- 作者撤回 ACTIVE→RETRACTED；Moderator 可 ACTIVE/RETRACTED→HIDDEN 并按策略恢复；
- Revision 是追加历史，不通过状态覆盖正文历史；
- RETRACTED/HIDDEN 对普通读取返回 Tombstone，不返回受限正文；
- 状态变化与 Notification/Outbox/Audit 在同一 PostgreSQL 事务提交。

## 13. AuditResult

`SUCCEEDED/FAILED/DENIED` 是事件结果而非可变状态：

- SUCCEEDED：授权且业务结果提交；
- FAILED：已授权，但验证/依赖/内部执行失败；
- DENIED：认证、授权、策略或高风险前置条件拒绝；
- 记录一经写入不可更新为另一结果；
- 异步最终结果产生新事件，不能改写原事件。


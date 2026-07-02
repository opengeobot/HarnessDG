# P0-B 身份、组织与授权需求

> 状态：`PROPOSED`
> 依据：ADR-0002、设计 5.4/5.5/11.1。
> 已确认：`DEC-004`（单公司租户、多组织、多项目）。
> 阻塞：用户跨组织、Team/Owner 与最终角色矩阵受 Q-102..106 影响。

## REQ-IAM-001 Bootstrap 首个管理员

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actor: 部署管理员
permission: 不适用；仅空身份库的受控引导
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- 仅当系统不存在任何平台管理员时允许执行；
- 用户名和初始密码来自交互输入或挂载 Secret 文件，不接受提交到 Git 的固定生产值；
- 创建 Principal、User 和平台管理员 Role Binding；
- 用户状态为 PENDING_ACTIVATION，`forcePasswordChange=true`；
- 重复启动不得创建第二个管理员或重置现有密码；
- 引导完成后关闭入口；再次提供 Secret 不产生影响；
- 原始密码不得进入日志、审计、进程参数、环境诊断或响应。

### 错误与审计

- 非空库尝试引导：无操作并产生安全级运行日志，不回显账号信息；
- 数据写入失败：事务回滚，不留下无 Principal 的 User 或无 User 的 Principal；
- 审计事件：`BOOTSTRAP_ADMIN_CREATED`（事件是否纳入公共 Schema 待补）。

### 追踪

- Invariants：`INV-IAM-001/002/005/007`、`INV-TKN-008`
- Acceptance：`AC-P0B-IAM-001`
- Page/Journey：`PAGE-AUTH-001`、`JRN-P0B-001`

## REQ-IAM-002 用户登录与首次改密

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actor: USER
restOperations: [login, getCurrentPrincipal, changeCurrentUserPassword]
idempotency: NOT_REQUIRED
minimumEvidenceLevel: E4
```

### 前置与输入

- username、password；
- 可选 Accept-Language、X-Request-Id、traceparent；
- 账号必须存在且不是 DISABLED/LOCKED；
- 密码使用恒定时间的自适应哈希验证。

### 行为

- 用户名不存在与密码错误返回相同 `AUTH_INVALID_CREDENTIALS`；
- 每次失败原子增加计数，达到配置阈值进入 LOCKED；
- 成功清理失败计数，签发 access JWT 和 refresh Cookie；
- PENDING_ACTIVATION/forcePasswordChange 用户仅能访问 `/me`、改密、刷新策略允许端点和登出；
- 改密验证当前密码、新密码策略和确认值；
- 成功改密递增 tokenVersion，吊销旧 Token Family，用户进入 ACTIVE，并签发/要求重新登录的精确策略需确认；
- 响应/日志/审计不包含密码或 JWT。

### 错误、审计与指标

| 条件 | 错误 | 审计 |
| --- | --- | --- |
| 用户名/密码错误 | `AUTH_INVALID_CREDENTIALS` | `AUTH_LOGIN_FAILED`/FAILED |
| LOCKED | `AUTH_ACCOUNT_LOCKED` | `AUTH_LOGIN_FAILED`/DENIED |
| DISABLED | `AUTH_ACCOUNT_DISABLED` | `AUTH_LOGIN_FAILED`/DENIED |
| 未先改密访问业务 | `PASSWORD_CHANGE_REQUIRED` | 访问拒绝审计策略待定 |
| 新密码不合规 | `PASSWORD_POLICY_VIOLATION` | 不记录密码 |
| 成功 | — | `AUTH_LOGIN_SUCCEEDED` / `USER_PASSWORD_CHANGED` |

指标至少区分成功、无效凭据、锁定、禁用，用户名不得作为高基数 label。

### 追踪

- Invariants：`INV-IAM-001..008`、`INV-TKN-001..008`
- Acceptance：`AC-P0B-IAM-002..005`
- Page/Journey：`PAGE-AUTH-001/002`、`JRN-P0B-001/002`

## REQ-IAM-003 access JWT 校验与 PrincipalContext

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actor: 所有在线 Principal
idempotency: NOT_APPLICABLE
minimumEvidenceLevel: E4
```

### 行为

每个受保护请求在进入 Controller/MCP Tool 前：

1. 提取唯一 Bearer JWT；
2. 按 `kid` 查找允许的公钥并验证算法、签名、iss、aud、iat、exp；
3. 解析 `sub=prn_...`、principalType、jti、tokenVersion 和 scopes；
4. 实时查询 Principal/User/Agent 状态和 tokenVersion；
5. 建立只读 PrincipalContext 和日志/Trace Context；
6. 请求完成后清理 ThreadLocal/MDC，防止线程复用串主体。

未知 kid、错误算法、签名/issuer/audience、过期、禁用或 tokenVersion 不一致一律 fail closed。业务模块不得
解析 JWT。

### PrincipalContext 最低字段

`principalId/principalType/subject/currentOrganizationId/accessibleProjectIds/roles/scopes/maxSensitivityLevel/
locale/requestId/traceId`。

其中角色/项目/敏感度可由实时 Query Port 构建，不能盲信 JWT。

### 错误

- 缺失/伪造/状态失效：`AUTH_UNAUTHENTICATED`；
- 过期：`AUTH_TOKEN_EXPIRED`；
- 未知内部异常不得暴露 Claim、密钥或 SQL。

### 追踪

- Invariants：`INV-COM-001/002`、`INV-TKN-002/003/007/008`
- Acceptance：`AC-P0B-IAM-008/010`、`AC-P0B-AUD-004`
- NFR：`NFR-SEC-001/003/004`

## REQ-IAM-004 refresh 轮换、并发与重放检测

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actor: USER 浏览器会话
restOperations: [refreshToken]
idempotency: 通过 Token jti 的单次消费保证
minimumEvidenceLevel: E4
```

### 行为

- refresh 只从 Secure/HttpOnly/SameSite Cookie 读取，不接受前端持久化；
- 校验 JWT 后，以原子 compare-and-set 将 jti 从 ACTIVE 置为 ROTATED；
- 同一事务创建同 Family 的新 refresh 摘要；
- 只有一个并发请求可以成功；
- ROTATED jti 再用视为重放，整个 Family REVOKED；
- REVOKED/EXPIRED/Principal disabled/tokenVersion mismatch 均拒绝；
- access/refresh 均重新签发，旧 Cookie 被覆盖；
- 客户端对并发 401 只发起一次协调 refresh，其他请求复用结果。

### 错误与审计

- 重放：`AUTH_REFRESH_REPLAYED` + `AUTH_TOKEN_REPLAY_REJECTED`/DENIED；
- 其他无效：`AUTH_UNAUTHENTICATED` 或 `AUTH_TOKEN_EXPIRED`；
- 成功：`AUTH_TOKEN_REFRESHED`；
- 审计只记录 Family/主体的安全摘要或业务 ID，不记录 JWT/jti 原文策略需统一。

### 追踪

- Invariants：`INV-TKN-001..008`
- Acceptance：`AC-P0B-IAM-006/007/011`
- State Machine：RefreshTokenStatus

## REQ-IAM-005 登出、禁用、改密和重置的会话失效

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actors: [USER, 用户管理员, Agent 管理员]
minimumEvidenceLevel: E4
```

### 行为

- 登出吊销当前 Token Family 并清除 refresh Cookie；重复登出安全幂等；
- 禁用 User/Agent 递增 tokenVersion、吊销所有 Family/credential；
- 重置密码吊销所有会话，User 进入 PENDING_ACTIVATION 并强制改密；
- 用户自行改密至少吊销其他会话；是否保留当前会话由产品确认；
- 任一动作提交后的下一次旧 access 请求必须实时失败；
- 已排队且尚未执行的该 Principal 写任务必须冻结/取消/重新授权，精确类型策略需 Job 需求声明。

### 权限/审计

- 自己登出/改密无需管理权限；
- 用户启停/重置需 `user:manage`；
- Agent 启停需 `agent:authorize`；
- 事件：`AUTH_LOGOUT`、`USER_DISABLED/ENABLED/PASSWORD_RESET`、`AGENT_DISABLED/ENABLED`。

### 追踪

- Invariants：`INV-IAM-005/006`、`INV-AGT-006`、`INV-TKN-006/007`
- Acceptance：`AC-P0B-IAM-008/009/012`、`AC-P0B-AGT-003`

## REQ-IAM-006 用户全生命周期管理

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actor: 用户管理员
permissions: [user:read, user:manage]
idempotency: REQUIRED for create/enable/disable/reset
minimumEvidenceLevel: E4
```

### 行为

- 查询使用小型 PageResult 或明确 Cursor，支持受控 keyword/status 排序；
- 创建校验 username、displayName、email/locale、临时密码策略；
- username 冲突返回 `USER_ALREADY_EXISTS`；
- 详情不存在返回 `USER_NOT_FOUND`；
- 编辑不能通过请求体修改 principalId/userId/tokenVersion/password hash；
- 启停和重置遵守 UserStatus；
- 不提供物理删除；
- 不能禁用最后一个有效平台管理员，规则需加入正式权限决策；
- 所有列表/详情/动作受后端 Permission，不仅依赖前端按钮。

### 审计与通知

`USER_CREATED/UPDATED/ENABLED/DISABLED/PASSWORD_RESET`；临时密码不得出现在 attributes。是否向目标用户发
站内通知由通知需求确认。

### 追踪

- Acceptance：`AC-P0B-IAM-012`、`AC-P0B-UI-002..006`
- Page：`PAGE-ADM-001`

## REQ-AGT-001 Agent 注册与凭据交换

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actors: [Agent 管理员, AGENT]
permissions: [agent:register, agent:authorize]
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- 注册输入 displayName、agentType、vendor、maxSensitivityLevel、受控 scopes、受控 tools；
- 创建独立 Principal/Agent 和高熵 credential；
- credential 只在首次成功响应显示，数据库保存带版本的自适应/密钥哈希摘要；
- Scope 必须是平台 Catalog、授权者可委派集合和 Agent 允许集合的交集；
- Tool 必须来自发布的 MCP Tool Catalog；
- 默认只读；`asset:publish/delete/system:configure` 不可默认授予；
- credential exchange 校验 Agent ACTIVE、credential、tokenVersion，签发短期 access JWT，不签浏览器 refresh；
- 注册重放返回同一 Agent 但绝不再次回显已丢失 credential；如何恢复必须显式轮换。

### 错误与审计

- 未知 Agent：`AGENT_NOT_FOUND` 或统一凭据错误，外部不得枚举；
- 无效凭据：`AUTH_INVALID_CREDENTIALS`；
- 禁用：`AUTH_ACCOUNT_DISABLED`；
- 未知 Scope/Tool：`COMMON_INVALID_ARGUMENT`（建议新增稳定专用错误）；
- 事件：`AGENT_REGISTERED`、`CLIENT_TOKEN_ISSUED/REJECTED`、`AGENT_TOOL_ALLOWLIST_UPDATED`。

### 追踪

- Invariants：`INV-AGT-001..007`
- Acceptance：`AC-P0B-AGT-001..005`
- Page：`PAGE-ADM-002`、`PAGE-INT-002/003`

## REQ-ORG-001 Organization、Project 与 Membership

```yaml
status: OPEN
priority: MUST
phase: P0-B
blockedBy: [Q-102, Q-105]
minimumEvidenceLevel: E4
```

### 建议行为

- 系统只有一个公司租户，不提供租户 CRUD；平台管理员创建/停用 Organization；
- 组织管理员在自己 Organization 内创建/停用 Project、管理成员；
- 同一 Principal 可属于多个 Organization；
- 成员关系只表达归属，权限由 Role Binding 表达；
- Organization code 全局唯一，Project code 组织内唯一；
- 非成员查询组织/项目使用防枚举；
- 停用上级后新写入和授权停止，历史数据保留；
- 移除成员实时撤销由成员关系派生的访问；
- Gitea Organization 字段是投影/映射，不作为业务授权。

### 权限、错误与审计

- `organization:manage`、`project:view/manage`；
- `ORGANIZATION_NOT_FOUND/ALREADY_EXISTS/MEMBER_*`、`PROJECT_*`；
- `ORGANIZATION_CREATED`、成员添加/移除、`PROJECT_CREATED`；启停事件需补契约。

### 追踪

- Invariants：`INV-ORG-001..006`
- Acceptance：`AC-P0B-AUTH-001..004/008`
- Pages：`PAGE-ADM-003/005`

## REQ-ORG-002 Team 与资产责任主体

```yaml
status: OPEN
priority: MUST if Team accepted
phase: P0-B/P1 boundary
blockedBy: [Q-103, Q-104, Q-105]
minimumEvidenceLevel: E4
```

### 建议行为

- Team 是 Organization 内正式资源，具有 teamId/code/name/status/rowVersion；
- 组织管理员管理 Team 和成员；
- Team Member 必须先是 Organization Member；
- Team 可作为 Role Binding、ACL 和 Asset Owner 的主体；
- 资产至少一个 Team Owner，可附个人 Maintainer（建议）；
- 移除成员/停用 Team 不得留下无 Owner 资产；
- 禁止用字符串 team name 作为 Owner。

### 追踪

- Invariants：`INV-TEAM-001..007`
- Acceptance：`AC-P0B-AUTH-001/009`
- Pages：`PAGE-ADM-004`、`PAGE-AST-005`

## REQ-AUTH-001 统一实时授权判定

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
actors: 所有 Principal/Adapter
minimumEvidenceLevel: E4
```

### 行为

所有 Application Use Case 在发生查询或副作用前调用统一授权 API，输入至少为：

```text
principal + action/permission + scope + resource + resourceState
+ visibility + sensitivity + environment/tool
```

判定同时要求：

1. Principal/上级作用域 ACTIVE；
2. 客户端 JWT Scope 允许；
3. Role Binding/Permission 或合法 ACL 允许；
4. 组织/项目成员关系允许；
5. 资源状态和操作前置条件允许；
6. 敏感等级允许；
7. Agent Tool Allowlist（MCP 时）允许；
8. 高风险默认策略允许。

任一数据缺失或依赖失败默认拒绝。Controller/MCP/Worker 只建立协议上下文，不能把 `platformAdmin=true`
之类可伪造结论传给 Application Service 代替授权。

### 错误与审计

- 一般拒绝：`AUTH_PERMISSION_DENIED`；
- Agent Tool：`MCP_TOOL_NOT_ALLOWED` + `AGENT_ACCESS_DENIED`；
- 私有资源详情使用资源 `*_NOT_FOUND` 防枚举；
- 高风险拒绝和所有授权管理写操作必须审计。

### 追踪

- Invariants：`INV-AUTH-001..009`
- Acceptance：`AC-P0B-AUTH-003..009`
- NFR：`NFR-SEC-001/005/006`、`NFR-MNT-002`

## REQ-AUTH-002 列表权限下推与防枚举

```yaml
status: PROPOSED
priority: MUST
phase: P0-B
minimumEvidenceLevel: E4
```

### 行为

- 资产、组织、项目、审计、任务等列表在 SQL 中合并 Principal 可见作用域；
- 禁止先查全量后在 Java/前端过滤；
- Cursor 必须绑定规范化 filter/sort/principal scope，防止换主体复用；
- count/total、facet、autocomplete、错误时间也不得泄露不可见资源；
- 详情、修改和删除加载使用相同访问 Predicate；
- 管理员全局视图是显式 Permission 结果，不是空过滤条件；
- 数据库/授权依赖失败时不返回宽松结果。

### 验收

- 两组织/两项目/PRIVATE/INTERNAL/PUBLIC/ACL/Team 的组合数据；
- 比较 API 响应和数据库查询计划/SQL 结果；
- 无权资源 ID 详情与随机不存在 ID 对外语义一致；
- 数据规模下验证无 N+1 和内存全量过滤。

### 追踪

- Acceptance：`AC-P0B-AUTH-002..005`
- NFR：`NFR-SEC-005`、`NFR-PERF-001`

## REQ-AUTH-003 Role、Binding 与 ACL 管理

```yaml
status: OPEN
priority: MUST
phase: P0-B
blockedBy: [Q-105, final permission matrix]
minimumEvidenceLevel: E4
```

### 建议行为

- Permission Catalog 只由版本化 Migration/代码扩展，不由 UI 任意创建；
- 内置 Role 稳定不可改删；自定义 Role 可在允许作用域创建；
- 自定义 Role 权限不得超出操作者可委派集合；
- Binding 输入受控 Principal/Team、Role、Scope；
- ACL 输入受控资源、主体、Permission；
- 重复 Binding/ACL 幂等或返回稳定冲突；
- 删除仍被引用 Role 返回 `ROLE_IN_USE`；
- 禁止自提权、跨组织绑定和给停用主体/资源新增授权；
- 变更提交后实时生效并使相关授权缓存失效。

### 错误与审计

`ROLE_NOT_FOUND/ALREADY_EXISTS/BUILTIN_IMMUTABLE/IN_USE`、
`PERMISSION_UNKNOWN`、`ROLE_BINDING_*`、`RESOURCE_ACL_*`；
所有 Create/Update/Delete/Denied 事件写审计。

### 追踪

- Acceptance：`AC-P0B-AUTH-004..008`
- Pages：`PAGE-ADM-006..009`
- Drift：`TERM-003/004/005`、`AUD-016/017`

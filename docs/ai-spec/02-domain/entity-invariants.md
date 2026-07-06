# 领域实体与不变量

> 状态：`READY`
> 作用：不变量必须同时体现在 Domain、数据库约束、API 校验和自动化测试中。

## 1. 通用不变量

| ID | 不变量 |
| --- | --- |
| `INV-COM-001` | 对外 ID 使用固定前缀和 ULID；数据库自增 ID 永不出现在 API/事件中 |
| `INV-COM-002` | 所有持久时间为 UTC；业务时间由注入 `Clock` 提供 |
| `INV-COM-003` | 可编辑聚合使用 `rowVersion` 乐观锁；客户端必须回传读取到的版本 |
| `INV-COM-004` | 排序字段只接受服务端白名单；任何客户端值不得直接拼 SQL |
| `INV-COM-005` | API DTO、Application Command/Query、Domain、Persistence DO 和外部 DTO 不混用 |
| `INV-COM-006` | 稳定状态只存在于代码枚举和 DB CHECK，不存在于动态字典 |
| `INV-COM-007` | 所有写用例明确事务边界、幂等策略、错误、审计和恢复方式 |

## 2. Principal、User、Agent 与 Token

### 2.1 标识

| 对象 | ID | 关系 |
| --- | --- | --- |
| Principal | `prn_...` | 认证、JWT subject、角色绑定、ACL、审计的唯一主体引用 |
| User | `usr_...` | 一对一引用 Principal |
| Agent | `agt_...` | 一对一引用 Principal |
| Token | `tkn_...`（待统一现有前缀） | 多对一引用 Principal |
| Token Family | 稳定随机 ID/摘要 | 一次登录会话的 refresh 轮换链 |

`TERM-001` 未解决前，任何迁移或 API 都不得继续扩大 `principalId=usr_` 的混用。

### 2.2 User

- `INV-IAM-001` username 规范化后全局唯一，原始大小写策略必须固定；
- `INV-IAM-002` password 只存自适应哈希及算法参数；
- `INV-IAM-003` `failedLoginAttempts` 不得小于 0；
- `INV-IAM-004` `lockedUntil` 与 LOCKED 状态一致，锁定是定时还是仅管理员解锁待确认；
- `INV-IAM-005` `tokenVersion` 单调递增，不允许回退；
- `INV-IAM-006` 禁用、重置密码和凭据泄漏处置必须使既有 access/refresh 失效；
- `INV-IAM-007` Bootstrap 管理员必须 `forcePasswordChange=true`；
- `INV-IAM-008` PENDING_ACTIVATION 用户除登录、刷新策略允许的端点、`/me`、改密、登出外不能访问业务；
- `INV-IAM-009` email 是否唯一、是否必填仍需产品决策。

### 2.3 Agent

- `INV-AGT-001` Agent 有独立 Principal 和凭据，不复用 User Token；
- `INV-AGT-002` 原始 credential 只在创建响应显示一次，数据库只存摘要；
- `INV-AGT-003` `scopes` 必须是受控 Scope Catalog 的子集；
- `INV-AGT-004` `toolAllowlist` 必须引用已注册 MCP Tool Catalog；
- `INV-AGT-005` Agent 的最大敏感等级不得高于授权者可授予等级；
- `INV-AGT-006` 禁用 Agent 必须递增 tokenVersion/吊销凭据，使旧 access JWT 实时失效；
- `INV-AGT-007` 高风险 Permission/Scope 默认不可授予 Agent，例外必须经过显式审批和审计。

### 2.4 Token

- `INV-TKN-001` access 默认 15 分钟、refresh 默认 7 天；最终值来自类型化配置；
- `INV-TKN-002` JWT 必含 iss、aud、sub=`principalId`、principalType、jti、iat、exp、tokenVersion 和粗粒度 scopes；
- `INV-TKN-003` 签名为部署 Secret 提供的非对称密钥并带已知 `kid`；
- `INV-TKN-004` 数据库只存 jti/Token Family/credential 摘要，不存 JWT；
- `INV-TKN-005` 同一 refresh jti 只能从 ACTIVE 转换一次；
- `INV-TKN-006` refresh 重放必须吊销整个 Family；
- `INV-TKN-007` access 的角色/ACL 不作为最终事实，每次请求实时授权；
- `INV-TKN-008` 日志、审计、埋点、错误报告和 LocalStorage 不得出现原始 Token。

## 3. Organization、Project、Team 与授权

### 3.1 Organization/Project

- `INV-ORG-001` organization.code 全局唯一，project.code 在 organization 内唯一；
- `INV-ORG-002` project 必须属于一个 ACTIVE organization；
- `INV-ORG-003` DISABLED organization/project 不能创建新资源或产生新授权；
- `INV-ORG-004` 停用不删除历史资产、审计、角色绑定或标签引用；
- `INV-ORG-005` 非成员访问私有作用域使用防枚举语义；
- `INV-ORG-006` 成员关系与权限角色分离：成员说明“属于”，Role Binding 说明“能做什么”。

### 3.2 Team

Team 是否进入 MVP 受 `Q-103` 阻塞。若接受，最低不变量为：

- `INV-TEAM-001` Team 必须属于一个 Organization；
- `INV-TEAM-002` team.code 在 Organization 内唯一；
- `INV-TEAM-003` Team Member 必须是同 Organization 的 ACTIVE Principal；
- `INV-TEAM-004` Team 可以被 Role Binding、ACL 和 Asset Owner 引用；
- `INV-TEAM-005` 移除最后一个 Asset Owner 前必须先转移责任；
- `INV-TEAM-006` Team 停用保留历史 Owner 引用，但不能获得新资产或新授权；
- `INV-TEAM-007` Team 删除必须检查 Owner、ACL、角色绑定和任务引用。

### 3.3 Role/Binding/ACL

- `INV-AUTH-001` Permission code 全局唯一且来自中央 Catalog；
- `INV-AUTH-002` 内置 Role 不可删除、改 code 或改变语义，权限集合变更必须前向迁移/决策；
- `INV-AUTH-003` 自定义 Role 的 Permission 必须是授权者可委派集合的子集；
- `INV-AUTH-004` Role Binding 唯一键为 principal+role+scopeType+scopeId；
- `INV-AUTH-005` scopeType 与 scopeId 必须成对合法，PLATFORM 的 scopeId 为空；
- `INV-AUTH-006` ACL 只能引用存在且 ACTIVE 的 Principal/Team、资源和 Permission；
- `INV-AUTH-007` 禁止通过 ACL 突破 JWT Scope、敏感等级和资源状态；
- `INV-AUTH-008` 授权者不能授予自己不拥有或不可委派的权限；
- `INV-AUTH-009` 删除/停用 Role、成员、Team 后权限实时重新计算。

## 4. 字典、标签、i18n 与配置

### 4.1 Dictionary

- `INV-DICT-001` dictCode 和 itemCode 是稳定机器标识，展示名不能用于业务分支；
- `INV-DICT-002` `(dictCode,itemCode)` 唯一；
- `INV-DICT-003` item 必须有 i18nKey，MVP 至少有 zh-CN/en-US 文案；
- `INV-DICT-004` DISABLED item 保留历史引用和回显，禁止新引用；
- `INV-DICT-005` 每次变更递增版本并产生审计；
- `INV-DICT-006` License、敏感度等治理字典不能由普通业务用户修改。

### 4.2 Tag

- `INV-TAG-001` tagId 是资产写入的唯一标签引用；
- `INV-TAG-002` PLATFORM 标签 scopeId 必须为空，ORGANIZATION 标签 scopeId 必须为 ACTIVE Organization；
- `INV-TAG-003` `(scopeType,scopeId,tagCode)` 唯一；
- `INV-TAG-004` 组织标签不能覆盖或改变平台标签语义；
- `INV-TAG-005` DISABLED 标签保留 asset_tag 历史，禁止建立新关联；
- `INV-TAG-006` 合并标签必须有显式源/目标、引用迁移、幂等和审计；
- `INV-TAG-007` 不提供自由字符串回退。

### 4.3 Configuration

- `INV-CFG-001` configKey 全局稳定，代码通过类型化对象读取；
- `INV-CFG-002` value 必须满足 declared type 和 validator；
- `INV-CFG-003` 密码、Token、私钥、凭据、预签名 URL 永不进入 system_config；
- `INV-CFG-004` 更新必须携带 expected version；
- `INV-CFG-005` `hotReloadable=false` 的变更不得伪装成已即时生效；
- `INV-CFG-006` 安全/发布策略变更需要二次确认，必要时双人审批待确认。

## 5. Asset、Version、Artifact 与 Relation

### 5.1 Asset

- `INV-AST-001` assetId 永久不变；
- `INV-AST-002` `(namespace,type,name)` 唯一；重命名后旧坐标的保留规则待 `Q-202`；
- `INV-AST-003` type 首期只能是 MODEL/DATASET；
- `INV-AST-004` organizationId/projectId/Owner 必须形成合法作用域关系；
- `INV-AST-005` 至少一个有效 Owner；是否必须 Team 由 `Q-104` 决定；
- `INV-AST-006` governed fields 只引用对应 ACTIVE Dictionary itemCode；
- `INV-AST-007` tagIds 必须是 ACTIVE 且平台/本组织可用标签；
- `INV-AST-008` visibility 只是授权输入，不替代 RBAC/ACL；
- `INV-AST-009` PostgreSQL Asset 是查询/流程投影，必须记录 sourceCommit/manifestDigest（发布后）并可重建；
- `INV-AST-010` 创建 Gitea 仓库成功前，资产不能对普通搜索显示为可用；
- `INV-AST-011` 删除为软删除/归档语义，正式对象回收必须引用检查和保留期。

### 5.2 Version

- `INV-VER-001` `(assetId,version)` 和 `(assetId,tag)` 唯一；
- `INV-VER-002` Published 必须同时有 tag、commitSha、manifestDigest；
- `INV-VER-003` Published 后上述三元组、Manifest 和 Artifact 集合不可变；
- `INV-VER-004` 状态变化只经领域方法/显式动作端点，不接受 `PATCH status`；
- `INV-VER-005` 提交审核时冻结待审 revision；审批不能悄然审核后续 Commit；
- `INV-VER-006` 提交人、审核人分离规则受 `Q-203`；
- `INV-VER-007` 驳回保留意见和审计，重新提交形成新的校验结果；
- `INV-VER-008` Deprecation 不删除内容，默认搜索降权；
- `INV-VER-009` Archived 默认不返回，但有权管理员可查询恢复元数据。

### 5.3 Artifact/Manifest

- `INV-ART-001` `(versionId,path)` 唯一；
- `INV-ART-002` path 为规范化相对 POSIX 路径，拒绝绝对路径、`..`、空段和歧义 Unicode；
- `INV-ART-003` 每项至少包含 DVC hash 或定义的内容摘要、SHA-256、size、mediaType；
- `INV-ART-004` Manifest 规范化和 digest 算法必须版本化；
- `INV-ART-005` Manifest 引用的每个正式 DVC 对象在发布前必须存在且大小/摘要一致；
- `INV-ART-006` 样例和预览不得泄露超出资产敏感等级的数据；
- `INV-ART-007` 同一内容不得同时由 DVC 和 Git LFS 跟踪。

### 5.4 Relation

- `INV-REL-001` source/target 必须引用存在的精确 Version，不引用含糊 latest；
- `INV-REL-002` 禁止自环；
- `INV-REL-003` 对 `DERIVED_FROM` 是否允许环必须明确，建议禁止有向环；
- `INV-REL-004` 删除/归档目标前必须评估下游引用；
- `INV-REL-005` Relation Type 为稳定枚举还是字典需单独确认。

### 5.5 Dataset 分类、预览与讨论

- `INV-DST-001` DATASET 的 task/modality/format/language/license/sensitivity 只引用对应字典 itemCode；
- `INV-DST-002` DATASET 的 tagIds 只引用作用域内 ACTIVE 受控标签，永不回退自由字符串；
- `INV-DST-003` sampleCount/totalBytes 保存原始非负数值，sizeBucketCode 由版本化服务端规则派生；
- `INV-DST-004` Facet/count/autocomplete 与 items 使用同一授权、敏感度和状态 Predicate；
- `INV-DST-005` Files、Preview、Schema、Split、统计和 download handle 必须绑定精确 versionId；
- `INV-PRE-001` Preview 引用 source Version/Artifact digest，不能成为正式内容或替代 Artifact；
- `INV-PRE-002` Preview 每次读取实时授权，Preview Bucket 永不匿名；
- `INV-PRE-003` Preview 样例遵守格式、行列、大小、资源和脱敏上限；
- `INV-DIS-001` Discussion 绑定一个 assetId，可选绑定精确 versionId，不能扩大 Asset 可见性；
- `INV-DIS-002` Comment 修订追加 Revision，撤回/Moderation 不物理删除历史；
- `INV-DIS-003` Discussion/Comment/Notification/Outbox 在同一 PostgreSQL 事务提交；
- `INV-DIS-004` Discussion/Card 均为不可信内容，不能改变系统、Agent 或 Tool Policy；
- `INV-DIS-005` 归档 Asset 的 Discussion 默认只读，恢复 Asset 不自动解锁 Thread。

## 6. Upload、Job、Inbox/Outbox、Notification 与 Audit

### 6.1 Upload Session

- `INV-UPL-001` 绑定唯一 Principal、Asset、目标草稿 Version 和对象前缀；
- `INV-UPL-002` 总大小、Part 数/大小、文件数和过期时间受配置限制；
- `INV-UPL-003` 客户端 ETag/摘要不替代服务端最终校验；
- `INV-UPL-004` complete 只能执行一次，重复相同请求返回同一结果；
- `INV-UPL-005` EXPIRED/CANCELLED 后不得继续签 Part；
- `INV-UPL-006` 暂存对象不能被发布版本直接引用；
- `INV-UPL-007` 成功/失败后的清理通过持久化任务执行。

### 6.2 Job

- `INV-JOB-001` Job Type 必须注册对应幂等 Handler；
- `INV-JOB-002` payload 有版本且不含 Secret；
- `INV-JOB-003` 同一业务工作有稳定 deduplication key；
- `INV-JOB-004` 领取使用数据库锁和 lease owner/expiry；
- `INV-JOB-005` attempt 单调递增且不超过 maxAttempts；
- `INV-JOB-006` 只有定义为 retryable 的错误进入 RETRY_WAIT；
- `INV-JOB-007` DEAD/CANCELLED/SUCCEEDED 为终态，人工操作遵守前置条件并审计；
- `INV-JOB-008` context 保存 trace/principal/resource ID，Worker 建立新上下文；
- `INV-JOB-009` 不允许 Sample/Noop Handler 承担正式业务类型。

### 6.3 Inbox/Outbox/Notification/Audit

- `INV-EVT-001` Webhook Inbox 的 deliveryId 唯一，先持久化再处理；
- `INV-EVT-002` Outbox 必须与触发它的核心 PostgreSQL 事务原子提交；
- `INV-EVT-003` Delivery 重试不修改核心业务结果；
- `INV-EVT-004` Notification 只能由接收 Principal 查询/标记已读；
- `INV-EVT-005` Audit 是追加写，应用与普通管理员无 UPDATE/DELETE 路径；
- `INV-EVT-006` 审计记录必须区分 SUCCEEDED/FAILED/DENIED；
- `INV-EVT-007` 所有载荷先按字段策略脱敏并限制大小；
- `INV-EVT-008` Runtime Log、Audit、Inbox、Outbox 各自职责独立，不互相替代。

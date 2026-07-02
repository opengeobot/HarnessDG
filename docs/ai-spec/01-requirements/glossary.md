# 领域术语与命名约束

> 状态：`PROPOSED`
> 规则：代码、API、数据库和 UI 使用同一术语；同义词必须指定唯一规范词。

## 1. 身份与授权

| 术语 | 规范定义 | 不等同于 |
| --- | --- | --- |
| Principal | 能被认证、授权和审计的统一访问主体 | User、角色、JWT |
| User | Principal 的人类账号扩展，P0 事实源为 PostgreSQL | Principal 本身、Gitea 用户 |
| Agent | 代表 OpenClaw/QwenPaw 等自动化调用方的独立 Principal | 人类用户 Token、Skill |
| Service | 后端服务或外部业务系统的工作负载 Principal | Agent 的别名 |
| API Client | SDK、CI/CD 或批处理客户端 Principal | 浏览器会话 |
| Worker | 平台受控后台任务执行主体 | 普通 Service 账号 |
| Role | Permission 的命名集合，可在作用域内绑定给 Principal | PrincipalType、Scope |
| Permission | `resource:action` 形式的细粒度业务动作 | JWT Scope |
| JWT Scope | 凭据被允许请求的粗粒度能力上限 | 实时 RBAC/ACL 授权结果 |
| ACL | 对指定资源和 Principal 的显式权限授予 | 组织成员关系 |
| Tool Allowlist | Agent 可以发现/调用的 MCP Tool 集合 | Scope 或 Permission |
| Sensitivity Level | Principal 可访问与资产声明的敏感度等级 | Visibility |

一次授权必须同时满足凭据 Scope、业务 Permission、作用域成员关系、资源 ACL/可见性、资源状态和敏感度。

## 2. 组织与责任

| 术语 | 规范定义 | 当前状态 |
| --- | --- | --- |
| Tenant | 公司级隔离边界 | `DEC-004`：MVP 为单公司租户，不建设多租户管理 |
| Organization | 公司内一级协作与治理作用域 | 已有表/API，精确成员语义待确认 |
| Project | Organization 下的业务隔离单元 | 已有表/API |
| Team | 可成为资产 Owner 的稳定人员集合 | 设计要求引用，但当前完整模型缺失 |
| Owner | 对资产生命周期负责的 Principal 或 Team 引用 | 不允许自由文本 |
| Maintainer | 可执行日常资产修改的主体 | 是否区别于 Owner 待确认 |

## 3. 资产与版本

| 术语 | 规范定义 | 约束 |
| --- | --- | --- |
| Asset | 可治理的 MODEL 或 DATASET 聚合根 | 具有稳定 `assetId`，名称可变 |
| Asset Card | `README.md` 中面向人的说明 | 与 `asset.yaml` 冲突时禁止发布 |
| Asset Manifest | `asset.yaml` 及正式文件清单的机器可读描述 | 由 Gitea 版本化 |
| Asset Projection | PostgreSQL 中用于搜索和流程的投影 | 不是版本事实源，必须可重建 |
| Asset Version | 某资产的一次不可变版本候选或发布记录 | 与 Asset 生命周期状态分离 |
| Published Version | 同时拥有 Git Tag、Commit SHA、Manifest/DVC Digest 的版本 | 不得覆盖或原地修改 |
| Artifact | 发布版本中的正式文件或目录条目 | 具有 path、摘要、大小和媒体类型 |
| DVC Hash | DVC 对内容寻址对象使用的摘要 | 不自动等于文件 SHA-256 |
| Manifest Digest | 正式文件清单的稳定摘要 | 生成算法和规范化规则必须另行定义 |
| Download Ticket | 授权后签发的短期下载方法集合 | 不是永久 URL 或对象凭据 |
| Upload Session | Web/REST Multipart 上传的可恢复会话 | 暂存内容不属于正式版本 |
| Visibility | PRIVATE/INTERNAL/PUBLIC 的资产可见性策略输入 | PUBLIC 仍不表示互联网匿名 |

## 4. 治理与可靠性

| 术语 | 规范定义 |
| --- | --- |
| Stable Enum | 驱动状态机和代码分支的固定枚举，同时受 DB 约束 |
| Dictionary Item | 管理员维护的可运营分类，以 `itemCode` 引用 |
| Tag | PLATFORM 或 ORGANIZATION 作用域的受控治理对象，以 `tagId` 引用 |
| Idempotency Key | 主体、方法、路径和请求摘要共同作用域下的重复请求键 |
| Job | 存入 PostgreSQL、带租约/重试/状态的可靠后台工作 |
| Inbox | 对外部事件先落库、按 Delivery ID 幂等消费的记录 |
| Outbox | 与业务事务原子写入、后续可靠投递的事件记录 |
| Saga | 跨 PostgreSQL/Gitea/MinIO 的多步骤状态与补偿编排 |
| Reconciliation | 周期比较权威事实源与投影并修复漂移 |
| Audit Log | 不可变追加写的业务/安全证据 |
| Runtime Log | 面向运维的结构化日志，不可替代 Audit Log |

## 5. 当前必须解决的命名冲突

| ID | 冲突 | 证据 | 风险 | 建议 |
| --- | --- | --- | --- | --- |
| `TERM-001` | `PrincipalContext.principalId` 示例使用 `usr_`，V3 又定义独立 `prn_` 与 `usr_` | shared-kernel 注释/测试与 V3/领域模型不一致 | JWT subject、ACL、审计和 API 可能混用两类 ID | `principalId` 始终使用 `prn_`；用户展示/管理使用 `userId=usr_` |
| `TERM-002` | PrincipalType 注释仍写 OIDC/LDAP/OAuth2，而 ADR-0002 固定 P0 本地身份/JWT | Java 注释与 ADR 冲突 | AI 可能错误引入 OAuth/OIDC | 修正文档债；未来标准 SSO 另立 ADR |
| `TERM-003` | 设计列出 `asset:update`/`asset:delete`，代码又增加 `asset:manage` | `Permissions.java` 与 V4 Seed 不一致 | 权限检查可能永远无法通过或语义重叠 | 删除或正式定义 `asset:manage`，同步 Seed/OpenAPI/角色 |
| `TERM-004` | 角色表作用域与角色绑定作用域不同 | 角色仅 PLATFORM/ORGANIZATION，绑定支持 PROJECT | 自定义项目角色语义不清 | 明确“角色模板”与“绑定作用域”的组合规则 |
| `TERM-005` | Organization Member 的 OWNER/MEMBER 与 RBAC Role 并存 | V5 成员 role 字符串 + V4 role binding | 两套角色可能产生冲突授权 | 成员类型只表达关系，权限统一由 Role Binding 决定 |

以上冲突解决后必须同步 Java 文档、数据库注释、OpenAPI Schema、前端字段和迁移策略。

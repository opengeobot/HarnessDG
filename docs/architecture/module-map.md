# 后端模块映射（Module Map）

> 依据：`prd/DEEP_RESEARCH_内部AI资产管理平台设计.md` 第 4.3、5.3、15.6 节
> 关联：`docs/adr/ADR-0001-technology-baseline.md`、`docs/adr/ADR-0002-p0-platform-foundation-gate.md`
> 作者：AxeXie

本文件描述后端模块化单体的模块划分、模块内分层规则与跨模块依赖约束，作为 ArchUnit 架构测试
与代码评审的依据。首期采用模块化单体，不拆分微服务。P0-B 公共平台底座完成前，业务模块只允许
接入公共能力和修复安全/兼容问题，不得继续扩展功能。

## 1. 模块清单

```text
backend/
├─ bootstrap                 # Spring Boot 启动与配置装配，唯一可见全部模块
├─ module-identity           # 本地用户、Agent、凭据、JWT、Principal
├─ module-authorization      # Permission、RBAC、Scope、ACL
├─ module-organization       # 组织、项目、成员、角色
├─ module-taxonomy           # 字典、受控标签、国际化
├─ module-configuration      # 类型化非敏感运行配置
├─ module-asset              # 资产、卡片、受控标签引用、检索、Discussion
├─ module-version            # Commit/Tag/DVC/Manifest、发布状态机
├─ module-transfer           # 上传会话、预签名 URL、下载授权
├─ module-integration-gitea  # Gitea Client、Webhook、对账
├─ module-integration-minio  # Bucket、签名、对象元信息
├─ module-job                # PostgreSQL 任务队列、Worker、重试
├─ module-mcp                # MCP Tools / Resources / Prompts（Adapter）
├─ module-audit              # 审计、Outbox、操作记录
├─ module-notification       # 站内信、签名 Webhook、渠道适配
├─ platform-observability    # 结构化日志、指标、Trace、健康与诊断
└─ shared-kernel             # 错误码、鉴权上下文、ID、分页等公共契约
```

| 模块 | 职责 | 关键说明 |
| --- | --- | --- |
| `bootstrap` | Spring Boot 启动、Profile、Bean 装配、Web/MCP/Worker 入口 | 只做组装，不含业务规则 |
| `module-identity` | 统一主体、本地用户、密码哈希、Agent/Client 凭据、JWT 生命周期 | 是唯一认证并构建 `PrincipalContext` 的模块 |
| `module-authorization` | Permission、RBAC、Scope、资源 ACL、Tool Allowlist | REST/MCP/Worker 共用；列表权限下推到数据库 |
| `module-organization` | 组织、项目、成员、业务角色与作用域 | P0-B 提供授权与组织标签所需的最小 Query Port |
| `module-taxonomy` | 字典、平台/组织受控标签、国际化 | 稳定枚举不进入字典；标签由关联表引用 |
| `module-configuration` | 类型化、可审计、非敏感运行配置 | Secret 不进入配置表 |
| `module-asset` | 资产登记、卡片、受控标签引用、可见性、检索投影、资产内 Discussion | `MODEL`/`DATASET` 为首期类型；Discussion 继承资产授权并复用通知/审计，P1 当前冻结 |
| `module-version` | 版本三元组、发布状态机、审批流转 | 状态机以代码枚举与 DB 约束表达 |
| `module-transfer` | 上传会话、Multipart 预签名、下载票据 | 不代理大文件数据流，只签发与校验 |
| `module-integration-gitea` | Gitea API、Webhook Inbox、Gitea 权限单向投影、对账 | 实现业务模块定义的 Gitea Port |
| `module-integration-minio` | Bucket、预签名签名、对象元信息 | 实现业务模块定义的存储 Port |
| `module-job` | 持久化任务队列、租约、重试、Worker 执行 | 可靠任务统一入口，禁止裸线程/`@Async` 承担 |
| `module-mcp` | MCP Tool/Resource/Prompt 适配 | 复用业务 Application Service，不形成第二套逻辑 |
| `module-audit` | 审计记录、Outbox 事件、操作日志 | 审计追加写、不可被普通管理员改删 |
| `module-notification` | 站内通知、签名 Webhook、渠道适配 | Outbox 驱动；渠道失败不回滚核心事务 |
| `platform-observability` | JSON 日志、指标、Trace、健康与依赖诊断 | 统一注入上下文和脱敏，不承载业务规则 |
| `shared-kernel` | 统一响应、错误码、`PrincipalContext`、ID 生成、分页契约 | 不依赖任何业务模块与基础设施 SDK |

## 2. 模块内分层

每个业务模块内部按 `api / application / domain / infrastructure` 四层组织：

```text
module-xxx/
├─ api             # 协议适配：Controller、MCP Tool、Worker 入口、Request/Response DTO
├─ application     # 应用服务：编排、事务边界、权限调用、审计触发、Command/Query
├─ domain          # 领域：实体、值对象、状态机、不变量、领域事件、Port 接口定义
└─ infrastructure  # 实现：Repository/Mapper、Gitea/MinIO/DVC/身份与 JWT Port、持久化 DO
```

分层职责：

- **api**：只负责协议转换、参数校验、构建上下文并调用 `application`。不含业务规则。
- **application**：负责用例编排、事务边界（仅覆盖 PostgreSQL）、权限判定调用、审计与可靠任务
  投递。返回 Command/Query 结果，不返回持久化实体。
- **domain**：保存状态机、不变量与值对象，定义对外依赖所需的 Port 接口。**不依赖 Spring、
  MyBatis、Gitea/MinIO/DVC SDK 等基础设施细节。**
- **infrastructure**：实现 Repository、外部系统 Port、Mapper 与持久化 DO；外部 SDK DTO 不得
  外溢到 domain。

## 3. 跨模块依赖约束

依赖方向（参见设计第 5.3 节）：

```text
业务模块 → shared-kernel / platform Application API 或 Query Port
platform 实现 → shared-kernel
integration adapter → 业务模块定义的 Port
shared-kernel 禁止依赖任何业务模块与基础设施 SDK
```

强制规则（由 ArchUnit 门禁执行）：

1. **Controller / MCP Tool / Worker 入口不得直接调用 Mapper**，必须经由 Application Service。
2. **MCP Tool 必须复用与 REST 相同的 Application Service**，不得绕过权限或形成第二套业务逻辑。
3. **跨模块禁止引用对方的 Mapper、持久化 DO/Entity 与内部实现类**；跨模块查询通过 Application
   Service、只读 Query Port 或领域事件完成。
4. **Mapper 只能在本模块 `infrastructure` 内使用**。
5. **`domain` 层禁止依赖 Spring/MyBatis/Gitea/MinIO/DVC** 等框架与 SDK。
6. **`shared-kernel` 禁止依赖任何业务模块与基础设施 SDK**；只承载公共契约。
7. **接口禁止直接返回 MyBatis-Plus Entity**；对外只暴露 API DTO 与业务 ID（前缀 + ULID）。
8. **integration adapter 依赖方向单向**：由业务模块定义 Port，integration 模块实现，不反向
   依赖业务模块的内部实现。
9. **认证与授权失败必须默认拒绝**：空 Principal、allow-all 策略和默认全 Scope 只能存在于隔离
   单元测试 Fixture，不得进入可部署 Profile。
10. **治理字段必须复用 taxonomy**：业务模块不得自建字典/标签表、接受未登记自由标签或按展示
    文案分支；资产标签通过 `tagId` 关联。
11. **横切能力只实现一次**：配置、幂等、任务、日志、审计、通知和观测必须调用公共接口，不得在
    业务模块复制实现。
12. **公共模块之间避免循环依赖**：组织模块提供成员关系 Query Port，授权模块消费该 Port；
    其他公共/业务模块只消费授权结果，不反向访问授权持久化实现。

> 上述约束与设计文档第 15.6、15.12 节及 `AGENTS.md` 中的工程规则一致。任何对模块边界的调整
> 都需新增 ADR。

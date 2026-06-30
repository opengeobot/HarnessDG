# 实现状态与 P0 公共底座差距（2026-06-30）

本文是一次带日期的仓库实现快照，用于支持 P0 重基线决策。它不替代 PRD、ADR、OpenAPI 或测试结果；代码继续演进后应新增快照，不回写本文件伪造历史。

## 1. 已实现或部分实现

| 范围 | 证据 | 结论 |
| --- | --- | --- |
| 工程与部署骨架 | Maven/React、Compose、CI、Flyway、ArchUnit、健康检查 | P0-A 基本完成 |
| Shared Kernel | `ApiResponse`、`ApiError`、`ErrorCode`、业务 ID、`PrincipalContext` | 契约存在，但 Principal 尚未接入认证 |
| 资产后端 | 资产领域、Application Service、MyBatis-Plus、显式搜索 SQL、REST | CRUD/检索已实现，尚未满足公共权限与治理要求 |
| Gitea | 条件启用的建仓与卡片写入 Adapter | 默认关闭并有 Noop；无 Saga/Outbox/对账 |
| 资产前端 | 列表、搜索、创建、删除、统一 API Client | 已调用真实资产接口，但治理字段和权限仍是临时实现 |
| 可观测基础 | Actuator 健康、PostgreSQL 依赖摘要、requestId/traceId | 未形成结构化日志、指标、Trace 和 Dashboard 闭环 |

## 2. 阻塞性差距

| 能力 | 当前代码事实 | P0-B 要求 |
| --- | --- | --- |
| 认证与用户 | 无 Spring Security；HTTP Filter 建立空主体；无用户/凭据表 | PostgreSQL 本地用户、JWT、禁用/锁定/密码与 Token 生命周期 |
| 授权 | `AssetAccessPolicy` 返回全部可见性；系统端点无管理员保护 | 默认拒绝、统一 `AuthorizationService`、数据库权限下推 |
| 前端权限 | `PermissionProvider` 默认注入全部 Scope | 从认证会话获得真实 Scope，未认证进入登录流程 |
| 字典 | License/Framework/Task/Format/Modality 为自由字符串 | 统一字典、itemCode 校验、停用回显、国际化与审计 |
| 标签 | `asset.tags` 为 JSONB；表单允许自由输入 | 平台/组织受控标签、`asset_tag` 关联、`tagIds` 写契约 |
| Owner | `asset.owners` 为字符串 JSON 数组 | 引用 Principal/Team ID，并按作用域授权 |
| 日志与审计 | 默认文本日志，仅少量 Logger；audit 模块为空 | JSON 日志、上下文注入、脱敏、追加写审计与查询权限 |
| 配置/幂等/任务/通知 | 仅设计或包骨架 | 公共持久化实现、管理入口、可靠性和故障路径测试 |
| 跨系统一致性 | `createAsset` 的事务内直接调用 Gitea | 事务外编排、Saga/Outbox、幂等、补偿和对账 |
| 契约 | OpenAPI 已含资产端点，但无安全方案和公共管理契约 | P0 目标契约、权限/错误/分页/幂等与实现状态标记 |

## 3. 数据与兼容性

- `V1__baseline.sql`、`V2__asset_catalog.sql` 视为已经应用，禁止修改。
- 后续以 `V3+` 新增 IAM、授权、组织、字典、标签、配置、任务、审计、通知等表。
- 资产标签 JSON、Owner 字符串和治理字段的回填必须先清点再迁移；未解析数据不得通过放宽约束继续写入。
- 现有资产 API/前端在 P1 整改前标记为部分实现，不得作为已经满足授权与审计要求的生产能力。

## 4. 2026-06-30 验证快照

| 检查 | 结果 |
| --- | --- |
| 后端 `mvnw verify` | 使用 JDK 21 通过；25 个单元/Web 测试通过 |
| PostgreSQL Testcontainers IT | Docker 环境不可用，3 个集成测试全部跳过 |
| 前端 `pnpm lint` | 通过 |
| 前端 `pnpm typecheck` | 通过 |
| 前端 `pnpm build` | 通过；存在单包体积大于 500 kB 警告 |
| `docker compose config --quiet` | 通过 |
| Compose 业务 E2E | 未运行；现有 Verify 仍只覆盖 V01-V03 |

因此当前构建成功不能解释为 P0-B 或 P1 验收完成。

## 5. 目标契约兼容性检查

目标 P0-B OpenAPI 相对当前 HEAD 检测到以下有意的破坏性收紧：

- `GET /assets`、`/models`、`/datasets` 的 `tag` 查询参数替换为 `tagId`；
- `CreateAssetRequest.tags`、`UpdateAssetRequest.tags` 替换为 `tagIds`；
- 既有业务端点由无全局认证约束收紧为默认 Bearer JWT。

这些变化由 ADR-0002 接受，仅能在 P1 冻结整改中连同后端、前端、测试和 V3+ 数据迁移同步落地。

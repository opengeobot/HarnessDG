# P0-B 公共平台底座追踪矩阵

> 依据：设计文档第 5、11、13、15、16 章及 ADR-0002
> 状态：目标基线；“目标”不表示代码已经实现

本矩阵用于检查每项公共能力是否同时覆盖模块、数据、API、权限、审计、管理端和验收。P0-B 实施任务必须更新对应行的具体 Migration、Schema、测试类和 Compose 用例；不得只完成其中一列就声明能力完成。

| 能力 | 模块/公共接口 | 目标表 | REST 契约 | 权限/审计 | 页面 | P0-B 最低验收 |
| --- | --- | --- | --- | --- | --- | --- |
| 本地用户与 Principal | identity / `PrincipalContext` | `iam_principal`、`iam_user` | `/auth/*`、`/me`、`/system/users`、`/system/principals` | `user:*`；登录失败、创建、禁用、密码变更/重置 | `/login`、`/profile`、`/admin/users` | 登录、锁定、禁用、强制改密、主体上下文 |
| JWT 与凭据 | identity / Token Service | `iam_token` | 登录、刷新、登出 | Token 创建、刷新重放、吊销 | 登录/凭据管理 | 非对称签名、`kid`、过期、轮换、重放拒绝、密钥不入日志 |
| Agent/Service 身份 | identity | `iam_agent`、`iam_token`、`iam_agent_tool` | `/system/agents`、Tool Allowlist | `agent:register`、`agent:authorize`；注册/禁用/授权 | `/admin/agents` | 独立凭据、禁用失效、最小 Scope、白名单 |
| 组织与项目作用域 | organization / Membership Query Port | `organization`、`project`、`organization_member` | `/system/organizations/**` | `organization:manage`、`project:*`；成员/项目变更 | `/admin/organizations`、`/admin/projects` | 成员隔离、越权拒绝、组织标签作用域来源 |
| RBAC、Scope 与 ACL | authorization / `AuthorizationService` | `iam_role`、`iam_permission`、`iam_role_permission`、`iam_role_binding`、`iam_resource_acl` | `/system/roles`、`permissions`、`role-bindings`、`resource-acls` | `authorization:*`；全部绑定和 ACL 变更 | `/admin/roles`、`/admin/permissions` | 默认拒绝、数据库权限下推、防资源枚举 |
| 字典与国际化 | taxonomy / Dictionary API | `system_dict_type`、`system_dict_item`、`system_i18n_message` | `/system/dictionaries/**` | `dictionary:*`；字典项增改停用 | `/admin/dictionaries` | itemCode 校验、停用回显、zh-CN/en-US、缓存版本 |
| 平台/组织标签 | taxonomy / Tag API | `system_tag`、`asset_tag` | `/system/tags/**` | `tag:*`；创建、更新、启停 | `/admin/tags` | 作用域唯一、自由标签拒绝、停用历史回显 |
| 配置 | configuration / Typed Config API | `system_config` | `/system/configurations/**` | `system:configure`；配置变更 | `/admin/configurations` | 类型/校验/版本、热更新提示、Secret 拒绝 |
| ID、上下文、响应、异常 | shared-kernel | 无独立业务表 | 全部 API | 统一错误码与 request/trace 关联 | 全局错误体验 | ID 前缀、响应一致性、错误脱敏、上下文清理 |
| 幂等与可靠任务 | job / Idempotency & Job API | `api_idempotency`、`job_task`、`job_attempt` | `Idempotency-Key`、`/system/jobs/**` | `job:*`；人工重试/取消 | `/admin/jobs` | 重放不重复、租约恢复、退避、Dead、并发领取 |
| 结构化日志与审计 | audit + observability / `AuditService` | `audit_log` | `/system/audit-logs` | `audit:read`；必审计事件追加写 | `/admin/audit-logs` | JSON 字段、Secret/JWT 脱敏、成功/失败/拒绝 100% 记录 |
| 通知与外发 Webhook | notification / Notification API | `notification`、`webhook_delivery`、`outbox_event` | `/system/notifications/**` | `notification:read`；人工重发/配置变更审计 | `/admin/notifications` | 站内信、签名 Webhook、失败重试、SSRF 防护 |
| 指标、Trace、健康与诊断 | platform-observability | 指标不以业务表代替 | `/system/metrics/summary`、`/system/dependencies`、Actuator | `system:observe`；敏感诊断不外泄 | `/admin/dependencies`、观测 Dashboard | Prometheus Target、Trace 贯通、健康分层、告警 |
| 公共管理端 | frontend admin + shared API/permission/i18n | 不直接访问数据库 | 只经上述公共 API | 路由/按钮权限仅作体验，后端最终判断 | 上述全部管理页面 | Loading/Empty/Error/Retry、无全 Scope、Token 不进 LocalStorage |

## P0-B 出口检查

1. 每行至少有一条正常、边界、失败和越权自动化测试。
2. 所有写操作声明幂等、错误码和审计事件；可靠异步工作只进入持久化任务。
3. OpenAPI、事件 Schema、Flyway、前后端类型、Fixture、Verify 和 Runbook 同步。
4. Compose 验证覆盖 JWT 生命周期、权限过滤、字典/标签、日志/审计、任务/通知和观测。
5. 任何未完成行都会阻止 P0-B 退出及 P1 新功能开发。

## 实现状态（2026-07-01）

> 上表为“目标基线”不表示已实现；本节按行标注 Task 1-15 完成后的实际落地证据。
> 迁移文件位于 `backend/src/main/resources/db/migration/`；Verify 用例位于 `deploy/compose/scripts/verify.{ps1,sh}`。

| 能力 | 迁移/关键表 | 后端测试类（`backend/src/test/...`） | Verify 用例 | 状态 |
| --- | --- | --- | --- | --- |
| 本地用户与 Principal | `V3__identity.sql`：`iam_principal`、`iam_user` | `identity/api/AuthControllerTest`、`identity/domain/LocalUserTest`、`identity/IdentityIT` | V05、V08 | 已实现 |
| JWT 与凭据 | `V3__identity.sql`：`iam_token` | `platform/security/JwtTokenServiceTest`、`bootstrap/security/JwtAuthenticationFilterTest`、`bootstrap/security/SecurityFilterChainTest` | V05 | 已实现 |
| Agent/Service 身份 | `V3__identity.sql`：`iam_agent`；`V4`：`iam_agent_tool` | `identity/IdentityIT`、`authorization/AuthorizationIT` | V05（fail-closed） | 已实现 |
| 组织与项目作用域 | `V5__organization.sql`：`organization`、`project`、`organization_member` | `organization/application/*ApplicationServiceTest`、`organization/api/OrganizationControllerTest`、`organization/OrganizationIT` | V06（默认拒绝） | 已实现 |
| RBAC、Scope 与 ACL | `V4__authorization.sql`：`iam_role`、`iam_permission`、`iam_role_permission`、`iam_role_binding`、`iam_resource_acl` | `authorization/application/AuthorizationServiceTest`、`authorization/api/AuthorizationManagementControllerTest`、`authorization/AuthorizationIT` | V05-V11（默认拒绝） | 已实现 |
| 字典与国际化 | `V6__taxonomy_dictionary.sql`：`system_dict_type`、`system_dict_item`、`system_i18n_message` | `taxonomy/dictionary/**/*Test`、`taxonomy/TaxonomyIT` | V07 | 已实现 |
| 平台/组织标签 | `V7__taxonomy_tag.sql`：`system_tag`、`asset_tag`（关联在 `V12` 完成） | `taxonomy/tag/**/*Test`（含 `TagValidationServiceTest` 自由标签拒绝） | V07 | 已实现 |
| 配置 | `V8__configuration.sql`：`system_config` | `configuration/**/*Test`、`configuration/ConfigurationIT` | V06/V11（`system:configure`/`observe`） | 已实现 |
| ID、上下文、响应、异常 | shared-kernel（无独立表） | `shared/SharedKernelTest`、`shared/logging/SensitiveDataMaskerTest`、`arch/LayeredArchitectureTest` | V05（错误体一致性） | 已实现 |
| 幂等与可靠任务 | `V9__job.sql`：`api_idempotency`、`job_task`、`job_attempt` | `job/application/IdempotencyServiceTest`、`job/application/BackoffCalculatorTest`、`job/infrastructure/JobWorkerTest` | V09 | 已实现 |
| 结构化日志与审计 | `V10__audit.sql`：`audit_log` | `audit/application/AuditServiceTest`、`audit/domain/AuditRepositoryImmutabilityTest`、`shared/logging/SensitiveDataMaskerTest` | V08（脱敏） | 部分（asset/taxonomy/authorization/configuration 已接入 AuditService；identity 登录审计接入待统一） |
| 通知与外发 Webhook | `V11__notification.sql`：`notification`、`webhook_delivery`、`outbox_event` | `notification/application/NotificationServiceTest`、`notification/infrastructure/WebhookSignerTest`、`notification/infrastructure/SsrfGuardTest` | V10 | 已实现 |
| 指标、Trace、健康与诊断 | 无业务表（Micrometer/OTel） | `platform/observability/**/*Test`（`MetricsSummaryServiceTest`、`SystemDependencyServiceTest`、`SystemDiagnosticsControllerTest`） | V11 | 已实现 |
| 公共管理端 | 前端（不直接访问 DB） | 前端 `pnpm lint/typecheck/build`（见 `frontend/`） | 手动 UI；Verify 走 API 层 | 已实现 |
| 资产目录（冻结整改） | `V12__asset_governance.sql`：`asset_tag`、治理引用列 | `asset/**/*Test`、`asset/AssetCatalogIT` | V06（下推过滤，登录后可扩展） | 部分（P1 冻结整改） |

### Verify 用例与能力对应（`deploy/compose/scripts/verify.{ps1,sh}`）

| 用例 | 覆盖 | 服务不可用行为 |
| --- | --- | --- |
| V01 | `docker compose config --quiet` | 始终执行 |
| V02 | postgres/minio/gitea/backend 健康 | 容器未起时 SKIP |
| V03 | 四个 Bucket 存在且非匿名 | minio 未起时 SKIP |
| V04 | Flyway V1-V12 成功迁移、关键表存在 | postgres 未起时 SKIP |
| V05 | 登录签发 JWT、`/me` 200、无 Token→401（fail-closed） | backend 未起时 SKIP |
| V06 | `/system/audit-logs`、`/metrics/summary` 无 Token→401、越权→403 | backend 未起时 SKIP |
| V07 | `/system/dictionaries`、`/tags` 无 Token→401、越权→403 | backend 未起时 SKIP |
| V08 | `audit_log` 存在、去检测无明文口令泄漏（脱敏恒定不变式） | postgres 未起时 SKIP |
| V09 | `/system/jobs` 默认拒绝 | backend 未起时 SKIP |
| V10 | `/system/notifications` 默认拒绝 | backend 未起时 SKIP |
| V11 | `/actuator/health` 200、`/system/dependencies` 默认拒绝 | backend 未起时 SKIP |

> 说明：Bootstrap 管理员默认仅持 `ADMIN_SCOPES`（user/authorization/agent）且首登强制改密，
> 因此 V06-V11 以“无 Token→401、越权 Token→403”验证 fail-closed 默认拒绝语义，
> 而非以管理员令牌断言 200。完整“有权限 200”路径由带具体 Scope 的 IT（如 `AuthorizationIT`）覆盖。
> SKIP 不等于 PASS；未启动全栈时相关用例只标 SKIP，脚本不冒充通过。

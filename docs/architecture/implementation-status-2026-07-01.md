# 实现状态快照（2026-07-01）

> 本文是 P0-B 平台底座整改在 Task 15 完成时的带日期实现快照，用于支持退出评审。
> 它不替代 PRD、ADR、OpenAPI 或测试结果，也不回写更早的 `implementation-status-2026-06-30.md`。
> 作者：AxeXie

## 1. 范围与结论

P0-B 公共平台底座（identity、authorization、organization、taxonomy、configuration、job、audit、
notification、platform-observability）以及前端公共管理端已按 Task 1-14 落地，Task 15 补齐 Compose/CI/
文档与本地验证。当前后端 `mvnw verify` 与前端 `pnpm lint/typecheck/build` 通过，OpenAPI 契约通过 Redocly lint。
仍有已知残余（identity 登录审计接入未统一、Testcontainers IT 依赖 Docker、全栈 E2E 需实际 `docker compose up`）。
不得据此声称 P1+ 业务能力已实现。

## 2. 各模块迁移 / 端点 / 测试

| 模块 | Flyway 迁移 | 关键端点前缀 | 代表测试类 |
| --- | --- | --- | --- |
| identity | `V3__identity.sql` | `/api/v1/auth/*`、`/me`、`/system/users`、`/system/agents`、`/system/principals` | `AuthControllerTest`、`LocalUserTest`、`JwtTokenServiceTest`、`IdentityIT` |
| authorization | `V4__authorization.sql` | `/system/roles`、`/permissions`、`/role-bindings`、`/resource-acls` | `AuthorizationServiceTest`、`AuthorizationManagementControllerTest`、`AuthorizationIT` |
| organization | `V5__organization.sql` | `/system/organizations/**` | `OrganizationApplicationServiceTest`、`ProjectApplicationServiceTest`、`OrganizationIT` |
| taxonomy（字典/标签） | `V6__taxonomy_dictionary.sql`、`V7__taxonomy_tag.sql` | `/system/dictionaries/**`、`/system/tags/**` | `DictionaryApplicationServiceTest`、`TagValidationServiceTest`、`TaxonomyIT` |
| configuration | `V8__configuration.sql` | `/system/configurations/**` | `ConfigurationApplicationServiceTest`、`PlatformConfigServiceTest`、`ConfigurationIT` |
| job | `V9__job.sql` | `/system/jobs/**` | `IdempotencyServiceTest`、`BackoffCalculatorTest`、`JobWorkerTest` |
| audit | `V10__audit.sql` | `/system/audit-logs` | `AuditServiceTest`、`AuditRepositoryImmutabilityTest` |
| notification | `V11__notification.sql` | `/system/notifications/**` | `NotificationServiceTest`、`WebhookSignerTest`、`SsrfGuardTest` |
| platform-observability | 无业务表 | `/system/metrics/summary`、`/system/dependencies`、Actuator | `MetricsSummaryServiceTest`、`SystemDependencyServiceTest`、`SystemDiagnosticsControllerTest` |
| asset（冻结整改） | `V12__asset_governance.sql` | `/api/v1/assets`、`/models`、`/datasets` | `AssetApplicationServiceTest`、`AssetControllerTest`、`AssetCatalogIT` |
| shared / security | — | 全局 | `SecurityFilterChainTest`、`JwtAuthenticationFilterTest`、`SensitiveDataMaskerTest`、`LayeredArchitectureTest` |

## 3. Task 15 交付物

- **CI**：`.github/workflows/ci.yml` 新增 `contracts` job，用 Redocly CLI 对 `contracts/openapi/aihub-v1.yaml`
  做 lint（强制门禁）与 breaking-change diff（对 base ref，`continue-on-error` 作为评审信号）；
  配置见 `contracts/openapi/redocly.yaml`（recommended 规则集，仅将 `no-unused-components` 降级为告警）。
  既有 backend/frontend/compose 三个 job 保持不变。
- **Compose 验收**：`deploy/compose/scripts/verify.{ps1,sh}` 由 V01-V03 扩展到 V01-V11，覆盖数据库迁移、
  JWT 生命周期（fail-closed）、权限过滤、字典/标签、审计脱敏、持久化任务、通知、观测与诊断；
  服务未启动时相关用例输出 SKIP（非 PASS），仅真实断言失败才 FAIL。
- **Compose fixtures/env**：`compose.yaml` 与 `.env.example` 新增 bootstrap 管理员引导变量（开发默认
  `admin`/`change-me-admin-01`，生产须用 Secret 文件），并对本地 http 关闭刷新 Cookie Secure 标志以便 verify。
- **文档**：更新 `docs/architecture/p0-platform-foundation-traceability.md`（新增“实现状态（2026-07-01）”与
  Verify 用例映射）与 `docs/runbooks/compose.md`（V04-V11 运行方式与 SKIP 语义）；新增本快照。
- **契约修复**：将 `AssetView`/`AssetSummary` 的 `organizationId`/`projectId` 由 OpenAPI 3.0 风格
  `nullable: true` 修正为 3.1 的 `type: [string, 'null']`（此前 4 处 lint error）。

## 4. 契约兼容性

沿用 2026-06-30 快照第 5 节列出的、由 ADR-0002 接受的有意破坏性收紧（`tag`→`tagId`、`tags`→`tagIds`、
业务端点默认 Bearer JWT）。本次仅做 3.1 语法修正与不阻断的 diff 集成，未引入新的破坏性契约变更。

## 5. 已知风险与后续

- **identity 登录/凭据审计接入未统一**：asset/taxonomy/authorization/configuration 已接入权威 `AuditService`，
  identity 登录成功/失败、Token 生命周期事件尚未统一写入 `audit_log`；V08 因此只校验脱敏恒定不变式，
  不断言登录事件计数。属 P0-B 收尾待办。
- **Testcontainers 集成测试依赖 Docker 网络**：`*IT` 在无 Docker 的 CI/本地会跳过；跳过的 IT 不能视为通过。
- **全栈 E2E 需实际 `docker compose up`**：V05-V11 的“执行”结果只有在全栈就绪后才产生；仅 `docker compose config`
  通过时这些用例为 SKIP。
- **前端单包体积**：`pnpm build` 存在 >500 kB chunk 警告（非阻断）。

## 6. 2026-07-01 验证快照

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 后端 verify | `mvnw -o verify`（`MAVEN_OPTS=-Xmx640m`） | BUILD SUCCESS；206 单元/Web 测试通过，26 个 Testcontainers IT 因无 Docker 网络 Skipped |
| 前端 lint | `pnpm lint` | 通过（exit 0） |
| 前端 typecheck | `pnpm typecheck` | 通过（exit 0） |
| 前端 build | `pnpm build` | 通过（exit 0）；单包 >500 kB 警告（非阻断） |
| Compose 配置 | `docker compose config --quiet` | 通过（exit 0） |
| OpenAPI lint | `npx @redocly/cli lint --config contracts/openapi/redocly.yaml ...` | 通过（exit 0，1 warning：TagReference 未引用，已降级为告警） |
| Verify 脚本 | `deploy/compose/scripts/verify.ps1`（未启动全栈） | exit 0：V01 PASS；V02-V11 SKIP（各附原因，未冒充通过） |

> 未实际执行 `docker compose up` 全栈（镜像拉取/构建耗时且非本任务门禁要求），故 V02-V11 为 SKIP 而非 PASS。
> 脚本已具备全栈就绪后逐项 PASS 的能力，SKIP 不等于通过。

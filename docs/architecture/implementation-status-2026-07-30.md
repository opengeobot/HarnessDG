# 实现状态快照（2026-07-30）——全量差距分析

> 本文是 2026-07-02 快照之后基于全量代码与设计文档差距分析生成的最新实现状态快照。
> 不回写更早快照（2026-06-30 / 2026-07-01 / 2026-07-02 保持不变）。
> 作者：AxeXie

## 1. 本轮范围与变更摘要

在 2026-07-02 快照基础上，执行了完整的"代码 vs 设计文档"差距分析，覆盖 P0-B 至 P5 全阶段。主要发现：

1. **差距分析文档**：生成 `.trae/specs/gap-analysis-code-vs-design/gap-list.md`，逐项对比 PRD、AI Spec、ADR 与实际代码，给出 G0-G3 差距等级与证据状态。
2. **文档不一致清理**：识别 `open-questions.md` 全文重复、`decision-log.md` DRAFT 状态标记、`p0b-exit-catalog.md` PROPOSED 与 spec Task 已完成的矛盾。
3. **核心结论**：P0-B 至 P4 的代码功能已基本实现，但 73 项 P0-B MUST 场景全部 Evidence=0（仅 G1），无法声明 P0-B 退出。P1-P4 大量功能处于 G1/G2（代码已实现但缺 E4 验证）状态。G3 项（CLI 工具、性能基准、安全扫描 CI、备份恢复演练）完全未实现。

## 2. P0-B 公共底座模块状态

P0-B 全部功能的 Java/前端代码已基本实现，213+ 单元测试覆盖主要逻辑。核心差距是缺少按 `p0b-exit-catalog.md` 生成的 E3/E4 级验证证据。

| 模块 | Flyway 迁移 | 关键表 | 代表测试 | Evidence |
| --- | --- | --- | --- | --- |
| identity（本地用户/Principal） | `V3__identity.sql` | `iam_principal`、`iam_user` | `AuthControllerTest`、`LocalUserTest`、`IdentityIT`、`AuthenticationApplicationServiceTest` | **G1** 缺 E4 Compose |
| identity（JWT 与凭据） | `V3__identity.sql` | `iam_token` | `JwtTokenServiceTest`、`JwtAuthenticationFilterTest`、`SecurityFilterChainTest` | **G1** 缺 kid 轮换 E4 |
| identity（Agent/Service 身份） | `V3__identity.sql` + `V4` | `iam_agent`、`iam_agent_tool` | `IdentityIT`、`AuthorizationIT` | **G1** 缺高风险权限拒绝 E4 |
| authorization（RBAC/Scope/ACL） | `V4__authorization.sql` | `iam_role`、`iam_permission`、`iam_role_permission`、`iam_role_binding`、`iam_resource_acl` | `AuthorizationServiceTest`、`AuthorizationIT` | **G1** 缺组合授权/防枚举 E4 |
| organization（组织/项目/成员） | `V5__organization.sql` | `organization`、`project`、`organization_member` | `OrganizationApplicationServiceTest`、`OrganizationIT` | **G1** 缺成员隔离 E4 |
| organization（Team 与 Owner） | `V19__team.sql` | `team`、`team_member` | `TeamControllerTest` | **G1** Team-Owner 关联需验证 |
| taxonomy（字典与 i18n） | `V6__taxonomy_dictionary.sql` | `system_dict_type`、`system_dict_item`、`system_i18n_message` | `DictionaryApplicationServiceTest`、`TaxonomyIT` | **G1** 缺停用回显 E4 |
| taxonomy（受控标签） | `V7__taxonomy_tag.sql` | `system_tag`、`asset_tag` | `TagValidationServiceTest`（自由标签拒绝） | **G1** 缺自由标签拒绝 E4 |
| configuration（类型化配置） | `V8__configuration.sql` | `system_config` | `ConfigurationApplicationServiceTest`、`ConfigurationIT` | **G1** 缺并发冲突 E4 |
| job（幂等与可靠任务） | `V9__job.sql` + `V20` | `api_idempotency`、`job_task`、`job_attempt` | `IdempotencyServiceTest`、`JobWorkerTest`、`BackoffCalculatorTest` | **G1** 缺多 Worker 并发 E4 |
| audit（结构化日志与审计） | `V10__audit.sql` | `audit_log` | `AuditServiceTest`、`AuditRepositoryImmutabilityTest`、`SensitiveDataMaskerTest` | **G1** 缺审计不可篡改 E4 |
| notification（通知与 Webhook） | `V11__notification.sql` | `notification`、`webhook_delivery`、`outbox_event` | `NotificationServiceTest`、`WebhookSignerTest`、`SsrfGuardTest` | **G1** 缺 SSRF 拒绝 E4 |
| platform-observability | 无业务表 | — | `MetricsSummaryServiceTest`、`SystemDiagnosticsControllerTest` | **G1** 缺 Prometheus Target UP E4 |
| frontend（公共管理端） | — | — | 前端 `pnpm lint/typecheck/build` | **G1** 缺 Playwright E2E 截图 |

### P0-B 73 项 MUST 场景 Evidence 汇总

| 类别 | 场景数 | 代码已实现 | Evidence 完整 | 完全未实现 |
| --- | --- | --- | --- | --- |
| 工程与契约门禁 | 6 | 6 | 0 | 0 |
| Bootstrap/用户/JWT | 12 | 12 | 0 | 0 |
| Agent/Service/API Client | 5 | 5 | 0 | 0 |
| 组织/Team/授权 | 9 | 9 | 0 | 0 |
| 字典/标签/国际化 | 6 | 6 | 0 | 0 |
| 配置/幂等/任务 | 11 | 11 | 0 | 0 |
| 日志/审计 | 5 | 5 | 0 | 0 |
| 通知/Webhook | 5 | 5 | 0 | 0 |
| 可观测性 | 5 | 5 | 0 | 0 |
| 公共管理端 | 6 | 6 | 0 | 0 |
| **合计** | **73** | **73** | **0** | **0** |

**结论**：P0-B 代码层已完成，但 73 项 MUST 场景全部处于"未验证"状态。没有 E3/E4 Evidence Manifest，无法声明 P0-B 退出。

## 3. P1 资产目录状态

P1 资产 CRUD + Discussion + Gitea 建仓已实现。代码超前于 P0-B 退出，但质量取决于后续 E4 验证。

| 需求 | 代码实现 | 迁移 | 差距 |
| --- | --- | --- | --- |
| 资产坐标与 Team Owner (`REQ-AST-001`) | `Asset` 聚合已实现 | `V12` + `V14` | **G1** 坐标格式 `aih://` 前缀校验需确认 |
| 创建资产 + Gitea 建仓 Saga (`REQ-AST-002`) | `RepositoryProvisionJobHandler` + `GiteaAssetRepositoryProvisioner` | `V12` + `V14` | **G1** Saga 补偿/恢复缺 E4 |
| 权限过滤搜索 + Facet (`REQ-AST-003`) | `AssetSearchDao` + Facet 统计 | `V14`（pg_trgm 全文索引） | **G2** P95<=500ms 缺基准测试 |
| 资产详情与 Card 投影 (`REQ-AST-004`) | `AssetApplicationService` | — | **G2** Card 安全渲染（防 XSS）需验证 |
| 更新元数据（乐观锁） (`REQ-AST-005`) | `AssetApplicationService` | — | **G1** 并发冲突 E4 缺 |
| Owner/Maintainer/ACL (`REQ-AST-006`) | Team 关联已实现 | `V19__team.sql` | **G2** "至少一个 Team" 约束需验证 |
| 弃用/归档/恢复/删除 (`REQ-AST-007`) | `AssetController` 端点 | — | **G1** 物理回收 Job 缺 Evidence |
| Gitea 仓库映射与对账 (`REQ-AST-008`) | `WebhookInboxController` + `AssetRepositoryReconciler` | `V18` + `V22` | **G1** Reconciler 漂移修复缺 E4 |
| 资产目录前端 (`REQ-AST-009`) | `AssetsPage`/`AssetDetailPage`/`CreateAssetModal` 等 | — | **G1** 缺 E2E Evidence |
| Dataset 多值分类与 Facet (`REQ-DST-TAX-001`) | Facet 统计已实现 | — | **G1** |
| Dataset Card + Preview (`REQ-DST-DETAIL-001`) | `PreviewPanel` | — | **G2** 安全预览限制需验证 |
| Discussion/Comment (`REQ-DST-DISC-001`) | `DiscussionApplicationService` + `CommentController` | `V15__discussion.sql` | **G1** 权限继承缺 E4 |

## 4. P2 版本与数据面状态

版本管理、上传/下载、Preview、DVC 配置代码框架已实现，但关键路径缺实际 Compose/DVC 验证。

| 需求 | 代码实现 | 迁移 | 差距 |
| --- | --- | --- | --- |
| 资产版本草稿 (`REQ-VER-001`) | `VersionController` + `VersionApplicationService` | `V16__version_transfer.sql` | **G1** SemVer + 日期版本 E4 缺 |
| Manifest Schema/规范化 (`REQ-MNF-001`) | `Manifest` 领域模型 | — | **G2** Golden Vector 一致性缺验证 |
| Web Upload Session (`REQ-UPL-001`) | `UploadController` + `UploadApplicationService` | `V16`（upload_session/upload_file/upload_part） | **G1** 20 GiB 限额缺验证 |
| Multipart Part 签名与恢复 (`REQ-UPL-002`) | `UploadMaterializeJobHandler` | `V16` | **G1** Part 恢复 E4 缺 |
| 上传校验 + DVC/Git 物化 (`REQ-UPL-003`) | Worker Handler | — | **G2** 实际 DVC 往返 E4 缺 |
| 授权下载票据 (`REQ-DL-001`) | `DownloadController` + `DownloadApplicationService` | — | **G1** TTL 过期 E4 缺 |
| 安全预览 (`REQ-PRE-001`) | `PreviewController` + `PreviewJobHandler` | `V16`（asset_preview） | **G2** 格式覆盖与 100行/50列/1MiB 限制需验证 |
| DVC 配置 (`REQ-DVC-001`) | `DvcConfigurationController` + `DvcConfigurationService` | — | **G2** 实际 DVC 往返验证缺 |
| CLI 工具 (`REQ-CLI-001`) | **未发现 CLI 项目代码** | — | **G3 完全未实现** |

## 5. P3 发布治理状态

Review/Publish 代码框架存在，Saga 完整性与补偿恢复链路待验证。

| 需求 | 代码实现 | 迁移 | 差距 |
| --- | --- | --- | --- |
| 发布前校验 (`REQ-VAL-001`) | `ReviewController` | `V17__release_governance.sql`（validation_report） | **G2** Pickle 高风险标记需验证 |
| 提交/审批/驳回 (`REQ-REV-001`) | `ReviewController` + `PublishApplicationService` | `V17`（publish_request, review_decision） | **G2** 职责分离/自审拒绝需验证 |
| 不可变发布 Saga (`REQ-PUB-001`) | `PublishApplicationService` 框架 | — | **G2** Saga 补偿/恢复缺 E4 |
| 已发布版本不可变 (`REQ-IMM-001`) | DB 约束需确认 | `V17` | **G2** 触发器/约束实际阻止覆盖需验证 |
| 版本弃用与归档 (`REQ-DEP-001`) | 端点需确认 | — | **G2** |

## 6. P4 Agent / MCP 状态

MCP Streamable HTTP + Tool Catalog + Agent API 已实现，但授权链路和人工闸门缺 E4 验证。

| 需求 | 代码实现 | 差距 |
| --- | --- | --- |
| MCP Streamable HTTP (`REQ-MCP-001`) | `McpController`（`POST /mcp`、initialize/tools/list/tools/call） | **G1** Nginx 不缓冲配置需验证 |
| Tool Catalog 双重授权 (`REQ-MCP-002`) | `McpToolCatalog`（8 层授权） | **G1** 8 层授权 E4 缺 |
| 只读 Tools (`REQ-MCP-003`) | `asset_search`/`asset_get` 已实现 | **G1** |
| 版本与下载 Tools (`REQ-MCP-004`) | `asset_list_versions`/`asset_get_version`/`asset_request_download` | **G1** downloadHandle 不暴露 URL 需验证 |
| 写 Tools 与人工闸门 (`REQ-MCP-005`) | `ConfirmationToken` 已实现 | **G1** 人工闸门 E4 缺 |
| MCP Resources (`REQ-MCP-006`) | `McpResourceHandler` | **G1** |
| Agent Skill 接入包 (`REQ-AGT-002`) | `aihub-agent-integration/SKILL.md` | **G1** 兼容性验证缺 |
| Agent 友好 OpenAPI (`REQ-API-AGT-001`) | `contracts/openapi/aihub-agent-v1.yaml` | **G1** |
| 30 分钟接入 (`REQ-COMP-AGT-001`) | — | **G3 未验证** |

## 7. P5 质量与运维状态

| 需求 | 代码实现 | 差距 |
| --- | --- | --- |
| Gitea Webhook Inbox (`REQ-INBOX-001`) | `WebhookInboxController` + `WebhookInboxHandler` | **G1** 时间窗口验证需确认 |
| 全量对账与安全修复 (`REQ-REC-001`) | `AssetRepositoryReconciler` + `MinioStorageReconciler` + `SessionStagingReconciler` | **G2** 分类 (AUTO_REPAIR/MANUAL_REVIEW/SECURITY_INCIDENT) 需验证 |
| 备份/恢复与演练 (`REQ-BKR-001`) | `deploy/compose/scripts/backup.sh` + `restore.sh` | **G3** 实际恢复演练未执行 |
| 性能/容量回归 (`REQ-PERF-001`) | **未发现性能基准测试代码** | **G3 完全未实现** |
| 安全验证 (`REQ-SEC-001`) | **未发现安全扫描配置** | **G3** CI 无 SAST/安全扫描步骤 |
| 运行 Dashboard/告警/Runbook (`REQ-OPS-001`) | `deploy/compose/grafana/` Dashboard JSON | **G1** 实际告警触发 E4 缺 |
| 发布候选全量审计 (`REQ-REL-001`) | — | **G3 未实现** |

## 8. 关键差距汇总

### 8.1 差距等级分布

| 等级 | 数量 | 说明 |
| --- | --- | --- |
| **G0**（已实现+Evidence） | 0 | 暂无 |
| **G1**（代码实现，缺 Evidence） | ~73 | 覆盖全部 P0-B MUST 场景 + P1 大部分 + P4 MCP |
| **G2**（部分实现） | ~8 | 性能基准、Saga 补偿恢复、安全预览限制、Card 渲染、Manifest Golden Vector |
| **G3**（完全未实现） | ~4 | CLI 工具、性能基准测试、安全扫描 CI、备份恢复演练 |
| **G-doc**（文档问题） | 5 | open-questions 重复、decision-log DRAFT、快照过期、exit-catalog 状态矛盾 |

### 8.2 G3 阻塞项（必须解决）

| 项目 | 影响 | 建议 |
| --- | --- | --- |
| CLI 工具 (`REQ-CLI-001`) | 外部用户无法通过命令行操作平台 | P2 出口前提，需新建 CLI 项目 |
| 性能基准测试 (`REQ-PERF-001`) | 无法保证 10 万 Asset / P95<=500ms | 建立基准测试框架，纳入 CI 回归 |
| 安全扫描 CI (`REQ-SEC-001`) | 无法检测 SAST/依赖漏洞/Secret 泄露/越权 | CI Pipeline 增加 SAST + 依赖扫描 + Secret 检测 |
| 备份恢复演练 (`REQ-BKR-001`) | 已有脚本但从未验证恢复可行 | 执行一次完整恢复演练并记录 |

### 8.3 代码超前于设计

以下功能在 P0-B 未正式退出前已实现，按 ADR-0002 应冻结业务扩展：

| 功能 | 原定阶段 | 状态 |
| --- | --- | --- |
| 资产 CRUD + Gitea 建仓 + Discussion | P1 | 代码已实现 |
| 版本管理 + 上传/下载 + Preview + DVC | P2 | 代码已实现 |
| Review/Publish 审批 | P3 | 代码已实现 |
| MCP + Agent API | P4 | 代码已实现 |

**风险**：这些功能的实现质量依赖于代码审查和实际 E4 验证。P0-B 退出前，只允许公共底座接入和安全整改，不允许新增业务能力。

## 9. Verify 用例状态 (V01-V12)

| 用例 | 覆盖 | 最近实际运行 | 状态 |
| --- | --- | --- | --- |
| V01 | `docker compose config --quiet` | 2026-07-02 PASS | **PASS** |
| V02 | postgres/minio/gitea/backend 健康检查 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V03 | 四个 Bucket 存在且非匿名 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V04 | Flyway V1-V22 迁移、关键表存在 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V05 | 登录签发 JWT、`/me` 200、无 Token→401 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V06 | `/system/audit-logs`、`/metrics/summary` 无 Token→401、越权→403 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V07 | `/system/dictionaries`、`/tags` 无 Token→401、越权→403 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V08 | `audit_log` 脱敏 + `AUTH_LOGIN_SUCCEEDED` 事件 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V09 | `/system/jobs` 默认拒绝 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V10 | `/system/notifications` 默认拒绝 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V11 | `/actuator/health` 200、`/system/dependencies` 默认拒绝 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |
| V12 | `/system/alerts` 告警列表、DEAD 任务/积压告警 | 2026-07-02 全栈 PASS | **PASS（需 Docker）** |

> 说明：V02-V12 的 PASS 基于 2026-07-02 全栈人工执行。全栈 E2E 尚未纳入 CI 自动化，每次 Compose 变更需手动重新验证。
> 脚本已支持 V1-V22 迁移范围（随 `V21__alerts.sql`、`V22__asset_p1_remediation.sql` 扩展）。
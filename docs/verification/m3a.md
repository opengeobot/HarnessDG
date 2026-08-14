# 阶段 4（interaction + governance）对齐验证报告

- 日期：2026-08-14
- 依据规格：`prd/v1/specs/03-domain-data-model.md` §6、`04-api-contract.md` §5、`06-async-workflows.md` §7、`02-identity-permissions.md` §9、`07-security-nfr-operations.md` SEC-05、`prd/v1/contracts/openapi-v1.yaml`（RelationshipState/Feedback/AuditLog schema）
- 构建证据：`mvn clean verify` BUILD SUCCESS（JDK 21，Testcontainers：PostgreSQL 16 + Redis 7 + Gitea 1.24 + 故障注入隔离库）
- 测试统计：共 **97 个自动化测试全绿**
  - `modules/app` 集成测试 84（较阶段 3 新增 10：InteractionFlowTest 6、GovernanceFlowTest 4）
  - `modules/shared` 单元 4、`modules/identity-access` 单元 3、`modules/arch-tests` ArchUnit 6

## 1. 交付物清单

| 类别 | 内容 |
|---|---|
| 迁移 | V8__interaction（repository_likes/repository_favorites/feedbacks/visit_events 4 表 + 双向/排序索引）、V9__audit_extension（audit_logs 加 organization/idempotency_key/public_id 列 + actor 索引） |
| 实体/仓储 | RepositoryLikeEntity、RepositoryFavoriteEntity、FeedbackEntity（moderationStatus/authorSnapshot/deletedAt）、VisitEventEntity；4 个 Spring Data Repository（likes/favorites 含 `insertIgnore` native `ON CONFLICT DO NOTHING`） |
| 服务 | InteractionService（like/unlike/favorite/unfavorite 幂等 + feedback 创建/cursor 分页 + listMine 三 tab）、VisitRecorder（visitorHash 匿名 HMAC-SHA256 轮换盐 + REQUIRES_NEW outbox 投递）、StatsRebuildService（rebuild/rebuildAll 对账重建）、AuditQueryService（cursor 分页 + ndjson fetchSize 流式导出） |
| 异步 | StatsEventHandler（7 类事件幂等重算式收敛 likes/favorites/visits/downloads/file_count）、CatalogConfiguration 注册 handler、DownloadService（新建会话投递 DownloadSessionIssued + 私有下载审计）、ArtifactUploadWorker/ArtifactFileDeletionWorker（FileCountChanged）、CatalogService.get + BrowseService.listFiles（VisitRecorded 投递） |
| API | RepositoriesController 5 端点（POST/DELETE likes、POST/DELETE favorite、GET/POST feedbacks）、MeController GET /me/repositories（created/likes/favorites）、AdminController GET /admin/audit-logs（json cursor 分页 + ndjson 流式导出双端点）、SecurityConfig（feedbacks 匿名 GET 白名单 + ASYNC/ERROR dispatch 放行） |
| 测试 | InteractionFlowTest（6：并发唯一/收藏/反馈分页/me 三 tab/visit 去重与计数/stats 重建）、GovernanceFlowTest（4：审计分页过滤/ndjson 导出/角色管控/admin retry 闭环）、CatalogTestSupport.awaitStats/statsValue 轮询助手 |

## 2. 规格逐章核对

### 03 §6 互动领域模型

| 规格条目 | 状态 | 证据 |
|---|---|---|
| likes/favorites 强事实表 + UNIQUE 兜底并发（Toggle 不得切换两次） | ✅ | V8 UNIQUE(user_id, repository_id)；InteractionFlowTest.likeIdempotentAndConcurrentUnique：重复 POST 收敛 1、双线程并发 POST 无异常收敛 1 |
| feedbacks 审核状态 + 作者快照 + 软删除 | ✅ | moderation_status CHECK 4 值默认 approved；author_snapshot JSONB；listFeedbacks 过滤 deleted_at 非空 |
| visit 30 分钟窗口去重（03 §6.2） | ✅ | visit_events UNIQUE(repository_id, visitor_hash, window_start)，window_start=floor(epoch/1800)*1800；登录 "u:"+userPublicId、匿名 HMAC-SHA256(盐+当日, IP) 轮换盐；visitDedupAndDownloadsFileCount 同用户两次详情 visits=1、匿名同 IP 两次=1 |
| 计数口径 downloads=成功签发唯一会话、file_count=active 文件 | ✅ | DownloadSessionIssued 仅在幂等键未命中时发布（同 Idempotency-Key 重放计数不变）；file_count 从 file_versions status='active' 重算 |

### 04 §5 API 契约

| 规格条目 | 状态 | 证据 |
|---|---|---|
| RelationshipState(active) / Feedback(id/author/content/createdAt) 视图 | ✅ | POST likes → 200 active:true 幂等；POST feedbacks → 201 author=username |
| GET feedbacks 匿名可读 public + cursor 分页 | ✅ | feedbacksCreateAndPage 匿名 GET 分页命中；陌生人 GET private 仓库反馈 → 404 防枚举 |
| GET /me/repositories tab ∈ {created,likes,favorites} | ✅ | meRepositoriesTabs 三 tab；liked 仓库转 private 后仅 owner 可见（ME-001 过滤） |
| 校验错误语义 | ✅ | content 超长 → 422 VALIDATION_FAILED（GlobalExceptionHandler 统一映射） |
| admin/audit-logs format=json\|ndjson | ✅ | json cursor 分页 + actor/action/resource 过滤；ndjson 流式导出，content-type=application/x-ndjson |

### 06 §7 计数与搜索投影

| 规格条目 | 状态 | 证据 |
|---|---|---|
| 写事实与 Outbox 同事务、stats 由幂等消费者更新（06 §7.1） | ✅ | like/unlike/favorite/unfavorite 事务内 outbox.publish；StatsEventHandler 对 7 类事件执行同一重算式（COUNT(*) 子查询）UPDATE repository_stats，天然消除增量竞争与重放副作用 |
| 请求线程不得同步写 visits（06 §7.2） | ✅ | VisitRecorder.record 以 REQUIRES_NEW 短写事务投递 VisitRecorded；handler 消费时 ON CONFLICT DO NOTHING 去重后重算 |
| stats 可由强事实表幂等重建 | ✅ | StatsRebuildService.rebuild；statsRebuildRestoresFromFactTables 手动置脏 likes=99 后重建恢复 1 |
| hot-desc 派生自 stats 无物化列 | ✅ | CatalogService 既有 SQL 内联公式（visits+3*downloads+2*likes）不变 |

### 02 §9 越权防护 / 07 SEC-05 审计

| 规格条目 | 状态 | 证据 |
|---|---|---|
| listMine 全部经可见范围过滤（ME-001 不泄漏私有资源） | ✅ | meRepositoriesTabs：他人对转 private 的 liked 仓库在 tab=likes 不可见 |
| 审计只追加不可篡改（GOV-002） | ✅ | 全系统无 audit_logs 更新/删除 API；AuditQueryService 无写路径；adminActionWritesAudit：:retry 202 后 repository.admin_retry 记录 result=accepted 闭环 |
| SEC-05 字段清单（actor/organization/action/resource/result/sourceIp/userAgent/traceId/idempotencyKey/createdAt） | ✅ | V9 扩展列 + AuditLogView 全字段；契约 AuditLog.id 为 UUID，V9 补 public_id 列（存量 gen_random_uuid() 回填） |
| 审计读角色管控 | ✅ | platform_admin/platform_auditor 服务层二次校验；auditLogsForbidden：普通用户 403、匿名 401 |
| 导出隐私合规（不泄漏 token/预签名 URL/文件正文） | ✅ | auditLogsNdjsonExport 逐行断言不含 `mh_`/`X-Amz-` 敏感串；fetchSize 分批流式不整表载入 |

## 3. 偏差记录

1. **Flyway 全局版本号 V9（风险 3 命中）**：identity-access 审计扩展按计划取"目录最大版本 +1"为 V2，但 Flyway 版本空间跨模块全局唯一，与 catalog 的 V2__catalog.sql 冲突（"Found more than one migration with version 2"）；改为 V9__audit_extension.sql（全局已用 V1-V8）。同时 V1 已建 ix_audit_logs_created，V9 不重复建 created_at 索引，仅补 ix_audit_logs_actor。
2. **audit_logs 补 public_id 列（超计划 T1）**：04 OpenAPI 契约 AuditLog.id 为 uuid，而 V1 表无此列；V9 加 `public_id UUID UNIQUE NOT NULL DEFAULT gen_random_uuid()`，存量行自动回填。
3. **like/favorite 改 `insertIgnore`（超计划）**：初版按计划"INSERT + 捕获 DataIntegrityViolationException"实现（saveAndFlush + catch），但 Spring 事务中该异常会将事务标记 rollback-only，捕获后提交仍抛 UnexpectedRollbackException → 并发时 500；修为 native `INSERT ... ON CONFLICT DO NOTHING`（insertIgnore）并发布 outbox 事件，删除 catch。并发唯一性测试覆盖。
4. **audit-logs 拆为双端点**：初版单端点声明 `ResponseEntity<?>`，StreamingResponseBodyReturnValueHandler 按**声明**返回类型选择处理器（经 spring-webmvc 6.1.14 源码核实），通配声明落到消息转换器 → 500 "No converter"；拆为 json 端点（`ResponseEntity<ApiEnvelope<...>>`）与 ndjson 端点（`@GetMapping(path="/audit-logs", params="format=ndjson")` 声明 `ResponseEntity<StreamingResponseBody>`），非法 format 在 json 端点抛 400。
5. **SecurityConfig 放行 ASYNC/ERROR dispatch（超计划）**：StreamingResponseBody 走 Servlet 异步，响应完成后容器以 ASYNC dispatch 重进过滤器链，STATELESS 下 SecurityContext 为空 → AuthorizationFilter 二次拒绝 → "response is already committed" → 连接中断（客户端 Premature end of chunk）；加 `dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`（初始 REQUEST dispatch 已完成授权，服务层仍二次校验角色）。
6. **GovernanceFlowTest 改 extends CatalogTestSupport**：计划写 extends BaseIntegrationTest，但 :retry 闭环需要 createRepo/awaitEntityStatus/userNamespaceId 等助手（CatalogTestSupport 提供）；沿用 CatalogProvisionFailureTest 的隔离模式（独立 govhub PostgreSQL + 不可达 Gitea + max-retries=1），避免与主上下文 poller 争抢 outbox 事件。
7. **feedback 超长断言 422**：计划写 400，实际 GlobalExceptionHandler 将 `MethodArgumentNotValidException` 统一映射为 422 VALIDATION_FAILED（与 AuthFlowTests 既有语义一致），测试按 422 + errorCode=VALIDATION_FAILED 断言。
8. **ndjson 客户端用 byte[] 提取**：TestRestTemplate 的 String 提取器不支持 `application/x-ndjson`，测试用 `byte[].class` + UTF-8 解码后逐行校验。

## 4. 结论

03 §6 互动强事实表与去重语义、04 §5 互动/我的仓库/审计契约、06 §7 幂等统计投影与异步收敛、02 §9 越权过滤、07 SEC-05 审计只追加与隐私导出全部满足并经真实 Testcontainers（PostgreSQL 16 + Redis 7 + Gitea 1.24）验证；97 个测试全绿，无 flaky 放行。M3 尚未收口（workflow 属阶段 5），本阶段不打 tag。

# 阶段 2（M2 前半：catalog 目录与统一授权）对齐验证报告

- 日期：2026-08-12
- 依据规格：`prd/v1/specs/02-identity-permissions.md` §3-§5、`03-domain-data-model.md`、`04-api-contract.md`、`05-artifact-storage-consistency.md` §8-§10、`prd/v1/contracts/openapi-v1.yaml`
- 构建证据：`mvn -pl modules/app -am clean test` BUILD SUCCESS + `mvn -pl modules/arch-tests -am test` BUILD SUCCESS
- 测试统计：共 **81 个自动化测试全绿**
  - `modules/app` 集成测试 68（真实 PostgreSQL 16 + Redis 7 + Gitea 1.24，Testcontainers；较阶段 1 新增 13：CatalogIntegrationTest 10、CatalogAuthzTest 2、CatalogProvisionFailureTest 1）
  - `modules/shared` 单元 4、`modules/identity-access` 单元 3、`modules/arch-tests` ArchUnit 6

## 1. 交付物清单

| 类别 | 内容 |
|---|---|
| 迁移 | V2__catalog（17 表：repositories/resource_types/collaborators/gated_*/outbox_events/git_bindings/profiles/stats/taxonomy 等）、V3 资源类型种子、V4 taxonomy 种子、V5 任务树种子、V6 jobs |
| 实体/仓储 | 17 实体（含 SchemaVersion 复合主键）、16 Spring Data Repository |
| 服务 | CatalogService（仓库 CRUD/分页/resolve/生命周期）、RepositoryAccessFacade（统一授权）、CollaboratorService（grant 权限校验）、GatedAccessService（申请/审批/吊销）、ResourceTypesService、MetadataOptionsService、MetadataValidator、ProfileProjector、OutboxService、GiteaClient |
| 异步 | OutboxPoller（指数退避 + provisioning 重试上限 + 死信）、ProvisioningWorker（Gitea 建仓 Saga：create→auto_init→README→active） |
| API | /resource-types（含 metadata-options）、/repositories（CRUD/分页/:resolve/:retry/:delete/:restore）、/repositories/{id}/collaborators、/access-requests（申请/审批/我的）、admin 仓库管理面 |
| 测试基类 | CatalogTestSupport（仓库生命周期 HTTP 轮询等待）、BaseIntegrationTest.infraOverrides 隔离钩子 |

## 2. 规格逐章核对

### 02 §3-§5 统一授权

| 规格条目 | 状态 | 证据 |
|---|---|---|
| 组织 visibility（private/organization/public）读取语义 | ✅ | CatalogAuthzTest.organizationVisibilityAndCollaboratorUserSubject：匿名/外部/他组织→404 防枚举；owner/member/viewer→200 |
| membership 派生角色（owner>admin>member>viewer） | ✅ | member 无 WRITE→PATCH 403；viewer 不能在组织 ns 建仓库→403 |
| READ 由 visibility 语义判定，高于 READ 按有效角色 | ✅ | RepositoryAccessFacade.authorizeRepo 第 5 步；org 成员/匿名对可见仓库有效角色可为 NONE 仍可读 |
| user/organization 双形态协作者 + 成员继承 | ✅ | organization 形态授 read→全体有效成员继承；移除后回 404 |
| grant 权限（maintain 可授 write/read 不可授 admin） | ✅ | CatalogAuthzTest L77-84（02 §3.3 checkGrantPermission） |
| 防枚举：无关系 404 / 有关系无权 403 | ✅ | 两测试全矩阵断言 |
| 协作者列表仅 ADMIN 可见 | ✅ | member→403、owner→200 |
| gated 访问：申请/审批/拒绝/吊销 + etag | ✅ | CatalogIntegrationTest.gatedFlowRequestApproveRevokeAndConflicts |

### 03 领域模型

| 规格条目 | 状态 | 证据 |
|---|---|---|
| repositories 双命名空间（user/org）+ slug 唯一（大小写不敏感） | ✅ | createProvisionsToActiveAndDuplicateCaseInsensitive409 |
| resource_types + schema_versions + profile 投影 | ✅ | resourceTypesAndMetadataOptionsAnonymousReadable；ProfileProjector 同事务投影 |
| taxonomy/facet 种子与 metadata 校验 | ✅ | V4/V5 种子在空白库 Flyway 执行；MetadataValidator 按 schema 校验 |
| outbox_events 与业务同事务写入 | ✅ | OutboxService 参与调用方事务；轮询投递解耦 |

### 04 API 契约

| 规格条目 | 状态 | 证据 |
|---|---|---|
| 分页 page/cursor 双模式、bounds 400 | ✅ | listPaginationNoDuplicateNoMiss（游标无重复无遗漏）；HealthContractTests.pagination_bounds_are_enforced |
| If-Match etag 乐观并发（版本递增） | ✅ | patchRequiresIfMatchAndBumpsVersion：无 If-Match→428、错版本→412、成功→version+1 |
| :resolve 端点尊重 visibility | ✅ | resolveEndpointRespectsVisibility：匿名 public→200、私有→404 |
| 错误信封统一 | ✅ | GlobalExceptionHandler；404/403/409/412/422/428 语义与契约一致 |

### 05 §8-§10 制品一致性与生命周期（本阶段相关部分）

| 规格条目 | 状态 | 证据 |
|---|---|---|
| provisioning Saga：pending→provisioning→active | ✅ | awaitLifecycleHttp 全流程断言（Gitea Testcontainer 真实建仓） |
| 失败重试上限（默认 3）后置 failed | ✅ | CatalogProvisionFailureTest（独立 failhub 库 + 不可达 Gitea + max-retries=1）：failed 后管理员 :retry 202 |
| 删除 Saga：deleting→deleted + 保留期 + :restore 幂等重放 | ✅ | deleteAndRestoreStateMachineWithIdempotentReplay（Gitea PATCH 归档/恢复） |
| 死信与重试退避 | ✅ | OutboxPoller：provisioning 受仓库 provision_retry_count 约束；其他事件 attempts≥10 进死信，指数退避上限 300s |

## 3. 偏差记录

1. **GiteaClient 换 httpclient5**：`SimpleClientHttpRequestFactory`（HttpURLConnection）不支持 PATCH，而删除/恢复 Saga 依赖 PATCH 归档（05 §9.2）；catalog 模块新增 `httpclient5` 依赖（版本由 spring-boot-dependencies BOM 管理），构造 `HttpComponentsClientHttpRequestFactory` 并沿用连接/读超时配置。
2. **saveAndFlush 替代 save（4+1 处）**：JPA `@Version` 在 flush 时才递增，`save()` 后立即构建响应视图会返回旧版本号，客户端 etag 落后一拍导致下一次 If-Match 412；CatalogService.update 与 GatedAccessService 四处改 `saveAndFlush()`（04 §10 etag 语义）。
3. **READ 语义修正**：初版把 READ 也按有效角色校验，导致组织成员对 organization 可见仓库（有效角色 NONE）被拒；修为 READ 仅由 visibility 语义判定（02 §4）。
4. **故障注入上下文隔离**：`@TestPropertySource` 内联属性经 addFirst 注册，但 `@DynamicPropertySource` 在 refresh 期同样 addFirst 且执行更晚，优先级反而更高——内联属性无法覆盖动态数据源。改为 `BaseIntegrationTest.infraOverrides` 静态钩子（refresh 时消费一次即清空），CatalogProvisionFailureTest 使用独立 failhub PostgreSQL，避免主上下文 poller 争抢 outbox 事件。
5. **构建脚本加 clean**：轮9 出现批量 500（`Name for argument of type [java.util.UUID] not specified`）——IDE 后台编译器向 target/classes 写入了不带 `-parameters` 的类文件，Maven 增量编译误判为最新而跳过重编译。`build-catalog.ps1` 改为 `mvn -pl modules/app -am clean test` 保证产物全部由 Maven（含 `-parameters`）编译；父 pom pluginManagement 的 `parameters=true` 经 javap MethodParameters 属性验证有效。
6. **Redis 限流超时降级（观察项）**：SessionRotationTests 期间 Redis 命令超时触发“降级本地保守模式”WARN，测试仍通过；降级机制按 07 章预期工作，暂不处理。
7. **m2 tag 顺延**：M2 退出门禁包含 artifact 上传下载，tag 在阶段 3 末统一打。

## 4. 结论

02 §3-§5 授权矩阵、03 catalog 领域模型、04 契约语义（分页/etag/resolve/错误码）、05 §8-§10 provisioning 与删除 Saga 的本阶段职责全部满足并经真实 Testcontainers（PostgreSQL 16 + Redis 7 + Gitea 1.24）验证；81 个测试全绿，无 flaky 放行。进入阶段 3（artifact 文件与上传下载）。

# 阶段 3（M2 后半：artifact 文件与上传下载）对齐验证报告

- 日期：2026-08-12
- 依据规格：`prd/v1/specs/05-artifact-storage-consistency.md` §2-§7、§11、`04-api-contract.md` Artifacts/Uploads 章节、`02-identity-permissions.md` §5、`prd/v1/contracts/openapi-v1.yaml`
- 构建证据：`mvn clean verify` BUILD SUCCESS（全模块含 ArchUnit 质量门禁）
- 测试统计：共 **87 个自动化测试全绿**
  - `modules/app` 集成测试 74（真实 PostgreSQL 16 + Redis 7 + Gitea 1.24 + MinIO，Testcontainers；较阶段 2 新增 6：ArtifactFlowTest）
  - `modules/shared` 单元 4、`modules/identity-access` 单元 3、`modules/arch-tests` ArchUnit 6

## 1. 交付物清单

| 类别 | 内容 |
|---|---|
| 迁移 | V7__artifact（5 表：object_blobs/file_versions/upload_sessions/upload_parts/download_sessions + 约束：content_source 二选一、head 唯一索引、上传 11 态、租户去重 UNIQUE(namespace_id, sha256)） |
| 实体/仓储 | 5 实体（UploadSession 十一态状态机、UploadPart 复合主键、DownloadSession）+ 5 Repository |
| 存储 | ObjectStorageService（S3 multipart 幂等初始化/ListParts 真相源/流式 SHA-256/自实现 SigV4 预签名）、ArtifactConfiguration（双端点：internal S3Client + public S3Presigner、bucket 启动自检）、ArtifactProperties |
| 服务 | UploadService（上传会话 11 态 + Idempotency-Key 幂等 + conflict_resolution 冻结）、ArtifactUploadWorker（初始化→签发 part URL、complete 后校验/扫描/提交）、ArtifactFileDeletionWorker（Git 删除提交 Saga）、DownloadService（git/object 双源下载会话）、BrowseService（branches/commits/files 清单 + ETag）、FilesService（If-Match 删除）、ContentScanner |
| 异步 | OutboxEventHandler 跨模块扩展点（catalog OutboxPoller 按 eventType 分发）；artifact 注册 5 事件：UploadInitialization/Verification/Commit/Abort、FileDeletion |
| API | UploadsController（initiate/part-urls/complete/status/abort）、ArtifactsController（branches/commits/files、下载会话签发、git content 交付、If-Match 删除） |
| 外部集成 | GiteaClient 扩展：listBranches（默认分支/受保护）、listCommits（分页）、rawFile（ref 取原文）；SecurityConfig 目录只读白名单 + 下载会话/内容端点 |
| 测试基类 | BaseIntegrationTest 新增 MinIO Testcontainer（health 等待 + 双端点同址注入）；ArtifactTestSupport（上传/分片直传/轮询工具） |

## 2. 规格逐章核对

### 05 §2-§7 对象存储与上传下载

| 规格条目 | 状态 | 证据 |
|---|---|---|
| 双端点：internal 服务端访问 / public 客户端预签名 | ✅ | ArtifactConfiguration：S3Client→internal-endpoint、S3Presigner→public-base-url，path-style 寻址 |
| 对象键服务端生成 objects/{publicId}，用户路径仅存 file_versions | ✅ | V7 upload_sessions.object_key 服务端预留；预签名 URL 断言含 `/artifacts/objects/` |
| 上传会话状态机（initiated→uploading→verifying→scanning→committing→completed，冲突/中止/失败旁路） | ✅ | ArtifactFlowTest.gitSourceUpload_fullLifecycle 全链路轮询；UploadService 状态机守卫 |
| multipart 幂等初始化（稳定 object key 认领已有 upload） | ✅ | ObjectStorageService.initMultipart 先 ListMultipartUploads 认领 |
| 分片投影不越权：以 Provider ListParts 为真相源 | ✅ | upload_parts 仅投影；Worker 校验阶段以 ListParts 确认真实分片 |
| 服务端全量 SHA-256 校验后才可发布 | ✅ | complete 后 verifiedSha256 断言与客户端 sha 一致（git/object 双用例） |
| 扫描隔离：scan_status 未 clean 不可下载 | ✅ | V7 默认 quarantined+pending；DownloadService 下载前校验 blob clean/available |
| 租户级去重 UNIQUE(namespace_id, sha256) + ref_count | ✅ | V7 约束；ref_count 由事务维护作回收候选（05 §7） |
| baseCommitSha 过期（≠ 分支 head）→ 422 stale | ✅ | initiate_withStaleBaseCommitSha_422：VALIDATION_FAILED + "stale" |
| 大文件分片直传（>16MiB 拆 2 片）客户端零组装直达 | ✅ | objectSourceUpload_multipartAndPresignedDownload：20MiB+12_345 字节 2 片直传，下载字节级一致 |

### 04 Artifacts 契约

| 规格条目 | 状态 | 证据 |
|---|---|---|
| branches/commits/files 清单端点 + 分页 | ✅ | browse_branchesAndCommits_anonymousReadable：默认分支 main + headCommitSha 40 位；commits 最新 sha 与 head 一致 |
| files 响应 ETag = resolvedCommitSha | ✅ | filesList 响应头 ETag 非空；删除前置条件依赖 |
| 下载会话：签发即下载计数事实（201）、Idempotency-Key 幂等复用 | ✅ | gitSourceUpload_fullLifecycle：同键二次签发返回同一 sessionId |
| git source 内容经业务端点交付，交付时再次鉴权 | ✅ | /api/v1/downloads/{sessionId}/content 匿名→可读仓库 200；SecurityConfig 白名单 |
| object source 预签名 GET（clean 隔离检查） | ✅ | 下载 URL 直接可达且内容字节一致 |
| deleteFile：If-Match 前置条件，错版本 412 | ✅ | deleteFile_withStaleIfMatch_412：PRECONDITION_FAILED；正确 ETag 删除成功且清单消失 |
| 匿名只读语义（目录浏览可达） | ✅ | gatedRepo_downloadSession_forbiddenWithoutGrant 末段：匿名 GET files 200 |

### 02 §5 gated 与下载拦截

| 规格条目 | 状态 | 证据 |
|---|---|---|
| gated 仓库授权成功但无有效 grant → 下载拒绝 | ✅ | gatedRepo_downloadSession_forbiddenWithoutGrant：用户与匿名均 403 FORBIDDEN，目录浏览仍 200 |

### 05 §10.1 Outbox 跨模块

| 规格条目 | 状态 | 证据 |
|---|---|---|
| 事件按类型分发，消费端幂等 | ✅ | OutboxPoller default→dispatchToHandler（eventType→handler 注册表）；artifact 5 事件处理器经构造注入，重复投递无副作用（上传/删除均幂等） |

## 3. 偏差记录

1. **自实现 SigV4 预签名（替代 AWS SDK Aws4Signer.presign）**：SDK 2.40.x 的 presign 在 canonical request 计算之后才追加 `X-Amz-*` 查询参数（签名未覆盖这些参数），而 MinIO 校验时 canonical query 必须包含全部 `X-Amz-*`，SDK 输出恒被拒（403 SignatureDoesNotMatch，实测验证）。改为严格对齐 MinIO `doesPresignedSignatureMatch` 的自实现（canonical query 含 X-Amz-*，按 Go url.Values 排序与编码；UploadPart 强制 `x-amz-content-sha256` 参与签名），探针端到端 PUT 200 验证通过。`X-Amz-Expires` 限 604800s（SigV4 上限），调用方 ttl 15min。
2. **父 pom build/plugins 显式声明 maven-compiler-plugin**：`pluginManagement` 的 `<parameters>true</parameters>` 不作用于默认生命周期绑定；IDE 后台编译器写出的 target/classes 缺 `-parameters` 时 Maven 增量编译误判为最新而跳过重编译，Controller 未命名 `@PathVariable` 反射解析失败（500 IllegalArgumentException，轮 9 批量出现）。父 pom `<build><plugins>` 显式声明插件使配置全局生效 + 构建脚本强制 `clean`。
3. **Outbox 跨模块分发扩展点**：catalog 的 OutboxPoller 原仅知 provisioning 三事件；改为构造注入 `List<OutboxEventHandler>`（eventType→handler 注册表），artifact 模块经 `@Bean` 注册 5 个处理器，catalog 零依赖 artifact 保持单向依赖。
4. **下载会话 URL 为视图字段**：git source 的 URL 形如 `/api/v1/downloads/{sessionId}/content`，sessionId 不能从路径截取（与 object 预签名 URL 形态不同）；测试从 `sessionId` 字段取值（04 DownloadSessionEnvelope）。
5. **测试基座统一 MinIO 凭据**：ArtifactConfiguration 无条件创建 S3Client，所有 app 上下文必须携带端点/凭据；BaseIntegrationTest 注入 internal/public 基址（测试网段同址，预签名 URL 对测试客户端可达）+ bucket 由启动自检创建。
6. **git 内容不经 Gitea 公网直连**：git source 下载 URL 指向业务自建端点，交付时再校验（会话有效期 + READ 授权 + gated 检查），SecurityConfig 白名单 `/api/v1/downloads/*/content`（05 §11）。

## 4. 结论

05 §2-§7 对象存储双端点/分片直传/全量校验/租户去重、§11 扫描隔离与下载鉴权、04 Artifacts 清单/ETag/下载会话/删除语义、02 §5 gated 下载拦截、05 §10.1 跨模块 outbox 分发全部满足，并经真实 Testcontainers（PostgreSQL 16 + Redis 7 + Gitea 1.24 + MinIO）验证；87 个测试全绿，无 flaky 放行。M2 退出门禁达成，打 tag m2，进入阶段 4（interaction + governance）。

# P2 版本与数据面需求

> 状态：`READY`
> 阻塞：P1 VERIFIED；Q-205；Manifest 算法、上传限额和短期 DVC 凭据策略需确认。
> 非目标：审批发布、受保护 Tag、MCP。
> 数据集专项：`REQ-DST-CLI-001` 和 `REQ-PRE-001` 的最小安全预览属于 P2 MUST；
> P5 负责格式扩展和 E5 加固，不得把最小预览无限延期。

## REQ-VER-001 资产版本草稿

```yaml
status: READY
priority: MUST
phase: P2
permission: asset:update
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 行为

- 在 ACTIVE/DEPRECATED（是否允许）Asset 下创建 DRAFT Version；
- version 语法按 AssetType 固定：模型建议 SemVer，数据集日期/自定义规则受 Q-205；
- 服务端规范化后 `(assetId,version)` 唯一；
- 创建时绑定基线 sourceCommit、创建者、目标分支和 rowVersion；
- 不创建 Git Tag；
- DRAFT 可更新卡片、Manifest 和 Artifact 指针；
- 同一 Idempotency-Key 重放返回同一 versionId；
- 版本号冲突返回 `ASSET_VERSION_CONFLICT`；
- Asset/Project/Owner/权限/状态实时检查；
- DRAFT 删除/取消语义需明确，不影响任何 Published Version。

### 追踪

- Invariants：`INV-VER-001/004/005`
- State：VersionStatus DRAFT
- Pages：`PAGE-VER-001/002`

## REQ-MNF-001 Manifest Schema、规范化与摘要

```yaml
status: READY
priority: MUST
phase: P2
minimumEvidenceLevel: E3
```

### Manifest 必需内容

```yaml
schemaVersion: aihub/manifest-v1
assetId: ast_...
versionId: ver_...
sourceCommit: <40/64-char git oid according to repository hash>
generatedAt: <UTC informational, excluded or normalized for digest>
artifacts:
  - path: ...
    dvcFile: ...
    dvcHash: ...
    sha256: ...
    size: ...
    mediaType: ...
```

### 规范化

- 明确字符编码 UTF-8、换行 LF、Unicode normalization、键排序、数组排序语义、数字格式和空值处理；
- Artifact 按规范化 path 排序，path 必须满足 `INV-ART-002`；
- digest 计算对象不包含易变字段，算法至少 SHA-256；
- digest 以 `algorithm:value` 或独立 algorithm 字段表达，避免未来算法歧义；
- schemaVersion 变更有兼容/升级策略；
- 同一逻辑 Manifest 在 Java/Worker/CLI 参考实现得到相同 bytes/digest；
- `asset.yaml` 与 Manifest 的 asset/version/artifact 声明冲突时校验失败；
- Manifest 和摘要写入 Gitea，PostgreSQL 只保存投影与 digest。

### 验收

- Golden fixtures 覆盖顺序、换行、Unicode、空字段、大整数和恶意 path；
- Java 与 Worker/DVC 侧交叉向量一致；
- 修改任一受保护字段导致 digest 改变；
- 规范化文档与 JSON/YAML Schema 版本化。

### 追踪

- Invariants：`INV-ART-001..007`
- NFR：`NFR-COMP-004`

## REQ-DVC-001 客户端原生 Git+DVC 往返

```yaml
status: READY
priority: MUST
phase: P2
actor: 研发用户/API Client
permission: asset:upload
minimumEvidenceLevel: E4
```

### 行为

- 平台返回受控 Git URL、目标 revision/branch、DVC remote 名称和最小配置说明；
- DVC Remote 指向 MinIO `dvc-cache`，对象前缀/策略隔离到授权作用域；
- 优先签发 STS/短期 S3 凭据；若 MVP 使用项目服务账号，必须有 Accepted 风险、最小策略、轮换和不入 Git；
- `.dvc/config.local`/环境变量承载凭据，仓库配置不含 Secret；
- 用户执行 dvc add/push、Git commit/push 后，Webhook/显式同步建立版本草稿投影；
- 清除本地内容再 dvc pull，SHA-256 与原文件一致；
- 不允许 Git LFS 与 DVC 追踪同一路径；
- 权限撤销/凭据过期后不能继续访问未授权前缀；
- DVC Hash、文件 SHA-256 和 Manifest Digest 分别保存，不混称。

### 追踪

- Journey：`JRN-P2-001`
- NFR：`NFR-SEC-004/009`

平台原生 `aih` CLI 的用户级命令、退出码、Secret Store 和 REST 复用规则由
`REQ-DST-CLI-001` 定义；直接拼装 DVC 命令不能替代该 CLI 契约。

## REQ-UPL-001 Web Upload Session

```yaml
status: READY
priority: MUST
phase: P2
permission: asset:upload
idempotency: REQUIRED
minimumEvidenceLevel: E4
```

### 创建输入

assetId、versionId/目标草稿、files(path/size/mediaType/optional sha256)、totalBytes、客户端能力。

### 行为

- 校验 Asset/Version/Owner/权限/状态；
- 规范化每个 path，拒绝重复、绝对/穿越/保留路径；
- 检查文件数、单文件、总量、Part 大小/数量和配额；
- 默认单 Session ≤ 20 GiB，超限返回 CLI/DVC 引导而非创建半会话；
- 在 PG 创建 Session，在 MinIO 创建唯一受限 Multipart/object prefix；
- Session 绑定 Principal 和目标 Version，其他主体不可读取/完成；
- 返回 sessionId、过期时间、Part 策略，不返回永久 MinIO credential；
- 同幂等请求返回同一 Session；
- 过期/取消触发持久化清理 Job；
- Session 状态按 `state-machines.md` 最终确认后固化。

### 错误与审计

需补 `UPLOAD_*` Catalog：NOT_FOUND、STATE_NOT_ALLOWED、LIMIT_EXCEEDED、PATH_INVALID、QUOTA_EXCEEDED；
创建/取消/完成请求审计不记录预签名 URL。

### 追踪

- Invariants：`INV-UPL-001..007`
- Journey：`JRN-P2-002`
- Pages：`PAGE-UPL-001/002`

## REQ-UPL-002 Multipart Part 签名、恢复与 complete

```yaml
status: READY
priority: MUST
phase: P2
permission: session owner + asset:upload
idempotency: REQUIRED for complete
minimumEvidenceLevel: E4
```

### 行为

- Part sign 只接受 Session 允许的 file/partNo/size，URL 最小权限且短 TTL；
- URL 使用公开 MinIO Endpoint，日志/响应分析不记录完整 query；
- 客户端并行上传并保存非敏感 session/file/part/etag 状态；
- 页面刷新后从服务端对账已完成 Parts，不盲信 LocalStorage；
- 失败只重签/上传失败 Part；
- complete 提交完整 Part 清单和客户端摘要，服务端核对 MinIO metadata；
- 第一次 complete 原子冻结清单、结束新 Part 签名并创建唯一 Materialization Job；
- 相同 complete 重放返回同一 Job/结果；不同清单冲突；
- complete 后客户端轮询 Session/Job，不用长 HTTP 等待 DVC；
- EXPIRED/CANCELLED/COMPLETING/PROCESSING 状态动作严格限制。

### 安全

- Presigned TTL 默认 15 分钟或更短的上传策略；
- object key 完全由服务端生成；
- 限制 Content-Length/Content-Type/checksum 条件；
- Nginx/Backend 不代理文件 bytes。

### 追踪

- Acceptance：从 `JRN-P2-002` 生成刷新、Part 失败、complete 重放/冲突、URL 过期、越权
- Page：`PAGE-UPL-002`

## REQ-UPL-003 上传校验与 DVC/Git 物化 Worker

```yaml
status: READY
priority: MUST
phase: P2
workerHandler: UPLOAD_MATERIALIZE
minimumEvidenceLevel: E4
```

### 行为

- Handler 由持久化 Job 触发并按 sessionId 幂等；
- 流式读取暂存对象，不把 GB 文件加载入内存；
- 核验 size、SHA-256、媒体类型、路径、压缩结构和内容安全策略；
- 在受控工作目录检出锁定基线 Commit；
- 用锁定版本 DVC CLI add/push，正式对象进入 dvc-cache；
- 生成 .dvc/Manifest，提交 Git 并 push；
- 保存 commitSha/dvcHash/sha256/size/manifestDigest/Artifact 投影；
- 完成 Session 并发布 Outbox；
- staging 清理作为后续可靠 Job；
- 失败按 error retryable 分类，保留可诊断报告但不含敏感样本；
- 每个外部成功/本地确认窗口按 Upload Saga 故障注入；
- 工作目录隔离、防符号链接/路径穿越，完成后安全清理。

### 追踪

- Consistency：U4-U9
- Invariants：`INV-JOB-*`、`INV-ART-*`
- NFR：`NFR-REL-003`

## REQ-DL-001 授权下载票据

```yaml
status: READY
priority: MUST
phase: P2/P3
permission: asset:download
idempotency: NOT_REQUIRED or short request dedupe
minimumEvidenceLevel: E4
```

### 行为

- 输入 assetId+精确 version（未指定版本是否允许 latestPublished 由契约明确，Agent 不臆测）；
- 检查 Principal ACTIVE、Scope、Permission、组织/项目/ACL/Visibility、Sensitivity、License/用途限制、Version 状态；
- P2 草稿下载只允许维护协作者，普通消费仅 PUBLISHED/DEPRECATED；
- 返回一种或多种方法：PRESIGNED_URL、GIT_DVC；
- 支持按规范化 Artifact path 选择单文件/多文件/完整 Version，并为 CLI 的 include/exclude/resume
  提供稳定清单；
- 每个方法含 expiresAt、sha256/manifestDigest、精确 revision；
- URL 最小对象权限、默认 15 分钟、不可 List；
- GIT_DVC 不返回永久 S3 凭据，短期凭据另经受控接口；
- 下载授权本身 100% 审计，实际对象下载指标/日志按 MinIO 可关联能力实现；
- 票据过期、权限撤销后行为需明确：预签名 URL 通常无法即时撤销，因此 TTL/高敏策略需匹配；
- 响应、日志、审计和 Agent 对话不记录完整 URL。

### 错误

防枚举 `ASSET_NOT_FOUND`、状态 `VERSION_STATE_NOT_ALLOWED`、无权 `AUTH_PERMISSION_DENIED`（对可见资源）、
对象缺失 `DVC_OBJECT_MISSING`。

### 追踪

- Journey：`JRN-P2-003`
- NFR：`NFR-PERF-002`、`NFR-SEC-004`
- Page：`PAGE-VER-002`

## REQ-UPL-004 上传与版本前端

```yaml
status: READY
priority: MUST
phase: P2
minimumEvidenceLevel: E4
```

- 实现 `PAGE-UPL-001/002` 和 P2 范围 `PAGE-VER-001/002`；
- 文件选择前展示配额/20 GiB 阈值/CLI 替代；
- 文件 path/size/type 清单可确认；
- Part 级进度、速度、失败、重试、暂停/恢复；
- 刷新恢复不保存 Token/预签名 URL；
- complete 后显示 Worker 各阶段和安全错误摘要；
- 版本页展示 Commit/DVC/Manifest 的区别，不把草稿称 Published；
- 下载动作只在后端能力允许时出现；
- 两语言、键盘、错误/冲突/过期/越权完整状态；
- 浏览器 E2E 使用多 Part 小 Fixture 和可控故障，不在常规 CI 上传 1 GiB；容量测试单独 E5。

## REQ-CLI-001 平台数据集 CLI 适配

```yaml
status: READY
priority: MUST
phase: P2
decisions: [DEC-008]
sourceRequirement: REQ-DST-CLI-001
minimumEvidenceLevel: E4
```

实现必须逐项领取 `REQ-DST-CLI-001` 与 `AC-DST-CLI-001..006`。CLI 是 REST/数据面 Adapter：

- 不复制 Authorization、分类校验、状态机、幂等或审计；
- pull 使用精确 Version、支持 include/exclude/resume/local-dir 并默认校验摘要；
- create/push 复用 Asset/Create、Upload Session、Job 和 DVC 路径；
- 认证来自 Secret Store/受保护引用，命令参数、JSON 输出和历史无 Token/URL；
- CLI 与 REST 对同一 Principal/请求返回等价错误和业务结果。

## P2 出口

1. CLI DVC push/pull SHA-256 一致；
2. Web Multipart 刷新恢复、单 Part 重试和 complete 幂等；
3. Worker 崩溃/依赖失败后任务恢复且无重复 Git Commit/投影；
4. Manifest 跨实现 Golden Vector 一致；
5. 草稿/已发布下载权限和短期票据过期正确；
6. 文件 bytes 不经过 Backend/Nginx；
7. Secret/URL 不进入 Git、浏览器存储、日志、审计；
8. `aih dataset search/pull/create/push/status` 通过 `AC-DST-CLI-001..006`；
9. 支持格式的最小安全 Preview 通过 `AC-DST-PRE-001..004`；
10. 契约、Migration、Worker/CLI 构建、Fixture、UI、Runbook 和 E4 同步。


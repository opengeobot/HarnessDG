# 数据集体验与 AI 数据交付需求

> 状态：`READY`
> 决策：`DEC-008`
> 范围：定义数据集发现、详情、讨论、CLI 传输和 AI 读写的端到端产品行为。
> 阶段门禁：本文件规格可以被任务引用，但 P0-B 未 VERIFIED 前不得实现 P1+ 产品代码。

本文件补齐类似 ModelScope 的数据集体验，但不复制公网社区运营或完整第三方 SDK。所有行为继续遵守
ADR-0001 的 Gitea + DVC + MinIO + PostgreSQL 事实源边界，并复用 P0-B 身份、授权、字典、标签、
幂等任务、审计、通知和观测能力。

## REQ-DST-TAX-001 数据集分类、受控标签与可解释筛选

```yaml
status: READY
priority: MUST
phase: P1
decisions: [DEC-008]
permissions: [asset:read, asset:create, asset:update]
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-TAX-001
  - AC-DST-TAX-002
  - AC-DST-TAX-003
  - AC-DST-TAX-004
```

### 分类模型

数据集分类使用稳定字典 `itemCode`，描述性标签使用受控 `tagId`，两者不得混用：

| 维度 | 机器字段 | 治理来源 | 基数 |
| --- | --- | --- | --- |
| 任务 | `taskCodes` | `dataset_task` 字典 | 多值 |
| 模态 | `modalityCodes` | `dataset_modality` 字典 | 多值 |
| 格式 | `formatCodes` | `dataset_format` 字典 | 多值 |
| 语言 | `languageCodes` | `dataset_language` 字典 | 多值 |
| License | `licenseCode` | `license_catalog` 字典 | 单值 |
| 敏感等级 | `sensitivityCode` | `sensitivity_level` 字典 | 单值 |
| 数据规模 | `sampleCount`、`totalBytes`、`sizeBucketCode` | 原始数值 + 服务端版本化分桶规则 | 单值 |
| 行业/主题 | `tagIds` | 平台/组织 `system_tag` | 多值 |

- `sizeBucketCode` 由服务端按类型化配置从 `sampleCount/totalBytes` 派生，客户端不能伪造；
- Split、Schema、字段统计属于精确 Version，不作为资产级自由分类字符串；
- 字典项和标签停用后保留历史回显，禁止新建关联；
- API、CLI、MCP 和 UI 只提交 `itemCode/tagId`，禁止提交展示名或自动创建自由值；
- MODEL 与 DATASET 可复用搜索基础设施，但数据集分类字段不能借用 `model_task`。

### 搜索和 Facet 语义

- 支持 `query/type/namespace/organizationId/projectId/ownerId/teamId/visibility/status`；
- 数据集过滤支持上述所有分类维度、`tagId`、更新时间和发布时间；
- 同一维度多值为 OR，不同维度之间为 AND；该规则写入 OpenAPI/MCP Description；
- Facet 和计数必须在数据库权限、敏感度和状态过滤后计算，不能泄露无权资产数量；
- 排序仅允许 `relevance/updatedAt/publishedAt/sampleCount/totalBytes` 白名单；
- 相关性基础顺序为坐标/名称精确匹配 > 任务/标签/治理字段 > 描述/Card 摘要；
- DEPRECATED 显式标记并降权，ARCHIVED 和未完成 Provision 的资产默认不返回；
- 返回 `matchedFields` 和稳定分类摘要，AI 不得编造“匹配原因”；
- PostgreSQL 字段/全文检索是首个 MUST；语义/向量检索属于独立增强，未交付前不得宣称支持。

### 接口与横切要求

- 目标 REST：`GET /datasets`、`GET /assets`、受控 taxonomy 查询；
- 目标 MCP：`asset_search`，`type=DATASET`；
- 页面：`PAGE-AST-001`；
- 错误：`COMMON_INVALID_ARGUMENT`、`DICTIONARY_VALUE_INVALID`、`TAG_VALUE_INVALID`、
  `AUTH_PERMISSION_DENIED`；
- 创建/更新分类产生 `ASSET_CREATED/ASSET_UPDATED` 审计；搜索只记录脱敏访问指标，不记录完整查询
  中可能包含的敏感业务文本；
- 索引和排序必须有 10 万资产规模的 E5 性能数据，但权限正确性先以 E4 验证。

## REQ-DST-DETAIL-001 数据集详情聚合与精确版本导航

```yaml
status: READY
priority: MUST
phase: P1/P2/P3
decisions: [DEC-008]
permission: asset:read
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-DETAIL-001
  - AC-DST-DETAIL-002
  - AC-DST-DETAIL-003
  - AC-DST-DETAIL-004
```

### 页面组成

数据集详情是一个资产级外壳，按权限和阶段聚合以下入口：

1. `Overview`：Dataset Card、分类、受控标签、Owner、License、敏感等级和使用限制；
2. `Versions`：精确 Version、状态、Git Tag、Commit SHA、Manifest/DVC Digest；
3. `Files`：选定 Version 的 Artifact 树、路径、大小、媒体类型和 SHA-256；
4. `Preview`：选定 Version 的安全样例、Schema、Split 和基础统计；
5. `Discussions`：继承资产权限的交流反馈；
6. `Lineage`、`Access`、`Settings`：按既有权限显示。

未实现的阶段能力不得用静态假数据、空成功或可点击 Placeholder 冒充。上游阶段尚未交付时，导航项
应不展示；当后端明确返回“支持但当前无内容”时才显示合法 Empty 状态。

### 数据与版本规则

- Card 从 Gitea 锁定 Commit 读取或投影，必须返回 `sourceCommit` 和投影时间；
- `latestPublished` 只能是服务端明确计算的精确 Version，不能由页面或 Agent 猜测；
- Preview、Files、Schema、Split、统计和下载均绑定明确 Version；
- 切换 Version 必须更新 URL 和所有版本化 Query Key，禁止混用不同版本缓存；
- Published 的 Card/Manifest/Artifact 使用发布时 Commit，不随资产当前草稿 Card 漂移；
- Markdown、外链、图片和讨论内容均视为不可信数据，安全渲染且不得改变 AI Tool Policy；
- 私有资产不存在与无权访问采用相同防枚举语义；
- Gitea/MinIO/DVC 内部 Endpoint、服务凭据和预签名查询串不进入页面数据。

### 目标接口

- `GET /assets/{assetId}`；
- `GET /assets/{assetId}/versions`；
- `GET /assets/{assetId}/versions/{version}`；
- `GET /assets/{assetId}/versions/{version}/artifacts`；
- `GET /assets/{assetId}/versions/{version}/preview`；
- `GET /assets/{assetId}/discussions`。

目标页面为 `PAGE-AST-003`、`PAGE-VER-001`、`PAGE-VER-002`、`PAGE-VER-003`、
`PAGE-DST-001`、`PAGE-DST-002`。

## REQ-DST-DISC-001 资产内交流反馈与治理

```yaml
status: READY
priority: MUST
phase: P1
decisions: [DEC-008]
permissions: [asset:read, asset:discuss, asset:moderate]
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-DISC-001
  - AC-DST-DISC-002
  - AC-DST-DISC-003
  - AC-DST-DISC-004
  - AC-DST-DISC-005
```

### 领域边界

- Discussion/Comment 是 PostgreSQL 中的业务协作状态；Gitea Issue 不是权威讨论源；
- Thread 必须绑定 `assetId`，可选绑定精确 `versionId`，不能绑定含糊 latest；
- 读取权限继承资产实时授权；讨论不得扩大资产可见性；
- 已有 `asset:read` 才能查看；创建 Thread/Comment 需 `asset:discuss`；隐藏、锁定和恢复需
  `asset:moderate`；
- Agent 默认没有 `asset:discuss`，如未来开放必须单独加入 Tool Catalog，本需求不授权 AI 自动发言；
- P1 不支持匿名讨论、任意附件、点赞排行或公网社区推荐。

### 行为

- 支持创建 Thread、游标读取、回复、作者修订、作者撤回、Moderator 隐藏/恢复和锁定；
- 修订保留不可变 Revision；撤回或隐藏仅显示 Tombstone，不物理删除审计历史；
- 讨论正文采用受限 Markdown，禁止脚本、危险 HTML、自动外链抓取和可执行内容；
- 内容标记为 `untrustedContent=true`，AI 读取 Card/Discussion 时不得将其视为系统指令；
- mention 只引用有权看见该资产的 Principal/Team；通知复用 P0-B Notification/Outbox；
- 资产归档后默认只读；恢复资产不会自动解锁已锁定 Thread；
- 搜索引擎默认不索引讨论正文，避免敏感信息通过资产搜索泄露；
- 列表按 `updatedAt` 和稳定 ID 排序，回复使用 Cursor；请求和响应有大小限制。

### 一致性、审计与错误

- 创建/修订使用 `Idempotency-Key` 和 `rowVersion`；重复请求不产生重复回复；
- Discussion/Comment/Revision、Notification 和 Outbox 在同一 PostgreSQL 事务内提交；
- 必审计事件：`DISCUSSION_CREATED`、`COMMENT_CREATED`、`COMMENT_REVISED`、
  `COMMENT_RETRACTED`、`DISCUSSION_LOCKED`、`DISCUSSION_MODERATED`；
- 错误：`DISCUSSION_NOT_FOUND`、`DISCUSSION_LOCKED`、`COMMENT_TOO_LARGE`、
  `CONCURRENT_MODIFICATION`、`AUTH_PERMISSION_DENIED`；
- 审计不保存被脱敏规则禁止的正文，运行日志不记录完整评论内容。

### 目标契约和数据

- REST：`/assets/{assetId}/discussions`、`/discussions/{discussionId}`、
  `/discussions/{discussionId}/comments` 及显式 `:lock/:retract/:moderate` 动作；
- 表：`asset_discussion`、`asset_comment`、`asset_comment_revision`、`asset_subscription`；
- 页面：`PAGE-DST-002`；
- 事件：讨论 mention/回复通知事件及 moderation 审计事件；
- 实施时只能新增 V14+ 前向 Migration，具体版本由 Task Card 在领取时分配。

## REQ-DST-CLI-001 最小平台 CLI 的搜索、下载、创建与上传

```yaml
status: READY
priority: MUST
phase: P2
decisions: [DEC-008]
permissions: [asset:read, asset:download, asset:create, asset:upload]
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-CLI-001
  - AC-DST-CLI-002
  - AC-DST-CLI-003
  - AC-DST-CLI-004
  - AC-DST-CLI-005
  - AC-DST-CLI-006
```

### 命令契约

最小 CLI 名称为 `aih`，只做 REST/数据面适配，不复制业务规则：

```text
aih dataset search --query <text> [受控 filters] --output json
aih dataset inspect aih://<namespace>/dataset/<name>@<version> --output json
aih dataset pull aih://<namespace>/dataset/<name>@<version> --local-dir <path>
                 [--include <glob>] [--exclude <glob>] [--resume] [--verify]
aih dataset create --manifest <asset.yaml> --output json
aih dataset push aih://<namespace>/dataset/<name>@<draft-version> <local-path>
                 [--resume] [--output json]
aih dataset status <operation-id> --output json
```

- `pull` 必须使用精确 Version；交互用户可以显式选择服务端 `latestPublished`，AI 不得省略 Version；
- `include/exclude` 只匹配规范化 Artifact path，不能逃逸目标目录；
- `--verify` 为默认启用，校验 Manifest Digest 和每个已下载 Artifact 的 SHA-256；
- 下载先获取受控 download handle，再由 CLI 的非 LLM 数据通道兑换短期 URL或 DVC 方法；
- CLI 从 OS Secret Store、受保护配置引用或进程环境取得短期平台凭据，命令参数、输出和历史不含
  Token、永久对象凭据或预签名 URL；
- 小/中型数据走下载票据和断点续传；大型目录按服务端能力使用 Git+DVC 和短期凭据；
- `push` 小于 Web/REST 阈值时复用 Upload Session/Multipart；超限时使用受控 DVC 路径；
- CLI 只调用 REST Application Use Case，不实现第二套授权、状态机、标签校验或发布规则；
- `create/push/complete` 自动生成并持久化稳定 Idempotency-Key，网络重试不更换用户意图键。

### 稳定输出与退出码

JSON 输出至少包含 `operationId/requestId/traceId/result/error.code/error.retryable`。退出码：

| Code | 含义 |
| --- | --- |
| `0` | 成功且校验完成 |
| `2` | 本地参数或 Manifest 非法 |
| `3` | 未认证或凭据过期 |
| `4` | 无权、资源不可见或状态不允许 |
| `5` | 幂等/并发/版本冲突 |
| `6` | 可重试依赖或网络失败 |
| `7` | 摘要或内容校验失败 |

不得使用包含本地化文本的 message 决定退出码。

## REQ-DST-AI-001 AI 搜索、精确选择和本地下载

```yaml
status: READY
priority: MUST
phase: P4
decisions: [DEC-008]
permissions: [mcp:invoke, asset:read, asset:download]
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-AI-001
  - AC-DST-AI-002
  - AC-DST-AI-003
  - AC-DST-AI-004
  - AC-DST-AI-005
```

- AI 先调用 `asset_search(type=DATASET)`，可使用 query、分类、tagId、作用域和 Cursor；
- Tool 复用 `REQ-DST-TAX-001`，返回小型候选、`matchedFields`、治理摘要和精确
  `latestPublished`，不返回完整文件清单或 URL；
- 多个候选相近、无发布版本或版本未指定时，AI 返回候选或调用版本工具，不得臆测；
- 选定后调用 `asset_get`、`asset_get_version`，检查 License、敏感等级、用途限制和弃用警告；
- 下载授权返回不透明 `downloadHandle` 和精确摘要，不把预签名 URL 放入 MCP 文本结果；
- 本地下载由受信任的 `aih dataset pull <exact-uri>` 数据通道完成，CLI 自行使用 Secret Store 身份
  换取/兑换短期授权；AI 对话只保留资产 URI、结果和非敏感校验摘要；
- 下载完成必须验证 SHA-256/Manifest Digest；校验失败不得向后续任务报告成功；
- 无权结果、Facet 数量和错误均使用防枚举语义；Tool Allowlist、Scope、Permission、资源策略缺一
  即拒绝并审计；
- 首期是“AI 将意图映射为结构化筛选 + PostgreSQL 全文检索”，不宣称语义向量搜索。

## REQ-DST-AIW-001 获授权 AI 创建草稿并上传数据集

```yaml
status: READY
priority: MUST
phase: P4
decisions: [DEC-008]
permissions: [mcp:invoke, asset:create, asset:upload, asset:submit]
minimumEvidenceLevel: E4
acceptance:
  - AC-DST-AIW-001
  - AC-DST-AIW-002
  - AC-DST-AIW-003
  - AC-DST-AIW-004
  - AC-DST-AIW-005
  - AC-DST-AIW-006
```

### 准入与工具

- 全局 `mcp.writeTools.enabled` 默认仍为 false；
- 自动化写入 Agent 必须有独立 Principal、限定 Organization/Project、最大敏感等级、JWT Scope、
  业务 Permission 和逐 Tool Allowlist；
- P4 MUST Tool：`asset_create_draft`、`asset_create_upload_session`、
  `asset_complete_upload`、`asset_get_upload_status`；
- `asset_submit_version` 仅在显式授权和平台策略允许时开放；
- `asset_publish_version`、删除、扩权、Token 管理和系统配置不属于本需求，继续默认禁止并要求人工；
- MCP Tool 不接收任意 Shell、Bucket、Git URL 或客户端本地绝对路径；本地文件传输由受信任 CLI
  适配层处理。

### 创建与上传

- 创建输入必须使用 Organization/Project/Owner ID、有效 itemCode 和 tagId；未知/停用/跨组织引用
  必须拒绝；
- Card/Manifest 模板有 schemaVersion，上传前校验 Dataset 必填分类、License、敏感声明和来源；
- 所有写 Tool 要求 Idempotency-Key；同键同请求返回同一 asset/session/job，不同请求冲突；
- Upload Session 绑定 Agent、Asset、Draft Version、文件清单、配额和过期时间；
- Tool 只返回 session/operation handle；Multipart URL 和对象凭据只进入非 LLM 数据通道；
- complete 后返回持久化 Job ID；AI 通过状态 Tool 查询校验、DVC、Git 和投影结果，不长轮询二进制；
- 失败只在 `retryable=true` 时退避重试；不确定超时先查 operation 状态，不创建第二资产或 Session；
- 创建、上传、完成、失败、拒绝和提交全部记录 Principal、Tool、资源、幂等键摘要和 Trace 审计；
- 上传成功不等于 Published；AI 最多提交审核，发布由 P3 人工审批和不可变 Tag 流程完成。

## 阶段出口与非目标

上述需求按 P0-B → P1 → P2 → P3 → P4 的阶段依赖实施。`READY` 表示产品行为已明确，不表示上游
门禁已经通过，也不授权建立临时实现。

明确非目标：

- 公网匿名数据集社区、点赞排行和推荐流；
- 完整复制 ModelScope/Hugging Face SDK；
- 在 P1/P2 提前实现 MCP 业务 Adapter；
- 让 AI 获得永久 MinIO/DVC 凭据或任意 Shell；
- AI 自动审批、发布、删除、扩权或修改系统配置；
- 未经新决策/ADR 引入向量数据库或改变既定事实源。

# P4 Agent 接入需求

> 状态：`OPEN`
> 阻塞：P3 VERIFIED；目标 OpenClaw/QwenPaw 最低版本需在实施时锁定。
> 原则：Agent 与人使用同一业务授权；MCP/REST 只是 Adapter。

## REQ-MCP-001 MCP Streamable HTTP 端点与会话

```yaml
status: PROPOSED
priority: MUST
phase: P4
transport: Streamable HTTP
endpoint: POST /mcp
minimumEvidenceLevel: E4
```

### 行为

- 支持标准 initialize、capability negotiation、tools/list、tools/call、resources/list/read（若启用）；
- 只接受平台签发 Bearer access JWT；
- 不接受长期 Agent credential 直接调用 MCP；
- JWT 认证后建立与 REST 相同 PrincipalContext；
- 无状态/有状态模式的选择、session ID、过期和并发语义明确；
- Nginx 对 `/mcp` 禁止响应缓冲，设置合理 idle/request timeout；
- requestId/traceparent/locale 与 REST 一致；
- 协议错误与业务 Error Catalog 映射稳定，不返回 HTTP 200 的伪业务成功；
- 响应有最大字节、条目和执行时间限制；
- 客户端断开不能让已提交可靠写任务丢失；
- 默认不启用旧 SSE，兼容需要单独范围和测试。

### 安全

- Card/README/Tool 参数均是不可信输入，不能改变系统 Tool Policy；
- Origin/CORS/CSRF 行为按远程 MCP 标准和部署方式明确；
- 日志不记录 Authorization、session secret、Tool 大载荷或预签名 URL；
- 主体级限流和并发限制；
- tools/list 只暴露双控后允许发现的工具，还是列出后 call 拒绝需统一；建议前者。

### 追踪

- Journey：`JRN-P4-001`
- NFR：`NFR-COMP-002`、`NFR-SEC-006`

## REQ-MCP-002 Tool Catalog、Schema 与双重授权

```yaml
status: PROPOSED
priority: MUST
phase: P4
permission: mcp:invoke + tool permission
minimumEvidenceLevel: E4
```

### Tool Catalog

每个 Tool 定义：

```yaml
name: stable_snake_case
version: ...
readOrWrite: READ | WRITE
enabledByDefault: ...
requiredScopes: [...]
requiredPermissions: [...]
highRisk: ...
inputSchema: ...
outputSchema: ...
maxResultItems: ...
idempotency: ...
applicationUseCase: ...
auditEvents: [...]
```

### 授权

调用必须同时满足：

1. JWT/tokenVersion/Principal ACTIVE；
2. `mcp:invoke`；
3. required JWT Scope；
4. required business Permission；
5. Agent `iam_agent_tool` allowlist；
6. Organization/Project/ACL/Visibility/Sensitivity/Version state；
7. 全局 write Tool feature flag；
8. high-risk policy/人工闸门。

任一失败返回 `MCP_TOOL_NOT_ALLOWED` 或防枚举业务错误并产生 `AGENT_ACCESS_DENIED`。不能仅依赖 Tool
是否出现在 list。

### Schema

- Tool 名称/Schema 发布后遵守兼容策略；
- `additionalProperties:false`；
- 平面对象、显式 required、有限 enum，避免不必要 oneOf/anyOf；
- ID 有 pattern，Cursor/limit 有边界；
- Description 说明何时用、何时禁用、前置条件和不可臆测规则；
- write 动作必须 `write:true`，修复当前 publish 标记错误；
- Contract phase 从错误的 target-p0-b 调整为 P4。

### 追踪

- Drift：`AUD-011`
- Journey：`JRN-P4-003`

## REQ-MCP-003 资产搜索与详情只读 Tools

```yaml
status: PROPOSED
priority: MUST
phase: P4
tools: [asset_search, asset_get]
minimumEvidenceLevel: E4
```

### asset_search

- 复用 `REQ-AST-003` 的 Application Query；
- 输入 keyword/type/namespace/tagId/受控 filter/cursor/limit；
- 默认只返回 Agent 有权的 PUBLISHED 资产/版本摘要；
- 是否返回无发布版本资产由权限/用途明确，普通只读 Agent 不返回；
- 返回 assetId、坐标、类型、显示名、治理摘要、latestPublished 精确 version；
- 最多配置 `mcp.maxResultItems` 且 ≤ REST 最大值；
- 不返回完整 README、文件清单或 URL；
- 无结果返回空 items，不暴露不可见数量。

### asset_get

- 输入 assetId；
- 复用资产详情 Query 和防枚举；
- 返回治理、Owner 公开摘要、latestPublished、使用限制和可用下一步 Tool；
- Card 内容截断/摘要且标记 untrusted，完整 Card 通过 Resource 按需读；
- 不把“最新”作为模糊文本，提供明确 version/commit。

### 追踪

- Journey：`JRN-P4-002`
- Application：与 REST 同一 Use Case

## REQ-MCP-004 精确版本与下载 Tools

```yaml
status: PROPOSED
priority: MUST
phase: P4
tools: [asset_list_versions, asset_get_version, asset_request_download]
minimumEvidenceLevel: E4
```

### 行为

- list versions 只返回有权 PUBLISHED/DEPRECATED，Cursor 分页；
- get version 要求 assetId+version，返回 Tag/Commit/Manifest Digest、Artifact 摘要和治理限制；
- 不指定 version 时 Tool 不自行猜测；Agent 先搜索候选或使用服务端明确 latestPublished 字段；
- request download 复用 `REQ-DL-001`；
- 下载 Tool 返回授权句柄/短期方法，不返回二进制；
- 结果携带 expiresAt、sha256/manifestDigest 和精确 revision；
- Tool 输出预签名 URL 的协议必要性与“不进入聊天”存在天然张力：建议返回一次性 ticketId，再由非 LLM 数据通道兑换 URL；最终方案需决策；
- Agent/Skill 指示下载后校验 SHA-256；
- DEPRECATED 返回明确警告，ARCHIVED 默认不返回。

### 审计

Tool 调用、授权决定和下载授权可关联同一 trace/session；审计不得保存 URL。

### 追踪

- Journey：`JRN-P4-002`
- NFR：`NFR-PERF-002`

## REQ-MCP-005 写 Tools 与人工闸门

```yaml
status: OPEN
priority: SHOULD for P4, MAY be deferred
phase: P4
blockedBy: product decision for write-agent scope
minimumEvidenceLevel: E4
```

### 默认策略

- `mcp.writeTools.enabled=false`；
- P4 最小出口只要求只读 Tools；
- create draft/upload/submit 若启用，逐项显式开关、Scope、Permission、allowlist、Idempotency-Key；
- publish/deprecate/delete/token/permission/config 默认不授予 Agent；
- “人工确认”不能只依赖 Agent 文字声称已确认，必须是平台可验证 Approval/Confirmation Token；
- Confirmation Token 绑定 Principal、Tool、规范化参数摘要、资源、过期时间和单次使用；
- 写 Tool 调用同一 Application Command，不直接调用 Mapper/SDK；
- 可靠工作返回 job/request ID；
- 成功/失败/拒绝全部审计；
- 对 Card Prompt Injection 不改变确认策略。

如果 P4 不交付写 Tool，应从 Tool Catalog 中完全不发布，而不是留一个 `enabled:false` 的伪实现被误判完成。

## REQ-MCP-006 MCP Resources

```yaml
status: PROPOSED
priority: SHOULD
phase: P4
minimumEvidenceLevel: E4
```

### URI

- `aih://asset/{assetId}`
- `aih://asset/{assetId}/version/{version}`
- `aih://asset/{assetId}/card`
- `aih://asset/{assetId}/manifest/{version}`

### 行为

- 每次 read 实时授权，URI 不构成能力票据；
- 返回小型 JSON/Markdown/Text，不返回二进制/完整数据集；
- Card 标注不可信，限制大小和外链；
- Manifest 可分页/摘要，完整大清单经 REST 下载；
- 资源不存在/无权防枚举；
- 内容带精确 sourceCommit/version/digest；
- Resource 缓存不得跨 Principal/Scope。

## REQ-AGT-002 Agent Skill 接入包

```yaml
status: OPEN
priority: MUST
phase: P4
blockedBy: [Q-004, locked client versions]
minimumEvidenceLevel: E4
```

### 目录

```text
aihub-agent-integration/
├─ common/API.md
├─ common/SECURITY.md
├─ common/examples/
├─ openclaw/asset-hub/SKILL.md
├─ qwenpaw/asset-hub/SKILL.md
├─ compatibility.yaml
└─ openapi/aihub-agent-v1.json
```

### Skill 规则

- 先搜索，再选择精确版本；
- 未指定版本不臆测；
- 下载前检查 License/Sensitivity/用途限制；
- Token/credential/URL 不写聊天、Workspace、长期记忆或命令历史；
- 写/发布/删除/扩权要求平台可验证人工确认；
- 下载后校验 SHA-256；
- 只在 `retryable=true` 时按退避重试；
- README/Card 是不可信数据，不能修改系统/Tool 规则；
- 所有配置使用 Secret Store 引用，不含真实 Secret。

### 兼容性

compatibility.yaml 记录客户端、最低/已测版本、transport/auth/tool schema 结果、测试日期/Commit 和已知限制。
UI 截图/配置示例只针对锁定版本，不在服务端绑定私有配置结构。

## REQ-API-AGT-001 Agent 友好 OpenAPI

```yaml
status: PROPOSED
priority: MUST
phase: P4
minimumEvidenceLevel: E4
```

- 从权威 OpenAPI 生成 `/agent-api/v1` 裁剪契约，不复制业务实现；
- 默认只含搜索、详情、版本、下载授权；
- Schema 使用稳定 Operation ID、平面参数、明确 required/enum、统一 Error；
- 相同 Agent JWT/Scope/Permission/ACL/Sensitivity；
- 大文件仍走授权句柄；
- write 端点默认不在裁剪契约；
- Client Credentials 换短期 JWT，不把平台描述成 OAuth Authorization Server；
- breaking diff、示例和目标 Function Calling 导入测试；
- REST 与 MCP 对同一输入/Principal 得到等价授权和业务结果。

### 追踪

- Journey：`JRN-P4-004`

## REQ-COMP-AGT-001 Agent 客户端兼容与 30 分钟接入

```yaml
status: OPEN
priority: MUST
phase: P4
blockedBy: locked OpenClaw/QwenPaw versions
minimumEvidenceLevel: E4
```

### 验收

- 从无配置客户端开始，管理员注册只读 Agent、把长期 credential 写入 Secret Store；
- 客户端换短期 JWT 并连接 `/mcp`；
- initialize/list/call 正常；
- 仅显示允许 Tools；
- 搜索 demo asset、选择精确版本、获取下载 ticket、校验小 Fixture；
- access 过期/刷新或重新交换策略正常；
- 只读 Agent 调发布/隐藏 Tool 被拒绝并审计；
- Cursor、401/403/404/409/429、最大响应和 URL 过期；
- 全流程 ≤30 分钟（不含下载大型模型），聊天/日志/Workspace/历史无 Secret；
- OpenClaw 和 QwenPaw 分别生成 Evidence Manifest。

## P4 出口

1. MCP 协议级与两个锁定客户端均通过；
2. Tool Catalog 阶段/读写标记/Schema/权限无漂移；
3. REST/MCP 复用同一 Application Service；
4. 只读 Agent 完成搜索→精确版本→下载校验；
5. Tool 列表和调用双控，高风险拒绝审计；
6. 30 分钟接入；
7. Skill/示例/对话/日志无 Secret；
8. Agent OpenAPI 导入通过；
9. 所有兼容证据绑定版本和 Commit。


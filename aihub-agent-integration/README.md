# AIHub Agent Integration

本目录提供 AI Agent 接入 HarnessDG 平台的适配器与指南。

## 目录结构

```
aihub-agent-integration/
├── README.md               # 本文件
├── SKILL.md                # Agent Skill 声明（OpenClaw/QwenPaw 兼容）
└── examples/
    └── search-and-download.md  # 典型工作流示例
```

## 接入方式

### 1. MCP (Model Context Protocol)

通过 MCP Streamable HTTP 端点 `POST /mcp` 接入，支持：

- **只读 Tools**: `asset_search`, `asset_get`, `asset_list_versions`, `asset_get_version`, `asset_request_download`
- **写 Tools** (默认关闭): `asset_create_draft` — 需 `mcp.writeTools.enabled=true`
- **Resources**: `aih://asset/{id}`, `aih://asset/{id}/version/{v}`, `aih://asset/{id}/card`

认证：Bearer JWT（通过 `/auth/agent/token` 获取）。

### 2. REST Agent API

精简 REST 端点子集，详见 `contracts/openapi/aihub-agent-v1.yaml`。

两个 Profile：
- **read-only**: 搜索/查看/下载
- **contribution**: 创建草稿版本

### 授权模型

双重约束：JWT Scope + Agent 工具白名单（`iam_agent_tool`），缺一即拒绝。

高风险操作（`asset:publish`, `asset:delete`, `system:configure`）对 Agent 一律拒绝。

## 凭据获取

```http
POST /api/v1/auth/agent/token
Content-Type: application/json

{
  "clientId": "agt_xxx",
  "clientSecret": "***"
}
```

返回 access_token (15min) + refresh_token (24h)。

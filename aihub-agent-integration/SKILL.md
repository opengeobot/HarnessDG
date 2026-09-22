# AIHub Skill Declaration

> Agent Skill manifest for OpenClaw/QwenPaw compatible AI assistants.

## Skill: aihub-asset-manager

**Description**: Search, inspect, and manage AI assets in the HarnessDG platform catalog.

### Capabilities

| Tool | Type | Description |
|------|------|-------------|
| `asset_search` | read | Search assets by keyword, type, namespace with ACL filtering |
| `asset_get` | read | Get full asset details by ID |
| `asset_list_versions` | read | List all versions for an asset |
| `asset_get_version` | read | Get specific version details |
| `asset_request_download` | read | Request download ticket (handle, not URL) |
| `asset_create_draft` | write | Create draft version (requires explicit enable) |
| `asset_create_upload_session` | write | Create upload session (requires explicit enable) |
| `asset_complete_upload` | write | Complete upload session (requires explicit enable) |
| `asset_get_upload_status` | write | Get upload session status (requires explicit enable) |

### Resources

| URI Pattern | Description |
|-------------|-------------|
| `aih://asset/{id}` | Asset details (JSON) |
| `aih://asset/{id}/version/{v}` | Version details (JSON) |
| `aih://asset/{id}/card` | Asset card / README (Markdown) |
| `aih://asset/{id}/manifest/{versionId}` | Version manifest (JSON, untrusted) |

### Authentication

```yaml
scheme: bearer-jwt
token_endpoint: /api/v1/auth/agent/token
grant_type: client_credentials
```

### Constraints

- Write tools disabled by default (`mcp.writeTools.enabled=false`)
- High-risk actions (`asset:publish`, `asset:delete`) denied for agents
- Dual authorization: JWT Scope + Agent tool whitelist
- Download returns handles only, never presigned URLs directly via MCP

### Example Workflow

```json
// 1. Search for models
{"jsonrpc": "2.0", "id": 1, "method": "tools/call",
 "params": {"name": "asset_search", "arguments": {"keyword": "nlp", "type": "MODEL"}}}

// 2. Get version list
{"jsonrpc": "2.0", "id": 2, "method": "tools/call",
 "params": {"name": "asset_list_versions", "arguments": {"assetId": "ast_xxx"}}}

// 3. Request download
{"jsonrpc": "2.0", "id": 3, "method": "tools/call",
 "params": {"name": "asset_request_download", "arguments": {"versionId": "ver_xxx"}}}
```

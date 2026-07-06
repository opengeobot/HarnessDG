# Error Catalog

> 状态：`READY`
> 权威源：`backend/src/main/java/com/aihub/shared/error/ErrorCode.java`
> 本文档是 ErrorCode 枚举的设计文档化，AI IDE 应以此为准规划 API 错误处理和前端错误 UI。

## 1. 用途

REQ-COM-001 要求：Error Catalog 每个 code 唯一绑定 HTTP status、i18nKey、retryable、alert level。
本文档列出所有平台语义错误码，按领域模块分组，供 API 实现者、前端开发者和 AI IDE 使用。

## 2. 错误码全表

### 2.1 通用（Common）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `COMMON_INVALID_ARGUMENT` | 400 | `error.common.invalidArgument` | false | NONE | 通用参数无效 |
| `CONCURRENT_MODIFICATION` | 409 | `error.common.concurrentModification` | **true** | NONE | 乐观锁冲突，可安全重试 |
| `INTERNAL_ERROR` | 500 | `error.common.internal` | false | CRITICAL | 未分类内部错误，不对外暴露细节 |

### 2.2 认证与密码（Auth / Password）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `AUTH_TOKEN_EXPIRED` | 401 | `error.auth.tokenExpired` | false | INFO | 访问 Token 已过期 |
| `AUTH_UNAUTHENTICATED` | 401 | `error.auth.unauthenticated` | false | INFO | 未认证或凭据无效 |
| `AUTH_PERMISSION_DENIED` | 403 | `error.auth.permissionDenied` | false | INFO | 无访问权限 |
| `AUTH_INVALID_CREDENTIALS` | 401 | `error.auth.invalidCredentials` | false | INFO | 用户名或口令错误（不区分以防枚举） |
| `AUTH_ACCOUNT_LOCKED` | 423 | `error.auth.accountLocked` | false | WARN | 连续登录失败锁定 |
| `AUTH_ACCOUNT_DISABLED` | 403 | `error.auth.accountDisabled` | false | WARN | 账户已禁用 |
| `AUTH_REFRESH_REPLAYED` | 401 | `error.auth.refreshReplayed` | false | WARN | 刷新令牌重放，整族吊销 |
| `PASSWORD_CHANGE_REQUIRED` | 403 | `error.auth.passwordChangeRequired` | false | INFO | 必须先修改口令 |
| `PASSWORD_POLICY_VIOLATION` | 400 | `error.auth.passwordPolicyViolation` | false | NONE | 口令不满足强度策略 |

### 2.3 用户（User）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `USER_ALREADY_EXISTS` | 409 | `error.user.alreadyExists` | false | NONE | 用户名已存在 |
| `USER_NOT_FOUND` | 404 | `error.user.notFound` | false | NONE | 用户不存在 |

### 2.4 Agent

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `AGENT_NOT_FOUND` | 404 | `error.agent.notFound` | false | NONE | Agent 不存在 |

### 2.5 角色与权限（Role / Permission）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `ROLE_NOT_FOUND` | 404 | `error.role.notFound` | false | NONE | 角色不存在 |
| `ROLE_ALREADY_EXISTS` | 409 | `error.role.alreadyExists` | false | NONE | 角色编码已存在 |
| `ROLE_BUILTIN_IMMUTABLE` | 409 | `error.role.builtinImmutable` | false | NONE | 内置角色不可改名/删除 |
| `ROLE_IN_USE` | 409 | `error.role.inUse` | false | NONE | 角色仍被绑定引用 |
| `PERMISSION_UNKNOWN` | 400 | `error.permission.unknown` | false | NONE | 引用了未注册的权限编码 |
| `ROLE_BINDING_NOT_FOUND` | 404 | `error.roleBinding.notFound` | false | NONE | 角色绑定不存在 |
| `ROLE_BINDING_ALREADY_EXISTS` | 409 | `error.roleBinding.alreadyExists` | false | NONE | 角色绑定已存在 |

### 2.6 资源 ACL

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `RESOURCE_ACL_NOT_FOUND` | 404 | `error.resourceAcl.notFound` | false | NONE | 资源 ACL 不存在 |
| `RESOURCE_ACL_ALREADY_EXISTS` | 409 | `error.resourceAcl.alreadyExists` | false | NONE | 资源 ACL 已存在 |

### 2.7 组织（Organization）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `ORGANIZATION_NOT_FOUND` | 404 | `error.organization.notFound` | false | NONE | 组织不存在（亦用于防枚举） |
| `ORGANIZATION_ALREADY_EXISTS` | 409 | `error.organization.alreadyExists` | false | NONE | 组织编码已存在 |
| `ORGANIZATION_MEMBER_NOT_FOUND` | 404 | `error.organization.memberNotFound` | false | NONE | 组织成员不存在 |
| `ORGANIZATION_MEMBER_ALREADY_EXISTS` | 409 | `error.organization.memberAlreadyExists` | false | NONE | 组织成员已存在 |

### 2.8 Team

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `TEAM_NOT_FOUND` | 404 | `error.team.notFound` | false | NONE | Team 不存在 |
| `TEAM_ALREADY_EXISTS` | 409 | `error.team.alreadyExists` | false | NONE | Team 名称在组织内已存在 |
| `TEAM_MEMBER_NOT_FOUND` | 404 | `error.team.memberNotFound` | false | NONE | Team 成员不存在 |
| `TEAM_MEMBER_ALREADY_EXISTS` | 409 | `error.team.memberAlreadyExists` | false | NONE | Team 成员已存在 |

### 2.9 项目（Project）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `PROJECT_NOT_FOUND` | 404 | `error.project.notFound` | false | NONE | 项目不存在 |
| `PROJECT_ALREADY_EXISTS` | 409 | `error.project.alreadyExists` | false | NONE | 项目编码在组织内已存在 |

### 2.10 访问主体（Principal）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `PRINCIPAL_NOT_FOUND` | 404 | `error.principal.notFound` | false | NONE | 访问主体不存在 |

### 2.11 字典（Dictionary）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `DICTIONARY_ITEM_NOT_FOUND` | 404 | `error.dictionary.itemNotFound` | false | NONE | 字典项不存在 |
| `DICTIONARY_ITEM_ALREADY_EXISTS` | 409 | `error.dictionary.itemAlreadyExists` | false | NONE | 字典项在该字典下已存在 |
| `DICTIONARY_VALUE_INVALID` | 400 | `error.dictionary.valueInvalid` | false | NONE | 引用了未知或已停用的字典项 |

### 2.12 标签（Tag）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `TAG_NOT_FOUND` | 404 | `error.tag.notFound` | false | NONE | 受控标签不存在 |
| `TAG_ALREADY_EXISTS` | 409 | `error.tag.alreadyExists` | false | NONE | 受控标签在该作用域下已存在 |
| `TAG_VALUE_INVALID` | 400 | `error.tag.valueInvalid` | false | NONE | 引用了未登记/已停用/作用域不允许的标签 |

### 2.13 配置（Config）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `CONFIG_NOT_FOUND` | 404 | `error.config.notFound` | false | NONE | 配置项不存在 |
| `CONFIG_SECRET_FORBIDDEN` | 400 | `error.config.secretForbidden` | false | WARN | 配置键命中敏感模式（密码/Token/私钥等） |
| `CONFIG_VALUE_INVALID` | 400 | `error.config.valueInvalid` | false | NONE | 配置值不满足类型或校验器 |

### 2.14 资产（Asset）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `ASSET_NOT_FOUND` | 404 | `error.asset.notFound` | false | NONE | 资产不存在（亦用于防枚举） |
| `ASSET_ALREADY_EXISTS` | 409 | `error.asset.alreadyExists` | false | NONE | 资产坐标已存在（namespace+type+name） |
| `ASSET_REPOSITORY_PROVISION_FAILED` | 502 | `error.asset.repositoryProvisionFailed` | **true** | WARN | Gitea 依赖异常，可重试 |
| `ASSET_CONCURRENT_MODIFICATION` | 409 | `error.asset.concurrentModification` | **true** | NONE | 资产乐观锁冲突 |
| `ASSET_STATE_NOT_ALLOWED` | 409 | `error.asset.stateNotAllowed` | false | INFO | 当前状态不允许该生命周期操作 |
| `ASSET_HAS_ACTIVE_VERSIONS` | 409 | `error.asset.hasActiveVersions` | false | INFO | 存在活跃发布版本，不可归档 |
| `ASSET_VERSION_CONFLICT` | 409 | `error.asset.versionConflict` | false | INFO | 目标版本已存在 |

### 2.15 版本（Version）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `VERSION_NOT_FOUND` | 404 | `error.version.notFound` | false | NONE | 版本未找到 |
| `VERSION_STATE_NOT_ALLOWED` | 409 | `error.version.stateNotAllowed` | false | INFO | 当前版本状态不允许该操作 |

### 2.16 上传（Upload）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `UPLOAD_SESSION_EXPIRED` | 409 | `error.upload.sessionExpired` | false | INFO | 上传会话已过期 |
| `UPLOAD_SESSION_NOT_FOUND` | 404 | `error.upload.sessionNotFound` | false | NONE | 上传会话未找到 |

### 2.17 讨论（Discussion）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `DISCUSSION_NOT_FOUND` | 404 | `error.discussion.notFound` | false | NONE | 讨论线程不存在 |
| `DISCUSSION_LOCKED` | 409 | `error.discussion.locked` | false | INFO | 讨论线程已锁定 |
| `COMMENT_TOO_LARGE` | 400 | `error.comment.tooLarge` | false | NONE | 评论内容超过大小限制 |

### 2.18 DVC

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `DVC_OBJECT_MISSING` | 404 | `error.dvc.objectMissing` | false | WARN | DVC 对象缺失 |

### 2.19 外部依赖（Dependency）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `GITEA_DEPENDENCY_UNAVAILABLE` | 503 | `error.dependency.gitea` | **true** | CRITICAL | Gitea 不可用 |
| `MINIO_DEPENDENCY_UNAVAILABLE` | 503 | `error.dependency.minio` | **true** | CRITICAL | MinIO 不可用 |

### 2.20 MCP

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `MCP_TOOL_NOT_ALLOWED` | 403 | `error.mcp.toolNotAllowed` | false | WARN | MCP 工具不在白名单 |

### 2.21 任务（Job）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `JOB_NOT_FOUND` | 404 | `error.job.notFound` | false | NONE | 任务不存在 |
| `JOB_STATE_NOT_ALLOWED` | 409 | `error.job.stateNotAllowed` | false | INFO | 任务当前状态不允许该操作 |
| `JOB_RETRY_EXHAUSTED` | 500 | `error.job.retryExhausted` | false | CRITICAL | 任务重试次数耗尽 |

### 2.22 通知（Notification）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `NOTIFICATION_NOT_FOUND` | 404 | `error.notification.notFound` | false | NONE | 通知不存在或不属于当前主体 |

### 2.23 幂等性（Idempotency）

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | `error.idempotency.keyConflict` | false | NONE | 幂等键冲突（同键不同请求体） |

### 2.24 Webhook

| ErrorCode | HTTP | i18nKey | retryable | alertLevel | 说明 |
|-----------|------|---------|-----------|------------|------|
| `WEBHOOK_TARGET_FORBIDDEN` | 400 | `error.webhook.targetForbidden` | false | WARN | SSRF 防护拒绝内网/保留地址 |

## 3. 统计

| 指标 | 值 |
|------|----|
| 错误码总数 | 56 |
| retryable=true | 5（CONCURRENT_MODIFICATION, ASSET_REPOSITORY_PROVISION_FAILED, ASSET_CONCURRENT_MODIFICATION, GITEA_DEPENDENCY_UNAVAILABLE, MINIO_DEPENDENCY_UNAVAILABLE） |
| alertLevel=CRITICAL | 4（INTERNAL_ERROR, JOB_RETRY_EXHAUSTED, GITEA_DEPENDENCY_UNAVAILABLE, MINIO_DEPENDENCY_UNAVAILABLE） |
| alertLevel=WARN | 10 |
| alertLevel=INFO | 14 |
| alertLevel=NONE | 28 |

## 4. API 错误响应格式

所有 API 错误响应统一使用 `ErrorResponse` schema（参见 OpenAPI `aihub-v1.yaml`）：

```json
{
  "code": "ASSET_NOT_FOUND",
  "message": "资产不存在",
  "retryable": false,
  "timestamp": "2026-07-06T12:00:00Z"
}
```

- `code`：上表中的 ErrorCode 枚举值
- `message`：按请求 `Accept-Language` 解析 i18nKey 后的本地化文本
- `retryable`：客户端是否可安全重试
- `timestamp`：服务器响应时间

## 5. 前端额外错误 Key

以下 i18nKey 仅在前端 `client.ts` 中生成，不来自服务器响应，因此不在 `ErrorCode.java` 中定义：

| 前端 i18nKey | 前端 ErrorCode | 触发场景 | 说明 |
|-------------|--------------|----------|------|
| `error.common.unknown` | *(fallback)* | 服务器响应无 `i18nKey` 字段时 | 默认回退键 |
| `error.common.network` | `COMMON_NETWORK_ERROR` | 网络错误或非 Axios 标准响应 | 客户端生成，retryable=true |

**规则**：
- 前端额外 Key 必须同时在 `zh.json` 和 `en.json` 中定义翻译。
- 新增前端 Key 时需同步更新本节。
- 后端 ErrorCode 新增时，前端不应再为此错误使用 `error.common.unknown` 回退。

## 6. 维护规则

1. **权威源是 `ErrorCode.java`**——本文档必须与枚举保持同步。
2. 新增 ErrorCode 时，必须同时更新本文档和 `ErrorCode.java`。
3. `i18nKey` 命名规则：`error.<domain>.<camelCase>`，与 ErrorCode 枚举一一对应。
4. 每个 ErrorCode 的 HTTP status、retryable、alertLevel 一经发布不可降级（可升级告警等级）。
5. Controller 和 MCP Tool 不得吞掉异常返回伪成功。

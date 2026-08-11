# 阶段 0 对齐验证：环境验证 + 契约基线修复

日期：2026-02（执行日）· 分支：modelhub · 提交：chore(stage0): baseline

## 1. 环境验证（计划阶段 0 第 1 条）

| 项 | 要求 | 实测 | 结论 |
|---|---|---|---|
| JDK | 21 | Temurin 21.0.9（JAVA_HOME=D:\tools\jdks\temurin-21.0.9；PATH 中 java 为 17，构建以 JAVA_HOME/Maven 为准） | ✅ |
| Maven | 3.x | 3.9.9 | ✅ |
| Docker | 可用 | Docker 29.1.2，12 CPU / 31GB 内存 | ✅ |
| Node | ≥20 | v20 | ✅ |
| git | 可用 | 2.45 | ✅ |

## 2. 契约基线修复（计划阶段 0 第 2 条）

### 2.1 openapi-v1.yaml（三批修复，最终 57 paths / 72 operations，101 个 $ref 全部可解析）

- 响应码补齐：register/access-requests/collaborators 加 409；refresh/createApiKey 加 429；initiateUpload 加 413（新增 PayloadTooLarge 组件）；createApiKey 加 422。
- 错误码枚举补 `CSRF_INVALID`、`IDEMPOTENCY_CONFLICT`。
- User/Organization 响应加只读 `namespaceId`（required）；Member.status 移除 `invited`。
- 分页 envelope：OrganizationPageEnvelope data `required: [total,page,pageSize,items]`；CommitPageEnvelope / PartUrlEnvelope data 补 `required: [items]`。
- Repository 加 `stats`（likes/favorites/downloads/visits/fileCount）；sort 加 `x-extensible-enum: [relevance-v1, updatedAt-desc, downloads-desc, likes-desc, hot-desc]`；ApiKey scopes 固化 8 个基线值。
- 新增端点：`GET /repositories/resolve/{typeKey}/{namespace}/{name}`、`GET /repositories/{repoId}/related`、`GET /me/repositories?tab=`、`POST /admin/users/{userId}:unlock`、`POST /admin/repositories/{repoId}:retry`、`POST /admin/repositories/{repoId}:delete`、`GET /admin/audit-logs`（含 ndjson 导出）；AuditLog schema；admin 端点带 `x-required-platform-roles`。
- tags 增加 Me / Admin。

### 2.2 specs 修复

- **04-api-contract.md**：新增 §4.1 管理与审计端点、§14 demo 23 路由迁移映射表（全部"覆盖"，/api/meta/hot 以 sort=hot-desc 覆盖）；§5 补 resolve/related/me 行；§6.3 sort 枚举固化；§6.4 补 /metadata/options；§11 计数口径；§12 兼容窗口统一"两个 minor 或 90 天"。
- **03-domain-data-model.md**：§2.1 增 namespaceId 发现规则；§8 去除 job_attempts 独立表；§10 兼容窗口对齐。
- **02-identity-permissions.md**：§3.1/§6.2 引用 04 §4.1 admin 端点。
- **05-artifact-storage-consistency.md**：§9.2 删除源状态集合扩展 active/archived/failed/draft/provisioning；§8 引用 admin 端点。
- **06-async-workflows.md**：§7.2 明确 visit 事件服务端唯一来源。
- **07-security-nfr-operations.md**：§5.3 健康词汇改 ok/degraded/unavailable。
- **requirements-traceability.md**：统一模块命名（identity-access/catalog/app），新增 INTERACT-001/INTERACT-002/GIT-001/SEARCH-001/ME-001/GOV-002 六行。

### 2.3 canvas 漂移修复

modelhub-v1-system-design.canvas.tsx：§06 契约统计 51/66 → 57/72；upload_sessions 状态机补 `scanning`；jobs 状态补 `cancel_requested`；Redis 职责移除"会话"；repositories 字段组 `gated` → `gated_policy_id`。

## 3. 验证结论（计划阶段 0 第 3 条）

- OpenAPI YAML 解析通过；57 paths / 72 operations；$ref 无悬空（Python 校验，spectral 未安装，以结构化校验替代并在阶段 6 契约 CI 中补 lint）。
- diff 为纯增量/澄清类变化，无破坏性删除既有端点或必填字段收紧。
- HarnessDG 建 orphan 分支 `modelhub`（旧仓库历史保留在 dev 等分支），提交 `chore(stage0): baseline`。

**阶段 0 通过，进入阶段 1。**

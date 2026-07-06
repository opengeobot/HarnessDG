# P0-B 管理页面 MIPS（Minimum Implementable Page Specification）

> 状态：`READY`
> 决策：`DEC-013`（zh-CN/en-US、Ant Design 企业后台基线、Playwright E2E）
> 目的：为 16 个 P0-B 管理页面提供 API、权限、i18n 前缀和交互元素的最低可实施规格。
> 约束：所有页面复用 `frontend/src/shared/api/client.ts`、`frontend/src/features/admin/api.ts`、
> `frontend/src/shared/i18n/` 统一基础设施；禁止页面级 fetch 或硬编码文案。

---

## MIPS-ADM-001：用户管理（PAGE-ADM-001）

| 项 | 值 |
| --- | --- |
| Route | `/admin/users` |
| Permission | `user:read`（列表/查看）、`user:manage`（创建/编辑/启停/重置） |
| API | `listUsers(params)` → `PageResultUser`；`createUser`；`updateUser`；`enableUser`/`disableUser`；`resetUserPassword` |
| i18n 前缀 | `admin.users.*` |
| 交互元素 | 搜索框（keyword）、创建 Modal（username/displayName/tempPassword/language）、编辑 Drawer、启用/禁用开关、重置密码 Modal |
| 边界 | 按钮按 `user:manage` 权限显隐；禁用确认提示 Token 吊销；临时密码仅显示一次 |
| 状态 | Loading → Skeleton；Empty → 空态文案；Error → QueryBoundary 重试 |

## MIPS-ADM-002：Agent 管理（PAGE-ADM-002）

| 项 | 值 |
| --- | --- |
| Route | `/admin/agents` |
| Permission | `agent:read`（列表）、`agent:register`/`agent:authorize`（注册/启停/白名单） |
| API | `listAgents()`；`createAgent` → `CreatedAgent`（含一次性凭据）；`enableAgent`/`disableAgent`；`updateAgentToolAllowlist` |
| i18n 前缀 | `admin.agents.*` |
| 交互元素 | 注册 Modal（agentType/vendor/maxSensitivityLevel/grantScopes）、一次性凭据 Alert（关闭前确认保存）、启停开关、Tool 白名单编辑 Modal |
| 边界 | 凭据仅展示一次，关闭后不可恢复；Scope/Tool 使用受控选择器（不手动输入）；高风险操作默认禁用 |
| 状态 | 凭据 Modal → 强制确认"已妥善保存"后方可关闭 |

## MIPS-ADM-003：组织管理（PAGE-ADM-003）

| 项 | 值 |
| --- | --- |
| Route | `/admin/organizations` |
| Permission | `organization:manage` |
| API | `listOrganizations()`；`createOrganization`；`listOrganizationMembers`；`addOrganizationMember`/`removeOrganizationMember` |
| i18n 前缀 | `admin.organizations.*` |
| 交互元素 | 组织列表、创建 Modal（orgCode/orgName）、成员管理 Drawer（添加/移除）、Principal 选择器（搜索 `listPrincipals`） |
| 边界 | 成员引用 Principal 选择器而非手动输入 ID；防自提权（不可授予超出自身 Scope 的角色） |

## MIPS-ADM-004：Team 管理（PAGE-ADM-004）

| 项 | 值 |
| --- | --- |
| Route | `/admin/organizations/:organizationId/teams` |
| Permission | `team:manage`（待确认） |
| API | `listTeams(orgId)`；`createTeam`；`updateTeam`；`listTeamMembers`；`addTeamMember`/`removeTeamMember` |
| i18n 前缀 | `admin.teams.*` |
| 交互元素 | Team 列表、创建/编辑 Modal（teamName）、成员管理 Drawer、Principal 选择器、角色选择器 |
| 边界 | 组织上下文固定（来自 URL 参数）；不可移除最后 Owner；跨组织请求后端拒绝 |

## MIPS-ADM-005：项目管理（PAGE-ADM-005）

| 项 | 值 |
| --- | --- |
| Route | `/admin/organizations/:organizationId/projects` |
| Permission | `project:view`/`project:manage` |
| API | `listOrganizationProjects(orgId)`；`createProject` |
| i18n 前缀 | `admin.projects.*` |
| 交互元素 | 项目列表、组织选择器（前置）、创建 Modal（projectCode/projectName） |
| 边界 | 先选组织再看项目；跨组织拒绝 |

## MIPS-ADM-006：角色管理（PAGE-ADM-006）

| 项 | 值 |
| --- | --- |
| Route | `/admin/roles` |
| Permission | `authorization:read`（列表）、`authorization:manage`（创建/编辑/删除） |
| API | `listRoles()`；`createRole`；`updateRole`；`deleteRole` |
| i18n 前缀 | `admin.roles.*` |
| 交互元素 | 角色列表（内置/自定义标记）、创建/编辑 Modal（roleCode/roleName/permissionSet 多选）、删除确认 |
| 边界 | 内置角色（ADMIN/READER/ASSET_AUTHOR/OBSERVER）不可改删；Permission 选择器从 `listPermissions()` 获取；不可授予超出委派范围的权限 |

## MIPS-ADM-007：权限清单（PAGE-ADM-007）

| 项 | 值 |
| --- | --- |
| Route | `/admin/permissions` |
| Permission | `authorization:read` |
| API | `listPermissions()` → `PermissionView[]` |
| i18n 前缀 | `admin.permissions.*` |
| 交互元素 | 只读表格（code/resource/action/i18nKey/使用角色数） |
| 边界 | 纯只读；UI 不提供新增权限入口（权限由后端 seed 管理） |

## MIPS-ADM-008：角色绑定（PAGE-ADM-008）

| 项 | 值 |
| --- | --- |
| Route | `/admin/role-bindings` |
| Permission | `authorization:read`/`authorization:manage` |
| API | `listRoleBindings()`；`createRoleBinding`；`deleteRoleBinding` |
| i18n 前缀 | `admin.roles.*`（复用） |
| 交互元素 | 绑定列表、创建 Modal（Principal/Team 选择器 + Role 选择器 + Scope 范围）、删除确认 |
| 边界 | 所有引用使用选择器（不手动输入 ID）；重复绑定冲突提示 |

## MIPS-ADM-009：资源 ACL（PAGE-ADM-009）

| 项 | 值 |
| --- | --- |
| Route | `/admin/resource-acls` |
| Permission | `authorization:read`/`authorization:manage` |
| API | `listResourceAcls()`；`createResourceAcl`；`deleteResourceAcl` |
| i18n 前缀 | `admin.roles.*`（复用） |
| 交互元素 | ACL 列表、创建 Modal（资源类型选择 + 主体选择 + Permission 选择）、删除确认 |
| 边界 | 资源类型受控选择；展示最终授权解释（角色 + ACL 合并视图） |

## MIPS-ADM-010：字典管理（PAGE-ADM-010）

| 项 | 值 |
| --- | --- |
| Route | `/admin/dictionaries` |
| Permission | `dictionary:read`/`dictionary:manage` |
| API | `listDictionaries()`；`listDictionaryItems(dictCode)`；`createDictionaryItem`；`updateDictionaryItem`；`enableDictionaryItem`/`disableDictionaryItem` |
| i18n 前缀 | `admin.dictionaries.*` |
| 交互元素 | 左侧字典类型列表、右侧字典项表格、新增项 Modal（itemCode/displayName/i18nKey/sortOrder）、编辑行、启停开关 |
| 边界 | 稳定 code 不可随意修改（编辑时标记锁定字段）；历史引用计数提示 |

## MIPS-ADM-011：标签管理（PAGE-ADM-011）

| 项 | 值 |
| --- | --- |
| Route | `/admin/tags` |
| Permission | `tag:read`/`tag:manage` |
| API | `listTags(params)`；`createTag`；`updateTag`；`enableTag`/`disableTag` |
| i18n 前缀 | `admin.tags.*` |
| 交互元素 | 标签列表（scopeType 筛选）、创建/编辑 Modal（tagCode/displayName/scopeType/color/orgId）、启停开关 |
| 边界 | 组织级标签需绑定 orgId；唯一性校验；停用影响提示（关联资产数） |

## MIPS-ADM-012：配置管理（PAGE-ADM-012）

| 项 | 值 |
| --- | --- |
| Route | `/admin/configurations` |
| Permission | `system:configure` |
| API | `listConfigurations()`；`updateConfiguration(configKey, payload)` |
| i18n 前缀 | `admin.configurations.*` |
| 交互元素 | 配置列表（configKey/currentValue/type/hotReloadable/version）、编辑 Drawer（类型化输入）、高风险配置二次确认 |
| 边界 | Secret 类型 Key 拒绝在 UI 编辑（标记 `CONFIG_SECRET_FORBIDDEN`）；值校验（JSON/NUMBER/BOOLEAN）；热更新标记 |

## MIPS-ADM-013：任务管理（PAGE-ADM-013）

| 项 | 值 |
| --- | --- |
| Route | `/admin/jobs` |
| Permission | `job:read`/`job:manage` |
| API | `listJobs(params)` → `CursorPage<JobView>`；`retryJob`；`cancelJob` |
| i18n 前缀 | `admin.jobs.*` |
| 交互元素 | 游标分页列表（jobId/status/jobType/retryCount/nextRunAt/errorCode）、状态筛选、重试/取消按钮 |
| 边界 | 只在合法状态展示动作按钮（retry: FAILED/CANCELLED；cancel: PENDING/RUNNING）；Payload 脱敏 |

## MIPS-ADM-014：审计日志（PAGE-ADM-014）

| 项 | 值 |
| --- | --- |
| Route | `/admin/audit-logs` |
| Permission | `audit:read` |
| API | `listAuditLogs(params)` → `CursorPage<AuditLogView>` |
| i18n 前缀 | `admin.auditLogs.*` |
| 交互元素 | 游标分页列表（time/principal/action/resource/result/errorCode）、筛选器（principalId/action/resourceId）、详情 Drawer |
| 边界 | 纯只读；详情字段脱敏（Token/密码/凭据不展示）；无导出越权入口 |

## MIPS-ADM-015：通知管理（PAGE-ADM-015）

| 项 | 值 |
| --- | --- |
| Route | `/admin/notifications` |
| Permission | `notification:read`（个人）、管理权限待确认（Outbox/Delivery） |
| API | `listNotifications(params)`；`markNotificationRead`；`listOutboxEvents`；`getOutboxPendingCount`；`listWebhookDeliveries`；`retryWebhookDelivery` |
| i18n 前缀 | `admin.notifications.*` |
| 交互元素 | Tab（用户通知/系统管理）、用户通知游标列表（markRead）、Outbox 表格（eventId/aggregateType/processed/pending）、Delivery 表格（deliveryId/targetUrl/attempts/lastResponse/retry） |
| 边界 | 管理 Tab 需额外权限（待决策）；重投递按钮仅在 FAILED 状态展示 |

## MIPS-ADM-016：系统依赖（PAGE-ADM-016）

| 项 | 值 |
| --- | --- |
| Route | `/admin/dependencies` |
| Permission | `system:observe` |
| API | `fetchSystemDependencies()` → `SystemDependencySummary`；`getMetricsSummary()` → `MetricsSummary` |
| i18n 前缀 | `admin.dependencies.*` |
| 交互元素 | 依赖健康表格（component/status/latencyMs）、指标摘要卡片（generatedAt/jobs/dependencies） |
| 边界 | Endpoint/凭据状态脱敏；不提供 Secret 展示；只读仪表盘 |

---

## 全局交互约束（适用所有 P0-B 管理页面）

### 权限按钮显隐

- 创建/编辑/删除/启停按钮仅在当前主体拥有对应 `*:manage` 权限时展示；
- `*:read` 仅展示列表和只读详情；
- Route Guard 和后端双重校验，UI 只做体验提示不做安全判断。

### 受控选择器

- 字典项、标签、Principal、Team、Role、Permission、Tool 均使用搜索选择器；
- 禁止 `mode="tags"` 或任意文本输入创建受控值；
- 选择器数据来自对应 `list*` API。

### i18n 约定

- 所有文案经 `useTranslation()` + i18nKey 渲染；
- 字典/标签显示名优先 i18n message，缺失时安全回退到稳定 code 并控制台告警；
- API 错误使用 `error.i18nKey`，不解析 `error.message` 文本。

### 列表分页

- 高增长列表（审计日志、任务、通知）使用 Cursor 分页；
- 小型后台列表（角色、权限、字典类型）使用页码分页；
- 筛选条件同步到 URL query params。

### 状态展示

| 状态 | 组件 | 行为 |
| --- | --- | --- |
| Loading | Ant Design Skeleton | 与目标布局匹配 |
| Empty | Empty 组件 + 合法下一步 | 区分"无数据"和"筛选无结果" |
| Error (retryable) | QueryBoundary + 重试按钮 | 展示 requestId |
| Error (non-retryable) | 错误说明 + 安全导航 | 不泄露资源存在性 |
| Unauthorized | 403 页面 | 展示所需能力，不泄露资源 |
| Conflict | 冲突原因 + 重新加载 | 不静默覆盖 |

# 页面目录与完成边界

> 状态：`PROPOSED`
> 规则：表中的“完成”只代表页面级最低行为；最终仍需所属 Journey 的 E4 验收。

## 1. 公共与身份页面

| Page ID | Route | 主要 Persona | 数据/动作 | 页面完成条件 |
| --- | --- | --- | --- | --- |
| `PAGE-AUTH-001` | `/login` | User | 登录、错误/锁定、returnTo | 无账号枚举；Token 安全；首次改密跳转；zh/en；E2+E4 |
| `PAGE-AUTH-002` | `/profile` | 已登录 User | 当前主体、角色/Scope 摘要、改密、登出 | 当前/新密码校验；改密后会话策略明确；无 Token 展示 |
| `PAGE-COM-001` | 全局 403 | 所有主体 | 所需 Permission/Scope、申请路径 | 不泄露私有资源；requestId 可复制 |
| `PAGE-COM-002` | 全局 404 | 所有主体 | 返回安全位置 | 不回显未授权资源名称 |
| `PAGE-COM-003` | `/notifications` | 已登录主体 | 游标列表、已读、关联资源跳转 | 只能看本人；停用资源安全回显 |

## 2. P0-B 管理页面

| Page ID | Route | Permission | 关键内容/动作 | 额外边界 |
| --- | --- | --- | --- | --- |
| `PAGE-ADM-001` | `/admin/users` | `user:read/manage` | 搜索、创建、编辑、启停、重置密码 | 操作按钮区分 read/manage；状态转换合法；临时密码不留存 |
| `PAGE-ADM-002` | `/admin/agents` | `agent:register/authorize` | 注册、一次性凭据、Scope、Tool、启停 | Scope/Tool 受控选择；高风险默认禁用 |
| `PAGE-ADM-003` | `/admin/organizations` | `organization:manage` | 组织、成员、状态 | 选择 Principal 而非输入 ID；防自提权 |
| `PAGE-ADM-004` | `/admin/organizations/:id/teams` | 待确认 | Team、成员、Owner 引用影响 | 受 `Q-103` 阻塞；不可移除最后 Owner |
| `PAGE-ADM-005` | `/admin/organizations/:id/projects` | `project:view/manage` | 项目列表、创建、状态 | 组织上下文固定；跨组织拒绝 |
| `PAGE-ADM-006` | `/admin/roles` | `authorization:read/manage` | 角色、Permission 集、作用域、启停 | 内置角色不可改删；不可授予超出委派范围 |
| `PAGE-ADM-007` | `/admin/permissions` | `authorization:read` | 只读 Catalog、使用角色数 | 不允许 UI 随意新增 Permission |
| `PAGE-ADM-008` | `/admin/role-bindings` | `authorization:read/manage` | Principal/Team、Role、Scope 绑定 | 所有引用用选择器；重复绑定冲突 |
| `PAGE-ADM-009` | `/admin/resource-acls` | `authorization:read/manage` | 资源、主体、Permission ACL | 资源类型受控；展示最终授权解释 |
| `PAGE-ADM-010` | `/admin/dictionaries` | `dictionary:read/manage` | 类型、item、i18n、启停、版本 | 稳定 code 不可随意修改；历史引用提示 |
| `PAGE-ADM-011` | `/admin/tags` | `tag:read/manage` | 平台/组织标签、i18n、颜色、启停 | 组织上下文和唯一性；停用影响提示 |
| `PAGE-ADM-012` | `/admin/configurations` | `system:configure` | 类型化值、作用域、版本、热更新 | Secret Key/Value 拒绝；高风险二次确认 |
| `PAGE-ADM-013` | `/admin/jobs` | `job:read/manage` | 状态、attempt、错误、Trace、重试/取消 | 只在合法状态展示动作；Payload 脱敏 |
| `PAGE-ADM-014` | `/admin/audit-logs` | `audit:read` | 游标筛选、主体/动作/结果/资源/Trace | 只读；详情字段脱敏；无导出越权 |
| `PAGE-ADM-015` | `/admin/notifications` | 待确认管理权限 | 模板/Delivery/Outbox/重发 | 当前权限表只有本人 notification:read，管理权限需补决策 |
| `PAGE-ADM-016` | `/admin/dependencies` | `system:observe` | 健康、延迟、版本、积压、Trace 链接 | Endpoint/凭据状态脱敏；不提供 Secret |

当前路由缺少 role-bindings/resource-acls/teams 的独立可发现入口；“存在 API”不能替代管理体验。

## 3. P1 资产目录页面

| Page ID | Route | Permission | 关键内容/动作 | 完成条件 |
| --- | --- | --- | --- | --- |
| `PAGE-AST-001` | `/assets` | `asset:read` | 搜索、类型/组织/项目/治理/状态筛选、DATASET 分类 Facet、Cursor | 数据库权限过滤；Facet 不泄漏；URL 保存筛选；无权限结果不可枚举 |
| `PAGE-AST-002` | `/assets/new` | `asset:create` | 选择作用域/类型/Owner/字典/tagId、仓库方式 | 所有治理值受控；幂等；建仓进度/失败恢复 |
| `PAGE-AST-003` | `/assets/:id/overview` | `asset:read` | Card、治理、Owner、最新发布、使用限制、详情任务导航 | 精确来源 Commit；历史停用项回显；未实现阶段不显示 Placeholder |
| `PAGE-AST-004` | `/assets/:id/settings` | `asset:update/delete` | 元数据、Owner、治理、弃用/归档 | 乐观锁；高风险权限；影响提示；审计 |
| `PAGE-AST-005` | `/assets/:id/access` | 授权读取/管理 | 角色、Team、ACL、最终权限解释 | 不只显示原始绑定；防自提权 |
| `PAGE-AST-006` | `/assets/:id/lineage` | `asset:read` | 精确版本依赖图 | 权限过滤下不泄露不可见节点 |

MODEL/DATASET 的创建与详情字段应由共享 Schema + 类型扩展驱动，不复制两套漂移表单。

## 4. P2/P3 版本、上传与审批页面

| Page ID | Route | Permission | 关键内容/动作 | 完成条件 |
| --- | --- | --- | --- | --- |
| `PAGE-UPL-001` | `/uploads` | `asset:upload` | 会话列表、进度、过期、失败重试 | 刷新恢复；仅失败 Part 重传；大于限额引导 CLI |
| `PAGE-UPL-002` | `/uploads/:sessionId` | 会话 Owner | 文件/Part/校验/Worker/Git 状态 | 不显示预签名查询串；取消/重试状态合法 |
| `PAGE-VER-001` | `/assets/:id/versions` | `asset:read` | 版本、状态、Tag/Commit/Digest、比较 | Published 三元组完整；精确排序 |
| `PAGE-VER-002` | `/assets/:id/versions/:version` | `asset:read` | Manifest、Artifact、校验报告、审批、下载 | 不返回二进制；下载票据按权；状态动作权限化 |
| `PAGE-VER-003` | `/assets/:id/versions/:version/files` | `asset:read` | Artifact 树、路径、大小、媒体类型、摘要、单项/批量下载 | 精确 Version；路径安全；权限过滤；不显示 URL 查询串 |
| `PAGE-DST-001` | `/assets/:id/versions/:version/preview` | `asset:read` + preview policy | Schema、Split、脱敏样例、统计、来源摘要 | 支持格式矩阵；实时授权；样例免责声明；PII/Secret 不泄漏 |
| `PAGE-DST-002` | `/assets/:id/discussions` | `asset:read/discuss/moderate` | Thread、回复、修订、撤回、锁定、通知 | 继承资产权限；不可信 Markdown；Tombstone；游标与审计 |
| `PAGE-REV-001` | `/reviews` | `asset:review` | 待审队列、风险、SLA/状态 | 数据权限过滤；提交人与审核人规则 |
| `PAGE-REV-002` | `/reviews/:requestId` | `asset:review` | 冻结 Commit、Diff、License/敏感/质量、批准/驳回 | 审批对象不可漂移；意见规则；高风险确认 |

## 5. P4 Agent 接入页面

| Page ID | Route | Permission | 关键内容/动作 | 完成条件 |
| --- | --- | --- | --- | --- |
| `PAGE-INT-001` | `/integrations` | 授权读取 | 接入方式、兼容版本、状态 | 区分 MCP/REST；不展示任何凭据 |
| `PAGE-INT-002` | `/integrations/agents/new` | `agent:register/authorize` | 注册、最小 Scope/Tool、一次性凭据 | 默认只读；Secret Store 指引；连接探测 |
| `PAGE-INT-003` | `/integrations/agents/:id` | `agent:authorize` | 状态、Scope、Tool、最近调用/拒绝、吊销 | 高风险默认关闭；变更审计 |
| `PAGE-INT-004` | `/integrations/clients` | `token:create` | OpenAPI 包、Client 凭据、调用示例 | 裁剪契约；凭据只显示一次；无永久对象凭据 |

## 6. 页面状态证据

每个 Page ID 至少关联：

- 一个组件/Hook 测试（E2）；
- 一个权限允许和一个权限拒绝浏览器场景；
- Loading、Empty、Error、Retry、Success 截图或 DOM 断言；
- zh-CN/en-US 两种关键状态；
- 目标视口的视觉基准；
- LocalStorage/console/error report 的 Secret 检查；
- 对应 API Contract 的 Mock 不作为唯一 E4 证据。


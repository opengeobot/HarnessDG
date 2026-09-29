# HarnessDG 页面功能逐项点击测试报告

- 功能: 按 PAGE/AC 口径对每一页面功能执行浏览器真实点击测试（Playwright E4）并汇总结果
- 时间: 2026-09-29
- 作者: AxeXie
- 分支/提交: `ai` @ `e9aa2a0`（TASK-P0BR-066 整改提交后的工作区；本报告未修改任何产品代码）

## 1. 测试环境与方式

- Compose 全栈持续运行 24h：`aihub-frontend/nginx/postgres/backend/worker/gitea/minio` 7 容器，postgres/backend/gitea/minio healthy。
- 入口：`http://localhost:5173`（Vite dev，实测存活）→ `http://localhost:8080` Nginx 网关 → 后端；非 mock、非内存替身。
- 方式：`cd frontend; pnpm test:e2e`（Playwright chromium，1 worker，每用例为浏览器真实导航/点击/表单/断言），凭据取环境变量（不落盘、不入日志）。
- 结果：**98 tests / 19 spec 文件；97 passed / 1 skipped / 0 failed，3.5m，exit 0**。唯一 skip 为 `profile.spec.ts:84`「登录名回显 username」`test.fixme`——即 D-02，需契约先行卡 `TASK-P0BR-067`（草案已落盘，待人工批准）在 `/me` 增可选 `username` 后解除。

## 2. 逐项功能点击结果（按功能域）

### 2.1 认证与会话（auth.spec 4 + error-paths.spec 5）

| 功能 | 点击路径 | 结果 |
| --- | --- | --- |
| 未登录重定向 | 直访受限 URL → 登录页 | PASS |
| 登录成功跳转 | 表单填凭据 → 提交 → 主页 | PASS |
| 错误凭据反馈 | 错误口令提交 → 提示渲染 | PASS |
| 登出清 Token | 点击登出 → 跳登录页、内存 token 清空 | PASS |
| 未登录访问管理页/个人设置 | 直连 → 重定向登录 | PASS |
| 未知路由 404 | 直访乱路径 → 404 页 + 返回入口 | PASS |
| 缺 scope 403 | 无 user:read 主体 → UI 403 且后端独立拒绝 | PASS |
| 引导管理员正向 | 改密引导态访问用户管理 → 不出现 403 | PASS |

### 2.2 个人中心（profile.spec 5 + 1 fixme）

| 功能 | 结果 |
| --- | --- |
| /profile 展示账户信息（主体、角色/Scope 摘要） | PASS |
| 「登录名」回显 username | **SKIP（fixme，D-02 → TASK-P0BR-067）** |
| 改密被后端拒绝 → 页内回显原因不跳转 | PASS |
| 两次新密码不一致 → 前端拦截 | PASS |
| 新密码 <12 位 → 前端拦截 | PASS |

### 2.3 权限可见性（permission.spec 4 + discoverability.spec 5）

| 功能 | 结果 |
| --- | --- |
| 管理员可见管理中心菜单并进入用户管理 | PASS |
| 无 authorization:read → 授权类菜单隐藏 + RouteGuard 直连拒绝 | PASS |
| 多主体目录隔离：组织成员见 INTERNAL 夹具、非成员持 asset:read 不可见 | PASS |
| 侧边导航打开角色绑定/资源 ACL/Team 入口 | PASS |
| 无 project:view → Team 入口隐藏 | PASS |
| 版本列表「查看全部」到达真实页面（非 404 死链） | PASS |

### 2.4 管理端页面状态与逐页可达（admin.spec 4 + admin-states.spec 7 + admin-pages.spec 12）

- 用户/字典/角色管理加载成功：PASS×3；组织下拉选项来自真实 API：PASS。
- Loading 骨架 / Empty 空文案 / Error+重试恢复真实 Success：角色绑定、资源 ACL、版本列表三页 PASS×7。
- 逐页点击可达（066 新断言全在其中）：组织列表/成员抽屉（organization:manage 门控正反向）、Team/项目页组织上下文、任务管理/幂等记录/系统告警（含 D-07 readonlyHint 文案可见）/系统依赖、配置管理/审计日志/通知中心（D-08 system:observe Tab 门控正反向）、标签/权限清单/字典、版本中心/上传中心/发布审批死链引导、/access token:create 边界正反向（D-09）：PASS×12。
- 运维越权边界：越权入口隐藏 + 直连被 RouteGuard 与后端双重拒绝：PASS。

### 2.5 用户管理（user-management.spec 4，含 D-01 回归）

创建用户 PENDING_ACTIVATION+强制改密、禁用吊销 Token 后启用恢复、重置口令后列表行状态/时间**即时回显**（D-01）、临时口令 <12 位前端拦截：PASS×4。

### 2.6 i18n（i18n.spec 5）

登录页切英文、刷新持久化、导航随语言、`Accept-Language` 一致、后端错误按 i18nKey 目标语言渲染：PASS×5。

### 2.7 资产目录与数据集体验（asset.spec 6 + dataset.spec 4）

列表导航+检索控件、弹窗登记、搜索过滤、详情完整信息、弃用→归档→恢复生命周期、缺失资产 UI/后端一致不存在：PASS×6。
数据集：类型过滤、画像 Card 字段、版本切换 URL 同步、讨论 Tab 三态（200/403/空态）——含 066 按 G6 语义重写的回退用例：PASS×4。

### 2.8 版本与发布（version-management.spec 6 + publish-flow.spec 4 + review-workflow.spec 8）

- 版本中心：无深链诚实引导、深链真实夹具版本行、选择版本回显后端 versionId、RouteGuard/后端 403 双重强制、坐标一致性：PASS×6。
- 发布链：DRAFT 提交审核禁用且无 DVC 入口、DVC Modal 掩码 secretKey 不泄漏真值、DRAFT 下载 409 无票据、无 asset:read 直连 download/credentials 403：PASS×4。
- 发布审批：导航可见性随 scope 三态、审查页真实查询与诚实状态、文案随语言（含 404 页 D6 整改正向证据）、无 asset:manage 直连提交 403、DRAFT 提交后端拒绝且 UI 显错误非假成功：PASS×8。

### 2.9 协作与血缘（discussion.spec 4 + lineage.spec 4）

线程创建、发表评论回显+计数联动、锁定/解锁状态流转、无 asset:discuss 直连 403：PASS×4。
血缘上下游方向真实切换、双向闭环、父/子列跳转、无 asset:read 直连 403：PASS×4。

### 2.10 Agent 与 MCP（agent-management.spec 4 + mcp-integration.spec 3）

注册后列表 ACTIVE、Tool 白名单行内回显、禁用吊销凭据→启用恢复、必填缺失前端拦截：PASS×4。
Agent 接入页 MCP 端点/OpenAPI 文档为后端真值、Skill 模板来自 agent-bundle、无 authorization:read 导航隐藏+直连 403：PASS×3。

## 3. 配套证据（非点击层）

- 前端单测同链复核（066 EVD）：Vitest 49 files / 217 tests 全过；lint 0 error、typecheck/build PASS。
- 066 治理三连交接时点全绿；`-CheckCompletion` 设计内失败（AI 不得自批 + notProven 即 D-02 移交项）。
- Compose 行为验收 V01–V28 于 2026-09-23 全 PASS（implementation-status-2026-07-10 §11）。

## 4. 未覆盖项与边界（如实申报）

1. **D-02 登录名回显**：fixme 锁定，待 TASK-P0BR-067 获批实施后复跑解除（本报告 97/98 口径的由来）。
2. **P1–P5 认证业务旅程**（`verify-journey.sh`）与 transfer/subscription 全旅程：既有阻塞项，不在本轮 e2e 套件内，P1–P5 维持 IMPLEMENTED_UNVERIFIED，不得呈现 VERIFIED。
3. **发布审批「决策端」完整闭环**：受夹具断链限制，本轮证据为拒绝路径与诚实状态（review-workflow 8 项），正向审批闭环仍属 journey 阻塞项。
4. MCP 协议真实握手（JSON-RPC 层）由后端 IT/verify.sh 间接覆盖，浏览器点击层只验证页面真实数据渲染。
5. E5 演练与 `-CheckCompletion` 人工验收：仍为全局未完成门禁。

## 5. 结论

对目录内**每一个页面功能域**（认证、个人、权限、11 个管理页、目录/详情/版本/发布/审批、讨论、血缘、Agent、MCP、i18n、错误路径）均以浏览器真实点击取得 E4 结果：**97 通过、1 个受控 skip（D-02 已立卡移交）、0 失败**，与 PAGE 目录及 AC-P0B-UI-001..006 口径一致。066 整改的六项（D-01/05/07/08/09/10）在各自用例子项中复验通过。剩余设计-实现差距仅 D-02，唯一收口路径是 TASK-P0BR-067 获批实施。

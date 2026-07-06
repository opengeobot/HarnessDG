# 前端信息架构与全局交互

> 状态：`READY`
> 技术基线：React + TypeScript + Ant Design + TanStack Query。

## 1. 导航原则

1. 一级导航按用户任务组织，不按后端模块包名组织；
2. 没有读取权限的导航项不展示，直接访问路由仍由 Route Guard 和后端双重拒绝；
3. 组织/项目上下文在全局选择器中明确显示，切换后清理不适用缓存和草稿；
4. 资产详情是版本、文件、血缘、权限和治理信息的聚合入口；
5. 高风险操作不放在常用主按钮，必须明确资源、后果和确认；
6. 页面不得显示数据库 ID，业务 ID 可复制并有类型标签；
7. 所有文案经 i18nKey 渲染，不根据后端本地化 message 做分支。

## 2. 建议路由树

```text
/login
/
├─ /assets
│  ├─ /new
│  └─ /:assetId
│     ├─ overview
│     ├─ versions
│     │  └─ /:version
│     │     ├─ files
│     │     └─ preview
│     ├─ discussions
│     ├─ lineage
│     ├─ access
│     └─ settings
├─ /uploads
│  └─ /:sessionId
├─ /reviews
│  └─ /:requestId
├─ /integrations
│  ├─ /agents
│  └─ /clients
├─ /notifications
├─ /profile
└─ /admin
   ├─ /users
   ├─ /agents
   ├─ /organizations
   │  └─ /:organizationId
   │     ├─ members
   │     ├─ teams
   │     └─ projects
   ├─ /roles
   ├─ /permissions
   ├─ /dictionaries
   ├─ /tags
   ├─ /configurations
   ├─ /jobs
   ├─ /audit-logs
   ├─ /notifications
   └─ /dependencies
```

当前 `/version`、`/upload`、`/access` 是脱离具体资源的占位路由。正式实现前应按上方任务归属确认，
避免生成一个无法表达 asset/version/session 上下文的“大杂烩页面”。

Dataset 详情的 Overview/Versions/Files/Preview/Discussions 是同一资产外壳下的任务入口。Files 和
Preview 必须绑定 URL 中的精确 Version；Discussions 继承资产权限。阶段能力未实现时不展示可点击
Placeholder。

## 3. 全局应用外壳

### 3.1 Header

- 产品名称和环境标识（开发/测试/生产）；
- 当前 Organization/Project 选择器；
- 全局搜索入口；
- 任务/上传进行中指示；
- 未读通知；
- locale 切换；
- 用户菜单（Profile、改密、登出）。

环境标识不能泄露内部 Endpoint；生产环境必须有明显但不过度干扰的标识。

### 3.2 Side Navigation

- 资产：发现、上传、审批；
- Agent 接入；
- 管理中心（按实际 Permission 展开）；
- 不展示只有未来阶段占位的可点击入口；
- 菜单结构和 Route Guard 使用同一受控 Route Catalog，禁止维护两份权限映射。

### 3.3 Breadcrumb/返回

- 所有二级详情页显示稳定面包屑；
- 创建/编辑完成返回来源列表并保留筛选、排序、Cursor 位置；
- 404/防枚举页面不显示私有资源名称；
- 外部 Gitea 链接标注将离开平台，URL 由后端返回受控引用。

## 4. 全局数据与错误行为

| 情况 | UI 行为 |
| --- | --- |
| 初次加载 | 显示与目标布局匹配的 Skeleton，不用全屏空白 |
| 后台刷新 | 保留已有数据，显示轻量刷新状态 |
| 空结果 | 区分“系统无数据”和“筛选无结果”，提供合法下一步 |
| 401 | 单次 refresh 协调；失败清会话并回登录，保留安全 returnTo |
| 403 | 显示所需能力/申请入口，不泄露资源存在性 |
| 404 | 使用通用不存在语义，不回显未授权资源标识 |
| 409 | 展示冲突原因和重新读取操作；不静默覆盖 |
| 429 | 使用 Retry-After/退避提示，不自动风暴重试 |
| 5xx/依赖失败 | 展示 requestId、可重试性和安全错误说明 |
| 网络离线 | 保留未提交表单，允许恢复连接后重试 |

错误组件只依赖稳定 `code`、`i18nKey`、`retryable`、`requestId`，不解析 message 文本。

## 5. 表格、筛选与分页

- 高增长列表使用 Cursor UI，不伪装成可跳任意页的 total page；
- 小型后台列表可使用页码；
- 筛选条件同步到 URL，Secret/Token/预签名 URL 除外；
- 排序选项来自服务端/前端共同受控 Catalog；
- 批量操作只在后端提供相应批量契约时展示；
- 行操作按资源级权限决定，不仅看全局 Scope；
- 空值、停用治理项和未知历史值有明确安全回显；
- 列表请求取消旧查询，避免快速切换筛选时旧结果覆盖新结果。

## 6. 表单与写操作

- 字典、标签、Principal、Team、Scope、Permission、Tool 均用受控搜索选择，不使用 `mode="tags"` 创建任意值；
- 表单展示字段级规则、单位、限额和示例；
- 服务端校验错误映射到字段或表单级错误；
- 未提交变更离开页面时提示；
- 双击提交、刷新重放和网络重试使用稳定 Idempotency-Key；
- Idempotency-Key 对同一用户意图稳定，不在每次自动重试时生成新值；
- 乐观锁冲突要求重新加载并展示差异；
- 高风险确认框必须显示动作、目标、影响范围和不可逆性，不用只有“确定吗”。

## 7. Token 与敏感数据体验

- access Token 仅存在 AuthProvider 内存；
- refresh Token 对 JavaScript 不可见；
- Agent credential 只显示一次，关闭前要求用户确认已保存；
- 凭据组件不把值写入 URL、DOM 长期隐藏字段、剪贴板日志、埋点或错误边界；
- 预签名 URL 只在用户明确下载动作中使用，不显示完整查询串；
- copy 操作需要用户主动触发，并在有限时间后清除页面状态中的敏感值；
- 浏览器 E2E 必须检查 LocalStorage/SessionStorage/IndexedDB/console/network logging。

## 8. 国际化

- MVP locale：zh-CN、en-US；
- 路由不因 locale 改变；
- 日期/数字/文件大小按 locale 展示，传输仍使用 UTC/机器值；
- 字典/标签显示优先 i18n message，缺失时安全回退到稳定 code 并告警；
- API Error 使用 i18nKey；
- 表单验证、通知模板、空态、确认框和接入向导均纳入；
- 自动化测试至少对两个 locale 跑关键旅程，不只检查语言切换按钮。

## 9. 可访问性与响应式

- 所有输入有可关联 label 和错误说明；
- Modal/Drawer 打开后管理焦点，关闭回到触发元素；
- 表格/菜单/Tab 可键盘操作；
- 状态不只依赖颜色；
- 复制凭据、上传、审批等关键流程支持键盘；
- 最小桌面视口与是否支持移动端受 `Q-302`/`Q-305` 进一步确认；
- WCAG 目标等级待 `NFR-UI-005` 决策。

## 10. 前端状态边界

- TanStack Query 管理服务端状态；
- Auth/locale/当前作用域可使用受控 Context；
- 上传状态机可持久化非敏感会话 ID/Part 状态，但不持久化 Token/预签名 URL；
- 不把服务器列表复制到全局 Store；
- Query Key 必须包含 organization/project/resource/filter 作用域；
- 权限变化、登出、组织切换后清理相关 Query Cache；
- 错误边界上报前统一脱敏。


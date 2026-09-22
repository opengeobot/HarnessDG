# 全局前端编码规范

> 状态：`READY`
> 决策：`DEC-013`（Ant Design 基线、zh-CN/en-US、Playwright E2E）
> 适用：所有 P0-B 及后续阶段的前端页面和组件。

---

## 1. API Client 规范

### 1.1 统一入口

所有后端请求必须经 `frontend/src/shared/api/client.ts` 导出的 `apiClient` 发起：

```typescript
import { apiClient } from '@/shared/api';
// ✅ 正确
apiClient.get<UserView>('/system/users', { params });
// ❌ 禁止
fetch('/api/v1/system/users');
axios.get('/api/v1/...');
```

### 1.2 端点封装

每个业务域在 `frontend/src/features/<domain>/api.ts` 中封装端点函数：

- 函数命名对齐 OpenAPI `operationId`（如 `listUsers`、`createAgent`）；
- 写操作必须携带 `Idempotency-Key`（使用 `idempotent()` helper）；
- 返回类型使用 OpenAPI 生成的 View 类型，禁止页面内声明重复 DTO。

### 1.3 错误处理

- 响应拦截器统一解包 `ApiResponse<T>.data`，失败时抛出 `ApiError`；
- `ApiError` 包含 `code`、`i18nKey`、`retryable`、`requestId`；
- 页面错误组件只依赖稳定 `code` 和 `i18nKey`，不解析 `message` 文本；
- 401 自动刷新一次后重试；刷新失败清会话跳登录；
- 403 `PASSWORD_CHANGE_REQUIRED` 跳转改密页。

### 1.4 Token 安全

- `accessToken` 仅存于 `tokenHolder.ts` 内存变量；
- `refreshToken` 由 Secure/HttpOnly/SameSite Cookie 管理，JS 不可见；
- 禁止写入 LocalStorage、SessionStorage、URL、埋点或错误上报。

---

## 2. Permission Provider 规范

### 2.1 权限来源

登录后由 `GET /me`（`getCurrentPrincipal`）返回 `CurrentPrincipal.scopes: string[]`，
存入 AuthContext 内存。

### 2.2 权限判断 Hook

```typescript
// frontend/src/shared/hooks/usePermission.ts（待实现）
function usePermission(): {
  hasScope(scope: string): boolean;
  hasAnyScope(...scopes: string[]): boolean;
  scopes: string[];
}
```

### 2.3 使用规则

- 路由级：`RouteGuard` 组件根据路由 meta.requiredScope 判断跳转 403；
- 按钮级：`{hasScope('user:manage') && <Button>创建用户</Button>}`；
- UI 权限判断仅做体验提示，安全校验以后端为准；
- 权限变化（登出、角色变更）后清理 TanStack Query Cache。

---

## 3. i18n 规范

### 3.1 基础配置

- 库：`i18next` + `react-i18next`；
- 默认语言：`zh-CN`；回退语言：`zh`；
- 语言包：`frontend/src/shared/i18n/{zh,en}.json`。

### 3.2 Key 命名约定

```
<domain>.<page>.<element>
admin.users.title         → "用户管理"
admin.users.searchPlaceholder → "搜索用户名/显示名"
common.loading            → "加载中..."
error.auth.unauthenticated → "请先登录"
```

- 一级：业务域（`common`/`admin`/`assets`/`login`/`profile` 等）；
- 二级：页面或模块（`users`/`agents`/`roles` 等）；
- 三级：元素（`title`/`searchPlaceholder`/`confirmDelete` 等）；
- 错误码 i18nKey 对齐后端 `ApiError.i18nKey`（`error.<domain>.<code>`）。

### 3.3 使用规则

```typescript
const { t } = useTranslation();
<h1>{t('admin.users.title')}</h1>
```

- 禁止硬编码中文/英文文案；
- 字典/标签显示名优先 i18n message，缺失时回退到稳定 code 并 `console.warn`；
- 日期格式：`Intl.DateTimeFormat`（按 locale）；
- 数字/文件大小：`Intl.NumberFormat`（按 locale）；
- 传输值始终使用 UTC/机器值，仅展示层格式化。

---

## 4. 状态管理规范

### 4.1 服务端状态

- 统一使用 TanStack Query 管理；
- Query Key 必须包含 `organization/project/resource/filter` 作用域；
- 列表请求取消旧查询（`AbortController`）；
- 权限变化、登出、组织切换后 `queryClient.invalidateQueries()`。

### 4.2 客户端状态

- Auth/locale/当前组织项目使用受控 React Context；
- 上传状态机可持久化非敏感会话 ID，不持久化 Token/预签名 URL；
- 不把服务器列表复制到全局 Store。

### 4.3 表单状态

- 受控表单使用 `react-hook-form` 或 Ant Design `Form`；
- 未提交变更离开页面时 `beforeunload` 提示；
- 双击提交、刷新重放使用稳定 `Idempotency-Key`。

---

## 5. 组件规范

### 5.1 UI 基线

- Ant Design 5.x 企业后台风格；
- 高增长列表用 Cursor UI（"加载更多"或"下一页"按钮），不伪装页码分页；
- 小型后台列表可用页码分页；
- 状态不只依赖颜色（需 icon/文字辅助）。

### 5.2 共享组件

存放 `frontend/src/shared/components/`：

| 组件 | 用途 |
| --- | --- |
| `QueryBoundary` | 统一 Loading/Error/Retry 边界 |
| `ControlledSelect` | 受控搜索选择器 |
| `SafeMarkdown` | 不可信 Markdown 安全渲染 |
| `PlaceholderPage` | P0 阶段占位页面 |

### 5.3 禁止模式

- 不在页面组件中直接调用 `fetch` 或 `axios`；
- 不在 URL、DOM 长期隐藏字段、剪贴板日志、埋点中存储敏感值；
- 不使用 `dangerouslySetInnerHTML`（除 `SafeMarkdown`）；
- 不在 `useEffect` 中拼接 URL 做数据请求。

---

## 6. 路由与导航规范

### 6.1 路由守卫

- 未登录 → `/login`（保留 `returnTo`）；
- 无权 → 全局 403（展示所需 Permission，不泄露资源存在性）；
- 404 → 通用不存在页面（不回显未授权资源标识）。

### 6.2 菜单可见性

- 导航菜单按当前主体 Permission 动态显隐；
- 不展示只有未来阶段占位的可点击入口；
- 菜单结构和 Route Guard 使用同一受控 Route Catalog。

### 6.3 URL 状态

- 筛选/排序/分页状态同步到 URL query params；
- Secret/Token/预签名 URL 除外；
- 创建/编辑完成返回列表时保留筛选、排序、Cursor 位置。

---

## 7. 测试规范

### 7.1 单元测试

- 关键 Hook、工具函数、权限判断有 Vitest 测试；
- 测试文件与源文件同目录（`*.test.ts`/`*.test.tsx`）。

### 7.2 E2E 测试

- 框架：Playwright；
- 每个管理页面至少覆盖：权限允许加载成功 + 权限拒绝 403；
- 关键流程覆盖 Loading/Empty/Error/Retry/Success 五种状态截图；
- zh-CN 和 en-US 两种语言跑关键旅程。

### 7.3 安全检查

- E2E 必须检查 LocalStorage/SessionStorage/IndexedDB/console/network；
- 确认无 Token、预签名 URL 或凭据泄漏。

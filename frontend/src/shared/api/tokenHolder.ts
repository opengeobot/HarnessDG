/**
 * 功能: 模块级访问令牌持有器与会话回调注册中心。
 *       access token 仅保存在内存变量中，绝不写入 LocalStorage/sessionStorage/埋点。
 *       用于打破 apiClient 与 AuthProvider 之间的循环依赖：AuthProvider 更新令牌与回调，
 *       client 拦截器仅通过此处读取令牌、触发刷新与登出。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */

/** 内存态访问令牌，页面刷新后即失效，依赖静默 refresh 恢复会话 */
let accessToken: string | null = null;

/** 由 AuthProvider 注入的刷新回调，返回新的 access token 或 null（刷新失败） */
let refreshHandler: (() => Promise<string | null>) | null = null;

/** 由 AuthProvider 注入的登出回调，用于刷新失败时清理内存会话 */
let unauthorizedHandler: (() => void) | null = null;

/** 读取当前内存访问令牌 */
export function getAccessToken(): string | null {
  return accessToken;
}

/** 更新内存访问令牌（登录/刷新成功后调用；传 null 表示清空） */
export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/** 注册刷新回调 */
export function setRefreshHandler(handler: (() => Promise<string | null>) | null): void {
  refreshHandler = handler;
}

/** 注册未授权（刷新失败）回调 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** 触发一次令牌刷新，返回新令牌或 null */
export function invokeRefresh(): Promise<string | null> {
  if (!refreshHandler) {
    return Promise.resolve(null);
  }
  return refreshHandler();
}

/** 触发未授权处理（清理会话并跳转登录） */
export function invokeUnauthorized(): void {
  unauthorizedHandler?.();
}

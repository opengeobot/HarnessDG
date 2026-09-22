/**
 * 功能: 认证会话 Provider。内存持有 access token、当前主体与真实 Scope 集合；
 *       提供 login/logout/silent-refresh；向 client 注册刷新与登出回调以支持 401 自动续期。
 *       access token 仅存 React state 与模块级内存变量，绝不写入 LocalStorage/sessionStorage。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  getCurrentPrincipal,
  login as loginApi,
  logout as logoutApi,
  refreshToken as refreshApi,
  setAccessToken,
  setPasswordChangeRequiredHandler,
  setRefreshHandler,
  setUnauthorizedHandler,
} from '@/shared/api';
import type { CurrentPrincipal, LoginRequest, TokenPair } from '@/shared/types';
import { AuthContext, type AuthContextValue, type AuthStatus } from './context';

/**
 * 启动静默刷新的跨挂载去重（缺陷事实 7，TASK-P0BR-065）：StrictMode 下本组件的启动
 * effect 会挂载→cleanup→再挂载；`cancelled` 只丢弃结果、不阻止请求发出，两次并发
 * POST /auth/refresh 携带同一 jti 会被后端重放检测整族吊销，导致登录态间歇丢失。
 * 模块级 Promise 保证每次真实整页加载恰好发出一次刷新（模块随整页加载重新求值）。
 * 失败返回 null 而非 reject：缓存的 Promise 若 reject，第二个挂载会再次触发 clearSession；
 * 有意不缓存错误后清空重来——避免 StrictMode 第二次挂载重发 refresh 再次触发重放吊销。
 */
let bootstrapRefresh: Promise<TokenPair | null> | null = null;
function refreshOncePerPageLoad(): Promise<TokenPair | null> {
  if (!bootstrapRefresh) {
    bootstrapRefresh = refreshApi().catch(() => null);
  }
  return bootstrapRefresh;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('initializing');
  const [principal, setPrincipal] = useState<CurrentPrincipal | null>(null);
  // 使用 ref 保存进行中的刷新 Promise，避免并发 401 触发多次刷新
  const refreshingRef = useRef<Promise<string | null> | null>(null);

  const applySession = useCallback((accessToken: string, next: CurrentPrincipal) => {
    setAccessToken(accessToken);
    setPrincipal(next);
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setPrincipal(null);
    setStatus('anonymous');
  }, []);

  const login = useCallback<AuthContextValue['login']>(
    async (payload: LoginRequest) => {
      const tokenPair = await loginApi(payload);
      // 登录响应已携带 principal；仍以 /me 为准确保 scopes 与最新状态一致
      setAccessToken(tokenPair.accessToken);
      const current = tokenPair.principal ?? (await getCurrentPrincipal());
      applySession(tokenPair.accessToken, current);
      return current;
    },
    [applySession],
  );

  const logout = useCallback<AuthContextValue['logout']>(async () => {
    try {
      await logoutApi();
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const reloadPrincipal = useCallback(async () => {
    const current = await getCurrentPrincipal();
    setPrincipal(current);
  }, []);

  // 向 client 注册刷新回调：换取新 access token 并同步主体信息
  useEffect(() => {
    setRefreshHandler(async () => {
      if (refreshingRef.current) {
        return refreshingRef.current;
      }
      const task = (async () => {
        try {
          const tokenPair = await refreshApi();
          setAccessToken(tokenPair.accessToken);
          if (tokenPair.principal) {
            setPrincipal(tokenPair.principal);
            setStatus('authenticated');
          }
          return tokenPair.accessToken;
        } catch {
          return null;
        } finally {
          refreshingRef.current = null;
        }
      })();
      refreshingRef.current = task;
      return task;
    });
    setUnauthorizedHandler(() => {
      clearSession();
    });
    // 强制改密：任何 API 返回 PASSWORD_CHANGE_REQUIRED 时跳转 /profile。
    // AuthProvider 在 Router 外部，无法用 useNavigate；用 window.location 做硬跳转。
    setPasswordChangeRequiredHandler(() => {
      if (window.location.pathname !== '/profile') {
        window.location.href = '/profile';
      }
    });
    return () => {
      setRefreshHandler(null);
      setUnauthorizedHandler(null);
      setPasswordChangeRequiredHandler(null);
    };
  }, [clearSession]);

  // 应用启动尝试静默刷新恢复会话（经模块级去重，StrictMode 双挂载只发一次请求）
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokenPair = await refreshOncePerPageLoad();
        if (cancelled) {
          return;
        }
        if (!tokenPair) {
          clearSession();
          return;
        }
        setAccessToken(tokenPair.accessToken);
        const current = tokenPair.principal ?? (await getCurrentPrincipal());
        if (!cancelled) {
          applySession(tokenPair.accessToken, current);
        }
      } catch {
        if (!cancelled) {
          clearSession();
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applySession, clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      principal,
      scopes: new Set(principal?.scopes ?? []),
      login,
      logout,
      reloadPrincipal,
    }),
    [status, principal, login, logout, reloadPrincipal],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

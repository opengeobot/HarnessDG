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
  setRefreshHandler,
  setUnauthorizedHandler,
} from '@/shared/api';
import type { CurrentPrincipal, LoginRequest } from '@/shared/types';
import { AuthContext, type AuthContextValue, type AuthStatus } from './context';

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
    return () => {
      setRefreshHandler(null);
      setUnauthorizedHandler(null);
    };
  }, [clearSession]);

  // 应用启动尝试静默刷新恢复会话
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const tokenPair = await refreshApi();
        if (cancelled) {
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

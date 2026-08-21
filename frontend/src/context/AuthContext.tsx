// 认证上下文 — 登录/注册/登出状态管理 + 会话变化驱动重渲染
// 时间：2026-08-21  作者：AxeXie
import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { api, setSession, clearSession, setSessionChangeListener, currentUser, type User, type AuthData } from '../api/client';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  register: (username: string, password: string, nickname?: string) => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** 会话引导：页面加载后调用 /auth/me 恢复登录态（refresh cookie 有效则自动续期）。 */
async function bootstrap(): Promise<User | null> {
  try {
    return await api.me();
  } catch {
    clearSession();
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 会话令牌变化 → 同步到 React 状态
    setSessionChangeListener(() => setUser(currentUser()));
    bootstrap().then((u) => {
      setUser(u);
      setLoading(false);
    });
  }, []);

  const register = useCallback(async (username: string, password: string, nickname?: string) => {
    const data: AuthData = await api.register({ username, password, nickname });
    setSession(data.accessToken, data.csrfToken, data.user);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data: AuthData = await api.login({ username, password });
    setSession(data.accessToken, data.csrfToken, data.user);
  }, []);

  const logout = useCallback(async () => {
    await api.logout().catch(() => {});
    clearSession();
  }, []);

  const logoutAll = useCallback(async () => {
    await api.logoutAll().catch(() => {});
    clearSession();
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, register, login, logout, logoutAll }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}

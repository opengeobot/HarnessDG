// 认证上下文 — 登录/注册/登出状态管理 + 会话变化驱动重渲染 + 登录弹窗拦截
// 时间：2026-08-21  作者：AxeXie
import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { api, setSession, clearSession, setSessionChangeListener, currentUser, type User, type AuthData } from '../api/client';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  register: (username: string, password: string, nickname?: string) => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
  /** 登录弹窗开关（09 §2.3）。 */
  authModalOpen: boolean;
  /** 打开登录弹窗；next 为登录成功后的续行回调（requireLogin 拦截，09 §2.3）。 */
  openLogin: (next?: () => void) => void;
  closeLogin: () => void;
  /** 需要登录的操作统一入口：已登录直接执行，未登录弹窗并在成功后继续原操作。 */
  requireLogin: (action: () => void) => void;
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
  const [authModalOpen, setAuthModalOpen] = useState(false);
  // 登录成功后续行回调（用 ref 避免弹窗重渲染丢失）
  const pendingAction = useRef<(() => void) | null>(null);
  const userRef = useRef<User | null>(null);
  userRef.current = user;

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
    setAuthModalOpen(false);
    pendingAction.current?.();
    pendingAction.current = null;
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const data: AuthData = await api.login({ username, password });
    setSession(data.accessToken, data.csrfToken, data.user);
    setAuthModalOpen(false);
    pendingAction.current?.();
    pendingAction.current = null;
  }, []);

  const logout = useCallback(async () => {
    await api.logout().catch(() => {});
    clearSession();
  }, []);

  const logoutAll = useCallback(async () => {
    await api.logoutAll().catch(() => {});
    clearSession();
  }, []);

  const openLogin = useCallback((next?: () => void) => {
    pendingAction.current = next ?? null;
    setAuthModalOpen(true);
  }, []);

  const closeLogin = useCallback(() => {
    setAuthModalOpen(false);
    pendingAction.current = null;
  }, []);

  const requireLogin = useCallback((action: () => void) => {
    if (userRef.current) {
      action();
    } else {
      openLogin(action);
    }
  }, [openLogin]);

  return (
    <AuthContext.Provider value={{
      user, loading, register, login, logout, logoutAll,
      authModalOpen, openLogin, closeLogin, requireLogin,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

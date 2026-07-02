/**
 * 功能: 认证上下文定义 (Context 对象与类型)，与 Provider 组件分离以满足 Fast Refresh。
 *       仅在内存中持有会话，access token 绝不进入持久化存储，遵循 AGENTS 安全约束。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { createContext } from 'react';
import type { CurrentPrincipal, LoginRequest } from '@/shared/types';

/** 会话初始化状态：启动静默刷新期间为 initializing */
export type AuthStatus = 'initializing' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  /** 当前会话状态 */
  status: AuthStatus;
  /** 当前主体，未认证时为 null */
  principal: CurrentPrincipal | null;
  /** 当前主体的权限 Scope 集合（resource:action），未认证为空 */
  scopes: ReadonlySet<string>;
  /** 用户名密码登录 */
  login: (payload: LoginRequest) => Promise<CurrentPrincipal>;
  /** 登出并清理会话 */
  logout: () => Promise<void>;
  /** 重新拉取当前主体（如改密后刷新 forcePasswordChange） */
  reloadPrincipal: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

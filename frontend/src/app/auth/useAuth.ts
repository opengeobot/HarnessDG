/**
 * 功能: useAuth Hook。消费认证上下文，供页面与守卫读取会话与调用登录/登出。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from './context';

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}

/**
 * 功能: AuthProvider 组件测试——覆盖认证会话生命周期：
 *       静默刷新恢复、登录/登出、Token 仅存内存、刷新回调注册。
 *       对应 AC-P0B-UI-001（认证体验）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import type { CurrentPrincipal, TokenPair } from '@/shared/types';

// ── Mock @/shared/api ──────────────────────────────────────────────────
const mockLogin = vi.fn();
const mockLogout = vi.fn();
const mockRefreshToken = vi.fn();
const mockGetCurrentPrincipal = vi.fn();
const mockSetAccessToken = vi.fn();
const mockSetRefreshHandler = vi.fn();
const mockSetUnauthorizedHandler = vi.fn();
const mockSetPasswordChangeRequiredHandler = vi.fn();

vi.mock('@/shared/api', () => ({
  login: (...args: unknown[]) => mockLogin(...args),
  logout: (...args: unknown[]) => mockLogout(...args),
  refreshToken: (...args: unknown[]) => mockRefreshToken(...args),
  getCurrentPrincipal: (...args: unknown[]) => mockGetCurrentPrincipal(...args),
  setAccessToken: (...args: unknown[]) => mockSetAccessToken(...args),
  setRefreshHandler: (...args: unknown[]) => mockSetRefreshHandler(...args),
  setUnauthorizedHandler: (...args: unknown[]) => mockSetUnauthorizedHandler(...args),
  setPasswordChangeRequiredHandler: (...args: unknown[]) => mockSetPasswordChangeRequiredHandler(...args),
}));

// 延迟导入（mock 注册后）
const { AuthProvider } = await import('./AuthProvider');
const { useAuth } = await import('./useAuth');

// ── Fixtures ───────────────────────────────────────────────────────────
const TEST_PRINCIPAL: CurrentPrincipal = {
  principalId: 'prn_test',
  userId: 'usr_test',
  principalType: 'USER',
  subject: 'prn_test',
  displayName: 'Test User',
  organizationId: null,
  roles: [],
  scopes: ['asset:read', 'asset:write'],
  locale: 'zh-CN',
  forcePasswordChange: false,
};

const TEST_TOKEN_PAIR: TokenPair = {
  tokenType: 'Bearer',
  accessToken: 'access-token-abc',
  expiresIn: 900,
  refreshExpiresIn: 86400,
  principal: TEST_PRINCIPAL,
};

function createWrapper() {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <AuthProvider>{children}</AuthProvider>;
  };
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 默认：静默刷新失败（未登录）
    mockRefreshToken.mockRejectedValue(new Error('no refresh cookie'));
    mockSetAccessToken.mockImplementation(() => {});
    mockSetRefreshHandler.mockImplementation(() => {});
    mockSetUnauthorizedHandler.mockImplementation(() => {});
    mockSetPasswordChangeRequiredHandler.mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('静默刷新失败时状态为 anonymous', async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    // 初始状态为 initializing
    expect(result.current.status).toBe('initializing');

    await waitFor(() => {
      expect(result.current.status).toBe('anonymous');
    });
    expect(result.current.principal).toBeNull();
    expect(result.current.scopes.size).toBe(0);
  });

  it('静默刷新成功时恢复为 authenticated', async () => {
    mockRefreshToken.mockResolvedValue(TEST_TOKEN_PAIR);

    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(result.current.status).toBe('authenticated');
    });
    expect(result.current.principal?.principalId).toBe('prn_test');
    expect(result.current.scopes.has('asset:read')).toBe(true);
    expect(result.current.scopes.has('asset:write')).toBe(true);
    // setAccessToken 应被调用
    expect(mockSetAccessToken).toHaveBeenCalledWith('access-token-abc');
  });

  it('login 成功后状态变为 authenticated 并设置 Token', async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    // 等待初始化完成
    await waitFor(() => {
      expect(result.current.status).toBe('anonymous');
    });

    mockLogin.mockResolvedValue(TEST_TOKEN_PAIR);

    await act(async () => {
      await result.current.login({ username: 'admin', password: 'secret' });
    });

    expect(result.current.status).toBe('authenticated');
    expect(result.current.principal?.displayName).toBe('Test User');
    expect(mockSetAccessToken).toHaveBeenCalledWith('access-token-abc');
  });

  it('logout 后状态变为 anonymous 并清除 Token', async () => {
    mockRefreshToken.mockResolvedValue(TEST_TOKEN_PAIR);
    mockLogout.mockResolvedValue(undefined);

    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(result.current.status).toBe('authenticated');
    });

    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.status).toBe('anonymous');
    expect(result.current.principal).toBeNull();
    expect(mockSetAccessToken).toHaveBeenCalledWith(null);
  });

  it('logout 即使 API 失败也清除本地会话', async () => {
    mockRefreshToken.mockResolvedValue(TEST_TOKEN_PAIR);
    mockLogout.mockRejectedValue(new Error('network error'));

    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(result.current.status).toBe('authenticated');
    });

    // logout API 失败会抛出错误，但 finally 块仍会清理会话
    await act(async () => {
      try {
        await result.current.logout();
      } catch {
        // 预期抛出 network error
      }
    });

    // 即使 API 失败，本地会话也应清除（try/finally）
    expect(result.current.status).toBe('anonymous');
    expect(result.current.principal).toBeNull();
  });

  it('Token 仅存内存——不写入 localStorage', async () => {
    mockRefreshToken.mockResolvedValue(TEST_TOKEN_PAIR);
    const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem');

    renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(mockSetAccessToken).toHaveBeenCalledWith('access-token-abc');
    });

    // 验证 localStorage 没有被写入 access token
    const tokenCalls = localStorageSpy.mock.calls.filter(
      ([key]) => typeof key === 'string' && key.toLowerCase().includes('token'),
    );
    expect(tokenCalls).toHaveLength(0);
    localStorageSpy.mockRestore();
  });

  it('注册 refresh 和 unauthorized 回调', async () => {
    renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(mockSetRefreshHandler).toHaveBeenCalledTimes(1);
      expect(mockSetUnauthorizedHandler).toHaveBeenCalledTimes(1);
      expect(mockSetPasswordChangeRequiredHandler).toHaveBeenCalledTimes(1);
    });

    // 回调应为函数
    expect(typeof mockSetRefreshHandler.mock.calls[0][0]).toBe('function');
    expect(typeof mockSetUnauthorizedHandler.mock.calls[0][0]).toBe('function');
  });

  it('卸载时清除回调注册', async () => {
    const { unmount } = renderHook(() => useAuth(), { wrapper: createWrapper() });

    await waitFor(() => {
      expect(mockSetRefreshHandler).toHaveBeenCalledTimes(1);
    });

    unmount();

    // 卸载时应传入 null 清除回调
    expect(mockSetRefreshHandler).toHaveBeenCalledWith(null);
    expect(mockSetUnauthorizedHandler).toHaveBeenCalledWith(null);
    expect(mockSetPasswordChangeRequiredHandler).toHaveBeenCalledWith(null);
  });
});

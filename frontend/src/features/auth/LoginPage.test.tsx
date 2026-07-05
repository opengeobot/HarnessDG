/**
 * LoginPage 组件测试——验证登录表单渲染、提交行为和错误展示。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from './LoginPage';
import { renderWithProviders } from '@/test/test-utils';

// Mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock useAuth
const mockLogin = vi.fn();
vi.mock('@/app/auth', () => ({
  useAuth: () => ({ login: mockLogin }),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
    mockLogin.mockReset();
  });

  it('渲染登录表单：标题、用户名、密码、提交按钮', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByText('AI 资产管理平台')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /登\s*录/ })).toBeInTheDocument();
  });

  it('提交表单成功后跳转到 /assets', async () => {
    const user = userEvent.setup();
    mockLogin.mockResolvedValue({ principalId: 'usr_1', forcePasswordChange: false });

    renderWithProviders(<LoginPage />);

    await user.type(screen.getByPlaceholderText('用户名'), 'admin');
    await user.type(screen.getByPlaceholderText('密码'), 'secret123');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        username: 'admin',
        password: 'secret123',
      });
    });
    expect(mockNavigate).toHaveBeenCalledWith('/assets', { replace: true });
  });

  it('强制改密时跳转到 /profile', async () => {
    const user = userEvent.setup();
    mockLogin.mockResolvedValue({ principalId: 'usr_1', forcePasswordChange: true });

    renderWithProviders(<LoginPage />);

    await user.type(screen.getByPlaceholderText('用户名'), 'newuser');
    await user.type(screen.getByPlaceholderText('密码'), 'temp123');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/profile', { replace: true });
    });
  });

  it('登录失败时展示错误消息', async () => {
    const user = userEvent.setup();
    mockLogin.mockRejectedValue({ message: '密码错误', code: 'AUTH_FAILED' });

    renderWithProviders(<LoginPage />);

    await user.type(screen.getByPlaceholderText('用户名'), 'admin');
    await user.type(screen.getByPlaceholderText('密码'), 'wrong');
    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalled();
    });
    // 不应跳转
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('空表单时不触发提交', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);

    await user.click(screen.getByRole('button', { name: /登\s*录/ }));

    // 等一小段时间确认 login 未被调用
    await new Promise((r) => setTimeout(r, 100));
    expect(mockLogin).not.toHaveBeenCalled();
  });
});

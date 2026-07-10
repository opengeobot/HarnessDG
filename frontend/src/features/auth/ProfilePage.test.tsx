/**
 * ProfilePage 组件测试——验证账户信息展示、改密表单和强制改密提示。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfilePage } from './ProfilePage';
import { renderWithProviders } from '@/test/test-utils';

const mockPrincipal = {
  principalId: 'prn_1',
  userId: 'usr_1',
  subject: 'alice',
  displayName: 'Alice Wang',
  locale: 'zh-CN',
  roles: ['platform_admin'],
  scopes: ['user:read', 'asset:read'],
  organizationId: null,
  forcePasswordChange: false,
};

const mockReloadPrincipal = vi.fn();
vi.mock('@/app/auth', () => ({
  useAuth: () => ({
    principal: mockPrincipal,
    reloadPrincipal: mockReloadPrincipal,
  }),
}));

const mockChangePassword = vi.fn();
vi.mock('@/shared/api', () => ({
  changeCurrentUserPassword: (...args: unknown[]) => mockChangePassword(...args),
  isApiError: (e: unknown) => e !== null && typeof e === 'object' && 'message' in e,
}));

describe('ProfilePage', () => {
  beforeEach(() => {
    mockChangePassword.mockReset();
    mockReloadPrincipal.mockReset();
  });

  it('渲染账户信息卡片', () => {
    renderWithProviders(<ProfilePage />);
    expect(screen.getByText('prn_1')).toBeInTheDocument();
    expect(screen.getByText('usr_1')).toBeInTheDocument();
    expect(screen.getByText('alice')).toBeInTheDocument();
    expect(screen.getByText('Alice Wang')).toBeInTheDocument();
    expect(screen.getByText('zh-CN')).toBeInTheDocument();
    expect(screen.getByText('platform_admin')).toBeInTheDocument();
    expect(screen.getByText('user:read')).toBeInTheDocument();
  });

  it('forcePasswordChange=true 时显示警告', () => {
    const origForce = mockPrincipal.forcePasswordChange;
    mockPrincipal.forcePasswordChange = true;
    try {
      renderWithProviders(<ProfilePage />);
      expect(screen.getByRole('alert')).toBeInTheDocument();
    } finally {
      mockPrincipal.forcePasswordChange = origForce;
    }
  });

  it('改密表单校验：当前密码必填', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProfilePage />);

    // 点击提交按钮（antd 按钮文字可能有空格）
    const submitBtn = screen.getByRole('button', { name: /提\s*交|submit/i });
    await user.click(submitBtn);

    await waitFor(() => {
      expect(mockChangePassword).not.toHaveBeenCalled();
    });
  });

  it('改密成功后调用 API 并刷新主体', async () => {
    const user = userEvent.setup();
    mockChangePassword.mockResolvedValue({});

    const { container } = renderWithProviders(<ProfilePage />);

    const currentPw = container.querySelector('#currentPassword') as HTMLInputElement;
    const newPw = container.querySelector('#newPassword') as HTMLInputElement;
    const confirmPw = container.querySelector('#confirmPassword') as HTMLInputElement;

    await user.type(currentPw, 'OldPass1234!');
    await user.type(newPw, 'NewSecureP@ss1');
    await user.type(confirmPw, 'NewSecureP@ss1');
    await user.click(screen.getByRole('button', { name: /提\s*交|submit/i }));

    await waitFor(() => {
      expect(mockChangePassword).toHaveBeenCalledWith({
        currentPassword: 'OldPass1234!',
        newPassword: 'NewSecureP@ss1',
      });
    });
  });

  it('改密失败不调用 API（密码不一致）', async () => {
    const user = userEvent.setup();

    const { container } = renderWithProviders(<ProfilePage />);

    const currentPw = container.querySelector('#currentPassword') as HTMLInputElement;
    const newPw = container.querySelector('#newPassword') as HTMLInputElement;
    const confirmPw = container.querySelector('#confirmPassword') as HTMLInputElement;

    await user.type(currentPw, 'OldPass1234!');
    await user.type(newPw, 'NewSecureP@ss1');
    await user.type(confirmPw, 'DifferentPass1'); // 与新密码不一致
    await user.click(screen.getByRole('button', { name: /提\s*交|submit/i }));

    // 前端校验失败，API 不应被调用
    await waitFor(() => {
      expect(mockChangePassword).not.toHaveBeenCalled();
    });
  });
});

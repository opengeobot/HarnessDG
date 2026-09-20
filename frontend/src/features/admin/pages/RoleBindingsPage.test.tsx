/**
 * RoleBindingsPage 组件测试——验证列表渲染，以及 Loading/Empty/Error/Retry/Success 四种页面状态，
 * 并验证角色字段使用受控选择器（不接受自由文本角色 ID）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RoleBindingsPage } from './RoleBindingsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListRoleBindings = vi.fn();
const mockCreateRoleBinding = vi.fn().mockResolvedValue({});
const mockDeleteRoleBinding = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listRoleBindings: (...args: unknown[]) => mockListRoleBindings(...args),
  createRoleBinding: (...args: unknown[]) => mockCreateRoleBinding(...args),
  deleteRoleBinding: (...args: unknown[]) => mockDeleteRoleBinding(...args),
}));

// ControlledSelect 内部用 apiClient 拉角色清单；返回固定角色以证明选项来自受控来源。
vi.mock('@/shared/api', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api')>('@/shared/api');
  return {
    ...actual,
    apiClient: {
      get: vi.fn().mockResolvedValue([
        { roleId: 'rol_admin', roleCode: 'ADMIN', roleName: 'Admin', permissionCodes: [] },
      ]),
    },
  };
});

const BINDINGS = [
  {
    bindingId: 'rbd_01',
    principalId: 'usr_01',
    roleId: 'rol_admin',
    scopeType: 'PLATFORM',
    scopeId: null,
    createdAt: '2026-09-18T00:00:00Z',
  },
];

/** antd 会在仅两个中文字符的按钮文案间插入空格，故用宽松正则匹配 */
const RETRY = /重\s*试/;

describe('RoleBindingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('Successful：加载完成后渲染绑定列表', async () => {
    mockListRoleBindings.mockResolvedValue(BINDINGS);
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText('usr_01')).toBeInTheDocument();
      expect(screen.getByText('rol_admin')).toBeInTheDocument();
      expect(screen.getByText('PLATFORM')).toBeInTheDocument();
    });
  });

  it('Empty：无绑定数据时展示空态而非错误', async () => {
    mockListRoleBindings.mockResolvedValue([]);
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无角色绑定')).toBeInTheDocument();
    });
  });

  it('Error + Retry：查询失败展示错误与重试按钮，点击后重新请求', async () => {
    mockListRoleBindings.mockRejectedValueOnce(new Error('boom'));
    mockListRoleBindings.mockResolvedValueOnce(BINDINGS);
    renderWithProviders(<RoleBindingsPage />);

    await waitFor(() => {
      expect(screen.getByText('加载失败')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: RETRY }));
    await waitFor(() => {
      expect(screen.getByText('usr_01')).toBeInTheDocument();
    });
  });

  it('Loading：请求未完成时不渲染列表内容', () => {
    // 永不 resolve，使页面停留在 loading 态。
    mockListRoleBindings.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<RoleBindingsPage />);
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('usr_01')).not.toBeInTheDocument();
  });

  it('创建表单的角色字段为受控选择器（combobox）而非自由文本输入', async () => {
    mockListRoleBindings.mockResolvedValue([]);
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无角色绑定')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /创建绑定/ }));
    const dialog = await screen.findByRole('dialog');

    // 角色与作用域类型两个受控字段都必须是下拉（combobox）。
    expect(within(dialog).getAllByRole('combobox').length).toBeGreaterThanOrEqual(2);
    expect(dialog.querySelector('#roleId')).toHaveAttribute('role', 'combobox');
    expect(dialog.querySelector('#scopeType')).toHaveAttribute('role', 'combobox');

    // 两个受控字段不得退化为自由文本框：文本框只允许出现在主体 ID 与作用域 ID。
    const textboxIds = within(dialog)
      .getAllByRole('textbox')
      .map((element) => element.id);
    expect(textboxIds).not.toContain('roleId');
    expect(textboxIds).not.toContain('scopeType');
  });

  it('创建绑定时 PLATFORM 作用域提交 null 而非空串', async () => {
    mockListRoleBindings.mockResolvedValue([]);
    renderWithProviders(<RoleBindingsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无角色绑定')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: /创建绑定/ }));
    const dialog = await screen.findByRole('dialog');

    // 选择受控角色选项。
    await userEvent.click(within(dialog).getAllByRole('combobox')[0]);
    await userEvent.click(await screen.findByText(/Admin \(ADMIN\)/));

    await userEvent.type(within(dialog).getAllByRole('textbox')[0], 'usr_02');
    // 提交（antd Modal 默认确认按钮为 OK）。
    await userEvent.click(within(dialog).getByRole('button', { name: /OK/ }));

    await waitFor(() => {
      expect(mockCreateRoleBinding).toHaveBeenCalled();
    });
    const payload = mockCreateRoleBinding.mock.calls[0][0] as {
      scopeType: string;
      scopeId: unknown;
      roleId: string;
    };
    // 默认作用域为 PLATFORM：必须提交 null，不得提交空串（后端契约 scopeId 为 nullable）。
    expect(payload.scopeType).toBe('PLATFORM');
    expect(payload.scopeId).toBeNull();
    expect(payload.roleId).toBe('rol_admin');
  });
});

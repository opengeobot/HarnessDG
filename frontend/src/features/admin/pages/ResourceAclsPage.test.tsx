/**
 * ResourceAclsPage 组件测试——验证列表渲染，以及 Loading/Empty/Error/Retry/Success 四种页面状态。
 * 同时验证权限必须来自受控选择器、资源类型为受控取值。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResourceAclsPage } from './ResourceAclsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListResourceAcls = vi.fn();
const mockCreateResourceAcl = vi.fn().mockResolvedValue({});
const mockDeleteResourceAcl = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listResourceAcls: (...args: unknown[]) => mockListResourceAcls(...args),
  createResourceAcl: (...args: unknown[]) => mockCreateResourceAcl(...args),
  deleteResourceAcl: (...args: unknown[]) => mockDeleteResourceAcl(...args),
}));

// ControlledSelect 内部用 apiClient 拉权限清单；此处返回固定权限以断言受控来源。
vi.mock('@/shared/api', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api')>('@/shared/api');
  return {
    ...actual,
    apiClient: {
      get: vi.fn().mockResolvedValue([
        { permissionCode: 'asset:read', resource: 'asset', action: 'read', i18nKey: 'perm.asset.read' },
      ]),
    },
  };
});

const ACLS = [
  {
    aclId: 'acl_01',
    principalId: 'usr_01',
    resourceType: 'ASSET',
    resourceId: 'ast_001',
    permissionCodes: ['asset:read'],
    createdAt: '2026-09-18T00:00:00Z',
  },
];

/** antd 会在仅两个中文字符的按钮文案间插入空格，故用宽松正则匹配 */
const RETRY = /重\s*试/;

describe('ResourceAclsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('Successful：加载完成后渲染 ACL 列表', async () => {
    mockListResourceAcls.mockResolvedValue(ACLS);
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText('ast_001')).toBeInTheDocument();
      expect(screen.getByText('usr_01')).toBeInTheDocument();
      expect(screen.getByText('ASSET')).toBeInTheDocument();
    });
  });

  it('Empty：无 ACL 数据时展示空态而非错误', async () => {
    mockListResourceAcls.mockResolvedValue([]);
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无资源 ACL')).toBeInTheDocument();
    });
  });

  it('Error + Retry：查询失败展示错误与重试按钮，点击后重新请求', async () => {
    mockListResourceAcls.mockRejectedValueOnce(new Error('boom'));
    mockListResourceAcls.mockResolvedValueOnce(ACLS);
    renderWithProviders(<ResourceAclsPage />);

    await waitFor(() => {
      expect(screen.getByText('加载失败')).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole('button', { name: RETRY }));
    await waitFor(() => {
      expect(screen.getByText('ast_001')).toBeInTheDocument();
    });
  });

  it('Loading：请求未完成时不渲染列表内容', () => {
    mockListResourceAcls.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<ResourceAclsPage />);
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('ast_001')).not.toBeInTheDocument();
  });

  it('创建表单的权限与资源类型均为受控选择，无自由文本权限输入', async () => {
    mockListResourceAcls.mockResolvedValue([]);
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无资源 ACL')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: '创建 ACL' }));
    const dialog = await screen.findByRole('dialog');

    // 资源类型与权限两个字段都是受控下拉，不存在 tags 模式的自由输入。
    expect(dialog.querySelector('#resourceType')).toHaveAttribute('role', 'combobox');
    expect(dialog.querySelector('#permissionCodes')).toHaveAttribute('role', 'combobox');
    // 自由文本输入只允许出现在主体 ID 与资源 ID。
    const textboxIds = within(dialog)
      .getAllByRole('textbox')
      .map((element) => element.id);
    expect(textboxIds).not.toContain('resourceType');
    expect(textboxIds).not.toContain('permissionCodes');
  });

  it('创建 ACL 时只提交受控取值（资源类型 ASSET 与所选权限）', async () => {
    mockListResourceAcls.mockResolvedValue([]);
    renderWithProviders(<ResourceAclsPage />);
    await waitFor(() => {
      expect(screen.getByText('暂无资源 ACL')).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: '创建 ACL' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(
      within(dialog).getByPlaceholderText(/usr_/),
      'usr_02',
    );
    await userEvent.type(
      within(dialog).getByPlaceholderText('目标资源业务 ID'),
      'ast_002',
    );

    // 权限只能从受控清单中点选（下拉面板挂载在 body，故单独取出面板再定位选项）。
    await userEvent.click(within(dialog).getAllByRole('combobox')[1]);
    const dropdown = await waitFor(() => {
      const list = document.querySelector('.ant-select-dropdown');
      expect(list).not.toBeNull();
      return list as HTMLElement;
    });
    const option = Array.from(
      dropdown.querySelectorAll('.ant-select-item-option'),
    ).find((element) => element.textContent === 'asset:read');
    expect(option).toBeTruthy();
    await userEvent.click(option as HTMLElement);

    await userEvent.click(within(dialog).getByRole('button', { name: /OK/ }));

    await waitFor(() => {
      expect(mockCreateResourceAcl).toHaveBeenCalled();
    });
    const payload = mockCreateResourceAcl.mock.calls[0][0] as {
      resourceType: string;
      permissionCodes: string[];
    };
    expect(payload.resourceType).toBe('ASSET');
    expect(payload.permissionCodes).toEqual(['asset:read']);
  });
});

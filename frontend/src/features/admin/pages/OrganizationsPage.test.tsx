/**
 * OrganizationsPage 组件测试——组织列表、创建、成员管理。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrganizationsPage } from './OrganizationsPage';
import { renderWithProviders } from '@/test/test-utils';
import type { OrganizationView } from '../types';

const mockOrgs: OrganizationView[] = [
  { organizationId: 'org_1', code: 'acme', name: 'ACME Corp', giteaOrganization: 'acme', status: 'ACTIVE', createdAt: '2026-07-01T00:00:00Z' },
];

const mockMembers = [
  { organizationId: 'org_1', principalId: 'prn_1', joinedAt: '2026-07-01T00:00:00Z' },
];

const mockListOrganizations = vi.fn().mockResolvedValue(mockOrgs);
const mockCreateOrganization = vi.fn().mockResolvedValue(mockOrgs[0]);
const mockListMembers = vi.fn().mockResolvedValue(mockMembers);
const mockAddMember = vi.fn().mockResolvedValue({});
const mockRemoveMember = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listOrganizations: (...args: unknown[]) => mockListOrganizations(...args),
  createOrganization: (...args: unknown[]) => mockCreateOrganization(...args),
  listOrganizationMembers: (...args: unknown[]) => mockListMembers(...args),
  addOrganizationMember: (...args: unknown[]) => mockAddMember(...args),
  removeOrganizationMember: (...args: unknown[]) => mockRemoveMember(...args),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

describe('OrganizationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染组织列表表格', async () => {
    renderWithProviders(<OrganizationsPage />);
    expect(screen.getByText('组织管理')).toBeInTheDocument();
    expect(await screen.findByText('ACME Corp')).toBeInTheDocument();
    expect(screen.getAllByText('acme').length).toBeGreaterThanOrEqual(1);
  });

  it('"创建组织"按钮存在', () => {
    renderWithProviders(<OrganizationsPage />);
    expect(screen.getByRole('button', { name: /创建组织|createOrg/i })).toBeInTheDocument();
  });

  it('点击"创建组织"打开弹窗', async () => {
    const user = userEvent.setup();
    renderWithProviders(<OrganizationsPage />);
    await user.click(screen.getByRole('button', { name: /创建组织|createOrg/i }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('"成员管理"按钮存在', async () => {
    renderWithProviders(<OrganizationsPage />);
    expect(await screen.findByText(/成员管理|memberManagement/i)).toBeInTheDocument();
  });

  it('点击"成员管理"打开抽屉并显示成员列表', async () => {
    const user = userEvent.setup();
    renderWithProviders(<OrganizationsPage />);
    const memberBtn = await screen.findByText(/成员管理|memberManagement/i);
    await user.click(memberBtn);
    // 抽屉打开后显示成员列表
    expect(await screen.findByText('prn_1')).toBeInTheDocument();
  });

  it('刷新按钮存在', () => {
    renderWithProviders(<OrganizationsPage />);
    expect(screen.getByRole('button', { name: /刷\s*新/ })).toBeInTheDocument();
  });
});

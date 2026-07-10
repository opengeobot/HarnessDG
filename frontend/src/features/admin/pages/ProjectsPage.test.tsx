/**
 * ProjectsPage 组件测试——组织选择、项目列表、创建弹窗。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProjectsPage } from './ProjectsPage';
import { renderWithProviders } from '@/test/test-utils';
import type { OrganizationView, ProjectView } from '../types';

const mockOrgs: OrganizationView[] = [
  { organizationId: 'org_1', code: 'acme', name: 'ACME Corp', giteaOrganization: 'acme', status: 'ACTIVE', createdAt: '2026-07-01T00:00:00Z' },
  { organizationId: 'org_2', code: 'beta', name: 'Beta Inc', status: 'ACTIVE', createdAt: '2026-07-02T00:00:00Z' },
];

const mockProjects: ProjectView[] = [
  { projectId: 'prj_1', organizationId: 'org_1', code: 'proj-alpha', name: 'Alpha Project', status: 'ACTIVE', createdAt: '2026-07-01T00:00:00Z' },
];

const mockListOrganizations = vi.fn().mockResolvedValue(mockOrgs);
const mockListProjects = vi.fn().mockResolvedValue(mockProjects);
const mockCreateProject = vi.fn().mockResolvedValue(mockProjects[0]);

vi.mock('../api', () => ({
  listOrganizations: (...args: unknown[]) => mockListOrganizations(...args),
  listOrganizationProjects: (...args: unknown[]) => mockListProjects(...args),
  createProject: (...args: unknown[]) => mockCreateProject(...args),
}));

describe('ProjectsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('初始提示"请先选择组织"', async () => {
    renderWithProviders(<ProjectsPage />);
    expect(screen.getByText(/项目管理|projects/i)).toBeInTheDocument();
    // 未选组织时显示提示
    expect(await screen.findByText(/请先选择组织|selectOrgHint/i)).toBeInTheDocument();
  });

  it('"创建项目"按钮未选组织时禁用', async () => {
    renderWithProviders(<ProjectsPage />);
    const btn = screen.getByRole('button', { name: /创建项目|createProject/i });
    expect(btn).toBeDisabled();
  });

  it('选择组织后加载项目列表', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProjectsPage />);

    // 打开组织下拉
    const selectInput = screen.getByRole('combobox');
    await user.click(selectInput);

    // 选择 ACME Corp
    const option = await screen.findByText(/ACME Corp/);
    await user.click(option);

    // 应加载项目列表
    expect(await screen.findByText('Alpha Project')).toBeInTheDocument();
    expect(screen.getByText('proj-alpha')).toBeInTheDocument();
  });

  it('选择组织后"创建项目"按钮可用并打开弹窗', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProjectsPage />);

    // 选择组织
    const selectInput = screen.getByRole('combobox');
    await user.click(selectInput);
    await user.click(await screen.findByText(/ACME Corp/));

    // 创建按钮应可用
    const createBtn = screen.getByRole('button', { name: /创建项目|createProject/i });
    await waitFor(() => expect(createBtn).not.toBeDisabled());

    // 点击打开弹窗
    await user.click(createBtn);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('创建弹窗包含 code 和 name 字段', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProjectsPage />);

    // 选择组织
    const selectInput = screen.getByRole('combobox');
    await user.click(selectInput);
    await user.click(await screen.findByText(/ACME Corp/));

    const createBtn = screen.getByRole('button', { name: /创建项目|createProject/i });
    await waitFor(() => expect(createBtn).not.toBeDisabled());
    await user.click(createBtn);

    // 弹窗中应有 code 和 name 输入框
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    const inputs = screen.getAllByRole('textbox');
    expect(inputs.length).toBeGreaterThanOrEqual(2);
  });
});

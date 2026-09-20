/**
 * TeamsPage 组件测试——验证 Team 管理页面的列表渲染、创建表单与成员管理，
 * 并覆盖「从侧边导航直接进入（无路由组织参数）」时的组织选择器路径。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TeamsPage } from './TeamsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListTeams = vi.fn().mockResolvedValue([
  { teamId: 'team_001', organizationId: 'org_001', name: 'NLP Team', description: 'NLP research', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
  { teamId: 'team_002', organizationId: 'org_001', name: 'Vision Team', description: 'Computer vision', status: 'ACTIVE', createdAt: '2026-01-02T00:00:00Z' },
]);
const mockListTeamMembers = vi.fn().mockResolvedValue([
  { teamId: 'team_001', principalId: 'prn_user1', role: 'LEAD', joinedAt: '2026-01-01T00:00:00Z' },
  { teamId: 'team_001', principalId: 'prn_user2', role: 'MEMBER', joinedAt: '2026-01-02T00:00:00Z' },
]);
const mockCreateTeam = vi.fn().mockResolvedValue({ teamId: 'team_003' });
const mockUpdateTeam = vi.fn().mockResolvedValue({});
const mockAddTeamMember = vi.fn().mockResolvedValue({});
const mockRemoveTeamMember = vi.fn().mockResolvedValue({});
const mockListOrganizations = vi.fn().mockResolvedValue([
  { organizationId: 'org_001', code: 'alpha', name: 'Alpha Org', status: 'ACTIVE' },
]);

vi.mock('../api', () => ({
  listTeams: (...args: unknown[]) => mockListTeams(...args),
  listTeamMembers: (...args: unknown[]) => mockListTeamMembers(...args),
  listOrganizations: (...args: unknown[]) => mockListOrganizations(...args),
  createTeam: (...args: unknown[]) => mockCreateTeam(...args),
  updateTeam: (...args: unknown[]) => mockUpdateTeam(...args),
  addTeamMember: (...args: unknown[]) => mockAddTeamMember(...args),
  removeTeamMember: (...args: unknown[]) => mockRemoveTeamMember(...args),
}));

// Mock react-router-dom useParams：默认模拟「从组织页进入」的路由参数场景。
const mockUseParams = vi.fn<() => { orgId?: string }>(() => ({ orgId: 'org_001' }));
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useParams: () => mockUseParams(),
    useNavigate: () => vi.fn(),
  };
});

describe('TeamsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseParams.mockReturnValue({ orgId: 'org_001' });
  });

  it('renders page title', async () => {
    renderWithProviders(<TeamsPage />);
    await waitFor(() => {
      expect(screen.getByText(/Team 管理|admin\.teams\.title/i)).toBeInTheDocument();
    });
  });

  it('renders team list after loading', async () => {
    renderWithProviders(<TeamsPage />);
    await waitFor(() => {
      expect(screen.getByText('NLP Team')).toBeInTheDocument();
      expect(screen.getByText('Vision Team')).toBeInTheDocument();
    });
  });

  it('renders create team button', async () => {
    renderWithProviders(<TeamsPage />);
    await waitFor(() => {
      expect(screen.getByText(/创建 Team|admin\.teams\.createTeam/i)).toBeInTheDocument();
    });
  });

  it('renders back button to organizations', async () => {
    renderWithProviders(<TeamsPage />);
    await waitFor(() => {
      expect(screen.getByText(/← 返回|common\.back/i)).toBeInTheDocument();
    });
  });

  it('无路由组织参数时（导航直入）展示组织选择器与提示，并可选定组织后加载列表', async () => {
    mockUseParams.mockReturnValue({});
    renderWithProviders(<TeamsPage />);

    // 未选定组织前不发起 Team 查询，只给出提示，避免出现无上下文的空白页。
    await waitFor(() => {
      expect(screen.getByText(/请先选择组织/)).toBeInTheDocument();
    });
    expect(mockListTeams).not.toHaveBeenCalled();

    // 组织选项来自受控 API。
    await waitFor(() => expect(mockListOrganizations).toHaveBeenCalled());
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(await screen.findByText('Alpha Org (alpha)'));

    // 选定后以该组织为上下文拉取 Team 列表。
    await waitFor(() => {
      expect(mockListTeams).toHaveBeenCalledWith('org_001');
      expect(screen.getByText('NLP Team')).toBeInTheDocument();
    });
  });
});

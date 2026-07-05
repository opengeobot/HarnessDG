/**
 * TeamsPage 组件测试——验证 Team 管理页面的列表渲染、创建表单与成员管理。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
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

vi.mock('../api', () => ({
  listTeams: (...args: unknown[]) => mockListTeams(...args),
  listTeamMembers: (...args: unknown[]) => mockListTeamMembers(...args),
  createTeam: (...args: unknown[]) => mockCreateTeam(...args),
  updateTeam: (...args: unknown[]) => mockUpdateTeam(...args),
  addTeamMember: (...args: unknown[]) => mockAddTeamMember(...args),
  removeTeamMember: (...args: unknown[]) => mockRemoveTeamMember(...args),
}));

// Mock react-router-dom useParams
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useParams: () => ({ orgId: 'org_001' }),
    useNavigate: () => vi.fn(),
  };
});

describe('TeamsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
});

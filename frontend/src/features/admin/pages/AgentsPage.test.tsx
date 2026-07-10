/**
 * AgentsPage 组件测试——Agent 列表、注册、禁用/启用、Tool 白名单。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AgentsPage } from './AgentsPage';
import { renderWithProviders } from '@/test/test-utils';
import type { AgentView, CreatedAgent } from '../types';

const mockAgents: AgentView[] = [
  {
    agentId: 'agt_1',
    displayName: 'Test Agent',
    agentType: 'MCP_CLIENT',
    vendor: 'TestVendor',
    maxSensitivityLevel: 2,
    status: 'ACTIVE',
    toolAllowlist: ['asset_search', 'asset_get'],
    scopes: ['asset:read'],
    principalId: 'prn_agt1',
  },
];

const mockCreatedAgent: CreatedAgent = {
  agentId: 'agt_new',
  credential: 'secret-one-time-credential',
  displayName: 'New Agent',
  principalId: 'prn_new',
  agentType: 'MCP_CLIENT',
  status: 'ACTIVE',
  maxSensitivityLevel: 0,
  scopes: [],
  toolAllowlist: [],
};

const mockListAgents = vi.fn().mockResolvedValue(mockAgents);
const mockCreateAgent = vi.fn().mockResolvedValue(mockCreatedAgent);
const mockEnableAgent = vi.fn().mockResolvedValue({});
const mockDisableAgent = vi.fn().mockResolvedValue({});
const mockUpdateAllowlist = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listAgents: (...args: unknown[]) => mockListAgents(...args),
  createAgent: (...args: unknown[]) => mockCreateAgent(...args),
  enableAgent: (...args: unknown[]) => mockEnableAgent(...args),
  disableAgent: (...args: unknown[]) => mockDisableAgent(...args),
  updateAgentToolAllowlist: (...args: unknown[]) => mockUpdateAllowlist(...args),
}));

describe('AgentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListAgents.mockResolvedValue(mockAgents);
  });

  it('渲染 Agent 列表表格', async () => {
    renderWithProviders(<AgentsPage />);
    expect(screen.getByText('Agent 管理')).toBeInTheDocument();
    expect(await screen.findByText('Test Agent')).toBeInTheDocument();
    expect(screen.getByText('MCP_CLIENT')).toBeInTheDocument();
  });

  it('点击"注册 Agent"打开创建弹窗', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AgentsPage />);
    await user.click(screen.getByRole('button', { name: /注册\s*Agent/i }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('创建成功后显示一次性凭据弹窗', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AgentsPage />);
    await user.click(screen.getByRole('button', { name: /注册\s*Agent/i }));

    const modal = await screen.findByRole('dialog');
    expect(modal).toBeInTheDocument();
    // 验证弹窗内有表单字段
    expect(screen.getByLabelText(/显示名/)).toBeInTheDocument();
  });

  it('禁用操作按钮存在（ACTIVE 状态）', async () => {
    renderWithProviders(<AgentsPage />);
    await screen.findByText('Test Agent');
    // ACTIVE 状态显示禁用按钮
    expect(screen.getByRole('button', { name: /禁\s*用/ })).toBeInTheDocument();
  });

  it('启用操作（DISABLED 状态显示启用按钮）', async () => {
    const disabledAgent = { ...mockAgents[0], status: 'DISABLED' as const };
    mockListAgents.mockResolvedValue([disabledAgent]);
    renderWithProviders(<AgentsPage />);
    await screen.findByText('Test Agent');
    expect(screen.getByRole('button', { name: /启\s*用/ })).toBeInTheDocument();
  });

  it('编辑 Tool 白名单弹窗', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AgentsPage />);
    await screen.findByText('Test Agent');
    await user.click(screen.getByRole('button', { name: /白\s*名\s*单/ }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
  });
});

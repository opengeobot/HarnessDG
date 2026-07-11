/**
 * IntegrationsPage 组件测试——agent-bundle API 渲染。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { IntegrationsPage } from './IntegrationsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockGetAgentBundle = vi.fn().mockResolvedValue({
  mcpEndpointUrl: 'http://localhost:8080/api/v1/mcp',
  openApiUrl: 'http://localhost:8080/v3/api-docs',
  skillTemplates: [
    { name: 'asset-search', description: 'Search assets', path: 'docs/skills/asset-search.md' },
  ],
  exampleCommands: [
    { label: 'Login', command: 'aih login --username user' },
  ],
});

vi.mock('./api', () => ({
  getAgentBundle: (...args: unknown[]) => mockGetAgentBundle(...args),
}));

describe('IntegrationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('渲染页面标题', async () => {
    renderWithProviders(<IntegrationsPage />);
    expect(screen.getByText('Agent 接入')).toBeInTheDocument();
  });

  it('从 agent-bundle 渲染 MCP 端点', async () => {
    renderWithProviders(<IntegrationsPage />);
    await waitFor(() => {
      expect(screen.getByText('http://localhost:8080/api/v1/mcp')).toBeInTheDocument();
    });
  });

  it('渲染 Skill 模板', async () => {
    renderWithProviders(<IntegrationsPage />);
    await waitFor(() => {
      expect(screen.getByText('asset-search')).toBeInTheDocument();
      expect(screen.getByText('Search assets')).toBeInTheDocument();
    });
  });

  it('渲染示例命令', async () => {
    renderWithProviders(<IntegrationsPage />);
    await waitFor(() => {
      expect(screen.getByText('Login')).toBeInTheDocument();
      expect(screen.getByText('aih login --username user')).toBeInTheDocument();
    });
  });
});

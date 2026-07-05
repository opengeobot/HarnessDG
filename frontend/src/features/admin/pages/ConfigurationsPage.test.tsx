/**
 * ConfigurationsPage 组件测试——验证配置管理页面渲染与配置项列表。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { ConfigurationsPage } from './ConfigurationsPage';
import { renderWithProviders } from '@/test/test-utils';

const mockListConfigurations = vi.fn().mockResolvedValue([
  { configKey: 'site.name', value: 'AIHub', version: 1, sensitive: false },
  { configKey: 'feature.upload.enabled', value: 'true', version: 2, sensitive: false },
]);
const mockUpdateConfiguration = vi.fn().mockResolvedValue({});

vi.mock('../api', () => ({
  listConfigurations: (...args: unknown[]) => mockListConfigurations(...args),
  updateConfiguration: (...args: unknown[]) => mockUpdateConfiguration(...args),
}));

describe('ConfigurationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders page title', async () => {
    renderWithProviders(<ConfigurationsPage />);
    await waitFor(() => {
      expect(screen.getByText(/配置管理|admin\.configurations\.title/i)).toBeInTheDocument();
    });
  });

  it('renders configuration list', async () => {
    renderWithProviders(<ConfigurationsPage />);
    await waitFor(() => {
      expect(screen.getByText('site.name')).toBeInTheDocument();
      expect(screen.getByText('feature.upload.enabled')).toBeInTheDocument();
    });
  });
});

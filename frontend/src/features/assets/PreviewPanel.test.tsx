/**
 * PreviewPanel 组件测试——验证预览面板渲染、加载、错误与空状态。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { PreviewPanel } from './PreviewPanel';
import { renderWithProviders } from '@/test/test-utils';

const mockGet = vi.fn();

vi.mock('@/shared/api', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

describe('PreviewPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('加载时显示卡片标题', () => {
    mockGet.mockImplementation(() => new Promise(() => {}));
    renderWithProviders(<PreviewPanel assetId="ast_001" />);
    // Card 标题为"数据预览"
    expect(screen.getByText('数据预览')).toBeInTheDocument();
  });

  it('成功加载 JSON 预览时渲染表格', async () => {
    const jsonContent = JSON.stringify({
      rows: [
        { name: 'alpha', value: '1' },
        { name: 'beta', value: '2' },
      ],
    });
    mockGet.mockResolvedValue({
      previewId: 'prv_001',
      contentType: 'application/json',
      content: jsonContent,
      generatedAt: '2026-07-11T10:00:00Z',
    });

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith(
        expect.stringContaining('/assets/ast_001/previews'),
      );
    });

    await waitFor(() => {
      expect(screen.getByText('alpha')).toBeInTheDocument();
    });
  });

  it('请求失败时显示提示信息', async () => {
    mockGet.mockRejectedValue(new Error('preview not available'));

    renderWithProviders(<PreviewPanel assetId="ast_001" />);

    await waitFor(() => {
      expect(screen.getByText('暂无预览数据')).toBeInTheDocument();
    });
  });

  it('预览内容为空 JSON 时显示空提示', async () => {
    mockGet.mockResolvedValue({
      previewId: 'prv_empty',
      contentType: 'application/json',
      content: JSON.stringify({ rows: [] }),
      generatedAt: null,
    });

    renderWithProviders(<PreviewPanel assetId="ast_001" />);

    await waitFor(() => {
      expect(screen.getByText('预览内容为空')).toBeInTheDocument();
    });
  });
});

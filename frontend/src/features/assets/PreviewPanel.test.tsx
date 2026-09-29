/**
 * PreviewPanel 组件测试——表格/空态/失败态与图片预览渲染（TASK-P0BR-068）。
 */
import { describe, it, expect, vi, beforeEach, afterEach, beforeAll } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { PreviewPanel } from './PreviewPanel';
import { renderWithProviders } from '@/test/test-utils';

const mockGet = vi.fn();
const mockGetBlob = vi.fn();

vi.mock('@/shared/api', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    getBlob: (...args: unknown[]) => mockGetBlob(...args),
  },
}));

const mockCreateObjectURL = vi.fn(() => 'blob:mock-url');
const mockRevokeObjectURL = vi.fn();

beforeAll(() => {
  // jsdom 未实现 ObjectURL API，注入桩函数以断言创建/释放
  Object.defineProperty(URL, 'createObjectURL', { writable: true, configurable: true, value: mockCreateObjectURL });
  Object.defineProperty(URL, 'revokeObjectURL', { writable: true, configurable: true, value: mockRevokeObjectURL });
});

const imageProjection = (extra: Record<string, unknown> = {}) =>
  JSON.stringify({
    mediaType: 'image/png',
    truncated: false,
    artifactId: 'art_img',
    artifactPath: 'samples/preview.png',
    note: 'image preview reference; render via GET /assets/{assetId}/previews/artifact',
    ...extra,
  });

const previewView = (content: string) => ({
  previewId: 'prv_001',
  contentType: 'application/json',
  content,
  generatedAt: '2026-09-29T10:00:00Z',
});

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PreviewPanel', () => {
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
    mockGet.mockResolvedValue(previewView(jsonContent));

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
    mockGet.mockResolvedValue(previewView(JSON.stringify({ rows: [] })));

    renderWithProviders(<PreviewPanel assetId="ast_001" />);

    await waitFor(() => {
      expect(screen.getByText('预览内容为空')).toBeInTheDocument();
    });
  });

  // ---- TASK-P0BR-068 图片渲染分支 ----

  it('图片投影渲染 blob ObjectURL 的 <img>', async () => {
    mockGet.mockResolvedValue(previewView(imageProjection()));
    mockGetBlob.mockResolvedValue({ blob: new Blob(['png-bytes'], { type: 'image/png' }), mediaType: 'image/png' });

    const { unmount } = renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:mock-url');
    });
    // 经 apiClient.getBlob 走代理端点，versionId/artifactId 作为查询参数
    expect(mockGetBlob).toHaveBeenCalledWith(
      '/assets/ast_001/previews/artifact',
      { params: { versionId: 'ver_001', artifactId: 'art_img' } },
    );

    // 卸载时释放 ObjectURL（AC-DST-DETAIL-001：旧 ObjectURL 被 revoke）
    unmount();
    expect(mockRevokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
  });

  it('旧投影缺 artifactId 时显示重新生成引导，不发起 blob 请求', async () => {
    mockGet.mockResolvedValue(
      previewView(imageProjection({ artifactId: undefined, artifactPath: undefined })),
    );

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('图片预览引用已过期，请重新生成预览')).toBeInTheDocument();
    });
    expect(mockGetBlob).not.toHaveBeenCalled();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('缺 versionId 时图片投影回退为引导空态', async () => {
    mockGet.mockResolvedValue(previewView(imageProjection()));

    renderWithProviders(<PreviewPanel assetId="ast_001" />);

    await waitFor(() => {
      expect(screen.getByText('图片预览引用已过期，请重新生成预览')).toBeInTheDocument();
    });
    expect(mockGetBlob).not.toHaveBeenCalled();
  });

  it('blob 请求失败时显示图片预览错误提示', async () => {
    mockGet.mockResolvedValue(previewView(imageProjection()));
    mockGetBlob.mockRejectedValue(new Error('boom'));

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('图片预览加载失败')).toBeInTheDocument();
    });
    // 注意：antd Alert 图标也是 role="img"，需按 alt 定位真实图片元素
    expect(screen.queryByAltText('数据预览')).not.toBeInTheDocument();
  });

  it('投影 mediaType 不在白名单时不发起 blob 请求', async () => {
    mockGet.mockResolvedValue(previewView(imageProjection({ mediaType: 'image/svg+xml' })));
    // 投影声称 svg：前端 image 分支白名单先拦截，不触达 blob 请求
    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('图片预览引用已过期，请重新生成预览')).toBeInTheDocument();
    });
    expect(mockGetBlob).not.toHaveBeenCalled();
  });

  it('响应 mediaType 不在白名单时不创建 ObjectURL', async () => {
    mockGet.mockResolvedValue(previewView(imageProjection()));
    mockGetBlob.mockResolvedValue({ blob: new Blob(['<svg/>']), mediaType: 'image/svg+xml' });

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('图片预览加载失败')).toBeInTheDocument();
    });
    expect(mockCreateObjectURL).not.toHaveBeenCalled();
    expect(screen.queryByAltText('数据预览')).not.toBeInTheDocument();
  });
});

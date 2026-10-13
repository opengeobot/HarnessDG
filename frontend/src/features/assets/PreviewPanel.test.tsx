/**
 * PreviewPanel 组件测试——表格/空态/失败态与图片预览渲染（TASK-P0BR-068）+
 * Split 选择器与统计卡片（TASK-P0BR-078）。
 */
import { describe, it, expect, vi, beforeEach, afterEach, beforeAll } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

const previewView = (content: string, extra: Record<string, unknown> = {}) => ({
  previewId: 'prv_001',
  contentType: 'application/json',
  content,
  generatedAt: '2026-09-29T10:00:00Z',
  ...extra,
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

  // ---- TASK-P0BR-078 Split 选择器与统计卡片 ----

  const twoSplits = [
    {
      name: 'train',
      rowCount: 120,
      fileCount: 2,
      sizeBytes: 2048,
      columns: ['id', 'label'],
      truncated: false,
      sampleRows: [{ id: '1', label: 'cat' }],
    },
    {
      name: 'validation',
      rowCount: 30,
      fileCount: 1,
      sizeBytes: 512,
      columns: ['id', 'label'],
      truncated: true,
      sampleRows: [{ id: '9', label: 'dog' }],
    },
  ];
  const totals = { rowCount: 150, fileCount: 3, sizeBytes: 2560, truncated: false };
  const generatedFrom = {
    versionId: 'ver_001',
    sourceCommit: 'abcdef1234567890',
    generatedAt: '2026-09-29T09:00:00Z',
  };
  const tableContent = JSON.stringify({
    rows: [
      { id: 'main-1', label: 'fallback' },
      { id: 'main-2', label: 'fallback' },
    ],
  });

  it('多 Split 时渲染选择器，默认选中首个且切换后样例随之变化', async () => {
    mockGet.mockResolvedValue(
      previewView(tableContent, { splits: twoSplits, totals, generatedFrom }),
    );

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    // 默认 train 切片
    await waitFor(() => {
      expect(screen.getByText('cat')).toBeInTheDocument();
    });
    expect(screen.getByText('数据切分（Split）')).toBeInTheDocument();
    expect(screen.queryByText('dog')).not.toBeInTheDocument();
    // 统计卡片与投影一致（选中 Split 的 rowCount）+ 来源注记（commit 前 8 位）
    expect(screen.getByText('120')).toBeInTheDocument();
    expect(screen.getByText(/abcdef12/)).toBeInTheDocument();

    // 切换到 validation：样例换为对应切片，截断徽标出现
    await userEvent.click(screen.getByText('validation'));
    await waitFor(() => {
      expect(screen.getByText('dog')).toBeInTheDocument();
    });
    expect(screen.queryByText('cat')).not.toBeInTheDocument();
    expect(screen.getByText('统计已按上限截断')).toBeInTheDocument();
  });

  it('旧投影（无统计分量）显示统计未知引导且样例渲染不回归', async () => {
    mockGet.mockResolvedValue(previewView(tableContent));

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('统计未知 · 重新生成预览后可查看 Split 统计')).toBeInTheDocument();
    });
    // 既有样例渲染不回归；不伪造零值统计
    expect(screen.getByText('main-1')).toBeInTheDocument();
    expect(screen.queryByText('行数')).not.toBeInTheDocument();
    expect(screen.queryByText('数据切分（Split）')).not.toBeInTheDocument();
  });

  it('单 default Split 不渲染选择器但统计卡片展示', async () => {
    const single = [
      {
        name: 'default',
        rowCount: 10,
        fileCount: 1,
        sizeBytes: 100,
        columns: ['a'],
        truncated: false,
        sampleRows: [{ a: 'x' }],
      },
    ];
    mockGet.mockResolvedValue(
      previewView(tableContent, {
        splits: single,
        totals: { rowCount: 10, fileCount: 1, sizeBytes: 100, truncated: false },
        generatedFrom,
      }),
    );

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    await waitFor(() => {
      expect(screen.getByText('x')).toBeInTheDocument();
    });
    expect(screen.queryByText('数据切分（Split）')).not.toBeInTheDocument();
    expect(screen.getByText('行数')).toBeInTheDocument();
  });

  it('Split 缺 sampleRows（旧投影结构）回退主样例，不伪造切片', async () => {
    const noSamples = twoSplits.map((s) => ({
      name: s.name,
      rowCount: s.rowCount,
      fileCount: s.fileCount,
      sizeBytes: s.sizeBytes,
      columns: s.columns,
      truncated: s.truncated,
    }));
    mockGet.mockResolvedValue(
      previewView(tableContent, { splits: noSamples, totals, generatedFrom }),
    );

    renderWithProviders(<PreviewPanel assetId="ast_001" versionId="ver_001" />);

    // 选择器仍在（splits 非空多值），但样例回退 content 主样例
    await waitFor(() => {
      expect(screen.getByText('main-1')).toBeInTheDocument();
    });
    expect(screen.queryByText('cat')).not.toBeInTheDocument();
    expect(screen.getByText('120')).toBeInTheDocument();
  });
});

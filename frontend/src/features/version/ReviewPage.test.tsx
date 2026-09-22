/**
 * 功能: ReviewPage 组件测试——锁定 TASK-P0BR-065 D2 整改：审批队列只包含本会话内
 *       以真实后端 requestId 提交的请求，不再渲染任何派生/虚构行；查询失败呈现
 *       Error + Retry；提交按钮仅对 PENDING_REVIEW 版本开放（AC-P0B-UI-003/006）。
 * 时间: 2026-09-22
 * 作者: AxeXie
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { ReviewPage } from './ReviewPage';
import type { VersionView } from './types';

const listVersionsMock = vi.fn();
vi.mock('./api', () => ({
  listVersions: (...args: unknown[]) => listVersionsMock(...args),
}));

const postMock = vi.fn();
vi.mock('@/shared/api', () => ({
  apiClient: {
    get: vi.fn(),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

function version(overrides: Partial<VersionView> = {}): VersionView {
  return {
    versionId: 'ver-1',
    assetId: 'asset-1',
    version: 'v1.0.0',
    status: 'PENDING_REVIEW',
    createdBy: 'u-1',
    createdAt: '2026-09-22T00:00:00Z',
    updatedAt: '2026-09-22T00:00:00Z',
    ...overrides,
  };
}

/** 输入资产 ID 并点击查询。 */
async function query(assetId = 'asset-1') {
  fireEvent.change(screen.getByPlaceholderText('输入资产 ID'), {
    target: { value: assetId },
  });
  fireEvent.click(screen.getByRole('button', { name: '查询' }));
}

describe('ReviewPage 数据流真实化', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('初始态诚实：仅提示查询，不渲染任何伪造请求行', async () => {
    renderWithProviders(<ReviewPage />);
    expect(await screen.findByText('输入资产 ID 后查询版本。')).toBeInTheDocument();
    expect(screen.getByText('本会话暂未提交发布请求')).toBeInTheDocument();
    // 旧实现以 pub_${versionId} 派生假 requestId、以 system/pending 伪造字段——必须不存在。
    expect(screen.queryByText(/pub_/)).not.toBeInTheDocument();
    expect(screen.queryByText(/冻结摘要/)).not.toBeInTheDocument();
    expect(screen.queryByText(/提交人/)).not.toBeInTheDocument();
    expect(listVersionsMock).not.toHaveBeenCalled();
  });

  it('提交发布按钮仅对 PENDING_REVIEW 版本开放', async () => {
    listVersionsMock.mockResolvedValue({
      items: [
        version(),
        version({ versionId: 'ver-draft', version: 'v0.9.0', status: 'DRAFT' }),
        version({ versionId: 'ver-pub', version: 'v0.8.0', status: 'PUBLISHED' }),
      ],
    });
    renderWithProviders(<ReviewPage />);
    await query();
    await screen.findByText('ver-1');
    expect(screen.queryByText('ver-draft')).not.toBeInTheDocument();
    expect(screen.queryByText('ver-pub')).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /提交发布/ })).toHaveLength(1);
    expect(listVersionsMock).toHaveBeenCalledWith('asset-1');
  });

  it('提交后仅以真实后端 requestId 入队，并可对未决请求提交决策', async () => {
    listVersionsMock.mockResolvedValue({ items: [version()] });
    postMock.mockImplementation((url: string) =>
      Promise.resolve(url.startsWith('/versions/') ? { requestId: 'req-real-1' } : {}),
    );
    renderWithProviders(<ReviewPage />);
    await query();
    fireEvent.click(await screen.findByRole('button', { name: /提交发布/ }));

    expect(await screen.findByText('req-real-1')).toBeInTheDocument();
    expect(postMock).toHaveBeenCalledWith('/versions/ver-1/publish-requests');

    fireEvent.click(screen.getByText('req-real-1'));
    fireEvent.click(await screen.findByRole('button', { name: '提交决策' }));
    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith('/publish-requests/req-real-1/decisions', {
        decision: 'APPROVE',
        comments: '',
      }),
    );
    expect(await screen.findByText('DECIDED:APPROVE')).toBeInTheDocument();
    // 已决策请求不再暴露决策表单
    expect(screen.queryByRole('button', { name: '提交决策' })).not.toBeInTheDocument();
  });

  it('查询失败呈现 alert 与重试，重试走真实查询', async () => {
    listVersionsMock
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce({ items: [] });
    renderWithProviders(<ReviewPage />);

    await query();
    expect(await screen.findByRole('alert')).toHaveTextContent('network down');
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    await waitFor(() => expect(listVersionsMock).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // 查询成功但无待审批版本 → 诚实空态
    expect(await screen.findByText('该资产暂无待审批版本')).toBeInTheDocument();
  });
});

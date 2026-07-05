/**
 * QueryBoundary 组件测试——验证 Loading/Error/Retry/Success 四种状态渲染。
 */
import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryBoundary } from './QueryBoundary';
import { renderWithProviders } from '@/test/test-utils';

describe('QueryBoundary', () => {
  it('加载中展示 Skeleton', () => {
    renderWithProviders(
      <QueryBoundary isLoading isError={false}>
        <div>内容</div>
      </QueryBoundary>,
    );
    expect(screen.queryByText('内容')).not.toBeInTheDocument();
    // Skeleton 渲染占位行
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
  });

  it('错误时展示错误标题和重试按钮', async () => {
    const onRetry = vi.fn();
    renderWithProviders(
      <QueryBoundary isLoading={false} isError error={new Error('test')} onRetry={onRetry}>
        <div>内容</div>
      </QueryBoundary>,
    );
    expect(screen.queryByText('内容')).not.toBeInTheDocument();
    expect(screen.getByText('加载失败')).toBeInTheDocument();

    const retryBtn = screen.getByRole('button');
    await userEvent.click(retryBtn);
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('成功时渲染子内容', () => {
    renderWithProviders(
      <QueryBoundary isLoading={false} isError={false}>
        <div>子内容</div>
      </QueryBoundary>,
    );
    expect(screen.getByText('子内容')).toBeInTheDocument();
  });

  it('错误但无 onRetry 时不展示重试按钮', () => {
    renderWithProviders(
      <QueryBoundary isLoading={false} isError error={new Error('oops')}>
        <div>内容</div>
      </QueryBoundary>,
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

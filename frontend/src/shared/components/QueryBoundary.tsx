/**
 * 功能: 查询状态边界组件。统一处理 TanStack Query 的加载/错误(+重试)状态；
 *       成功时渲染子内容（空态由具体列表组件自行处理，如 Table 的 emptyText）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { ReactNode } from 'react';
import { Button, Result, Skeleton } from 'antd';
import { isApiError } from '@/shared/api';

export interface QueryBoundaryProps {
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  onRetry?: () => void;
  children: ReactNode;
}

export function QueryBoundary({
  isLoading,
  isError,
  error,
  onRetry,
  children,
}: QueryBoundaryProps) {
  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (isError) {
    const message = isApiError(error) ? error.message : '加载失败，请稍后重试';
    return (
      <Result
        status="error"
        title="加载失败"
        subTitle={message}
        extra={
          onRetry ? (
            <Button type="primary" onClick={onRetry}>
              重试
            </Button>
          ) : undefined
        }
      />
    );
  }
  return <>{children}</>;
}

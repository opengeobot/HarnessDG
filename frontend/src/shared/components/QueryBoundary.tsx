/**
 * 功能: 查询状态边界组件。统一处理 TanStack Query 的加载/错误(+重试)状态；
 *       成功时渲染子内容（空态由具体列表组件自行处理，如 Table 的 emptyText）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { ReactNode } from 'react';
import { Button, Result, Skeleton } from 'antd';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
  if (isLoading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (isError) {
    const message = isApiError(error) ? error.message : t('common.loadFailedRetry');
    return (
      <Result
        status="error"
        title={t('queryBoundary.errorTitle')}
        subTitle={message}
        extra={
          onRetry ? (
            <Button type="primary" onClick={onRetry}>
              {t('queryBoundary.retry')}
            </Button>
          ) : undefined
        }
      />
    );
  }
  return <>{children}</>;
}

/**
 * 功能: 任务管理页面。持久化任务游标列表 + 状态过滤 + 人工重试/取消。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Flex,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isApiError } from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { cancelJob, listJobs, retryJob } from '../api';
import type { JobStatus, JobView } from '../types';

const STATUS_COLOR: Record<JobStatus, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  SUCCEEDED: 'green',
  RETRY_WAIT: 'gold',
  DEAD: 'red',
  CANCELLED: 'default',
};

export function JobsPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.jobs.title'));
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<JobStatus | undefined>();

  const query = useInfiniteQuery({
    queryKey: ['admin', 'jobs', { status }],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => listJobs({ status, cursor: pageParam, limit: 20 }),
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'jobs'] });
  const onError = (error: unknown) =>
    message.error(isApiError(error) ? error.message : t('common.operationFailed'));

  const retryMutation = useMutation({
    mutationFn: (jobId: string) => retryJob(jobId),
    onSuccess: () => {
      message.success(t('admin.jobs.retryScheduled'));
      void invalidate();
    },
    onError,
  });

  const cancelMutation = useMutation({
    mutationFn: (jobId: string) => cancelJob(jobId),
    onSuccess: () => {
      message.success(t('admin.jobs.cancelled'));
      void invalidate();
    },
    onError,
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<JobView> = [
    { title: t('admin.jobs.jobId'), dataIndex: 'jobId', key: 'jobId' },
    { title: t('common.type'), dataIndex: 'jobType', key: 'jobType' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (value: JobStatus) => <Tag color={STATUS_COLOR[value]}>{value}</Tag>,
    },
    { title: t('admin.jobs.retryCount'), dataIndex: 'retryCount', key: 'retryCount' },
    { title: t('admin.jobs.nextRunAt'), dataIndex: 'nextRunAt', key: 'nextRunAt', render: (v: string) => v || '-' },
    { title: t('admin.jobs.errorCode'), dataIndex: 'errorCode', key: 'errorCode', render: (v: string) => v || '-' },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => retryMutation.mutate(record.jobId)}>
            {t('admin.jobs.retry')}
          </Button>
          <Popconfirm title={t('admin.jobs.confirmCancel')} onConfirm={() => cancelMutation.mutate(record.jobId)}>
            <Button type="link" size="small" danger>
              {t('common.cancel')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.jobs.title')}
        </Typography.Title>
        <Space>
          <Select
            allowClear
            style={{ width: 160 }}
            placeholder={t('admin.jobs.filterByStatus')}
            value={status}
            onChange={(value) => setStatus(value)}
            options={(Object.keys(STATUS_COLOR) as JobStatus[]).map((s) => ({ value: s, label: s }))}
          />
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<JobView> rowKey="jobId" columns={columns} dataSource={items} pagination={false} />
        {query.hasNextPage ? (
          <Flex justify="center" style={{ marginTop: 16 }}>
            <Button onClick={() => query.fetchNextPage()} loading={query.isFetchingNextPage}>
              {t('assets.loadMore')}
            </Button>
          </Flex>
        ) : null}
      </QueryBoundary>
    </Flex>
  );
}

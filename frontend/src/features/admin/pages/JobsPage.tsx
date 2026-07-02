/**
 * 功能: 任务管理页面。持久化任务游标列表 + 状态过滤 + 人工重试/取消。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
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
  useDocumentTitle('任务管理');
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
    message.error(isApiError(error) ? error.message : '操作失败');

  const retryMutation = useMutation({
    mutationFn: (jobId: string) => retryJob(jobId),
    onSuccess: () => {
      message.success('已安排重试');
      void invalidate();
    },
    onError,
  });

  const cancelMutation = useMutation({
    mutationFn: (jobId: string) => cancelJob(jobId),
    onSuccess: () => {
      message.success('已取消');
      void invalidate();
    },
    onError,
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<JobView> = [
    { title: '任务 ID', dataIndex: 'jobId', key: 'jobId' },
    { title: '类型', dataIndex: 'jobType', key: 'jobType' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (value: JobStatus) => <Tag color={STATUS_COLOR[value]}>{value}</Tag>,
    },
    { title: '重试次数', dataIndex: 'retryCount', key: 'retryCount' },
    { title: '下次执行', dataIndex: 'nextRunAt', key: 'nextRunAt', render: (v: string) => v || '-' },
    { title: '错误码', dataIndex: 'errorCode', key: 'errorCode', render: (v: string) => v || '-' },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => retryMutation.mutate(record.jobId)}>
            重试
          </Button>
          <Popconfirm title="确认取消该任务？" onConfirm={() => cancelMutation.mutate(record.jobId)}>
            <Button type="link" size="small" danger>
              取消
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
          任务管理
        </Typography.Title>
        <Space>
          <Select
            allowClear
            style={{ width: 160 }}
            placeholder="按状态过滤"
            value={status}
            onChange={(value) => setStatus(value)}
            options={(Object.keys(STATUS_COLOR) as JobStatus[]).map((s) => ({ value: s, label: s }))}
          />
          <Button onClick={() => query.refetch()}>刷新</Button>
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
              加载更多
            </Button>
          </Flex>
        ) : null}
      </QueryBoundary>
    </Flex>
  );
}

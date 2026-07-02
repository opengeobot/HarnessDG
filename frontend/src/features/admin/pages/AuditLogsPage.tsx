/**
 * 功能: 审计日志页面（只读）。过滤（主体/动作/资源）+ 游标分页查询不可变审计记录。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { Button, Flex, Form, Input, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listAuditLogs } from '../api';
import type { AuditLogView, AuditResult, ListAuditLogsParams } from '../types';

const RESULT_COLOR: Record<AuditResult, string> = {
  SUCCEEDED: 'green',
  FAILED: 'red',
  DENIED: 'orange',
};

export function AuditLogsPage() {
  useDocumentTitle('审计日志');
  const [filter, setFilter] = useState<ListAuditLogsParams>({});
  const [form] = Form.useForm<ListAuditLogsParams>();

  const query = useInfiniteQuery({
    queryKey: ['admin', 'audit-logs', filter],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => listAuditLogs({ ...filter, cursor: pageParam, limit: 20 }),
    getNextPageParam: (last) => (last.hasMore ? last.nextCursor ?? undefined : undefined),
  });

  const items = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );

  const columns: ColumnsType<AuditLogView> = [
    { title: '时间', dataIndex: 'occurredAt', key: 'occurredAt' },
    { title: '主体', dataIndex: 'principalId', key: 'principalId' },
    { title: '动作', dataIndex: 'action', key: 'action' },
    {
      title: '资源',
      key: 'resource',
      render: (_, record) => `${record.resourceType}:${record.resourceId}`,
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      render: (value: AuditResult) => <Tag color={RESULT_COLOR[value]}>{value}</Tag>,
    },
    { title: '错误码', dataIndex: 'errorCode', key: 'errorCode', render: (v: string) => v || '-' },
    { title: 'Trace', dataIndex: 'traceId', key: 'traceId', render: (v: string) => v || '-' },
  ];

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        审计日志
      </Typography.Title>

      <Form
        form={form}
        layout="inline"
        onFinish={(values) =>
          setFilter({
            principalId: values.principalId?.trim() || undefined,
            action: values.action?.trim() || undefined,
            resourceId: values.resourceId?.trim() || undefined,
          })
        }
      >
        <Form.Item name="principalId">
          <Input allowClear placeholder="主体 ID" style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="action">
          <Input allowClear placeholder="动作" style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="resourceId">
          <Input allowClear placeholder="资源 ID" style={{ width: 200 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                setFilter({});
              }}
            >
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<AuditLogView> rowKey="auditId" columns={columns} dataSource={items} pagination={false} />
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

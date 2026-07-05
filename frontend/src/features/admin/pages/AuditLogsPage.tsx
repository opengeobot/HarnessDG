/**
 * 功能: 审计日志页面（只读）。过滤（主体/动作/资源）+ 游标分页查询不可变审计记录。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
  useDocumentTitle(t('admin.auditLogs.title'));
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
    { title: t('admin.auditLogs.time'), dataIndex: 'occurredAt', key: 'occurredAt' },
    { title: t('admin.auditLogs.principal'), dataIndex: 'principalId', key: 'principalId' },
    { title: t('admin.auditLogs.action'), dataIndex: 'action', key: 'action' },
    {
      title: t('admin.auditLogs.resource'),
      key: 'resource',
      render: (_, record) => `${record.resourceType}:${record.resourceId}`,
    },
    {
      title: t('admin.auditLogs.result'),
      dataIndex: 'result',
      key: 'result',
      render: (value: AuditResult) => <Tag color={RESULT_COLOR[value]}>{value}</Tag>,
    },
    { title: t('admin.auditLogs.errorCode'), dataIndex: 'errorCode', key: 'errorCode', render: (v: string) => v || '-' },
    { title: 'Trace', dataIndex: 'traceId', key: 'traceId', render: (v: string) => v || '-' },
  ];

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('admin.auditLogs.title')}
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
          <Input allowClear placeholder={t('admin.auditLogs.principalPlaceholder')} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="action">
          <Input allowClear placeholder={t('admin.auditLogs.actionPlaceholder')} style={{ width: 180 }} />
        </Form.Item>
        <Form.Item name="resourceId">
          <Input allowClear placeholder={t('admin.auditLogs.resourceIdPlaceholder')} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              {t('common.query')}
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                setFilter({});
              }}
            >
              {t('admin.auditLogs.reset')}
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
              {t('assets.loadMore')}
            </Button>
          </Flex>
        ) : null}
      </QueryBoundary>
    </Flex>
  );
}

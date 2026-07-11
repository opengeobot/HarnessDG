/**
 * 功能: 幂等记录浏览页面。只读列表，展示主体/键/路径/指纹/创建时间。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Button, Flex, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listIdempotencyRecords } from '../api';
import type { IdempotencyRecordView } from '../types';

export function IdempotencyPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.idempotency.title'));

  const query = useQuery({
    queryKey: ['admin', 'idempotency'],
    queryFn: () => listIdempotencyRecords(100),
  });

  const columns: ColumnsType<IdempotencyRecordView> = [
    { title: t('admin.idempotency.key'), dataIndex: 'idempotencyKey', key: 'idempotencyKey', ellipsis: true },
    { title: t('admin.idempotency.principalId'), dataIndex: 'principalId', key: 'principalId' },
    {
      title: t('admin.idempotency.methodPath'),
      key: 'methodPath',
      render: (_, record) => `${record.method} ${record.path}`,
      ellipsis: true,
    },
    {
      title: t('admin.idempotency.digest'),
      dataIndex: 'requestDigest',
      key: 'requestDigest',
      render: (v: string) => v || '-',
      ellipsis: true,
    },
    {
      title: t('admin.idempotency.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.idempotency.title')}
        </Typography.Title>
        <Space>
          <Typography.Text type="secondary">{t('admin.idempotency.readonlyHint')}</Typography.Text>
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<IdempotencyRecordView>
          rowKey="idempotencyKey"
          columns={columns}
          dataSource={query.data ?? []}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          size="small"
        />
      </QueryBoundary>
    </Flex>
  );
}

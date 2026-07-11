/**
 * 功能: 系统告警页面。展示 system_alert 记录，支持人工确认。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Flex, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { acknowledgeAlert, listAlerts } from '../api';
import type { SystemAlertView } from '../types';

const SEVERITY_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'gold',
  CRITICAL: 'red',
};

const STATUS_COLOR: Record<string, string> = {
  FIRING: 'red',
  RESOLVED: 'green',
};

export function AlertsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  useDocumentTitle(t('admin.alerts.title'));

  const query = useQuery({
    queryKey: ['admin', 'alerts'],
    queryFn: () => listAlerts(100),
  });

  const acknowledgeMutation = useMutation({
    mutationFn: (alertId: string) => acknowledgeAlert(alertId),
    onSuccess: () => {
      message.success(t('admin.alerts.acknowledgeSuccess'));
      queryClient.invalidateQueries({ queryKey: ['admin', 'alerts'] });
    },
    onError: () => {
      message.error(t('admin.alerts.acknowledgeFailed'));
    },
  });

  const columns: ColumnsType<SystemAlertView> = [
    { title: t('admin.alerts.alertId'), dataIndex: 'alertId', key: 'alertId' },
    { title: t('admin.alerts.alertType'), dataIndex: 'alertType', key: 'alertType' },
    {
      title: t('admin.alerts.severity'),
      dataIndex: 'severity',
      key: 'severity',
      render: (value: string) => <Tag color={SEVERITY_COLOR[value] ?? 'default'}>{value}</Tag>,
    },
    { title: t('admin.alerts.titleCol'), dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (value: string) => <Tag color={STATUS_COLOR[value] ?? 'default'}>{value}</Tag>,
    },
    {
      title: t('admin.alerts.firedAt'),
      dataIndex: 'firedAt',
      key: 'firedAt',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: t('admin.alerts.resolvedAt'),
      dataIndex: 'resolvedAt',
      key: 'resolvedAt',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'actions',
      render: (_: unknown, record: SystemAlertView) =>
        record.status === 'FIRING' ? (
          <Button
            size="small"
            aria-label={t('admin.alerts.acknowledge')}
            loading={acknowledgeMutation.isPending && acknowledgeMutation.variables === record.alertId}
            onClick={() => acknowledgeMutation.mutate(record.alertId)}
          >
            {t('admin.alerts.acknowledge')}
          </Button>
        ) : null,
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('admin.alerts.title')}
        </Typography.Title>
        <Space>
          <Button onClick={() => query.refetch()}>{t('common.refresh')}</Button>
        </Space>
      </Flex>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<SystemAlertView>
          rowKey="alertId"
          columns={columns}
          dataSource={query.data ?? []}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          size="small"
          expandable={{
            expandedRowRender: (record) => (
              <Typography.Paragraph style={{ margin: 0 }}>
                {record.detail || t('admin.alerts.noDetail')}
              </Typography.Paragraph>
            ),
          }}
        />
      </QueryBoundary>
    </Flex>
  );
}

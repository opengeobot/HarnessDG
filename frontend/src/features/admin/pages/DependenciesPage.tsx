/**
 * 功能: 系统依赖健康页面。复用 fetchSystemDependencies 展示依赖健康；
 *       附带受保护的平台指标摘要（system:observe）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Card, Descriptions, Flex, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  fetchSystemDependencies,
  type DependencyHealth,
  type DependencyStatus,
} from '@/shared/api';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { getMetricsSummary } from '../api';

const HEALTH_COLOR: Record<DependencyHealth, string> = {
  UP: 'green',
  DEGRADED: 'orange',
  DOWN: 'red',
};

export function DependenciesPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('admin.dependencies.title'));

  const depsQuery = useQuery({
    queryKey: ['admin', 'dependencies'],
    queryFn: fetchSystemDependencies,
  });

  const metricsQuery = useQuery({
    queryKey: ['admin', 'metrics-summary'],
    queryFn: getMetricsSummary,
  });

  const columns: ColumnsType<DependencyStatus> = [
    { title: t('admin.dependencies.component'), dataIndex: 'name', key: 'name' },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: DependencyHealth) => <Tag color={HEALTH_COLOR[status]}>{status}</Tag>,
    },
    {
      title: t('admin.dependencies.latencyMs'),
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      render: (value: number) => value ?? '-',
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {t('admin.dependencies.title')}
      </Typography.Title>

      <Card
        title={
          <Space>
            {t('admin.dependencies.health')}
            {depsQuery.data ? (
              <Tag color={HEALTH_COLOR[depsQuery.data.status]}>{depsQuery.data.status}</Tag>
            ) : null}
          </Space>
        }
      >
        <QueryBoundary
          isLoading={depsQuery.isLoading}
          isError={depsQuery.isError}
          error={depsQuery.error}
          onRetry={() => depsQuery.refetch()}
        >
          <Table<DependencyStatus>
            rowKey="name"
            size="small"
            columns={columns}
            dataSource={depsQuery.data?.dependencies ?? []}
            pagination={false}
          />
        </QueryBoundary>
      </Card>

      <Card title={t('admin.dependencies.metricsSummary')}>
        <QueryBoundary
          isLoading={metricsQuery.isLoading}
          isError={metricsQuery.isError}
          error={metricsQuery.error}
          onRetry={() => metricsQuery.refetch()}
        >
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label={t('admin.dependencies.generatedAt')}>
              {metricsQuery.data?.generatedAt}
            </Descriptions.Item>
            <Descriptions.Item label="API">
              <Typography.Text code>{JSON.stringify(metricsQuery.data?.api ?? {})}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('admin.dependencies.jobs')}>
              <Typography.Text code>{JSON.stringify(metricsQuery.data?.jobs ?? {})}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={t('admin.dependencies.dependencies')}>
              <Typography.Text code>
                {JSON.stringify(metricsQuery.data?.dependencies ?? {})}
              </Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        </QueryBoundary>
      </Card>
    </Flex>
  );
}

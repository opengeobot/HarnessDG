/**
 * 功能: 版本列表面板——嵌入资产详情页，展示该资产下的版本列表与快捷操作；
 *       并导出该资产版本列表的独立页面，供「查看全部」到达真实可达路由（修复死链）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Empty, Flex, Space, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { QueryBoundary } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { listVersions } from './api';
import type { VersionStatus, VersionView } from './types';

const STATUS_COLOR: Record<VersionStatus, string> = {
  DRAFT: 'default',
  VALIDATING: 'processing',
  PENDING_REVIEW: 'warning',
  PUBLISHED: 'success',
  DEPRECATED: 'orange',
  ARCHIVED: 'default',
};

interface VersionListPanelProps {
  assetId: string;
}

/** 版本列表的列定义。面板与独立页面共用，避免两处漂移。 */
function useVersionColumns(assetId: string): ColumnsType<VersionView> {
  const { t } = useTranslation();
  return [
    {
      title: t('version.version'),
      dataIndex: 'version',
      key: 'version',
      render: (version: string, record) => (
        <Link to={`/assets/${assetId}/versions/${record.versionId}`}>
          <Typography.Text strong code>
            {version}
          </Typography.Text>
        </Link>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (status: VersionStatus) => (
        <Tag color={STATUS_COLOR[status] ?? 'default'}>{status}</Tag>
      ),
    },
    {
      title: t('version.commit'),
      dataIndex: 'sourceCommit',
      key: 'sourceCommit',
      render: (commit: string | null) =>
        commit ? (
          <Typography.Text code>{commit.slice(0, 12)}</Typography.Text>
        ) : (
          '-'
        ),
    },
    {
      title: t('version.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (v ? new Date(v).toLocaleDateString() : '-'),
    },
    {
      title: t('common.action'),
      key: 'action',
      render: (_, record) => (
        <Link to={`/assets/${assetId}/versions/${record.versionId}`}>
          <Button type="link" size="small">
            {t('version.viewDetail')}
          </Button>
        </Link>
      ),
    },
  ];
}

/**
 * 资产版本列表独立页面（`PAGE-VER-001`，路由 `/assets/:assetId/versions`）。
 *
 * <p>存在的理由：资产详情页的「查看全部」需要一个真实可达的目标，
 * 否则会落到全局 404。本页面只呈现既有版本列表能力，不新增后端能力。
 */
export function VersionListPage() {
  const { t } = useTranslation();
  const { assetId } = useParams<{ assetId: string }>();
  useDocumentTitle(t('version.versionList'));

  const query = useQuery({
    queryKey: ['versions', assetId],
    queryFn: () => listVersions(assetId!),
    enabled: Boolean(assetId),
  });

  const columns = useVersionColumns(assetId ?? '');

  return (
    <Flex vertical gap={16}>
      <Space>
        <Link to={`/assets/${assetId}`}>
          <Button>{t('common.back')}</Button>
        </Link>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t('version.versionList')}
        </Typography.Title>
      </Space>

      <QueryBoundary
        isLoading={query.isLoading}
        isError={query.isError}
        error={query.error}
        onRetry={() => query.refetch()}
      >
        <Table<VersionView>
          rowKey="versionId"
          columns={columns}
          dataSource={query.data?.items ?? []}
          locale={{ emptyText: <Empty description={t('version.noVersions')} /> }}
        />
      </QueryBoundary>
    </Flex>
  );
}

export function VersionListPanel({ assetId }: VersionListPanelProps) {
  const { t } = useTranslation();

  const query = useQuery({
    queryKey: ['versions', assetId],
    queryFn: () => listVersions(assetId),
    enabled: !!assetId,
  });

  const columns = useVersionColumns(assetId);

  return (
    <Card
      title={t('version.versionList')}
      size="small"
      extra={
        <Link to={`/assets/${assetId}/versions`}>
          <Button type="link" size="small">
            {t('version.viewAll')}
          </Button>
        </Link>
      }
    >
      {query.isLoading ? (
        <Flex justify="center" style={{ padding: 24 }}>
          <Spin />
        </Flex>
      ) : query.isError || !query.data ? (
        <Empty description={t('version.noVersions')} />
      ) : (
        <Table<VersionView>
          rowKey="versionId"
          columns={columns}
          dataSource={query.data.items}
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description={t('version.noVersions')} /> }}
        />
      )}
    </Card>
  );
}

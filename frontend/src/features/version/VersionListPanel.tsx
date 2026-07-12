/**
 * 功能: 版本列表面板——嵌入资产详情页，展示该资产下的版本列表与快捷操作。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Empty, Flex, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
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

export function VersionListPanel({ assetId }: VersionListPanelProps) {
  const { t } = useTranslation();

  const query = useQuery({
    queryKey: ['versions', assetId],
    queryFn: () => listVersions(assetId),
    enabled: !!assetId,
  });

  const columns: ColumnsType<VersionView> = [
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

  return (
    <Card
      title={t('version.versionList')}
      size="small"
      extra={
        <Link to={`/assets/${assetId}?tab=versions`}>
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

/**
 * 功能: 版本详情页——展示版本元数据与工件文件列表。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { getVersion, listArtifacts } from './api';
import type { ArtifactView, VersionStatus } from './types';
import { PreviewPanel } from '@/features/assets/PreviewPanel';

const STATUS_COLOR: Record<VersionStatus, string> = {
  DRAFT: 'default',
  VALIDATING: 'processing',
  PENDING_REVIEW: 'warning',
  PUBLISHED: 'success',
  DEPRECATED: 'orange',
  ARCHIVED: 'default',
};

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function VersionDetailPage() {
  const { assetId, versionId } = useParams<{ assetId: string; versionId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  useDocumentTitle(t('version.versionDetail'));

  const versionQuery = useQuery({
    queryKey: ['version', versionId],
    queryFn: () => getVersion(assetId!, versionId!),
    enabled: !!versionId,
  });

  const artifactsQuery = useQuery({
    queryKey: ['artifacts', versionId],
    queryFn: () => listArtifacts(assetId!, versionId!),
    enabled: !!versionId,
  });

  if (versionQuery.isLoading) return <Spin style={{ display: 'block', margin: '80px auto' }} />;
  if (versionQuery.isError || !versionQuery.data) return <Empty description={t('version.noVersions')} />;

  const version = versionQuery.data;
  const artifacts = artifactsQuery.data ?? [];

  const columns: ColumnsType<ArtifactView> = [
    {
      title: t('version.path'),
      dataIndex: 'path',
      key: 'path',
      render: (path: string) => (
        <Typography.Text code>{path}</Typography.Text>
      ),
    },
    {
      title: t('version.size'),
      dataIndex: 'size',
      key: 'size',
      align: 'right',
      render: (size: number) => formatBytes(size),
    },
    {
      title: 'SHA-256',
      dataIndex: 'sha256',
      key: 'sha256',
      render: (hash: string) => (
        <Typography.Text code style={{ fontSize: 11 }}>
          {hash.slice(0, 16)}...
        </Typography.Text>
      ),
    },
    {
      title: t('version.mediaType'),
      dataIndex: 'mediaType',
      key: 'mediaType',
      render: (mt: string | null) => mt ?? '-',
    },
  ];

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate(`/assets/${assetId}`)}>{t('common.back')}</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t('version.versionDetail')} · {version.version}
          </Typography.Title>
          <Tag color={STATUS_COLOR[version.status] ?? 'default'}>{version.status}</Tag>
        </Space>
      </Flex>

      <Card title={t('version.versionInfo')} size="small">
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('version.versionId')}>
            <Typography.Text code>{version.versionId}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('common.status')}>
            <Tag color={STATUS_COLOR[version.status] ?? 'default'}>{version.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('version.commit')}>
            {version.sourceCommit ? (
              <Typography.Text code>{version.sourceCommit}</Typography.Text>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Git Tag">{version.gitTag ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="Manifest Digest" span={2}>
            {version.manifestDigest ? (
              <Typography.Text code style={{ fontSize: 11 }}>
                {version.manifestDigest}
              </Typography.Text>
            ) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('version.publishedAt')}>
            {version.publishedAt ? new Date(version.publishedAt).toLocaleString() : '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('version.publishedBy')}>
            {version.publishedBy ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('version.notes')} span={2}>
            {version.notes ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('version.createdBy')}>
            {version.createdBy}
          </Descriptions.Item>
          <Descriptions.Item label={t('version.createdAt')}>
            {new Date(version.createdAt).toLocaleString()}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={`${t('version.artifactList')} (${artifacts.length})`} size="small">
        <Table<ArtifactView>
          rowKey="artifactId"
          columns={columns}
          dataSource={artifacts}
          loading={artifactsQuery.isLoading}
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description={t('version.noArtifacts')} /> }}
        />
      </Card>

      <PreviewPanel assetId={assetId!} versionId={versionId} />
    </Flex>
  );
}

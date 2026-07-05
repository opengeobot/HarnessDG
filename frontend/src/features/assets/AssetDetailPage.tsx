/**
 * 功能: 资产详情页——Card 安全渲染、治理字段、Owner、标签、生命周期操作、讨论面板。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  App,
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { useTranslation } from 'react-i18next';
import { useDocumentTitle } from '@/shared/hooks';
import { SafeMarkdown } from '@/shared/components';
import { isApiError } from '@/shared/api';
import {
  archiveAsset,
  deprecateAsset,
  getAsset,
  restoreAsset,
} from './api';
import { DiscussionPanel } from './DiscussionPanel';
import type { AssetView, ProvisioningStatus } from './types';

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  DEPRECATED: 'orange',
  ARCHIVED: 'default',
};

const PROVISIONING_STATUS_COLOR: Record<ProvisioningStatus, string> = {
  NONE: 'default',
  PENDING: 'processing',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
};

export function AssetDetailPage() {
  const { assetId } = useParams<{ assetId: string }>();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  useDocumentTitle(t('assets.detailTitle'));

  const PROVISIONING_LABEL: Record<ProvisioningStatus, string> = {
    NONE: t('assets.provisioning.NONE'),
    PENDING: t('assets.provisioning.PENDING'),
    IN_PROGRESS: t('assets.provisioning.IN_PROGRESS'),
    COMPLETED: t('assets.provisioning.COMPLETED'),
    FAILED: t('assets.provisioning.FAILED'),
  };

  const query = useQuery({
    queryKey: ['asset', assetId],
    queryFn: () => getAsset(assetId!),
    enabled: !!assetId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    void queryClient.invalidateQueries({ queryKey: ['assets'] });
  }, [queryClient, assetId]);

  const deprecateMut = useMutation({
    mutationFn: () => deprecateAsset(assetId!),
    onSuccess: () => { message.success(t('assets.deprecated')); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : t('common.operationFailed')),
  });
  const archiveMut = useMutation({
    mutationFn: () => archiveAsset(assetId!),
    onSuccess: () => { message.success(t('assets.archived')); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : t('common.operationFailed')),
  });
  const restoreMut = useMutation({
    mutationFn: () => restoreAsset(assetId!),
    onSuccess: () => { message.success(t('assets.restored')); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : t('common.operationFailed')),
  });

  if (query.isLoading) return <Spin style={{ display: 'block', margin: '80px auto' }} />;
  if (query.isError || !query.data) return <Empty description={t('assets.notExist')} />;

  const asset: AssetView = query.data;
  const provStatus = asset.provisioningStatus ?? 'NONE';

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/assets')}>{t('common.back')}</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {asset.displayName || asset.name}
          </Typography.Title>
          <Tag color={STATUS_COLOR[asset.status]}>{asset.status}</Tag>
          <Badge
            status={PROVISIONING_STATUS_COLOR[provStatus] as 'default'}
            text={PROVISIONING_LABEL[provStatus]}
          />
        </Space>
        <Space>
          {asset.status === 'ACTIVE' && (
            <Popconfirm title={t('assets.confirmDeprecate')} onConfirm={() => deprecateMut.mutate()}>
              <Button>{t('assets.deprecate')}</Button>
            </Popconfirm>
          )}
          {asset.status !== 'ARCHIVED' && (
            <Popconfirm title={t('assets.confirmArchive')} onConfirm={() => archiveMut.mutate()}>
              <Button>{t('assets.archive')}</Button>
            </Popconfirm>
          )}
          {asset.status !== 'ACTIVE' && (
            <Popconfirm title={t('assets.confirmRestore')} onConfirm={() => restoreMut.mutate()}>
              <Button type="primary">{t('assets.restore')}</Button>
            </Popconfirm>
          )}
        </Space>
      </Flex>

      <Card title={t('assets.detail.basicInfo')}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('assets.detail.coordinate')}>
            <Typography.Text code>{asset.namespace}/{asset.type}/{asset.name}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label={t('common.type')}>
            <Tag color={asset.type === 'MODEL' ? 'geekblue' : 'purple'}>
              {asset.type === 'MODEL' ? t('assets.model') : t('assets.dataset')}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('assets.detail.organization')}>{asset.organizationId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('assets.detail.project')}>{asset.projectId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('assets.columns.visibility')}>
            <Tag color={asset.visibility === 'PUBLIC' ? 'green' : asset.visibility === 'INTERNAL' ? 'blue' : 'red'}>
              {asset.visibility}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('assets.detail.license')}>{asset.license ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('assets.detail.owners')} span={2}>
            {asset.owners?.join(', ') ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('common.description')} span={2}>
            {asset.description ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('assets.columns.tags')} span={2}>
            <Space size={[0, 4]} wrap>
              {asset.tags?.map((tag) => <Tag key={tag}>{tag}</Tag>)}
              {asset.tagIds?.map((tagId) => <Tag key={tagId} color="blue">{tagId}</Tag>)}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {asset.model && (
        <Card title={t('assets.detail.modelProfile')} size="small">
          <Descriptions column={3} size="small">
            <Descriptions.Item label={t('assets.detail.framework')}>{asset.model.framework ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.task')}>{asset.model.task ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.architecture')}>{asset.model.architecture ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.dataset && (
        <Card title={t('assets.detail.datasetProfile')} size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label={t('assets.detail.format')}>{asset.dataset.format ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.modality')}>{asset.dataset.modality ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.repository && (
        <Card title={t('assets.detail.repoInfo')} size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label={t('assets.detail.repoName')}>{asset.repository.fullName}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.link')}>
              {asset.repository.htmlUrl ? (
                <a href={asset.repository.htmlUrl} target="_blank" rel="noopener noreferrer">
                  {asset.repository.htmlUrl}
                </a>
              ) : '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.card && (
        <Card title={t('assets.detail.cardProjection')} size="small">
          {asset.card.untrustedContent && (
            <Alert
              type="warning"
              showIcon
              message={t('assets.detail.untrustedWarning')}
              style={{ marginBottom: 12 }}
            />
          )}
          {asset.card.sourceCommit && (
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
              {t('assets.detail.sourceCommit')}: {asset.card.sourceCommit}
            </Typography.Text>
          )}
          {asset.card.readme && (
            <Typography.Paragraph>
              <Typography.Title level={5}>README</Typography.Title>
              <SafeMarkdown content={asset.card.readme} />
            </Typography.Paragraph>
          )}
          {asset.card.assetYaml && (
            <Typography.Paragraph>
              <Typography.Title level={5}>asset.yaml</Typography.Title>
              <SafeMarkdown content={asset.card.assetYaml} />
            </Typography.Paragraph>
          )}
        </Card>
      )}

      {assetId && <DiscussionPanel assetId={assetId} />}
    </Flex>
  );
}

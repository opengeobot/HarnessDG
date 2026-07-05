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
import { useDocumentTitle } from '@/shared/hooks';
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

const PROVISIONING_LABEL: Record<ProvisioningStatus, string> = {
  NONE: '未触发',
  PENDING: '等待建仓',
  IN_PROGRESS: '建仓中',
  COMPLETED: '建仓完成',
  FAILED: '建仓失败',
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

  useDocumentTitle('资产详情');

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
    onSuccess: () => { message.success('已弃用'); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : '操作失败'),
  });
  const archiveMut = useMutation({
    mutationFn: () => archiveAsset(assetId!),
    onSuccess: () => { message.success('已归档'); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : '操作失败'),
  });
  const restoreMut = useMutation({
    mutationFn: () => restoreAsset(assetId!),
    onSuccess: () => { message.success('已恢复'); invalidate(); },
    onError: (err) => message.error(isApiError(err) ? err.message : '操作失败'),
  });

  if (query.isLoading) return <Spin style={{ display: 'block', margin: '80px auto' }} />;
  if (query.isError || !query.data) return <Empty description="资产不存在" />;

  const asset: AssetView = query.data;
  const provStatus = asset.provisioningStatus ?? 'NONE';

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={12}>
        <Space>
          <Button onClick={() => navigate('/assets')}>← 返回</Button>
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
            <Popconfirm title="确认弃用该资产？" onConfirm={() => deprecateMut.mutate()}>
              <Button>弃用</Button>
            </Popconfirm>
          )}
          {asset.status !== 'ARCHIVED' && (
            <Popconfirm title="确认归档该资产？" onConfirm={() => archiveMut.mutate()}>
              <Button>归档</Button>
            </Popconfirm>
          )}
          {asset.status !== 'ACTIVE' && (
            <Popconfirm title="确认恢复该资产？" onConfirm={() => restoreMut.mutate()}>
              <Button type="primary">恢复</Button>
            </Popconfirm>
          )}
        </Space>
      </Flex>

      <Card title="基本信息">
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="坐标">
            <Typography.Text code>{asset.namespace}/{asset.type}/{asset.name}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            <Tag color={asset.type === 'MODEL' ? 'geekblue' : 'purple'}>
              {asset.type === 'MODEL' ? '模型' : '数据集'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="组织">{asset.organizationId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="项目">{asset.projectId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="可见性">
            <Tag color={asset.visibility === 'PUBLIC' ? 'green' : asset.visibility === 'INTERNAL' ? 'blue' : 'red'}>
              {asset.visibility}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="许可证">{asset.license ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="Owner" span={2}>
            {asset.owners?.join(', ') ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="描述" span={2}>
            {asset.description ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="标签" span={2}>
            <Space size={[0, 4]} wrap>
              {asset.tags?.map((t) => <Tag key={t}>{t}</Tag>)}
              {asset.tagIds?.map((t) => <Tag key={t} color="blue">{t}</Tag>)}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {asset.model && (
        <Card title="模型画像" size="small">
          <Descriptions column={3} size="small">
            <Descriptions.Item label="框架">{asset.model.framework ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="任务">{asset.model.task ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="架构">{asset.model.architecture ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.dataset && (
        <Card title="数据集画像" size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label="格式">{asset.dataset.format ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="模态">{asset.dataset.modality ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.repository && (
        <Card title="仓库信息" size="small">
          <Descriptions column={2} size="small">
            <Descriptions.Item label="仓库名">{asset.repository.fullName}</Descriptions.Item>
            <Descriptions.Item label="链接">
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
        <Card title="Card 投影" size="small">
          {asset.card.untrustedContent && (
            <Alert
              type="warning"
              showIcon
              message="以下内容来自外部仓库，未经信任，请注意安全。"
              style={{ marginBottom: 12 }}
            />
          )}
          {asset.card.sourceCommit && (
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
              来源 Commit: {asset.card.sourceCommit}
            </Typography.Text>
          )}
          {asset.card.readme && (
            <Typography.Paragraph>
              <Typography.Title level={5}>README</Typography.Title>
              <pre style={{ whiteSpace: 'pre-wrap', background: '#f5f5f5', padding: 12, borderRadius: 4 }}>
                {asset.card.readme}
              </pre>
            </Typography.Paragraph>
          )}
          {asset.card.assetYaml && (
            <Typography.Paragraph>
              <Typography.Title level={5}>asset.yaml</Typography.Title>
              <pre style={{ whiteSpace: 'pre-wrap', background: '#f5f5f5', padding: 12, borderRadius: 4 }}>
                {asset.card.assetYaml}
              </pre>
            </Typography.Paragraph>
          )}
        </Card>
      )}

      {assetId && <DiscussionPanel assetId={assetId} />}
    </Flex>
  );
}

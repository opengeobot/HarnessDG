/**
 * 功能: 资产详情页——统一外壳 Tab（Overview|Versions|Files|Preview|Discussions|Lineage|Access|Settings）
 *       + 顶部版本切换器（切换 Version 同步 URL 与版本化 Query Key），对齐 REQ-DST-DETAIL-001。
 * 时间: 2026-07-05，2026-07-12 Wave S 重构
 * 作者: AxeXie
 */
import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
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
  Select,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Tree,
  Typography,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
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
import { DownloadStats } from './DownloadStats';
import { PreviewPanel } from './PreviewPanel';
import { VersionListPanel } from '@/features/version/VersionListPanel';
import { listArtifacts, listVersions } from '@/features/version/api';
import type { ArtifactView, VersionView } from '@/features/version/types';
import type { AssetView, ProvisioningStatus } from './types';

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

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

type TabKey = 'overview' | 'versions' | 'files' | 'preview' | 'discussions' | 'lineage' | 'access' | 'settings';

/** 按 artifact path 构造目录树节点（path/size/mediaType/sha256）。 */
function buildArtifactTree(artifacts: ArtifactView[]): DataNode[] {
  const root: Record<string, { children: Record<string, unknown>; artifact?: ArtifactView }> = {
    __root: { children: {} },
  };
  const ensure = (parts: string[]): { children: Record<string, unknown>; artifact?: ArtifactView } => {
    let node = root.__root;
    for (const part of parts) {
      if (!node.children[part]) {
        node.children[part] = { children: {} };
      }
      node = node.children[part] as typeof node;
    }
    return node;
  };
  for (const art of artifacts) {
    const parts = art.path.split('/').filter(Boolean);
    const node = ensure(parts);
    node.artifact = art;
  }
  const toNodes = (children: Record<string, unknown>): DataNode[] =>
    Object.entries(children)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([name, childRaw]) => {
        const child = childRaw as { children: Record<string, unknown>; artifact?: ArtifactView };
        const sub = toNodes(child.children);
        const title = child.artifact ? (
          <Space size={8} wrap>
            <Typography.Text strong>{name}</Typography.Text>
            <Tag>{formatBytes(child.artifact.size)}</Tag>
            {child.artifact.mediaType && <Tag color="blue">{child.artifact.mediaType}</Tag>}
            <Typography.Text type="secondary" code style={{ fontSize: 11 }}>
              {child.artifact.sha256.slice(0, 12)}
            </Typography.Text>
          </Space>
        ) : (
          <Typography.Text>{name}/</Typography.Text>
        );
        return { key: name + JSON.stringify(child.artifact ?? {}), title, children: sub.length ? sub : undefined, isLeaf: !!child.artifact };
      });
  return toNodes(root.__root.children);
}

export function AssetDetailPage() {
  const { assetId } = useParams<{ assetId: string }>();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();

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

  const versionsQuery = useQuery({
    queryKey: ['versions', assetId],
    queryFn: () => listVersions(assetId!),
    enabled: !!assetId,
  });

  const activeTab = (searchParams.get('tab') as TabKey) ?? 'overview';
  const selectedVersionId = searchParams.get('version') ?? undefined;

  const setParam = (key: string, value: string | undefined) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    }, { replace: true });
  };

  const versions: VersionView[] = versionsQuery.data?.items ?? [];
  const selectedVersion = useMemo(
    () => versions.find((v) => v.versionId === selectedVersionId) ?? versions[0],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [versionsQuery.data, selectedVersionId],
  );
  const effectiveVersionId = selectedVersion?.versionId;

  const artifactsQuery = useQuery({
    queryKey: ['artifacts', assetId, effectiveVersionId],
    queryFn: () => listArtifacts(assetId!, effectiveVersionId!),
    enabled: !!assetId && !!effectiveVersionId && activeTab === 'files',
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    void queryClient.invalidateQueries({ queryKey: ['assets'] });
  };

  const deprecateMut = useMutation({
    mutationFn: () => deprecateAsset(assetId!),
    onSuccess: () => { message.success(t('assets.deprecated')); invalidate(); },
    onError: (err) => {
      if (isApiError(err) && err.httpStatus === 409) {
        message.warning(t('assets.conflictReload'));
        invalidate();
      } else {
        message.error(isApiError(err) ? err.message : t('common.operationFailed'));
      }
    },
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

  if (query.isLoading) return (
    <Flex vertical gap={16}>
      <Skeleton.Input active size="large" style={{ width: 300 }} />
      <Skeleton active paragraph={{ rows: 8 }} />
    </Flex>
  );
  if (query.isError || !query.data) return <Empty description={t('assets.notExist')} />;

  const asset: AssetView = query.data;
  const provStatus = asset.provisioningStatus ?? 'NONE';

  const overviewContent = (
    <Flex vertical gap={16}>
      <Card title={t('assets.detail.basicInfo')}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('assets.detail.coordinate')}>
            <Typography.Text code>
              {asset.coordinate ?? `aih://${asset.namespace}/${asset.type.toLowerCase()}/${asset.name}`}
            </Typography.Text>
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
          <Descriptions.Item label={t('assets.detail.ownerTeam')}>{asset.ownerTeamId ?? '-'}</Descriptions.Item>
          <Descriptions.Item label={t('assets.detail.rowVersion')}>
            <Tag>v{asset.rowVersion}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('common.description')} span={2}>
            {asset.description ?? '-'}
          </Descriptions.Item>
          {asset.aliases && asset.aliases.length > 0 && (
            <Descriptions.Item label={t('assets.detail.aliases')} span={2}>
              <Space size={[0, 4]} wrap>
                {asset.aliases.map(a => <Tag key={a} color="geekblue">{a}</Tag>)}
              </Space>
            </Descriptions.Item>
          )}
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
            <Descriptions.Item label={t('assets.detail.parameterScale')}>{asset.model.parameterScale ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.precision')}>
              {asset.model.precision ? <Tag>{asset.model.precision}</Tag> : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.weightFormat')}>{asset.model.weightFormat ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.runtime')}>{asset.model.runtime ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.sensitivity')}>
              {asset.model.sensitivityCode ? (
                <Tag color={asset.model.sensitivityCode === 'PUBLIC' ? 'green' : asset.model.sensitivityCode === 'SECRET' ? 'red' : 'orange'}>
                  {t(`assets.sensitivity.${asset.model.sensitivityCode}`, asset.model.sensitivityCode)}
                </Tag>
              ) : '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.dataset && (
        <Card title={t('assets.detail.datasetProfile')} size="small">
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label={t('assets.detail.format')}>{asset.dataset.format ?? '-'}</Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.modality')}>{asset.dataset.modality ?? '-'}</Descriptions.Item>
            {asset.dataset.taskCodes && asset.dataset.taskCodes.length > 0 && (
              <Descriptions.Item label={t('assets.detail.taskCodes')}>
                <Space size={[0, 4]} wrap>{asset.dataset.taskCodes.map(c => <Tag key={c}>{c}</Tag>)}</Space>
              </Descriptions.Item>
            )}
            {asset.dataset.modalityCodes && asset.dataset.modalityCodes.length > 0 && (
              <Descriptions.Item label={t('assets.detail.modalityCodes')}>
                <Space size={[0, 4]} wrap>{asset.dataset.modalityCodes.map(c => <Tag key={c}>{c}</Tag>)}</Space>
              </Descriptions.Item>
            )}
            {asset.dataset.formatCodes && asset.dataset.formatCodes.length > 0 && (
              <Descriptions.Item label={t('assets.detail.formatCodes')}>
                <Space size={[0, 4]} wrap>{asset.dataset.formatCodes.map(c => <Tag key={c}>{c}</Tag>)}</Space>
              </Descriptions.Item>
            )}
            {asset.dataset.languageCodes && asset.dataset.languageCodes.length > 0 && (
              <Descriptions.Item label={t('assets.detail.languageCodes')}>
                <Space size={[0, 4]} wrap>{asset.dataset.languageCodes.map(c => <Tag key={c}>{c}</Tag>)}</Space>
              </Descriptions.Item>
            )}
            <Descriptions.Item label={t('assets.detail.sampleCount')}>
              {asset.dataset.sampleCount != null ? asset.dataset.sampleCount.toLocaleString() : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.totalBytes')}>
              {asset.dataset.totalBytes != null ? formatBytes(asset.dataset.totalBytes) : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('assets.detail.sizeBucketCode')}>{asset.dataset.sizeBucketCode ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {asset.card && (
        <Card title={t('assets.detail.cardProjection')} size="small">
          {asset.card.untrustedContent && (
            <Alert type="warning" showIcon message={t('assets.detail.untrustedWarning')} style={{ marginBottom: 12 }} />
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

      {/* DEC-014 Quick Use 快速使用面板 */}
      <Card title={t('assets.deprecation.quickStart')} size="small">
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Typography.Text strong>{t('assets.detail.coordinate')}</Typography.Text>
            <Typography.Paragraph code copyable style={{ marginTop: 4 }}>
              {asset.coordinate ?? `aih://${asset.namespace}/${asset.type.toLowerCase()}/${asset.name}`}
            </Typography.Paragraph>
          </div>
          {asset.repository?.cloneUrl && (
            <div>
              <Typography.Text strong>{t('assets.deprecation.cloneUrl')}</Typography.Text>
              <Typography.Paragraph code copyable style={{ marginTop: 4 }}>
                git clone {asset.repository.cloneUrl}
              </Typography.Paragraph>
            </div>
          )}
          {asset.type === 'DATASET' && (
            <div>
              <Typography.Text strong>{t('assets.deprecation.dvcPull')}</Typography.Text>
              <Typography.Paragraph code copyable style={{ marginTop: 4 }}>
                dvc pull {asset.coordinate ?? `aih://${asset.namespace}/dataset/${asset.name}`}
              </Typography.Paragraph>
            </div>
          )}
          <div>
            <Typography.Text strong>{t('assets.deprecation.aihCli')}</Typography.Text>
            <Typography.Paragraph code copyable style={{ marginTop: 4 }}>
              {asset.type === 'DATASET'
                ? `aih dataset pull ${asset.coordinate ?? `aih://${asset.namespace}/dataset/${asset.name}`}`
                : `aih model pull ${asset.coordinate ?? `aih://${asset.namespace}/model/${asset.name}`}`}
            </Typography.Paragraph>
          </div>
        </Space>
      </Card>
    </Flex>
  );

  const filesContent = (
    <Card title={t('assets.detail.filesTitle')} size="small">
      {!effectiveVersionId ? (
        <Empty description={t('assets.detail.selectVersionFirst')} />
      ) : artifactsQuery.isLoading ? (
        <Flex justify="center" style={{ padding: 24 }}><Skeleton active /></Flex>
      ) : artifactsQuery.isError || !artifactsQuery.data || artifactsQuery.data.length === 0 ? (
        <Empty description={t('assets.detail.noArtifacts')} />
      ) : (
        <Tree
          treeData={buildArtifactTree(artifactsQuery.data)}
          defaultExpandAll
          showLine
          selectable={false}
        />
      )}
    </Card>
  );

  const previewContent = effectiveVersionId ? (
    <PreviewPanel assetId={assetId!} versionId={effectiveVersionId} />
  ) : (
    <Card title={t('version.preview')} size="small">
      <Empty description={t('assets.detail.selectVersionFirst')} />
    </Card>
  );

  const tabItems = [
    { key: 'overview', label: t('assets.detail.tabOverview'), children: overviewContent },
    { key: 'versions', label: t('assets.detail.tabVersions'), children: assetId && <VersionListPanel assetId={assetId} /> },
    { key: 'files', label: t('assets.detail.tabFiles'), children: filesContent },
    { key: 'preview', label: t('assets.detail.tabPreview'), children: previewContent },
    { key: 'discussions', label: t('assets.detail.tabDiscussions'), children: assetId && <DiscussionPanel assetId={assetId} /> },
    {
      key: 'lineage', label: t('assets.detail.tabLineage'),
      children: (
        <Card size="small">
          <Button type="link" onClick={() => navigate(`/assets/${assetId}/lineage`)}>
            {t('assets.detail.lineageLink')}
          </Button>
        </Card>
      ),
    },
    {
      key: 'access', label: t('assets.detail.tabAccess'),
      children: (
        <Card size="small">
          <Button type="link" onClick={() => navigate(`/assets/${assetId}/access`)}>
            {t('assets.detail.accessLink')}
          </Button>
        </Card>
      ),
    },
    {
      key: 'settings', label: t('assets.detail.tabSettings'),
      children: (
        <Card size="small">
          <Button type="link" onClick={() => navigate(`/assets/${assetId}/settings`)}>
            {t('assets.detail.settingsLink')}
          </Button>
        </Card>
      ),
    },
  ];

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
          {assetId && <DownloadStats assetId={assetId} />}
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

      <Card size="small">
        <Flex align="center" gap={12} wrap>
          <Typography.Text strong>{t('assets.detail.versionSwitcher')}</Typography.Text>
          <Select
            style={{ minWidth: 280 }}
            placeholder={t('assets.detail.selectVersion')}
            value={effectiveVersionId}
            loading={versionsQuery.isLoading}
            onChange={(v) => setParam('version', v)}
            options={versions.map((v) => ({
              value: v.versionId,
              label: `${v.version} (${v.status})`,
            }))}
            allowClear
          />
          {selectedVersion && (
            <Space size={6} wrap>
              {selectedVersion.gitTag && <Tag color="success">{selectedVersion.gitTag}</Tag>}
              {selectedVersion.sourceCommit && (
                <Typography.Text code>{selectedVersion.sourceCommit.slice(0, 12)}</Typography.Text>
              )}
              {selectedVersion.manifestDigest && (
                <Typography.Text type="secondary" code style={{ fontSize: 11 }}>
                  {t('assets.detail.manifestDigest')}: {selectedVersion.manifestDigest.slice(0, 12)}
                </Typography.Text>
              )}
            </Space>
          )}
        </Flex>
      </Card>

      <Tabs
        activeKey={activeTab}
        onChange={(k) => setParam('tab', k)}
        items={tabItems}
        destroyInactiveTabPane={false}
      />
    </Flex>
  );
}

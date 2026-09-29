/**
 * 功能: 资产数据预览面板——表格 / Parquet schema / 图片渲染（TASK-P0BR-068）。
 * 时间: 2026-09-29
 * 作者: AxeXie
 */
import { useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Empty, Flex, Spin, Table, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/shared/api';

interface PreviewView {
  previewId: string;
  contentType: string;
  content: string;
  generatedAt: string | null;
}

interface PreviewPanelProps {
  assetId: string;
  versionId?: string;
}

interface ParsedPreview {
  rows?: Record<string, unknown>[];
  truncated?: boolean;
  columnTruncated?: boolean;
  columns?: string[];
  rowGroups?: number;
  note?: string;
  // image 投影分量（后端 PreviewJobHandler note JSON；旧投影缺 artifactId）
  mediaType?: string;
  artifactId?: string;
}

// 与后端 renderPreviewArtifact 白名单一致；SVG 等可执行媒体类型禁止内联渲染
const RENDERABLE_IMAGE_MEDIA_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif']);

function isImageProjection(parsed: ParsedPreview): boolean {
  return (
    (!parsed.rows || parsed.rows.length === 0) &&
    typeof parsed.mediaType === 'string' &&
    parsed.mediaType.startsWith('image/')
  );
}

export function PreviewPanel({ assetId, versionId }: PreviewPanelProps) {
  const { t } = useTranslation();

  const { data, isLoading, isError, error } = useQuery<PreviewView>({
    queryKey: ['preview', assetId, versionId],
    queryFn: () => {
      const params = versionId ? `?versionId=${encodeURIComponent(versionId)}` : '';
      return apiClient.get<PreviewView>(`/assets/${assetId}/previews${params}`);
    },
    enabled: !!assetId,
  });

  if (isLoading) {
    return (
      <Card title={t('version.preview')}>
        <Spin style={{ display: 'block', margin: '40px auto' }} />
      </Card>
    );
  }

  if (isError) {
    return (
      <Card title={t('version.preview')}>
        <Alert
          type="info"
          message={t('version.previewNotAvailable')}
          description={error instanceof Error ? error.message : undefined}
          showIcon
        />
      </Card>
    );
  }

  if (!data) {
    return (
      <Card title={t('version.preview')}>
        <Empty description={t('version.previewNotAvailable')} />
      </Card>
    );
  }

  let parsed: ParsedPreview | null = null;
  try {
    parsed = JSON.parse(data.content) as ParsedPreview;
  } catch {
    parsed = null;
  }

  if (!parsed) {
    return (
      <Card title={t('version.preview')}>
        <Empty description={t('version.previewEmpty')} />
      </Card>
    );
  }

  // 图片投影：note JSON 携带白名单 mediaType + artifactId 时，经服务端代理端点渲染字节流
  if (isImageProjection(parsed)) {
    // 渲染端点要求 versionId 校验 artifact 归属；旧投影缺 artifactId 同样无法渲染
    if (
      !parsed.artifactId ||
      !versionId ||
      !RENDERABLE_IMAGE_MEDIA_TYPES.has(parsed.mediaType as string)
    ) {
      // 合法 Empty + 重新生成引导，不白屏、不裸 JSON
      return (
        <Card title={t('version.preview')}>
          <Empty description={t('version.previewImageStale')} />
        </Card>
      );
    }
    return (
      <ImagePreview
        assetId={assetId}
        versionId={versionId}
        artifactId={parsed.artifactId}
        generatedAt={data.generatedAt}
      />
    );
  }

  // Parquet schema-only metadata（行数据不可读时后端返回 columns/rowGroups/note）
  if ((!parsed.rows || parsed.rows.length === 0) && parsed.columns && parsed.columns.length > 0) {
    return (
      <Card
        title={t('version.preview')}
        extra={
          data.generatedAt && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {new Date(data.generatedAt).toLocaleString()}
            </Typography.Text>
          )
        }
        size="small"
      >
        {parsed.note && (
          <Alert type="info" message={parsed.note} showIcon style={{ marginBottom: 12 }} />
        )}
        <Typography.Text strong>{t('version.previewSchema')}</Typography.Text>
        <Flex wrap gap={8} style={{ marginTop: 8 }}>
          {parsed.columns.map((col) => (
            <Tag key={col} color="blue">{col}</Tag>
          ))}
        </Flex>
        {parsed.rowGroups != null && (
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
            {t('version.previewRowGroups', { count: parsed.rowGroups })}
          </Typography.Text>
        )}
      </Card>
    );
  }

  if (!parsed.rows || parsed.rows.length === 0) {
    return (
      <Card title={t('version.preview')}>
        <Empty description={t('version.previewEmpty')} />
      </Card>
    );
  }

  // 提取列名（取第一行的 key）
  const columns = Object.keys(parsed.rows[0]).map((key) => ({
    title: key,
    dataIndex: key,
    key,
    ellipsis: true,
    render: (value: unknown) => {
      if (value === null || value === undefined) return '-';
      const str = String(value);
      return (
        <Typography.Text style={{ fontSize: 12, maxWidth: 200 }} ellipsis={{ tooltip: str }}>
          {str}
        </Typography.Text>
      );
    },
  }));

  return (
    <Card
      title={t('version.preview')}
      extra={
        data.generatedAt && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(data.generatedAt).toLocaleString()}
          </Typography.Text>
        )
      }
      size="small"
    >
      {parsed.truncated && (
        <Alert
          type="warning"
          message={t('version.previewTruncated')}
          showIcon
          style={{ marginBottom: 12 }}
        />
      )}
      {parsed.columnTruncated && (
        <Alert
          type="warning"
          message={t('version.previewColumnTruncated')}
          showIcon
          style={{ marginBottom: 12 }}
        />
      )}
      <Table
        rowKey={(_, index) => String(index)}
        columns={columns}
        dataSource={parsed.rows}
        pagination={{ pageSize: 20, showSizeChanger: false }}
        size="small"
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}

interface ImagePreviewProps {
  assetId: string;
  versionId: string;
  artifactId: string;
  generatedAt: string | null;
}

/**
 * 图片字节流渲染：blob 取数 → ObjectURL → <img>；卸载或换工件时 revoke。
 * mediaType 以响应头为准（后端已从 dvc-cache 对象记录返回），前端不再信任投影 note 值。
 */
function ImagePreview({ assetId, versionId, artifactId, generatedAt }: ImagePreviewProps) {
  const { t } = useTranslation();
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  const { data, isLoading, isError } = useQuery({
    // 版本化工件 Query Key：切换版本/工件即重新取数，旧 ObjectURL 在 effect cleanup 中 revoke
    queryKey: ['preview-artifact', assetId, versionId, artifactId],
    queryFn: () =>
      apiClient.getBlob(`/assets/${assetId}/previews/artifact`, {
        params: { versionId, artifactId },
      }),
    enabled: !!assetId && !!versionId && !!artifactId,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });

  useEffect(() => {
    // 双重守卫：响应 mediaType 必须仍在白名单内，防止后端配置漂移导致可执行媒体内联
    if (!data || !data.mediaType || !RENDERABLE_IMAGE_MEDIA_TYPES.has(data.mediaType)) {
      setObjectUrl(null);
      return;
    }
    const url = URL.createObjectURL(data.blob);
    setObjectUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [data]);

  const card = (node: ReactNode) => (
    <Card
      title={t('version.preview')}
      extra={
        generatedAt && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(generatedAt).toLocaleString()}
          </Typography.Text>
        )
      }
      size="small"
    >
      {node}
    </Card>
  );

  if (isLoading) {
    return card(<Spin style={{ display: 'block', margin: '40px auto' }} />);
  }

  if (isError) {
    return card(<Alert type="warning" message={t('version.previewImageError')} showIcon />);
  }

  // 响应 mediaType 不在白名单：拒绝内联渲染
  if (data && (!data.mediaType || !RENDERABLE_IMAGE_MEDIA_TYPES.has(data.mediaType))) {
    return card(<Alert type="warning" message={t('version.previewImageError')} showIcon />);
  }

  if (!objectUrl) {
    // data 已返回但 effect 尚未落地 ObjectURL（同帧内瞬态）
    return card(<Spin style={{ display: 'block', margin: '40px auto' }} />);
  }

  return card(
    <img
      src={objectUrl}
      alt={t('version.preview')}
      style={{ maxWidth: '100%', maxHeight: 480, display: 'block', margin: '0 auto' }}
    />,
  );
}

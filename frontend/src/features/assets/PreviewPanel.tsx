/**
 * 功能: 资产数据预览面板——表格 / Parquet schema / 图片渲染（TASK-P0BR-068）+
 *       Split 选择器与统计卡片（TASK-P0BR-078，统计来自服务端投影，前端不重算）。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
import { useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Empty, Flex, Segmented, Spin, Statistic, Table, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { apiClient } from '@/shared/api';

/** 单 Split 统计（对齐契约 PreviewSplitStat；null = 未统计，不得当 0 处理）。 */
interface PreviewSplitStat {
  name: string;
  rowCount?: number | null;
  fileCount?: number | null;
  sizeBytes?: number | null;
  columns?: string[] | null;
  truncated?: boolean | null;
  /** 该 Split 的脱敏样例切片（旧投影为 null/缺失 → 回退主样例，不伪造）。 */
  sampleRows?: Record<string, unknown>[] | null;
}

interface PreviewStatTotals {
  rowCount?: number | null;
  fileCount?: number | null;
  sizeBytes?: number | null;
  truncated?: boolean | null;
}

interface PreviewStatsSource {
  versionId?: string | null;
  sourceCommit?: string | null;
  generatedAt?: string | null;
}

interface PreviewView {
  previewId: string;
  contentType: string;
  content: string;
  generatedAt: string | null;
  splits?: PreviewSplitStat[] | null;
  totals?: PreviewStatTotals | null;
  generatedFrom?: PreviewStatsSource | null;
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

/** 统计是否未知：三分量全 null/缺失 = 旧投影或无表格工件（AC-DST-PRE-002，未知≠0）。 */
function isStatsUnknown(view: PreviewView): boolean {
  return !view.splits && !view.totals && !view.generatedFrom;
}

function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

interface StatsSectionProps {
  view: PreviewView;
  activeSplit?: PreviewSplitStat | null;
}

/**
 * 统计卡片（TASK-P0BR-078）：优先展示选中 Split 的统计，未选中 Split 时展示 totals。
 * 数值缺失一律渲染「-」，不得伪造零值；未知态显示重新生成引导。
 */
function StatsSection({ view, activeSplit }: StatsSectionProps) {
  const { t } = useTranslation();

  if (isStatsUnknown(view)) {
    return (
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message={t('version.previewStatsUnknown')}
      />
    );
  }

  const rowCount = activeSplit ? activeSplit.rowCount : view.totals?.rowCount;
  const fileCount = activeSplit ? activeSplit.fileCount : view.totals?.fileCount;
  const sizeBytes = activeSplit ? activeSplit.sizeBytes : view.totals?.sizeBytes;
  const truncated = Boolean(activeSplit?.truncated ?? view.totals?.truncated);
  const columnCount = activeSplit?.columns?.length ?? null;
  const source = view.generatedFrom;

  return (
    <div style={{ marginBottom: 12 }}>
      <Flex gap={24} wrap>
        <Statistic
          title={t('version.previewStatsRows')}
          value={rowCount ?? '-'}
          valueStyle={{ fontSize: 18 }}
        />
        <Statistic
          title={t('version.previewStatsFiles')}
          value={fileCount ?? '-'}
          valueStyle={{ fontSize: 18 }}
        />
        <Statistic
          title={t('version.previewStatsSize')}
          value={formatBytes(sizeBytes)}
          valueStyle={{ fontSize: 18 }}
        />
        {columnCount !== null && (
          <Statistic
            title={t('version.previewStatsColumns')}
            value={columnCount}
            valueStyle={{ fontSize: 18 }}
          />
        )}
        {truncated && (
          <Tag color="orange" style={{ alignSelf: 'center' }}>
            {t('version.previewStatsTruncated')}
          </Tag>
        )}
      </Flex>
      {(source?.generatedAt || source?.sourceCommit || source?.versionId) && (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 12 }}>
          {t('version.previewStatsSource', {
            generatedAt: source.generatedAt ? new Date(source.generatedAt).toLocaleString() : '-',
            version: source.versionId ?? '-',
            commit: source.sourceCommit ? source.sourceCommit.slice(0, 8) : '-',
          })}
        </Typography.Text>
      )}
    </div>
  );
}

export function PreviewPanel({ assetId, versionId }: PreviewPanelProps) {
  const { t } = useTranslation();
  const [splitName, setSplitName] = useState<string | null>(null);

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

  const splits = data.splits ?? [];
  // 默认选中首个 Split；用户选择仅在 Split 仍存在时生效
  const effectiveSplitName =
    splitName && splits.some((s) => s.name === splitName)
      ? splitName
      : (splits[0]?.name ?? null);
  const activeSplit = splits.find((s) => s.name === effectiveSplitName) ?? null;

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
        <StatsSection view={data} activeSplit={activeSplit} />
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

  // 选中 Split 的脱敏样例切片优先；缺失（旧投影/该 Split 无样例）回退主样例，不伪造
  const sampleRows =
    activeSplit?.sampleRows && activeSplit.sampleRows.length > 0
      ? activeSplit.sampleRows
      : parsed.rows;

  if (!sampleRows || sampleRows.length === 0) {
    return (
      <Card title={t('version.preview')}>
        <Empty description={t('version.previewEmpty')} />
      </Card>
    );
  }

  // 提取列名（取当前展示样例首行的 key）
  const columns = Object.keys(sampleRows[0]).map((key) => ({
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
      <StatsSection view={data} activeSplit={activeSplit} />
      {splits.length > 1 && (
        <Flex align="center" gap={8} style={{ marginBottom: 12 }}>
          <Typography.Text strong style={{ fontSize: 12 }}>
            {t('version.previewSplitLabel')}
          </Typography.Text>
          <Segmented
            value={effectiveSplitName ?? undefined}
            options={splits.map((s) => ({ label: s.name, value: s.name }))}
            onChange={(value) => setSplitName(String(value))}
          />
        </Flex>
      )}
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
        dataSource={sampleRows}
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

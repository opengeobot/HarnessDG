/**
 * 功能: 资产数据预览面板——从后端获取已生成的预览内容并渲染为表格。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
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
  rows: Record<string, unknown>[];
  truncated?: boolean;
  columns?: string[];
  rowGroups?: number;
  note?: string;
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

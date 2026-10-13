/**
 * 功能: 版本工件文本内容面板——Files Tab 选中叶子后经服务端内容代理端点取字节流，
 *       Blob.text() 解码以 <pre> 纯文本渲染（TASK-P0BR-077）。内容为不可信数据，
 *       绝不使用 dangerouslySetInnerHTML / Markdown 语义渲染；超限/白名单外/失败态
 *       给出对应文案与下载引导（复用既有下载票据通道）。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Flex, Skeleton, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { apiClient, isApiError } from '@/shared/api';
import { issueDownloadTicket } from '@/features/version/api';
import type { ArtifactView } from '@/features/version/types';

interface ArtifactContentPanelProps {
  assetId: string;
  versionId: string;
  artifact: ArtifactView;
}

export function ArtifactContentPanel({ assetId, versionId, artifact }: ArtifactContentPanelProps) {
  const { t } = useTranslation();
  const [downloadError, setDownloadError] = useState(false);

  // 版本化工件 Query Key：切换版本/工件即重新取数；卸载时 react-query 自然释放缓存引用
  const query = useQuery({
    queryKey: ['artifact-content', assetId, versionId, artifact.artifactId],
    queryFn: async () => {
      const { blob } = await apiClient.getBlob(
        `/assets/${assetId}/versions/${versionId}/artifacts/${artifact.artifactId}/content`,
      );
      // 以响应文本原样呈现；解码异常（非法字节序列）同样进入错误态而非白屏
      return blob.text();
    },
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });

  const handleDownload = async () => {
    setDownloadError(false);
    try {
      const ticket = await issueDownloadTicket(versionId, artifact.artifactId);
      if (ticket.method === 'PRESIGNED_URL' && ticket.presignedUrl) {
        window.open(ticket.presignedUrl, '_blank');
      } else {
        setDownloadError(true);
      }
    } catch {
      setDownloadError(true);
    }
  };

  const downloadGuide = (
    <Flex vertical align="start" gap={8} style={{ marginTop: 12 }}>
      <Button size="small" onClick={() => void handleDownload()}>
        {t('assets.filesContentDownload')}
      </Button>
      {downloadError && (
        <Typography.Text type="secondary">{t('assets.filesContentDownloadFailed')}</Typography.Text>
      )}
    </Flex>
  );

  const header = (
    <Typography.Text type="secondary" code style={{ fontSize: 12 }}>
      {artifact.path}
    </Typography.Text>
  );

  if (query.isLoading) {
    return (
      <div>
        {header}
        <Skeleton active paragraph={{ rows: 3 }} style={{ marginTop: 8 }} />
      </div>
    );
  }

  if (query.isError) {
    const code = isApiError(query.error) ? query.error.code : undefined;
    const message =
      code === 'PREVIEW_TOO_LARGE'
        ? t('assets.filesContentTooLarge')
        : code === 'PREVIEW_UNSUPPORTED_FORMAT'
          ? t('assets.filesContentUnsupported')
          : t('assets.filesContentError');
    return (
      <div>
        {header}
        <Alert type="warning" message={message} showIcon style={{ marginTop: 8 }} />
        {downloadGuide}
      </div>
    );
  }

  const content = query.data ?? '';
  if (!content) {
    return (
      <div>
        {header}
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('assets.filesContentEmpty')} style={{ marginTop: 8 }} />
      </div>
    );
  }

  return (
    <div>
      {header}
      <pre
        data-testid="artifact-content-pre"
        style={{
          marginTop: 8,
          marginBottom: 0,
          padding: 12,
          maxHeight: 480,
          overflow: 'auto',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          background: 'rgba(150, 150, 150, 0.08)',
          borderRadius: 6,
          fontSize: 12,
        }}
      >
        {content}
      </pre>
    </div>
  );
}

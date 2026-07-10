/**
 * 功能: 资产下载量统计徽章——基于 audit_log 下载授权事件聚合（DEC-016）。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
import { useQuery } from '@tanstack/react-query';
import { Badge, Tooltip } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { apiClient } from '@/shared/api';

interface AssetDownloadStats {
  assetId: string;
  totalDownloads: number;
}

interface DownloadStatsProps {
  assetId: string;
}

export function DownloadStats({ assetId }: DownloadStatsProps) {
  const query = useQuery({
    queryKey: ['assetDownloadStats', assetId],
    queryFn: () => apiClient.get<AssetDownloadStats>(`/assets/${assetId}/stats`),
    retry: false,
    staleTime: 60_000,
  });

  if (query.isError || !query.data || query.data.totalDownloads === 0) {
    return null;
  }

  return (
    <Tooltip title={`${query.data.totalDownloads} authorized downloads`}>
      <Badge
        count={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <DownloadOutlined />
            {query.data.totalDownloads}
          </span>
        }
        style={{ backgroundColor: '#1677ff' }}
      />
    </Tooltip>
  );
}

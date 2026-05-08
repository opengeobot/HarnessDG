/**
 * 功能：周报详情页 - 展示周报内容（标题、时间范围、Markdown、指标快照）
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import { useEffect, useState } from 'react';
import { Card, Descriptions, Spin, Typography, Empty } from 'antd';
import { useTranslation } from 'react-i18next';
import { reportApi } from '@/services/reportApi';

const { Title, Paragraph } = Typography;

interface WeeklyReport {
  id: number;
  title: string;
  reportType: string;
  startDate: string;
  endDate: string;
  creator: string;
  createdAt: string;
  status: string;
  markdownContent?: string;
  metricsSnapshot?: Record<string, any>;
}

export default function ReportView({ report: propReport }: { report?: WeeklyReport }) {
  const { t } = useTranslation(['report', 'common']);
  const [detail, setDetail] = useState<WeeklyReport | null>(propReport || null);
  const [loading, setLoading] = useState(!propReport);
  const reportId = propReport?.id;

  useEffect(() => {
    if (propReport) {
      setDetail(propReport);
      setLoading(false);
      return;
    }
    const loadDetail = async () => {
      setLoading(true);
      try {
        // If used standalone, fetch from API
        const res: any = await reportApi.getReport(0);
        setDetail(res.data);
      } finally {
        setLoading(false);
      }
    };
    if (!propReport) loadDetail();
  }, [propReport, reportId]);

  if (loading) return <Spin />;
  if (!detail) return <Empty description={t('report.no_data', '无数据')} />;

  return (
    <Spin spinning={loading}>
      <Descriptions column={2} bordered style={{ marginBottom: 24 }}>
        <Descriptions.Item label={t('report.title')}>{detail.title}</Descriptions.Item>
        <Descriptions.Item label={t('report.report_type')}>{detail.reportType}</Descriptions.Item>
        <Descriptions.Item label={t('report.date_range')}>{detail.startDate} ~ {detail.endDate}</Descriptions.Item>
        <Descriptions.Item label={t('report.creator')}>{detail.creator}</Descriptions.Item>
        <Descriptions.Item label={t('report.created_at')}>{detail.createdAt}</Descriptions.Item>
      </Descriptions>

      <Card title={t('report.content', '报告内容')} style={{ marginBottom: 16 }}>
        {detail.markdownContent ? (
          <pre style={{
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontFamily: 'inherit',
            fontSize: 14,
            lineHeight: 1.8,
          }}>
            {detail.markdownContent}
          </pre>
        ) : (
          <Empty description={t('report.no_content', '暂无报告内容')} />
        )}
      </Card>

      {detail.metricsSnapshot && Object.keys(detail.metricsSnapshot).length > 0 && (
        <Card title={t('report.metrics_snapshot', '指标快照')}>
          <pre style={{
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontFamily: 'monospace',
            fontSize: 13,
            background: '#f5f5f5',
            padding: 12,
            borderRadius: 4,
          }}>
            {JSON.stringify(detail.metricsSnapshot, null, 2)}
          </pre>
        </Card>
      )}
    </Spin>
  );
}

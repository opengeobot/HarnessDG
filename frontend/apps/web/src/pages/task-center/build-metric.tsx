/**
 * 功能：建指标向导页面（占位）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography } from 'antd';
import { useTranslation } from 'react-i18next';

function BuildMetric() {
  const { t } = useTranslation('task');

  return (
    <div>
      <Typography.Title level={3}>{t('card.build_metric.title')}</Typography.Title>
      <Typography.Text type="secondary">
        {t('card.build_metric.description')}
      </Typography.Text>
      {/* Sprint 3 实现完整向导 */}
    </div>
  );
}

export default BuildMetric;

/**
 * 功能：治理中心页面
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography, Card, Empty } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export default function Governance() {
  const { t } = useTranslation('navigation');

  return (
    <div>
      <Typography.Title level={2}>{t('menu.governance')}</Typography.Title>
      <Card>
        <Empty
          image={<SafetyCertificateOutlined style={{ fontSize: 64, color: '#bfbfbf' }} />}
          description={t('menu.governance') + ' - 功能建设中'}
        />
      </Card>
    </div>
  );
}

/**
 * 功能：异常诊断页面
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography, Card, Empty } from 'antd';
import { BugOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export default function Diagnosis() {
  const { t } = useTranslation('navigation');

  return (
    <div>
      <Typography.Title level={2}>{t('menu.diagnosis')}</Typography.Title>
      <Card>
        <Empty
          image={<BugOutlined style={{ fontSize: 64, color: '#bfbfbf' }} />}
          description={t('menu.diagnosis') + ' - 功能建设中'}
        />
      </Card>
    </div>
  );
}

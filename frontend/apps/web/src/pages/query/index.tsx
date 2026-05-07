/**
 * 功能：语义查询页面
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography, Card, Empty } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export default function Query() {
  const { t } = useTranslation('navigation');

  return (
    <div>
      <Typography.Title level={2}>{t('menu.query')}</Typography.Title>
      <Card>
        <Empty
          image={<MessageOutlined style={{ fontSize: 64, color: '#bfbfbf' }} />}
          description={t('menu.query') + ' - 功能建设中'}
        />
      </Card>
    </div>
  );
}

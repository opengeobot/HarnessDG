/**
 * 功能：AI问数页面（占位）
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Typography } from 'antd';
import { useTranslation } from 'react-i18next';

function AskData() {
  const { t } = useTranslation('task');

  return (
    <div>
      <Typography.Title level={3}>{t('card.ask_data.title')}</Typography.Title>
      <Typography.Text type="secondary">
        {t('card.ask_data.description')}
      </Typography.Text>
      {/* Sprint 4 实现完整聊天界面 */}
    </div>
  );
}

export default AskData;

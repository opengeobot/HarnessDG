/**
 * 功能: 404 占位页面。文案全部走 i18n，支持运行时中英文切换。
 * 时间: 2026-09-22
 * 作者: AxeXie
 */
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  return (
    <Result
      status="404"
      title={t('notFound.title')}
      subTitle={t('notFound.subTitle')}
      extra={
        <Button type="primary" onClick={() => navigate('/assets')}>
          {t('notFound.backToCatalog')}
        </Button>
      }
    />
  );
}

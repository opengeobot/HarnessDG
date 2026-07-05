/**
 * 功能: 访问与凭据占位页面 (成员、Agent、Token、Scope)。骨架阶段无业务逻辑。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { PlaceholderPage } from '@/shared/components';
import { useDocumentTitle } from '@/shared/hooks';
import { useTranslation } from 'react-i18next';

export function AccessPage() {
  const { t } = useTranslation();
  useDocumentTitle(t('access.title'));
  return (
    <PlaceholderPage
      title={t('access.title')}
      description={t('access.description')}
    />
  );
}

/**
 * 功能: 设置文档标题的通用 Hook
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export function useDocumentTitle(title?: string): void {
  const { t, i18n } = useTranslation();
  const baseTitle = t('app.title');
  useEffect(() => {
    document.title = title ? `${title} · ${baseTitle}` : baseTitle;
  }, [title, baseTitle, i18n.language]);
}

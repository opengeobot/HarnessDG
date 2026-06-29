/**
 * 功能: 设置文档标题的通用 Hook
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useEffect } from 'react';

const BASE_TITLE = 'AI 资产管理平台';

export function useDocumentTitle(title?: string): void {
  useEffect(() => {
    document.title = title ? `${title} · ${BASE_TITLE}` : BASE_TITLE;
  }, [title]);
}

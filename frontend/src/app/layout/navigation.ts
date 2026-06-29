/**
 * 功能: 主导航配置。集中定义侧边导航项、路由路径与所需 Scope，
 *       供布局与路由共享，避免散落硬编码。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import type { Scope } from '@/shared/types';

export interface NavItem {
  /** 路由路径（相对根） */
  key: string;
  /** 导航标题 */
  label: string;
  /** 访问所需 Scope */
  requiredScopes?: Scope[];
}

export const NAV_ITEMS: readonly NavItem[] = [
  { key: '/assets', label: '资产目录', requiredScopes: ['asset:read'] },
  { key: '/version', label: '版本中心', requiredScopes: ['asset:read'] },
  { key: '/upload', label: '上传中心', requiredScopes: ['asset:write'] },
  { key: '/access', label: '访问与凭据', requiredScopes: ['asset:admin'] },
  {
    key: '/integrations',
    label: 'Agent 接入',
    requiredScopes: ['asset:admin'],
  },
  { key: '/admin', label: '管理中心', requiredScopes: ['asset:admin'] },
];

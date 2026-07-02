/**
 * 功能: 主导航配置。集中定义侧边导航项（含管理中心子菜单）、路由路径与所需 Scope，
 *       供布局按权限过滤显示，避免散落硬编码。真实拦截在后端。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { Scope } from '@/shared/types';

export interface NavItem {
  /** 路由路径（相对根） */
  key: string;
  /** 导航标题 */
  label: string;
  /** 访问所需 Scope（全部满足才显示） */
  requiredScopes?: Scope[];
  /** 子导航项 */
  children?: NavItem[];
}

export const NAV_ITEMS: readonly NavItem[] = [
  { key: '/assets', label: '资产目录', requiredScopes: ['asset:read'] },
  { key: '/version', label: '版本中心', requiredScopes: ['asset:read'] },
  { key: '/upload', label: '上传中心', requiredScopes: ['asset:write'] },
  { key: '/access', label: '访问与凭据', requiredScopes: ['authorization:read'] },
  { key: '/integrations', label: 'Agent 接入', requiredScopes: ['authorization:read'] },
  {
    key: '/admin',
    label: '管理中心',
    children: [
      { key: '/admin/users', label: '用户管理', requiredScopes: ['user:read'] },
      { key: '/admin/agents', label: 'Agent 管理', requiredScopes: ['authorization:read'] },
      { key: '/admin/organizations', label: '组织管理', requiredScopes: ['project:view'] },
      { key: '/admin/projects', label: '项目管理', requiredScopes: ['project:view'] },
      { key: '/admin/roles', label: '角色管理', requiredScopes: ['authorization:read'] },
      { key: '/admin/permissions', label: '权限清单', requiredScopes: ['authorization:read'] },
      { key: '/admin/dictionaries', label: '字典管理', requiredScopes: ['dictionary:read'] },
      { key: '/admin/tags', label: '标签管理', requiredScopes: ['tag:read'] },
      { key: '/admin/configurations', label: '配置管理', requiredScopes: ['system:configure'] },
      { key: '/admin/jobs', label: '任务管理', requiredScopes: ['job:read'] },
      { key: '/admin/audit-logs', label: '审计日志', requiredScopes: ['audit:read'] },
      { key: '/admin/notifications', label: '通知中心', requiredScopes: ['notification:read'] },
      { key: '/admin/dependencies', label: '系统依赖', requiredScopes: ['system:observe'] },
    ],
  },
];

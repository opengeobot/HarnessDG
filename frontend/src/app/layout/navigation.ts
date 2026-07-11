/**
 * 功能: 主导航配置。集中定义侧边导航项（含管理中心子菜单）、路由路径与所需 Scope，
 *       供布局按权限过滤显示，避免散落硬编码。真实拦截在后端。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { Scope } from '@/shared/types';
import i18n from '@/shared/i18n';

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
  { key: '/assets', label: i18n.t('nav.assetCatalog'), requiredScopes: ['asset:read'] },
  { key: '/version', label: i18n.t('nav.versionCenter'), requiredScopes: ['asset:read'] },
  { key: '/review', label: i18n.t('nav.reviewCenter'), requiredScopes: ['asset:review'] },
  { key: '/upload', label: i18n.t('nav.uploadCenter'), requiredScopes: ['asset:write'] },
  { key: '/access', label: i18n.t('nav.accessCredentials'), requiredScopes: ['authorization:read'] },
  { key: '/integrations', label: i18n.t('nav.agentIntegration'), requiredScopes: ['authorization:read'] },
  {
    key: '/admin',
    label: i18n.t('nav.adminCenter'),
    children: [
      { key: '/admin/users', label: i18n.t('nav.userManagement'), requiredScopes: ['user:read'] },
      { key: '/admin/agents', label: i18n.t('nav.agentManagement'), requiredScopes: ['authorization:read'] },
      { key: '/admin/organizations', label: i18n.t('nav.orgManagement'), requiredScopes: ['project:view'] },
      { key: '/admin/projects', label: i18n.t('nav.projectManagement'), requiredScopes: ['project:view'] },
      { key: '/admin/roles', label: i18n.t('nav.roleManagement'), requiredScopes: ['authorization:read'] },
      { key: '/admin/permissions', label: i18n.t('nav.permissionList'), requiredScopes: ['authorization:read'] },
      { key: '/admin/dictionaries', label: i18n.t('nav.dictManagement'), requiredScopes: ['dictionary:read'] },
      { key: '/admin/tags', label: i18n.t('nav.tagManagement'), requiredScopes: ['tag:read'] },
      { key: '/admin/configurations', label: i18n.t('nav.configManagement'), requiredScopes: ['system:configure'] },
      { key: '/admin/jobs', label: i18n.t('nav.jobManagement'), requiredScopes: ['job:read'] },
      { key: '/admin/idempotency', label: i18n.t('nav.idempotency'), requiredScopes: ['job:read'] },
      { key: '/admin/role-bindings', label: i18n.t('nav.roleBindings'), requiredScopes: ['authorization:read'] },
      { key: '/admin/resource-acls', label: i18n.t('nav.resourceAcls'), requiredScopes: ['authorization:read'] },
      { key: '/admin/alerts', label: i18n.t('nav.alerts'), requiredScopes: ['system:observe'] },
      { key: '/admin/audit-logs', label: i18n.t('nav.auditLogs'), requiredScopes: ['audit:read'] },
      { key: '/admin/notifications', label: i18n.t('nav.notificationCenter'), requiredScopes: ['notification:read'] },
      { key: '/admin/dependencies', label: i18n.t('nav.systemDeps'), requiredScopes: ['system:observe'] },
    ],
  },
];

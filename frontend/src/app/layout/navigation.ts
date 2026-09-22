/**
 * 功能: 主导航配置。集中定义侧边导航项（含管理中心子菜单）、路由路径与所需 Scope，
 *       供布局按权限过滤显示，避免散落硬编码。真实拦截在后端。
 *       导航标题以 i18n key 存储，由组件在渲染时求值，使运行时语言切换能即时生效。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { Scope } from '@/shared/types';

export interface NavItem {
  /** 路由路径（相对根） */
  key: string;
  /** 导航标题的 i18n key（渲染时求值，支持运行时切换语言） */
  labelKey: string;
  /** 访问所需 Scope（全部满足才显示） */
  requiredScopes?: Scope[];
  /** 子导航项 */
  children?: NavItem[];
}

export const NAV_ITEMS: readonly NavItem[] = [
  { key: '/assets', labelKey: 'nav.assetCatalog', requiredScopes: ['asset:read'] },
  { key: '/assets/new', labelKey: 'nav.createAsset', requiredScopes: ['asset:create'] },
  { key: '/version', labelKey: 'nav.versionCenter', requiredScopes: ['asset:read'] },
  { key: '/review', labelKey: 'nav.reviewCenter', requiredScopes: ['asset:manage'] },
  { key: '/upload', labelKey: 'nav.uploadCenter', requiredScopes: ['asset:manage'] },
  { key: '/access', labelKey: 'nav.accessCredentials', requiredScopes: ['authorization:read'] },
  { key: '/integrations', labelKey: 'nav.agentIntegration', requiredScopes: ['authorization:read'] },
  {
    key: '/admin',
    labelKey: 'nav.adminCenter',
    children: [
      { key: '/admin/users', labelKey: 'nav.userManagement', requiredScopes: ['user:read'] },
      { key: '/admin/agents', labelKey: 'nav.agentManagement', requiredScopes: ['authorization:read'] },
      { key: '/admin/organizations', labelKey: 'nav.orgManagement', requiredScopes: ['project:view'] },
      { key: '/admin/teams', labelKey: 'nav.teamManagement', requiredScopes: ['project:view'] },
      { key: '/admin/projects', labelKey: 'nav.projectManagement', requiredScopes: ['project:view'] },
      { key: '/admin/roles', labelKey: 'nav.roleManagement', requiredScopes: ['authorization:read'] },
      { key: '/admin/role-bindings', labelKey: 'nav.roleBindings', requiredScopes: ['authorization:read'] },
      { key: '/admin/resource-acls', labelKey: 'nav.resourceAcls', requiredScopes: ['authorization:read'] },
      { key: '/admin/permissions', labelKey: 'nav.permissionList', requiredScopes: ['authorization:read'] },
      { key: '/admin/dictionaries', labelKey: 'nav.dictManagement', requiredScopes: ['dictionary:read'] },
      { key: '/admin/tags', labelKey: 'nav.tagManagement', requiredScopes: ['tag:read'] },
      { key: '/admin/configurations', labelKey: 'nav.configManagement', requiredScopes: ['system:configure'] },
      { key: '/admin/jobs', labelKey: 'nav.jobManagement', requiredScopes: ['job:read'] },
      { key: '/admin/idempotency', labelKey: 'nav.idempotency', requiredScopes: ['job:read'] },
      { key: '/admin/alerts', labelKey: 'nav.alerts', requiredScopes: ['system:observe'] },
      { key: '/admin/audit-logs', labelKey: 'nav.auditLogs', requiredScopes: ['audit:read'] },
      { key: '/admin/notifications', labelKey: 'nav.notificationCenter', requiredScopes: ['notification:read'] },
      { key: '/admin/dependencies', labelKey: 'nav.systemDeps', requiredScopes: ['system:observe'] },
    ],
  },
];

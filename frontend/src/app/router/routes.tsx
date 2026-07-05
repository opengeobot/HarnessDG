/**
 * 功能: 应用路由配置。集中声明路由表，结合 RouteGuard 表达路由级权限点。
 *       新增 /login（认证流程外壳外）、/profile 与 /admin/* 管理页面，均由真实权限守卫。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '@/app/layout/AppLayout';
import { RouteGuard } from '@/app/permission';
import { AssetsPage, AssetDetailPage } from '@/features/assets';
import { VersionPage } from '@/features/version';
import { UploadPage } from '@/features/upload';
import { AccessPage } from '@/features/access';
import { IntegrationsPage } from '@/features/integrations';
import { LoginPage, ProfilePage } from '@/features/auth';
import {
  AgentsPage,
  AuditLogsPage,
  ConfigurationsPage,
  DependenciesPage,
  DictionariesPage,
  JobsPage,
  NotificationsPage,
  OrganizationsPage,
  PermissionsPage,
  ProjectsPage,
  RolesPage,
  TagsPage,
  UsersPage,
} from '@/features/admin';
import type { Scope } from '@/shared/types';
import { NotFoundPage } from './NotFoundPage';

/** 生成受权限守卫的路由 element */
function guarded(element: ReactNode, requiredScopes?: Scope[]) {
  return <RouteGuard requiredScopes={requiredScopes}>{element}</RouteGuard>;
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/assets" replace /> },
      { path: 'assets', element: guarded(<AssetsPage />, ['asset:read']) },
      { path: 'assets/:assetId', element: guarded(<AssetDetailPage />, ['asset:read']) },
      { path: 'version', element: guarded(<VersionPage />, ['asset:read']) },
      { path: 'upload', element: guarded(<UploadPage />, ['asset:write']) },
      { path: 'access', element: guarded(<AccessPage />, ['authorization:read']) },
      { path: 'integrations', element: guarded(<IntegrationsPage />, ['authorization:read']) },
      { path: 'profile', element: guarded(<ProfilePage />) },
      {
        path: 'admin',
        children: [
          { index: true, element: <Navigate to="/admin/users" replace /> },
          { path: 'users', element: guarded(<UsersPage />, ['user:read']) },
          { path: 'agents', element: guarded(<AgentsPage />, ['authorization:read']) },
          { path: 'organizations', element: guarded(<OrganizationsPage />, ['project:view']) },
          { path: 'projects', element: guarded(<ProjectsPage />, ['project:view']) },
          { path: 'roles', element: guarded(<RolesPage />, ['authorization:read']) },
          { path: 'permissions', element: guarded(<PermissionsPage />, ['authorization:read']) },
          { path: 'dictionaries', element: guarded(<DictionariesPage />, ['dictionary:read']) },
          { path: 'tags', element: guarded(<TagsPage />, ['tag:read']) },
          { path: 'configurations', element: guarded(<ConfigurationsPage />, ['system:configure']) },
          { path: 'jobs', element: guarded(<JobsPage />, ['job:read']) },
          { path: 'audit-logs', element: guarded(<AuditLogsPage />, ['audit:read']) },
          { path: 'notifications', element: guarded(<NotificationsPage />, ['notification:read']) },
          { path: 'dependencies', element: guarded(<DependenciesPage />, ['system:observe']) },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

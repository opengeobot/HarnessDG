/**
 * 功能: 应用路由配置。集中声明路由表，结合 RouteGuard 表达路由级权限点。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '@/app/layout/AppLayout';
import { RouteGuard } from '@/app/permission';
import { AssetsPage } from '@/features/assets';
import { VersionPage } from '@/features/version';
import { UploadPage } from '@/features/upload';
import { AccessPage } from '@/features/access';
import { IntegrationsPage } from '@/features/integrations';
import { AdminPage } from '@/features/admin';
import { NotFoundPage } from './NotFoundPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/assets" replace /> },
      {
        path: 'assets',
        element: (
          <RouteGuard requiredScopes={['asset:read']}>
            <AssetsPage />
          </RouteGuard>
        ),
      },
      {
        path: 'version',
        element: (
          <RouteGuard requiredScopes={['asset:read']}>
            <VersionPage />
          </RouteGuard>
        ),
      },
      {
        path: 'upload',
        element: (
          <RouteGuard requiredScopes={['asset:write']}>
            <UploadPage />
          </RouteGuard>
        ),
      },
      {
        path: 'access',
        element: (
          <RouteGuard requiredScopes={['asset:admin']}>
            <AccessPage />
          </RouteGuard>
        ),
      },
      {
        path: 'integrations',
        element: (
          <RouteGuard requiredScopes={['asset:admin']}>
            <IntegrationsPage />
          </RouteGuard>
        ),
      },
      {
        path: 'admin',
        element: (
          <RouteGuard requiredScopes={['asset:admin']}>
            <AdminPage />
          </RouteGuard>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

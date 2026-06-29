/**
 * 功能: 根应用组件。组合全局 Providers 与路由。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { RouterProvider } from 'react-router-dom';
import { AppProviders } from '@/app/providers/AppProviders';
import { router } from '@/app/router';

export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  );
}

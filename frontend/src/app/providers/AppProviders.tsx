/**
 * 功能: 全局 Providers。组合 QueryClientProvider、Ant Design ConfigProvider、
 *       AuthProvider（认证会话）与 PermissionProvider（真实 Scope），集中管理全局上下文。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import { useState, type ReactNode } from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AuthProvider } from '@/app/auth';
import { PermissionProvider } from '@/app/permission';
import '@/shared/i18n';

function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
    },
  });
}

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  const { i18n } = useTranslation();
  const antdLocale = i18n.language === 'en' ? enUS : zhCN;

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={antdLocale}>
        <AntdApp>
          <AuthProvider>
            <PermissionProvider>{children}</PermissionProvider>
          </AuthProvider>
        </AntdApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
}

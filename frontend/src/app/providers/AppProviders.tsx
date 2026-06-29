/**
 * 功能: 全局 Providers。组合 QueryClientProvider、Ant Design ConfigProvider、
 *       PermissionProvider，集中管理服务端状态与全局上下文。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import { useState, type ReactNode } from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PermissionProvider } from '@/app/permission';

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
  // 每个应用实例持有单一 QueryClient，避免重渲染重建缓存
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN}>
        <PermissionProvider>{children}</PermissionProvider>
      </ConfigProvider>
    </QueryClientProvider>
  );
}

/**
 * 功能：App 根组件，集成 Provider 层
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { ConfigProvider, App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { useEffect } from 'react';
import { lightTheme } from '@harnessdg/design-tokens';
import { initI18n } from '@harnessdg/i18n';
import AppRoutes from './routes';
import { preloadDictionaries } from '@/stores/dictStore';

// 初始化 i18n
initI18n();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60 * 1000,
      retry: 2,
      refetchOnWindowFocus: false,
    },
  },
});

function App() {
  useEffect(() => {
    // 预加载高频字典分组，降低后续页面的首屏延迟
    preloadDictionaries().catch(() => {
      // 字典预加载失败不阻断页面渲染，各页面会在需要时自动按需加载
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider theme={lightTheme}>
        <AntApp>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
}

export default App;

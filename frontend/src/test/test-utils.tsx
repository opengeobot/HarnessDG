/**
 * 测试工具——提供 i18next、QueryClient、MemoryRouter 等测试上下文包装器。
 */
import type { ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { App as AntdApp } from 'antd';
import i18n from '@/shared/i18n';

interface TestWrapperOptions {
  route?: string;
}

/** 创建带 i18n + QueryClient + Router + antd App 的测试包装器 */
function createWrapper({ route = '/' }: TestWrapperOptions = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <I18nextProvider i18n={i18n}>
          <AntdApp>
            <MemoryRouter initialEntries={[route]}>{children}</MemoryRouter>
          </AntdApp>
        </I18nextProvider>
      </QueryClientProvider>
    );
  };
}

/** render 的增强版：自动包裹测试上下文 */
export function renderWithProviders(
  ui: React.ReactElement,
  options?: RenderOptions & TestWrapperOptions,
) {
  const { route, ...renderOptions } = options ?? {};
  return render(ui, { wrapper: createWrapper({ route }), ...renderOptions });
}

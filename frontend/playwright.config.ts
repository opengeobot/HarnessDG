import { defineConfig, devices } from '@playwright/test';

/**
 * P0B 前端 E2E 测试配置。
 *
 * 覆盖 AC-P0B-UI-001..006 场景：登录/重定向/Token 存储、权限菜单/按钮、
 * 管理页 Loading/Empty/Error、受控值渲染、i18n 切换。
 *
 * 运行前提：`docker compose up` 后以前端容器 8080 为权威口径——只有真实 nginx
 * 栈才暴露静态服务与 SPA 深链的交互（TASK-P0BR-073 前该缺陷因默认 5173 而长期隐身）。
 * 5173 为开发口径，经 `E2E_BASE_URL` 环境变量覆盖，但 Vite dev server 自带
 * history fallback，不能证明深链在真实栈下可用。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? 'html' : 'list',
  timeout: 30_000,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});

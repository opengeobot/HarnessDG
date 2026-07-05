import { defineConfig, devices } from '@playwright/test';

/**
 * P0B 前端 E2E 测试配置。
 *
 * 覆盖 AC-P0B-UI-001..006 场景：登录/重定向/Token 存储、权限菜单/按钮、
 * 管理页 Loading/Empty/Error、受控值渲染、i18n 切换。
 *
 * 运行前提：前后端均已启动（`docker compose up` 或 `pnpm dev` + 后端）。
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
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
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

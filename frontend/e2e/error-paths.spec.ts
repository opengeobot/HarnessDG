/*
 * 功能: AC-P0B-UI-003 E2E 测试——错误路径（未登录重定向 / 404 / 403）。
 * 时间: 2026-09-28
 * 作者: AxeXie
 *
 * <p>403 用例必须用真实受限主体：旧 spec 用 admin 访问 /admin/users 后只 console.log
 * observation，等于空跑。E2E_ASSET_OUTSIDER 仅持有 asset:read，而 routes.tsx 对
 * /admin/users 声明 requiredScopes=['user:read']，RouteGuard 必然渲染 403 Result；
 * 同时用 directApiStatus 证明后端在 UI 之外独立拒绝同一主体（UI 不是安全边界）。
 * <p>夹具口令来自 prepareFixtures 的幂等归一，故登录前必须先准备夹具。
 * <p>深链仅用于 /admin/*、/profile 与未知路径（实测返回 200）；/assets/** 会被
 * 前端容器 nginx 的静态资源规则遮蔽，不在本 spec 使用。
 */
import { test, expect, request } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  directApiStatus,
  E2E_ASSET_OUTSIDER,
  login,
  loginBootstrap,
  prepareFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    // 幂等确保 E2E_ASSET_OUTSIDER 存在且口令已知，否则下面的登录必然失败。
    await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-003: 错误路径', () => {
  test('未登录访问管理页重定向到登录页', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('未登录访问个人设置页重定向到登录页', async ({ page }) => {
    await page.goto('/profile');
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  test('已登录访问未知路由显示 404 与返回入口', async ({ page }) => {
    await loginBootstrap(page);
    await page.goto('/this-page-does-not-exist-xyz-123');

    // 限定到 Result 的 title 节点：裸 getByText 会同时命中 subtitle（「页面不存在」是其前缀）。
    await expect(page.locator('.ant-result-title', { hasText: '页面不存在' })).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-subtitle')).toContainText('您访问的页面不存在或已被移动。');
    await page.getByRole('button', { name: /返回资产目录/ }).click();
    await expect(page).toHaveURL(/\/assets$/, { timeout: 10_000 });
  });

  test('缺少 user:read 的主体看到 403 且后端独立拒绝', async ({ page }) => {
    // 后端层：不受前端守卫影响，直连 REST 仍必须 403。
    const status = await directApiStatus(
      '/system/users',
      E2E_ASSET_OUTSIDER.username,
      E2E_ASSET_OUTSIDER.password,
    );
    expect(status, 'E2E_ASSET_OUTSIDER 无 user:read，后端列表必须被拒绝').toBe(403);

    // 前端层：RouteGuard 渲染 403 Result 并列出所需 Scope。
    await login(page, E2E_ASSET_OUTSIDER.username, E2E_ASSET_OUTSIDER.password);
    await page.goto('/admin/users');
    await expect(page.getByText('缺少访问权限')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/所需权限: user:read/)).toBeVisible();
    // 守卫必须挡住页面本体：用户管理表格不得渲染。
    await expect(page.locator('.ant-table')).toHaveCount(0);
  });

  test('引导管理员访问用户管理页不出现 403', async ({ page }) => {
    await loginBootstrap(page);
    await page.goto('/admin/users');
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('缺少访问权限')).toHaveCount(0);
  });
});

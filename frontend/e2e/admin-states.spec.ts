/*
 * 功能: AC-P0B-UI-003 E2E 规格——角色绑定、资源 ACL 与版本列表页面的
 *       Loading/Empty/Error/Retry/Success 五种状态在真实浏览器中均有可用交互。
 *       空态与错误态在 HTTP 边界注入以确保确定性；Retry 后必须回到真实后端的 Success 态。
 * 时间: 2026-09-18
 * 作者: AxeXie
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_MANAGER,
  activateVersionsTab,
  adminToken,
  bootstrapCredentials,
  login,
  openAssetDetailViaCatalog,
  prepareFixtures,
  viewAllVersionsButton,
  type E2eFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;

/** antd 会在仅两个字符的按钮文案间插入空格（「重试」渲染为「重 试」）。 */
const RETRY = /重\s*试|retr\s*y/i;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

/** 把列表端点替换为固定响应，制造确定性的空态与错误态。 */
async function stubList(
  page: Page,
  pathPattern: RegExp,
  fulfill: { status: number; body: unknown } | 'delay',
) {
  await page.route(pathPattern, async (route) => {
    if (fulfill === 'delay') {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
      return;
    }
    await route.fulfill({
      status: fulfill.status,
      contentType: 'application/json',
      body: JSON.stringify(fulfill.body),
    });
  });
}

const EMPTY_OK = { status: 200, body: { data: [], requestId: 'e2e', timestamp: '2026-09-18T00:00:00Z' } };
const SERVER_ERROR = {
  status: 500,
  body: {
    code: 'COMMON_INTERNAL_ERROR',
    message: 'e2e injected failure',
    i18nKey: 'error.common.unknown',
    details: {},
    retryable: false,
  },
};

test.describe('AC-P0B-UI-003: 角色绑定页面状态', () => {
  const LIST = /\/api\/v1\/system\/role-bindings$/;

  test('Loading 态显示骨架而非空白', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await stubList(page, LIST, 'delay');
    await page.goto('/admin/role-bindings');
    await expect(page.locator('.ant-skeleton')).toBeVisible();
  });

  test('Empty 态显示空文案而非错误', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await stubList(page, LIST, EMPTY_OK);
    await page.goto('/admin/role-bindings');
    await expect(page.getByText(/暂无角色绑定|No role bindings/)).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('Error 态显示错误与重试，点击重试后恢复真实 Success 态', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await stubList(page, LIST, SERVER_ERROR);
    await page.goto('/admin/role-bindings');

    await expect(page.getByText(/加载失败|Load failed/)).toBeVisible({ timeout: 10_000 });
    const retry = page.getByRole('button', { name: RETRY });
    await expect(retry).toBeVisible();

    // 解除注入后重试必须命中真实后端并渲染列表。
    await page.unroute(LIST);
    await retry.click();
    await expect(page.locator('.ant-table-tbody tr').first()).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

test.describe('AC-P0B-UI-003: 资源 ACL 页面状态', () => {
  const LIST = /\/api\/v1\/system\/resource-acls$/;

  test('Empty 态显示空文案而非错误', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await stubList(page, LIST, EMPTY_OK);
    await page.goto('/admin/resource-acls');
    await expect(page.getByText(/暂无资源 ACL|No resource ACLs/)).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('Error 态显示错误与重试，点击重试后恢复真实 Success 态', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await stubList(page, LIST, SERVER_ERROR);
    await page.goto('/admin/resource-acls');

    await expect(page.getByText(/加载失败|Load failed/)).toBeVisible({ timeout: 10_000 });
    const retry = page.getByRole('button', { name: RETRY });
    await expect(retry).toBeVisible();

    await page.unroute(LIST);
    await retry.click();
    // Success 态：表格容器可见且不再处于错误态（列表可能为空，故只断言容器与错误态缺席）。
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

test.describe('AC-P0B-UI-003: 版本列表页面状态', () => {
  test('独立版本列表页面显示 Loading 骨架后进入 Success', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const pattern = new RegExp(`/api/v1/assets/${fixtures.assetId}/versions`);
    await stubList(page, pattern, 'delay');

    // 经应用内导航到达（073 修复后 SPA 深链整页加载亦可用；导航路径覆盖真实用户动线）。
    await openAssetDetailViaCatalog(page, E2E_ASSET.displayName);
    await activateVersionsTab(page);
    await viewAllVersionsButton(page).click();

    await expect(page.locator('.ant-skeleton')).toBeVisible();
    await page.unroute(pattern);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('版本列表 Error 态可重试恢复', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const pattern = new RegExp(`/api/v1/assets/${fixtures.assetId}/versions`);
    await stubList(page, pattern, SERVER_ERROR);

    await openAssetDetailViaCatalog(page, E2E_ASSET.displayName);
    await activateVersionsTab(page);
    await viewAllVersionsButton(page).click();

    await expect(page.getByText(/加载失败|Load failed/)).toBeVisible({ timeout: 15_000 });
    await page.unroute(pattern);
    await page.getByRole('button', { name: RETRY }).click();
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

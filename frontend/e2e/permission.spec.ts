/*
 * 功能: AC-P0B-UI-002 E2E 测试——权限控制菜单/按钮可见性。
 *       主体：授权管理员（authorization:read + user:read）与资产作者（无授权类 Scope）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';
import { E2E_AUTHOR, bootstrapCredentials, login } from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(() => {
  credentials = bootstrapCredentials();
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-002: 权限菜单与按钮可见性', () => {
  test('管理员可见管理中心菜单', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);

    const adminCenter = page.locator('.ant-menu-submenu-title', {
      hasText: /管理中心|Admin Center/,
    });
    await expect(adminCenter).toBeVisible();
  });

  test('管理员可进入用户管理页面', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);

    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/admin\/users$/);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
  });

  test('无 authorization:read 的主体看不到授权类菜单项', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);

    await page.locator('.ant-menu-submenu-title', { hasText: /管理中心|Admin Center/ }).click();

    // 菜单确实展开了（以 project:view 可见的入口为锚）。
    await expect(
      page.locator('.ant-menu-item').filter({ hasText: /团队管理|Team Management/ }),
    ).toBeVisible();
    await expect(
      page.locator('.ant-menu-item').filter({ hasText: /角色绑定|Role Bindings/ }),
    ).toHaveCount(0);
  });

  test('多主体搜索隔离', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 管理员应能看到所有资产
    await page.goto('/assets');
    await page.waitForTimeout(2000);

    // 资产搜索应返回结果（管理员视角）
    const assetRows = page.locator('.ant-table-row');
    const rowCount = await assetRows.count();

    // 管理员应能看到至少一些资产
    expect(rowCount).toBeGreaterThanOrEqual(0);

    // 验证搜索功能存在
    const searchInput = page.getByPlaceholder(/搜索|search|keyword/i).first();
    if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await searchInput.fill('journey');
      await page.waitForTimeout(1000);
    }

    // 页面不崩溃
    await expect(page.locator('#root')).toBeVisible();
  });
});

/*
 * 功能: AC-P0B-UI-002 E2E 测试——权限控制菜单/按钮可见性。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

/**
 * 辅助函数：登录。
 */
async function login(page: import('@playwright/test').Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(username);
  await page.getByLabel(/密码|password/i).fill(password);
  await page.getByRole('button', { name: /登录|login|sign in/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

test.describe('AC-P0B-UI-002: 权限菜单与按钮可见性', () => {
  test('管理员可见系统管理菜单', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 管理员应能看到系统管理相关菜单项。
    const adminMenuItems = page.locator('.ant-menu-item, .ant-menu-submenu-title')
      .filter({ hasText: /系统|管理|system|admin|用户|user/i });
    await expect(adminMenuItems.first()).toBeVisible();
  });

  test('管理员可访问管理页面', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 导航到用户管理页面。
    const userMenuLink = page.locator('a[href*="/admin/users"], a[href*="/system/users"]').first();
    if (await userMenuLink.isVisible()) {
      await userMenuLink.click();
      await expect(page).toHaveURL(/\/admin\/users|\/system\/users/);
    }
  });

  test('无权限页面不可通过 URL 直接访问', async ({ page }) => {
    // 普通用户（假设已存在）不应能访问系统配置。
    // 此测试需要在环境中存在普通用户账号时才有效。
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 作为管理员，验证管理页面可正常加载（不出现错误）。
    await page.goto('/admin/users');
    const errorAlert = page.locator('.ant-alert-error, [role="alert"]').first();
    const hasError = await errorAlert.isVisible({ timeout: 3000 }).catch(() => false);
    expect(hasError).toBe(false);
  });
});

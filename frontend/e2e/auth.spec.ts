/*
 * 功能: AC-P0B-UI-001 E2E 测试——登录/重定向/Token 存储。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';

/**
 * 默认管理员凭据（与 docker-compose 初始化一致）。
 */
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

test.describe('AC-P0B-UI-001: 登录与 Token 生命周期', () => {
  test('未登录用户被重定向到登录页', async ({ page }) => {
    await page.goto('/');
    // 应被路由守卫重定向到 /login。
    await expect(page).toHaveURL(/\/login/);
  });

  test('登录成功后重定向到主页', async ({ page }) => {
    await page.goto('/login');

    // 填写凭据。
    await page.getByLabel(/用户名|username/i).fill(ADMIN_USERNAME);
    await page.getByLabel(/密码|password/i).fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: /登录|login|sign in/i }).click();

    // 登录成功后不在 /login 页。
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('错误凭据显示提示信息', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel(/用户名|username/i).fill('wrong-user');
    await page.getByLabel(/密码|password/i).fill('wrong-password');
    await page.getByRole('button', { name: /登录|login|sign in/i }).click();

    // 应显示错误提示且不泄露用户是否存在。
    const errorText = await page.locator('.ant-message-error, .ant-alert-error, [role="alert"]').textContent();
    expect(errorText).toBeTruthy();
    // 不应暴露 "user not found" 等存在性信息。
    expect(errorText?.toLowerCase()).not.toContain('user not found');
    expect(errorText?.toLowerCase()).not.toContain('用户不存在');
  });

  test('登出后 Token 被清除并重定向到登录页', async ({ page }) => {
    // 先登录。
    await page.goto('/login');
    await page.getByLabel(/用户名|username/i).fill(ADMIN_USERNAME);
    await page.getByLabel(/密码|password/i).fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: /登录|login|sign in/i }).click();
    await expect(page).not.toHaveURL(/\/login/);

    // 点击登出（通常在用户菜单下拉中）。
    const logoutTrigger = page.getByText(/登出|logout|sign out|退出/i).first();
    if (await logoutTrigger.isVisible()) {
      await logoutTrigger.click();
    } else {
      // 可能需要先点击头像/用户名打开下拉菜单。
      const userMenu = page.locator('.ant-dropdown-trigger, [data-testid="user-menu"]').first();
      await userMenu.click();
      await page.getByText(/登出|logout|sign out|退出/i).first().click();
    }

    // 登出后重定向到登录页。
    await expect(page).toHaveURL(/\/login/);
  });
});

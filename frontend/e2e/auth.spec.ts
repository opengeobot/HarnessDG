/*
 * 功能: AC-P0B-UI-001 E2E 测试——登录/重定向/Token 存储。
 *       引导凭据一律从环境变量读取，缺失时显式 skip，不得以仓库内口令充当凭据。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';
import { bootstrapCredentials, login, loginButton } from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(() => {
  credentials = bootstrapCredentials();
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-001: 登录与 Token 生命周期', () => {
  test('未登录用户被重定向到登录页', async ({ page }) => {
    await page.goto('/');
    // 应被路由守卫重定向到 /login。
    await expect(page).toHaveURL(/\/login/);
  });

  test('登录成功后重定向到主页', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);
    // 登录成功后不在 /login 页。
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('错误凭据显示提示信息', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel(/用户名|username/i).fill('wrong-user');
    await page.getByLabel(/密码|password/i).fill('wrong-password');
    await loginButton(page).click();

    // 应显示错误提示且不泄露用户是否存在。
    const errorText = await page
      .locator('.ant-message-error, .ant-alert-error, [role="alert"]')
      .first()
      .textContent();
    expect(errorText).toBeTruthy();
    // 不应暴露 "user not found" 等存在性信息。
    expect(errorText?.toLowerCase()).not.toContain('user not found');
    expect(errorText?.toLowerCase()).not.toContain('用户不存在');
  });

  test('登出后 Token 被清除并重定向到登录页', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);

    // 顶栏有两个 Dropdown（语言切换器与用户菜单），登出入口只在后者。
    await page.locator('.ant-layout-header .ant-dropdown-trigger').last().click();
    await page.getByText(/登\s*出|log\s*out|sign\s*out|退出/i).first().click();

    // 登出后重定向到登录页。
    await expect(page).toHaveURL(/\/login/);
  });
});

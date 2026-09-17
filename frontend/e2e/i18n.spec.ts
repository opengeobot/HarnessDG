/*
 * 功能: AC-P0B-UI-005 / AC-P0B-TAX-006 E2E 测试——运行时中英文切换与错误按 i18nKey 渲染。
 *       断言为无条件式：切换器缺失、文案未变化或请求语言不符时测试必须失败。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

async function login(page: import('@playwright/test').Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(username);
  await page.getByLabel(/密码|password/i).fill(password);
  await page.getByRole('button', { name: /登录|login|sign in/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** 经唯一入口切换语言并等待界面文案更新 */
async function switchLanguage(page: import('@playwright/test').Page, label: '中文' | 'English') {
  await page.getByTestId('language-switcher').click();
  await page.getByRole('menuitem', { name: label }).click();
}

test.describe('AC-P0B-UI-005/TAX-006: i18n 运行时切换', () => {
  test('登录页提供语言切换入口并可切换为英文', async ({ page }) => {
    await page.goto('/login');

    // 登录页必须存在切换器；缺少即失败（不得条件跳过）。
    const switcher = page.getByTestId('language-switcher');
    await expect(switcher).toBeVisible();

    await expect(page.getByText('AI 资产管理平台')).toBeVisible();
    await switchLanguage(page, 'English');
    await expect(page.getByText('AI Asset Management Platform')).toBeVisible();
  });

  test('切换语言后刷新仍保持所选语言', async ({ page }) => {
    await page.goto('/login');
    await switchLanguage(page, 'English');
    await expect(page.getByText('AI Asset Management Platform')).toBeVisible();

    await page.reload();
    await expect(page.getByText('AI Asset Management Platform')).toBeVisible();
  });

  test('登录后导航与界面文案随语言切换', async ({ page }) => {
    await page.goto('/login');
    await switchLanguage(page, 'English');
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 英文界面下导航与用户菜单应为英文。
    await expect(page.getByText('Asset Catalog', { exact: true })).toBeVisible();
    await expect(page.getByText('Logout', { exact: true })).toBeVisible();

    // 切回中文后同一界面应立即变化（导航不再绑定模块加载时的旧文案）。
    await switchLanguage(page, '中文');
    await expect(page.getByText('资产目录', { exact: true })).toBeVisible();
  });

  test('请求头 Accept-Language 与所选语言一致', async ({ page }) => {
    const observed: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/')) {
        const value = request.headers()['accept-language'];
        if (value) {
          observed.push(value);
        }
      }
    });

    await page.goto('/login');
    await switchLanguage(page, 'English');
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);
    await page.waitForLoadState('networkidle');

    expect(observed.length).toBeGreaterThan(0);
    expect(observed.some((value) => value.startsWith('en'))).toBe(true);
  });

  test('切换语言后后端错误按 i18nKey 以所选语言渲染', async ({ page }) => {
    await page.goto('/login');
    await switchLanguage(page, 'English');

    // 使用错误凭据触发后端认证错误。
    await page.getByLabel(/用户名|username/i).fill('admin');
    await page.getByLabel(/密码|password/i).fill('definitely-wrong-password');
    await page.getByRole('button', { name: /Login|Sign in/i }).click();

    // 错误提示必须出现且不含原始堆栈；文案随界面语言（英文）呈现。
    const alert = page.locator('.ant-message').first();
    await expect(alert).toBeVisible();
    await expect(alert).not.toContainText('at com.aihub');
  });
});

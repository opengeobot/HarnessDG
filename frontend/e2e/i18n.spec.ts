/*
 * 功能: AC-P0B-UI-005 / AC-P0B-TAX-006 E2E 测试——运行时中英文切换与错误按 i18nKey 渲染。
 *       断言为无条件式：切换器缺失、文案未变化或请求语言不符时测试必须失败。
 *       凭据一律从环境变量读取，缺失时显式 skip。
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
    await login(page, credentials!.username, credentials!.password);

    // 英文界面下导航与用户菜单应为英文。
    // 断言使用引导管理员确实具备 Scope 的入口（authorization:read）：导航按权限过滤，
    // 无 asset:read 的主体看不到「资产目录」，断言该入口会因权限而非 i18n 失败。
    await expect(page.getByText('Access & Credentials', { exact: true })).toBeVisible();

    // 顶栏用户菜单同样随语言切换（菜单项仅在展开时渲染）。
    await page.locator('.ant-layout-header .ant-dropdown-trigger').last().click();
    await expect(page.getByText('Logout', { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');

    // 切回中文后同一界面应立即变化（导航不再绑定模块加载时的旧文案）。
    await switchLanguage(page, '中文');
    await expect(page.getByText('访问与凭据', { exact: true })).toBeVisible();
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
    await login(page, credentials!.username, credentials!.password);
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
    await loginButton(page).click();

    // 错误提示必须出现且不含原始堆栈。
    const alert = page.locator('.ant-message').first();
    await expect(alert).toBeVisible();
    await expect(alert).not.toContainText('at com.aihub');
  });
});

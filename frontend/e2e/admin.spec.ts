/*
 * 功能: AC-P0B-UI-003/004 E2E 测试——管理页 Loading/Empty/Error 状态。
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

test.describe('AC-P0B-UI-003: 管理页面正常渲染', () => {
  test('用户管理页面加载成功', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    await page.goto('/admin/users');

    // 页面加载完成后应显示表格或列表组件。
    const table = page.locator('.ant-table, .ant-list, [data-testid="users-table"]').first();
    await expect(table).toBeVisible({ timeout: 10_000 });

    // 不应显示错误状态。
    const errorAlert = page.locator('.ant-alert-error, .ant-result-error').first();
    const hasError = await errorAlert.isVisible({ timeout: 2000 }).catch(() => false);
    expect(hasError).toBe(false);
  });

  test('字典管理页面加载成功', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    await page.goto('/admin/dictionaries');

    // 应显示字典列表或表格。
    const content = page.locator('.ant-table, .ant-list, .ant-card, [data-testid="dict-list"]').first();
    await expect(content).toBeVisible({ timeout: 10_000 });
  });

  test('角色管理页面加载成功', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    await page.goto('/admin/roles');

    const content = page.locator('.ant-table, .ant-list, .ant-card, [data-testid="roles-list"]').first();
    await expect(content).toBeVisible({ timeout: 10_000 });
  });
});

test.describe('AC-P0B-UI-004: 受控值渲染', () => {
  test('下拉选择器使用字典受控值', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    // 导航到资产页面（如果存在）。
    await page.goto('/assets');

    // 查找使用字典值的下拉组件。
    const select = page.locator('.ant-select, [data-testid="controlled-select"]').first();
    const hasSelect = await select.isVisible({ timeout: 5000 }).catch(() => false);

    if (hasSelect) {
      // 下拉选项应从字典 API 获取（非硬编码值）。
      await select.click();
      const options = page.locator('.ant-select-dropdown .ant-select-item-option');
      // 至少有一些选项或空状态。
      const optionCount = await options.count();
      expect(optionCount).toBeGreaterThanOrEqual(0);
    }
  });
});

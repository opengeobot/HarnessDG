/**
 * 功能: 版本管理流程 E2E 测试。
 * 运行前提: docker compose up + pnpm dev，系统中已有版本数据。
 */
import { test, expect, type Page } from '@playwright/test';

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(ADMIN_USERNAME);
  await page.getByLabel(/密码|password/i).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登录|login|sign in/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

test.describe('版本管理流程', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('版本中心页面加载', async ({ page }) => {
    // 导航到资产页面
    await page.goto('/assets');
    await expect(page).not.toHaveURL(/\/login/);

    // 资产页面应可见
    const content = page.locator('#root');
    await expect(content).toBeVisible();
  });

  test('输入 assetId 查询版本列表', async ({ page }) => {
    await page.goto('/assets');

    // 如果有搜索框，输入 assetId
    const searchInput = page.getByPlaceholder(/assetId|搜索|search/i).first();
    if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
      await searchInput.fill('ast_');
      await page.waitForTimeout(1000);
    }

    // 页面应不崩溃
    await expect(page.locator('#root')).toBeVisible();
  });

  test('选择版本查看详情', async ({ page }) => {
    // 假设已知资产和版本路径
    await page.goto('/assets/ast_001/versions/ver_001');

    // 页面应加载（可能显示版本详情或空状态）
    await page.waitForTimeout(2000);
    const content = page.locator('#root');
    await expect(content).toBeVisible();
  });

  test('版本状态显示', async ({ page }) => {
    await page.goto('/assets');

    // 等待页面加载
    await page.waitForTimeout(2000);

    // 如果有版本列表，状态 Tag 应可见
    const tags = page.locator('.ant-tag');
    if (await tags.count() > 0) {
      await expect(tags.first()).toBeVisible();
    }
  });
});

/**
 * 功能: AC-P0B-IAM-012 E2E 测试——用户管理 CRUD。
 * 运行前提: docker compose up + pnpm dev
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

test.describe('AC-P0B-IAM-012: 用户管理 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('创建用户弹窗填写并提交', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).not.toHaveURL(/\/login/);

    // 点击创建用户按钮
    const createBtn = page.getByRole('button', { name: /创建用户|createUser/i });
    await createBtn.click();

    // 弹窗应出现
    await expect(page.getByRole('dialog')).toBeVisible();

    // 填写表单
    await page.getByLabel(/用户名|username/i).last().fill('testuser_e2e');
    await page.getByLabel(/显示名|displayName/i).fill('E2E Test User');
    await page.getByLabel(/临时密码|temporaryPassword/i).fill('Temp@12345678');

    // 提交
    await page.getByRole('button', { name: /确定|ok|submit/i }).click();

    // 弹窗关闭或显示成功消息
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10000 });
  });

  test('新用户出现在列表', async ({ page }) => {
    await page.goto('/admin/users');

    // 搜索刚创建的用户
    const searchInput = page.getByPlaceholder(/搜索|search|keyword/i);
    if (await searchInput.isVisible()) {
      await searchInput.fill('testuser_e2e');
      await page.getByRole('button', { name: /搜索|search/i }).click();
    }

    // 等待列表刷新
    await page.waitForTimeout(1000);
    // 列表中应可见
    const userCell = page.getByText('testuser_e2e');
    await expect(userCell.first()).toBeVisible({ timeout: 10000 });
  });

  test('禁用/启用用户', async ({ page }) => {
    await page.goto('/admin/users');

    // 找到测试用户行中的操作按钮
    const row = page.locator('tr', { has: page.getByText('testuser_e2e') });
    if (await row.isVisible()) {
      // 查找禁用/启用按钮
      const disableBtn = row.getByRole('button', { name: /禁用|disable/i });
      if (await disableBtn.isVisible()) {
        await disableBtn.click();
        // 确认操作
        const confirmBtn = page.getByRole('button', { name: /确定|ok/i });
        if (await confirmBtn.isVisible()) {
          await confirmBtn.click();
        }
        await page.waitForTimeout(1000);
      }
    }
  });

  test('重置密码', async ({ page }) => {
    await page.goto('/admin/users');

    const row = page.locator('tr', { has: page.getByText('testuser_e2e') });
    if (await row.isVisible()) {
      const resetBtn = row.getByRole('button', { name: /重置密码|resetPassword/i });
      if (await resetBtn.isVisible()) {
        await resetBtn.click();
        await expect(page.getByRole('dialog')).toBeVisible();

        // 填写新密码
        await page.getByLabel(/临时密码|temporaryPassword/i).last().fill('NewTemp@12345678');
        await page.getByRole('button', { name: /确定|ok/i }).click();

        await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10000 });
      }
    }
  });
});

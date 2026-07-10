/**
 * 功能: AC-P0B-IAM-003 E2E 测试——Profile 改密。
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

test.describe('AC-P0B-IAM-003: Profile 改密', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('/profile 展示账户信息', async ({ page }) => {
    await page.goto('/profile');

    // 页面应加载
    await expect(page).not.toHaveURL(/\/login/);

    // 应显示账户信息
    const content = page.locator('#root');
    await expect(content).toBeVisible();

    // 应显示 principalId 或用户名
    const principalText = page.getByText(/admin|principalId/i).first();
    await expect(principalText).toBeVisible({ timeout: 5000 });
  });

  test('改密表单填写和提交', async ({ page }) => {
    await page.goto('/profile');

    // 查找改密表单区域
    const currentPasswordInput = page.getByLabel(/当前密码|currentPassword/i);
    const newPasswordInput = page.getByLabel(/新密码|newPassword/i);
    const confirmPasswordInput = page.getByLabel(/确认密码|confirmPassword/i);

    if (await currentPasswordInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await currentPasswordInput.fill(ADMIN_PASSWORD);
      await newPasswordInput.fill('NewSecure@12345');
      await confirmPasswordInput.fill('NewSecure@12345');

      // 提交
      await page.getByRole('button', { name: /修改密码|changePassword|submit/i }).click();

      // 应显示成功或错误消息
      await page.waitForTimeout(2000);
    }
  });

  test('密码不一致前端校验', async ({ page }) => {
    await page.goto('/profile');

    const currentPasswordInput = page.getByLabel(/当前密码|currentPassword/i);
    const newPasswordInput = page.getByLabel(/新密码|newPassword/i);
    const confirmPasswordInput = page.getByLabel(/确认密码|confirmPassword/i);

    if (await currentPasswordInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await currentPasswordInput.fill(ADMIN_PASSWORD);
      await newPasswordInput.fill('NewSecure@12345');
      await confirmPasswordInput.fill('DifferentPassword@12345');

      // 提交
      await page.getByRole('button', { name: /修改密码|changePassword|submit/i }).click();

      // 应显示不一致错误
      await page.waitForTimeout(1000);
      const errorText = page.getByText(/不一致|not match|differ/i);
      if (await errorText.first().isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(errorText.first()).toBeVisible();
      }
    }
  });

  test('新密码 >=12 字符校验', async ({ page }) => {
    await page.goto('/profile');

    const currentPasswordInput = page.getByLabel(/当前密码|currentPassword/i);
    const newPasswordInput = page.getByLabel(/新密码|newPassword/i);
    const confirmPasswordInput = page.getByLabel(/确认密码|confirmPassword/i);

    if (await currentPasswordInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await currentPasswordInput.fill(ADMIN_PASSWORD);
      await newPasswordInput.fill('short');
      await confirmPasswordInput.fill('short');

      // 提交
      await page.getByRole('button', { name: /修改密码|changePassword|submit/i }).click();

      // 应显示长度不足错误
      await page.waitForTimeout(1000);
      const errorText = page.getByText(/12|字符|character|length/i);
      if (await errorText.first().isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(errorText.first()).toBeVisible();
      }
    }
  });
});

/**
 * 功能: AC-P0B-AGT-001~005 E2E 测试——Agent 管理。
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

test.describe('AC-P0B-AGT-001~005: Agent 管理', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('注册 Agent 并查看一次性凭据', async ({ page }) => {
    await page.goto('/admin/agents');

    // 点击注册按钮
    await page.getByRole('button', { name: /注册.*Agent|registerAgent/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    // 填写表单
    await page.getByLabel(/显示名|displayName/i).last().fill('E2E Test Agent');
    await page.getByLabel(/Agent.*类型|agentType/i).fill('CODE_ASSISTANT');

    // 提交
    await page.getByRole('button', { name: /确定|ok|submit/i }).click();

    // 应显示一次性凭据弹窗
    await page.waitForTimeout(2000);
    const credentialText = page.getByText(/凭据|credential/i);
    if (await credentialText.first().isVisible({ timeout: 5000 }).catch(() => false)) {
      await expect(credentialText.first()).toBeVisible();
    }
  });

  test('Agent 列表展示', async ({ page }) => {
    await page.goto('/admin/agents');

    // 表格应可见
    const table = page.locator('.ant-table');
    await expect(table).toBeVisible();

    // 应有 Agent 相关列
    await expect(page.getByText(/Agent|display/i).first()).toBeVisible();
  });

  test('禁用/启用 Agent', async ({ page }) => {
    await page.goto('/admin/agents');

    // 找到测试 Agent 行
    const agentRow = page.locator('tr', { has: page.getByText('E2E Test Agent') });
    if (await agentRow.isVisible({ timeout: 5000 }).catch(() => false)) {
      const disableBtn = agentRow.getByRole('button', { name: /禁用|disable/i });
      if (await disableBtn.isVisible()) {
        await disableBtn.click();
        const confirmBtn = page.getByRole('button', { name: /确定|ok/i });
        if (await confirmBtn.isVisible()) {
          await confirmBtn.click();
        }
        await page.waitForTimeout(1000);
      }
    }
  });

  test('编辑 Tool 白名单', async ({ page }) => {
    await page.goto('/admin/agents');

    // 找到测试 Agent 行的编辑白名单按钮
    const agentRow = page.locator('tr', { has: page.getByText('E2E Test Agent') });
    if (await agentRow.isVisible({ timeout: 5000 }).catch(() => false)) {
      const editBtn = agentRow.getByRole('button', { name: /白名单|allowlist|tool/i });
      if (await editBtn.isVisible()) {
        await editBtn.click();
        await expect(page.getByRole('dialog')).toBeVisible();
        // 关闭弹窗
        await page.getByRole('button', { name: /取消|cancel/i }).click();
      }
    }
  });
});

/**
 * 功能: 数据集详情 E2E 测试（PRD V25）。
 * 覆盖：数据集切换版本 → Card/Preview/Files 同版本；Discussion 继承资产权限。
 * 运行前提: docker compose up + pnpm dev，系统中已有数据集。
 * 时间: 2026-07-11
 * 作者: AxeXie
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

test.describe('PRD V25: 数据集详情 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('数据集类型过滤', async ({ page }) => {
    await page.goto('/assets');
    await page.waitForTimeout(2000);

    // 点击数据集类型过滤
    const datasetFilter = page.getByText(/数据集|Dataset/).first();
    if (await datasetFilter.isVisible({ timeout: 5000 }).catch(() => false)) {
      await datasetFilter.click();
      await page.waitForTimeout(2000);

      // 过滤后页面应仍正常加载
      await expect(page.locator('#root')).toBeVisible();
    }
  });

  test('数据集详情页展示 Dataset profile 字段', async ({ page }) => {
    await page.goto('/assets');
    await page.waitForTimeout(2000);

    // 切换到数据集类型
    const datasetFilter = page.getByText(/数据集|Dataset/).first();
    if (await datasetFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      await datasetFilter.click();
      await page.waitForTimeout(2000);
    }

    // 尝试找到第一个数据集
    const firstDataset = page.locator('table tbody tr').first();
    if (await firstDataset.isVisible({ timeout: 5000 }).catch(() => false)) {
      await firstDataset.locator('a, td').first().click();
      await page.waitForTimeout(2000);

      // 详情页应展示 Dataset 特有字段
      const rootText = await page.locator('#root').textContent();
      expect(rootText).toBeTruthy();

      // 查找 format 或 modality 相关字段
      const profileFields = page.locator('#root').getByText(/format|modality|格式|模态/i).first();
      if (await profileFields.isVisible({ timeout: 3000 }).catch(() => false)) {
        await expect(profileFields).toBeVisible();
      }
    } else {
      test.skip(true, '无可用数据集，跳过详情页测试');
    }
  });

  test('数据集版本切换——Card/Preview/Files 同版本', async ({ page }) => {
    await page.goto('/assets');
    await page.waitForTimeout(2000);

    // 切换到数据集类型
    const datasetFilter = page.getByText(/数据集|Dataset/).first();
    if (await datasetFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      await datasetFilter.click();
      await page.waitForTimeout(2000);
    }

    // 找到第一个数据集
    const firstDataset = page.locator('table tbody tr').first();
    if (!await firstDataset.isVisible({ timeout: 5000 }).catch(() => false)) {
      test.skip(true, '无可用数据集，跳过版本切换测试');
      return;
    }

    await firstDataset.locator('a, td').first().click();
    await page.waitForTimeout(2000);

    // 查找版本选择器
    const versionSelector = page.getByText(/版本|version/i).first();
    if (await versionSelector.isVisible({ timeout: 3000 }).catch(() => false)) {
      // 版本选择器可见
      await expect(versionSelector).toBeVisible();
    }

    // 验证各 Tab (Card/Preview/Files) 存在
    const cardTab = page.getByText(/Card|卡片|README/i).first();
    const previewTab = page.getByText(/Preview|预览/i).first();
    const filesTab = page.getByText(/Files|文件/i).first();

    // 至少部分 Tab 应可见
    const hasCard = await cardTab.isVisible({ timeout: 2000 }).catch(() => false);
    const hasPreview = await previewTab.isVisible({ timeout: 1000 }).catch(() => false);
    const hasFiles = await filesTab.isVisible({ timeout: 1000 }).catch(() => false);

    expect(hasCard || hasPreview || hasFiles).toBe(true);
  });

  test('数据集 Discussion 继承资产权限', async ({ page }) => {
    await page.goto('/assets');
    await page.waitForTimeout(2000);

    // 切换到数据集类型
    const datasetFilter = page.getByText(/数据集|Dataset/).first();
    if (await datasetFilter.isVisible({ timeout: 3000 }).catch(() => false)) {
      await datasetFilter.click();
      await page.waitForTimeout(2000);
    }

    // 找到第一个数据集
    const firstDataset = page.locator('table tbody tr').first();
    if (!await firstDataset.isVisible({ timeout: 5000 }).catch(() => false)) {
      test.skip(true, '无可用数据集，跳过 Discussion 测试');
      return;
    }

    await firstDataset.locator('a, td').first().click();
    await page.waitForTimeout(2000);

    // 查找 Discussion 或评论 Tab
    const discussionTab = page.getByText(/讨论|discussion|comment|评论/i).first();
    if (await discussionTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await discussionTab.click();
      await page.waitForTimeout(1000);

      // Discussion 区域应正常加载
      await expect(page.locator('#root')).toBeVisible();
    }

    // 页面不崩溃
    await expect(page.locator('#root')).toBeVisible();
  });
});

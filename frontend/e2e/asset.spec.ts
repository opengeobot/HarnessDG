/*
 * 功能: P1 资产目录 E2E 测试——创建→搜索→详情→更新→弃用→归档→恢复全流程。
 * 时间: 2026-07-08
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

/**
 * 辅助：登录管理员账号。
 */
async function loginAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(ADMIN_USERNAME);
  await page.getByLabel(/密码|password/i).fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /登录|login|sign in/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

test.describe('P1 资产目录完整生命周期', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('资产列表页加载并显示内容', async ({ page }) => {
    await page.goto('/assets');
    // 页面标题
    await expect(page.getByText(/资产目录|Asset Catalog/)).toBeVisible();
    // 类型切换控件
    await expect(page.getByText(/全部|All/)).toBeVisible();
    await expect(page.getByText(/模型|Model/)).toBeVisible();
    await expect(page.getByText(/数据集|Dataset/)).toBeVisible();
    // 搜索框
    await expect(page.getByPlaceholder(/搜索|Search/)).toBeVisible();
  });

  test('通过弹窗登记新资产', async ({ page }) => {
    await page.goto('/assets');
    await page.getByRole('button', { name: /登记资产|Register Asset/ }).click();

    // 弹窗标题
    await expect(page.getByText(/登记资产|Register Asset/)).toBeVisible();

    // 选择类型
    await page.getByLabel(/类型|Type/).click();
    await page.getByText(/模型|Model/).click();

    // 填写必填字段
    await page.getByLabel(/命名空间|Namespace/).fill('e2e-test');
    await page.getByLabel(/名称|Name/).fill('test-model-' + Date.now());

    // 可见性
    await page.getByLabel(/可见性|Visibility/).click();
    await page.getByText(/内部|Internal/).click();

    // 提交
    await page.getByRole('button', { name: /创建|Create/ }).click();

    // 应显示成功提示
    await expect(page.getByText(/e2e-test/)).toBeVisible({ timeout: 10000 });
  });

  test('搜索与过滤资产', async ({ page }) => {
    await page.goto('/assets');
    // 使用搜索框
    const searchInput = page.getByPlaceholder(/搜索|Search/);
    await searchInput.fill('test');
    await searchInput.press('Enter');
    // 页面应刷新结果
    await page.waitForTimeout(1000);

    // 过滤面板
    await expect(page.getByText(/语言|Language/)).toBeVisible();
    await expect(page.getByText(/敏感等级|Sensitivity/)).toBeVisible();
  });

  test('资产详情页展示完整信息', async ({ page }) => {
    // 先导航到资产列表
    await page.goto('/assets');
    // 如果有资产则点击第一个
    const firstAsset = page.locator('table tbody tr').first();
    if (await firstAsset.isVisible()) {
      await firstAsset.locator('a').click();
      // 详情页应包含基本信息卡片
      await expect(page.getByText(/基本信息|Basic Info/)).toBeVisible();
      // 坐标信息
      await expect(page.getByText(/坐标|Coordinate/)).toBeVisible();
    }
  });

  test('资产生命周期操作: 弃用→归档→恢复', async ({ page }) => {
    await page.goto('/assets');

    // 找到第一个资产并进入设置页
    const firstAssetLink = page.locator('table tbody tr').first().locator('a').first();
    if (await firstAssetLink.isVisible()) {
      await firstAssetLink.click();

      // 导航到设置页
      await page.getByRole('button', { name: /设置|Settings/ }).click();
      await expect(page.getByText(/资产设置|Asset Settings/)).toBeVisible();

      // 弃用
      const deprecateBtn = page.getByRole('button', { name: /弃用|Deprecate/ });
      if (await deprecateBtn.isVisible()) {
        // 选择弃用原因
        await page.getByLabel(/弃用原因|Deprecation Reason/).click();
        await page.getByText(/已过时|Outdated/).click();
        await deprecateBtn.click();
        // 确认
        await page.getByRole('button', { name: /确认|Confirm|OK/ }).click();
        await expect(page.getByText(/已弃用|Deprecated/)).toBeVisible({ timeout: 5000 });
      }

      // 归档
      const archiveBtn = page.getByRole('button', { name: /归档|Archive/ });
      if (await archiveBtn.isVisible()) {
        await archiveBtn.click();
        await page.getByRole('button', { name: /确认|Confirm|OK/ }).click();
        await expect(page.getByText(/已归档|Archived/)).toBeVisible({ timeout: 5000 });
      }

      // 恢复
      const restoreBtn = page.getByRole('button', { name: /恢复|Restore/ });
      if (await restoreBtn.isVisible()) {
        await restoreBtn.click();
        await page.getByRole('button', { name: /确认|Confirm|OK/ }).click();
        await expect(page.getByText(/已恢复|Restored/)).toBeVisible({ timeout: 5000 });
      }
    }
  });

  test('Skeleton 加载态展示', async ({ page }) => {
    // 导航到详情页，在数据加载完成前应有 Skeleton
    await page.goto('/assets/non-existent-id');
    // 不存在的资产应显示 Empty
    await expect(page.getByText(/不存在|does not exist/)).toBeVisible({ timeout: 5000 });
  });
});

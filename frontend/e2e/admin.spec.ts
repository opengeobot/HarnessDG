/*
 * 功能: AC-P0B-UI-003/004 E2E 测试——管理页面真实渲染，以及受控值来自受控 API。
 *       主体：显式列出所需 Scope 的最小管理主体（project:view/asset:read/dictionary:read/
 *       tag:read/authorization:read/authorization:manage/user:read）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect } from '@playwright/test';
import {
  E2E_AUTHOR,
  E2E_MANAGER,
  bootstrapCredentials,
  directApiGet,
  login,
  openAssetsViaNav,
} from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(() => {
  credentials = bootstrapCredentials();
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-003: 管理页面正常渲染', () => {
  test('用户管理页面加载成功', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await page.goto('/admin/users');

    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('字典管理页面加载成功', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await page.goto('/admin/dictionaries');

    await expect(page.locator('.ant-table, .ant-list, .ant-card').first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('角色管理页面加载成功', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await page.goto('/admin/roles');

    await expect(page.locator('.ant-table, .ant-list, .ant-card').first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

test.describe('AC-P0B-UI-004: 受控值来自受控 API', () => {
  test('资产表单的组织下拉选项来自组织 API 而非硬编码', async ({ page }) => {
    // 以同一主体的独立 API 会话取得真实响应，作为受控来源的基准。
    // 注意：浏览器内存中的 access Token 不会被 page.request 复用，故不经 page.request 取数。
    // 主体选资产所有者：组织列表按成员关系过滤，非成员主体会（正确地）看到空列表。
    const organizations = await directApiGet<{ code: string; name: string }[]>(
      '/system/organizations',
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(organizations.status).toBe(200);
    const organizationsData = organizations.data ?? [];
    expect(organizationsData.length).toBeGreaterThan(0);

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetsViaNav(page);
    await page.getByRole('button', { name: /登记资产|Register Asset/ }).click();

    const dialog = page.locator('.ant-modal-content');
    await expect(dialog).toBeVisible();

    // 组织字段必须是受控下拉（antd 的 combobox 语义落在内层 input 上）；
    // 按表单项定位而非按索引，避免表单顺序变化后断言指向别的字段。
    const orgItem = dialog
      .locator('.ant-form-item')
      .filter({ has: page.locator('label', { hasText: /组织|Organization/ }) })
      .first();
    const orgInput = orgItem.locator('input[role="combobox"]');
    await expect(orgInput).toHaveCount(1);
    // 自由文本输入不能是受控取值的载体：该字段不接受任意输入，只接受选项。
    await orgInput.click();

    const firstOrganization = organizationsData[0];
    await expect(
      page.locator('.ant-select-dropdown .ant-select-item-option').filter({
        hasText: firstOrganization.name,
      }),
    ).toBeVisible({ timeout: 10_000 });
  });
});

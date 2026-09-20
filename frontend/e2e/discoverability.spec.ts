/*
 * 功能: AC-P0B-UI-006 E2E 规格——管理端能力可发现性与版本入口死链回归。
 *       以两个权限不同的真实主体执行：授权管理员主体（authorization:read）与
 *       资产作者主体（project:view/asset:read，无 authorization:read）。
 *       断言为无条件式：入口缺失、导航未按权限过滤、或「查看全部」落到 404 时测试必须失败。
 * 时间: 2026-09-18
 * 作者: AxeXie
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  E2E_MANAGER,
  adminToken,
  bootstrapCredentials,
  directApiStatus,
  login,
  openAssetDetailViaCatalog,
  prepareFixtures,
  viewAllVersionsButton,
  type E2eFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;
let fixtures: E2eFixtures;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    fixtures = await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

/** 展开管理中心子菜单（默认仅在当前路径属于该父项时才展开）。 */
async function openAdminMenu(page: Page) {
  const adminCenter = page.locator('.ant-menu-submenu-title', { hasText: /管理中心|Admin Center/ });
  await expect(adminCenter).toBeVisible();
  await adminCenter.click();
}

/** 侧边导航中的叶子入口。 */
function navEntry(page: Page, label: RegExp) {
  return page.locator('.ant-menu-item').filter({ hasText: label });
}

test.describe('AC-P0B-UI-006: 管理端入口可发现', () => {
  test('授权管理员可从侧边导航发现并打开角色绑定与资源 ACL', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await openAdminMenu(page);

    const roleBindings = navEntry(page, /角色绑定|Role Bindings/);
    await expect(roleBindings).toBeVisible();
    await roleBindings.click();
    await expect(page).toHaveURL(/\/admin\/role-bindings$/);
    // 页面必须真实渲染列表容器，而不是空白或错误页。
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);

    const resourceAcls = navEntry(page, /资源 ACL|Resource ACLs/);
    await expect(resourceAcls).toBeVisible();
    await resourceAcls.click();
    await expect(page).toHaveURL(/\/admin\/resource-acls$/);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('有 project:view 的主体可从侧边导航发现并打开 Team 入口', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAdminMenu(page);

    const teams = navEntry(page, /团队管理|Team Management/);
    await expect(teams).toBeVisible();
    await teams.click();

    await expect(page).toHaveURL(/\/admin\/teams$/);
    // 无路由参数时必须给出组织选择器，而不是空白页或 404。
    await expect(page.getByText(/请先选择组织|Select an organization/)).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText(/页面不存在|Page not found/)).toHaveCount(0);
  });

  test('无 authorization:read 的主体看不到授权类入口，直接访问被 RouteGuard 拒绝', async ({
    page,
  }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAdminMenu(page);

    // 权限过滤：无 authorization:read 时授权类入口不得出现。
    await expect(navEntry(page, /角色绑定|Role Bindings/)).toHaveCount(0);
    await expect(navEntry(page, /资源 ACL|Resource ACLs/)).toHaveCount(0);
    // 同时确认菜单确实展开了（否则上面的断言会因菜单未渲染而空洞成立）。
    await expect(navEntry(page, /团队管理|Team Management/)).toBeVisible();

    // UI 不是安全边界：直接访问展示 403，且后端独立于前端守卫仍拒绝。
    await page.goto('/admin/role-bindings');
    await expect(page.getByText(/缺少访问权限|Access denied/)).toBeVisible({ timeout: 10_000 });

    const denied = await directApiStatus(
      '/system/role-bindings',
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(denied).toBe(403);
  });

  test('无 project:view 的主体看不到 Team 入口', async ({ page }) => {
    await login(page, E2E_AUTHZ_READER.username, E2E_AUTHZ_READER.password);
    await openAdminMenu(page);

    await expect(navEntry(page, /团队管理|Team Management/)).toHaveCount(0);
    // 该主体具备 authorization:read，故授权类入口仍应可见（证明是逐项过滤而非整体开关）。
    await expect(navEntry(page, /角色绑定|Role Bindings/)).toBeVisible();
  });
});

test.describe('AC-P0B-UI-006: 版本列表入口不再死链', () => {
  test('资产详情点击版本列表「查看全部」到达真实页面而非全局 404', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);

    await openAssetDetailViaCatalog(page, E2E_ASSET.displayName);

    // 版本列表面板的「查看全部」必须指向已注册路由。
    const viewAll = viewAllVersionsButton(page);
    await expect(viewAll).toBeVisible({ timeout: 10_000 });
    await viewAll.click();

    await expect(page).toHaveURL(new RegExp(`/assets/${fixtures.assetId}/versions$`));
    // 关键回归点：不得落入全局 404（此前该链接指向未注册路由，落到 NotFoundPage）。
    await expect(page.getByText(/页面不存在|Page not found/)).toHaveCount(0);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
  });
});

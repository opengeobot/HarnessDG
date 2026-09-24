/*
 * 功能: AC-P0B-UI-002 E2E 测试——权限控制菜单/按钮可见性。
 *       主体：授权管理员（authorization:read + user:read）与资产作者（无授权类 Scope）。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
import { test, expect, request } from '@playwright/test';
import {
  E2E_ASSET,
  E2E_ASSET_OUTSIDER,
  E2E_AUTHOR,
  adminToken,
  bootstrapCredentials,
  directApiGet,
  login,
  openAssetsViaNav,
  prepareFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (credentials) {
    // 保证搜索隔离用例的夹具资产与只读主体存在，消除对其它 spec 执行顺序的依赖。
    const api = await request.newContext();
    try {
      await prepareFixtures(await adminToken(api), api);
    } finally {
      await api.dispose();
    }
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('AC-P0B-UI-002: 权限菜单与按钮可见性', () => {
  test('管理员可见管理中心菜单', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);

    const adminCenter = page.locator('.ant-menu-submenu-title', {
      hasText: /管理中心|Admin Center/,
    });
    await expect(adminCenter).toBeVisible();
  });

  test('管理员可进入用户管理页面', async ({ page }) => {
    await login(page, credentials!.username, credentials!.password);

    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/admin\/users$/);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
  });

  test('无 authorization:read 的主体看不到授权类菜单项', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);

    await page.locator('.ant-menu-submenu-title', { hasText: /管理中心|Admin Center/ }).click();

    // 菜单确实展开了（以 project:view 可见的入口为锚）。
    await expect(
      page.locator('.ant-menu-item').filter({ hasText: /团队管理|Team Management/ }),
    ).toBeVisible();
    await expect(
      page.locator('.ant-menu-item').filter({ hasText: /角色绑定|Role Bindings/ }),
    ).toHaveCount(0);
  });

  test('多主体目录隔离：组织成员可见 INTERNAL 夹具，非成员持 asset:read 不可见', async ({ page }) => {
    // 夹具资产 visibility=INTERNAL（support.ensureAsset）。后端契约：
    //   - AssetApplicationService.searchAssets 入口 requirePermission(asset:read)；
    //   - AssetSearchDao.appendAccessScopeFilter 对 INTERNAL 仅放行主体所属组织作用域内的行，
    //     但对 platformAdmin（AuthorizationService 以 authorization:manage 判定）整体放行。
    // 故非成员侧必须用 E2E_ASSET_OUTSIDER（仅 asset:read、非组织成员、无 authorization:manage），
    // 不可用 E2E_MANAGER——它持 authorization:manage，行过滤对其失效，断言必然失真。
    // E2E_AUTHOR 在 e2e050-org 组织内（prepareFixtures 已加成员）——同一关键词必须得到
    // 不同结果集，证明逐项行级过滤而非整体开关。
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetsViaNav(page);
    await page
      .getByPlaceholder(/搜索名称、描述或标签|Search name, description or tags/i)
      .fill(E2E_ASSET.displayName);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('link', { name: E2E_ASSET.displayName })).toBeVisible({
      timeout: 10_000,
    });

    // 差分守护：同一关键词，组织成员作者必须查到，排除“搜索词无效导致两侧都空”的假阴性。
    const keyword = encodeURIComponent(E2E_ASSET.name);
    const member = await directApiGet<{ items?: { name: string }[] }>(
      `/assets?keyword=${keyword}&size=50`,
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(member.status).toBe(200);
    expect(
      (member.data?.items ?? []).some((a) => a.name === E2E_ASSET.name),
      '组织成员作者应能在目录查询到 INTERNAL 夹具资产（否则隔离断言失去依据）',
    ).toBe(true);

    const outsider = await directApiGet<{ items?: { name: string }[] }>(
      `/assets?keyword=${keyword}&size=50`,
      E2E_ASSET_OUTSIDER.username,
      E2E_ASSET_OUTSIDER.password,
    );
    expect(outsider.status, 'E2E_ASSET_OUTSIDER 具备 asset:read，目录查询应放行（非 403）').toBe(200);
    const visible = (outsider.data?.items ?? []).some((a) => a.name === E2E_ASSET.name);
    expect(visible, '非组织成员即使持 asset:read 也不应在目录看到 INTERNAL 夹具资产').toBe(false);
  });
});

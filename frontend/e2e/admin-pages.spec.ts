/*
 * 功能: AC-P0B-UI-003/006 E2E 测试——补齐既有 spec 从未点击过的管理端与顶级页面。
 *       逐页以侧边导航真实点击 → 断言 URL 与页面主容器渲染，并断言不出现数据加载错误态。
 *       主体按后端可见性规则分派：组织/Team/项目按成员关系过滤，故用夹具中唯一的
 *       组织成员主体 E2E_AUTHOR；运维类页面用仅持运维 Scope 的 E2E_OPS_OBSERVER，
 *       并反向断言其看不到、也进不去未授权页面；标签/权限/字典只需 Scope，用 E2E_MANAGER。
 *       登录页默认跳 /assets，无 asset:read 的主体登录后先回 /profile，
 *       避免把落地 403 与目标页状态混为一谈。
 * 时间: 2026-09-28
 * 作者: AxeXie
 */
import { test, expect, request, type Page } from '@playwright/test';
import {
  E2E_ASSET_OUTSIDER,
  E2E_AUTHOR,
  E2E_MANAGER,
  E2E_OPS_OBSERVER,
  E2E_ORG,
  adminToken,
  bootstrapCredentials,
  directApiStatus,
  login,
  prepareFixtures,
} from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    // 只需要夹具的落库副作用（用户/组织/Team 幂等就绪），断言不依赖返回值。
    await prepareFixtures(await adminToken(api), api);
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

/** 点击导航入口打开一个以表格为主容器的管理页面，并断言加载成功。 */
async function openTablePage(page: Page, label: RegExp, url: RegExp, heading: RegExp) {
  const entry = navEntry(page, label);
  await expect(entry).toBeVisible();
  await entry.click();
  await expect(page).toHaveURL(url);
  await expect(page.getByRole('heading', { name: heading })).toBeVisible({ timeout: 10_000 });
  // 表格出现说明查询已落地（加载中是 Spin、失败是 .ant-result-error，二者都不会渲染 Table）。
  await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: 10_000 });
  await expect(page.locator('.ant-result-error')).toHaveCount(0);
}

/** 无 organization:manage 的主体点行内「成员管理」必得 403（已登记为缺陷 D-10），故只断入口在位。 */
test.describe('组织成员类管理页面点击可达（E2E_AUTHOR）', () => {
  test('组织列表按成员关系返回夹具组织，行内操作入口在位', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAdminMenu(page);

    const orgs = navEntry(page, /组织管理|Organization Management/);
    await expect(orgs).toBeVisible();
    await orgs.click();
    await expect(page).toHaveURL(/\/admin\/organizations$/);
    await expect(page.getByRole('heading', { name: /组织管理/ })).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
    // 数据断言：AUTHOR 是 e2e050-org 成员，后端可见性过滤后必须看到该行。
    await expect(page.locator('.ant-table-row', { hasText: E2E_ORG.name })).toBeVisible();
    await expect(page.getByRole('button', { name: '成员管理' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Team 管理' }).first()).toBeVisible();
  });

  test('Team 页选定组织上下文后加载表格', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAdminMenu(page);

    const teams = navEntry(page, /团队管理|Team Management/);
    await teams.click();
    await expect(page).toHaveURL(/\/admin\/teams$/);
    // 无路由参数时先给选择提示，而不是空白或 404。
    await expect(page.getByText(/请先选择组织以查看其 Team/)).toBeVisible({ timeout: 10_000 });

    await page.locator('.ant-select .ant-select-selector').first().click();
    await page.locator('.ant-select-item-option', { hasText: E2E_ORG.code }).first().click();
    await expect(page.getByText(/请先选择组织以查看其 Team/)).toHaveCount(0);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('项目页选定组织上下文后加载表格', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAdminMenu(page);

    const projects = navEntry(page, /项目管理|Project Management/);
    await projects.click();
    await expect(page).toHaveURL(/\/admin\/projects$/);
    await expect(page.getByRole('heading', { name: /项目管理/ })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/请先选择组织以查看项目/)).toBeVisible();

    await page.locator('.ant-select .ant-select-selector').first().click();
    await page.locator('.ant-select-item-option', { hasText: E2E_ORG.code }).first().click();
    await expect(page.getByText(/请先选择组织以查看项目/)).toHaveCount(0);
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

test.describe('运维类管理页面点击可达（E2E_OPS_OBSERVER）', () => {
  test('运维主体逐页打开任务管理、幂等记录、系统告警、系统依赖', async ({ page }) => {
    await login(page, E2E_OPS_OBSERVER.username, E2E_OPS_OBSERVER.password);
    // 该主体无 asset:read，登录落地页是 /assets 的 403；先回到人人可进的 /profile。
    await page.goto('/profile');
    await openAdminMenu(page);

    await openTablePage(page, /任务管理|Job Management/, /\/admin\/jobs$/, /任务管理/);
    await openTablePage(page, /幂等记录|Idempotency/, /\/admin\/idempotency$/, /幂等记录/);
    await openTablePage(page, /系统告警|Alerts/, /\/admin\/alerts$/, /系统告警/);

    // 系统依赖页由「依赖健康表格 + 指标摘要 Descriptions」两块组成，两块都必须真实落地。
    const deps = navEntry(page, /系统依赖|Dependencies/);
    await deps.click();
    await expect(page).toHaveURL(/\/admin\/dependencies$/);
    await expect(page.getByRole('heading', { name: /系统依赖/ })).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-descriptions')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('运维主体打开配置管理、审计日志与通知中心（含系统管理 Tab）', async ({ page }) => {
    await login(page, E2E_OPS_OBSERVER.username, E2E_OPS_OBSERVER.password);
    await page.goto('/profile');
    await openAdminMenu(page);

    await openTablePage(page, /配置管理|Configuration Management/, /\/admin\/configurations$/, /配置管理/);
    await openTablePage(page, /审计日志|Audit Logs/, /\/admin\/audit-logs$/, /审计日志/);
    await openTablePage(page, /通知中心|Notification Center/, /\/admin\/notifications$/, /通知中心/);

    // 通知中心「系统管理」Tab 的后端端点要求 system:observe，该主体具备，切换后不得出现错误态。
    const adminTab = page.getByRole('tab', { name: /系统管理/ });
    await expect(adminTab).toBeVisible();
    await adminTab.click();
    // antd Tabs 保留非活跃面板（隐藏），断言必须限定在激活面板内。
    await expect(page.locator('.ant-tabs-tabpane-active .ant-table').first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });

  test('运维主体看不到越权入口，直连未授权页面被 RouteGuard 与后端双重拒绝', async ({ page }) => {
    await login(page, E2E_OPS_OBSERVER.username, E2E_OPS_OBSERVER.password);
    await page.goto('/profile');
    await openAdminMenu(page);

    for (const label of [/用户管理|User Management/, /组织管理|Organization Management/,
      /团队管理|Team Management/, /项目管理|Project Management/,
      /字典管理|Dictionary Management/, /标签管理|Tag Management/,
      /访问与凭据|Access & Credentials/]) {
      await expect(navEntry(page, label)).toHaveCount(0);
    }
    // 证明菜单确实展开了（否则上面的缺失断言会因菜单未渲染而空洞成立）。
    await expect(navEntry(page, /任务管理|Job Management/)).toBeVisible();

    await page.goto('/admin/users');
    await expect(page.getByText(/缺少访问权限|Access denied/)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/所需权限: user:read/)).toBeVisible();
    await expect(page.locator('.ant-table')).toHaveCount(0);

    // UI 不是安全边界：后端独立拒绝同一请求。
    const denied = await directApiStatus(
      '/system/users',
      E2E_OPS_OBSERVER.username,
      E2E_OPS_OBSERVER.password,
    );
    expect(denied).toBe(403);
  });
});

test.describe('其余管理页面点击可达（E2E_MANAGER）', () => {
  test('标签管理、权限清单、字典管理逐页可达', async ({ page }) => {
    await login(page, E2E_MANAGER.username, E2E_MANAGER.password);
    await openAdminMenu(page);

    await openTablePage(page, /标签管理|Tag Management/, /\/admin\/tags$/, /标签管理/);
    await openTablePage(page, /权限清单|Permission List/, /\/admin\/permissions$/, /权限清单/);

    // 字典管理左侧是字典类型 List（Flyway 种子保证非空），右侧在选中类型后出现条目表。
    const dicts = navEntry(page, /字典管理|Dictionary Management/);
    await dicts.click();
    await expect(page).toHaveURL(/\/admin\/dictionaries$/);
    await expect(page.getByRole('heading', { name: /字典管理/ })).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-list-item').first()).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('.ant-result-error')).toHaveCount(0);
  });
});

test.describe('顶级入口页点击可达（E2E_AUTHOR）', () => {
  test('版本中心、上传中心、发布审批给出死链引导而不是 404 或权限拒绝', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);

    const cases: Array<[RegExp, RegExp, string]> = [
      [/版本中心|Version Center/, /\/version$/, '请从资产详情页进入，或携带 ?assetId= 查询参数'],
      [/上传中心|Upload Center/, /\/upload$/, '请从资产详情页进入，或携带 ?assetId=&versionId= 查询参数'],
      [/发布审批|Review Center/, /\/review$/, '请从资产详情页进入，或携带 ?assetId= 查询参数'],
    ];
    for (const [label, url, hint] of cases) {
      const entry = navEntry(page, label);
      await expect(entry).toBeVisible();
      await entry.click();
      await expect(page).toHaveURL(url);
      await expect(page.locator('.ant-card-head-title', { hasText: /版本中心|上传中心|发布审批/ })).toBeVisible({
        timeout: 10_000,
      });
      await expect(page.locator('.ant-empty')).toBeVisible();
      await expect(page.getByText(hint)).toBeVisible();
      await expect(page.getByText(/页面不存在|Page not found/)).toHaveCount(0);
      await expect(page.getByText(/缺少访问权限|Access denied/)).toHaveCount(0);
    }
  });
});

test.describe('访问与凭据入口权限边界（E2E_ASSET_OUTSIDER）', () => {
  test('无 authorization:read 的主体看不到入口，直连展示 403', async ({ page }) => {
    await login(page, E2E_ASSET_OUTSIDER.username, E2E_ASSET_OUTSIDER.password);

    // 顶级菜单本身按权限过滤，无需展开子菜单；先证明菜单已渲染。
    await expect(navEntry(page, /资产目录|Asset Catalog/)).toBeVisible();
    await expect(navEntry(page, /访问与凭据|Access & Credentials/)).toHaveCount(0);

    await page.goto('/access');
    await expect(page.getByText(/缺少访问权限|Access denied/)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/所需权限: authorization:read/)).toBeVisible();
  });
});

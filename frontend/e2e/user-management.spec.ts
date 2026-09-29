/*
 * 功能: AC-P0B-IAM-012 E2E 测试——用户管理 CRUD（创建/检索/禁用启用/重置密码）。
 * 时间: 2026-09-28
 * 作者: AxeXie
 *
 * <p>写旅程必须以引导管理员执行：后端 SystemUserController 的 create/enable/disable/
 * reset-password 均强制 {@code user:manage}，而夹具 E2E_MANAGER 仅持有 {@code user:read}（会 403）；
 * 引导管理员的 ADMIN_SCOPES 恰好覆盖 user:read + user:manage，前端守卫与后端鉴权同时通过。
 * <p>禁用/重置密码只作用在本用例自建的用户上：REQ-IAM-006 禁止禁用最后一个持有 user:manage 的
 * 活跃平台管理员，故引导管理员自身不可作为禁用目标。
 * <p>每个用例各自经 UI 建用户（用户名含时间戳与 retry 序号），互不依赖，可单独重跑。
 */
import { test, expect, type Page } from '@playwright/test';
import { bootstrapCredentials, loginBootstrap } from './support';

/** antd 对恰好两个汉字的按钮插入空格（「确 定」「禁 用」「启 用」），故一律用宽松正则。 */
const CONFIRM_OK = /确\s*定|OK/i;
/** 满足后端 PasswordPolicy.defaults()：长度 ≥12 且至少三类字符。 */
const TEMP_PASSWORD = 'E2e0928-Tmp-01!z';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(() => {
  credentials = bootstrapCredentials();
});

/** 用页面真实的搜索框（Input.Search，无独立搜索按钮）按用户名过滤列表。 */
async function searchUsers(page: Page, keyword: string) {
  await page.getByPlaceholder(/搜索用户名\/显示名|Search username/).fill(keyword);
  await page.keyboard.press('Enter');
}

/** 定位包含指定用户名的表格行（用户名渲染在 code 元素中）。 */
function userRow(page: Page, username: string) {
  return page.locator('.ant-table-row').filter({ has: page.locator('code', { hasText: username }) });
}

/**
 * 经真实 UI 旅程创建一个一次性用户并等待其出现在列表首屏。
 *
 * <p>返回的用户名对每次执行与每次 retry 都唯一，避免 409 USER_ALREADY_EXISTS。
 */
async function createUserViaUi(page: Page, tag: string): Promise<string> {
  const username = `e2e0928-${tag}-${Date.now().toString(36)}-${test.info().retry}`;
  const displayName = `E2E 0928 ${tag}`;

  await page.getByRole('button', { name: /创建用户|createUser/i }).click();
  const modal = page.locator('.ant-modal-content').filter({ hasText: /创建用户/ });
  await expect(modal).toBeVisible();
  await modal.getByLabel(/用户名|username/i).fill(username);
  await modal.getByLabel(/显示名|displayName/i).fill(displayName);
  await modal.getByLabel(/临时密码|temporaryPassword/i).fill(TEMP_PASSWORD);
  await modal.getByRole('button', { name: CONFIRM_OK }).click();

  await expect(page.locator('.ant-message').getByText(/用户已创建|User created/)).toBeVisible({
    timeout: 15_000,
  });
  await expect(modal).toBeHidden({ timeout: 10_000 });
  return username;
}

test.describe('AC-P0B-IAM-012: 用户管理 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
    await loginBootstrap(page);
    await page.goto('/admin/users');
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10_000 });
  });

  test('创建用户后状态为 PENDING_ACTIVATION 且强制改密', async ({ page }) => {
    const username = await createUserViaUi(page, 'create');

    // 列表按 createdAt 倒序 + 服务端分页，但新建项必然在首屏；仍走真实搜索路径以免受历史夹具干扰。
    await searchUsers(page, username);
    const row = userRow(page, username);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row).toContainText('E2E 0928 create');
    // createUser 落库即为 PENDING_ACTIVATION（临时口令未改），状态列渲染原始枚举 Tag。
    await expect(row.getByText('PENDING_ACTIVATION')).toBeVisible();
    await expect(row.getByText('是', { exact: true })).toBeVisible();
  });

  test('禁用用户吊销 Token 后启用恢复 ACTIVE', async ({ page }) => {
    const username = await createUserViaUi(page, 'toggle');
    await searchUsers(page, username);
    const row = userRow(page, username);
    await expect(row).toBeVisible({ timeout: 10_000 });

    await row.getByRole('button', { name: /禁\s*用|disable/i }).click();
    const popconfirm = page.locator('.ant-popconfirm');
    await expect(popconfirm).toBeVisible();
    // 断言确认文案，证明「Token 将被吊销」的告知语义存在于 UI。
    await expect(popconfirm).toContainText(/其 Token 将被吊销/);
    await popconfirm.getByRole('button', { name: CONFIRM_OK }).click();

    await expect(page.locator('.ant-message').getByText(/已禁用|disabled/i)).toBeVisible({
      timeout: 15_000,
    });
    await expect(row.getByText('DISABLED')).toBeVisible();
    // DISABLED 后行内按钮切换为「启用」，不再有禁用入口。
    await expect(row.getByRole('button', { name: /禁\s*用|disable/i })).toHaveCount(0);

    await row.getByRole('button', { name: /启\s*用|enable/i }).click();
    await expect(page.locator('.ant-message').getByText(/已启用|enabled/i)).toBeVisible({
      timeout: 15_000,
    });
    // LocalUser.enable 显式置 ACTIVE（区别于此前的 PENDING_ACTIVATION）。
    await expect(row.getByText('ACTIVE')).toBeVisible();
  });

  test('重置密码写入临时口令并重新要求改密', async ({ page }) => {
    const username = await createUserViaUi(page, 'reset');
    await searchUsers(page, username);
    const row = userRow(page, username);
    await expect(row).toBeVisible({ timeout: 10_000 });

    // 新建用户是 PENDING_ACTIVATION，行内没有「启用」入口（UsersPage 仅在 DISABLED 时渲染）。
    // 因此走真实旅程 禁用 → 启用 把它提升为 ACTIVE，才能证明重置动作真的回退了状态。
    await row.getByRole('button', { name: /禁\s*用|disable/i }).click();
    const disableConfirm = page.locator('.ant-popconfirm');
    await expect(disableConfirm).toBeVisible();
    await disableConfirm.getByRole('button', { name: CONFIRM_OK }).click();
    await expect(row.getByText('DISABLED')).toBeVisible({ timeout: 15_000 });

    await row.getByRole('button', { name: /启\s*用|enable/i }).click();
    // LocalUser.enable 显式置 ACTIVE。
    await expect(row.getByText('ACTIVE')).toBeVisible({ timeout: 15_000 });

    await row.getByRole('button', { name: /重置密码|resetPassword/i }).click();
    const modal = page.locator('.ant-modal-content').filter({ hasText: /重置密码/ });
    await expect(modal).toBeVisible();
    await modal.getByLabel(/临时密码|temporaryPassword/i).fill(`${TEMP_PASSWORD}${Date.now().toString(36)}`);
    await modal.getByRole('button', { name: CONFIRM_OK }).click();

    await expect(page.locator('.ant-message').getByText(/已重置临时密码|password reset/i)).toBeVisible({
      timeout: 15_000,
    });
    await expect(modal).toBeHidden({ timeout: 10_000 });
    // D-01 修复后 resetMutation.onSuccess 会 invalidate 列表：无需手动「刷新」，
    // 行内状态必须由 UI 自行回显为重置后的真值（此前的缺陷是列表停留在重置前状态）。
    const updated = userRow(page, username);
    await expect(updated.getByText('是', { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(updated.getByText('PENDING_ACTIVATION')).toBeVisible();
    // 再点一次「刷新」确认回显值与后端真值一致，而非前端的乐观状态。
    await page.getByRole('button', { name: /刷\s*新|refresh/i }).click();
    const refetched = userRow(page, username);
    await expect(refetched.getByText('是', { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(refetched.getByText('PENDING_ACTIVATION')).toBeVisible();
  });

  test('临时口令不足 12 位被前端校验拦截', async ({ page }) => {
    await page.getByRole('button', { name: /创建用户|createUser/i }).click();
    const modal = page.locator('.ant-modal-content').filter({ hasText: /创建用户/ });
    await expect(modal).toBeVisible();
    await modal.getByLabel(/用户名|username/i).fill(`e2e0928-short-${Date.now().toString(36)}`);
    await modal.getByLabel(/显示名|displayName/i).fill('E2E 0928 弱口令');
    await modal.getByLabel(/临时密码|temporaryPassword/i).fill('Short0928!');
    await modal.getByRole('button', { name: CONFIRM_OK }).click();

    await expect(page.getByText(/至少 12 位|at least 12/i)).toBeVisible();
    // 校验未通过则不得发出写请求：弹窗必须仍打开。
    await expect(modal).toBeVisible();
  });
});

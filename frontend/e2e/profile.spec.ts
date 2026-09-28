/*
 * 功能: AC-P0B-IAM-003 E2E 测试——Profile 账户信息与改密校验。
 * 时间: 2026-09-28
 * 作者: AxeXie
 *
 * 运行前提: docker compose up（验收栈）+ E2E_* 环境变量。
 *
 * <p>刻意不使用引导管理员作为被测主体：改密用例以「错误的当前口令」提交，
 * 只验证后端拒绝路径的 UI 回显，不产生口令漂移；夹具主体 E2E_AUTHOR 的
 * displayName 由 prepareFixtures 固定写入，可作确定性断言依据。
 */
import { test, expect, request, type Page } from '@playwright/test';
import { E2E_AUTHOR, adminToken, bootstrapCredentials, login, prepareFixtures } from './support';

let credentials: { username: string; password: string } | null = null;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    // 幂等确保 E2E_AUTHOR 存在并已解除首登强制改密门，否则 /profile 无法到达
    await prepareFixtures(await adminToken(api), api);
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

/** 提交按钮：antd 对两汉字按钮插入空格（「提 交」），故用允许空白的正则。 */
function submitButton(page: Page) {
  return page.getByRole('button', { name: /提\s*交|submit/i });
}

/**
 * 按 antd Form.Item 生成的字段 id 定位口令输入框。
 *
 * <p>不可用 getByLabel(/新密码/)：该正则同时命中「新密码」与「确认新密码」两个
 * input（实测 strict mode violation 解析到 2 个元素）。id 由 Form name 派生，
 * 与语言包无关，因此比可见文案更稳定。
 */
function passwordField(page: Page, name: 'currentPassword' | 'newPassword' | 'confirmPassword') {
  return page.locator(`#${name}`);
}

test.describe('AC-P0B-IAM-003: Profile 改密', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await page.goto('/profile');
    await expect(page.getByRole('heading', { name: /个人中心|Profile/i })).toBeVisible();
  });

  test('/profile 展示账户信息', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /个人中心|Profile/i })).toBeVisible();
    const info = page.locator('.ant-card').filter({ hasText: /账户信息|Account Information/ });
    const item = (label: RegExp) =>
      info.locator('.ant-descriptions-row').filter({ hasText: label }).locator('.ant-descriptions-item-content');

    // 主体 ID 是页面其它字段的比对基准：先取真值再断言，避免硬编码 prn_xxx。
    const principalId = (await item(/主体 ID|principalId/i).innerText()).trim();
    await expect(item(/主体 ID|principalId/i)).toHaveText(/^prn_/);
    // 用户 ID 与主体 ID 是两个独立标识（LocalUser.userId vs principalId）。
    await expect(item(/用户 ID|userId/i)).not.toHaveText(principalId);
    // 显示名回显 ensureUser 写入的 `E2E 050 <username>`
    await expect(item(/显示名|displayName/i)).toHaveText(`E2E 050 ${E2E_AUTHOR.username}`);
    await expect(item(/语言|language/i)).toHaveText('zh-CN');
    // Scope 标签来自 JWT 有效 Scope，证明页面读取的是共享主体上下文而非本地拼装
    for (const scope of E2E_AUTHOR.scopes) {
      await expect(info.getByText(scope, { exact: true })).toBeVisible();
    }
  });

  // 设计缺陷（已登记，未修）：PRD 的「登录名」应是本地用户 username，但后端
  // CurrentPrincipalView.subject 三处（toPrincipalView / currentPrincipal /
  // PrincipalQueryApplicationService）一律以 principalId 填充，/profile 因此把
  // 主体 ID 当登录名展示。夹具用户 e2e050-asset-owner 在页面上完全不可见。
  // 修复需要后端改动，超出 TASK-P0BR-065 的 allowedPaths（forbiddenPaths 含 backend），
  // 故以 fixme 固定住设计口径：修复落地后去掉 fixme 即成为回归断言。
  test.fixme('登录名回显本地用户 username', async ({ page }) => {
    const info = page.locator('.ant-card').filter({ hasText: /账户信息|Account Information/ });
    await expect(
      info.locator('.ant-descriptions-row').filter({ hasText: /登录名|loginName/i })
        .locator('.ant-descriptions-item-content'),
    ).toHaveText(E2E_AUTHOR.username);
  });

  test('改密表单提交后回显后端拒绝原因且不离开页面', async ({ page }) => {
    await passwordField(page, 'currentPassword').fill('Wrong-Current-01!x');
    await passwordField(page, 'newPassword').fill('E2e0928-NewValid-01!z');
    await passwordField(page, 'confirmPassword').fill('E2e0928-NewValid-01!z');
    await submitButton(page).click();

    // 后端 changePassword 对旧口令不符返回 AUTH_INVALID_CREDENTIALS，
    // ProfilePage.onError 以 message.error(err.message) 回显；口令未变更
    await expect(page.locator('.ant-message').getByText(/密码修改失败|invalid credentials|Unauthorized|凭据/i).first())
      .toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveURL(/\/profile$/);
  });

  test('两次新密码不一致由前端校验拦截', async ({ page }) => {
    await passwordField(page, 'currentPassword').fill(E2E_AUTHOR.password);
    await passwordField(page, 'newPassword').fill('E2e0928-NewValid-01!z');
    await passwordField(page, 'confirmPassword').fill('E2e0928-OtherValid-02!z');
    await submitButton(page).click();

    await expect(page.getByText('两次输入的密码不一致')).toBeVisible();
    // 校验失败不得发出请求：不应出现后端消息条
    await expect(page.locator('.ant-message')).toHaveCount(0);
  });

  test('新密码长度不足 12 位由前端校验拦截', async ({ page }) => {
    await passwordField(page, 'currentPassword').fill(E2E_AUTHOR.password);
    await passwordField(page, 'newPassword').fill('short');
    await passwordField(page, 'confirmPassword').fill('short');
    await submitButton(page).click();

    await expect(page.getByText('密码至少 12 位')).toBeVisible();
    await expect(page.locator('.ant-message')).toHaveCount(0);
  });
});

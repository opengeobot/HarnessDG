/*
 * 功能: 发布链 E2E——资产详情页版本门控（提交发布禁用态）、DVC 入口按资产类型出现、
 *       DVC 短期凭据 Modal 掩码 secretKey、DRAFT 下载 409 业务错误呈现、
 *       download/DVC 端点后端独立授权（UI 非安全边界）。
 *       断言全部取自 AssetDetailPage 当前代码与运行态实证的错误体（409 原文
 *       "only PUBLISHED versions can be downloaded"），无条件式：夹具缺失或后端放行即失败。
 *       已知限制：DRAFT→VALIDATING 之后的校验/审批正向作业链在运行态断链
 *       （与 review-workflow.spec 同一缺口），本 spec 不点击推进，缺口记入 Evidence limitations。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import {
  adminToken,
  bootstrapCredentials,
  directApiGet,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  E2E_DVC_DATASET,
  E2E_PUBLISH_ASSET,
  ensureAssetVersion,
  ensurePublishFlowAssets,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/** 私有夹具版本：落在 publish-flow 专属资产上，与共享夹具的 1.0.x 命名空间彻底隔离。 */
const PUBLISH_VERSION = '2.0.0'; // 私有 MODEL 资产上的唯一 DRAFT 版本
const DVC_VERSION = '2.1.0'; // 私有 DATASET 资产上的 DRAFT 版本（DVC 按钮要求存在选中版本）

interface Fixtures extends E2eFixtures {
  /** publish-flow 专属 MODEL 资产（发布门控 / 下载 409 用例）。 */
  publishAssetId: string;
  /** publish-flow 专属 DATASET 资产（DVC 入口 / 凭据 / 403 用例）。 */
  dvcAssetId: string;
  draftVersionId: string;
}

let credentials: { username: string; password: string } | null = null;
let fixtures: Fixtures;

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function dataOf(body: unknown): unknown {
  if (body && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return null;
}

async function readJson(response: { json: () => Promise<unknown> }): Promise<unknown> {
  return response.json().catch(() => null);
}

async function loginApi(api: APIRequestContext, username: string, password: string): Promise<string> {
  const response = await api.post(`${API_BASE}/auth/login`, {
    data: { username, password },
    failOnStatusCode: false,
  });
  const data = dataOf(await readJson(response)) as { accessToken?: string } | null;
  const token = data?.accessToken;
  expect(token, `夹具主体 ${username} 登录失败（HTTP ${response.status()}）`).toBeTruthy();
  return token as string;
}

/** 版本切换器所在 Card（含「当前版本」文案），用于限定按钮与 Select。 */
function switcherCard(page: Page) {
  return page.locator('.ant-card').filter({ hasText: /当前版本|Current version/ });
}

/** 通过详情页版本切换器真实点选目标版本（默认选中项不保证为目标版本）。 */
async function selectVersion(page: Page, optionText: string) {
  // 点击 .ant-select 外层而非 role=combobox 的 input：antd 的选中项 span 会遮挡 input，
  // Playwright 命中检测将无限重试（实测 30s 超时）。
  const selector = switcherCard(page).locator('.ant-select');
  await selector.click();
  await page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .filter({ hasText: optionText })
    .click();
  // 切换必须真实生效：按钮区按 selectedVersion.status 重渲染，先确认选中的是目标版本。
  await expect(selector).toContainText(optionText);
}

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) {
    return;
  }
  const api = await request.newContext();
  try {
    const base = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const { publishAssetId, dvcAssetId } = await ensurePublishFlowAssets(api, base, authorToken);
    const draftVersionId = await ensureAssetVersion(api, authorToken, publishAssetId, PUBLISH_VERSION);
    await ensureAssetVersion(api, authorToken, dvcAssetId, DVC_VERSION);
    fixtures = { ...base, publishAssetId, dvcAssetId, draftVersionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
});

test.describe('资产详情页：发布门控与 DVC 入口（E2E 050 夹具）', () => {
  test('MODEL 夹具 DRAFT 版本：提交发布审核禁用，且不出现 DVC 入口', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, E2E_PUBLISH_ASSET.displayName);
    await selectVersion(page, `${PUBLISH_VERSION} (DRAFT)`);

    const switcher = switcherCard(page);
    const submitButton = switcher.getByRole('button', { name: /提交发布审核|Submit for review/ });
    await expect(submitButton).toBeVisible();
    // AssetDetailPage L588：仅 PENDING_REVIEW/VALIDATING 可点，DRAFT 必须禁用。
    await expect(submitButton).toBeDisabled();
    // AssetDetailPage L596：DVC 两按钮受 asset.type === 'DATASET' 门控。
    await expect(switcher.getByRole('button', { name: /DVC 配置|DVC config/ })).toHaveCount(0);
    await expect(switcher.getByRole('button', { name: /DVC 凭据|DVC credentials/ })).toHaveCount(0);
  });

  test('DATASET 夹具：DVC 短期凭据 Modal 掩码 secretKey 且不泄漏后端真值', async ({ page }) => {
    // 数据前提：后端确已签发凭据（STS 或 scoped 回退链路可用）。注意 issueCredentials
    // 非幂等（STS 模式每次新 accessKeyId），故只以此为前提、不做跨调用值一致性断言。
    const issued = await directApiGet<{ accessKey?: string; secretKey?: string }>(
      `/assets/${fixtures.dvcAssetId}/dvc/credentials`,
      E2E_AUTHOR.username,
      E2E_AUTHOR.password,
    );
    expect(issued.status, 'DVC 凭据端点应 200').toBe(200);
    expect(issued.data?.accessKey, '后端未返回 accessKey').toBeTruthy();
    expect(issued.data?.secretKey, '后端未返回 secretKey').toBeTruthy();

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, E2E_DVC_DATASET.displayName);
    await selectVersion(page, `${DVC_VERSION} (DRAFT)`);

    const switcher = switcherCard(page);
    await expect(switcher.getByRole('button', { name: /DVC 配置|DVC config/ })).toBeVisible();
    await switcher.getByRole('button', { name: /DVC 凭据|DVC credentials/ }).click();

    const modal = page
      .locator('.ant-modal')
      .filter({ hasText: /DVC 短期凭据|DVC short-term credentials/ });
    await expect(modal).toBeVisible({ timeout: 10_000 });
    // AssetDetailPage L699-716：警告 Alert + secretKey 恒渲染固定掩码。
    await expect(modal.getByText(/短期敏感信息|sensitive data/)).toBeVisible();
    // 掩码恰好一处：证明只有 secretKey 被掩码，accessKey 行渲染的是真值而非同一占位符。
    await expect(modal.getByText('••••••••', { exact: true })).toHaveCount(1);
    // bucket/prefix 为后端确定性值，证明 Modal 渲染的是真实签发数据。
    await expect(modal.getByText('dvc-cache', { exact: true })).toBeVisible();
    await expect(modal.getByText(`${fixtures.dvcAssetId}/`, { exact: true })).toBeVisible();
    // 泄漏红线：后端真值 secretKey 绝不允许出现在 Modal 中。
    await expect(modal.getByText(issued.data!.secretKey!, { exact: true })).toHaveCount(0);
  });
});

test.describe('下载链：状态机强制与后端独立授权', () => {
  test('DRAFT 版本点击下载：呈现 409 业务错误且不弹出下载票据', async ({ page }) => {
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, E2E_PUBLISH_ASSET.displayName);
    await selectVersion(page, `${PUBLISH_VERSION} (DRAFT)`);

    // 下载按钮无版本状态门控（AssetDetailPage L593），点击后必须由后端拒绝。
    await switcherCard(page).getByRole('button', { name: /下载版本|Download version/ }).click();

    const messageError = page.locator('.ant-message-error');
    await expect(messageError).toBeVisible({ timeout: 10_000 });
    // Compose 运行态实证：GlobalExceptionHandler 透传 ConflictException 英文原文。
    await expect(messageError).toContainText('only PUBLISHED versions can be downloaded');
    await expect(
      page.locator('.ant-modal-title').filter({ hasText: /下载票据|Download ticket/ }),
    ).toHaveCount(0);
  });

  test('无 asset:read 的主体直连 download 与 dvc/credentials → 403', async () => {
    // DownloadApplicationService L67 与 DvcConfigurationService L51/L88 均首行强制 asset:read；
    // E2E_AUTHZ_READER 仅持有 authorization:read，后端必须独立于 UI 守卫拒绝。
    const api = await request.newContext();
    try {
      const readerToken = await loginApi(api, E2E_AUTHZ_READER.username, E2E_AUTHZ_READER.password);
      const download = await api.get(
        `${API_BASE}/versions/${fixtures.draftVersionId}/download`,
        { headers: bearer(readerToken), failOnStatusCode: false },
      );
      expect(download.status()).toBe(403);
      expect(((await readJson(download)) as { code?: string } | null)?.code).toBe('AUTH_PERMISSION_DENIED');

      const credentials = await api.get(
        `${API_BASE}/assets/${fixtures.dvcAssetId}/dvc/credentials`,
        { headers: bearer(readerToken), failOnStatusCode: false },
      );
      expect(credentials.status()).toBe(403);
      expect(((await readJson(credentials)) as { code?: string } | null)?.code).toBe('AUTH_PERMISSION_DENIED');
    } finally {
      await api.dispose();
    }
  });
});

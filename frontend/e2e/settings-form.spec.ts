/*
 * 功能: E4 资产 Settings 表单编辑 E2E（TASK-P0BR-069 §5，切片 8/9/10）——真实 Compose 栈上验证
 *       元数据编辑持久化 + 双语切换、空展示名称提交的真实失败态、并发版本冲突提示。
 * 时间: 2026-09-30
 * 作者: AxeXie
 *
 * 诚实边界（与卡 §5 切片 9 口径的偏差，详见 EVD-P0BR-069-001）：
 *   卡面假设「展示名称必填校验拦截、无请求发出」。实测取证：AssetSettingsPage 元数据
 *   Form 无 rules（本卡 §4 明确禁改该页面），清空展示名称提交会真实发出 PATCH，
 *   后端对空 displayName 返回 500 INTERNAL_ERROR（探针确证，事务回滚）。
 *   本 spec 因此如实断言当前真实行为：请求发出且失败、错误提示出现、远端值未被成功覆盖。
 *   ——不得用 skip/放宽断言掩盖该产品缺陷，缺陷修复须另立卡。
 *
 *   取证的两项后端契约违背（详见 EVD，均须另立 backend 卡修复，本卡 forbiddenPaths 不含 backend）：
 *     [缺陷 A] PATCH /assets/{id} 部分更新会静默清空未提交字段——Asset.updateMetadata 对
 *              displayName/organizationId/projectId/license 无条件赋值（null 也覆盖），
 *              与 UpdateAssetRequest javadoc 及 PAD「为空表示不变更」矛盾。
 *     [缺陷 B] 对 displayName 为 NULL 的资产再 PATCH 一个非空 displayName，会走 rename→
 *              insertAlias；若旧坐标别名行已存在则触发 ux_asset_alias_coordinate 唯一键冲突，
 *              PostgreSQL「current transaction is aborted」→ 500，资产无法经 PATCH 修复。
 *   为在不触碰上述缺陷的前提下让「真实 UI 点击旅程」稳定可重跑，本 spec 夹具一律发送
 *   完整治理字段元组（避免只传部分字段触发 [缺陷 A] 把 displayName/organizationId 清成 NULL，
 *   进而因 [缺陷 B] 使夹具彻底不可归正）——这是夹具卫生，不放宽任何被测断言。
 *
 * 夹具独立性：目录检索（AssetSearchDao）过滤 provisioning_status='COMPLETED' 且按组织访问域
 *   下推可见性；新建资产须等异步建仓完成才可经目录检索导航。ensure 轮询建仓并在每次测试前
 *   把 displayName/描述/可见性/organizationId 归正到基线，保证切片可独立重跑。
 *   夹具坐标为专用新坐标（历史 ast_01m3qzgjehtr7fc54xncvz2ymv 因 [缺陷 A/B] 已损坏不可复用）。
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  adminToken,
  bootstrapCredentials,
  E2E_AUTHOR,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  openAssetSettingsViaNav,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const __dirnameE2e = dirname(fileURLToPath(import.meta.url));
const COMPOSE_DIR = resolve(__dirnameE2e, '..', '..', 'deploy', 'compose');
const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/**
 * 独立 DATASET 夹具坐标（E2E_AUTHOR 自有资产）。
 * v2 坐标：v1（e2e069-settings-form-target）因后端 [缺陷 A/B]（见文件头）已被
 * 部分更新清空 displayName 且 rename 别名冲突导致无法归正，弃用换新坐标。
 */
const SETTINGS_ASSET = {
  namespace: 'nlp',
  name: 'e2e069-settings-target-v2',
  displayName: 'E2E 069 设置表单目标 v2',
  format: 'PARQUET',
  modality: 'TEXT',
} as const;
const BASELINE_DESCRIPTION = 'E2E 069 settings baseline';

/* ------------------------------------------------------------------ */
/* 内部辅助（与 upload-flow.spec 同构，support 私有件不可复用）          */
/* ------------------------------------------------------------------ */

function bearer(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function dataOf(body: unknown): unknown {
  if (body && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return null;
}

async function readJson(resp: { json: () => Promise<unknown> }): Promise<unknown> {
  return resp.json().catch(() => null);
}

async function loginApi(api: APIRequestContext, username: string, password: string): Promise<string> {
  const resp = await api.post(`${API_BASE}/auth/login`, {
    data: { username, password },
    failOnStatusCode: false,
  });
  const data = dataOf(await readJson(resp)) as { accessToken?: string } | null;
  expect(data?.accessToken, `登录 ${username} 失败（HTTP ${resp.status()}）`).toBeTruthy();
  return data!.accessToken!;
}

interface AssetDetail {
  assetId?: string;
  displayName?: string;
  description?: string | null;
  visibility?: string;
  status?: string;
  rowVersion?: number;
  provisioningStatus?: string;
  organizationId?: string | null;
}

async function getAssetDetail(
  api: APIRequestContext,
  token: string,
  assetId: string,
): Promise<AssetDetail> {
  const resp = await api.get(`${API_BASE}/assets/${assetId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  expect(resp.status(), `GET 资产详情返回 HTTP ${resp.status()}`).toBe(200);
  return (dataOf(await readJson(resp)) as AssetDetail | null) ?? {};
}

/** 轮询异步建仓完成（目录检索门槛 provisioning_status='COMPLETED'）。 */
async function waitForProvisioned(api: APIRequestContext, token: string, assetId: string): Promise<void> {
  const deadline = Date.now() + 90_000;
  for (;;) {
    const detail = await getAssetDetail(api, token, assetId);
    if (detail.provisioningStatus === 'COMPLETED') return;
    expect(
      detail.provisioningStatus,
      `建仓失败（provisioningStatus=${detail.provisioningStatus}），目录检索将永远不可见`,
    ).not.toBe('FAILED');
    expect(Date.now(), `建仓轮询超时（当前 ${detail.provisioningStatus}）`).toBeLessThan(deadline);
    await new Promise((r) => setTimeout(r, 2_000));
  }
}

/** 幂等创建独立 DATASET 夹具：201 直取，否则按坐标查回；随后等建仓完成。 */
async function ensureSettingsAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e069_settings_${SETTINGS_ASSET.name}` },
    data: {
      type: 'DATASET',
      namespace: SETTINGS_ASSET.namespace,
      name: SETTINGS_ASSET.name,
      displayName: SETTINGS_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: SETTINGS_ASSET.format, modality: SETTINGS_ASSET.modality },
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    await waitForProvisioned(api, authorToken, createdData.assetId);
    return createdData.assetId;
  }
  const assetId = await lookupAssetId(api, authorToken);
  await waitForProvisioned(api, authorToken, assetId);
  return assetId;
}

async function lookupAssetId(api: APIRequestContext, authorToken: string): Promise<string> {
  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: SETTINGS_ASSET.namespace, keyword: SETTINGS_ASSET.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((it) => it.name === SETTINGS_ASSET.name);
  expect(match, `夹具资产 ${SETTINGS_ASSET.name} 无法查回（HTTP ${listed.status()}）`).toBeTruthy();
  return match!.assetId;
}

/**
 * 基线归正：目录检索导航按 displayName + 组织访问域定位资产，上一切片（尤其 UI 保存
 * 只发三字段，触发后端 [缺陷 A]）可能已把 organizationId 清成 NULL，必须先归正。
 * PATCH 恒携带完整治理字段元组——差异式 PATCH（只传变更字段）正是 [缺陷 A] 的触发器，
 * 会把未提交字段静默清空；displayName 等值回传不会进入 rename 分支（无别名副作用）。
 */
async function normalizeBaseline(
  api: APIRequestContext,
  token: string,
  assetId: string,
  organizationId: string,
): Promise<AssetDetail> {
  let detail = await getAssetDetail(api, token, assetId);
  const dirty =
    detail.displayName !== SETTINGS_ASSET.displayName ||
    (detail.description ?? '') !== BASELINE_DESCRIPTION ||
    detail.visibility !== 'INTERNAL' ||
    (detail.organizationId ?? '') !== organizationId;
  if (dirty) {
    const resp = await api.patch(`${API_BASE}/assets/${assetId}`, {
      headers: bearer(token),
      data: {
        displayName: SETTINGS_ASSET.displayName,
        description: BASELINE_DESCRIPTION,
        visibility: 'INTERNAL',
        organizationId,
        expectedVersion: detail.rowVersion ?? 0,
      },
      failOnStatusCode: false,
    });
    expect(resp.status(), `夹具基线归正失败 HTTP ${resp.status()}：${JSON.stringify(await readJson(resp))}`).toBeLessThan(300);
    detail = await getAssetDetail(api, token, assetId);
  }
  // 归正后终态复核：导航（displayName 检索 + 组织访问域）与乐观锁前提必须成立
  expect(detail.displayName, '归正后 displayName 必须等于基线，否则目录检索导航必然失败').toBe(
    SETTINGS_ASSET.displayName,
  );
  expect(detail.organizationId, '归正后 organizationId 必须回写（[缺陷 A] 回归哨兵）').toBe(organizationId);
  expect(detail.status, '夹具资产应为 ACTIVE（生命周期切片不得复用本资产）').toBe('ACTIVE');
  return detail;
}

/** 元数据表单定位：编辑元数据卡片内、含描述输入框的那个 form 的保存按钮。 */
const metadataForm = (page: Page) =>
  page.locator('form').filter({ has: page.getByLabel(/描\s*述|Description/) });

const patchResponse = (page: Page, assetId: string) =>
  page.waitForResponse(
    (r) => r.url().endsWith(`/assets/${assetId}`) && r.request().method() === 'PATCH',
    { timeout: 30_000 },
  );

/* ------------------------------------------------------------------ */
/* 测试状态                                                            */
/* ------------------------------------------------------------------ */

let credentials: { username: string; password: string } | null = null;
let dockerAvailable = false;
let fixtures: (E2eFixtures & { settingsAssetId: string }) | null = null;

test.beforeAll(async () => {
  credentials = bootstrapCredentials();
  if (!credentials) return;

  try {
    execFileSync('docker', ['compose', 'version'], { cwd: COMPOSE_DIR, stdio: 'pipe', timeout: 10_000 });
    dockerAvailable = true;
  } catch {
    dockerAvailable = false;
    return;
  }

  const api = await request.newContext();
  try {
    const base = await prepareFixtures(await adminToken(api), api);
    const authorToken = await loginAs(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    const settingsAssetId = await ensureSettingsAsset(api, authorToken, base.organizationId, base.teamId);
    fixtures = { ...base, settingsAssetId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(async () => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  test.skip(!dockerAvailable, 'docker 不可用，跳过 E4 Settings 表单旅程');
  test.skip(!fixtures, '夹具准备失败');

  // 切片独立可重跑：无论上一切片把远端改成什么，先归正基线
  const api = await request.newContext();
  try {
    const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await normalizeBaseline(api, token, fixtures!.settingsAssetId, fixtures!.organizationId);
  } finally {
    await api.dispose();
  }
});

test.describe('资产 Settings 表单编辑（TASK-P0BR-069 切片 8/9/10）', () => {
  /* ---- 切片 8：编辑元数据 → 保存 → 服务端持久化 → 重载回显 ---- */
  test('⑧ 修改描述保存成功：PATCH 真实入库、成功提示、重载回显新值', async ({ page }) => {
    test.setTimeout(120_000);
    const assetId = fixtures!.settingsAssetId;
    const newDescription = `e2e069-settings-desc-${Date.now()}`;

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, SETTINGS_ASSET.displayName);
    await openAssetSettingsViaNav(page);

    await expect(
      page.locator('.ant-card').filter({ hasText: /编辑元数据|Edit Metadata/ }).first(),
    ).toBeVisible();

    const description = page.getByLabel(/描\s*述|Description/);
    await description.fill(newDescription);

    const [resp] = await Promise.all([
      patchResponse(page, assetId),
      metadataForm(page).getByRole('button', { name: /保\s*存|Save/ }).click(),
    ]);
    const raw = await resp.text();
    expect(resp.status(), `元数据 PATCH 返回 HTTP ${resp.status()}：${raw.slice(0, 300)}`).toBe(200);
    const saved = dataOf(JSON.parse(raw) as unknown) as AssetDetail | null;
    expect(saved?.description, 'PATCH 响应应回显服务端入库后的新描述').toBe(newDescription);
    expect(saved?.rowVersion, '成功保存必须前进 rowVersion').toBeGreaterThan(0);

    // 成功 toast 文案 = t('common.save')；与表单按钮同名，限定 .ant-message 容器
    await expect(page.locator('.ant-message').getByText(/保\s*存|Save/).first()).toBeVisible();

    // 服务端复核 + 整页重载回显（证明持久化而非仅内存态）
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const detail = await getAssetDetail(api, token, assetId);
      expect(detail.description, '远端描述应为新值').toBe(newDescription);
    } finally {
      await api.dispose();
    }
    await page.reload();
    await expect(page.getByLabel(/描\s*述|Description/)).toHaveValue(newDescription);
  });

  /* ---- 切片 8（双语）：语言切换走真实用户路径，文案即时切换 ---- */
  test('⑧b 双语切换：English 下 Settings 表单文案为英文，可切回中文', async ({ page }) => {
    test.setTimeout(120_000);
    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, SETTINGS_ASSET.displayName);
    await openAssetSettingsViaNav(page);

    // 中文基线
    await expect(
      page.locator('.ant-card').filter({ hasText: '编辑元数据' }).first(),
    ).toBeVisible();

    // 切到 English（AppLayout 顶栏语言切换器）
    await page.getByTestId('language-switcher').click();
    await page.getByRole('menuitem', { name: 'English' }).click();

    await expect(
      page.locator('.ant-card').filter({ hasText: 'Edit Metadata' }).first(),
    ).toBeVisible();
    await expect(page.getByLabel('Display Name')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save' }).first()).toBeVisible();

    // 切回中文，恢复全局态避免影响其他 spec
    await page.getByTestId('language-switcher').click();
    await page.getByRole('menuitem', { name: '中文' }).click();
    await expect(
      page.locator('.ant-card').filter({ hasText: '编辑元数据' }).first(),
    ).toBeVisible();
  });

  /* ---- 切片 9：空展示名称——如实断言当前真实失败态（见文件头诚实边界） ---- */
  test('⑨ 清空展示名称提交：请求真实发出且被服务端拒绝、远端值未被成功覆盖', async ({ page }) => {
    test.setTimeout(120_000);
    const assetId = fixtures!.settingsAssetId;

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, SETTINGS_ASSET.displayName);
    await openAssetSettingsViaNav(page);

    // 当前实现（本卡禁改）：元数据 Form 无 rules，清空展示名称后提交会真实发出 PATCH。
    // 若未来补上前端必填校验（另立卡），此 waitForResponse 会超时如实红——提醒同步更新本断言。
    await page.getByLabel(/展示名称|Display Name/).fill('');
    const [resp] = await Promise.all([
      patchResponse(page, assetId),
      metadataForm(page).getByRole('button', { name: /保\s*存|Save/ }).click(),
    ]);
    expect(resp.status(), `空展示名称应被服务端拒绝（探针实测 500），实际 HTTP ${resp.status()}`).toBeGreaterThanOrEqual(400);

    // 错误提示出现（message.error 透传后端 body.message，探针实测为 "Internal server error"）
    await expect(
      page.locator('.ant-message').getByText(/Internal server error|操作失败|Operation failed/).first(),
    ).toBeVisible();

    // 服务端复核：事务回滚，远端 displayName 仍为基线，绝不允许被清空
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const detail = await getAssetDetail(api, token, assetId);
      expect(detail.displayName, '失败提交不得覆盖远端展示名称').toBe(SETTINGS_ASSET.displayName);
    } finally {
      await api.dispose();
    }
  });

  /* ---- 切片 10：并发修改冲突——stale expectedVersion → 409 → 冲突提示 ---- */
  test('⑩ 并发冲突：外部先行修改后 UI 保存得到 409 提示、远端保留外部值', async ({ page }) => {
    test.setTimeout(120_000);
    const assetId = fixtures!.settingsAssetId;
    const externalDescription = `e2e069-external-${Date.now()}`;
    const uiDisplayName = `${SETTINGS_ASSET.displayName}-冲突试验`;

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, SETTINGS_ASSET.displayName);
    await openAssetSettingsViaNav(page);
    // 此刻 UI query 已缓存 rowVersion=N

    // 外部（模拟另一用户）经 API 修改描述 → rowVersion 前进为 N+1
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const fresh = await getAssetDetail(api, token, assetId);
      // 完整元组 bump：只传 description 会触发后端 [缺陷 A] 清空 displayName，
      // 反噬本切片「冲突提交不得覆盖展示名称」断言（409 乐观锁先于 rename，等值回传安全）。
      const bump = await api.patch(`${API_BASE}/assets/${assetId}`, {
        headers: bearer(token),
        data: {
          displayName: fresh.displayName ?? SETTINGS_ASSET.displayName,
          description: externalDescription,
          visibility: fresh.visibility ?? 'INTERNAL',
          organizationId: fresh.organizationId ?? fixtures!.organizationId,
          expectedVersion: fresh.rowVersion ?? 0,
        },
        failOnStatusCode: false,
      });
      expect(bump.status(), `外部 bump 应成功，实际 HTTP ${bump.status()}`).toBe(200);

      // UI 仍持 stale N：修改展示名称后保存 → 乐观锁 409
      await page.getByLabel(/展示名称|Display Name/).fill(uiDisplayName);
      const [resp] = await Promise.all([
        patchResponse(page, assetId),
        metadataForm(page).getByRole('button', { name: /保\s*存|Save/ }).click(),
      ]);
      const raw = await resp.text();
      expect(resp.status(), `陈旧 expectedVersion 应返回 409，实际：${raw.slice(0, 300)}`).toBe(409);
      expect(JSON.parse(raw) as { code?: string }).toMatchObject({ code: 'ASSET_CONCURRENT_MODIFICATION' });

      // 冲突提示（message.warning(t('assets.conflictReload'))）
      await expect(
        page.locator('.ant-message').getByText(/资产已被其他人修改|modified by another user/i).first(),
      ).toBeVisible();

      // 服务端复核：并发写被拦截——外部描述保持、UI 输入的名称未落库
      const detail = await getAssetDetail(api, token, assetId);
      expect(detail.description, '远端描述应保留外部用户的值').toBe(externalDescription);
      expect(detail.displayName, '冲突提交不得覆盖展示名称').toBe(SETTINGS_ASSET.displayName);
    } finally {
      await api.dispose();
    }
  });
});

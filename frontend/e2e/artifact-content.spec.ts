/*
 * 功能: E4 工件文本内容浏览闭环 E2E（TASK-P0BR-077）——在真实 Compose 栈上走完
 *       多文件上传会话 → staging PUT → complete → UPLOAD_MATERIALIZE → VALIDATING →
 *       PENDING_REVIEW → transition PUBLISHED → dvc-cache SigV4 注入 的端到端链，
 *       随后断言：UI「文件」Tab 树叶子选中后内容面板以纯文本 <pre> 渲染 fixture 字节、
 *       版本切换后面板随版本化 Query Key 重置、415/413 错误文案分支、页面不泄漏
 *       内部主机名/bucket/对象键/预签名 Token；并以纯 API 矩阵覆盖
 *       200 字节一致性 / 415 / 413 / 404（未知与跨版本绑定防枚举）/ 403 / 401。
 *       dvc-cache 注入与 ETag 提取沿用 preview-image.spec.ts 的容器内执行模式
 *       （后端业务链无 dvc-cache 写入口，编排层经 worker 容器标准库 SigV4 PUT）。
 * 时间: 2026-10-10
 * 作者: AxeXie
 */
import { test, expect, request, type APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  adminToken,
  bootstrapCredentials,
  directApiGet,
  E2E_AUTHOR,
  E2E_AUTHZ_READER,
  ensureAssetVersion,
  login,
  loginAs,
  openAssetDetailViaCatalog,
  prepareFixtures,
  type E2eFixtures,
} from './support';

const __filename = fileURLToPath(import.meta.url);
const __dirnameE2e = dirname(__filename);

const COMPOSE_DIR = resolve(__dirnameE2e, '..', '..', 'deploy', 'compose');
const FIXTURE_CSV = resolve(__dirnameE2e, 'fixtures', 'content-sample.csv');
const FIXTURE_TXT = resolve(__dirnameE2e, 'fixtures', 'content-sample.txt');
const FIXTURE_V2 = resolve(__dirnameE2e, 'fixtures', 'v2-sample.txt');
const API_BASE = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// 幂等夹具坐标（与 068 预览资产、共享夹具资产隔离）
const CONTENT_ASSET = {
  namespace: 'nlp',
  name: 'e2e077-content-target',
  displayName: 'E2E 077 文件内容浏览目标',
  format: 'CSV',
  modality: 'TEXT',
} as const;
const V1 = '3.1.0'; // 多文件版本：csv/txt（已注入）+ payload.bin（413）+ binary/blob.bin（415）
const V2 = '4.1.0'; // 单文件版本：v2-sample.txt（已注入），用于版本切换重置断言

// payload.bin 元数据 size 超过后端 maxTextBytes 默认 1MiB → 读取时 413 元数据预检命中；
// sha256 仅为占位声明（物化链只截 [0:32] 作 dvcHash，对象不注入，413 先于对象检查）。
const OVERLIMIT_SHA256 = `${'e2e077overlimit'.padEnd(64, '0')}`;
const OVERLIMIT_SIZE = 2 * 1024 * 1024;

/* ------------------------------------------------------------------ */
/* 内部辅助（沿用 preview-image.spec.ts 模式）                          */
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

/** 在 compose 目录下运行 docker compose 子命令，返回 stdout。 */
function dockerCompose(args: string[], stdin?: string): string {
  try {
    return execFileSync('docker', ['compose', ...args], {
      cwd: COMPOSE_DIR,
      encoding: 'utf8',
      input: stdin ?? '',
      timeout: 60_000,
      maxBuffer: 10 * 1024 * 1024,
    });
  } catch (e: unknown) {
    const err = e as { stdout?: string; stderr?: string; message?: string };
    throw new Error(
      `docker compose ${args.join(' ')} 失败:\n${err.stderr ?? err.message ?? ''}\n${err.stdout ?? ''}`,
    );
  }
}

/** 轮询直到 fn 返回 truthy 值，超时抛出。 */
async function pollUntil<T>(
  fn: () => Promise<T | null | false>,
  timeoutMs: number,
  intervalMs: number,
  label: string,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const result = await fn();
    if (result) return result as T;
    expect(Date.now(), `轮询超时：${label}`).toBeLessThan(deadline);
    await new Promise((r) => setTimeout(r, intervalMs));
  }
}

function sha256Hex(bytes: Buffer): string {
  return createHash('sha256').update(bytes).digest('hex');
}

/**
 * 通过 docker compose exec 将本地文件 PUT 到 staging（容器内 curl -T）。
 * presign URL 中的 host 在宿主机不可达，必须在 minio 容器内发起 PUT。
 */
function putStagingViaContainer(presignUrl: string, localPath: string): string {
  dockerCompose(['cp', localPath, 'minio:/tmp/e2e077_put.bin']);
  const raw = dockerCompose([
    'exec', '-T', 'minio', 'sh', '-c',
    `curl -s -D /tmp/e2e077_resp.hdr -o /dev/null -T /tmp/e2e077_put.bin '${presignUrl}'; cat /tmp/e2e077_resp.hdr`,
  ]);
  const headers = raw.replace(/\r/g, '');
  const match = headers.match(/^etag:\s*"?([0-9a-fA-F]+)"?/im);
  expect(match, `staging PUT 未返回 ETag，响应头：${headers.slice(0, 400)}`).toBeTruthy();
  return match![1].toLowerCase();
}

/**
 * 通过 docker compose exec 在 worker 容器内向 dvc-cache PUT 文本字节（SigV4 签名）。
 * objectKey = dvcHash[0:2] + '/' + dvcHash[2:]，其中 dvcHash = sha256[0:32]。
 */
function injectDvcCache(sha256HexStr: string, bytes: Buffer): void {
  const dvcHash = sha256HexStr.substring(0, 32);
  const objectKey = `${dvcHash.substring(0, 2)}/${dvcHash.substring(2)}`;
  const b64 = bytes.toString('base64');

  const pyScript = `
import sys, os, hashlib, hmac, datetime, urllib.request, urllib.error, base64

ak = os.environ['AWS_ACCESS_KEY_ID']
sk = os.environ['AWS_SECRET_ACCESS_KEY']
region = 'us-east-1'
service = 's3'
bucket = 'dvc-cache'
key = '${objectKey}'
body = base64.b64decode('${b64}')

def sign(key_b, msg):
    return hmac.new(key_b, msg.encode(), hashlib.sha256).digest()

def get_sig_key(key_b, dstr, region_s, service_s):
    k1 = sign(('AWS4' + key_b.decode()).encode() if isinstance(key_b, bytes) else ('AWS4' + key_b).encode(), dstr)
    k2 = sign(k1, region_s)
    k3 = sign(k2, service_s)
    return sign(k3, 'aws4_request')

t = datetime.datetime.utcnow()
amzdate = t.strftime('%Y%m%dT%H%M%SZ')
datestamp = t.strftime('%Y%m%d')

host = 'minio:9000'
canonical_uri = '/' + bucket + '/' + key
payload_hash = hashlib.sha256(body).hexdigest()

canonical_headers = (
    'host:' + host + '\\n' +
    'x-amz-content-sha256:' + payload_hash + '\\n' +
    'x-amz-date:' + amzdate + '\\n'
)
signed_headers = 'host;x-amz-content-sha256;x-amz-date'

canonical_request = '\\n'.join([
    'PUT', canonical_uri, '',
    canonical_headers, signed_headers, payload_hash,
])

scope = '/'.join([datestamp, region, service, 'aws4_request'])
string_to_sign = '\\n'.join(['AWS4-HMAC-SHA256', amzdate, scope,
    hashlib.sha256(canonical_request.encode()).hexdigest()])

signing_key = get_sig_key(sk, datestamp, region, service)
signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()

auth = (
    'AWS4-HMAC-SHA256 Credential=' + ak + '/' + scope +
    ', SignedHeaders=' + signed_headers +
    ', Signature=' + signature
)

url = 'http://' + host + canonical_uri
req = urllib.request.Request(url, data=body, method='PUT')
req.add_header('Authorization', auth)
req.add_header('x-amz-date', amzdate)
req.add_header('x-amz-content-sha256', payload_hash)
req.add_header('Host', host)

try:
    resp = urllib.request.urlopen(req, timeout=30)
    print('PUT status=' + str(resp.status))
except urllib.error.HTTPError as e:
    print('PUT HTTPError status=' + str(e.code), file=sys.stderr)
    print(e.read().decode()[:600], file=sys.stderr)
    sys.exit(1)
`;

  const output = dockerCompose(['exec', '-T', 'worker', 'python3', '-c', pyScript]);
  expect(output, `dvc-cache SigV4 PUT 失败：${output}`).toContain('PUT status=200');
}

interface BundleFile {
  path: string;
  sha256: string;
  size: number;
  mediaType: string;
}

/**
 * 单 session 完成一次 bundle 上传：本地临时 bundle → 容器内 PUT → complete。
 * 多文件时物化校验跳过 session 对象 sha256 比对（bundle 字节不被解包，仅逐文件元数据入册），
 * 单文件时必须 PUT 声明 sha256 对应的真实字节。
 */
async function uploadBundle(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
  bundleTotalBytes: number,
  files: BundleFile[],
): Promise<void> {
  const bundlePath = join(tmpdir(), `e2e077_bundle_${versionId}_${Date.now()}.tar`);
  writeFileSync(bundlePath, Buffer.from(JSON.stringify({ bundle: 'e2e077', files: files.map((f) => f.path) }), 'utf8'));
  try {
    const sessionResp = await api.post(
      `${API_BASE}/assets/${assetId}/versions/${versionId}/upload-sessions`,
      { headers: bearer(token), data: { totalBytes: bundleTotalBytes, fileCount: 1 }, failOnStatusCode: false },
    );
    const sessionData = dataOf(await readJson(sessionResp)) as { sessionId?: string } | null;
    expect(sessionData?.sessionId, `创建上传会话失败 HTTP ${sessionResp.status()}`).toBeTruthy();
    const sessionId = sessionData!.sessionId!;

    const presignResp = await api.get(
      `${API_BASE}/upload-sessions/${sessionId}/parts/1/presign`,
      { headers: bearer(token), failOnStatusCode: false },
    );
    const presignData = dataOf(await readJson(presignResp)) as { url?: string } | null;
    expect(presignData?.url, `获取 presign URL 失败 HTTP ${presignResp.status()}`).toBeTruthy();

    const etag = putStagingViaContainer(presignData!.url!, bundlePath);

    const completeResp = await api.post(
      `${API_BASE}/upload-sessions/${sessionId}/complete`,
      {
        headers: bearer(token),
        data: { parts: [{ partNumber: 1, etag }], files },
        failOnStatusCode: false,
      },
    );
    expect(completeResp.status(), `complete 会话失败 HTTP ${completeResp.status()}`).toBeLessThan(300);
  } finally {
    rmSync(bundlePath, { force: true });
  }
}

/** 轮询版本至 PENDING_REVIEW（物化 + 校验完成）。 */
async function waitPendingReview(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<void> {
  await pollUntil(
    async () => {
      const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
        headers: bearer(token),
        failOnStatusCode: false,
      });
      const d = dataOf(await readJson(r)) as { status?: string } | null;
      const s = d?.status;
      if (s === 'PENDING_REVIEW' || s === 'PUBLISHED') return true;
      expect(s, `版本进入意外状态：${s}`).not.toBe('REJECTED');
      return null;
    },
    120_000,
    3_000,
    `版本 ${versionId} 等待 PENDING_REVIEW 超时`,
  );
}

/** transition 到 PUBLISHED（409 视为并发已推进，确认状态）。 */
async function publishVersion(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<void> {
  const curResp = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  const curData = dataOf(await readJson(curResp)) as { status?: string } | null;
  if (curData?.status === 'PUBLISHED') return;
  const transResp = await api.post(
    `${API_BASE}/assets/${assetId}/versions/${versionId}/transition`,
    { headers: bearer(token), data: { targetStatus: 'PUBLISHED' }, failOnStatusCode: false },
  );
  expect([200, 204, 409], `transition 到 PUBLISHED 失败 HTTP ${transResp.status()}`).toContain(transResp.status());
  if (transResp.status() === 409) {
    const confirmResp = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    const cd = dataOf(await readJson(confirmResp)) as { status?: string } | null;
    expect(cd?.status, `transition 返回 409 但状态非 PUBLISHED：${cd?.status}`).toBe('PUBLISHED');
  }
}

interface ArtifactEntry {
  artifactId: string;
  versionId: string;
  path: string;
  sha256: string;
  size: number;
  mediaType: string | null;
}

/** 列出版本工件，返回 path → ArtifactEntry 映射。 */
async function listArtifactMap(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
): Promise<Map<string, ArtifactEntry>> {
  const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}/artifacts`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  expect(r.status(), `列出版本工件失败 HTTP ${r.status()}`).toBe(200);
  const list = dataOf(await readJson(r)) as ArtifactEntry[] | null;
  const map = new Map<string, ArtifactEntry>();
  (list ?? []).forEach((a) => map.set(a.path, a));
  return map;
}

/** 轮询工件列表直到包含全部期望 path（物化入库完成的标志）。 */
async function waitArtifacts(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
  paths: string[],
): Promise<Map<string, ArtifactEntry>> {
  return pollUntil(
    async () => {
      const map = await listArtifactMap(api, token, assetId, versionId);
      return paths.every((p) => map.has(p)) ? map : null;
    },
    120_000,
    3_000,
    `版本 ${versionId} 工件入库超时（期望 ${paths.join(', ')}）`,
  );
}

/** 版本已 PUBLISHED 且工件列表含期望 path → 上传链可跳过（幂等重跑快速路径）。 */
async function versionChainDone(
  api: APIRequestContext,
  token: string,
  assetId: string,
  versionId: string,
  paths: string[],
): Promise<boolean> {
  const r = await api.get(`${API_BASE}/assets/${assetId}/versions/${versionId}`, {
    headers: bearer(token),
    failOnStatusCode: false,
  });
  const d = dataOf(await readJson(r)) as { status?: string } | null;
  if (d?.status !== 'PUBLISHED') return false;
  try {
    const map = await listArtifactMap(api, token, assetId, versionId);
    return paths.every((p) => map.has(p));
  } catch {
    return false;
  }
}

/** 幂等创建独立 DATASET 夹具资产（与 support.ts 私有 ensureDatasetAsset 相同契约，此处内联）。 */
async function ensureContentAsset(
  api: APIRequestContext,
  authorToken: string,
  organizationId: string,
  teamId: string,
): Promise<string> {
  const created = await api.post(`${API_BASE}/assets`, {
    headers: { ...bearer(authorToken), 'Idempotency-Key': `idmp_e2e077_asset_${CONTENT_ASSET.name}` },
    data: {
      type: 'DATASET',
      namespace: CONTENT_ASSET.namespace,
      name: CONTENT_ASSET.name,
      displayName: CONTENT_ASSET.displayName,
      visibility: 'INTERNAL',
      organizationId,
      ownerTeamId: teamId,
      dataset: { format: CONTENT_ASSET.format, modality: CONTENT_ASSET.modality },
    },
    failOnStatusCode: false,
  });
  const createdData = dataOf(await readJson(created)) as { assetId?: string } | null;
  if (created.status() === 201 && createdData?.assetId) {
    await waitAssetReady(api, authorToken, createdData.assetId);
    return createdData.assetId;
  }
  const listed = await api.get(`${API_BASE}/assets`, {
    headers: bearer(authorToken),
    params: { namespace: CONTENT_ASSET.namespace, keyword: CONTENT_ASSET.name, type: 'DATASET', size: 50 },
    failOnStatusCode: false,
  });
  const page = dataOf(await readJson(listed)) as { items?: { assetId: string; name: string }[] } | null;
  const match = page?.items?.find((it) => it.name === CONTENT_ASSET.name);
  expect(match, `夹具资产 ${CONTENT_ASSET.name} 无法创建（HTTP ${created.status()}）也无法查回`).toBeTruthy();
  await waitAssetReady(api, authorToken, match!.assetId);
  return match!.assetId;
}

/** 等待资产建仓完成（内联复刻 support.ts waitForAssetReady）。 */
async function waitAssetReady(api: APIRequestContext, token: string, assetId: string): Promise<void> {
  await pollUntil(
    async () => {
      const r = await api.get(`${API_BASE}/assets/${assetId}`, {
        headers: bearer(token),
        failOnStatusCode: false,
      });
      const d = dataOf(await readJson(r)) as { provisioningStatus?: string } | null;
      expect(d?.provisioningStatus, `资产 ${assetId} 建仓失败`).not.toBe('FAILED');
      return d?.provisioningStatus === 'COMPLETED' ? true : null;
    },
    60_000,
    2_000,
    `资产 ${assetId} 建仓超时`,
  );
}

/* ------------------------------------------------------------------ */
/* 测试状态                                                             */
/* ------------------------------------------------------------------ */

let credentials: { username: string; password: string } | null = null;
let dockerAvailable = false;
let fixtures: (E2eFixtures & { assetId: string; v1VersionId: string; v2VersionId: string }) | null = null;
let v1ChainAlreadyDone = false;
let v2ChainAlreadyDone = false;

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
    const assetId = await ensureContentAsset(api, authorToken, base.organizationId, base.teamId);
    const v1VersionId = await ensureAssetVersion(api, authorToken, assetId, V1);
    const v2VersionId = await ensureAssetVersion(api, authorToken, assetId, V2);

    v1ChainAlreadyDone = await versionChainDone(api, authorToken, assetId, v1VersionId, [
      'content-sample.csv',
      'content-sample.txt',
      'payload.bin',
      'binary/blob.bin',
    ]);
    v2ChainAlreadyDone = await versionChainDone(api, authorToken, assetId, v2VersionId, ['v2-sample.txt']);

    fixtures = { ...base, assetId, v1VersionId, v2VersionId };
  } finally {
    await api.dispose();
  }
});

test.beforeEach(() => {
  test.skip(!credentials, '未提供 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD，无法登录真实栈');
  test.skip(!dockerAvailable, 'docker 不可用，跳过 E4 文件内容浏览闭环');
  test.skip(!fixtures, '夹具准备失败');
});

// 链步骤间有先后依赖，保持声明顺序执行（文件其余 spec 不受影响）
test.describe.configure({ mode: 'serial' });

test.describe('E4 工件文本内容浏览闭环（TASK-P0BR-077）', () => {
  test('v1 多文件上传→物化→发布→dvc-cache 注入链', async () => {
    test.setTimeout(300_000);
    const { assetId, v1VersionId } = fixtures!;
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      if (v1ChainAlreadyDone) return;

      const csvBytes = readFileSync(FIXTURE_CSV);
      const txtBytes = readFileSync(FIXTURE_TXT);
      const csvSha = sha256Hex(csvBytes);
      const txtSha = sha256Hex(txtBytes);

      await uploadBundle(
        api,
        token,
        assetId,
        v1VersionId,
        csvBytes.length + txtBytes.length,
        [
          { path: 'content-sample.csv', sha256: csvSha, size: csvBytes.length, mediaType: 'text/csv' },
          { path: 'content-sample.txt', sha256: txtSha, size: txtBytes.length, mediaType: 'text/plain' },
          // 413 探针：mediaType 在白名单内但元数据 size 超 1MiB → 读取时 size 预检命中，对象无需注入
          { path: 'payload.bin', sha256: OVERLIMIT_SHA256, size: OVERLIMIT_SIZE, mediaType: 'text/plain' },
          // 415 探针：白名单外 mediaType；带目录段同时覆盖 Tree 目录结构
          { path: 'binary/blob.bin', sha256: '0'.repeat(64), size: 16, mediaType: 'application/octet-stream' },
        ],
      );

      await waitPendingReview(api, token, assetId, v1VersionId);
      await publishVersion(api, token, assetId, v1VersionId);
      const artifacts = await waitArtifacts(api, token, assetId, v1VersionId, [
        'content-sample.csv',
        'content-sample.txt',
      ]);
      // 注入实际字节（sha256 与物化记录一致 → dvcObjectKey 命中）
      injectDvcCache(artifacts.get('content-sample.csv')!.sha256, csvBytes);
      injectDvcCache(artifacts.get('content-sample.txt')!.sha256, txtBytes);
    } finally {
      await api.dispose();
    }
  });

  test('v2 单文件上传→物化→发布→dvc-cache 注入链（版本切换夹具）', async () => {
    test.setTimeout(180_000);
    const { assetId, v2VersionId } = fixtures!;
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      if (v2ChainAlreadyDone) return;

      const v2Bytes = readFileSync(FIXTURE_V2);
      const v2Sha = sha256Hex(v2Bytes);
      // 单文件链：PUT 真实字节，物化校验会比对 session 对象 sha256
      await uploadBundle(api, token, assetId, v2VersionId, v2Bytes.length, [
        { path: 'v2-sample.txt', sha256: v2Sha, size: v2Bytes.length, mediaType: 'text/plain' },
      ]);

      await waitPendingReview(api, token, assetId, v2VersionId);
      await publishVersion(api, token, assetId, v2VersionId);
      const artifacts = await waitArtifacts(api, token, assetId, v2VersionId, ['v2-sample.txt']);
      injectDvcCache(artifacts.get('v2-sample.txt')!.sha256, v2Bytes);
    } finally {
      await api.dispose();
    }
  });

  test('UI：文件 Tab 树选中 → 纯文本渲染 → 版本切换重置 → 错误文案 → 泄漏红线', async ({ page }) => {
    test.setTimeout(120_000);
    const csvText = readFileSync(FIXTURE_CSV, 'utf8');
    const txtText = readFileSync(FIXTURE_TXT, 'utf8');
    const v2Text = readFileSync(FIXTURE_V2, 'utf8');

    await login(page, E2E_AUTHOR.username, E2E_AUTHOR.password);
    await openAssetDetailViaCatalog(page, CONTENT_ASSET.displayName);

    // 激活「文件」Tab（antd 两字之间插空格）
    const filesTab = page.getByRole('tab', { name: /^文\s*件$|^Files$/ });
    await filesTab.click();
    await expect(filesTab).toHaveAttribute('aria-selected', 'true');

    // 版本切换器：默认回退最近 PUBLISHED（4.1.0）；显式选择 3.1.0 驱动多文件版本
    const switcher = page.locator('.ant-select').filter({ hasText: /\d+\.\d+\.\d+ \(/ }).first();
    const versionOption = (label: string) =>
      page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .filter({ hasText: label });
    await switcher.click();
    await versionOption(`${V1} (PUBLISHED)`).click();

    // 树叶子选中 CSV → 面板 <pre> 纯文本 = fixture 全文
    await page.getByText('content-sample.csv', { exact: true }).click();
    const pre = page.getByTestId('artifact-content-pre');
    await expect(pre).toBeVisible({ timeout: 20_000 });
    await expect(pre).toHaveText(csvText);

    // 切换 TXT：同一面板按工件重挂载，内容整体替换
    await page.getByText('content-sample.txt', { exact: true }).click();
    await expect(pre).toContainText('e2e077-txt-marker');
    await expect(pre).toHaveText(txtText);

    // XSS 红线：TXT fixture 中的 <script> 只允许作为文本节点存在，不得进入 DOM 脚本元素
    await expect(pre).toContainText("<script>alert('e2e077-xss-probe')</script>");
    await expect(page.locator('script:has-text("e2e077-xss-probe")')).toHaveCount(0);

    // 413 探针：payload.bin（声明 2MiB）→「超出在线预览大小限制」文案 + 下载引导
    await page.getByText('payload.bin', { exact: true }).click();
    await expect(page.getByText(/文件超出在线预览大小限制|exceeds the inline preview/)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('artifact-content-pre')).toHaveCount(0);

    // 415 探针：binary/blob.bin（目录叶子）→「不支持在线预览」文案
    await page.getByText('blob.bin', { exact: true }).click();
    await expect(page.getByText(/该文件类型不支持在线预览|does not support inline preview/)).toBeVisible({ timeout: 20_000 });

    // 版本切换 → 面板随版本化 key 重置：3.1.0 的选中项在 4.1.0 工件列表中不存在 → 回到空态提示
    await switcher.click();
    await versionOption(`${V2} (PUBLISHED)`).click();
    await expect(page.getByText(/选择一个文本文件以预览内容|Select a text file/)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('artifact-content-pre')).toHaveCount(0);

    // 选择 4.1.0 独有文件 → 面板渲染 v2 字节（证明重置后按新 Query Key 重新取数）
    await page.getByText('v2-sample.txt', { exact: true }).click();
    await expect(pre).toBeVisible({ timeout: 20_000 });
    await expect(pre).toHaveText(v2Text);

    // 泄漏红线：页面数据不含内部主机名 / bucket / 对象键 / 预签名 Token
    const bodyText = await page.locator('body').innerText();
    expect(bodyText).not.toContain('minio:9000');
    expect(bodyText).not.toContain('localhost:9000');
    expect(bodyText).not.toContain('dvc-cache');
    expect(bodyText).not.toContain('asset-staging');
    expect(bodyText).not.toContain('X-Amz-');
    expect(bodyText).not.toContain('AWS4-HMAC-SHA256');
  });

  test('API 矩阵：200 字节一致性 / 415 / 413 / 404 防枚举 / 403 / 401', async () => {
    test.setTimeout(90_000);
    const { assetId, v1VersionId, v2VersionId } = fixtures!;
    const api = await request.newContext();
    try {
      const token = await loginApi(api, E2E_AUTHOR.username, E2E_AUTHOR.password);
      const artifacts = await listArtifactMap(api, token, assetId, v1VersionId);
      const csv = artifacts.get('content-sample.csv');
      const txt = artifacts.get('content-sample.txt');
      const oversize = artifacts.get('payload.bin');
      const blob = artifacts.get('binary/blob.bin');
      expect(csv, 'v1 夹具不完整（重跑上传链）').toBeTruthy();
      expect(txt && oversize && blob).toBeTruthy();

      const contentPath = (versionId: string, artifactId: string) =>
        `${API_BASE}/assets/${assetId}/versions/${versionId}/artifacts/${artifactId}/content`;

      // 200 + 白名单 mediaType + 字节 sha256 与 fixture 一致
      const csvResp = await api.get(contentPath(v1VersionId, csv!.artifactId), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(csvResp.status(), `CSV 内容应 200，实际 ${csvResp.status()}`).toBe(200);
      expect(csvResp.headers()['content-type']).toContain('text/csv');
      const csvBody = await csvResp.body();
      expect(sha256Hex(csvBody), 'CSV 字节与 fixture 不一致').toBe(csv!.sha256);

      const txtResp = await api.get(contentPath(v1VersionId, txt!.artifactId), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(txtResp.status()).toBe(200);
      expect(txtResp.headers()['content-type']).toContain('text/plain');
      expect(sha256Hex(await txtResp.body())).toBe(txt!.sha256);

      // 415：白名单外 mediaType（记录值优先，不做扩展名回退）
      const f415 = await api.get(contentPath(v1VersionId, blob!.artifactId), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(f415.status()).toBe(415);
      expect(((await readJson(f415)) as { code?: string } | null)?.code).toBe('PREVIEW_UNSUPPORTED_FORMAT');

      // 413：元数据 size 超 maxTextBytes（对象未注入也应在预检命中，证明预检先于读取）
      const f413 = await api.get(contentPath(v1VersionId, oversize!.artifactId), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(f413.status()).toBe(413);
      expect(((await readJson(f413)) as { code?: string } | null)?.code).toBe('PREVIEW_TOO_LARGE');

      // 404 防枚举：未知 artifactId
      const f404a = await api.get(contentPath(v1VersionId, 'art_e2e077_unknown'), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(f404a.status()).toBe(404);
      expect(((await readJson(f404a)) as { code?: string } | null)?.code).toBe('PREVIEW_NOT_FOUND');

      // 404 防枚举：跨版本绑定（v1 的 artifactId 挂在 v2 路径下）
      const f404b = await api.get(contentPath(v2VersionId, csv!.artifactId), {
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      });
      expect(f404b.status()).toBe(404);
      expect(((await readJson(f404b)) as { code?: string } | null)?.code).toBe('PREVIEW_NOT_FOUND');

      // 403：无 asset:read 主体（E2E_AUTHZ_READER 仅 authorization:read）
      const forbidden = await directApiGet(
        `/assets/${assetId}/versions/${v1VersionId}/artifacts/${csv!.artifactId}/content`,
        E2E_AUTHZ_READER.username,
        E2E_AUTHZ_READER.password,
      );
      expect(forbidden.status, `未授权主体应被拒绝，实际 ${forbidden.status}`).toBe(403);

      // 401：匿名（无 Authorization 头）
      const anon = await api.get(contentPath(v1VersionId, csv!.artifactId), { failOnStatusCode: false });
      expect(anon.status(), `匿名访问应 401，实际 ${anon.status()}`).toBe(401);
    } finally {
      await api.dispose();
    }
  });
});

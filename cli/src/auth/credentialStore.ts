// AIHub CLI 凭据存储抽象层
// 作者: AxeXie
// 创建时间: 2026-07-30
// 使用本地文件存储凭据，文件权限设为 0600

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

/** 凭据数据结构 */
export interface StoredCredentials {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;      // unix timestamp in seconds
  principalId: string;
  username: string;
  serverUrl: string;
}

const CREDENTIALS_FILENAME = '.aih-credentials.json';

/** 获取文件凭据路径 */
function getCredentialsFilePath(): string {
  return path.join(os.homedir(), CREDENTIALS_FILENAME);
}

/** 从文件读取凭据 */
function readFileCredentials(): StoredCredentials | null {
  const filePath = getCredentialsFilePath();
  try {
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath, 'utf-8');
    return JSON.parse(raw) as StoredCredentials;
  } catch {
    return null;
  }
}

/** 写入文件凭据 */
function writeFileCredentials(creds: StoredCredentials): void {
  const filePath = getCredentialsFilePath();
  fs.writeFileSync(filePath, JSON.stringify(creds, null, 2), { mode: 0o600 });
}

/** 删除文件凭据 */
function deleteFileCredentials(): void {
  const filePath = getCredentialsFilePath();
  try {
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath);
    }
  } catch {
    // 忽略删除错误
  }
}

/** 保存凭据到存储 */
export function saveCredentials(creds: StoredCredentials): void {
  writeFileCredentials(creds);
}

/** 从存储读取凭据 */
export function getCredentials(): StoredCredentials | null {
  return readFileCredentials();
}

/** 从存储删除凭据 */
export function clearCredentials(): void {
  deleteFileCredentials();
}

/** 检查凭据是否过期 */
export function isTokenExpired(creds: StoredCredentials): boolean {
  const now = Date.now() / 1000;
  return now >= creds.expiresAt;
}

/** 尝试刷新访问令牌（使用 refreshToken 端点） */
export async function tryRefreshToken(creds: StoredCredentials): Promise<StoredCredentials | null> {
  // 刷新逻辑在 api/client.ts 中实现，此处仅做过期判断
  if (!isTokenExpired(creds)) {
    return creds;
  }
  return null;
}
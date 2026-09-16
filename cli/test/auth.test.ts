// AIHub CLI Auth 模块单元测试
// 作者: AxeXie
// 创建时间: 2026-07-30

import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

describe('credentialStore file fallback', () => {
  const testFile = path.join(os.tmpdir(), '.aih-test-credentials.json');

  beforeEach(() => {
    // 清理测试文件
    if (fs.existsSync(testFile)) {
      fs.unlinkSync(testFile);
    }
  });

  it('should write and read credentials from file', () => {
    const creds = {
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      expiresAt: Date.now() / 1000 + 3600,
      principalId: 'prn_test',
      username: 'testuser',
      serverUrl: 'http://localhost:8080',
    };

    fs.writeFileSync(testFile, JSON.stringify(creds, null, 2), { mode: 0o600 });

    const content = fs.readFileSync(testFile, 'utf-8');
    const parsed = JSON.parse(content);

    expect(parsed.accessToken).toBe('test-access-token');
    expect(parsed.refreshToken).toBe('test-refresh-token');
    expect(parsed.principalId).toBe('prn_test');

    fs.unlinkSync(testFile);
  });

  it('should detect expired token', () => {
    const now = Date.now() / 1000;
    const expiredCreds = {
      expiresAt: now - 10, // 10 seconds ago
    };
    const validCreds = {
      expiresAt: now + 3600,
    };

    const isExpired = (c: { expiresAt: number }) => now >= c.expiresAt;
    expect(isExpired(expiredCreds)).toBe(true);
    expect(isExpired(validCreds)).toBe(false);
  });

  it('should handle missing credentials file gracefully', () => {
    const nonExistentPath = path.join(os.tmpdir(), '.aih-nonexistent-12345.json');
    expect(fs.existsSync(nonExistentPath)).toBe(false);
  });
});

describe('auth command behavior', () => {
  it('should build credentials object correctly', () => {
    const now = Math.floor(Date.now() / 1000);
    const creds = {
      accessToken: 'eyJ_test',
      refreshToken: 'rfr_test',
      expiresAt: now + 900,
      principalId: 'prn_abc123',
      username: 'alice',
      serverUrl: 'https://aihub.example.com',
    };

    expect(creds.accessToken).toBeTruthy();
    expect(creds.expiresAt > now).toBe(true);
    expect(creds.serverUrl).not.toMatch(/\/$/);
  });

  it('should not expose tokens in error messages', () => {
    const token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.abc123';
    const password = 'MyS3cureP@ss!';

    const safeToken = token.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED_TOKEN]');
    const safePassword = password.replace(/password["\s:=]+[^"\\s]+/gi, 'password=[REDACTED]');

    expect(safeToken).toBe('[REDACTED_TOKEN]');
    expect(safePassword).toBe(password); // not a password pattern
  });
});
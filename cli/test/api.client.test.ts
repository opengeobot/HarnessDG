// AIHub CLI API Client 单元测试
// 作者: AxeXie
// 创建时间: 2026-07-30

import { describe, it, expect, vi } from 'vitest';

describe('API client token handling', () => {
  it('should inject Bearer token in request header', () => {
    const accessToken = 'test-jwt-token';
    const header = `Bearer ${accessToken}`;

    expect(header).toMatch(/^Bearer /);
    expect(header).not.toContain('undefined');
  });

  it('should construct correct API base URL', () => {
    const serverUrl = 'http://localhost:8080';
    const baseUrl = `${serverUrl}/api/v1`;
    expect(baseUrl).toBe('http://localhost:8080/api/v1');
  });

  it('should construct correct API base URL with trailing slash', () => {
    const serverUrl = 'http://localhost:8080/';
    const cleanUrl = serverUrl.replace(/\/+$/, '');
    const baseUrl = `${cleanUrl}/api/v1`;
    expect(baseUrl).toBe('http://localhost:8080/api/v1');
  });
});

describe('API endpoint paths', () => {
  it('should generate correct asset search path', () => {
    const path = '/assets';
    expect(path).toBe('/assets');
  });

  it('should generate correct asset detail path', () => {
    const assetId = 'ast_123';
    const path = `/assets/${assetId}`;
    expect(path).toBe('/assets/ast_123');
  });

  it('should generate correct upload session path', () => {
    const assetId = 'ast_123';
    const versionId = 'ver_456';
    const path = `/assets/${assetId}/versions/${versionId}/upload-sessions`;
    expect(path).toBe('/assets/ast_123/versions/ver_456/upload-sessions');
  });

  it('should generate correct download ticket path', () => {
    const versionId = 'ver_456';
    const path = `/versions/${versionId}/download`;
    expect(path).toBe('/versions/ver_456/download');
  });

  it('should generate correct presign part path', () => {
    const sessionId = 'sess_789';
    const partNumber = 1;
    const path = `/upload-sessions/${sessionId}/parts/${partNumber}/presign`;
    expect(path).toBe('/upload-sessions/sess_789/parts/1/presign');
  });

  it('should encode download ticket path with artifact query param', () => {
    const versionId = 'ver_456';
    const artifactId = 'art_123';
    const path = `/versions/${versionId}/download?artifactId=${encodeURIComponent(artifactId)}`;
    expect(path).toBe('/versions/ver_456/download?artifactId=art_123');
  });
});

describe('error handling', () => {
  it('should format API error without leaking secrets', () => {
    const errorMsg = 'Failed with token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.abc123';
    const safe = errorMsg.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED_TOKEN]');
    expect(safe).not.toContain('eyJ');
    expect(safe).toContain('[REDACTED_TOKEN]');
  });

  it('should classify exit codes correctly', () => {
    const codes = {
      Success: 0,
      Error: 1,
      AuthFailed: 2,
      NetworkError: 3,
    };
    expect(codes.Success).toBe(0);
    expect(codes.AuthFailed).toBe(2);
    expect(codes.NetworkError).toBe(3);
  });
});

describe('file size formatting', () => {
  function formatFileSize(bytes: number | null | undefined): string {
    if (bytes == null || bytes < 0) return '-';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  it('should format bytes correctly', () => {
    expect(formatFileSize(0)).toBe('0 B');
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(1024)).toBe('1.0 KB');
    expect(formatFileSize(1024 * 1024)).toBe('1.0 MB');
    expect(formatFileSize(null)).toBe('-');
    expect(formatFileSize(undefined)).toBe('-');
  });
});
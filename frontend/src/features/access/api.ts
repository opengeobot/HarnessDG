/**
 * 功能: PAT API 客户端。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
import { apiClient } from '@/shared/api';

export interface PatView {
  tokenId: string;
  name: string;
  scopes: string[];
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  issuedAt: string;
  expiresAt: string;
  lastUsedAt?: string;
}

export interface CreatedPatView {
  tokenId: string;
  name: string;
  token: string;
  scopes: string[];
  issuedAt: string;
  expiresAt: string;
}

export interface CreatePatRequest {
  name: string;
  scopes?: string[];
  ttlDays?: number;
}

export function listPats(): Promise<PatView[]> {
  return apiClient.get('/tokens');
}

export function createPat(payload: CreatePatRequest): Promise<CreatedPatView> {
  return apiClient.post('/tokens', payload);
}

export function revokePat(tokenId: string): Promise<void> {
  return apiClient.delete(`/tokens/${tokenId}`);
}

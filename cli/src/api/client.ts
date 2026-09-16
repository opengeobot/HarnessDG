// AIHub CLI API 客户端
// 作者: AxeXie
// 创建时间: 2026-07-30
// 使用 Axios，自动注入 Bearer JWT，支持 Token 刷新

import axios, { type AxiosInstance, type AxiosResponse } from 'axios';
import { getCredentials, saveCredentials, type StoredCredentials } from '../auth/credentialStore';
import { AuthError } from '../shared/errors';

export interface ApiResponse<T = unknown> {
  data: T;
  requestId: string;
  traceId: string;
  timestamp: string;
}

export interface ApiError {
  code: string;
  message: string;
  retryable: boolean;
  requestId: string;
  traceId: string;
}

/** 创建认证 Axios 实例 */
export async function createApiClient(): Promise<AxiosInstance> {
  const creds = getCredentials();
  if (!creds) {
    throw new AuthError('Not logged in. Run "aih auth login" first.');
  }

  const serverUrl = creds.serverUrl.replace(/\/+$/, '');
  const client = axios.create({
    baseURL: `${serverUrl}/api/v1`,
    timeout: 30_000,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  });

  // 请求拦截器：注入 JWT
  client.interceptors.request.use((config) => {
    config.headers.Authorization = `Bearer ${creds.accessToken}`;
    return config;
  });

  // 响应拦截器：处理 401 自动刷新 Token
  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;

      // 401 且未重试过，尝试刷新 Token
      if (error.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true;
        try {
          const freshCreds = await refreshToken(creds);
          originalRequest.headers.Authorization = `Bearer ${freshCreds.accessToken}`;
          return client(originalRequest);
        } catch {
          throw new AuthError('Token refresh failed. Please login again.');
        }
      }

      // API 业务错误
      if (error.response?.data) {
        const apiErr = error.response.data as ApiError;
        if (apiErr.code) {
          error.message = `[${apiErr.code}] ${apiErr.message}`;
        }
      }

      throw error;
    },
  );

  return client;
}

/** 刷新 Token */
async function refreshToken(creds: StoredCredentials): Promise<StoredCredentials> {
  const serverUrl = creds.serverUrl.replace(/\/+$/, '');
  const response = await axios.post<ApiResponse<{
    tokenType: string;
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    refreshExpiresIn: number;
    principal: { principalId: string };
  }>>(`${serverUrl}/api/v1/auth/refresh`, {
    refreshToken: creds.refreshToken,
  }, {
    timeout: 15_000,
  });

  const data = response.data?.data;
  if (!data?.accessToken) {
    throw new AuthError('Token refresh failed: invalid response');
  }

  const now = Date.now() / 1000;
  const freshCreds = {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    expiresAt: now + data.expiresIn,
    principalId: data.principal?.principalId || creds.principalId,
    username: creds.username,
    serverUrl: creds.serverUrl,
  };

  saveCredentials(freshCreds);
  return freshCreds;
}

/** 通用 GET 请求，解包 ApiResponse */
export async function apiGet<T>(client: AxiosInstance, url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await client.get<ApiResponse<T>>(url, { params });
  return response.data.data as T;
}

/** 通用 POST 请求，解包 ApiResponse */
export async function apiPost<T>(client: AxiosInstance, url: string, body?: unknown): Promise<T> {
  const response = await client.post<ApiResponse<T>>(url, body);
  return response.data.data as T;
}

/** 通用 POST 请求，不解包 ApiResponse */
export async function apiPostRaw<T>(client: AxiosInstance, url: string, body?: unknown): Promise<AxiosResponse<T>> {
  return client.post<T>(url, body);
}

/** 通用 PATCH 请求，解包 ApiResponse */
export async function apiPatch<T>(client: AxiosInstance, url: string, body?: unknown): Promise<T> {
  const response = await client.patch<ApiResponse<T>>(url, body);
  return response.data.data as T;
}
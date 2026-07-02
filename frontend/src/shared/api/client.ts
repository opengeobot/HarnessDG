/**
 * 功能: 统一 API Client。封装 baseURL=/api/v1、请求/响应拦截、
 *       统一成功响应 {data,requestId,traceId,timestamp} 解包与失败响应规范化抛出。
 *       扩展: 请求注入 Authorization: Bearer（内存令牌）；对认证端点开启 withCredentials
 *       以携带/接收 refresh Cookie；响应 401 且非认证端点时自动刷新一次并重试。
 *       页面与 Hook 禁止直接调用 fetch 或拼接 URL，一律经此 client 访问后端。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiErrorBody, ApiResponse } from '@/shared/types';
import { ApiError } from './errors';
import {
  getAccessToken,
  invokePasswordChangeRequired,
  invokeRefresh,
  invokeUnauthorized,
} from './tokenHolder';

/** 统一 API 前缀，对齐网关路由 /api/v1 */
const API_BASE_URL = '/api/v1';

/** 认证相关端点：使用安全 Cookie 传递 refresh JWT，需开启 withCredentials */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

/** 判断是否为认证端点（用于 withCredentials 与 401 重试豁免） */
function isAuthEndpoint(url?: string): boolean {
  if (!url) {
    return false;
  }
  return AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint));
}

/**
 * 生成请求级 Request Id（占位实现）。
 * 注意: 不写入 LocalStorage、埋点或错误上报，遵循设计第 15.7 节安全约束。
 */
function generateRequestId(): string {
  const cryptoRef = globalThis.crypto;
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return `req_${cryptoRef.randomUUID()}`;
  }
  return `req_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
}

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截：注入统一上下文头（X-Request-Id / Accept-Language）与访问令牌
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers.set('X-Request-Id', generateRequestId());
  if (!config.headers.has('Accept-Language')) {
    config.headers.set('Accept-Language', 'zh-CN');
  }
  // 认证端点需携带安全 Cookie 完成 refresh 轮换
  if (isAuthEndpoint(config.url)) {
    config.withCredentials = true;
  }
  const token = getAccessToken();
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

/** 内部标记：请求配置已重试过一次，避免刷新失败后的无限循环 */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

// 响应拦截：403 PASSWORD_CHANGE_REQUIRED → 跳转改密；401 且非认证端点时尝试刷新一次并重试
instance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    const body = error.response?.data as Partial<ApiErrorBody> | undefined;

    // 强制改密：后端对 mustChangePassword 用户拦截受保护端点，跳转 /profile 引导改密
    if (
      status === 403 &&
      body?.code === 'PASSWORD_CHANGE_REQUIRED'
    ) {
      invokePasswordChangeRequired();
      return Promise.reject(error);
    }

    if (
      status === 401 &&
      config &&
      !config._retried &&
      !isAuthEndpoint(config.url)
    ) {
      config._retried = true;
      const newToken = await invokeRefresh();
      if (newToken) {
        config.headers.set('Authorization', `Bearer ${newToken}`);
        return instance.request(config);
      }
      // 刷新失败：清理内存会话并跳转登录
      invokeUnauthorized();
    }

    return Promise.reject(error);
  },
);

function toApiError(error: AxiosError): ApiError {
  const response = error.response;
  const body = response?.data as Partial<ApiErrorBody> | undefined;
  if (body && typeof body.code === 'string') {
    return new ApiError(
      {
        code: body.code,
        message: body.message ?? error.message,
        i18nKey: body.i18nKey ?? 'error.common.unknown',
        details: body.details,
        retryable: body.retryable ?? false,
        requestId: body.requestId,
        traceId: body.traceId,
      },
      response?.status,
    );
  }
  // 网络错误或非标准响应，规范化为统一异常，不向页面暴露原始堆栈
  return new ApiError(
    {
      code: 'COMMON_NETWORK_ERROR',
      message: error.message || '网络请求失败',
      i18nKey: 'error.common.network',
      retryable: true,
    },
    response?.status,
  );
}

/**
 * 发起请求并解包统一成功响应，返回业务 data。
 * 失败时抛出规范化的 ApiError。
 */
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await instance.request<ApiResponse<T>>(config);
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      throw toApiError(error);
    }
    throw error;
  }
}

/** 统一 API Client，仅暴露语义化方法，屏蔽底层 HTTP 细节 */
export const apiClient = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'GET', url });
  },
  post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'POST', url, data: body });
  },
  put<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'PUT', url, data: body });
  },
  patch<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'PATCH', url, data: body });
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return request<T>({ ...config, method: 'DELETE', url });
  },
};

export { API_BASE_URL };

/**
 * 功能: 统一 API Client。封装 baseURL=/api/v1、请求/响应拦截、
 *       统一成功响应 {data,requestId,traceId,timestamp} 解包与失败响应规范化抛出。
 *       页面与 Hook 禁止直接调用 fetch 或拼接 URL，一律经此 client 访问后端。
 * 时间: 2026-06-29
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

/** 统一 API 前缀，对齐网关路由 /api/v1 */
const API_BASE_URL = '/api/v1';

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

// 请求拦截：注入统一上下文头（X-Request-Id / Accept-Language）
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  config.headers.set('X-Request-Id', generateRequestId());
  if (!config.headers.has('Accept-Language')) {
    config.headers.set('Accept-Language', 'zh-CN');
  }
  return config;
});

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

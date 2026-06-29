/**
 * 功能: 统一 API 错误类型，承载后端失败响应结构供页面错误处理使用
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
import type { ApiErrorBody } from '@/shared/types';

/**
 * 统一 API 异常。所有经由 API Client 的失败都会被规范化为该异常抛出，
 * 页面/Hook 仅需捕获 ApiError，禁止各自解析原始响应体。
 */
export class ApiError extends Error {
  readonly code: string;
  readonly i18nKey: string;
  readonly retryable: boolean;
  readonly httpStatus?: number;
  readonly details?: Record<string, unknown>;
  readonly requestId?: string;
  readonly traceId?: string;

  constructor(body: ApiErrorBody, httpStatus?: number) {
    super(body.message);
    this.name = 'ApiError';
    this.code = body.code;
    this.i18nKey = body.i18nKey;
    this.retryable = body.retryable;
    this.httpStatus = httpStatus;
    this.details = body.details;
    this.requestId = body.requestId;
    this.traceId = body.traceId;
  }
}

/** 判断未知异常是否为 ApiError */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

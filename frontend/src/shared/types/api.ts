/**
 * 功能: 统一 API 契约类型 (成功响应、失败响应、分页)，对齐设计文档第 5.8 节
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/** 统一成功响应包装 */
export interface ApiResponse<T> {
  data: T;
  requestId: string;
  traceId: string;
  timestamp: string;
}

/** 统一失败响应结构 */
export interface ApiErrorBody {
  code: string;
  message: string;
  i18nKey: string;
  details?: Record<string, unknown>;
  retryable: boolean;
  requestId?: string;
  traceId?: string;
}

/** 游标分页 (高增长列表，如资产检索、审计、任务) */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

/** 偏移分页 (小型后台字典、角色列表) */
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

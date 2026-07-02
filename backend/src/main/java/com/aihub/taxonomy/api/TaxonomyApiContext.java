/*
 * 功能: taxonomy API 上下文辅助，统一读取请求主体并包装统一响应（字典与标签控制器共用）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;

/**
 * taxonomy API 上下文辅助。
 *
 * <p>集中从入口建立的 {@link PrincipalContext} 读取主体与关联标识，避免在每个控制器方法重复样板。
 */
public final class TaxonomyApiContext {

    private TaxonomyApiContext() {
    }

    /**
     * @return 当前主体 ID（未认证时为 {@code null}）
     */
    public static String principalId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }

    /**
     * 以统一成功响应包装业务数据，回填 requestId/traceId。
     */
    public static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}

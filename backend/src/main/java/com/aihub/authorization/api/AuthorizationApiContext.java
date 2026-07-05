/*
 * 功能: authorization API 上下文辅助，统一包装成功响应并回填 requestId/traceId。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;

/**
 * authorization API 上下文辅助。
 */
final class AuthorizationApiContext {

    private AuthorizationApiContext() {
    }

    static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }

    static String principalId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }
}

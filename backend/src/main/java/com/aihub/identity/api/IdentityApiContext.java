/*
 * 功能: identity API 上下文辅助，统一读取请求主体并包装统一响应。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;

/**
 * identity API 上下文辅助。
 *
 * <p>集中从入口建立的 {@link PrincipalContext} 读取主体与关联标识，避免在每个控制器方法重复样板。
 */
final class IdentityApiContext {

    private IdentityApiContext() {
    }

    static PrincipalContext require() {
        return PrincipalContextHolder.require();
    }

    static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}

/*
 * 功能: transfer API 上下文辅助，统一包装平台成功响应信封。
 * 时间: 2026-09-24
 * 作者: AxeXie
 */
package com.aihub.transfer.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;

/**
 * transfer API 上下文辅助。
 *
 * <p>以统一成功响应包装业务数据，requestId/traceId 从入口建立的 {@link PrincipalContext} 回填。
 */
final class TransferApiContext {

    private TransferApiContext() {
    }

    static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}

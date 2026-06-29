/*
 * 功能: 资产 API 上下文辅助，统一读取请求主体并包装统一响应。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;

/**
 * 资产 API 上下文辅助。
 *
 * <p>集中从入口建立的 {@link PrincipalContext} 读取主体与关联标识，避免在每个控制器方法重复样板。
 */
final class AssetApiContext {

    private AssetApiContext() {
    }

    /**
     * @return 当前主体 ID（P1 未接入认证时可能为 {@code null}）
     */
    static String principalId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }

    /**
     * 以统一成功响应包装业务数据，回填 requestId/traceId。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一成功响应
     */
    static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}

/*
 * 功能: 统一成功响应包装，承载业务数据与请求追踪上下文。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.api;

import java.time.Instant;

/**
 * 统一成功响应包装。
 *
 * <p>所有 REST 成功响应统一以本结构对外返回，业务数据置于 {@code data}，
 * 同时携带 {@code requestId}、{@code traceId} 与服务端 {@code timestamp} 便于排障与审计关联。
 *
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(T data, String requestId, String traceId, Instant timestamp) {

    /**
     * 使用当前时间构造成功响应。
     *
     * @param data      业务数据
     * @param requestId 请求 ID
     * @param traceId   分布式追踪 ID
     * @param <T>       业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> of(T data, String requestId, String traceId) {
        return new ApiResponse<>(data, requestId, traceId, Instant.now());
    }
}

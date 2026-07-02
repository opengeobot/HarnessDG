/*
 * 功能: 幂等键值对象，封装 Idempotency-Key 头解析与作用域绑定。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

/**
 * 幂等键。
 *
 * <p>由写接口入口从 {@code Idempotency-Key} 头解析；与主体、HTTP 方法、请求路径共同界定
 * 幂等作用域，避免跨主体或跨资源误命中。
 *
 * @param key           客户端提供的幂等键
 * @param principalId   发起主体 ID
 * @param method        HTTP 方法
 * @param path          请求路径
 */
public record IdempotencyKey(String key, String principalId, String method, String path) {

    public IdempotencyKey {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }
}

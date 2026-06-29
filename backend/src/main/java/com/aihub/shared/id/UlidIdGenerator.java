/*
 * 功能: 基于 ULID 的业务 ID 生成器默认实现。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.id;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * 基于 ULID 的 {@link IdGenerator} 默认实现。
 *
 * <p>使用单调 ULID 保证同毫秒内的递增有序性，输出统一为小写以贴合 URL 友好规范。
 */
public class UlidIdGenerator implements IdGenerator {

    private static final char SEPARATOR = '_';

    @Override
    public String generate(IdPrefix prefix) {
        return generate(prefix.value());
    }

    @Override
    public String generate(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("ID prefix must not be blank");
        }
        String ulid = UlidCreator.getMonotonicUlid().toString().toLowerCase();
        return prefix + SEPARATOR + ulid;
    }
}

/*
 * 功能: 业务 ID 生成器接口，约定"业务前缀 + ULID"的生成契约。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.shared.id;

/**
 * 业务 ID 生成器。
 *
 * <p>统一生成"业务前缀_ULID"形式的标识，保证可读、单调有序与全局唯一。
 */
public interface IdGenerator {

    /**
     * 按预定义前缀生成业务 ID。
     *
     * @param prefix 业务前缀
     * @return 形如 {@code ast_01J...} 的业务 ID
     */
    String generate(IdPrefix prefix);

    /**
     * 按自定义前缀生成业务 ID，便于扩展尚未纳入枚举的对象类型。
     *
     * @param prefix 前缀字面值（不含分隔符）
     * @return 形如 {@code prefix_01J...} 的业务 ID
     */
    String generate(String prefix);
}

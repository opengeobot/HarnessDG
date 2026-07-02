/*
 * 功能: 口令哈希契约，约定自适应不可逆哈希与校验能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

/**
 * 口令哈希器。
 *
 * <p>使用自适应单向哈希（如 BCrypt）存储口令摘要，严禁可逆加密或明文存储。
 * 明文口令不得进入日志、埋点或测试 Fixture。
 */
public interface PasswordHasher {

    /**
     * 对明文口令生成不可逆哈希摘要。
     *
     * @param rawPassword 明文口令
     * @return 编码后的哈希摘要（含算法与盐）
     */
    String hash(CharSequence rawPassword);

    /**
     * 校验明文口令是否与已存储摘要匹配。
     *
     * @param rawPassword     明文口令
     * @param encodedPassword 已存储的哈希摘要
     * @return 是否匹配
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

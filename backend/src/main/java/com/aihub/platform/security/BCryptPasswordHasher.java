/*
 * 功能: 基于 Spring Security BCrypt 的口令哈希实现。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import com.aihub.shared.security.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 基于 BCrypt 的 {@link PasswordHasher} 实现。
 *
 * <p>使用 Spring Security {@link BCryptPasswordEncoder} 进行自适应单向哈希；
 * 哈希不可逆，明文口令不进入日志。
 */
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this(new BCryptPasswordEncoder());
    }

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(CharSequence rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, encodedPassword);
    }
}

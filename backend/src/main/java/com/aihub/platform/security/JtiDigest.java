/*
 * 功能: JWT jti 摘要工具——SHA-256 十六进制，用于令牌存储与查询。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * JWT jti 摘要工具。
 */
public final class JtiDigest {

    private JtiDigest() {
    }

    /**
     * 计算 jti 的 SHA-256 十六进制摘要。
     */
    public static String sha256Hex(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jti.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

/*
 * 功能: 字段级敏感数据脱敏器，统一清除/掩码认证头、Cookie、口令、JWT 与预签名凭据。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.logging;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 字段级敏感数据脱敏器。
 *
 * <p>在日志边界统一处理敏感信息：对敏感字段名整体掩码；对文本中的 Bearer Token、Cookie、
 * 预签名查询串（含 {@code X-Amz-} 系列参数）做正则掩码。脱敏不可逆，仅保留极少前缀辅助排障。
 *
 * <p>本类无状态、线程安全，属于 shared-kernel，不依赖任何具体日志框架。
 */
public final class SensitiveDataMasker {

    /** 掩码占位符。 */
    public static final String MASK = "***";

    /** 敏感字段名（小写匹配，整体掩码）。 */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "password",
            "passwd",
            "secret",
            "token",
            "access_token",
            "refresh_token",
            "id_token",
            "jwt",
            "x-amz-credential",
            "x-amz-signature",
            "x-amz-security-token",
            "aws_secret_access_key",
            "aws_access_key_id");

    /** Bearer Token 掩码：保留方案前缀，掩码实际 Token。 */
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)(bearer)\\s+[A-Za-z0-9\\-._~+/]+=*");

    /** 紧凑 JWT 掩码：三段 base64url。 */
    private static final Pattern JWT_PATTERN =
            Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");

    /** 预签名 / 凭据查询参数掩码：X-Amz-* 与常见 token/signature/credential 参数值。 */
    private static final Pattern PRESIGNED_QUERY_PATTERN =
            Pattern.compile("(?i)([?&](?:x-amz-[a-z-]+|signature|credential|token|access_key|secret)=)"
                    + "[^&\\s]+");

    /**
     * 判断字段名是否敏感（大小写无关）。
     *
     * @param fieldName 字段名
     * @return 是否敏感
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return SENSITIVE_FIELD_NAMES.contains(fieldName.toLowerCase(Locale.ROOT));
    }

    /**
     * 按字段名脱敏：敏感字段整体掩码，否则脱敏值内的 Token/凭据。
     *
     * @param fieldName 字段名
     * @param value     字段值
     * @return 脱敏后的值
     */
    public String maskField(String fieldName, String value) {
        if (isSensitiveField(fieldName)) {
            return MASK;
        }
        return maskValue(value);
    }

    /**
     * 对任意文本做内容级脱敏，清除 Bearer Token、JWT 与预签名/凭据查询串。
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    public String maskValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = BEARER_PATTERN.matcher(value).replaceAll("$1 " + MASK);
        masked = JWT_PATTERN.matcher(masked).replaceAll(MASK);
        masked = PRESIGNED_QUERY_PATTERN.matcher(masked).replaceAll("$1" + MASK);
        return masked;
    }
}

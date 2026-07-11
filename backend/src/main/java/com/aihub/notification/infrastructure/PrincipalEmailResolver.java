/*
 * 功能: 主体→邮箱解析——查询 iam_user.email，供邮件渠道外发。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 主体邮箱解析器。
 *
 * <p>优先将 {@code recipient} 视为邮箱地址；否则按 {@code principal_id} 查询
 * {@code iam_user.email}。Agent/Service 等非用户主体无邮箱字段，返回空并由渠道跳过投递。
 */
@Component
public class PrincipalEmailResolver {

    private final JdbcTemplate jdbcTemplate;

    public PrincipalEmailResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析外发邮箱地址。
     *
     * @param recipient 邮箱地址或 principal_id
     * @return 可投递邮箱；无映射时为空
     */
    public Optional<String> resolveEmail(String recipient) {
        if (!StringUtils.hasText(recipient)) {
            return Optional.empty();
        }
        String trimmed = recipient.trim();
        if (trimmed.contains("@")) {
            return Optional.of(trimmed);
        }
        List<String> emails = jdbcTemplate.queryForList("""
                SELECT email FROM iam_user
                WHERE principal_id = ? AND email IS NOT NULL AND TRIM(email) <> ''
                """, String.class, trimmed);
        if (emails.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(emails.getFirst().trim());
    }
}

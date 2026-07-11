/*
 * 功能: 密码改密状态 JDBC 查询实现。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.application.PasswordChangeQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 {@code iam_user.must_change_password} 的改密状态查询。
 */
@Repository
public class JdbcPasswordChangeQueryPort implements PasswordChangeQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPasswordChangeQueryPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isPasswordChangeRequired(String principalId) {
        if (principalId == null || principalId.isBlank()) {
            return false;
        }
        Integer flag = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM iam_user WHERE principal_id = ?",
                Integer.class, principalId);
        return flag != null && flag == 1;
    }
}

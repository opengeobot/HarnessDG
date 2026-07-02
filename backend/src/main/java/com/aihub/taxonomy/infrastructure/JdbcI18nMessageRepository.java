/*
 * 功能: 国际化文案仓储 JDBC 实现，按 locale 读取 system_i18n_message 的 i18n_key→文案 映射。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.infrastructure;

import com.aihub.taxonomy.domain.I18nMessageRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 国际化文案仓储 JDBC 实现。
 */
@Repository
public class JdbcI18nMessageRepository implements I18nMessageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcI18nMessageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, String> findAllByLocale(String locale) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT message_key, message FROM system_i18n_message WHERE locale = :locale ORDER BY message_key",
                new MapSqlParameterSource("locale", locale),
                rs -> {
                    result.put(rs.getString("message_key"), rs.getString("message"));
                });
        return result;
    }
}

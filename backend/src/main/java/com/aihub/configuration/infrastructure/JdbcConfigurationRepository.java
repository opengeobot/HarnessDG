/*
 * 功能: 统一配置仓储 JDBC 实现，读写 system_config，更新时以 expectedVersion 做乐观并发校验。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.configuration.infrastructure;

import com.aihub.configuration.domain.ConfigurationRepository;
import com.aihub.configuration.domain.ConfigValueType;
import com.aihub.configuration.domain.SystemConfig;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 统一配置仓储 JDBC 实现。
 */
@Repository
public class JdbcConfigurationRepository implements ConfigurationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcConfigurationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SystemConfig> findAll() {
        return jdbcTemplate.query(
                "SELECT config_id, config_key, value_type, config_value, default_value, scope_type, "
                        + "scope_id, hot_reloadable, validator, description, version, updated_by "
                        + "FROM system_config ORDER BY config_key",
                new MapSqlParameterSource(), this::mapConfig);
    }

    @Override
    public Optional<SystemConfig> findByKey(String configKey) {
        List<SystemConfig> result = jdbcTemplate.query(
                "SELECT config_id, config_key, value_type, config_value, default_value, scope_type, "
                        + "scope_id, hot_reloadable, validator, description, version, updated_by "
                        + "FROM system_config WHERE config_key = :configKey",
                new MapSqlParameterSource("configKey", configKey), this::mapConfig);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public SystemConfig update(String configKey, String configValue, long expectedVersion, String updatedBy) {
        int affected = jdbcTemplate.update(
                "UPDATE system_config SET config_value = :configValue, version = version + 1, "
                        + "updated_by = :updatedBy, updated_at = now() "
                        + "WHERE config_key = :configKey AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("configValue", configValue)
                        .addValue("updatedBy", updatedBy)
                        .addValue("configKey", configKey)
                        .addValue("expectedVersion", expectedVersion));
        if (affected == 0) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "configuration was concurrently modified",
                    Map.of("configKey", configKey));
        }
        return findByKey(configKey).orElseThrow();
    }

    private SystemConfig mapConfig(ResultSet rs, int rowNum) throws SQLException {
        return new SystemConfig(
                rs.getString("config_id"),
                rs.getString("config_key"),
                ConfigValueType.valueOf(rs.getString("value_type")),
                rs.getString("config_value"),
                rs.getString("default_value"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getInt("hot_reloadable") == 1,
                rs.getString("validator"),
                rs.getString("description"),
                rs.getLong("version"),
                rs.getString("updated_by"));
    }
}

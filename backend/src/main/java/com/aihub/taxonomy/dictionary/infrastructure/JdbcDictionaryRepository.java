/*
 * 功能: 字典仓储 JDBC 实现，读写 system_dict_type / system_dict_item，并在字典项变更时自增类型缓存版本。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.infrastructure;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.taxonomy.dictionary.domain.DictionaryItem;
import com.aihub.taxonomy.dictionary.domain.DictionaryRepository;
import com.aihub.taxonomy.dictionary.domain.DictionaryType;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 字典仓储 JDBC 实现。
 */
@Repository
public class JdbcDictionaryRepository implements DictionaryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDictionaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DictionaryType> findAllTypes() {
        return jdbcTemplate.query(
                "SELECT dict_type_id, dict_code, name, i18n_key, description, status, version "
                        + "FROM system_dict_type ORDER BY dict_code",
                new MapSqlParameterSource(), this::mapType);
    }

    @Override
    public boolean typeExists(String dictCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM system_dict_type WHERE dict_code = :dictCode",
                new MapSqlParameterSource("dictCode", dictCode), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<DictionaryItem> findItems(String dictCode, boolean includeDisabled) {
        String sql = "SELECT dict_item_id, dict_code, item_code, i18n_key, sort_order, status, version "
                + "FROM system_dict_item WHERE dict_code = :dictCode";
        MapSqlParameterSource params = new MapSqlParameterSource("dictCode", dictCode);
        if (!includeDisabled) {
            sql += " AND status = 'ACTIVE'";
        }
        sql += " ORDER BY sort_order, item_code";
        return jdbcTemplate.query(sql, params, this::mapItem);
    }

    @Override
    public Optional<DictionaryItem> findItem(String dictCode, String itemCode) {
        List<DictionaryItem> result = jdbcTemplate.query(
                "SELECT dict_item_id, dict_code, item_code, i18n_key, sort_order, status, version "
                        + "FROM system_dict_item WHERE dict_code = :dictCode AND item_code = :itemCode",
                new MapSqlParameterSource()
                        .addValue("dictCode", dictCode)
                        .addValue("itemCode", itemCode), this::mapItem);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public void insertItem(DictionaryItem item) {
        jdbcTemplate.update(
                "INSERT INTO system_dict_item (dict_item_id, dict_code, item_code, i18n_key, sort_order, "
                        + "status, version, created_at, updated_at) "
                        + "VALUES (:dictItemId, :dictCode, :itemCode, :i18nKey, :sortOrder, :status, :version, "
                        + "now(), now())",
                new MapSqlParameterSource()
                        .addValue("dictItemId", item.dictItemId())
                        .addValue("dictCode", item.dictCode())
                        .addValue("itemCode", item.itemCode())
                        .addValue("i18nKey", item.i18nKey())
                        .addValue("sortOrder", item.sortOrder())
                        .addValue("status", item.status().name())
                        .addValue("version", item.version()));
    }

    @Override
    public void updateItem(DictionaryItem item, long expectedVersion) {
        int affected = jdbcTemplate.update(
                "UPDATE system_dict_item SET i18n_key = :i18nKey, sort_order = :sortOrder, status = :status, "
                        + "version = version + 1, updated_at = :now "
                        + "WHERE dict_code = :dictCode AND item_code = :itemCode AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("i18nKey", item.i18nKey())
                        .addValue("sortOrder", item.sortOrder())
                        .addValue("status", item.status().name())
                        .addValue("dictCode", item.dictCode())
                        .addValue("itemCode", item.itemCode())
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", toOffsetDateTime(java.time.Instant.now())));
        if (affected == 0) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "dictionary item was concurrently modified",
                    Map.of("dictCode", item.dictCode(), "itemCode", item.itemCode()));
        }
    }

    @Override
    public void incrementTypeVersion(String dictCode) {
        jdbcTemplate.update(
                "UPDATE system_dict_type SET version = version + 1, updated_at = now() WHERE dict_code = :dictCode",
                new MapSqlParameterSource("dictCode", dictCode));
    }

    private DictionaryType mapType(ResultSet rs, int rowNum) throws SQLException {
        return new DictionaryType(
                rs.getString("dict_type_id"),
                rs.getString("dict_code"),
                rs.getString("name"),
                rs.getString("i18n_key"),
                rs.getString("description"),
                TaxonomyStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private DictionaryItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new DictionaryItem(
                rs.getString("dict_item_id"),
                rs.getString("dict_code"),
                rs.getString("item_code"),
                rs.getString("i18n_key"),
                rs.getInt("sort_order"),
                TaxonomyStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

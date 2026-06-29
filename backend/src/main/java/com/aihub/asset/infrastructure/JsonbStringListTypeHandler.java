/*
 * 功能: MyBatis 类型处理器，在 List<String> 与 PostgreSQL jsonb 列之间互转。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * {@code List<String>} 与 jsonb 列的类型处理器。
 *
 * <p>写入时以 {@code Types.OTHER} 传入 JSON 字符串，由 PostgreSQL 驱动转换为 jsonb，避免引入
 * 驱动私有类型；读取时将 jsonb 文本解析为字符串列表。用于 {@code owners} / {@code tags} 列。
 */
public class JsonbStringListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, writeJson(parameter), Types.OTHER);
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return readJson(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return readJson(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return readJson(cs.getString(columnIndex));
    }

    private String writeJson(List<String> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize string list to jsonb", ex);
        }
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to deserialize jsonb to string list", ex);
        }
    }
}

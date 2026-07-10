package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 最小安全预览 Job Handler。
 *
 * <p>处理 {@code PREVIEW_GENERATE} 类型任务：解析工件内容（CSV/JSONL），
 * 脱敏后写入 {@code asset_preview} 表。
 *
 * <p>限制：100 行 / 50 列 / 1MiB 内容。
 */
@Component
public class PreviewJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewJobHandler.class);

    /** 预览最大行数。 */
    private static final int MAX_ROWS = 100;

    /** 预览最大列数。 */
    private static final int MAX_COLS = 50;

    /** 预览内容最大字节数：1MiB。 */
    private static final long MAX_CONTENT_BYTES = 1024L * 1024;

    private final IdGenerator idGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PreviewJobHandler(IdGenerator idGenerator,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.idGenerator = idGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "PREVIEW_GENERATE";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String assetId = payload.path("assetId").asText(context.assetId());
        String versionId = payload.path("versionId").asText(null);
        String content = payload.path("content").asText("");
        String contentType = payload.path("contentType").asText("text/csv");

        LOG.info("generating preview assetId={} versionId={} contentType={}", assetId, versionId, contentType);

        // 内容大小检查
        if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            LOG.warn("preview content exceeds 1MiB limit assetId={}", assetId);
            return;
        }

        // 解析内容
        String previewJson;
        if ("text/csv".equals(contentType)) {
            previewJson = parseCsv(content);
        } else if ("application/x-ndjson".equals(contentType) || "application/jsonl".equals(contentType)) {
            previewJson = parseJsonl(content);
        } else if ("application/x-parquet".equals(contentType) || "parquet".equals(contentType)
                || "application/vnd.apache.parquet".equals(contentType)) {
            previewJson = parseParquet(content, payload.path("contentEncoding").asText(null));
        } else {
            // 其他类型：原样截取
            previewJson = objectMapper.writeValueAsString(Map.of("raw", truncate(content, MAX_ROWS * 100)));
        }

        // 幂等写入预览（先删除再插入）
        String previewId = idGenerator.generate(IdPrefix.PREVIEW);
        if (versionId != null) {
            jdbcTemplate.update("DELETE FROM asset_preview WHERE asset_id = ? AND version_id = ?", assetId, versionId);
        } else {
            jdbcTemplate.update("DELETE FROM asset_preview WHERE asset_id = ? AND version_id IS NULL", assetId);
        }

        jdbcTemplate.update("""
                INSERT INTO asset_preview (preview_id, asset_id, version_id, content_type, content, generated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, previewId, assetId, versionId, "application/json", previewJson, Instant.now());

        LOG.info("preview generated previewId={} assetId={}", previewId, assetId);
    }

    /**
     * 解析 Parquet 二进制（可选 base64 编码）为 JSON 表格。
     */
    private String parseParquet(String content, String contentEncoding) throws Exception {
        byte[] bytes = ParquetPreviewReader.decodeContent(content, contentEncoding);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new UnsupportedOperationException("PREVIEW_UNSUPPORTED_FORMAT: parquet exceeds size limit");
        }
        try {
            List<Map<String, Object>> rows = ParquetPreviewReader.readRows(bytes, MAX_ROWS, MAX_COLS);
            return objectMapper.writeValueAsString(Map.of("rows", rows, "truncated", rows.size() >= MAX_ROWS));
        } catch (UnsupportedOperationException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warn("parquet preview failed, returning unsupported format", ex);
            throw new UnsupportedOperationException("PREVIEW_UNSUPPORTED_FORMAT: " + ex.getMessage(), ex);
        }
    }

    /**
     * 解析 CSV 内容为 JSON 表格。
     */
    private String parseCsv(String csvContent) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return objectMapper.writeValueAsString(Map.of("rows", List.of()));
            }
            String[] headers = headerLine.split(",");
            if (headers.length > MAX_COLS) {
                headers = java.util.Arrays.copyOf(headers, MAX_COLS);
            }

            String line;
            int rowCount = 0;
            while ((line = reader.readLine()) != null && rowCount < MAX_ROWS) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), sanitize(values[i].trim()));
                }
                rows.add(row);
                rowCount++;
            }
        }
        return objectMapper.writeValueAsString(Map.of("rows", rows, "truncated", rows.size() >= MAX_ROWS));
    }

    /**
     * 解析 JSONL（每行一个 JSON 对象）。
     */
    private String parseJsonl(String jsonlContent) throws Exception {
        List<Object> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(jsonlContent))) {
            String line;
            int rowCount = 0;
            while ((line = reader.readLine()) != null && rowCount < MAX_ROWS) {
                if (line.isBlank()) continue;
                try {
                    Object parsed = objectMapper.readValue(line, Object.class);
                    rows.add(parsed);
                } catch (Exception e) {
                    rows.add(Map.of("_raw", sanitize(line)));
                }
                rowCount++;
            }
        }
        return objectMapper.writeValueAsString(Map.of("rows", rows, "truncated", rows.size() >= MAX_ROWS));
    }

    /**
     * 脱敏处理：移除潜在的危险字符。
     */
    private String sanitize(String value) {
        if (value == null) return "";
        // 移除控制字符（保留换行和制表符）
        return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private String truncate(String content, int maxChars) {
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "...";
    }
}

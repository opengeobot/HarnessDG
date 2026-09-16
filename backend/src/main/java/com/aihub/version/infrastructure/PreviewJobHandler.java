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
            String errorJson = objectMapper.writeValueAsString(Map.of(
                    "errorReason", "PREVIEW_SIZE_EXCEEDED",
                    "rows", List.of()));
            savePreview(assetId, versionId, errorJson);
            return;
        }

        // 解析内容
        String previewJson;
        if ("text/csv".equals(contentType)) {
            previewJson = parseCsv(content);
        } else if ("application/x-ndjson".equals(contentType) || "application/jsonl".equals(contentType)) {
            previewJson = parseJsonl(content);
        } else if ("application/x-parquet".equals(contentType) || "parquet".equals(contentType)) {
            // P5: Parquet 格式预览（需要 Apache Parquet 库，当前返回占位信息）
            previewJson = objectMapper.writeValueAsString(Map.of(
                    "format", "parquet",
                    "message", "Parquet preview requires native library, pending integration",
                    "rows", List.of()));
        } else {
            // 其他类型：原样截取
            previewJson = objectMapper.writeValueAsString(Map.of("raw", truncate(content, MAX_ROWS * 100)));
        }

        savePreview(assetId, versionId, previewJson);
    }

    /**
     * 幂等写入预览记录（先删除再插入）。
     */
    private void savePreview(String assetId, String versionId, String previewJson) {
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
     * 解析 CSV 内容为 JSON 表格（RFC 4180 兼容）。
     *
     * <p>支持引号字段、转义引号（"" → "）、引号内换行符等标准 CSV 特性。
     */
    private String parseCsv(String csvContent) throws Exception {
        List<String[]> allRecords = parseCsvRecords(csvContent);
        if (allRecords.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of("rows", List.of()));
        }

        String[] rawHeaders = allRecords.remove(0);
        boolean columnTruncated = rawHeaders.length > MAX_COLS;
        String[] headers = applyColumnLimit(rawHeaders);

        List<Map<String, String>> rows = new ArrayList<>();
        int rowCount = 0;
        for (String[] values : allRecords) {
            if (rowCount >= MAX_ROWS) break;
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String value = i < values.length ? sanitize(values[i]) : "";
                row.put(headers[i].trim(), value);
            }
            rows.add(row);
            rowCount++;
        }
        return objectMapper.writeValueAsString(Map.of(
                "rows", rows,
                "truncated", rowCount >= MAX_ROWS,
                "columnTruncated", columnTruncated));
    }

    /**
     * RFC 4180 CSV 解析器。将整段 CSV 文本解析为记录列表（每条记录是一个字段数组）。
     *
     * <p>规则：
     * <ul>
     *   <li>字段可以是用双引号包裹的，也可以是无引号的。</li>
     *   <li>引号字段中的双引号通过连续两个双引号（""）转义。</li>
     *   <li>引号字段中可以包含逗号、换行符等分隔符。</li>
     *   <li>CRLF 和 LF 均作为记录分隔符。</li>
     * </ul>
     */
    private List<String[]> parseCsvRecords(String csv) {
        List<String[]> records = new ArrayList<>();
        int len = csv.length();
        int pos = 0;

        while (pos <= len) {
            List<String> fields = new ArrayList<>();

            // 解析一条记录的所有字段
            while (true) {
                // 跳过字段前导空格（仅用于判断是否引号字段）
                int fieldStart = pos;

                if (pos < len && csv.charAt(pos) == '"') {
                    // 引号字段
                    pos++; // skip opening quote
                    StringBuilder sb = new StringBuilder();
                    while (pos < len) {
                        char c = csv.charAt(pos);
                        if (c == '"') {
                            if (pos + 1 < len && csv.charAt(pos + 1) == '"') {
                                // escaped quote
                                sb.append('"');
                                pos += 2;
                            } else {
                                // closing quote
                                pos++; // skip closing quote
                                break;
                            }
                        } else {
                            sb.append(c);
                            pos++;
                        }
                    }
                    fields.add(sb.toString());

                    // 跳过关闭引号后的空白
                    while (pos < len && (csv.charAt(pos) == ' ' || csv.charAt(pos) == '\t')) {
                        pos++;
                    }
                } else {
                    // 无引号字段 — 读到逗号或行末
                    while (pos < len && csv.charAt(pos) != ','
                            && csv.charAt(pos) != '\r' && csv.charAt(pos) != '\n') {
                        pos++;
                    }
                    fields.add(csv.substring(fieldStart, pos));
                }

                // 检查字段终止符
                if (pos < len && csv.charAt(pos) == ',') {
                    pos++; // skip comma, next field
                    continue;
                }

                // CRLF 或 LF → 记录结束
                if (pos < len && csv.charAt(pos) == '\r') {
                    pos++; // skip CR
                    if (pos < len && csv.charAt(pos) == '\n') {
                        pos++; // skip LF
                    }
                    break;
                }
                if (pos < len && csv.charAt(pos) == '\n') {
                    pos++; // skip LF
                    break;
                }

                // 文件末尾
                break;
            }

            // 跳过空记录末尾（连续 CRLF）
            if (pos >= len && fields.size() == 1 && fields.get(0).isEmpty()) {
                break;
            }

            records.add(fields.toArray(new String[0]));
        }

        return records;
    }

    private String[] applyColumnLimit(String[] headers) {
        if (headers.length > MAX_COLS) {
            return java.util.Arrays.copyOf(headers, MAX_COLS);
        }
        return headers;
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

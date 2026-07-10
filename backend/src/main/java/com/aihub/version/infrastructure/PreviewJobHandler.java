package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 最小安全预览 Job Handler。
 *
 * <p>处理 {@code PREVIEW_GENERATE} 类型任务：解析工件内容（CSV/JSONL），
 * 脱敏后写入 {@code asset_preview} 表。
 *
 * <p>资源上限由 {@link PreviewProperties}（{@code aihub.preview.*}）配置；
 * Worker 进程级内存/CPU 隔离见运维 Runbook cgroup 说明。
 */
@Component
public class PreviewJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewJobHandler.class);

    private final IdGenerator idGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PreviewProperties previewProperties;
    private final PlatformMetrics platformMetrics;

    public PreviewJobHandler(IdGenerator idGenerator,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper,
                             PreviewProperties previewProperties,
                             ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.idGenerator = idGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.previewProperties = previewProperties;
        this.platformMetrics = platformMetricsProvider.getIfAvailable();
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

        int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentBytes > previewProperties.maxBytes()) {
            recordPreviewFailure("limit_exceeded");
            throw new UnsupportedOperationException(
                    "PREVIEW_LIMIT_EXCEEDED: content bytes " + contentBytes
                            + " exceed maxBytes " + previewProperties.maxBytes());
        }

        String previewJson;
        if ("text/csv".equals(contentType)) {
            previewJson = parseCsv(content);
        } else if ("application/x-ndjson".equals(contentType) || "application/jsonl".equals(contentType)) {
            previewJson = parseJsonl(content);
        } else if ("application/x-parquet".equals(contentType) || "parquet".equals(contentType)
                || "application/vnd.apache.parquet".equals(contentType)) {
            previewJson = parseParquet(content, payload.path("contentEncoding").asText(null));
        } else {
            previewJson = objectMapper.writeValueAsString(Map.of(
                    "raw", truncate(content, previewProperties.maxRows() * 100)));
        }

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

    private String parseParquet(String content, String contentEncoding) throws Exception {
        byte[] bytes = ParquetPreviewReader.decodeContent(content, contentEncoding);
        if (bytes.length > previewProperties.maxBytes()) {
            recordPreviewFailure("limit_exceeded");
            throw new UnsupportedOperationException("PREVIEW_LIMIT_EXCEEDED: parquet exceeds maxBytes limit");
        }
        try {
            List<Map<String, Object>> rows = ParquetPreviewReader.readRows(
                    bytes, previewProperties.maxRows(), previewProperties.maxCols());
            return objectMapper.writeValueAsString(Map.of(
                    "rows", rows, "truncated", rows.size() >= previewProperties.maxRows()));
        } catch (UnsupportedOperationException ex) {
            recordPreviewFailure("unsupported_format");
            throw ex;
        } catch (Exception ex) {
            LOG.warn("parquet preview failed, returning unsupported format", ex);
            recordPreviewFailure("unsupported_format");
            throw new UnsupportedOperationException("PREVIEW_UNSUPPORTED_FORMAT: " + ex.getMessage(), ex);
        }
    }

    private String parseCsv(String csvContent) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return objectMapper.writeValueAsString(Map.of("rows", List.of()));
            }
            String[] headers = headerLine.split(",");
            if (headers.length > previewProperties.maxCols()) {
                headers = java.util.Arrays.copyOf(headers, previewProperties.maxCols());
            }

            String line;
            int rowCount = 0;
            while ((line = reader.readLine()) != null && rowCount < previewProperties.maxRows()) {
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), sanitize(values[i].trim()));
                }
                rows.add(row);
                rowCount++;
            }
        }
        return objectMapper.writeValueAsString(Map.of(
                "rows", rows, "truncated", rows.size() >= previewProperties.maxRows()));
    }

    private String parseJsonl(String jsonlContent) throws Exception {
        List<Object> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(jsonlContent))) {
            String line;
            int rowCount = 0;
            while ((line = reader.readLine()) != null && rowCount < previewProperties.maxRows()) {
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
        return objectMapper.writeValueAsString(Map.of(
                "rows", rows, "truncated", rows.size() >= previewProperties.maxRows()));
    }

    private void recordPreviewFailure(String reason) {
        if (platformMetrics != null) {
            platformMetrics.recordPreviewFailure(reason);
        }
    }

    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    private String truncate(String content, int maxChars) {
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "...";
    }
}

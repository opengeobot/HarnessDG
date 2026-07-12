/*
 * 功能: 最小安全预览 Job Handler——支持从对象存储自动取数生成 csv/jsonl/parquet/text/image 预览。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.VersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
 * <p>处理 {@code PREVIEW_GENERATE} 类型任务：当调用方提供 {@code content} 时直接解析；
 * 当 {@code content} 为空时按 {@code versionId} 解析首个可预览 artifact，从对象存储（dvc-cache）
 * 自动取数后解析，支持 CSV/JSONL/Parquet/文本/图片。脱敏后写入 {@code asset_preview} 表。
 *
 * <p>资源上限由 {@link PreviewProperties}（{@code aihub.preview.*}）配置；
 * Worker 进程级内存/CPU 隔离见运维 Runbook cgroup 说明。artifact path 由服务端解析，
 * 非用户 supplied URL，SSRF/DNS-rebinding 攻击面不适用。
 */
@Component
public class PreviewJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PreviewJobHandler.class);
    private static final String DVC_CACHE_BUCKET = "dvc-cache";

    private final IdGenerator idGenerator;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PreviewProperties previewProperties;
    private final PlatformMetrics platformMetrics;
    private final StoragePort storagePort;
    private final VersionRepository versionRepository;

    public PreviewJobHandler(IdGenerator idGenerator,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper,
                             PreviewProperties previewProperties,
                             ObjectProvider<PlatformMetrics> platformMetricsProvider,
                             StoragePort storagePort,
                             VersionRepository versionRepository) {
        this.idGenerator = idGenerator;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.previewProperties = previewProperties;
        this.platformMetrics = platformMetricsProvider.getIfAvailable();
        this.storagePort = storagePort;
        this.versionRepository = versionRepository;
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
        String contentEncoding = payload.path("contentEncoding").asText(null);

        // content 为空时从对象存储按 versionId 自动取数（REQ-DST-DETAIL-001 预览绑定 Version）
        if ((content == null || content.isBlank()) && versionId != null) {
            FetchedSource src = fetchFromStorage(versionId);
            if (src != null) {
                content = src.content();
                contentType = src.contentType();
                contentEncoding = src.contentEncoding();
            }
        }

        LOG.info("generating preview assetId={} versionId={} contentType={}", assetId, versionId, contentType);

        // Parquet 的字节上限在 parseParquet 内部按解码后字节校验；其余类型按 UTF-8 字节校验
        if (!isParquet(contentType)) {
            int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (contentBytes > previewProperties.maxBytes()) {
                recordPreviewFailure("limit_exceeded");
                throw new UnsupportedOperationException(
                        "PREVIEW_LIMIT_EXCEEDED: content bytes " + contentBytes
                                + " exceed maxBytes " + previewProperties.maxBytes());
            }
        }

        String previewJson;
        if ("text/csv".equals(contentType)) {
            previewJson = parseCsv(content);
        } else if ("application/x-ndjson".equals(contentType) || "application/jsonl".equals(contentType)) {
            previewJson = parseJsonl(content);
        } else if (isParquet(contentType)) {
            previewJson = parseParquet(content, contentEncoding);
        } else if (contentType != null && contentType.startsWith("image/")) {
            // 图片不内联二进制；前端经独立预签名端点渲染
            previewJson = objectMapper.writeValueAsString(Map.of(
                    "mediaType", contentType, "truncated", false,
                    "note", "image preview reference; render via presigned endpoint"));
        } else if (contentType != null
                && (contentType.startsWith("text/") || "application/json".equals(contentType))) {
            int maxChars = previewProperties.maxRows() * 100;
            String sanitized = sanitize(content);
            previewJson = objectMapper.writeValueAsString(Map.of(
                    "raw", truncate(sanitized, maxChars),
                    "truncated", sanitized.length() > maxChars));
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

    /**
     * 按 versionId 解析首个可预览 artifact 并从 dvc-cache 取数。
     * 图片类型仅返回安全引用，不读取二进制。对象不存在时抛 PREVIEW_SOURCE_UNAVAILABLE。
     */
    private FetchedSource fetchFromStorage(String versionId) {
        List<Artifact> artifacts = versionRepository.listArtifactsByVersion(versionId);
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        Artifact target = pickPreviewableArtifact(artifacts);
        if (target == null) {
            return null;
        }
        String mediaType = target.mediaType() != null ? target.mediaType() : inferContentType(target.path());
        if (mediaType != null && mediaType.startsWith("image/")) {
            return new FetchedSource("", mediaType, null);
        }
        String objectKey = dvcObjectKey(target.dvcHash(), target.path());
        if (!storagePort.objectExists(DVC_CACHE_BUCKET, objectKey)) {
            LOG.warn("preview source unavailable versionId={} objectKey={}", versionId, objectKey);
            recordPreviewFailure("source_unavailable");
            throw new UnsupportedOperationException(
                    "PREVIEW_SOURCE_UNAVAILABLE: artifact object not found in dvc-cache for version " + versionId);
        }
        try (InputStream in = storagePort.readObject(DVC_CACHE_BUCKET, objectKey)) {
            byte[] bytes = readLimited(in, previewProperties.maxBytes());
            if (isParquet(mediaType)) {
                return new FetchedSource(Base64.getEncoder().encodeToString(bytes), mediaType, "base64");
            }
            return new FetchedSource(new String(bytes, StandardCharsets.UTF_8), mediaType, null);
        } catch (IOException ex) {
            recordPreviewFailure("source_read_failed");
            throw new UnsupportedOperationException("PREVIEW_SOURCE_UNAVAILABLE: " + ex.getMessage(), ex);
        }
    }

    private Artifact pickPreviewableArtifact(List<Artifact> artifacts) {
        for (Artifact a : artifacts) {
            String mt = a.mediaType() != null ? a.mediaType() : inferContentType(a.path());
            if (isPreviewable(mt)) {
                return a;
            }
        }
        return null;
    }

    private static boolean isPreviewable(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        return mediaType.startsWith("text/") || mediaType.startsWith("image/")
                || "application/json".equals(mediaType) || "text/csv".equals(mediaType)
                || "application/x-ndjson".equals(mediaType) || "application/jsonl".equals(mediaType)
                || isParquet(mediaType);
    }

    private static boolean isParquet(String mediaType) {
        return "application/x-parquet".equals(mediaType) || "parquet".equals(mediaType)
                || "application/vnd.apache.parquet".equals(mediaType);
    }

    private static String inferContentType(String path) {
        if (path == null) {
            return null;
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".jsonl") || lower.endsWith(".ndjson")) return "application/x-ndjson";
        if (lower.endsWith(".parquet")) return "application/x-parquet";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return null;
    }

    private static String dvcObjectKey(String dvcHash, String path) {
        if (dvcHash != null && dvcHash.length() >= 2) {
            return dvcHash.substring(0, 2) + "/" + dvcHash.substring(2);
        }
        return path;
    }

    private static byte[] readLimited(InputStream in, long max) throws IOException {
        int cap = (int) Math.min(max, Integer.MAX_VALUE);
        byte[] buffer = new byte[cap];
        int read = 0;
        int n;
        while (read < cap && (n = in.read(buffer, read, cap - read)) != -1) {
            read += n;
        }
        if (read == cap) {
            // 丢弃剩余流并标记截断
            while (in.read() != -1) {
                // drain
            }
        }
        if (read == cap) {
            return buffer;
        }
        byte[] exact = new byte[read];
        System.arraycopy(buffer, 0, exact, 0, read);
        return exact;
    }

    private record FetchedSource(String content, String contentType, String contentEncoding) {
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

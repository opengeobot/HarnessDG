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
import java.sql.Timestamp;
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
        FetchedSource src = null;
        if ((content == null || content.isBlank()) && versionId != null) {
            src = fetchFromStorage(versionId);
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
            // 图片不内联二进制；前端经服务端代理端点 renderPreviewArtifact 渲染（TASK-P0BR-068）
            Map<String, Object> imageNote = new LinkedHashMap<>();
            imageNote.put("mediaType", contentType);
            imageNote.put("truncated", false);
            if (src != null && src.artifactId() != null) {
                imageNote.put("artifactId", src.artifactId());
                imageNote.put("artifactPath", src.artifactPath());
            }
            imageNote.put("note", "image preview reference; render via GET /assets/{assetId}/previews/artifact");
            previewJson = objectMapper.writeValueAsString(imageNote);
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

        // Split 维度统计（TASK-P0BR-078）：仅在 Worker 自行从对象存储取数、且数据源为 csv/jsonl 时按
        // 版本工件全量归组统计；payload 直传 content 或 parquet 等超能力格式一律不产出统计（保持 null
        // = 统计未知），禁止伪造零值（AC-DST-PRE-002）。
        String splitStatsJson = null;
        if (src != null && isTabular(contentType)) {
            splitStatsJson = computeSplitStats(versionId);
        }

        if (versionId != null) {
            jdbcTemplate.update("DELETE FROM asset_preview WHERE asset_id = ? AND version_id = ?", assetId, versionId);
        } else {
            jdbcTemplate.update("DELETE FROM asset_preview WHERE asset_id = ? AND version_id IS NULL", assetId);
        }

        // generated_at 必须转 Timestamp：JdbcTemplate 对 java.time.Instant 变参无法推断 SQL 类型，
        // 会在客户端绑定阶段抛 07006（表现为 BadSqlGrammarException，语句不会到达服务端）。
        // split_stats 经 CAST(? AS jsonb) 绑定：JdbcTemplate 传 String 无法推断 JSONB 目标类型。
        jdbcTemplate.update("""
                INSERT INTO asset_preview (preview_id, asset_id, version_id, content_type, content, generated_at, split_stats)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, previewId, assetId, versionId, "application/json", previewJson,
                Timestamp.from(Instant.now()), splitStatsJson);

        LOG.info("preview generated previewId={} assetId={} splitStats={}",
                previewId, assetId, splitStatsJson != null);
    }

    /** Split 归组兜底名：无白名单顶层目录时统一归入 default（单默认视图，选择器不渲染）。 */
    static final String DEFAULT_SPLIT = "default";

    /** 参与四元组统计的表格族；parquet/图片/纯文本不伪造行数（TASK-P0BR-078 禁止新增解析依赖）。 */
    private static boolean isTabular(String mediaType) {
        return "text/csv".equals(mediaType) || "application/x-ndjson".equals(mediaType)
                || "application/jsonl".equals(mediaType);
    }

    /**
     * 按 artifact {@code path} 顶层目录识别 Split。
     *
     * <p>规则（TASK-P0BR-078 CONFIG，服务端白名单见 {@link PreviewProperties#splitDirs()}）：
     * 顶层目录命中白名单 → 该目录名；顶层为 {@code data} 根 → 取次级目录再匹配白名单（否则 default）；
     * 其余 → default。大小写不敏感。
     */
    String resolveSplit(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_SPLIT;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] segments = normalized.split("/");
        if (segments.length < 2) {
            return DEFAULT_SPLIT;
        }
        List<String> whitelist = previewProperties.splitDirs();
        String first = segments[0].toLowerCase();
        if (whitelist.contains(first)) {
            return first;
        }
        if ("data".equals(first)) {
            String second = segments[1].toLowerCase();
            if (whitelist.contains(second)) {
                return second;
            }
        }
        return DEFAULT_SPLIT;
    }

    /**
     * 生成版本级 Split 统计投影 JSON。
     *
     * <p>确定性口径：工件按 path 字典序遍历；每 Split 文件数上限 {@code maxStatsFiles}、
     * 累计字节上限 {@code maxStatsBytes}、单文件采样行数上限 {@code maxStatsRows}，
     * 任一触顶则该 Split 与 totals 标记 {@code truncated=true}。版本内无 csv/jsonl 工件时返回
     * {@code null}（统计未知），不产出空 splits 伪值。对象缺失按既有语义抛
     * {@code PREVIEW_SOURCE_UNAVAILABLE}（不产出部分统计冒充完整）。
     */
    String computeSplitStats(String versionId) {
        List<Artifact> artifacts = versionRepository.listArtifactsByVersion(versionId);
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        List<Artifact> sorted = new ArrayList<>(artifacts);
        sorted.sort(java.util.Comparator.comparing(a -> a.path() == null ? "" : a.path()));

        Map<String, SplitAccumulator> bySplit = new java.util.TreeMap<>();
        for (Artifact artifact : sorted) {
            String mediaType = artifact.mediaType() != null
                    ? artifact.mediaType() : inferContentType(artifact.path());
            if (!isTabular(mediaType)) {
                continue;
            }
            bySplit.computeIfAbsent(resolveSplit(artifact.path()), k -> new SplitAccumulator())
                    .accept(artifact, mediaType);
        }
        if (bySplit.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> splits = new ArrayList<>();
        long totalRows = 0;
        long totalFiles = 0;
        long totalBytes = 0;
        boolean totalTruncated = false;
        for (Map.Entry<String, SplitAccumulator> entry : bySplit.entrySet()) {
            SplitAccumulator acc = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("rowCount", acc.rowCount);
            item.put("fileCount", acc.fileCount);
            item.put("sizeBytes", acc.sizeBytes);
            item.put("columns", acc.columns);
            item.put("truncated", acc.truncated);
            item.put("sampleRows", List.copyOf(acc.sampleRows));
            splits.add(item);
            totalRows += acc.rowCount;
            totalFiles += acc.fileCount;
            totalBytes += acc.sizeBytes;
            totalTruncated |= acc.truncated;
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("rowCount", totalRows);
        totals.put("fileCount", totalFiles);
        totals.put("sizeBytes", totalBytes);
        totals.put("truncated", totalTruncated);

        Map<String, Object> generatedFrom = new LinkedHashMap<>();
        generatedFrom.put("versionId", versionId);
        generatedFrom.put("sourceCommit", versionRepository.findByVersionId(versionId)
                .map(v -> v.sourceCommit()).orElse(null));
        generatedFrom.put("generatedAt", Instant.now().toString());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("splits", splits);
        stats.put("totals", totals);
        stats.put("generatedFrom", generatedFrom);
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (Exception ex) {
            LOG.warn("split stats serialization failed versionId={}", versionId, ex);
            return null;
        }
    }

    /** 单 Split 统计累加器（上限触顶即标记 truncated，超限文件不再读取）。 */
    private final class SplitAccumulator {
        private long rowCount;
        private long fileCount;
        private long sizeBytes;
        private List<String> columns;
        private boolean truncated;
        /** 该 Split 的脱敏样例切片（TASK-P0BR-078 批准口径：切换 Split 展示对应样例）。 */
        private final List<Object> sampleRows = new ArrayList<>();

        void accept(Artifact artifact, String mediaType) {
            if (fileCount >= previewProperties.maxStatsFiles()) {
                truncated = true;
                return;
            }
            long budget = previewProperties.maxStatsBytes() - sizeBytes;
            if (budget <= 0) {
                truncated = true;
                return;
            }
            String objectKey = dvcObjectKey(artifact.dvcHash(), artifact.path());
            if (!storagePort.objectExists(DVC_CACHE_BUCKET, objectKey)) {
                LOG.warn("split stats source unavailable versionId objectKey={}", objectKey);
                recordPreviewFailure("source_unavailable");
                throw new UnsupportedOperationException(
                        "PREVIEW_SOURCE_UNAVAILABLE: artifact object not found in dvc-cache for stats "
                                + artifact.artifactId());
            }
            long cap = Math.min(budget, Integer.MAX_VALUE - 1L);
            try (InputStream in = storagePort.readObject(DVC_CACHE_BUCKET, objectKey)) {
                byte[] bytes = readLimited(in, cap);
                sizeBytes += bytes.length;
                fileCount++;
                if (bytes.length >= cap) {
                    truncated = true;
                }
                accumulateRows(new String(bytes, StandardCharsets.UTF_8), mediaType);
            } catch (IOException ex) {
                recordPreviewFailure("source_read_failed");
                throw new UnsupportedOperationException("PREVIEW_SOURCE_UNAVAILABLE: " + ex.getMessage(), ex);
            }
        }

        /** 行数/列数统计：解析异常按不可靠处理（不抛、不计入 columns），避免脏数据阻断预览产出。 */
        private void accumulateRows(String text, String mediaType) {
            try {
                int sampleLimit = previewProperties.maxRows();
                if ("text/csv".equals(mediaType)) {
                    List<String[]> records = parseCsvRecords(text);
                    if (records.isEmpty()) {
                        return;
                    }
                    String[] headers = records.remove(0);
                    if (columns == null) {
                        columns = headerNames(headers);
                    }
                    // 脱敏样例切片：至多 maxRows 行，字段按表头对齐并 sanitize（与主样例同一脱敏路径）
                    String[] displayHeaders = columns == null ? headers : columns.toArray(new String[0]);
                    for (String[] values : records) {
                        if (sampleRows.size() >= sampleLimit) {
                            break;
                        }
                        Map<String, String> row = new LinkedHashMap<>();
                        for (int i = 0; i < displayHeaders.length; i++) {
                            String value = i < values.length ? sanitize(values[i]) : "";
                            row.put(displayHeaders[i].trim(), value);
                        }
                        sampleRows.add(row);
                    }
                    int rows = records.size();
                    int limit = previewProperties.maxStatsRows();
                    if (rows > limit) {
                        rows = limit;
                        truncated = true;
                    }
                    rowCount += rows;
                    return;
                }
                // jsonl / ndjson：首行键集合为列名，逐非空行计数
                int rows = 0;
                int limit = previewProperties.maxStatsRows();
                try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        if (rows == 0 && columns == null) {
                            columns = jsonlKeys(line);
                        }
                        if (sampleRows.size() < sampleLimit) {
                            sampleRows.add(sanitizeJsonlLine(line));
                        }
                        if (rows >= limit) {
                            truncated = true;
                            break;
                        }
                        rows++;
                    }
                }
                rowCount += rows;
            } catch (Exception ex) {
                LOG.warn("split stats row counting degraded for one artifact: {}", ex.getMessage());
            }
        }

        /** jsonl 单行脱敏样例：可解析为对象则原样结构化，否则退化为 sanitize 后的 _raw。 */
        private Object sanitizeJsonlLine(String line) {
            try {
                JsonNode node = objectMapper.readTree(line);
                if (node.isObject()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    node.fields().forEachRemaining(e -> {
                        JsonNode v = e.getValue();
                        row.put(e.getKey(), v.isTextual() ? sanitize(v.asText()) : v.asText());
                    });
                    return row;
                }
                return Map.of("_raw", sanitize(line));
            } catch (Exception ex) {
                return Map.of("_raw", sanitize(line));
            }
        }

        private List<String> headerNames(String[] headers) {
            int maxCols = previewProperties.maxCols();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < headers.length && i < maxCols; i++) {
                names.add(headers[i].trim());
            }
            return names;
        }

        private List<String> jsonlKeys(String line) {
            try {
                JsonNode node = objectMapper.readTree(line);
                if (!node.isObject()) {
                    return List.of();
                }
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                return keys.size() > previewProperties.maxCols()
                        ? keys.subList(0, previewProperties.maxCols()) : keys;
            } catch (Exception ex) {
                return List.of();
            }
        }
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
            return new FetchedSource("", mediaType, null, target.artifactId(), target.path());
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
                return new FetchedSource(Base64.getEncoder().encodeToString(bytes), mediaType, "base64",
                        target.artifactId(), target.path());
            }
            return new FetchedSource(new String(bytes, StandardCharsets.UTF_8), mediaType, null,
                    target.artifactId(), target.path());
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

    /**
     * DVC 对象键算法（dvc-cache 内按 hash 前 2 位分桶）。
     *
     * <p>TASK-P0BR-068：预览代理用例（PreviewApplicationService）与本 Worker 共用唯一实现，
     * 禁止复制漂移；hash 缺失时回退 artifact path（历史数据兼容）。
     */
    public static String dvcObjectKey(String dvcHash, String path) {
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

    private record FetchedSource(String content, String contentType, String contentEncoding,
                                 String artifactId, String artifactPath) {
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

    /**
     * 解析 CSV 内容为 JSON 表格（RFC 4180 兼容）。
     *
     * <p>支持引号字段、转义引号（"" → "）、引号内换行符等标准 CSV 特性；
     * 行列上限取 {@link PreviewProperties} 可配置值，超限部分截断并标记。
     */
    private String parseCsv(String csvContent) throws Exception {
        List<String[]> allRecords = parseCsvRecords(csvContent);
        if (allRecords.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of("rows", List.of()));
        }

        String[] rawHeaders = allRecords.remove(0);
        int maxCols = previewProperties.maxCols();
        boolean columnTruncated = rawHeaders.length > maxCols;
        String[] headers = columnTruncated
                ? java.util.Arrays.copyOf(rawHeaders, maxCols)
                : rawHeaders;

        int maxRows = previewProperties.maxRows();
        List<Map<String, String>> rows = new ArrayList<>();
        int rowCount = 0;
        for (String[] values : allRecords) {
            if (rowCount >= maxRows) break;
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
                "truncated", rowCount >= maxRows,
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

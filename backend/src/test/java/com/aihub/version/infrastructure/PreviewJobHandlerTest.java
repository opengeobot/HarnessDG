package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviewJobHandler")
class PreviewJobHandlerTest {

    @Mock
    private IdGenerator idGenerator;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformMetrics platformMetrics;
    @Mock
    private ObjectProvider<PlatformMetrics> platformMetricsProvider;
    @Mock
    private StoragePort storagePort;
    @Mock
    private VersionRepository versionRepository;

    private PreviewJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PreviewProperties previewProperties =
            new PreviewProperties(100, 50, 1024L * 1024, 5L * 1024 * 1024, 1024L * 1024,
                    null, 0, 0, 0L);

    @BeforeEach
    void setUp() {
        lenient().when(platformMetricsProvider.getIfAvailable()).thenReturn(platformMetrics);
        handler = new PreviewJobHandler(idGenerator, jdbcTemplate, objectMapper, previewProperties,
                platformMetricsProvider, storagePort, versionRepository);
    }

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("PREVIEW_GENERATE");
    }

    @Test
    @DisplayName("CSV 解析应限制行数和列数")
    void csvShouldRespectLimits() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        StringBuilder csv = new StringBuilder("a,b,c\n");
        for (int i = 0; i < 150; i++) {
            csv.append(i).append(",").append(i * 2).append(",").append(i * 3).append("\n");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");
        payload.put("content", csv.toString());
        payload.put("contentType", "text/csv");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("超大内容应抛出 PREVIEW_LIMIT_EXCEEDED")
    void oversizedContentShouldBeRejected() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2 * 1024 * 1024; i++) {
            huge.append('x');
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("content", huge.toString());

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("PREVIEW_LIMIT_EXCEEDED");

        verify(platformMetrics).recordPreviewFailure("limit_exceeded");
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("JSONL 解析应处理每行 JSON")
    void jsonlShouldParseEachLine() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        String jsonl = """
                {"name": "Alice", "age": 30}
                {"name": "Bob", "age": 25}
                invalid json line
                {"name": "Carol", "age": 35}
                """;

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");
        payload.put("content", jsonl);
        payload.put("contentType", "application/x-ndjson");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("content 为空时应从 dvc-cache 自动取数解析 CSV")
    void shouldAutoFetchFromStorageWhenContentAbsent() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Artifact csv = new Artifact("art_1", "ver_1", "data/train.csv",
                "data/train.csv.dvc", "abcdef1234567890abcdef1234567890",
                "sha256...", 200L, "text/csv");
        lenient().when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(csv));
        lenient().when(storagePort.objectExists(eq("dvc-cache"), eq("ab/cdef1234567890abcdef1234567890")))
                .thenReturn(true);
        // 每次调用返回新流：预览取数与 Split 统计分别读取同一对象
        lenient().when(storagePort.readObject(eq("dvc-cache"), eq("ab/cdef1234567890abcdef1234567890")))
                .thenAnswer(inv -> new ByteArrayInputStream("col1,col2\nv1,v2\n".getBytes()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        // 预览取数 + Split 统计各读一次同对象（TASK-P0BR-078：统计按版本工件全量归组）
        verify(storagePort, times(2)).readObject("dvc-cache", "ab/cdef1234567890abcdef1234567890");
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());

        // split_stats 位于第 7 个 vararg（sql + 7）：data/train.csv 无白名单次级目录 → default 归组
        ArgumentCaptor<Object> statsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any(), statsCaptor.capture());
        String statsJson = (String) statsCaptor.getValue();
        assertThat(statsJson).isNotNull()
                .contains("\"name\":\"default\"")
                .contains("\"rowCount\":1")
                .contains("\"fileCount\":1")
                .contains("ver_1");
    }

    @Test
    @DisplayName("图片 artifact 应返回安全引用，不读取二进制")
    void imageArtifactShouldReturnReferenceWithoutBytes() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_img");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Artifact img = new Artifact("art_img", "ver_1", "samples/preview.png",
                "samples/preview.png.dvc", "ffeedd...".repeat(4),
                "sha256...", 1024L, "image/png");
        lenient().when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(img));

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");

        JobContext context = new JobContext("job_img", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        // 图片不读取对象二进制
        verify(storagePort, never()).readObject(anyString(), anyString());
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any());

        // TASK-P0BR-068：note JSON 必须携带渲染端点所需的 artifactId/artifactPath 与安全引用
        // TASK-P0BR-078：INSERT 共 8 参（sql + 7 varargs）；按位捕获第 5 个 vararg（content 列），
        // 第 7 个 vararg 为 split_stats——图片非表格族，必须为 null（未知≠零值）。
        ArgumentCaptor<Object> jsonCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> statsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), jsonCaptor.capture(),
                any(), statsCaptor.capture());
        assertThat(statsCaptor.getValue()).isNull();
        String previewJson = (String) jsonCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> note = objectMapper.readValue(previewJson, Map.class);
        assertThat(note).containsEntry("mediaType", "image/png")
                .containsEntry("truncated", false)
                .containsEntry("artifactId", "art_img")
                .containsEntry("artifactPath", "samples/preview.png");
        assertThat((String) note.get("note")).contains("previews/artifact");
        assertThat(previewJson).doesNotContain("data:image").doesNotContain("dvcHash");
    }

    @Test
    @DisplayName("对象存储缺少 artifact 时抛 PREVIEW_SOURCE_UNAVAILABLE")
    void missingArtifactObjectShouldBeRejected() throws Exception {
        Artifact csv = new Artifact("art_1", "ver_1", "data/train.csv",
                "data/train.csv.dvc", "abcdef1234567890abcdef1234567890",
                "sha256...", 200L, "text/csv");
        lenient().when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(csv));
        lenient().when(storagePort.objectExists(eq("dvc-cache"), eq("ab/cdef1234567890abcdef1234567890")))
                .thenReturn(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");

        JobContext context = new JobContext("job_miss", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        assertThatThrownBy(() -> handler.handle(context))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("PREVIEW_SOURCE_UNAVAILABLE");

        verify(platformMetrics).recordPreviewFailure("source_unavailable");
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    // ---------------- TASK-P0BR-078: Split 识别与统计生成 ----------------

    @Test
    @DisplayName("resolveSplit：白名单顶层/次级、大小写、./ 与 / 前缀、反斜杠、default 兜底")
    void resolveSplitCoversWhitelistAndFallbackRules() {
        assertThat(handler.resolveSplit("train/a.csv")).isEqualTo("train");
        assertThat(handler.resolveSplit("TRAIN/a.csv")).isEqualTo("train");
        assertThat(handler.resolveSplit("validation/b.jsonl")).isEqualTo("validation");
        assertThat(handler.resolveSplit("val/c.csv")).isEqualTo("val");
        assertThat(handler.resolveSplit("dev/d.csv")).isEqualTo("dev");
        assertThat(handler.resolveSplit("data/validation/e.csv")).isEqualTo("validation");
        assertThat(handler.resolveSplit("data/test/f.jsonl")).isEqualTo("test");
        // data 根但次级非白名单（含 data/train.csv 平铺形态）→ default
        assertThat(handler.resolveSplit("data/train.csv")).isEqualTo("default");
        assertThat(handler.resolveSplit("data/raw/x.csv")).isEqualTo("default");
        // 非白名单顶层 → default
        assertThat(handler.resolveSplit("samples/preview.png")).isEqualTo("default");
        // 无目录层级 → default
        assertThat(handler.resolveSplit("README.csv")).isEqualTo("default");
        // 前缀归一
        assertThat(handler.resolveSplit("./test/x.csv")).isEqualTo("test");
        assertThat(handler.resolveSplit("/test/x.csv")).isEqualTo("test");
        // 反斜杠（Windows 风格 path）归一
        assertThat(handler.resolveSplit("train\\sub\\x.csv")).isEqualTo("train");
        assertThat(handler.resolveSplit(null)).isEqualTo("default");
        assertThat(handler.resolveSplit("  ")).isEqualTo("default");
    }

    @Test
    @DisplayName("computeSplitStats：多 Split 归组、totals 聚合、generatedFrom 绑定 sourceCommit")
    void computeSplitStatsGroupsBySplitAndAggregatesTotals() throws Exception {
        Version version = publishedVersion("ver_stats", "c".repeat(40));
        lenient().when(versionRepository.findByVersionId("ver_stats")).thenReturn(Optional.of(version));
        lenient().when(versionRepository.listArtifactsByVersion("ver_stats")).thenReturn(List.of(
                csv("art_1", "train/part-0.csv", "1".repeat(32), "a,b\n1,2\n3,4\n"),
                csv("art_2", "train/part-1.csv", "2".repeat(32), "a,b\n5,6\n"),
                jsonl("art_3", "validation/eval.jsonl", "3".repeat(32))));
        stubObject("1".repeat(32), "a,b\n1,2\n3,4\n");
        stubObject("2".repeat(32), "a,b\n5,6\n");
        stubObject("3".repeat(32), "{\"name\":\"A\"}\n{\"name\":\"B\"}\n");

        String json = handler.computeSplitStats("ver_stats");
        assertThat(json).isNotNull();
        Map<String, Object> root = objectMapper.readValue(json, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splits = (List<Map<String, Object>>) root.get("splits");
        // TreeMap 字典序：train < validation
        assertThat(splits).hasSize(2);
        assertThat(splits.get(0)).containsEntry("name", "train")
                .containsEntry("rowCount", 3).containsEntry("fileCount", 2)
                .containsEntry("truncated", false);
        assertThat((List<String>) splits.get(0).get("columns")).containsExactly("a", "b");
        // 脱敏样例切片（批准口径：切换 Split 展示对应样例）——train 聚合两文件的行数上限内样例
        assertThat((List<Map<String, Object>>) splits.get(0).get("sampleRows")).hasSize(3)
                .first().extracting(row -> row.get("a")).isEqualTo("1");
        assertThat(splits.get(1)).containsEntry("name", "validation")
                .containsEntry("rowCount", 2).containsEntry("fileCount", 1)
                .containsEntry("truncated", false);
        assertThat((List<String>) splits.get(1).get("columns")).containsExactly("name");
        assertThat((List<Map<String, Object>>) splits.get(1).get("sampleRows")).hasSize(2)
                .first().extracting(row -> row.get("name")).isEqualTo("A");

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) root.get("totals");
        assertThat(totals).containsEntry("rowCount", 5).containsEntry("fileCount", 3)
                .containsEntry("truncated", false);
        long sumSize = ((Number) splits.get(0).get("sizeBytes")).longValue()
                + ((Number) splits.get(1).get("sizeBytes")).longValue();
        assertThat(((Number) totals.get("sizeBytes")).longValue()).isEqualTo(sumSize);

        @SuppressWarnings("unchecked")
        Map<String, Object> generatedFrom = (Map<String, Object>) root.get("generatedFrom");
        assertThat(generatedFrom).containsEntry("versionId", "ver_stats")
                .containsEntry("sourceCommit", "c".repeat(40));
        assertThat(generatedFrom.get("generatedAt")).isNotNull();
    }

    @Test
    @DisplayName("computeSplitStats：版本内无 csv/jsonl 工件 → null（统计未知，不产空 splits 伪值）")
    void computeSplitStatsReturnsNullWhenNoTabularArtifacts() {
        lenient().when(versionRepository.listArtifactsByVersion("ver_img")).thenReturn(List.of(
                new Artifact("art_i", "ver_img", "samples/x.png", "samples/x.png.dvc",
                        "f".repeat(32), "sha256", 10L, "image/png")));

        assertThat(handler.computeSplitStats("ver_img")).isNull();
        assertThat(handler.computeSplitStats("ver_empty")).isNull();
    }

    @Test
    @DisplayName("computeSplitStats：maxStatsFiles 触顶 → 该 Split 与 totals 标记 truncated")
    void computeSplitStatsMarksTruncatedAtFileLimit() throws Exception {
        PreviewJobHandler capped = new PreviewJobHandler(idGenerator, jdbcTemplate, objectMapper,
                new PreviewProperties(100, 50, 1024L * 1024, 5L * 1024 * 1024, 1024L * 1024,
                        null, 0, 1, 0L),
                platformMetricsProvider, storagePort, versionRepository);
        lenient().when(versionRepository.findByVersionId("ver_cap")).thenReturn(Optional.empty());
        lenient().when(versionRepository.listArtifactsByVersion("ver_cap")).thenReturn(List.of(
                csv("art_1", "train/a.csv", "1".repeat(32), "x\n1\n"),
                csv("art_2", "train/b.csv", "2".repeat(32), "x\n1\n2\n")));
        stubObject("1".repeat(32), "x\n1\n");
        stubObject("2".repeat(32), "x\n1\n2\n");

        String json = capped.computeSplitStats("ver_cap");
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splits = (List<Map<String, Object>>) root.get("splits");
        assertThat(splits).hasSize(1);
        assertThat(splits.get(0)).containsEntry("fileCount", 1).containsEntry("rowCount", 1)
                .containsEntry("truncated", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) root.get("totals");
        assertThat(totals).containsEntry("truncated", true);
    }

    @Test
    @DisplayName("computeSplitStats：maxStatsRows 触顶 → rowCount 截顶且 truncated=true")
    void computeSplitStatsCapsRowsAtSamplingLimit() throws Exception {
        PreviewJobHandler capped = new PreviewJobHandler(idGenerator, jdbcTemplate, objectMapper,
                new PreviewProperties(100, 50, 1024L * 1024, 5L * 1024 * 1024, 1024L * 1024,
                        null, 2, 0, 0L),
                platformMetricsProvider, storagePort, versionRepository);
        lenient().when(versionRepository.findByVersionId("ver_rows")).thenReturn(Optional.empty());
        lenient().when(versionRepository.listArtifactsByVersion("ver_rows")).thenReturn(List.of(
                csv("art_1", "train/big.csv", "1".repeat(32), "x\n1\n2\n3\n4\n")));
        stubObject("1".repeat(32), "x\n1\n2\n3\n4\n");

        String json = capped.computeSplitStats("ver_rows");
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splits = (List<Map<String, Object>>) root.get("splits");
        assertThat(splits.get(0)).containsEntry("rowCount", 2).containsEntry("truncated", true);
    }

    @Test
    @DisplayName("computeSplitStats：统计阶段对象缺失 → PREVIEW_SOURCE_UNAVAILABLE（不产部分统计）")
    void computeSplitStatsFailsClosedOnMissingObject() {
        lenient().when(versionRepository.listArtifactsByVersion("ver_miss")).thenReturn(List.of(
                csv("art_1", "train/a.csv", "9".repeat(32), "x\n1\n")));
        lenient().when(storagePort.objectExists(eq("dvc-cache"), eq("99/" + "9".repeat(30))))
                .thenReturn(false);

        assertThatThrownBy(() -> handler.computeSplitStats("ver_miss"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("PREVIEW_SOURCE_UNAVAILABLE");
        verify(platformMetrics).recordPreviewFailure("source_unavailable");
    }

    private static Version publishedVersion(String versionId, String sourceCommit) {
        Version v = Version.createDraft(versionId, "ast_1", "0.1.0", "usr_owner");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256-abc");
        v.bindSourceCommit(sourceCommit);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        v.publish("sha256-abc", "v0.1.0", "usr_admin");
        return v;
    }

    private static Artifact csv(String artifactId, String path, String hash, String body) {
        return new Artifact(artifactId, "ver_x", path, path + ".dvc", hash, "sha256",
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, "text/csv");
    }

    private static Artifact jsonl(String artifactId, String path, String hash) {
        return new Artifact(artifactId, "ver_x", path, path + ".dvc", hash, "sha256",
                32L, "application/x-ndjson");
    }

    /** dvc-cache 对象桩：键按 DVC hash 前 2 位分桶（与 PreviewJobHandler.dvcObjectKey 一致）。 */
    private void stubObject(String hash, String body) {
        String key = hash.substring(0, 2) + "/" + hash.substring(2);
        lenient().when(storagePort.objectExists("dvc-cache", key)).thenReturn(true);
        lenient().when(storagePort.readObject("dvc-cache", key))
                .thenReturn(new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}

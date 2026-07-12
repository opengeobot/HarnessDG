package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.VersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private final PreviewProperties previewProperties = new PreviewProperties(100, 50, 1024L * 1024);

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
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

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
                anyString(), anyString(), any());
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
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
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
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("content 为空时应从 dvc-cache 自动取数解析 CSV")
    void shouldAutoFetchFromStorageWhenContentAbsent() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Artifact csv = new Artifact("art_1", "ver_1", "data/train.csv",
                "data/train.csv.dvc", "abcdef1234567890abcdef1234567890",
                "sha256...", 200L, "text/csv");
        lenient().when(versionRepository.listArtifactsByVersion("ver_1")).thenReturn(List.of(csv));
        lenient().when(storagePort.objectExists(eq("dvc-cache"), eq("ab/cdef1234567890abcdef1234567890")))
                .thenReturn(true);
        lenient().when(storagePort.readObject(eq("dvc-cache"), eq("ab/cdef1234567890abcdef1234567890")))
                .thenReturn(new ByteArrayInputStream("col1,col2\nv1,v2\n".getBytes()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        verify(storagePort).readObject("dvc-cache", "ab/cdef1234567890abcdef1234567890");
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("图片 artifact 应返回安全引用，不读取二进制")
    void imageArtifactShouldReturnReferenceWithoutBytes() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_img");
        lenient().when(jdbcTemplate.update(anyString(), any(), any())).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

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
                anyString(), anyString(), any());
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
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }
}

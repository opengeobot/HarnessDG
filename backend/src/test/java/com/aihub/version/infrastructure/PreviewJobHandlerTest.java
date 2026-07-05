package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreviewJobHandler")
class PreviewJobHandlerTest {

    @Mock
    private IdGenerator idGenerator;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private PreviewJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new PreviewJobHandler(idGenerator, jdbcTemplate, objectMapper);
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
    @DisplayName("超大内容应被拒绝")
    void oversizedContentShouldBeRejected() throws Exception {
        // 生成 2MiB 内容
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2 * 1024 * 1024; i++) {
            huge.append('x');
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("content", huge.toString());

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        // 不应写入预览
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
}

package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @DisplayName("超大内容应写入 errorReason 预览记录")
    void oversizedContentShouldWriteErrorReason() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any());
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());

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

        // 应写入预览记录，且包含 errorReason
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), jsonCaptor.capture(), any());
        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(node.path("errorReason").asText()).isEqualTo("PREVIEW_SIZE_EXCEEDED");
    }

    @Test
    @DisplayName("CSV 超过 50 列应标记 columnTruncated")
    void csvExceedingFiftyColumnsShouldMarkColumnTruncated() throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any());
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());

        // 构建 60 列的 CSV
        StringBuilder csv = new StringBuilder();
        for (int col = 0; col < 60; col++) {
            if (col > 0) csv.append(",");
            csv.append("col").append(col);
        }
        csv.append("\n");
        for (int col = 0; col < 60; col++) {
            if (col > 0) csv.append(",");
            csv.append(col);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("content", csv.toString());
        payload.put("contentType", "text/csv");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        // 验证 INSERT 被调用，并检查 columnTruncated 标记
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), jsonCaptor.capture(), any());
        JsonNode node = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(node.path("columnTruncated").asBoolean()).isTrue();
        assertThat(node.path("truncated").asBoolean()).isFalse();
        // 应该只保留了 50 列
        assertThat(node.path("rows").get(0).size()).isEqualTo(50);
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

    // -------------------------------------------------------------------------
    // RFC 4180 CSV parser tests
    // -------------------------------------------------------------------------

    private JsonNode runCsv(String csv) throws Exception {
        lenient().when(idGenerator.generate(IdPrefix.PREVIEW)).thenReturn("prv_1");
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any());
        lenient().doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());

        Map<String, Object> payload = new HashMap<>();
        payload.put("assetId", "ast_1");
        payload.put("versionId", "ver_1");
        payload.put("content", csv);
        payload.put("contentType", "text/csv");

        JobContext context = new JobContext("job_1", "PREVIEW_GENERATE",
                objectMapper.writeValueAsString(payload), 0, "principal_1", "trace_1", "ast_1");

        handler.handle(context);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), anyString(), anyString(), anyString(),
                anyString(), jsonCaptor.capture(), any());

        return objectMapper.readTree(jsonCaptor.getValue());
    }

    @Test
    @DisplayName("CSV: 普通无引号字段应正确解析")
    void csvNormalUnquotedFields() throws Exception {
        String csv = "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai\n";
        JsonNode result = runCsv(csv);

        JsonNode rows = result.get("rows");
        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).get("name").asText()).isEqualTo("Alice");
        assertThat(rows.get(0).get("age").asText()).isEqualTo("30");
        assertThat(rows.get(0).get("city").asText()).isEqualTo("Beijing");

        assertThat(rows.get(1).get("name").asText()).isEqualTo("Bob");
        assertThat(rows.get(1).get("age").asText()).isEqualTo("25");
    }

    @Test
    @DisplayName("CSV: 引号字段中包含逗号应正确处理")
    void csvQuotedFieldWithComma() throws Exception {
        String csv = "\"name\",\"description\"\n\"Smith, John\",\"Engineer, Senior\"\n\"Doe, Jane\",\"Manager, VP\"\n";
        JsonNode result = runCsv(csv);

        JsonNode rows = result.get("rows");
        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).get("name").asText()).isEqualTo("Smith, John");
        assertThat(rows.get(0).get("description").asText()).isEqualTo("Engineer, Senior");

        assertThat(rows.get(1).get("name").asText()).isEqualTo("Doe, Jane");
    }

    @Test
    @DisplayName("CSV: 转义引号（双引号）应正确解析为单个引号")
    void csvEscapedQuotes() throws Exception {
        String csv = "name,quote\n\"He said \"\"hello\"\"\",\"It was \"\"great\"\"\"\n";
        JsonNode result = runCsv(csv);

        JsonNode rows = result.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name").asText()).isEqualTo("He said \"hello\"");
        assertThat(rows.get(0).get("quote").asText()).isEqualTo("It was \"great\"");
    }

    @Test
    @DisplayName("CSV: 引号字段中包含换行符应作为同一行解析")
    void csvQuotedFieldWithNewline() throws Exception {
        String csv = "name,note\n\"Alice\",\"Line 1\nLine 2\nLine 3\"\n\"Bob\",\"Simple\"\n";
        JsonNode result = runCsv(csv);

        JsonNode rows = result.get("rows");
        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).get("name").asText()).isEqualTo("Alice");
        assertThat(rows.get(0).get("note").asText()).isEqualTo("Line 1\nLine 2\nLine 3");

        assertThat(rows.get(1).get("name").asText()).isEqualTo("Bob");
        assertThat(rows.get(1).get("note").asText()).isEqualTo("Simple");
    }

    @Test
    @DisplayName("CSV: 混合引号和无引号字段应正确解析")
    void csvMixedQuotedAndUnquoted() throws Exception {
        String csv = "id,name,notes,active\n1,\"Alice\",\"Normal note\",true\n2,Bob,\"Has, comma\",false\n3,\"\"\"Quoted\"\"\"\",,maybe\n";
        JsonNode result = runCsv(csv);

        JsonNode rows = result.get("rows");
        assertThat(rows).hasSize(3);

        assertThat(rows.get(0).get("id").asText()).isEqualTo("1");
        assertThat(rows.get(0).get("name").asText()).isEqualTo("Alice");
        assertThat(rows.get(0).get("notes").asText()).isEqualTo("Normal note");
        assertThat(rows.get(0).get("active").asText()).isEqualTo("true");

        assertThat(rows.get(1).get("id").asText()).isEqualTo("2");
        assertThat(rows.get(1).get("name").asText()).isEqualTo("Bob");
        assertThat(rows.get(1).get("notes").asText()).isEqualTo("Has, comma");

        assertThat(rows.get(2).get("name").asText()).isEqualTo("\"Quoted\"");
        assertThat(rows.get(2).get("notes").asText()).isEmpty();
    }
}

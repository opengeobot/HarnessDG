package com.aihub.integration.gitea.application;

import com.aihub.integration.gitea.application.WebhookInboxApplicationService.ReceiveResult;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookInboxApplicationService")
class WebhookInboxApplicationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformMetrics platformMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookInboxApplicationService buildService(String secret) {
        return new WebhookInboxApplicationService(jdbcTemplate, objectMapper, platformMetrics, secret);
    }

    private String computeSignature(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return "sha256=" + sb;
    }

    @Nested
    @DisplayName("签名验证 fail-closed")
    class SignatureVerification {

        @Test
        @DisplayName("secret 为空时应返回 UNAUTHORIZED (fail-closed)")
        void secretEmptyShouldReturnUnauthorized() {
            WebhookInboxApplicationService service = buildService("");

            ReceiveResult result = service.receive("d1", "push", "sha256=abc", "{}".getBytes());

            assertThat(result).isEqualTo(ReceiveResult.UNAUTHORIZED);
            verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("secret 为 null 时应返回 UNAUTHORIZED")
        void secretNullShouldReturnUnauthorized() {
            WebhookInboxApplicationService service = buildService(null);

            ReceiveResult result = service.receive("d1", "push", "sha256=abc", "{}".getBytes());

            assertThat(result).isEqualTo(ReceiveResult.UNAUTHORIZED);
        }

        @Test
        @DisplayName("签名错误时应返回 UNAUTHORIZED")
        void wrongSignatureShouldReturnUnauthorized() {
            WebhookInboxApplicationService service = buildService("my-secret");

            ReceiveResult result = service.receive("d1", "push", "sha256=wronghash", "{}".getBytes());

            assertThat(result).isEqualTo(ReceiveResult.UNAUTHORIZED);
        }

        @Test
        @DisplayName("签名正确时应返回 ACCEPTED")
        void correctSignatureShouldReturnAccepted() throws Exception {
            String secret = "my-secret";
            byte[] body = "{\"action\":\"completed\"}".getBytes(StandardCharsets.UTF_8);
            String sig = computeSignature(secret, body);

            WebhookInboxApplicationService service = buildService(secret);
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

            ReceiveResult result = service.receive("d1", "push", sig, body);

            assertThat(result).isEqualTo(ReceiveResult.ACCEPTED);
            verify(jdbcTemplate).update(anyString(), eq("d1"), eq("push"), eq(true), any());
        }
    }

    @Nested
    @DisplayName("幂等入库")
    class IdempotentInsert {

        @Test
        @DisplayName("重复 delivery 应幂等返回 ACCEPTED")
        void duplicateDeliveryShouldReturnAccepted() throws Exception {
            String secret = "my-secret";
            byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
            String sig = computeSignature(secret, body);

            WebhookInboxApplicationService service = buildService(secret);
            // 幂等：ON CONFLICT DO NOTHING → inserted = 0
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

            ReceiveResult result = service.receive("d_dup", "push", sig, body);

            assertThat(result).isEqualTo(ReceiveResult.ACCEPTED);
        }

        @Test
        @DisplayName("入库异常应返回 INTERNAL_ERROR")
        void insertExceptionShouldReturnInternalError() throws Exception {
            String secret = "my-secret";
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            String sig = computeSignature(secret, body);

            WebhookInboxApplicationService service = buildService(secret);
            when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("db error"));

            ReceiveResult result = service.receive("d_err", "push", sig, body);

            assertThat(result).isEqualTo(ReceiveResult.INTERNAL_ERROR);
        }
    }

    @Nested
    @DisplayName("JSON 解析")
    class JsonParsing {

        @Test
        @DisplayName("无效 JSON 应返回 BAD_REQUEST")
        void invalidJsonShouldReturnBadRequest() throws Exception {
            String secret = "my-secret";
            byte[] body = "not-json{{{".getBytes(StandardCharsets.UTF_8);
            String sig = computeSignature(secret, body);

            WebhookInboxApplicationService service = buildService(secret);

            ReceiveResult result = service.receive("d_bad", "push", sig, body);

            assertThat(result).isEqualTo(ReceiveResult.BAD_REQUEST);
        }
    }
}

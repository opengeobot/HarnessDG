/*
 * 功能: WebhookInboxController 安全合规测试——fail-closed 签名验证、幂等接收。
 * 时间: 2026-07-08
 */
package com.aihub.integration.gitea.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebhookInboxControllerTest {

    private MockMvc mockMvc;
    private JdbcTemplate jdbcTemplate;
    private static final String SECRET = "test-webhook-secret";

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        var controller = new WebhookInboxController(jdbcTemplate, new ObjectMapper(), SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejectWhenSecretEmpty() throws Exception {
        // 未配置 secret 时应 fail-closed（401）
        var controller = new WebhookInboxController(jdbcTemplate, new ObjectMapper(), "");
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-1")
                        .header("X-Gitea-Event", "push")
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectWhenSecretNull() throws Exception {
        var controller = new WebhookInboxController(jdbcTemplate, new ObjectMapper(), null);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-1")
                        .header("X-Gitea-Event", "push")
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectWhenSignatureInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-2")
                        .header("X-Gitea-Event", "push")
                        .header("X-Hub-Signature-256", "sha256=invalidsignature")
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectWhenSignatureMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-3")
                        .header("X-Gitea-Event", "push")
                        .content("{\"ref\":\"refs/heads/main\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptWhenSignatureValid() throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";
        String sig = computeHmac(body, SECRET);
        when(jdbcTemplate.update(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(1);

        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-4")
                        .header("X-Gitea-Event", "push")
                        .header("X-Hub-Signature-256", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void acceptDuplicateIdempotently() throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";
        String sig = computeHmac(body, SECRET);
        // 重复 delivery 返回 inserted=0
        when(jdbcTemplate.update(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(0);

        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-5")
                        .header("X-Gitea-Event", "push")
                        .header("X-Hub-Signature-256", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectWhenDeliveryIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Event", "push")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectWhenEventTypeMissing() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/gitea")
                        .header("X-Gitea-Delivery", "dlv-7")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static String computeHmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

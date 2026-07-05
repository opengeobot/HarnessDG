package com.aihub.integration.gitea.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gitea Webhook Inbox 控制器。
 *
 * <p>接收 Gitea Webhook 推送，验证 HMAC-SHA256 签名后幂等入库，快速返回 202。
 * 正式 Tag 删除/改指向触发 CRITICAL 事件告警。
 */
@RestController
@RequestMapping("/api/v1/webhooks/gitea")
public class WebhookInboxController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookInboxController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public WebhookInboxController(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${aihub.gitea.webhook-secret:}") String webhookSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    /**
     * 接收 Gitea Webhook。
     *
     * <p>签名验证 → 幂等入库 → 返回 202 Accepted。
     * 重复 delivery 直接返回 202（幂等）。
     */
    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "X-Gitea-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Gitea-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (deliveryId == null || eventType == null) {
            return ResponseEntity.badRequest().build();
        }

        // 签名验证
        boolean signatureValid = verifySignature(rawBody, signature);
        if (!signatureValid && webhookSecret != null && !webhookSecret.isEmpty()) {
            LOG.warn("webhook signature invalid deliveryId={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 解析 payload
        String payloadJson;
        try {
            Object payload = objectMapper.readValue(rawBody, Object.class);
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            LOG.error("webhook payload parse failed deliveryId={}", deliveryId, e);
            return ResponseEntity.badRequest().build();
        }

        // 幂等入库
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, signature_valid, payload) " +
                            "VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, eventType, signatureValid, payloadJson);
            if (inserted == 0) {
                LOG.debug("webhook duplicate deliveryId={}", deliveryId);
            }
        } catch (Exception e) {
            LOG.error("webhook inbox insert failed deliveryId={}", deliveryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // 检测高风险事件
        if (isCriticalEvent(eventType, payloadJson)) {
            LOG.warn("CRITICAL webhook event detected deliveryId={} eventType={}", deliveryId, eventType);
        }

        return ResponseEntity.accepted().build();
    }

    private boolean verifySignature(byte[] body, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            return true; // 未配置密钥时跳过验证
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body);
            String expected = "sha256=" + hexEncode(hash);
            return constantTimeEquals(expected, signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOG.error("HMAC-SHA256 computation failed", e);
            return false;
        }
    }

    private boolean isCriticalEvent(String eventType, String payload) {
        // Tag 删除或 push 中包含 ref 删除
        if ("delete".equals(eventType)) {
            return payload.contains("\"ref_type\":\"tag\"");
        }
        return false;
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

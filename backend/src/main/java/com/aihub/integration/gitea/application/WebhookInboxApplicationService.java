/*
 * 功能: Webhook Inbox 应用服务——编排签名验证、幂等入库、CRITICAL 事件检测。
 * 时间: 2026-07-08
 */
package com.aihub.integration.gitea.application;

import com.aihub.platform.observability.application.PlatformMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Webhook Inbox 应用服务。
 *
 * <p>编排 Gitea Webhook 接收流程：HMAC-SHA256 签名验证 → JSON 解析 → 幂等入库 → CRITICAL 事件检测。
 * 控制器仅作为适配器委托本服务。
 */
@Service
public class WebhookInboxApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookInboxApplicationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformMetrics platformMetrics;
    private final String webhookSecret;

    public WebhookInboxApplicationService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformMetrics platformMetrics,
            @Value("${aihub.gitea.webhook-secret:}") String webhookSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformMetrics = platformMetrics;
        this.webhookSecret = webhookSecret;
    }

    /**
     * 接收并处理 Webhook 事件。
     *
     * @param deliveryId Gitea Delivery ID
     * @param eventType  事件类型
     * @param signature  HMAC-SHA256 签名（X-Hub-Signature-256）
     * @param rawBody    原始请求体
     * @return 处理结果
     */
    public ReceiveResult receive(String deliveryId, String eventType, String signature, byte[] rawBody) {
        // 签名验证（fail-closed）
        if (!verifySignature(rawBody, signature)) {
            LOG.warn("webhook signature rejected deliveryId={} secretConfigured={}",
                    deliveryId, webhookSecret != null && !webhookSecret.isEmpty());
            return ReceiveResult.UNAUTHORIZED;
        }

        // JSON 解析
        String payloadJson;
        try {
            Object payload = objectMapper.readValue(rawBody, Object.class);
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            LOG.error("webhook payload parse failed deliveryId={}", deliveryId, e);
            return ReceiveResult.BAD_REQUEST;
        }

        // 幂等入库
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, signature_valid, payload) " +
                            "VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, eventType, true, payloadJson);
            if (inserted == 0) {
                LOG.debug("webhook duplicate deliveryId={}", deliveryId);
            }
        } catch (Exception e) {
            LOG.error("webhook inbox insert failed deliveryId={}", deliveryId, e);
            return ReceiveResult.INTERNAL_ERROR;
        }

        // CRITICAL 事件检测
        if (isCriticalEvent(eventType, payloadJson)) {
            LOG.warn("CRITICAL webhook event detected deliveryId={} eventType={}", deliveryId, eventType);
            platformMetrics.recordCriticalEvent(deliveryId, eventType);
        }

        return ReceiveResult.ACCEPTED;
    }

    private boolean verifySignature(byte[] body, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            return false; // fail-closed
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

    /** Webhook 接收结果枚举。 */
    public enum ReceiveResult {
        ACCEPTED,
        UNAUTHORIZED,
        BAD_REQUEST,
        INTERNAL_ERROR
    }
}

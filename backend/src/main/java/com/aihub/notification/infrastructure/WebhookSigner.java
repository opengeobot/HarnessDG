/*
 * 功能: Webhook HMAC-SHA256 签名器——签名 payload + 时间戳 + deliveryId。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Webhook HMAC-SHA256 签名器。
 *
 * <p>签名内容为 {@code deliveryId + "\n" + timestamp + "\n" + payload}，
 * 输出头格式为 {@code t=<timestamp>,v1=<hexHmac>}。签名密钥来自部署 Secret，<b>不入库/日志</b>。
 */
@Component
public class WebhookSigner {

    private final byte[] secretKey;

    /**
     * @param secretKey 签名密钥（部署期注入 Secret，绝不入库/日志）
     */
    public WebhookSigner(@Value("${aihub.webhook.signing-secret:dev-only-default-secret}") String secretKey) {
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成签名头。
     *
     * @param deliveryId 投递唯一 ID
     * @param payload    投递负载
     * @param timestamp  签名时间戳（Unix 秒）
     * @return 签名头值（{@code t=<ts>,v1=<hexHmac>}）
     */
    public String sign(String deliveryId, String payload, long timestamp) {
        String data = deliveryId + "\n" + timestamp + "\n" + (payload == null ? "" : payload);
        byte[] hmac = computeHmac(data);
        return "t=" + timestamp + ",v1=" + bytesToHex(hmac);
    }

    /**
     * 便捷方法：用当前时间生成签名头。
     */
    public String sign(String deliveryId, String payload) {
        return sign(deliveryId, payload, Instant.now().getEpochSecond());
    }

    private byte[] computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

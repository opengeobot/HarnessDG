/*
 * 功能: Webhook 签名器单元测试——验证 HMAC-SHA256 签名格式与一致性。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Webhook 签名器单元测试。
 */
class WebhookSignerTest {

    @Test
    void shouldProduceValidSignatureFormat() {
        WebhookSigner signer = new WebhookSigner("test-secret-key");
        String signature = signer.sign("whk_123", "{\"event\":\"test\"}", 1700000000L);

        assertThat(signature).startsWith("t=1700000000,v1=");
        // v1 后面应该是 64 字符的十六进制 HMAC-SHA256
        String hmac = signature.substring(signature.indexOf("v1=") + 3);
        assertThat(hmac).hasSize(64);
        assertThat(hmac).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldProduceSameSignatureForSameInput() {
        WebhookSigner signer = new WebhookSigner("test-secret-key");
        String sig1 = signer.sign("whk_123", "{\"event\":\"test\"}", 1700000000L);
        String sig2 = signer.sign("whk_123", "{\"event\":\"test\"}", 1700000000L);
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void shouldProduceDifferentSignatureForDifferentPayload() {
        WebhookSigner signer = new WebhookSigner("test-secret-key");
        String sig1 = signer.sign("whk_123", "{\"event\":\"test1\"}", 1700000000L);
        String sig2 = signer.sign("whk_123", "{\"event\":\"test2\"}", 1700000000L);
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void shouldProduceDifferentSignatureForDifferentKey() {
        WebhookSigner signer1 = new WebhookSigner("key1");
        WebhookSigner signer2 = new WebhookSigner("key2");
        String sig1 = signer1.sign("whk_123", "{\"event\":\"test\"}", 1700000000L);
        String sig2 = signer2.sign("whk_123", "{\"event\":\"test\"}", 1700000000L);
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void shouldHandleNullPayload() {
        WebhookSigner signer = new WebhookSigner("test-secret-key");
        String signature = signer.sign("whk_123", null, 1700000000L);
        assertThat(signature).startsWith("t=1700000000,v1=");
        String hmac = signature.substring(signature.indexOf("v1=") + 3);
        assertThat(hmac).hasSize(64);
    }
}

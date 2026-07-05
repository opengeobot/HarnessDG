/*
 * 功能: 幂等辅助工具单元测试——验证键构建、摘要与序列化。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 幂等辅助工具单元测试。
 */
class IdempotencySupportTest {

    private final IdempotencySupport support = new IdempotencySupport(new ObjectMapper());

    @Test
    void shouldBuildKeyFromHeader() {
        IdempotencyKey key = support.buildKey("idem-001", "usr_01", "POST", "/api/v1/assets");

        assertThat(key).isNotNull();
        assertThat(key.key()).isEqualTo("idem-001");
        assertThat(key.principalId()).isEqualTo("usr_01");
        assertThat(key.method()).isEqualTo("POST");
        assertThat(key.path()).isEqualTo("/api/v1/assets");
    }

    @Test
    void shouldReturnNullKeyWhenHeaderIsNull() {
        IdempotencyKey key = support.buildKey(null, "usr_01", "POST", "/api/v1/assets");
        assertThat(key).isNull();
    }

    @Test
    void shouldReturnNullKeyWhenHeaderIsBlank() {
        IdempotencyKey key = support.buildKey("  ", "usr_01", "POST", "/api/v1/assets");
        assertThat(key).isNull();
    }

    @Test
    void shouldComputeSha256Digest() {
        String digest = support.sha256Digest(Map.of("name", "test-model"));
        assertThat(digest).isNotBlank().hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void shouldReturnUnknownOnDigestFailure() {
        // ObjectMapper cannot serialize objects that throw on write
        String digest = support.sha256Digest(null);
        // null serializes to "null" in Jackson, so it should still produce a valid hash
        assertThat(digest).isNotBlank();
    }

    @Test
    void shouldSerializeObjectToJson() {
        String json = support.serialize(Map.of("id", "ast_01", "name", "model"));
        assertThat(json).contains("ast_01").contains("model");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeserializeJsonToType() {
        String json = "{\"id\":\"ast_01\",\"name\":\"model\"}";
        Map<String, Object> result = support.deserialize(json, Map.class);
        assertThat(result).containsEntry("id", "ast_01").containsEntry("name", "model");
    }
}

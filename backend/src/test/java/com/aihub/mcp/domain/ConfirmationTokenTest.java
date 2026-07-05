package com.aihub.mcp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfirmationToken} 单元测试。
 */
class ConfirmationTokenTest {

    @Test
    void createShouldProduceValidToken() {
        ConfirmationToken token = ConfirmationToken.create("usr_1", "asset_create_draft",
                Map.of("assetId", "ast_1", "version", "v1"));
        assertThat(token.tokenId()).isNotBlank();
        assertThat(token.principalId()).isEqualTo("usr_1");
        assertThat(token.toolName()).isEqualTo("asset_create_draft");
        assertThat(token.argumentsDigest()).hasSize(64); // SHA-256 hex
        assertThat(token.consumed()).isFalse();
        assertThat(token.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void validateShouldPassForMatchingCall() {
        Map<String, Object> args = Map.of("assetId", "ast_1", "version", "v1");
        ConfirmationToken token = ConfirmationToken.create("usr_1", "asset_create_draft", args);
        token.validate("usr_1", "asset_create_draft", args); // should not throw
    }

    @Test
    void validateShouldRejectConsumedToken() {
        Map<String, Object> args = Map.of("assetId", "ast_1");
        ConfirmationToken token = ConfirmationToken.create("usr_1", "tool_a", args);
        ConfirmationToken consumed = token.markConsumed();
        assertThat(consumed.consumed()).isTrue();
        assertThatThrownBy(() -> consumed.validate("usr_1", "tool_a", args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumed");
    }

    @Test
    void validateShouldRejectPrincipalMismatch() {
        Map<String, Object> args = Map.of("assetId", "ast_1");
        ConfirmationToken token = ConfirmationToken.create("usr_1", "tool_a", args);
        assertThatThrownBy(() -> token.validate("usr_2", "tool_a", args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("principal");
    }

    @Test
    void validateShouldRejectToolMismatch() {
        Map<String, Object> args = Map.of("assetId", "ast_1");
        ConfirmationToken token = ConfirmationToken.create("usr_1", "tool_a", args);
        assertThatThrownBy(() -> token.validate("usr_1", "tool_b", args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool");
    }

    @Test
    void validateShouldRejectArgumentsMismatch() {
        ConfirmationToken token = ConfirmationToken.create("usr_1", "tool_a",
                Map.of("assetId", "ast_1"));
        assertThatThrownBy(() -> token.validate("usr_1", "tool_a",
                Map.of("assetId", "ast_2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    void validateShouldRejectExpiredToken() {
        // 构造已过期令牌
        ConfirmationToken expired = new ConfirmationToken("tok_1", "usr_1", "tool_a",
                "digest", Instant.now().minusSeconds(10), false);
        assertThatThrownBy(() -> expired.validate("usr_1", "tool_a", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }
}

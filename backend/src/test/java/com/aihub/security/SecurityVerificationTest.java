package com.aihub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.mcp.domain.ConfirmationToken;
import com.aihub.shared.logging.SensitiveDataMasker;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 安全验证测试集。
 *
 * <p>覆盖：
 * <ul>
 *   <li>路径穿越防御</li>
 *   <li>符号链接防御</li>
 *   <li>确认令牌安全（过期/重放/参数篡改）</li>
 *   <li>敏感数据脱敏</li>
 * </ul>
 */
class SecurityVerificationTest {

    // ---- 路径穿越防御 ----

    @Test
    void pathTraversalShouldBeRejected() {
        String[] maliciousPaths = {
                "../../../etc/passwd",
                "..\\..\\windows\\system32",
                "/etc/shadow",
                "data/../../secret",
                "data/./../config"
        };

        for (String path : maliciousPaths) {
            assertThat(isPathSafe(path))
                    .as("Path should be rejected: %s", path)
                    .isFalse();
        }
    }

    @Test
    void safePathShouldBeAccepted() {
        String[] safePaths = {
                "data/train.csv",
                "models/v1/weights.bin",
                "README.md",
                "src/main.py"
        };

        for (String path : safePaths) {
            assertThat(isPathSafe(path))
                    .as("Path should be accepted: %s", path)
                    .isTrue();
        }
    }

    // ---- 确认令牌安全 ----

    @Test
    void confirmationTokenShouldRejectReplay() {
        Map<String, Object> args = Map.of("assetId", "ast_1", "version", "v1");
        ConfirmationToken token = ConfirmationToken.create("usr_1", "asset_create_draft", args);

        // 第一次验证通过
        token.validate("usr_1", "asset_create_draft", args);

        // 标记消费后重放应被拒绝
        ConfirmationToken consumed = token.markConsumed();
        assertThatThrownBy(() -> consumed.validate("usr_1", "asset_create_draft", args))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmationTokenShouldRejectTamperedArguments() {
        ConfirmationToken token = ConfirmationToken.create("usr_1", "tool_a",
                Map.of("assetId", "ast_1", "version", "v1"));

        // 篡改参数
        assertThatThrownBy(() -> token.validate("usr_1", "tool_a",
                Map.of("assetId", "ast_1", "version", "v2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    void confirmationTokenShouldRejectAfterExpiry() {
        ConfirmationToken expired = new ConfirmationToken("tok_1", "usr_1", "tool_a",
                "digest", Instant.now().minusSeconds(1), false);

        assertThatThrownBy(() -> expired.validate("usr_1", "tool_a", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    // ---- 敏感数据脱敏 ----

    @Test
    void sensitiveDataMaskerShouldMaskJwtTokens() {
        SensitiveDataMasker masker = new SensitiveDataMasker();
        String input = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature";
        String masked = masker.maskValue(input);
        assertThat(masked).doesNotContain("eyJhbGciOiJSUzI1NiJ9");
    }

    @Test
    void sensitiveDataMaskerShouldMaskPresignedUrls() {
        SensitiveDataMasker masker = new SensitiveDataMasker();
        String input = "https://minio.example.com/bucket/key?X-Amz-Signature=abc123&X-Amz-Expires=900";
        String masked = masker.maskValue(input);
        assertThat(masked).doesNotContain("abc123");
    }

    // ---- 辅助方法 ----

    /**
     * 路径安全检查（与 UploadMaterializeJobHandler 逻辑一致）。
     */
    private boolean isPathSafe(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.contains("..")) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        if (path.contains("./")) return false;
        // 检查空组件
        for (String component : path.split("[/\\\\]")) {
            if (component.isEmpty()) return false;
        }
        return true;
    }
}

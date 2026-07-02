/*
 * 功能: SSRF 防护器单元测试——验证拒绝内网/保留地址与非法 scheme。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.ValidationException;
import com.aihub.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * SSRF 防护器单元测试。
 */
class SsrfGuardTest {

    @Test
    void shouldRejectNullUrl() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate(null))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).errorCode())
                .isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN);
    }

    @Test
    void shouldRejectBlankUrl() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("   "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRejectNonHttpScheme() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("ftp://example.com/hook"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRejectHttpsOnlyWhenHttpUsed() {
        SsrfGuard guard = new SsrfGuard(true, false);
        assertThatThrownBy(() -> guard.validate("http://example.com/hook"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldAllowHttpsWhenHttpsOnly() {
        SsrfGuard guard = new SsrfGuard(true, false);
        // example.com 是公网地址，不会触发内网拒绝
        assertThatCode(() -> guard.validate("https://example.com/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowHttpWhenNotHttpsOnly() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatCode(() -> guard.validate("http://example.com/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectLocalhostWhenNotAllowed() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("http://localhost:8080/hook"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> guard.validate("http://127.0.0.1:8080/hook"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldAllowLocalhostWhenAllowed() {
        SsrfGuard guard = new SsrfGuard(false, true);
        assertThatCode(() -> guard.validate("http://localhost:8080/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectLocalDomain() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("http://internal.local/hook"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRejectMissingHost() {
        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("https:///hook"))
                .isInstanceOf(ValidationException.class);
    }
}

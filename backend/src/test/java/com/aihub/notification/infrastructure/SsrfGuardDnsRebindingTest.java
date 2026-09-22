/*
 * 功能: SSRF DNS rebinding 探测——主机名解析为 loopback 时拒绝。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import java.net.InetAddress;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * DNS rebinding 防护探测（依赖环境 DNS 将 127.0.0.1.nip.io 解析为 loopback）。
 */
class SsrfGuardDnsRebindingTest {

    private static final String REBIND_HOST = "127.0.0.1.nip.io";

    @Test
    void shouldRejectDnsRebindingHostname() throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(REBIND_HOST);
        boolean resolvesToLoopback = false;
        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()) {
                resolvesToLoopback = true;
                break;
            }
        }
        Assumptions.assumeTrue(resolvesToLoopback,
                "DNS rebinding probe skipped: " + REBIND_HOST + " did not resolve to loopback");

        SsrfGuard guard = new SsrfGuard(false, false);
        assertThatThrownBy(() -> guard.validate("http://" + REBIND_HOST + ":8080/webhook"))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).errorCode())
                .isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN);
    }
}

/*
 * 功能: PrincipalContextFilter 单元测试——验证请求上下文建立、关联标识回显与清理。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.shared.id.UlidIdGenerator;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link PrincipalContextFilter} 离线单元测试，无需 Spring 上下文与数据库。
 */
class PrincipalContextFilterTest {

    private final PrincipalContextFilter filter = new PrincipalContextFilter(new UlidIdGenerator());

    @AfterEach
    void cleanup() {
        PrincipalContextHolder.clear();
    }

    @Test
    void echoesProvidedRequestIdAndExtractsTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/dependencies");
        request.addHeader(PrincipalContextFilter.REQUEST_ID_HEADER, "req_provided_001");
        request.addHeader(PrincipalContextFilter.TRACEPARENT_HEADER,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader(PrincipalContextFilter.ACCEPT_LANGUAGE_HEADER, "en-US,en;q=0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<PrincipalContext> captured = new AtomicReference<>();
        FilterChain chain = (req, res) ->
                captured.set(PrincipalContextHolder.require());

        filter.doFilter(request, response, chain);

        PrincipalContext context = captured.get();
        assertThat(context.requestId()).isEqualTo("req_provided_001");
        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.locale()).isEqualTo("en-US");
        assertThat(response.getHeader(PrincipalContextFilter.REQUEST_ID_HEADER)).isEqualTo("req_provided_001");
        assertThat(response.getHeader(PrincipalContextFilter.TRACE_ID_HEADER))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        // 请求结束后必须清理 ThreadLocal，避免线程复用串台。
        assertThat(PrincipalContextHolder.current()).isEmpty();
    }

    @Test
    void generatesRequestIdAndTraceIdWhenHeadersAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/dependencies");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<PrincipalContext> captured = new AtomicReference<>();
        FilterChain chain = (req, res) -> captured.set(PrincipalContextHolder.require());

        filter.doFilter(request, response, chain);

        PrincipalContext context = captured.get();
        assertThat(context.requestId()).startsWith("req_");
        assertThat(context.traceId()).hasSize(32);
        assertThat(context.locale()).isEqualTo("zh-CN");
    }

    @Test
    void generatesTraceIdWhenTraceparentMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/dependencies");
        request.addHeader(PrincipalContextFilter.TRACEPARENT_HEADER, "not-a-valid-traceparent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<PrincipalContext> captured = new AtomicReference<>();
        FilterChain chain = (req, res) -> captured.set(PrincipalContextHolder.require());

        filter.doFilter(request, response, chain);

        assertThat(captured.get().traceId()).hasSize(32);
    }
}

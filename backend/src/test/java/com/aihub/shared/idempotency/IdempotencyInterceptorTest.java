/*
 * 功能: 幂等拦截器单元测试——验证重放、放行与无键场景。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 幂等拦截器单元测试。
 */
class IdempotencyInterceptorTest {

    private IdempotencyStore store;
    private IdempotencyInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        store = mock(IdempotencyStore.class);
        interceptor = new IdempotencyInterceptor(store, new ObjectMapper());
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void shouldPassThroughWhenNoIdempotencyKey() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(store, never()).find(any());
    }

    @Test
    void shouldPassThroughWhenBlankIdempotencyKey() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn("  ");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(store, never()).find(any());
    }

    @Test
    void shouldPassThroughWhenStoreMisses() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn("idem-123");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/assets");
        when(request.getAttribute("principalId")).thenReturn("usr_01");
        when(store.find(any(IdempotencyKey.class))).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(store).find(any(IdempotencyKey.class));
    }

    @Test
    void shouldReturnCachedResponseOnReplay() throws Exception {
        IdempotencyKey key = new IdempotencyKey("idem-456", "usr_01", "POST", "/api/v1/assets");
        IdempotencyRecord record = new IdempotencyRecord(key, "digest", 201,
                "{\"id\":\"ast_01\"}", Instant.now());
        when(request.getHeader("Idempotency-Key")).thenReturn("idem-456");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/assets");
        when(request.getAttribute("principalId")).thenReturn("usr_01");
        when(store.find(any(IdempotencyKey.class))).thenReturn(Optional.of(record));

        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).setStatus(201);
        verify(response).setHeader("X-Idempotency-Replayed", "true");
        assertThat(writer.toString()).contains("ast_01");
    }

    @Test
    void shouldHandleEmptyResponseBodyOnReplay() throws Exception {
        IdempotencyKey key = new IdempotencyKey("idem-789", "usr_01", "DELETE", "/api/v1/assets/ast_01");
        IdempotencyRecord record = new IdempotencyRecord(key, null, 200, "", Instant.now());
        when(request.getHeader("Idempotency-Key")).thenReturn("idem-789");
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/v1/assets/ast_01");
        when(request.getAttribute("principalId")).thenReturn("usr_01");
        when(store.find(any(IdempotencyKey.class))).thenReturn(Optional.of(record));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        verify(response).setStatus(200);
        verify(response).setHeader("X-Idempotency-Replayed", "true");
    }

    @Test
    void shouldFallbackToUserPrincipalWhenNoAttribute() throws Exception {
        java.security.Principal principal = mock(java.security.Principal.class);
        when(principal.getName()).thenReturn("usr_fallback");
        when(request.getHeader("Idempotency-Key")).thenReturn("idem-fb");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        when(request.getAttribute("principalId")).thenReturn(null);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(store.find(any(IdempotencyKey.class))).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void shouldUseAnonymousWhenNoPrincipalAvailable() throws Exception {
        when(request.getHeader("Idempotency-Key")).thenReturn("idem-anon");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        when(request.getAttribute("principalId")).thenReturn(null);
        when(request.getUserPrincipal()).thenReturn(null);
        when(store.find(any(IdempotencyKey.class))).thenReturn(Optional.empty());

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }
}

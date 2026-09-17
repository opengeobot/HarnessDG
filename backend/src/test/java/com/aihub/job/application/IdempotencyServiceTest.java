/*
 * 功能: 幂等服务单元测试——验证命中复用首次结果、未命中执行并落库。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencyRecord;
import com.aihub.shared.idempotency.IdempotencyStore;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 幂等服务单元测试。
 */
class IdempotencyServiceTest {

    @Test
    void shouldReplayFirstResultWhenKeyExists() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyKey key = new IdempotencyKey("test-key", "usr_1", "POST", "/api/v1/assets");
        IdempotencyRecord existing = new IdempotencyRecord(key, "digest", 201, "created", Instant.now());
        when(store.find(key)).thenReturn(Optional.of(existing));

        IdempotencyService service = new IdempotencyService(store);
        AtomicInteger callCount = new AtomicInteger(0);

        IdempotencyService.IdempotencyResult result = service.execute(key, "digest", () -> {
            callCount.incrementAndGet();
            return new IdempotencyService.IdempotencyResponse(200, "should-not-run");
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().status()).isEqualTo(201);
        assertThat(result.response().body()).isEqualTo("created");
        assertThat(callCount.get()).isZero();
        verify(store, never()).save(any());
    }

    @Test
    void shouldExecuteAndSaveWhenKeyMissing() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyKey key = new IdempotencyKey("new-key", "usr_1", "POST", "/api/v1/assets");
        when(store.find(key)).thenReturn(Optional.empty());

        IdempotencyService service = new IdempotencyService(store);

        IdempotencyService.IdempotencyResult result = service.execute(key, "digest", () ->
                new IdempotencyService.IdempotencyResponse(201, "created-body"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().status()).isEqualTo(201);
        assertThat(result.response().body()).isEqualTo("created-body");
        verify(store, times(1)).save(any());
    }

    @Test
    void shouldExecuteWithoutIdempotencyWhenKeyNull() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyService service = new IdempotencyService(store);

        IdempotencyService.IdempotencyResult result = service.execute(null, null, () ->
                new IdempotencyService.IdempotencyResponse(200, "ok"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().status()).isEqualTo(200);
        verify(store, never()).find(any());
        verify(store, never()).save(any());
    }

    @Test
    void shouldRejectSameKeyWithDifferentFingerprint() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyKey key = new IdempotencyKey("conflict-key", "usr_1", "POST", "/api/v1/organizations");
        IdempotencyRecord existing =
                new IdempotencyRecord(key, "digest-first", 201, "created", Instant.now());
        when(store.find(key)).thenReturn(Optional.of(existing));

        IdempotencyService service = new IdempotencyService(store);
        AtomicInteger callCount = new AtomicInteger(0);

        assertThatThrownBy(() -> service.execute(key, "digest-second", () -> {
            callCount.incrementAndGet();
            return new IdempotencyService.IdempotencyResponse(201, "should-not-run");
        }))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        assertThat(callCount.get())
                .as("同键不同请求体不得执行业务逻辑")
                .isZero();
        verify(store, never()).save(any());
    }

    @Test
    void shouldReplayWhenFingerprintMatches() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyKey key = new IdempotencyKey("same-key", "usr_1", "POST", "/api/v1/organizations");
        IdempotencyRecord existing =
                new IdempotencyRecord(key, "digest-same", 201, "created", Instant.now());
        when(store.find(key)).thenReturn(Optional.of(existing));

        IdempotencyService service = new IdempotencyService(store);

        IdempotencyService.IdempotencyResult result = service.execute(key, "digest-same", () -> {
            throw new AssertionError("同键同摘要必须复用首次结果，不得执行 supplier");
        });

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().status()).isEqualTo(201);
        assertThat(result.response().body()).isEqualTo("created");
    }
}

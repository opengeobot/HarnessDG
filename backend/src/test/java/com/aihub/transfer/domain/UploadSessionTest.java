package com.aihub.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link UploadSession} 领域单元测试。
 */
class UploadSessionTest {

    @Test
    void createReturnsOpenSession() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
        UploadSession session = UploadSession.create(
                "upl_01", "ast_01", "ver_01", "usr_01", 1024, 2, expiresAt);

        assertThat(session.sessionId()).isEqualTo("upl_01");
        assertThat(session.status()).isEqualTo(UploadSessionStatus.OPEN);
        assertThat(session.totalBytes()).isEqualTo(1024);
    }

    @Test
    void createRejectsExcessiveBytes() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
        long tooLarge = UploadSession.MAX_SESSION_BYTES + 1;

        assertThatThrownBy(() -> UploadSession.create(
                "upl_01", "ast_01", "ver_01", "usr_01", tooLarge, 1, expiresAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commitTransitionsToCommitting() {
        UploadSession session = createOpenSession();
        session.commit();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.COMMITTING);
    }

    @Test
    void commitFromNonOpenThrowsIllegalState() {
        UploadSession session = createOpenSession();
        session.commit();
        session.complete();

        assertThatThrownBy(session::commit)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeTransitionsToCompleted() {
        UploadSession session = createOpenSession();
        session.commit();
        session.markProcessing();
        session.complete();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.COMPLETED);
    }

    @Test
    void markProcessingFromCommitting() {
        UploadSession session = createOpenSession();
        session.commit();
        session.markProcessing();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.PROCESSING);
    }

    @Test
    void markFailedFromProcessing() {
        UploadSession session = createOpenSession();
        session.commit();
        session.markProcessing();
        session.markFailed();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.FAILED);
    }

    @Test
    void completeFromCommittingStillSupported() {
        UploadSession session = createOpenSession();
        session.commit();
        session.complete();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.COMPLETED);
    }

    @Test
    void cancelFromOpenSucceeds() {
        UploadSession session = createOpenSession();
        session.cancel();
        assertThat(session.status()).isEqualTo(UploadSessionStatus.CANCELLED);
    }

    @Test
    void cancelFromCompletedThrowsIllegalState() {
        UploadSession session = createOpenSession();
        session.commit();
        session.markProcessing();
        session.complete();

        assertThatThrownBy(session::cancel)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isExpiredReturnsTrueForPastExpiry() {
        Instant expiredAt = Instant.now().minus(Duration.ofMinutes(1));
        UploadSession session = new UploadSession(
                "upl_01", "ast_01", "ver_01", "usr_01",
                UploadSessionStatus.OPEN, 1024, 1, expiredAt,
                null, Instant.now(), Instant.now());

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void isExpiredReturnsFalseForFutureExpiry() {
        UploadSession session = createOpenSession();
        assertThat(session.isExpired()).isFalse();
    }

    private UploadSession createOpenSession() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
        return UploadSession.create(
                "upl_01", "ast_01", "ver_01", "usr_01", 1024, 1, expiresAt);
    }
}

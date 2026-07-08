package com.aihub.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UploadApplicationService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UploadApplicationServiceTest {

    @Mock private UploadSessionRepository sessionRepository;
    @Mock private StoragePort storagePort;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;

    private UploadApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UploadApplicationService(
                sessionRepository, storagePort, authorizationService, auditService, idGenerator);
    }

    @Test
    void createSessionReturnsOpenSession() {
        when(idGenerator.generate(IdPrefix.UPLOAD_SESSION)).thenReturn("upl_gen");
        when(storagePort.createMultipartUpload(anyString(), anyString(), anyString()))
                .thenReturn("minio-upload-id");
        doNothing().when(sessionRepository).insert(any(UploadSession.class));

        UploadSessionView view = service.createSession(
                "ast_01", "ver_01", 1024, 2, "usr_01");

        assertThat(view.sessionId()).isEqualTo("upl_gen");
        assertThat(view.status()).isEqualTo(UploadSessionStatus.OPEN);
        verify(authorizationService).requirePermission("asset:manage");
        verify(auditService).record(any());
    }

    @Test
    void createSessionRejectsOversizedUpload() {
        long tooLarge = UploadSession.MAX_SESSION_BYTES + 1;

        assertThatThrownBy(() -> service.createSession(
                "ast_01", "ver_01", tooLarge, 1, "usr_01"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void getSessionReturnsExistingSession() {
        UploadSession session = createOpenSession();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        UploadSessionView view = service.getSession("upl_01");

        assertThat(view.sessionId()).isEqualTo("upl_01");
    }

    @Test
    void getSessionThrowsNotFound() {
        when(sessionRepository.findBySessionId("upl_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession("upl_missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void presignPartUploadReturnsUrl() throws Exception {
        UploadSession session = createOpenSession();
        session.bindMinioUploadId("minio-id");
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));
        URL mockUrl = new URL("https://minio.example.com/staging/part1");
        when(storagePort.presignPartUpload(anyString(), anyString(), anyString(),
                eq(1), any(Duration.class))).thenReturn(mockUrl);

        URL result = service.presignPartUpload("upl_01", 1, "usr_01");

        assertThat(result).isEqualTo(mockUrl);
    }

    @Test
    void presignPartUploadRejectsClosedSession() {
        UploadSession session = createOpenSession();
        session.bindMinioUploadId("minio-id");
        session.commit();
        session.complete();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presignPartUpload("upl_01", 1, "usr_01"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeSessionTransitionsToCompleted() {
        UploadSession session = createOpenSession();
        session.bindMinioUploadId("minio-id");
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        List<StoragePort.PartInfo> parts = List.of(new StoragePort.PartInfo(1, "etag1"));
        UploadSessionView view = service.completeSession("upl_01", parts, "usr_01");

        assertThat(view.status()).isEqualTo(UploadSessionStatus.COMPLETED);
        verify(storagePort).completeMultipartUpload(anyString(), anyString(), anyString(), any());
        verify(auditService).record(any());
    }

    @Test
    void cancelSessionTransitionsToCancelled() {
        UploadSession session = createOpenSession();
        session.bindMinioUploadId("minio-id");
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        UploadSessionView view = service.cancelSession("upl_01", "usr_01");

        assertThat(view.status()).isEqualTo(UploadSessionStatus.CANCELLED);
        verify(storagePort).abortMultipartUpload(anyString(), anyString(), anyString());
    }

    private UploadSession createOpenSession() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
        return UploadSession.create("upl_01", "ast_01", "ver_01", "usr_01", 1024, 1, expiresAt);
    }
}

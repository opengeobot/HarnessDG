package com.aihub.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobStatus;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadFile;
import com.aihub.transfer.domain.UploadFileRepository;
import com.aihub.transfer.domain.UploadPartRepository;
import com.aihub.transfer.domain.UploadSessionRepository;
import com.aihub.transfer.domain.UploadSessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Mock private UploadFileRepository uploadFileRepository;
    @Mock private UploadPartRepository uploadPartRepository;
    @Mock private StoragePort storagePort;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @Mock private IdGenerator idGenerator;
    @Mock private com.aihub.notification.application.NotificationService notificationService;
    @Mock private JobApplicationService jobApplicationService;

    private UploadApplicationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new UploadApplicationService(
                sessionRepository, uploadFileRepository, uploadPartRepository,
                storagePort, authorizationService, auditService, idGenerator,
                notificationService, jobApplicationService, objectMapper);
    }

    @Test
    void createSessionReturnsOpenSession() {
        when(idGenerator.generate(IdPrefix.UPLOAD_SESSION)).thenReturn("upl_gen");
        when(idGenerator.generate(IdPrefix.UPLOAD_FILE)).thenReturn("upf_bundle");
        when(storagePort.createMultipartUpload(anyString(), anyString(), anyString()))
                .thenReturn("minio-upload-id");
        doNothing().when(sessionRepository).insert(any(UploadSession.class));
        doNothing().when(uploadFileRepository).insert(any(UploadFile.class));

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
    void getSessionReturnsFiles() {
        UploadSession session = createOpenSession();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));
        UploadFile bundle = new UploadFile(
                "upf_bundle", "upl_01", "_session_bundle", 1024,
                null, "application/octet-stream", 1,
                UploadFile.UploadFileStatus.UPLOADING);
        when(uploadFileRepository.findSessionBundleFile("upl_01")).thenReturn(Optional.of(bundle));
        when(uploadPartRepository.listByFileId("upf_bundle")).thenReturn(List.of());
        when(uploadFileRepository.listBySessionId("upl_01")).thenReturn(List.of(
                bundle,
                new UploadFile("upf_1", "upl_01", "data.csv", 100L,
                        "abc", "text/csv", 1, UploadFile.UploadFileStatus.COMPLETED)));

        UploadSessionView view = service.getSession("upl_01");

        assertThat(view.files()).hasSize(1);
        assertThat(view.files().getFirst().path()).isEqualTo("data.csv");
        assertThat(view.files().getFirst().status()).isEqualTo("COMPLETED");
    }

    @Test
    void getSessionReturnsParts() {
        UploadSession session = createOpenSession();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));
        UploadFile bundle = new UploadFile(
                "upf_bundle", "upl_01", "_session_bundle", 1024,
                null, "application/octet-stream", 1,
                UploadFile.UploadFileStatus.UPLOADING);
        when(uploadFileRepository.findSessionBundleFile("upl_01")).thenReturn(Optional.of(bundle));
        when(uploadPartRepository.listByFileId("upf_bundle")).thenReturn(List.of(
                new com.aihub.transfer.domain.UploadPart(
                        "upf_bundle", 1, 512L, "etag1", null, Instant.now())));
        when(uploadFileRepository.listBySessionId("upl_01")).thenReturn(List.of(bundle));

        UploadSessionView view = service.getSession("upl_01");

        assertThat(view.parts()).hasSize(1);
        assertThat(view.parts().getFirst().status()).isEqualTo("COMPLETED");
    }

    @Test
    void getSessionForAssetRejectsMismatchedAsset() {
        UploadSession session = createOpenSession();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.getSessionForAsset("ast_other", "upl_01"))
                .isInstanceOf(NotFoundException.class);
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
        when(uploadFileRepository.findSessionBundleFile("upl_01"))
                .thenReturn(Optional.of(new UploadFile(
                        "upf_1", "upl_01", "_session_bundle", 1024L,
                        null, "application/octet-stream", 1,
                        UploadFile.UploadFileStatus.UPLOADING)));
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
        session.markProcessing();
        session.complete();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.presignPartUpload("upl_01", 1, "usr_01"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeSessionEnqueuesMaterializeAndLeavesProcessing() {
        UploadSession session = createOpenSession();
        session.bindMinioUploadId("minio-id");
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));
        when(uploadFileRepository.findSessionBundleFile("upl_01"))
                .thenReturn(Optional.of(new UploadFile(
                        "upf_1", "upl_01", "_session_bundle", 1024L,
                        null, "application/octet-stream", 1,
                        UploadFile.UploadFileStatus.UPLOADING)));
        when(idGenerator.generate(IdPrefix.UPLOAD_FILE)).thenReturn("upf_file_1");
        Job job = mock(Job.class);
        when(job.jobId()).thenReturn("job_mat_01");
        when(jobApplicationService.enqueue(eq("UPLOAD_MATERIALIZE"), anyString(), any(), any(),
                eq("ast_01"), eq(3))).thenReturn(job);

        List<StoragePort.PartInfo> parts = List.of(new StoragePort.PartInfo(1, "etag1"));
        List<UploadApplicationService.FileMetadata> files = List.of(
                new UploadApplicationService.FileMetadata("data.csv", "abc", 100L, "text/csv", null));
        UploadSessionView view = service.completeSession("upl_01", parts, files, "usr_01");

        assertThat(view.status()).isEqualTo(UploadSessionStatus.PROCESSING);
        assertThat(view.materializeJobId()).isEqualTo("job_mat_01");
        verify(storagePort).completeMultipartUpload(anyString(), anyString(), anyString(), any());
        verify(jobApplicationService).enqueue(eq("UPLOAD_MATERIALIZE"), anyString(), eq("usr_01"),
                eq(null), eq("ast_01"), eq(3));
        verify(auditService).record(any());
    }

    @Test
    void completeSessionRejectsEmptyFiles() {
        UploadSession session = createOpenSession();
        when(sessionRepository.findBySessionId("upl_01")).thenReturn(Optional.of(session));

        List<StoragePort.PartInfo> parts = List.of(new StoragePort.PartInfo(1, "etag1"));
        assertThatThrownBy(() -> service.completeSession("upl_01", parts, List.of(), "usr_01"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("upload files required");

        // 空文件不应推进物化或审计
        verify(storagePort, org.mockito.Mockito.never())
                .completeMultipartUpload(anyString(), anyString(), anyString(), any());
        verify(jobApplicationService, org.mockito.Mockito.never())
                .enqueue(anyString(), anyString(), any(), any(), anyString(), anyInt());
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

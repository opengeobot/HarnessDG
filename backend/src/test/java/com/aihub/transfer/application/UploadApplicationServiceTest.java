/*
 * 功能: UploadApplicationService 单元测试，覆盖上传大小限制逻辑。
 * 时间: 2026-07-31
 * 作者: AxeXie
 */
package com.aihub.transfer.application;

import com.aihub.audit.application.AuditService;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.domain.UploadSession;
import com.aihub.transfer.domain.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadApplicationService")
class UploadApplicationServiceTest {

    private static final long MAX_UPLOAD_SIZE_BYTES = 20L * 1024 * 1024 * 1024;

    @Mock
    private UploadSessionRepository sessionRepository;
    @Mock
    private StoragePort storagePort;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private AuditService auditService;
    @Mock
    private IdGenerator idGenerator;

    private UploadApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UploadApplicationService(sessionRepository, storagePort,
                authorizationService, auditService, idGenerator);
        // These stubs are only used by tests that create sessions
        lenient().when(idGenerator.generate(IdPrefix.UPLOAD_SESSION)).thenReturn("upl_001");
        lenient().when(storagePort.createMultipartUpload(anyString(), anyString(), anyString()))
                .thenReturn("minio-upload-id");
    }

    @Test
    @DisplayName("正常小尺寸应创建会话")
    void shouldCreateSessionForNormalSize() {
        var view = service.createSession("ast_01", "ver_01", 1024, 1, "usr_01");

        assertThat(view).isNotNull();
        assertThat(view.sessionId()).isEqualTo("upl_001");
        assertThat(view.totalBytes()).isEqualTo(1024);
        assertThat(view.fileCount()).isEqualTo(1);
        verify(sessionRepository).insert(any(UploadSession.class));
    }

    @Test
    @DisplayName("恰好 20 GiB 应创建会话")
    void shouldCreateSessionForExactlyTwentyGib() {
        var view = service.createSession("ast_01", "ver_01", MAX_UPLOAD_SIZE_BYTES, 1, "usr_01");

        assertThat(view).isNotNull();
        assertThat(view.sessionId()).isEqualTo("upl_001");
        assertThat(view.totalBytes()).isEqualTo(MAX_UPLOAD_SIZE_BYTES);
        verify(sessionRepository).insert(any(UploadSession.class));
    }

    @Test
    @DisplayName("20 GiB + 1 byte 应抛出 UPLOAD_SIZE_EXCEEDED")
    void shouldRejectSizeExceedingTwentyGib() {
        long tooLarge = MAX_UPLOAD_SIZE_BYTES + 1;

        assertThatThrownBy(() ->
                service.createSession("ast_01", "ver_01", tooLarge, 1, "usr_01"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException e = (ValidationException) ex;
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.UPLOAD_SIZE_EXCEEDED);
                });

        verify(sessionRepository, never()).insert(any(UploadSession.class));
        verify(storagePort, never()).createMultipartUpload(anyString(), anyString(), anyString());
    }
}
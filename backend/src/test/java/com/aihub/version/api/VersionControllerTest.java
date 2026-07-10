/*
 * 功能: VersionController Idempotency-Key 单元测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.job.application.IdempotencyService;
import com.aihub.job.application.JobApplicationService;
import com.aihub.version.application.PublishApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VersionControllerTest {

    @Mock private VersionApplicationService versionService;
    @Mock private PublishApplicationService publishApplicationService;
    @Mock private JobApplicationService jobApplicationService;
    @Mock private IdempotencyService idempotencyService;

    private VersionController controller;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        controller = new VersionController(
                versionService, publishApplicationService, jobApplicationService,
                idempotencyService, objectMapper);
    }

    @Test
    void createDraftVersionReplaysIdempotentResponse() throws Exception {
        VersionView view = VersionView.from(
                com.aihub.version.domain.Version.createDraft("ver_1", "ast_1", "v1.0.0", "usr_1"));
        String body = objectMapper.writeValueAsString(view);
        when(idempotencyService.execute(any(), any(), any()))
                .thenReturn(new IdempotencyService.IdempotencyResult(
                        new IdempotencyService.IdempotencyResponse(201, body), true));

        var response = controller.createDraftVersion("ast_1", "key-1",
                new VersionController.CreateVersionRequest("v1.0.0"));

        assertThat(response.data().versionId()).isEqualTo("ver_1");
        assertThat(response.data().status()).isEqualTo(VersionStatus.DRAFT);
        verify(idempotencyService).execute(any(), any(), any());
    }
}

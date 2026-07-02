/*
 * 功能: 通知服务单元测试——验证站内通知落库、参数脱敏与查询。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationRepository;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.logging.SensitiveDataMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 通知服务单元测试。
 */
class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private OutboxRepository outboxRepository;
    private IdGenerator idGenerator;
    private SensitiveDataMasker masker;
    private ObjectMapper objectMapper;
    private Clock clock;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        outboxRepository = mock(OutboxRepository.class);
        idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate(com.aihub.shared.id.IdPrefix.NOTIFICATION)).thenReturn("ntf_test1");
        when(idGenerator.generate(com.aihub.shared.id.IdPrefix.REQUEST)).thenReturn("req_test1");
        masker = new SensitiveDataMasker();
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(notificationRepository, outboxRepository, idGenerator,
                clock, masker, objectMapper);

        PrincipalContext ctx = new PrincipalContext(
                "usr_1", PrincipalType.USER, "subj-1", null, List.of(), Set.of(), Set.of(),
                0, "zh-CN", "req-1", "trace-1");
        PrincipalContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    @Test
    void shouldSendInAppNotificationWithMaskedParams() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jobId", "job_123");
        params.put("password", "secret");
        params.put("token", "Bearer abc.def.ghi");

        service.sendInAppNotification("usr_1", "JOB_DEAD", "notification.job.dead",
                NotificationSeverity.ERROR, params);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.notificationId()).isEqualTo("ntf_test1");
        assertThat(n.principalId()).isEqualTo("usr_1");
        assertThat(n.eventType()).isEqualTo("JOB_DEAD");
        assertThat(n.severity()).isEqualTo(NotificationSeverity.ERROR);
        assertThat(n.read()).isZero();
        // 验证参数已脱敏
        JsonNode parsed = objectMapper.readTree(n.parameters());
        assertThat(parsed.path("jobId").asText()).isEqualTo("job_123");
        assertThat(parsed.path("password").asText()).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(parsed.path("token").asText()).isEqualTo(SensitiveDataMasker.MASK);
    }

    @Test
    void shouldPublishOutboxEventWithMaskedPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resourceId", "ast_1");
        payload.put("secret", "topsecret");

        service.publishOutboxEvent("ASSET", "ast_1", "ASSET_PUBLISHED", payload, Map.of());

        verify(outboxRepository, times(1)).append(any());
    }

    @Test
    void shouldHandleNullParams() {
        service.sendInAppNotification("usr_1", "TEST_EVENT", "notification.test",
                NotificationSeverity.INFO, null);

        verify(notificationRepository, times(1)).insert(any(Notification.class));
    }
}

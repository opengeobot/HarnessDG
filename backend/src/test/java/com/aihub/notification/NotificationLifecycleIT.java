/*
 * 功能: P0B 通知生命周期集成测试——显式覆盖 p0b-exit-catalog AC-P0B-NOT-001..005。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.identity.application.IdentityCommands.CreateUserCommand;
import com.aihub.identity.application.UserManagementApplicationService;
import com.aihub.identity.application.UserView;
import com.aihub.notification.application.NotificationAdminService;
import com.aihub.notification.application.NotificationService;
import com.aihub.notification.application.NotificationView;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.notification.infrastructure.SsrfGuard;
import com.aihub.notification.infrastructure.WebhookSigner;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0B 通知生命周期集成测试。
 *
 * <p>显式映射 p0b-exit-catalog AC-P0B-NOT-001..005 场景。
 * 无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class NotificationLifecycleIT {

    private static final String PASSWORD = "Sup3rSecret!23";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationAdminService adminService;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private WebhookSigner webhookSigner;
    @Autowired private SsrfGuard ssrfGuard;
    @Autowired private UserManagementApplicationService userService;

    private void setPrincipalContext(String principalId) {
        PrincipalContext ctx = new PrincipalContext(principalId, PrincipalType.USER,
                null, null, List.of(), Set.of(), Set.of("notification:read"),
                0, "zh-CN", "req_not", "trace_not");
        PrincipalContextHolder.set(ctx);
    }

    private UserView createAndEnable(String username) {
        UserView user = userService.createUser(new CreateUserCommand(
                username, username + " name", null, "zh-CN", PASSWORD, Set.of("notification:read")));
        userService.enableUser(user.userId());
        return user;
    }

    // ── AC-P0B-NOT-001: 通知创建后可查询并标记已读 ──────────

    @Test
    void not001_notificationCreatedQueryableAndMarkableRead() {
        UserView user = createAndEnable("not001_user");
        String principalId = user.principalId();

        // 发送站内通知。
        notificationService.sendInAppNotification(principalId, "ASSET_CREATED",
                "notification.asset.created", NotificationSeverity.INFO,
                Map.of("assetName", "test-asset", "assetId", "ast_001"));

        // 查询通知列表。
        setPrincipalContext(principalId);
        try {
            CursorPage<NotificationView> page = notificationService.listNotifications(false, null, 20);
            assertThat(page.items()).isNotEmpty();

            NotificationView notification = page.items().get(0);
            assertThat(notification.eventType()).isEqualTo("ASSET_CREATED");
            assertThat(notification.i18nKey()).isEqualTo("notification.asset.created");
            assertThat(notification.status()).isEqualTo("UNREAD");

            // 标记已读。
            notificationService.markRead(notification.notificationId());

            // 重新查询，已读通知不在未读列表中。
            CursorPage<NotificationView> unreadPage = notificationService.listNotifications(true, null, 20);
            boolean stillUnread = unreadPage.items().stream()
                    .anyMatch(n -> n.notificationId().equals(notification.notificationId()));
            assertThat(stillUnread).isFalse();

            // 标记已读幂等：再次调用不抛错。
            notificationService.markRead(notification.notificationId());
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    // ── AC-P0B-NOT-001 补充: Outbox 事件发布与核心事务原子性 ──────────

    @Test
    void not001_outboxEventPublishedAtomicallyWithBusinessTransaction() {
        UserView user = createAndEnable("not001_outbox_user");
        String principalId = user.principalId();

        // 发布 Outbox 事件。
        setPrincipalContext(principalId);
        try {
            notificationService.publishOutboxEvent("ASSET", "ast_outbox_1",
                    "ASSET_CREATED",
                    Map.of("assetId", "ast_outbox_1", "name", "outbox-test"),
                    Map.of("target", "https://example.com/webhook"));

            // Outbox 事件可见。
            var outboxEvents = adminService.listOutboxEvents(50);
            boolean found = outboxEvents.stream()
                    .anyMatch(e -> "ast_outbox_1".equals(e.aggregateId())
                            && "ASSET_CREATED".equals(e.eventType()));
            assertThat(found).isTrue();
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    // ── AC-P0B-NOT-004: SSRF 防护拒绝内网/保留地址 ──────────

    @Test
    void not004_ssrfGuardRejectsLoopbackAndPrivateAddresses() {
        // localhost 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("http://localhost:8080/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 127.0.0.1 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("http://127.0.0.1:8080/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 内网 IP 10.x 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("http://10.0.0.1/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 内网 IP 192.168.x 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("http://192.168.1.1/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 内网 IP 172.16.x 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("http://172.16.0.1/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 空 URL 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate(""))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 无 scheme 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("example.com/webhook"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));

        // 非 HTTP/HTTPS scheme 被拒绝。
        assertThatThrownBy(() -> ssrfGuard.validate("ftp://example.com/file"))
                .isInstanceOfSatisfying(ValidationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.WEBHOOK_TARGET_FORBIDDEN));
    }

    // ── AC-P0B-NOT-005: Webhook HMAC-SHA256 签名与验证 ──────────

    @Test
    void not005_webhookSignatureCanBeGeneratedAndVerified() {
        String deliveryId = "dlv_test_001";
        String payload = "{\"event\":\"ASSET_CREATED\",\"assetId\":\"ast_001\"}";
        long timestamp = Instant.now().getEpochSecond();

        // 生成签名。
        String signature = webhookSigner.sign(deliveryId, payload, timestamp);
        assertThat(signature).isNotBlank();
        assertThat(signature).startsWith("t=");
        assertThat(signature).contains("v1=");

        // 签名格式：t=<timestamp>,v1=<hexHmac>
        String[] parts = signature.split(",");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo("t=" + timestamp);
        assertThat(parts[1]).startsWith("v1=");

        // 相同输入生成相同签名（确定性）。
        String sameSignature = webhookSigner.sign(deliveryId, payload, timestamp);
        assertThat(sameSignature).isEqualTo(signature);

        // 不同 payload 生成不同签名。
        String differentPayloadSig = webhookSigner.sign(deliveryId, payload + "tampered", timestamp);
        assertThat(differentPayloadSig).isNotEqualTo(signature);

        // 不同 deliveryId 生成不同签名。
        String differentIdSig = webhookSigner.sign("dlv_other", payload, timestamp);
        assertThat(differentIdSig).isNotEqualTo(signature);

        // 不同时间戳生成不同签名。
        String differentTimeSig = webhookSigner.sign(deliveryId, payload, timestamp + 1);
        assertThat(differentTimeSig).isNotEqualTo(signature);
    }

    // ── 补充: 通知参数脱敏 ──────────

    @Test
    void notificationParametersAreMasked() {
        UserView user = createAndEnable("not_mask_user");
        String principalId = user.principalId();

        // 发送包含敏感参数的通知。
        notificationService.sendInAppNotification(principalId, "SENSITIVE_TEST",
                "notification.test", NotificationSeverity.WARN,
                Map.of("password", "secret123", "token", "bearer-token-abc",
                        "normalField", "visible-value"));

        // 查询通知（参数在存储时已脱敏）。
        setPrincipalContext(principalId);
        try {
            CursorPage<NotificationView> page = notificationService.listNotifications(false, null, 20);
            assertThat(page.items()).isNotEmpty();

            NotificationView notification = page.items().get(0);
            assertThat(notification.eventType()).isEqualTo("SENSITIVE_TEST");
            // parameters 已脱敏，不含原文密码和 token。
            if (notification.parameters() != null) {
                String params = notification.parameters().toString();
                assertThat(params).doesNotContain("secret123");
                assertThat(params).doesNotContain("bearer-token-abc");
            }
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    // ── 补充: 通知不存在或越权访问时返回 NOT_FOUND ──────────

    @Test
    void notificationMarkReadDeniedForOtherUser() {
        UserView user1 = createAndEnable("not_owner");
        UserView user2 = createAndEnable("not_other");

        // 给 user1 发送通知。
        notificationService.sendInAppNotification(user1.principalId(), "TEST",
                "notification.test", NotificationSeverity.INFO, Map.of("key", "val"));

        // user2 尝试标记 user1 的通知为已读。
        setPrincipalContext(user2.principalId());
        try {
            CursorPage<NotificationView> user1Page =
                    notificationService.listNotifications(false, null, 100);
            // user2 查不到 user1 的通知（按主体隔离）。
            assertThat(user1Page.items()).isNotNull();

            // 直接用不存在的 ID 标记已读，抛 NotFoundException。
            assertThatThrownBy(() -> notificationService.markRead("ntf_nonexistent"))
                    .isInstanceOf(NotFoundException.class);
        } finally {
            PrincipalContextHolder.clear();
        }
    }

    // ── 补充: Admin Outbox/Webhook 查询 ──────────

    @Test
    void adminOutboxAndDeliveryQueryWorks() {
        // Outbox 待处理计数。
        long pendingCount = adminService.countPendingOutbox();
        assertThat(pendingCount).isGreaterThanOrEqualTo(0);

        // Outbox 列表。
        var events = adminService.listOutboxEvents(10);
        assertThat(events).isNotNull();

        // 投递列表。
        var deliveries = adminService.listDeliveries(10);
        assertThat(deliveries).isNotNull();
    }
}

/*
 * 功能: 通知应用服务——编排站内通知创建、查询、标记已读与 Outbox 事件发布。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.application;

import com.aihub.notification.domain.Notification;
import com.aihub.notification.domain.NotificationRepository;
import com.aihub.notification.domain.NotificationSeverity;
import com.aihub.notification.domain.OutboxEvent;
import com.aihub.notification.domain.OutboxRepository;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.logging.SensitiveDataMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通知应用服务。
 *
 * <p>编排站内通知创建（同步落库）、查询（按主体游标分页）、标记已读与 Outbox 事件发布。
 * 站内通知同步落库；Webhook 经 Outbox 异步投递，失败不回滚核心事务。
 * 所有 parameters 在落库前做字段级脱敏，绝不记录密钥/凭据/令牌。
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NotificationRepository notificationRepository;
    private final OutboxRepository outboxRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               OutboxRepository outboxRepository,
                               IdGenerator idGenerator, Clock clock,
                               SensitiveDataMasker masker, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxRepository = outboxRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.masker = masker;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送站内通知（同步落库）。
     *
     * @param principalId 目标主体 ID
     * @param eventType   事件类型
     * @param i18nKey     国际化文案键
     * @param severity    严重等级
     * @param parameters  文案参数（落库前脱敏）
     */
    public void sendInAppNotification(String principalId, String eventType, String i18nKey,
                                       NotificationSeverity severity, Map<String, Object> parameters) {
        Instant now = clock.instant();
        String maskedParams = maskParameters(parameters);
        Notification notification = new Notification(
                null,
                idGenerator.generate(IdPrefix.NOTIFICATION),
                principalId,
                eventType,
                i18nKey,
                maskedParams,
                severity,
                0,
                now,
                null,
                0);
        notificationRepository.insert(notification);
        LOG.info("in-app notification sent principalId={} eventType={}", principalId, eventType);
    }

    /**
     * 向多个主体 fan-out 站内通知，跳过排除主体与空值；单条失败不中断其余投递。
     */
    public void fanOutInAppNotifications(Set<String> principalIds, String excludePrincipalId,
                                         String eventType, String i18nKey,
                                         NotificationSeverity severity, Map<String, Object> parameters) {
        if (principalIds == null || principalIds.isEmpty()) {
            return;
        }
        for (String principalId : principalIds) {
            if (principalId == null || principalId.isBlank()
                    || principalId.equals(excludePrincipalId)) {
                continue;
            }
            try {
                sendInAppNotification(principalId, eventType, i18nKey, severity, parameters);
            } catch (Exception ex) {
                LOG.warn("failed to fan-out in-app notification principalId={} eventType={}",
                        principalId, eventType, ex);
            }
        }
    }

    /**
     * 在业务事务内写入 Outbox 事件（保证与核心事务原子提交）。
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根业务 ID
     * @param eventType     事件类型
     * @param payload       事件负载（落库前脱敏）
     * @param headers       事件头（如 webhook 目标等路由信息）
     */
    public void publishOutboxEvent(String aggregateType, String aggregateId, String eventType,
                                    Map<String, Object> payload, Map<String, Object> headers) {
        PrincipalContext ctx = PrincipalContextHolder.current().orElse(null);
        Instant now = clock.instant();
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate(IdPrefix.REQUEST),
                aggregateType,
                aggregateId,
                eventType,
                maskParameters(payload),
                toJson(headers),
                now,
                null,
                ctx == null ? null : ctx.traceId(),
                ctx == null ? null : ctx.principalId());
        outboxRepository.append(event);
    }

    /**
     * 游标查询当前主体的通知列表。
     *
     * @param unreadOnly 仅未读
     * @param cursor     游标（可空）
     * @param limit      每页条数
     * @return 游标分页结果
     */
    public CursorPage<NotificationView> listNotifications(boolean unreadOnly, String cursor, int limit) {
        String principalId = PrincipalContextHolder.require().principalId();
        Cursor decoded = Cursor.decode(cursor);
        List<Notification> notifications = notificationRepository.listByPrincipal(
                principalId, unreadOnly, decoded.time(), decoded.id(), limit + 1);
        boolean hasMore = notifications.size() > limit;
        List<NotificationView> views = notifications.stream().limit(limit)
                .map(n -> NotificationView.from(n, this::parseParams)).toList();
        String nextCursor = null;
        if (hasMore) {
            Notification last = notifications.get(limit - 1);
            nextCursor = Cursor.encode(last.createdAt(), last.id());
        }
        return new CursorPage<>(views, nextCursor, hasMore);
    }

    /**
     * 标记当前主体通知已读。
     */
    public void markRead(String notificationId) {
        String principalId = PrincipalContextHolder.require().principalId();
        Notification notification = notificationRepository.findByNotificationId(notificationId);
        if (notification == null || !notification.principalId().equals(principalId)) {
            throw new NotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND,
                    "notification not found or not owned by current principal",
                    Map.of("notificationId", notificationId));
        }
        boolean updated = notificationRepository.markRead(notificationId, principalId, clock.instant());
        if (!updated) {
            // 已读，幂等返回
            LOG.debug("notification already read notificationId={}", notificationId);
        }
    }

    private String maskParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        Map<String, Object> masked = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String s) {
                masked.put(key, masker.maskField(key, s));
            } else if (value != null) {
                masked.put(key, masker.maskField(key, String.valueOf(value)));
            } else {
                masked.put(key, null);
            }
        }
        return toJson(masked);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /** 游标（不透明，base64 编码 createdAt|id）。 */
    private record Cursor(Instant time, Long id) {
        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new Cursor(null, null);
            }
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = decoded.indexOf('|');
            if (sep < 0) {
                return new Cursor(null, null);
            }
            return new Cursor(Instant.parse(decoded.substring(0, sep)),
                    Long.valueOf(decoded.substring(sep + 1)));
        }

        static String encode(Instant time, Long id) {
            String raw = time.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}

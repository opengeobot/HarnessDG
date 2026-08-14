package com.modelhub.catalog.service;

import com.modelhub.catalog.config.CatalogProperties;
import com.modelhub.identity.security.CurrentPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Visit 投递（03 §6.2 / 06 §7.2）：请求线程不得同步写 visits——仅以 REQUIRES_NEW
 * 短写事务投递单行 Outbox 事件，visit_events 与 stats 重算由幂等消费者完成。
 * visitorHash：登录用户 = u:{userPublicId}；匿名 = HMAC-SHA256(轮换盐 + 当日, sourceIp)
 * （摘要不作为长期身份）；匿名且无 IP 时跳过（健康检查/预取不计数）。
 */
@Service
public class VisitRecorder {

    private static final Logger log = LoggerFactory.getLogger(VisitRecorder.class);
    private static final long WINDOW_SECONDS = 1800;

    private final OutboxService outbox;
    private final String salt;

    public VisitRecorder(OutboxService outbox, CatalogProperties props) {
        this.outbox = outbox;
        this.salt = resolveSalt(props);
    }

    /** 授权成功后由详情/文件树读取链路调用；短写事务，绝不阻塞请求线程等待重算。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(CurrentPrincipal actor, Long repositoryId, String requestIp) {
        String hash = visitorHash(actor, requestIp);
        if (hash == null) {
            return;
        }
        long epoch = Instant.now().getEpochSecond();
        OffsetDateTime windowStart = Instant.ofEpochSecond((epoch / WINDOW_SECONDS) * WINDOW_SECONDS)
                .atOffset(ZoneOffset.UTC);
        outbox.publish("VisitRecorded", "repo:" + repositoryId, null, Map.of(
                "repositoryId", repositoryId,
                "visitorHash", hash,
                "windowStart", windowStart.toString()));
    }

    /** 匿名无 IP 返回 null（调用方跳过）；登录用户始终可投递（身份即摘要）。 */
    private String visitorHash(CurrentPrincipal actor, String requestIp) {
        if (actor != null) {
            return "u:" + actor.userPublicId();
        }
        if (requestIp == null || requestIp.isBlank()) {
            return null;
        }
        String dailySalt = salt + LocalDate.now(ZoneOffset.UTC);
        return hmacSha256(dailySalt, requestIp.trim());
    }

    private static String hmacSha256(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("visitorHash HMAC 计算失败", e);
        }
    }

    /** 未配置盐时运行时随机生成并告警：摘要仍可用但不跨实例稳定（03 §6.2 轮换语义）。 */
    private static String resolveSalt(CatalogProperties props) {
        if (props.getVisitSalt() != null && !props.getVisitSalt().isBlank()) {
            return props.getVisitSalt();
        }
        String random = new SecureRandom().nextLong() + ":" + UUID.randomUUID();
        log.warn("modelhub.catalog.visit-salt 未配置，使用随机值（进程重启后匿名摘要不可比对）");
        return random;
    }
}

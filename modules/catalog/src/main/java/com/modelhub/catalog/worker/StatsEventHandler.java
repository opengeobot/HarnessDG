package com.modelhub.catalog.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.service.StatsRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 统计投影消费者（06 §7.1）：OutboxPoller 在事务外调用本组件，本组件只做薄分发并跨 bean
 * 调用 {@link StatsRebuildService}（保证 @Transactional 代理生效）。重算式幂等：重复消费
 * 重放同一事实集，结果不变（至少一次投递语义，05 §10.2）。
 */
@Component
public class StatsEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StatsEventHandler.class);

    private final StatsRebuildService stats;

    public StatsEventHandler(StatsRebuildService stats) {
        this.stats = stats;
    }

    /** RepositoryLiked/Unliked/Favorited/Unfavorited/DownloadSessionIssued/FileCountChanged 共用。 */
    public void handleCountChanged(JsonNode payload) {
        long repositoryId = payload.path("repositoryId").asLong(-1);
        if (repositoryId <= 0) {
            log.warn("统计事件缺少 repositoryId: {}", payload);
            return;
        }
        stats.recalculate(repositoryId);
    }

    /** VisitRecorded：先按 30 分钟窗口去重写入 visit_events，再重算（06 §7.2）。 */
    public void handleVisitRecorded(JsonNode payload) {
        long repositoryId = payload.path("repositoryId").asLong(-1);
        String visitorHash = payload.path("visitorHash").asText("");
        String windowStartRaw = payload.path("windowStart").asText("");
        if (repositoryId <= 0 || visitorHash.isBlank() || windowStartRaw.isBlank()) {
            log.warn("VisitRecorded 事件字段缺失: {}", payload);
            return;
        }
        stats.recordVisit(repositoryId, visitorHash, OffsetDateTime.parse(windowStartRaw));
    }
}

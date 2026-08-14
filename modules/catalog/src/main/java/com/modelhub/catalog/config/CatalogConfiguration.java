package com.modelhub.catalog.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.worker.OutboxEventHandler;
import com.modelhub.catalog.worker.StatsEventHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * catalog 模块装配：运行参数绑定（Gitea 连接、轮询/重试策略）与统计投影消费者注册
 * （06 §7.1，handler 幂等重算 repository_stats）。调度开关由 app 启动类统一 @EnableScheduling。
 */
@Configuration
@EnableConfigurationProperties({GiteaProperties.class, CatalogProperties.class})
public class CatalogConfiguration {

    // ---------- Outbox 事件处理器注册（06 §7.1 幂等消费者） ----------

    @Bean
    public OutboxEventHandler statsLikeHandler(StatsEventHandler stats) {
        return handler("RepositoryLiked", stats::handleCountChanged);
    }

    @Bean
    public OutboxEventHandler statsUnlikeHandler(StatsEventHandler stats) {
        return handler("RepositoryUnliked", stats::handleCountChanged);
    }

    @Bean
    public OutboxEventHandler statsFavoriteHandler(StatsEventHandler stats) {
        return handler("RepositoryFavorited", stats::handleCountChanged);
    }

    @Bean
    public OutboxEventHandler statsUnfavoriteHandler(StatsEventHandler stats) {
        return handler("RepositoryUnfavorited", stats::handleCountChanged);
    }

    @Bean
    public OutboxEventHandler statsVisitHandler(StatsEventHandler stats) {
        return handler("VisitRecorded", stats::handleVisitRecorded);
    }

    @Bean
    public OutboxEventHandler statsDownloadHandler(StatsEventHandler stats) {
        return handler("DownloadSessionIssued", stats::handleCountChanged);
    }

    @Bean
    public OutboxEventHandler statsFileCountHandler(StatsEventHandler stats) {
        return handler("FileCountChanged", stats::handleCountChanged);
    }

    private static OutboxEventHandler handler(String eventType, Consumer<JsonNode> fn) {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public void handle(JsonNode payload) {
                fn.accept(payload);
            }
        };
    }
}

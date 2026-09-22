/*
 * 功能: Gitea Tag 发布的 Noop 实现，未启用 Gitea 时仅 PG 记录。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.integration.gitea.domain.GiteaTagPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Noop Gitea Tag 发布器。
 *
 * <p>当 {@code aihub.gitea.enabled=false}（默认）时启用：不调用 Gitea API，
 * 由发布 Saga 仅在 PostgreSQL 记录 git_tag。Compose 生产验证需启用真实 Gitea。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "false", matchIfMissing = true)
public class NoopGiteaTagPublisher implements GiteaTagPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(NoopGiteaTagPublisher.class);

    @Override
    public TagPublishResult createProtectedTag(String repoFullName, String tagName, String commitSha) {
        LOG.info("gitea disabled: PG-only tag record repo={} tag={} commit={}",
                repoFullName, tagName, commitSha == null ? "null" : commitSha.substring(0, 8) + "...");
        return new TagPublishResult(TagPublishMode.PG_ONLY_GITEA_DISABLED, tagName, commitSha);
    }
}

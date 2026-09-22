/*
 * 功能: Gitea Tag 发布端口，供发布 Saga 创建受保护 Tag。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.domain;

/**
 * Gitea Tag 发布端口。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时由 REST 适配器实现真实 Tag 创建；
 * 禁用时由 Noop 实现记录 PG-only 模式。
 */
public interface GiteaTagPublisher {

    /**
     * 在指定仓库创建 Tag（幂等：同 Commit 视为成功）。
     *
     * @param repoFullName Gitea 仓库全名（owner/repo）
     * @param tagName      Tag 名称（与版本号映射）
     * @param commitSha    目标 Commit SHA（40 hex）
     * @return 创建结果
     */
    TagPublishResult createProtectedTag(String repoFullName, String tagName, String commitSha);

    /** Tag 创建结果。 */
    record TagPublishResult(
            TagPublishMode mode,
            String tagName,
            String commitSha) {}

    /** Tag 创建模式。 */
    enum TagPublishMode {
        /** Gitea 新创建 Tag */
        GITEA_TAG_CREATED,
        /** Tag 已存在且指向同一 Commit（幂等） */
        GITEA_TAG_EXISTS_SAME_COMMIT,
        /** Gitea 禁用，仅 PG 记录 */
        PG_ONLY_GITEA_DISABLED
    }
}

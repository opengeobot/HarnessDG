/*
 * 功能: Gitea Tag/Commit 校验端口，供已发布版本对账 Worker 校验 PG 与 Gitea 一致性。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

/**
 * Gitea Tag 与 Commit 校验端口。
 *
 * <p>仅在 {@code aihub.gitea.enabled=true} 时由基础设施适配器注册。
 */
public interface GiteaTagVerificationPort {

    /**
     * 校验 Gitea 仓库中 Tag 是否存在且指向期望 Commit。
     *
     * @param namespace         命名空间（组织或用户）
     * @param name              仓库名
     * @param tag               Git Tag（如 v1.0.0）
     * @param expectedCommitSha 期望 Commit SHA（40 位 hex）
     * @return 校验结果
     */
    TagVerifyResult verifyTag(String namespace, String name, String tag, String expectedCommitSha);

    /** Tag 校验结果。 */
    enum TagVerifyResult {
        /** Tag 存在且 Commit 匹配。 */
        MATCHES,
        /** Gitea 中不存在该 Tag。 */
        MISSING_TAG,
        /** Tag 存在但 Commit 不匹配。 */
        COMMIT_MISMATCH,
        /** Gitea 不可用或查询失败。 */
        UNAVAILABLE
    }
}

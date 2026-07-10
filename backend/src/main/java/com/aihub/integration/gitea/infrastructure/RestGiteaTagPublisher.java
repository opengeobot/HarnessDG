/*
 * 功能: 基于 Gitea REST API 的受保护 Tag 创建适配器。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.integration.gitea.domain.GiteaTagPublisher;
import com.aihub.shared.error.DependencyException;
import com.aihub.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 基于 Gitea REST API 的 Tag 发布器。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时启用：调用 Gitea Tag API 创建 Tag，
 * 并尝试为 Tag 模式注册保护规则。令牌仅用于鉴权，不记录到日志。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class RestGiteaTagPublisher implements GiteaTagPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(RestGiteaTagPublisher.class);

    private final RestClient restClient;
    private final GiteaProperties properties;

    public RestGiteaTagPublisher(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public TagPublishResult createProtectedTag(String repoFullName, String tagName, String commitSha) {
        try {
            String existingCommit = fetchTagCommit(repoFullName, tagName);
            if (existingCommit != null) {
                if (existingCommit.equalsIgnoreCase(commitSha)) {
                    LOG.info("gitea tag already exists with same commit repo={} tag={}", repoFullName, tagName);
                    return new TagPublishResult(TagPublishMode.GITEA_TAG_EXISTS_SAME_COMMIT, tagName, commitSha);
                }
                throw new IllegalStateException(
                        "git tag conflict: " + tagName + " points to different commit");
            }

            createTag(repoFullName, tagName, commitSha);
            protectTag(repoFullName, tagName);

            LOG.info("gitea tag created repo={} tag={}", repoFullName, tagName);
            return new TagPublishResult(TagPublishMode.GITEA_TAG_CREATED, tagName, commitSha);
        } catch (RestClientException ex) {
            LOG.warn("gitea tag creation failed repo={} tag={}", repoFullName, tagName);
            throw new DependencyException(
                    ErrorCode.GITEA_DEPENDENCY_UNAVAILABLE,
                    "failed to create gitea tag",
                    Map.of("repo", repoFullName, "tag", tagName),
                    ex);
        }
    }

    @SuppressWarnings("unchecked")
    private void createTag(String repoFullName, String tagName, String commitSha) {
        Map<String, Object> body = Map.of(
                "tag_name", tagName,
                "target", commitSha,
                "message", "Release " + tagName);
        restClient.post()
                .uri("/api/v1/repos/{full}/tags", repoFullName)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void protectTag(String repoFullName, String tagName) {
        try {
            Map<String, Object> body = Map.of(
                    "name_pattern", tagName,
                    "allowlist_user", List.of(),
                    "allowlist_team", List.of(),
                    "prevent_creation", false);
            restClient.post()
                    .uri("/api/v1/repos/{full}/tag_protections", repoFullName)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // 保护规则失败不阻断发布，但记录以便运维补齐
            LOG.warn("gitea tag protection registration failed repo={} tag={}", repoFullName, tagName);
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchTagCommit(String repoFullName, String tagName) {
        try {
            Map<String, Object> tag = restClient.get()
                    .uri("/api/v1/repos/{full}/tags/{tag}", repoFullName, tagName)
                    .retrieve()
                    .body(Map.class);
            if (tag == null) {
                return null;
            }
            Object commit = tag.get("commit");
            if (commit instanceof Map<?, ?> commitMap) {
                Object sha = commitMap.get("sha");
                return sha == null ? null : String.valueOf(sha);
            }
            return null;
        } catch (RestClientException ex) {
            return null;
        }
    }
}

/*
 * 功能: 基于 Gitea REST API 的 Tag/Commit 校验适配器。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gitea REST Tag 校验实现。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class RestGiteaTagVerificationPort implements GiteaTagVerificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(RestGiteaTagVerificationPort.class);

    private final RestClient restClient;

    public RestGiteaTagVerificationPort(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public TagVerifyResult verifyTag(String namespace, String name, String tag, String expectedCommitSha) {
        try {
            JsonNode body = restClient.get()
                    .uri("/api/v1/repos/{namespace}/{name}/tags/{tag}", namespace, name, tag)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return TagVerifyResult.UNAVAILABLE;
            }
            String actualSha = body.path("commit").path("id").asText("");
            if (actualSha.isEmpty()) {
                return TagVerifyResult.UNAVAILABLE;
            }
            if (expectedCommitSha != null && !expectedCommitSha.equalsIgnoreCase(actualSha)) {
                return TagVerifyResult.COMMIT_MISMATCH;
            }
            return TagVerifyResult.MATCHES;
        } catch (HttpClientErrorException.NotFound ex) {
            return TagVerifyResult.MISSING_TAG;
        } catch (RestClientException ex) {
            LOG.warn("failed to verify gitea tag {}/{} tag={}: {}", namespace, name, tag, ex.getMessage());
            return TagVerifyResult.UNAVAILABLE;
        }
    }
}

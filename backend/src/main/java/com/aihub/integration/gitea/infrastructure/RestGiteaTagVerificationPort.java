/*
 * 功能: 基于 Gitea REST API 的 Tag/Commit 校验适配器。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.version.domain.Manifest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
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
    private static final String MANIFEST_PATH = "aihub/manifest.json";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RestGiteaTagVerificationPort(RestClient.Builder restClientBuilder,
                                        GiteaProperties properties,
                                        ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    @Override
    public ManifestDigestResult fetchManifestDigest(String namespace, String name, String tag) {
        try {
            JsonNode body = restClient.get()
                    .uri("/api/v1/repos/{namespace}/{name}/contents/{path}?ref={tag}",
                            namespace, name, MANIFEST_PATH, tag)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.isMissingNode()) {
                return ManifestDigestResult.unavailable();
            }
            String encoded = body.path("content").asText("");
            if (encoded.isBlank()) {
                return ManifestDigestResult.missingFile();
            }
            String json = new String(Base64.getDecoder().decode(encoded.replace("\n", "")), StandardCharsets.UTF_8);
            Map<String, Object> entries = objectMapper.readValue(json, new TypeReference<>() {});
            String digest = new Manifest(entries).computeDigest();
            return ManifestDigestResult.computed(digest);
        } catch (HttpClientErrorException.NotFound ex) {
            return ManifestDigestResult.missingFile();
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            LOG.warn("failed to fetch gitea manifest {}/{} tag={}: {}", namespace, name, tag, ex.getMessage());
            return ManifestDigestResult.unavailable();
        }
    }
}

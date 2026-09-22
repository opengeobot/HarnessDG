/*
 * 功能: Gitea REST 版本物化适配器——写入 manifest.json 与 .dvc 指针并返回 commit SHA。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.version.domain.VersionMaterializationPort;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 基于 Gitea REST API 的版本物化适配器。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class GiteaVersionMaterializer implements VersionMaterializationPort {

    private static final Logger LOG = LoggerFactory.getLogger(GiteaVersionMaterializer.class);
    private static final String MANIFEST_PATH = "aihub/manifest.json";

    private final RestClient restClient;
    private final GiteaProperties properties;

    public GiteaVersionMaterializer(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public MaterializationResult materialize(MaterializationRequest request) {
        try {
            String lastCommit = null;
            lastCommit = writeFile(request.repoFullName(), MANIFEST_PATH, request.manifestJson(),
                    "feat: materialize manifest for " + request.versionLiteral(), lastCommit);
            for (DvcPointer pointer : request.dvcPointers()) {
                lastCommit = writeFile(request.repoFullName(), pointer.dvcFilePath(), pointer.content(),
                        "feat: add dvc pointer " + pointer.dvcFilePath(), lastCommit);
            }
            if (lastCommit == null) {
                lastCommit = fetchHeadCommit(request.repoFullName());
            }
            LOG.info("version materialized to gitea repo={} commit={}", request.repoFullName(), lastCommit);
            return new MaterializationResult(lastCommit, true, "gitea commit");
        } catch (RestClientException ex) {
            LOG.warn("gitea version materialization failed repo={}", request.repoFullName(), ex);
            return new MaterializationResult(null, false, "gitea materialization failed: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String writeFile(String fullName, String path, String content, String message, String parentCommit) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        body.put("message", message);
        body.put("branch", properties.defaultBranch());
        String sha = fetchFileSha(fullName, path);
        if (sha != null) {
            body.put("sha", sha);
        }
        Map<String, Object> response = restClient.put()
                .uri("/api/v1/repos/{full}/contents/{path}", fullName, path)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response != null && response.get("commit") instanceof Map<?, ?> commitMap) {
            return stringOrNull(commitMap.get("sha"));
        }
        return parentCommit != null ? parentCommit : fetchHeadCommit(fullName);
    }

    @SuppressWarnings("unchecked")
    private String fetchFileSha(String fullName, String path) {
        try {
            Map<String, Object> file = restClient.get()
                    .uri("/api/v1/repos/{full}/contents/{path}?ref={branch}",
                            fullName, path, properties.defaultBranch())
                    .retrieve()
                    .body(Map.class);
            return file == null ? null : stringOrNull(file.get("sha"));
        } catch (RestClientException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchHeadCommit(String fullName) {
        try {
            Map<String, Object> branchInfo = restClient.get()
                    .uri("/api/v1/repos/{full}/branches/{branch}", fullName, properties.defaultBranch())
                    .retrieve()
                    .body(Map.class);
            if (branchInfo != null && branchInfo.get("commit") instanceof Map<?, ?> commitMap) {
                return stringOrNull(commitMap.get("id"));
            }
            return null;
        } catch (RestClientException ex) {
            return null;
        }
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

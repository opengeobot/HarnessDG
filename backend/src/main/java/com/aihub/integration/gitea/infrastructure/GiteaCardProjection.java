/*
 * 功能: 基于 Gitea REST API 的 Card 投影适配器——从仓库读取 asset.yaml/README.md。
 * 时间: 2026-07-04
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.asset.domain.AssetCardProjectionPort;
import com.aihub.shared.error.DependencyException;
import com.aihub.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 基于 Gitea REST API 的 Card 投影适配器。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时启用：从仓库指定 Commit 读取 {@code asset.yaml}
 * 和 {@code README.md} 的内容并返回投影。读取失败时返回空投影（不阻断主流程）。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class GiteaCardProjection implements AssetCardProjectionPort {

    private static final Logger LOG = LoggerFactory.getLogger(GiteaCardProjection.class);
    private static final String ASSET_YAML_PATH = "asset.yaml";
    private static final String README_PATH = "README.md";

    private final RestClient restClient;
    private final GiteaProperties properties;

    public GiteaCardProjection(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public CardProjection fetchCard(String repoFullName, String commit) {
        try {
            String ref = commit != null ? commit : properties.defaultBranch();
            String readme = fetchFileContent(repoFullName, README_PATH, ref);
            String assetYaml = fetchFileContent(repoFullName, ASSET_YAML_PATH, ref);
            String headCommit = commit != null ? commit : fetchHeadCommit(repoFullName, ref);
            return new CardProjection(readme, assetYaml, headCommit);
        } catch (RestClientException ex) {
            LOG.warn("failed to fetch card projection from {}/{}: {}", repoFullName, commit, ex.getMessage());
            return new CardProjection(null, null, commit);
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchFileContent(String repoFullName, String path, String ref) {
        try {
            Map<String, Object> file = restClient.get()
                    .uri("/api/v1/repos/{full}/contents/{path}?ref={ref}", repoFullName, path, ref)
                    .retrieve()
                    .body(Map.class);
            if (file == null || file.get("content") == null) {
                return null;
            }
            String encoded = String.valueOf(file.get("content"));
            return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (RestClientException ex) {
            LOG.debug("file not found: {}/{} at {}", repoFullName, path, ref);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchHeadCommit(String repoFullName, String branch) {
        try {
            Map<String, Object> branchInfo = restClient.get()
                    .uri("/api/v1/repos/{full}/branches/{branch}", repoFullName, branch)
                    .retrieve()
                    .body(Map.class);
            if (branchInfo != null && branchInfo.get("commit") instanceof Map<?, ?> commitMap) {
                return (String) commitMap.get("id");
            }
            return null;
        } catch (RestClientException ex) {
            return null;
        }
    }
}

/*
 * 功能: 基于 Gitea REST API 的资产仓库开通器，创建仓库并写入初始卡片。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.asset.domain.AssetRepositoryProvisioner;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.Visibility;
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
 * 基于 Gitea REST API 的资产仓库开通器。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时启用：在以 {@code namespace} 命名的组织下创建仓库
 * （组织缺失时回退到令牌用户名下），随后写入 {@code asset.yaml} 并以资产卡片覆盖 {@code README.md}。
 * 令牌仅用于鉴权，不记录到日志。失败统一抛出可重试的依赖异常。
 *
 * <p>说明：本适配器在 Compose 集成环境验证；当前 P1 默认走 Noop 开通器。跨系统强一致开通
 * （Saga/Outbox/对账）属 P2 范围。
 */
@Component
@ConditionalOnProperty(name = "aihub.gitea.enabled", havingValue = "true")
public class GiteaAssetRepositoryProvisioner implements AssetRepositoryProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(GiteaAssetRepositoryProvisioner.class);
    private static final String ASSET_YAML_PATH = "asset.yaml";
    private static final String README_PATH = "README.md";

    private final RestClient restClient;
    private final GiteaProperties properties;

    public GiteaAssetRepositoryProvisioner(RestClient.Builder restClientBuilder, GiteaProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .build();
    }

    @Override
    public AssetRepositoryRef provision(ProvisionRequest request) {
        try {
            Map<String, Object> repo = createRepository(request);
            String fullName = String.valueOf(repo.get("full_name"));
            writeFile(fullName, ASSET_YAML_PATH, request.assetYaml(), "chore: initialize asset.yaml", null);
            overwriteReadme(fullName, request.readme());
            return new AssetRepositoryRef(
                    fullName,
                    stringOrNull(repo.get("html_url")),
                    stringOrNull(repo.get("clone_url")));
        } catch (RestClientException ex) {
            LOG.warn("Gitea repository provisioning failed for {}/{}", request.namespace(), request.name());
            throw new DependencyException(
                    ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED,
                    "failed to provision gitea repository",
                    Map.of("namespace", request.namespace(), "name", request.name()),
                    ex);
        }
    }

    private Map<String, Object> createRepository(ProvisionRequest request) {
        Map<String, Object> body = Map.of(
                "name", request.name(),
                "description", request.description() == null ? "" : request.description(),
                "private", request.visibility() != Visibility.PUBLIC,
                "auto_init", true,
                "default_branch", properties.defaultBranch());
        try {
            return postForMap("/api/v1/orgs/" + request.namespace() + "/repos", body);
        } catch (RestClientException orgFailure) {
            // 组织不存在或无权创建时，回退到令牌用户名下创建。
            LOG.info("falling back to user repo creation for namespace {}", request.namespace());
            return postForMap("/api/v1/user/repos", body);
        }
    }

    private void overwriteReadme(String fullName, String readme) {
        String sha = fetchFileSha(fullName, README_PATH);
        writeFile(fullName, README_PATH, readme, "docs: initialize asset card", sha);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String uri, Map<String, Object> body) {
        return restClient.post().uri(uri).body(body).retrieve().body(Map.class);
    }

    private void writeFile(String fullName, String path, String content, String message, String sha) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(safe(content).getBytes(StandardCharsets.UTF_8)));
        body.put("message", message);
        body.put("branch", properties.defaultBranch());
        if (sha != null) {
            body.put("sha", sha);
        }
        restClient.put()
                .uri("/api/v1/repos/{full}/contents/{path}", fullName, path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

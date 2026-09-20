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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 基于 Gitea REST API 的资产仓库开通器。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时启用：在以 {@code namespace} 命名的组织下创建仓库
 * （组织缺失时回退到令牌用户名下），随后写入 {@code asset.yaml} 并以资产卡片覆盖 {@code README.md}。
 * 令牌仅用于鉴权，不记录到日志。失败统一抛出可重试的依赖异常。
 *
 * <p><b>路径拼接</b>：见 {@link GiteaRepoPath}。仓库全名必须拆成两个独立路径段，
 * 整体作为单个模板变量会让 {@code /} 被编码为 {@code %2F} 并导致 404。
 *
 * <p><b>幂等</b>：建仓在「仓库已存在」时回查并复用既有仓库，使「仓库已建成但后续写文件失败」
 * 的任务在重试时能够收敛，而不是被 409 反复拒绝直至重试预算耗尽。真实依赖故障
 * （连接被拒、超时、5xx）仍以可重试依赖异常上抛，不被幂等分支吞掉。
 *
 * <p>说明：本适配器在 Compose 集成环境验证。跨系统强一致开通（Saga/Outbox/对账）属 P2 范围。
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
        this.restClient = GiteaRestClientFactory.create(restClientBuilder, properties);
    }

    @Override
    public AssetRepositoryRef provision(ProvisionRequest request) {
        try {
            Map<String, Object> repo = createRepository(request);
            GiteaRepoPath repoPath = GiteaRepoPath.parse(String.valueOf(repo.get("full_name")));
            writeFile(repoPath, ASSET_YAML_PATH, request.assetYaml(), "chore: initialize asset.yaml");
            writeFile(repoPath, README_PATH, request.readme(), "docs: initialize asset card");
            return new AssetRepositoryRef(
                    repoPath.fullName(),
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

    /**
     * 创建仓库；仓库已存在时复用既有仓库。
     *
     * <p>先尝试在 {@code namespace} 组织下创建，组织不存在（404）时回退到令牌用户名下。
     * 两处都可能因仓库重名返回 409：这表示此前的尝试已成功建仓、只是后续步骤失败，
     * 属于可收敛的重试场景，因此回查并复用，而不是当成失败。
     */
    private Map<String, Object> createRepository(ProvisionRequest request) {
        Map<String, Object> body = Map.of(
                "name", request.name(),
                "description", request.description() == null ? "" : request.description(),
                "private", request.visibility() != Visibility.PUBLIC,
                "auto_init", true,
                "default_branch", properties.defaultBranch());
        try {
            return postForMap("/api/v1/orgs/" + request.namespace() + "/repos", body);
        } catch (HttpClientErrorException.Conflict conflict) {
            // 组织下已有同名仓库：复用，不重复创建。owner 即 namespace。
            LOG.info("gitea repository already exists under org {}; reusing", request.namespace());
            return fetchRepository(request.namespace(), request.name());
        } catch (RestClientException orgFailure) {
            // 组织不存在或无权创建时，回退到令牌用户名下创建。
            LOG.info("falling back to user repo creation for namespace {}", request.namespace());
            try {
                return postForMap("/api/v1/user/repos", body);
            } catch (HttpClientErrorException.Conflict conflict) {
                String owner = currentUserName();
                LOG.info("gitea repository already exists under user {}; reusing", owner);
                return fetchRepository(owner, request.name());
            }
        }
    }

    /** 回查既有仓库；查不到说明并发删除或权限不足，按可重试依赖失败处理。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRepository(String owner, String name) {
        Map<String, Object> repo = restClient.get()
                .uri("/api/v1/repos/{owner}/{repo}", owner, name)
                .retrieve()
                .body(Map.class);
        if (repo == null) {
            throw new DependencyException(
                    ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED,
                    "gitea reported conflict but repository is not readable",
                    Map.of("owner", owner, "name", name),
                    null);
        }
        return repo;
    }

    /** 取当前令牌所属用户名，用于「已在用户名下建仓」时的 owner 推断。 */
    @SuppressWarnings("unchecked")
    private String currentUserName() {
        Map<String, Object> user = restClient.get().uri("/api/v1/user").retrieve().body(Map.class);
        String login = user == null ? null : stringOrNull(user.get("login"));
        if (login == null || login.isBlank()) {
            throw new DependencyException(
                    ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED,
                    "cannot resolve gitea service account login",
                    Map.of(),
                    null);
        }
        return login;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForMap(String uri, Map<String, Object> body) {
        return restClient.post().uri(uri).body(body).retrieve().body(Map.class);
    }

    /**
     * 写入或更新仓库内文件（upsert）。
     *
     * <p>Gitea 的 contents 接口对「新建」与「更新」要求不同的动词与载荷：
     * 更新走 {@code PUT} 且 {@code sha} 必填（缺失时返回 422 {@code [SHA]: Required}），
     * 新建走 {@code POST} 且不得带 {@code sha}（对已存在的路径会返回 409）。
     * 因此这里先读回当前 {@code sha} 决定动词，并在 POST 撞到 409 时按「并发新建」重试为 PUT，
     * 使重试路径（文件已由上一次尝试写入）同样收敛。
     */
    private void writeFile(GiteaRepoPath repoPath, String path, String content, String message) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(safe(content).getBytes(StandardCharsets.UTF_8)));
        body.put("message", message);
        body.put("branch", properties.defaultBranch());

        String sha = fetchFileSha(repoPath, path);
        if (sha != null) {
            putFile(repoPath, path, body, sha);
            return;
        }
        try {
            postFile(repoPath, path, body);
        } catch (HttpClientErrorException.Conflict conflict) {
            // 并发场景：读取 sha 与实际创建之间文件已存在，改按更新处理。
            String concurrentSha = fetchFileSha(repoPath, path);
            if (concurrentSha == null) {
                throw conflict;
            }
            putFile(repoPath, path, body, concurrentSha);
        }
    }

    private void postFile(GiteaRepoPath repoPath, String path, Map<String, Object> body) {
        restClient.post()
                .uri("/api/v1/repos/{owner}/{repo}/contents/{path}",
                        repoPath.owner(), repoPath.repo(), path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void putFile(GiteaRepoPath repoPath, String path, Map<String, Object> body, String sha) {
        body.put("sha", sha);
        restClient.put()
                .uri("/api/v1/repos/{owner}/{repo}/contents/{path}",
                        repoPath.owner(), repoPath.repo(), path)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private String fetchFileSha(GiteaRepoPath repoPath, String path) {
        try {
            Map<String, Object> file = restClient.get()
                    .uri("/api/v1/repos/{owner}/{repo}/contents/{path}?ref={branch}",
                            repoPath.owner(), repoPath.repo(), path, properties.defaultBranch())
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

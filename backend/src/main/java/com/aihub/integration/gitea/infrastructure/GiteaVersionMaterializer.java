/*
 * 功能: Gitea REST 版本物化适配器——写入 manifest.json 与 .dvc 指针并返回 commit SHA。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.shared.error.DependencyException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.version.domain.VersionMaterializationPort;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
        // Gitea 启用时写入失败必须抛出让作业重试；静默降级会伪造 sourceCommit，
        // 破坏「发布版本以 Git Tag + Commit SHA + Digest 三元组寻址」的不变量。
        GiteaRepoPath repoPath = GiteaRepoPath.parse(request.repoFullName());
        String lastCommit = null;
        lastCommit = writeFile(repoPath, MANIFEST_PATH, request.manifestJson(),
                "feat: materialize manifest for " + request.versionLiteral(), lastCommit);
        for (DvcPointer pointer : request.dvcPointers()) {
            lastCommit = writeFile(repoPath, pointer.dvcFilePath(), pointer.content(),
                    "feat: add dvc pointer " + pointer.dvcFilePath(), lastCommit);
        }
        if (lastCommit == null) {
            lastCommit = fetchHeadCommit(repoPath);
        }
        LOG.info("version materialized to gitea repo={} commit={}", request.repoFullName(), lastCommit);
        return new MaterializationResult(lastCommit, true, "gitea commit");
    }

    /**
     * Gitea contents 语义：新建走 POST、更新走 PUT 且 sha 必填。
     * PUT 对不存在的文件返回 422（[SHA]: Required），POST 对已存在的文件返回 409，
     * 因此必须先查 sha 再分派，并对并发创建（409）重取 sha 回退为 PUT。
     */
    private String writeFile(GiteaRepoPath repoPath, String path, String content, String message,
                             String parentCommit) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        body.put("message", message);
        body.put("branch", properties.defaultBranch());
        String sha = fetchFileSha(repoPath, path);
        Map<String, Object> response;
        if (sha != null) {
            response = exchangeWrite(repoPath, path, body, sha, true);
        } else {
            try {
                response = exchangeWrite(repoPath, path, body, null, false);
            } catch (HttpClientErrorException.Conflict conflict) {
                String concurrentSha = fetchFileSha(repoPath, path);
                if (concurrentSha == null) {
                    throw new DependencyException(ErrorCode.GITEA_DEPENDENCY_UNAVAILABLE,
                            "failed to create " + path + " in gitea repo " + repoPath.fullName(),
                            Map.of("repo", repoPath.fullName(), "path", path), conflict);
                }
                response = exchangeWrite(repoPath, path, body, concurrentSha, true);
            }
        }
        String commitSha = commitShaOf(response);
        if (commitSha != null) {
            return commitSha;
        }
        return parentCommit != null ? parentCommit : fetchHeadCommit(repoPath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeWrite(GiteaRepoPath repoPath, String path,
                                              Map<String, Object> body, String sha, boolean update) {
        Map<String, Object> requestSpec = new HashMap<>(body);
        if (update) {
            requestSpec.put("sha", sha);
        }
        try {
            if (update) {
                return restClient.put()
                        .uri("/api/v1/repos/{owner}/{repo}/contents/{path}",
                                repoPath.owner(), repoPath.repo(), path)
                        .body(requestSpec)
                        .retrieve()
                        .body(Map.class);
            }
            return restClient.post()
                    .uri("/api/v1/repos/{owner}/{repo}/contents/{path}",
                            repoPath.owner(), repoPath.repo(), path)
                    .body(requestSpec)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.Conflict conflict) {
            throw conflict;
        } catch (RestClientException ex) {
            throw new DependencyException(ErrorCode.GITEA_DEPENDENCY_UNAVAILABLE,
                    "failed to write " + path + " to gitea repo " + repoPath.fullName(),
                    Map.of("repo", repoPath.fullName(), "path", path), ex);
        }
    }

    private String commitShaOf(Map<String, Object> response) {
        if (response != null && response.get("commit") instanceof Map<?, ?> commitMap) {
            return stringOrNull(commitMap.get("sha"));
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private String fetchHeadCommit(GiteaRepoPath repoPath) {
        try {
            Map<String, Object> branchInfo = restClient.get()
                    .uri("/api/v1/repos/{owner}/{repo}/branches/{branch}",
                            repoPath.owner(), repoPath.repo(), properties.defaultBranch())
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

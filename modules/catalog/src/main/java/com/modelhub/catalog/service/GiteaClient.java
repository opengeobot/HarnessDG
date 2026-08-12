package com.modelhub.catalog.service;

import com.modelhub.catalog.config.GiteaProperties;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Gitea HTTP 客户端（05 §2/§8）：所有写操作幂等（先查存在性再创建，05 §10.2
 * 以「Gitea 仓库名 + repository publicId」为稳定幂等键）。
 */
@Component
public class GiteaClient {

    private static final Logger log = LoggerFactory.getLogger(GiteaClient.class);

    /** Gitea 侧不可恢复的失败（如名称非法），重试无意义。 */
    public static class GiteaException extends RuntimeException {
        private final int status;

        public GiteaException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() { return status; }
    }

    private final RestClient rest;

    public GiteaClient(GiteaProperties props) {
        // SimpleClientHttpRequestFactory（HttpURLConnection）不支持 PATCH，
        // 而删除/恢复 Saga 依赖 PATCH 归档与可见性切换（05 §9.2），改用 httpclient5
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeoutMs()))
                .setSocketTimeout(Timeout.ofMilliseconds(props.getReadTimeoutMs()))
                .build());
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().setConnectionManager(cm).build());
        String credentials = Base64.getEncoder().encodeToString(
                (props.getUsername() + ":" + props.getPassword()).getBytes(StandardCharsets.UTF_8));
        this.rest = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .build();
    }

    /** 幂等确保组织存在：每个 namespace 建同名 Gitea org。 */
    public void ensureOrganization(String slug, String displayName) {
        if (orgExists(slug)) {
            return;
        }
        try {
            rest.post().uri("/api/v1/orgs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", slug, "full_name", displayName == null ? slug : displayName,
                            "visibility", "public"))
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 并发创建竞争：已存在视为成功
            if (e.getStatusCode().value() == 409 || e.getStatusCode().value() == 422) {
                if (orgExists(slug)) {
                    return;
                }
            }
            throw new GiteaException(e.getStatusCode().value(), "创建 Gitea 组织失败: " + slug);
        }
    }

    public boolean orgExists(String slug) {
        try {
            rest.get().uri("/api/v1/orgs/{org}", slug).retrieve().toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw new GiteaException(e.getStatusCode().value(), "查询 Gitea 组织失败: " + slug);
        }
    }

    public record GiteaRepo(long id, String fullName, String defaultBranch) {}

    /** 幂等建仓库：返回外部仓库 id 与默认分支投影。 */
    public GiteaRepo ensureRepository(String org, String name, boolean isPrivate) {
        GiteaRepo existing = getRepository(org, name);
        if (existing != null) {
            return existing;
        }
        try {
            Map<String, Object> resp = rest.post().uri("/api/v1/orgs/{org}/repos", org)
                    .contentType(MediaType.APPLICATION_JSON)
                    // auto_init 建初始 commit：空仓库无任何分支时 Contents API 无法写入，
                    // 后续 putFile 以幂等更新方式覆盖种子文件
                    .body(Map.of("name", name, "private", isPrivate,
                            "auto_init", true, "default_branch", "main"))
                    .retrieve().body(Map.class);
            return toRepo(resp);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                GiteaRepo raced = getRepository(org, name);
                if (raced != null) {
                    return raced;
                }
            }
            throw new GiteaException(e.getStatusCode().value(), "创建 Gitea 仓库失败: " + org + "/" + name);
        }
    }

    public GiteaRepo getRepository(String org, String name) {
        try {
            Map<String, Object> resp = rest.get().uri("/api/v1/repos/{org}/{repo}", org, name)
                    .retrieve().body(Map.class);
            return toRepo(resp);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new GiteaException(e.getStatusCode().value(), "查询 Gitea 仓库失败: " + org + "/" + name);
        }
    }

    private GiteaRepo toRepo(Map<String, Object> resp) {
        if (resp == null) {
            throw new GiteaException(502, "Gitea 返回空响应");
        }
        long id = ((Number) resp.get("id")).longValue();
        String fullName = (String) resp.get("full_name");
        String branch = resp.get("default_branch") == null ? "main" : (String) resp.get("default_branch");
        return new GiteaRepo(id, fullName, branch);
    }

    /**
     * 幂等写入初始文件（README/LICENSE/manifest）：已存在则携 sha 更新。
     * Gitea Contents API 更新既有文件时强制要求当前文件 sha（否则 422 [SHA]: Required）。
     */
    public void putFile(String org, String repo, String path, String content, String message, String branch) {
        String base64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String existingSha = fileSha(org, repo, path);
        try {
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("message", message);
            body.put("content", base64);
            body.put("branch", branch);
            if (existingSha != null) {
                body.put("sha", existingSha);
                rest.put().uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve().toBodilessEntity();
            } else {
                rest.post().uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve().toBodilessEntity();
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new GiteaException(e.getStatusCode().value(),
                    "写入 Gitea 文件失败: " + org + "/" + repo + "/" + path
                            + " status=" + e.getStatusCode() + " body=" + e.getResponseBodyAsString());
        }
    }

    /** 返回既有文件 sha；不存在返回 null。 */
    private String fileSha(String org, String repo, String path) {
        try {
            Map<String, Object> resp = rest.get().uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path)
                    .retrieve().body(Map.class);
            if (resp != null && resp.get("sha") != null) {
                return resp.get("sha").toString();
            }
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new GiteaException(e.getStatusCode().value(), "查询 Gitea 文件失败: " + path);
        }
    }

    /** 仓库删除 Saga：先置 private 再 archive（05 §9.2 第 2 步），保留 Git 历史。 */
    public void archiveRepository(String org, String repo) {
        patchRepo(org, repo, Map.of("private", true, "archived", true));
    }

    /** restore：解除 archive 并按目标可见性恢复（05 §9.2 第 3 步）。 */
    public void unarchiveRepository(String org, String repo, boolean isPrivate) {
        patchRepo(org, repo, Map.of("archived", false, "private", isPrivate));
    }

    /** purge 阶段才允许物理删除（05 §9.2 第 5 步）。 */
    public void deleteRepository(String org, String repo) {
        try {
            rest.delete().uri("/api/v1/repos/{org}/{repo}", org, repo)
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return;
            }
            throw new GiteaException(e.getStatusCode().value(), "删除 Gitea 仓库失败: " + org + "/" + repo);
        }
    }

    private void patchRepo(String org, String repo, Map<String, Object> body) {
        try {
            rest.patch().uri("/api/v1/repos/{org}/{repo}", org, repo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new GiteaException(e.getStatusCode().value(), "更新 Gitea 仓库失败: " + org + "/" + repo);
        }
    }

    /** 读取指定分支最新 commit sha（provisioning 回填投影用）。 */
    public String headCommitSha(String org, String repo, String branch) {
        try {
            Map<String, Object> resp = rest.get()
                    .uri("/api/v1/repos/{org}/{repo}/branches/{branch}", org, repo, branch)
                    .retrieve().body(Map.class);
            if (resp != null && resp.get("commit") instanceof Map<?, ?> commit) {
                Object id = commit.get("id");
                return id == null ? null : id.toString();
            }
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("读取 Gitea 分支失败 {}/{} {}: {}", org, repo, branch, e.getStatusCode());
            return null;
        }
    }
}

package com.modelhub.catalog.service;

import com.modelhub.catalog.config.GiteaProperties;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
            awaitBranchReady(org, name, existing.defaultBranch());
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
            GiteaRepo created = toRepo(resp);
            awaitBranchReady(org, name, created.defaultBranch());
            return created;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                GiteaRepo raced = getRepository(org, name);
                if (raced != null) {
                    awaitBranchReady(org, name, raced.defaultBranch());
                    return raced;
                }
            }
            throw new GiteaException(e.getStatusCode().value(), "创建 Gitea 仓库失败: " + org + "/" + name);
        }
    }

    /**
     * auto_init 的初始 commit 在 Gitea 内部异步落盘：repo 创建 API 返回后默认分支
     * 可能短暂不存在，紧随其后的 putFile 会 404 "branch does not exist"，触发整条
     * provisioning 退避重试链（重复副作用窗口）。创建后显式等待分支就绪。
     */
    private void awaitBranchReady(String org, String repo, String branch) {
        if (branch == null || headCommitSha(org, repo, branch) != null) {
            return;
        }
        long deadline = System.currentTimeMillis() + 5000;
        log.info("等待 Gitea 初始分支就绪 {}/{} branch={}", org, repo, branch);
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (headCommitSha(org, repo, branch) != null) {
                return;
            }
        }
        log.warn("等待 Gitea 初始分支就绪超时 {}/{} branch={}", org, repo, branch);
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
        putFileBase64(org, repo, path, base64, message, branch);
    }

    public record PutFileResult(String commitSha, String fileSha) {}

    /** 写入 base64 内容并返回提交结果（commit sha + 新 blob sha）；幂等携既有 sha 更新。 */
    public PutFileResult putFileBase64(String org, String repo, String path, String contentBase64,
                                       String message, String branch) {
        String existingSha = fileSha(org, repo, path, branch);
        try {
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("message", message);
            body.put("content", contentBase64);
            body.put("branch", branch);
            if (existingSha != null) {
                body.put("sha", existingSha);
            }
            Map<String, Object> resp = (existingSha != null
                    ? rest.put().uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path)
                    : rest.post().uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(Map.class);
            String commitSha = null;
            String fileSha = null;
            if (resp != null) {
                if (resp.get("commit") instanceof Map<?, ?> commit) {
                    Object sha = commit.get("sha");
                    commitSha = sha == null ? null : sha.toString();
                }
                if (resp.get("content") instanceof Map<?, ?> contentMap) {
                    Object sha = contentMap.get("sha");
                    fileSha = sha == null ? null : sha.toString();
                }
            }
            return new PutFileResult(commitSha, fileSha);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new GiteaException(e.getStatusCode().value(),
                    "写入 Gitea 文件失败: " + org + "/" + repo + "/" + path
                            + " status=" + e.getStatusCode() + " body=" + e.getResponseBodyAsString());
        }
    }

    /** 幂等删除文件：不存在视为已删除返回 false；存在则提交删除 commit 返回 true。 */
    public boolean deleteFile(String org, String repo, String path, String message, String branch) {
        String existingSha = fileSha(org, repo, path, branch);
        if (existingSha == null) {
            return false;
        }
        try {
            // Gitea DeleteFile 需要 body（sha/message/branch），rest.delete() 不携带 body，改用 method()
            rest.method(HttpMethod.DELETE)
                    .uri("/api/v1/repos/{org}/{repo}/contents/{path}", org, repo, path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", message, "sha", existingSha, "branch", branch))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw new GiteaException(e.getStatusCode().value(),
                    "删除 Gitea 文件失败: " + org + "/" + repo + "/" + path
                            + " status=" + e.getStatusCode() + " body=" + e.getResponseBodyAsString());
        }
    }

    public record GiteaBranch(String name, String headCommitSha, boolean isProtected) {}

    /** 列出仓库分支（04 Artifacts listBranches）。 */
    public List<GiteaBranch> listBranches(String org, String repo) {
        try {
            List<Map<String, Object>> resp = rest.get().uri("/api/v1/repos/{org}/{repo}/branches", org, repo)
                    .retrieve().body(List.class);
            List<GiteaBranch> out = new ArrayList<>();
            if (resp == null) {
                return out;
            }
            for (Map<String, Object> b : resp) {
                String name = (String) b.get("name");
                boolean isProtected = Boolean.TRUE.equals(b.get("protected"));
                String head = null;
                if (b.get("commit") instanceof Map<?, ?> commit) {
                    Object id = commit.get("id");
                    head = id == null ? null : id.toString();
                }
                out.add(new GiteaBranch(name, head, isProtected));
            }
            return out;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new GiteaException(e.getStatusCode().value(), "查询 Gitea 分支列表失败: " + org + "/" + repo);
        }
    }

    public record GiteaCommit(String sha, String message, String author, OffsetDateTime committedAt) {}

    /** 分支提交历史（04 Artifacts listCommits，Gitea 侧分页）。 */
    public List<GiteaCommit> listCommits(String org, String repo, String branch, int page, int limit) {
        try {
            List<Map<String, Object>> resp = rest.get()
                    .uri("/api/v1/repos/{org}/{repo}/commits?sha={branch}&page={page}&limit={limit}",
                            org, repo, branch, page, limit)
                    .retrieve().body(List.class);
            List<GiteaCommit> out = new ArrayList<>();
            if (resp == null) {
                return out;
            }
            for (Map<String, Object> c : resp) {
                String sha = c.get("sha") == null ? null : c.get("sha").toString();
                String message = null;
                String author = null;
                OffsetDateTime committedAt = null;
                if (c.get("commit") instanceof Map<?, ?> commit) {
                    message = commit.get("message") == null ? null : commit.get("message").toString();
                    if (commit.get("author") instanceof Map<?, ?> a) {
                        author = a.get("name") == null ? null : a.get("name").toString();
                    }
                    if (commit.get("committer") instanceof Map<?, ?> cm
                            && cm.get("date") != null) {
                        try {
                            committedAt = OffsetDateTime.parse(cm.get("date").toString());
                        } catch (Exception ignored) {
                            // Gitea 日期格式异常时置空，不阻断列表
                        }
                    }
                }
                out.add(new GiteaCommit(sha, message, author, committedAt));
            }
            return out;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new GiteaException(e.getStatusCode().value(), "查询 Gitea 提交历史失败: " + org + "/" + repo);
        }
    }

    /** 读取指定 ref 的文件原始字节（05 §6.3 git 下载与预览取值）。 */
    public byte[] rawFile(String org, String repo, String ref, String path) {
        try {
            return rest.get().uri("/api/v1/repos/{org}/{repo}/raw/{path}?ref={ref}", org, repo, path, ref)
                    .retrieve().body(byte[].class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new GiteaException(e.getStatusCode().value(), "读取 Gitea 原始文件失败: " + path);
        }
    }

    /** 返回既有文件 sha；不存在返回 null（ref 缺省用默认分支）。 */
    public String fileSha(String org, String repo, String path, String ref) {
        try {
            Map<String, Object> resp = rest.get()
                    .uri(ref == null
                            ? "/api/v1/repos/{org}/{repo}/contents/{path}"
                            : "/api/v1/repos/{org}/{repo}/contents/{path}?ref={ref}",
                            ref == null ? new Object[] {org, repo, path} : new Object[] {org, repo, path, ref})
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

    /** 分支 head 提交的 message（至少一次投递的认领恢复用）；无提交返回 null。 */
    public String headCommitMessage(String org, String repo, String branch) {
        List<GiteaCommit> commits = listCommits(org, repo, branch, 1, 1);
        return commits.isEmpty() ? null : commits.get(0).message();
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

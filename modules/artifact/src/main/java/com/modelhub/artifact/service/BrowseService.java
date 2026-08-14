package com.modelhub.artifact.service;

import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.VisitRecorder;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 仓库 Git 投影只读查询（04 Artifacts）：branches/commits 直接读 Gitea（Git 真相源，05 §1），
 * files 清单以 PG file_versions 为真相源并附 resolvedCommitSha（04 FilePageEnvelope）。
 */
@Service
public class BrowseService {

    // ---------- 契约视图 ----------

    public record BranchView(String name, String headCommitSha, boolean isDefault, boolean isProtected) {}

    public record BranchListData(List<BranchView> items, String nextCursor) {}

    public record CommitView(String sha, String message, String author, OffsetDateTime committedAt) {}

    public record CommitPageData(List<CommitView> items, String nextCursor) {}

    /** FileEntry：status 投影 sync_error 对外映射为 failed（契约枚举不含 sync_error）。 */
    public record FileEntryView(UUID id, String path, long sizeBytes, String contentType,
                                String contentSource, String commitSha, String status) {}

    public record FilePageData(List<FileEntryView> items, String resolvedCommitSha, String nextCursor) {}

    private final RepositoryAccessFacade access;
    private final GitBindingRepository gitBindings;
    private final FileVersionRepository fileVersions;
    private final GiteaClient gitea;
    private final VisitRecorder visitRecorder;

    public BrowseService(RepositoryAccessFacade access, GitBindingRepository gitBindings,
                         FileVersionRepository fileVersions, GiteaClient gitea, VisitRecorder visitRecorder) {
        this.access = access;
        this.gitBindings = gitBindings;
        this.fileVersions = fileVersions;
        this.gitea = gitea;
        this.visitRecorder = visitRecorder;
    }

    public BranchListData listBranches(CurrentPrincipal actor, UUID repoId, String cursor, int limit) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        List<GiteaClient.GiteaBranch> branches =
                gitea.listBranches(binding.getExternalNamespace(), binding.getExternalName());
        int from = parseCursor(cursor);
        List<BranchView> items = new ArrayList<>();
        for (int i = from; i < branches.size() && items.size() < limit; i++) {
            GiteaClient.GiteaBranch b = branches.get(i);
            items.add(new BranchView(b.name(), b.headCommitSha(),
                    b.name().equals(ctx.repo().getDefaultBranch()), b.isProtected()));
        }
        String next = from + items.size() < branches.size() ? String.valueOf(from + items.size()) : null;
        return new BranchListData(items, next);
    }

    public CommitPageData listCommits(CurrentPrincipal actor, UUID repoId, String branch,
                                      String cursor, int limit) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        String target = branch == null || branch.isBlank() ? ctx.repo().getDefaultBranch() : branch;
        int page = Math.max(1, parseCursor(cursor) + 1);
        List<GiteaClient.GiteaCommit> commits =
                gitea.listCommits(binding.getExternalNamespace(), binding.getExternalName(), target, page, limit);
        List<CommitView> items = commits.stream()
                .map(c -> new CommitView(c.sha(), c.message(), c.author(), c.committedAt()))
                .toList();
        String next = commits.size() == limit ? String.valueOf(page) : null;
        return new CommitPageData(items, next);
    }

    /** 文件清单：PG file_versions 为真相源；ETag 用 resolvedCommitSha（04 FilePageEnvelope）。
     *  授权成功后投递 visit（06 §7.2）。 */
    public FilePageData listFiles(CurrentPrincipal actor, UUID repoId, String branch, String pathPrefix,
                                  String cursor, int limit, String requestIp) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        visitRecorder.record(actor, ctx.repo().getId(), requestIp);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        String target = branch == null || branch.isBlank() ? ctx.repo().getDefaultBranch() : branch;
        String head = gitea.headCommitSha(binding.getExternalNamespace(), binding.getExternalName(), target);
        if (head == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "分支不存在: " + target);
        }
        long fromId = parseCursorLong(cursor);
        List<FileVersionEntity> all = fileVersions
                .findByRepositoryIdAndBranchOrderByPathAsc(ctx.repo().getId(), target);
        List<FileEntryView> items = new ArrayList<>();
        String next = null;
        for (FileVersionEntity fv : all) {
            if (!"active".equals(fv.getStatus()) && !"staging".equals(fv.getStatus())) {
                continue;
            }
            if (pathPrefix != null && !pathPrefix.isBlank() && !fv.getPath().startsWith(pathPrefix)) {
                continue;
            }
            if (fv.getId() <= fromId) {
                continue;
            }
            if (items.size() >= limit) {
                next = String.valueOf(items.get(items.size() - 1).id());
                break;
            }
            items.add(new FileEntryView(fv.getPublicId(), fv.getPath(), fv.getSizeBytes(),
                    fv.getContentType(), fv.getContentSource(), fv.getCommitSha(), projectStatus(fv.getStatus())));
        }
        return new FilePageData(items, head, next);
    }

    /** 解析 Gitea 绑定；未就绪返回 503（provisioning 未完成）。 */
    public GitBindingEntity requireBinding(Long repositoryId) {
        return gitBindings.findByRepositoryId(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "仓库 Git 绑定未就绪"));
    }

    private static String projectStatus(String status) {
        return "sync_error".equals(status) ? "failed" : status;
    }

    private static int parseCursor(String cursor) {
        try {
            return cursor == null || cursor.isBlank() ? 0 : Integer.parseInt(cursor);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("cursor 非法", List.of(new ApiException.Detail("cursor", "invalid")));
        }
    }

    private static long parseCursorLong(String cursor) {
        try {
            return cursor == null || cursor.isBlank() ? 0L : Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("cursor 非法", List.of(new ApiException.Detail("cursor", "invalid")));
        }
    }
}

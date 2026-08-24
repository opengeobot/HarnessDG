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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** 分支清单：Gitea 全量投影本地分页；cursor 为不透明页号（BrowseCursor，ADR-003）。 */
    public BranchListData listBranches(CurrentPrincipal actor, UUID repoId, String cursor, int limit) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        requireGatedAccess(ctx);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        int pageIndex = BrowseCursor.decodePage(cursor);
        List<GiteaClient.GiteaBranch> branches =
                gitea.listBranches(binding.getExternalNamespace(), binding.getExternalName());
        long start = (long) pageIndex * limit;
        List<BranchView> items = new ArrayList<>();
        for (int i = (int) Math.min(start, branches.size()); i < branches.size() && items.size() < limit; i++) {
            GiteaClient.GiteaBranch b = branches.get(i);
            items.add(new BranchView(b.name(), b.headCommitSha(),
                    b.name().equals(ctx.repo().getDefaultBranch()), b.isProtected()));
        }
        String next = start + items.size() < branches.size() ? BrowseCursor.encodePage(pageIndex + 1) : null;
        return new BranchListData(items, next);
    }

    /** 提交历史：Gitea 侧页号分页；cursor 为不透明页号（BrowseCursor，ADR-003）。 */
    public CommitPageData listCommits(CurrentPrincipal actor, UUID repoId, String branch,
                                      String cursor, int limit) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        requireGatedAccess(ctx);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        int pageIndex = BrowseCursor.decodePage(cursor);
        String target = branch == null || branch.isBlank() ? ctx.repo().getDefaultBranch() : branch;
        List<GiteaClient.GiteaCommit> commits =
                gitea.listCommits(binding.getExternalNamespace(), binding.getExternalName(),
                        target, pageIndex + 1, limit);
        List<CommitView> items = commits.stream()
                .map(c -> new CommitView(c.sha(), c.message(), c.author(), c.committedAt()))
                .toList();
        String next = commits.size() == limit ? BrowseCursor.encodePage(pageIndex + 1) : null;
        return new CommitPageData(items, next);
    }

    /** 文件清单：PG file_versions 为真相源；ETag 用 resolvedCommitSha（04 FilePageEnvelope）。
     *  授权成功后投递 visit（06 §7.2）。
     *  ref（契约 listFiles）：分支名优先；40 位 sha 按默认分支历史解析到该提交时刻；
     *  其余值（含 tag，Gitea 客户端无 tag 查询）与不存在分支一致返回 404。 */
    public FilePageData listFiles(CurrentPrincipal actor, UUID repoId, String branch, String ref,
                                  String pathPrefix, String cursor, int limit, String requestIp) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        requireGatedAccess(ctx);
        visitRecorder.record(actor, ctx.repo().getId(), requestIp);
        GitBindingEntity binding = requireBinding(ctx.repo().getId());
        String namespace = binding.getExternalNamespace();
        String repoName = binding.getExternalName();
        String defaultBranch = ctx.repo().getDefaultBranch();
        String target = defaultBranch;
        String head;
        Set<String> commitsUpToRef = null;
        if (ref != null && !ref.isBlank()) {
            String branchHead = gitea.headCommitSha(namespace, repoName, ref);
            if (branchHead != null) {
                target = ref;
                head = branchHead;
            } else if (COMMIT_SHA_PATTERN.matcher(ref).matches()) {
                head = ref.toLowerCase(Locale.ROOT);
                commitsUpToRef = commitsUpTo(namespace, repoName, defaultBranch, head);
                if (commitsUpToRef == null) {
                    throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "提交不存在: " + ref);
                }
            } else {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "引用不存在（仅支持分支名或 40 位 commit sha）: " + ref);
            }
        } else {
            target = branch == null || branch.isBlank() ? defaultBranch : branch;
            head = gitea.headCommitSha(namespace, repoName, target);
            if (head == null) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "分支不存在: " + target);
            }
        }
        BrowseCursor.FileKey lastKey = BrowseCursor.decodeFileKey(cursor);
        List<FileVersionEntity> all = fileVersions
                .findByRepositoryIdAndBranchOrderByPathAsc(ctx.repo().getId(), target);
        // keyset 必须匹配排序（04 §6.2）：path ASC + id ASC 稳定 tie-breaker；
        // DB 查询仅按 path 排序，内存补 id 次序保证 (path,id) 全序，续读无重复/遗漏
        List<FileVersionEntity> ordered = all.stream()
                .sorted(Comparator.comparing(FileVersionEntity::getPath)
                        .thenComparing(FileVersionEntity::getId))
                .toList();
        List<FileEntryView> items = new ArrayList<>();
        String next = null;
        FileVersionEntity lastEmitted = null;
        for (FileVersionEntity fv : ordered) {
            if (!"active".equals(fv.getStatus()) && !"staging".equals(fv.getStatus())) {
                continue;
            }
            if (commitsUpToRef != null && !commitsUpToRef.contains(fv.getCommitSha())) {
                continue;
            }
            if (pathPrefix != null && !pathPrefix.isBlank() && !fv.getPath().startsWith(pathPrefix)) {
                continue;
            }
            if (lastKey != null && compareKey(fv, lastKey) <= 0) {
                continue;
            }
            if (items.size() >= limit) {
                next = BrowseCursor.encodeFileKey(lastEmitted.getPath(), lastEmitted.getId());
                break;
            }
            items.add(new FileEntryView(fv.getPublicId(), fv.getPath(), fv.getSizeBytes(),
                    fv.getContentType(), fv.getContentSource(), fv.getCommitSha(), projectStatus(fv.getStatus())));
            lastEmitted = fv;
        }
        return new FilePageData(items, head, next);
    }

    /** gated 仓库：无有效 grant 禁止浏览（02 §4：public+gated → 文件/提交需已批准用户）。 */
    private void requireGatedAccess(RepoContext ctx) {
        if (ctx.gatedEnabled() && !ctx.hasActiveGrant()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "该仓库内容受 gated 策略保护，需先获得访问授权");
        }
    }

    /** 完整 commit sha 形态（40 位十六进制）。 */
    private static final Pattern COMMIT_SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");

    private static final int COMMIT_SCAN_PAGE_SIZE = 50;
    private static final int COMMIT_SCAN_MAX_PAGES = 20;

    /** 默认分支历史（新→旧）中 ref（含）及其之前的 sha 集合，即该提交时刻已存在的全部提交；
     *  ref 不在历史中（或历史超出扫描上限）返回 null。 */
    private Set<String> commitsUpTo(String org, String repo, String branch, String ref) {
        List<String> ordered = new ArrayList<>();
        for (int page = 1; page <= COMMIT_SCAN_MAX_PAGES; page++) {
            List<GiteaClient.GiteaCommit> commits =
                    gitea.listCommits(org, repo, branch, page, COMMIT_SCAN_PAGE_SIZE);
            for (GiteaClient.GiteaCommit c : commits) {
                if (c.sha() != null) {
                    ordered.add(c.sha());
                }
            }
            if (commits.size() < COMMIT_SCAN_PAGE_SIZE) {
                break;
            }
        }
        int idx = ordered.indexOf(ref);
        return idx < 0 ? null : new HashSet<>(ordered.subList(idx, ordered.size()));
    }

    /** 解析 Gitea 绑定；未就绪返回 503（provisioning 未完成）。 */
    public GitBindingEntity requireBinding(Long repositoryId) {
        return gitBindings.findByRepositoryId(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "仓库 Git 绑定未就绪"));
    }

    private static String projectStatus(String status) {
        return "sync_error".equals(status) ? "failed" : status;
    }

    /** (path,id) keyset 值比较，与 listFiles 的排序一致（path ASC, id ASC）。 */
    private static int compareKey(FileVersionEntity fv, BrowseCursor.FileKey key) {
        int byPath = fv.getPath().compareTo(key.path());
        return byPath != 0 ? byPath : Long.compare(fv.getId(), key.id());
    }
}

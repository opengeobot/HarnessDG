package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.access.RepositoryAccessFacade.VisibleScope;
import com.modelhub.catalog.config.CatalogProperties;
import com.modelhub.catalog.domain.GatedPolicyEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.RepoStatsEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.domain.ResourceTypeEntity;
import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.catalog.domain.SchemaVersionId;
import com.modelhub.catalog.repo.GatedPolicyRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.RepoStatsRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.repo.ResourceTypeRepository;
import com.modelhub.catalog.repo.SchemaVersionRepository;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.domain.OrganizationMembershipEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.OrganizationMembershipRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuditService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import com.modelhub.shared.web.ETags;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 仓库目录服务（03/04/05 章）：
 * - 创建幂等 + provisioning Saga 入口（Outbox RepositoryProvisionRequested，05 §8）；
 * - PATCH 条件更新（If-Match，04 §10）；
 * - DELETE/restore 状态机（05 §9.2）；
 * - 列表搜索 page 模式 + sort 受控枚举 + 授权过滤（04 §6）。
 */
@Service
public class CatalogService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$");
    private static final Set<String> SORT_KEYS =
            Set.of("relevance-v1", "updatedAt-desc", "downloads-desc", "likes-desc", "hot-desc");
    private static final Map<String, String> SORT_ORDER_SQL = Map.of(
            "relevance-v1", "(COALESCE(s.downloads,0)*2 + COALESCE(s.likes,0)*3 "
                    + "+ COALESCE(s.favorites,0)*2 + COALESCE(s.visits,0)*0.1) DESC, "
                    + "r.updated_at DESC, r.public_id DESC",
            "updatedAt-desc", "r.updated_at DESC, r.public_id DESC",
            "downloads-desc", "COALESCE(s.downloads,0) DESC, r.updated_at DESC, r.public_id DESC",
            "likes-desc", "COALESCE(s.likes,0) DESC, r.updated_at DESC, r.public_id DESC",
            "hot-desc", "(COALESCE(s.visits,0) + 3*COALESCE(s.downloads,0) + 2*COALESCE(s.likes,0)) DESC, "
                    + "r.updated_at DESC, r.public_id DESC");

    /** 创建请求（契约 CreateRepositoryRequest）。 */
    public record CreateRepoCmd(UUID namespaceId, String type, Integer metadataSchemaVersion, String name,
                                String displayName, String description, String visibility,
                                Boolean gated, String metadataJson) {}

    /** 更新请求（契约 UpdateRepositoryRequest，字段可空表示不修改）。 */
    public record UpdateRepoCmd(String displayName, String description, String visibility,
                                Boolean gated, Integer metadataSchemaVersion, String metadataJson,
                                boolean hasDisplayName, boolean hasDescription) {}

    public record StatsView(long likes, long favorites, long downloads, long visits, long fileCount) {}

    /** 契约 Repository schema。 */
    public record RepoView(UUID id, String type, String namespace, String name, String displayName,
                           String description, String visibility, boolean gated, StatsView stats,
                           String lifecycleStatus, String restoreTargetStatus, OffsetDateTime deletedAt,
                           OffsetDateTime retentionUntil, Object metadata, int metadataSchemaVersion,
                           long version, OffsetDateTime createdAt, OffsetDateTime updatedAt, String etag) {}

    /** 契约 Job schema（04 §5 最小字段集）。 */
    public record JobView(UUID id, String type, String status, OffsetDateTime createdAt) {}

    private final RepositoryRepository repositories;
    private final ResourceTypeRepository resourceTypes;
    private final SchemaVersionRepository schemaVersions;
    private final GatedPolicyRepository gatedPolicies;
    private final RepoStatsRepository repoStats;
    private final JobRepository jobs;
    private final NamespaceRepository namespaces;
    private final OrganizationMembershipRepository memberships;
    private final RepositoryAccessFacade access;
    private final MetadataValidator metadataValidator;
    private final ProfileProjector projector;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final CatalogProperties props;
    private final VisitRecorder visitRecorder;

    @PersistenceContext
    private EntityManager em;

    public CatalogService(RepositoryRepository repositories, ResourceTypeRepository resourceTypes,
                          SchemaVersionRepository schemaVersions, GatedPolicyRepository gatedPolicies,
                          RepoStatsRepository repoStats, JobRepository jobs, NamespaceRepository namespaces,
                          OrganizationMembershipRepository memberships, RepositoryAccessFacade access,
                          MetadataValidator metadataValidator, ProfileProjector projector,
                          OutboxService outbox, AuditService audit, ObjectMapper objectMapper,
                          CatalogProperties props, VisitRecorder visitRecorder) {
        this.repositories = repositories;
        this.resourceTypes = resourceTypes;
        this.schemaVersions = schemaVersions;
        this.gatedPolicies = gatedPolicies;
        this.repoStats = repoStats;
        this.jobs = jobs;
        this.namespaces = namespaces;
        this.memberships = memberships;
        this.access = access;
        this.metadataValidator = metadataValidator;
        this.projector = projector;
        this.outbox = outbox;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.props = props;
        this.visitRecorder = visitRecorder;
    }

    // ---------- 创建（05 §8 Saga 入口） ----------

    @Transactional
    public RepoView create(CurrentPrincipal actor, CreateRepoCmd cmd) {
        NamespaceEntity ns = namespaces.findByPublicId(cmd.namespaceId()).orElseThrow(
                () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "namespace 不存在"));
        checkCreatePermission(ns, actor);

        ResourceTypeEntity type = resourceTypes.findById(cmd.type()).orElseThrow(
                () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "未知资源类型: " + cmd.type()));
        if (!"active".equals(type.getStatus())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "资源类型不可用: " + cmd.type());
        }
        SchemaVersionEntity schema = schemaVersions
                .findById(new SchemaVersionId(cmd.type(), cmd.metadataSchemaVersion()))
                .orElseThrow(() -> new ApiException(ErrorCode.METADATA_SCHEMA_INVALID,
                        "metadataSchemaVersion 未注册",
                        List.of(new ApiException.Detail("metadataSchemaVersion", "unknown_version"))));
        if (!"published".equals(schema.getStatus())) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "metadataSchemaVersion 未发布",
                    List.of(new ApiException.Detail("metadataSchemaVersion", "not_published")));
        }

        String name = cmd.name() == null ? "" : cmd.name().trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "仓库名称格式非法",
                    List.of(new ApiException.Detail("name", "invalid_format")));
        }
        boolean gated = Boolean.TRUE.equals(cmd.gated());
        if (gated && !"public".equals(cmd.visibility())) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "v1 只允许 public 仓库启用 gated",
                    List.of(new ApiException.Detail("gated", "requires_public")));
        }
        if ("organization".equals(cmd.visibility()) && !"organization".equals(ns.getNamespaceType())) {
            throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "organization 可见性仅适用于组织 namespace",
                    List.of(new ApiException.Detail("visibility", "requires_organization_namespace")));
        }

        String metadataJson = cmd.metadataJson() == null ? "{}" : cmd.metadataJson();
        JsonNode validatedMetadata = metadataValidator.validate(schema, metadataJson);

        OffsetDateTime now = OffsetDateTime.now();
        RepositoryEntity repo = new RepositoryEntity();
        repo.setPublicId(PublicIds.next());
        repo.setNamespaceId(ns.getId());
        repo.setResourceType(cmd.type());
        repo.setName(name);
        repo.setNormalizedName(name.toLowerCase(Locale.ROOT));
        repo.setDisplayName(cmd.displayName());
        repo.setDescription(cmd.description());
        repo.setCreatedByUserId(actor.userId());
        repo.setVisibility(cmd.visibility());
        repo.setLifecycleStatus("provisioning");
        repo.setMetadataSchemaVersion(cmd.metadataSchemaVersion());
        repo.setMetadata(metadataJson);
        repo.setDefaultBranch("main");
        repo.setProvisionRetryCount(0);
        repo.setCreatedAt(now);
        repo.setUpdatedAt(now);

        if (gated) {
            GatedPolicyEntity policy = new GatedPolicyEntity();
            policy.setEnabled(true);
            policy.setGeneration(1);
            policy.setDefaultGrantTtlSeconds(2_592_000L);
            policy.setRequestSchemaVersion(1);
            policy.setCreatedBy(actor.userId());
            policy.setUpdatedBy(actor.userId());
            policy.setCreatedAt(now);
            policy.setUpdatedAt(now);
            gatedPolicies.save(policy);
            repo.setGatedPolicyId(policy.getId());
        }

        try {
            repositories.saveAndFlush(repo);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "同 namespace 下已存在同名仓库");
        }

        RepoStatsEntity stats = new RepoStatsEntity();
        stats.setRepositoryId(repo.getId());
        stats.setUpdatedAt(now);
        repoStats.save(stats);

        projector.project(repo, schema, validatedMetadata);

        // 同事务写 Outbox（05 §10.1）：Worker 幂等建 Gitea 仓库
        outbox.publish("RepositoryProvisionRequested", repo.getPublicId().toString(), repo.getVersion(),
                Map.of("repositoryId", repo.getId(), "publicId", repo.getPublicId().toString(),
                        "namespaceSlug", ns.getSlug(), "type", repo.getResourceType(),
                        "name", repo.getNormalizedName(), "visibility", repo.getVisibility()));

        audit.appendSimple(String.valueOf(actor.userId()), "repository.create",
                "repository:" + repo.getPublicId(), "accepted");
        return toView(repo, ns.getSlug(), zeroStats());
    }

    private void checkCreatePermission(NamespaceEntity ns, CurrentPrincipal actor) {
        if (actor.isPlatformAdmin()) {
            return;
        }
        if ("user".equals(ns.getNamespaceType())) {
            if (!actor.userId().equals(ns.getUserId())) {
                throw new ApiException(ErrorCode.FORBIDDEN, "无权在他人 namespace 创建仓库");
            }
            return;
        }
        // 组织 namespace：owner/admin/member 可创建，viewer 不可（02 §3.2）
        OrganizationMembershipEntity m = memberships
                .findByOrganizationIdAndUserId(ns.getOrganizationId(), actor.userId()).orElse(null);
        if (m == null || !m.isActive() || "viewer".equals(m.getRole())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "当前组织角色无权创建仓库");
        }
    }

    // ---------- 详情 / resolve ----------

    /** 详情读取；授权成功后异步投递 visit（06 §7.2 请求线程不得同步写 visits）。 */
    public RepoView get(CurrentPrincipal actor, UUID repoId, String requestIp) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        visitRecorder.record(actor, ctx.repo().getId(), requestIp);
        return toView(ctx.repo(), ctx.namespace().getSlug(), statsOf(ctx.repo().getId()));
    }

    public RepoView resolve(CurrentPrincipal actor, String typeKey, String namespaceSlug, String name) {
        RepoContext ctx = access.authorizeByName(typeKey, namespaceSlug, name, actor, RepoRole.READ);
        return toView(ctx.repo(), ctx.namespace().getSlug(), statsOf(ctx.repo().getId()));
    }

    /** 授权后的上下文（子资源端点复用）。 */
    public RepoContext authorize(CurrentPrincipal actor, UUID repoId, RepoRole required) {
        return access.authorize(repoId, actor, required);
    }

    // ---------- PATCH（04 §10 If-Match） ----------

    @Transactional
    public RepoView update(CurrentPrincipal actor, UUID repoId, UpdateRepoCmd cmd, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.WRITE);
        RepositoryEntity repo = repositories.findByIdForUpdate(ctx.repo().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        ETags.requireMatch(ifMatch, ETags.ofVersion(repo.getVersion()), "仓库");

        if (!"active".equals(repo.getLifecycleStatus()) && !"archived".equals(repo.getLifecycleStatus())
                && !"draft".equals(repo.getLifecycleStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不允许更新: " + repo.getLifecycleStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (cmd.hasDisplayName()) {
            repo.setDisplayName(cmd.displayName());
        }
        if (cmd.hasDescription()) {
            repo.setDescription(cmd.description());
        }

        // visibility 变更：public → 非 public 时递增 gated generation 并停用策略（03 §7）
        if (cmd.visibility() != null && !cmd.visibility().equals(repo.getVisibility())) {
            if ("organization".equals(cmd.visibility())) {
                NamespaceEntity ns = ctx.namespace();
                if (!"organization".equals(ns.getNamespaceType())) {
                    throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID,
                            "organization 可见性仅适用于组织 namespace",
                            List.of(new ApiException.Detail("visibility", "requires_organization_namespace")));
                }
            }
            if (repo.getGatedPolicyId() != null && "public".equals(repo.getVisibility())) {
                GatedPolicyEntity policy = gatedPolicies.findById(repo.getGatedPolicyId()).orElseThrow();
                policy.setEnabled(false);
                policy.setGeneration(policy.getGeneration() + 1);
                policy.setUpdatedBy(actor.userId());
                policy.setUpdatedAt(now);
                gatedPolicies.save(policy);
            }
            repo.setVisibility(cmd.visibility());
        }

        // gated 开关：v1 仅 public 可启用（03 §7）
        if (cmd.gated() != null) {
            if (cmd.gated()) {
                if (!"public".equals(repo.getVisibility())) {
                    throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "v1 只允许 public 仓库启用 gated",
                            List.of(new ApiException.Detail("gated", "requires_public")));
                }
                if (repo.getGatedPolicyId() == null) {
                    GatedPolicyEntity policy = new GatedPolicyEntity();
                    policy.setEnabled(true);
                    policy.setGeneration(1);
                    policy.setDefaultGrantTtlSeconds(2_592_000L);
                    policy.setRequestSchemaVersion(1);
                    policy.setCreatedBy(actor.userId());
                    policy.setUpdatedBy(actor.userId());
                    policy.setCreatedAt(now);
                    policy.setUpdatedAt(now);
                    gatedPolicies.save(policy);
                    repo.setGatedPolicyId(policy.getId());
                } else {
                    GatedPolicyEntity policy = gatedPolicies.findById(repo.getGatedPolicyId()).orElseThrow();
                    policy.setEnabled(true);
                    policy.setUpdatedBy(actor.userId());
                    policy.setUpdatedAt(now);
                    gatedPolicies.save(policy);
                }
            } else if (repo.getGatedPolicyId() != null) {
                GatedPolicyEntity policy = gatedPolicies.findById(repo.getGatedPolicyId()).orElseThrow();
                if (policy.isEnabled()) {
                    policy.setEnabled(false);
                    policy.setGeneration(policy.getGeneration() + 1);
                    policy.setUpdatedBy(actor.userId());
                    policy.setUpdatedAt(now);
                    gatedPolicies.save(policy);
                }
            }
        }

        // metadata 变更：必须同时提交 metadataSchemaVersion（契约 dependentRequired）
        if (cmd.metadataJson() != null) {
            int version = cmd.metadataSchemaVersion() == null
                    ? repo.getMetadataSchemaVersion() : cmd.metadataSchemaVersion();
            SchemaVersionEntity schema = schemaVersions
                    .findById(new SchemaVersionId(repo.getResourceType(), version))
                    .orElseThrow(() -> new ApiException(ErrorCode.METADATA_SCHEMA_INVALID,
                            "metadataSchemaVersion 未注册",
                            List.of(new ApiException.Detail("metadataSchemaVersion", "unknown_version"))));
            if (!"published".equals(schema.getStatus())) {
                throw new ApiException(ErrorCode.METADATA_SCHEMA_INVALID, "metadataSchemaVersion 未发布",
                        List.of(new ApiException.Detail("metadataSchemaVersion", "not_published")));
            }
            JsonNode validated = metadataValidator.validate(schema, cmd.metadataJson());
            repo.setMetadataSchemaVersion(version);
            repo.setMetadata(cmd.metadataJson());
            projector.project(repo, schema, validated);
        }

        repo.setUpdatedAt(now);
        // saveAndFlush：@Version 在 flush 时才递增，响应视图必须在同事务内拿到新版本号，
        // 否则客户端拿到的 etag 落后一拍，后续 If-Match 会 412（04 §10）
        repositories.saveAndFlush(repo);
        audit.appendSimple(String.valueOf(actor.userId()), "repository.update",
                "repository:" + repo.getPublicId(), "success");
        return toView(repo, ctx.namespace().getSlug(), statsOf(repo.getId()));
    }

    // ---------- DELETE / restore（05 §9.2） ----------

    @Transactional
    public JobView delete(CurrentPrincipal actor, UUID repoId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.ADMIN);
        RepositoryEntity repo = repositories.findByIdForUpdate(ctx.repo().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        ETags.requireMatch(ifMatch, ETags.ofVersion(repo.getVersion()), "仓库");

        String status = repo.getLifecycleStatus();
        if (!"active".equals(status) && !"archived".equals(status)) {
            // failed/draft/provisioning 仅允许管理端点删除（05 §9.2 第 1 步）
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不允许删除: " + status);
        }
        return enterDeleting(actor, repo, "repository.deletion");
    }

    /** admin 端点：failed/draft/provisioning 源状态删除（05 §8、§9.2）。 */
    @Transactional
    public JobView adminDelete(CurrentPrincipal actor, UUID repoId) {
        requirePlatformAdmin(actor);
        RepositoryEntity repo = repositories.findByPublicId(repoId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        String status = repo.getLifecycleStatus();
        if (!"failed".equals(status) && !"draft".equals(status) && !"provisioning".equals(status)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "管理删除仅支持 failed/draft/provisioning 源状态: " + status);
        }
        return enterDeleting(actor, repo, "repository.admin_deletion");
    }

    private JobView enterDeleting(CurrentPrincipal actor, RepositoryEntity repo, String action) {
        OffsetDateTime now = OffsetDateTime.now();
        String previous = repo.getLifecycleStatus();
        repo.setLifecycleStatus("deleting");
        repo.setRestoreTargetStatus("archived".equals(previous) ? "archived" : "active");
        repo.setUpdatedAt(now);
        repositories.save(repo);

        outbox.publish("RepositoryDeletionRequested", repo.getPublicId().toString(), repo.getVersion(),
                Map.of("repositoryId", repo.getId(), "publicId", repo.getPublicId().toString()));
        JobEntity job = newJob(actor, "repository.deletion", repo.getPublicId().toString());
        audit.appendSimple(String.valueOf(actor.userId()), action,
                "repository:" + repo.getPublicId(), "accepted");
        return new JobView(job.getPublicId(), job.getJobType(), job.getStatus(), job.getCreatedAt());
    }

    @Transactional
    public JobView restore(CurrentPrincipal actor, UUID repoId) {
        RepositoryEntity repo = repositories.findByPublicId(repoId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        if (!"deleted".equals(repo.getLifecycleStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "仅 deleted 状态可恢复");
        }
        if ("purging".equals(repo.getLifecycleStatus()) || "purged".equals(repo.getLifecycleStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "purging/purged 不可恢复");
        }
        // 授权：platform_admin 或 namespace 管理角色（deleted 状态不走普通 facade 流程）
        NamespaceEntity ns = namespaces.findById(repo.getNamespaceId()).orElseThrow();
        RepoRole role = access.computeEffectiveRole(repo, ns, actor);
        boolean platformAdmin = actor != null && actor.isPlatformAdmin();
        if (!platformAdmin && !role.atLeast(RepoRole.ADMIN)) {
            if (actor == null || !access.isNamespaceMember(ns, actor)) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在");
            }
            throw new ApiException(ErrorCode.FORBIDDEN, "权限不足");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (repo.getRetentionUntil() != null && now.isAfter(repo.getRetentionUntil())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "保留期已结束，无法恢复");
        }

        String target = repo.getRestoreTargetStatus() == null ? "active" : repo.getRestoreTargetStatus();
        repo.setLifecycleStatus(target);
        repo.setRestoreTargetStatus(null);
        repo.setDeletedAt(null);
        repo.setRetentionUntil(null);
        repo.setUpdatedAt(now);
        repositories.save(repo);

        outbox.publish("RepositoryRestoreRequested", repo.getPublicId().toString(), repo.getVersion(),
                Map.of("repositoryId", repo.getId(), "publicId", repo.getPublicId().toString(),
                        "visibility", repo.getVisibility()));
        JobEntity job = newJob(actor, "repository.restore", repo.getPublicId().toString());
        audit.appendSimple(String.valueOf(actor == null ? "system" : actor.userId()), "repository.restore",
                "repository:" + repo.getPublicId(), "accepted");
        return new JobView(job.getPublicId(), job.getJobType(), job.getStatus(), job.getCreatedAt());
    }

    /** admin 端点：重试失败的 provisioning（05 §8），契约返回 202 JobEnvelope。 */
    @Transactional
    public JobView adminRetry(CurrentPrincipal actor, UUID repoId) {
        requirePlatformAdmin(actor);
        RepositoryEntity repo = repositories.findByPublicId(repoId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        if (!"failed".equals(repo.getLifecycleStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "仅 failed 状态可重试 provisioning");
        }
        NamespaceEntity ns = namespaces.findById(repo.getNamespaceId()).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        repo.setLifecycleStatus("provisioning");
        repo.setProvisionRetryCount(0);
        repo.setProvisionNextRetryAt(null);
        repo.setProvisionLastError(null);
        repo.setUpdatedAt(now);
        repositories.save(repo);
        outbox.publish("RepositoryProvisionRequested", repo.getPublicId().toString(), repo.getVersion(),
                Map.of("repositoryId", repo.getId(), "publicId", repo.getPublicId().toString(),
                        "namespaceSlug", ns.getSlug(), "type", repo.getResourceType(),
                        "name", repo.getNormalizedName(), "visibility", repo.getVisibility()));
        JobEntity job = newJob(actor, "repository.provisioning", repo.getPublicId().toString());
        audit.appendSimple(String.valueOf(actor.userId()), "repository.admin_retry",
                "repository:" + repo.getPublicId(), "accepted");
        return new JobView(job.getPublicId(), job.getJobType(), job.getStatus(), job.getCreatedAt());
    }

    private void requirePlatformAdmin(CurrentPrincipal actor) {
        if (actor == null || !actor.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "需要 platform_admin 权限");
        }
    }

    private JobEntity newJob(CurrentPrincipal actor, String type, String aggregateId) {
        OffsetDateTime now = OffsetDateTime.now();
        JobEntity job = new JobEntity();
        job.setPublicId(PublicIds.next());
        job.setJobType(type);
        job.setStatus("queued");
        job.setAggregateType("repository");
        job.setAggregateId(aggregateId);
        job.setCreatedBy(actor == null ? null : actor.userId());
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return jobs.save(job);
    }

    // ---------- 列表搜索（04 §6） ----------

    @Transactional(readOnly = true)
    public PageResult<RepoView> list(CurrentPrincipal actor, MultiValueMap<String, String> params) {
        PageQuery page = PageQuery.from(firstValueMap(params));
        String sort = params.getFirst("sort");
        if (sort == null || sort.isBlank()) {
            sort = "relevance-v1";
        }
        if (!SORT_KEYS.contains(sort)) {
            throw ApiException.badRequest("未知 sort 值: " + sort,
                    List.of(new ApiException.Detail("sort", "unknown_value")));
        }

        VisibleScope scope = access.computeVisibleScope(actor);
        StringBuilder where = new StringBuilder(" WHERE r.lifecycle_status IN ('active','archived') ");
        Map<String, Object> qp = new HashMap<>();
        appendScope(where, scope, qp);
        appendFilters(where, qp, params);

        long total = count(where.toString(), qp);
        String sql = "SELECT r.* FROM repositories r LEFT JOIN repository_stats s ON s.repository_id = r.id "
                + where + " ORDER BY " + SORT_ORDER_SQL.get(sort)
                + " LIMIT :limit OFFSET :offset";
        Query query = em.createNativeQuery(sql, RepositoryEntity.class);
        bindParams(query, qp);
        query.setParameter("limit", page.pageSize());
        query.setParameter("offset", page.offset());
        @SuppressWarnings("unchecked")
        List<RepositoryEntity> repos = query.getResultList();
        return PageResult.of(total, page, toViews(repos));
    }

    /** 相关推荐：同类型、同一授权过滤、排除自身。 */
    @Transactional(readOnly = true)
    public PageResult<RepoView> related(CurrentPrincipal actor, UUID repoId, String typeFilter, PageQuery page) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        String type = typeFilter == null || typeFilter.isBlank() ? ctx.repo().getResourceType() : typeFilter;

        VisibleScope scope = access.computeVisibleScope(actor);
        StringBuilder where = new StringBuilder(" WHERE r.lifecycle_status IN ('active','archived') "
                + "AND r.resource_type = :relType AND r.id <> :selfId ");
        Map<String, Object> qp = new HashMap<>();
        qp.put("relType", type);
        qp.put("selfId", ctx.repo().getId());
        appendScope(where, scope, qp);

        long total = count(where.toString(), qp);
        String sql = "SELECT r.* FROM repositories r LEFT JOIN repository_stats s ON s.repository_id = r.id "
                + where + " ORDER BY r.updated_at DESC, r.public_id DESC LIMIT :limit OFFSET :offset";
        Query query = em.createNativeQuery(sql, RepositoryEntity.class);
        bindParams(query, qp);
        query.setParameter("limit", page.pageSize());
        query.setParameter("offset", page.offset());
        @SuppressWarnings("unchecked")
        List<RepositoryEntity> repos = query.getResultList();
        return PageResult.of(total, page, toViews(repos));
    }

    /** 列表可见范围过滤（供 InteractionService.listMine 等复用，ME-001 不泄漏私有资源）。 */
    public void appendScope(StringBuilder where, VisibleScope scope, Map<String, Object> qp) {
        if (scope.seesAll()) {
            return;
        }
        if (scope.visibleNamespaceIds().isEmpty() && scope.collaboratorRepoIds().isEmpty()) {
            where.append(" AND r.visibility = 'public' ");
            return;
        }
        where.append(" AND (r.visibility = 'public' ");
        if (!scope.visibleNamespaceIds().isEmpty()) {
            // 组织 namespace 成员仅可见 public/organization 仓库，private 需显式协作者授权（02 §9）
            where.append(" OR (r.namespace_id IN (:scopeNsIds) AND r.visibility IN ('public','organization')) ");
            qp.put("scopeNsIds", scope.visibleNamespaceIds());
        }
        if (!scope.collaboratorRepoIds().isEmpty()) {
            where.append(" OR r.id IN (:scopeRepoIds) ");
            qp.put("scopeRepoIds", scope.collaboratorRepoIds());
        }
        where.append(") ");
    }

    private void appendFilters(StringBuilder where, Map<String, Object> qp, MultiValueMap<String, String> params) {
        String type = params.getFirst("type");
        if (notBlank(type)) {
            where.append(" AND r.resource_type = :fType ");
            qp.put("fType", type);
        }
        String keyword = params.getFirst("keyword");
        if (notBlank(keyword)) {
            where.append(" AND (r.name ILIKE :kw OR r.display_name ILIKE :kw OR r.description ILIKE :kw) ");
            qp.put("kw", "%" + keyword.trim().replace("%", "\\%") + "%");
        }
        String org = params.getFirst("org");
        if (notBlank(org)) {
            where.append(" AND r.namespace_id IN (SELECT n.id FROM namespaces n WHERE lower(n.slug) = lower(:orgSlug)) ");
            qp.put("orgSlug", org.trim());
        }
        String gated = params.getFirst("gated");
        if ("true".equalsIgnoreCase(gated)) {
            where.append(" AND r.gated_policy_id IS NOT NULL ");
        } else if ("false".equalsIgnoreCase(gated)) {
            where.append(" AND r.gated_policy_id IS NULL ");
        }
        String featured = params.getFirst("featured");
        if ("true".equalsIgnoreCase(featured)) {
            // v1 尚无 featured 投影，返回空集（行为记录于验证报告）
            where.append(" AND 1 = 0 ");
        }

        // facet 文本筛选：单值字段
        appendFacetFilter(where, qp, "task", params.get("task"), false);
        appendFacetFilter(where, qp, "license", params.get("license"), false);
        appendFacetFilter(where, qp, "architecture", params.get("architecture"), false);
        appendFacetFilter(where, qp, "language", params.get("language"), false);
        appendFacetFilter(where, qp, "apiStatus", params.get("apiStatus"), false);
        // 多值字段（同字段 OR；capability 支持 all 模式）
        appendFacetFilter(where, qp, "framework", params.get("framework"), false);
        appendFacetFilter(where, qp, "tag", params.get("tag"), false);
        appendFacetFilter(where, qp, "scene", params.get("scene"), false);
        appendFacetFilter(where, qp, "capability", params.get("capability"),
                "all".equalsIgnoreCase(params.getFirst("capabilityMode")));
        // 布尔 facet
        if ("true".equalsIgnoreCase(params.getFirst("mcp"))) {
            appendBooleanFacet(where, "mcpCompatible");
        }
        if ("true".equalsIgnoreCase(params.getFirst("deployable"))) {
            appendBooleanFacet(where, "deployable");
        }
        // 通用 facet=key=value
        List<String> facets = params.get("facet");
        if (facets != null && !facets.isEmpty()) {
            boolean all = "all".equalsIgnoreCase(params.getFirst("facetMode"));
            appendGenericFacets(where, qp, facets, all);
        }
    }

    private void appendFacetFilter(StringBuilder where, Map<String, Object> qp, String facetKey,
                                   List<String> values, boolean all) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        int i = 0;
        for (String v : values) {
            if (v == null || v.isBlank()) {
                continue;
            }
            String p = "f_" + facetKey + "_" + i++;
            clauses.add("EXISTS (SELECT 1 FROM repository_facet_values f WHERE f.repository_id = r.id "
                    + "AND f.facet_key = '" + facetKey + "' AND f.value_text = :" + p + ") ");
            qp.put(p, v.trim());
        }
        if (clauses.isEmpty()) {
            return;
        }
        where.append(" AND (").append(String.join(all ? " AND " : " OR ", clauses)).append(") ");
    }

    private void appendBooleanFacet(StringBuilder where, String facetKey) {
        where.append(" AND EXISTS (SELECT 1 FROM repository_facet_values f WHERE f.repository_id = r.id ")
                .append("AND f.facet_key = '").append(facetKey).append("' AND f.value_boolean = true) ");
    }

    private void appendGenericFacets(StringBuilder where, Map<String, Object> qp, List<String> facets, boolean all) {
        List<String> clauses = new ArrayList<>();
        int i = 0;
        for (String raw : facets) {
            int eq = raw == null ? -1 : raw.indexOf('=');
            if (eq <= 0 || eq == raw.length() - 1) {
                throw ApiException.badRequest("facet 参数格式必须为 key=value",
                        List.of(new ApiException.Detail("facet", "invalid_format")));
            }
            String key = raw.substring(0, eq);
            if (!key.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}")) {
                throw ApiException.badRequest("facet key 非法",
                        List.of(new ApiException.Detail("facet", "invalid_format")));
            }
            String value = raw.substring(eq + 1);
            String p = "gfacet_" + i++;
            clauses.add("EXISTS (SELECT 1 FROM repository_facet_values f WHERE f.repository_id = r.id "
                    + "AND f.facet_key = :gk_" + p + " AND f.value_text = :" + p + ") ");
            qp.put("gk_" + p, key);
            qp.put(p, value);
        }
        if (!clauses.isEmpty()) {
            where.append(" AND (").append(String.join(all ? " AND " : " OR ", clauses)).append(") ");
        }
    }

    private long count(String where, Map<String, Object> qp) {
        Query query = em.createNativeQuery("SELECT COUNT(*) FROM repositories r " + where);
        bindParams(query, qp);
        return ((Number) query.getSingleResult()).longValue();
    }

    private void bindParams(Query query, Map<String, Object> qp) {
        qp.forEach(query::setParameter);
    }

    private static Map<String, String> firstValueMap(MultiValueMap<String, String> params) {
        Map<String, String> map = new HashMap<>();
        params.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                map.put(k, v.get(0));
            }
        });
        return map;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ---------- 视图装配 ----------

    /** 视图装配（供 InteractionService.listMine 等复用 CatalogService.toViews 的 RepoView）。 */
    public List<RepoView> toViews(List<RepositoryEntity> repos) {
        if (repos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = repos.stream().map(RepositoryEntity::getId).toList();
        Map<Long, RepoStatsEntity> statsMap = new HashMap<>();
        repoStats.findAllById(ids).forEach(s -> statsMap.put(s.getRepositoryId(), s));
        Set<Long> nsIds = new java.util.HashSet<>();
        repos.forEach(r -> nsIds.add(r.getNamespaceId()));
        Map<Long, String> slugMap = new HashMap<>();
        namespaces.findAllById(nsIds).forEach(n -> slugMap.put(n.getId(), n.getSlug()));
        List<RepoView> views = new ArrayList<>();
        for (RepositoryEntity r : repos) {
            RepoStatsEntity s = statsMap.get(r.getId());
            views.add(toView(r, slugMap.getOrDefault(r.getNamespaceId(), ""), s == null ? zeroStats() : s));
        }
        return views;
    }

    private RepoStatsEntity statsOf(Long repoId) {
        return repoStats.findById(repoId).orElse(null);
    }

    private static RepoStatsEntity zeroStats() {
        return null;
    }

    private RepoView toView(RepositoryEntity r, String namespaceSlug, RepoStatsEntity stats) {
        StatsView sv = stats == null ? new StatsView(0, 0, 0, 0, 0)
                : new StatsView(stats.getLikes(), stats.getFavorites(), stats.getDownloads(),
                stats.getVisits(), stats.getFileCount());
        boolean gated = false;
        if (r.getGatedPolicyId() != null) {
            gated = gatedPolicies.findById(r.getGatedPolicyId()).map(GatedPolicyEntity::isEnabled).orElse(false);
        }
        return new RepoView(r.getPublicId(), r.getResourceType(), namespaceSlug, r.getName(),
                r.getDisplayName(), r.getDescription(), r.getVisibility(), gated, sv,
                r.getLifecycleStatus(), r.getRestoreTargetStatus(), r.getDeletedAt(), r.getRetentionUntil(),
                parseMetadata(r.getMetadata()), r.getMetadataSchemaVersion(),
                r.getVersion() == null ? 0 : r.getVersion(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getVersion() == null ? ETags.ofVersion(0) : ETags.ofVersion(r.getVersion()));
    }

    private Object parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}


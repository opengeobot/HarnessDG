package com.modelhub.catalog.access;

import com.modelhub.catalog.domain.CollaboratorEntity;
import com.modelhub.catalog.domain.GatedGrantEntity;
import com.modelhub.catalog.domain.GatedPolicyEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.CollaboratorRepository;
import com.modelhub.catalog.repo.GatedGrantRepository;
import com.modelhub.catalog.repo.GatedPolicyRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.domain.OrganizationMembershipEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.OrganizationMembershipRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 统一授权门面（02 §5 七步流程）：
 * 1. public_id 或 type/namespace/name 解析仓库；
 * 2. lifecycle 校验（deleted/purging/purged 不进入普通流程）；
 * 3. 解析当前主体（匿名/Web 用户）；
 * 4. namespace 归属合并平台角色、组织角色与协作者（created_by 不参与授权）；
 * 5. 能力校验；
 * 6. 审计上下文由调用方随操作落 audit_logs；
 * 7. 只有授权成功后才访问内容。
 *
 * private 防枚举（02 §4）：匿名或无任何关系的主体访问非 public 资源返回 404；
 * 已知资源但权限不足的组织成员/协作者返回 403。
 */
@Component
public class RepositoryAccessFacade {

    /** 仓库有效角色强度（02 §3.3）。 */
    public enum RepoRole {
        NONE, READ, WRITE, MAINTAIN, ADMIN;

        public boolean atLeast(RepoRole minimum) {
            return ordinal() >= minimum.ordinal();
        }
    }

    /** 授权后的仓库上下文：所有子资源端点复用。 */
    public record RepoContext(RepositoryEntity repo, NamespaceEntity namespace, RepoRole role,
                              boolean platformAdmin, boolean hasActiveGrant) {
        public boolean gatedEnabled() {
            return repo.getGatedPolicyId() != null;
        }
    }

    /** 列表查询的可见范围（避免逐行 facade 调用）。 */
    public record VisibleScope(boolean platformAdmin, Set<Long> visibleNamespaceIds,
                               Set<Long> collaboratorRepoIds) {
        public boolean seesAll() {
            return platformAdmin;
        }
    }

    private final RepositoryRepository repositories;
    private final NamespaceRepository namespaces;
    private final OrganizationMembershipRepository memberships;
    private final CollaboratorRepository collaborators;
    private final GatedGrantRepository grants;
    private final GatedPolicyRepository policies;

    public RepositoryAccessFacade(RepositoryRepository repositories, NamespaceRepository namespaces,
                                  OrganizationMembershipRepository memberships,
                                  CollaboratorRepository collaborators, GatedGrantRepository grants,
                                  GatedPolicyRepository policies) {
        this.repositories = repositories;
        this.namespaces = namespaces;
        this.memberships = memberships;
        this.collaborators = collaborators;
        this.grants = grants;
        this.policies = policies;
    }

    // ---------- 第 1 步：解析 ----------

    /** 按 publicId 解析并授权；未找到/无权限按防枚举策略返回 404/403。 */
    public RepoContext authorize(UUID publicId, CurrentPrincipal principal, RepoRole required) {
        RepositoryEntity repo = repositories.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        return authorizeRepo(repo, principal, required);
    }

    /** 按 type/namespace/name 解析并授权（04 §5 resolve 端点）。 */
    public RepoContext authorizeByName(String typeKey, String namespaceSlug, String name,
                                       CurrentPrincipal principal, RepoRole required) {
        NamespaceEntity ns = namespaces.findBySlugIgnoreCase(namespaceSlug)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        RepositoryEntity repo = repositories
                .findByNamespaceIdAndResourceTypeAndNormalizedName(
                        ns.getId(), typeKey, name.toLowerCase(Locale.ROOT))
                .orElse(null);
        if (repo == null) {
            // namespace 已知但仓库不存在：无关系主体同样 404 防枚举
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在");
        }
        return authorizeRepo(repo, principal, required);
    }

    // ---------- 第 2-5 步：lifecycle、主体、角色合并、能力校验 ----------

    public RepoContext authorizeRepo(RepositoryEntity repo, CurrentPrincipal principal, RepoRole required) {
        // 第 2 步：lifecycle 校验（deleted/purging/purged 不进入普通流程）
        String status = repo.getLifecycleStatus();
        if ("deleted".equals(status) || "purging".equals(status) || "purged".equals(status)) {
            if (principal == null || !principal.isPlatformAdmin()) {
                throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在");
            }
        }
        NamespaceEntity ns = namespaces.findById(repo.getNamespaceId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));

        // 第 3/4 步：主体解析与角色合并
        RepoRole role = computeEffectiveRole(repo, ns, principal);
        boolean platformAdmin = principal != null && principal.isPlatformAdmin();

        // 第 5 步：能力校验（READ 需结合 visibility 语义，02 §4）
        if (required.atLeast(RepoRole.READ)) {
            if (!isReadAllowed(repo, ns, principal, role, platformAdmin)) {
                throw deny(repo, ns, principal);
            }
            // 高于 READ 的能力按有效角色校验；READ 本身由 visibility 语义判定，
            // 组织成员对 organization 可见仓库、匿名对 public 仓库有效角色可为 NONE
            if (required != RepoRole.READ && !role.atLeast(required)) {
                throw deny(repo, ns, principal);
            }
        }

        boolean grant = platformAdmin || role.atLeast(RepoRole.READ) || hasActiveGrant(repo, principal);
        return new RepoContext(repo, ns, role, platformAdmin, grant);
    }

    /** 合并有效角色：platform_admin → ADMIN；个人 ns 本人 → ADMIN；
     *  组织 ns owner/admin → ADMIN、member → write（自建）；协作者显式覆盖取最高。 */
    public RepoRole computeEffectiveRole(RepositoryEntity repo, NamespaceEntity ns, CurrentPrincipal principal) {
        if (principal == null) {
            return RepoRole.NONE;
        }
        if (principal.isPlatformAdmin()) {
            return RepoRole.ADMIN;
        }
        RepoRole role = RepoRole.NONE;
        if ("user".equals(ns.getNamespaceType())) {
            if (principal.userId().equals(ns.getUserId())) {
                role = RepoRole.ADMIN;
            }
        } else if ("organization".equals(ns.getNamespaceType())) {
            role = orgDerivedRole(repo, ns.getOrganizationId(), principal);
        }
        return max(role, collaboratorRole(repo.getId(), principal));
    }

    private RepoRole orgDerivedRole(RepositoryEntity repo, Long orgId, CurrentPrincipal principal) {
        Optional<OrganizationMembershipEntity> m =
                memberships.findByOrganizationIdAndUserId(orgId, principal.userId());
        if (m.isEmpty() || !m.get().isActive()) {
            return RepoRole.NONE;
        }
        return switch (m.get().getRole()) {
            case "owner", "admin" -> RepoRole.ADMIN;
            // member 仅对自己创建的仓库获得 write；created_by 不形成其他永久权限
            case "member" -> principal.userId().equals(repo.getCreatedByUserId())
                    ? RepoRole.WRITE : RepoRole.NONE;
            default -> RepoRole.NONE;
        };
    }

    private RepoRole collaboratorRole(Long repoId, CurrentPrincipal principal) {
        OffsetDateTime now = OffsetDateTime.now();
        RepoRole role = RepoRole.NONE;
        for (CollaboratorEntity c : collaborators.findByRepositoryIdOrderByIdAsc(repoId)) {
            if (c.getExpiresAt() != null && !c.getExpiresAt().isAfter(now)) {
                continue;
            }
            boolean hit;
            if ("user".equals(c.getSubjectType())) {
                hit = principal.userId().equals(c.getSubjectUserId());
            } else {
                hit = memberships.findByOrganizationIdAndUserId(c.getSubjectOrganizationId(), principal.userId())
                        .map(OrganizationMembershipEntity::isActive).orElse(false);
            }
            if (hit) {
                role = max(role, parseRole(c.getRole()));
            }
        }
        return role;
    }

    private static RepoRole parseRole(String role) {
        return switch (role) {
            case "admin" -> RepoRole.ADMIN;
            case "maintain" -> RepoRole.MAINTAIN;
            case "write" -> RepoRole.WRITE;
            case "read" -> RepoRole.READ;
            default -> RepoRole.NONE;
        };
    }

    private static RepoRole max(RepoRole a, RepoRole b) {
        return a.atLeast(b) ? a : b;
    }

    /** visibility 读取语义（02 §4）：public 所有人；organization 有效成员及 read+；private read+。 */
    private boolean isReadAllowed(RepositoryEntity repo, NamespaceEntity ns, CurrentPrincipal principal,
                                  RepoRole role, boolean platformAdmin) {
        if (platformAdmin) {
            return true;
        }
        switch (repo.getVisibility()) {
            case "public":
                return true;
            case "organization":
                if (role.atLeast(RepoRole.READ)) {
                    return true;
                }
                return "organization".equals(ns.getNamespaceType()) && principal != null
                        && memberships.findByOrganizationIdAndUserId(ns.getOrganizationId(), principal.userId())
                        .map(OrganizationMembershipEntity::isActive).orElse(false);
            case "private":
            default:
                return role.atLeast(RepoRole.READ);
        }
    }

    /** 防枚举拒绝（02 §4）：有关系 → 403，无关系/匿名 → 404。 */
    private ApiException deny(RepositoryEntity repo, NamespaceEntity ns, CurrentPrincipal principal) {
        if (principal == null) {
            return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在");
        }
        if (principal.isPlatformAdmin()) {
            return new ApiException(ErrorCode.FORBIDDEN, "权限不足");
        }
        boolean related = false;
        if ("organization".equals(ns.getNamespaceType())) {
            related = memberships.findByOrganizationIdAndUserId(ns.getOrganizationId(), principal.userId())
                    .map(OrganizationMembershipEntity::isActive).orElse(false);
        } else if ("user".equals(ns.getNamespaceType())) {
            related = principal.userId().equals(ns.getUserId());
        }
        if (!related) {
            OffsetDateTime now = OffsetDateTime.now();
            related = !collaborators.findRepoIdsForUser(principal.userId(), now).contains(repo.getId())
                    ? false : true;
        }
        if (related) {
            return new ApiException(ErrorCode.FORBIDDEN, "权限不足");
        }
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在");
    }

    // ---------- gated grant ----------

    /** 当前主体是否持有 approved 且未过期未吊销、generation 与当前策略一致的 grant（02 §4：旧 generation 永不复活）。 */
    public boolean hasActiveGrant(RepositoryEntity repo, CurrentPrincipal principal) {
        if (principal == null || repo.getGatedPolicyId() == null) {
            return false;
        }
        GatedPolicyEntity policy = policies.findById(repo.getGatedPolicyId()).orElse(null);
        if (policy == null || !policy.isEnabled()) {
            return false;
        }
        return !grants.findActive(repo.getId(), principal.userId(),
                OffsetDateTime.now(), policy.getGeneration()).isEmpty();
    }

    // ---------- 列表可见范围 ----------

    /** 计算列表查询可见范围：public 永远可见；可见 namespace 内仓库；显式协作者仓库。 */
    public VisibleScope computeVisibleScope(CurrentPrincipal principal) {
        if (principal == null) {
            return new VisibleScope(false, Set.of(), Set.of());
        }
        if (principal.isPlatformAdmin()) {
            return new VisibleScope(true, Set.of(), Set.of());
        }
        OffsetDateTime now = OffsetDateTime.now();
        Set<Long> nsIds = new HashSet<>();
        namespaces.findByUserIdAndNamespaceType(principal.userId(), "user")
                .ifPresent(n -> nsIds.add(n.getId()));
        List<OrganizationMembershipEntity> myMemberships =
                memberships.findByUserIdAndStatus(principal.userId(), "active");
        Set<Long> orgIds = new HashSet<>();
        for (OrganizationMembershipEntity m : myMemberships) {
            orgIds.add(m.getOrganizationId());
        }
        for (Long orgId : orgIds) {
            namespaces.findByOrganizationIdAndNamespaceType(orgId, "organization")
                    .ifPresent(n -> nsIds.add(n.getId()));
        }
        Set<Long> repoIds = new HashSet<>(collaborators.findRepoIdsForUser(principal.userId(), now));
        if (!orgIds.isEmpty()) {
            repoIds.addAll(collaborators.findRepoIdsForOrgs(orgIds, now));
        }
        return new VisibleScope(false, Set.copyOf(nsIds), Set.copyOf(repoIds));
    }

    /** namespace 已知且主体为其有效成员（防枚举 403 判定辅助）。 */
    public boolean isNamespaceMember(NamespaceEntity ns, CurrentPrincipal principal) {
        if (principal == null || !"organization".equals(ns.getNamespaceType())) {
            return false;
        }
        return memberships.findByOrganizationIdAndUserId(ns.getOrganizationId(), principal.userId())
                .map(OrganizationMembershipEntity::isActive).orElse(false);
    }

    /** 供服务层复用的集合可见性判断。 */
    public boolean isVisible(RepositoryEntity repo, VisibleScope scope) {
        if (scope.seesAll()) {
            return true;
        }
        if ("public".equals(repo.getVisibility())) {
            return true;
        }
        return scope.visibleNamespaceIds().contains(repo.getNamespaceId())
                || scope.collaboratorRepoIds().contains(repo.getId());
    }

    public Collection<RepositoryEntity> filterVisible(List<RepositoryEntity> repos, VisibleScope scope) {
        return repos.stream().filter(r -> isVisible(r, scope)).toList();
    }
}

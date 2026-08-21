package com.modelhub.identity.service;

import com.modelhub.identity.domain.NamespaceEntity;
import com.modelhub.identity.domain.OrganizationEntity;
import com.modelhub.identity.domain.OrganizationMembershipEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.repo.OrganizationMembershipRepository;
import com.modelhub.identity.repo.OrganizationRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import com.modelhub.shared.web.ETags;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 组织与成员管理（02 §3.2）：
 * - 创建组织同事务建立创建者 active owner membership；
 * - 仅 owner 可授予/撤销 owner；admin 不可移除 owner；
 * - 任何操作不得移除或降级最后一个 active owner。
 */
@Service
public class OrganizationService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{2,62}$");
    private static final Map<String, Integer> ROLE_RANK =
            Map.of("viewer", 0, "member", 1, "admin", 2, "owner", 3);

    /** 组织视图：含 namespaceId（组织 namespace 发现，03 §2.1）与 ETag。字段名对齐 OpenAPI。 */
    public record OrgView(@JsonProperty("id") String publicId, String slug, String name, String description,
                          String namespaceId, String status, long version, String etag,
                          OffsetDateTime createdAt) {}

    /** Member view: nested user + version (OpenAPI Member schema). */
    public record MemberView(UserInfo user, String role, String status, long version, OffsetDateTime joinedAt) {
        public record UserInfo(String id, String username, String nickname) {}
    }

    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final NamespaceRepository namespaces;
    private final UserRepository users;
    private final AuditService auditService;

    public OrganizationService(OrganizationRepository organizations,
                               OrganizationMembershipRepository memberships,
                               NamespaceRepository namespaces, UserRepository users,
                               AuditService auditService) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.namespaces = namespaces;
        this.users = users;
        this.auditService = auditService;
    }

    @Transactional
    public OrgView create(CurrentPrincipal actor, String slug, String name, String description) {
        String normalizedSlug = normalizeSlug(slug);
        OrganizationEntity org = new OrganizationEntity();
        org.setPublicId(PublicIds.next());
        org.setSlug(normalizedSlug);
        org.setName(name);
        org.setDescription(description);
        org.setCreatedByUserId(actor.userId());
        try {
            organizations.save(org);
            organizations.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "组织 slug 已被占用");
        }

        NamespaceEntity ns = new NamespaceEntity();
        ns.setPublicId(PublicIds.next());
        ns.setNamespaceType("organization");
        ns.setOrganizationId(org.getId());
        ns.setSlug(normalizedSlug);
        ns.setDisplayName(name);
        namespaces.save(ns);

        OrganizationMembershipEntity owner = new OrganizationMembershipEntity();
        owner.setOrganizationId(org.getId());
        owner.setUserId(actor.userId());
        owner.setRole("owner");
        owner.setStatus("active");
        owner.setGrantedBy(actor.userId());
        memberships.save(owner);

        auditService.appendSimple(actor.username(), "org.create", "org:" + org.getPublicId(), "success");
        return toView(org, ns.getPublicId());
    }

    @Transactional(readOnly = true)
    public PageResult<OrgView> listMine(CurrentPrincipal actor, PageQuery page) {
        Page<OrganizationEntity> result = organizations.findMyOrganizations(
                actor.userId(), PageRequest.of(page.page() - 1, page.pageSize()));
        List<OrgView> items = result.getContent().stream()
                .map(o -> toView(o, orgNamespaceId(o.getId())))
                .toList();
        return PageResult.of(result.getTotalElements(), page, items);
    }

    @Transactional(readOnly = true)
    public OrgView get(CurrentPrincipal actor, UUID orgPublicId) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, 0);
        return toView(org, orgNamespaceId(org.getId()));
    }

    @Transactional
    public OrgView update(CurrentPrincipal actor, UUID orgPublicId, String name, String status,
                          String ifMatch) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, ROLE_RANK.get("admin"));
        ETags.requireMatch(ifMatch, ETags.ofVersion(org.getVersion()), "组织");
        int updated = organizations.compareAndSwap(org.getId(), org.getVersion(),
                name != null ? name : org.getName(),
                status != null ? status : org.getStatus(), OffsetDateTime.now());
        if (updated == 0) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED, "组织已被修改，请刷新后重试");
        }
        auditService.appendSimple(actor.username(), "org.update", "org:" + orgPublicId, "success");
        OrganizationEntity fresh = organizations.findById(org.getId()).orElseThrow();
        return toView(fresh, orgNamespaceId(fresh.getId()));
    }

    @Transactional(readOnly = true)
    public CursorResult<MemberView> listMembers(CurrentPrincipal actor, UUID orgPublicId, CursorQuery cursor) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, 0);
        long lastId = cursor.cursor() == null ? 0 : cursor.lastKey();
        List<OrganizationMembershipEntity> rows =
                memberships.findActiveAfter(org.getId(), lastId, cursor.limit() + 1);
        boolean hasMore = rows.size() > cursor.limit();
        List<OrganizationMembershipEntity> page = hasMore ? rows.subList(0, cursor.limit()) : rows;
        List<MemberView> items = page.stream().map(this::toMemberView).toList();
        Long nextKey = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        return CursorResult.of(items, nextKey);
    }

    @Transactional
    public MemberView addMember(CurrentPrincipal actor, UUID orgPublicId, UUID userPublicId, String role) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, ROLE_RANK.get("admin"));
        requireRoleValid(role);
        if ("owner".equals(role) && operatorRank(org, actor) < ROLE_RANK.get("owner")) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 owner 可以授予 owner 角色");
        }
        UserEntity target = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        OrganizationMembershipEntity existing =
                memberships.findByOrganizationIdAndUserId(org.getId(), target.getId()).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ApiException(ErrorCode.CONFLICT, "用户已是组织成员");
        }
        OrganizationMembershipEntity m = existing != null ? existing : new OrganizationMembershipEntity();
        m.setOrganizationId(org.getId());
        m.setUserId(target.getId());
        m.setRole(role);
        m.setStatus("active");
        m.setGrantedBy(actor.userId());
        m.setUpdatedAt(OffsetDateTime.now());
        memberships.save(m);
        auditService.appendSimple(actor.username(), "org.member_add",
                "org:" + orgPublicId + " user:" + userPublicId + " role:" + role, "success");
        return toMemberView(m);
    }

    @Transactional
    public MemberView updateMemberRole(CurrentPrincipal actor, UUID orgPublicId, UUID userPublicId,
                                       String newRole, String ifMatch) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, ROLE_RANK.get("admin"));
        requireRoleValid(newRole);
        OrganizationMembershipEntity target = findActiveMembership(org.getId(), userPublicId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(target.getVersion()), "成员角色");
        if ((target.isOwner() || "owner".equals(newRole))
                && operatorRank(org, actor) < ROLE_RANK.get("owner")) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 owner 可以授予或撤销 owner 角色");
        }
        if (target.isOwner() && !"owner".equals(newRole)) {
            long owners = memberships.countActiveOwners(org.getId());
            if (owners <= 1) {
                throw new ApiException(ErrorCode.CONFLICT, "不能降级最后一个 active owner");
            }
        }
        target.setRole(newRole);
        target.setVersion(target.getVersion() + 1);
        target.setUpdatedAt(OffsetDateTime.now());
        memberships.save(target);
        auditService.appendSimple(actor.username(), "org.member_role_change",
                "org:" + orgPublicId + " user:" + userPublicId + " role:" + newRole, "success");
        return toMemberView(target);
    }

    @Transactional
    public void removeMember(CurrentPrincipal actor, UUID orgPublicId, UUID userPublicId) {
        OrganizationEntity org = findOrgAndCheckMembership(orgPublicId, actor, ROLE_RANK.get("admin"));
        OrganizationMembershipEntity target = findActiveMembership(org.getId(), userPublicId);
        if (operatorRank(org, actor) < ROLE_RANK.get("owner") && target.isOwner()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "admin 不可移除 owner");
        }
        if (target.isOwner() && memberships.countActiveOwners(org.getId()) <= 1) {
            throw new ApiException(ErrorCode.CONFLICT, "不能移除最后一个 active owner");
        }
        target.setStatus("disabled");
        target.setUpdatedAt(OffsetDateTime.now());
        memberships.save(target);
        auditService.appendSimple(actor.username(), "org.member_remove",
                "org:" + orgPublicId + " user:" + userPublicId, "success");
    }

    // ---------- 内部 ----------

    private OrganizationEntity findOrgAndCheckMembership(UUID orgPublicId, CurrentPrincipal actor,
                                                         int minimumRank) {
        OrganizationEntity org = organizations.findByPublicId(orgPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "组织不存在"));
        if (!"active".equals(org.getStatus())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "组织不可用");
        }
        int rank = operatorRank(org, actor);
        if (rank < 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "组织不存在");
        }
        if (rank < minimumRank) {
            throw new ApiException(ErrorCode.FORBIDDEN, "当前组织角色无权执行该操作");
        }
        return org;
    }

    private int operatorRank(OrganizationEntity org, CurrentPrincipal actor) {
        if (actor.isPlatformAdmin()) {
            return ROLE_RANK.get("owner");
        }
        return memberships.findByOrganizationIdAndUserId(org.getId(), actor.userId())
                .filter(OrganizationMembershipEntity::isActive)
                .map(m -> ROLE_RANK.getOrDefault(m.getRole(), 0))
                .orElse(-1);
    }

    private OrganizationMembershipEntity findActiveMembership(Long orgId, UUID userPublicId) {
        UserEntity target = users.findByPublicId(userPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        OrganizationMembershipEntity m = memberships.findByOrganizationIdAndUserId(orgId, target.getId())
                .filter(OrganizationMembershipEntity::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "该用户不是组织成员"));
        return m;
    }

    private UUID orgNamespaceId(Long orgId) {
        return namespaces.findByOrganizationIdAndNamespaceType(orgId, "organization")
                .map(NamespaceEntity::getPublicId).orElse(null);
    }

    private OrgView toView(OrganizationEntity org, UUID namespacePublicId) {
        return new OrgView(org.getPublicId().toString(), org.getSlug(), org.getName(),
                org.getDescription(), namespacePublicId == null ? null : namespacePublicId.toString(),
                org.getStatus(), org.getVersion(), ETags.ofVersion(org.getVersion()), org.getCreatedAt());
    }

    private MemberView toMemberView(OrganizationMembershipEntity m) {
        UserEntity u = users.findById(m.getUserId()).orElse(null);
        MemberView.UserInfo userInfo = u == null ? null : new MemberView.UserInfo(
                u.getPublicId().toString(), u.getUsername(), u.getNickname());
        return new MemberView(userInfo, m.getRole(), m.getStatus(), m.getVersion(), m.getCreatedAt());
    }

    private static void requireRoleValid(String role) {
        if (!ROLE_RANK.containsKey(role)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "role 必须是 owner/admin/member/viewer",
                    List.of(new ApiException.Detail("role", "invalid_value")));
        }
    }

    private static String normalizeSlug(String slug) {
        if (slug == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "slug 不能为空",
                    List.of(new ApiException.Detail("slug", "required")));
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "slug 必须为 3-63 位小写字母/数字，可含 -，且以字母或数字开头",
                    List.of(new ApiException.Detail("slug", "invalid_format")));
        }
        return normalized;
    }
}

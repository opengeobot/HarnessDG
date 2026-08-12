package com.modelhub.catalog.service;

import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.CollaboratorEntity;
import com.modelhub.catalog.repo.CollaboratorRepository;
import com.modelhub.identity.domain.OrganizationEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.OrganizationRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuditService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ETags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 协作者管理（02 §2/§3.3）：
 * - 显式授权、可审计、可过期；subject 支持 user 与 organization；
 * - ADMIN 可授全部角色，MAINTAIN 仅可授 write/read；
 * - remove 幂等（不存在也返回成功）。
 */
@Service
public class CollaboratorService {

    /** 契约 Collaborator schema。 */
    public record CollaboratorView(UUID subjectId, String subjectType, String role,
                                   OffsetDateTime expiresAt, long version, OffsetDateTime createdAt) {}

    private static final Set<String> VALID_ROLES = Set.of("read", "write", "maintain", "admin");

    private final CollaboratorRepository collaborators;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final RepositoryAccessFacade access;
    private final AuditService audit;

    public CollaboratorService(CollaboratorRepository collaborators, UserRepository users,
                               OrganizationRepository organizations, RepositoryAccessFacade access,
                               AuditService audit) {
        this.collaborators = collaborators;
        this.users = users;
        this.organizations = organizations;
        this.access = access;
        this.audit = audit;
    }

    /** 仅仓库 ADMIN 可见协作者列表（契约 listRepositoryCollaborators）。 */
    @Transactional(readOnly = true)
    public CursorResult<CollaboratorView> list(CurrentPrincipal actor, UUID repoId, CursorQuery cursor) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.ADMIN);
        List<CollaboratorEntity> all = collaborators.findByRepositoryIdOrderByIdAsc(ctx.repo().getId());
        List<CollaboratorEntity> filtered = new ArrayList<>();
        for (CollaboratorEntity c : all) {
            if (cursor.lastKey() == Long.MIN_VALUE || c.getId() > cursor.lastKey()) {
                filtered.add(c);
            }
        }
        boolean hasMore = filtered.size() > cursor.limit();
        List<CollaboratorView> items = toViews(filtered.stream().limit(cursor.limit()).toList());
        String next = hasMore && !items.isEmpty()
                ? CursorQuery.encode(filtered.get(cursor.limit() - 1).getId()) : null;
        return new CursorResult<>(items, next);
    }

    @Transactional
    public CollaboratorView add(CurrentPrincipal actor, UUID repoId, String subjectType,
                                UUID subjectId, String role, OffsetDateTime expiresAt) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        validateRole(role);
        checkGrantPermission(ctx.role(), role);

        CollaboratorEntity c = new CollaboratorEntity();
        c.setRepositoryId(ctx.repo().getId());
        if ("user".equals(subjectType)) {
            UserEntity user = users.findByPublicId(subjectId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "协作者用户不存在"));
            if (collaborators.findByRepositoryIdAndSubjectTypeAndSubjectUserId(
                    ctx.repo().getId(), "user", user.getId()).isPresent()) {
                throw new ApiException(ErrorCode.CONFLICT, "该用户已是仓库协作者");
            }
            c.setSubjectType("user");
            c.setSubjectUserId(user.getId());
        } else if ("organization".equals(subjectType)) {
            OrganizationEntity org = organizations.findByPublicId(subjectId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "协作者组织不存在"));
            if (collaborators.findByRepositoryIdAndSubjectTypeAndSubjectOrganizationId(
                    ctx.repo().getId(), "organization", org.getId()).isPresent()) {
                throw new ApiException(ErrorCode.CONFLICT, "该组织已是仓库协作者");
            }
            c.setSubjectType("organization");
            c.setSubjectOrganizationId(org.getId());
        } else {
            throw ApiException.badRequest("subjectType 仅支持 user/organization",
                    List.of(new ApiException.Detail("subjectType", "invalid_value")));
        }
        c.setRole(role);
        c.setGrantedBy(actor.userId());
        c.setExpiresAt(expiresAt);
        OffsetDateTime now = OffsetDateTime.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        collaborators.save(c);
        audit.appendSimple(String.valueOf(actor.userId()), "collaborator.add",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(c, subjectId);
    }

    @Transactional
    public CollaboratorView update(CurrentPrincipal actor, UUID repoId, UUID subjectId,
                                   String role, OffsetDateTime expiresAt, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        CollaboratorEntity c = findBySubject(ctx.repo().getId(), subjectId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(c.getVersion()), "协作者");
        if (role != null) {
            validateRole(role);
            checkGrantPermission(ctx.role(), role);
            checkGrantPermission(ctx.role(), c.getRole());
            c.setRole(role);
        }
        if (expiresAt != null) {
            c.setExpiresAt(expiresAt);
        }
        c.setUpdatedAt(OffsetDateTime.now());
        collaborators.save(c);
        audit.appendSimple(String.valueOf(actor.userId()), "collaborator.update",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(c, subjectId);
    }

    /** 幂等移除：不存在视为成功。 */
    @Transactional
    public void remove(CurrentPrincipal actor, UUID repoId, UUID subjectId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        CollaboratorEntity c = collaborators.findByRepositoryIdAndSubjectTypeAndSubjectUserId(
                        ctx.repo().getId(), "user", userIdOf(subjectId))
                .or(() -> collaborators.findByRepositoryIdAndSubjectTypeAndSubjectOrganizationId(
                        ctx.repo().getId(), "organization", orgIdOf(subjectId)))
                .orElse(null);
        if (c == null) {
            return;
        }
        if (ifMatch != null && !ifMatch.isBlank()) {
            ETags.requireMatch(ifMatch, ETags.ofVersion(c.getVersion()), "协作者");
        }
        // 仅可移除自己有权授予的角色（MAINTAIN 不能移除 admin/maintain 协作者）
        checkGrantPermission(ctx.role(), c.getRole());
        collaborators.delete(c);
        audit.appendSimple(String.valueOf(actor.userId()), "collaborator.remove",
                "repository:" + ctx.repo().getPublicId(), "success");
    }

    private CollaboratorEntity findBySubject(Long repoId, UUID subjectId) {
        Long userId = userIdOf(subjectId);
        if (userId != null) {
            CollaboratorEntity c = collaborators
                    .findByRepositoryIdAndSubjectTypeAndSubjectUserId(repoId, "user", userId).orElse(null);
            if (c != null) {
                return c;
            }
        }
        Long orgId = orgIdOf(subjectId);
        if (orgId != null) {
            CollaboratorEntity c = collaborators
                    .findByRepositoryIdAndSubjectTypeAndSubjectOrganizationId(repoId, "organization", orgId)
                    .orElse(null);
            if (c != null) {
                return c;
            }
        }
        throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "协作者不存在");
    }

    private Long userIdOf(UUID subjectId) {
        return users.findByPublicId(subjectId).map(UserEntity::getId).orElse(null);
    }

    private Long orgIdOf(UUID subjectId) {
        return organizations.findByPublicId(subjectId).map(OrganizationEntity::getId).orElse(null);
    }

    private static void validateRole(String role) {
        if (role == null || !VALID_ROLES.contains(role)) {
            throw ApiException.badRequest("未知协作者角色: " + role,
                    List.of(new ApiException.Detail("role", "invalid_value")));
        }
    }

    /** ADMIN 可授全部角色；MAINTAIN 仅可授 write/read（02 §3.3）。 */
    private static void checkGrantPermission(RepoRole actorRole, String grantedRole) {
        if (actorRole.atLeast(RepoRole.ADMIN)) {
            return;
        }
        if (actorRole.atLeast(RepoRole.MAINTAIN)
                && ("write".equals(grantedRole) || "read".equals(grantedRole))) {
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN, "无权授予角色: " + grantedRole);
    }

    private List<CollaboratorView> toViews(List<CollaboratorEntity> entities) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> orgIds = new HashSet<>();
        for (CollaboratorEntity c : entities) {
            if ("user".equals(c.getSubjectType()) && c.getSubjectUserId() != null) {
                userIds.add(c.getSubjectUserId());
            } else if (c.getSubjectOrganizationId() != null) {
                orgIds.add(c.getSubjectOrganizationId());
            }
        }
        Map<Long, UUID> userPublicIds = new HashMap<>();
        users.findAllById(userIds).forEach(u -> userPublicIds.put(u.getId(), u.getPublicId()));
        Map<Long, UUID> orgPublicIds = new HashMap<>();
        organizations.findAllById(orgIds).forEach(o -> orgPublicIds.put(o.getId(), o.getPublicId()));
        return entities.stream().map(c -> toView(c,
                        "user".equals(c.getSubjectType())
                                ? userPublicIds.get(c.getSubjectUserId())
                                : orgPublicIds.get(c.getSubjectOrganizationId())))
                .toList();
    }

    private static CollaboratorView toView(CollaboratorEntity c, UUID subjectId) {
        return new CollaboratorView(subjectId, c.getSubjectType(), c.getRole(),
                c.getExpiresAt(), c.getVersion(), c.getCreatedAt());
    }
}

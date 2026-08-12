package com.modelhub.catalog.service;

import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GatedGrantEntity;
import com.modelhub.catalog.domain.GatedPolicyEntity;
import com.modelhub.catalog.domain.GatedRequestEntity;
import com.modelhub.catalog.repo.GatedGrantRepository;
import com.modelhub.catalog.repo.GatedPolicyRepository;
import com.modelhub.catalog.repo.GatedRequestRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuditService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.web.ETags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * gated 访问控制（02 §4、03 §7）：
 * - 申请/批准/拒绝/撤销/撤回全流程，完整保存审批历史；
 * - 批准建立可过期 grant；撤销/过期立即阻止签发新凭据；
 * - generation 递增使旧 grant 永不自动复活。
 */
@Service
public class GatedAccessService {

    /** 契约 AccessRequest schema。 */
    public record AccessRequestView(UUID id, UUID repositoryId, UUID requesterId, String status,
                                    String reason, UUID reviewerId, OffsetDateTime grantExpiresAt,
                                    long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    private final GatedRequestRepository requests;
    private final GatedGrantRepository grants;
    private final GatedPolicyRepository policies;
    private final UserRepository users;
    private final RepositoryRepository repositories;
    private final RepositoryAccessFacade access;
    private final AuditService audit;

    public GatedAccessService(GatedRequestRepository requests, GatedGrantRepository grants,
                              GatedPolicyRepository policies, UserRepository users,
                              RepositoryRepository repositories,
                              RepositoryAccessFacade access, AuditService audit) {
        this.requests = requests;
        this.grants = grants;
        this.policies = policies;
        this.users = users;
        this.repositories = repositories;
        this.access = access;
        this.audit = audit;
    }

    @Transactional
    public AccessRequestView requestAccess(CurrentPrincipal actor, UUID repoId, String reason) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.NONE);
        if (!ctx.gatedEnabled() || !isPolicyEnabled(ctx.repo().getGatedPolicyId())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "该仓库未启用 gated 访问控制");
        }
        // 已持有有效 grant 或存在 pending 申请 → 409
        if (access.hasActiveGrant(ctx.repo(), actor)) {
            throw new ApiException(ErrorCode.CONFLICT, "已持有有效访问授权");
        }
        if (requests.findFirstByRepositoryIdAndUserIdAndStatus(
                ctx.repo().getId(), actor.userId(), "pending").isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "已存在待审核的访问申请");
        }
        OffsetDateTime now = OffsetDateTime.now();
        GatedRequestEntity req = new GatedRequestEntity();
        req.setPublicId(PublicIds.next());
        req.setRepositoryId(ctx.repo().getId());
        req.setUserId(actor.userId());
        req.setStatus("pending");
        req.setMessage(reason);
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        // saveAndFlush：@Version flush 时才递增，响应 version 直接作为后续 If-Match（04 §10）
        requests.saveAndFlush(req);
        audit.appendSimple(String.valueOf(actor.userId()), "gated.request",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(req, null);
    }

    /** 维护者查看申请（cursor 模式，04 §6.2）。 */
    @Transactional(readOnly = true)
    public CursorResult<AccessRequestView> listForRepo(CurrentPrincipal actor, UUID repoId, CursorQuery cursor) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        List<GatedRequestEntity> all = requests.findByRepositoryIdOrderByCreatedAtDesc(ctx.repo().getId());
        return page(all, cursor);
    }

    /** 申请人查看自己的申请（/me/access-requests）。 */
    @Transactional(readOnly = true)
    public CursorResult<AccessRequestView> listMine(CurrentPrincipal actor, CursorQuery cursor, String status) {
        List<GatedRequestEntity> all = requests.findByUserIdOrderByCreatedAtDesc(actor.userId());
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(r -> status.equals(r.getStatus())).toList();
        }
        return page(all, cursor);
    }

    @Transactional
    public AccessRequestView approve(CurrentPrincipal actor, UUID repoId, UUID requestId,
                                     OffsetDateTime grantExpiresAt, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        GatedRequestEntity req = loadRequest(ctx.repo().getId(), requestId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(req.getVersion()), "访问申请");
        if (!"pending".equals(req.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "仅 pending 申请可批准");
        }
        GatedPolicyEntity policy = policies.findById(ctx.repo().getGatedPolicyId()).orElseThrow(
                () -> new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "gated 策略不存在"));
        OffsetDateTime now = OffsetDateTime.now();

        req.setStatus("approved");
        req.setReviewedBy(actor.userId());
        req.setReviewedAt(now);
        req.setUpdatedAt(now);
        requests.saveAndFlush(req);

        GatedGrantEntity grant = new GatedGrantEntity();
        grant.setRepositoryId(ctx.repo().getId());
        grant.setPolicyGeneration(policy.getGeneration());
        grant.setUserId(req.getUserId());
        grant.setRequestId(req.getId());
        grant.setGrantedBy(actor.userId());
        grant.setValidFrom(now);
        grant.setExpiresAt(grantExpiresAt != null
                ? grantExpiresAt : now.plusSeconds(policy.getDefaultGrantTtlSeconds()));
        grant.setConditions("{}");
        grant.setCreatedAt(now);
        grants.save(grant);

        audit.appendSimple(String.valueOf(actor.userId()), "gated.approve",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(req, grant.getExpiresAt());
    }

    @Transactional
    public AccessRequestView reject(CurrentPrincipal actor, UUID repoId, UUID requestId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        GatedRequestEntity req = loadRequest(ctx.repo().getId(), requestId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(req.getVersion()), "访问申请");
        if (!"pending".equals(req.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "仅 pending 申请可拒绝");
        }
        OffsetDateTime now = OffsetDateTime.now();
        req.setStatus("rejected");
        req.setReviewedBy(actor.userId());
        req.setReviewedAt(now);
        req.setUpdatedAt(now);
        requests.saveAndFlush(req);
        audit.appendSimple(String.valueOf(actor.userId()), "gated.reject",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(req, null);
    }

    /** 维护者撤销已批准 grant，或申请人撤回 pending 申请（契约 revoke 双语义）。 */
    @Transactional
    public AccessRequestView revoke(CurrentPrincipal actor, UUID repoId, UUID requestId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.NONE);
        GatedRequestEntity req = loadRequest(ctx.repo().getId(), requestId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(req.getVersion()), "访问申请");
        OffsetDateTime now = OffsetDateTime.now();
        boolean applicant = actor.userId().equals(req.getUserId());

        if ("pending".equals(req.getStatus()) && applicant) {
            req.setStatus("withdrawn");
        } else if ("approved".equals(req.getStatus()) && ctx.role().atLeast(RepoRole.MAINTAIN)) {
            req.setStatus("revoked");
            grants.findByRequestId(req.getId()).ifPresent(g -> {
                if (g.getRevokedAt() == null) {
                    g.setRevokedAt(now);
                    grants.save(g);
                }
            });
        } else {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不可撤销/撤回: " + req.getStatus());
        }
        req.setReviewedBy(applicant && "withdrawn".equals(req.getStatus()) ? null : actor.userId());
        req.setUpdatedAt(now);
        requests.saveAndFlush(req);
        audit.appendSimple(String.valueOf(actor.userId()), "gated.revoke",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(req, null);
    }

    private GatedRequestEntity loadRequest(Long repoId, UUID requestId) {
        GatedRequestEntity req = requests.findByPublicId(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "访问申请不存在"));
        if (!req.getRepositoryId().equals(repoId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "访问申请不存在");
        }
        return req;
    }

    private boolean isPolicyEnabled(Long policyId) {
        if (policyId == null) {
            return false;
        }
        return policies.findById(policyId).map(GatedPolicyEntity::isEnabled).orElse(false);
    }

    private CursorResult<AccessRequestView> page(List<GatedRequestEntity> all, CursorQuery cursor) {
        // 简化：id 倒序集合按 cursor(lastKey=id) 过滤后取 limit+1 判定 nextCursor
        List<GatedRequestEntity> filtered = new ArrayList<>();
        for (GatedRequestEntity r : all) {
            if (r.getId() < -cursor.lastKey() || cursor.lastKey() == Long.MIN_VALUE) {
                filtered.add(r);
            }
        }
        boolean hasMore = filtered.size() > cursor.limit();
        List<AccessRequestView> items = filtered.stream().limit(cursor.limit())
                .map(r -> toView(r, grantExpiry(r)))
                .toList();
        String next = hasMore && !items.isEmpty()
                ? CursorQuery.encode(-filtered.get(cursor.limit() - 1).getId()) : null;
        return new CursorResult<>(items, next);
    }

    private OffsetDateTime grantExpiry(GatedRequestEntity req) {
        if (!"approved".equals(req.getStatus())) {
            return null;
        }
        return grants.findByRequestId(req.getId())
                .map(g -> g.getRevokedAt() == null ? g.getExpiresAt() : null).orElse(null);
    }

    private AccessRequestView toView(GatedRequestEntity req, OffsetDateTime grantExpiresAt) {
        UUID requester = users.findById(req.getUserId()).map(UserEntity::getPublicId).orElse(null);
        UUID reviewer = req.getReviewedBy() == null ? null
                : users.findById(req.getReviewedBy()).map(UserEntity::getPublicId).orElse(null);
        UUID repoPublicId = repositories.findById(req.getRepositoryId())
                .map(com.modelhub.catalog.domain.RepositoryEntity::getPublicId).orElse(null);
        return new AccessRequestView(req.getPublicId(), repoPublicId, requester, req.getStatus(),
                req.getMessage(), reviewer, grantExpiresAt,
                req.getVersion() == null ? 0 : req.getVersion(), req.getCreatedAt(), req.getUpdatedAt());
    }
}

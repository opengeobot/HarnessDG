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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * gated 访问控制（02 §4、03 §7）：
 * - 状态机 pending/approved/rejected/revoked/expired（契约 AccessRequest.status enum，
 *   02 §4；无 withdrawn 状态——申请人撤回与维护者撤销统一落到 revoked）；
 * - 申请/批准/拒绝/撤销全流程，完整保存审批历史；
 * - 批准建立可过期 grant；撤销/过期立即阻止签发新凭据；
 * - {@link #expireOverdue()} 将 grant 已到期的 approved 申请收敛为 expired；
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

    /**
     * 维护者查看申请（cursor 模式，04 §6.2）：DB 层 keyset 分页，
     * 排序 id DESC 与续读键（id < lastId）一致，并发写入下不重复、不遗漏。
     */
    @Transactional(readOnly = true)
    public CursorResult<AccessRequestView> listForRepo(CurrentPrincipal actor, UUID repoId, CursorQuery cursor) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.MAINTAIN);
        Pageable pageable = PageRequest.of(0, cursor.limit() + 1);
        List<GatedRequestEntity> rows = cursor.lastKey() == Long.MIN_VALUE
                ? requests.findByRepositoryIdOrderByIdDesc(ctx.repo().getId(), pageable)
                : requests.findByRepositoryIdAndIdLessThanOrderByIdDesc(
                        ctx.repo().getId(), cursor.lastKey(), pageable);
        return keysetPage(rows, cursor.limit());
    }

    /**
     * 申请人查看自己的申请（/me/access-requests）：同 keyset 语义 + 可选 status 过滤。
     * 有/无 status 走不同查询：PostgreSQL 无法推断 null 绑定参数类型（42P18），
     * 不能用 (:status is null OR ...) 合并单条 JPQL。
     */
    @Transactional(readOnly = true)
    public CursorResult<AccessRequestView> listMine(CurrentPrincipal actor, CursorQuery cursor, String status) {
        String statusFilter = status == null || status.isBlank() ? null : status;
        boolean firstPage = cursor.lastKey() == Long.MIN_VALUE;
        Long userId = actor.userId();
        Long lastId = cursor.lastKey();
        Pageable pageable = PageRequest.of(0, cursor.limit() + 1);
        List<GatedRequestEntity> rows;
        if (statusFilter == null) {
            rows = firstPage
                    ? requests.findByUserIdOrderByIdDesc(userId, pageable)
                    : requests.findByUserIdAndIdLessThanOrderByIdDesc(userId, lastId, pageable);
        } else {
            rows = firstPage
                    ? requests.findByUserIdAndStatusOrderByIdDesc(userId, statusFilter, pageable)
                    : requests.findByUserIdAndIdLessThanAndStatusOrderByIdDesc(
                            userId, lastId, statusFilter, pageable);
        }
        return keysetPage(rows, cursor.limit());
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

    /**
     * 维护者撤销已批准 grant，或申请人撤回 pending 申请（04 §4）。
     * 两种语义共用同一 :revoke 端点，契约状态统一为 revoked：
     * - 申请人撤回自己的 pending 申请 → revoked（reviewed_by 留空，不是审批行为；
     *   审计动作仍为 gated.withdraw 以区分主动撤销）
     * - 维护者撤销已批准的 grant → revoked（同时吊销 grant，reviewed_by 记录维护者）
     * 终态幂等（04 §10「revoke 重复调用返回相同最终语义」）：申请已处于
     * revoked/rejected/expired 时重复调用直接返回当前视图，不产生变更也不报错。
     */
    @Transactional
    public AccessRequestView revoke(CurrentPrincipal actor, UUID repoId, UUID requestId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.NONE);
        GatedRequestEntity req = loadRequest(ctx.repo().getId(), requestId);
        OffsetDateTime now = OffsetDateTime.now();
        boolean applicant = actor.userId().equals(req.getUserId());
        boolean maintainer = ctx.role().atLeast(RepoRole.MAINTAIN);
        String status = req.getStatus();

        // 终态幂等：重复 revoke 返回相同终态。rejected/expired 与本操作无竞态，
        // 一律幂等返回；revoked 要求申请本人（撤回重放）或维护者（撤销重放），
        // 其余主体对他人申请无任何撤销权限。重放可携带过期 If-Match（首次调用
        // 已推进 version），故终态判定先于条件更新校验。
        if ("rejected".equals(status) || "expired".equals(status)
                || ("revoked".equals(status) && (applicant || maintainer))) {
            return toView(req, null);
        }
        if ("revoked".equals(status)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不可撤销/撤回: " + status);
        }

        ETags.requireMatch(ifMatch, ETags.ofVersion(req.getVersion()), "访问申请");
        boolean selfWithdraw = "pending".equals(status) && applicant;

        if (selfWithdraw) {
            // 申请人撤回：契约无 withdrawn 状态，语义归入 revoked（reviewed_by 留空）
            req.setStatus("revoked");
        } else if ("approved".equals(status) && maintainer) {
            req.setStatus("revoked");
            grants.findByRequestId(req.getId()).ifPresent(g -> {
                if (g.getRevokedAt() == null) {
                    g.setRevokedAt(now);
                    grants.save(g);
                }
            });
            req.setReviewedBy(actor.userId());
            req.setReviewedAt(now);
        } else {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不可撤销/撤回: " + status);
        }
        req.setUpdatedAt(now);
        requests.saveAndFlush(req);
        audit.appendSimple(String.valueOf(actor.userId()),
                selfWithdraw ? "gated.withdraw" : "gated.revoke",
                "repository:" + ctx.repo().getPublicId(), "success");
        return toView(req, null);
    }

    /**
     * grant 到期收敛（02 §4、04 §4）：grant 已过期且未吊销的 approved 申请
     * 转为 expired 终态。由 {@link com.modelhub.catalog.worker.GatedGrantExpiryWorker}
     * 定时驱动；仅推进 approved 申请，重复执行幂等无副作用。expired 与 revoked
     * 一样是终态：此后申请与 grant 均不再允许签发新凭据（hasActiveGrant 已过滤）。
     */
    @Transactional
    public void expireOverdue() {
        OffsetDateTime now = OffsetDateTime.now();
        for (GatedGrantEntity grant : grants.findByExpiresAtBeforeAndRevokedAtIsNull(now)) {
            if (grant.getRequestId() == null) {
                continue;
            }
            requests.findById(grant.getRequestId()).ifPresent(req -> {
                if ("approved".equals(req.getStatus())) {
                    req.setStatus("expired");
                    req.setUpdatedAt(now);
                    requests.save(req);
                }
            });
        }
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

    /**
     * keyset 分页装配：查询已取 limit+1 行，多出一行即 hasMore；
     * nextCursor 为本页末条 id（CursorQuery 单键 lastKey），末页为 null。
     */
    private CursorResult<AccessRequestView> keysetPage(List<GatedRequestEntity> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<AccessRequestView> items = rows.stream().limit(limit)
                .map(r -> toView(r, grantExpiry(r)))
                .toList();
        String next = hasMore && !items.isEmpty()
                ? CursorQuery.encode(rows.get(limit - 1).getId()) : null;
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

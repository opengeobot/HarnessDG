package com.modelhub.catalog.service;

import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.identity.repo.AuditLogRepository;
import com.modelhub.identity.repo.OrganizationRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.SystemHealthService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 系统概览服务（管理后台计划 §四）：平台计数 + 健康检查。
 * 仅 platform_admin / platform_auditor 可读（admin:system:view）。
 */
@Service
public class SystemOverviewService {

    /** 契约 SystemOverview schema。 */
    public record Counts(long users, long organizations, long repositories, long auditLogs) {}

    public record OverviewView(Counts counts, String healthStatus, Map<String, String> healthChecks) {}

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final RepositoryRepository repositories;
    private final AuditLogRepository auditLogs;
    private final SystemHealthService health;

    public SystemOverviewService(UserRepository users, OrganizationRepository organizations,
                                 RepositoryRepository repositories, AuditLogRepository auditLogs,
                                 SystemHealthService health) {
        this.users = users;
        this.organizations = organizations;
        this.repositories = repositories;
        this.auditLogs = auditLogs;
        this.health = health;
    }

    @Transactional(readOnly = true)
    public OverviewView overview(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        if (!actor.isPlatformAdmin() && !actor.isPlatformAuditor()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "需要 platform_admin 或 platform_auditor 角色");
        }
        Counts counts = new Counts(users.count(), organizations.count(),
                repositories.count(), auditLogs.count());
        SystemHealthService.HealthStatus ready = health.ready();
        return new OverviewView(counts, ready.status(), ready.checks());
    }
}

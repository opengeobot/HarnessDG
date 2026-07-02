/*
 * 功能: 资源 ACL 应用服务，编排资源级显式授权的查询、创建与删除（一次请求可授予多个权限编码）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.application;

import com.aihub.authorization.application.AuthorizationDtos.CreateResourceAclCommand;
import com.aihub.authorization.application.AuthorizationDtos.ResourceAclView;
import com.aihub.authorization.domain.PermissionRepository;
import com.aihub.authorization.domain.ResourceAcl;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资源 ACL 应用服务。
 *
 * <p>用例编排：列表、创建（权限编码须均已注册；同一资源+主体可授予多个权限）、删除。每个
 * (资源类型,资源ID,主体,权限) 在库内是一行；视图按行返回。
 */
@Service
public class ResourceAclApplicationService {

    private final ResourceAclRepository resourceAclRepository;
    private final PermissionRepository permissionRepository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public ResourceAclApplicationService(ResourceAclRepository resourceAclRepository,
                                         PermissionRepository permissionRepository,
                                         IdGenerator idGenerator,
                                         AuditPort auditPort,
                                         Clock clock) {
        this.resourceAclRepository = resourceAclRepository;
        this.permissionRepository = permissionRepository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 查询全部资源 ACL。
     */
    @Transactional(readOnly = true)
    public List<ResourceAclView> listAcls() {
        return resourceAclRepository.findAll().stream().map(ResourceAclView::from).toList();
    }

    /**
     * 创建资源 ACL：为同一资源+主体授予一个或多个权限编码，共享同一 aclId 成组。
     */
    @Transactional
    public ResourceAclView createAcl(CreateResourceAclCommand command) {
        if (command.principalId() == null || command.principalId().isBlank()) {
            throw new ValidationException("principalId is required");
        }
        if (command.resourceType() == null || command.resourceType().isBlank()) {
            throw new ValidationException("resourceType is required");
        }
        if (command.resourceId() == null || command.resourceId().isBlank()) {
            throw new ValidationException("resourceId is required");
        }
        if (command.permissionCodes() == null || command.permissionCodes().isEmpty()) {
            throw new ValidationException("permissionCodes is required");
        }
        List<String> unknown = permissionRepository.findUnknownCodes(command.permissionCodes());
        if (!unknown.isEmpty()) {
            throw new ValidationException(ErrorCode.PERMISSION_UNKNOWN,
                    "unknown permission codes", Map.of("unknown", unknown));
        }

        String resourceType = command.resourceType().trim();
        String resourceId = command.resourceId().trim();
        Set<String> permissions = new LinkedHashSet<>(command.permissionCodes());
        for (String permission : permissions) {
            if (resourceAclRepository.exists(resourceType, resourceId, command.principalId(), permission)) {
                throw new ConflictException(ErrorCode.RESOURCE_ACL_ALREADY_EXISTS,
                        "resource acl already exists", Map.of("permission", permission));
            }
        }
        Instant now = clock.instant();
        String aclId = idGenerator.generate(IdPrefix.RESOURCE_ACL);
        ResourceAcl acl = new ResourceAcl(aclId, resourceType, resourceId, command.principalId(),
                permissions, actorId(), now);
        resourceAclRepository.create(acl);
        auditPort.record("RESOURCE_ACL_CREATED", actorId(), aclId,
                Map.of("resourceType", resourceType, "resourceId", resourceId,
                        "principalId", command.principalId()));
        return new ResourceAclView(aclId, command.principalId(), resourceType, resourceId,
                List.copyOf(permissions), now);
    }

    /**
     * 删除资源 ACL 条目。
     */
    @Transactional
    public void deleteAcl(String aclId) {
        resourceAclRepository.findByAclId(aclId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESOURCE_ACL_NOT_FOUND,
                        "resource acl not found", Map.of()));
        resourceAclRepository.deleteByAclId(aclId);
        auditPort.record("RESOURCE_ACL_DELETED", actorId(), aclId, Map.of());
    }

    private String actorId() {
        return PrincipalContextHolder.current().map(PrincipalContext::principalId).orElse(null);
    }
}

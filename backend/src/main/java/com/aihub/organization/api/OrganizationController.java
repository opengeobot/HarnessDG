/*
 * 功能: 组织与组织成员管理 REST 适配器，提供组织列表/创建与成员列表/添加/移除接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.organization.api.OrganizationRequests.AddOrganizationMemberRequest;
import com.aihub.organization.api.OrganizationRequests.CreateOrganizationRequest;
import com.aihub.organization.application.OrganizationApplicationService;
import com.aihub.organization.application.OrganizationDtos.AddMemberCommand;
import com.aihub.organization.application.OrganizationDtos.CreateOrganizationCommand;
import com.aihub.organization.application.OrganizationDtos.OrganizationMemberView;
import com.aihub.organization.application.OrganizationDtos.OrganizationView;
import com.aihub.organization.domain.MemberRole;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组织与成员管理 REST 适配器。
 *
 * <p>授权判定走统一 {@link AuthorizationService}（fail-closed）：读列表需 {@code project:view}，
 * 写操作（创建组织、增删成员）需 {@code organization:manage}。成员隔离由应用层据平台管理员标记下推。
 */
@RestController
@RequestMapping("/api/v1/system/organizations")
public class OrganizationController {

    private final OrganizationApplicationService organizationService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public OrganizationController(OrganizationApplicationService organizationService,
                                  AuthorizationService authorizationService,
                                  IdempotencyService idempotencyService,
                                  ObjectMapper objectMapper) {
        this.organizationService = organizationService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
    }

    /**
     * 查询调用者可访问的组织（平台组织管理员可见全部，其余仅见所属组织）。
     */
    @GetMapping
    public ApiResponse<List<OrganizationView>> listOrganizations() {
        authorizationService.requirePermission(Permissions.PROJECT_VIEW);
        boolean platformAdmin = authorizationService.isPermitted(Permissions.ORGANIZATION_MANAGE);
        return OrganizationApiContext.respond(
                organizationService.listOrganizations(OrganizationApiContext.principalId(), platformAdmin));
    }

    /**
     * 创建组织。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrganizationView> createOrganization(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateOrganizationRequest request) {
        authorizationService.requirePermission(Permissions.ORGANIZATION_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                OrganizationApiContext.principalId(), "POST", "/api/v1/system/organizations");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<OrganizationView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            OrganizationView view = organizationService.createOrganization(
                    new CreateOrganizationCommand(request.code(), request.name(), request.giteaOrganization()),
                    OrganizationApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return OrganizationApiContext.respond(ref.get());
    }

    /**
     * 查询组织成员。
     */
    @GetMapping("/{organizationId}/members")
    public ApiResponse<List<OrganizationMemberView>> listMembers(@PathVariable String organizationId) {
        authorizationService.requirePermission(Permissions.ORGANIZATION_MANAGE);
        return OrganizationApiContext.respond(organizationService.listMembers(organizationId));
    }

    /**
     * 添加组织成员（默认 MEMBER 角色）。
     */
    @PostMapping("/{organizationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrganizationMemberView> addMember(
            @PathVariable String organizationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody AddOrganizationMemberRequest request) {
        authorizationService.requirePermission(Permissions.ORGANIZATION_MANAGE);
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                OrganizationApiContext.principalId(), "POST",
                "/api/v1/system/organizations/" + organizationId + "/members");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<OrganizationMemberView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            OrganizationMemberView view = organizationService.addMember(
                    new AddMemberCommand(organizationId, request.principalId()),
                    MemberRole.MEMBER, OrganizationApiContext.principalId());
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return OrganizationApiContext.respond(ref.get());
    }

    /**
     * 移除组织成员并使其组织作用域授权失效。
     */
    @DeleteMapping("/{organizationId}/members/{principalId}")
    public ApiResponse<Void> removeMember(
            @PathVariable String organizationId,
            @PathVariable String principalId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.ORGANIZATION_MANAGE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                OrganizationApiContext.principalId(), "DELETE",
                "/api/v1/system/organizations/" + organizationId + "/members/" + principalId);
        idempotencyService.execute(key, null, () -> {
            organizationService.removeMember(organizationId, principalId,
                    OrganizationApiContext.principalId());
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return OrganizationApiContext.respond(null);
    }
}

package com.modelhub.api.controller;

import com.modelhub.api.dto.OrgRequests.AddMemberRequest;
import com.modelhub.api.dto.OrgRequests.CreateOrgRequest;
import com.modelhub.api.dto.OrgRequests.UpdateMemberRoleRequest;
import com.modelhub.api.dto.OrgRequests.UpdateOrgRequest;
import com.modelhub.api.support.Principals;
import com.modelhub.catalog.service.CatalogService;
import com.modelhub.identity.service.OrganizationService;
import com.modelhub.identity.service.OrganizationService.MemberView;
import com.modelhub.identity.service.OrganizationService.OrgView;
import com.modelhub.shared.paging.CursorQuery;
import com.modelhub.shared.paging.CursorResult;
import com.modelhub.shared.paging.PageQuery;
import com.modelhub.shared.paging.PageResult;
import com.modelhub.shared.web.ApiEnvelope;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 组织与成员端点（04 §4）：organizations 列表/详情用 page 模式，members 用 cursor 模式（04 §6.2）。
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationsController {

    /** members cursor 响应 data：items + nextCursor。 */
    public record MemberPageData(java.util.List<MemberView> items, String nextCursor) {}

    /** 组织列表项（09 §5.2）：OrgView 全字段 + repoCounts（typeKey → 计数，契约 Organization.repoCounts）。 */
    public record OrgListItem(@JsonProperty("id") String publicId, String slug, String name,
                              String description, String namespaceId, String status, long version,
                              String etag, OffsetDateTime createdAt, Map<String, Long> repoCounts) {}

    private final OrganizationService organizationService;
    private final CatalogService catalogService;

    public OrganizationsController(OrganizationService organizationService, CatalogService catalogService) {
        this.organizationService = organizationService;
        this.catalogService = catalogService;
    }

    /** 组织列表（09 §5.2）：匿名可读全部 active 组织 + repoCounts 聚合计数。 */
    @GetMapping
    public ApiEnvelope<PageResult<OrgListItem>> list(@RequestParam Map<String, String> params) {
        PageQuery page = PageQuery.from(params);
        PageResult<OrgView> orgs = organizationService.listAllActive(page);
        Map<String, Map<String, Long>> counts = catalogService.repoCountsByNamespace();
        List<OrgListItem> items = orgs.items().stream()
                .map(o -> new OrgListItem(o.publicId(), o.slug(), o.name(), o.description(),
                        o.namespaceId(), o.status(), o.version(), o.etag(), o.createdAt(),
                        counts.getOrDefault(o.namespaceId(), Map.of())))
                .toList();
        return ApiEnvelope.ok(new PageResult<>(orgs.total(), orgs.page(), orgs.pageSize(), items));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<OrgView>> create(@Valid @RequestBody CreateOrgRequest body,
                                                       HttpServletRequest request) {
        OrgView org = organizationService.create(Principals.requireCurrent(request),
                body.slug(), body.name(), body.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(org));
    }

    @GetMapping("/{orgId}")
    public ResponseEntity<ApiEnvelope<OrgView>> get(@PathVariable UUID orgId, HttpServletRequest request) {
        OrgView view = organizationService.get(Principals.requireCurrent(request), orgId);
        return ResponseEntity.ok().eTag(view.etag()).body(ApiEnvelope.ok(view));
    }

    @PatchMapping("/{orgId}")
    public ApiEnvelope<OrgView> update(@PathVariable UUID orgId,
                                       @Valid @RequestBody UpdateOrgRequest body,
                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                       HttpServletRequest request) {
        return ApiEnvelope.ok(organizationService.update(Principals.requireCurrent(request), orgId,
                body.name(), body.status(), ifMatch));
    }

    @GetMapping("/{orgId}/members")
    public ApiEnvelope<MemberPageData> listMembers(@PathVariable UUID orgId,
                                                   @RequestParam Map<String, String> params,
                                                   HttpServletRequest request) {
        CursorQuery cursor = CursorQuery.from(normalize(params));
        CursorResult<MemberView> result =
                organizationService.listMembers(Principals.requireCurrent(request), orgId, cursor);
        return ApiEnvelope.ok(new MemberPageData(result.items(), result.nextCursor()));
    }

    @PostMapping("/{orgId}/members")
    public ResponseEntity<ApiEnvelope<MemberView>> addMember(@PathVariable UUID orgId,
                                                             @Valid @RequestBody AddMemberRequest body,
                                                             HttpServletRequest request) {
        MemberView member = organizationService.addMember(Principals.requireCurrent(request), orgId,
                body.userId(), body.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(member));
    }

    @PatchMapping("/{orgId}/members/{userId}")
    public ApiEnvelope<MemberView> updateMemberRole(@PathVariable UUID orgId, @PathVariable UUID userId,
                                                    @Valid @RequestBody UpdateMemberRoleRequest body,
                                                    @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                    HttpServletRequest request) {
        return ApiEnvelope.ok(organizationService.updateMemberRole(
                Principals.requireCurrent(request), orgId, userId, body.role(), ifMatch));
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID orgId, @PathVariable UUID userId,
                                             HttpServletRequest request) {
        organizationService.removeMember(Principals.requireCurrent(request), orgId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Spring 把单值参数映射为 String；多值参数（本端点无）保持单值语义。 */
    private static Map<String, String> normalize(Map<String, String> params) {
        return new HashMap<>(params);
    }
}
